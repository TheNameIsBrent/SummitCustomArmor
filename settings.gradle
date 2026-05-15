package gg.summit.customarmor.db;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction over storage backends (YAML or MariaDB).
 * All implementations must be safe to call from any thread.
 */
public interface StorageBackend {

    /** Called once on plugin enable. May throw if setup fails. */
    void connect() throws Exception;

    /** Called once on plugin disable. */
    void disconnect();

    /** Loads all armor data for a player into the cache. */
    CompletableFuture<Void> loadPlayer(UUID uuid);

    /** Persists all cached armor data for a player. Evicts from cache if requested. */
    CompletableFuture<Void> savePlayer(UUID uuid, boolean evictAfter);

    /** Saves all online players (periodic task). */
    void saveAll();
}
