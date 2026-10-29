package net.jj.hollowbell.client.dev.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.jj.hollowbell.client.dev.DrawTrace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (autotest drawcheck) every draw call, written down while he is drawn */
@Mixin(RenderSystem.class)
public abstract class DrawElementsTrace {
    @Inject(method = "drawElements", at = @At("HEAD"), remap = false)
    private static void hollowbell$draw(int mode, int count, int type, CallbackInfo ci) {
        DrawTrace.drawn(mode, count, type);
    }
}
