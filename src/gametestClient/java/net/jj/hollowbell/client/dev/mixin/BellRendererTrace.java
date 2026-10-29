package net.jj.hollowbell.client.dev.mixin;

import net.jj.hollowbell.client.dev.DrawTrace;
import net.jj.hollowbell.client.render.BellRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (autotest drawcheck) while he is being drawn: his render and his glass pass */
@Mixin(value = BellRenderer.class, remap = false)
public abstract class BellRendererTrace {
    private static final String RENDER =
            "render(Lnet/jj/hollowbell/entity/HollowbellEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V";

    @Inject(method = RENDER, at = @At("HEAD"))
    private void hollowbell$in(CallbackInfo ci) {
        DrawTrace.inHim++;
    }

    @Inject(method = RENDER, at = @At("RETURN"))
    private void hollowbell$out(CallbackInfo ci) {
        DrawTrace.inHim--;
    }

    @Inject(method = "drawGlass", at = @At("HEAD"))
    private static void hollowbell$glassIn(CallbackInfo ci) {
        DrawTrace.inHim++;
    }

    @Inject(method = "drawGlass", at = @At("RETURN"))
    private static void hollowbell$glassOut(CallbackInfo ci) {
        DrawTrace.inHim--;
    }
}
