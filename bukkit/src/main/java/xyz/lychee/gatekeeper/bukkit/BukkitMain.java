package xyz.lychee.gatekeeper.bukkit;

import net.md_5.bungee.api.ChatColor;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.*;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.ColoredLogger;

import java.io.File;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BukkitMain extends JavaPlugin implements Gatekeeper<String>, Listener {
    private ColoredLogger logger;
    private Metrics metrics;
    private AbstractLang<String> language;

    @Override
    public void onEnable() {
        this.logger = new ColoredLogger(Bukkit.getLogger());
        this.metrics = new Metrics(this, 27416);
        this.language = new BukkitLang(this);

        ConfigManager.INSTANCE.loadConfig(this);
        DataManager.INSTANCE.loadDatabase(this);
        ModuleManager.INSTANCE.loadChecks(this);
        GeoipManager.INSTANCE.loadDatabases(this);
        TaskManager.INSTANCE.loadTasks(this);
        UpdaterManager.INSTANCE.load(this);

        this.language.loadLanguage();

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new BukkitListeners(), this);

        BukkitCommand commandHandler = new BukkitCommand(this);
        PluginCommand command = this.getCommand("gatekeeper");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        if (this.metrics != null) this.metrics.shutdown();

        DataManager.INSTANCE.close();
    }

    @Override
    public InputStream resource(String name) {
        return this.getResource(name);
    }

    @Override
    public File dataFolder() {
        return this.getDataFolder();
    }

    @Override
    public ColoredLogger logger() {
        return this.logger;
    }

    @Override
    public String version() {
        return this.getDescription().getVersion();
    }

    @Override
    public AbstractLang<String> language() {
        return this.language;
    }

    @Override
    public CommandPlayer<String> commandPlayer(Object player) {
        return new CommandPlayer<>(player) {
            @Override
            public boolean hasPermission(String permission) {
                if (this.getPlayer() instanceof CommandSender) {
                    return ((CommandSender) this.getPlayer()).hasPermission(permission);
                }
                return false;
            }

            @Override
            public void sendMessage(String message) {
                if (this.getPlayer() instanceof CommandSender) {
                    ((CommandSender) this.getPlayer()).sendMessage(message);
                }
            }

            @Override
            public void applyChange(String target, EnumAccess newAccess) {
                BlacklistModule check = ModuleManager.INSTANCE.getCheck(BlacklistModule.class);
                Object kickMessage = check.getKickMessage();

                try {
                    if (AddressUtils.isIpAddress(target)) {
                        InetAddress addr = InetAddress.getByAddress(AddressUtils.parseIp(target));
                        DataManager.INSTANCE.setAccess(AddressUtils.addressToInteger(addr), newAccess);
                        if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof String) {
                            String str = (String) kickMessage;
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(player -> player.getAddress().getAddress().equals(addr))
                                    .forEach(player -> player.kickPlayer(str));
                        }
                        return;
                    }
                } catch (Exception ignored) {}

                DataManager.INSTANCE.setAccess(target, newAccess);
                if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof String) {
                    Player player = Bukkit.getPlayer(target);
                    if (player != null) {
                        player.kickPlayer((String) kickMessage);
                    }
                }
            }
        };
    }

    public static class BukkitLang extends AbstractLang<String> {
        private final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");

        public BukkitLang(BukkitMain gatekeeper) {
            super(gatekeeper);
        }

        @Override
        public String color(String text, boolean prefix) {
            String colored = applyColors(text);

            if (prefix && ConfigManager.INSTANCE.getPrefix() instanceof String) {
                return colored.replace("%prefix%", (String) ConfigManager.INSTANCE.getPrefix());
            }

            return colored;
        }

        private String applyColors(String message) {
            Matcher matcher = this.hexPattern.matcher(message);
            StringBuilder buffer = new StringBuilder();

            while (matcher.find()) {
                String hex = matcher.group(1);
                StringBuilder replacement = new StringBuilder("§x");
                for (char c : hex.toCharArray()) {
                    replacement.append('§').append(c);
                }
                matcher.appendReplacement(buffer, replacement.toString());
            }
            matcher.appendTail(buffer);

            return ChatColor.translateAlternateColorCodes('&', buffer.toString());
        }
    }
}