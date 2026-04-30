package gg.summit.customarmor.db;

import gg.summit.customarmor.SummitCustomArmor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class YamlStorageBackend implements StorageBackend {

    private static final String[] PIECES = {"chestplate", "leggings", "boots"};

    private final SummitCustomArmor plugin;
    private final PlayerDataCache cache;
    private final File dataDir;

    public YamlStorageBackend(SummitCustomArmor plugin, PlayerDataCache cache) {
        this.plugin  = plugin;
        this.cache   = cache;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
    }

    @Override
    public void connect() {
        dataDir.mkdirs();
        plugin.getLogger().info("[Storage] Using YAML backend.");
    }

    @Override
    public void disconnect() {}

    @Override
    public CompletableFuture<Void> loadPlayer(UUID uuid) {
        return CompletableFuture.runAsync(() -> {
            File file = playerFile(uuid);
            if (!file.exists()) return;

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (String piece : PIECES) {
                int  level    = yaml.getInt(piece + ".level", 1);
                int  xp       = yaml.getInt(piece + ".xp", 0);
                String ownerStr = yaml.getString(piece + ".owner", null);
                UUID owner    = ownerStr != null ? UUID.fromString(ownerStr) : null;
                cache.put(uuid, piece, new ArmorData(level, xp, owner));
            }
        });
    }

    @Override
    public CompletableFuture<Void> savePlayer(UUID uuid, boolean evictAfter) {
        // Snapshot on calling thread
        String[]    pieceNames = PIECES;
        ArmorData[] snapshots  = new ArmorData[PIECES.length];
        for (int i = 0; i < PIECES.length; i++) {
            ArmorData d = cache.get(uuid, PIECES[i]);
            snapshots[i] = new ArmorData(d.getLevel(), d.getXp(), d.getOwner());
        }

        return CompletableFuture.runAsync(() -> {
            YamlConfiguration yaml = new YamlConfiguration();
            for (int i = 0; i < pieceNames.length; i++) {
                yaml.set(pieceNames[i] + ".level", snapshots[i].getLevel());
                yaml.set(pieceNames[i] + ".xp",    snapshots[i].getXp());
                UUID owner = snapshots[i].getOwner();
                yaml.set(pieceNames[i] + ".owner", owner != null ? owner.toString() : null);
            }
            try {
                yaml.save(playerFile(uuid));
            } catch (Exception e) {
                plugin.getLogger().severe("[Storage] Failed to save " + uuid + ": " + e.getMessage());
            }
            if (evictAfter) cache.remove(uuid);
        });
    }

    @Override
    public void saveAll() {
        plugin.getServer().getOnlinePlayers()
              .forEach(p -> savePlayer(p.getUniqueId(), false));
    }

    private File playerFile(UUID uuid) {
        return new File(dataDir, uuid + ".yml");
    }
}
