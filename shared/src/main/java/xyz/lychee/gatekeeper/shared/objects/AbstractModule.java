package xyz.lychee.gatekeeper.shared.objects;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.util.TimingUtil;

import java.io.File;
import java.util.Collections;

@Setter
@Getter
public abstract class AbstractModule {
    private final Gatekeeper<?> gatekeeper;
    private final String name;
    private YamlDocument yamlDocument;
    private Section config;
    private boolean loaded = false;
    private int priority;
    private Object kickMessage;
    private @Nullable String logMessage;

    public AbstractModule(Gatekeeper<?> gatekeeper, String name) {
        this.gatekeeper = gatekeeper;
        this.name = name;
    }

    public abstract boolean handlePreLogin(GeoConnection connection);

    public abstract boolean handlePostLogin(GeoConnection connection);

    public abstract boolean handleDisconnect(GeoConnection connection);

    public void printCheck(GeoConnection connection, TimingUtil timer) {
        if (this.logMessage != null) {
            this.gatekeeper.logger().info(
                    this.logMessage
                            .replace("%name%", connection.getName())
                            .replace("%address%", connection.getAddress().getHostAddress())
                            .replace("%country%", connection.getCountry())
                            .replace("%asn%", Integer.toString(connection.getAsn()))
                            .replace("%time%", Long.toString(timer.stop().getExecutingTime()))
            );
        }
    }

    public boolean loadAllConfig() throws Exception {
        String resourcePath = "modules/" + this.name + ".yml";

        File configFile = new File(this.gatekeeper.dataFolder(), resourcePath);

        this.yamlDocument = YamlDocument.create(
                configFile,
                this.gatekeeper.resource(resourcePath),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT
        );
        this.yamlDocument.save();

        if (!this.yamlDocument.getBoolean("enabled")) {
            return false;
        }

        this.config = this.yamlDocument.getSection("values");

        this.priority = this.yamlDocument.getInt("priority");

        String log = this.yamlDocument.getString("log", "");
        this.logMessage = log.isEmpty() ? null : log;

        String kick = String.join("\n", this.yamlDocument.getStringList("kick", Collections.emptyList()));
        this.kickMessage = this.gatekeeper.language().color(kick, true);

        return this.load();
    }

    public abstract boolean load() throws Exception;

    public abstract boolean unload() throws Exception;
}
