package dev.denfry.tickbudget.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;

/** Where work must run. On Paper this is the main thread; on Folia the matching region scheduler. */
public sealed interface Target {

    record OfGlobal() implements Target {}

    record OfAsync() implements Target {}

    record OfRegion(World world, int chunkX, int chunkZ) implements Target {}

    record OfEntity(Entity entity) implements Target {}

    static Target global() {
        return new OfGlobal();
    }

    static Target async() {
        return new OfAsync();
    }

    static Target region(Location location) {
        return new OfRegion(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    static Target region(World world, int chunkX, int chunkZ) {
        return new OfRegion(world, chunkX, chunkZ);
    }

    static Target entity(Entity entity) {
        return new OfEntity(entity);
    }
}
