package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import gg.summit.customarmor.db.ArmorData;
import gg.summit.customarmor.db.PlayerDataCache;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private final PlayerDataCache cache;

    // Armor slot raw indices in player inventory (36=boots 37=leggings 38=chestplate 39=helmet)
    private static final int SLOT_BOOTS      = 36;
    private static final int SLOT_LEGGINGS   = 37;
    private static final int SLOT_CHESTPLATE = 38;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
        this.cache        = plugin.getDataCache();
    }

    // -------------------------------------------------------------------------
    // RIGHT-CLICK equip (hold item and right-click)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        Player player = event.getPlayer();
        String piece  = getPieceFromItem(item);
        if (piece == null) return;

        ArmorData data = cache.get(player.getUniqueId(), piece);

        if (!armorManager.canWear(data.getOwner(), player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Bind owner if unbound
        if (data.getOwner() == null) {
            data.setOwner(player.getUniqueId());
            if (plugin.getLevelManager() != null) {
                plugin.getLevelManager().refreshLoreOnWornPieces(player);
            }
        }

        // Schedule equip message 1 tick after item auto-equips
        plugin.getServer().getScheduler().runTask(plugin, () ->
            sendEquipMessage(player, armorManager.countPieces(player))
        );
    }

    // -------------------------------------------------------------------------
    // INVENTORY CLICK equip
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        // Determine which item is being moved into an armor slot
        ItemStack moving = getItemMovingToArmorSlot(event);
        if (moving == null || !armorManager.isCustomArmor(moving)) return;

        String piece = getPieceFromItem(moving);
        if (piece == null) return;

        ArmorData data = cache.get(player.getUniqueId(), piece);

        if (!armorManager.canWear(data.getOwner(), player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Bind owner if unbound
        if (data.getOwner() == null) {
            data.setOwner(player.getUniqueId());
        }

        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Refresh lore after equip
            if (plugin.getLevelManager() != null) {
                plugin.getLevelManager().refreshLoreOnWornPieces(player);
            }
            int after = armorManager.countPieces(player);
            if (after > before) sendEquipMessage(player, after);
        });
    }

    // -------------------------------------------------------------------------
    // SHIFT-CLICK from external inventory (chest etc.) into armor slot
    // We block non-owners from picking up and immediately equipping via shift-click
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Determines if this click is moving a custom armor item into an armor slot.
     * Returns the item being placed, or null if not applicable.
     */
    private ItemStack getItemMovingToArmorSlot(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        InventoryAction action = event.getAction();

        // Direct click into an armor slot
        if (slot >= SLOT_BOOTS && slot <= SLOT_CHESTPLATE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) return cursor;
        }

        // Shift-click from hotbar/inventory — item jumps to armor slot automatically
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return event.getCurrentItem();
        }

        return null;
    }

    private String getPieceFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                   .get(new org.bukkit.NamespacedKey(plugin, "custom_armor"),
                        org.bukkit.persistence.PersistentDataType.STRING);
    }

    private void sendEquipMessage(Player player, int pieces) {
        if (pieces <= 0) return;
        String raw = plugin.getConfig().getString("messages.equip-" + pieces, "");
        if (raw.isBlank()) return;
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }

    private void sendSoulboundMessage(Player player) {
        String msg = plugin.getConfig().getString(
                "messages.soulbound-blocked",
                "&cThis armor is soulbound to another player.");
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
