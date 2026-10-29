package net.jj.hollowbell.mixin;

import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** the view's own box test without making a box (what isVisible does with one), for Fast drawing */
@Mixin(Frustum.class)
public interface FrustumAccess {
    @Invoker("cubeInFrustum")
    boolean hollowbell$cubeInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ);
}
