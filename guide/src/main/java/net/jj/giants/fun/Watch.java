package net.jj.giants.fun;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Spectate") An admin watching a giant with a hidden camera: they become a spectator
 * (players don't see them), are put behind and above him, and from then on are carried along by however far he moves
 * each tick, so they fly and look where they like and he stays in view ("free"); or they see from him ("eyes", the
 * game's own spectating; sneaking lets go into the free camera). Their game mode and place are kept with the world
 * and put back when they stop, or on their next join if they left while watching.
 */
public final class Watch {
    private Watch() {}

    /** who is being carried along now: the giant, where he was last tick, the mode */
    static final class Follow {
        UUID giant;
        String kind = "", mode = "free", label = "";
        @Nullable Vec3 last;
        boolean lost;
    }

    static final Map<UUID, Follow> following = new HashMap<>();

    public static boolean watching(ServerPlayer p) {
        return following.containsKey(p.getUUID())
                || FunWorld.get(p.getServer()).s.watching.containsKey(p.getStringUUID());
    }

    public static @Nullable Follow of(ServerPlayer p) {
        return following.get(p.getUUID());
    }

    /** start (or move the camera to another giant) */
    public static AdminServer.Result start(MinecraftServer server, ServerPlayer p, LivingEntity giant, String mode) {
        String kind = FunGiants.kindOf(giant);
        if (kind == null) return AdminServer.Result.fail("That isn't a giant.");
        FunWorld w = FunWorld.get(server);
        // (the first place is kept: watching another giant doesn't change where they go back to)
        if (!w.s.watching.containsKey(p.getStringUUID())) {
            PlayAs.stopQuietly(server, p);
            FunWorld.Watch rec = new FunWorld.Watch();
            rec.gameMode = p.gameMode.getGameModeForPlayer().getName();
            rec.dim = p.level().dimension().location().toString();
            rec.x = p.getX();
            rec.y = p.getY();
            rec.z = p.getZ();
            rec.yaw = p.getYRot();
            rec.pitch = p.getXRot();
            w.s.watching.put(p.getStringUUID(), rec);
        }
        FunWorld.Watch rec = w.s.watching.get(p.getStringUUID());
        rec.mode = mode;
        rec.kind = kind;
        rec.giant = giant.getStringUUID();
        w.setDirty();
        p.setCamera(p);
        if (p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) p.setGameMode(GameType.SPECTATOR);
        double size = FunGiants.sizeOf(giant), h = FunGiants.height(kind, size);
        // behind him and above, looking at his middle
        Vec3 face = Vec3.directionFromRotation(0, giant.getYRot()).multiply(1, 0, 1);
        if (face.lengthSqr() < 1e-4) face = new Vec3(0, 0, 1);
        face = face.normalize();
        // (a long one, the worm, is seen whole: from as far back as he is long)
        double back = Math.max(10, Math.max(h * 1.1, FunGiants.reach(kind, size)) + 8), up = Math.max(6, h * 0.6 + 4);
        Vec3 mid = giant.position().add(0, h * 0.45, 0);
        Vec3 eye = clearView((ServerLevel) giant.level(), giant.position(), h, FunGiants.reach(kind, size), face, back,
                up - h * 0.45);
        Vec3 d = mid.subtract(eye);
        float yaw = (float) (Mth.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180 / Math.PI);
        p.teleportTo((ServerLevel) giant.level(), eye.x, eye.y, eye.z, yaw, pitch);
        Follow f = new Follow();
        f.giant = giant.getUUID();
        f.kind = kind;
        f.mode = mode;
        f.label = FunGiants.label(server, giant);
        f.last = giant.position();
        following.put(p.getUUID(), f);
        if (mode.equals("eyes")) p.setCamera(giant);
        Fun.changed();
        String name = f.label;
        return new AdminServer.Result(true, List.of(mode.equals("eyes")
                ? "Watching through the " + name + "'s eyes. Sneak to let go into a free camera that keeps up with"
                        + " him. /giants admin spectate stop to come back."
                : "Watching the " + name + ". Fly where you like: the camera keeps up with him. /giants admin"
                        + " spectate stop to come back."), List.of(p.getGameProfile().getName() + " is watching a "
                + AdminServer.nameOf(AdminServer.giant(kind)) + "."));
    }

    /** an admin, or (JJ answers 2, #15) a moderator while Moderators may watch is on */
    public static boolean mayWatch(MinecraftServer server, ServerPlayer p) {
        if (Fun.admin(p)) return true;
        return net.jj.giants.admin.AdminWorld.get(server).isMod(p.getUUID())
                && net.jj.giants.admin.AdminBook.modWatch(server);
    }

    /** back in their own game mode, where they were */
    public static AdminServer.Result stop(MinecraftServer server, ServerPlayer p) {
        following.remove(p.getUUID());
        FunWorld w = FunWorld.get(server);
        FunWorld.Watch rec = w.s.watching.remove(p.getStringUUID());
        w.setDirty();
        if (rec == null) return AdminServer.Result.fail("You aren't watching a giant.");
        putBack(server, p, rec);
        Fun.changed();
        return AdminServer.Result.said(List.of("Back where you were."));
    }

    static void putBack(MinecraftServer server, ServerPlayer p, FunWorld.Watch rec) {
        p.setCamera(p);
        GameType gm = GameType.byName(rec.gameMode, GameType.SURVIVAL);
        if (p.gameMode.getGameModeForPlayer() != gm) p.setGameMode(gm);
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(rec.dim)));
        if (l == null) l = server.overworld();
        p.teleportTo(l, rec.x, rec.y, rec.z, rec.yaw, rec.pitch);
        p.fallDistance = 0;
    }

    /** each tick: everyone watching is carried along by however far their giant moved */
    static void tick(MinecraftServer server) {
        if (following.isEmpty()) return;
        for (var e : List.copyOf(following.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            Follow f = e.getValue();
            if (p == null) {
                following.remove(e.getKey());
                continue;
            }
            // (JJ answers 2, #15) a moderator watching when the admins turn it off (or no longer a moderator): back
            if (server.getTickCount() % 20 == 0 && !Fun.admin(p) && !mayWatch(server, p)) {
                stop(server, p);
                p.displayClientMessage(Component.literal("Watching giants is for admins now: back where you were."),
                        false);
                continue;
            }
            LivingEntity g = FunGiants.find(server, f.giant);
            if (g == null || g.isDeadOrDying()) {
                if (!f.lost) {
                    f.lost = true;
                    p.setCamera(p);
                    p.displayClientMessage(Component.literal("He's gone from view."), true);
                }
                continue;
            }
            if (f.lost) {
                // (back in the world, a new one of him by the same id: carried along from here)
                f.lost = false;
                f.last = g.position();
            }
            Vec3 now = g.position();
            if (p.getCamera() == g) {
                f.last = now;
                continue;
            }
            if (f.mode.equals("eyes")) f.mode = "free";
            if (p.level() != g.level()) {
                // (he went to another world: the camera goes along, the same way behind him)
                Vec3 off = f.last == null ? new Vec3(0, 20, -20) : p.position().subtract(f.last);
                p.teleportTo((ServerLevel) g.level(), now.x + off.x, now.y + off.y, now.z + off.z, p.getYRot(),
                        p.getXRot());
                f.last = now;
                continue;
            }
            // (carried in steps of most of a block, not every tick: each step holds their own flying for a moment)
            if (f.last == null) f.last = now;
            Vec3 d = now.subtract(f.last);
            if (d.lengthSqr() > 0.75 * 0.75) {
                // (where to, in full; told to their game as a step from where it has them, so their flying goes on)
                p.connection.teleport(p.getX() + d.x, p.getY() + d.y, p.getZ() + d.z, p.getYRot(), p.getXRot(),
                        EnumSet.of(RelativeMovement.X, RelativeMovement.Y, RelativeMovement.Z, RelativeMovement.X_ROT,
                                RelativeMovement.Y_ROT));
                f.last = now;
            }
        }
    }

    /** a player came back: one who left while watching is put back */
    static void joined(MinecraftServer server, ServerPlayer p) {
        FunWorld w = FunWorld.get(server);
        FunWorld.Watch rec = w.s.watching.remove(p.getStringUUID());
        if (rec == null) return;
        w.setDirty();
        putBack(server, p, rec);
        p.displayClientMessage(Component.literal("You were watching a giant when you left: back where you were."),
                false);
    }

    static void left(ServerPlayer p) {
        following.remove(p.getUUID());
    }

    static void forget() {
        following.clear();
    }

    /**
     * (Part 9) where the camera looks from: behind him and above as always; when rock (or lava) is in the way (a cave,
     * the Nether), the place round him (16 ways, a little, as usual and well above) from which most of him is seen: his middle, head,
     * feet and both sides, out of his body and just short of the rock, so the view is never from inside the rock or him
     */
    static Vec3 clearView(ServerLevel l, Vec3 feet, double h, double reach, Vec3 face, double back, double up) {
        Vec3 mid = feet.add(0, h * 0.45, 0);
        Vec3 first = mid.add(face.scale(-back)).add(0, up, 0);
        if (clear(l, mid, first) >= first.distanceTo(mid) - 0.01 && seen(l, first, feet, h, reach, face) >= 4) return first;
        Vec3 best = null;
        double bestScore = -1;
        double[] lifts = {up, up * 0.3, up * 1.8};
        for (double lift : lifts)
            for (int i = 0; i < 16; i++) {
                Vec3 dir = face.yRot((float) (i * Math.PI / 8)).scale(-1);
                Vec3 want = mid.add(dir.scale(back)).add(0, lift, 0);
                double full = want.distanceTo(mid), c = clear(l, mid, want);
                double dist = c >= full - 0.01 ? full : Math.max(0, c - 1.5);
                Vec3 at = mid.add(want.subtract(mid).normalize().scale(dist));
                // (inside his own body is no view: at least out past his reach)
                double out = Math.min(1, dist / Math.max(1, reach + 2));
                double score = seen(l, at, feet, h, reach, dir.scale(-1)) * 2 + out * 3 + (i == 0 ? 0.2 : 0);
                if (score > bestScore) {
                    bestScore = score;
                    best = at;
                }
            }
        return best == null ? first : best;
    }

    static double clear(ServerLevel l, Vec3 from, Vec3 to) {
        net.minecraft.world.phys.BlockHitResult hit = l.clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY,
                net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? to.distanceTo(from)
                : hit.getLocation().distanceTo(from);
    }

    /** how many of his middle, head, feet and two sides can be seen from here (0-5) */
    static int seen(ServerLevel l, Vec3 eye, Vec3 feet, double h, double reach, Vec3 look) {
        Vec3 side = new Vec3(-look.z, 0, look.x);
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(Math.min(reach, h) * 0.6);
        Vec3 mid = feet.add(0, h * 0.45, 0);
        Vec3[] parts = {mid, feet.add(0, h * 0.9, 0), feet.add(0, Math.min(1.5, h * 0.2), 0), mid.add(side),
                mid.subtract(side)};
        int n = 0;
        for (Vec3 t : parts)
            if (clear(l, eye, t) >= t.distanceTo(eye) - 0.6) n++;
        return n;
    }
}
