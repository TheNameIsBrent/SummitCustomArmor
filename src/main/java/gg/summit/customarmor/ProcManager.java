package gg.summit.customarmor;

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

    // Cached config values — rebuilt on /ca reload
    private double basePer;
    private double increasePerLvl;
    private double maxChance;
    private double[] setBonuses; // index = piece count (0 unused, 1-3 used)

    // Cached key tiers — rebuilt on /ca reload
    private record KeyTier(String id, double weight, String message, String displayName, List<String> commands) {}
    private List<KeyTier> keyTiers = new ArrayList<>();
    private double totalWeight = 0;

    public ProcManager(SummitCustomArmor plugin, ArmorManager armorManager) {
        this.plugin       = plugin;
        this.armorManager = armorManager;
        reload();
    }

    /** Call after /ca reload to refresh cached config. */
    public void reload() {
        basePer        = plugin.getConfig().getDouble("proc.base-per-piece", 0.02);
        increasePerLvl = plugin.getConfig().getDouble("proc.increase-per-level", 0.002);
        maxChance      = plugin.getConfig().getDouble("proc.max-total-chance", 0.15);

        setBonuses = new double[4];
        for (int i = 1; i <= 3; i++) {
            setBonuses[i] = plugin.getConfig().getDouble("proc.set-bonus." + i, 1.0);
        }

        keyTiers.clear();
        totalWeight = 0;
        ConfigurationSection keysSection = plugin.getConfig().getConfigurationSection("proc.keys");
        if (keysSection != null) {
            for (String tier : keysSection.getKeys(false)) {
                double w = keysSection.getDouble(tier + ".chance", 0);
                if (w <= 0) continue;
                String message     = keysSection.getString(tier + ".message", "");
                String displayName = keysSection.getString(tier + ".display-name", tier);
                List<String> cmds  = keysSection.getStringList(tier + ".commands");
                keyTiers.add(new KeyTier(tier, w, message, displayName, cmds));
                totalWeight += w;
            }
        }
    }

    public boolean tryProc(Player player) {
        int pieces = armorManager.countPieces(player);
        if (pieces == 0) return false;

        double chance = calculateChance(player);
        if (random.nextDouble() > chance) return false;

        executeReward(player);
        return true;
    }

    public double calculateChance(Player player) {
        List<org.bukkit.inventory.ItemStack> worn = armorManager.getWornPieces(player);
        if (worn.isEmpty()) return 0;

        LevelManager levelManager = plugin.getLevelManager();
        double sum = 0;
        for (org.bukkit.inventory.ItemStack piece : worn) {
            sum += basePer + (levelManager.getLevel(piece) * increasePerLvl);
        }

        int count = worn.size();
        double bonus = (count >= 1 && count <= 3) ? setBonuses[count] : 1.0;
        return Math.min(maxChance, sum * bonus);
    }

    public double calculateChance(int pieces) {
        double bonus = (pieces >= 1 && pieces <= 3) ? setBonuses[pieces] : 1.0;
        return Math.min(maxChance, (pieces * basePer) * bonus);
    }

    public double getSetBonus(int pieces) {
        return (pieces >= 1 && pieces <= 3) ? setBonuses[pieces] : 1.0;
    }

    private void executeReward(Player player) {
        if (keyTiers.isEmpty()) return;

        double roll = random.nextDouble() * totalWeight;
        KeyTier selected = keyTiers.get(keyTiers.size() - 1);
        double cumulative = 0;
        for (KeyTier tier : keyTiers) {
            cumulative += tier.weight();
            if (roll < cumulative) { selected = tier; break; }
        }

        if (!selected.message().isBlank()) {
            String resolved = selected.message()
                    .replace("%key%",    selected.displayName())
                    .replace("%player%", player.getName());
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(resolved));
        }

        for (String command : selected.commands()) {
            plugin.getServer().dispatchCommand(
                plugin.getServer().getConsoleSender(),
                command.replace("%player%", player.getName())
            );
        }
    }
}
