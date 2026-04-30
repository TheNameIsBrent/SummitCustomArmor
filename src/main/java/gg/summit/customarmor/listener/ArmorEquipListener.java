package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    /** Inventory drag/click equip. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        // Check if the item being moved is a custom armor piece
        ItemStack cursor  = event.getCursor();
        ItemStack current = event.getCurrentItem();
        ItemStack relevant = armorManager.isCustomArmor(cursor)  ? cursor
                           : armorManager.isCustomArmor(current) ? current
                           : null;
        if (relevant == null) return;

        checkSoulboundAndSchedule(player, relevant, event);
    }

    /**
     * Right-click equip — player holds armor and right-clicks.
     * This auto-equips the item into the correct armor slot.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        Player player = event.getPlayer();

        // Block if soulbound to someone else — cancel before the game equips it
        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        // Bind on first equip, then schedule the count message
        armorManager.bindOwner(item, player);
        plugin.getServer().getScheduler().runTask(plugin, () ->
            sendEquipMessage(player, armorManager.countPieces(player))
        );
    }

    // -------------------------------------------------------------------------

    private void checkSoulboundAndSchedule(Player player, ItemStack item,
                                            InventoryClickEvent event) {
        if (!armorManager.canWear(item, player)) {
            event.setCancelled(true);
            sendSoulboundMessage(player);
            return;
        }

        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            // Bind if this was a fresh equip into an armor slot
            armorManager.getWornPieces(player).forEach(p -> armorManager.bindOwner(p, player));

            int after = armorManager.countPieces(player);
            if (after > before) sendEquipMessage(player, after);
        });
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
