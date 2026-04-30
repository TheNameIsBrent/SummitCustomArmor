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

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    /** Inventory drag/click equip. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        scheduleCheck(player);
    }

    /** Right-click equip (holding armor and right-clicking in the world). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRightClickEquip(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR &&
            event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // Only care if holding a custom armor piece
        var item = event.getItem();
        if (!armorManager.isCustomArmor(item)) return;

        scheduleCheck(event.getPlayer());
    }

    private void scheduleCheck(Player player) {
        int before = armorManager.countPieces(player);
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int after = armorManager.countPieces(player);
            if (after > before && after > 0) {
                sendEquipMessage(player, after);
            }
        });
    }

    private void sendEquipMessage(Player player, int pieces) {
        String raw = plugin.getConfig().getString("messages.equip-" + pieces, "");
        if (raw.isBlank()) return;
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }
}
