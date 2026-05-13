package xyz.lychee.gatekeeper.shared.modules;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.manager.TaskManager;
import xyz.lychee.gatekeeper.shared.objects.*;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.IOException;
import java.net.URI;
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
    private final List<Provider> providers = new ArrayList<>();
    private Semaphore semaphore;
    private int timeout;
    private boolean blacklist_asn;
    private boolean whitelist_localhost;

    public AntiVpnModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AntiVpn");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        if (this.whitelist_localhost && connection.isLocalhost()) {
            return false;
        }

        if (this.providers.isEmpty()) {
            return false;
        }

        int id = this.blacklist_asn && connection.getAsn() > 0 ? connection.getAsn() : connection.getAddressData();

        Boolean cached = this.checked.get(id);
        if (cached != null) {
            return cached;
        }

        int index = roundRobinIndex.getAndUpdate(i -> (i + 1) % this.providers.size());
        Provider provider = this.providers.get(index);

        String urlStr = provider.getUrl().replace("%address%", connection.getAddress().getHostAddress());

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofMillis(this.timeout))
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
            CompletableFuture<Boolean> future = TaskManager.INSTANCE.getHttpClient().sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        this.pendingFutures.remove(id);
                        int statusCode = response.statusCode();
                        if (statusCode == 200) {
                            boolean detected = provider.matches(response.body());
                            this.checked.put(id, detected);
                            if (detected && this.blacklist_asn) {
                                GeoipManager.INSTANCE.getBlacklistedAsns().add(connection.getAsn());
                            } else {
                                GeoipManager.INSTANCE.getBlacklistedProxies().add(connection.getAddressData());
                            }
                            return detected;
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
        this.timeout = this.getConfig().getInt("timeout");
        this.blacklist_asn = this.getConfig().getBoolean("blacklist_asn");

        int max_concurrent_checks = this.getConfig().getInt("max_concurrent_checks");
        this.semaphore = max_concurrent_checks > 0 ? new Semaphore(max_concurrent_checks) : null;

        this.whitelist_localhost = this.getConfig().getBoolean("whitelist_localhost");

        boolean needSave = false;

        for (Object key : this.getConfig().getSection("checks").getKeys()) {
            Section section = this.getConfig().getSection("checks." + key);
            String url = section.getString("url");
            if (url == null || url.isBlank() || !section.getBoolean("enabled")) continue;

            if (section.isString("condition")) {
                section.set("condition.json", section.getString("condition"));
                needSave = true;
            }

            Map<String, String> headers = section.getStringList("headers", Collections.emptyList()).stream()
                    .map(h -> h.split(":", 2))
                    .filter(parts -> parts.length == 2)
                    .collect(Collectors.toMap(
                            parts -> parts[0].trim(),
                            parts -> parts[1].trim(),
                            (existing, replacement) -> replacement
                    ));

            AbstractConditionSet conditionSet = null;
            if (section.contains("condition.json")) {
                String conditionJson = section.getString("condition.json");
                if (conditionJson != null) {
                    conditionSet = JsonConditionSet.compile(conditionJson);
                }
            } else if (section.contains("condition.text")) {
                String conditionText = section.getString("condition.text");
                if (conditionText != null) {
                    conditionSet = TextConditionSet.compile(conditionText);
                }
            }

            if (conditionSet != null) {
                this.providers.add(new Provider(Objects.toString(key), url, headers, conditionSet));
            }
        }

        Collections.shuffle(this.providers, RandomUtils.RANDOM);

        if (needSave) {
            this.getYamlDocument().save();
        }

        return true;
    }

    @Override
    public boolean unload() {
        this.providers.clear();
        return true;
    }

    @Getter
    public static final class Provider {
        private final String name;
        private final String url;
        private final Map<String, String> headers;
        private final AbstractConditionSet condition;

        public Provider(String name, String url, Map<String, String> headers, AbstractConditionSet condition) {
            this.name = name;
            this.url = url;
            this.headers = headers;
            this.condition = condition;
        }

        public boolean matches(String str) {
            return condition.evaluate(str);
        }
    }
}