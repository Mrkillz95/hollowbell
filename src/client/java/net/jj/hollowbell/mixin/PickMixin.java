package net.jj.hollowbell.mixin;

import net.jj.hollowbell.client.BellPick;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** After the game picks what the crosshair is on, check the real blocks of him (see BellPick). */
@Mixin(GameRenderer.class)
public class PickMixin {
    @Inject(method = "pick(F)V", at = @At("TAIL"))
    private void hollowbell$pickHim(float partial, CallbackInfo ci) { BellPick.afterPick(partial); }
}
