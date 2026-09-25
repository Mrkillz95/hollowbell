package net.jj.hollowbell.client;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Seat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

/**
 * The sounds that go on all the time: his low hum and the wet sound of him drifting (louder the faster he goes),
 * both following him and deeper the bigger he is; and, while you're inside his dome, a muffled heartbeat with the
 * dome echoing round you now and then.
 */
public final class BellSounds {
    private BellSounds() {}

    private static final Map<Integer, Loop[]> loops = new HashMap<>();
    private static Beat heartbeat;
    private static int echoIn;

    /** a sound that loops and follows him, easing its volume up and down */
    static final class Loop extends AbstractTickableSoundInstance {
        final HollowbellEntity h;
        final boolean drift;
        float want;

        Loop(HollowbellEntity h, SoundEvent ev, boolean drift) {
            super(ev, SoundSource.HOSTILE, RandomSource.create());
            this.h = h;
            this.drift = drift;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            follow();
        }

        private void follow() {
            float s = h.bellScale();
            // from the middle of his bell
            x = h.getX(); y = h.getY() + (h.rig.rimY + 20) * s; z = h.getZ();
            pitch = Mth.clamp(1.25f - 0.35f * (float) Math.sqrt(s), 0.6f, 1.4f);
        }

        @Override
        public void tick() {
            if (h.isRemoved() || !HollowbellConfig.V.ambientSounds) { stop(); return; }
            follow();
            float s = h.bellScale();
            float size = Mth.clamp((float) Math.sqrt(s), 0.25f, 1.5f);
            if (drift) {
                double sp = h.velocity().length() / Math.max(0.01, h.maxSpeed());
                want = (float) (0.15 + 0.85 * Mth.clamp(sp, 0, 1)) * size;
            } else want = 0.9f * size;
            if (h.isDeadOrDying()) want *= Math.max(0f, 1f - h.deathTime / 120f);
            want *= HollowbellConfig.V.soundVolume;
            volume += (want - volume) * 0.08f;
            if (h.isDeadOrDying() && h.deathTime > 130) stop();
        }

        void end() { stop(); }
    }

    /** the heartbeat while you're in the dome */
    static final class Beat extends AbstractTickableSoundInstance {
        Beat() {
            super(ModSounds.HEARTBEAT, SoundSource.HOSTILE, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 0.9f * HollowbellConfig.V.soundVolume;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override public void tick() { if (!insideNow()) stop(); }
        void end() { stop(); }
    }

    private static boolean insideNow() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getVehicle() instanceof Seat seat && seat.mode() == Seat.INSIDE;
    }

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) { clear(); return; }
        var sm = mc.getSoundManager();
        // start a pair of loops for each one near enough to hear
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof HollowbellEntity h) || h.isDeadOrDying() && h.deathTime > 120) continue;
            double r = 160 * h.bellScale() + 60;
            if (h.distanceToSqr(mc.player) > r * r) continue;
            Loop[] l = loops.get(h.getId());
            if (l == null || l[0].isStopped() || l[1].isStopped()) {
                if (l != null) { l[0].end(); l[1].end(); }
                l = new Loop[]{new Loop(h, ModSounds.HUM, false), new Loop(h, ModSounds.DRIFT, true)};
                loops.put(h.getId(), l);
                sm.play(l[0]);
                sm.play(l[1]);
            }
        }
        loops.entrySet().removeIf(en -> {
            Loop[] l = en.getValue();
            double r = 160 * l[0].h.bellScale() + 80;
            boolean gone = l[0].h.isRemoved() || l[0].h.distanceToSqr(mc.player) > r * r || l[0].isStopped();
            if (gone) { l[0].end(); l[1].end(); }
            return gone;
        });
        // inside the dome
        if (insideNow()) {
            if (heartbeat == null || heartbeat.isStopped()) { heartbeat = new Beat(); sm.play(heartbeat); }
            if (--echoIn <= 0) {
                echoIn = 60 + mc.level.random.nextInt(100);
                mc.player.playSound(ModSounds.ECHO, 0.7f * HollowbellConfig.V.soundVolume, 0.8f + mc.level.random.nextFloat() * 0.4f);
            }
        } else if (heartbeat != null) { heartbeat.end(); heartbeat = null; }
    }

    public static void clear() {
        for (Loop[] l : loops.values()) { l[0].end(); l[1].end(); }
        loops.clear();
        if (heartbeat != null) { heartbeat.end(); heartbeat = null; }
    }
}
