package dev.denfry.tickbudget.api;

import java.util.concurrent.CompletableFuture;
import org.bukkit.Chunk;
import org.bukkit.World;

public interface AsyncChunks {

    /** Loads (and generates if needed) the chunk off the main thread. Safe to call from any thread. */
    CompletableFuture<Chunk> loadAsync(World world, int chunkX, int chunkZ);
}
