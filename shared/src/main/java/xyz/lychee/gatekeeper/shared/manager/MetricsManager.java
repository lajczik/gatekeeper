package xyz.lychee.gatekeeper.shared.manager;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import dev.dejvokep.boostedyaml.YamlDocument;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.PlatformData;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MetricsManager extends AbstractManager {
    public static final MetricsManager INSTANCE = new MetricsManager();
    private Gatekeeper<?> plugin;
    private String serverUuid;

    @Override
    public boolean load(Gatekeeper<?> plugin) {
        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();
        if (!yaml.getBoolean("enabled", true)) return true;

        this.plugin = plugin;

        if (!yaml.contains("main.bStats.uuid")) {
            this.serverUuid = UUID.randomUUID().toString();
            yaml.set("main.bStats.uuid", this.serverUuid);
            try {
                yaml.save();
            } catch (IOException ignored) {}
        } else {
            this.serverUuid = yaml.getString("main.bStats.uuid");
        }

        this.startSubmitting();
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    /*public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }*/

    //
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
        TaskManager.INSTANCE.getScheduler().schedule(this::submitData, initialDelay, TimeUnit.MILLISECONDS);
        TaskManager.INSTANCE.getScheduler().scheduleAtFixedRate(this::submitData, initialDelay + secondDelay, 1000 * 60 * 30, TimeUnit.MILLISECONDS);
    }

    private void submitData() {
        PlatformData platformData = this.plugin.platformData();

        JsonObject baseJson = new JsonObject();
        platformData.getConsumer().accept(baseJson);

        baseJson.put("javaVersion", System.getProperty("java.version"));
        baseJson.put("osName", System.getProperty("os.name"));
        baseJson.put("osArch", System.getProperty("os.arch"));
        baseJson.put("osVersion", System.getProperty("os.version"));
        baseJson.put("coreCount", Runtime.getRuntime().availableProcessors());

        JsonObject serviceJson = new JsonObject();
        serviceJson.put("id", platformData.getServiceId());
        serviceJson.put("pluginVersion", platformData.getPluginVersion());
        /*JsonArray chartsJson = new JsonArray();
        for (CustomChart chart : this.customCharts) {
            chartsJson.add(chart.getRequestJsonObject());
        }
        serviceJson.put("customCharts", chartsJson);*/

        baseJson.put("service", serviceJson);
        baseJson.put("serverUUID", this.serverUuid);
        baseJson.put("metricsVersion", "3.2.2-SNAPSHOT");

        sendData(baseJson);
    }

    private void sendData(JsonObject data) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://bStats.org/api/v2/data/" + this.plugin.platformData().getPlatform()))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Metrics-Service/1")
                .POST(HttpRequest.BodyPublishers.ofString(JsonWriter.string(data)))
                .build();

        TaskManager.INSTANCE.getHttpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}