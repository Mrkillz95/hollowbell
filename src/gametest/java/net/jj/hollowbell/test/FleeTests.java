package net.jj.hollowbell.test;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.entity.BellFlee;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.Meeting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Backing down (JJ, Oct 27): never from a fight a player started, from a wild one only while /giants flee is on, and
 * when he does, he runs to his ground (or to whoever has his book), then rests and heals a little.
 */
public class FleeTests implements FabricGameTest {

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) {

        FabricGameTest.super.invokeTestMethod(context, method);
    }

    private static HollowbellEntity him(GameTestHelper h) {
        BellConfig.V.meetings = true;
        BellConfig.V.fightGiants = true;
        BellConfig.V.yieldAt = 0.25f;
        return HollowbellGameTests.spawnAway(h, 0.3f, HollowbellEntity.CALM, 951);
    }

    /** a zombie standing in for another giant: the giant tag and that giant's kind tag */
    private static Zombie fakeGiant(GameTestHelper h, Vec3 near, double x, double z, String kind) {
        Zombie f = EntityType.ZOMBIE.create(h.getLevel());
        f.setNoAi(true);
        f.setInvulnerable(true);
        f.setPersistenceRequired();
        f.addTag("jj_giant");
        f.addTag(Meeting.KIND + kind);
        Vec3 at = near.add(x, 0, z);
        f.moveTo(at.x, at.y, at.z, 0.0f, 0.0f);
        h.getLevel().addFreshEntity(f);
        return f;
    }

    /** the other giant hits he once, at a fifth of his health */
    private static void lowAndHit(HollowbellEntity e, Zombie z) {
        e.setHealthTo(e.healthMax() * 0.2f);
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().mobAttack(z), 5f);
    }

    /** the book's order to go after it */
    private static boolean book(HollowbellEntity e, Zombie z) {
        e.sendAfter(z);
        return true;
    }

    /** what /giants meet does */
    private static boolean meet(GameTestHelper h, HollowbellEntity e, Zombie z) {
        return e.startMeeting(z, true);
    }

    private static boolean backedDown(HollowbellEntity e) {
        return e.getTags().contains(Meeting.YIELD);
    }

    private static void gone(HollowbellEntity e, Zombie... zs) {
        e.discard();
        for (Zombie z : zs) z.discard();
    }

    /** a player with a name, standing at a spot in the test */
    static ServerPlayer player(GameTestHelper h, Vec3 at, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var p = new ServerPlayer(
                h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        var c = new Connection(PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "flee_off")
    public void fleeOffHeFightsAWildFightToTheEnd(GameTestHelper h) {
        boolean was = BellConfig.V.fleeInWildFights;
        HollowbellEntity e = him(h);
        Zombie z = fakeGiant(h, e.position(), 6, 6, "fire_ice_cerberus");
        h.runAfterDelay(20, () -> {
            try {
                BellConfig.V.fleeInWildFights = false;
                lowAndHit(e, z);
                h.assertFalse(backedDown(e), "with flee off he doesn't back down from a wild fight");
                h.assertTrue(!e.flee().running(), "and doesn't run");
            } catch (RuntimeException ex) {
                BellConfig.V.fleeInWildFights = was;
                gone(e, z);
                throw ex;
            }
        });
        // (a later tick: one giant's blows in the same tick count once)
        h.runAfterDelay(30, () -> {
            try {
                BellConfig.V.fleeInWildFights = true;
                lowAndHit(e, z);
                h.assertTrue(backedDown(e), "with flee on, the same blow makes he back down");
            } finally {
                BellConfig.V.fleeInWildFights = was;
                gone(e, z);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "flee_ordered")
    public void HeNeverBacksDownFromAFightAPlayerStarted(GameTestHelper h) {
        HollowbellEntity e = him(h);
        Zombie sent = fakeGiant(h, e.position(), 6, 6, "fire_ice_cerberus");
        Zombie met = fakeGiant(h, e.position(), -6, 6, "fire_ice_cerberus");
        Zombie wild = fakeGiant(h, e.position(), 6, -6, "fire_ice_cerberus");
        h.runAfterDelay(20, () -> {
            try {
                BellConfig.V.fleeInWildFights = true;
                // the book's order to go after it
                h.assertTrue(book(e, sent), "the order is taken");
                h.assertTrue(e.flee().playerFight(sent), "a book order is a player's fight");
                lowAndHit(e, sent);
                h.assertFalse(backedDown(e), "sent after it by the book, he doesn't back down at a fifth");
            } catch (RuntimeException ex) {
                gone(e, sent, met, wild);
                throw ex;
            }
        });
        h.runAfterDelay(30, () -> {
            try {
                // /giants meet
                h.assertTrue(meet(h, e, met), "/giants meet starts it");
                h.assertTrue(e.flee().playerFight(met), "/giants meet is a player's fight");
                lowAndHit(e, met);
                h.assertFalse(backedDown(e), "nor from one /giants meet started");
            } catch (RuntimeException ex) {
                gone(e, sent, met, wild);
                throw ex;
            }
        });
        h.runAfterDelay(40, () -> {
            try {
                // a wild one: he still backs down
                e.flee().orderedForTests(null);
                h.assertFalse(e.flee().playerFight(wild), "a wild fight isn't");
                lowAndHit(e, wild);
                h.assertTrue(backedDown(e), "from a wild one he still backs down");
            } finally {
                gone(e, sent, met, wild);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 460, batch = "flee_home")
    public void backingDownHeHeadsForHome(GameTestHelper h) {
        HollowbellEntity e = him(h);
        Zombie z = fakeGiant(h, e.position(), 6, 6, "fire_ice_cerberus");
        Vec3[] home = new Vec3[1];
        double[] start = new double[1];
        h.runAfterDelay(20, () -> {
            Vec3 at = e.position();
            home[0] = at.add(-300, 0, 40);
            e.flee().fleeSpotsForTests(null, home[0]);
            lowAndHit(e, z);
            h.assertTrue(backedDown(e) && e.flee().running(), "he backs down and runs");
            Vec3 to = e.flee().fleeTo();
            h.assertTrue(
                    to != null && Math.hypot(to.x - home[0].x, to.z - home[0].z) < 1.0,
                    "to the middle of his ground: " + to);
            start[0] = Math.hypot(at.x - home[0].x, at.z - home[0].z);
            // a ground too far off: as far as the cap toward it
            e.flee().fleeSpotsForTests(null, at.add(5000, 0, 0));
            e.flee().fleeTick(h.getLevel());
            Vec3 capped = e.flee().fleeTo();
            h.assertTrue(
                    capped != null && Math.abs(capped.x - at.x - BellFlee.FLEE_CAP) < 2.0,
                    "no further than " + BellFlee.FLEE_CAP + " toward it: " + capped);
            e.flee().fleeSpotsForTests(null, home[0]);
            e.flee().fleeTick(h.getLevel());
        });
        h.runAfterDelay(400, () -> {
            try {
                Vec3 at = e.position();
                double now = Math.hypot(at.x - home[0].x, at.z - home[0].z);
                h.assertTrue(now < start[0] - 1.0, "and gets nearer to it: " + start[0] + " -> " + now);
            } finally {
                gone(e, z);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "flee_owner")
    public void ownedHeRunsToWhoeverHasTheBook(GameTestHelper h) {
        HollowbellEntity e = him(h);
        Zombie z = fakeGiant(h, e.position(), 6, 6, "fire_ice_cerberus");
        ServerPlayer p = player(h, e.position().add(0, 1, 40), "flee-owner");
        h.runAfterDelay(20, () -> {
            try {
                Vec3 at = e.position();
                e.flee().fleeSpotsForTests(p.getUUID(), at.add(-300, 0, 0));
                lowAndHit(e, z);
                Vec3 to = e.flee().fleeTo();
                h.assertTrue(
                        to != null && Math.hypot(to.x - p.getX(), to.z - p.getZ()) < 1.0,
                        "owned, he runs to the owner: " + to + " (owner at " + p.position() + ")");
                // the owner leaves the game: home instead
                p.getServer().getPlayerList().remove(p);
                e.flee().fleeTick(h.getLevel());
                Vec3 home = e.flee().fleeTo();
                h.assertTrue(home != null && home.x < at.x - 200, "owner gone, he goes home: " + home);
            } finally {
                if (p.getServer().getPlayerList().getPlayer(p.getUUID()) == p)
                    p.getServer().getPlayerList().remove(p);
                gone(e, z);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 220, batch = "flee_rest")
    public void thereHeRestsAndHealsALittle(GameTestHelper h) {
        HollowbellEntity e = him(h);
        Zombie z = fakeGiant(h, e.position(), 6, 6, "fire_ice_cerberus");
        Zombie other = fakeGiant(h, e.position(), -6, -6, "fire_ice_cerberus");
        float[] hp = new float[1];
        h.runAfterDelay(20, () -> {
            e.flee().fleeSpotsForTests(null, e.position().add(300, 0, 0));
            lowAndHit(e, z);
            e.flee().restForTests(h.getLevel().getGameTime());
            h.assertTrue(e.flee().resting() && !e.flee().running(), "the run is over: he rests");
            hp[0] = e.healthNow();
        });
        h.runAfterDelay(105, () -> {
            float now = e.healthNow(), max = e.healthMax();
            h.assertTrue(now > hp[0], "resting, he heals: " + hp[0] + " -> " + now);
            h.assertTrue(backedDown(e), "still backed down: no giant fights him");
            h.assertTrue(e.meetFoe() == null, "and he starts nothing with a giant close by: " + e.meetFoe());
            e.setHealthTo(max * 0.495f);
        });
        h.runAfterDelay(190, () -> {
            try {
                float share = e.healthNow() / e.healthMax();
                h.assertTrue(share <= BellFlee.REST_HEAL_TO + 0.001f, "never past half: " + share);
                h.assertTrue(share >= 0.49f, "and he keeps what he has: " + share);
            } finally {
                gone(e, z, other);
            }
            h.succeed();
        });
    }

    private static CommandSourceStack op(GameTestHelper h) {
        return h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withPermission(4)
                .withSuppressedOutput();
    }

    private static int run(GameTestHelper h, CommandSourceStack src, String cmd) {
        try {
            return h.getLevel().getServer().getCommands().getDispatcher().execute(cmd, src);
        } catch (Exception e) {
            return -1;
        }
    }

    /** what the settings file on disk says */
    private static boolean saved() throws java.io.IOException {
        var p = FabricLoader.getInstance().getConfigDir().resolve("hollowbell.json");
        return JsonParser.parseString(Files.readString(p))
                .getAsJsonObject()
                .get("fleeInWildFights")
                .getAsBoolean();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "flee_cmd")
    public void giantsFleeIsForOpsAndIsSaved(GameTestHelper h) throws Exception {
        boolean was = BellConfig.V.fleeInWildFights;
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        CommandSourceStack op = op(h);
        CommandSourceStack anyone = op.withPermission(0);
        try {
            for (String c : new String[] {"giants flee", "giants flee off", "giants flee on hollowbell"}) {
                h.assertTrue(d.parse(c, anyone).getReader().canRead(), "/" + c + " works without cheats");
                var r = d.parse(c, op);
                h.assertTrue(!r.getReader().canRead() && r.getExceptions().isEmpty(), "/" + c + " doesn't parse");
            }
            h.assertTrue(run(h, op, "giants flee off") > 0, "/giants flee off failed");
            h.assertFalse(BellConfig.V.fleeInWildFights, "flee off wasn't set");
            h.assertFalse(saved(), "flee off wasn't saved");
            var said = GiantsCommand.ask(h.getLevel().getServer(), "flee", "");
            h.assertTrue(said.contains("Hollowbell: he fights wild fights to the end."), "bare flee says " + said);
            h.assertTrue(run(h, op, "giants flee on hollowbell") > 0, "/giants flee on hollowbell failed");
            h.assertTrue(BellConfig.V.fleeInWildFights && saved(), "flee on wasn't set and saved");
            said = GiantsCommand.ask(h.getLevel().getServer(), "flee", "");
            h.assertTrue(
                    said.contains("Hollowbell: he backs down from a wild fight he's losing."), "flee on says " + said);
        } finally {
            BellConfig.V.fleeInWildFights = was;
            BellConfig.save();
        }
        h.succeed();
    }
}
