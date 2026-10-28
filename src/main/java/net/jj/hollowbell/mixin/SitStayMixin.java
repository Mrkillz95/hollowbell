package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.SitSeat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sitting on him: crouching doesn't get you up (crouching is how you sit down); jumping does. */
@Mixin(Player.class)
public abstract class SitStayMixin {
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void hollowbell$staySitting(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof SitSeat) cir.setReturnValue(false);
    }
}
