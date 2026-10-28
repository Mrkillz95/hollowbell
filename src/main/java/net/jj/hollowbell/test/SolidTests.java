package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.BellKit.after;
import static net.jj.hollowbell.test.BellKit.chunksFor;
import static net.jj.hollowbell.test.BellKit.bell;
import static net.jj.hollowbell.test.BellKit.done;
import static net.jj.hollowbell.test.BellKit.onDome;
import static net.jj.hollowbell.test.BellKit.player;
import static net.jj.hollowbell.test.BellKit.runs;
import static net.jj.hollowbell.test.BellKit.spot;
import static net.jj.hollowbell.test.BellKit.stand;
import static net.jj.hollowbell.test.BellKit.topOver;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.BellSolid;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidShape;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The solid kit on him: his bell (glass and all), rim, crown, the glowing balls inside and his arms are solid; the
 * strands, pods and egg clumps aren't. What lands on his dome stays there at every size, through his pulses and as
 * he drifts along; the inside of the dome stays hollow, and whoever he holds in there is left where he holds them.
 */
public class SolidTests implements FabricGameTest {
    private static final float[] SIZES = {0.1f, 1f, 2f};

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "solid_what")
    public void hisBellRimCrownAndArmsAreSolidButNotHisStrands(GameTestHelper h) {
        SolidShape sh = BellSolid.shape();
        BellRig rig = BellRig.get();
        BellModel m = BellModel.get();
        for (int b = 0; b < rig.boneCount(); b++) {
            boolean solid = m.count(b) > 0 && switch (rig.kind[b]) { case BELL, RIM, CROWN, SPOT, ARM -> true; default -> false; };
            h.assertTrue((sh.frameOfBone[b] >= 0) == solid, rig.boneNames[b] + (solid ? " isn't solid" : " is solid"));
        }
        // every block of the solid parts is solid, the glass too (glass is a solid block: the dome can be stood on)
        int glass = 0, glassSolid = 0;
        for (int b = 0; b < rig.boneCount(); b++) {
            int f = sh.frameOfBone[b];
            if (f < 0) continue;
            for (int i = 0; i < m.count(b); i++) {
                boolean has = sh.has(f, m.x[b][i], m.y[b][i], m.z[b][i]);
                h.assertTrue(has, rig.boneNames[b] + " has a block that isn't solid");
                if (m.palette[m.pal[b][i]].contains("glass")) { glass++; if (has) glassSolid++; }
            }
        }
        h.assertTrue(glass > 10000 && glassSolid == glass, glassSolid + " of " + glass + " glass blocks are solid");
        // the dome's walls are filled in (the model keeps only the blocks that show), its inside stays hollow
        BlockPos home = spot(h, 0, chunksFor(1f));
        HollowbellEntity e = bell(h, home, 1f);
        after(h, 5, () -> {
            e.setStay(true);
            for (int i = 0; i < HollowbellEntity.SLOTS; i++) {
                Vec3 at = e.slotWorld(i);
                AABB box = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
                h.assertTrue(Solid.depthIn(e, box, 0.08, null) <= 0.0, "the place " + i + " in his dome is inside him: " + runs(e, at.x, at.z, at.y, at.y + 2));
            }
            // straight up through the dome halfway out: a wall (several blocks thick, filled), then hollow, then the
            // wall again over the top; nothing between
            Vec3 w = e.toWorld(new Vector3f(62, 0, 9));
            Solid.Runs r = Solid.column(e, w.x, w.z, e.getY() + (rig.rimY - 4), e.getY() + rig.crownY + 4, false, new Solid.Runs());
            h.assertTrue(r.n >= 1, "nothing of his dome over " + w + ": " + runs(e, w.x, w.z, e.getY(), e.getY() + 210));
            double thickest = 0;
            for (int j = 0; j < r.n; j++) thickest = Math.max(thickest, r.top[j] - r.bot[j]);
            h.assertTrue(thickest >= 2.0, "his dome's wall is a shell, not filled in: " + runs(e, w.x, w.z, e.getY(), e.getY() + 210));
            done(h, e, home, chunksFor(1f));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "solid_strands")
    public void theStrandsPodsAndEggsDontBlockOrHoldAnyone(GameTestHelper h) {
        BlockPos at = spot(h, 1, chunksFor(0.3f));
        HollowbellEntity e = bell(h, at, 0.3f);
        List<ArmorStand> stands = new ArrayList<>();
        List<Vec3> put = new ArrayList<>();
        after(h, 20, () -> {
            e.setStay(true);
            e.ensurePose();
            // stood right in the strands, the pods and the egg clumps: nothing of him to bump into there, nothing to be
            // pushed out of, nothing to stand on
            List<Vec3> spots = new ArrayList<>();
            for (int s = 0; s < e.rig.strands.length; s += 7) spots.add(e.strandTipWorld(s).add(0, 0.5, 0));
            for (int p = 0; p < e.rig.pods.length; p += 4) spots.add(e.podWorld(p));
            for (int g = 0; g < e.rig.eggs.length; g += 5) spots.add(e.eggWorld(g));
            for (Vec3 v : spots) {
                // (only where none of his solid parts is near: his arms and the vase hang among the strands too)
                boolean clear = true;
                for (int a = -2; a <= 2 && clear; a++)
                    for (int c = -2; c <= 2 && clear; c++)
                        if (Solid.column(e, v.x + a * 1.5, v.z + c * 1.5, v.y - 4, v.y + 4, false, new Solid.Runs()).n > 0) clear = false;
                if (!clear) continue;
                ArmorStand a = stand(h, v.subtract(0, 0.9, 0));
                a.setNoGravity(true);
                stands.add(a);
                put.add(a.position());
                h.assertTrue(!Solid.inside(e, a, 0.08), "he's solid round " + v + ":" + runs(e, v.x, v.z, v.y - 2, v.y + 2));
                Solid.Runs r = Solid.column(e, v.x, v.z, v.y - 1.5, v.y + 1.5, true, new Solid.Runs());
                for (int j = 0; j < r.n; j++) {
                    BellRig.Kind k = e.rig.kind[e.solidShape().bone[r.frame[j]]];
                    h.assertTrue(k != BellRig.Kind.STRAND && k != BellRig.Kind.POD && k != BellRig.Kind.EGG, "a " + k + " is solid at " + v);
                }
            }
        });
        after(h, 40, () -> {
            h.assertTrue(stands.size() >= 5, "only " + stands.size() + " spots in his strands clear of his solid parts");
            // and nothing of his moved them: they're where they were put
            int moved = 0;
            for (int i = 0; i < stands.size(); i++) {
                ArmorStand a = stands.get(i);
                if (a.position().distanceTo(put.get(i)) > 1e-3) {
                    // (only ever by a solid part of him that came by, an arm or his rim, never by a strand)
                    moved++;
                    boolean solidNear = false;
                    for (int x = -2; x <= 2; x++)
                        for (int z = -2; z <= 2; z++)
                            if (Solid.column(e, a.getX() + x * 0.5, a.getZ() + z * 0.5, a.getY() - 2, a.getY() + 3, false, new Solid.Runs()).n > 0) solidNear = true;
                    h.assertTrue(solidNear, "something moved the stand at " + put.get(i) + " to " + a.position() + " with none of his solid parts by it");
                }
                a.discard();
            }
            h.assertTrue(moved <= stands.size() / 3, moved + " of " + stands.size() + " stands in his strands were moved");
            done(h, e, at, chunksFor(0.3f));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "solid_stand")
    public void whatLandsOnHisDomeStaysThereAtEverySize(GameTestHelper h) {
        BlockPos[] at = new BlockPos[SIZES.length];
        HollowbellEntity[] es = new HollowbellEntity[SIZES.length];
        ArmorStand[] stands = new ArmorStand[SIZES.length];
        for (int i = 0; i < SIZES.length; i++) {
            at[i] = spot(h, 2 + i, chunksFor(SIZES[i]));
            es[i] = bell(h, at[i], SIZES[i]);
        }
        after(h, 20, () -> {
            for (int i = 0; i < SIZES.length; i++) {
                HollowbellEntity e = es[i];
                e.setStay(true);
                // on the top of his dome, off to one side of the crown
                Vec3 top = onDome(e, 34, 12);
                h.assertTrue(top != null, "nothing of his dome there at size " + SIZES[i]);
                h.assertTrue(top.y - e.getY() > 150 * SIZES[i], "his dome's top is only " + (top.y - e.getY()) + " up at size " + SIZES[i]);
                stands[i] = stand(h, top.add(0, 1.0, 0));
            }
        });
        // (he breathes: a pulse or two in this time, the dome squeezing in and out under the stand)
        after(h, 220, () -> {
            for (int i = 0; i < SIZES.length; i++) {
                ArmorStand a = stands[i];
                HollowbellEntity e = es[i];
                double top = topOver(e, a.getX(), a.getZ());
                h.assertTrue(Math.abs(a.getY() - top) < 0.6 && Solid.onTop(e, a, 0.9, 1.5),
                        "at size " + SIZES[i] + " it's at " + a.getY() + ", his dome at " + top + runs(e, a.getX(), a.getZ(), a.getY() - 3, a.getY() + 3));
                h.assertTrue(!Solid.inside(e, a, 0.08), "at size " + SIZES[i] + " it's in his dome");
                a.discard();
                e.discard();
            }
            for (int i = 1; i < at.length; i++) BellKit.force(h, at[i].getX(), at[i].getZ(), chunksFor(SIZES[i]), false);
            done(h, null, at[0], chunksFor(SIZES[0]));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "solid_drift")
    public void standingOnHisDomeYouAreCarriedAsHeDrifts(GameTestHelper h) {
        BlockPos at = spot(h, 6, chunksFor(0.3f) + 6);
        HollowbellEntity e = bell(h, at, 0.3f);
        ArmorStand[] a = new ArmorStand[1];
        Vector3f[] was = new Vector3f[1];
        Vec3[] from = new Vec3[1];
        after(h, 20, () -> {
            Vec3 top = onDome(e, 30, -10);
            h.assertTrue(top != null, "nothing of his dome there");
            a[0] = stand(h, top.add(0, 0.6, 0));
        });
        after(h, 60, () -> {
            h.assertTrue(Solid.onTop(e, a[0], 0.9, 1.5), "the stand didn't land on his dome: " + a[0].position());
            was[0] = e.toModel(a[0].position());
            from[0] = e.position();
            // off he goes, 80 blocks away
            e.setGoal(e.position().add(80, 0, 30));
        });
        after(h, 360, () -> {
            double went = e.position().subtract(from[0]).horizontalDistance();
            h.assertTrue(went > 15, "he hardly drifted: " + went);
            Vector3f now = e.toModel(a[0].position());
            float s = e.bellScale();
            // (where it stands on him, in his own blocks: the same spot of him, give or take his leaning and pulsing)
            double off = Math.hypot(now.x - was[0].x, now.z - was[0].z) * s;
            h.assertTrue(Solid.onTop(e, a[0], 0.9, 1.5), "it fell off him as he drifted: " + a[0].position() + " he went " + went);
            h.assertTrue(off < 4.0, "it slid " + off + " blocks over his dome as he drifted " + went);
            done(h, e, at, chunksFor(0.3f) + 6, a[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "solid_held")
    public void whoeverHeHoldsInsideHisDomeIsLeftWhereHeHoldsThem(GameTestHelper h) {
        BlockPos at = spot(h, 7, chunksFor(0.25f));
        HollowbellEntity e = bell(h, at, 0.25f);
        ServerPlayer[] pl = new ServerPlayer[1];
        int[] seen = {0};
        after(h, 20, () -> {
            e.setStay(true);
            pl[0] = player(h, new Vec3(e.getX() + 0.3, e.groundAt(e.getX(), e.getZ()), e.getZ() + 0.3));
            pl[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.GRAB, pl[0]), "no grab");
        });
        h.onEachTick(() -> {
            ServerPlayer p = pl[0];
            if (p == null || !e.moves().isInside(p)) return;
            seen[0]++;
            // held: on his seat in the dome, never moved off it by the solid kit
            h.assertTrue(p.isPassenger(), "inside his dome, but not held");
            h.assertTrue(!Solid.inside(e, p, 0.08) || p.isPassenger(), "inside him");
        });
        after(h, 20 + Moves.length(Moves.GRAB) + 60, () -> {
            ServerPlayer p = pl[0];
            h.assertTrue(e.moves().isInside(p), "the player didn't stay inside the dome (" + seen[0] + " ticks in there)");
            h.assertTrue(seen[0] > 40, "inside only " + seen[0] + " ticks");
            int slot = -1;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < HollowbellEntity.SLOTS; i++) {
                double d = e.slotWorld(i).distanceTo(p.position());
                if (d < best) { best = d; slot = i; }
            }
            h.assertTrue(best < 1.5, "not at his place in the dome: " + best + " from place " + slot);
            done(h, e, at, chunksFor(0.25f), p);
        });
    }
}
