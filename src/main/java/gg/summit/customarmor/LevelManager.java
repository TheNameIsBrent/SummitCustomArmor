package gg.summit.customarmor;

import gg.summit.customarmor.db.ArmorData;
import gg.summit.customarmor.db.PlayerDataCache;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
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

    /** Writes the initial lore (level 1, 0 xp, no owner) onto a freshly built item's meta. */
    public void applyInitialLore(ItemMeta meta) {
        applyLore(meta, 1, 0, null);
    }

    /**
     * Reads level/xp from the player's worn items and writes them into the cache.
     * Owner is preserved — we never overwrite it from the item side.
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
            // owner stays in cache — don't touch it here
        }
    }

    /**
     * Reads level/xp/owner from the cache and writes them onto worn items (PDC + lore).
     * Called after a successful DB load on join.
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
            applyLore(meta, data.getLevel(), data.getXp(), data.getOwner());
            item.setItemMeta(meta);
            changed = true;
        }

        if (changed) player.getInventory().setArmorContents(armor);
    }

    /**
     * Refreshes lore on all worn custom armor pieces using the current cache state.
     * Called after owner is bound so the lore updates immediately.
     */
    public void refreshLoreOnWornPieces(Player player) {
        if (cache == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;

            ArmorData data = cache.get(player.getUniqueId(), SLOT_PIECE[i]);
            ItemMeta meta  = item.getItemMeta();
            applyLore(meta, data.getLevel(), data.getXp(), data.getOwner());
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
        applyLore(meta, level, xp, data.getOwner());
        item.setItemMeta(meta);
    }

    private ArmorData dataFromItem(ItemStack item) {
        return new ArmorData(getLevel(item), getXp(item));
    }

    private void applyLore(ItemMeta meta, int level, int xp, java.util.UUID owner) {
        int max       = maxLevel();
        boolean maxed = level >= max;
        int required  = maxed ? 0 : xpRequired(level);

        String ownerName = resolveOwnerName(owner);

        List<String> loreTemplate = plugin.getConfig().getStringList("armor.lore");
        if (loreTemplate.isEmpty()) {
            loreTemplate = List.of("", "&6Level: %level%/%max_level%",
                    "&eXP: %xp%/%xp_required%", "&7Owner: %owner%");
        }

        List<Component> lore = new ArrayList<>();
        for (String line : loreTemplate) {
            String resolved = line
                    .replace("%level%",       String.valueOf(level))
                    .replace("%max_level%",   String.valueOf(max))
                    .replace("%xp%",          maxed ? "--" : String.valueOf(xp))
                    .replace("%xp_required%", maxed ? "--" : String.valueOf(required))
                    .replace("%owner%",       ownerName);

            if (resolved.isEmpty()) {
                lore.add(Component.empty());
            } else {
                lore.add(LegacyComponentSerializer.legacyAmpersand()
                        .deserialize(resolved)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
    }

    private String resolveOwnerName(java.util.UUID owner) {
        if (owner == null) return "Unbound";
        // Try online player first
        org.bukkit.entity.Player online = plugin.getServer().getPlayer(owner);
        if (online != null) return online.getName();
        // Fall back to offline lookup (may return null name for unknown UUIDs)
        org.bukkit.OfflinePlayer offline = plugin.getServer().getOfflinePlayer(owner);
        String name = offline.getName();
        return name != null ? name : owner.toString().substring(0, 8) + "...";
    }
}
