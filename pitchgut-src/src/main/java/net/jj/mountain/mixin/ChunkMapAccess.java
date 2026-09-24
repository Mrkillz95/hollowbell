package net.jj.mountain.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** lets the tracking mixin still call the game's own check for everything that isn't him */
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Invoker("isChunkTracked")
    boolean mountain_breathes$isChunkTracked(ServerPlayer player, int x, int z);
}
