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
import java.util.UUID;

public class LevelManager {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private PlayerDataCache cache;

    private final NamespacedKey KEY_LEVEL;
    private final NamespacedKey KEY_XP;

    private static final String[] SLOT_PIECE = {"boots", "leggings", "chestplate"};

    // Cached config — rebuilt on reload()
    private int     maxLevel;
    private double  baseXp;
    private double  scaling;
    private int[]   xpTable;       // xpTable[level] = XP required to go from level → level+1
    private List<String> loreTemplate;

    public LevelManager(SummitCustomArmor plugin, ArmorManager armorManager) {
        this.plugin       = plugin;
        this.armorManager = armorManager;
        this.KEY_LEVEL    = new NamespacedKey(plugin, "armor_level");
        this.KEY_XP       = new NamespacedKey(plugin, "armor_xp");
        reload();
    }

    public void setCache(PlayerDataCache cache) { this.cache = cache; }

    /** Rebuilds cached config values. Call after /ca reload. */
    public void reload() {
        maxLevel = plugin.getConfig().getInt("leveling.max-level", 25);
        baseXp   = plugin.getConfig().getDouble("leveling.base-xp", 100);
        scaling  = plugin.getConfig().getDouble("leveling.xp-scaling", 1.5);

        xpTable = new int[maxLevel + 1];
        for (int i = 1; i <= maxLevel; i++) {
            xpTable[i] = (int) Math.round(baseXp * Math.pow(i, scaling));
        }

        loreTemplate = plugin.getConfig().getStringList("armor.lore");
        if (loreTemplate.isEmpty()) {
            loreTemplate = List.of("", "&6Level: %level%/%max_level%",
                    "&eXP: %xp%/%xp_required%", "&7Owner: %owner%");
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void grantXp(Player player, String source) {
        int amount = plugin.getConfig().getInt("leveling.xp-gain." + source, 0);
        if (amount <= 0) return;

        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;

            String piece = SLOT_PIECE[i];
            ArmorData data = (cache != null) ? cache.get(player.getUniqueId(), piece) : dataFromItem(item);
            addXp(item, data, amount);
            if (cache != null) cache.put(player.getUniqueId(), piece, data);
        }

        player.getInventory().setArmorContents(armor);
    }

    public int getLevel(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return 1;
        return item.getItemMeta().getPersistentDataContainer()
                   .getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
    }

    public int getXp(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return 0;
        return item.getItemMeta().getPersistentDataContainer()
                   .getOrDefault(KEY_XP, PersistentDataType.INTEGER, 0);
    }

    public int xpRequired(int level) {
        if (level >= 1 && level <= maxLevel) return xpTable[level];
        return (int) Math.round(baseXp * Math.pow(level, scaling));
    }

    public int maxLevel() { return maxLevel; }

    public void applyInitialLore(ItemMeta meta) {
        applyLore(meta, 1, 0, null);
    }

    public void syncCacheFromItems(Player player) {
        if (cache == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            ArmorData data = cache.get(player.getUniqueId(), SLOT_PIECE[i]);
            data.setLevel(getLevel(item));
            data.setXp(getXp(item));
            UUID owner = armorManager.getOwner(item);
            if (owner != null) data.setOwner(owner);
        }
    }

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
            applyLore(meta, data.getLevel(), data.getXp(), armorManager.getOwner(item));
            item.setItemMeta(meta);
            changed = true;
        }

        if (changed) player.getInventory().setArmorContents(armor);
    }

    /** Refresh lore on a single item (not necessarily worn, e.g. after unbind). */
    public void refreshLoreOnItem(ItemStack item) {
        if (!armorManager.isCustomArmor(item)) return;
        ItemMeta meta = item.getItemMeta();
        int level = meta.getPersistentDataContainer().getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
        int xp    = meta.getPersistentDataContainer().getOrDefault(KEY_XP, PersistentDataType.INTEGER, 0);
        applyLore(meta, level, xp, armorManager.getOwner(item));
        item.setItemMeta(meta);
    }

    /** Refresh lore on all worn custom armor pieces. */
    public void refreshLoreOnWornPieces(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            ItemMeta meta = item.getItemMeta();
            int level = meta.getPersistentDataContainer().getOrDefault(KEY_LEVEL, PersistentDataType.INTEGER, 1);
            int xp    = meta.getPersistentDataContainer().getOrDefault(KEY_XP, PersistentDataType.INTEGER, 0);
            applyLore(meta, level, xp, armorManager.getOwner(item));
            item.setItemMeta(meta);
            changed = true;
        }

        if (changed) player.getInventory().setArmorContents(armor);
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private void addXp(ItemStack item, ArmorData data, int amount) {
        int level = data.getLevel();
        int xp    = data.getXp();

        if (level >= maxLevel) return;

        xp += amount;

        while (level < maxLevel) {
            int required = xpTable[level];
            if (xp < required) break;
            xp -= required;
            level++;
        }

        if (level >= maxLevel) xp = 0;

        data.setLevel(level);
        data.setXp(xp);

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_LEVEL, PersistentDataType.INTEGER, level);
        pdc.set(KEY_XP,    PersistentDataType.INTEGER, xp);
        applyLore(meta, level, xp, armorManager.getOwner(item));
        item.setItemMeta(meta);
    }

    private ArmorData dataFromItem(ItemStack item) {
        return new ArmorData(getLevel(item), getXp(item), null);
    }

    private void applyLore(ItemMeta meta, int level, int xp, UUID owner) {
        boolean maxed    = level >= maxLevel;
        int required     = maxed ? 0 : xpTable[level];
        String ownerName = resolveOwnerName(owner);
        String xpStr     = maxed ? "--" : String.valueOf(xp);
        String reqStr    = maxed ? "--" : String.valueOf(required);
        String lvlStr    = String.valueOf(level);
        String maxStr    = String.valueOf(maxLevel);

        List<Component> lore = new ArrayList<>(loreTemplate.size());
        for (String line : loreTemplate) {
            String resolved = line
                    .replace("%level%",       lvlStr)
                    .replace("%max_level%",   maxStr)
                    .replace("%xp%",          xpStr)
                    .replace("%xp_required%", reqStr)
                    .replace("%owner%",       ownerName);

            lore.add(resolved.isEmpty()
                ? Component.empty()
                : LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(resolved)
                    .decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
    }

    private String resolveOwnerName(UUID owner) {
        if (owner == null) return "Unbound";
        // Online player lookup is cheap
        Player online = plugin.getServer().getPlayer(owner);
        if (online != null) return online.getName();
        // Offline lookup — only called for lore on worn pieces, not hot-path
        // Use cached offline player (no disk I/O if player has joined before)
        String name = plugin.getServer().getOfflinePlayer(owner).getName();
        return name != null ? name : owner.toString().substring(0, 8) + "...";
    }
}
