package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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

        this.ipsPath = this.getGatekeeper().dataFolder().toPath().resolve("ips.txt");
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
            Files.readAllLines(this.ipsPath).forEach(line ->
                    this.downloadedIps.add(Integer.parseInt(line))
            );
        }

        this.interval = this.getConfig().getInt("auto_update.interval") * 60L * 60L;
        this.pattern = Pattern.compile(this.getConfig().getString("auto_update.asn_pattern"));
        this.sources.addAll(this.getConfig().getStringList("auto_update.sources"));
        Collections.shuffle(this.sources);

        this.task = this.executor.scheduleAtFixedRate(this, 10L, this.interval, TimeUnit.SECONDS);

        for (String address : this.getConfig().getStringList("list")) {
            this.listedIps.add(AddressUtils.addressToInteger(address));
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
                                            AddressUtils.addressToInteger(
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
            try {
                StringBuilder lines = new StringBuilder();
                for (int ip : this.downloadedIps) {
                    lines.append(ip).append('\n');
                }
                Files.writeString(this.ipsPath, lines, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
            this.getGatekeeper().logger().info("Downloaded " + this.downloadedIps.size() + " proxy ips to database!");
        });
    }
}
