package net.jj.mountain.mixin;

import net.jj.mountain.entity.MountainCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * His back is made of a few huge boxes. The game only looks for things to bump into near where each thing stands,
 * and a box that size stands far from anyone on top of it, so the game never found it and you fell through.
 * This adds the top of his back to what a moving entity can bump into whenever it is near him, and afterwards
 * lifts anything that ended up a little sunk into him back onto the surface.
 */
@Mixin(Entity.class)
public abstract class EntityCollideMixin {
    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> mountain_breathes$hisBack(List<VoxelShape> list, Vec3 movement) {
        Entity self = (Entity) (Object) this;
        return MountainCollision.addHisBack(self, self.getBoundingBox().expandTowards(movement), list);
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void mountain_breathes$ontoHisBack(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 v = cir.getReturnValue();
        Vec3 w = MountainCollision.pushUp((Entity) (Object) this, v);
        if (w != v) cir.setReturnValue(w);
    }
}
