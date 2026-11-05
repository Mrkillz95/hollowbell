package net.jj.giants.mixin.client;

import net.jj.giants.client.editor.EditorClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29.2) The sound and music picker on the player's game: a giant's fight music swapped for
 * another giant's (or not played), his ground's sounds off or quieter, his voice higher or deeper. Only the giants'
 * own sounds, only when the server changed them: otherwise each returns at once and nothing changes.
 */
@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"),
            cancellable = true)
    private void giants$picker(SoundInstance sound, CallbackInfo ci) {
        if (EditorClient.silenced(sound)) ci.cancel();
    }

    @Inject(method = "calculatePitch", at = @At("RETURN"), cancellable = true)
    private void giants$pitch(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float k = EditorClient.pitch(sound);
        if (k != 1f) cir.setReturnValue(Mth.clamp(cir.getReturnValueF() * k, 0.5f, 2f));
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"),
            cancellable = true)
    private void giants$volume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float k = EditorClient.volume(sound);
        if (k != 1f) cir.setReturnValue(Mth.clamp(cir.getReturnValueF() * k, 0f, 1f));
    }
}
