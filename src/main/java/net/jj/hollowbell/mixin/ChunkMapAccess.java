package net.jj.hollowbell.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** lets the tracking mixin still call the game's own check for everything that isn't him */
@Mixin(ChunkMap.class)
public interface ChunkMapAccess {
    @Invoker("isChunkTracked")
    boolean hollowbell$isChunkTracked(ServerPlayer player, int x, int z);

    /** every entity the game is sending to players, by id (far sight asks who already sees him) */
    @Accessor("entityMap")
    it.unimi.dsi.fastutil.ints.Int2ObjectMap<Object> hollowbell$entityMap();
}
