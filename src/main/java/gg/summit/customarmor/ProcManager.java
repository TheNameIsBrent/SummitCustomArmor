package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class ProcManager {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private final Random random = new Random();

    // Sound played when the player is wearing all 3 pieces (full set)
    private static final Sound FULL_SET_SOUND = Sound.BLOCK_BEACON_ACTIVATE;

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

        // Play full-set sound if all 3 pieces are worn
        if (pieces == 3) {
            player.playSound(player.getLocation(), FULL_SET_SOUND, 0.6f, 1.2f);
        }

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

    /**
     * finalChance = min(maxChance, sum(base + level * increasePerLevel) * setBonus)
     */
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

    /** Flat chance estimate for /ca check display (level 1 baseline). */
    public double calculateChance(int pieces) {
        double basePer   = plugin.getConfig().getDouble("proc.base-per-piece", 0.02);
        double maxChance = plugin.getConfig().getDouble("proc.max-total-chance", 0.15);
        double setBonus  = getSetBonus(pieces);
        return Math.min(maxChance, (pieces * basePer) * setBonus);
    }

    public double getSetBonus(int pieces) {
        return plugin.getConfig().getDouble("proc.set-bonus." + pieces, 1.0);
    }

    // -------------------------------------------------------------------------
    // Reward execution
    // -------------------------------------------------------------------------

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

        // Action bar message
        String tierDisplay = capitalize(selected);
        Component actionBar = Component.text("✦ You found a " + tierDisplay + " Key! ✦",
                tierColor(selected)).decoration(TextDecoration.BOLD, true);
        player.sendActionBar(actionBar);

        // Sound on proc
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);

        // Particle burst at player location
        spawnProcParticles(player, selected);

        // Execute commands
        List<String> commands = keysSection.getStringList(selected + ".commands");
        for (String command : commands) {
            String resolved = command.replace("%player%", player.getName());
            plugin.getLogger().info("[Proc] Executing: " + resolved);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), resolved);
        }
    }

    private void spawnProcParticles(Player player, String tier) {
        Location loc = player.getLocation().add(0, 1, 0);
        Color color = switch (tier) {
            case "rare"      -> Color.AQUA;
            case "epic"      -> Color.PURPLE;
            case "legendary" -> Color.ORANGE;
            default          -> Color.YELLOW; // common
        };

        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);
        player.getWorld().spawnParticle(Particle.DUST, loc, 30, 0.4, 0.6, 0.4, 0, dust);
    }

    private NamedTextColor tierColor(String tier) {
        return switch (tier) {
            case "rare"      -> NamedTextColor.AQUA;
            case "epic"      -> NamedTextColor.LIGHT_PURPLE;
            case "legendary" -> NamedTextColor.GOLD;
            default          -> NamedTextColor.YELLOW;
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
