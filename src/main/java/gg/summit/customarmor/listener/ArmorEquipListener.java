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
import org.bukkit.Material;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    // Raw slot indices in the player inventory for armor slots
    private static final int SLOT_BOOTS      = 36;
    private static final int SLOT_LEGGINGS   = 37;
    private static final int SLOT_CHESTPLATE = 38;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    // -------------------------------------------------------------------------
    // Right-click equip while NOT in inventory (item in hand, right-click world)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        // Only fires when inventory is CLOSED — open inventory uses InventoryClickEvent
        if (event.getPlayer().getOpenInventory().getTopInventory().getType()
                != org.bukkit.event.inventory.InventoryType.CRAFTING) return;

        Player player = event.getPlayer();

        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Item will be auto-equipped by Minecraft after this event — handle post-equip in next tick
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            postEquip(player, before)
        );
    }

    // -------------------------------------------------------------------------
    // All inventory-based equip methods (drag to slot, shift-click, hotbar swap)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only the player's own inventory screen
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        // Determine if a custom armor item is about to land in an armor slot
        ItemStack candidate = getItemBeingEquipped(event);
        if (candidate == null) return; // not an equip action targeting an armor slot

        // SOULBOUND CHECK — block before the item moves
        if (!armorManager.canWear(candidate, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            postEquip(player, before)
        );
    }

    // -------------------------------------------------------------------------
    // Shared post-equip logic (runs 1 tick after equip, on main thread)
    // -------------------------------------------------------------------------

    private void postEquip(Player player, int piecesBeforeEquip) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean changed = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack worn = armor[i];
            if (!armorManager.isCustomArmor(worn)) continue;
            if (armorManager.getOwner(worn) == null) {
                armorManager.bindOwner(worn, player);
                armor[i] = worn;
                changed = true;
            }
        }

        if (changed) player.getInventory().setArmorContents(armor);

        // Sync owner PDC → cache so it gets saved
        syncOwnerToCache(player, armor);

        // Refresh lore on all worn pieces
        if (plugin.getLevelManager() != null) {
            plugin.getLevelManager().refreshLoreOnWornPieces(player);
        }

        int after = armorManager.countPieces(player);
        if (after > piecesBeforeEquip) sendEquipMessage(player, after);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the item that is about to be placed INTO an armor slot, or null.
     * Only returns non-null when the destination is actually an armor slot.
     * Does NOT return items being removed from armor slots.
     */
    private ItemStack getItemBeingEquipped(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        InventoryAction action = event.getAction();

        // Case 1: player places cursor item onto an armor slot directly
        if (slot >= SLOT_BOOTS && slot <= SLOT_CHESTPLATE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR
                    && armorManager.isCustomArmor(cursor)) {
                return cursor;
            }
            // Also catch number key swap (hotbar → armor slot)
            if (action == InventoryAction.HOTBAR_SWAP) {
                int hotbarButton = event.getHotbarButton();
                if (hotbarButton >= 0) {
                    ItemStack hotbarItem = player(event).getInventory().getItem(hotbarButton);
                    if (armorManager.isCustomArmor(hotbarItem)) return hotbarItem;
                }
            }
            return null; // clicking on armor slot but not placing custom armor
        }

        // Case 2: shift-click from hotbar/inventory → automatically goes to armor slot
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (armorManager.isCustomArmor(current)) return current;
        }

        return null;
    }

    private Player player(InventoryClickEvent event) {
        return (Player) event.getWhoClicked();
    }

    private void syncOwnerToCache(Player player, ItemStack[] armor) {
        if (plugin.getDataCache() == null) return;
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
