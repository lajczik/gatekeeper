package xyz.lychee.gatekeeper.shared;

import xyz.lychee.gatekeeper.shared.manager.*;
import xyz.lychee.gatekeeper.shared.objects.*;
import xyz.lychee.gatekeeper.shared.util.TimingUtil;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Level;

public interface Gatekeeper<T> {
    AbstractManager[] MANAGERS = new AbstractManager[]{
            ConfigManager.INSTANCE,
            DataManager.INSTANCE,
            ModuleManager.INSTANCE,
            GeoipManager.INSTANCE,
            TaskManager.INSTANCE,
            UpdaterManager.INSTANCE,
            MetricsManager.INSTANCE
    };

    InputStream resource(String name);

    File dataFolder();

    ColoredLogger logger();

    PlatformData platformData();

    AbstractLang<T> language();

    CommandPlayer<T> commandPlayer(Object player);

    default void loadManagers() {
        TimingUtil t = new TimingUtil();
        for (AbstractManager manager : MANAGERS) {
            try {
                this.logger().info("&8(&b" + manager.getClass().getSimpleName() + "&8) &7-> &fEnabling manager...");
                t.start();
                manager.load(this);
                this.logger().info("&8(&b" + manager.getClass().getSimpleName() + "&8) &7-> &fEnabled manager in &b" + t.stop().getExecutingTime() + "ms&f!");
            } catch (Exception ex) {
                this.logger().log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    default void unloadManagers() {
        TimingUtil t = new TimingUtil();
        for (AbstractManager manager : MANAGERS) {
            try {
                this.logger().info("&8(&b" + manager.getClass().getSimpleName() + "&8) &7-> &fDisabling manager...");
                t.start();
                manager.unload(this);
                this.logger().info("&8(&b" + manager.getClass().getSimpleName() + "&8) &7-> &fDisabled manager in &b" + t.stop().getExecutingTime() + "ms&f!");
            } catch (Exception ex) {
                this.logger().log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
    }

    default void reloadManagers() {
        TimingUtil t = TimingUtil.startNew();
        for (AbstractManager manager : MANAGERS) {
            try {
                manager.reload(this);
            } catch (Exception ex) {
                this.logger().log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        this.logger().info("Reloaded gatekeeper in &b" + t.stop().getExecutingTime() + "ms&f!");
    }
}