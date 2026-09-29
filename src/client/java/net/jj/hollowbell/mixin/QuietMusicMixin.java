package net.jj.hollowbell.mixin;

import net.jj.hollowbell.sound.MusicBoard;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** while any giant's fight theme plays (see MusicBoard), the game's own music stops and waits */
@Mixin(MusicManager.class)
public abstract class QuietMusicMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hollowbell$quiet(CallbackInfo ci) {
        if (MusicBoard.anyFresh(MusicBoard.musicBoard(), System.currentTimeMillis())) {
            ((MusicManager) (Object) this).stopPlaying();
            ci.cancel();
        }
    }
}
