package net.jj.hollowbell.test.mixin;

import net.jj.hollowbell.test.SyncLoads;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** for the stall probe: every time the server thread asks for a chunk that isn't loaded and waits for it */
@Mixin(ServerChunkCache.class)
public abstract class SyncLoadWatch {
    @Inject(
            method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"))
    private void hollowbell$watch(int x, int z, ChunkStatus st, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        if (!SyncLoads.on || !load) return;
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        if (Thread.currentThread() != self.getLevel().getServer().getRunningThread()) return;
        if (self.getChunkNow(x, z) != null) return;
        SyncLoads.note(x, z, st);
    }
}
