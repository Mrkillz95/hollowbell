package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.pig;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellState;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * His blows land only where they are seen to land, and only while they are live: pigs stand (or hang in the air)
 * where each move reaches and where it doesn't (far off, high over a ground ring, beside a beam, walled in from his
 * flash), and nothing is hit before the blow comes. A ring is hit as it runs out: what stands further off is hit
 * later.
 */
public class HitSenseTests implements FabricGameTest {
    /**
     * a pig: out from under him (in bell radii, at an angle round him from his front), up off the ground (in bell
     * radii; floating there), shut in a stone hut, and whether the move must hit it
     */
    record Spot(String name, double out, double angle, double up, boolean walled, boolean hit) {
        static Spot in(String name, double out, double angle) {
            return new Spot(name, out, angle, 0, false, true);
        }

        static Spot out(String name, double out, double angle) {
            return new Spot(name, out, angle, 0, false, false);
        }

        static Spot high(String name, double out, double angle, double up) {
            return new Spot(name, out, angle, up, false, false);
        }

        static Spot inHigh(String name, double out, double angle, double up) {
            return new Spot(name, out, angle, up, false, true);
        }

        static Spot walled(String name, double out, double angle, double up) {
            return new Spot(name, out, angle, up, true, false);
        }
    }

    /**
     * a move, the tick before which nothing may be hit, the spots (the first is what it's aimed at), and for a ring,
     * which two spots (near, far) must be hit at least `later` ticks apart
     */
    record Probe(int move, int from, List<Spot> spots, String near, String far, int later) {
        Probe(int move, int from, Spot... spots) {
            this(move, from, List.of(spots), null, null, 0);
        }

        Probe ring(String near, String far, int later) {
            return new Probe(move, from, spots, near, far, later);
        }
    }

    static final float SIZE = 0.3f;

    static final Probe[] PROBES = {
        // (the shock runs out from his rim, as far as 70s + 30 past it)
        new Probe(Moves.PULSE, Moves.PULSE_AT,
                Spot.in("aimed", 1.3, 0), Spot.in("near", 1.1, 2.0), Spot.in("far", 2.3, 3.5),
                Spot.out("far off", 7.0, 1.0)).ring("near", "far", 5),
        // (the light bursts out of his glowing spots, high in his dome, as far as 50s + 30: up there, and only
        // where nothing stands in its way)
        new Probe(Moves.FLASH, Moves.FLASH_AT,
                Spot.inHigh("aimed", 1.4, 0, 1.8), Spot.inHigh("near", 1.45, 2.0, 1.9),
                Spot.inHigh("far", 1.45, 3.5, 1.7), Spot.walled("walled in", 1.4, 1.2, 1.8),
                Spot.out("far off", 8.0, 4.5)),
        // (the big ring runs out along the ground from under him to 120s + 40, as high as his rim)
        new Probe(Moves.DEEP_TOLL, Moves.TOLL_BIG,
                Spot.in("aimed", 1.3, 0), Spot.in("near", 0.9, 2.0), Spot.in("far", 2.6, 3.5),
                Spot.high("high over the ring", 2.6, 1.0, 3.6), Spot.out("far off", 9.0, 4.5))
                .ring("near", "far", 6),
        new Probe(Moves.SUN_LANCES, Moves.LANCE_FROM,
                Spot.in("aimed", 1.0, 0), Spot.high("high beside a beam", 1.4, 1.0, 0.9),
                Spot.high("high beside a beam 2", 1.4, 2.6, 0.9), Spot.high("high beside a beam 3", 1.4, 4.2, 0.9),
                Spot.high("high beside a beam 4", 1.4, 5.6, 0.9), Spot.out("far off", 4.0, 3.0)),
        new Probe(Moves.DROP, Moves.DROP_WIND + Moves.DROP_FALL,
                Spot.in("aimed", 0.3, 0), Spot.out("beside", 2.2, 2.0), Spot.out("far off", 4.0, 4.0)),
        new Probe(Moves.WHIRLPOOL, Moves.WHIRL_UP,
                Spot.in("aimed", 0.3, 0), Spot.out("far off", 4.0, 3.0)),
        new Probe(Moves.UNDERTOW, Moves.UNDERTOW_PULL,
                Spot.in("aimed", 0.3, 0), Spot.out("far off", 4.5, 3.0)),
        new Probe(Moves.SLAM, Math.round(net.jj.hollowbell.rig.BellRig.SLAM_HIT * Moves.length(Moves.SLAM)) - 2,
                Spot.in("aimed", 1.3, 0), Spot.out("other side", 1.6, Math.PI), Spot.out("far off", 4.0, 0)),
        new Probe(Moves.CURTAIN, Moves.CURTAIN_CLOSE - 6,
                Spot.in("aimed", 1.3, 0), Spot.out("other side", 1.6, Math.PI), Spot.out("far off", 4.0, 1.5)),
    };

    @GameTestGenerator
    public Collection<TestFunction> hitSense() {
        List<TestFunction> out = new ArrayList<>();
        int slot = 0;
        for (Probe p : PROBES) {
            String name = "hits_" + Moves.NAMES[p.move()];
            int sl = 900 + slot++;
            out.add(new TestFunction(name, name, EMPTY_STRUCTURE, 1200, 0, true, h -> probe(h, p, sl)));
        }
        return out;
    }

    /**
     * The lash hits only while its strand whips forward: stepped off the game (BellMotion), the lashed strand's end
     * goes the way of the whip in every tick the server looks for hits (at its tick t it sees the pose of t - 1, and
     * sweeps from the pose before that), and in the tick before them it is still winding back.
     */
    @GameTest(template = EMPTY_STRUCTURE)
    public void theLashHitsOnlyWhileItWhipsForward(GameTestHelper h) {
        // (a long strand at what's out in front, a short one round the other side, one at something low off his side)
        List<String> bad = new ArrayList<>();
        lashWhip(30, 55f, 4f, 25f, bad);
        lashWhip(5, -40f, 4f, 10f, bad);
        lashWhip(60, 10f, -10f, -70f, bad);
        h.assertTrue(bad.isEmpty(), String.join("; ", bad));
        h.succeed();
    }

    private static void lashWhip(int k, float tx, float ty, float tz, List<String> bad) {
        int len = Moves.length(Moves.LASH);
        BellMotion b = new BellMotion();
        b.tx = tx;
        b.ty = ty;
        b.tz = tz;
        b.run(80);
        b.move = Moves.LASH;
        b.arg = k;
        int c = BellMotion.RIG.strandChain[k];
        BellState st = new BellState(BellMotion.RIG);
        float[][] tip = new float[len + 1][];
        for (int t = 0; t <= len; t++) {
            b.moveT = t;
            b.step();
            b.anim.fill(st, 1f);
            float[] p = st.chain[c];
            tip[t] = new float[] {p[p.length - 3], p[p.length - 2], p[p.length - 1]};
        }
        // (the way of the whip: where the end went over the ticks it hits)
        int a0 = Moves.LASH_HIT_FROM - 2, a1 = Moves.LASH_HIT_TO - 1;
        float wx = tip[a1][0] - tip[a0][0], wz = tip[a1][2] - tip[a0][2];
        float wl = (float) Math.sqrt(wx * wx + wz * wz);
        StringBuilder log = new StringBuilder();
        for (int t = Moves.LASH_HIT_FROM - 1; t <= Moves.LASH_HIT_TO; t++) {
            int p1 = t - 1, p0 = t - 2;
            float go = ((tip[p1][0] - tip[p0][0]) * wx + (tip[p1][2] - tip[p0][2]) * wz) / wl;
            log.append(String.format(" %d:%.1f", t, go));
            if (t >= Moves.LASH_HIT_FROM && go < -0.5f) bad.add("strand " + k + " at " + t + " goes back " + go);
            if (t == Moves.LASH_HIT_FROM - 1 && go > 0.5f)
                bad.add("strand " + k + " already whipping forward at " + t + ": " + go);
        }
        HollowbellMod.LOG.info("lash whip, strand {}, along the whip a tick (model blocks):{}", k, log);
        if (wl < 15f) bad.add("strand " + k + "'s whip hardly went anywhere: " + wl);
    }

    /** a stone hut shut all round (and over) whatever stands in it */
    static void hut(ServerLevel l, BlockPos c) {
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = -1; dy <= 3; dy++) {
                    boolean inside = dx == 0 && dz == 0 && dy >= 0 && dy <= 1;
                    l.setBlock(c.offset(dx, dy, dz),
                            inside ? Blocks.AIR.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState(), 2);
                }
    }

    static void probe(GameTestHelper h, Probe pr, int slot) {
        HollowbellEntity e = spawnAway(h, SIZE, HollowbellEntity.CALM, slot);
        List<Pig> pigs = new ArrayList<>();
        Map<String, Float> firstAt = new HashMap<>();
        List<String> wrong = new ArrayList<>();
        StringBuilder log = new StringBuilder();
        boolean[] started = {false};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            e.setCruise(0);
        });
        h.runAfterDelay(140, () -> {
            Vec3 x = e.toWorld(new Vector3f(1, 0, 0)).subtract(e.position()).normalize();
            Vec3 z = new Vec3(-x.z, 0, x.x);
            double bell = e.bellRadius();
            for (Spot s : pr.spots()) {
                Vec3 p = e.position()
                        .add(x.scale(Math.cos(s.angle()) * bell * s.out()))
                        .add(z.scale(Math.sin(s.angle()) * bell * s.out()));
                double g = e.groundAt(p.x, p.z);
                Vec3 at = new Vec3(Math.floor(p.x) + 0.5, g + s.up() * bell, Math.floor(p.z) + 0.5);
                if (s.walled()) {
                    BlockPos c = BlockPos.containing(at.x, at.y + 1, at.z);
                    hut(h.getLevel(), c);
                    at = new Vec3(at.x, c.getY(), at.z);
                }
                Pig pg = pig(h, at);
                pg.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);
                pg.setHealth(pg.getMaxHealth());
                pg.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
                if (s.up() > 0) pg.setNoGravity(true);
                pigs.add(pg);
            }
            e.setTarget(pigs.get(0));
            started[0] = e.forceMove(pr.move(), pigs.get(0));
            h.assertTrue(started[0], "could not start " + Moves.NAMES[pr.move()]);
        });
        h.onEachTick(() -> {
            if (!started[0]) return;
            boolean in = e.moveNow() == pr.move();
            float t = in ? e.moveT(0f) : -1f;
            for (int i = 0; i < pigs.size(); i++) {
                Pig p = pigs.get(i);
                Spot s = pr.spots().get(i);
                if (p.isAlive() && p.getHealth() >= p.getMaxHealth()) continue;
                // (stung by touching one of his strands where it stands, the way anything touching one is: just
                // poisoned, that is no blow of the move's, and may come before it)
                var poison = p.getEffect(MobEffects.POISON);
                boolean touched = poison != null && poison.getDuration() >= 75;
                log.append(" ").append(s.name()).append("@").append(t).append(touched ? "(touched)" : "");
                firstAt.putIfAbsent(s.name(), in ? t : 9999f);
                if (in && t < pr.from() && !touched && !wrong.contains("early " + s.name()))
                    wrong.add("early " + s.name());
                if (p.isAlive()) p.setHealth(p.getMaxHealth());
                p.clearFire();
                // (and the poison taken off again, so what it does later isn't taken for another blow)
                p.removeAllEffects();
            }
        });
        h.runAfterDelay(140 + Moves.length(pr.move()) + 30, () -> {
            List<String> bad = new ArrayList<>();
            for (String w : wrong) bad.add("hit '" + w.substring(6) + "' before the blow came");
            for (Spot s : pr.spots()) {
                boolean hit = firstAt.containsKey(s.name());
                if (s.hit() && !hit) bad.add("missed '" + s.name() + "' where it lands");
                if (!s.hit() && hit) bad.add("hit '" + s.name() + "' (move tick " + firstAt.get(s.name()) + ")");
            }
            if (pr.near() != null && firstAt.containsKey(pr.near()) && firstAt.containsKey(pr.far())) {
                float gap = firstAt.get(pr.far()) - firstAt.get(pr.near());
                log.append(" | ring gap ").append(gap);
                if (gap < pr.later())
                    bad.add("the ring hit '" + pr.far() + "' only " + gap + " ticks after '" + pr.near()
                            + "' (it runs out: at least " + pr.later() + ")");
            }
            HollowbellMod.LOG.info("hit sense {} (bell {}):{}", Moves.NAMES[pr.move()],
                    String.format("%.1f", e.bellRadius()), log);
            for (Pig p : pigs) p.discard();
            release(h, e);
            h.assertTrue(bad.isEmpty(), Moves.NAMES[pr.move()] + ": " + String.join("; ", bad));
            h.succeed();
        });
    }
}
