package net.jj.hollowbell.client;

import net.jj.hollowbell.LandMusic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * (leak-fix) One of his land's tracks, played by the game's music player as its own music (so the game's waits and
 * its music slider work as ever), but faded out once you leave his land or die: see {@link LandMusic}.
 */
public final class LandSong extends AbstractTickableSoundInstance {
    private float fade = 1f;

    public LandSong(SoundEvent e) {
        super(e, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
        // (as the game's own music: once through, from nowhere in particular)
        this.looping = false;
        this.delay = 0;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = 1f;
    }

    @Override
    public void tick() {
        fade = LandMusic.fade(fade, LandMusic.wanted(Minecraft.getInstance().player));
        volume = fade;
        if (fade <= 0f) stop();
    }

    /** how far up it is (0..1) */
    public float fade() {
        return fade;
    }
}
