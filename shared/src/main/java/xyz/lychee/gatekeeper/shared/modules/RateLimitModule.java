package xyz.lychee.gatekeeper.shared.modules;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;

import java.net.InetAddress;
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
    public boolean handlePreLogin(InetAddress address, String name, int dataAddress) {
        long now = System.currentTimeMillis();
        if (this.server_connect + this.server_limit > now) {
            return true;
        }

        Long l = this.ip_connect.get(dataAddress);
        return l != null && l + this.ip_limit > now;
    }

    @Override
    public boolean handlePostLogin(InetAddress address, String name, int dataAddress) {
        long now = System.currentTimeMillis();
        this.server_connect = now;
        this.ip_connect.put(dataAddress, now);
        return false;
    }

    @Override
    public boolean handleDisconnect(InetAddress address, String name, int dataAddress) {
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
