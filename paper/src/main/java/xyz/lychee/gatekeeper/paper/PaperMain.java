package xyz.lychee.gatekeeper.paper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
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
import xyz.lychee.gatekeeper.shared.util.MathUtils;

import java.io.File;
import java.io.InputStream;

public class PaperMain extends JavaPlugin implements Gatekeeper<Component>, Listener {
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.builder()
            .character('&')
            .hexCharacter('#')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();
    private final AbstractLang<Component> language = new PaperLang(this);
    private final ColoredLogger logger = new ColoredLogger(Bukkit.getLogger());
    private final PlatformData platformData = new PlatformData(
            this.getPluginMeta().getVersion(),
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
        this.loadManagers();
        this.language.loadLanguage();

        Bukkit.getPluginManager().registerEvents(new PaperListeners(this), this);

        PaperCommand commandHandler = new PaperCommand(this);
        CommandMap commandMap = Bukkit.getServer().getCommandMap();
        commandMap.register("gatekeeper", commandHandler);
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
                byte accessType = newAccess.getType();
                Player targetPlayer = Bukkit.getPlayer(target);

                if (AddressUtils.isIpv4(target)) {
                    int addressData = AddressUtils.ipv4ToInt(target);
                    DataManager.INSTANCE.getAddresses().put(addressData, accessType);
                    if (newAccess == EnumAccess.BLACKLIST) {
                        Bukkit.getOnlinePlayers().stream()
                                .filter(player -> AddressUtils.isIpv4Equal(player.getAddress().getAddress(), addressData))
                                .forEach(player -> player.kick(kickMessage));
                    }
                } else if (MathUtils.isInteger(target) && targetPlayer == null) {
                    int asn = Integer.parseInt(target);
                    DataManager.INSTANCE.getAsns().put(asn, accessType);
                } else {
                    DataManager.INSTANCE.getNicknames().put(target, accessType);
                    if (newAccess == EnumAccess.BLACKLIST && targetPlayer != null) {
                        targetPlayer.kick(kickMessage);
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

        @Override
        public Component hoverAndOpenUrl(String text, String hoverText, String url) {
            return this.color(text, false)
                    .hoverEvent(HoverEvent.showText(this.color(hoverText, false)))
                    .clickEvent(ClickEvent.openUrl(url));
        }
    }
}