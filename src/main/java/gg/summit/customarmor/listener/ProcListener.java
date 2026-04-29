package gg.summit.customarmor.listener;

import gg.summit.customarmor.ProcManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;

public class ProcListener implements Listener {

    // Only trigger on fully-grown crops to avoid rewarding half-grown harvests
    private static final Set<Material> CROP_MATERIALS = Set.of(
            Material.WHEAT,
            Material.CARROTS,      // block is CARROTS at any age
            Material.POTATOES,     // block is POTATOES at any age
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH
    );

    private final ProcManager procManager;

    public ProcListener(ProcManager procManager) {
        this.procManager = procManager;
    }

    /** Handles mining and fully-grown crop harvesting. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block   = event.getBlock();
        Material type = block.getType();

        boolean isMining = Tag.MINEABLE_PICKAXE.isTagged(type)
                        || Tag.MINEABLE_AXE.isTagged(type)
                        || Tag.MINEABLE_SHOVEL.isTagged(type);

        boolean isCrop = false;
        if (CROP_MATERIALS.contains(type)) {
            // Only count fully-grown crops (max age)
            if (block.getBlockData() instanceof Ageable ageable) {
                isCrop = ageable.getAge() == ageable.getMaximumAge();
            } else {
                isCrop = true; // COCOA, SWEET_BERRY_BUSH etc. handled as-is
            }
        }

        if (!isMining && !isCrop) return;

        procManager.tryProc(player);
    }

    /** Fires when a player successfully catches a fish. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        procManager.tryProc(event.getPlayer());
    }
}
