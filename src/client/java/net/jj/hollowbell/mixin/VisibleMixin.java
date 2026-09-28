package net.jj.hollowbell.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game only draws an entity if the piece of world under its middle has been built on your screen. He is far
 * bigger than that: you can be under his rim with his middle out past your render distance. He is drawn anyway.
 */
@Mixin(LevelRenderer.class)
public abstract class VisibleMixin {
    @Shadow private ClientLevel level;
    @Shadow public abstract boolean isSectionCompiled(BlockPos pos);
    @Unique private final LongOpenHashSet hollowbell$spots = new LongOpenHashSet();

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void hollowbell$find(CallbackInfo ci) {
        hollowbell$spots.clear();
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) if (e instanceof HollowbellEntity) hollowbell$spots.add(e.blockPosition().asLong());
    }

    @WrapOperation(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;isSectionCompiled(Lnet/minecraft/core/BlockPos;)Z"), require = 0)
    private boolean hollowbell$drawAnyway(LevelRenderer self, BlockPos pos, Operation<Boolean> original) {
        if (hollowbell$spots.contains(pos.asLong())) return true;
        return original.call(self, pos);
    }
}
