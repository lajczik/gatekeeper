package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.AbstractManager;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public class TaskManager extends AbstractManager {
    public static final TaskManager INSTANCE = new TaskManager();

    private ScheduledExecutorService scheduler;
    private ExecutorService callbackExecutor;
    private ExecutorService asyncExecutor;
    private HttpClient httpClient;

    @Override
    public boolean load(Gatekeeper<?> plugin) {
        int cores = Runtime.getRuntime().availableProcessors();

        this.callbackExecutor = new ThreadPoolExecutor(
                cores,
                128,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new SimpleThreadFactory("Gatekeeper-Callback"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.scheduler = new ScheduledThreadPoolExecutor(
                1,
                new SimpleThreadFactory("Gatekeeper-Scheduler")
        );

        this.asyncExecutor = new ThreadPoolExecutor(
                Math.max(1, cores / 2),
                Math.max(1, cores / 2),
                0, TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(),
                new SimpleThreadFactory("Gatekeeper-Worker"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .executor(this.callbackExecutor)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        this.scheduler.scheduleAtFixedRate(GeoipManager.INSTANCE, 1, 6, TimeUnit.HOURS);
        this.scheduler.scheduleAtFixedRate(UpdaterManager.INSTANCE, 1, 60, TimeUnit.MINUTES);
        this.scheduler.scheduleAtFixedRate(DataManager.INSTANCE, 1, 1, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public boolean unload(Gatekeeper<?> plugin) {
        if (this.scheduler != null) {
            this.scheduler.shutdown();
        }
        if (this.callbackExecutor != null) {
            this.callbackExecutor.shutdown();
        }
        if (this.asyncExecutor != null) {
            this.asyncExecutor.shutdown();
        }
        if (this.httpClient != null) {
            this.httpClient.close();
        }
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