package net.jj.hollowbell.world;

import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * /hollowbell tp [which] (cheats on, never in the book): takes you to him. You land on safe open ground a little
 * way out from his bell, facing him; to one out of the world, near where his sum says he is (he comes back as you
 * arrive); to one not come down yet, to where he will.
 */
public final class TakeMe {
    private TakeMe() {}

    /** everyone /hollowbell list shows, in the same order: those in the world, then those out of it */
    public static List<Object> listed(MinecraftServer server) {
        List<Object> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels()) out.addAll(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved()));
        out.addAll(Away.get(server).all());
        return out;
    }

    /** which = 0: the nearest (this dimension first); 1, 2...: that one on /hollowbell list */
    public static Component tp(ServerPlayer p, int which) {
        MinecraftServer server = p.server;
        Object pick = null;
        if (which > 0) {
            List<Object> all = listed(server);
            if (which > all.size()) return Component.translatable("command.hollowbell.tp_bad", which);
            pick = all.get(which - 1);
        } else {
            double bd = Double.MAX_VALUE;
            for (Object o : listed(server)) {
                double d;
                if (o instanceof HollowbellEntity h) d = h.level() == p.level() ? h.distanceToSqr(p) : 1e18;
                else {
                    Away.Rec r = (Away.Rec) o;
                    Vec3 s = r.spot(p.serverLevel().getGameTime());
                    d = r.dim.equals(p.level().dimension().location().toString()) ? Mth.square(s.x - p.getX()) + Mth.square(s.z - p.getZ()) : 1e18;
                    d += 1;                                            // one in the world wins a tie
                }
                if (pick == null || d < bd) { bd = d; pick = o; }
            }
        }
        if (pick instanceof HollowbellEntity h) {
            ServerLevel l = (ServerLevel) h.level();
            return land(p, l, h.getX(), h.getZ(), h.bellScale());
        }
        if (pick instanceof Away.Rec r) {
            ServerLevel l = level(server, r.dim);
            Vec3 s = r.spot(l.getGameTime());
            return land(p, l, s.x, s.z, r.scale);
        }
        // one put away with his chunk where nobody was near (not loaded, not out of the world): beside him, he loads round you
        if (which <= 0) {
            java.util.Map.Entry<java.util.UUID, Away.Parked> lay = null;
            double bd = Double.MAX_VALUE;
            for (var e : Away.get(server).parked().entrySet()) {
                ServerLevel l = level(server, e.getValue().dim());
                if (l.getEntity(e.getKey()) != null) continue;
                double d = (l == p.level() ? 0 : 1e12) + Mth.square(e.getValue().x() - p.getX()) + Mth.square(e.getValue().z() - p.getZ());
                if (d < bd) { bd = d; lay = e; }
            }
            if (lay != null) {
                Away.Parked k = lay.getValue();
                Component c = land(p, level(server, k.dim()), k.x(), k.z(), net.jj.hollowbell.HollowbellConfig.V.worldScale);
                if (NoWait.onTheWay(p)) return c;
                return Component.translatable("command.hollowbell.tp_waiting", Mth.floor(k.x()), Mth.floor(k.z()));
            }
        }
        // none standing: where the next one comes down
        WorldOne w = WorldOne.get(server);
        BlockPos at = w.where();
        if (at != null && w.aliveNow()) {
            Component c = land(p, server.overworld(), at.getX() + 0.5, at.getZ() + 0.5, net.jj.hollowbell.HollowbellConfig.V.worldScale);
            if (NoWait.onTheWay(p)) return c;
            return Component.translatable("command.hollowbell.tp_waiting", at.getX(), at.getZ());
        }
        if (at != null) {
            ServerLevel over = server.overworld();
            java.util.function.Function<ServerPlayer, Component> go = q -> {
                BlockPos safe = safeNear(over, at.getX(), at.getZ(), 48);
                q.teleportTo(over, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, q.getYRot(), 0f);
                return Component.translatable("command.hollowbell.tp_rise");
            };
            if (!NoWait.loadedAround(over, at.getX(), at.getZ())) {
                NoWait.go(p, over, at.getX(), at.getZ(), go);
                return Component.translatable("command.hollowbell.tp_soon");
            }
            return go.apply(p);
        }
        return Component.translatable("command.hollowbell.tp_none");
    }

    /** onto safe ground just outside his bell on your side of him (the far side of a new arrival), facing him */
    private static Component land(ServerPlayer p, ServerLevel l, double hx, double hz, float scale) {
        double dx = p.level() == l ? p.getX() - hx : 1, dz = p.level() == l ? p.getZ() - hz : 0;
        double len = Math.hypot(dx, dz);
        if (len < 1e-3) { dx = 1; dz = 0; len = 1; }
        double out = 88 * scale * 1.05 + 20 * scale + 3;
        int tx = Mth.floor(hx + dx / len * out), tz = Mth.floor(hz + dz / len * out);
        if (!NoWait.loadedAround(l, tx, tz)) {
            // (the land there isn't loaded: you go the moment it is, without the server waiting while it's made)
            NoWait.go(p, l, tx, tz, q -> landAt(q, l, hx, hz, tx, tz));
            return Component.translatable("command.hollowbell.tp_soon");
        }
        return landAt(p, l, hx, hz, tx, tz);
    }

    private static Component landAt(ServerPlayer p, ServerLevel l, double hx, double hz, int tx, int tz) {
        BlockPos safe = safeNear(l, tx, tz, 32);
        double fx = hx - (safe.getX() + 0.5), fz = hz - (safe.getZ() + 0.5);
        float yaw = (float) Math.toDegrees(Math.atan2(-fx, fz));
        p.teleportTo(l, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, yaw, 0f);
        int n = net.jj.hollowbell.item.FinderItem.tens(Math.hypot(fx, fz));
        return Component.translatable("command.hollowbell.tp", n);
    }

    /** a spot to stand on near x, z: solid ground under your feet, air for your body, no water or lava (searched outward) */
    public static BlockPos safeNear(ServerLevel l, int x, int z, int reach) {
        for (int r = 0; r <= reach; r += 2) {
            int steps = Math.max(1, r * 3);
            for (int k = 0; k < steps; k++) {
                double a = k * Math.PI * 2 / steps;
                int px = x + (int) Math.round(Math.cos(a) * r), pz = z + (int) Math.round(Math.sin(a) * r);
                // (land not loaded right now is passed over: loading it here made the server wait while it was made)
                if (!NoWait.loadedAround(l, px, pz)) continue;
                int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, px, pz);
                BlockPos feet = new BlockPos(px, y, pz);
                BlockState under = l.getBlockState(feet.below());
                if (y <= l.getMinBuildHeight() + 1 || !under.getFluidState().isEmpty() || !under.isFaceSturdy(l, feet.below(), Direction.UP)) continue;
                if (!l.getBlockState(feet).isAir() || !l.getBlockState(feet.above()).isAir()) continue;
                if (under.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK) || under.is(net.minecraft.world.level.block.Blocks.CACTUS)) continue;
                return feet;
            }
        }
        int y = NoWait.heightOrGuess(l, Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, y + 1, z);
    }

    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation key = ResourceLocation.tryParse(dim);
        ServerLevel l = key == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        return l != null ? l : server.overworld();
    }
}
