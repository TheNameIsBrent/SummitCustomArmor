package gg.summit.customarmor.listener;

import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final SummitCustomArmor plugin;

    public PlayerListener(SummitCustomArmor plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        plugin.getDatabaseManager()
              .loadPlayer(player.getUniqueId())
              .thenRun(() ->
                  // Back onto the main thread to mutate inventory safely
                  plugin.getServer().getScheduler().runTask(plugin, () ->
                      plugin.getLevelManager().syncItemsFromCache(player)
                  )
              )
              .exceptionally(ex -> {
                  plugin.getLogger().severe("[DB] Load failed for "
                          + player.getName() + ": " + ex.getMessage());
                  return null;
              });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Sync PDC → cache before saving
        plugin.getLevelManager().syncCacheFromItems(player);

        plugin.getDatabaseManager()
              .savePlayer(player.getUniqueId(), true)
              .exceptionally(ex -> {
                  plugin.getLogger().severe("[DB] Save failed for "
                          + player.getName() + ": " + ex.getMessage());
                  return null;
              });
    }
}
