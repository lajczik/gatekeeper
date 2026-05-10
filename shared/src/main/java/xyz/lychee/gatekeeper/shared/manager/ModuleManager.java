package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.modules.*;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;
import xyz.lychee.gatekeeper.shared.util.TimingUtil;

import java.util.*;
import java.util.logging.Level;

@Getter
public class ModuleManager extends AbstractManager {
    public static ModuleManager INSTANCE = new ModuleManager();
    private final Set<AbstractModule> allChecks = new HashSet<>();
    private final List<AbstractModule> loadedChecks = new ArrayList<>();
    private final HashMap<Class<? extends AbstractModule>, AbstractModule> checksMap = new HashMap<>();

    @Override
    public boolean load(Gatekeeper<?> plugin) {
        this.register(
                new AccountLimitModule(plugin),
                new AsnFilterModule(plugin),
                new BlacklistModule(plugin),
                new CountryFilterModule(plugin),
                new RateLimitModule(plugin),
                new AntiVpnModule(plugin),
                new IpFilterModule(plugin)
        );
        this.reload(plugin);
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> plugin) {
        this.loadedChecks.clear();
        for (AbstractModule check : this.allChecks) {
            try {
                TimingUtil t = TimingUtil.startNew();
                boolean success = check.loadAllConfig();
                if (success) {
                    this.loadedChecks.add(check);
                    check.getGatekeeper().logger().info(" &8• &rSuccessfully loaded module " + check.getName() + " in " + t.stop().getExecutingTime() + "ms.");
                }
                check.setLoaded(success);
            } catch (Exception ex) {
                check.setLoaded(false);
                check.getGatekeeper().logger().info(" &8• &cSkipping module " + check.getName() + ", reason: " + ex.getMessage());
            }
        }
        this.loadedChecks.sort(Comparator.comparingInt(AbstractModule::getPriority));
        return true;
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
}