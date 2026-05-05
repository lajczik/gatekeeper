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

import java.io.File;
import java.net.InetAddress;
import java.util.Collections;

@Setter
@Getter
public abstract class AbstractModule {
    private final Gatekeeper<?> gatekeeper;
    private final String name;
    private Section config;
    private boolean loaded = false;
    private int priority;
    private Object kickMessage;
    private @Nullable String logMessage;

    public AbstractModule(Gatekeeper<?> gatekeeper, String name) {
        this.gatekeeper = gatekeeper;
        this.name = name;
    }

    public abstract boolean handlePreLogin(InetAddress address, String name, int dataAddress);

    public abstract boolean handlePostLogin(InetAddress address, String name, int dataAddress);

    public abstract boolean handleDisconnect(InetAddress address, String name, int dataAddress);

    public void printCheck(InetAddress address, String name) {
        if (this.logMessage != null) {
            this.gatekeeper.logger().info(
                    this.logMessage
                            .replace("%name%", name)
                            .replace("%address%", address.getHostAddress())
            );
        }
    }

    public boolean loadAllConfig() throws Exception {
        String resourcePath = "modules/" + this.name + ".yml";

        File configFile = new File(this.gatekeeper.dataFolder(), resourcePath);

        YamlDocument yaml = YamlDocument.create(
                configFile,
                this.gatekeeper.resource(resourcePath),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT
        );
        yaml.save();

        if (!yaml.getBoolean("enabled")) {
            return false;
        }

        this.config = yaml.getSection("values");

        this.priority = yaml.getInt("priority");

        String log = yaml.getString("log", "");
        this.logMessage = log.isEmpty() ? null : log;

        String kick = String.join("\n", yaml.getStringList("kick", Collections.emptyList()));
        this.kickMessage = this.gatekeeper.language().color(kick, true);

        return this.load();
    }

    public abstract boolean load() throws Exception;
}
