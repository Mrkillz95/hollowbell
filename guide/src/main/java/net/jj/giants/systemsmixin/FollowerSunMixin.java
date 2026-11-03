package net.jj.giants.systemsmixin;

import net.jj.giants.systems.Followers;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** (Part 9) a giant's follower (husk, drowned, phantom...) isn't burnt by the sun: he keeps his band by day */
@Mixin(Mob.class)
public abstract class FollowerSunMixin {
    @Inject(method = "isSunBurnTick", at = @At("HEAD"), cancellable = true)
    private void giants$followerSun(CallbackInfoReturnable<Boolean> cir) {
        if (((Mob) (Object) this).getTags().contains(Followers.MARK)) cir.setReturnValue(false);
    }
}
