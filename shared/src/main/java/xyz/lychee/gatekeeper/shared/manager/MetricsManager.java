package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.MetricsBase;

import java.io.IOException;
import java.util.UUID;

public class MetricsManager extends AbstractManager {
    public static final MetricsManager INSTANCE = new MetricsManager();
    private MetricsBase metricsBase;

    @Override
    public boolean load(Gatekeeper<?> plugin) {
        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();
        if (!yaml.getBoolean("enabled", true)) return true;

        String uuid;
        if (!yaml.contains("main.bStats.uuid")) {
            uuid = UUID.randomUUID().toString();
            yaml.set("main.bStats.uuid", uuid);
            try {
                yaml.save();
            } catch (IOException ignored) {}
        } else {
            uuid = yaml.getString("main.bStats.uuid");
        }

        this.metricsBase = new MetricsBase(
                "bukkit",
                uuid,
                27416,
                plugin
        );
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        if (this.metricsBase != null) {
            this.metricsBase.shutdown();
        }
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    /*public void addCustomChart(CustomChart chart) {
        metricsBase.addCustomChart(chart);
    }*/
}