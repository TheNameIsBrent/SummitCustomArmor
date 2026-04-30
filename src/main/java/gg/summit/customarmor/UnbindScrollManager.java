package gg.summit.customarmor;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class UnbindScrollManager {

    private final SummitCustomArmor plugin;
    private final NamespacedKey SCROLL_KEY;

    public UnbindScrollManager(SummitCustomArmor plugin) {
        this.plugin     = plugin;
        this.SCROLL_KEY = new NamespacedKey(plugin, "unbind_scroll");
    }

    /** Builds the unbind scroll ItemStack from config. */
    public ItemStack buildScroll() {
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("unbind-scroll");
        String materialName = cfg != null ? cfg.getString("material", "PAPER") : "PAPER";
        String displayName  = cfg != null ? cfg.getString("name", "&fUnbind Scroll") : "&fUnbind Scroll";
        List<String> loreLines = cfg != null ? cfg.getStringList("lore") : List.of();

        Material material = Material.matchMaterial(materialName);
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(
            LegacyComponentSerializer.legacyAmpersand()
                .deserialize(displayName)
                .decoration(TextDecoration.ITALIC, false)
        );

        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) {
            lore.add(LegacyComponentSerializer.legacyAmpersand()
                .deserialize(line)
                .decoration(TextDecoration.ITALIC, false));
        }
        if (!lore.isEmpty()) meta.lore(lore);

        // Stamp PDC so we can identify it reliably (not just by name)
        meta.getPersistentDataContainer().set(SCROLL_KEY, PersistentDataType.BYTE, (byte) 1);

        item.setItemMeta(meta);
        return item;
    }

    /** Returns true if the item is an unbind scroll. */
    public boolean isUnbindScroll(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .has(SCROLL_KEY, PersistentDataType.BYTE);
    }
}
