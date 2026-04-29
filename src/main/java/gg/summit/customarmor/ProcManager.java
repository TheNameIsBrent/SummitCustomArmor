package gg.summit.customarmor;

import org.bukkit.entity.Player;

import java.util.Random;

public class ProcManager {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private final Random random = new Random();

    public ProcManager(SummitCustomArmor plugin, ArmorManager armorManager) {
        this.plugin = plugin;
        this.armorManager = armorManager;
    }

    /**
     * Attempts a proc for the given player.
     * Returns true if the proc fired, false otherwise.
     */
    public boolean tryProc(Player player) {
        int pieces = armorManager.countPieces(player);
        if (pieces == 0) return false;

        double chance = calculateChance(pieces);
        double roll   = random.nextDouble();

        plugin.getLogger().info("[Proc] " + player.getName()
                + " | pieces=" + pieces
                + " | chance=" + String.format("%.4f", chance)
                + " | roll=" + String.format("%.4f", roll)
                + " | fired=" + (roll <= chance));

        if (roll > chance) return false;

        executeReward(player);
        return true;
    }

    /**
     * finalChance = min(maxChance, (pieces * baseChance) * setBonus)
     */
    public double calculateChance(int pieces) {
        double baseChance = plugin.getConfig().getDouble("proc.base-chance", 0.02);
        double maxChance  = plugin.getConfig().getDouble("proc.max-chance", 0.15);
        double setBonus   = plugin.getConfig().getDouble("proc.set-bonus." + pieces, 1.0);

        return Math.min(maxChance, (pieces * baseChance) * setBonus);
    }

    public double getSetBonus(int pieces) {
        return plugin.getConfig().getDouble("proc.set-bonus." + pieces, 1.0);
    }

    private void executeReward(Player player) {
        String command = plugin.getConfig().getString("proc.command", "");
        if (command.isBlank()) {
            plugin.getLogger().warning("[Proc] Command is blank — check config.yml proc.command!");
            return;
        }

        String resolved = command.replace("%player%", player.getName());
        plugin.getLogger().info("[Proc] Executing: " + resolved);
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
    }
}
