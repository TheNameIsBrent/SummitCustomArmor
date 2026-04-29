package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    public boolean tryProc(Player player) {
        int pieces = armorManager.countPieces(player);
        if (pieces == 0) return false;

        double chance = calculateChance(player);
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

    public double calculateChance(Player player) {
        List<org.bukkit.inventory.ItemStack> worn = armorManager.getWornPieces(player);
        if (worn.isEmpty()) return 0;

        double basePer        = plugin.getConfig().getDouble("proc.base-per-piece", 0.02);
        double increasePerLvl = plugin.getConfig().getDouble("proc.increase-per-level", 0.002);
        double maxChance      = plugin.getConfig().getDouble("proc.max-total-chance", 0.15);
        double setBonus       = getSetBonus(worn.size());

        LevelManager levelManager = plugin.getLevelManager();
        double sum = 0;
        for (org.bukkit.inventory.ItemStack piece : worn) {
            int level = levelManager.getLevel(piece);
            sum += basePer + (level * increasePerLvl);
        }

        return Math.min(maxChance, sum * setBonus);
    }

    public double calculateChance(int pieces) {
        double basePer   = plugin.getConfig().getDouble("proc.base-per-piece", 0.02);
        double maxChance = plugin.getConfig().getDouble("proc.max-total-chance", 0.15);
        double setBonus  = getSetBonus(pieces);
        return Math.min(maxChance, (pieces * basePer) * setBonus);
    }

    public double getSetBonus(int pieces) {
        return plugin.getConfig().getDouble("proc.set-bonus." + pieces, 1.0);
    }

    private void executeReward(Player player) {
        ConfigurationSection keysSection = plugin.getConfig().getConfigurationSection("proc.keys");
        if (keysSection == null) {
            plugin.getLogger().warning("[Proc] No keys section found in config.yml!");
            return;
        }

        // Weighted tier selection
        List<String> tiers = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double totalWeight = 0;

        for (String tier : keysSection.getKeys(false)) {
            double w = keysSection.getDouble(tier + ".chance", 0);
            if (w <= 0) continue;
            tiers.add(tier);
            weights.add(w);
            totalWeight += w;
        }

        if (tiers.isEmpty()) {
            plugin.getLogger().warning("[Proc] All key tiers have 0 chance — nothing to give!");
            return;
        }

        double roll = random.nextDouble() * totalWeight;
        String selected = tiers.get(tiers.size() - 1);
        double cumulative = 0;
        for (int i = 0; i < tiers.size(); i++) {
            cumulative += weights.get(i);
            if (roll < cumulative) { selected = tiers.get(i); break; }
        }

        plugin.getLogger().info("[Proc] Selected key tier: " + selected + " for " + player.getName());

        // Send configurable message (supports & color codes, %key% placeholder)
        String rawMsg = keysSection.getString(selected + ".message", "");
        if (!rawMsg.isBlank()) {
            String displayName = keysSection.getString(selected + ".display-name", selected);
            String resolved = rawMsg.replace("%key%", displayName)
                                    .replace("%player%", player.getName());
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(resolved));
        }

        // Execute commands
        List<String> commands = keysSection.getStringList(selected + ".commands");
        for (String command : commands) {
            String resolved = command.replace("%player%", player.getName());
            plugin.getLogger().info("[Proc] Executing: " + resolved);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
        }
    }
}
