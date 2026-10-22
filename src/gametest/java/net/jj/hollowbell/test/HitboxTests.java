package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A player in reach of any part of him they can see, looking at it and swinging, hits that part: the server finds
 * the same block along their look and it hurts as that part should (the crown a lot, the copper a little). At every
 * size, floating still and drifting. A swing the client saw a moment ago still lands while the player is in reach of
 * where that part was; looking past him, or swinging from out of reach, does nothing.
 */
public class HitboxTests implements FabricGameTest {
    static final float[] SIZES = {0.3f, 1.0f, 2.0f};

    /** a part of him: its bones, and the sides (from the spot) a player could stand on to hit it */
    record Target(String name, List<Integer> bones, String[] sides) {}

    static List<Target> targets(BellRig rig) {
        List<Target> out = new ArrayList<>();
        List<Integer> bell = new ArrayList<>(), rim = new ArrayList<>(), spot = new ArrayList<>(), pod =
                new ArrayList<>();
        for (int b = 0; b < rig.boneCount(); b++) {
            switch (rig.kind[b]) {
                case BELL -> bell.add(b);
                case RIM -> rim.add(b);
                case SPOT -> spot.add(b);
                case POD -> pod.add(b);
                default -> {}
            }
        }
        out.add(new Target("crown", List.of(rig.crownBone), new String[] {"down", "out_down", "out"}));
        out.add(new Target("dome", bell, new String[] {"out", "out_down", "down"}));
        out.add(new Target("glowing spot", spot, new String[] {"out", "out_down", "down"}));
        out.add(new Target("rim", rim, new String[] {"out", "out_up", "up"}));
        out.add(new Target("pod", pod, new String[] {"out", "out_up", "up"}));
        List<Integer> armRoot = new ArrayList<>(), armMid = new ArrayList<>(), armTip = new ArrayList<>();
        for (BellRig.ArmDef a : rig.arms) {
            armRoot.add(a.bones()[1]);
            armMid.add(a.bones()[a.bones().length / 2]);
            armTip.add(a.bones()[a.bones().length - 1]);
        }
        out.add(new Target("arm by the rim", armRoot, new String[] {"out", "out_up", "tan", "up"}));
        out.add(new Target("middle of an arm", armMid, new String[] {"out", "tan", "out_up", "up"}));
        out.add(new Target("arm tip", armTip, new String[] {"out", "tan", "down", "up"}));
        List<Integer> top = new ArrayList<>(), mid = new ArrayList<>(), tip = new ArrayList<>();
        for (int s = 0; s < rig.strands.length; s += 5) {
            int[] bs = rig.strands[s].bones();
            top.add(bs[0]);
            mid.add(bs[bs.length / 2]);
            tip.add(bs[bs.length - 1]);
        }
        out.add(new Target("top of a strand", top, new String[] {"out", "tan", "out_up"}));
        out.add(new Target("middle of a strand", mid, new String[] {"out", "tan", "out_up"}));
        out.add(new Target("strand tip", tip, new String[] {"out", "tan", "down"}));
        return out;
    }

    @GameTestGenerator
    public Collection<TestFunction> hitboxes() {
        List<TestFunction> out = new ArrayList<>();
        int slot = 900;
        for (float s : SIZES)
            for (boolean moving : new boolean[] {false, true}) {
                String name = "hitbox_" + (moving ? "moving_" : "idle_") + String.valueOf(s).replace('.', '_');
                int at = slot++;
                out.add(new TestFunction(name, name, EMPTY_STRUCTURE, 500, 0, true, h -> hits(h, s, moving, at)));
            }
        return out;
    }

    /** where a player looks at a part from: the spot of it they see, the way they look, the bone there */
    record Aim(Vec3 at, Vec3 look, int bone) {}

    /** the way from a spot of him out to the eye */
    static Vec3 side(String kind, Vec3 spot, HollowbellEntity e) {
        Vec3 o = new Vec3(spot.x - e.getX(), 0, spot.z - e.getZ());
        o = o.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : o.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        return switch (kind) {
            case "down" -> up;
            case "up" -> up.scale(-1);
            case "out_up" -> o.subtract(up).normalize();
            case "out_down" -> o.add(up).normalize();
            case "tan" -> o.cross(up).normalize();
            default -> o;
        };
    }

    /** a spot of the part a player standing back away can see, with room to stand there, or null */
    static @Nullable Aim aim(GameTestHelper h, HollowbellEntity e, Target t, double back) {
        BellModel model = BellModel.get();
        float[][] spots = {{0.5f, 0.5f, 0.5f}, {0.3f, 0.5f, 0.5f}, {0.7f, 0.5f, 0.5f}, {0.5f, 0.3f, 0.5f},
            {0.5f, 0.7f, 0.5f}, {0.5f, 0.5f, 0.3f}, {0.5f, 0.5f, 0.7f}};
        double far = 260 * e.bellScale() + 8;
        for (String kind : t.sides())
            for (int b : t.bones()) {
                float[] bb = model.bounds[b];
                if (bb == null) continue;
                for (float[] sp : spots) {
                    Vec3 mid = e.boneWorld(b, new Vector3f(
                            bb[0] + (bb[3] - bb[0]) * sp[0], bb[1] + (bb[4] - bb[1]) * sp[1],
                            bb[2] + (bb[5] - bb[2]) * sp[2]));
                    Vec3 out = side(kind, mid, e);
                    Vec3 look = out.scale(-1);
                    Vec3 from = mid.add(out.scale(far));
                    BellRig.Hit hit = e.raycast(from, look, far * 2);
                    if (hit == null || !t.bones().contains(hit.bone())) continue;
                    Vec3 at = from.add(look.scale(hit.t()));
                    // a face of him, not the very edge of a block the look only grazes
                    BellRig.Hit near = e.raycast(at.subtract(look.scale(back)), look, back + 1);
                    if (near == null || near.bone() != hit.bone() || Math.abs(near.t() - back) > 0.05) continue;
                    Vec3 eye = at.subtract(look.scale(back));
                    AABB box = new AABB(eye.x - 0.3, eye.y - 1.62, eye.z - 0.3, eye.x + 0.3, eye.y + 0.18, eye.z + 0.3);
                    if (eye.y - 1.62 < e.groundAt(eye.x, eye.z) + 0.1 || Solid.depthIn(e, box, 0.0, null) > 0) continue;
                    ChunkPos cp = new ChunkPos(BlockPos.containing(eye));
                    h.getLevel().setChunkForced(cp.x, cp.z, true);
                    return new Aim(at, look, hit.bone());
                }
            }
        return null;
    }

    /** a fresh player with this eye and look swings at him (as the client's hit message does) */
    static float swing(HollowbellEntity e, GameTestHelper h, Vec3 eye, Vec3 look, int claimed, List<ServerPlayer> ps) {
        ServerPlayer p = player(h, eye.subtract(0, 1.62, 0));
        ps.add(p);
        p.setYRot((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        p.setXRot((float) -Math.toDegrees(Math.asin(look.normalize().y)));
        p.setYHeadRot(p.getYRot());
        float before = e.healthNow();
        e.hitBy(p, claimed);
        return before - e.healthNow();
    }

    static void hits(GameTestHelper h, float size, boolean moving, int slot) {
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, slot);
        Vec3 p0 = e.position();
        Vec3[] before = new Vec3[1];
        h.runAfterDelay(60, () -> {
            e.setStay(!moving);
            if (moving) e.orderTo(p0.add(300 * size + 40, 0, 120 * size), null);
        });
        h.runAfterDelay(100, () -> before[0] = e.position());
        h.runAfterDelay(120, () -> {
            if (moving) h.assertTrue(e.position().distanceTo(before[0]) > 0.01, "he isn't drifting");
            BellRig rig = e.rig;
            List<ServerPlayer> players = new ArrayList<>();
            StringBuilder log = new StringBuilder();
            int tried = 0;
            double perWorth = -1;
            try {
                for (Target t : targets(rig)) {
                    Aim a = aim(h, e, t, 2.5);
                    if (a == null) {
                        log.append(t.name()).append(": no spot; ");
                        continue;
                    }
                    tried++;
                    Vec3 eye = a.at().subtract(a.look().scale(2.5));
                    float lost = swing(e, h, eye, a.look(), -1, players);
                    h.assertTrue(lost > 0f, t.name() + " (size " + size + (moving ? ", drifting" : "")
                            + "): a swing looking right at " + rig.boneNames[a.bone()] + " did nothing");
                    // the same swing on every part: what he loses goes by the part it landed on
                    double pw = lost / e.worth(a.bone(), false);
                    if (perWorth < 0) perWorth = pw;
                    // (his health is a float: a small loss off a big health is only good to a few of its steps)
                    double slack = perWorth * 0.02 + 4 * Math.ulp(e.healthNow()) / e.worth(a.bone(), false);
                    h.assertTrue(Math.abs(pw - perWorth) < slack, t.name() + ": lost " + lost
                            + ", not as a blow on " + rig.boneNames[a.bone()] + " should");
                    log.append(t.name()).append(" ok; ");
                }
                h.assertTrue(tried >= 8, "too few parts of him to aim at: " + log);

                Aim a = aim(h, e, targets(rig).get(1), 2.5);
                h.assertTrue(a != null, "no spot on his dome to aim at");
                Vec3 eye = a.at().subtract(a.look().scale(2.5));
                // looking away from him finds nothing
                h.assertTrue(e.raycast(eye, a.look().scale(-1), 4.5) == null, "found him looking away");
                h.assertTrue(swing(e, h, eye, a.look().scale(-1), -1, players) == 0f, "hit him looking away");
                // the client's word on a bone is taken in reach of it only
                h.assertTrue(swing(e, h, eye, a.look().scale(-1), a.bone(), players) > 0f,
                        "a swing the client saw land, in reach of it, did nothing");
                // back along the look until nothing of him is within reach of the eye
                Vec3 far = null;
                for (int k = 7; k < 60 && far == null; k++) {
                    Vec3 f = a.at().subtract(a.look().scale(k));
                    if (e.partIn(new AABB(f, f).inflate(4.5), -1) == null) far = f;
                }
                h.assertTrue(far != null, "nowhere out of his reach along the look");
                h.assertTrue(swing(e, h, far, a.look(), a.bone(), players) == 0f,
                        "a swing from out of reach hurt him");
                int farBone = -1;
                double farDist = 0;
                for (BellRig.StrandDef sd : rig.strands) {
                    int b = sd.bones()[sd.bones().length - 1];
                    float[] bb = BellModel.get().bounds[b];
                    if (bb == null) continue;
                    Vector3f c = new Vector3f((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2);
                    double d = e.boneWorld(b, c).distanceTo(eye);
                    if (d > farDist) {
                        farDist = d;
                        farBone = b;
                    }
                }
                h.assertTrue(swing(e, h, eye, a.look().scale(-1), farBone, players) == 0f,
                        "the client's word on a far-off bone was taken");

                if (moving) {
                    // seen a few ticks ago: he has drifted on since, the swing still lands where he was
                    Aim was = aim(h, e, targets(rig).get(7), 2.5);
                    h.assertTrue(was != null, "no arm tip to aim at");
                    Vec3 wasEye = was.at().subtract(was.look().scale(2.5));
                    h.runAfterDelay(6, () -> {
                        List<ServerPlayer> late = new ArrayList<>();
                        try {
                            h.assertTrue(swing(e, h, wasEye, was.look(), was.bone(), late) > 0f,
                                    "a swing at where his arm tip was a moment ago did nothing");
                        } finally {
                            for (ServerPlayer p : late) drop(p);
                        }
                        release(h, e);
                        h.succeed();
                    });
                }
                HollowbellMod.LOG.info("hitbox size {} {}: {}", size, moving ? "drifting" : "still", log);
            } finally {
                for (ServerPlayer p : players) drop(p);
            }
            if (!moving) {
                release(h, e);
                h.succeed();
            }
        });
    }
}
