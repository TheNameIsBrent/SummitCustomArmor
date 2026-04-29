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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ArmorManager {

    private final SummitCustomArmor plugin;
    private LevelManager levelManager; // set after construction to avoid circular dependency

    private final NamespacedKey CUSTOM_ARMOR_KEY;

    public static final Set<String> PIECES = Set.of("chestplate", "leggings", "boots");

    public ArmorManager(SummitCustomArmor plugin) {
        this.plugin          = plugin;
        this.CUSTOM_ARMOR_KEY = new NamespacedKey(plugin, "custom_armor");
    }

    /** Called by SummitCustomArmor after both managers are constructed. */
    public void setLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    /**
     * Builds a custom armor ItemStack, stamping PDC identity and initial lore.
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
        ItemMeta meta  = item.getItemMeta();

        meta.displayName(LegacyComponentSerializer.legacyAmpersand().deserialize(displayName));
        meta.getPersistentDataContainer().set(CUSTOM_ARMOR_KEY, PersistentDataType.STRING, piece);

        if (levelManager != null) levelManager.applyInitialLore(meta);

        item.setItemMeta(meta);
        return item;
    }

    /** Returns true if the item is a Summit CustomArmor piece. */
    public boolean isCustomArmor(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta()
                   .getPersistentDataContainer()
                   .has(CUSTOM_ARMOR_KEY, PersistentDataType.STRING);
    }

    /**
     * Counts how many CustomArmor pieces the player is wearing (0–3).
     */
    public int countPieces(Player player) {
        int count = 0;
        for (ItemStack item : getWornPieces(player)) count++;
        return count;
    }

    /**
     * Returns all CustomArmor ItemStacks currently worn by the player.
     * Only checks boots, leggings, chestplate slots.
     */
    public List<ItemStack> getWornPieces(Player player) {
        List<ItemStack> result = new ArrayList<>();
        ItemStack[] armor = player.getInventory().getArmorContents();
        // 0=boots 1=leggings 2=chestplate 3=helmet
        for (int i = 0; i <= 2; i++) {
            if (isCustomArmor(armor[i])) result.add(armor[i]);
        }
        return result;
    }
}
