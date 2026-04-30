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
import org.bukkit.inventory.PlayerInventory;

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

    // =========================================================================
    // DEBUG — log every inventory click involving custom armor
    // =========================================================================
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClickDebug(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor  = event.getCursor();
        ItemStack current = event.getCurrentItem();

        boolean cursorIsArmor  = armorManager.isCustomArmor(cursor);
        boolean currentIsArmor = armorManager.isCustomArmor(current);
        if (!cursorIsArmor && !currentIsArmor) return;

        plugin.getLogger().info("[DEBUG-CLICK] player=" + player.getName()
            + " invType=" + event.getInventory().getType()
            + " rawSlot=" + event.getRawSlot()
            + " slot=" + event.getSlot()
            + " action=" + event.getAction()
            + " hotbarBtn=" + event.getHotbarButton()
            + " cursorIsArmor=" + cursorIsArmor
            + " currentIsArmor=" + currentIsArmor
            + " cancelled=" + event.isCancelled());
    }

    // DEBUG — log right-click interact when holding custom armor
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractDebug(PlayerInteractEvent event) {
        if (!armorManager.isCustomArmor(event.getItem())) return;
        plugin.getLogger().info("[DEBUG-INTERACT] player=" + event.getPlayer().getName()
            + " action=" + event.getAction()
            + " hand=" + event.getHand()
            + " invType=" + event.getPlayer().getOpenInventory().getTopInventory().getType()
            + " cancelled=" + event.isCancelled());
    }

    // DEBUG — log drop attempts of custom armor
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDropDebug(PlayerDropItemEvent event) {
        if (!armorManager.isCustomArmor(event.getItemDrop().getItemStack())) return;
        plugin.getLogger().info("[DEBUG-DROP] player=" + event.getPlayer().getName()
            + " cancelled=" + event.isCancelled());
    }
}
