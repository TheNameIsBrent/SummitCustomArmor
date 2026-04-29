package gg.summit.customarmor;

import gg.summit.customarmor.db.ArmorData;
import gg.summit.customarmor.db.PlayerDataCache;
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
    private PlayerDataCache cache; // set after DatabaseManager is ready

    private final NamespacedKey KEY_LEVEL;
    private final NamespacedKey KEY_XP;

    // Armor slot indices → piece names (matches getArmorContents() order)
    private static final String[] SLOT_PIECE = {"boots", "leggings", "chestplate"};

    public LevelManager(SummitCustomArmor plugin, ArmorManager armorManager) {
        this.plugin       = plugin;
        this.armorManager = armorManager;
        this.KEY_LEVEL    = new NamespacedKey(plugin, "armor_level");
        this.KEY_XP       = new NamespacedKey(plugin, "armor_xp");
    }

    public void setCache(PlayerDataCache cache) {
        this.cache = cache;
    }

    // -------------------------------------------------------------------------
    // Public API — called on the main thread during gameplay
    // -------------------------------------------------------------------------

    /**
     * Grants XP to all worn custom armor pieces.
     * Writes to PDC (for item tooltip) and cache (for DB persistence).
     * source: "mining", "farming", or "fishing"
     */
    public void grantXp(Player player, String source) {
        int amount = plugin.getConfig().getInt("leveling.xp-gain." + source, 0);
        if (amount <= 0) return;

        ItemStack[] armor = player.getInventory().getArmorContents();

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;

            String piece = SLOT_PIECE[i];
            ArmorData data = (cache != null)
                    ? cache.get(player.getUniqueId(), piece)
                    : dataFromItem(item);

            addXp(item, data, amount);

            if (cache != null) cache.put(player.getUniqueId(), piece, data);
        }

        player.getInventory().setArmorContents(armor);
    }

    /** Level for a piece — reads from cache if available, else PDC. */
    public int getLevel(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                   .getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
    }

    /** XP for a piece — reads from PDC. */
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

    /** Writes the initial lore (level 1, 0 xp) onto a freshly built item's meta. */
    public void applyInitialLore(ItemMeta meta) {
        applyLore(meta, 1, 0);
    }

    /**
     * Reads level/xp from the player's worn items and writes them into the cache.
     * Called on quit before saving so the DB gets the latest in-memory state.
     */
    public void syncCacheFromItems(Player player) {
        if (cache == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            ArmorData data = cache.get(player.getUniqueId(), SLOT_PIECE[i]);
            data.setLevel(getLevel(item));
            data.setXp(getXp(item));
        }
    }

    /**
     * Reads level/xp from the cache and writes them onto worn items (PDC + lore).
     * Called after a successful DB load on join so items reflect the stored progress.
     */
    public void syncItemsFromCache(Player player) {
        if (cache == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;

            ArmorData data = cache.get(player.getUniqueId(), SLOT_PIECE[i]);
            ItemMeta meta  = item.getItemMeta();
            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, data.getLevel());
            pdc.set(KEY_XP,    PersistentDataType.INTEGER, data.getXp());
            applyLore(meta, data.getLevel(), data.getXp());
            item.setItemMeta(meta);
            changed = true;
        }

        if (changed) player.getInventory().setArmorContents(armor);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void addXp(ItemStack item, ArmorData data, int amount) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int level = data.getLevel();
        int xp    = data.getXp();
        int max   = maxLevel();

        if (level >= max) return;

        xp += amount;

        while (level < max) {
            int required = xpRequired(level);
            if (xp < required) break;
            xp -= required;
            level++;
        }

        if (level >= max) xp = 0;

        data.setLevel(level);
        data.setXp(xp);

        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
        pdc.set(KEY_XP,    PersistentDataType.INTEGER, xp);
        applyLore(meta, level, xp);
        item.setItemMeta(meta);
    }

    private ArmorData dataFromItem(ItemStack item) {
        return new ArmorData(getLevel(item), getXp(item));
    }

    private void applyLore(ItemMeta meta, int level, int xp) {
        int max       = maxLevel();
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
