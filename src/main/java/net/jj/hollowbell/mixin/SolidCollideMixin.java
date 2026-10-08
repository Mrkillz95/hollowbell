package net.jj.hollowbell.mixin;

import net.jj.hollowbell.solid.Solid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The solid kit: his parts aren't blocks of the world, so the game never finds them when it looks for what a moving
 * thing bumps into. This adds the parts of him near it, and afterwards lifts anything a little sunk into him back on top.
 */
@Mixin(Entity.class)
public abstract class SolidCollideMixin {
    @ModifyVariable(method = "collide", at = @At("STORE"), ordinal = 0)
    private List<VoxelShape> hollowbell$solidParts(List<VoxelShape> list, Vec3 movement) {
        Entity self = (Entity) (Object) this;
        return Solid.addShapes(self, self.getBoundingBox().expandTowards(movement), list);
    }

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void hollowbell$ontoSolidParts(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 v = cir.getReturnValue();
        Vec3 w = Solid.pushUp((Entity) (Object) this, v);
        if (w != v) cir.setReturnValue(w);
    }
}
