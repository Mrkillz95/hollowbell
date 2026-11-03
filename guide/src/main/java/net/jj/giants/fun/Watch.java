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
        double back = Math.max(10, h * 1.1 + 8), up = Math.max(6, h * 0.6 + 4);
        Vec3 eye = giant.position().subtract(face.scale(back)).add(0, up, 0);
        Vec3 mid = giant.position().add(0, h * 0.45, 0);
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
}
