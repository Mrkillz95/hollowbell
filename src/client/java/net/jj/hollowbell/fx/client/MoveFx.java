package net.jj.hollowbell.fx.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.entity.Shot;
import net.jj.hollowbell.entity.StingerHook;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * What his moves look like beyond his body, drawn by each player's own game from the move he is in and how far into
 * it he is: rings of sound running out from his bell when it tolls (at the very pace and size of what they hit),
 * the glass of his rim glinting as it rings, trails behind the strand ends and arms as they swing, goo pouring from
 * his pods into pools, the sun lances burning down onto the ground, the dust spiralling in under a whirlpool and
 * dragged in by the undertow, the rush of air as he dives, and his light bursting out in a glow flash.
 */
public final class MoveFx {
    private MoveFx() {}

    private static final BellRig RIG = BellRig.get();

    /** his glow, the poison goo, sunlight */
    private static final float[] MINT = {0.75f, 1f, 0.85f}, GOO = {0.55f, 1f, 0.35f}, SUN = {1f, 0.92f, 0.62f};

    /** how far a trail reaches back, in ticks */
    private static final int TRAIL = 14;

    /** what this game last saw each of him do */
    private static final class Seen {
        int move = -1;
        float t = -1;
        final Map<Integer, Trail> trails = new HashMap<>();
    }

    /** where a strand end or arm tip has been over the last ticks, newest first */
    private static final class Trail {
        final double[] p = new double[3 * TRAIL];
        int n, quiet;
        boolean arm;
        float size;

        void add(Vec3 v) {
            System.arraycopy(p, 0, p, 3, p.length - 3);
            p[0] = v.x;
            p[1] = v.y;
            p[2] = v.z;
            n = Math.min(TRAIL, n + 1);
        }
    }

    /** a ring of sound: flat in the air, flat on the ground, or a wall standing up off the ground */
    private static final class Ring {
        double x, y, z;
        float r0, r1, h, w, a, cut;
        float[] c = MINT;
        int age, max, kind, waves;
        /** at an even pace (a ring the server runs at that pace), or fast then slowing */
        boolean even;
    }

    private static final int AIR = 0, GROUND = 1, WALL = 2;

    /** a glint of light: his rim ringing, a stinger rattling, an ember */
    private static final class Glint {
        double x, y, z, vx, vy, vz;
        float size, rot;
        float[] c = MINT;
        int age, max;
        boolean cross;
    }

    /** something lying on the ground and fading: a goo puddle, a burn */
    private static final class Mark {
        double x, y, z;
        float r, rot;
        int age, max;
        boolean burn;
    }

    private static final Map<Integer, Seen> seen = new HashMap<>();
    /** stingers in flight (his, and the thrown Stinger's barb), and how big each is drawn */
    private static final Map<Integer, Trail> flying = new HashMap<>();

    private static final List<Ring> rings = new ArrayList<>();
    private static final List<Glint> glints = new ArrayList<>();
    private static final List<Mark> marks = new ArrayList<>();

    public static void clear() {
        seen.clear();
        flying.clear();
        rings.clear();
        glints.clear();
        marks.clear();
    }

    // each tick

    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            clear();
            return;
        }
        if (mc.isPaused()) return;
        rings.removeIf(r -> ++r.age >= r.max);
        marks.removeIf(m -> ++m.age >= m.max);
        for (Iterator<Glint> it = glints.iterator(); it.hasNext(); ) {
            Glint g = it.next();
            g.x += g.vx;
            g.y += g.vy;
            g.z += g.vz;
            g.vx *= 0.9;
            g.vy = g.vy * 0.9 + 0.01;
            g.vz *= 0.9;
            if (++g.age >= g.max) it.remove();
        }
        RandomSource rnd = mc.level.random;
        List<Integer> here = new ArrayList<>();
        for (Iterator<Trail> it = flying.values().iterator(); it.hasNext(); )
            if (++it.next().quiet > TRAIL) it.remove();
        for (Entity e : mc.level.entitiesForRendering()) {
            float size = e instanceof Shot sh && sh.kind() == Shot.STINGER
                    ? sh.size()
                    : e instanceof StingerHook hk && hk.state() == StingerHook.FLYING ? 0.15f : -1f;
            if (size >= 0f && e.distanceToSqr(mc.player) < 400 * 400) {
                // just flicked off: a glint where it left
                if (!flying.containsKey(e.getId()) && size > 0.2f)
                    glint(e.position(), Vec3.ZERO, 3f * size + 0.6f, 6, GOO, true);
                Trail t = flying.computeIfAbsent(e.getId(), k -> new Trail());
                t.quiet = 0;
                t.size = size;
                t.add(e.position());
            }
            if (!(e instanceof HollowbellEntity h) || h.distanceToSqr(mc.player) > 900 * 900) continue;
            here.add(h.getId());
            Seen sn = seen.computeIfAbsent(h.getId(), k -> new Seen());
            int move = h.isDeadOrDying() ? Moves.NONE : h.moveNow();
            float t = h.moveT(0f);
            if (move != sn.move || t < sn.t) {
                sn.move = move;
                sn.t = -1;
            }
            if (move != Moves.NONE) moveTick(h, sn, move, sn.t, t, rnd);
            trails(h, sn, move, t);
            sn.t = t;
        }
        seen.keySet().retainAll(here);
        while (rings.size() > 64) rings.removeFirst();
        while (glints.size() > 600) glints.removeFirst();
        while (marks.size() > 160) marks.removeFirst();
    }

    /** the moments of a move that set something off: from was the move's tick last time, t now */
    private static void moveTick(HollowbellEntity h, Seen sn, int move, float from, float t, RandomSource rnd) {
        float s = h.bellScale();
        double bell = h.bellRadius();
        Vec3 rim = rimNow(h);
        switch (move) {
            case Moves.PULSE -> {
                if (at(from, t, Moves.PULSE_AT - 5)) rimGlints(h, rnd, 10);
                if (at(from, t, Moves.PULSE_AT)) {
                    // the shock the server runs: a ball out from his rim, bell to bell + 70s + 30 in 24 ticks
                    float r1 = (float) (bell + 70 * s + 30);
                    toll(rim, (float) bell, r1, 24, true, 1f, s);
                    Ring g = ring(
                            GROUND,
                            h.position().x,
                            ground(h, h.getX(), h.getZ()),
                            h.position().z,
                            (float) bell,
                            r1,
                            24,
                            0.7f,
                            s);
                    g.even = true;
                    g.cut = (float) (rim.y - g.y);
                }
            }
            case Moves.DEEP_TOLL -> {
                for (int k = 0; k < 3; k++) {
                    if (!at(from, t, 1 + 36 * k)) continue;
                    toll(
                            rim,
                            (float) bell,
                            (float) (bell + (40 * s + 20) * (1 + 0.4f * k)),
                            20,
                            false,
                            0.6f + 0.2f * k,
                            s);
                    rimGlints(h, rnd, 8 + 6 * k);
                }
                if (at(from, t, Moves.TOLL_BIG)) {
                    // the big ring the server runs: from right under him out to 120s + 40 in 25 ticks
                    float R = 120 * s + 40;
                    double gy = ground(h, h.getX(), h.getZ());
                    Ring w = ring(WALL, h.getX(), gy, h.getZ(), 0f, R, 25, 1f, s);
                    w.even = true;
                    w.h = RIG.rimY * s * 0.55f + 4;
                    toll(rim, (float) bell, R, 25, true, 1.2f, s);
                    toll(rim.add(0, -RIG.rimY * s * 0.3, 0), (float) bell * 0.8f, R, 25, true, 0.6f, s);
                    rimGlints(h, rnd, 30);
                }
            }
            case Moves.DROP -> {
                int hit = Moves.DROP_WIND + Moves.DROP_FALL;
                if (at(from, t, hit)) slamToll(h, rim, (float) (bell * 1.2 + 6), rnd);
            }
            case Moves.WHIRLPOOL -> {
                if (at(from, t, Moves.WHIRL_CRUSH)) slamToll(h, rim, (float) (bell * 0.75 + 4), rnd);
            }
            case Moves.UNDERTOW -> {
                if (at(from, t, Moves.UNDERTOW_SLAM)) slamToll(h, rim, (float) (bell * 0.95 + 5), rnd);
            }
            case Moves.EGG_RAIN -> {
                if (at(from, t, Moves.EGG_AT - 8))
                    toll(rim, (float) bell, (float) (bell + 30 * s + 10), 16, false, 0.5f, s);
                if (t < Moves.EGG_AT && (int) t % 3 == 0) eggGlints(h, rnd, t / Moves.EGG_AT);
            }
            case Moves.SHED -> {
                if (t < Moves.SHED_AT && (int) t % 3 == 0) eggGlints(h, rnd, t / Moves.SHED_AT);
            }
            case Moves.FLASH -> {
                if (at(from, t, Moves.FLASH_AT)) {
                    // the light bursts out as far as it blinds: 50s + 30 round the middle of him
                    Vec3 c = h.position().add(0, (RIG.rimY + 30) * s, 0);
                    float reach = Math.max((float) bell * 1.1f, 50 * s + 30);
                    Ring a = ring(AIR, c.x, c.y, c.z, (float) bell * 0.9f, reach, 10, 1.2f, s);
                    a.c = new float[] {1f, 1f, 0.9f};
                    for (int i = 0; i < RIG.spots.length; i++) {
                        Vec3 p = h.spotWorld(i);
                        for (int k = 0; k < 10; k++) {
                            Vec3 d = p.subtract(c)
                                    .normalize()
                                    .add(rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5, rnd.nextGaussian() * 0.5)
                                    .normalize();
                            glint(p, d.scale(1.2 * s + 0.3), 3 * s + 0.6f, 14, new float[] {1f, 1f, 0.85f}, false);
                        }
                    }
                }
            }
            case Moves.POD_BURST -> {
                if (at(from, t, Moves.POD_AT + 2)) {
                    float r = 5 * s + 3;
                    for (int i = 0; i < RIG.pods.length; i++) {
                        if (h.isPodPopped(i)) continue;
                        Vec3 p = h.podWorld(i);
                        mark(p.x, ground(h, p.x, p.z), p.z, r, 120, false, rnd);
                    }
                }
                if (t < Moves.POD_AT && (int) t % 2 == 0) {
                    // the pods swelling: goo beading and dripping off them
                    for (int i = 0; i < RIG.pods.length; i++) {
                        if (h.isPodPopped(i) || rnd.nextInt(3) != 0) continue;
                        Vec3 p = h.podWorld(i).add(0, -RIG.pods[i].radius() * s * 0.8, 0);
                        glint(p, new Vec3(0, -0.4 * s - 0.1, 0), 1.2f * s + 0.3f, 12, GOO, false);
                    }
                }
            }
            case Moves.SUN_LANCES -> {
                if (t < Moves.LANCE_FROM) {
                    // the light gathering into his glowing spots
                    for (int i = 0; i < RIG.spots.length; i++) {
                        Vec3 p = h.spotWorld(i);
                        Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian(), rnd.nextGaussian()).normalize();
                        float far = 18 * s + 4;
                        Glint g = glint(p.add(d.scale(far)), d.scale(-far / 10), 1.4f * s + 0.4f, 10, SUN, false);
                        g.vy -= 0.01;
                    }
                } else if (t <= Moves.LANCE_TO && (int) t % 3 == 0) {
                    float beam = 2.5f + 2.5f * s;
                    for (int i = 0; i < 5; i++) {
                        Vec3 g = lanceGround(h, i, t);
                        mark(g.x, g.y, g.z, beam * 1.1f, 70, true, rnd);
                        glint(
                                g.add(rnd.nextGaussian() * beam * 0.5, 0.3, rnd.nextGaussian() * beam * 0.5),
                                new Vec3(0, 0.15 + 0.1 * s, 0),
                                0.8f * s + 0.25f,
                                24,
                                SUN,
                                false);
                    }
                }
            }
            case Moves.STINGER_STORM -> {
                if (t < Moves.RAIN_FROM && (int) t % 2 == 0) {
                    // the stingers rattling on every strand end
                    for (int k = 0; k < 6; k++) {
                        int i = rnd.nextInt(RIG.strands.length);
                        if (RIG.strands[i].hidden()) continue;
                        glint(h.strandTipWorld(i), Vec3.ZERO, 4f * s + 0.6f, 5, GOO, true);
                    }
                }
            }
            case Moves.SKY_DIVE -> {
                int back = Moves.DIVE_CLIMB + Moves.DIVE_TURN + Moves.DIVE_FALL;
                // (the server jumps the move on to its turn back up the moment he lands)
                if (from >= 0 && from < back - 1 && t >= back && t < back + 4) {
                    Vec3 c = h.crownWorld();
                    double gy = ground(h, c.x, c.z);
                    float R = 75 * s + 24;
                    Ring w = ring(WALL, c.x, gy, c.z, 0f, R, 12, 1f, s);
                    w.even = true;
                    w.h = 40 * s + 6;
                    Ring a = ring(AIR, c.x, gy + 12 * s + 2, c.z, 0f, R * 1.15f, 16, 0.8f, s);
                    a.waves = 6;
                    rimGlints(h, rnd, 30);
                }
            }
            default -> {}
        }
    }

    /** the bell slammed down: the toll of it and a ring along the ground as far as the blow reaches */
    private static void slamToll(HollowbellEntity h, Vec3 rim, float reach, RandomSource rnd) {
        float s = h.bellScale();
        toll(rim, (float) h.bellRadius(), (float) (h.bellRadius() + 50 * s + 20), 20, false, 0.8f, s);
        Ring g = ring(GROUND, h.getX(), ground(h, h.getX(), h.getZ()), h.getZ(), reach * 0.3f, reach, 7, 0.9f, s);
        g.even = true;
        rimGlints(h, rnd, 16);
    }

    /** a toll: a ring of sound out from his rim, rippling, with two fainter ones after it */
    private static void toll(Vec3 c, float r0, float r1, int ticks, boolean even, float strength, float s) {
        for (int k = 0; k < 3; k++) {
            Ring r = ring(
                    AIR,
                    c.x,
                    c.y - k * 3 * s,
                    c.z,
                    r0,
                    r1 * (1f - 0.12f * k),
                    ticks + 4 * k,
                    strength * (1f - 0.3f * k),
                    s);
            r.age = -3 * k;
            r.even = even && k == 0;
            r.waves = 5 + 2 * k;
        }
    }

    private static Ring ring(int kind, double x, double y, double z, float r0, float r1, int max, float a, float s) {
        Ring r = new Ring();
        r.kind = kind;
        r.x = x;
        r.y = y;
        r.z = z;
        r.r0 = r0;
        r.r1 = r1;
        r.max = max;
        r.a = a;
        r.w = 4f * s + 1f;
        rings.add(r);
        return r;
    }

    private static Glint glint(Vec3 p, Vec3 v, float size, int max, float[] c, boolean cross) {
        Glint g = new Glint();
        g.x = p.x;
        g.y = p.y;
        g.z = p.z;
        g.vx = v.x;
        g.vy = v.y;
        g.vz = v.z;
        g.size = size;
        g.max = max;
        g.c = c;
        g.cross = cross;
        g.rot = (float) (Math.random() * Math.PI);
        glints.add(g);
        return g;
    }

    private static void mark(double x, double y, double z, float r, int max, boolean burn, RandomSource rnd) {
        Mark m = new Mark();
        m.x = x;
        m.y = y;
        m.z = z;
        m.r = r;
        m.max = max;
        m.burn = burn;
        m.rot = rnd.nextFloat() * Mth.TWO_PI;
        marks.add(m);
    }

    /** the middle of his rim as it is posed now (brought down in a drop, tipped in a dive) */
    private static Vec3 rimNow(HollowbellEntity h) {
        Vec3 sum = Vec3.ZERO;
        int n = RIG.sectors.length;
        for (int k = 0; k < 4; k++)
            sum = sum.add(h.boneWorld(RIG.sectors[k * n / 4].bone(), new Vector3f(0, RIG.rimY, 0)));
        return sum.scale(0.25);
    }

    /** the glass and copper of his rim ringing: glints running round it */
    private static void rimGlints(HollowbellEntity h, RandomSource rnd, int n) {
        float s = h.bellScale();
        double bell = h.bellRadius();
        Vec3 rim = rimNow(h);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Mth.TWO_PI;
            Vec3 p = rim.add(Math.cos(a) * bell, rnd.nextGaussian() * 4 * s, Math.sin(a) * bell);
            Glint g = glint(p, Vec3.ZERO, (2.5f + rnd.nextFloat() * 2f) * s + 0.5f, 8 + rnd.nextInt(10), MINT, true);
            g.age = -rnd.nextInt(10);
        }
    }

    /** the egg clumps in his strands glowing brighter as they're about to come off */
    private static void eggGlints(HollowbellEntity h, RandomSource rnd, float k) {
        float s = h.bellScale();
        for (int i = 0; i < RIG.eggs.length; i++) {
            if (h.state.eggGone[i] || rnd.nextFloat() > 0.3f + 0.5f * k) continue;
            Vec3 p = h.eggWorld(i);
            glint(p, Vec3.ZERO, (2f + 3f * k) * s + 0.4f, 6, GOO, false);
        }
    }

    /** where sun lance i meets the ground at tick t (the same as the server's: four spiral out, one hunts) */
    private static Vec3 lanceGround(HollowbellEntity h, int i, float t) {
        float frac = Mth.clamp((t - Moves.LANCE_FROM) / (float) (Moves.LANCE_TO - Moves.LANCE_FROM), 0f, 1f);
        double x, z;
        if (i < 4) {
            Vector3f c = RIG.spots[i].centre();
            double a = Math.atan2(c.z, c.x) - h.getYRot() * Mth.DEG_TO_RAD + frac * 2.4;
            double r = h.bellRadius() * (0.35 + 1.35 * frac);
            x = h.getX() + Math.cos(a) * r;
            z = h.getZ() + Math.sin(a) * r;
        } else {
            // (the server keeps his aim on where the hunting one is)
            Vec3 w = h.aimWorld();
            x = w.x;
            z = w.z;
        }
        return new Vec3(x, ground(h, x, z), z);
    }

    private static double ground(HollowbellEntity h, double x, double z) {
        return h.groundAt(x, z);
    }

    private static boolean at(float from, float t, float when) {
        return from < when && t >= when;
    }

    /** the strand ends and arm tips that swing in this move, followed so their trails can be drawn */
    private static void trails(HollowbellEntity h, Seen sn, int move, float t) {
        Map<Integer, Trail> tr = sn.trails;
        for (Iterator<Map.Entry<Integer, Trail>> it = tr.entrySet().iterator(); it.hasNext(); )
            if (++it.next().getValue().quiet > TRAIL) it.remove();
        switch (move) {
            case Moves.SWEEP -> {
                if (t < 18 || t > 46) return;
                for (int i = 0; i < RIG.strands.length; i += 3) follow(tr, i, false, h.strandTipWorld(i));
            }
            case Moves.LASH -> {
                int k = h.moveArg();
                if (k >= 0 && k < RIG.strands.length && t > 6 && t < 30) follow(tr, k, false, h.strandTipWorld(k));
            }
            case Moves.SLAM -> {
                int k = h.moveArg();
                if (k >= 0 && k < RIG.arms.length) follow(tr, 1000 + k, true, h.armTipWorld(k));
            }
            case Moves.ARM_STORM -> {
                if (t < Moves.STORM_UP - 6) return;
                for (int k = 0; k < RIG.arms.length; k++) follow(tr, 1000 + k, true, h.armTipWorld(k));
            }
            case Moves.WHIRLPOOL -> {
                if (t < Moves.WHIRL_UP || t > Moves.WHIRL_CRUSH) return;
                for (int i = 0; i < RIG.strands.length; i += 4) follow(tr, i, false, h.strandTipWorld(i));
            }
            default -> {}
        }
    }

    private static void follow(Map<Integer, Trail> tr, int key, boolean arm, Vec3 at) {
        Trail t = tr.computeIfAbsent(key, k -> new Trail());
        t.arm = arm;
        t.quiet = 0;
        t.add(at);
    }

    // drawing

    public static void render(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<HollowbellEntity> busy = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering())
            if (e instanceof HollowbellEntity h && seen.containsKey(h.getId())) busy.add(h);
        boolean trails = false;
        for (Seen sn : seen.values()) trails |= !sn.trails.isEmpty() || sn.move > 0;
        if (rings.isEmpty() && glints.isEmpty() && marks.isEmpty() && flying.isEmpty() && !trails) return;
        float pt = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Draw.begin(ctx.camera(), pt, mc.level);
        for (Mark m : marks) drawMark(m, pt);
        for (HollowbellEntity h : busy) {
            Seen sn = seen.get(h.getId());
            float s = h.bellScale();
            for (Trail t : sn.trails.values()) drawTrail(t, s, pt);
            float t = h.moveT(pt);
            switch (sn.move) {
                case Moves.POD_BURST -> drawGoo(h, t);
                case Moves.SUN_LANCES -> drawLances(h, t);
                case Moves.WHIRLPOOL ->
                    drawWhirl(h, t, (float) (h.bellRadius() * 1.9 + 6), Moves.WHIRL_UP, Moves.WHIRL_CRUSH, true);
                case Moves.UNDERTOW ->
                    drawWhirl(
                            h, t, (float) (h.bellRadius() * 2.2 + 8), Moves.UNDERTOW_PULL, Moves.UNDERTOW_SLAM, false);
                case Moves.SKY_DIVE -> drawDiveAir(h, t, pt);
                case Moves.FLASH -> drawFlash(h, t);
                default -> {}
            }
        }
        for (Trail t : flying.values()) drawStinger(t, pt);
        for (Ring r : rings) drawRing(r, pt);
        for (Glint g : glints) drawGlint(g, pt);
        Draw.end();
    }

    private static void drawRing(Ring r, float pt) {
        float age = r.age + pt;
        if (age < 0) return;
        float k = Mth.clamp(age / r.max, 0f, 1f);
        float e = r.even ? k : 1f - (1f - k) * (1f - k) * (1f - k);
        float R = Mth.lerp(e, r.r0, r.r1);
        float fade = r.a * Draw.smooth(age / 2f) * (1f - k * k);
        if (r.kind == GROUND && r.cut > 0f) {
            // where a ball of that size meets the ground
            if (R <= r.cut) return;
            R = (float) Math.sqrt(R * R - r.cut * r.cut);
        }
        float w = r.w * (1f + 1.5f * k);
        float[] c = r.c;
        float phase = Draw.time * 0.6f;
        switch (r.kind) {
            case AIR -> {
                // a soft wide swell of air with a bright crest running round in it, rippling up and down
                float amp = r.waves > 0 ? (0.6f + 0.03f * R) * (1f - k) : 0f;
                Draw.hoop(
                        Draw.lay(Draw.BAND),
                        r.x,
                        r.z,
                        r.y,
                        r.y,
                        Math.max(0, R - w * 3f),
                        R + w * 1.5f,
                        c[0],
                        c[1],
                        c[2],
                        0.22f * fade,
                        0.22f * fade,
                        r.waves,
                        amp,
                        phase);
                Draw.hoop(
                        Draw.lay(Draw.BAND),
                        r.x,
                        r.z,
                        r.y,
                        r.y,
                        Math.max(0, R - w),
                        R + w * 0.5f,
                        c[0],
                        c[1],
                        c[2],
                        0.75f * fade,
                        0.75f * fade,
                        r.waves,
                        amp,
                        phase);
                Draw.hoop(
                        Draw.glow(Draw.BAND),
                        r.x,
                        r.z,
                        r.y,
                        r.y,
                        Math.max(0, R - w * 0.6f),
                        R + w * 0.3f,
                        c[0] * 0.6f,
                        c[1] * 0.6f,
                        c[2] * 0.6f,
                        fade,
                        fade,
                        r.waves,
                        amp,
                        phase);
                // and the ripple standing up off it a little, so it reads side on too
                Draw.hoop(
                        Draw.lay(Draw.BAND),
                        r.x,
                        r.z,
                        r.y - w,
                        r.y + w,
                        R,
                        R,
                        c[0],
                        c[1],
                        c[2],
                        0.4f * fade,
                        0.4f * fade,
                        r.waves,
                        amp,
                        phase);
            }
            case GROUND -> {
                double y = r.y + Draw.lift(r.x + R, r.z);
                Draw.hoop(
                        Draw.lay(Draw.BAND),
                        r.x,
                        r.z,
                        y,
                        y,
                        Math.max(0, R - w * 1.6f),
                        R + w * 0.8f,
                        c[0],
                        c[1],
                        c[2],
                        0.5f * fade,
                        0.5f * fade,
                        0,
                        0,
                        0);
                Draw.hoop(
                        Draw.glow(Draw.BAND),
                        r.x,
                        r.z,
                        y,
                        y,
                        Math.max(0, R - w),
                        R + w * 0.5f,
                        c[0] * 0.5f,
                        c[1] * 0.5f,
                        c[2] * 0.5f,
                        fade,
                        fade,
                        0,
                        0,
                        0);
            }
            default -> {
                // a standing wall of sound: brightest at its foot, gone at its top
                double y0 = r.y - 1;
                Draw.hoop(
                        Draw.lay(Draw.BAND), r.x, r.z, y0, y0 + r.h, R, R, c[0], c[1], c[2], 0.6f * fade, 0f, 0, 0, 0);
                Draw.hoop(
                        Draw.glow(Draw.BAND),
                        r.x,
                        r.z,
                        y0,
                        y0 + r.h * 0.7f,
                        R,
                        R,
                        c[0] * 0.6f,
                        c[1] * 0.6f,
                        c[2] * 0.6f,
                        fade,
                        0f,
                        0,
                        0,
                        0);
                double y = r.y + Draw.lift(r.x + R, r.z);
                Draw.hoop(
                        Draw.lay(Draw.BAND),
                        r.x,
                        r.z,
                        y,
                        y,
                        Math.max(0, R - w * 2f),
                        R + w,
                        c[0],
                        c[1],
                        c[2],
                        0.45f * fade,
                        0.45f * fade,
                        0,
                        0,
                        0);
            }
        }
    }

    private static void drawGlint(Glint g, float pt) {
        float age = g.age + pt;
        if (age < 0) return;
        float k = age / g.max;
        float a = Draw.smooth(age / 2f) * (1f - k);
        double x = g.x + g.vx * pt, y = g.y + g.vy * pt, z = g.z + g.vz * pt;
        float[] c = g.c;
        float size = g.size * (g.cross ? 1f - 0.5f * k : 1f);
        Draw.sprite(Draw.glow(Draw.GLINTS), x, y, z, size, g.rot, Draw.EMBER, c[0], c[1], c[2], a);
        Draw.sprite(Draw.lay(Draw.GLINTS), x, y, z, size * 0.6f, g.rot, Draw.DOT, c[0], c[1], c[2], 0.7f * a);
        if (g.cross) {
            Draw.sprite(Draw.glow(Draw.GLINTS), x, y, z, size * 1.8f, 0f, Draw.STREAK, c[0], c[1], c[2], a);
            Draw.sprite(Draw.glow(Draw.GLINTS), x, y, z, size * 1.8f, Mth.HALF_PI, Draw.STREAK, c[0], c[1], c[2], a);
        }
    }

    private static void drawMark(Mark m, float pt) {
        float age = m.age + pt;
        float k = age / m.max;
        double y = m.y + Draw.lift(m.x, m.z);
        if (m.burn) {
            // scorched ground, glowing as it cools
            float a = Draw.smooth(age / 3f) * (1f - k);
            Draw.flat(Draw.lay(Draw.POOL), m.x, y, m.z, m.r, m.rot, 0.12f, 0.08f, 0.05f, 0.55f * a);
            float hot = (1f - k) * (1f - k);
            Draw.flat(Draw.glow(Draw.POOL), m.x, y + 0.02, m.z, m.r * 0.8f, m.rot, 1f, 0.45f, 0.1f, hot);
        } else {
            // a puddle of goo: it spreads, glistens, then soaks away
            float spread = 0.5f + 0.5f * Draw.smooth(age / 8f);
            float a = (1f - Draw.smooth((k - 0.6f) / 0.4f));
            Draw.flat(
                    Draw.lay(Draw.POOL),
                    m.x,
                    y,
                    m.z,
                    m.r * spread,
                    m.rot,
                    GOO[0] * 0.6f,
                    GOO[1] * 0.75f,
                    GOO[2] * 0.4f,
                    0.75f * a);
            Draw.flat(
                    Draw.glow(Draw.POOL), m.x, y + 0.02, m.z, m.r * spread * 0.7f, m.rot, 0.25f, 0.5f, 0.12f, 0.6f * a);
        }
    }

    private static void drawTrail(Trail t, float s, float pt) {
        if (t.n < 3) return;
        int n = t.n;
        double[] p = new double[3 * n];
        float[] w = new float[n], a = new float[n];
        float half = t.arm ? 8f * s + 1f : 4.5f * s + 0.8f;
        float speed = (float) Math.sqrt(sq(t.p[0] - t.p[3]) + sq(t.p[1] - t.p[4]) + sq(t.p[2] - t.p[5]));
        // (only a swing that's really moving leaves a trail)
        float show = Mth.clamp((speed / (0.6f * s + 0.1f)) - 0.5f, 0f, 1f) * Math.max(0f, 1f - t.quiet / 3f);
        if (show <= 0.01f) return;
        for (int i = 0; i < n; i++) {
            // drawn between the last two ticks, as the end itself is
            int o = Math.min(i + 1, n - 1);
            double f = i + 1 < n ? pt : 1;
            p[3 * i] = Mth.lerp(f, t.p[3 * o], t.p[3 * i]);
            p[3 * i + 1] = Mth.lerp(f, t.p[3 * o + 1], t.p[3 * i + 1]);
            p[3 * i + 2] = Mth.lerp(f, t.p[3 * o + 2], t.p[3 * i + 2]);
            float u = i / (n - 1f);
            w[i] = half * (1f - 0.6f * u);
            a[i] = show * (1f - u);
        }
        float[] a2 = new float[n];
        for (int i = 0; i < n; i++) a2[i] = a[i] * 0.8f;
        Draw.ribbon(Draw.lay(Draw.WISP), p, n, w, 0.8f, 0.95f, 0.85f, a2, 0f, 0.3f);
        Draw.ribbon(Draw.glow(Draw.BAND), p, n, w, 0.3f, 0.5f, 0.38f, a, 0f, 0f);
    }

    /** the pod burst: goo pouring straight down from every pod still on him, as wide as it hits */
    private static void drawGoo(HollowbellEntity h, float t) {
        float from = Moves.POD_AT, to = Moves.POD_AT + 16;
        if (t < from - 1 || t > to + 10) return;
        float s = h.bellScale();
        float on = Draw.smooth((t - from + 1) / 3f) * (1f - Draw.smooth((t - to) / 10f));
        float r = 5 * s + 3;
        for (int i = 0; i < RIG.pods.length; i++) {
            if (h.isPodPopped(i)) continue;
            Vec3 top = h.podWorld(i);
            double gy = ground(h, top.x, top.z);
            if (top.y - gy < 0.5) continue;
            // the stream falls from the pod: its front reaches the ground a few ticks in
            float drop = Mth.clamp((t - from) / 5f, 0f, 1f);
            double bottom = Mth.lerp(drop, top.y, gy);
            int n = 8;
            double[] p = new double[3 * n];
            float[] w = new float[n], a = new float[n];
            for (int k = 0; k < n; k++) {
                float u = k / (n - 1f);
                double wob = Math.sin(Draw.time * 0.8 + k + i) * 0.15 * s;
                p[3 * k] = top.x + wob;
                p[3 * k + 1] = Mth.lerp(u, top.y, bottom);
                p[3 * k + 2] = top.z - wob;
                w[k] = r * (0.3f + 0.3f * u);
                a[k] = 0.95f * on;
            }
            float scroll = -Draw.time * 0.35f;
            Draw.ribbon(Draw.lay(Draw.BAND), p, n, w, 0.45f, 0.85f, 0.2f, a, scroll, 0.25f);
            Draw.ribbon(Draw.lay(Draw.WISP), p, n, w, GOO[0] * 0.7f, GOO[1], GOO[2] * 0.5f, a, scroll, 0.25f);
            Draw.ribbon(Draw.glow(Draw.WISP), p, n, w, 0.3f, 0.6f, 0.15f, a, scroll, 0.25f);
            if (drop >= 1f) {
                // the splash where it lands, as wide as it hits
                double y = gy + Draw.lift(top.x, top.z);
                Draw.flat(
                        Draw.lay(Draw.GLINTS),
                        top.x,
                        y,
                        top.z,
                        r,
                        Draw.time * 0.05f,
                        GOO[0] * 0.6f,
                        GOO[1] * 0.8f,
                        GOO[2] * 0.4f,
                        0.5f * on);
                Draw.sprite(
                        Draw.lay(Draw.GLINTS),
                        top.x,
                        gy + r * 0.3,
                        top.z,
                        r * 0.7f,
                        Draw.time * 0.1f + i,
                        Draw.PUFF,
                        GOO[0] * 0.7f,
                        GOO[1] * 0.9f,
                        GOO[2] * 0.5f,
                        0.45f * on);
            }
        }
    }

    /** the sun lances: a beam of burning light from each glowing spot down onto the ground, as wide as it burns */
    private static void drawLances(HollowbellEntity h, float t) {
        float s = h.bellScale();
        float on = Draw.smooth((t - Moves.LANCE_FROM) / 3f) * (1f - Draw.smooth((t - Moves.LANCE_TO) / 6f));
        // the spots burning brighter as the light gathers
        float gather = Mth.clamp(t / Moves.LANCE_FROM, 0f, 1f) * (1f - Draw.smooth((t - Moves.LANCE_TO) / 10f));
        for (int i = 0; i < 5; i++) {
            Vec3 top = h.spotWorld(i);
            float pulse = 0.85f + 0.15f * Mth.sin(Draw.time * 1.3f + i);
            Draw.sprite(
                    Draw.glow(Draw.GLINTS),
                    top.x,
                    top.y,
                    top.z,
                    (8 + 10 * gather) * s * pulse + 1,
                    0f,
                    Draw.EMBER,
                    SUN[0],
                    SUN[1],
                    SUN[2],
                    gather);
        }
        if (on <= 0.01f) return;
        float beam = 2.5f + 2.5f * s;
        for (int i = 0; i < 5; i++) {
            Vec3 top = h.spotWorld(i);
            Vec3 g = lanceGround(h, i, t);
            int n = 10;
            double[] p = new double[3 * n];
            float[] wc = new float[n], wh = new float[n], a = new float[n], ah = new float[n];
            for (int k = 0; k < n; k++) {
                float u = k / (n - 1f);
                p[3 * k] = Mth.lerp(u, top.x, g.x);
                p[3 * k + 1] = Mth.lerp(u, top.y, g.y);
                p[3 * k + 2] = Mth.lerp(u, top.z, g.z);
                float flick = 0.9f + 0.1f * Mth.sin(Draw.time * 2.1f + k * 1.7f + i);
                wc[k] = beam * (0.45f + 0.3f * u) * flick;
                wh[k] = beam * (1.3f + 0.8f * u) * flick;
                a[k] = on;
                ah[k] = 0.6f * on;
            }
            Draw.ribbon(Draw.lay(Draw.BAND), p, n, wh, 1f, 0.85f, 0.45f, ah, 0f, 0f);
            Draw.ribbon(Draw.glow(Draw.BAND), p, n, wh, SUN[0] * 0.8f, SUN[1] * 0.6f, SUN[2] * 0.3f, a, 0f, 0f);
            Draw.ribbon(Draw.lay(Draw.BAND), p, n, wc, 1f, 1f, 0.92f, a, 0f, 0f);
            Draw.ribbon(Draw.glow(Draw.BAND), p, n, wc, 1f, 1f, 0.9f, a, 0f, 0f);
            // where it burns: a white-hot spot as wide as it hurts, and heat round it
            double y = g.y + Draw.lift(g.x, g.z);
            Draw.flat(Draw.glow(Draw.GLINTS), g.x, y, g.z, beam * 2.4f, Draw.time * 0.2f, 1f, 0.7f, 0.3f, on);
            Draw.sprite(
                    Draw.glow(Draw.GLINTS),
                    g.x,
                    g.y + beam * 0.5,
                    g.z,
                    beam * 2.5f,
                    0f,
                    Draw.EMBER,
                    1f,
                    0.8f,
                    0.45f,
                    on);
            Draw.sprite(
                    Draw.lay(Draw.GLINTS),
                    g.x,
                    g.y + beam * 0.4,
                    g.z,
                    beam * 1.6f,
                    0f,
                    Draw.EMBER,
                    1f,
                    0.95f,
                    0.75f,
                    0.8f * on);
        }
    }

    /**
     * The whirlpool and the undertow: dust and grit on the ground dragged round and in under him, out to as far as
     * the pull reaches (round and in for the whirlpool, straight in for the undertow).
     */
    private static void drawWhirl(HollowbellEntity h, float t, float R, int from, int to, boolean round) {
        if (t < from - 4 || t > to + 6) return;
        float on = Draw.smooth((t - from + 4) / 8f) * (1f - Draw.smooth((t - to) / 6f));
        float s = h.bellScale();
        double cx = h.getX(), cz = h.getZ();
        double gy = ground(h, cx, cz);
        int arms = round ? 14 : 28;
        float dust = 0.25f + 0.55f * Draw.daylight;
        for (int k = 0; k < arms; k++) {
            float seed = k * 2.39996f;
            int n = 9;
            double[] p = new double[3 * n];
            float[] w = new float[n], a = new float[n];
            // each streak runs in from the edge over and over, a little out of step with the others
            float run = ((t * (round ? 0.012f : 0.03f)) + k * 0.137f) % 1f;
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1f);
                float rr = R * (1f - run) * (1f - 0.25f * u) + R * 0.05f;
                double ang = seed + (round ? (1f - rr / R) * 2.6f + t * 0.05f + u * 0.5f : 0f);
                double x = cx + Math.cos(ang) * rr, z = cz + Math.sin(ang) * rr;
                double y = (i % 3 == 0 ? ground(h, x, z) : gy) + 0.6 * s + 0.3;
                p[3 * i] = x;
                p[3 * i + 1] = y;
                p[3 * i + 2] = z;
                w[i] = (round ? 5f : 3.5f) * s + 0.8f;
                a[i] = on * (1f - u * 0.6f) * Mth.sin(run * Mth.PI) * 0.85f;
            }
            Draw.ribbon(Draw.lay(Draw.WISP), p, n, w, 0.78f * dust, 0.72f * dust, 0.6f * dust, a, run * 4f, 0.2f);
        }
    }

    /** the sky dive: the air rushing past him as he comes down crown first */
    private static void drawDiveAir(HollowbellEntity h, float t, float pt) {
        int fall = Moves.DIVE_CLIMB + Moves.DIVE_TURN, back = fall + Moves.DIVE_FALL;
        if (t < fall - 4 || t > back) return;
        float s = h.bellScale();
        float on = Draw.smooth((t - fall + 4) / 8f);
        Vec3 c = h.getPosition(pt).add(0, RIG.rimY * s * 0.5, 0);
        Vec3 v = h.velocity();
        double sp = v.length();
        if (sp < 0.2) return;
        Vec3 d = v.scale(1 / sp);
        double len = Math.min(sp * 8, 80 * s + 10);
        double bell = h.bellRadius();
        for (int k = 0; k < 16; k++) {
            double a = k * 0.3927 + (k % 2) * 0.2;
            double rr = bell * (0.7 + 0.3 * ((k * 7) % 5) / 4.0);
            double off = (k * 13 % 7) / 7.0 * RIG.rimY * s;
            Vec3 base = c.add(Math.cos(a) * rr, -d.y * off * 0.5, Math.sin(a) * rr);
            int n = 6;
            double[] p = new double[3 * n];
            float[] w = new float[n], al = new float[n];
            for (int i = 0; i < n; i++) {
                float u = i / (n - 1f);
                p[3 * i] = base.x - d.x * len * u;
                p[3 * i + 1] = base.y - d.y * len * u;
                p[3 * i + 2] = base.z - d.z * len * u;
                w[i] = (1.2f * s + 0.3f) * (1f - 0.6f * u);
                al[i] = 0.35f * on * Mth.sin(u * Mth.PI);
            }
            Draw.ribbon(Draw.lay(Draw.BAND), p, n, w, 0.95f, 1f, 0.97f, al, 0f, 0f);
        }
    }

    /**
     * The glow flash: his light swelling in every spot, flickering out for a moment, then bursting out of him as far
     * as it blinds (50s + 30 round the middle of his dome), in rays and a bloom that swallows him.
     */
    private static void drawFlash(HollowbellEntity h, float t) {
        float s = h.bellScale();
        float build = Mth.clamp(t / (Moves.FLASH_AT - 4f), 0f, 1f);
        float burst = t >= Moves.FLASH_AT - 1 ? (float) Math.exp(-(t - Moves.FLASH_AT + 1) / 6f) : 0f;
        if (t > Moves.FLASH_AT - 4 && t < Moves.FLASH_AT - 1) build = 0.1f;
        Vec3 c = h.position().add(0, (RIG.rimY + 30) * s, 0);
        // (the spots are under his dome: their light is drawn out at his skin, toward you, so it shows)
        Vec3 toCam = new Vec3(Draw.cx - c.x, Draw.cy - c.y, Draw.cz - c.z).normalize();
        double skin = h.bellRadius() * 0.9;
        for (int i = 0; i < RIG.spots.length; i++) {
            Vec3 p = h.spotWorld(i).add(toCam.scale(skin * 0.6));
            float flick = 0.8f + 0.2f * Mth.sin(Draw.time * (1.2f + 0.5f * build) + i * 2f);
            float size = (10 * build * flick + 24 * burst) * s + 1;
            Draw.sprite(
                    Draw.glow(Draw.GLINTS),
                    p.x,
                    p.y,
                    p.z,
                    size,
                    0f,
                    Draw.EMBER,
                    0.9f,
                    1f,
                    0.8f,
                    Math.min(1f, 0.6f * build + burst));
        }
        if (burst < 0.02f) return;
        Vec3 front = c.add(toCam.scale(skin));
        float reach = 50 * s + 30;
        // (the bloom washes over him and all in front of him, as a flash does)
        Draw.sprite(
                Draw.over(Draw.GLINTS),
                front.x,
                front.y,
                front.z,
                (reach * 1.3f + (float) h.bellRadius() * 0.6f) * (1.1f - 0.3f * burst) + 6,
                0f,
                Draw.EMBER,
                1f,
                1f,
                0.85f,
                0.9f * burst);
        Draw.sprite(
                Draw.lay(Draw.GLINTS),
                front.x,
                front.y,
                front.z,
                reach * 0.6f + 2,
                0f,
                Draw.EMBER,
                1f,
                1f,
                0.92f,
                0.8f * burst);
        // rays: long thin streaks out of him every way, fixed for this flash
        float k = 1f - burst;
        for (int r = 0; r < 16; r++) {
            double a = r * 2.39996, e = Math.sin(r * 1.7) * 0.9;
            Vec3 d = new Vec3(Math.cos(a) * Math.cos(e), Math.sin(e), Math.sin(a) * Math.cos(e));
            double len = Math.max(h.bellRadius() * 1.15, reach) * (0.8 + 0.3 * ((r * 7) % 5) / 4.0) * (0.6 + 0.5 * k);
            int n = 5;
            double[] p = new double[3 * n];
            float[] w = new float[n], al = new float[n];
            for (int q = 0; q < n; q++) {
                float u = q / (n - 1f);
                Vec3 at = c.add(d.scale(len * u));
                p[3 * q] = at.x;
                p[3 * q + 1] = at.y;
                p[3 * q + 2] = at.z;
                w[q] = (3f + 6f * u) * s + 0.6f;
                al[q] = burst * (1f - u) * 0.9f;
            }
            Draw.ribbon(Draw.glow(Draw.BAND), p, n, w, 1f, 1f, 0.85f, al, 0f, 0f);
        }
    }

    /** a stinger in flight: a thin green streak of poison behind it, fading */
    private static void drawStinger(Trail t, float pt) {
        if (t.n < 2 || t.quiet > 0) return;
        int n = t.n;
        double[] p = new double[3 * n];
        float[] w = new float[n], a = new float[n];
        float half = 0.4f + 1.4f * t.size;
        for (int i = 0; i < n; i++) {
            int o = Math.min(i + 1, n - 1);
            double f = i + 1 < n ? pt : 1;
            p[3 * i] = Mth.lerp(f, t.p[3 * o], t.p[3 * i]);
            p[3 * i + 1] = Mth.lerp(f, t.p[3 * o + 1], t.p[3 * i + 1]);
            p[3 * i + 2] = Mth.lerp(f, t.p[3 * o + 2], t.p[3 * i + 2]);
            float u = i / (n - 1f);
            w[i] = half * (1f - 0.8f * u);
            a[i] = (1f - u);
        }
        Draw.ribbon(Draw.lay(Draw.BAND), p, n, w, 0.45f, 0.85f, 0.3f, a, 0f, 0f);
        Draw.ribbon(Draw.glow(Draw.BAND), p, n, w, GOO[0] * 0.5f, GOO[1] * 0.7f, GOO[2] * 0.35f, a, 0f, 0f);
        Draw.sprite(
                Draw.glow(Draw.GLINTS), p[0], p[1], p[2], half * 2.2f, 0f, Draw.EMBER, GOO[0], GOO[1], GOO[2], 0.7f);
    }

    private static double sq(double v) {
        return v * v;
    }
}
