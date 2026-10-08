package net.jj.hollowbell.net;

import net.jj.hollowbell.entity.Giants;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Go after what I look at": what a look lands on. The same on both sides: the book runs it on your screen (out to
 * your render distance, at least 512 blocks, from where your view really is, riding or being him included) and sends
 * the server what it found; the server runs it too when it is given nothing.
 *
 * It hits creatures, players and the part boxes of the other giants (which stand for their giant); a voxel giant (the
 * Hollowbell, the Willow, Wreckback, the Swarmforge) is hit on the blocks it is made of. Far things are a little
 * easier to hit than near ones. If the look lands on a block first, the creature nearest that spot (a few blocks
 * round) is taken, so a mob in a crowd or behind grass still counts.
 */
public final class LookTrace {
    private LookTrace() {}

    /** how far a look reaches, at the least */
    public static final double RANGE = 512;
    /** how near the spot a look lands on a creature has to be, to be taken instead */
    public static final double NEAR_SPOT = 4.5;

    /** the creature (or giant's part) a look along this line lands on, or null */
    public static @Nullable Entity trace(Level l, Vec3 from, Vec3 dir, double range, @Nullable Player who, @Nullable Entity also) {
        dir = dir.normalize();
        Vec3 to = from.add(dir.scale(range));
        CollisionContext cc = who != null ? CollisionContext.of(who) : CollisionContext.empty();
        // (collider: grass, flowers and the like have none, so the look goes through them)
        BlockHitResult bh = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cc));
        boolean block = bh.getType() != HitResult.Type.MISS;
        double blockT = block ? bh.getLocation().distanceTo(from) : range;
        Entity best = null;
        double bestT = blockT + 0.5;
        // (wide: a giant drawn from blocks has its own box only at its middle)
        AABB around = new AABB(from, from.add(dir.scale(blockT + 2))).inflate(240);
        for (Entity e : l.getEntities((Entity) null, around, e -> candidate(e) && !skip(e, who, also))) {
            double t = hitT(e, from, dir, bestT);
            if (t >= 0 && t < bestT) { bestT = t; best = e; }
        }
        if (best != null || !block) return best;
        // the look stopped on a block: the creature nearest that spot
        Vec3 at = bh.getLocation();
        double bd = NEAR_SPOT * NEAR_SPOT;
        for (Entity e : l.getEntities((Entity) null, new AABB(at, at).inflate(NEAR_SPOT + 2), e -> candidate(e) && !skip(e, who, also))) {
            if (voxelGiant(e)) continue;
            double d = e.getBoundingBox().distanceToSqr(at);
            // (a creature is taken before a part box at the same spot)
            if (!(e instanceof LivingEntity)) d += 1;
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    /** a creature, a player, or a part box of a giant; never a spectator, an item, an arrow or a boat */
    public static boolean candidate(Entity e) {
        if (!e.isAlive() || e.isSpectator() || e.isRemoved()) return false;
        if (e instanceof LivingEntity) return !(e instanceof net.minecraft.world.entity.decoration.ArmorStand);
        if (e instanceof net.minecraft.world.entity.boss.EnderDragonPart) return true;
        // the part boxes of JJ's giants (and anything else with an owner() that is a creature)
        return e.getClass().getName().startsWith("net.jj.") ? hasOwner(e) || Giants.isGiant(e) : hasOwner(e);
    }

    /** the looker, what they ride (and the giant that seat belongs to), and whatever the view is from */
    private static boolean skip(Entity e, @Nullable Player who, @Nullable Entity also) {
        if (e == also) return true;
        if (who == null) return false;
        if (e == who) return true;
        Entity v = who.getVehicle();
        for (int i = 0; i < 3 && v != null; i++) {
            if (e == v || e.getId() == seatOwner(v) || ownerOf(v) == e) return true;
            v = v.getVehicle();
        }
        return false;
    }

    /** how far along the look it first touches this, or -1 */
    private static double hitT(Entity e, Vec3 from, Vec3 dir, double maxT) {
        if (voxelGiant(e)) {
            AABB bb = body(e);
            if (bb == null) return -1;
            if (!bb.contains(from) && bb.clip(from, from.add(dir.scale(maxT))).isEmpty()) return -1;
            return voxelT(e, from, dir, maxT);
        }
        AABB box = e.getBoundingBox();
        if (box.contains(from)) return -1;          // the look starts inside it (riding it, or inside it)
        double far = e.position().distanceTo(from);
        // far things a bit fatter, so a mob 300 blocks off is not a single pixel to aim at
        Optional<Vec3> hit = box.inflate(0.2 + far * 0.008).clip(from, from.add(dir.scale(maxT)));
        return hit.map(h -> h.distanceTo(from)).orElse(-1.0);
    }

    // ------------------------------------------------------------------ reaching the giants without knowing them

    private static final Map<Class<?>, Optional<Method>> OWNER = new ConcurrentHashMap<>(), SEAT = new ConcurrentHashMap<>(),
            RAY = new ConcurrentHashMap<>(), BODY = new ConcurrentHashMap<>();

    private static Optional<Method> find(Map<Class<?>, Optional<Method>> cache, Class<?> c, String name, Class<?>... args) {
        return cache.computeIfAbsent(c, k -> {
            try { Method m = k.getMethod(name, args); m.setAccessible(true); return Optional.of(m); }
            catch (Exception x) { return Optional.empty(); }
        });
    }

    private static boolean hasOwner(Entity e) { return find(OWNER, e.getClass(), "owner").isPresent(); }

    /** the giant (or creature) this is: a creature is itself, a part box is its giant's, a dragon part its dragon */
    public static @Nullable LivingEntity ownerOf(@Nullable Entity e) {
        if (e == null) return null;
        if (e instanceof LivingEntity le) return le;
        if (e instanceof net.minecraft.world.entity.boss.EnderDragonPart dp) return dp.parentMob;
        Optional<Method> m = find(OWNER, e.getClass(), "owner");
        if (m.isEmpty()) return null;
        try { return m.get().invoke(e) instanceof LivingEntity le ? le : null; } catch (Exception x) { return null; }
    }

    /** the id of the giant a seat belongs to (riding one), or -1 */
    private static int seatOwner(Entity seat) {
        Optional<Method> m = find(SEAT, seat.getClass(), "ownerId");
        if (m.isEmpty()) return -1;
        try { return m.get().invoke(seat) instanceof Integer i ? i : -1; } catch (Exception x) { return -1; }
    }

    /** a giant drawn from blocks, hit on the blocks it is made of (it has raycast(from, dir, max) and bodyBox()) */
    private static boolean voxelGiant(Entity e) {
        return find(RAY, e.getClass(), "raycast", Vec3.class, Vec3.class, double.class).isPresent()
                && find(BODY, e.getClass(), "bodyBox").isPresent();
    }

    private static @Nullable AABB body(Entity e) {
        try { return find(BODY, e.getClass(), "bodyBox").get().invoke(e) instanceof AABB a ? a : null; } catch (Exception x) { return null; }
    }

    private static double voxelT(Entity e, Vec3 from, Vec3 dir, double maxT) {
        try {
            Object hit = find(RAY, e.getClass(), "raycast", Vec3.class, Vec3.class, double.class).get().invoke(e, from, dir, maxT);
            if (hit == null) return -1;
            Method t = hit.getClass().getMethod("t");
            t.setAccessible(true);
            return ((Number) t.invoke(hit)).doubleValue();
        } catch (Throwable x) {
            return -1;
        }
    }
}
