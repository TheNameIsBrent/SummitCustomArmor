package gg.summit.customarmor.db;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache.
 * Key: UUID + ":" + piece  (e.g. "uuid:chestplate")
 * All gameplay reads/writes use this. DB is only touched async on join/quit/periodic save.
 */
public class PlayerDataCache {

    private final Map<String, ArmorData> cache = new ConcurrentHashMap<>();

    private static String key(UUID uuid, String piece) {
        return uuid + ":" + piece;
    }

    /** Returns cached data, or a fresh default if not present. */
    public ArmorData get(UUID uuid, String piece) {
        return cache.computeIfAbsent(key(uuid, piece), k -> new ArmorData(1, 0));
    }

    /** Stores data for a player+piece. */
    public void put(UUID uuid, String piece, ArmorData data) {
        cache.put(key(uuid, piece), data);
    }

    /** Removes all entries for a player (called after save-on-quit). */
    public void remove(UUID uuid) {
        cache.keySet().removeIf(k -> k.startsWith(uuid + ":"));
    }

    /** Returns true if any data exists for this player. */
    public boolean isLoaded(UUID uuid) {
        return cache.keySet().stream().anyMatch(k -> k.startsWith(uuid + ":"));
    }
}
