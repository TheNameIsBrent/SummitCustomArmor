package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.event.player.PlayerArmorChangeEvent;

public class ArmorEquipListener implements Listener {

    private final SummitCustomArmor plugin;
    private final ArmorManager armorManager;

    public ArmorEquipListener(SummitCustomArmor plugin) {
        this.plugin       = plugin;
        this.armorManager = plugin.getArmorManager();
    }

    /**
     * Paper's PlayerArmorChangeEvent fires for ALL equip methods:
     * inventory click, right-click, dispensers, etc.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = event.getNewItem();

        // Only care about custom armor being equipped (not removed)
        if (!armorManager.isCustomArmor(newItem)) return;

        // --- Soulbound check ---
        if (!armorManager.canWear(newItem, player)) {
            event.setCancelled(true);
            String msg = plugin.getConfig().getString(
                    "messages.soulbound-blocked",
                    "&cThis armor belongs to someone else.");
            player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(msg));
            return;
        }

        // --- Bind owner on first equip ---
        boolean justBound = armorManager.bindOwner(newItem, player);
        if (justBound) {
            plugin.getLogger().info("[Soulbound] " + player.getName()
                    + " bound " + event.getSlotType().name());
        }

        // --- Equip message ---
        // Schedule 1 tick so the inventory reflects the new piece
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            int count = armorManager.countPieces(player);
            if (count > 0) sendEquipMessage(player, count);
        });
    }

    private void sendEquipMessage(Player player, int pieces) {
        String raw = plugin.getConfig().getString("messages.equip-" + pieces, "");
        if (raw.isBlank()) return;
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(raw));
    }
}
