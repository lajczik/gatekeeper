package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.BinaryGeoIPBuilder;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
public class GeoipManager extends AbstractManager implements Runnable {
    public static final GeoipManager INSTANCE = new GeoipManager();
    private static final Pattern ASN_PATTERN = Pattern.compile("\\d{4,6}");
    private static final Pattern IP_PATTERN = Pattern.compile("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
    private final Set<Integer> blacklistedAsns = ConcurrentHashMap.newKeySet();
    private final Set<Integer> blacklistedProxies = ConcurrentHashMap.newKeySet();
    private final List<String> asnSource = new ArrayList<>();
    private final List<String> proxySources = new ArrayList<>();
    private final BinaryGeoIPDatabase database = new BinaryGeoIPDatabase();
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

        this.download();

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
        }
        catch (IOException ignored) {
            return true;
        }
    }

    @Override
    public void run() {
        this.download();
    }

    public void download() {
        if (this.needUpdate(this.geoDataPath)) {
            BinaryGeoIPBuilder.buildDatabase(this.logger, this.geoDataPath)
                    .thenCompose(v -> this.database.load(this.logger, this.geoDataPath));
        }
        else {
            this.database.load(this.logger, this.geoDataPath);
        }

        this.downloadFromSource(
                " &8• &rDownloading suspicious ASNs from " + this.asnSource.size() + " sources...",
                " &8• &rDownloaded %amount% suspicious ASNs in %time%ms!",
                " &8• &rLoaded %amount% suspicious ASNs in %time%ms!",
                this.asnSource,
                this.blacklistedAsns,
                this.asnDataPath,
                ASN_PATTERN,
                str -> RandomUtils.isInteger(str) ? Integer.parseInt(str) : null
        );

        this.downloadFromSource(
                " &8• &rDownloading suspicious IPs from " + this.proxySources.size() + " sources...",
                " &8• &rDownloaded %amount% suspicious IPs in %time%ms!",
                " &8• &rLoaded %amount% suspicious IPs in %time%ms!",
                this.proxySources,
                this.blacklistedProxies,
                this.proxyDataPath,
                IP_PATTERN,
                str -> AddressUtils.isIpv4(str) ? AddressUtils.ipv4ToInt(str) : null
        );
    }

    public void downloadFromSource(
            String downloadingLog,
            String downloadedLog,
            String loadedLog,
            List<String> sources,
            Set<Integer> outputSet,
            Path outputPath,
            Pattern pattern,
            Function<String, Integer> parser
    ) {
        TimingUtil t = TimingUtil.startNew();
        if (this.needUpdate(outputPath)) {
            this.logger.info(downloadingLog);

            CompletableFuture<Void> future = CompletableFuture.completedFuture(null);

            Executor delayed = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, TaskManager.INSTANCE.getCallbackExecutor());

            for (String source : sources) {
                future = future.thenCompose(v -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(source))
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0")
                            .GET()
                            .build();

                    CompletableFuture<HttpResponse<String>> responseFuture =
                            TaskManager.INSTANCE.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString());

                    return responseFuture
                            .exceptionally(err -> null)
                            .thenAcceptAsync(response -> {
                                if (response != null && response.statusCode() == 200) {
                                    Matcher matcher = pattern.matcher(response.body());
                                    while (matcher.find()) {
                                        String group = matcher.group();
                                        Integer parsed = parser.apply(group);
                                        if (parsed != null) {
                                            outputSet.add(parsed);
                                        }
                                    }
                                }
                            }, delayed);
                });
            }

            future.thenRun(() -> {
                byte[] serialized = SerializeUtils.serialize(outputSet);
                try {
                    Files.write(outputPath, serialized);
                } catch (IOException ignored) {}

                this.logger.info(
                        downloadedLog
                                .replace("%amount%", Integer.toString(outputSet.size()))
                                .replace("%time%", Long.toString(t.stop().getExecutingTime()))
                );
            });
        } else {
            try {
                SerializeUtils.deserialize(Files.readAllBytes(outputPath), outputSet);

                this.logger.info(
                        loadedLog
                                .replace("%amount%", Integer.toString(outputSet.size()))
                                .replace("%time%", Long.toString(t.stop().getExecutingTime()))
                );
            }
            catch (IOException ignored) {}
        }
    }
}