package net.jj.hollowbell.client;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.sound.MusicBoard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * His fight theme. It plays while a fight with him is on near you (he's after a player, or fighting another giant,
 * within 96 blocks times his size, at least 32), fading in over two seconds and out over four once it's over. It
 * goes through the music slider, and the game's own music keeps quiet while it plays. Only one giant's theme plays
 * at a time (see MusicBoard). Off with fightMusic in the settings.
 */
public final class FightMusic {
    private FightMusic() {}

    private static Theme playing;

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
            // in over two seconds, out over four
            volume = want ? Math.min(1f, volume + 1f / 40f) : volume - 1f / 80f;
            if (volume <= 0f) { volume = 0f; stop(); }
        }

        void end() { stop(); }
    }

    /** the nearest one fighting near you, as distance squared; or -1 */
    static double fightNear(Minecraft mc) {
        if (mc.level == null || mc.player == null) return -1;
        double best = -1;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h) || h.isGhost() || h.isDeadOrDying() || !h.fighting()) continue;
            double r = Math.max(32, 96 * h.bellScale());
            double d = h.distanceToSqr(mc.player);
            if (d < r * r && (best < 0 || d < best)) best = d;
        }
        return best;
    }

    public static void tick(Minecraft mc) {
        double d = HollowbellConfig.V.fightMusic ? fightNear(mc) : -1;
        if (d >= 0) MusicBoard.want(HollowbellMod.MOD_ID, d); else MusicBoard.drop(HollowbellMod.MOD_ID);
        boolean mine = d >= 0 && MusicBoard.plays(MusicBoard.musicBoard(), HollowbellMod.MOD_ID, System.currentTimeMillis());
        if (mine) {
            if (playing == null || playing.isStopped() || !mc.getSoundManager().isActive(playing)) {
                if (playing != null) playing.end();
                playing = new Theme();
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
        if (playing != null) { Minecraft.getInstance().getSoundManager().stop(playing); playing = null; }
    }
}
