package xyz.lychee.gatekeeper.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginManager;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.*;
import xyz.lychee.gatekeeper.shared.modules.BlacklistModule;
import xyz.lychee.gatekeeper.shared.objects.AbstractLang;
import xyz.lychee.gatekeeper.shared.objects.CommandPlayer;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;
import xyz.lychee.gatekeeper.shared.objects.ColoredLogger;

import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class BungeeMain extends Plugin implements Gatekeeper<BaseComponent>, Listener {
    private final AbstractLang<BaseComponent> language = new BungeeLang(this);
    private final ColoredLogger logger = new ColoredLogger(this.getProxy().getLogger());

    @Override
    public void onEnable() {
        this.logger.sendHeader(this.version());

        ConfigManager.INSTANCE.loadConfig(this);
        DataManager.INSTANCE.loadDatabase(this);
        ModuleManager.INSTANCE.loadChecks(this);
        GeoipManager.INSTANCE.loadDatabases(this);
        TaskManager.INSTANCE.loadTasks(this);
        UpdaterManager.INSTANCE.loadUpdater(this);
        MetricsManager.INSTANCE.loadMetrics(this, json -> {
            json.put("playerAmount", this.getProxy().getOnlineCount());
            json.put("onlineMode", this.getProxy().getConfig().isOnlineMode() ? 1 : 0);
            json.put("bukkitVersion", this.getProxy().getVersion());
            json.put("bukkitName", this.getProxy().getName());
        });

        this.language.loadLanguage();

        PluginManager pm = getProxy().getPluginManager();
        pm.registerListener(this, new BungeeListeners(this));
        pm.registerCommand(this, new BungeeCommand(this));
    }

    @Override
    public void onDisable() {
        MetricsManager.INSTANCE.shutdown();
        DataManager.INSTANCE.close();
    }

    @Override
    public InputStream resource(String name) {
        return this.getResourceAsStream(name);
    }

    @Override
    public File dataFolder() {
        return getDataFolder();
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
    public AbstractLang<BaseComponent> language() {
        return this.language;
    }

    @Override
    public CommandPlayer<BaseComponent> commandPlayer(Object player) {
        return new CommandPlayer<>(player) {
            @Override
            public boolean hasPermission(String permission) {
                if (this.getPlayer() instanceof CommandSender) {
                    return ((CommandSender) this.getPlayer()).hasPermission(permission);
                }
                return false;
            }

            @Override
            public void sendMessage(BaseComponent message) {
                if (this.getPlayer() instanceof CommandSender) {
                    ((CommandSender) this.getPlayer()).sendMessage(message);
                }
            }

            @Override
            public void applyChange(String target, EnumAccess newAccess) {
                BlacklistModule check = ModuleManager.INSTANCE.getCheck(BlacklistModule.class);
                Object kickMessage = check.getKickMessage();

                if (AddressUtils.isIpv4(target)) {
                    int addressData = AddressUtils.ipv4ToInt(target);
                    DataManager.INSTANCE.setAccess(addressData, newAccess);
                    if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof BaseComponent) {
                        BaseComponent baseComponent = (BaseComponent) kickMessage;
                        getProxy().getPlayers().stream()
                                .filter(player -> AddressUtils.isIpv4Equal(((InetSocketAddress) player.getSocketAddress()).getAddress(), addressData))
                                .forEach(player -> player.disconnect(baseComponent));
                    }
                    return;
                }

                DataManager.INSTANCE.setAccess(target, newAccess);
                if (newAccess == EnumAccess.BLACKLIST && kickMessage instanceof BaseComponent) {
                    ProxiedPlayer player = getProxy().getPlayer(target);
                    if (player != null) {
                        BaseComponent baseComponent = (BaseComponent) kickMessage;
                        player.disconnect(baseComponent);
                    }
                }
            }
        };
    }

    public static class BungeeLang extends AbstractLang<BaseComponent> {
        private final Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        private final boolean singleBase;

        public BungeeLang(BungeeMain gatekeeper) {
            super(gatekeeper);

            boolean singleBase = false;
            try {
                TextComponent.fromLegacy("test");
                singleBase = true;
            } catch (Exception ignored) {}

            this.singleBase = singleBase;
        }

        @Override
        public BaseComponent color(String text, boolean prefix) {
            String colored = this.applyColors(text);

            if (prefix && ConfigManager.INSTANCE.getPrefix() instanceof BaseComponent) {
                TextComponent root = new TextComponent();
                BaseComponent p = (BaseComponent) ConfigManager.INSTANCE.getPrefix();
                String[] parts = colored.split("%prefix%", -1);

                for (int i = 0; i < parts.length; i++) {
                    if (!parts[i].isEmpty()) {
                        if (this.singleBase) {
                            root.addExtra(TextComponent.fromLegacy(parts[i]));
                        } else {
                            for (BaseComponent bc : TextComponent.fromLegacyText(parts[i])) {
                                root.addExtra(bc);
                            }
                        }
                    }
                    if (i < parts.length - 1) {
                        root.addExtra(p);
                    }
                }

                return root;
            }

            if (this.singleBase) {
                return TextComponent.fromLegacy(colored);
            } else {
                TextComponent root = new TextComponent();
                for (BaseComponent bc : TextComponent.fromLegacyText(colored)) {
                    root.addExtra(bc);
                }
                return root;
            }
        }

        private String applyColors(String message) {
            Matcher matcher = this.hexPattern.matcher(message);
            StringBuilder builder = new StringBuilder();

            while (matcher.find()) {
                String hex = matcher.group(1);
                StringBuilder replacement = new StringBuilder("&x");
                for (char c : hex.toCharArray()) {
                    replacement.append('&').append(c);
                }
                matcher.appendReplacement(builder, replacement.toString());
            }
            matcher.appendTail(builder);
            return ChatColor.translateAlternateColorCodes('&', builder.toString());
        }
    }
}