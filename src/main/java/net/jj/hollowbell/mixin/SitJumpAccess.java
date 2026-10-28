package net.jj.hollowbell.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Sitting on him: the jump key (a rider's game sends it every tick) gets you up. */
@Mixin(LivingEntity.class)
public interface SitJumpAccess {
    @Accessor("jumping")
    boolean hollowbell$sitJumping();
}
