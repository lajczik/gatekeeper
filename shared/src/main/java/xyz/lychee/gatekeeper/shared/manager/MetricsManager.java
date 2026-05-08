package xyz.lychee.gatekeeper.shared.manager;

import com.grack.nanojson.JsonObject;
import dev.dejvokep.boostedyaml.YamlDocument;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.charts.CustomChart;
import xyz.lychee.gatekeeper.shared.util.MetricsBase;

import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

public class MetricsManager {
    public static final MetricsManager INSTANCE = new MetricsManager();
    private MetricsBase metricsBase;

    public void loadMetrics(Gatekeeper<?> plugin, Consumer<JsonObject> platformDataConsumer) {
        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();
        if (!yaml.getBoolean("enabled", true)) return;

        String uuid;
        if (!yaml.contains("bStats.uuid")) {
            uuid = UUID.randomUUID().toString();
            yaml.set("bStats.uuid", uuid);
            try {
                yaml.save();
            } catch (IOException ignored) {}
        } else {
            uuid = yaml.getString("bStats.uuid");
        }

        this.metricsBase = new MetricsBase(
                "bukkit",
                uuid,
                27416,
                plugin.version(),
                platformDataConsumer
        );
    }

    public void shutdown() {
        metricsBase.shutdown();
    }

    public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }
}