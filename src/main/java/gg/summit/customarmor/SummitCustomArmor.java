package gg.summit.customarmor;

import gg.summit.customarmor.command.CustomArmorCommand;
import gg.summit.customarmor.listener.ProcListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SummitCustomArmor extends JavaPlugin {

    private static SummitCustomArmor instance;
    private ArmorManager armorManager;
    private LevelManager levelManager;
    private ProcManager  procManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        armorManager = new ArmorManager(this);
        levelManager = new LevelManager(this, armorManager);
        procManager  = new ProcManager(this, armorManager);

        // Give ArmorManager access to LevelManager for lore on buildItem
        armorManager.setLevelManager(levelManager);

        CustomArmorCommand handler = new CustomArmorCommand(this, armorManager);
        getCommand("ca").setExecutor(handler);
        getCommand("ca").setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new ProcListener(procManager, levelManager, this), this);

        getLogger().info("SummitCustomArmor enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SummitCustomArmor disabled.");
    }

    public static SummitCustomArmor getInstance() { return instance; }
    public ArmorManager getArmorManager()         { return armorManager; }
    public LevelManager getLevelManager()         { return levelManager; }
    public ProcManager  getProcManager()          { return procManager; }
}
