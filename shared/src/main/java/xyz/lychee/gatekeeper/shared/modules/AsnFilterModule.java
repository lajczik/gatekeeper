package xyz.lychee.gatekeeper.shared.modules;

import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.manager.GeoipManager;
import xyz.lychee.gatekeeper.shared.objects.AbstractModule;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AsnFilterModule extends AbstractModule implements Runnable {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final HashSet<Integer> downloadedAsn = new HashSet<>();
    private final HashSet<Integer> listedAsn = new HashSet<>();
    private final HashSet<String> sources = new HashSet<>();
    private final Path asnPath;
    private Pattern pattern;
    private ScheduledFuture<?> task;
    private long interval;
    private boolean list_mode;

    public AsnFilterModule(Gatekeeper<?> gatekeeper) {
        super(gatekeeper, "AsnFilter");

        this.asnPath = this.getGatekeeper().dataFolder().toPath().resolve("asn.txt");
    }

    @Override
    public boolean handlePreLogin(InetAddress address, String name, int dataAddress) {
        int asn = GeoipManager.INSTANCE.getAsnCode(dataAddress);
        return asn > 0 && (this.downloadedAsn.contains(asn) || this.listedAsn.contains(asn) == this.list_mode);
    }

    @Override
    public boolean handlePostLogin(InetAddress address, String name, int dataAddress) {
        return false;
    }

    @Override
    public boolean handleDisconnect(InetAddress address, String name, int dataAddress) {
        return false;
    }

    @Override
    public boolean load() throws IOException {
        this.downloadedAsn.clear();
        this.listedAsn.clear();
        this.sources.clear();

        if (this.task != null) {
            this.task.cancel(true);
        }

        if (Files.exists(this.asnPath)) {
            Files.readAllLines(this.asnPath).forEach(line -> {
                try {
                    this.downloadedAsn.add(Integer.parseInt(line));
                } catch (NumberFormatException ignored) {}
            });
        }


        this.interval = this.getConfig().getInt("auto_update.interval") * 60L * 60L;
        this.pattern = Pattern.compile(this.getConfig().getString("auto_update.asn_pattern"));
        this.sources.addAll(this.getConfig().getStringList("auto_update.sources"));
        this.task = this.executor.scheduleAtFixedRate(this, 10L, interval, TimeUnit.SECONDS);

        this.listedAsn.addAll(this.getConfig().getIntList("list"));
        this.list_mode = this.getConfig().getBoolean("list_mode");

        return true;
    }

    @Override
    public void run() {
        try {
            if (Files.exists(this.asnPath)) {
                long time = System.currentTimeMillis() - Files.getLastModifiedTime(this.asnPath).toMillis();
                if (time < this.interval * 1000L) {
                    return;
                }
            }

            HashSet<Integer> asns = new HashSet<>();
            HashSet<String> lines = new HashSet<>();
            for (String source : this.sources) {
                HttpURLConnection conn = (HttpURLConnection) URI.create(source).toURL().openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:90.0) Gecko/20100101 Firefox/90.0");

                try (InputStream is = conn.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append('\n');
                    }

                    String text = sb.toString();
                    Matcher matcher = this.pattern.matcher(text);
                    while (matcher.find()) {
                        try {
                            String g = matcher.group();
                            asns.add(Integer.parseInt(g));
                            lines.add(g);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                conn.disconnect();
            }

            if (asns.isEmpty()) return;

            this.downloadedAsn.clear();
            this.downloadedAsn.addAll(asns);

            Files.write(this.asnPath, lines);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
