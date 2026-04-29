package gg.summit.customarmor.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final SummitCustomArmor plugin;
    private final PlayerDataCache cache;
    private HikariDataSource dataSource;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS custom_armor_data (
                id   INT AUTO_INCREMENT PRIMARY KEY,
                uuid VARCHAR(36)  NOT NULL,
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

    public DatabaseManager(SummitCustomArmor plugin, PlayerDataCache cache) {
        this.plugin = plugin;
        this.cache  = cache;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Initialises the connection pool and creates the table. Throws on failure. */
    public void connect() throws SQLException {
        ConfigurationSection db = plugin.getConfig().getConfigurationSection("database");
        if (db == null) throw new SQLException("Missing 'database' section in config.yml");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + db.getString("host", "localhost")
                + ":" + db.getInt("port", 3306)
                + "/" + db.getString("database", "summit_customarmor"));
        config.setUsername(db.getString("username", "root"));
        config.setPassword(db.getString("password", ""));
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(10_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("SummitCustomArmor");

        dataSource = new HikariDataSource(config);

        // Create table synchronously on startup — safe here, not during gameplay
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE)) {
            stmt.execute();
        }

        plugin.getLogger().info("[DB] Connected to MariaDB.");
    }

    public void disconnect() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("[DB] Connection pool closed.");
        }
    }

    // -------------------------------------------------------------------------
    // Async operations
    // -------------------------------------------------------------------------

    /**
     * Loads all armor pieces for a player from the DB into the cache.
     * Called async on join. Populates defaults if no row exists.
     */
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(SELECT_PLAYER)) {

                stmt.setString(1, uuid.toString());
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    String piece = rs.getString("piece");
                    int    level = rs.getInt("level");
                    int    xp    = rs.getInt("xp");
                    cache.put(uuid, piece, new ArmorData(level, xp));
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("[DB] Failed to load player " + uuid + ": " + e.getMessage());
            }
        });
    }

    /**
     * Saves all cached armor data for a player to the DB.
     * Called async on quit and by the periodic save task.
     */
    public CompletableFuture<Void> savePlayer(UUID uuid, boolean evictAfter) {
        // Snapshot the pieces to save — do this on whatever thread calls savePlayer.
        // The pieces array is fixed; ArmorData fields are volatile so reads are safe.
        String[] pieces = {"chestplate", "leggings", "boots"};

        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
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

            } catch (SQLException e) {
                plugin.getLogger().severe("[DB] Failed to save player " + uuid + ": " + e.getMessage());
            }

            if (evictAfter) cache.remove(uuid);
        });
    }

    /**
     * Saves all online players asynchronously (periodic task).
     */
    public void saveAll() {
        plugin.getServer().getOnlinePlayers().forEach(p ->
                savePlayer(p.getUniqueId(), false));
    }
}
