package xyz.lychee.gatekeeper.shared.objects;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.charts.CustomChart;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MetricsBase {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            task -> {
                Thread thread = new Thread(task, "bStats-Metrics");
                thread.setDaemon(true);
                return thread;
            });

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(this.scheduler)
            .build();

    private final String platform;
    private final String serverUuid;
    private final int serviceId;
    private final Gatekeeper<?> plugin;

    private final Set<CustomChart> customCharts = new HashSet<>();

    public MetricsBase(
            String platform,
            String serverUuid,
            int serviceId,
            Gatekeeper<?> plugin) {
        this.platform = platform;
        this.serverUuid = serverUuid;
        this.serviceId = serviceId;
        this.plugin = plugin;

        startSubmitting();
    }

    public void addCustomChart(CustomChart chart) {
        this.customCharts.add(chart);
    }

    public void shutdown() {
        scheduler.shutdown();
        httpClient.close();
    }

    public void startSubmitting() {
        // Many servers tend to restart at a fixed time at xx:00 which causes an uneven
        // distribution of requests on the
        // bStats backend. To circumvent this problem, we introduce some randomness into
        // the initial and second delay.
        // WARNING: You must not modify and part of this Metrics class, including the
        // submit delay or frequency!
        // WARNING: Modifying this code will get your plugin banned on bStats. Just
        // don't do it!
        long initialDelay = 1000 * 60 * (3 + RandomUtils.RANDOM.nextInt(3));
        long secondDelay = 1000 * 60 * RandomUtils.RANDOM.nextInt(30);
        scheduler.schedule(this::submitData, initialDelay, TimeUnit.MILLISECONDS);
        scheduler.scheduleAtFixedRate(this::submitData, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
    }

    private void submitData() {
        PlatformData platformData = this.plugin.platformData();

        JsonObject baseJson = new JsonObject();
        baseJson.put("playerAmount", platformData.getPlayers());
        baseJson.put("onlineMode", platformData.isOnlineMode() ? 1 : 0);
        baseJson.put("bukkitVersion", platformData.getVersion());
        baseJson.put("bukkitName", platformData.getName());

        baseJson.put("javaVersion", System.getProperty("java.version"));
        baseJson.put("osName", System.getProperty("os.name"));
        baseJson.put("osArch", System.getProperty("os.arch"));
        baseJson.put("osVersion", System.getProperty("os.version"));
        baseJson.put("coreCount", Runtime.getRuntime().availableProcessors());

        JsonObject serviceJson = new JsonObject();
        serviceJson.put("id", this.serviceId);
        serviceJson.put("pluginVersion", platformData.getPluginVersion());
        JsonArray chartsJson = new JsonArray();
        for (CustomChart chart : this.customCharts) {
            chartsJson.add(chart.getRequestJsonObject());
        }
        serviceJson.put("customCharts", chartsJson);

        baseJson.put("service", serviceJson);
        baseJson.put("serverUUID", this.serverUuid);
        baseJson.put("metricsVersion", "3.2.2-SNAPSHOT");

        sendData(baseJson);
    }

    private void sendData(JsonObject data) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://bStats.org/api/v2/data/" + this.platform))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Metrics-Service/1")
                .POST(HttpRequest.BodyPublishers.ofString(JsonWriter.string(data)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}