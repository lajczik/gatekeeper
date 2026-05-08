package xyz.lychee.gatekeeper.shared.modules;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import java.util.concurrent.*;

@Getter
public class RateLimitModule extends AbstractModule implements Runnable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentHashMap<Integer, Long> ip_connect = new ConcurrentHashMap<>();
    private ScheduledFuture<?> task;
    private int server_limit;
    private int ip_limit;
    private volatile long server_connect = 0L;

    public RateLimitModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "RateLimit");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        long now = System.currentTimeMillis();
        if (this.server_connect + this.server_limit > now) {
            return true;
        }

        Long l = this.ip_connect.get(connection.getAddressData());
        return l != null && l + this.ip_limit > now;
    }

    @Override
    public boolean handlePostLogin(GeoConnection connection) {
        long now = System.currentTimeMillis();
        this.server_connect = now;
        this.ip_connect.put(connection.getAddressData(), now);
        return false;
    }

    @Override
    public boolean handleDisconnect(GeoConnection connection) {
        return false;
    }

    @Override
    public boolean load() {
        if (this.task != null) {
            this.task.cancel(true);
        }

        this.server_limit = this.getConfig().getInt("server_limit");
        this.ip_limit = this.getConfig().getInt("ip_limit");

        this.task = this.executor.scheduleAtFixedRate(this, 1, 1, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void run() {
        long removable = System.currentTimeMillis() - ip_limit;
        this.ip_connect.values().removeIf(value -> value < removable);
    }
}
