package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A strand grabs and lets go; the inside of the dome. */
public class GrabTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "grab_letgo")
    public void hitTheStrandAndItLetsGo(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 30);
        Pig[] p = new Pig[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(h, under(e));
            p[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.GRAB, p[0]), "no grab");
        });
        // while it's held, the pig stays on the end of the strand holding it
        for (int k = 8; k <= 18; k += 5) {
            int at = k;
            h.runAfterDelay(20 + Moves.REACH + at, () -> {
                if (!p[0].isPassenger()) return;
                Vec3 tip = e.strandTipWorld(e.moveArg());
                double d = p[0].position().add(0, p[0].getBbHeight() * 0.5, 0).distanceTo(tip);
                h.assertTrue(
                        d < 3.0,
                        "the held pig is " + String.format("%.1f", d) + " blocks from the strand tip " + at
                                + " ticks in");
            });
        }
        h.runAfterDelay(20 + Moves.REACH + 20, () -> {
            h.assertTrue(e.moves().grabbed() == p[0], "the strand didn't take hold of the pig");
            h.assertTrue(p[0].isPassenger(), "the pig isn't held");
            h.assertTrue(e.moves().lift() > 0f, "it isn't pulling it up");
            int strand = e.moveArg();
            int bone = e.rig.strands[strand].bones()[0];
            for (int i = 0; i < 4; i++) e.applyDamage(e.damageSources().generic(), 3f, bone, false);
            h.assertTrue(e.moves().grabbed() == null, "hit four times and the strand still holds on");
            h.assertTrue(e.moveNow() == Moves.NONE, "the grab didn't end");
        });
        h.runAfterDelay(20 + Moves.REACH + 30, () -> {
            h.assertTrue(!p[0].isPassenger(), "the pig is still riding something");
            p[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    /** a player pulled up is shut in one of his whole pods, and rides in it as it swings */
    private static ServerPlayer[] pulledIntoAPod(GameTestHelper h, HollowbellEntity e) {
        ServerPlayer[] pl = new ServerPlayer[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            pl[0] = player(h, under(e).add(0.5, 0, 0.5));
            pl[0].setInvulnerable(true);
            h.assertTrue(e.forceMove(Moves.GRAB, pl[0]), "no grab");
        });
        return pl;
    }

    private static int inPodCheck(GameTestHelper h, HollowbellEntity e, ServerPlayer p) {
        h.assertTrue(e.moves().isInside(p), "the player wasn't taken in");
        int pod = e.moves().podOf(p);
        h.assertTrue(pod >= 0, "the player was taken into the dome, not a pod");
        h.assertTrue(!e.isPodPopped(pod), "shut in a popped pod");
        double off = p.position().add(0, p.getBbHeight() * 0.5, 0).distanceTo(e.podWorld(pod));
        h.assertTrue(off < 1.5, "not in the middle of the pod: " + off + " blocks off");
        return pod;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "inside")
    public void pulledIntoAPodAndLetGo(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 31);
        ServerPlayer[] pl = pulledIntoAPod(h, e);
        int at = 20 + Moves.length(Moves.GRAB) + 10;
        h.runAfterDelay(at, () -> inPodCheck(h, e, pl[0]));
        // held: still in it a while later, wherever the pod has swung to
        h.runAfterDelay(at + 40, () -> {
            inPodCheck(h, e, pl[0]);
            // hit him hard enough from in there and he lets you go
            for (int i = 0; i < 30 && e.moves().isInside(pl[0]); i++)
                e.applyDamage(e.damageSources().playerAttack(pl[0]), 20f, e.rig.spots[0].bone(), true);
            h.assertTrue(
                    !e.moves().isInside(pl[0]) && !pl[0].isPassenger(), "hurting him from in there didn't let you go");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "inside_pop")
    public void poppingThePodLetsYouDropFree(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 34);
        ServerPlayer[] pl = pulledIntoAPod(h, e);
        int at = 20 + Moves.length(Moves.GRAB) + 10;
        h.runAfterDelay(at, () -> {
            int pod = inPodCheck(h, e, pl[0]);
            for (int i = 0; i < 40 && !e.isPodPopped(pod); i++)
                e.applyDamage(e.damageSources().generic(), 200f, e.rig.pods[pod].bone(), false);
            h.assertTrue(e.isPodPopped(pod), "the pod wouldn't pop");
        });
        h.runAfterDelay(at + 3, () -> {
            h.assertTrue(!e.moves().isInside(pl[0]) && !pl[0].isPassenger(), "still held once the pod popped");
            h.assertTrue(pl[0].hasEffect(MobEffects.SLOW_FALLING), "dropped with no slow fall");
            h.assertTrue(!Solid.inside(e, pl[0], 0.3), "dropped inside his body");
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "inside_saved")
    public void heldInAPodThroughASave(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.25f, HollowbellEntity.HUNTER, 37);
        ServerPlayer[] pl = pulledIntoAPod(h, e);
        int at = 20 + Moves.length(Moves.GRAB) + 10;
        HollowbellEntity[] b = new HollowbellEntity[1];
        h.runAfterDelay(at, () -> {
            inPodCheck(h, e, pl[0]);
            // he goes out with his land and comes back: the one he held is let go, safely, never left in him
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            e.discard();
            b[0] = ModEntities.HOLLOWBELL.create(h.getLevel());
            b[0].load(tag);
            h.getLevel().addFreshEntity(b[0]);
        });
        h.runAfterDelay(at + 10, () -> {
            h.assertTrue(!pl[0].isPassenger(), "still riding a seat of the one that went");
            h.assertTrue(!b[0].moves().isInside(pl[0]), "the reloaded one has the player inside");
            h.assertTrue(pl[0].hasEffect(MobEffects.SLOW_FALLING), "let go with no slow fall");
            h.assertTrue(!Solid.inside(b[0], pl[0], 0.3), "left inside his body");
            drop(pl[0]);
            release(h, b[0]);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sting")
    public void theStrandsSting(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 32);
        Pig[] z = new Pig[1];
        // the strand sways, so the pig is kept on the same block of it until it's stung
        Runnable onStrand = () -> {
            var S = e.rig.strands[e.rig.strands.length / 2];
            int bone = S.bones()[S.bones().length - 1];
            BellModel m = BellModel.get();
            int k = m.count(bone) / 2;
            Vec3 tip = e.boneWorld(bone, new Vector3f(m.x[bone][k] + 0.5f, m.y[bone][k] + 0.5f, m.z[bone][k] + 0.5f));
            z[0].moveTo(tip.x, tip.y - 0.45, tip.z, 0, 0);
            z[0].setDeltaMovement(Vec3.ZERO);
        };
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            z[0] = EntityType.PIG.create(h.getLevel());
            onStrand.run();
            z[0].setNoGravity(true);
            z[0].setNoAi(true);
            z[0].setPersistenceRequired();
            h.getLevel().addFreshEntity(z[0]);
        });
        h.onEachTick(() -> {
            if (z[0] != null && !z[0].hasEffect(MobEffects.POISON)) onStrand.run();
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(
                    z[0].hasEffect(MobEffects.POISON) && z[0].hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                    "touching a strand didn't sting");
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }
}
