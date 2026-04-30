package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import gg.summit.customarmor.UnbindScrollManager;
import gg.summit.customarmor.db.ArmorData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;
    private final UnbindScrollManager scrollManager;

    // rawSlot values for armor slots when the player inventory screen is open
    private static final int RAW_SLOT_CHESTPLATE = 6;
    private static final int RAW_SLOT_LEGGINGS   = 7;
    private static final int RAW_SLOT_BOOTS      = 8;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin        = plugin;
        this.armorManager  = plugin.getArmorManager();
        this.scrollManager = plugin.getUnbindScrollManager();
    }

    // =========================================================================
    // UNBIND SCROLL — drag scroll onto armor piece anywhere EXCEPT armor slots
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUnbindScroll(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        if (!scrollManager.isUnbindScroll(cursor)) return;

        // Block scroll use on armor slots — player would equip unbound armor
        int raw = event.getRawSlot();
        if (raw == RAW_SLOT_CHESTPLATE || raw == RAW_SLOT_LEGGINGS || raw == RAW_SLOT_BOOTS) {
            event.setCancelled(true);
            return;
        }

        ItemStack target = event.getCurrentItem();
        if (!armorManager.isCustomArmor(target)) return;

        event.setCancelled(true);

        if (armorManager.getOwner(target) == null) {
            player.sendMessage(Component.text("This armor piece is not soulbound.", NamedTextColor.YELLOW));
            return;
        }

        // Remove owner from PDC
        ItemMeta meta = target.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey(plugin, "armor_owner"));
        target.setItemMeta(meta);

        // Clear from cache
        String piece = armorManager.getPiece(target);
        if (piece != null && plugin.getDataCache() != null) {
            plugin.getDataCache().get(player.getUniqueId(), piece).setOwner(null);
        }

        // Refresh lore
        plugin.getLevelManager().refreshLoreOnItem(target);

        // Consume one scroll
        if (cursor.getAmount() > 1) {
            cursor.setAmount(cursor.getAmount() - 1);
        } else {
            event.getView().setCursor(null);
        }

        player.sendMessage(Component.text("Soulbound removed from armor piece.", NamedTextColor.GREEN));
    }

    // =========================================================================
    // Always allow dropping custom armor
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (!armorManager.isCustomArmor(event.getItemDrop().getItemStack())) return;
        event.setCancelled(false);
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

        event.setCancelled(false);
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> postEquip(player, before));
    }

    // =========================================================================
    // INVENTORY CLICK — place cursor onto armor slot, number-swap, shift-click
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING) return;

        int rawSlot = event.getRawSlot();
        InventoryAction action = event.getAction();

        // Cursor placed onto an armor slot
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
            // Number-key swap onto armor slot
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

        // Shift-click from hotbar or inventory
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
    // Post-equip: bind + cache sync + lore + message (1 tick after equip)
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

        plugin.getLevelManager().refreshLoreOnWornPieces(player);

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
        String msg = plugin.getConfig().getString("messages.soulbound-blocked",
                "&cThis armor is soulbound to another player.");
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
    }
}
