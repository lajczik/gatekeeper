package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.BinaryGeoIPDatabase;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;
import xyz.lychee.gatekeeper.shared.util.SerializeUtils;
import xyz.lychee.gatekeeper.shared.util.TimingUtil;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final Pattern ASN_PATTERN = Pattern.compile("\\d{4,6}");
    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private final List<String> asnSource = new ArrayList<>();
    private final List<String> proxySources = new ArrayList<>();
    private final BinaryGeoIPDatabase database = new BinaryGeoIPDatabase();
    private volatile Set<Integer> blacklistedAsns = Collections.emptySet();
    private volatile Set<Integer> blacklistedProxies = Collections.emptySet();
    private Logger logger;
    private Path geoDataPath;
    private Path asnDataPath;
    private Path proxyDataPath;

    @Override
    public boolean load(Gatekeeper<?> plugin) throws IOException {
        this.logger = plugin.logger();
        this.geoDataPath = new File(plugin.dataFolder(), "geodata.ldb").toPath();
        this.asnDataPath = new File(plugin.dataFolder(), "asn_data.bin").toPath();
        this.proxyDataPath = new File(plugin.dataFolder(), "proxy_data.bin").toPath();

        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();
        this.asnSource.clear();
        this.asnSource.addAll(yaml.getStringList("main.auto_updater.asn_sources"));
        Collections.shuffle(this.asnSource, RandomUtils.RANDOM);

        this.proxySources.clear();
        this.proxySources.addAll(yaml.getStringList("main.auto_updater.proxy_sources"));
        Collections.shuffle(this.proxySources, RandomUtils.RANDOM);

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
                            .thenAccept(timing ->
                                    this.logger.info(" &8• &rDownloaded " + this.database.getCountryRecordCount() + " country and " + this.database.getAsnRecordCount() + " asn ranges in " + timing.stop().getExecutingTime() + "ms!")
                            )
            );
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading GeoIP database from " + this.geoDataPath + "...");
            futures.add(
                    this.database.load(this.logger, this.geoDataPath)
                            .thenAccept(timing ->
                                    this.logger.info(" &8• &rLoaded " + this.database.getCountryRecordCount() + " country and " + this.database.getAsnRecordCount() + " asn ranges in " + timing.stop().getExecutingTime() + "ms!")
                            )
            );
        }

        if (this.needUpdate(this.asnDataPath)) {
            this.logger.info(" &8• &rDownloading suspicious ASNs from " + this.asnSource.size() + " sources...");
            futures.add(
                    this.downloadFromSources(
                            this.asnSource,
                            this.asnDataPath,
                            ASN_PATTERN,
                            str -> RandomUtils.isInteger(str) ? Integer.parseInt(str) : null,
                            outputSet -> this.blacklistedAsns = outputSet
                    ).thenAccept(timing ->
                            this.logger.info(" &8• &rDownloaded " + this.blacklistedAsns.size() + " suspicious ASNs in " + timing.stop().getExecutingTime() + "ms!")
                    )
            );
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading suspicious ASNs from " + this.asnDataPath + "...");
            futures.add(
                    this.loadFromFile(
                            this.asnDataPath,
                            outputSet -> this.blacklistedAsns = outputSet
                    ).thenAccept(timing ->
                            this.logger.info(" &8• &rLoaded " + this.blacklistedAsns.size() + " suspicious ASNs in " + timing.stop().getExecutingTime() + "ms!")
                    )
            );
        }

        if (this.needUpdate(this.proxyDataPath)) {
            this.logger.info(" &8• &rDownloading suspicious IPs from " + this.proxySources.size() + " sources...");
            futures.add(
                    this.downloadFromSources(
                            this.proxySources,
                            this.proxyDataPath,
                            IP_PATTERN,
                            str -> AddressUtils.isIpv4(str) ? AddressUtils.ipv4ToInt(str) : null,
                            outputSet -> this.blacklistedProxies = outputSet
                    ).thenAccept(timing ->
                            this.logger.info(" &8• &rDownloaded " + this.blacklistedProxies.size() + " suspicious IPs in " + timing.stop().getExecutingTime() + "ms!")
                    )
            );
        } else if (firstLoad) {
            this.logger.info(" &8• &rLoading suspicious IPs from " + this.proxyDataPath + "...");
            futures.add(
                    this.loadFromFile(
                            this.proxyDataPath,
                            outputSet -> this.blacklistedProxies = outputSet
                    ).thenAccept(timing ->
                            this.logger.info(" &8• &rLoaded " + this.blacklistedProxies.size() + " suspicious IPs in " + timing.stop().getExecutingTime() + "ms!")
                    )
            );
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    public CompletableFuture<TimingUtil> downloadFromSources(
            List<String> sources,
            Path outputPath,
            Pattern pattern,
            Function<String, Integer> parser,
            Consumer<Set<Integer>> consumer
    ) {
        Set<Integer> outputSet = new HashSet<>();
        TimingUtil timing = TimingUtil.startNew();
        return CompletableFuture.allOf(
                        sources.stream().map(source -> {
                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(source))
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0")
                                    .GET()
                                    .build();

                            return TaskManager.INSTANCE.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                                    .thenAccept(response -> {
                                        if (response.statusCode() != 200) {
                                            this.logger.warning(" &8• &eReceived " + response.statusCode() + " status code from source: &6" + source);
                                            return;
                                        }

                                        Set<Integer> localSet = new HashSet<>();

                                        Matcher matcher = pattern.matcher(response.body());
                                        while (matcher.find()) {
                                            Integer parsed = parser.apply(matcher.group());
                                            if (parsed != null) {
                                                localSet.add(parsed);
                                            }
                                        }

                                        synchronized (outputSet) {
                                            outputSet.addAll(localSet);
                                        }
                                    })
                                    .exceptionally(ex -> {
                                        this.logger.log(Level.WARNING, " &8• &cError while downloading data from " + source, ex);
                                        return null;
                                    });
                        }).toArray(CompletableFuture[]::new)
                )
                .thenApplyAsync(v -> {
                    byte[] serialized = SerializeUtils.serialize(outputSet);
                    try {
                        Files.write(outputPath, serialized);
                    } catch (IOException ex) {
                        this.logger.log(Level.SEVERE, " &8• &cFailed to write data to " + outputPath, ex);
                    }
                    consumer.accept(Collections.unmodifiableSet(outputSet));
                    return timing;
                }, TaskManager.INSTANCE.getAsyncExecutor());
    }

    public CompletableFuture<TimingUtil> loadFromFile(Path path, Consumer<Set<Integer>> consumer) {
        TimingUtil timing = TimingUtil.startNew();
        return CompletableFuture.supplyAsync(() -> {
            Set<Integer> outputSet = new HashSet<>();
            try {
                SerializeUtils.deserialize(Files.readAllBytes(path), outputSet);
            } catch (IOException ex) {
                this.logger.log(Level.SEVERE, " &8• &cFailed to read data from " + path, ex);
            }
            consumer.accept(Collections.unmodifiableSet(outputSet));
            return timing;
        }, TaskManager.INSTANCE.getAsyncExecutor());
    }
}