package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountLimitModule extends AbstractModule {
    private final ConcurrentHashMap<Integer, AtomicInteger> ip_connected = new ConcurrentHashMap<>();
    private int accountLimitPerIp;

    public AccountLimitModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AccountLimit");
    }

    @Override
    public boolean handlePreLogin(InetAddress address, String name, int dataAddress) {
        if (this.accountLimitPerIp > 0) {
            AtomicInteger val = this.ip_connected.get(dataAddress);
            return val != null && val.get() >= this.accountLimitPerIp;
        }
        return false;
    }

    @Override
    public boolean handlePostLogin(InetAddress address, String name, int dataAddress) {
        this.ip_connected.computeIfAbsent(dataAddress, k -> new AtomicInteger()).incrementAndGet();
        return false;
    }

    @Override
    public boolean handleDisconnect(InetAddress address, String name, int dataAddress) {
        this.ip_connected.computeIfPresent(dataAddress, (k, ai) -> {
            if (ai.decrementAndGet() <= 0) return null;
            return ai;
        });
        return false;
    }

    @Override
    public boolean load() {
        this.accountLimitPerIp = this.getConfig().getInt("per_ip_limit");

        return true;
    }
}
