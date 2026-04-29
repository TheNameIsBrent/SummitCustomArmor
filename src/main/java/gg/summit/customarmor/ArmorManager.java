package gg.summit.customarmor;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Set;

public class ArmorManager {

    private final SummitCustomArmor plugin;

    /** The PDC key stamped on every custom armor item. */
    private final NamespacedKey CUSTOM_ARMOR_KEY;

    /** Recognized armor piece keys */
    public static final Set<String> PIECES = Set.of("chestplate", "leggings", "boots");

    public ArmorManager(SummitCustomArmor plugin) {
        this.plugin = plugin;
        this.CUSTOM_ARMOR_KEY = new NamespacedKey(plugin, "custom_armor");
    }

    /**
     * Builds an ItemStack for the given armor piece key (e.g. "chestplate").
     * Stamps a PersistentDataContainer tag so the item can be identified later.
     *
     * @param piece the armor piece key as defined in config.yml
     * @return the built ItemStack, or null if the key is unknown / config is missing
     */
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
        ItemMeta meta = item.getItemMeta();

        Component name = LegacyComponentSerializer.legacyAmpersand().deserialize(displayName);
        meta.displayName(name);

        // Stamp the custom armor identifier into the item's PDC
        meta.getPersistentDataContainer().set(CUSTOM_ARMOR_KEY, PersistentDataType.STRING, piece);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns true if the given ItemStack is a Summit CustomArmor piece.
     */
    public boolean isCustomArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .has(CUSTOM_ARMOR_KEY, PersistentDataType.STRING);
    }

    /**
     * Counts how many CustomArmor pieces the player is currently wearing.
     * Checks chestplate, leggings, and boots slots only.
     *
     * @return a value between 0 and 3 (inclusive)
     */
    public int countPieces(Player player) {
        ItemStack[] armorContents = player.getInventory().getArmorContents();
        // armorContents index: 0=boots, 1=leggings, 2=chestplate, 3=helmet
        // We only care about our three pieces (no helmet)
        int count = 0;
        for (int i = 0; i <= 2; i++) {
            if (isCustomArmor(armorContents[i])) count++;
        }
        return count;
    }
}
