package xyz.lychee.gatekeeper.shared.manager;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.dejvokep.boostedyaml.YamlDocument;
import lombok.Getter;
import xyz.lychee.gatekeeper.shared.Gatekeeper;
import xyz.lychee.gatekeeper.shared.objects.EnumAccess;
import xyz.lychee.gatekeeper.shared.objects.GeoConnection;
import xyz.lychee.gatekeeper.shared.util.AddressUtils;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

@Getter
public class DataManager {
    public static final DataManager INSTANCE = new DataManager();

    private final Map<Integer, Byte> addresses = new ConcurrentHashMap<>();
    private final Map<String, Byte> nicknames = new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private HikariDataSource dataSource;
    private Logger logger;
    private DatabaseType currentDbType;

    public void loadDatabase(Gatekeeper<?> gatekeeper) {
        this.logger = gatekeeper.logger();
        YamlDocument yaml = ConfigManager.INSTANCE.getYaml();

        String typeStr = yaml.getString("database.type", "H2").toUpperCase();

        try {
            this.currentDbType = DatabaseType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            this.currentDbType = DatabaseType.H2;
            this.logger.warning("Unknown database type, using H2 by default.");
        }

        HikariConfig config = new HikariConfig();

        if (this.currentDbType == DatabaseType.H2) {
            String path = gatekeeper.dataFolder().toPath().resolve("database").toAbsolutePath().toString();

            config.setJdbcUrl("jdbc:h2:" + path + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
            config.setDriverClassName("org.h2.Driver");
            config.setUsername("sa");
            config.setPassword("");
        } else {
            String host = yaml.getString("database.host", "localhost");
            int port = yaml.getInt("database.port", 3306);
            String database = yaml.getString("database.database", "gatekeeper");
            String username = yaml.getString("database.username", "root");
            String password = yaml.getString("database.password", "");
            boolean useSsl = yaml.getBoolean("database.useSSL", false);

            String jdbcUrl = "jdbc:mariadb://" + host + ":" + port + "/" + database + "?useSSL=" + useSsl;

            config.setJdbcUrl(jdbcUrl);
            config.setDriverClassName("org.mariadb.jdbc.Driver");
            config.setUsername(username);
            config.setPassword(password);

            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        }

        config.setMaximumPoolSize(10);
        config.setConnectionTimeout(5000);
        config.setLeakDetectionThreshold(10000);

        try {
            this.dataSource = new HikariDataSource(config);
            this.initTables();

            this.loadAddresses();
            this.loadNicknames();

            this.logger.info("Connected to database: " + this.currentDbType.name());
        } catch (Exception ex) {
            this.logger.log(Level.SEVERE, "Failed to load database", ex);
        }
    }

    public void close() {
        if (!this.executor.isShutdown()) {
            this.executor.shutdown();
        }

        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }

    private void initTables() throws SQLException {
        String[] queries = {
                "CREATE TABLE IF NOT EXISTS addresses (address INT, access TINYINT DEFAULT 0, PRIMARY KEY (address));",
                "CREATE TABLE IF NOT EXISTS nicknames (nickname VARCHAR(64), access TINYINT DEFAULT 0, PRIMARY KEY (nickname));"
        };

        try (Connection conn = this.dataSource.getConnection()) {
            for (String query : queries) {
                try (PreparedStatement stmt = conn.prepareStatement(query)) {
                    stmt.executeUpdate();
                }
            }
        }
    }

    public void loadAddresses() {
        String query = "SELECT address, access FROM addresses";
        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                this.addresses.put(rs.getInt("address"), rs.getByte("access"));
            }
        } catch (SQLException ex) {
            this.logger.log(Level.SEVERE, "Failed to load addresses database", ex);
        }
    }

    public void loadNicknames() {
        String query = "SELECT nickname, access FROM nicknames";
        try (Connection conn = this.dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                this.nicknames.put(rs.getString("nickname"), rs.getByte("access"));
            }
        } catch (SQLException ex) {
            this.logger.log(Level.SEVERE, "Failed to load nicknames database", ex);
        }
    }

    public boolean updateAndCheckAccess(GeoConnection connection, EnumAccess targetAccess) {
        Byte access = this.nicknames.get(connection.getName());
        if (access == null) {
            access = this.addresses.get(connection.getAddressData());
        }
        if (access != null) {
            connection.setAccess(EnumAccess.getByType(access));
        }
        return access != null && access == targetAccess.getType();
    }

    public void setAccess(int address, EnumAccess access) {
        this.addresses.put(address, access.getType());
        this.updateAccess(address, access);
    }

    public void setAccess(String nickname, EnumAccess access) {
        this.nicknames.put(nickname, access.getType());
        this.updateAccess(nickname, access);
    }

    private String getUpsertQuery(String table, String keyColumn, String valColumn) {
        if (currentDbType == DatabaseType.H2 || currentDbType == DatabaseType.MYSQL) {
            return String.format("INSERT INTO %s (%s, %s) VALUES (?, ?) ON DUPLICATE KEY UPDATE %s = VALUES(%s)",
                    table, keyColumn, valColumn, valColumn, valColumn);
        } else {
            return String.format("INSERT INTO %s (%s, %s) VALUES (?, ?) ON CONFLICT(%s) DO UPDATE SET %s = excluded.%s",
                    table, keyColumn, valColumn, keyColumn, valColumn, valColumn);
        }
    }

    public void updateAccess(int ip, EnumAccess access) {
        this.executor.execute(() -> {
            String sql = getUpsertQuery("addresses", "address", "access");
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, ip);
                pstmt.setInt(2, access.getType());
                pstmt.executeUpdate();

            } catch (SQLException ex) {
                this.logger.log(Level.SEVERE, "Błąd aktualizacji adresu IP", ex);
            }
        });
    }

    public void updateAccess(String nickname, EnumAccess access) {
        this.executor.execute(() -> {
            String sql = getUpsertQuery("nicknames", "nickname", "access");
            try (Connection conn = this.dataSource.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setString(1, nickname);
                pstmt.setInt(2, access.getType());
                pstmt.executeUpdate();

            } catch (SQLException ex) {
                this.logger.log(Level.SEVERE, "Błąd aktualizacji nicku", ex);
            }
        });
    }

    public byte resolveAccess(String target) {
        try {
            if (AddressUtils.isIpAddress(target)) {
                InetAddress addr = InetAddress.getByAddress(AddressUtils.parseIp(target));
                return this.addresses.getOrDefault(AddressUtils.addressToInteger(addr), (byte) 0);
            }
        } catch (Exception ignored) {}

        return this.nicknames.getOrDefault(target, (byte) 0);
    }

    public enum DatabaseType {
        H2, MYSQL
    }
}