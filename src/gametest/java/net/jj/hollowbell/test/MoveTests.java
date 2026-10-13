package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.entity.Shot;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;

/** Every move starts and ends, and how hard each one is. */
public class MoveTests implements FabricGameTest {
    /** the moves that must send a big moment (fx/BigFx) when they run */
    static final Set<Integer> BIG_MOMENTS = Set.of(
            Moves.SLAM,
            Moves.PULSE,
            Moves.DROP,
            Moves.SPORES,
            Moves.POD_BURST,
            Moves.EGG_RAIN,
            Moves.WHIRLPOOL,
            Moves.SKY_DIVE,
            Moves.DEEP_TOLL,
            Moves.ARM_STORM,
            Moves.SUN_LANCES,
            Moves.UNDERTOW);

    private static void moveRuns(GameTestHelper h, int move, int slot) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, slot);
        Pig[] p = new Pig[1];
        int len = Moves.length(move);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = pig(
                    h,
                    move == Moves.SLAM || move == Moves.WRAP || move == Moves.SWEEP
                            ? e.armTipWorld(0)
                                    .multiply(1, 0, 1)
                                    .add(0, e.groundAt(e.armTipWorld(0).x, e.armTipWorld(0).z), 0)
                            : under(e));
            p[0].setInvulnerable(true);
            e.setTarget(p[0]);
            h.assertTrue(e.forceMove(move, p[0]), "could not start " + Moves.NAMES[move]);
        });
        h.runAfterDelay(
                24,
                () -> h.assertTrue(e.moveNow() == move, Moves.NAMES[move] + " is not running (" + e.moveNow() + ")"));
        long[] from = {0};
        h.runAfterDelay(19, () -> from[0] = h.getLevel().getGameTime());
        h.runAfterDelay(20 + len + 20, () -> {
            h.assertTrue(e.moveNow() != move, Moves.NAMES[move] + " never finished");
            // the big ones are seen from far off: each sends its big moment (dust, splashes, flashes...) to the players
            // near
            if (BIG_MOMENTS.contains(move))
                h.assertTrue(
                        !BigFx.sentNear(e.position(), 400, from[0]).isEmpty(),
                        Moves.NAMES[move] + " sent no big moment");
            h.assertTrue(e.isAlive(), "he died doing " + Moves.NAMES[move]);
            e.moves().letGoOfEverything(false);
            p[0].discard();
            for (Belling b :
                    h.getLevel().getEntitiesOfClass(Belling.class, e.bodyBox().inflate(30))) b.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_grab")
    public void moveGrab(GameTestHelper h) {
        moveRuns(h, Moves.GRAB, 10);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_harvest")
    public void moveHarvest(GameTestHelper h) {
        moveRuns(h, Moves.HARVEST, 11);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_curtain")
    public void moveCurtain(GameTestHelper h) {
        moveRuns(h, Moves.CURTAIN, 12);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_sweep")
    public void moveSweep(GameTestHelper h) {
        moveRuns(h, Moves.SWEEP, 13);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_slam")
    public void moveSlam(GameTestHelper h) {
        moveRuns(h, Moves.SLAM, 14);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_wrap")
    public void moveWrap(GameTestHelper h) {
        moveRuns(h, Moves.WRAP, 15);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_pulse")
    public void movePulse(GameTestHelper h) {
        moveRuns(h, Moves.PULSE, 16);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "move_drop")
    public void moveDrop(GameTestHelper h) {
        moveRuns(h, Moves.DROP, 17);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_shed")
    public void moveShed(GameTestHelper h) {
        moveRuns(h, Moves.SHED, 18);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_volley")
    public void moveVolley(GameTestHelper h) {
        moveRuns(h, Moves.VOLLEY, 21);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_lash")
    public void moveLash(GameTestHelper h) {
        moveRuns(h, Moves.LASH, 22);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_flash")
    public void moveFlash(GameTestHelper h) {
        moveRuns(h, Moves.FLASH, 23);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_spores")
    public void moveSpores(GameTestHelper h) {
        moveRuns(h, Moves.SPORES, 24);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_podburst")
    public void movePodBurst(GameTestHelper h) {
        moveRuns(h, Moves.POD_BURST, 25);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "move_eggrain")
    public void moveEggRain(GameTestHelper h) {
        moveRuns(h, Moves.EGG_RAIN, 26);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_whirlpool")
    public void moveWhirlpool(GameTestHelper h) {
        moveRuns(h, Moves.WHIRLPOOL, 27);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "move_skydive")
    public void moveSkyDive(GameTestHelper h) {
        moveRuns(h, Moves.SKY_DIVE, 28);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_deeptoll")
    public void moveDeepToll(GameTestHelper h) {
        moveRuns(h, Moves.DEEP_TOLL, 29);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_armstorm")
    public void moveArmStorm(GameTestHelper h) {
        moveRuns(h, Moves.ARM_STORM, 33);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_stingerstorm")
    public void moveStingerStorm(GameTestHelper h) {
        moveRuns(h, Moves.STINGER_STORM, 34);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_sunlances")
    public void moveSunLances(GameTestHelper h) {
        moveRuns(h, Moves.SUN_LANCES, 35);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "move_undertow")
    public void moveUndertow(GameTestHelper h) {
        moveRuns(h, Moves.UNDERTOW, 36);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "tiers")
    public void everyMoveHasAStrength(GameTestHelper h) {
        int[] n = new int[3];
        for (int m : Moves.ORDER) n[Moves.tier(m)]++;
        h.assertTrue(Moves.ORDER.length == Moves.NAMES.length - 1, "the move order misses some: " + Moves.ORDER.length);
        h.assertTrue(Moves.NAMES.length - 1 >= 16, "fewer than 16 moves: " + (Moves.NAMES.length - 1));
        h.assertTrue(
                n[Moves.LIGHT] >= 4 && n[Moves.MEDIUM] >= 5 && n[Moves.HEAVY] >= 6,
                "light/medium/heavy: " + n[0] + "/" + n[1] + "/" + n[2]);
        for (int m = 1; m < Moves.NAMES.length; m++) {
            h.assertTrue(CodexOrders.windCost(CodexPayload.ATTACK_MOVE, m) > 0f, Moves.NAMES[m] + " costs no wind");
            if (Moves.tier(m) == Moves.HEAVY)
                h.assertTrue(
                        CodexOrders.windCost(CodexPayload.ATTACK_MOVE, m) >= 0.4f,
                        Moves.NAMES[m] + " is heavy but cheap");
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "tired")
    public void aHeavyMoveWearsHimOut(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 37);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            h.assertTrue(e.forceMove(Moves.DEEP_TOLL, null), "no deep toll");
        });
        h.runAfterDelay(20 + Moves.length(Moves.DEEP_TOLL) + 5, () -> {
            h.assertTrue(e.tired() && e.moves().isTired(), "not worn out after a heavy move");
            h.assertTrue(e.maxSpeed() < 0.5 * (0.10 + 0.16 * Math.sqrt(S)), "worn out but not slower: " + e.maxSpeed());
        });
        h.runAfterDelay(20 + Moves.length(Moves.DEEP_TOLL) + 130, () -> {
            h.assertTrue(!e.tired(), "still worn out long after");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "egg_rain_big")
    public void eggRainEggsAreBigMarkedAndHatch(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 325);
        Pig[] pig = new Pig[1];
        Set<Shot> landed = new HashSet<>();
        double[] worst = {0};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Shot.hatchChance = 1f;
            Vec3 u = under(e).add(14, 0, 0);
            pig[0] = pig(h, new Vec3(u.x, e.groundAt(u.x, u.z), u.z));
            pig[0].setInvulnerable(true);
            e.setTarget(pig[0]);
            h.assertTrue(e.forceMove(Moves.EGG_RAIN, pig[0]), "no egg rain");
        });
        // the wind-up: nothing has come off yet
        h.runAfterDelay(
                20 + Moves.EGG_AT - 5,
                () -> h.assertTrue(e.moves().lastEggs.isEmpty(), "eggs came off before the wind-up was over"));
        h.runAfterDelay(20 + Moves.EGG_AT + 30, () -> {
            var eggs = e.moves().lastEggs;
            h.assertTrue(eggs.size() >= 4, "too few eggs: " + eggs.size());
            for (var sh : eggs) {
                h.assertTrue(Math.abs(sh.size() - 0.3f) < 1e-4, "an egg isn't drawn at his size");
                Vec3 l = sh.landing();
                h.assertTrue(Math.abs(l.y - e.groundAt(l.x, l.z)) < 1.5, "a marker isn't on the ground: " + l);
                // the eggs spread over a square round the target (each way up to 6 x size + 2), so its corners count
                // too
                h.assertTrue(
                        Math.hypot(l.x - pig[0].getX(), l.z - pig[0].getZ()) < (6 * 0.3 + 2) * 1.415 + 1,
                        "a marker is off the target: " + l);
            }
        });
        // each egg comes down on its marker
        for (int k = 0; k < 120; k += 2) {
            h.runAfterDelay(20 + Moves.EGG_AT + k, () -> {
                for (var sh : e.moves().lastEggs) {
                    if (sh.stage() == Shot.FALLING || !landed.add(sh)) continue;
                    Vec3 l = sh.landing();
                    worst[0] = Math.max(worst[0], Math.hypot(sh.getX() - l.x, sh.getZ() - l.z));
                }
            });
        }
        h.runAfterDelay(20 + Moves.EGG_AT + 124, () -> {
            Shot.hatchChance = 0.65f;
            h.assertTrue(landed.size() >= 4, "the eggs never landed: " + landed.size());
            h.assertTrue(worst[0] < 3.2, "an egg came down off its marker by " + worst[0]);
            var bellings =
                    h.getLevel().getEntitiesOfClass(Belling.class, e.bodyBox().inflate(40));
            h.assertTrue(bellings.size() >= 3, "the eggs didn't hatch: " + bellings.size());
            for (var sh : e.moves().lastEggs) h.assertTrue(sh.isRemoved(), "a splat never went away");
            for (var b : bellings) b.discard();
            pig[0].discard();
            release(h, e);
            h.succeed();
        });
    }
}
