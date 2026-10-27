package net.jj.giants.duels.mixin;

import net.jj.giants.duels.NaNCount;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Duel runs only: an entity is never moved to a NaN or infinite position, it stays where it was (and it is counted).
 * A Swarmforge drone's position went NaN against the Hollowbell, and the Hollowbell's line-of-sight check to it then
 * waited for a chunk forever, hanging the server. The count goes in the duel's notes, as that is a finding too.
 */
@Mixin(Entity.class)
public abstract class NoNaNPosition {
    @Inject(method = "setPosRaw", at = @At("HEAD"), cancellable = true)
    private void jjDuels$noNaN(double x, double y, double z, CallbackInfo ci) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            NaNCount.STOPPED.incrementAndGet();
            NaNCount.WHO.add(net.minecraft.world.entity.EntityType.getKey(((Entity) (Object) this).getType()).toString());
            ci.cancel();
        }
    }
}
