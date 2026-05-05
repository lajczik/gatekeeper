package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.modules.*;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;

import java.util.*;
import java.util.logging.Level;

@Getter
public class ModuleManager {
    public static ModuleManager INSTANCE = new ModuleManager();
    private final Set<AbstractModule> allChecks = new HashSet<>();
    private final List<AbstractModule> loadedChecks = new ArrayList<>();
    private final HashMap<Class<? extends AbstractModule>, AbstractModule> checksMap = new HashMap<>();

    public void loadChecks(Gatekeeper<?> gatekeeper) {
        this.register(
                new AccountLimitModule(gatekeeper),
                new AsnFilterModule(gatekeeper),
                new BlacklistModule(gatekeeper),
                new CountryFilterModule(gatekeeper),
                new RateLimitModule(gatekeeper),
                new AntiVpnModule(gatekeeper),
                new IpFilterModule(gatekeeper)
        );
        this.reload();
    }

    public void register(AbstractModule... checks) {
        this.checksMap.clear();
        this.allChecks.clear();

        for (AbstractModule check : checks) {
            this.checksMap.put(check.getClass(), check);
            this.allChecks.add(check);
        }
    }

    public <T extends AbstractModule> T getCheck(Class<T> clazz) {
        return clazz.cast(this.checksMap.get(clazz));
    }

    public void reload() {
        this.loadedChecks.clear();
        for (AbstractModule check : this.allChecks) {
            try {
                boolean success = check.loadAllConfig();
                if (success) {
                    this.loadedChecks.add(check);
                }
                check.setLoaded(success);
            } catch (Exception ex) {
                check.setLoaded(false);
                check.getGatekeeper().logger().log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        this.loadedChecks.sort(Comparator.comparingInt(AbstractModule::getPriority));
    }
}