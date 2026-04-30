package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ArmorManager {

    private final SummitCustomArmor plugin;
    private LevelManager levelManager;

    private final NamespacedKey CUSTOM_ARMOR_KEY;
    private final NamespacedKey OWNER_KEY;

    public static final Set<String> PIECES = Set.of("chestplate", "leggings", "boots");

    public ArmorManager(SummitCustomArmor plugin) {
        this.plugin           = plugin;
        this.CUSTOM_ARMOR_KEY = new NamespacedKey(plugin, "custom_armor");
        this.OWNER_KEY        = new NamespacedKey(plugin, "armor_owner");
    }

    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    public ItemStack buildItem(String piece) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("armor." + piece);
        if (section == null) return null;

        String materialName = section.getString("material", "");
        String displayName  = section.getString("name", piece);

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Unknown material '" + materialName + "' for piece: " + piece);
            return null;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();

        // Display name — italic explicitly disabled so it renders without italic
        meta.displayName(
            LegacyComponentSerializer.legacyAmpersand()
                .deserialize(displayName)
                .decoration(TextDecoration.ITALIC, false)
        );

        meta.getPersistentDataContainer().set(CUSTOM_ARMOR_KEY, PersistentDataType.STRING, piece);

        // Leather color support
        if (meta instanceof LeatherArmorMeta leatherMeta && section.contains("color")) {
            String hex = section.getString("color", "#FFFFFF").replace("#", "");
            try {
                int rgb = Integer.parseInt(hex, 16);
                leatherMeta.setColor(Color.fromRGB(rgb));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid color '" + hex + "' for piece: " + piece);
            }
        }

        if (levelManager != null) levelManager.applyInitialLore(meta);

        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    public boolean isCustomArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .has(CUSTOM_ARMOR_KEY, PersistentDataType.STRING);
    }

    // -------------------------------------------------------------------------
    // Soulbound
    // -------------------------------------------------------------------------

    /**
     * Returns the UUID of the player who first equipped this item, or null if unbound.
     */
    public UUID getOwner(ItemStack item) {
        if (!isCustomArmor(item)) return null;
        String stored = item.getItemMeta().getPersistentDataContainer()
                            .get(OWNER_KEY, PersistentDataType.STRING);
        return stored != null ? UUID.fromString(stored) : null;
    }

    /**
     * Binds the item to the given player. No-op if already bound.
     * Returns true if the item was just bound (first equip).
     */
    public boolean bindOwner(ItemStack item, Player player) {
        if (!isCustomArmor(item)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(OWNER_KEY, PersistentDataType.STRING)) {
            return false; // already bound
        }
        meta.getPersistentDataContainer()
            .set(OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        item.setItemMeta(meta);
        return true;
    }

    /**
     * Returns true if the item is unbound OR bound to this player.
     */
    public boolean canWear(ItemStack item, Player player) {
        UUID owner = getOwner(item);
        return owner == null || owner.equals(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Worn piece helpers
    // -------------------------------------------------------------------------

    public int countPieces(Player player) {
        return getWornPieces(player).size();
    }

    public List<ItemStack> getWornPieces(Player player) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i <= 2; i++) {
            if (isCustomArmor(armor[i])) result.add(armor[i]);
        }
        return result;
    }
}
