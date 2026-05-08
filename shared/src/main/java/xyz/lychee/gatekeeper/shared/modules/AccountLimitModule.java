package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class AccountLimitModule extends AbstractModule {
    private final Map<Integer, LongAdder> ip_connected = new ConcurrentHashMap<>();
    private int accountLimitPerIp;

    public AccountLimitModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AccountLimit");
    }

    @Override
    public boolean handlePreLogin(GeoConnection connection) {
        if (this.accountLimitPerIp > 0) {
            LongAdder val = this.ip_connected.get(connection.getAddressData());
            return val != null && val.longValue() >= this.accountLimitPerIp;
        }
        return false;
    }

    @Override
    public boolean handlePostLogin(GeoConnection connection) {
        this.ip_connected.computeIfAbsent(connection.getAddressData(), k -> new LongAdder()).increment();
        return false;
    }

    @Override
    public boolean handleDisconnect(GeoConnection connection) {
        this.ip_connected.computeIfPresent(connection.getAddressData(), (k, la) -> {
            la.decrement();
            return la.longValue() > 0 ? la : null;
        });
        return false;
    }

    @Override
    public boolean load() {
        this.accountLimitPerIp = this.getConfig().getInt("per_ip_limit");

        return true;
    }
}
