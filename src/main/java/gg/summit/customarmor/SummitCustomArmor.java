package gg.summit.customarmor;

import gg.summit.customarmor.command.CustomArmorCommand;
import gg.summit.customarmor.db.*;
import gg.summit.customarmor.listener.ArmorEquipListener;
import gg.summit.customarmor.listener.PlayerListener;
import gg.summit.customarmor.listener.ProcListener;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URLClassLoader;

public final class SummitCustomArmor extends JavaPlugin {

    private static SummitCustomArmor instance;
    private ArmorManager    armorManager;
    private LevelManager    levelManager;
    private ProcManager     procManager;
    private StorageBackend  storage;
    private PlayerDataCache dataCache;
    private UnbindScrollManager unbindScrollManager;

    private static final long SAVE_INTERVAL_TICKS = 6000L;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Core managers
        armorManager = new ArmorManager(this);
        levelManager = new LevelManager(this, armorManager);
        procManager  = new ProcManager(this, armorManager);
        unbindScrollManager = new UnbindScrollManager(this);
        armorManager.setLevelManager(levelManager);

        dataCache = new PlayerDataCache();

        // Pick storage backend
        String storageType = getConfig().getString("storage-type", "yaml").toLowerCase();

        if (storageType.equals("mariadb")) {
            LibraryLoader libraryLoader = new LibraryLoader(this);
            URLClassLoader libClassLoader;
            try {
                libClassLoader = libraryLoader.load();
            } catch (Exception e) {
                getLogger().severe("[Storage] Failed to load MariaDB libraries: " + e.getMessage());
                getLogger().severe("[Storage] Falling back to YAML storage.");
                storageType = "yaml";
                libClassLoader = null;
            }

            if (libClassLoader != null) {
                storage = new DatabaseManager(this, dataCache, libClassLoader);
            }
        }

        if (storage == null) {
            storage = new YamlStorageBackend(this, dataCache);
        }

        try {
            storage.connect();
        } catch (Exception e) {
            getLogger().severe("[Storage] Failed to initialise storage: " + e.getMessage());
            getLogger().severe("[Storage] Plugin will disable to prevent data loss.");
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
        getServer().getPluginManager().registerEvents(new ArmorEquipListener(this), this);

        // Periodic async save every 5 minutes
        getServer().getScheduler().runTaskTimerAsynchronously(
                this, storage::saveAll, SAVE_INTERVAL_TICKS, SAVE_INTERVAL_TICKS);

        getLogger().info("SummitCustomArmor enabled.");
    }

    @Override
    public void onDisable() {
        if (storage != null) {
            getServer().getOnlinePlayers().forEach(p -> {
                levelManager.syncCacheFromItems(p);
                storage.savePlayer(p.getUniqueId(), false);
            });
            storage.disconnect();
        }
        getLogger().info("SummitCustomArmor disabled.");
    }

    public static SummitCustomArmor getInstance()        { return instance; }
    public ArmorManager         getArmorManager()        { return armorManager; }
    public LevelManager         getLevelManager()        { return levelManager; }
    public ProcManager          getProcManager()         { return procManager; }
    public StorageBackend       getStorage()             { return storage; }
    public PlayerDataCache      getDataCache()           { return dataCache; }
    public UnbindScrollManager  getUnbindScrollManager() { return unbindScrollManager; }
}
