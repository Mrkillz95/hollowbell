package net.jj.hollowbell.test.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** makes the game act on added and removed chunk tickets right away */
@Mixin(ServerChunkCache.class)
public interface ChunkCacheAccess {
    @Invoker("runDistanceManagerUpdates")
    boolean hollowbell$update();
}
