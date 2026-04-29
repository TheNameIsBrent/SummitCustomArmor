package gg.summit.customarmor;

import gg.summit.customarmor.command.CustomArmorCommand;
import gg.summit.customarmor.listener.ProcListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class SummitCustomArmor extends JavaPlugin {

    private static SummitCustomArmor instance;
    private ArmorManager armorManager;
    private ProcManager procManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        armorManager = new ArmorManager(this);
        procManager  = new ProcManager(this, armorManager);

        CustomArmorCommand handler = new CustomArmorCommand(this, armorManager);
        getCommand("ca").setExecutor(handler);
        getCommand("ca").setTabCompleter(handler);

        getServer().getPluginManager().registerEvents(new ProcListener(procManager), this);

        getLogger().info("SummitCustomArmor enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SummitCustomArmor disabled.");
    }

    public static SummitCustomArmor getInstance() { return instance; }
    public ArmorManager getArmorManager()         { return armorManager; }
    public ProcManager getProcManager()           { return procManager; }
}
