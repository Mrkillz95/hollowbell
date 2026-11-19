package net.jj.hollowbell.mixin;

import net.jj.hollowbell.LandMusic;
import net.jj.hollowbell.client.LandSong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * (leak-fix) His land's music is played as a LandSong, which fades out once you leave his land or die, instead of the
 * game's plain music sound, which would play on to its end wherever you went (LandMusic). The other giants' copies
 * each take only their own tracks, so they chain.
 */
@Mixin(MusicManager.class)
public abstract class LandMusicMixin {
    @Shadow
    private SoundInstance currentMusic;

    @Shadow
    private int nextSongDelay;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void hollowbell$hisLandsSong(Music music, CallbackInfo ci) {
        var e = music.getEvent().value();
        if (!LandMusic.isHisSong(e.getLocation())) return;
        currentMusic = new LandSong(e);
        minecraft.getSoundManager().play(currentMusic);
        nextSongDelay = Integer.MAX_VALUE;
        ci.cancel();
    }
}
