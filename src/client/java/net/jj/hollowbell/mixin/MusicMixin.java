package net.jj.hollowbell.mixin;

import net.jj.hollowbell.MusicBoard;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** while any giant's fight theme plays (see MusicBoard), the game's own music stops and waits */
@Mixin(MusicManager.class)
public abstract class MusicMixin {
    @Shadow
    private SoundInstance currentMusic;

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void hollowbell$quiet(CallbackInfo ci) {
        if (MusicBoard.anyFresh(MusicBoard.musicBoard(), System.currentTimeMillis())) {
            // (only when some is playing: each stop pushes the game's next song back five seconds)
            if (currentMusic != null) ((MusicManager) (Object) this).stopPlaying();
            ci.cancel();
        }
    }
}
