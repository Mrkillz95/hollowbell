package net.jj.mountain.mixin;

import net.jj.mountain.client.InsideHim;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While you are in him your own body stands still: the keys are driving him, not you. */
@Mixin(KeyboardInput.class)
public abstract class FrozenBodyMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void mountain_breathes$holdStill(boolean slow, float speed, CallbackInfo ci) {
        if (!InsideHim.inside()) return;
        Input in = (Input) (Object) this;
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }
}
