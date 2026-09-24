package net.jj.mountain.mixin;

import net.jj.mountain.client.InsideHim;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you are inside him the scroll wheel pulls the view in and out instead of picking an item. */
@Mixin(MouseHandler.class)
public class DriveZoomMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true, require = 0)
    private void mountain_breathes$zoom(long window, double dx, double dy, CallbackInfo ci) {
        if (InsideHim.inside() && dy != 0) {
            InsideHim.zoomBy(dy);
            ci.cancel();
        }
    }
}
