package gg.summit.customarmor;

import gg.summit.customarmor.command.CustomArmorCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class SummitCustomArmor extends JavaPlugin {

    private static SummitCustomArmor instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        CustomArmorCommand handler = new CustomArmorCommand(this);
        getCommand("ca").setExecutor(handler);
        getCommand("ca").setTabCompleter(handler);

        getLogger().info("SummitCustomArmor enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("SummitCustomArmor disabled.");
    }

    public static SummitCustomArmor getInstance() {
        return instance;
    }
}
