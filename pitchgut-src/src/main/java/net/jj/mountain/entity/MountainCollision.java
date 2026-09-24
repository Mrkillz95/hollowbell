package net.jj.mountain.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** The solid boxes along his back, for things walking, standing and landing on him. */
public final class MountainCollision {
    private MountainCollision() {}
    private static final boolean DEBUG = System.getProperty("fabric-api.gametest") != null || Boolean.getBoolean("mountain.debug");

    /** the Mountains in the world right now, one list for the server and one for the client (they run on different threads) */
    static final List<MountainEntity> SERVER = new CopyOnWriteArrayList<>(), CLIENT = new CopyOnWriteArrayList<>();

    static void track(MountainEntity m) {
        List<MountainEntity> l = m.level().isClientSide ? CLIENT : SERVER;
        if (!l.contains(m)) l.add(m);
    }

    static void untrack(MountainEntity m) { SERVER.remove(m); CLIENT.remove(m); }

    /** leaving a world: nothing in it is still standing anywhere */
    public static void forgetAll(boolean client) { (client ? CLIENT : SERVER).clear(); }
    public static void forget(MountainEntity m) { untrack(m); }

    /** a bump on his back up to this high above your feet you simply walk up onto, no jumping */
    public static final double SOFT = 1.3;

    public static List<MountainEntity> clientMountains() { return CLIENT; }

    private static boolean ignores(Entity e) {
        return e instanceof MountainEntity || e instanceof MountainPart || e instanceof GripSeat || e.noPhysics;
    }

    public static List<VoxelShape> addHisBack(Entity e, AABB box, List<VoxelShape> list) {
        if (ignores(e)) return list;
        List<MountainEntity> ms = e.level().isClientSide ? CLIENT : SERVER;
        if (ms.isEmpty()) return list;
        double feet = e.getBoundingBox().minY;
        // the server only replays what a player's own game already worked out: give it floors, never walls, so the
        // two never disagree about a bump and snap you back
        boolean floorsOnly = !e.level().isClientSide && e instanceof net.minecraft.world.entity.player.Player;
        if (DEBUG && e instanceof net.minecraft.world.entity.player.Player && e.tickCount % 20 == 0)
            net.jj.mountain.MountainMod.LOG.info("collide {} client={} mountains={} box={} backTop {}", e.getName().getString(), e.level().isClientSide, ms.size(), box,
                    ms.get(0).backTop(e.getX(), e.getZ()));
        List<VoxelShape> out = null;
        for (MountainEntity m : ms) {
            if (m.level() != e.level() || m.isRemoved() || m.isDeadOrDying()) { if (m.isRemoved()) ms.remove(m); continue; }
            AABB all = m.backBounds();
            if (all == null || !all.intersects(box)) continue;
            for (AABB b : m.backShellIn(box, feet, floorsOnly)) {
                if (out == null) out = new ArrayList<>(list);
                out.add(Shapes.create(b));
            }
        }
        return out == null ? list : out;
    }

    /**
     * After the game has worked out a move: if your feet ended up below the top of his back (he breathed in under
     * you, you walked onto a bump, he turned), lift you onto it, a little each tick, the way stairs do.
     */
    public static Vec3 pushUp(Entity e, Vec3 moved) {
        if (ignores(e) || moved.y > 0.0) return moved;
        if (!e.level().isClientSide && e instanceof net.minecraft.world.entity.player.Player) return moved;   // their own game does it
        List<MountainEntity> ms = e.level().isClientSide ? CLIENT : SERVER;
        if (ms.isEmpty()) return moved;
        double feet0 = e.getBoundingBox().minY;
        AABB nb = e.getBoundingBox().move(moved);
        double best = Double.NaN;
        for (MountainEntity m : ms) {
            if (m.level() != e.level() || m.isRemoved() || m.isDeadOrDying()) continue;
            AABB all = m.backBounds();
            if (all == null || !all.intersects(nb.inflate(0, 1, 0))) continue;
            double top = m.surfaceUnder(nb, feet0);
            if (!Double.isNaN(top) && (Double.isNaN(best) || top > best)) best = top;
        }
        if (Double.isNaN(best) || best <= nb.minY + 1e-4) return moved;
        return new Vec3(moved.x, moved.y + Math.min(best - nb.minY, 0.6), moved.z);
    }

    /** true when e is standing over (or on) one of his backs: the server shouldn't call that flying */
    public static boolean overHisBack(Entity e) {
        List<MountainEntity> ms = e.level().isClientSide ? CLIENT : SERVER;
        for (MountainEntity m : ms) {
            if (m.level() != e.level() || m.isRemoved()) continue;      // one in another world is not under your feet
            AABB all = m.backBounds();
            if (all == null || !all.inflate(0, 3, 0).intersects(e.getBoundingBox())) continue;
            double top = m.backTop(e.getX(), e.getZ());
            if (!Double.isNaN(top) && e.getY() < top + 3 && e.getY() > top - 4) return true;
        }
        return false;
    }
}
