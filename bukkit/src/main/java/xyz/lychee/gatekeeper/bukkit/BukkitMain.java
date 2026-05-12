package xyz.lychee.gatekeeper.bukkit;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.ConfigManager;
import xyz.lychee.gatekeeper.shared.manager.DataManager;
import xyz.lychee.gatekeeper.shared.manager.ModuleManager;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.*;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.util.RandomUtils;

import java.io.File;
import java.io.InputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BukkitMain extends JavaPlugin implements Gatekeeper<String>, Listener {
    private final AbstractLang<String> language = new BukkitLang(this);
    private final ColoredLogger logger = new ColoredLogger(Bukkit.getLogger());
    private final PlatformData platformData = new PlatformData(
            this.getDescription().getVersion(),
            27416,
            "bukkit",
            json -> {
                json.put("playerAmount", Bukkit.getOnlinePlayers().size());
                json.put("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
                json.put("bukkitVersion", Bukkit.getVersion());
                json.put("bukkitName", Bukkit.getName());
            }
    );

    @Override
    public void onEnable() {
        this.logger.sendHeader(this);

        this.loadManagers();

        this.language.loadLanguage();

        Bukkit.getPluginManager().registerEvents(new BukkitListeners(this), this);

        BukkitCommand commandHandler = new BukkitCommand(this);
        PluginCommand command = this.getCommand("gatekeeper");
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    @Override
    public void onDisable() {
        this.unloadManagers();
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
    public PlatformData platformData() {
        return this.platformData;
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
                if (!(check.getKickMessage() instanceof String)) return;

                String kickMessage = (String) check.getKickMessage();
                byte accessType = newAccess.getType();
                Player targetPlayer = Bukkit.getPlayer(target);

                if (AddressUtils.isIpv4(target)) {
                    int addressData = AddressUtils.ipv4ToInt(target);
                    DataManager.INSTANCE.getAddresses().put(addressData, accessType);
                    if (newAccess == EnumAccess.BLACKLIST) {
                        Bukkit.getOnlinePlayers().stream()
                                .filter(player -> AddressUtils.isIpv4Equal(player.getAddress().getAddress(), addressData))
                                .forEach(player -> player.kickPlayer(kickMessage));
                    }
                } else if (RandomUtils.isInteger(target) && targetPlayer == null) {
                    int asn = Integer.parseInt(target);
                    DataManager.INSTANCE.getAsns().put(asn, accessType);
                } else {
                    DataManager.INSTANCE.getNicknames().put(target, accessType);
                    if (newAccess == EnumAccess.BLACKLIST && targetPlayer != null) {
                        targetPlayer.kickPlayer(kickMessage);
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

        @Override
        public String hover(String component, String text) {
            return component;
        }

        private String applyColors(String message) {
            Matcher matcher = this.hexPattern.matcher(message);
            StringBuffer buffer = new StringBuffer();

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