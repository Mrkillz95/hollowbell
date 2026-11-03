package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.force;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.AdminScope;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBehave;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Part 4's behaviour settings (ADMIN_BRIDGE.md 19): as shipped each changes nothing, and each value does what it says.
 * The world's values are set the real way (the bridge's adminset) and cleared at the end of each test; every test has
 * a batch of its own, so no two run at once.
 */
public class BehaviourTests implements FabricGameTest {

    private static void set(GameTestHelper h, String values) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{" + values + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    private static Vec3 spot(HollowbellEntity e, double dx, double dz) {
        double x = Math.floor(e.getX() + dx) + 0.5, z = Math.floor(e.getZ() + dz) + 0.5;
        int y = e.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(x), (int) Math.floor(z));
        return new Vec3(x, y, z);
    }

    private static void finish(GameTestHelper h, HollowbellEntity e, Entity... others) {
        GiantsAdmin.clear();
        for (Entity o : others) {
            if (o instanceof ServerPlayer p) drop(p);
            else if (o != null) o.discard();
        }
        release(h, e);
        h.succeed();
    }

    private static JsonObject line(String id) {
        for (String l : GiantsAdmin.describe())
            if (l.contains("\"id\":\"" + id + "\"")) return JsonParser.parseString(l).getAsJsonObject();
        return null;
    }

    // the rows, and the defaults changing nothing

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "beh_rows")
    public void theTenRowsAreThereAndAsShippedChangeNothing(GameTestHelper h) {
        String[][] want = {
            {"spawnSize", "spawning", "range", "newSpawns"}, {"worldCount", "spawning", "int", "newSpawns"},
            {"respawn", "spawning", "bool", "live"}, {"temper", "behaviour", "choice", "live"},
            {"roam", "behaviour", "choice", "live"}, {"seekGiants", "behaviour", "bool", "live"},
            {"raidVillages", "behaviour", "bool", "live"}, {"sleepSchedule", "behaviour", "choice", "live"},
            {"nightStrength", "stats", "float", "live"}, {"lootMultiplier", "loot", "float", "live"}
        };
        for (String[] w : want) {
            JsonObject l = line(w[0]);
            h.assertTrue(l != null, "no row " + w[0]);
            h.assertTrue(l.get("cat").getAsString().equals(w[1]) && l.get("type").getAsString().equals(w[2])
                    && l.get("when").getAsString().equals(w[3]), w[0] + ": " + l);
            h.assertFalse(l.get("set").getAsBoolean(), w[0] + " set");
        }
        h.assertTrue(line("temper").get("options").toString().equals("[\"peaceful\",\"normal\",\"hunter\"]")
                && line("temper").get("jar").getAsString().equals("normal"), "temper's options");
        h.assertTrue(line("roam").get("jar").getAsString().equals("home")
                && line("sleepSchedule").get("jar").getAsString().equals("usual"), "roam and sleep jars");
        h.assertTrue(line("spawnSize").get("jar").toString().equals("[1.0,1.0]"), "spawnSize jar "
                + line("spawnSize").get("jar"));
        h.assertTrue(line("worldScale").get("min").getAsDouble() == 0.01, "the size floor");
        h.assertTrue(HollowbellEntity.MIN_SCALE == 0.01f, "his smallest size");
        BellConfig.Values v = new BellConfig.Values();
        h.assertTrue(v.worldCount == 1 && v.respawn && !v.seekGiants && !v.raidVillages && v.nightStrength == 1f
                && v.lootMultiplier == 1f && v.spawnSizeMin == 1f && v.spawnSizeMax == 1f, "the jar's values");
        // nothing drawn, nothing changed
        RandomSource a = new LegacyRandomSource(7), b = new LegacyRandomSource(7);
        h.assertTrue(GiantsBehave.spawnScale(a) == BellConfig.V.worldScale, "spawn size is worldScale");
        h.assertTrue(a.nextLong() == b.nextLong(), "no random number drawn for the spawn size");
        List<ItemStack> loot = new ArrayList<>(List.of(new ItemStack(Items.DIAMOND, 3)));
        h.assertTrue(GiantsBehave.loot(loot, a) == loot, "the same loot list");
        h.assertTrue(GiantsBehave.nightHit(h.getLevel(), 7.25f) == 7.25f, "the same hit");
        h.assertFalse(GiantsBehave.peaceful() || GiantsBehave.hunterTemper() || GiantsBehave.wanders()
                || GiantsBehave.stays() || GiantsBehave.neverSleeps() || GiantsBehave.daySleeper(h.getLevel())
                || GiantsBehave.huntsNow(h.getLevel()), "every switch off");
        h.assertTrue(Meeting.wayNow("wreckback") == Meeting.Way.AVOID, "the meeting table as it was");
        h.assertTrue(Meeting.wayNow("fire_ice_cerberus") == Meeting.Way.FIGHT, "the meeting table as it was");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "beh_values")
    public void choicesAndRangesGoOnTopAndBack(GameTestHelper h) {
        try {
            set(h, "\"temper\":\"HUNTER\",\"roam\":2,\"spawnSize\":[1.5,0.5],\"sleepSchedule\":\"day\"");
            h.assertTrue(BellConfig.V.temper.equals("hunter"), "an option, any case: " + BellConfig.V.temper);
            h.assertTrue(BellConfig.V.roam.equals("stay"), "an option by its place: " + BellConfig.V.roam);
            h.assertTrue(BellConfig.V.spawnSizeMin == 0.5f && BellConfig.V.spawnSizeMax == 1.5f,
                    "a range, put in order");
            h.assertTrue(line("spawnSize").get("value").toString().equals("[0.5,1.5]"), "listed as a range");
            h.assertTrue(BellConfig.fileValues().temper.equals("normal"), "the file keeps its own");
            List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                    "{\"admin\":1,\"values\":{\"temper\":\"angry\",\"spawnSize\":\"big\"}}");
            h.assertTrue(r.size() == 3, "a choice that isn't one and a range that isn't one are refused: " + r);
            h.assertTrue(AdminScope.parse("temper", "Peaceful").equals("peaceful"), "/config takes the option");
            h.assertTrue(AdminScope.parse("temper", "angry") == null, "and refuses a wrong one");
            float[] rg = (float[]) AdminScope.parse("spawnSize", "2-0.5");
            h.assertTrue(rg[0] == 0.5f && rg[1] == 2f, "and a range");
        } finally {
            GiantsAdmin.clear();
        }
        h.assertTrue(BellConfig.V.temper.equals("normal") && BellConfig.V.spawnSizeMax == 1f, "back");
        h.succeed();
    }

    // temper

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "beh_peaceful")
    public void aPeacefulHunterLeavesYouBeUntilYouHitHim(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 1400);
        set(h, "\"temper\":\"peaceful\"");
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(20, () -> p[0] = player(h, spot(e, 0, 40)));
        h.runAfterDelay(100, () -> {
            h.assertTrue(e.getTarget() != p[0], "a peaceful one picked a player he only saw");
            e.hurt(e.damageSources().playerAttack(p[0]), 4f);
        });
        h.runAfterDelay(160, () -> {
            h.assertTrue(e.getTarget() == p[0], "he didn't fight back: " + e.getTarget());
            finish(h, e, p[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "beh_hunter")
    public void aHunterTemperMakesACalmOneHunt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1401);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(20, () -> p[0] = player(h, spot(e, 0, 40)));
        h.runAfterDelay(80, () -> {
            h.assertTrue(e.getTarget() == null, "a calm one picked a player as shipped");
            set(h, "\"temper\":\"hunter\"");
        });
        h.runAfterDelay(140, () -> {
            h.assertTrue(e.getTarget() == p[0], "a calm one with a hunter's temper didn't hunt: " + e.getTarget());
            finish(h, e, p[0]);
        });
    }

    // sleep

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "beh_day_sleeper")
    public void aDaySleeperSleepsByDayAndHuntsAtNight(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        long was = l.getDayTime();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1402);
        l.setDayTime(1000);
        set(h, "\"sleepSchedule\":\"day\"");
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(240, () -> {
            h.assertTrue(e.asleep(), "a day sleeper wasn't asleep ten quiet seconds into the day");
            l.setDayTime(14000);
        });
        h.runAfterDelay(300, () -> {
            h.assertFalse(e.asleep(), "he didn't get up at dusk");
            p[0] = player(h, spot(e, 0, 40));
        });
        h.runAfterDelay(400, () -> {
            boolean hunts = e.getTarget() == p[0];
            l.setDayTime(was);
            h.assertTrue(hunts, "a calm day sleeper didn't hunt at night: " + e.getTarget());
            finish(h, e, p[0]);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "beh_never_sleeps")
    public void neverMeansHeNeverLiesDownButUsualDoes(GameTestHelper h) {
        int was = HollowbellEntity.quietBeforeSleep;
        HollowbellEntity.quietBeforeSleep = 60;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1403);
        set(h, "\"sleepSchedule\":\"never\"");
        h.runAfterDelay(200, () -> {
            if (e.asleep()) HollowbellEntity.quietBeforeSleep = was;
            h.assertFalse(e.asleep(), "he lay down with never");
            set(h, "");
        });
        h.runAfterDelay(320, () -> {
            HollowbellEntity.quietBeforeSleep = was;
            h.assertTrue(e.asleep(), "as usual he lies down when it's quiet");
            finish(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "beh_night_strength")
    public void atNightHisHitsAreTimesNightStrength(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        long was = l.getDayTime();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1404);
        ServerPlayer p = player(h, spot(e, 0, 40));
        float[] day = new float[2];
        // (the level works out whether it is day once a tick)
        l.setDayTime(1000);
        h.runAfterDelay(3, () -> {
            day[0] = e.dmg(10f, p);
            set(h, "\"nightStrength\":2");
            day[1] = e.dmg(10f, p);
            l.setDayTime(14000);
        });
        h.runAfterDelay(6, () -> {
            float night = e.dmg(10f, p);
            l.setDayTime(was);
            h.assertTrue(day[0] == day[1], "by day nothing changes: " + day[0] + " " + day[1]);
            h.assertTrue(Math.abs(night - 2 * day[0]) < 1e-4, "at night twice: " + day[0] + " " + night);
            finish(h, e, p);
        });
    }

    // roaming

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "beh_wander")
    public void aWandererMakesForSomewhereFar(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1405);
        e.setStay(false);
        set(h, "\"roam\":\"wander\"");
        h.runAfterDelay(240, () -> {
            Vec3 g = e.wandering();
            h.assertTrue(g != null && e.horiz(g) > 250, "no far spot: " + g);
            h.assertTrue(e.goal() == null, "a far spot isn't an order");
            finish(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "beh_stay")
    public void aStayerPicksNoSpotOfHisOwn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.GUARDIAN, 1406);
        e.setStay(false);
        // (a guardian away from his post: as shipped he'd make back for it)
        e.setHome(e.position().add(30, 0, 0));
        set(h, "\"roam\":\"stay\"");
        Vec3[] start = new Vec3[1];
        h.runAfterDelay(20, () -> start[0] = e.position());
        h.runAfterDelay(1000, () -> {
            h.assertTrue(e.wandering() == null && e.goal() == null, "he picked a spot: " + e.wandering());
            h.assertTrue(e.horiz(start[0]) < 2, "he moved off: " + e.horiz(start[0]));
            finish(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "beh_seek")
    public void aSeekerMakesForAnotherGiantAndFightsWhomHeWouldAvoid(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1407);
        e.setStay(false);
        // a stand-in for a Wreckback (whom he keeps away from), out past where he'd meet it
        Vec3 at = spot(e, 90, 0);
        force(h, (int) at.x, (int) at.z, 1, true);
        HollowbellGameTests.runsEntities(h, (int) at.x, (int) at.z, 1);
        // (a cow: a hostile stand-in is gone the moment another test turns the world to Peaceful)
        net.minecraft.world.entity.Mob other = EntityType.COW.create(h.getLevel());
        other.moveTo(at.x, at.y, at.z, 0f, 0f);
        other.setNoAi(true);
        other.setInvulnerable(true);
        other.setPersistenceRequired();
        other.addTag("jj_giant");
        other.addTag(Meeting.KIND + "wreckback");
        h.getLevel().addFreshEntity(other);
        h.runAfterDelay(5, () -> {
            try {
                e.roamLook(h.getLevel());
                h.assertTrue(e.wandering() == null || e.wandering().distanceTo(other.position()) > 5,
                        "as shipped he doesn't go looking");
                set(h, "\"seekGiants\":true");
                h.assertTrue(Meeting.wayNow("wreckback") == Meeting.Way.FIGHT, "seeking, every other kind is a fight");
                h.assertTrue(Meeting.wayNow("hollowbell") == Meeting.Way.NONE, "his own kind isn't");
                e.roamLook(h.getLevel());
                Vec3 g = e.wandering();
                h.assertTrue(g != null && Math.hypot(g.x - other.getX(), g.z - other.getZ()) < 5,
                        "he didn't make for the giant: " + g + " free=" + e.freeToSeek());
                h.assertTrue(e.goal() == null, "seeking isn't an order");
                set(h, "\"seekGiants\":true,\"roam\":\"stay\"");
                e.setGoal(null);
                e.clearWandering();
                e.roamLook(h.getLevel());
                h.assertTrue(e.wandering() == null, "staying, he doesn't seek");
            } finally {
                force(h, (int) at.x, (int) at.z, 1, false);
            }
            finish(h, e, other);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "beh_raid")
    public void aRaiderMakesForVillagersAndTakesThemOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1408);
        e.setStay(false);
        Villager far = EntityType.VILLAGER.create(h.getLevel()), near = EntityType.VILLAGER.create(h.getLevel());
        Vec3 a = spot(e, 0, 110), b = spot(e, 25, 0);
        force(h, (int) a.x, (int) a.z, 1, true);
        HollowbellGameTests.runsEntities(h, (int) a.x, (int) a.z, 1);
        far.moveTo(a.x, a.y, a.z, 0f, 0f);
        far.setNoAi(true);
        far.setPersistenceRequired();
        h.getLevel().addFreshEntity(far);
        h.runAfterDelay(5, () -> {
            e.roamLook(h.getLevel());
            h.assertTrue(e.wandering() == null || e.wandering().distanceTo(far.position()) > 5,
                    "as shipped he doesn't raid");
            set(h, "\"raidVillages\":true");
            e.roamLook(h.getLevel());
            Vec3 g = e.wandering();
            h.assertTrue(g != null && Math.hypot(g.x - far.getX(), g.z - far.getZ()) < 5,
                    "he didn't make for the village: " + g);
            near.moveTo(b.x, b.y, b.z, 0f, 0f);
            near.setNoAi(true);
            near.setPersistenceRequired();
            h.getLevel().addFreshEntity(near);
        });
        h.runAfterDelay(120, () -> {
            boolean hit = e.getTarget() == near;
            force(h, (int) a.x, (int) a.z, 1, false);
            h.assertTrue(hit, "a villager near a raider wasn't his target: " + e.getTarget());
            finish(h, e, far, near);
        });
    }

    // spawning

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "beh_companions")
    public void companionsComeBesideTheWorldsOwnOneUpToTheCount(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        // (nobody else of him about: the count is the whole world's)
        for (ServerLevel ol : l.getServer().getAllLevels())
            for (HollowbellEntity o : ol.getEntities(ModEntities.HOLLOWBELL, o -> o.isAlive())) o.discard();
        Away.get(l.getServer()).forgetAll();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1409);
        e.markWorldOne();
        List<HollowbellEntity> made = new ArrayList<>();
        h.runAfterDelay(5, () -> {
            try {
                e.companionsTick(l);
                h.assertTrue(WorldOne.standing(l.getServer()) == 1,
                        "as shipped none come: " + WorldOne.standing(l.getServer()));
                set(h, "\"worldCount\":3,\"maxInWorld\":5,\"worldScale\":0.2,\"spawnSize\":[0.5,0.5]");
                e.companionsTick(l);
                h.assertTrue(WorldOne.standing(l.getServer()) == 3,
                        "two companions: " + WorldOne.standing(l.getServer()));
                for (HollowbellEntity o : l.getEntities(ModEntities.HOLLOWBELL, o -> o != e && o.isAlive()))
                    made.add(o);
                for (HollowbellEntity o : made) {
                    h.assertTrue(Math.abs(o.bellScale() - 0.1f) < 1e-4,
                            "a companion's size is worldScale x spread: " + o.bellScale());
                    h.assertFalse(o.isWorldOne(), "a companion isn't the world's own");
                    h.assertTrue(o.variant() == HollowbellEntity.CALM, "a companion comes calm");
                    h.assertTrue(o.horiz(e.position()) >= 12, "too near him");
                    h.assertTrue(o.arriving() || o.tickCount == 0, "a companion comes down out of the sky");
                }
                e.companionsTick(l);
                h.assertTrue(WorldOne.standing(l.getServer()) == 3, "no more than the count");
                // one gone: none again before the wait (a minute at least) has passed
                made.get(0).discard();
                e.companionsTick(l);
                h.assertTrue(WorldOne.standing(l.getServer()) == 2, "replaced before the wait");
                // the cap: never past Most at once
                set(h, "\"worldCount\":8,\"maxInWorld\":2,\"worldRespawnDays\":0");
                e.companionsTick(l);
                h.assertTrue(WorldOne.standing(l.getServer()) == 2, "past the cap");
            } finally {
                for (HollowbellEntity o : made) o.discard();
            }
            finish(h, e);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "beh_respawn")
    public void withRespawnOffNoneComesAfterAFall(GameTestHelper h) {
        ServerLevel l = h.getLevel();
        WorldOne w = WorldOne.get(l.getServer());
        BlockPos here = h.absolutePos(BlockPos.ZERO);
        ServerPlayer p = player(h, Vec3.atCenterOf(here.above(2)));
        boolean nat = BellConfig.V.oneInTheWorld;
        try {
            Away.get(l.getServer()).forgetAll();
            w.clearForTests();
            BookRule.get(l.getServer()).free();
            HollowbellEntity e = ModEntities.HOLLOWBELL.create(l);
            e.moveTo(here.getX() + 40000.5, here.getY(), here.getZ() + 0.5, 0f, 0f);
            w.died(l, e);
            h.assertTrue(w.fellOnce(), "the fall is noted");
            h.assertFalse(w.heldBackByRespawn(), "as shipped the next one comes");
            w.noteSpot(here.getX() + 40000, here.getZ(), false, l.getGameTime());
            set(h, "\"respawn\":false,\"oneInTheWorld\":true");
            h.assertTrue(w.heldBackByRespawn(), "respawn off doesn't hold the next one back");
            w.tick(l.getServer().overworld());
            h.assertTrue(w.dueIn(l) >= 0 && !w.aliveNow(),
                    "one came with respawn off: " + w.dueIn(l) + " " + w.aliveNow());
            // (the world's own one's companions aren't brought back either)
            set(h, "\"respawn\":false,\"worldCount\":3");
            HollowbellEntity lead = ModEntities.HOLLOWBELL.create(l);
            lead.moveTo(here.getX() + 0.5, here.getY(), here.getZ() + 0.5, 0f, 0f);
            lead.companionsTick(l);
            h.assertTrue(WorldOne.standing(l.getServer()) == 0, "a companion came with respawn off");
            // (JJ) and one still standing (a companion, an egg's) isn't made the world's own one: gone for good
            set(h, "\"respawn\":false,\"oneInTheWorld\":true");
            HollowbellEntity left = ModEntities.HOLLOWBELL.create(l);
            left.moveTo(here.getX() + 0.5, here.getY() + 1, here.getZ() + 0.5, 0f, 0f);
            left.setBellScale(0.05f);
            l.addFreshEntity(left);
            try {
                for (int i = 0; i < 101; i++) w.tick(l.getServer().overworld());
                h.assertTrue(!left.isWorldOne() && !w.aliveNow(), "a companion took over with respawn off");
                set(h, "\"oneInTheWorld\":true");
                for (int i = 0; i < 101; i++) w.tick(l.getServer().overworld());
                h.assertTrue(left.isWorldOne() && w.aliveNow(),
                        "with respawn on the one standing is the world's own again");
            } finally {
                left.discard();
            }
            set(h, "\"oneInTheWorld\":true");
            h.assertFalse(w.heldBackByRespawn(), "with respawn on again the next one comes once due");
        } finally {
            Away.get(l.getServer()).forgetAll();
            w.clearForTests();
            GiantsAdmin.clear();
            BellConfig.V.oneInTheWorld = nat;
            drop(p);
        }
        h.succeed();
    }

    // loot and size

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "beh_loot")
    public void lootIsTimesTheSetting(GameTestHelper h) {
        RandomSource r = new LegacyRandomSource(3);
        List<ItemStack> in = List.of(new ItemStack(Items.DIAMOND, 20), new ItemStack(Items.ENDER_PEARL, 1),
                new ItemStack(Items.TRIDENT));
        try {
            set(h, "\"lootMultiplier\":3");
            List<ItemStack> out = GiantsBehave.loot(in, r);
            int diamonds = 0, tridents = 0;
            for (ItemStack s : out) {
                if (s.is(Items.DIAMOND)) diamonds += s.getCount();
                if (s.is(Items.TRIDENT)) tridents++;
                h.assertTrue(s.getCount() <= s.getMaxStackSize(), "an over-full stack");
            }
            h.assertTrue(diamonds == 60 && tridents == 3, "x3: " + diamonds + " diamonds, " + tridents + " tridents");
            set(h, "\"lootMultiplier\":0");
            h.assertTrue(GiantsBehave.loot(in, r).isEmpty(), "x0 drops nothing");
            set(h, "\"lootMultiplier\":2.5");
            int pearls = 0;
            for (int i = 0; i < 200; i++)
                for (ItemStack s : GiantsBehave.loot(List.of(new ItemStack(Items.ENDER_PEARL)), r))
                    pearls += s.getCount();
            h.assertTrue(pearls >= 450 && pearls <= 550, "x2.5 of one, rounded at random: " + pearls + " in 200");
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "beh_tiny")
    public void heCanBeAboutTwoBlocksTall(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.01f, HollowbellEntity.CALM, 1410);
        e.setStay(false);
        h.assertTrue(e.bellScale() == 0.01f, "the floor is 0.01: " + e.bellScale());
        ServerPlayer[] p = new ServerPlayer[2];
        boolean[] hunted = new boolean[1];
        h.runAfterDelay(10, () -> {
            e.setVariant(HollowbellEntity.HUNTER);
            e.setMoveCooldown(0);
            p[0] = player(h, spot(e, 0, 6));
            // (one standing right in him: pushed out, nothing thrown)
            p[1] = player(h, e.position());
            h.assertFalse(e.bigEnoughToHold(), "that small he holds nobody");
            h.assertFalse(e.comeAndGetMe(p[0]), "nor carries anybody up to his crown");
        });
        for (int t = 20; t < 250; t += 10)
            h.runAfterDelay(t, () -> {
                if (e.getTarget() == p[0] || e.getTarget() == p[1]) hunted[0] = true;
                if (p[0] != null) p[0].setHealth(p[0].getMaxHealth());
                if (p[1] != null) p[1].setHealth(p[1].getMaxHealth());
            });
        h.runAfterDelay(250, () -> {
            double g = e.groundAt(e.getX(), e.getZ());
            h.assertTrue(e.isAlive() && Double.isFinite(e.getY()) && e.getY() - g > -1 && e.getY() - g < 8,
                    "he stays just over the ground at 0.01: " + e.position() + " ground " + g);
            h.assertTrue(e.getBbHeight() <= 1.0f, "his box is small: " + e.getBbHeight());
            h.assertTrue(e.healthMax() >= 40f, "his health floor: " + e.healthMax());
            h.assertTrue(hunted[0], "a tiny hunter still hunts: " + e.getTarget());
            finish(h, e, p[0], p[1]);
        });
    }

    /** (Part 4) the choice settings can be set with /hollowbell config like any other (the server default here) */
    @net.minecraft.gametest.framework.GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "beh_config_choice")
    public void theChoicesTakeConfigCommands(net.minecraft.gametest.framework.GameTestHelper h) {
        var server = h.getLevel().getServer();
        var src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String was = net.jj.hollowbell.BellConfig.fileValues().temper, roamWas = net.jj.hollowbell.BellConfig.fileValues().roam;
        try {
            server.getCommands().performPrefixedCommand(src, "hollowbell config temper hunter allworlds");
            h.assertTrue(net.jj.hollowbell.BellConfig.fileValues().temper.equals("hunter"), "config temper hunter: " + net.jj.hollowbell.BellConfig.V.temper);
            server.getCommands().performPrefixedCommand(src, "hollowbell config roam wander allworlds");
            h.assertTrue(net.jj.hollowbell.BellConfig.fileValues().roam.equals("wander"), "config roam wander: " + net.jj.hollowbell.BellConfig.V.roam);
            server.getCommands().performPrefixedCommand(src, "hollowbell config sleepSchedule nonsense allworlds");
            h.assertTrue(net.jj.hollowbell.BellConfig.fileValues().sleepSchedule.equals("usual"), "a wrong option is refused");
        } finally {
            server.getCommands().performPrefixedCommand(src, "hollowbell config temper " + was + " allworlds");
            server.getCommands().performPrefixedCommand(src, "hollowbell config roam " + roamWas + " allworlds");
        }
        h.assertTrue(net.jj.hollowbell.BellConfig.fileValues().temper.equals(was) && net.jj.hollowbell.BellConfig.fileValues().roam.equals(roamWas), "back");
        h.succeed();
    }
}
