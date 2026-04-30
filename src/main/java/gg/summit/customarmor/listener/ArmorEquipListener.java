package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import gg.summit.customarmor.db.ArmorData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    private static final int SLOT_BOOTS      = 36;
    private static final int SLOT_LEGGINGS   = 37;
    private static final int SLOT_CHESTPLATE = 38;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    // -------------------------------------------------------------------------
    // Right-click equip
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        Player player = event.getPlayer();

        // Block non-owner before equip happens
        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Bind and update everything 1 tick later (after Minecraft auto-equips)
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Only bind the item that is now in the slot it belongs in
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (int i = 0; i <= 2; i++) {
                ItemStack worn = armor[i];
                if (!armorManager.isCustomArmor(worn)) continue;
                if (armorManager.getOwner(worn) == null) {
                    armorManager.bindOwner(worn, player);
                    // Write updated meta back into the array
                    armor[i] = worn;
                }
            }
            player.getInventory().setArmorContents(armor);

            // Sync owner into cache for storage
            syncOwnerToCache(player);

            // Refresh lore so owner shows up
            if (plugin.getLevelManager() != null) {
                plugin.getLevelManager().refreshLoreOnWornPieces(player);
            }

            int after = armorManager.countPieces(player);
            if (after > before) sendEquipMessage(player, after);
        });
    }

    // -------------------------------------------------------------------------
    // Inventory click equip (drag into slot, shift-click)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        ItemStack moving = getArmorItemBeingEquipped(event);
        if (moving == null || !armorManager.isCustomArmor(moving)) return;

        // Block non-owner immediately
        if (!armorManager.canWear(moving, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Bind and refresh 1 tick after inventory updates
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] armor = player.getInventory().getArmorContents();
            for (int i = 0; i <= 2; i++) {
                ItemStack worn = armor[i];
                if (!armorManager.isCustomArmor(worn)) continue;
                if (armorManager.getOwner(worn) == null) {
                    armorManager.bindOwner(worn, player);
                    armor[i] = worn;
                }
            }
            player.getInventory().setArmorContents(armor);

            syncOwnerToCache(player);

            if (plugin.getLevelManager() != null) {
                plugin.getLevelManager().refreshLoreOnWornPieces(player);
            }

            int after = armorManager.countPieces(player);
            if (after > before) sendEquipMessage(player, after);
        });
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the custom armor item being moved into an armor slot, or null. */
    private ItemStack getArmorItemBeingEquipped(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        InventoryAction action = event.getAction();

        // Clicking directly into an armor slot with cursor
        if (slot >= SLOT_BOOTS && slot <= SLOT_CHESTPLATE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && !cursor.getType().isAir()) return cursor;
        }

        // Shift-click from inventory/hotbar
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            return event.getCurrentItem();
        }

        return null;
    }

    /** Writes the owner UUID from the item's PDC back into the cache (for storage persistence). */
    private void syncOwnerToCache(Player player) {
        if (plugin.getDataCache() == null) return;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            String piece = armorManager.getPiece(item);
            if (piece == null) continue;
            java.util.UUID owner = armorManager.getOwner(item);
            if (owner == null) continue;
            ArmorData data = plugin.getDataCache().get(player.getUniqueId(), piece);
            if (data.getOwner() == null) data.setOwner(owner);
        }
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
