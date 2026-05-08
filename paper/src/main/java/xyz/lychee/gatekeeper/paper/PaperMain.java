package xyz.lychee.gatekeeper.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
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

public class PaperMain extends JavaPlugin implements Gatekeeper<Component>, Listener {
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final AbstractLang<Component> language = new PaperLang(this);
    private final ColoredLogger logger = new ColoredLogger(Bukkit.getLogger());

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
            json.put("playerAmount", Bukkit.getOnlinePlayers().size());
            json.put("onlineMode", Bukkit.getOnlineMode() ? 1 : 0);
            json.put("bukkitVersion", Bukkit.getVersion());
            json.put("bukkitName", Bukkit.getName());
        });

        this.language.loadLanguage();

        Bukkit.getPluginManager().registerEvents(new PaperListeners(this), this);

        PaperCommand commandHandler = new PaperCommand(this);
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        commandMap.register("gatekeeper", commandHandler);
    }

    @Override
    public void onDisable() {
        MetricsManager.INSTANCE.shutdown();
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
        return this.getPluginMeta().getVersion();
    }

    @Override
    public AbstractLang<Component> language() {
        return this.language;
    }

    @Override
    public CommandPlayer<Component> commandPlayer(Object player) {
        return new CommandPlayer<>(player) {
            @Override
            public boolean hasPermission(String permission) {
                if (this.getPlayer() instanceof CommandSender) {
                    return ((CommandSender) this.getPlayer()).hasPermission(permission);
                }
                return false;
            }

            @Override
            public void sendMessage(Component message) {
                if (this.getPlayer() instanceof CommandSender) {
                    ((CommandSender) this.getPlayer()).sendMessage(message);
                }
            }

            @Override
            public void applyChange(String target, EnumAccess newAccess) {
                BlacklistModule check = ModuleManager.INSTANCE.getCheck(BlacklistModule.class);
                if (!(check.getKickMessage() instanceof Component)) return;

                Component kickMessage = (Component) check.getKickMessage();

                try {
                    if (AddressUtils.isIpAddress(target)) {
                        InetAddress addr = InetAddress.getByAddress(AddressUtils.parseIp(target));
                        DataManager.INSTANCE.setAccess(AddressUtils.addressToInteger(addr), newAccess);
                        if (newAccess == EnumAccess.BLACKLIST) {
                            Bukkit.getOnlinePlayers().stream()
                                    .filter(player -> player.getAddress().getAddress().equals(addr))
                                    .forEach(player -> player.kick(kickMessage));
                        }
                        return;
                    }
                } catch (Exception ignored) {}

                DataManager.INSTANCE.setAccess(target, newAccess);
                if (newAccess == EnumAccess.BLACKLIST) {
                    Player player = Bukkit.getPlayer(target);
                    if (player != null) {
                        player.kick(kickMessage);
                    }
                }
            }
        };
    }

    public class PaperLang extends AbstractLang<Component> {
        public PaperLang(PaperMain gatekeeper) {
            super(gatekeeper);
        }

        @Override
        public Component color(String text, boolean prefix) {
            Component deserialized = serializer.deserialize(text);
            if (prefix && ConfigManager.INSTANCE.getPrefix() instanceof ComponentLike) {
                return deserialized
                        .replaceText(TextReplacementConfig.builder()
                                .matchLiteral("%prefix%")
                                .replacement((Component) ConfigManager.INSTANCE.getPrefix())
                                .build()
                        );
            }
            return deserialized;
        }
    }
}