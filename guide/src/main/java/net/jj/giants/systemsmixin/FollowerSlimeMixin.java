package net.jj.giants.systemsmixin;

import net.jj.giants.systems.Followers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Slime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (Part 9) a giant's slime or magma cube follower doesn't split into wild little ones when it dies */
@Mixin(Slime.class)
public abstract class FollowerSlimeMixin {
    @Inject(method = "remove", at = @At("HEAD"))
    private void giants$noSplit(Entity.RemovalReason reason, CallbackInfo ci) {
        Slime s = (Slime) (Object) this;
        if (s.getTags().contains(Followers.MARK) && s.isDeadOrDying() && s.getSize() > 1) s.setSize(1, false);
    }
}
