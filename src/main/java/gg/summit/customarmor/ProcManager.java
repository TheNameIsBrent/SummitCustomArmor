package gg.summit.customarmor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
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

    /**
     * Selects a key tier via weighted random, then executes its commands.
     * Tiers with chance 0 are excluded entirely.
     * Chances are normalized if they don't sum to 100.
     */
    private void executeReward(Player player) {
        ConfigurationSection keysSection = plugin.getConfig().getConfigurationSection("proc.keys");
        if (keysSection == null) {
            plugin.getLogger().warning("[Proc] No keys section found in config.yml!");
            return;
        }

        // Build a list of eligible tiers (chance > 0)
        List<String> tiers = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;

        for (String tier : keysSection.getKeys(false)) {
            double chance = keysSection.getDouble(tier + ".chance", 0);
            if (chance <= 0) continue;
            tiers.add(tier);
            weights.add(chance);
            totalWeight += chance;
        }

        if (tiers.isEmpty()) {
            plugin.getLogger().warning("[Proc] All key tiers have 0 chance — nothing to give!");
            return;
        }

        // Weighted random selection (normalizes automatically)
        double roll = random.nextDouble() * totalWeight;
        String selected = tiers.get(tiers.size() - 1); // fallback to last
        double cumulative = 0;
        for (int i = 0; i < tiers.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) {
                selected = tiers.get(i);
                break;
            }
        }

        plugin.getLogger().info("[Proc] Selected key tier: " + selected + " for " + player.getName());

        // Execute all commands for the selected tier
        List<String> commands = keysSection.getStringList(selected + ".commands");
        for (String command : commands) {
            String resolved = command.replace("%player%", player.getName());
            plugin.getLogger().info("[Proc] Executing: " + resolved);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
        }
    }
}
