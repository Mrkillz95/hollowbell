package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.force;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBehave;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Part 2C's meetGiants (ADMIN_BRIDGE.md 21.1): decide is the old table exactly; fight, run_away and ignore do what they
 * say for a wild meeting; run_away backs down at the first blow of a wild fight only; ignore and run_away never go
 * looking. The world's value is set the real way (the bridge's adminset) and cleared at the end of each test.
 */
public class MeetGiantsTests implements FabricGameTest {

    private static final String[] KINDS = {
        "mountain_breathes", "furrowmaw", "fire_ice_cerberus", "hollowbell",
        "lanternwillow", "wreckback", "swarmforge", "gloamwick"
    };

    private static void set(GameTestHelper h, String values) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{" + values + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    private static JsonObject line(String id) {
        for (String l : GiantsAdmin.describe())
            if (l.contains("\"id\":\"" + id + "\"")) return JsonParser.parseString(l).getAsJsonObject();
        return null;
    }

    /** a stand-in for another giant: the giant tag and that giant's kind tag */
    private static Mob fakeGiant(GameTestHelper h, Vec3 at, String kind) {
        // (a cow: a hostile stand-in is gone the moment another test turns the world to Peaceful)
        Mob f = EntityType.COW.create(h.getLevel());
        f.moveTo(at.x, at.y, at.z, 0f, 0f);
        f.setNoAi(true);
        f.setInvulnerable(true);
        f.setPersistenceRequired();
        f.addTag("jj_giant");
        f.addTag(Meeting.KIND + kind);
        h.getLevel().addFreshEntity(f);
        return f;
    }

    private static boolean backedDown(HollowbellEntity e) {
        return e.getTags().contains(Meeting.YIELD);
    }

    /** the other giant lands one small blow on him at full health */
    private static void firstBlow(HollowbellEntity e, Mob by) {
        e.setHealthTo(e.healthMax());
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().mobAttack(by), 5f);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_row")
    public void theRowIsThereAfterSeekGiantsAndDecideAsShipped(GameTestHelper h) {
        JsonObject l = line("meetGiants");
        h.assertTrue(l != null, "no meetGiants row");
        h.assertTrue(l.get("cat").getAsString().equals("behaviour") && l.get("type").getAsString().equals("choice")
                && l.get("when").getAsString().equals("live"), "the row: " + l);
        h.assertTrue(l.get("options").toString().equals("[\"decide\",\"fight\",\"run_away\",\"ignore\"]"),
                "the options: " + l.get("options"));
        h.assertTrue(l.get("jar").getAsString().equals("decide") && l.get("value").getAsString().equals("decide"),
                "the jar: " + l);
        h.assertFalse(l.get("set").getAsBoolean(), "set");
        List<String> all = GiantsAdmin.describe();
        int seek = -1, meet = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).contains("\"id\":\"seekGiants\"")) seek = i;
            if (all.get(i).contains("\"id\":\"meetGiants\"")) meet = i;
        }
        h.assertTrue(seek >= 0 && meet == seek + 1, "right after seekGiants: " + seek + ", " + meet);
        h.assertTrue(new BellConfig.Values().meetGiants.equals("decide") && GiantsBehave.meets().equals("decide"),
                "decide as shipped");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_decide")
    public void decideIsTheOldTableForEveryKind(GameTestHelper h) {
        try {
            for (boolean seek : new boolean[] {false, true}) {
                set(h, "\"seekGiants\":" + seek);
                for (String k : KINDS) {
                    Meeting.Way old = Meeting.way(Meeting.ME, k);
                    Meeting.Way want = seek && old == Meeting.Way.AVOID ? Meeting.Way.FIGHT : old;
                    h.assertTrue(Meeting.wayNow(k) == want, "decide, seek " + seek + ", " + k + ": "
                            + Meeting.wayNow(k) + " not " + want);
                }
            }
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_ways")
    public void fightRunAwayAndIgnoreForAKindHeFightsAndOneHeKeepsFrom(GameTestHelper h) {
        // (he fights the Cerberus and keeps away from Wreckback)
        h.assertTrue(Meeting.way(Meeting.ME, "fire_ice_cerberus") == Meeting.Way.FIGHT
                && Meeting.way(Meeting.ME, "wreckback") == Meeting.Way.AVOID, "the table");
        try {
            Object[][] want = {
                {"fight", Meeting.Way.FIGHT}, {"run_away", Meeting.Way.AVOID}, {"ignore", Meeting.Way.NONE}
            };
            for (Object[] w : want) {
                set(h, "\"meetGiants\":\"" + w[0] + "\"");
                for (String k : new String[] {"fire_ice_cerberus", "wreckback"})
                    h.assertTrue(Meeting.wayNow(k) == w[1], w[0] + ", " + k + ": " + Meeting.wayNow(k));
                h.assertTrue(Meeting.wayNow("hollowbell") == Meeting.Way.NONE, w[0] + ": his own kind, nothing");
                h.assertTrue(Meeting.wayNow(null) == Meeting.Way.NONE, w[0] + ": not a giant, nothing");
            }
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "meet_run_wild")
    public void runAwayBacksDownAtTheFirstBlowOfAWildFight(GameTestHelper h) {
        BellConfig.V.meetings = true;
        BellConfig.V.fightGiants = true;
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 1501);
        Mob z = fakeGiant(h, e.position().add(6, 0, 6), "fire_ice_cerberus");
        h.runAfterDelay(20, () -> {
            try {
                firstBlow(e, z);
                h.assertFalse(backedDown(e), "decide: one small blow at full health doesn't back him down");
            } catch (RuntimeException ex) {
                z.discard();
                release(h, e);
                throw ex;
            }
        });
        // (a later tick: one giant's blows in the same tick count once)
        h.runAfterDelay(30, () -> {
            try {
                set(h, "\"meetGiants\":\"run_away\"");
                firstBlow(e, z);
                h.assertTrue(backedDown(e), "run_away: the first blow of a wild fight backs him down");
            } finally {
                GiantsAdmin.clear();
                z.discard();
                release(h, e);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "meet_run_player")
    public void runAwayDoesntBackDownFromAPlayersFight(GameTestHelper h) {
        BellConfig.V.meetings = true;
        BellConfig.V.fightGiants = true;
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 1502);
        Mob met = fakeGiant(h, e.position().add(6, 0, 6), "fire_ice_cerberus");
        Mob sent = fakeGiant(h, e.position().add(-6, 0, 6), "wreckback");
        h.runAfterDelay(20, () -> {
            try {
                set(h, "\"meetGiants\":\"run_away\"");
                // /giants meet
                h.assertTrue(e.startMeeting(met, true), "/giants meet starts it");
                firstBlow(e, met);
                h.assertFalse(backedDown(e), "run_away: not from a fight /giants meet started");
            } catch (RuntimeException ex) {
                GiantsAdmin.clear();
                met.discard();
                sent.discard();
                release(h, e);
                throw ex;
            }
        });
        h.runAfterDelay(30, () -> {
            try {
                // the book's order to go after it
                e.sendAfter(sent);
                firstBlow(e, sent);
                h.assertFalse(backedDown(e), "run_away: not from one the book sent him at");
            } finally {
                GiantsAdmin.clear();
                met.discard();
                sent.discard();
                release(h, e);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_seek")
    public void ignoreAndRunAwayDontSeek(GameTestHelper h) {
        BellConfig.V.meetings = true;
        BellConfig.V.fightGiants = true;
        HollowbellEntity e = spawnAway(h, HollowbellGameTests.S, HollowbellEntity.CALM, 1503);
        e.setStay(false);
        Vec3 at = new Vec3(Math.floor(e.getX() + 90) + 0.5, e.getY(), Math.floor(e.getZ()) + 0.5);
        force(h, (int) at.x, (int) at.z, 1, true);
        HollowbellGameTests.runsEntities(h, (int) at.x, (int) at.z, 1);
        int y = h.getLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(at.x), (int) Math.floor(at.z));
        Mob other = fakeGiant(h, new Vec3(at.x, y, at.z), "wreckback");
        h.runAfterDelay(5, () -> {
            try {
                for (String m : new String[] {"ignore", "run_away", "fight"}) {
                    set(h, "\"seekGiants\":true,\"meetGiants\":\"" + m + "\"");
                    e.setGoal(null);
                    e.clearWandering();
                    e.roamLook(h.getLevel());
                    Vec3 g = e.wandering();
                    boolean went = g != null && Math.hypot(g.x - other.getX(), g.z - other.getZ()) < 5;
                    if (m.equals("fight")) {
                        h.assertTrue(e.freeToSeek(), "fight: free to seek");
                        h.assertTrue(went, "fight: he goes looking: " + g);
                    } else {
                        h.assertFalse(e.freeToSeek(), m + ": not free to seek");
                        h.assertFalse(went, m + ": he went looking: " + g);
                    }
                }
            } finally {
                GiantsAdmin.clear();
                force(h, (int) at.x, (int) at.z, 1, false);
                other.discard();
                release(h, e);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_clamp")
    public void anUnknownOptionClampsToDecide(GameTestHelper h) {
        BellConfig.Values v = new BellConfig.Values();
        v.meetGiants = "angry";
        BellConfig.migrate(v);
        h.assertTrue(v.meetGiants.equals("decide"), "an unknown option: " + v.meetGiants);
        v.meetGiants = " Run_Away ";
        BellConfig.migrate(v);
        h.assertTrue(v.meetGiants.equals("run_away"), "any case: " + v.meetGiants);
        v.meetGiants = null;
        BellConfig.migrate(v);
        h.assertTrue("decide".equals(v.meetGiants), "none: " + v.meetGiants);
        try {
            List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                    "{\"admin\":1,\"values\":{\"meetGiants\":\"angry\"}}");
            h.assertFalse(r != null && r.equals(List.of("ok")), "adminset took a wrong option: " + r);
            h.assertTrue(BellConfig.V.meetGiants.equals("decide"), "still decide: " + BellConfig.V.meetGiants);
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_config")
    public void theConfigCommandTakesIt(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        try {
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetGiants fight allworlds");
            h.assertTrue(BellConfig.V.meetGiants.equals("fight"), "config meetGiants fight: " + BellConfig.V.meetGiants);
            h.assertTrue(Meeting.wayNow("wreckback") == Meeting.Way.FIGHT, "and he fights");
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetGiants nonsense allworlds");
            h.assertTrue(BellConfig.V.meetGiants.equals("fight"), "a wrong option is refused");
        } finally {
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetGiants decide allworlds");
        }
        h.assertTrue(BellConfig.V.meetGiants.equals("decide"), "back");
        h.succeed();
    }
}
