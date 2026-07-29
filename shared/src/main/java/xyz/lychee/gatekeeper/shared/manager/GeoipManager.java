package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.BinaryGeoIPDatabase;
import xyz.lychee.gatekeeper.shared.objects.GeoRange;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.SerializeUtils;
import xyz.lychee.gatekeeper.shared.util.TimingUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class GeoipManager extends AbstractManager implements Runnable {
    public static final GeoipManager INSTANCE = new GeoipManager();
    private static final Pattern ASN_PATTERN = Pattern.compile("(?i)\\b(?:AS)?(\\d{3,10})\\b");
    private static final Pattern IP_PATTERN = Pattern
            .compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(?:/(\\d{1,2}))?");
    private final List<String> asnSource = new ArrayList<>();
    private final List<String> proxySources = new ArrayList<>();
    private final BinaryGeoIPDatabase database = new BinaryGeoIPDatabase();
    private volatile IntOpenHashSet blacklistedAsns = new IntOpenHashSet();
    private volatile IntOpenHashSet blacklistedProxies = new IntOpenHashSet();
    private volatile List<GeoRange<Void>> blacklistedProxyRanges = Collections.emptyList();
    private Logger logger;
    private Path geoDataPath;
    private Path asnDataPath;
    private Path proxyDataPath;
    private Path proxyRangesDataPath;

    @Override
    public boolean load(Gatekeeper<?> plugin) throws IOException {
        this.logger = plugin.logger();
        this.geoDataPath = new File(plugin.dataFolder(), "geodata.ldb").toPath();
        this.asnDataPath = new File(plugin.dataFolder(), "asn_data.bin").toPath();
        this.proxyDataPath = new File(plugin.dataFolder(), "proxy_data.bin").toPath();
        this.proxyRangesDataPath = new File(plugin.dataFolder(), "proxy_ranges_data.bin").toPath();

        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();
        this.asnSource.clear();
        this.asnSource.addAll(yaml.getStringList("main.auto_updater.asn_sources"));
        Collections.shuffle(this.asnSource);

        this.proxySources.clear();
        this.proxySources.addAll(yaml.getStringList("main.auto_updater.proxy_sources"));
        Collections.shuffle(this.proxySources);

        this.download(true).join();
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    private boolean needUpdate(Path dataFile) {
        if (Files.notExists(dataFile)) {
            return true;
        }

        try {
            Instant updateThreshold = Instant.now().minus(12, ChronoUnit.HOURS);
            Instant fileModified = Files.getLastModifiedTime(dataFile).toInstant();
            return fileModified.compareTo(updateThreshold) < 0;
        } catch (IOException ignored) {
            return true;
        }
    }

    @Override
    public void run() {
        this.download(false);
    }

    public CompletableFuture<Void> download(boolean firstLoad) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        if (this.needUpdate(this.geoDataPath)) {
            this.logger.info(" &8• &rDownloading and building GeoIP database...");
            futures.add(
                    this.database.update(this.logger, this.geoDataPath)
                            .thenAccept(t -> this.logger
                                    .info(" &8• &rDownloaded " + this.database.getCountryRecordCount() + " country and "
                                            + this.database.getAsnRecordCount() + " asn ranges in " + t.stop() + "!")));
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading GeoIP database from " + this.geoDataPath + "...");
            futures.add(
                    this.database.load(this.logger, this.geoDataPath)
                            .thenAccept(t -> this.logger
                                    .info(" &8• &rLoaded " + this.database.getCountryRecordCount() + " country and "
                                            + this.database.getAsnRecordCount() + " asn ranges in " + t.stop() + "!")));
        }

        if (this.needUpdate(this.asnDataPath)) {
            this.logger.info(" &8• &rDownloading suspicious ASNs from " + this.asnSource.size() + " sources...");
            futures.add(
                    this.downloadFromSources(
                            this.asnSource,
                            this.asnDataPath,
                            line -> {
                                int commentIdx = line.indexOf('#');
                                String uncommented = (commentIdx != -1 ? line.substring(0, commentIdx) : line).trim();
                                if (uncommented.isEmpty())
                                    return null;
                                Matcher matcher = ASN_PATTERN.matcher(uncommented);
                                List<Integer> asns = new ArrayList<>();
                                while (matcher.find()) {
                                    asns.add(Integer.parseInt(matcher.group(1)));
                                }
                                return asns;
                            },
                            outputSet -> this.blacklistedAsns = outputSet)
                            .thenAccept(t -> this.logger.info(" &8• &rDownloaded " + this.blacklistedAsns.size()
                                    + " suspicious ASNs in " + t.stop() + "!")));
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading suspicious ASNs from " + this.asnDataPath + "...");
            futures.add(
                    this.loadFromFile(
                            this.asnDataPath,
                            outputSet -> this.blacklistedAsns = outputSet)
                            .thenAccept(t -> this.logger.info(" &8• &rLoaded " + this.blacklistedAsns.size()
                                    + " suspicious ASNs in " + t.stop() + "!")));
        }

        if (this.needUpdate(this.proxyDataPath) || this.needUpdate(this.proxyRangesDataPath)) {
            this.logger.info(" &8• &rDownloading suspicious IPs from " + this.proxySources.size() + " sources...");
            futures.add(this.downloadProxies().thenAccept(timing -> 
                this.logger.info(" &8• &rDownloaded " + this.blacklistedProxies.size()
                    + " suspicious IPs and " + this.blacklistedProxyRanges.size() + " ranges in " + timing.stop() + "!")));
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading suspicious IPs from " + this.proxyDataPath + " and ranges...");
            futures.add(
                    this.loadFromFile(this.proxyDataPath, outputSet -> this.blacklistedProxies = outputSet)
                        .thenCombine(this.loadProxyRangesFromFile(), (t1, t2) -> t1)
                        .thenAccept(timing -> this.logger.info(" &8• &rLoaded " + this.blacklistedProxies.size()
                                + " suspicious IPs and " + this.blacklistedProxyRanges.size() + " ranges in " + timing.stop() + "!")));
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<TimingUtil> downloadFromSources(
            List<String> sources,
            Path outputPath,
            Function<String, Collection<Integer>> parser,
            Consumer<IntOpenHashSet> consumer) {
        IntOpenHashSet outputSet = new IntOpenHashSet();
        TimingUtil timing = TimingUtil.startNew();
        return CompletableFuture.allOf(
                sources.stream().map(source -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(source))
                            .header("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0")
                            .GET()
                            .build();

                    return TaskManager.INSTANCE.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenAccept(response -> {
                                if (response.statusCode() != 200) {
                                    this.logger.warning(" &8• &eReceived " + response.statusCode()
                                            + " status code from source: &6" + source);
                                    return;
                                }

                                IntOpenHashSet localSet = new IntOpenHashSet();

                                String[] lines = response.body().split("\n");
                                for (String line : lines) {
                                    Collection<Integer> parsed = parser.apply(line);
                                    if (parsed != null && !parsed.isEmpty()) {
                                        localSet.addAll(parsed);
                                    }
                                }

                                synchronized (outputSet) {
                                    outputSet.addAll(localSet);
                                }
                            })
                            .exceptionally(ex -> {
                                this.logger.log(Level.WARNING, " &8• &cError while downloading data from " + source,
                                        ex);
                                return null;
                            });
                }).toArray(CompletableFuture[]::new))
                .thenApplyAsync(v -> {
                    byte[] serialized = SerializeUtils.serialize(outputSet);
                    try {
                        Files.write(outputPath, serialized);
                    } catch (IOException ex) {
                        this.logger.log(Level.SEVERE, " &8• &cFailed to write data to " + outputPath, ex);
                    }
                    consumer.accept(outputSet);
                    return timing;
                }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    private CompletableFuture<TimingUtil> downloadProxies() {
        IntOpenHashSet outputSet = new IntOpenHashSet();
        List<GeoRange<Void>> outputRanges = new ArrayList<>();
        TimingUtil timing = TimingUtil.startNew();
        return CompletableFuture.allOf(
                this.proxySources.stream().map(source -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(source))
                            .header("User-Agent",
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0")
                            .GET()
                            .build();

                    return TaskManager.INSTANCE.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                            .thenAccept(response -> {
                                if (response.statusCode() != 200) {
                                    this.logger.warning(" &8• &eReceived " + response.statusCode()
                                            + " status code from source: &6" + source);
                                    return;
                                }

                                IntOpenHashSet localSet = new IntOpenHashSet();
                                List<GeoRange<Void>> localRanges = new ArrayList<>();

                                String[] lines = response.body().split("\n");
                                for (String line : lines) {
                                    int commentIdx = line.indexOf('#');
                                    String uncommented = (commentIdx != -1 ? line.substring(0, commentIdx) : line).trim();
                                    if (uncommented.isEmpty()) continue;
                                    Matcher matcher = IP_PATTERN.matcher(uncommented);
                                    while (matcher.find()) {
                                        String ipStr = matcher.group(1);
                                        String cidrStr = matcher.group(2);
                                        if (AddressUtils.isIpv4(ipStr)) {
                                            int ip = AddressUtils.ipv4ToInt(ipStr);
                                            if (cidrStr != null) {
                                                int prefix = Integer.parseInt(cidrStr);
                                                if (prefix >= 0 && prefix <= 32) {
                                                    int mask = prefix == 0 ? 0 : 0xFFFFFFFF << (32 - prefix);
                                                    int startIp = ip & mask;
                                                    int endIp = startIp | ~mask;
                                                    if (prefix >= 8) {
                                                        if (startIp == endIp) {
                                                            localSet.add(startIp);
                                                        } else {
                                                            localRanges.add(new GeoRange<>(startIp, endIp, null));
                                                        }
                                                    }
                                                }
                                            } else {
                                                localSet.add(ip);
                                            }
                                        }
                                    }
                                }

                                synchronized (outputSet) {
                                    outputSet.addAll(localSet);
                                }
                                synchronized (outputRanges) {
                                    outputRanges.addAll(localRanges);
                                }
                            })
                            .exceptionally(ex -> {
                                this.logger.log(Level.WARNING, " &8• &cError while downloading data from " + source, ex);
                                return null;
                            });
                }).toArray(CompletableFuture[]::new))
                .thenApplyAsync(v -> {
                    outputRanges.sort((a, b) -> Integer.compareUnsigned(a.getStart(), b.getStart()));
                    List<GeoRange<Void>> mergedRanges = new ArrayList<>();
                    if (!outputRanges.isEmpty()) {
                        GeoRange<Void> current = outputRanges.get(0);
                        for (int i = 1; i < outputRanges.size(); i++) {
                            GeoRange<Void> next = outputRanges.get(i);
                            if (Integer.compareUnsigned(next.getStart(), current.getEnd()) <= 0 || (Integer.compareUnsigned(next.getStart(), current.getEnd() + 1) == 0 && next.getStart() != 0)) {
                                current = new GeoRange<>(current.getStart(), Integer.compareUnsigned(current.getEnd(), next.getEnd()) >= 0 ? current.getEnd() : next.getEnd(), null);
                            } else {
                                mergedRanges.add(current);
                                current = next;
                            }
                        }
                        mergedRanges.add(current);
                    }

                    byte[] serialized = SerializeUtils.serialize(outputSet);
                    try {
                        Files.write(this.proxyDataPath, serialized);
                        
                        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(mergedRanges.size() * 8);
                        for (GeoRange<Void> range : mergedRanges) {
                            buffer.putInt(range.getStart());
                            buffer.putInt(range.getEnd());
                        }
                        Files.write(this.proxyRangesDataPath, buffer.array());
                    } catch (IOException ex) {
                        this.logger.log(Level.SEVERE, " &8• &cFailed to write data", ex);
                    }
                    this.blacklistedProxies = outputSet;
                    this.blacklistedProxyRanges = mergedRanges;
                    return timing;
                }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    public CompletableFuture<TimingUtil> loadFromFile(Path path, Consumer<IntOpenHashSet> consumer) {
        TimingUtil timing = TimingUtil.startNew();
        return CompletableFuture.supplyAsync(() -> {
            IntOpenHashSet outputSet = new IntOpenHashSet();
            try {
                SerializeUtils.deserialize(Files.readAllBytes(path), outputSet);
            } catch (IOException ex) {
                this.logger.log(Level.SEVERE, " &8• &cFailed to read data from " + path, ex);
            }
            consumer.accept(outputSet);
            return timing;
        }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    private CompletableFuture<Void> loadProxyRangesFromFile() {
        return CompletableFuture.runAsync(() -> {
            if (Files.notExists(this.proxyRangesDataPath)) return;
            try {
                byte[] bytes = Files.readAllBytes(this.proxyRangesDataPath);
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                int count = bytes.length / 8;
                List<GeoRange<Void>> ranges = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ranges.add(new GeoRange<>(buffer.getInt(), buffer.getInt(), null));
                }
                this.blacklistedProxyRanges = ranges;
            } catch (IOException ex) {
                this.logger.log(Level.SEVERE, " &8• &cFailed to read data from " + this.proxyRangesDataPath, ex);
            }
        }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    public boolean isBlacklistedProxy(int ip) {
        if (this.blacklistedProxies.contains(ip)) {
            return true;
        }

        List<GeoRange<Void>> ranges = this.blacklistedProxyRanges;
        int low = 0;
        int high = ranges.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            GeoRange<Void> midVal = ranges.get(mid);

            if (Integer.compareUnsigned(ip, midVal.getStart()) < 0) {
                high = mid - 1;
            } else if (Integer.compareUnsigned(ip, midVal.getEnd()) > 0) {
                low = mid + 1;
            } else {
                return true;
            }
        }

        return false;
    }
}