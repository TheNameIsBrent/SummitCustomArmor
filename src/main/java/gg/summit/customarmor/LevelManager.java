package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class LevelManager {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private final NamespacedKey KEY_LEVEL;
    private final NamespacedKey KEY_XP;

    public LevelManager(SummitCustomArmor plugin, ArmorManager armorManager) {
        this.plugin       = plugin;
        this.armorManager = armorManager;
        this.KEY_LEVEL    = new NamespacedKey(plugin, "armor_level");
        this.KEY_XP       = new NamespacedKey(plugin, "armor_xp");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Grants XP to all worn custom armor pieces.
     * source: "mining", "farming", or "fishing"
     */
    public void grantXp(Player player, String source) {
        int amount = plugin.getConfig().getInt("leveling.xp-gain." + source, 0);
        if (amount <= 0) return;

        ItemStack[] armor = player.getInventory().getArmorContents();

        // indices: 0=boots 1=leggings 2=chestplate (skip 3=helmet)
        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            addXp(item, amount);
        }

        // Write the (mutated) array back so the client sees the lore update
        player.getInventory().setArmorContents(armor);
    }

    /** Level stored on an item (default 1). */
    public int getLevel(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                   .getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
    }

    /** XP stored on an item (default 0). */
    public int getXp(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                   .getOrDefault(KEY_XP, PersistentDataType.INTEGER, 0);
    }

    /** XP required to reach the next level from the given level. */
    public int xpRequired(int level) {
        double baseXp  = plugin.getConfig().getDouble("leveling.base-xp", 100);
        double scaling = plugin.getConfig().getDouble("leveling.xp-scaling", 1.5);
        return (int) Math.round(baseXp * Math.pow(level, scaling));
    }

    public int maxLevel() {
        return plugin.getConfig().getInt("leveling.max-level", 25);
    }

    /**
     * Writes the initial lore (level 1, 0 xp) onto a freshly built item's meta.
     * Called by ArmorManager.buildItem() before setItemMeta.
     */
    public void applyInitialLore(ItemMeta meta) {
        applyLore(meta, 1, 0);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void addXp(ItemStack item, int amount) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int level = pdc.getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int xp    = pdc.getOrDefault(KEY_XP,    PersistentDataType.INTEGER, 0);
        int max   = maxLevel();

        if (level >= max) return; // nothing to do at max level

        xp += amount;

        // Handle multiple level-ups from one XP grant
        while (level < max) {
            int required = xpRequired(level);
            if (xp < required) break;
            xp -= required;
            level++;
        }

        if (level >= max) xp = 0;

        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
        pdc.set(KEY_XP,    PersistentDataType.INTEGER, xp);

        applyLore(meta, level, xp);
        item.setItemMeta(meta);
    }

    private void applyLore(ItemMeta meta, int level, int xp) {
        int max      = maxLevel();
        boolean maxed = level >= max;
        int required  = maxed ? 0 : xpRequired(level);

        String levelLine = "Level: " + level + "/" + max + (maxed ? " (MAX)" : "");
        String xpLine    = maxed ? "XP: --" : "XP: " + xp + "/" + required;

        meta.lore(List.of(
                Component.empty(),
                Component.text(levelLine, NamedTextColor.GOLD)
                         .decoration(TextDecoration.ITALIC, false),
                Component.text(xpLine, NamedTextColor.YELLOW)
                         .decoration(TextDecoration.ITALIC, false)
        ));
    }
}
