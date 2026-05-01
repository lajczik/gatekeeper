package xyz.lychee.gatekeeper.shared.manager;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;
import com.grack.nanojson.JsonParserException;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;

@Getter
public class UpdaterManager implements Runnable {
    public static final UpdaterManager INSTANCE = new UpdaterManager();
    private final VersionComparator comparator = new VersionComparator();
    private int compared = 0;
    private int difference = 0;
    private int behind = -1;
    private String latestVersion = "";
    private String currentVersion = "";
    private boolean updater;
    private Gatekeeper<?> plugin;

    public void load(Gatekeeper<?> plugin) throws IOException {
        this.updater = ConfigManager.INSTANCE.getYaml().getBoolean("updater");
        this.currentVersion = plugin.version().trim();
        this.plugin = plugin;
    }

    private int calculateBuildsBehind(List<String> versions) {
        if (this.currentVersion.equals(this.latestVersion)) {
            return 0;
        }

        int currentIndex = -1;
        int latestIndex = -1;

        for (int i = 0; i < versions.size(); i++) {
            String version = versions.get(i);
            if (version.equals(this.currentVersion)) {
                currentIndex = i;
            }
            if (version.equals(this.latestVersion)) {
                latestIndex = i;
            }
        }

        if (currentIndex != -1 && latestIndex != -1) {
            return currentIndex - latestIndex;
        }

        return -1;
    }

    @Override
    public void run() {
        try {
            JsonArray array = JsonParser.array().from(URI.create("https://api.modrinth.com/v2/project/gatekeeper-mc/version").toURL());

            List<String> versions = new ArrayList<>();
            for (Object o : array) {
                String v = ((JsonObject) o).getString("version_number");

                if (!v.matches(".*[^0-9.].*")) {
                    versions.add(v);
                }
            }

            if (!versions.isEmpty()) {
                versions.sort((v1, v2) -> this.comparator.compare(v2, v1));

                this.latestVersion = versions.get(0);
                this.behind = this.calculateBuildsBehind(versions);
            } else {
                this.latestVersion = this.currentVersion;
                this.behind = 0;
            }

            this.difference = this.comparator.difference(this.currentVersion, this.latestVersion);
            this.compared = this.comparator.compare(this.currentVersion, this.latestVersion);

            if ((this.difference >= 0 && this.difference < 2) || this.behind > 5) {
                this.updater = true;
            }
        } catch (IOException | JsonParserException ex) {
            this.plugin.logger().log(Level.WARNING, ex.getMessage(), ex);
        }

        if (this.updater && this.compared < 0) {
            this.plugin.logger().info(
                    String.format("\n&8∘₊✧────────────────────────────────✧₊∘" +
                                    "\n&c&lGatekeeper needs an update!" +
                                    "\n&fVersion: &e&n%s&r -> &e&n%s&r" +
                                    "\n&ahttps://modrinth.com/plugin/gatekeeper-mc/version/%s" +
                                    "\n&8∘₊✧────────────────────────────────✧₊∘",
                            this.currentVersion, this.latestVersion, this.latestVersion
                    )
            );
        }
    }

    public static class VersionComparator implements Comparator<String> {
        @Override
        public int compare(String current, String latest) {
            String[] parts1 = current.split("\\.");
            String[] parts2 = latest.split("\\.");

            int length = Math.max(parts1.length, parts2.length);

            for (int i = 0; i < length; i++) {
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

                if (num1 != num2) {
                    return Integer.compare(num1, num2);
                }
            }

            return 0;
        }

        public int difference(String current, String latest) {
            String[] parts1 = current.split("\\.");
            String[] parts2 = latest.split("\\.");

            int length = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < length; i++) {
                int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
                int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

                if (num1 < num2) {
                    return i;
                }
            }
            return -1;
        }
    }
}

