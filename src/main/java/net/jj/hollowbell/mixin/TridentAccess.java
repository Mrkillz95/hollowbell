package net.jj.hollowbell.mixin;

import net.minecraft.world.entity.projectile.ThrownTrident;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A trident that struck him counts as having hit, so Loyalty brings it back the way it does off a mob. */
@Mixin(ThrownTrident.class)
public interface TridentAccess {
    @Accessor("dealtDamage") void hollowbell$setDealtDamage(boolean hit);
}
