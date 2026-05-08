package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;
import xyz.lychee.gatekeeper.shared.util.SerializeUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsnFilterModule extends AbstractModule implements Runnable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Set<Integer> downloadedAsn = ConcurrentHashMap.newKeySet();
    private final Set<Integer> listedAsn = new HashSet<>();
    private final Set<String> sources = new HashSet<>();
    private final Path asnPath;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private Pattern pattern;
    private ScheduledFuture<?> task;
    private long interval;
    private boolean list_mode;

    public AsnFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AsnFilter");

        this.asnPath = this.getGatekeeper().dataFolder().toPath().resolve("asn_data.bin");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        return connection.getAsn() > 0 && (this.downloadedAsn.contains(connection.getAsn()) || this.listedAsn.contains(connection.getAsn()) == this.list_mode);
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
        this.downloadedAsn.clear();
        this.listedAsn.clear();
        this.sources.clear();

        if (this.task != null) {
            this.task.cancel(true);
        }

        if (Files.exists(this.asnPath)) {
            SerializeUtils.deserialize(Files.readAllBytes(this.asnPath), this.downloadedAsn);
            this.getGatekeeper().logger().info("Loaded "+this.downloadedAsn.size()+" suspicious ASNs from file.");
        }

        this.interval = this.getConfig().getInt("auto_update.interval") * 60L * 60L;
        this.pattern = Pattern.compile(this.getConfig().getString("auto_update.asn_pattern"));
        this.sources.addAll(this.getConfig().getStringList("auto_update.sources"));
        this.task = this.executor.scheduleAtFixedRate(this, 0L, interval, TimeUnit.SECONDS);

        this.listedAsn.addAll(this.getConfig().getIntList("list"));
        this.list_mode = this.getConfig().getBoolean("list_mode");

        return true;
    }

    @Override
    public void run() {
        if (Files.exists(this.asnPath)) {
            try {
                long lastModified = Files.getLastModifiedTime(this.asnPath).toMillis();
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
                                Set<Integer> asnSet = new HashSet<>();
                                Matcher matcher = this.pattern.matcher(response.body());
                                while (matcher.find()) {
                                    String group = matcher.group();
                                    if (RandomUtils.isInteger(group)) {
                                        asnSet.add(Integer.parseInt(group));
                                    }
                                }

                                this.downloadedAsn.addAll(asnSet);
                            }
                        }, delayed);
            });
        }

        chain.thenRun(() -> {
            byte[] serialized = SerializeUtils.serialize(this.downloadedAsn);
            try {
                Files.write(this.asnPath, serialized);
            } catch (IOException ignored) {}
            this.getGatekeeper().logger().info("Downloaded " + this.downloadedAsn.size() + " danger ASNs to database!");
        });
    }
}
