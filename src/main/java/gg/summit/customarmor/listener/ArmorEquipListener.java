package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import gg.summit.customarmor.db.ArmorData;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
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
import org.bukkit.inventory.PlayerInventory;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    // Raw slot indices in PlayerInventory for the three pieces we track
    private static final int SLOT_BOOTS      = 36;
    private static final int SLOT_LEGGINGS   = 37;
    private static final int SLOT_CHESTPLATE = 38;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    // =========================================================================
    // RIGHT-CLICK equip with item in hand (inventory closed)
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        // Only when inventory is closed — open inventory sends InventoryClickEvent instead
        Player player = event.getPlayer();
        if (player.getOpenInventory().getTopInventory().getType() != InventoryType.CRAFTING) return;

        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Allowed — let Minecraft equip it, then handle post-equip next tick
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
    }

    // =========================================================================
    // INVENTORY CLICK equip — only fires when a custom armor piece is moving
    //   into an armor slot. Every other inventory interaction is ignored.
    // =========================================================================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        ItemStack arriving = getItemArrivingInArmorSlot(event, player);
        if (arriving == null) return; // not an armor-slot equip — ignore completely

        if (!armorManager.canWear(arriving, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
    }

    // =========================================================================
    // Post-equip: bind owner + refresh lore + send message (1 tick after equip)
    // =========================================================================

    private void postEquip(Player player, int countBefore) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean dirty = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            if (armorManager.getOwner(item) == null) {
                armorManager.bindOwner(item, player);
                // Sync owner into cache so it gets persisted
                String piece = armorManager.getPiece(item);
                if (piece != null && plugin.getDataCache() != null) {
                    ArmorData data = plugin.getDataCache().get(player.getUniqueId(), piece);
                    if (data.getOwner() == null) data.setOwner(player.getUniqueId());
                }
                dirty = true;
            }
        }

        if (dirty) player.getInventory().setArmorContents(armor);

        if (plugin.getLevelManager() != null) {
            plugin.getLevelManager().refreshLoreOnWornPieces(player);
        }

        int after = armorManager.countPieces(player);
        if (after > countBefore) sendEquipMessage(player, after);
    }

    // =========================================================================
    // Determine whether a custom armor piece is arriving in an armor slot
    // Returns the arriving item, or null if this click is irrelevant.
    // =========================================================================

    private ItemStack getItemArrivingInArmorSlot(InventoryClickEvent event, Player player) {
        int rawSlot = event.getRawSlot();
        InventoryAction action = event.getAction();
        PlayerInventory inv = player.getInventory();

        // --- Case 1: cursor placed onto an armor slot ---
        if (rawSlot >= SLOT_BOOTS && rawSlot <= SLOT_CHESTPLATE) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR
                    && armorManager.isCustomArmor(cursor)) {
                return cursor;
            }
            // Number-key swap: hotbar item swapped into the armor slot the cursor is hovering over
            if (action == InventoryAction.HOTBAR_SWAP) {
                int button = event.getHotbarButton();
                if (button >= 0) {
                    ItemStack hotbarItem = inv.getItem(button);
                    if (armorManager.isCustomArmor(hotbarItem)) return hotbarItem;
                }
            }
            return null; // cursor is on an armor slot but nothing custom is landing there
        }

        // --- Case 2: shift-click sends item from any non-armor slot to best target ---
        // Only fire if the item is custom armor AND would go to an armor slot
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (!armorManager.isCustomArmor(current)) return null;

            // Make sure the target armor slot is empty (that's where shift-click sends it)
            String piece = armorManager.getPiece(current);
            if (piece == null) return null;
            int targetSlot = armorSlotFor(piece);
            if (targetSlot < 0) return null;
            ItemStack existing = inv.getItem(targetSlot);
            if (existing != null && existing.getType() != Material.AIR) return null; // slot occupied
            return current;
        }

        return null; // any other click — not our concern
    }

    /** Returns the raw inventory slot index for a piece name, or -1. */
    private int armorSlotFor(String piece) {
        return switch (piece) {
            case "boots"      -> SLOT_BOOTS;
            case "leggings"   -> SLOT_LEGGINGS;
            case "chestplate" -> SLOT_CHESTPLATE;
            default           -> -1;
        };
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
