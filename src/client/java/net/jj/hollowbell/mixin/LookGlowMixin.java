package net.jj.hollowbell.mixin;

import net.jj.hollowbell.client.LookAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** the one he was just sent after glows for a moment, on your screen only */
@Mixin(Minecraft.class)
public abstract class LookGlowMixin {
    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void hollowbell$lookGlow(Entity e, CallbackInfoReturnable<Boolean> cir) {
        if (LookAim.glows(e)) cir.setReturnValue(true);
    }
}
