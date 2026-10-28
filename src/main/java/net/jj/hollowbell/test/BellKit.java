package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.BellSolid;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidShape;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** What the solid kit's game tests share: a spot far from the others, him there, waits, stand-in players. */
final class BellKit {
    private BellKit() {}

    private static final Map<GameTestHelper, List<Object[]>> LATER = new IdentityHashMap<>();
    private static final Map<GameTestHelper, int[]> NOW = new IdentityHashMap<>();

    /** runs r this many ticks from now */
    static void after(GameTestHelper h, int ticks, Runnable r) {
        List<Object[]> q = LATER.get(h);
        if (q == null) {
            List<Object[]> nq = new ArrayList<>();
            int[] now = {0};
            LATER.put(h, nq);
            NOW.put(h, now);
            h.onEachTick(() -> {
                now[0]++;
                for (Object[] o : new ArrayList<>(nq))
                    if ((int) o[0] <= now[0]) {
                        nq.remove(o);
                        ((Runnable) o[1]).run();
                    }
            });
            q = nq;
        }
        q.add(new Object[] {NOW.get(h)[0] + ticks, r});
    }

    /** the chunks round a spot kept loaded and running (r chunks out) */
    static void force(GameTestHelper h, int x, int z, int r, boolean on) {
        ServerLevel l = h.getLevel();
        for (int cx = (x >> 4) - r; cx <= (x >> 4) + r; cx++)
            for (int cz = (z >> 4) - r; cz <= (z >> 4) + r; cz++) {
                l.setChunkForced(cx, cz, on);
                if (on) l.getChunk(cx, cz);
            }
    }

    /** how many chunks out to keep loaded round one of him this size (all of him, and whoever is in or on him) */
    static int chunksFor(float size) { return (int) Math.ceil((112 * size + 8) / 16.0) + 1; }

    /** a spot of its own for each test slot, far out where nothing else is: on the ground there */
    static BlockPos spot(GameTestHelper h, int slot, int chunks) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 1200, z = o.getZ() - 9000;
        force(h, x, z, chunks, true);
        int y = h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        return new BlockPos(x, y, z);
    }

    /** him, this size, calm and doing nothing of his own (no moves unless a test asks), already arrived */
    static HollowbellEntity bell(GameTestHelper h, BlockPos at, float size) {
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
        e.setBellScale(size);
        e.setVariant(HollowbellEntity.CALM);
        e.setMoveCooldown(100000);
        e.skipArrival();
        h.getLevel().addFreshEntity(e);
        return e;
    }

    static ArmorStand stand(GameTestHelper h, Vec3 at) {
        ArmorStand a = EntityType.ARMOR_STAND.create(h.getLevel());
        a.moveTo(at.x, at.y, at.z, 0f, 0f);
        h.getLevel().addFreshEntity(a);
        return a;
    }

    @SuppressWarnings("deprecation")
    static ServerPlayer player(GameTestHelper h, Vec3 at) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "hb-solid"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        try {
            java.lang.reflect.Field f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException ignored) {
            // (spawn protection left on: only matters to tests that hurt them)
        }
        p.setGameMode(GameType.SURVIVAL);
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        p.setHealth(1000);
        return p;
    }

    static void leave(ServerPlayer p) { p.getServer().getPlayerList().remove(p); }

    /** clears up and passes */
    static void done(GameTestHelper h, @Nullable HollowbellEntity e, BlockPos at, int chunks, Entity... others) {
        for (Entity o : others)
            if (o instanceof ServerPlayer p) leave(p);
            else if (o != null) o.discard();
        if (e != null) e.discard();
        force(h, at.getX(), at.getZ(), chunks, false);
        LATER.remove(h);
        NOW.remove(h);
        h.succeed();
    }

    /** what of him is up a column, for the failure messages */
    static String runs(HollowbellEntity e, double x, double z, double y0, double y1) {
        Solid.Runs r = Solid.column(e, x, z, y0, y1, true, new Solid.Runs());
        StringBuilder b = new StringBuilder();
        for (int j = 0; j < r.n; j++)
            b.append(String.format(" [%s %.2f..%.2f]", e.rig.boneNames[e.solidShape().bone[r.frame[j]]], r.bot[j], r.top[j]));
        return b.toString();
    }

    /** the highest top of him over this spot (world), or NaN */
    static double topOver(HollowbellEntity e, double x, double z) {
        Solid.Runs r = Solid.column(e, x, z, e.getY() - 60 * e.bellScale() - 4, e.getY() + 260 * e.bellScale() + 4, false, new Solid.Runs());
        double top = Double.NaN;
        for (int j = 0; j < r.n; j++) if (Double.isNaN(top) || r.top[j] > top) top = r.top[j];
        return top;
    }

    /** over a spot of his dome (model x z, about his crown), on its top in the world now; null if nothing's there */
    static @Nullable Vec3 onDome(HollowbellEntity e, float mx, float mz) {
        e.ensurePose();
        Vec3 w = e.toWorld(new Vector3f(mx, 0, mz));
        double top = topOver(e, w.x, w.z);
        return Double.isNaN(top) ? null : new Vec3(w.x, top, w.z);
    }

    /**
     * A spot (the world) deep in one of his solid parts of this kind: solid for `up` blocks over it and for a player's
     * width round it, the one nearest the part's middle; or null.
     */
    static @Nullable Vec3 deepIn(HollowbellEntity e, BellRig.Kind kind, double up) {
        SolidShape sh = BellSolid.shape();
        float s = e.bellScale();
        int need = (int) Math.ceil(up / s) + 2, side = Math.max(2, (int) Math.ceil(0.6 / s) + 1);
        for (int f = 0; f < sh.frames(); f++) {
            if (e.rig.kind[sh.bone[f]] != kind) continue;
            final int ff = f;
            float[] bb = sh.bounds[f];
            float mx = (bb[0] + bb[3]) / 2, my = (bb[1] + bb[4]) / 2, mz = (bb[2] + bb[5]) / 2;
            double[] best = {Double.MAX_VALUE};
            int[] at = new int[3];
            sh.cells(f, (x, y, z) -> {
                for (int k = -2; k <= need; k++) if (!sh.has(ff, x, y + k, z)) return;
                for (int k = -2; k <= need; k += 2)
                    if (!sh.has(ff, x + side, y + k, z) || !sh.has(ff, x - side, y + k, z)
                            || !sh.has(ff, x, y + k, z + side) || !sh.has(ff, x, y + k, z - side)) return;
                double d = (x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz);
                if (d < best[0]) { best[0] = d; at[0] = x; at[1] = y; at[2] = z; }
            });
            if (best[0] < Double.MAX_VALUE) return e.boneWorld(sh.bone[f], new Vector3f(at[0] + 0.5f, at[1] + 0.5f, at[2] + 0.5f));
        }
        return null;
    }

    /**
     * Where to put a player well inside him: deep in his rim, an arm or his bell; small, where none of him is that
     * thick, with a part of him through their middle (not just under their feet, where they'd only be lifted onto it)
     */
    static @Nullable Vec3 inHim(HollowbellEntity e) {
        for (BellRig.Kind k : new BellRig.Kind[] {BellRig.Kind.RIM, BellRig.Kind.ARM, BellRig.Kind.BELL}) {
            Vec3 v = deepIn(e, k, 2.2);
            if (v != null) return v;
        }
        return throughTheMiddle(e);
    }

    /** a player's feet where a part of him (rim, arm or bell) goes through their middle, clear of the ground; or null */
    static @Nullable Vec3 throughTheMiddle(HollowbellEntity e) {
        SolidShape sh = BellSolid.shape();
        Vec3[] found = {null};
        for (BellRig.Kind k : new BellRig.Kind[] {BellRig.Kind.RIM, BellRig.Kind.ARM, BellRig.Kind.BELL}) {
            for (int f = 0; f < sh.frames() && found[0] == null; f++) {
                if (e.rig.kind[sh.bone[f]] != k) continue;
                int bone = sh.bone[f];
                int[] n = {0};
                sh.cells(f, (x, y, z) -> {
                    if (found[0] != null || n[0]++ % 7 != 0) return;
                    Vec3 c = e.boneWorld(bone, new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f));
                    Vec3 feet = c.subtract(0, 0.9, 0);
                    AABB box = new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
                    if (Solid.depthIn(e, box, 0.08, null, true) > 0.15 && e.level().noCollision(box)) found[0] = feet;
                });
            }
            if (found[0] != null) return found[0];
        }
        return null;
    }
}
