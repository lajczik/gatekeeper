package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;

import java.io.File;

@Getter
public class ConfigManager {
    public static final ConfigManager INSTANCE = new ConfigManager();
    private Gatekeeper<?> gatekeeper;
    private YamlDocument yaml;
    private Object prefix;

    public void loadConfig(Gatekeeper<?> gatekeeper) {
        this.gatekeeper = gatekeeper;

        try {
            this.yaml = YamlDocument.create(
                    new File(this.gatekeeper.dataFolder(), "config.yml"),
                    this.gatekeeper.resource("config.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT
            );

            this.yaml.save();

            this.prefix = this.gatekeeper.language().color(this.yaml.getString("prefix"), false);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}