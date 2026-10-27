package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;

/** The other giants: blows, carrying, meetings. */
public class GiantsTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "nocarry")
    public void heNeverPicksUpTheGiants(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 70);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            Vec3 at = under(e);
            Zombie z = EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(at.x, at.y, at.z, 0f, 0f);
            z.setNoAi(true);
            h.getLevel().addFreshEntity(z);
            Giant giant = EntityType.GIANT.create(h.getLevel());
            giant.moveTo(at.x + 3, at.y, at.z, 0f, 0f);
            giant.setNoAi(true);
            h.getLevel().addFreshEntity(giant);
            WitherBoss wither = EntityType.WITHER.create(h.getLevel());
            h.assertTrue(HollowbellEntity.canCarry(z), "a zombie can be picked up");
            h.assertFalse(HollowbellEntity.canCarry(giant), "a giant is too big to pick up");
            h.assertFalse(HollowbellEntity.canCarry(wither), "a boss can't be picked up");
            h.assertFalse(e.forceMove(Moves.GRAB, giant), "he doesn't try to grab the giant");
            h.assertFalse(e.forceMove(Moves.WRAP, giant), "or wrap it");
            z.discard();
            giant.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "bosshits")
    public void anotherGiantsBlowLandsInFull(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 71);
        h.runAfterDelay(20, () -> {
            Zombie z = EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            float before = e.healthNow();
            e.hurt(h.getLevel().damageSources().mobAttack(z), 50f);
            h.assertTrue(
                    before - e.healthNow() > 49f, "a creature's blow should land in full: " + (before - e.healthNow()));
            float before2 = e.healthNow();
            e.hurt(h.getLevel().damageSources().mobAttack(z), 1.0E6f);
            h.assertTrue(
                    e.healthNow() <= 0f || e.isDeadOrDying(),
                    "the Unmake's killing blow should kill him: " + e.healthNow() + " of " + before2);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_armour")
    public void aGiantsBlowLandsByHisArmourAgainstGiants(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 120);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            h.assertTrue(e.getTags().contains(Giants.TAG), "he isn't tagged as a giant");
            var g = fakeGiant(h, under(e));
            float before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(g), 100f);
            float took = before - e.healthNow();
            float want = 100f * BellConfig.V.giantArmor * Giants.fightPace(e.bellScale(), 1f);
            h.assertTrue(Math.abs(took - want) < 0.5f, "a giant's blow of 100 took " + took + ", not " + want);
            // a blast that isn't a giant's: a bit over a third
            before = e.healthNow();
            e.hurt(e.damageSources().explosion(null, null), 100f);
            took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 35f) < 0.5f, "a blast of 100 took " + took + ", not 35");
            // an ordinary zombie's blow still lands in full
            var z = EntityType.ZOMBIE.create(h.getLevel());
            z.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(z), 100f);
            took = before - e.healthNow();
            h.assertTrue(Math.abs(took - 100f) < 0.5f, "a zombie's blow of 100 took " + took);
            // a giant's small helper (a belling, a mudling, ...) counts as its giant
            var kin = EntityType.ZOMBIE.create(h.getLevel());
            kin.moveTo(e.getX(), e.getY(), e.getZ(), 0f, 0f);
            kin.addTag(Giants.KIN);
            float b3 = e.healthNow();
            e.hurt(e.damageSources().mobAttack(kin), 100f);
            float kinTook = b3 - e.healthNow();
            float kinWant = 100f * BellConfig.V.giantArmor * Giants.fightPace(e.bellScale(), 1f);
            h.assertTrue(
                    Math.abs(kinTook - kinWant) < 0.5f,
                    "a giant's helper's blow of 100 took " + kinTook + ", not " + kinWant);
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_nocarry")
    public void heNeverCarriesAGiant(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 121);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = fakeGiant(h, under(e));
            h.assertFalse(HollowbellEntity.canCarry(g), "a giant (even a small one) can't be picked up");
            h.assertFalse(e.forceMove(Moves.GRAB, g), "he doesn't try to grab it");
            h.assertFalse(e.forceMove(Moves.WRAP, g), "or wrap it");
            h.assertTrue(g.getVehicle() == null, "it got picked up anyway");
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_off")
    public void withGiantsOffHeLeavesThemAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.HUNTER, 122);
        h.runAfterDelay(20, () -> {
            var g = fakeGiant(h, under(e));
            boolean was = BellConfig.V.fightGiants;
            try {
                BellConfig.V.fightGiants = true;
                h.assertTrue(e.fairGame(g), "with giants on, another giant is fair game");
                BellConfig.V.fightGiants = false;
                h.assertFalse(e.fairGame(g), "with giants off, he leaves it alone");
            } finally {
                BellConfig.V.fightGiants = was;
            }
            g.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "pod_worth")
    public void aPoppedPodTakesOnePercent(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 123);
        h.runAfterDelay(20, () -> {
            h.assertTrue(
                    new BellConfig.Values().podRegrowSeconds == 150, "pods should take 150 s to start growing back");
            float before = e.healthNow();
            e.popPods(1);
            float took = before - e.healthNow();
            h.assertTrue(Math.abs(took - e.healthMax() * 0.01f) < 0.05f, "a pod took " + took + " of " + e.healthMax());
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "sweep_reach")
    public void hisSweepReachesAllTheWayOut(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 124);
        Husk[] z = new Husk[1];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            // out past the box round his body (112 at full size), where the strands still drag along the ground
            double d = e.bellRadius() * 1.2 + 4;
            double dx = e.getX() + d, dz = e.getZ();
            h.assertTrue(d > 112 * e.bellScale() + 2, "the test spot is inside his box anyway: " + d);
            z[0] = EntityType.HUSK.create(h.getLevel());
            z[0].moveTo(dx, e.groundAt(dx, dz), dz, 0f, 0f);
            z[0].setNoAi(true);
            z[0].setPersistenceRequired();
            h.getLevel().addFreshEntity(z[0]);
            e.setTarget(z[0]);
            h.assertTrue(e.forceMove(Moves.SWEEP, z[0]), "could not start the sweep");
        });
        h.runAfterDelay(20 + Moves.length(Moves.SWEEP) + 10, () -> {
            h.assertTrue(
                    z[0].getLastHurtByMob() == e || z[0].isDeadOrDying(),
                    "the sweep never reached the husk " + (int) e.horiz(z[0].position()) + " blocks out");
            z[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "giants_once")
    public void aGiantOfManyPartsHitsOnceATick(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 136);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            var g = fakeGiant(h, under(e));
            float armour = BellConfig.V.giantArmor * Giants.fightPace(e.bellScale(), 1f);
            float before = e.healthNow();
            // four parts of one giant land at once
            for (int i = 0; i < 4; i++) e.hurt(e.damageSources().mobAttack(g), 100f);
            float took = before - e.healthNow();
            h.assertTrue(
                    Math.abs(took - 100f * armour) < 0.5f,
                    "four parts' blows of 100 in one tick took " + took + ", not " + 100f * armour);
            // a bigger one the same tick: only what it's over the first
            before = e.healthNow();
            e.hurt(e.damageSources().mobAttack(g), 150f);
            took = before - e.healthNow();
            h.assertTrue(
                    Math.abs(took - 50f * armour) < 0.5f,
                    "a bigger blow the same tick took " + took + ", not " + 50f * armour);
            float[] next = {e.healthNow()};
            h.runAfterDelay(1, () -> {
                e.hurt(e.damageSources().mobAttack(g), 100f);
                float t2 = next[0] - e.healthNow();
                h.assertTrue(Math.abs(t2 - 100f * armour) < 0.5f, "the next tick's blow took " + t2);
                g.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    /**
     * Another giant made of part boxes (its middle far off, one part right beside him) is struck through the part: his
     * pulse wave reaches the part and the giant it belongs to is hurt. A part deep under the ground is out of reach.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "giant_parts")
    public void heStrikesAnotherGiantThroughItsParts(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 148);
        Zombie[] g = new Zombie[2];
        FakePart[] part = new FakePart[2];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            double far = e.bellRadius() * 3 + 40;
            // one giant with a part on the ground beside him, one with its only part deep under the ground
            for (int i = 0; i < 2; i++) {
                g[i] = fakeGiant(h, e.position().add(i == 0 ? far : -far, 0, 0));
                g[i].setHealth(20f);
                part[i] = new FakePart(h.getLevel(), g[i]);
                double x = e.getX() + (i == 0 ? 1 : -1) * e.bellRadius() * 1.1, z = e.getZ();
                part[i].moveTo(x, e.groundAt(x, z) - (i == 0 ? 0 : 12), z, 0f, 0f);
                h.getLevel().addFreshEntity(part[i]);
                h.assertTrue(Giants.ownerOf(part[i]) == g[i], "the part's giant wasn't found");
            }
            e.setTarget(g[0]); // the giant he's fighting (his blows leave other giants alone)
            h.assertTrue(e.forceMove(Moves.PULSE, g[0]), "no pulse wave");
        });
        h.runAfterDelay(20 + Moves.length(Moves.PULSE) + 10, () -> {
            h.assertTrue(
                    g[0].getHealth() < 20f || g[0].isDeadOrDying(),
                    "the pulse wave never struck the giant through its part");
            h.assertTrue(g[1].getHealth() >= 20f, "a part deep under the ground was struck");
            for (int i = 0; i < 2; i++) {
                part[i].discard();
                g[i].discard();
            }
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_table")
    public void theMeetingTable(GameTestHelper h) {
        String[] k = {
            "mountain_breathes",
            "furrowmaw",
            "fire_ice_cerberus",
            "hollowbell",
            "lanternwillow",
            "wreckback",
            "swarmforge",
            "gloamwick"
        };
        String[] want = {"-ffaafff", "f-fafffa", "ff-fffff", "aaf-aaaa", "affa-aff", "fffaa-aa", "fffafa-f", "fafafaf-"
        };
        for (int a = 0; a < 8; a++)
            for (int b = 0; b < 8; b++) {
                var w = Meeting.way(k[a], k[b]);
                char c = want[a].charAt(b);
                var expect = c == 'f' ? Meeting.Way.FIGHT : c == 'a' ? Meeting.Way.AVOID : Meeting.Way.NONE;
                h.assertTrue(w == expect, k[a] + " meeting " + k[b] + ": " + w);
                h.assertTrue(
                        w == Meeting.way(k[b], k[a]), "the table isn't the same both ways for " + k[a] + ", " + k[b]);
            }
        h.assertTrue(Meeting.way("hollowbell", "minecraft") == Meeting.Way.NONE, "an unknown kind");
        h.assertTrue(Meeting.name("fire_ice_cerberus", false).equals("the Cerberus"), "names");
        h.assertTrue(
                Meeting.name("swarmforge", true).equals("The Swarmforge"),
                "his name: " + Meeting.name("swarmforge", true));
        h.assertTrue(Meeting.range(1f) == 160 && Meeting.range(0.1f) == 48 && Meeting.range(2f) == 320, "the range");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_table")
    public void theFightLineIsSaidOnce(GameTestHelper h) {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        long t = 1_000_000L + h.getLevel().getGameTime();
        h.assertTrue(Meeting.claimMeetingLine(a, b, t), "the first claim didn't win");
        h.assertFalse(Meeting.claimMeetingLine(b, a, t), "the other one said it too");
        h.assertFalse(Meeting.claimMeetingLine(a, b, t + 6000), "said again too soon");
        h.assertTrue(Meeting.claimMeetingLine(b, a, t + 6001), "not said again after 6001 ticks");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_fight")
    public void heFightsAGiantTheTableSaysToFight(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 326);
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.getTags().contains(Meeting.KIND + "hollowbell"), "he has no kind tag");
            var z = giantOfKind(h, under(e).add(20, 0, 0), "fire_ice_cerberus");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.getTarget() == z, "he didn't go for the Cerberus: " + e.getTarget());
                h.assertTrue(z.getUUID().equals(e.meetFoe()), "it isn't a meeting");
                h.assertTrue(e.meetCooldownLeft(z.getUUID()) > 20 * 60 * 9, "no wait before they meet again");
                z.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "meet_avoid")
    public void heKeepsAwayFromAGiantTheTableSaysToAvoid(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 327);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(16, 0, 0), "mountain_breathes");
            double d0 = e.horiz(z.position());
            h.runAfterDelay(160, () -> {
                h.assertTrue(e.getTarget() != z, "he went for Pitchgut");
                h.assertTrue(
                        z.getUUID().equals(e.avoiding()) || e.horiz(z.position()) > Meeting.range(0.3f) * 1.5,
                        "he isn't keeping away");
                h.assertTrue(
                        e.horiz(z.position()) > d0 + 3, "he didn't move away: " + d0 + " -> " + e.horiz(z.position()));
                // hit by it, he still hits back
                e.hurt(e.damageSources().mobAttack(z), 5f);
                h.assertTrue(e.getTarget() == z, "he didn't hit back");
                z.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_yield")
    public void broughtLowByAGiantHeBacksDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 328);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            e.startMeeting(z);
            e.setHealthTo(e.healthMax() * 0.2f);
            e.hurt(e.damageSources().mobAttack(z), 5f);
            h.assertTrue(e.yielding() && e.getTags().contains("jj_giant_yield"), "he didn't back down");
            h.assertTrue(e.getTarget() != z && e.meetFoe() == null, "he's still after it");
            e.hurt(e.damageSources().mobAttack(z), 5f);
            h.assertTrue(e.getTarget() != z, "he hit back while backing down");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.getTarget() != z, "he went back for it");
                h.assertTrue(e.horiz(z.position()) > 12, "he isn't getting away from it");
                // and a fresh one, backing down, is left alone by him too: no giant is his while he yields
                var z2 = giantOfKind(h, under(e).add(-10, 0, 0), "fire_ice_cerberus");
                h.runAfterDelay(50, () -> {
                    h.assertTrue(e.getTarget() != z2, "backing down, he went for another giant");
                    z.discard();
                    z2.discard();
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_won")
    public void aGiantThatBacksDownIsLeftAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 329);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            h.assertTrue(e.startMeeting(z), "the meeting didn't start");
            z.addTag("jj_giant_yield");
            h.runAfterDelay(10, () -> {
                h.assertTrue(e.meetFoe() == null && e.getTarget() != z, "he kept fighting one that backed down");
                h.runAfterDelay(60, () -> {
                    h.assertTrue(e.getTarget() != z, "he picked it again");
                    z.discard();
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "meet_cooldown")
    public void heKeepsAwayAgainWhenAGiantHeAvoidsComesBack(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 330);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(16, 0, 0), "lanternwillow");
            h.runAfterDelay(60, () -> {
                h.assertTrue(z.getUUID().equals(e.avoiding()), "he never noticed the Willow");
                // it goes well off, and he stops keeping away
                Vec3 far = e.position().add(100, 0, 0);
                h.getLevel().setChunkForced(Mth.floor(far.x) >> 4, Mth.floor(far.z) >> 4, true);
                z.teleportTo(far.x, z.getY(), far.z);
                h.runAfterDelay(20, () -> {
                    h.assertTrue(e.avoiding() == null, "he is still keeping away from one far off");
                    // it comes back: keeping away has no wait, so he keeps away from it again (and never fights it)
                    Vec3 near = e.position().add(12, 0, 0);
                    z.teleportTo(near.x, z.getY(), near.z);
                    h.runAfterDelay(100, () -> {
                        h.assertTrue(
                                z.getUUID().equals(e.avoiding()), "he let a giant he keeps away from come back close");
                        h.assertTrue(e.getTarget() != z, "he went for a giant he keeps away from");
                        h.assertTrue(e.meetCooldownLeft(z.getUUID()) == 0, "keeping away set a wait");
                        h.getLevel().setChunkForced(Mth.floor(far.x) >> 4, Mth.floor(far.z) >> 4, false);
                        z.discard();
                        release(h, e);
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "meet_commands")
    public void theMeetingCommandsWork(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 334);
        var server = h.getLevel().getServer();
        h.runAfterDelay(20, () -> {
            var src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, "hollowbell meetings off");
            h.assertTrue(!BellConfig.V.meetings, "/hollowbell meetings off didn't");
            server.getCommands().performPrefixedCommand(src, "giants meetings on");
            h.assertTrue(BellConfig.V.meetings, "/giants meetings on didn't");
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetRange 200");
            h.assertTrue(BellConfig.V.meetRange == 200, "meetRange can't be set");
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetRange 160");
            // /giants meet asks each giant's mod with the other's id (and its own)
            var z = giantOfKind(h, under(e).add(10, 0, 0), "furrowmaw");
            var said = GiantsBridge.giants(server, "meet", z.getUUID() + " " + e.getUUID());
            h.assertTrue(said != null && !said.isEmpty(), "the bridge didn't answer meet");
            h.assertTrue(
                    z.getUUID().equals(e.meetFoe()) && e.getTarget() == z,
                    "meet didn't start a fight (whatever the table says)");
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    /** his strands' stings and blows leave a giant he isn't fighting alone (else a pair meant to keep apart fights) */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fighting_giant")
    public void hisStingsLeaveAGiantHeIsNotFightingAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 361);
        h.runAfterDelay(10, () -> {
            var z = fakeGiant(h, e.position().add(20, 0, 0));
            z.addTag(Meeting.KIND + "lanternwillow");
            float was = z.getHealth();
            h.assertFalse(e.fightingGiant(z), "a giant drifting past counts as one he's fighting");
            e.moves().sting(z);
            h.assertTrue(
                    z.getHealth() == was, "his sting hurt a giant he isn't fighting: " + z.getHealth() + " of " + was);
            // once it's the one he's going for, it's fair game
            e.setTarget(z);
            h.assertTrue(e.fightingGiant(z), "the giant he's going for doesn't count");
            z.invulnerableTime = 0;
            e.moves().sting(z);
            h.assertTrue(z.getHealth() < was, "his sting didn't touch the giant he's going for");
            e.setTarget(null);
            z.discard();
            release(h, e);
            h.succeed();
        });
    }
}
