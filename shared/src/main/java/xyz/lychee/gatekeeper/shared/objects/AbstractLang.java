package xyz.lychee.gatekeeper.shared.objects;

import dev.dejvokep.boostedyaml.YamlDocument;
import dev.dejvokep.boostedyaml.block.implementation.Section;
import dev.dejvokep.boostedyaml.settings.dumper.DumperSettings;
import dev.dejvokep.boostedyaml.settings.general.GeneralSettings;
import dev.dejvokep.boostedyaml.settings.loader.LoaderSettings;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import xyz.lychee.gatekeeper.shared.Gatekeeper;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Getter
public abstract class AbstractLang<T> {
    private final Gatekeeper<T> gatekeeper;
    private final HashMap<String, T> components = new HashMap<>();
    private YamlDocument yaml;

    public AbstractLang(Gatekeeper<T> gatekeeper) {
        this.gatekeeper = gatekeeper;
    }

    public void loadLanguage() {
        try {
            this.yaml = YamlDocument.create(
                    new File(this.gatekeeper.dataFolder(), "lang.yml"),
                    this.gatekeeper.resource("lang.yml"),
                    GeneralSettings.DEFAULT,
                    LoaderSettings.builder().setAutoUpdate(true).build(),
                    DumperSettings.DEFAULT
            );

            this.yaml.save();

            Set<String> keys = new HashSet<>();
            collect(this.yaml, "", keys);
            for (String key : keys) {
                String text = this.yaml.isList(key) ? String.join("\n", this.yaml.getStringList(key)) : this.yaml.getString(key);
                this.components.put(key, this.color(text, true));
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void collect(Section section, String prefix, Collection<String> out) {
        for (Object keyObj : section.getKeys()) {
            String key = keyObj.toString();
            String route = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isSection(key)) {
                Section child = section.getSection(key);
                collect(child, route, out);
            } else {
                out.add(route);
            }
        }
    }

    public abstract T color(String text, boolean prefix);

    public T message(String key, String... placeholders) {
        if (placeholders != null && placeholders.length > 0) {
            String message = this.getString(key);
            if (message == null) return null;

            for (String placeholder : placeholders) {
                message = message.replaceFirst("\\{}", placeholder);
            }

            return this.color(message, true);
        }

        return this.components.get(key);
    }

    public @Nullable T getComponent(String key) {
        return this.components.get(key);
    }

    public @Nullable String getString(String key) {
        return this.yaml.getString(key);
    }
}