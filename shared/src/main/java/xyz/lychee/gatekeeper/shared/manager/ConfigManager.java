package xyz.lychee.gatekeeper.shared.manager;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;

import java.io.File;
import java.io.IOException;

@Getter
public class ConfigManager extends AbstractManager {
    public static final ConfigManager INSTANCE = new ConfigManager();
    private YamlDocument yaml;
    private Object prefix;

    @Override
    public boolean load(Gatekeeper<?> plugin) throws Exception {
        this.yaml = YamlDocument.create(
                new File(plugin.dataFolder(), "config.yml"),
                plugin.resource("config.yml"),
                GeneralSettings.DEFAULT,
                LoaderSettings.builder().setAutoUpdate(true).build(),
                DumperSettings.DEFAULT
        );
        this.yaml.save();

        this.updatePrefix(plugin);
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> plugin) throws IOException {
        this.yaml.reload();
        this.updatePrefix(plugin);
        return true;
    }

    private void updatePrefix(Gatekeeper<?> plugin) {
        String prefixString = this.yaml.getString("main.prefix");

        if (this.yaml.getBoolean("main.prefix_hover")) {
            this.prefix = plugin.language().hover(prefixString, prefixString+" &fGateKeeper &b" + plugin.platformData().getPluginVersion() + "\n &8{*} &7Click to open plugin in modrinth!");
        }
        else {
            this.prefix = plugin.language().color(prefixString, false);
        }
    }
}