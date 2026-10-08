package net.jj.hollowbell.solid;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The solid kit: big creatures you bump into, stand on and ride, and are never left inside of.
 *
 * His parts aren't blocks of the world, so the game never finds them when it looks for what a moving thing bumps
 * into: whenever something moves near him, the parts of him it could bump into are added (SolidCollideMixin), as
 * boxes a column wide up each world column, worked out from his pose this tick (only near what moves, once per pose).
 * A bump up to a step high is walked onto and something a little sunk in is lifted out (like stairs); whatever ends up
 * inside him (he moved into it, it was put there, he grew) is moved at once to the nearest free spot, preferring the
 * way the part was moving, so a moving part shoves and never traps. Whatever stands on him is carried along exactly:
 * the server carries mobs and dropped things, your own game carries you, every frame as he's drawn (SolidClient).
 *
 * A player is only ever moved by their own game: the server adds no parts of him to what a player bumps into (the
 * two would disagree and snap you back), doesn't call standing on him flying, and only moves a player out of him if
 * they have been well inside him for half a second (their game should have done it).
 */
public final class Solid {
    private Solid() {}

    static final List<SolidBody> SERVER = new CopyOnWriteArrayList<>(), CLIENT = new CopyOnWriteArrayList<>();

    public static void track(SolidBody b) {
        List<SolidBody> l = b.solidSelf().level().isClientSide ? CLIENT : SERVER;
        if (!l.contains(b)) l.add(b);
    }
    public static void untrack(SolidBody b) { SERVER.remove(b); CLIENT.remove(b); }
    public static void forgetAll(boolean client) { (client ? CLIENT : SERVER).clear(); }
    public static List<SolidBody> bodies(boolean client) { return client ? CLIENT : SERVER; }

    /** counters for the tests and the tick-time report */
    public static long nanosServer, nanosClient, queries;
    public static int unstuckServer, unstuckClient;

    // ------------------------------------------------------------------ who

    /** what never bumps into anyone's parts */
    static boolean ignoresAll(Entity e) {
        return e.noPhysics || e instanceof Projectile || e.isSpectator() || e.isPassenger() || e instanceof SolidBody;
    }

    static boolean live(SolidBody b, Entity e) {
        Entity s = b.solidSelf();
        if (s.isRemoved()) { untrack(b); return false; }
        return s.level() == e.level() && b.solidReady() && !b.solidIgnores(e);
    }

    // ------------------------------------------------------------------ his frames this tick

    /** his frames as of now (worked out once per pose) */
    public static SolidCache frames(SolidBody b) {
        SolidCache c = b.solidCache();
        Entity s = b.solidSelf();
        long tick = s.level().getGameTime();
        // (worked out again when his pose, his place, his facing or his size has changed)
        if (c.stamp == c.poseVersion && c.tick == tick && c.ox == s.getX() && c.oy == s.getY() && c.oz == s.getZ() && c.yaw == s.getYRot()
                && c.scale == b.solidScale()) return c;
        SolidShape sh = b.solidShape();
        int n = sh.frames();
        c.ensure(n);
        if (c.tick != tick) {
            c.havePrev = c.tick == tick - 1;
            if (c.havePrev) { for (int i = 0; i < n; i++) c.prevWorld[i].set(c.toWorld[i]); c.pox = c.ox; c.poy = c.oy; c.poz = c.oz; }
        }
        Matrix4f root = b.solidRoot(1f, new Matrix4f());
        Matrix4f[] pose = b.solidPose();
        c.ox = s.getX(); c.oy = s.getY(); c.oz = s.getZ(); c.yaw = s.getYRot(); c.scale = b.solidScale();
        for (int i = 0; i < n; i++) {
            c.on[i] = b.solidBoneOn(sh.bone[i]);
            c.toWorld[i].set(root).mul(pose[sh.bone[i]]);
            c.toWorld[i].invert(c.toRest[i]);
            SolidCache.worldBox(c.toWorld[i], sh.bounds[i], c.box[i]);
        }
        c.stamp = c.poseVersion; c.tick = tick; c.builds++;
        return c;
    }

    /** a world point into frame f as built */
    public static Vector3f toRest(SolidCache c, int f, double x, double y, double z, Vector3f out) {
        return c.toRest[f].transformPosition((float) (x - c.ox), (float) (y - c.oy), (float) (z - c.oz), out);
    }
    /** a point of frame f as built, in the world now */
    public static Vec3 toWorld(SolidCache c, int f, Vector3f rest) {
        Vector3f v = c.toWorld[f].transformPosition(rest, new Vector3f());
        return new Vec3(v.x + c.ox, v.y + c.oy, v.z + c.oz);
    }
    /** how far a point of frame f moved since last tick (zero if not known) */
    public static Vec3 motion(SolidCache c, int f, Vector3f rest) {
        if (!c.havePrev) return Vec3.ZERO;
        Vector3f a = c.toWorld[f].transformPosition(rest, new Vector3f()), b = c.prevWorld[f].transformPosition(rest, new Vector3f());
        return new Vec3(a.x + c.ox - b.x - c.pox, a.y + c.oy - b.y - c.poy, a.z + c.oz - b.z - c.poz);
    }
    /** which way frame f faces in the world now (degrees, the game's yaw) */
    public static float yawOf(Matrix4f m) {
        Vector3f fw = m.transformDirection(new Vector3f(0, 0, 1));
        return (float) Math.toDegrees(Math.atan2(-fw.x, fw.z));
    }

    // ------------------------------------------------------------------ up a world column

    /** solid stretches up a world column: bottom and top (world y), frame and kind, for each */
    public static final class Runs {
        public double[] bot = new double[16], top = new double[16];
        public int[] frame = new int[16];
        public byte[] kind = new byte[16];
        public int n;
        void add(double b, double t, int f, byte k) {
            if (n == bot.length) {
                bot = java.util.Arrays.copyOf(bot, n * 2); top = java.util.Arrays.copyOf(top, n * 2);
                frame = java.util.Arrays.copyOf(frame, n * 2); kind = java.util.Arrays.copyOf(kind, n * 2);
            }
            bot[n] = b; top[n] = t; frame[n] = f; kind[n] = k; n++;
        }
    }

    private static final ThreadLocal<SolidShape.FloatList> SCRATCH = ThreadLocal.withInitial(SolidShape.FloatList::new);

    /** every solid (and soft, if asked) stretch of him up the world column x z, between y0 and y1 */
    public static Runs column(SolidBody b, double x, double z, double y0, double y1, boolean soft, Runs out) {
        out.n = 0;
        SolidCache c = frames(b);
        SolidShape sh = b.solidShape();
        SolidShape.FloatList t = SCRATCH.get();
        float lx = (float) (x - c.ox), lz = (float) (z - c.oz), ly0 = (float) (y0 - c.oy), ly1 = (float) (y1 - c.oy);
        Vector3f a = new Vector3f(), d = new Vector3f();
        queries++;
        for (int f : c.framesAt(lx, lz)) {
            if (!c.on[f] || (!soft && sh.kind[f] == SolidShape.SOFT)) continue;
            float[] bx = c.box[f];
            if (lx < bx[0] || lx > bx[3] || lz < bx[2] || lz > bx[5] || ly1 < bx[1] || ly0 > bx[4]) continue;
            c.toRest[f].transformPosition(lx, ly0, lz, a);
            c.toRest[f].transformDirection(0, 1, 0, d);
            t.clear();
            sh.march(f, a.x, a.y, a.z, d.x, d.y, d.z, ly1 - ly0, t);
            for (int i = 0; i < t.size(); i += 2) out.add(y0 + t.get(i), y0 + t.get(i + 1), f, sh.kind[f]);
        }
        return out;
    }

    /** how wide a column is: a block, or less for a small one (so his thinner parts still count) */
    public static double columnWidth(SolidBody b) {
        float s = b.solidScale();
        return s >= 1f ? 1.0 : 0.5;
    }

    // ------------------------------------------------------------------ bumping into him

    /**
     * What an entity moving through box could land on or bump into: for each column, a box for each solid stretch. A
     * stretch whose top is at most a step over its feet (or that it has sunk into) becomes a floor at its feet, so it
     * walks straight over bumps; {@link #pushUp} then lifts it onto the real top.
     */
    public static List<VoxelShape> addShapes(Entity e, AABB box, List<VoxelShape> list) {
        boolean client = e.level().isClientSide;
        List<SolidBody> bs = client ? CLIENT : SERVER;
        if (bs.isEmpty() || ignoresAll(e)) return list;
        if (!client && e instanceof ServerPlayer) return list;                    // their own game does it
        long t0 = System.nanoTime();
        double feet = e.getBoundingBox().minY, height = e.getBbHeight();
        AABB cur = e.getBoundingBox().deflate(1e-3, 0, 1e-3);
        List<VoxelShape> out = null;
        Runs r = new Runs();
        for (SolidBody b : bs) {
            if (!live(b, e) || !b.solidBox().intersects(box) || !near(b, box.expandTowards(0, -1.1, 0))) continue;
            double cw = columnWidth(b), step = b.solidStep();
            int xa = Mth.floor(box.minX / cw), xb = Mth.floor((box.maxX - 1e-7) / cw), za = Mth.floor(box.minZ / cw), zb = Mth.floor((box.maxZ - 1e-7) / cw);
            if ((long) (xb - xa + 1) * (zb - za + 1) > 600) continue;
            double y0 = box.minY - 2, y1 = Math.max(box.maxY, feet + step) + 1.5;
            for (int i = xa; i <= xb; i++) for (int k = za; k <= zb; k++) {
                column(b, (i + 0.5) * cw, (k + 0.5) * cw, y0, y1, false, r);
                for (int j = 0; j < r.n; j++) {
                    double bot = r.bot[j], top = r.top[j];
                    AABB s;
                    if (top <= feet + 1e-4) {                                       // under it: a floor
                        if (top < box.minY - 0.01) continue;
                        s = new AABB(i * cw, Math.max(bot, top - 1), k * cw, (i + 1) * cw, top, (k + 1) * cw);
                    } else if (top - feet <= step && bot < feet + 0.5 && headroom(r, j, height)) {   // a bump: walked onto
                        s = new AABB(i * cw, Math.min(bot, feet - 1), k * cw, (i + 1) * cw, feet, (k + 1) * cw);
                    } else if (bot < feet && top > feet && in(cur, i, k, cw)) {    // sunk into it (already in that column): held where it is
                        s = new AABB(i * cw, feet - 1, k * cw, (i + 1) * cw, feet, (k + 1) * cw);
                    } else {                                                        // a wall, or him overhead
                        if (bot > box.maxY || top < box.minY) continue;
                        s = new AABB(i * cw, bot, k * cw, (i + 1) * cw, top, (k + 1) * cw);
                    }
                    if (out == null) out = new ArrayList<>(list);
                    out.add(Shapes.create(s));
                }
            }
        }
        time(client, t0);
        return out == null ? list : out;
    }

    /** a box reaches over column (i, k) already (not just moving into it) */
    private static boolean in(AABB b, int i, int k, double cw) { return b.minX < (i + 1) * cw && b.maxX > i * cw && b.minZ < (k + 1) * cw && b.maxZ > k * cw; }

    /**
     * Room over stretch j's top in its column for something this tall (no other stretch of him there): only then is a
     * bump walked onto or a sunk foot lifted onto it, the way the game's own step-up looks for headroom first.
     */
    static boolean headroom(Runs r, int j, double height) {
        double top = r.top[j];
        for (int q = 0; q < r.n; q++) if (q != j && r.bot[q] < top + height - 0.01 && r.top[q] > top + 0.01) return false;
        return true;
    }

    static void time(boolean client, long t0) { long d = System.nanoTime() - t0; if (client) nanosClient += d; else nanosServer += d; }

    /** the highest top of his, under box, that something whose feet were at feet0 should be lifted onto (or NaN) */
    static double surfaceUnder(SolidBody b, AABB box, double feet0, Runs r) {
        double best = Double.NaN, cw = columnWidth(b), step = b.solidStep();
        double height = box.getYsize();
        int xa = Mth.floor(box.minX / cw), xb = Mth.floor((box.maxX - 1e-7) / cw), za = Mth.floor(box.minZ / cw), zb = Mth.floor((box.maxZ - 1e-7) / cw);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 144) return best;
        for (int i = xa; i <= xb; i++) for (int k = za; k <= zb; k++) {
            column(b, (i + 0.5) * cw, (k + 0.5) * cw, feet0 - 2, feet0 + step + 0.5 + height, false, r);
            for (int j = 0; j < r.n; j++) {
                double top = r.top[j];
                if (top <= box.minY || top - feet0 > step || r.bot[j] >= feet0 + 0.5 || !headroom(r, j, height)) continue;
                if (top >= feet0 + step + 0.49) continue;                         // (clipped: taller than a step)
                best = Double.isNaN(best) ? top : Math.max(best, top);
            }
        }
        return best;
    }

    /** after a move is worked out: feet a little sunk into him are lifted onto him, a bit a tick */
    public static Vec3 pushUp(Entity e, Vec3 moved) {
        boolean client = e.level().isClientSide;
        List<SolidBody> bs = client ? CLIENT : SERVER;
        if (bs.isEmpty() || moved.y > 0.0 || ignoresAll(e)) return moved;
        if (!client && e instanceof ServerPlayer) return moved;
        long t0 = System.nanoTime();
        double feet0 = e.getBoundingBox().minY;
        AABB nb = e.getBoundingBox().move(moved);
        double best = Double.NaN;
        Runs r = new Runs();
        for (SolidBody b : bs) {
            if (!live(b, e) || !b.solidBox().intersects(nb.inflate(0, 1, 0)) || !near(b, nb.inflate(0, 1, 0))) continue;
            double top = surfaceUnder(b, nb, feet0, r);
            if (!Double.isNaN(top) && (Double.isNaN(best) || top > best)) best = top;
        }
        time(client, t0);
        if (Double.isNaN(best) || best <= nb.minY + 1e-4) return moved;
        return new Vec3(moved.x, moved.y + Math.min(best - nb.minY, 0.6), moved.z);
    }

    // ------------------------------------------------------------------ never inside him

    /**
     * How deep box is in his solid parts (0 not at all): the most any column's stretch overlaps it, the box first
     * made smaller by `in` on every side (so standing on him or brushing him isn't being inside).
     */
    public static double depthIn(SolidBody b, AABB box, double in, @Nullable int[] frameOut) { return depthIn(b, box, in, frameOut, false); }

    /**
     * liftable: a top a step or less over the feet (a floor sunk into a little) doesn't count, it's lifted onto
     * ({@link #pushUp}), not moved out of.
     */
    public static double depthIn(SolidBody b, AABB box, double in, @Nullable int[] frameOut, boolean liftable) {
        AABB d = box.deflate(Math.min(in, box.getXsize() * 0.4), Math.min(in, box.getYsize() * 0.4), Math.min(in, box.getZsize() * 0.4));
        double cw = columnWidth(b), worst = 0, feet = box.minY, step = b.solidStep();
        int xa = Mth.floor(d.minX / cw), xb = Mth.floor((d.maxX - 1e-7) / cw), za = Mth.floor(d.minZ / cw), zb = Mth.floor((d.maxZ - 1e-7) / cw);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 400) return 0;
        Runs r = new Runs();
        for (int i = xa; i <= xb; i++) for (int k = za; k <= zb; k++) {
            column(b, (i + 0.5) * cw, (k + 0.5) * cw, Math.min(d.minY - 0.01, feet - 1), Math.max(d.maxY + 0.01, feet + step + 0.1), false, r);
            for (int j = 0; j < r.n; j++) {
                if (liftable && r.top[j] - feet <= step && r.bot[j] < feet + 0.5 && headroom(r, j, box.getYsize())) continue;
                double o = Math.min(r.top[j], d.maxY) - Math.max(r.bot[j], d.minY);
                if (o > worst) { worst = o; if (frameOut != null) frameOut[0] = r.frame[j]; }
            }
        }
        return worst;
    }

    public static boolean inside(SolidBody b, Entity e, double in) {
        return b.solidBox().intersects(e.getBoundingBox()) && depthIn(b, e.getBoundingBox(), in, null, true) > 0.02;
    }

    /** a box is clear of him and of the world's blocks */
    public static boolean clear(SolidBody b, Entity e, AABB box) {
        return depthIn(b, box, 0.02, null) <= 0.0 && e.level().noCollision(e, box);
    }

    /**
     * The nearest spot to `from` where e fits clear of him and of the world: straight up out of him (onto him), the
     * way the part that had it was moving, or out sideways (away from his middle first). Going up wins unless out
     * sideways is much nearer, so you end up standing on the part that came up under you.
     */
    public static Vec3 freeSpot(SolidBody b, Entity e, Vec3 from, Vec3 push) {
        var dim = e.getDimensions(e.getPose());
        if (clear(b, e, dim.makeBoundingBox(from))) return from;
        AABB body = b.solidBox();
        double step = Math.max(0.2, Math.min(0.75, 0.5 * columnWidth(b)));
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (double dy = step * 0.5; from.y + dy < body.maxY + 3 && dy < 64; dy += step) {
            Vec3 c = from.add(0, dy, 0);
            if (clear(b, e, dim.makeBoundingBox(c))) { best = c; bestD = dy * 0.66; break; }
        }
        Vec3 mid = b.solidSelf().position();
        double base = Math.atan2(from.z - mid.z, from.x - mid.x);
        double ph = Math.hypot(push.x, push.z);
        if (ph > 0.02) base = Math.atan2(push.z, push.x);
        double far = Math.min(48, Math.hypot(body.getXsize(), body.getZsize()) + 4);
        for (double turn : new double[]{0, 1, -1, 2, -2, 3, -3, 4}) {
            double ang = base + turn * Math.PI / 4;
            for (double dd = step; dd < Math.min(bestD, far); dd += step) {
                Vec3 c = from.add(Math.cos(ang) * dd, 0, Math.sin(ang) * dd);
                if (!clear(b, e, dim.makeBoundingBox(c))) continue;
                double cost = dd * (turn == 0 ? 1.0 : 1.15);
                if (cost < bestD) { best = c; bestD = cost; }
                break;
            }
        }
        if (best != null) {
            // (shoved on a little the way the part is going, if there's room, so it isn't in its way again at once)
            if (ph > 0.02) {
                Vec3 on = best.add(push.x / ph * Math.min(ph, 1.0), 0, push.z / ph * Math.min(ph, 1.0));
                if (clear(b, e, dim.makeBoundingBox(on))) return on;
            }
            return best;
        }
        // nothing near: up over the top of him
        return new Vec3(from.x, Math.max(body.maxY + 1, groundNow(e.level(), from.x, from.z, body.maxY + 1)), from.z);
    }

    /** moves e out of him if it's inside; true if it was */
    public static boolean unstick(SolidBody b, Entity e, double in) {
        int[] fr = {-1};
        if (!b.solidBox().intersects(e.getBoundingBox()) || depthIn(b, e.getBoundingBox(), in, fr, true) <= 0.02) return false;
        SolidCache c = frames(b);
        Vec3 push = fr[0] >= 0 ? motion(c, fr[0], toRest(c, fr[0], e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), new Vector3f())) : Vec3.ZERO;
        Vec3 to = freeSpot(b, e, e.position(), push);
        if (e instanceof ServerPlayer sp) sp.teleportTo(sp.serverLevel(), to.x, to.y + 0.01, to.z, sp.getYRot(), sp.getXRot());
        else e.setPos(to.x, to.y + 0.01, to.z);
        e.resetFallDistance();
        // shoved the way the part went (a little), never flung
        Vec3 v = e.getDeltaMovement();
        double k = Math.min(1.0, 0.6 / Math.max(0.6, push.length()));
        e.setDeltaMovement(new Vec3(push.x * k, Math.max(v.y, Math.max(0, push.y * k)), push.z * k));
        e.hurtMarked = true;
        return true;
    }

    /**
     * Soft parts (strands, kelp, sails) never hold anyone: something in among them is nudged out sideways, away from
     * the part's middle and along the way the part moves.
     */
    public static boolean softPush(SolidBody b, Entity e) {
        AABB box = e.getBoundingBox();
        if (!b.solidBox().intersects(box)) return false;
        SolidCache c = frames(b);
        SolidShape sh = b.solidShape();
        Runs r = new Runs();
        column(b, e.getX(), e.getZ(), box.minY + 0.1, box.maxY - 0.1, true, r);
        for (int j = 0; j < r.n; j++) {
            if (r.kind[j] != SolidShape.SOFT) continue;
            int f = r.frame[j];
            float[] bx = c.box[f];
            double mx = c.ox + (bx[0] + bx[3]) / 2, mz = c.oz + (bx[2] + bx[5]) / 2;
            Vec3 away = new Vec3(e.getX() - mx, 0, e.getZ() - mz);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 mv = motion(c, f, toRest(c, f, e.getX(), (r.bot[j] + r.top[j]) / 2, e.getZ(), new Vector3f()));
            Vec3 add = away.scale(0.08).add(mv.x * 0.5, 0, mv.z * 0.5);
            e.setDeltaMovement(e.getDeltaMovement().add(add));
            if (!e.level().isClientSide) e.hurtMarked = true;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ standing on him

    /** e stands on (or just over) him: within `over` blocks above one of his tops under its feet */
    public static boolean onTop(SolidBody b, Entity e, double over, double under) {
        return anchorUnder(b, e, over, under) != null;
    }

    /** standing on him, or close over him: the server mustn't call that flying */
    public static boolean overAny(Entity e) {
        List<SolidBody> bs = e.level().isClientSide ? CLIENT : SERVER;
        for (SolidBody b : bs) {
            if (!live(b, e) || !b.solidBox().inflate(0, 8, 0).intersects(e.getBoundingBox())) continue;
            if (anchorUnder(b, e, 8.0, 3.0) != null) return true;
        }
        return false;
    }

    /** a spot fixed to one of his frames (as built), and where it was in the world when it was taken */
    public record Anchor(int frame, Vector3f rest) {}

    /**
     * The spot of him e stands on: the top nearest its feet among the columns under it, from `over` blocks under its
     * feet up to `under` over them; e's own position as a point fixed to that frame (or null).
     */
    public static @Nullable Anchor anchorUnder(SolidBody b, Entity e, double over, double under) {
        if (!live(b, e) || !b.solidBox().inflate(0, over + 1, 0).intersects(e.getBoundingBox())) return null;
        double feet = e.getY(), cw = columnWidth(b);
        AABB box = e.getBoundingBox().deflate(0.05, 0, 0.05);
        int xa = Mth.floor(box.minX / cw), xb = Mth.floor((box.maxX - 1e-7) / cw), za = Mth.floor(box.minZ / cw), zb = Mth.floor((box.maxZ - 1e-7) / cw);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 64) { xa = xb = Mth.floor(e.getX() / cw); za = zb = Mth.floor(e.getZ() / cw); }
        Runs r = new Runs();
        int bestF = -1;
        double bestD = Double.MAX_VALUE;
        // (the column right under its middle first, then those under the rest of it: a thin top between the middles of
        // the columns is still found)
        for (int i = xa - 1; i <= xb; i++) for (int k = za; k <= zb; k++) {
            if (i < xa && k > za) break;
            if (i < xa) column(b, e.getX(), e.getZ(), feet - over - 1, feet + under, false, r);
            else column(b, (i + 0.5) * cw, (k + 0.5) * cw, feet - over - 1, feet + under, false, r);
            for (int j = 0; j < r.n; j++) {
                double top = r.top[j];
                if (top > feet + under - 1e-3 || top < feet - over) continue;
                double dd = Math.abs(top - feet);
                if (dd < bestD) { bestD = dd; bestF = r.frame[j]; }
            }
        }
        if (bestF < 0) return null;
        SolidShape sh = b.solidShape();
        int cf = sh.frameOfBone[b.solidCarryBone(sh.bone[bestF])];
        if (cf >= 0) bestF = cf;
        return new Anchor(bestF, toRest(frames(b), bestF, e.getX(), e.getY(), e.getZ(), new Vector3f()));
    }

    /** standing on (an anchor under) any of them: which */
    public static @Nullable SolidBody under(Entity e) {
        for (SolidBody b : e.level().isClientSide ? CLIENT : SERVER) if (anchorUnder(b, e, 0.9, 1.5) != null) return b;
        return null;
    }

    public static Vec3 anchorWorld(SolidBody b, Anchor a) { return toWorld(frames(b), a.frame(), a.rest()); }

    /** where something carried along should stand: never left a little sunk into the top it stands on */
    public static Vec3 carriedTo(SolidBody b, Vec3 now) {
        double best = Double.NaN;
        Runs r = column(b, now.x, now.z, now.y - 1, now.y + 0.8, false, new Runs());
        for (int j = 0; j < r.n; j++) {
            double top = r.top[j];
            if (top > now.y - 0.01 && top - now.y < 0.7 && top < now.y + 0.79 && r.bot[j] < now.y + 0.5) best = Double.isNaN(best) ? top : Math.max(best, top);
        }
        return Double.isNaN(best) ? now : new Vec3(now.x, best, now.z);
    }

    // ------------------------------------------------------------------ the server's part, each tick of his

    /**
     * End of his server tick: mobs and dropped things on him move and turn with him (players are carried by their
     * own game); whatever is inside him is moved out at once (a player only after half a second well inside); soft
     * parts nudge things aside.
     */
    public static void serverTick(SolidBody b) {
        Entity s = b.solidSelf();
        SolidCache c = b.solidCache();
        if (!b.solidReady()) { c.riding.clear(); c.stuck.clear(); return; }
        long t0 = System.nanoTime();
        var level = s.level();
        // carried along
        for (var en : c.riding.int2ObjectEntrySet()) {
            Entity r = level.getEntity(en.getIntKey());
            if (r == null || r.isRemoved() || r.isPassenger() || r instanceof Player || r.isInWater() && !r.onGround()) continue;
            Carried cd = en.getValue();
            SolidCache fc = frames(b);
            if (cd.frame >= fc.toWorld.length || !fc.on[cd.frame]) continue;
            Vec3 d = toWorld(fc, cd.frame, cd.rest).subtract(cd.was);
            if (d.lengthSqr() > 64) continue;
            Vec3 to = carriedTo(b, new Vec3(r.getX() + d.x, r.getY() + d.y, r.getZ() + d.z));
            r.setPos(to.x, to.y, to.z);
            float dy = Mth.wrapDegrees(yawOf(fc.toWorld[cd.frame]) - cd.yaw);
            if (Math.abs(dy) < 45f) {
                r.setYRot(r.getYRot() + dy);
                if (r instanceof LivingEntity le) { le.setYBodyRot(le.yBodyRot + dy); le.setYHeadRot(le.getYHeadRot() + dy); }
            }
        }
        c.riding.clear();
        AABB all = b.solidBox();
        for (Entity e : level.getEntities(s, all, x -> !ignoresAll(x) && !b.solidIgnores(x) && !(x instanceof SolidBody))) {
            if (e instanceof Player p) {
                if (p.isCreative() && p.getAbilities().flying) continue;
                // their own game moves them out at once; this is for when it didn't
                if (depthIn(b, e.getBoundingBox(), 0.3, null, true) > 0.3) {
                    int n = c.stuck.merge(e.getId(), 1, Integer::sum);
                    if (n >= 10) { c.stuck.remove(e.getId()); if (unstick(b, e, 0.3)) unstuckServer++; }
                } else c.stuck.remove(e.getId());
                continue;
            }
            // (one look up the columns under it does for all three: is it in him, in his soft parts, on him)
            if (!near(b, e.getBoundingBox().expandTowards(0, -1.5, 0))) continue;
            Probe pr = probe(b, e, 0.08);
            if (pr.depth > 0.02) { if (unstick(b, e, 0.08)) unstuckServer++; continue; }
            if (pr.soft) softPush(b, e);
            if (pr.anchor >= 0) {
                SolidCache fc = frames(b);
                SolidShape sh = b.solidShape();
                int f = sh.frameOfBone[b.solidCarryBone(sh.bone[pr.anchor])];
                if (f < 0) f = pr.anchor;
                c.riding.put(e.getId(), new Carried(f, toRest(fc, f, e.getX(), e.getY(), e.getZ(), new Vector3f()), e.position(), yawOf(fc.toWorld[f])));
            }
        }
        time(false, t0);
    }

    /** is any part of him (his frames' boxes) about this box at all: the quick first look */
    public static boolean near(SolidBody b, AABB box) {
        SolidCache c = frames(b);
        // (the whole columns the box reaches into: a column counts as solid by the line up its middle, which can be
        // outside the box itself)
        double cw = columnWidth(b);
        box = new AABB(Math.floor(box.minX / cw) * cw, box.minY, Math.floor(box.minZ / cw) * cw, Math.ceil(box.maxX / cw) * cw, box.maxY, Math.ceil(box.maxZ / cw) * cw);
        double[] xs = {box.minX, box.maxX, (box.minX + box.maxX) / 2}, zs = {box.minZ, box.maxZ, (box.minZ + box.maxZ) / 2};
        float x0 = (float) (box.minX - c.ox), x1 = (float) (box.maxX - c.ox), y0 = (float) (box.minY - c.oy), y1 = (float) (box.maxY - c.oy), z0 = (float) (box.minZ - c.oz), z1 = (float) (box.maxZ - c.oz);
        for (double x : xs) for (double z : zs)
            for (int f : c.framesAt(x - c.ox, z - c.oz)) {
                if (!c.on[f]) continue;
                float[] q = c.box[f];
                if (q[0] <= x1 && q[3] >= x0 && q[1] <= y1 && q[4] >= y0 && q[2] <= z1 && q[5] >= z0) return true;
            }
        return false;
    }

    /** what one look up the columns under an entity finds: how deep in him it is, in his soft parts or not, what it stands on */
    static final class Probe { double depth; boolean soft; int anchor = -1; }

    static Probe probe(SolidBody b, Entity e, double in) {
        Probe out = new Probe();
        AABB box = e.getBoundingBox();
        AABB d = box.deflate(Math.min(in, box.getXsize() * 0.4), Math.min(in, box.getYsize() * 0.4), Math.min(in, box.getZsize() * 0.4));
        double feet = e.getY(), cw = columnWidth(b), bestD = Double.MAX_VALUE;
        int xa = Mth.floor(d.minX / cw), xb = Mth.floor((d.maxX - 1e-7) / cw), za = Mth.floor(d.minZ / cw), zb = Mth.floor((d.maxZ - 1e-7) / cw);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 64) { xa = xb = Mth.floor(e.getX() / cw); za = zb = Mth.floor(e.getZ() / cw); }
        Runs r = new Runs();
        double y0 = Math.min(d.minY, feet - 1.9), y1 = Math.max(d.maxY, feet + Math.max(1.5, b.solidStep() + 0.1));
        for (int i = xa - 1; i <= xb; i++) for (int k = za; k <= zb; k++) {
            if (i < xa && k > za) break;
            if (i < xa) column(b, e.getX(), e.getZ(), y0, y1, true, r);
            else column(b, (i + 0.5) * cw, (k + 0.5) * cw, y0, y1, true, r);
            for (int j = 0; j < r.n; j++) {
                double o = Math.min(r.top[j], d.maxY) - Math.max(r.bot[j], d.minY);
                if (r.kind[j] == SolidShape.SOFT) { if (o > 0 && i >= xa) out.soft = true; continue; }
                boolean lift = r.top[j] - feet <= b.solidStep() && r.bot[j] < feet + 0.5 && headroom(r, j, box.getYsize());
                if (i >= xa && !lift && o > out.depth) out.depth = o;
                double top = r.top[j];
                if (top > feet + 1.5 - 1e-3 || top < feet - 0.9) continue;
                double dd = Math.abs(top - feet);
                if (dd < bestD) { bestD = dd; out.anchor = r.frame[j]; }
            }
        }
        return out;
    }

    /** something the server carries: the spot under it (frame, as built), where that was, which way the frame faced */
    record Carried(int frame, Vector3f rest, Vec3 was, float yaw) {}

    /** whoever stands on him now (for his moves: shaking his deck, setting people down when he goes) */
    public static List<Entity> standingOn(SolidBody b, boolean players) {
        List<Entity> out = new ArrayList<>();
        Entity s = b.solidSelf();
        if (!b.solidReady()) return out;
        for (Entity e : s.level().getEntities(s, b.solidBox(), x -> !ignoresAll(x) && !b.solidIgnores(x) && (players || !(x instanceof Player))))
            if (anchorUnder(b, e, 0.9, 1.5) != null) out.add(e);
        return out;
    }

    /**
     * The ground's top at a spot if its land is loaded right now, else `or`. Never asks the world for land that isn't
     * loaded: hasChunk says yes for land still being made, and getHeight there stops the server until it is made.
     */
    public static double groundNow(net.minecraft.world.level.Level l, double x, double z, double or) {
        int gx = Mth.floor(x), gz = Mth.floor(z);
        var c = l.getChunkSource().getChunkNow(gx >> 4, gz >> 4);
        return c == null ? or : c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx & 15, gz & 15) + 1;
    }
}
