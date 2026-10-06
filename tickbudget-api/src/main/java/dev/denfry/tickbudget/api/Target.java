package dev.denfry.tickbudget.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/**
 * Designates where a task or action must execute.
 * <p>
 * On Paper, all world-bound targets resolve to the server main thread.
 * On Folia, each target dispatches to its respective region, entity, global, or async scheduler.
 */
public sealed interface Target {

    /** Global region context (server-wide state, console, global tick). */
    record OfGlobal() implements Target {}

    /** Asynchronous background execution pool. */
    record OfAsync() implements Target {}

    /**
     * Region context bound to specific chunk coordinates.
     *
     * @param world the world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     */
    record OfRegion(World world, int chunkX, int chunkZ) implements Target {}

    /**
     * Context bound to a living or world entity.
     *
     * @param entity the entity
     */
    record OfEntity(Entity entity) implements Target {}

    /**
     * Target for global server actions (Paper main thread, Folia GlobalRegionScheduler).
     *
     * @return global target
     */
    static Target global() {
        return new OfGlobal();
    }

    /**
     * Target for off-thread background worker tasks.
     *
     * @return async target
     */
    static Target async() {
        return new OfAsync();
    }

    /**
     * Target bound to the region owning the specified location.
     *
     * @param location the location
     * @return region target
     */
    static Target region(Location location) {
        return new OfRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    /**
     * Target bound to the region owning the specified chunk coordinates.
     *
     * @param world the world
     * @param chunkX chunk X coordinate
     * @param chunkZ chunk Z coordinate
     * @return region target
     */
    static Target region(World world, int chunkX, int chunkZ) {
        return new OfRegion(world, chunkX, chunkZ);
    }

    /**
     * Target bound to the entity and its owning region scheduler.
     *
     * @param entity the entity
     * @return entity target
     */
    static Target entity(Entity entity) {
        return new OfEntity(entity);
    }
}
