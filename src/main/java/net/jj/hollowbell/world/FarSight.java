package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.mixin.ChunkMapAccess;
import net.jj.hollowbell.mixin.TrackedEntityAccess;
import net.jj.hollowbell.net.FarSightPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeing him from far off. The game only sends a player the creatures within a few hundred blocks, and with
 * nobody near him he steps out of the world altogether, so walking toward him you used to see nothing until you
 * were nearly under him. Now, once a second, each player is told about every Hollowbell within farSightBlocks that
 * they aren't already being sent (loaded but too far, or out of the world as a sum), and their game draws him
 * simply out there until the real one comes into range.
 */
public final class FarSight {
    private FarSight() {}

    private static int clock;

    public static void serverTick(MinecraftServer server) {
        if (--clock > 0) return;
        clock = 20;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, FarSightPayload.TYPE)) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new FarSightPayload(listFor(p)));
        }
    }

    /** is this player already being sent this one as a real creature? */
    public static boolean tracked(ServerPlayer p, HollowbellEntity h) {
        if (!(h.level() instanceof ServerLevel sl)) return false;
        Object te = ((ChunkMapAccess) sl.getChunkSource().chunkMap).hollowbell$entityMap().get(h.getId());
        return te != null && ((TrackedEntityAccess) te).hollowbell$seenBy().contains(p.connection);
    }

    /** every one this player should see on the horizon but isn't sent: in the world, or out of it */
    public static List<FarSightPayload.Far> listFor(ServerPlayer p) {
        List<FarSightPayload.Far> out = new ArrayList<>();
        int range = Mth.clamp(HollowbellConfig.V.farSightBlocks, 0, 4096);
        if (range <= 0 || p.isRemoved()) return out;
        ServerLevel l = p.serverLevel();
        double r2 = (double) range * range;
        for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying())) {
            if (Mth.square(h.getX() - p.getX()) + Mth.square(h.getZ() - p.getZ()) > r2) continue;
            if (tracked(p, h)) continue;
            out.add(new FarSightPayload.Far(h.getUUID(), h.getX(), h.getY(), h.getZ(), h.getYRot(), h.bellScale(), h.variant(), h.asleep()));
            if (out.size() >= 16) return out;
        }
        String dim = l.dimension().location().toString();
        long now = l.getGameTime();
        for (Away.Rec r : Away.get(l.getServer()).all()) {
            if (!dim.equals(r.dim)) continue;
            Vec3 s = r.spot(now);
            if (Mth.square(s.x - p.getX()) + Mth.square(s.z - p.getZ()) > r2) continue;
            float yaw = 0f;
            if (r.body.contains("Rotation", Tag.TAG_LIST)) {
                ListTag rot = r.body.getList("Rotation", Tag.TAG_FLOAT);
                if (!rot.isEmpty()) yaw = rot.getFloat(0);
            }
            out.add(new FarSightPayload.Far(r.id, s.x, groundUnder(l, s.x, s.z) + r.lift, s.z, yaw, r.scale, r.variant, r.body.getBoolean("Asleep")));
            if (out.size() >= 16) return out;
        }
        return out;
    }

    /** the ground under a spot: the loaded world if it's there, otherwise what the generator would make (nothing loads) */
    public static int groundUnder(ServerLevel l, double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (net.jj.hollowbell.world.NoWait.loaded(l, new BlockPos(bx, 0, bz))) return l.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        var src = l.getChunkSource();
        return src.getGenerator().getBaseHeight(bx, bz, Heightmap.Types.MOTION_BLOCKING, l, src.randomState());
    }
}
