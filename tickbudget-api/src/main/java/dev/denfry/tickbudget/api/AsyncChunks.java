package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.World;

/**
 * Provides safe asynchronous chunk loading across Paper and Folia.
 */
public interface AsyncChunks {

    /**
     * Loads (and generates if needed) the specified chunk asynchronously. Safe to call from any thread.
     *
     * @param world the world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return future completing with the loaded chunk
     */
    CompletableFuture<Chunk> loadAsync(World world, int chunkX, int chunkZ);
}
