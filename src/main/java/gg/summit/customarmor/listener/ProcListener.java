package gg.summit.customarmor.listener;

import gg.summit.customarmor.ProcManager;
import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;

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

    private static final Set<Material> HOES = Set.of(
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE
    );

    private final ProcManager procManager;
    private final SummitCustomArmor plugin;

    public ProcListener(ProcManager procManager, SummitCustomArmor plugin) {
        this.procManager = procManager;
        this.plugin = plugin;
    }

    /** Mining — pickaxe, axe, shovel blocks only. Crops excluded here, handled separately. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Material type = event.getBlock().getType();

        boolean isMining = Tag.MINEABLE_PICKAXE.isTagged(type)
                        || Tag.MINEABLE_AXE.isTagged(type)
                        || Tag.MINEABLE_SHOVEL.isTagged(type);

        if (!isMining) return;

        procManager.tryProc(event.getPlayer());
    }

    /**
     * Crop harvesting — fires when a player hits any crop with a hoe,
     * regardless of whether the block is destroyed or protected.
     * Only triggers on fully-grown crops.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropHit(PlayerInteractEvent event) {
        // Only main hand to avoid double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        // Must be a left or right click on a block
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        // Must be holding a hoe
        ItemStack item = event.getItem();
        if (item == null || !HOES.contains(item.getType())) return;

        // Must be a crop block
        Block block = event.getClickedBlock();
        if (block == null || !CROP_MATERIALS.contains(block.getType())) return;

        // Must be fully grown
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) {
                plugin.getLogger().info("[Crop] " + event.getPlayer().getName()
                        + " hit " + block.getType() + " age=" + ageable.getAge()
                        + "/" + ageable.getMaximumAge() + " (not fully grown, skipping)");
                return;
            }
        }

        plugin.getLogger().info("[Crop] " + event.getPlayer().getName()
                + " hit " + block.getType() + " with " + item.getType() + " — attempting proc");

        procManager.tryProc(event.getPlayer());
    }

    /** Fishing. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        procManager.tryProc(event.getPlayer());
    }
}
