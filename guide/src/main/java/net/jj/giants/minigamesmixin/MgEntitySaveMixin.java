package net.jj.giants.minigamesmixin;

import net.jj.giants.minigames.MgLevels;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.storage.EntityStorage;
import net.minecraft.world.level.entity.ChunkEntities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Part 10A) Nothing living in a minigame's world is written to disk either: it goes with the world. */
@Mixin(EntityStorage.class)
public abstract class MgEntitySaveMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @Inject(method = "storeEntities", at = @At("HEAD"), cancellable = true)
    private void jjGiants$noMinigameEntities(ChunkEntities<Entity> entities, CallbackInfo ci) {
        if (MgLevels.temporary(level)) ci.cancel();
    }
}
