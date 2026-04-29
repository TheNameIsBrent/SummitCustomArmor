package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    /**
     * Catch armor being clicked into or out of armor slots via inventory.
     * We compare piece count before and after the click.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Only care about clicks in the player's own inventory
        if (event.getInventory().getType() != InventoryType.CRAFTING &&
            event.getInventory().getType() != InventoryType.PLAYER) return;

        int before = armorManager.countPieces(player);

        // We need to check after the inventory has updated — schedule 1 tick later
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int after = armorManager.countPieces(player);
            if (after != before && after > 0) {
                sendEquipMessage(player, after);
            }
        });
    }

    private void sendEquipMessage(Player player, int pieces) {
        String key = "messages.equip-" + pieces;
        String raw = plugin.getConfig().getString(key, "");
        if (raw.isBlank()) return;

        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }
}
