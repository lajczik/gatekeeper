package xyz.lychee.gatekeeper.shared.modules;

import it.unimi.dsi.fastutil.ints.Int2LongMap;
import it.unimi.dsi.fastutil.ints.Int2LongMaps;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.TaskManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Getter
public class RateLimitModule extends AbstractModule implements Runnable {
    private final Int2LongMap ip_connect = Int2LongMaps.synchronize(new Int2LongOpenHashMap());
    private ScheduledFuture<?> task;
    private int server_limit;
    private int ip_limit;
    private volatile long server_connect = 0L;

    public RateLimitModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "RateLimit");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        long now = connection.getTimestamp();
        if (this.server_connect + this.server_limit > now) {
            return true;
        }

        return !connection.isLocalhost() && this.ip_connect.get(connection.getAddressData()) + this.ip_limit > now;
    }

    @Override
    public boolean handlePostLogin(GeoConnection connection) {
        long now = System.currentTimeMillis();
        connection.setTimestamp(now);
        this.server_connect = now;
        this.ip_connect.put(connection.getAddressData(), now);
        return false;
    }

    @Override
    public boolean handleDisconnect(GeoConnection connection) {
        return false;
    }

    @Override
    public void run() {
        long removable = System.currentTimeMillis() - ip_limit;
        this.ip_connect.values().removeIf(time -> time < removable);
    }

    @Override
    public boolean load() {
        this.server_limit = this.getConfig().getInt("server_limit");
        this.ip_limit = this.getConfig().getInt("ip_limit");

        this.task = TaskManager.INSTANCE.getScheduler().scheduleAtFixedRate(this, 1, 1, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public boolean unload() {
        if (this.task != null) {
            this.task.cancel(true);
        }
        return true;
    }
}
