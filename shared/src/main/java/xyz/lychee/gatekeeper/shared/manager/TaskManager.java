package xyz.lychee.gatekeeper.shared.manager;

import xyz.lychee.gatekeeper.shared.Gatekeeper;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class TaskManager {
    public static final TaskManager INSTANCE = new TaskManager();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setName("Gatekeeper-Thread");
        return t;
    });
    private final Set<ScheduledFuture<?>> tasks = new HashSet<>();

    public void loadTasks(Gatekeeper<?> gatekeeper) {
        Iterator<ScheduledFuture<?>> it = this.tasks.iterator();
        while (it.hasNext()) {
            it.next().cancel(true);
            it.remove();
        }

        tasks.add(executor.scheduleAtFixedRate(GeoipManager.INSTANCE, 1, 6, TimeUnit.HOURS));
        tasks.add(executor.scheduleAtFixedRate(UpdaterManager.INSTANCE, 1, 60, TimeUnit.MINUTES));
    }
}