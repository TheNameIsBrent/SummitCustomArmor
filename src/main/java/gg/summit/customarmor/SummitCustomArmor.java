package gg.summit.customarmor;

import gg.summit.customarmor.command.CustomArmorCommand;
import gg.summit.customarmor.db.DatabaseManager;
import gg.summit.customarmor.db.PlayerDataCache;
import gg.summit.customarmor.listener.PlayerListener;
import gg.summit.customarmor.listener.ProcListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public final class SummitCustomArmor extends JavaPlugin {

    private static SummitCustomArmor instance;
    private ArmorManager   armorManager;
    private LevelManager   levelManager;
    private ProcManager    procManager;
    private DatabaseManager databaseManager;
    private PlayerDataCache dataCache;

    // Save all online players every 5 minutes (6000 ticks)
    private static final long SAVE_INTERVAL_TICKS = 6000L;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Core managers
        armorManager = new ArmorManager(this);
        levelManager = new LevelManager(this, armorManager);
        procManager  = new ProcManager(this, armorManager);
        armorManager.setLevelManager(levelManager);

        // Database
        dataCache       = new PlayerDataCache();
        databaseManager = new DatabaseManager(this, dataCache);

        try {
            databaseManager.connect();
        } catch (SQLException e) {
            getLogger().severe("[DB] Failed to connect: " + e.getMessage());
            getLogger().severe("[DB] Plugin will disable to prevent data loss.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        levelManager.setCache(dataCache);

        // Commands
        CustomArmorCommand handler = new CustomArmorCommand(this, armorManager);
        getCommand("ca").setExecutor(handler);
        getCommand("ca").setTabCompleter(handler);

        // Listeners
        getServer().getPluginManager().registerEvents(new ProcListener(procManager, levelManager, this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        // Periodic async save task
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, databaseManager::saveAll, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        getLogger().info("SummitCustomArmor enabled.");
    }

    @Override
    public void onDisable() {
        // Save all online players synchronously-ish before shutdown
        if (databaseManager != null) {
            getServer().getOnlinePlayers().forEach(p -> {
                levelManager.syncCacheFromItems(p);
                // Fire async but don't wait — best effort on shutdown
                databaseManager.savePlayer(p.getUniqueId(), false);
            });
            databaseManager.disconnect();
        }
        getLogger().info("SummitCustomArmor disabled.");
    }

    public static SummitCustomArmor getInstance()     { return instance; }
    public ArmorManager    getArmorManager()          { return armorManager; }
    public LevelManager    getLevelManager()          { return levelManager; }
    public ProcManager     getProcManager()           { return procManager; }
    public DatabaseManager getDatabaseManager()       { return databaseManager; }
    public PlayerDataCache getDataCache()             { return dataCache; }
}
