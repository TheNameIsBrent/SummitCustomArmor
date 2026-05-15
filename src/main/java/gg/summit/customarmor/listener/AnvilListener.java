package gg.summit.customarmor.listener;

import gg.summit.customarmor.ArmorManager;
import gg.summit.customarmor.SummitCustomArmor;
import gg.summit.customarmor.UnbindScrollManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

public class AnvilListener implements Listener {

    private final ArmorManager armorManager;
    private final UnbindScrollManager scrollManager;

    public AnvilListener(SummitCustomArmor plugin) {
        this.armorManager  = plugin.getArmorManager();
        this.scrollManager = plugin.getUnbindScrollManager();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack first  = inv.getItem(0);
        ItemStack second = inv.getItem(1);

        if (isBlocked(first) || isBlocked(second)) {
            event.setResult(null); // clear the output slot — nothing can be crafted
        }
    }

    private boolean isBlocked(ItemStack item) {
        return armorManager.isCustomArmor(item) || scrollManager.isUnbindScroll(item);
    }
}
