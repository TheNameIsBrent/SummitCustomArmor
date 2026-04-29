package gg.summit.customarmor.listener;

import gg.summit.customarmor.ProcManager;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;

import java.util.Set;

public class ProcListener implements Listener {

    private static final Set<Material> CROP_MATERIALS = Set.of(
            Material.WHEAT,
            Material.CARROTS,
            Material.POTATOES,
            Material.BEETROOTS,
            Material.NETHER_WART,
            Material.COCOA,
            Material.SWEET_BERRY_BUSH
    );

    private final ProcManager procManager;

    public ProcListener(ProcManager procManager) {
        this.procManager = procManager;
    }

    /** Handles both mining and crop harvesting (both come through BlockBreakEvent). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        boolean isMining = Tag.MINEABLE_PICKAXE.isTagged(block.getType())
                        || Tag.MINEABLE_AXE.isTagged(block.getType())
                        || Tag.MINEABLE_SHOVEL.isTagged(block.getType());
        boolean isCrop = CROP_MATERIALS.contains(block.getType());

        if (!isMining && !isCrop) return;

        procManager.tryProc(player);
    }

    /** Handles fishing — fires when a player successfully catches something. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        procManager.tryProc(event.getPlayer());
    }
}
