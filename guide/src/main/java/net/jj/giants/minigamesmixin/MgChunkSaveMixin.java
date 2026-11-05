package net.jj.giants.minigamesmixin;

import net.jj.giants.minigames.MgLevels;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 10A) A minigame's world is never written to disk: its land is thrown away with it when the round ends, so
 * no chunk of it is saved (not when it unloads, not when the world closes). Every other world as always.
 */
@Mixin(ChunkMap.class)
public abstract class MgChunkSaveMixin {
    @Shadow
    @Final
    ServerLevel level;

    @Inject(method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z", at = @At("HEAD"), cancellable = true)
    private void jjGiants$noMinigameSave(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (MgLevels.temporary(level)) cir.setReturnValue(false);
    }
}
