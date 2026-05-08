package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.SerializeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IpFilterModule extends AbstractModule implements Runnable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Set<Integer> downloadedIps = ConcurrentHashMap.newKeySet();
    private final Set<Integer> listedIps = new HashSet<>();
    private final List<String> sources = new ArrayList<>();
    private final Path ipsPath;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(this.executor)
            .build();
    private Pattern pattern;
    private ScheduledFuture<?> task;
    private long interval;
    private boolean list_mode;

    public IpFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "IpFilter");

        this.ipsPath = this.getGatekeeper().dataFolder().toPath().resolve("proxies_data.bin");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return this.downloadedIps.contains(connection.getAddressData())
                || this.listedIps.contains(connection.getAddressData()) == this.list_mode;
    }

    @Override
    public boolean handlePostLogin(GeoConnection connection) {
        return false;
    }

    @Override
    public boolean handleDisconnect(GeoConnection connection) {
        return false;
    }

    @Override
    public boolean load() throws IOException {
        this.downloadedIps.clear();
        this.listedIps.clear();
        this.sources.clear();

        if (this.task != null) {
            this.task.cancel(true);
        }

        if (Files.exists(this.ipsPath)) {
            SerializeUtils.deserialize(Files.readAllBytes(this.ipsPath), this.downloadedIps);
            this.getGatekeeper().logger().info("Loaded "+this.downloadedIps.size()+" proxy IPs from file.");
        }

        this.interval = this.getConfig().getInt("auto_update.interval") * 60L * 60L;
        this.pattern = Pattern.compile(this.getConfig().getString("auto_update.asn_pattern"));
        this.sources.addAll(this.getConfig().getStringList("auto_update.sources"));
        Collections.shuffle(this.sources);

        this.task = this.executor.scheduleAtFixedRate(this, 0L, this.interval, TimeUnit.SECONDS);

        for (String address : this.getConfig().getStringList("list")) {
            this.listedIps.add(AddressUtils.ipv4ToInt(address));
        }
        this.list_mode = this.getConfig().getBoolean("list_mode");

        return true;
    }

    @Override
    public void run() {
        if (Files.exists(this.ipsPath)) {
            try {
                long lastModified = Files.getLastModifiedTime(this.ipsPath).toMillis();
                if (System.currentTimeMillis() - lastModified < this.interval * 1000L) {
                    return;
                }
            } catch (IOException ignored) {}
        }

        Executor delayed = CompletableFuture.delayedExecutor(1, TimeUnit.SECONDS, this.executor);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (String source : this.sources) {
            chain = chain.thenCompose(v -> {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(source))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:110.0) Gecko/20100101 Firefox/110.0")
                        .GET()
                        .build();

                CompletableFuture<HttpResponse<String>> responseFuture =
                        this.httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());

                return responseFuture
                        .exceptionally(t -> null)
                        .thenAcceptAsync(response -> {
                            if (response != null && response.statusCode() == 200) {
                                Set<Integer> ips = new HashSet<>();
                                Matcher matcher = this.pattern.matcher(response.body());
                                while (matcher.find()) {
                                    ips.add(
                                            AddressUtils.ipv4ToInt(
                                                    matcher.group()
                                            )
                                    );
                                }
                                this.downloadedIps.addAll(ips);
                            }
                        }, delayed);
            });
        }

        chain.thenRun(() -> {
            byte[] serialized = SerializeUtils.serialize(this.downloadedIps);
            try {
                Files.write(this.ipsPath, serialized);
            } catch (IOException ignored) {}
            this.getGatekeeper().logger().info("Downloaded " + this.downloadedIps.size() + " proxy IPs to database!");
        });
    }
}
