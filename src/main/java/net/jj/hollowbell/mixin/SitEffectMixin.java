package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.Sitting;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sitting on him: none of his harmful effects take hold on you. */
@Mixin(LivingEntity.class)
public abstract class SitEffectMixin {
    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void hollowbell$sitterSpared(MobEffectInstance fx, Entity from, CallbackInfoReturnable<Boolean> cir) {
        if (Sitting.blocksEffect((LivingEntity) (Object) this, fx, from)) cir.setReturnValue(false);
    }
}
