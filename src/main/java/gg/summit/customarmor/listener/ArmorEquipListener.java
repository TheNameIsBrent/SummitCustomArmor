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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    // Armor slot indices when inventory screen is OPEN (invType=CRAFTING)
    // slot field (not rawSlot) for the armor slots
    private static final int OPEN_SLOT_BOOTS      = 36;
    private static final int OPEN_SLOT_LEGGINGS   = 37;
    private static final int OPEN_SLOT_CHESTPLATE = 38;

    // The armor slots have rawSlot values of 5,6,7,8 when inventory is open
    // boots=8, leggings=7, chestplate=6, helmet=5
    private static final int RAW_SLOT_CHESTPLATE = 6;
    private static final int RAW_SLOT_LEGGINGS   = 7;
    private static final int RAW_SLOT_BOOTS      = 8;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    // DEBUG - keep for one more round to verify right-click cancel source
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractDebugLowest(PlayerInteractEvent event) {
        if (!armorManager.isCustomArmor(event.getItem())) return;
        plugin.getLogger().info("[DEBUG-INTERACT-LOWEST] cancelled=" + event.isCancelled()
            + " action=" + event.getAction() + " hand=" + event.getHand());
    }

    // =========================================================================
    // RIGHT-CLICK equip (inventory closed, item in hand)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;
        if (event.getPlayer().getOpenInventory().getTopInventory().getType()
                != InventoryType.CRAFTING) return;

        Player player = event.getPlayer();

        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Un-cancel in case another plugin cancelled it, then handle post-equip
        event.setCancelled(false);
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
    }

    // =========================================================================
    // INVENTORY CLICK — place cursor onto armor slot (drag = PICKUP then PLACE)
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING) return;

        int rawSlot = event.getRawSlot();
        InventoryAction action = event.getAction();

        // --- Placing cursor item onto an armor slot (rawSlot 6,7,8) ---
        if (rawSlot == RAW_SLOT_CHESTPLATE || rawSlot == RAW_SLOT_LEGGINGS
                || rawSlot == RAW_SLOT_BOOTS) {
            ItemStack cursor = event.getCursor();
            if (cursor != null && cursor.getType() != Material.AIR
                    && armorManager.isCustomArmor(cursor)) {
                if (!armorManager.canWear(cursor, player)) {
                    event.setCancelled(true);
                    sendSoulboundMessage(player);
                    return;
                }
                int before = armorManager.countPieces(player);
                plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
                return;
            }
            // Hotbar number-key swap onto armor slot
            if (action == InventoryAction.HOTBAR_SWAP) {
                int btn = event.getHotbarButton();
                if (btn >= 0) {
                    ItemStack hotbarItem = player.getInventory().getItem(btn);
                    if (armorManager.isCustomArmor(hotbarItem)) {
                        if (!armorManager.canWear(hotbarItem, player)) {
                            event.setCancelled(true);
                            sendSoulboundMessage(player);
                            return;
                        }
                        int before = armorManager.countPieces(player);
                        plugin.getServer().getScheduler().runTask(plugin,
                                () -> postEquip(player, before));
                    }
                }
            }
            return;
        }

        // --- Shift-click from hotbar or inventory → goes to armor slot ---
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            ItemStack current = event.getCurrentItem();
            if (!armorManager.isCustomArmor(current)) return;
            if (!armorManager.canWear(current, player)) {
                event.setCancelled(true);
                sendSoulboundMessage(player);
                return;
            }
            int before = armorManager.countPieces(player);
            plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
        }
    }

    // =========================================================================
    // Post-equip: bind + lore + message (1 tick after equip)
    // =========================================================================
    private void postEquip(Player player, int countBefore) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        boolean dirty = false;

        for (int i = 0; i <= 2; i++) {
            ItemStack item = armor[i];
            if (!armorManager.isCustomArmor(item)) continue;
            if (armorManager.getOwner(item) == null) {
                armorManager.bindOwner(item, player);
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
