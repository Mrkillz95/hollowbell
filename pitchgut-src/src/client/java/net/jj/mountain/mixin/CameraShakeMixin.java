package net.jj.mountain.mixin;

import net.jj.mountain.client.InsideHim;
import net.jj.mountain.client.Shake;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two things move the camera. His feet coming down shove the view about a little, and while you are inside him
 * the view swings out behind and above him, far enough back that you can see the whole of what you are driving.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {
    @Shadow protected abstract void move(float forward, float up, float side);
    @Shadow protected abstract void setPosition(double x, double y, double z);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void mountain_breathes$shake(BlockGetter world, Entity focus, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
        MountainEntity m = InsideHim.him();
        if (m != null && focus == m) {
            // your own mouse still turns the view, and turns him with it
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) setRotation(mc.player.getViewYRot(partial), mc.player.getViewXRot(partial));
            setPosition(Mth.lerp(partial, m.xo, m.getX()), Mth.lerp(partial, m.yo, m.getY()), Mth.lerp(partial, m.zo, m.getZ()));
            float s = m.mountainScale();
            float z = InsideHim.zoom();
            float back = Math.min(600f, (250 * s + 26) * z), up = Math.min(300f, (110 * s + 14) * z);
            move(-back, up, 0);
        }
        float[] o = Shake.offset(partial);
        if (o != null) move(o[2], o[1], o[0]);
    }
}
