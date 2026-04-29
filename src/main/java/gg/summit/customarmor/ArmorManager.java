package gg.summit.customarmor;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Set;

public class ArmorManager {

    private final SummitCustomArmor plugin;

    /** Recognized armor piece keys */
    public static final Set<String> PIECES = Set.of("chestplate", "leggings", "boots");

    public ArmorManager(SummitCustomArmor plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds an ItemStack for the given armor piece key (e.g. "chestplate").
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

        item.setItemMeta(meta);
        return item;
    }
}
