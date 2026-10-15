package net.jj.hollowbell.test.mixin;

import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.rig.BellState;
import net.jj.hollowbell.test.DevView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** see DevView */
@Mixin(value = BellRig.class, remap = false)
public abstract class ArmsOnlyMixin {
    @Inject(method = "shown", at = @At("HEAD"), cancellable = true)
    private void hollowbell$armsOnly(BellState st, int b, CallbackInfoReturnable<Boolean> cir) {
        if (!DevView.armsOnly) return;
        BellRig.Kind k = ((BellRig) (Object) this).kind[b];
        if (k == BellRig.Kind.STRAND || k == BellRig.Kind.POD || k == BellRig.Kind.EGG) cir.setReturnValue(false);
    }
}
