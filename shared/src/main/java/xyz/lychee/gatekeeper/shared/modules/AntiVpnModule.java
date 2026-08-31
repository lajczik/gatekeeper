package xyz.lychee.gatekeeper.shared.modules;

import dev.dejvokep.boostedyaml.block.implementation.Section;
import it.unimi.dsi.fastutil.ints.*;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.TaskManager;
import xyz.lychee.gatekeeper.shared.objects.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class AntiVpnModule extends AbstractModule {
    private final Int2BooleanMap checked = Int2BooleanMaps.synchronize(new Int2BooleanOpenHashMap());
    private final Int2ObjectMap<CompletableFuture<Boolean>> pendingFutures = Int2ObjectMaps.synchronize(new Int2ObjectOpenHashMap<>());
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private Provider[] providers = new Provider[0];
    private Semaphore semaphore;
    private int timeout;
    private int checks_per_player;
    private int block_threshold;
    private boolean blacklist_asn;
    private long rate_limit_cooldown;

    public AntiVpnModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AntiVpn");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        if (connection.isLocalhost()) {
            return false;
        }

        final Provider[] currentProviders = this.providers;
        final int totalProviders = currentProviders.length;
        if (totalProviders == 0 || this.checks_per_player <= 0) {
            return false;
        }

        final int id = this.blacklist_asn && connection.getAsn() > 0 ? connection.getAsn() : connection.getAddressData();

        if (this.checked.containsKey(id)) {
            return this.checked.get(id);
        }

        CompletableFuture<Boolean> newFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> inFlight = this.pendingFutures.putIfAbsent(id, newFuture);

        if (inFlight != null) {
            return inFlight.join();
        }

        boolean permitAcquired = false;
        if (this.semaphore != null) {
            try {
                this.semaphore.acquire();
                permitAcquired = true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                getGatekeeper().logger().log(Level.WARNING, "Interrupted while waiting for VPN check permit", e);
                this.pendingFutures.remove(id);
                newFuture.complete(false);
                return false;
            }
        }

        try {
            final int countToTake = Math.min(this.checks_per_player, totalProviders);
            final int startIdx = this.roundRobinIndex.getAndUpdate(i -> (i + countToTake) % totalProviders);
            final long now = System.currentTimeMillis();

            List<Provider> selected = new ArrayList<>(countToTake);
            for (int i = 0; i < totalProviders && selected.size() < countToTake; i++) {
                Provider provider = currentProviders[(startIdx + i) % totalProviders];
                if (!provider.isRateLimited(now)) {
                    selected.add(provider);
                }
            }

            if (selected.isEmpty()) {
                getGatekeeper().logger().log(Level.WARNING, "All Anti-VPN providers are currently rate-limited!");
                this.pendingFutures.remove(id);
                newFuture.complete(false);
                return false;
            }

            final String address = connection.getAddress().getHostAddress();
            final int selectedSize = selected.size();

            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean>[] checkFutures = new CompletableFuture[selectedSize];
            for (int i = 0; i < selectedSize; i++) {
                checkFutures[i] = this.performSingleCheck(selected.get(i), address);
            }

            CompletableFuture.allOf(checkFutures)
                    .handle((v, ex) -> {
                        int positiveCount = 0;
                        for (CompletableFuture<Boolean> checkFuture : checkFutures) {
                            if (checkFuture.join()) {
                                positiveCount++;
                            }
                        }
                        boolean blocked = positiveCount >= this.block_threshold;

                        this.checked.put(id, blocked);
                        this.pendingFutures.remove(id);
                        newFuture.complete(blocked);
                        return blocked;
                    });

            return newFuture.join();
        } catch (Exception e) {
            this.pendingFutures.remove(id);
            newFuture.complete(false);
            throw e;
        } finally {
            if (permitAcquired && this.semaphore != null) {
                this.semaphore.release();
            }
        }
    }

    private CompletableFuture<Boolean> performSingleCheck(Provider provider, String address) {
        String urlStr = provider.buildUrl(address);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .timeout(Duration.ofMillis(this.timeout))
                .GET();

        String[] headers = provider.headerPairs;
        for (int i = 0; i < headers.length; i += 2) {
            requestBuilder.header(headers[i], headers[i + 1]);
        }

        return TaskManager.INSTANCE.getHttpClient()
                .sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();

                    if (statusCode == 200) {
                        return provider.matches(response.body());
                    }

                    if (statusCode == 429) {
                        provider.applyRateLimit(this.rate_limit_cooldown);
                        getGatekeeper().logger()
                                .warning("Provider '" + provider.name + "' (ID: " + provider.id + ") hit rate-limit (HTTP 429). Cooldown: " + this.rate_limit_cooldown + "ms");
                    }

                    return false;
                })
                .exceptionally(t -> {
                    getGatekeeper().logger().log(Level.FINE, "VPN check failed for " + provider.name, t);
                    return false;
                });
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
        this.checks_per_player = this.getConfig().getInt("checks_per_player");
        this.block_threshold = this.getConfig().getInt("block_threshold");
        this.rate_limit_cooldown = this.getConfig().getLong("rate_limit_cooldown") * 1000L;

        boolean needSave = false;
        List<RawProviderData> rawList = new ArrayList<>();

        for (Object key : this.getConfig().getSection("checks").getKeys()) {
            Section section = this.getConfig().getSection("checks." + key);
            String url = section.getString("url");
            if (url == null || url.isBlank() || !section.getBoolean("enabled")) continue;

            if (section.isString("condition")) {
                section.set("condition.json", section.getString("condition"));
                needSave = true;
            }

            List<String> headers = section.getStringList("headers", Collections.emptyList());

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
                rawList.add(new RawProviderData(Objects.toString(key), url, headers, conditionSet));
            }
        }

        Collections.shuffle(rawList);

        this.providers = new Provider[rawList.size()];
        for (int i = 0; i < rawList.size(); i++) {
            RawProviderData raw = rawList.get(i);
            this.providers[i] = new Provider(i, raw.name, raw.url, raw.headers, raw.condition);
        }

        if (needSave) {
            this.getYamlDocument().save();
        }

        return true;
    }

    @Override
    public boolean unload() {
        this.providers = new Provider[0];
        return true;
    }

    private static final class RawProviderData {
        private final String name;
        private final String url;
        private final List<String> headers;
        private final AbstractConditionSet condition;

        public RawProviderData(String name, String url, List<String> headers, AbstractConditionSet condition) {
            this.name = name;
            this.url = url;
            this.headers = headers;
            this.condition = condition;
        }
    }

    public static final class Provider {
        private final int id;
        private final String name;
        private final String urlPrefix;
        private final String urlSuffix;
        private final String[] headerPairs;
        private final AbstractConditionSet condition;
        private volatile long rateLimitedUntil = 0L;

        public Provider(int id, String name, String url, List<String> headers, AbstractConditionSet condition) {
            this.id = id;
            this.name = name;
            this.condition = condition;

            int placeholderIdx = url.indexOf("%address%");
            if (placeholderIdx != -1) {
                this.urlPrefix = url.substring(0, placeholderIdx);
                this.urlSuffix = url.substring(placeholderIdx + "%address%".length());
            } else {
                this.urlPrefix = url;
                this.urlSuffix = "";
            }

            this.headerPairs = new String[headers.size() * 2];
            int idx = 0;
            for (String header : headers) {
                int colonIdx = header.indexOf(':');
                if (colonIdx != -1) {
                    this.headerPairs[idx++] = header.substring(0, colonIdx).trim();
                    this.headerPairs[idx++] = header.substring(colonIdx + 1).trim();
                }
            }
        }

        public String buildUrl(String address) {
            return this.urlPrefix + address + this.urlSuffix;
        }

        public boolean isRateLimited(long now) {
            return now < this.rateLimitedUntil;
        }

        public void applyRateLimit(long cooldownMillis) {
            this.rateLimitedUntil = System.currentTimeMillis() + cooldownMillis;
        }

        public boolean matches(String str) {
            return this.condition.evaluate(str);
        }
    }
}