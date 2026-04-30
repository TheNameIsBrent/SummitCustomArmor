package gg.summit.customarmor.db;

import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.configuration.ConfigurationSection;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager implements StorageBackend {

    private final SummitCustomArmor plugin;
    private final PlayerDataCache cache;
    private final ClassLoader libLoader;

    private AutoCloseable dataSource;
    private DataSource sqlDataSource;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS custom_armor_data (
                id    INT AUTO_INCREMENT PRIMARY KEY,
                uuid  VARCHAR(36) NOT NULL,
                piece VARCHAR(16) NOT NULL,
                level INT         NOT NULL,
                xp    DOUBLE      NOT NULL,
                owner VARCHAR(36) DEFAULT NULL,
                UNIQUE KEY unique_player_piece (uuid, piece)
            )""";

    private static final String UPSERT =
            "INSERT INTO custom_armor_data (uuid, piece, level, xp, owner) VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE level = VALUES(level), xp = VALUES(xp), owner = VALUES(owner)";

    private static final String SELECT_PLAYER =
            "SELECT piece, level, xp, owner FROM custom_armor_data WHERE uuid = ?";

    public DatabaseManager(SummitCustomArmor plugin, PlayerDataCache cache, ClassLoader libLoader) {
        this.plugin    = plugin;
        this.cache     = cache;
        this.libLoader = libLoader;
    }

    @Override
    public void connect() throws Exception {
        ConfigurationSection db = plugin.getConfig().getConfigurationSection("database");
        if (db == null) throw new Exception("Missing 'database' section in config.yml");

        String jdbcUrl = "jdbc:mariadb://"
                + db.getString("host", "localhost") + ":"
                + db.getInt("port", 3306) + "/"
                + db.getString("database", "summit_customarmor");

        Class<?> hikariConfigClass = libLoader.loadClass("com.zaxxer.hikari.HikariConfig");
        Object hikariConfig = hikariConfigClass.getDeclaredConstructor().newInstance();

        set(hikariConfig,  "setJdbcUrl",          jdbcUrl);
        set(hikariConfig,  "setUsername",          db.getString("username", "root"));
        set(hikariConfig,  "setPassword",          db.getString("password", ""));
        set(hikariConfig,  "setDriverClassName",   "org.mariadb.jdbc.Driver");
        setInt(hikariConfig,  "setMaximumPoolSize", 10);
        setInt(hikariConfig,  "setMinimumIdle",      2);
        setLong(hikariConfig, "setConnectionTimeout", 10_000L);
        setLong(hikariConfig, "setIdleTimeout",      600_000L);
        setLong(hikariConfig, "setMaxLifetime",    1_800_000L);
        set(hikariConfig,  "setPoolName", "SummitCustomArmor");

        Class<?> hikariDsClass = libLoader.loadClass("com.zaxxer.hikari.HikariDataSource");
        Constructor<?> ctor = hikariDsClass.getConstructor(hikariConfigClass);
        Object ds = ctor.newInstance(hikariConfig);

        dataSource    = (AutoCloseable) ds;
        sqlDataSource = (DataSource) ds;

        try (Connection conn = sqlDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.execute();
        }

        plugin.getLogger().info("[Storage] Connected to MariaDB.");
    }

    @Override
    public void disconnect() {
        if (dataSource != null) {
            try { dataSource.close(); } catch (Exception ignored) {}
            plugin.getLogger().info("[Storage] MariaDB connection pool closed.");
        }
    }

    @Override
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = sqlDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_PLAYER)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String ownerStr = rs.getString("owner");
                    UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
                    cache.put(uuid, rs.getString("piece"),
                            new ArmorData(rs.getInt("level"), rs.getInt("xp"), owner));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Load failed for " + uuid + ": " + e.getMessage());
            }
        });
    }

    @Override
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
                    UUID owner = data.getOwner();
                    stmt.setString(5, owner != null ? owner.toString() : null);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Save failed for " + uuid + ": " + e.getMessage());
            }
            if (evictAfter) cache.remove(uuid);
        });
    }

    @Override
    public void saveAll() {
        plugin.getServer().getOnlinePlayers()
              .forEach(p -> savePlayer(p.getUniqueId(), false));
    }

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
