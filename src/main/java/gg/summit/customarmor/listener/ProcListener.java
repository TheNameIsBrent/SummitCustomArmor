package gg.summit.customarmor.listener;

import gg.summit.customarmor.LevelManager;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

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
    private final LevelManager levelManager;
    private final SummitCustomArmor plugin;

    public ProcListener(ProcManager procManager, LevelManager levelManager, SummitCustomArmor plugin) {
        this.procManager  = procManager;
        this.levelManager = levelManager;
        this.plugin       = plugin;
    }

    private static final Set<Material> PICKAXES = Set.of(
            Material.WOODEN_PICKAXE,
            Material.STONE_PICKAXE,
            Material.IRON_PICKAXE,
            Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE,
            Material.NETHERITE_PICKAXE
    );

    /** Mining — pickaxe required, block must be mineable by pickaxe. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!PICKAXES.contains(held.getType())) return;

        Material type = event.getBlock().getType();
        if (!Tag.MINEABLE_PICKAXE.isTagged(type)) return;

        levelManager.grantXp(player, "mining");
        procManager.tryProc(player);
    }

    /** Crop harvesting — hoe hits a fully-grown crop, block destruction not required. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropHit(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_BLOCK && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || !HOES.contains(item.getType())) return;

        Block block = event.getClickedBlock();
        if (block == null || !CROP_MATERIALS.contains(block.getType())) return;

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

        Player player = event.getPlayer();
        levelManager.grantXp(player, "farming");
        procManager.tryProc(player);
    }

    /** Fishing */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        levelManager.grantXp(player, "fishing");
        procManager.tryProc(player);
    }
}
