package gg.summit.customarmor.listener;

import gg.summit.customarmor.ProcManager;
import gg.summit.customarmor.SummitCustomArmor;
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
    private final SummitCustomArmor plugin;

    public ProcListener(ProcManager procManager, SummitCustomArmor plugin) {
        this.procManager = procManager;
        this.plugin = plugin;
    }

    /** Handles mining and crop harvesting via left-click (BlockBreakEvent). */
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
            if (block.getBlockData() instanceof Ageable ageable) {
                int age = ageable.getAge();
                int max = ageable.getMaximumAge();
                plugin.getLogger().info("[Crop] " + player.getName()
                        + " broke " + type + " age=" + age + "/" + max);
                isCrop = age == max;
            } else {
                plugin.getLogger().info("[Crop] " + player.getName()
                        + " broke " + type + " (no age data)");
                isCrop = true;
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
