package net.jj.mountain.mixin;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game only draws an entity if the piece of world it is standing in has already been built on your screen.
 * He is bigger than that: at a large size you stand hundreds of blocks from the one block he counts as standing on,
 * that piece of world is far past your render distance, and so he was not drawn at all, even though every part of
 * him was being sent to you and half of him was towering over your head. He is drawn wherever he is.
 */
@Mixin(LevelRenderer.class)
public abstract class MountainVisibleMixin {
    @Shadow private ClientLevel level;
    @Shadow public abstract boolean isSectionCompiled(BlockPos pos);

    @Unique private final LongOpenHashSet mountain_breathes$spots = new LongOpenHashSet();

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void mountain_breathes$findHim(CallbackInfo ci) {
        mountain_breathes$spots.clear();
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) if (e instanceof MountainEntity) mountain_breathes$spots.add(e.blockPosition().asLong());
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"), require = 0)
    private boolean mountain_breathes$drawHimAnyway(LevelRenderer self, BlockPos pos) {
        if (mountain_breathes$spots.contains(pos.asLong())) return true;
        return isSectionCompiled(pos);
    }
}
