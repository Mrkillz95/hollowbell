package net.jj.hollowbell.mixin;

import net.jj.hollowbell.client.BeingHim;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you are on his crown the scroll wheel pulls the view in and out instead of picking an item. */
@Mixin(MouseHandler.class)
public class DriveZoomMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void hollowbell$zoom(long window, double dx, double dy, CallbackInfo ci) {
        if (BeingHim.inside() && dy != 0) {
            BeingHim.zoomBy(dy);
            ci.cancel();
        }
    }
}
