package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellAnim;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidCarry;
import net.jj.hollowbell.solid.SolidShape;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

/**
 * The solid kit on him (1.10): his dome, rim, crown, glowing spots, pods, egg clumps and the roots of his arms are
 * solid, his strands and the ends of his arms push you aside. People and things stand on him and stay on while he
 * drifts and pulses; whatever is put inside him is out within two ticks, unhurt; what he moves into is pushed, never
 * left inside; dropped things land on him; the inside of his dome stays a place you can be; his parts don't pass
 * through each other (much) in his moves; and what it costs a tick.
 */
public class SolidTests implements FabricGameTest {
    static final float SZ = 0.3f;

    /**
     * Runs r t ticks from now. The game's own way (runAfterDelay) adds to the very map the game walks while it runs a
     * test's callbacks, and called from inside one it crashed the server now and then; so each test keeps its own list,
     * looked at every tick by one watcher put in place the first time (always at the start of the test).
     */
    static void after(GameTestHelper h, int t, Runnable r) {
        Later l = LATER.computeIfAbsent(h, Later::new);
        synchronized (l.due) { l.due.add(new Object[]{h.getTick() + t, r}); }
    }

    private static final java.util.Map<GameTestHelper, Later> LATER = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static final class Later {
        final List<Object[]> due = new ArrayList<>();
        final GameTestHelper h;
        Later(GameTestHelper h) { this.h = h; h.onEachTick(this::tick); }
        void tick() {
            List<Object[]> now = new ArrayList<>();
            synchronized (due) {
                for (var it = due.iterator(); it.hasNext(); ) { Object[] d = it.next(); if ((Long) d[0] <= h.getTick()) { now.add(d); it.remove(); } }
            }
            for (Object[] d : now) ((Runnable) d[1]).run();
        }
    }

    static int slice(BellRig rig, String bone, int k) { return BellPieces.get().first[rig.index.get(bone)] + k; }

    /** a spot (the world) deep in one of his slices, solid for `up` blocks over it, near its middle */
    static @Nullable Vec3 deepIn(HollowbellEntity e, int slice, double up) {
        SolidShape sh = e.solidShape();
        int f = sh.frameOfBone[slice];
        if (f < 0) return null;
        float[] bb = sh.bounds[f];
        float mx = (bb[0] + bb[3]) / 2, my = (bb[1] + bb[4]) / 2, mz = (bb[2] + bb[5]) / 2;
        int need = (int) Math.ceil(up / e.bellScale()) + 2, side = (int) Math.ceil(0.6 / e.bellScale()) + 1;
        double[] best = {Double.MAX_VALUE};
        int[] at = new int[3];
        sh.cells(f, (x, y, z) -> {
            for (int k = -2; k <= need; k++) if (!sh.has(f, x, y + k, z)) return;
            if (!sh.has(f, x + side, y, z) || !sh.has(f, x - side, y, z) || !sh.has(f, x, y, z + side) || !sh.has(f, x, y, z - side)) return;
            double d = (x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz);
            if (d < best[0]) { best[0] = d; at[0] = x; at[1] = y; at[2] = z; }
        });
        if (best[0] == Double.MAX_VALUE) return null;
        return Solid.toWorld(Solid.frames(e), f, new Vector3f(at[0] + 0.5f, at[1] + 0.5f, at[2] + 0.5f));
    }

    static String runs(HollowbellEntity e, double x, double z, double y0, double y1) {
        Solid.Runs r = Solid.column(e, x, z, y0, y1, true, new Solid.Runs());
        StringBuilder b = new StringBuilder("runs");
        for (int j = 0; j < r.n; j++) b.append(String.format(" [%s %.2f..%.2f]", e.rig.boneNames[BellPieces.get().bone[e.solidShape().bone[r.frame[j]]]], r.bot[j], r.top[j]));
        return b.toString();
    }

    static ArmorStand stand(GameTestHelper h, Vec3 at, boolean floating) {
        ArmorStand a = EntityType.ARMOR_STAND.create(h.getLevel());
        a.moveTo(at.x, at.y + 0.02, at.z, 0f, 0f);
        a.setNoGravity(floating);
        h.getLevel().addFreshEntity(a);
        return a;
    }

    static void done(GameTestHelper h, HollowbellEntity e, Entity... others) {
        for (Entity o : others) {
            if (o instanceof ServerPlayer p) HollowbellGameTests.drop(p);
            else o.discard();
        }
        release(h, e);
        h.succeed();
    }

    // ------------------------------------------------------------------ riding along

    /** players (carried as their own game would) and stands (carried by the server) on his parts while he drifts */
    static void rideCase(GameTestHelper h, float size, int slot, boolean full) {
        HollowbellEntity e = spawnAway(h, size, HollowbellEntity.CALM, slot);
        after(h, 60, () -> {
            BellRig rig = e.rig;
            e.setStay(true);
            String[] names = full ? new String[]{"the crown", "the top of his dome", "the side of his dome", "a glowing spot inside", "a big pod", "the root of an arm"}
                    : new String[]{"the crown", "the top of his dome"};
            int[] slices = full ? new int[]{slice(rig, "crown", 0), slice(rig, "bell_2", 0), slice(rig, "bell_6", 0), slice(rig, "spot_0", 0),
                    BellPieces.get().first[rig.pods[11].bone()], slice(rig, rig.boneNames[rig.arms[2].bones()[0]], 2)}
                    : new int[]{slice(rig, "crown", 0), slice(rig, "bell_2", 0)};
            // (a pod and an arm swing about on their own: they must carry you while he drifts, not through his big move)
            boolean[] steady = full ? new boolean[]{true, true, true, true, false, false} : new boolean[]{true, true};
            List<ServerPlayer> ps = new ArrayList<>();
            List<SolidCarry> carries = new ArrayList<>();
            List<String> on = new ArrayList<>();
            List<Boolean> keep = new ArrayList<>();
            // (how far one may slide on him while he drifts: the side of his dome is a slope that squeezes in with each
            // pulse, so you slip a step down it now and then; an arm bends under you)
            double[] slides = full ? new double[]{0.5, 0.5, 1.6, 0.5, 2.0, 1.0} : new double[]{0.5, 0.5};
            List<Double> slideOk = new ArrayList<>();
            List<ArmorStand> stands = new ArrayList<>();
            for (int i = 0; i < slices.length; i++) {
                Vec3 at = e.topOfSlice(slices[i]);
                h.assertTrue(at != null || !steady[i], "no spot to stand on " + names[i]);
                if (at == null) continue;
                ServerPlayer p = player(h, at.add(0, 0.01, 0));
                SolidCarry c = new SolidCarry();
                c.step(p, Solid.bodies(false));
                h.assertTrue(c.riding(), "a player put on " + names[i] + " stands on him: " + p.position() + " " + runs(e, p.getX(), p.getZ(), p.getY() - 3, p.getY() + 3));
                ps.add(p); carries.add(c); on.add(names[i]); keep.add(steady[i]); slideOk.add(slides[i]);
                if (full) stands.add(stand(h, at.add(0.4 * size, 0, 0), false));
            }
            int n = ps.size();
            int[] offTicks = new int[n];
            double[] drift = new double[n], walkDrift = new double[n];
            Vector3f[] rest0 = new Vector3f[n];
            int[] frame0 = new int[n];
            boolean[] wasOff = new boolean[n];
            for (int i = 0; i < n; i++) { rest0[i] = new Vector3f(carries.get(i).rest); frame0[i] = carries.get(i).frame; }
            Vec3 from = e.position();
            e.setStay(false);
            e.setGoal(from.add(30 * size + 6, 0, 20 * size + 4));
            int total = full ? 420 : 200, bigAt = full ? 300 : 10000;
            for (int k = 1; k < total; k++) {
                int kk = k;
                after(h, k, () -> {
                    for (int i = 0; i < n; i++) {
                        SolidCarry c = carries.get(i);
                        c.step(ps.get(i), Solid.bodies(false));
                        // (thrown off: they fall, as their own game would have them, and land on him again if he's under them)
                        if (!c.riding()) {
                            ServerPlayer pl = ps.get(i);
                            pl.setPos(pl.getX(), pl.getY() - 0.4, pl.getZ());
                            c.step(pl, Solid.bodies(false));
                        }
                        // (landed on him again after coming off: what it slides is measured from there)
                        if (c.riding() && wasOff[i]) { rest0[i] = new Vector3f(c.rest); frame0[i] = c.frame; }
                        wasOff[i] = !c.riding();
                        if (!c.riding() && (kk < bigAt || keep.get(i))) {
                            if (offTicks[i] == 0) HollowbellMod.LOG.info("solid ride: {} came off at tick {} at {}; {}", on.get(i), kk, ps.get(i).position(),
                                    runs(e, ps.get(i).getX(), ps.get(i).getZ(), ps.get(i).getY() - 8, ps.get(i).getY() + 4));
                            offTicks[i]++;
                        } else if (c.riding() && c.frame == frame0[i] && c.air == 0) {
                            double dd = c.rest.distance(rest0[i]) * e.bellScale();
                            if (dd > drift[i] + 0.3) HollowbellMod.LOG.info("solid ride: {} slid to {} at tick {} (him at {}, the player at {}, move {})", on.get(i), String.format("%.2f", dd), kk, e.position(), ps.get(i).position(), e.moveNow());
                            drift[i] = Math.max(drift[i], dd);
                            if (kk < bigAt) walkDrift[i] = drift[i];
                        }
                    }
                    if (kk == bigAt) { e.setGoal(null); e.setStay(true); h.assertTrue(e.forceMove(Moves.PULSE), "the pulse wave starts"); }
                });
            }
            after(h, total + 2, () -> {
                double went = Math.hypot(e.getX() - from.x, e.getZ() - from.z);
                h.assertTrue(went > Math.min(2, 20 * size), "he drifted: " + went);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) sb.append(String.format("%s: off %d ticks, slid %.2f drifting and %.2f in all; ", on.get(i), offTicks[i], walkDrift[i], drift[i]));
                for (int i = 0; i < n; i++) {
                    // (a pod and an arm swing about and throw you now and then: you land on them again)
                    h.assertTrue(offTicks[i] <= (keep.get(i) ? total / 50 : total / 10), "the player on " + on.get(i) + " came off him for " + offTicks[i] + " ticks (" + sb + ")");
                    // (an arm bends under you: a little give there)
                    h.assertTrue(walkDrift[i] < slideOk.get(i), "the player on " + on.get(i) + " slid " + walkDrift[i] + " blocks on him while he drifted (" + sb + ")");
                    if (keep.get(i)) h.assertTrue(Solid.onTop(e, ps.get(i), 0.9, 1.5), "the player on " + on.get(i) + " is still on him at the end: " + ps.get(i).position());
                }
                int standsOn = 0;
                for (ArmorStand a : stands) if (Solid.onTop(e, a, 0.9, 1.5)) standsOn++;
                h.assertTrue(standsOn >= (stands.size() + 1) / 2, "the stands the server carries are still on him: " + standsOn + " of " + stands.size());
                HollowbellMod.LOG.info("solid ride (size {}): {} stands on {}/{}", size, sb, standsOn, stands.size());
                List<Entity> all = new ArrayList<>(ps);
                all.addAll(stands);
                done(h, e, all.toArray(new Entity[0]));
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "solid_ride")
    public void peopleAndThingsStayOnHisPartsWhileHeDriftsAndPulses(GameTestHelper h) { rideCase(h, SZ, 500, true); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "solid_ride_small")
    public void aTinyOneCarriesYouToo(GameTestHelper h) { rideCase(h, 0.05f, 501, false); }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "solid_ride_big")
    public void aBigOneCarriesYouToo(GameTestHelper h) { rideCase(h, 1.5f, 502, false); }

    // ------------------------------------------------------------------ never inside

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "solid_inside")
    public void whateverIsPutInsideHimIsOutWithinTwoTicksUnhurt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.6f, HollowbellEntity.CALM, 503);
        after(h, 60, () -> {
            e.setStay(true);
            BellRig rig = e.rig;
            BellPieces pc = BellPieces.get();
            // deep in a pod, in the root of an arm, in the crown or the dome (the first that's thick enough of each)
            List<Vec3> spots = new ArrayList<>();
            List<int[]> tries = new ArrayList<>();
            int[] pods = new int[rig.pods.length];
            for (int k = 0; k < pods.length; k++) pods[k] = pc.first[rig.pods[k].bone()];
            tries.add(pods);
            int[] arms = new int[rig.arms.length * 2];
            for (int k = 0; k < rig.arms.length; k++) { arms[2 * k] = pc.first[rig.arms[k].bones()[0]] + 3; arms[2 * k + 1] = pc.first[rig.arms[k].bones()[1]] + 3; }
            tries.add(arms);
            List<Integer> dome = new ArrayList<>();
            for (int b = 0; b < rig.boneCount(); b++) if (rig.kind[b] == BellRig.Kind.CROWN || rig.kind[b] == BellRig.Kind.BELL || rig.kind[b] == BellRig.Kind.RIM || rig.kind[b] == BellRig.Kind.SPOT) dome.add(pc.first[b]);
            tries.add(dome.stream().mapToInt(Integer::intValue).toArray());
            for (int[] t : tries) for (int sl : t) { Vec3 v = deepIn(e, sl, 0.6); if (v != null) { spots.add(v); break; } }
            h.assertTrue(spots.size() >= 2, "spots well inside him: " + spots);
            List<Entity> things = new ArrayList<>();
            for (Vec3 at : spots) {
                ArmorStand a = stand(h, at.subtract(0, 0.4, 0), true);
                things.add(a);
                ItemEntity it = new ItemEntity(h.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COD));
                it.setNoGravity(true);
                h.getLevel().addFreshEntity(it);
                things.add(it);
                var cow = EntityType.COW.create(h.getLevel());
                cow.moveTo(at.x, at.y - 0.3, at.z, 0f, 0f);
                cow.setNoGravity(true);
                h.getLevel().addFreshEntity(cow);
                things.add(cow);
            }
            // (a dropped thing is too small to tell by the middles of the columns: only the bigger things are checked here)
            for (Entity x : things) if (x instanceof LivingEntity) h.assertTrue(Solid.inside(e, x, 0.08), "it was put inside him: " + x + " " + x.position() + " " + runs(e, x.getX(), x.getZ(), x.getY() - 3, x.getY() + 3));
            float[] hp = new float[things.size()];
            for (int i = 0; i < hp.length; i++) hp[i] = things.get(i) instanceof LivingEntity le ? le.getHealth() : 0;
            // a player: their own game moves them out at once; the server, if it didn't, after half a second
            ServerPlayer client = player(h, spots.get(0).subtract(0, 0.4, 0));
            ServerPlayer noClient = player(h, spots.get(spots.size() - 1).subtract(0, 0.4, 0));
            h.assertTrue(Solid.inside(e, client, 0.08) && Solid.inside(e, noClient, 0.08), "the players were put inside him");
            h.assertTrue(Solid.unstick(e, client, 0.08) && !Solid.inside(e, client, 0.08), "a player's own game moves them out at once: " + client.position());
            // (out within two ticks, and never in again for more than two ticks running: he moves, and nothing carries
            // a thing that floats beside him, so his next pulse or step may come into it and shove it out again)
            boolean[] wasOut = new boolean[things.size()];
            int[] run = new int[things.size()], worstRun = new int[things.size()];
            float[] hp2 = new float[things.size()];
            for (int k = 1; k <= 12; k++) {
                int kk = k;
                after(h, k, () -> {
                    for (int i = 0; i < things.size(); i++) {
                        boolean in = Solid.inside(e, things.get(i), 0.08);
                        if (!in && kk <= 2) wasOut[i] = true;
                        // (unhurt by being moved out: checked right after, before anything else of his, a sting, can touch it)
                        if (kk == 2 && things.get(i) instanceof LivingEntity le) hp2[i] = le.getHealth();
                        run[i] = in ? run[i] + 1 : 0;
                        worstRun[i] = Math.max(worstRun[i], run[i]);
                    }
                });
            }
            after(h, 13, () -> {
                for (int i = 0; i < things.size(); i++) {
                    Entity x = things.get(i);
                    h.assertTrue(!x.isRemoved() && wasOut[i] && worstRun[i] <= 2, "out of him within two ticks: " + x + " " + x.position() + " (out by tick 2 " + wasOut[i] + ", inside at most "
                            + worstRun[i] + " ticks running) " + runs(e, x.getX(), x.getZ(), x.getY() - 2, x.getY() + 2));
                    if (x instanceof LivingEntity) h.assertTrue(hp2[i] >= hp[i], "and unhurt: " + x + " " + hp2[i] + " of " + hp[i]);
                }
            });
            // (he moves: once out, nobody's game carries this one, so he may come back into them: they must have been
            // moved out at some point)
            boolean[] out = {false};
            for (int k = 1; k <= 16; k++) after(h, k, () -> { if (!Solid.inside(e, noClient, 0.08)) out[0] = true; });
            after(h, 17, () -> {
                h.assertTrue(out[0], "the server moved a player out of him whose game didn't: " + noClient.position() + " depth " + Solid.depthIn(e, noClient.getBoundingBox(), 0.3, null, true)
                        + " " + runs(e, noClient.getX(), noClient.getZ(), noClient.getY() - 3, noClient.getY() + 4) + " him " + e.position() + " unstuck " + Solid.unstuckServer);
                h.assertTrue(noClient.getHealth() >= noClient.getMaxHealth() - 0.01f, "unhurt: " + noClient.getHealth());
                List<Entity> all = new ArrayList<>(things);
                all.add(client); all.add(noClient);
                done(h, e, all.toArray(new Entity[0]));
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "solid_hollow")
    public void theInsideOfHisDomeIsAPlaceYouCanBe(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, SZ, HollowbellEntity.CALM, 504);
        after(h, 60, () -> {
            e.setStay(true);
            // floating in the hollow under the dome, between the vase and the dome's wall
            BellRig rig = e.rig;
            float y = rig.rimY + 18, r = (rig.domeInner(y) + 25) / 2;
            List<ArmorStand> in = new ArrayList<>();
            for (int k = 0; k < 4; k++) {
                double a = Math.PI / 4 + k * Math.PI / 2;
                Vec3 at = e.toWorld(new Vector3f((float) (Math.cos(a) * r), y, (float) (Math.sin(a) * r)));
                ArmorStand s = stand(h, at, true);
                in.add(s);
            }
            Vec3[] was = new Vec3[in.size()];
            for (int i = 0; i < was.length; i++) was[i] = in.get(i).position();
            after(h, 20, () -> {
                int free = 0;
                for (int i = 0; i < in.size(); i++) if (!Solid.inside(e, in.get(i), 0.08) && in.get(i).position().distanceTo(was[i]) < 1.5) free++;
                h.assertTrue(free >= 3, "things in the hollow of his dome were pushed out of it: " + free + " of " + in.size() + " stayed");
                done(h, e, in.toArray(new Entity[0]));
            });
        });
    }

    // ------------------------------------------------------------------ pushed, never trapped

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "solid_push")
    public void whatHeMovesIntoIsPushedNotLeftInside(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, SZ, HollowbellEntity.CALM, 505);
        after(h, 60, () -> {
            e.setStay(true);
            BellRig rig = e.rig;
            // stands just clear of the roots of his arms, the rim and his pods, floating; then he turns round on the spot
            List<ArmorStand> stands = new ArrayList<>();
            for (int a = 0; a < rig.arms.length; a += 2) {
                Vec3 root = e.boneWorld(rig.arms[a].bones()[0], rig.arms[a].joints()[1]);
                for (int s = -1; s <= 1; s += 2) {
                    double ang = Math.atan2(root.z - e.getZ(), root.x - e.getX()) + s * 0.35;
                    double rr = Math.hypot(root.x - e.getX(), root.z - e.getZ());
                    stands.add(stand(h, new Vec3(e.getX() + Math.cos(ang) * rr, root.y, e.getZ() + Math.sin(ang) * rr), true));
                }
            }
            for (int p : new int[]{11, 3, 17}) {
                Vec3 c = e.podWorld(p);
                stands.add(stand(h, c.add(rig.pods[p].radius() * SZ + 1.2, -0.8, 0), true));
            }
            int[] streak = new int[stands.size()], worst = new int[1];
            float yaw0 = e.getYRot();
            for (int k = 1; k <= 180; k++) {
                int kk = k;
                after(h, k, () -> {
                    for (int i = 0; i < stands.size(); i++) {
                        boolean in = Solid.inside(e, stands.get(i), 0.08);
                        streak[i] = in ? streak[i] + 1 : 0;
                        worst[0] = Math.max(worst[0], streak[i]);
                    }
                    e.setYRot(yaw0 + 3f * kk);
                });
            }
            after(h, 182, () -> {
                h.assertTrue(worst[0] <= 2, "something he turned into was left inside him for " + worst[0] + " ticks running");
                for (ArmorStand a : stands) h.assertTrue(!Solid.inside(e, a, 0.08), "a stand is inside him at the end: " + a.position());
                HollowbellMod.LOG.info("solid push: longest inside {} ticks; unstuck by the server {}", worst[0], Solid.unstuckServer);
                done(h, e, stands.toArray(new Entity[0]));
            });
        });
    }

    // ------------------------------------------------------------------ mobs and dropped things

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "solid_collide")
    public void mobsAndDroppedThingsBumpIntoHimAndLandOnHim(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, SZ, HollowbellEntity.CALM, 506);
        after(h, 60, () -> {
            e.setStay(true);
            BellRig rig = e.rig;
            Vec3 top = e.topOfSlice(slice(rig, "bell_3", 0));
            h.assertTrue(top != null, "a spot on his dome to drop onto");
            ItemEntity it = new ItemEntity(h.getLevel(), top.x, top.y + 6, top.z, new ItemStack(Items.LEAD));
            it.setDeltaMovement(Vec3.ZERO);
            h.getLevel().addFreshEntity(it);
            Vec3 crown = e.topOfSlice(slice(rig, "crown", 0));
            h.assertTrue(crown != null, "a spot on his crown");
            var cow = EntityType.COW.create(h.getLevel());
            cow.moveTo(crown.x, crown.y + 4, crown.z, 0f, 0f);
            h.getLevel().addFreshEntity(cow);
            // a floating cow walked straight at a big pod
            Vec3 pod = e.podWorld(11);
            Vec3 start = pod.add(rig.pods[11].radius() * SZ + 8, 0, 0);
            var cow2 = EntityType.COW.create(h.getLevel());
            cow2.moveTo(start.x, start.y, start.z, 0f, 0f);
            cow2.setNoAi(true);
            cow2.setNoGravity(true);
            h.getLevel().addFreshEntity(cow2);
            int[] inside = {0, 0};
            for (int k = 1; k <= 60; k++) after(h, k, () -> {
                Vec3 p = e.podWorld(11);
                Vec3 d = new Vec3(p.x - cow2.getX(), 0, p.z - cow2.getZ());
                if (d.lengthSqr() > 0.01) cow2.move(MoverType.SELF, d.normalize().scale(0.3));
                // (the pod swings: it may come into the cow now and then, and shoves it out at once)
                if (Solid.inside(e, cow2, 0.08)) { inside[1]++; inside[0] = Math.max(inside[0], inside[1]); } else inside[1] = 0;
            });
            after(h, 62, () -> {
                h.assertTrue(it.getY() > top.y - 3 && Solid.onTop(e, it, 0.9, 1.5) && !Solid.inside(e, it, 0.08), "a dropped thing lands on him: " + it.position() + " dome " + top.y
                        + " " + runs(e, it.getX(), it.getZ(), it.getY() - 3, it.getY() + 3));
                h.assertTrue(Solid.onTop(e, cow, 0.9, 1.5) && !Solid.inside(e, cow, 0.08), "a cow lands on his crown: " + cow.position() + " crown " + crown.y + " on ground " + cow.onGround()
                        + " " + runs(e, cow.getX(), cow.getZ(), cow.getY() - 6, cow.getY() + 3) + " him " + e.position());
                h.assertTrue(inside[0] <= 2, "the cow walked into his pod: inside " + inside[0] + " ticks running");
                h.assertTrue(cow2.position().distanceTo(start) > 0.5, "and it did walk up to it: " + cow2.position());
                done(h, e, it, cow, cow2);
            });
        });
    }

    // ------------------------------------------------------------------ his parts apart

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 4000, batch = "solid_overlap")
    public void hisPartsDoNotPassThroughEachOtherInHisMoves(GameTestHelper h) {
        // (the solid lab's count: model cells where one part is right inside another, away from where they join, in
        // the worst key pose; measured before the keep-apart pass (1.9.8) and after it (1.10.0), the limit a bit over after)
        String[][] want = {{"climb", "4600"}, {"curtain", "4800"}, {"sky_dive", "3200"}, {"drop", "3600"}, {"sweep", "1600"}, {"grab", "1100"}, {"carry", "1500"}, {"idle", "1000"}};
        StringBuilder sb = new StringBuilder();
        try {
            Class<?> lab = Class.forName("net.jj.hollowbell.lab.SolidLab");
            var m = lab.getMethod("worst", String.class);
            for (String[] w : want) {
                Object[] r = (Object[]) m.invoke(null, w[0]);
                int all = (Integer) r[1];
                sb.append(w[0]).append(' ').append(all).append(" (solid parts ").append(r[0]).append(", ").append(r[2]).append("); ");
                h.assertTrue(all <= Integer.parseInt(w[1]), w[0] + ": " + all + " cells of his parts in each other, over " + w[1] + " (" + r[2] + ")");
            }
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        HollowbellMod.LOG.info("solid overlap: {}", sb);
        h.succeed();
    }

    // ------------------------------------------------------------------ what it costs

    /** the server's tick, on average over the last hundred ticks (ms) */
    static double avgTick(GameTestHelper h) {
        long[] times = h.getLevel().getServer().getTickTimesNanos();
        double avg = 0;
        for (long x : times) avg += x;
        return avg / times.length / 1e6;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1000, batch = "solid_perf")
    public void whatItCostsATick(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, SZ, HollowbellEntity.CALM, 507);
        List<Entity> stuff = new ArrayList<>();
        after(h, 60, () -> {
            e.setStay(true);
            BellRig rig = e.rig;
            // cows round him and under him, stands and dropped things on his dome; he drifts
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8;
                Vec3 at = e.position().add(Math.cos(a) * 25 * SZ * (1 + i % 3), 0, Math.sin(a) * 25 * SZ * (1 + i % 3));
                var cow = EntityType.COW.create(h.getLevel());
                cow.moveTo(at.x, e.groundAt(at.x, at.z), at.z, 0f, 0f);
                h.getLevel().addFreshEntity(cow);
                stuff.add(cow);
                Vec3 d = e.topOfSlice(slice(rig, "bell_" + (1 + i % 6), 0));
                if (d != null) {
                    ItemEntity it = new ItemEntity(h.getLevel(), d.x + (i % 3) * 0.3, d.y + 1, d.z, new ItemStack(Items.COD));
                    h.getLevel().addFreshEntity(it);
                    stuff.add(it);
                    stuff.add(stand(h, d.add(0, 0, (i % 4) * 0.2), false));
                }
            }
            e.setStay(false);
            e.setGoal(e.position().add(40, 0, 25));
        });
        long[] t = new long[6];
        double[] tick = new double[2];
        // (the first stretch with the kit on; then with it and the keep-apart pass off, as in 1.9.8)
        long[] n0 = new long[2];
        after(h, 120, () -> { n0[0] = Solid.nanosServer; n0[1] = Solid.queries; t[2] = BellAnim.apartNanos; });
        double[] kit = new double[3];
        after(h, 320, () -> {
            tick[1] = avgTick(h);
            kit[0] = (Solid.nanosServer - n0[0]) / 1e6 / 200.0;
            kit[1] = (Solid.queries - n0[1]) / 200.0;
            kit[2] = (BellAnim.apartNanos - t[2]) / 1e6 / 200.0;
            HollowbellEntity.solidOff = true; BellAnim.APART = false;
        });
        after(h, 520, () -> { tick[0] = avgTick(h); HollowbellEntity.solidOff = false; BellAnim.APART = true; });
        after(h, 600, () -> {
            double kitMs = kit[0], q = kit[1], apartMs = kit[2];
            double before = tick[0], now = tick[1];
            HollowbellMod.LOG.info("solid perf: with {} things round him: the server's tick {} ms before (no kit, no keep-apart), {} ms after; the kit's server work {} ms a tick ({} column looks a tick); keeping his parts apart {} ms a tick",
                    stuff.size(), String.format("%.2f", before), String.format("%.2f", now), String.format("%.3f", kitMs), String.format("%.0f", q), String.format("%.3f", apartMs));
            h.assertTrue(kitMs < 3.0, "the solid kit takes " + kitMs + " ms a tick");
            h.assertTrue(apartMs < 1.5, "keeping his parts apart takes " + apartMs + " ms a tick");
            done(h, e, stuff.toArray(new Entity[0]));
        });
    }
}
