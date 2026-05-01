package xyz.lychee.gatekeeper.shared.manager;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.util.BinaryGeoIPDatabase;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Getter
public class GeoipManager implements Runnable {
    public static final GeoipManager INSTANCE = new GeoipManager();
    private final Map<Integer, String> countryCache = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> asnCache = new ConcurrentHashMap<>();
    private BinaryGeoIPDatabase database;
    private Gatekeeper<?> gatekeeper;
    private Set<BinaryGeoIPDatabase.GeoRange<String>> countryRangeCache = Collections.emptySet();
    private Set<BinaryGeoIPDatabase.GeoRange<Integer>> asnRangeCache = Collections.emptySet();

    public void loadDatabases(Gatekeeper<?> gatekeeper) {
        this.gatekeeper = gatekeeper;
        File dataFile = new File(gatekeeper.dataFolder(), "geodata.lgi");
        try {
            if (shouldDownloadDatabase(dataFile)) {
                this.gatekeeper.logger().info("Downloading GeoIP database...");
                downloadDatabase(dataFile);
            } else {
                this.gatekeeper.logger().info("Using existing GeoIP database: " + dataFile.getName());
            }

            initializeDatabase(dataFile);

            cacheAllRanges();

            this.gatekeeper.logger().info("GeoIP database has been loaded successfully!");
        } catch (IOException ex) {
            this.gatekeeper.logger().log(Level.SEVERE, "GeoIP files could not be loaded.", ex);
            clearCache();
        }
    }

    private boolean shouldDownloadDatabase(File dataFile) {
        if (!dataFile.exists()) {
            return true;
        }

        long fileAgeInMillis = System.currentTimeMillis() - dataFile.lastModified();
        long updateIntervalMillis = 11 * 60 * 60 * 1000;

        return fileAgeInMillis > updateIntervalMillis;
    }

    private void downloadDatabase(File dataFile) throws IOException {
        File parentDir = dataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }

        File tempFile = new File(dataFile.getAbsolutePath() + ".tmp");
        try (InputStream is = URI.create("https://cdn.sakuramc.pl/geodata.lgi").toURL().openStream();
             OutputStream os = Files.newOutputStream(tempFile.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        if (dataFile.exists()) {
            dataFile.delete();
        }
        if (!tempFile.renameTo(dataFile)) {
            throw new IOException("Failed to replace old database file");
        }
    }

    private void initializeDatabase(File dataFile) throws IOException {
        if (this.database != null) {
            this.close();
        }

        this.database = new BinaryGeoIPDatabase(dataFile.getAbsolutePath());
        this.database.load();
    }

    private void cacheAllRanges() {
        clearCache();

        try {
            this.countryRangeCache = this.database.getAllCountryRanges();
            this.asnRangeCache = this.database.getAllASNRanges();

            this.gatekeeper.logger().info("Loaded " + this.countryRangeCache.size() + " country and " + this.asnRangeCache.size() + " asn ranges!");
        } catch (Exception ex) {
            this.gatekeeper.logger().log(Level.WARNING, "Failed to load GeoIP ranges", ex);
            clearCache();
        }
    }

    public @NotNull String getCountryCode(int addressData) {
        String cached = countryCache.get(addressData);
        if (cached != null) {
            return cached;
        }

        for (BinaryGeoIPDatabase.GeoRange<String> record : countryRangeCache) {
            if (record.contains(addressData)) {
                countryCache.put(addressData, record.getValue());
                return record.getValue();
            }
        }
        String unknown = BinaryGeoIPDatabase.UNKNOWN_COUNTRY;
        countryCache.put(addressData, unknown);
        return unknown;
    }

    public @NotNull Integer getAsnCode(int addressData) {
        Integer cached = asnCache.get(addressData);
        if (cached != null) {
            return cached;
        }

        for (BinaryGeoIPDatabase.GeoRange<Integer> record : asnRangeCache) {
            if (record.contains(addressData)) {
                asnCache.put(addressData, record.getValue());
                return record.getValue();
            }
        }
        int unknown = BinaryGeoIPDatabase.UNKNOWN_ASN;
        asnCache.put(addressData, unknown);
        return unknown;
    }

    public void clearCache() {
        countryCache.clear();
        asnCache.clear();
    }

    public void close() {
        clearCache();
        if (this.database != null) {
            this.database.close();
        }
    }

    @Override
    public void run() {
        if (this.gatekeeper != null) {
            this.loadDatabases(this.gatekeeper);
        }
    }
}