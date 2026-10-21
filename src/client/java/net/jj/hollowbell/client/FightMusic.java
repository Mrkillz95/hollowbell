package net.jj.hollowbell.client;

import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.MusicBoard;
import net.jj.hollowbell.ThemeHold;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * His fight theme. It plays while you're near an awake one (see ThemeHold: his music range by the flat distance, at
 * least 128 blocks), in any game mode but spectator, fading in over half a second; it keeps on eight seconds after
 * you leave or he dies, then fades out over four. It goes through the music slider, and the game's own music keeps
 * quiet while it plays. Only one giant's theme plays at a time, the nearest (see MusicBoard). Off with fightMusic in
 * the settings.
 */
public final class FightMusic {
    private FightMusic() {}

    private static Theme playing;
    private static final ThemeHold HOLD = new ThemeHold();
    private static double lastNear = -1;
    /** ticks the theme has been asked for but the sound system hasn't had it running */
    private static int idle;

    static final class Theme extends AbstractTickableSoundInstance {
        boolean want = true;

        Theme() {
            super(ModSounds.MUSIC_FIGHT, SoundSource.MUSIC, RandomSource.create());
            looping = true;
            delay = 0;
            volume = 0.001f;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public void tick() {
            // in over half a second, out over four
            volume = want ? Math.min(1f, volume + 1f / 10f) : volume - 1f / 80f;
            if (volume <= 0f) {
                volume = 0f;
                stop();
            }
        }

        void end() {
            stop();
        }
    }

    /** the flat distance squared to the nearest awake one in range of you; or -1 */
    static double nearest(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.player.isSpectator()) return -1;
        double best = -1;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h) || h.isGhost() || h.isDeadOrDying() || !h.awake()) continue;
            double dx = h.getX() - mc.player.getX(), dz = h.getZ() - mc.player.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (HOLD.near(d, ThemeHold.range(h.bellScale())) && (best < 0 || d * d < best)) best = d * d;
        }
        return best;
    }

    public static void tick(Minecraft mc) {
        double d = BellConfig.V.fightMusic ? nearest(mc) : -1;
        if (d >= 0) lastNear = d;
        boolean want = BellConfig.V.fightMusic && HOLD.tick(d >= 0);
        if (want) MusicBoard.want(HollowbellMod.MOD_ID, lastNear);
        else MusicBoard.drop(HollowbellMod.MOD_ID);
        long now = System.currentTimeMillis();
        boolean mine = want && MusicBoard.plays(MusicBoard.musicBoard(), HollowbellMod.MOD_ID, now);
        if (mine) {
            // (a stream takes a moment to start: only one that has stopped, or never got going, is begun again)
            idle = playing != null && !mc.getSoundManager().isActive(playing) ? idle + 1 : 0;
            if (playing == null || playing.isStopped() || idle > 40) {
                if (playing != null) playing.end();
                playing = new Theme();
                idle = 0;
                mc.getSoundManager().play(playing);
            }
            playing.want = true;
        } else if (playing != null) {
            playing.want = false;
            if (playing.isStopped()) playing = null;
        }
    }

    public static void clear() {
        MusicBoard.drop(HollowbellMod.MOD_ID);
        HOLD.reset();
        lastNear = -1;
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
            playing = null;
        }
    }
}
