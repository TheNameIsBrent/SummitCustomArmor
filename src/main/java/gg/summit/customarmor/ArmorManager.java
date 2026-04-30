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

    public static final Set<String> PIECES = Set.of("chestplate", "leggings", "boots");

    // Slot index → piece name (getArmorContents order)
    public static final String[] SLOT_TO_PIECE = {"boots", "leggings", "chestplate"};

    public ArmorManager(SummitCustomArmor plugin) {
        this.plugin           = plugin;
        this.CUSTOM_ARMOR_KEY = new NamespacedKey(plugin, "custom_armor");
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

        meta.displayName(
            LegacyComponentSerializer.legacyAmpersand()
                .deserialize(displayName)
                .decoration(TextDecoration.ITALIC, false)
        );

        meta.getPersistentDataContainer().set(CUSTOM_ARMOR_KEY, PersistentDataType.STRING, piece);

        if (meta instanceof LeatherArmorMeta leatherMeta && section.contains("color")) {
            String hex = section.getString("color", "#FFFFFF").replace("#", "");
            try {
                leatherMeta.setColor(Color.fromRGB(Integer.parseInt(hex, 16)));
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
    // Soulbound — owner lives in the cache/storage, not on the item
    // -------------------------------------------------------------------------

    /**
     * Returns true if the item is unbound OR the cache says this player owns it.
     * itemOwnerUuid is the UUID stored in cache for this item's slot.
     */
    public boolean canWear(UUID itemOwner, Player player) {
        return itemOwner == null || itemOwner.equals(player.getUniqueId());
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
