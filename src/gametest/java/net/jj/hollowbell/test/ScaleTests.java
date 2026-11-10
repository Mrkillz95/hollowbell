package net.jj.hollowbell.test;

import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsMinigame;
import net.jj.hollowbell.GiantsSystems;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.fx.BigFx;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Everything of his goes with his size, by the rules all eight giants share:
 * <ul>
 *   <li>health: his full-size health times his size, never under 40 (a STATS line per size, for the giants' table);
 *   <li>how hard he hits: the shared curve 0.2 + 0.8 s^0.6 (between 0.25 and 1.5), times his full-size blow;
 *   <li>his effects, measured while he does some big moves at 0.01, 0.25, 1 and 2: the biggest shockwave and shake
 *       he sends, the widest his particles spread, and how loud he is all grow with him (about in step with his
 *       size; his sounds to the 0.8).
 * </ul>
 * The same test is in every giant's mod; only the part marked "the giant" at the end differs.
 */
public class ScaleTests implements FabricGameTest {
    static final Logger LOG = LoggerFactory.getLogger("scale-tests");
    static final float[] SIZES = {0.01f, 0.25f, 1f, 2f};

    /** what was measured at each size: [shockwave, shake, spread, sound] */
    static final Map<Float, double[]> SEEN = new ConcurrentHashMap<>();
    /** each test's giant (by id) and what it has made so far */
    static final Map<Integer, double[]> BY_ID = new ConcurrentHashMap<>();

    static void saw(int id, String what, double v) {
        double[] m = BY_ID.get(id);
        if (m == null) return;
        if (what.equals("spread")) m[2] = Math.max(m[2], v);
        else m[3] = Math.max(m[3], v);
    }

    /** the rules every giant follows: health and blow at a size, from his full-size ones */
    static float sharedHealth(float full, float s) {
        return Math.max(40f, full * s);
    }

    static float sharedHit(float s) {
        return Math.max(0.25f, Math.min(1.5f, 0.2f + 0.8f * (float) Math.pow(s, 0.6)));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100)
    public void statsFollowTheSharedCurve(GameTestHelper h) {
        ServerPlayer p = TinySizeTests.player(h, h.absoluteVec(new Vec3(1, 2, 1)), "scale-stats");
        HollowbellEntity e = spawnAt(h, h.absoluteVec(new Vec3(4, 2, 4)), 1f);
        try {
            float full = healthMax(e), hit1 = blow(e, p);
            StringBuilder bad = new StringBuilder();
            for (float s : new float[] {0.01f, 0.1f, 0.25f, 0.5f, 1f, 2f}) {
                setSize(e, s);
                float hp = healthMax(e), hit = blow(e, p);
                LOG.info(String.format("STATS %s size %.2f health %.0f blow10 %.2f armour %.1f", NAME, s, hp, hit,
                        e.getArmorValue() * 1.0));
                if (Math.abs(hp - sharedHealth(full, s)) > 0.05f * sharedHealth(full, s) + 1)
                    bad.append(String.format("health at %.2f is %.0f, the shared curve says %.0f; ", s, hp,
                            sharedHealth(full, s)));
                float want = hit1 * sharedHit(s) / sharedHit(1f);
                if (Math.abs(hit - want) > 0.05f * want + 0.01f)
                    bad.append(String.format("a blow at %.2f is %.2f, the shared curve says %.2f; ", s, hit, want));
            }
            h.assertTrue(bad.length() == 0, bad.toString());
        } finally {
            p.getServer().getPlayerList().remove(p);
            e.discard();
        }
        h.succeed();
    }

    @GameTestGenerator
    public Collection<TestFunction> effects() {
        List<TestFunction> out = new ArrayList<>();
        int slot = SLOT;
        for (float s : SIZES) {
            String name = "scale_fx_" + String.valueOf(s).replace('.', '_');
            int at = slot;
            slot += 2;
            out.add(new TestFunction("scale_fx", name, EMPTY_STRUCTURE, 3000, 0, true, h -> measure(h, s, at)));
        }
        return out;
    }

    static void measure(GameTestHelper h, float s, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 600, z = o.getZ();
        for (int cx = (x >> 4) - 5; cx <= (x >> 4) + 5; cx++)
            for (int cz = (z >> 4) - 5; cz <= (z >> 4) + 5; cz++) {
                h.getLevel().setChunkForced(cx, cz, true);
                h.getLevel().getChunk(cx, cz);
            }
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
        HollowbellEntity e = spawnAt(h, new Vec3(x + 0.5, y, z + 0.5), s);
        double[] most = new double[4];
        long[] since = {0};
        ServerPlayer[] pl = {null};
        h.runAfterDelay(60, () -> {
            Vec3 ahead = e.position().add(Vec3.directionFromRotation(0, e.getYRot()).scale(12 * s + 3));
            pl[0] = TinySizeTests.player(h, new Vec3(ahead.x, e.getY(), ahead.z), "scale-" + slot);
            pl[0].setInvulnerable(true);
            since[0] = h.getLevel().getGameTime();
            BY_ID.put(e.getId(), most);
        });
        // (gathered as they come: the record of what was sent keeps only the last 512)
        h.onEachTick(() -> {
            if (since[0] == 0 || h.getLevel().getGameTime() % 10 != 0) return;
            for (BigFx.Sent sent : BigFx.sentNear(e.position(), 400 * s + 40, since[0])) {
                if (sent.kind() == BigFx.SHOCKWAVE) most[0] = Math.max(most[0], sent.size());
                if (sent.kind() == BigFx.SHAKE) most[1] = Math.max(most[1], sent.size());
            }
        });
        int t = 70;
        for (int mv : FX_MOVES) {
            h.runAfterDelay(t, () -> force(e, mv, pl[0]));
            t += moveLength(mv) + 20;
        }
        h.runAfterDelay(t, () -> {
            for (BigFx.Sent sent : BigFx.sentNear(e.position(), 400 * s + 40, since[0])) {
                if (sent.kind() == BigFx.SHOCKWAVE) most[0] = Math.max(most[0], sent.size());
                if (sent.kind() == BigFx.SHAKE) most[1] = Math.max(most[1], sent.size());
            }
            BY_ID.remove(e.getId());
            SEEN.put(s, most);
            LOG.info(String.format("FX %s size %.2f shockwave %.2f shake %.2f spread %.2f sound %.2f", NAME, s, most[0],
                    most[1], most[2], most[3]));
            pl[0].getServer().getPlayerList().remove(pl[0]);
            e.discard();
            for (int cx = (x >> 4) - 5; cx <= (x >> 4) + 5; cx++)
                for (int cz = (z >> 4) - 5; cz <= (z >> 4) + 5; cz++) h.getLevel().setChunkForced(cx, cz, false);
            // the last size to finish compares them all
            if (SEEN.size() == SIZES.length) compare(h);
            else h.succeed();
        });
    }

    static void compare(GameTestHelper h) {
        String[] names = {"shockwave", "shake", "particle spread", "sound"};
        StringBuilder bad = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            double tiny = SEEN.get(0.01f)[i], small = SEEN.get(0.25f)[i], one = SEEN.get(1f)[i], two = SEEN.get(2f)[i];
            if (one <= 0 && small <= 0) continue; // (none of these moves makes one)
            // in step with size (x4 from 0.25 to 1, x2 from 1 to 2), his sound to the 0.8 (x3.0, x1.7)
            boolean sound = i == 3;
            double lo4 = sound ? 2.0 : 2.2, hi4 = sound ? 4.5 : 6.5, lo2 = sound ? 1.3 : 1.4, hi2 = sound ? 2.3 : 2.8;
            if (one <= 0 || small <= 0) {
                bad.append(names[i]).append(": only at some sizes; ");
                continue;
            }
            double r4 = one / small, r2 = two / one;
            if (r4 < lo4 || r4 > hi4) bad.append(String.format("%s x%.2f from 0.25 to 1; ", names[i], r4));
            if (r2 < lo2 || r2 > hi2) bad.append(String.format("%s x%.2f from 1 to 2; ", names[i], r2));
            if (tiny > small * 0.35)
                bad.append(String.format("%s at 0.01 is %.2f against %.2f at 0.25; ", names[i], tiny, small));
        }
        h.assertTrue(bad.length() == 0, bad.toString());
        h.succeed();
    }

    // ---- the giant: everything above is the same in every giant's copy ----

    static final String NAME = "hollowbell";
    static final int SLOT = 1740;
    /** a few of his biggest moves */
    static final int[] FX_MOVES = {Moves.byName("arm_slam"), Moves.byName("deep_toll"), Moves.byName("pulse_wave"),
        Moves.byName("sky_dive")};

    static {
        HollowbellEntity.FxSpy was = HollowbellEntity.fxSpy;
        HollowbellEntity.fxSpy = (id, what, v) -> {
            if (was != null) was.saw(id, what, v);
            saw(id, what, v);
        };
    }

    static HollowbellEntity spawnAt(GameTestHelper h, Vec3 at, float s) {
        return TinySizeTests.spawn(h, at, s);
    }

    static void setSize(HollowbellEntity e, float s) {
        e.setBellScale(s);
    }

    static float healthMax(HollowbellEntity e) {
        return e.healthMax();
    }

    /** a full-size blow of 10 on a player, at his size now */
    static float blow(HollowbellEntity e, ServerPlayer p) {
        return e.dmg(10f, p);
    }

    static int moveLength(int mv) {
        return TinySizeTests.moveLength(mv);
    }

    static void force(HollowbellEntity e, int mv, ServerPlayer p) {
        TinySizeTests.force(e, mv, p);
    }
}
