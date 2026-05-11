package xyz.lychee.gatekeeper.shared.modules;

import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.manager.TaskManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.ConditionSet;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class AntiVpnModule extends AbstractModule {
    private final Map<Integer, Boolean> checked = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<Boolean>> pendingFutures = new ConcurrentHashMap<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Set<String> whitelist = new HashSet<>();
    private final List<ConditionSet.Provider> providers = new ArrayList<>();
    private HttpClient httpClient;
    private Semaphore semaphore;
    private int connect_timeout;
    private int read_timeout;
    private boolean blacklist_asn;

    public AntiVpnModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AntiVpn");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        if (this.providers.isEmpty()) {
            return false;
        }

        int id = this.blacklist_asn && connection.getAsn() > 0 ? connection.getAsn() : connection.getAddressData();

        Boolean cached = this.checked.get(id);
        if (cached != null) {
            return cached;
        }

        String ip = connection.getAddress().getHostAddress();
        if (this.whitelist.contains(ip)) {
            this.checked.put(id, Boolean.FALSE);
            return false;
        }

        int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % this.providers.size());
        ConditionSet.Provider provider = this.providers.get(index);

        String urlStr = provider.getUrl().replace("%address%", ip);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofMillis(this.connect_timeout + this.read_timeout))
                .GET();

        provider.getHeaders().forEach(requestBuilder::header);

        CompletableFuture<Boolean> pendingFuture = this.pendingFutures.get(id);
        if (pendingFuture != null) {
            return pendingFuture.join();
        }

        if (this.semaphore != null) {
            try {
                this.semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getGatekeeper().logger().log(Level.WARNING, "Interrupted while waiting for VPN check permit", e);
                return false;
            }
        }

        try {
            CompletableFuture<Boolean> future = this.httpClient.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream())
                    .thenApply(response -> {
                        this.pendingFutures.remove(id);
                        int statusCode = response.statusCode();
                        if (statusCode == 200) {
                            try (InputStream is = response.body()) {
                                boolean detected = provider.matches(JsonParser.object().from(is));
                                this.checked.put(id, detected);
                                if (detected && this.blacklist_asn) {
                                    GeoipManager.INSTANCE.getBlacklistedAsns().add(connection.getAsn());
                                } else {
                                    GeoipManager.INSTANCE.getBlacklistedProxies().add(connection.getAddressData());
                                }
                                return detected;
                            } catch (IOException | JsonParserException ignored) {}
                        }
                        return false;
                    })
                    .exceptionally(t -> {
                        this.pendingFutures.remove(id);
                        return false;
                    });

            this.pendingFutures.put(id, future);
            return future.join();
        } finally {
            if (this.semaphore != null) {
                this.semaphore.release();
            }
        }
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
        this.whitelist.clear();
        this.providers.clear();

        if (this.httpClient != null) {
            this.httpClient.close();
        }

        this.connect_timeout = this.getConfig().getInt("connect_timeout");
        this.read_timeout = this.getConfig().getInt("read_timeout");
        this.blacklist_asn = this.getConfig().getBoolean("blacklist_asn");

        int max_concurrent_checks = this.getConfig().getInt("max_concurrent_checks");
        this.semaphore = max_concurrent_checks > 0 ? new Semaphore(max_concurrent_checks) : null;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.connect_timeout))
                .executor(TaskManager.INSTANCE.getCallbackExecutor())
                .build();

        this.whitelist.addAll(this.getConfig().getStringList("whitelist"));

        for (Object key : this.getConfig().getSection("checks").getKeys()) {
            Section section = this.getConfig().getSection("checks." + key);
            if (section.getBoolean("enabled", false)) {
                String condStr = section.getString("condition", null);
                String url = section.getString("url", "");

                Map<String, String> headers = section.getStringList("headers", Collections.emptyList()).stream()
                        .map(h -> h.split(":", 2))
                        .filter(parts -> parts.length == 2)
                        .collect(Collectors.toMap(
                                parts -> parts[0].trim(),
                                parts -> parts[1].trim(),
                                (existing, replacement) -> replacement
                        ));

                ConditionSet cs = condStr != null ? ConditionSet.compile(condStr) : null;
                this.providers.add(new ConditionSet.Provider(Objects.toString(key), url, headers, cs));
            }
        }
        Collections.shuffle(this.providers, RandomUtils.RANDOM);

        return true;
    }


    @Override
    public boolean unload() {
        if (this.httpClient != null) {
            this.httpClient.close();
        }
        return true;
    }
}