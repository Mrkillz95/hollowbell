package net.jj.hollowbell.client.dev.mixin;

import com.mojang.blaze3d.shaders.Uniform;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.jj.hollowbell.client.dev.DrawTrace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** (autotest drawcheck) what each uniform last sent to the card */
@Mixin(Uniform.class)
public abstract class UniformTrace {
    @Shadow
    @Final
    private int type;

    @Shadow
    @Final
    private IntBuffer intValues;

    @Shadow
    @Final
    private FloatBuffer floatValues;

    @Inject(method = "upload", at = @At("HEAD"))
    private void hollowbell$upload(CallbackInfo ci) {
        if (DrawTrace.recording()) DrawTrace.uploaded((Uniform) (Object) this, type, intValues, floatValues);
    }
}
