package gg.summit.customarmor.db;

import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final SummitCustomArmor plugin;
    private final PlayerDataCache cache;
    private final ClassLoader libLoader;

    private AutoCloseable dataSource;  // HikariDataSource — held as Object to avoid compile-time dep
    private DataSource sqlDataSource;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS custom_armor_data (
                id    INT AUTO_INCREMENT PRIMARY KEY,
                uuid  VARCHAR(36) NOT NULL,
                piece VARCHAR(16) NOT NULL,
                level INT         NOT NULL,
                xp    DOUBLE      NOT NULL,
                UNIQUE KEY unique_player_piece (uuid, piece)
            )""";

    private static final String UPSERT =
            "INSERT INTO custom_armor_data (uuid, piece, level, xp) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp)";

    private static final String SELECT_PLAYER =
            "SELECT piece, level, xp FROM custom_armor_data WHERE uuid = ?";

    public DatabaseManager(SummitCustomArmor plugin, PlayerDataCache cache, ClassLoader libLoader) {
        this.plugin    = plugin;
        this.cache     = cache;
        this.libLoader = libLoader;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void connect() throws Exception {
        ConfigurationSection db = plugin.getConfig().getConfigurationSection("database");
        if (db == null) throw new SQLException("Missing 'database' section in config.yml");

        String jdbcUrl = "jdbc:mariadb://"
                + db.getString("host", "localhost") + ":"
                + db.getInt("port", 3306) + "/"
                + db.getString("database", "summit_customarmor");

        // Reflectively build HikariConfig and HikariDataSource using the lib classloader
        Class<?> hikariConfigClass = libLoader.loadClass("com.zaxxer.hikari.HikariConfig");
        Object hikariConfig = hikariConfigClass.getDeclaredConstructor().newInstance();

        set(hikariConfig, "setJdbcUrl",         jdbcUrl);
        set(hikariConfig, "setUsername",         db.getString("username", "root"));
        set(hikariConfig, "setPassword",         db.getString("password", ""));
        set(hikariConfig, "setDriverClassName",  "org.mariadb.jdbc.Driver");
        setInt(hikariConfig, "setMaximumPoolSize",  10);
        setInt(hikariConfig, "setMinimumIdle",       2);
        setLong(hikariConfig, "setConnectionTimeout", 10_000L);
        setLong(hikariConfig, "setIdleTimeout",      600_000L);
        setLong(hikariConfig, "setMaxLifetime",    1_800_000L);
        set(hikariConfig, "setPoolName", "SummitCustomArmor");

        Class<?> hikariDsClass = libLoader.loadClass("com.zaxxer.hikari.HikariDataSource");
        Constructor<?> ctor = hikariDsClass.getConstructor(hikariConfigClass);
        Object ds = ctor.newInstance(hikariConfig);

        dataSource    = (AutoCloseable) ds;
        sqlDataSource = (DataSource) ds;

        // Create table — safe on startup
        try (Connection conn = sqlDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.execute();
        }

        plugin.getLogger().info("[DB] Connected to MariaDB.");
    }

    public void disconnect() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Exception ignored) {}
            plugin.getLogger().info("[DB] Connection pool closed.");
        }
    }

    // -------------------------------------------------------------------------
    // Async operations
    // -------------------------------------------------------------------------

    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = sqlDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_PLAYER)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    cache.put(uuid, rs.getString("piece"),
                            new ArmorData(rs.getInt("level"), rs.getInt("xp")));
                }

            } catch (Exception e) {
                plugin.getLogger().severe("[DB] Load failed for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> savePlayer(UUID uuid, boolean evictAfter) {
        String[] pieces = {"chestplate", "leggings", "boots"};
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = sqlDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(UPSERT)) {

                for (String piece : pieces) {
                    ArmorData data = cache.get(uuid, piece);
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, piece);
                    stmt.setInt(3, data.getLevel());
                    stmt.setDouble(4, data.getXp());
                    stmt.addBatch();
                }
                stmt.executeBatch();

            } catch (Exception e) {
                plugin.getLogger().severe("[DB] Save failed for " + uuid + ": " + e.getMessage());
            }
            if (evictAfter) cache.remove(uuid);
        });
    }

    public void saveAll() {
        plugin.getServer().getOnlinePlayers()
              .forEach(p -> savePlayer(p.getUniqueId(), false));
    }

    // -------------------------------------------------------------------------
    // Reflection helpers
    // -------------------------------------------------------------------------

    private void set(Object obj, String method, String value) throws Exception {
        obj.getClass().getMethod(method, String.class).invoke(obj, value);
    }

    private void setInt(Object obj, String method, int value) throws Exception {
        obj.getClass().getMethod(method, int.class).invoke(obj, value);
    }

    private void setLong(Object obj, String method, long value) throws Exception {
        obj.getClass().getMethod(method, long.class).invoke(obj, value);
    }
}
