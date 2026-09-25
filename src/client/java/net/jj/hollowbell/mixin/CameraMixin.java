package net.jj.hollowbell.mixin;

import net.jj.hollowbell.client.BeingHim;
import net.jj.hollowbell.client.Shake;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * His slams shove the view about a little, and while you ride his crown the view swings out behind and above him,
 * far enough back that you can see the whole of what you are driving (the same as the Mountain's).
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void move(float forward, float up, float side);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void hollowbell$view(BlockGetter world, Entity focus, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
        HollowbellEntity m = BeingHim.him();
        if (m != null && focus == m) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) setRotation(mc.player.getViewYRot(partial), mc.player.getViewXRot(partial));
            float s = m.bellScale();
            setPosition(Mth.lerp(partial, m.xo, m.getX()), Mth.lerp(partial, m.yo, m.getY()) + 120 * s, Mth.lerp(partial, m.zo, m.getZ()));
            float z = BeingHim.zoom();
            float back = Math.min(700f, (300 * s + 26) * z), up = Math.min(300f, (80 * s + 10) * z);
            move(-back, up, 0);
        }
        float[] o = Shake.offset(partial);
        if (o != null) move(o[2], o[1], o[0]);
    }
}
