package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Stops the game hanging on "Saving worlds" when you quit.
 *
 * When a chunk is let go, the game queues a job to save and drop it. If the chunk is wanted again before that job
 * runs (you fly away and straight back, a slow server, a far teleport), the chunk is taken back, but the old job
 * stays queued. While any new land nearby is still being made, the old job finds the chunk "not ready" and puts
 * itself straight back on the queue. In a normal tick that only wastes the rest of the tick. On quitting, the game
 * runs that queue until it is empty, so the old job goes round forever, and the land being made can't finish
 * (it needs the same thread), so it never becomes ready: the game hangs.
 *
 * The old job has nothing left to do once its chunk was taken back (it only ever drops a chunk that is still
 * waiting to be dropped), so here it simply ends. If the chunk is let go again, a new job is queued for it then.
 */
@Mixin(ChunkMap.class)
public abstract class UnloadSpinMixin {
    @Shadow @Final private Long2ObjectLinkedOpenHashMap<ChunkHolder> pendingUnloads;

    @WrapOperation(method = "method_60440", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;scheduleUnload(JLnet/minecraft/server/level/ChunkHolder;)V"))
    private void hollowbell$dropStaleUnload(ChunkMap self, long pos, ChunkHolder holder, Operation<Void> original) {
        if (pendingUnloads.get(pos) != holder) return;       // taken back since: this job is over
        original.call(self, pos, holder);
    }
}
