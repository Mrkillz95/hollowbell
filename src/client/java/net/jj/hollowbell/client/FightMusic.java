package net.jj.hollowbell.client;

import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsPerf;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.MusicBoard;
import net.jj.hollowbell.ThemeHold;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * His fight theme. It plays while you're near an awake one (see ThemeHold: within 96 blocks of his rim by the flat
 * distance, at least 128 blocks from his middle), in any game mode but spectator, fading in over half a second. It
 * keeps on eight seconds after you leave or he dies, then fades out over four. It goes through the music slider, and
 * the game's own music keeps quiet while it plays. Only one giant's theme plays at a time, the nearest (see
 * MusicBoard). Off with fightMusic in the settings.
 *
 * It follows him. The theme is four takes of the same music, exactly as long as each other, started together and
 * kept playing together; only one is heard at a time. By his health: whole, under half, under a quarter, each blended
 * in over four seconds. While he lies sunk on the ground the fourth take plays, lower and heavier.
 */
public final class FightMusic {
    private FightMusic() {}

    private static final SoundEvent[] TAKES = {ModSounds.MUSIC_FIGHT, ModSounds.MUSIC_FIGHT_DARK,
        ModSounds.MUSIC_FIGHT_LAST, ModSounds.MUSIC_FIGHT_SUNK};
    private static final int SUNK = 3;
    /** ticks for one take to blend into the next */
    private static final float BLEND = 80f;

    private static Take[] playing;
    /** how loud each take is in the blend now (0..1) */
    private static final float[] mix = new float[TAKES.length];
    /** the take wanted now, and the one the nearest of them wants */
    private static int want;
    private static int nearTake;
    private static final ThemeHold HOLD = new ThemeHold();
    private static double lastNear = -1;
    /** ticks the theme has been asked for but the sound system hasn't had it running */
    private static int idle;

    static final class Take extends AbstractTickableSoundInstance {
        final int which;
        boolean want = true;
        float fade;

        Take(int which) {
            super(TAKES[which], SoundSource.MUSIC, RandomSource.create());
            this.which = which;
            looping = true;
            delay = 0;
            volume = 0.001f;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            // in over half a second, out over four
            fade = want ? Math.min(1f, fade + 1f / 10f) : fade - 1f / 80f;
            if (fade <= 0f) {
                volume = 0f;
                stop();
                return;
            }
            volume = Math.max(0.001f, fade * mix[which]);
        }

        @Override
        public float getVolume() {
            // (the Giants Guide's music level: 1 changes nothing)
            return super.getVolume() * GiantsPerf.f("detail.music");
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
            if (HOLD.near(d, ThemeHold.range(h.bellScale())) && (best < 0 || d * d < best)) {
                best = d * d;
                nearTake = take(h);
            }
        }
        return best;
    }

    /** the take for how he is now: sunk to the ground, or by his health */
    static int take(HollowbellEntity h) {
        if (h.sunk()) return SUNK;
        float f = h.healthNow() / Math.max(1f, h.healthMax());
        return f <= 0.25f ? 2 : f <= 0.5f ? 1 : 0;
    }

    public static void tick(Minecraft mc) {
        boolean on = BellConfig.V.fightMusic && GiantsPerf.f("detail.music") > 0f;
        double d = on ? nearest(mc) : -1;
        if (d >= 0) {
            lastNear = d;
            want = nearTake;
        }
        boolean wanted = on && HOLD.tick(d >= 0);
        if (wanted) MusicBoard.want(HollowbellMod.MOD_ID, lastNear);
        else MusicBoard.drop(HollowbellMod.MOD_ID);
        long now = System.currentTimeMillis();
        boolean mine = wanted && MusicBoard.plays(MusicBoard.musicBoard(), HollowbellMod.MOD_ID, now);
        if (mine) {
            // (a stream takes a moment to start: only one that has stopped, or never got going, is begun again)
            idle = playing != null && !mc.getSoundManager().isActive(playing[0]) ? idle + 1 : 0;
            if (playing == null || playing[0].isStopped() || idle > 40) start(mc);
            for (Take t : playing) t.want = true;
        } else if (playing != null) {
            for (Take t : playing) t.want = false;
            if (playing[0].isStopped()) playing = null;
        }
        for (int i = 0; i < TAKES.length; i++) {
            float to = i == want ? 1f : 0f;
            mix[i] += Math.max(-1f / BLEND, Math.min(1f / BLEND, to - mix[i]));
        }
    }

    /** a fresh start: straight to the take for how he is now, all of them together */
    private static void start(Minecraft mc) {
        if (playing != null) for (Take t : playing) t.end();
        for (int i = 0; i < TAKES.length; i++) mix[i] = i == want ? 1f : 0f;
        playing = new Take[TAKES.length];
        idle = 0;
        for (int i = 0; i < TAKES.length; i++) {
            playing[i] = new Take(i);
            mc.getSoundManager().play(playing[i]);
        }
    }

    public static void clear() {
        MusicBoard.drop(HollowbellMod.MOD_ID);
        HOLD.reset();
        lastNear = -1;
        if (playing != null) {
            for (Take t : playing) Minecraft.getInstance().getSoundManager().stop(t);
            playing = null;
        }
    }
}
