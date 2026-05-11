package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class TaskManager extends AbstractManager {
    public static final TaskManager INSTANCE = new TaskManager();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            1,
            new SimpleThreadFactory("Gatekeeper-Scheduler")
    );

    private final ExecutorService callbackExecutor = new ThreadPoolExecutor(
            4,
            64,
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new SimpleThreadFactory("Gatekeeper-Callback"),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private final Set<ScheduledFuture<?>> tasks = new HashSet<>();

    @Override
    public boolean load(Gatekeeper<?> plugin) {
        Iterator<ScheduledFuture<?>> it = this.tasks.iterator();
        while (it.hasNext()) {
            it.next().cancel(true);
            it.remove();
        }

        this.tasks.add(scheduler.scheduleAtFixedRate(GeoipManager.INSTANCE, 1, 6, TimeUnit.HOURS));
        this.tasks.add(scheduler.scheduleAtFixedRate(UpdaterManager.INSTANCE, 1, 60, TimeUnit.MINUTES));
        this.tasks.add(scheduler.scheduleAtFixedRate(DataManager.INSTANCE, 1, 1, TimeUnit.MINUTES));
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        scheduler.shutdown();
        callbackExecutor.shutdown();
        this.tasks.clear();
        return true;
    }

    @Override
    public boolean reload(Gatekeeper<?> gatekeeper) {
        return true;
    }

    private static class SimpleThreadFactory implements ThreadFactory {
        private final String namePrefix;
        private final AtomicInteger threadNumber = new AtomicInteger(1);

        public SimpleThreadFactory(String name) {
            this.namePrefix = name;
        }

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread t = new Thread(r, this.namePrefix + "-" + this.threadNumber.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    }
}