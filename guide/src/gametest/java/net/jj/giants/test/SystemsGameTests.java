package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakesys.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminPresets;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminUndo;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.systems.Fear;
import net.jj.giants.systems.Grief;
import net.jj.giants.systems.Look;
import net.jj.giants.systems.PlayerDeaths;
import net.jj.giants.systems.Remains;
import net.jj.giants.systems.Scaling;
import net.jj.giants.systems.Systems;
import net.jj.giants.systems.SystemsAdmin;
import net.jj.giants.systems.SystemsCatalog;
import net.jj.giants.systems.SystemsData;
import net.jj.giants.systems.SystemsTags;
import net.jj.giants.systems.Weak;
import net.jj.giants.tools.Stats;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * Part 8, "New systems A" (ADMIN_BRIDGE.md 24), the Guide's side with a stand-in giant (net.jj.fakesys: iron golems
 * tagged "fakesys"): the settings as ordinary ones (log, undo, locks, export, presets, a server default), nothing
 * sent or done while they're off, the scaling/moon/season factors, Growth (and a Titan), Hunger (eating, weakening,
 * moods), Loyalty (and turning), Herds, Grief healing, remains, a player's death (kept or swallowed), fear and awe,
 * the weak point glints, the tags shared with the fun tools, the book's page and the danger rating. Each test has its
 * own batch and spot and turns the systems back off. Left out with the real giants loaded (SystemsGiantsGameTests).
 */
public class SystemsGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakesys", "fakesys", "fakesys", "net.jj.fakesys.GiantsBridge", List.of(),
            Set.of());

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        FunGiants.TEST_KINDS.put(GiantsBridge.TAG, "fakesys");
        GiantsBridge.reset();
        Look.forgetSysForTests();
        Watch.forget();
        off(s);
        SystemsData.get(s).s = new SystemsData.State();
        return s;
    }

    /** every system back to its default (only the systems' own ids: other batches keep theirs) */
    static void off(MinecraftServer s) {
        AdminWorld w = AdminWorld.get(s);
        for (AdminSetting x : AdminCatalog.shared()) if (AdminCatalog.systems(x)) w.remove("all", x.id());
        AdminServer.push(s, w);
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        for (ServerPlayer p : players) s.getPlayerList().remove(p);
        for (IronGolem g : GiantsBridge.golems(s)) g.discard();
        off(s);
        Systems.forget();
        AdminServer.useGiants(null);
        h.succeed();
    }

    static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + " " + v + ": " + r.lines());
    }

    /** this run's own stand-ins (the test world is kept between runs: a failed run's are swept away) */
    static final String RUN = "fakesys.run=" + java.util.UUID.randomUUID();

    static IronGolem golem(GameTestHelper h, BlockPos at, double dx, double dz) {
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + dx + 0.5, at.getY(), at.getZ() + dz + 0.5);
        g.addTag(RUN);
        return g;
    }

    /** an earlier run's stand-ins and anything they dropped, gone */
    static void sweep(GameTestHelper h) {
        List<IronGolem> mine = new java.util.ArrayList<>();
        for (IronGolem g : GiantsBridge.golems(h.getLevel().getServer()))
            if (!g.getTags().contains(RUN)) g.discard();
            else mine.add(g);
        for (IronGolem g : mine)
            for (ItemEntity it : h.getLevel().getEntitiesOfClass(ItemEntity.class, g.getBoundingBox().inflate(100)))
                it.discard();
    }

    static boolean has(IronGolem g, String tag) {
        return g.getTags().contains(tag);
    }

    // the settings

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_settings", timeoutTicks = 100)
    public void theSystemsAreOrdinarySettingsAndOffChangesNothing(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        // every one: category systems, live, off (or as always) in the jar, grouped once on the book's page
        int grouped = 0;
        for (String[] g : SystemsCatalog.GROUPS) grouped += g.length - 1;
        int n = 0;
        for (AdminSetting x : AdminCatalog.shared())
            if (AdminCatalog.systems(x)) {
                n++;
                h.assertTrue(x.when().equals("live") && x.id().startsWith("all."), x.id());
                h.assertTrue(AdminCatalog.plain(x), x.id() + " is no multiplier");
            }
        h.assertTrue(n == 42 && grouped == 42, "42 settings (Part 8 and 9), every one in a group: " + n + " " + grouped);
        // nothing on: what the giants are sent is exactly the world's own
        Map<String, JsonElement> all = AdminWorld.get(s).values("all");
        h.assertTrue(Systems.forGiants(s, all) == all, "off: the very same map");
        h.assertTrue(!GiantsBridge.all.has("all.sysReport") && !GiantsBridge.all.has("all.weakPoints"),
                "nothing extra sent: " + GiantsBridge.all);
        // set, told, logged, undone
        set(h, s, "growth", "on");
        AdminWorld w = AdminWorld.get(s);
        AdminWorld.Change c = w.log().get(w.log().size() - 1);
        h.assertTrue(c.id().equals("all.growth") && c.now().equals("on"), "logged: " + c);
        h.assertTrue(GiantsBridge.all.get("all.sysReport").getAsBoolean(), "growth wants hits told");
        AdminServer.Result u = AdminUndo.undo(s, "Tester", w.log().size() - 1, c.time());
        h.assertTrue(u.ok() && !Systems.on(s, "growth"), "undone: " + u.lines());
        // a bad value refused
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "griefHeal", "sometimes").ok(), "not an option");
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "weakHit", "9").ok(), "past the most");
        // locked by one admin, refused to another
        h.assertTrue(AdminServer.lock(s, "Tester", "-", "all", "fear").ok(), "locked");
        h.assertTrue(!AdminServer.set(s, "Other", "all", "fear", "strong").ok(), "locked against others");
        h.assertTrue(AdminServer.unlock(s, "Tester", "all", "fear").ok(), "unlocked");
        w.remove("all", "all.fear");
        // exported and read back
        set(h, s, "deathRule", "skeleton");
        AdminTransfer.Bundle b = AdminTransfer.current(s);
        AdminTransfer.Bundle back = AdminTransfer.decode(AdminTransfer.encode(b));
        h.assertTrue(back != null && AdminSetting.same(back.values().get("all").get("all.deathRule"),
                new JsonPrimitive("skeleton")), "exported");
        // Classic puts them back; Herds and Evolution name theirs
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        h.assertTrue(Systems.option(s, "deathRule").equals("drops"), "Classic: back to the default");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("herds")));
        h.assertTrue(Systems.on(s, "herd"), "Herds turns herds on");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("evolution")));
        h.assertTrue(Systems.on(s, "growth") && !Systems.on(s, "herd") && Systems.num(s, "growthMost") == 5
                && Systems.num(s, "growthTitan") == 7, "Evolution: growth, and herds back off");
        h.assertTrue(AdminSetting.same(w.values("all").get("all.size"), new JsonPrimitive(0.4)), "they start small");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        done(h);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_default", timeoutTicks = 100)
    public void allWorldsWritesTheSystemsOwnServerDefault(GameTestHelper h) throws Exception {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Path dir = Files.createTempDirectory("p8sys");
        Path was = AdminPresets.dirOverride;
        AdminPresets.dirOverride = dir;
        try {
            AdminServer.Result r = AdminServer.setDefault(s, "Tester", "all", "weakPoints", "on");
            h.assertTrue(r.ok() && r.lines().get(0).contains("for all worlds"), "" + r.lines());
            h.assertTrue(Files.readString(dir.resolve("systems.json")).contains("all.weakPoints"), "in the file");
            h.assertTrue(Systems.on(s, "weakPoints"), "the server default is in use");
            h.assertTrue(GiantsBridge.all.get("all.weakPoints").getAsBoolean(), "and sent to the giants");
            // this world keeps its own on top
            set(h, s, "weakPoints", "off");
            h.assertTrue(!Systems.on(s, "weakPoints"), "the world's own wins");
            AdminWorld.get(s).remove("all", "all.weakPoints");
            // back to the jar's: out of the file
            AdminServer.setDefault(s, "Tester", "all", "weakPoints", "off");
            h.assertTrue(!Files.readString(dir.resolve("systems.json")).contains("all.weakPoints"), "gone again");
            // the multipliers still refuse all worlds
            h.assertTrue(!AdminServer.setDefault(s, "Tester", "all", "health", "2").ok(), "multipliers: world only");
        } finally {
            AdminPresets.dirOverride = was;
        }
        done(h);
    }

    // scaling, the moon, the seasons

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_scale", timeoutTicks = 100)
    public void scalingMoonAndSeasonsGoOnWhatTheGiantsAreSent(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = PlayersGameTests.join(h, "scaleA", false), b = PlayersGameTests.join(h, "scaleB", false);
        a.setGameMode(GameType.SURVIVAL);
        b.setGameMode(GameType.SURVIVAL);
        set(h, s, "health", "2");
        set(h, s, "scalePlayers", "0.5");
        set(h, s, "scaleMost", "10");
        int on = s.getPlayerList().getPlayerCount();
        Scaling.Factors f = Scaling.factors(s);
        double want = 1 + 0.5 * (on - 1) + 0; // (no days, no armour on the test players)
        long days = s.overworld().getGameTime() / 24000L;
        h.assertTrue(f.players() == on && Math.abs(f.scale() - Math.round(want * 20) / 20.0) < 1e-9 || days > 0,
                "players: " + f + " want " + want);
        Map<String, JsonElement> sent = Systems.forGiants(s, AdminWorld.get(s).values("all"));
        h.assertTrue(Math.abs(sent.get("all.health").getAsDouble() - 2 * f.health()) < 1e-6,
                "on top of the world's own x2: " + sent.get("all.health"));
        h.assertTrue(AdminWorld.get(s).values("all").get("all.health").getAsDouble() == 2, "the world keeps x2");
        // capped
        set(h, s, "scaleMost", "1.25");
        h.assertTrue(Scaling.factors(s).scale() <= 1.25, "never past the most");
        // seasons: a day each, so the season is the day count
        set(h, s, "scalePlayers", "0");
        set(h, s, "seasons", "on");
        set(h, s, "seasonDays", "1");
        int season = (int) (s.overworld().getDayTime() / 24000L % 4);
        f = Scaling.factors(s);
        h.assertTrue(f.season() == season, "the season: " + f.season() + " want " + season);
        double hw = season == 3 ? 1.2 : 1, sw = season == 1 ? 1.1 : season == 3 ? 0.85 : 1,
                heal = season == 0 ? 1.5 : 1;
        h.assertTrue(f.health() == hw && f.healing() == heal && Math.abs(f.speed() / sw - 1) < 0.06
                || f.fullMoon(), "season " + season + ": " + f);
        // the full moon: a frenzy only on a full-moon night
        set(h, s, "seasons", "off");
        set(h, s, "moonFrenzy", "2");
        long t = s.overworld().getDayTime() % 24000L;
        boolean full = s.overworld().getMoonPhase() == 0 && t >= 13000 && t < 23000;
        f = Scaling.factors(s);
        h.assertTrue(f.fullMoon() == full && f.damage() == (full ? 2 : 1), "moon " + full + ": " + f);
        // sent again when a factor changes
        Scaling.resendForTests();
        set(h, s, "moonFrenzy", "1");
        set(h, s, "health", "1");
        Scaling.tick(s);
        h.assertTrue(!GiantsBridge.all.has("all.health") || GiantsBridge.all.get("all.health").getAsDouble() == 1,
                "back to the world's own: " + GiantsBridge.all);
        AdminWorld.get(s).remove("all", "all.health");
        done(h, a, b);
    }

    // Growth

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_growth", timeoutTicks = 200)
    public void giantsGrowWithWinsAndBecomeTitansLeftAlone(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 20);
        IronGolem g = golem(h, at, 0, 0);
        ServerPlayer p = PlayersGameTests.join(h, "grower", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            set(h, s, "growth", "on");
            set(h, s, "growthPerDay", "0");
            set(h, s, "growthPerWin", "0.5");
            set(h, s, "growthMost", "2");
            Look.lookForTests(s);
            SystemsData.G rec = SystemsData.get(s).s.giants.get(g.getStringUUID());
            h.assertTrue(rec != null && rec.start == 1, "followed from size 1: " + rec);
            // a win: he brought down another giant who he last hurt
            IronGolem other = golem(h, at, 10, 0);
            Look.lookForTests(s);
            Systems.hurt(s, "{\"sys\":1,\"giant\":\"fakesys\",\"id\":\"" + other.getStringUUID() + "\",\"by\":\""
                    + g.getStringUUID() + "\",\"amount\":5,\"weak\":false}");
            Systems.fell(other);
            other.discard();
            h.assertTrue(rec.wins == 1, "one win: " + rec.wins);
            GiantsBridge.calls.clear();
            Look.lookForTests(s);
            Look.lookForTests(s);
            Look.lookForTests(s);
            h.assertTrue(GiantsBridge.called("liveact") && GiantsBridge.get(g, "size", "1").equals("1.5"),
                    "grown to 1.5: " + GiantsBridge.calls);
            // never past the most
            rec.wins = 10;
            for (int i = 0; i < 3; i++) Look.lookForTests(s);
            h.assertTrue(GiantsBridge.get(g, "size", "1").equals("2"), "kept to x2: " + GiantsBridge.get(g, "size", ""));
            // left alone a day: a Titan
            set(h, s, "growthTitan", "1");
            rec.lastFight = s.overworld().getGameTime() - 25000;
            rec.born = rec.lastFight;
            for (int i = 0; i < 3; i++) Look.lookForTests(s);
            h.assertTrue(rec.titan, "a Titan");
            h.assertTrue(SystemsTags.sys(g, "health") == 2 && SystemsTags.sys(g, "damage") == 2
                    && has(g, "jj_giants.health=2") && has(g, "jj_giants.damage=2"), "twice the health and damage: "
                    + g.getTags());
            h.assertTrue(GiantsBridge.called("funact"), "his stats read again");
            // growth off: the Titan's tags come off
            set(h, s, "growth", "off");
            Look.lookForTests(s);
            h.assertTrue(!has(g, "jj_giants.health=2") && SystemsTags.sys(g, "health") == 1, "x1 again: " + g.getTags());
            done(h, p);
        });
    }

    // Hunger

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_hunger", timeoutTicks = 200)
    public void hungryGiantsWeakenHuntAndEatThenCalmDown(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 21);
        IronGolem g = golem(h, at, 0, 0);
        ServerPlayer p = PlayersGameTests.join(h, "feeder", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            set(h, s, "hunger", "on");
            set(h, s, "hungerWeak", "0.5");
            Look.lookForTests(s);
            SystemsData.G rec = SystemsData.get(s).s.giants.get(g.getStringUUID());
            h.assertTrue(rec != null && rec.food > 99, "full to start: " + (rec == null ? null : rec.food));
            h.assertTrue(SystemsTags.sys(g, "damage") == 1, "full: no weaker");
            // just past hungry: he goes hunting and weakens
            rec.food = 25.0001;
            set(h, s, "hungerDays", "0.5");
            Look.lookForTests(s);
            h.assertTrue(GiantsBridge.get(g, "mood", "calm").equals("hunting"), "hungry: hunting");
            rec.food = 0;
            Look.lookForTests(s);
            h.assertTrue(Math.abs(SystemsTags.sys(g, "damage") - 0.5) < 1e-6 && Math.abs(SystemsTags.sys(g, "speed") - 0.5)
                    < 1e-6, "starving: half: " + g.getTags());
            // food thrown to him: eaten, and full he settles down
            ItemEntity it = new ItemEntity(h.getLevel(), g.getX() + 1, g.getY(), g.getZ(), new ItemStack(Items.COOKED_BEEF,
                    16));
            it.setThrower(p);
            h.getLevel().addFreshEntity(it);
            Look.lookForTests(s);
            h.assertTrue(!it.isAlive() && rec.food > 80, "eaten: " + rec.food);
            h.assertTrue(GiantsBridge.get(g, "mood", "").equals("calm"), "full: calm again");
            Look.lookForTests(s);
            h.assertTrue(SystemsTags.sys(g, "damage") == 1, "fed: his strength back");
            // a creature he kills feeds him
            rec.food = 10;
            Cow cow = EntityType.COW.create(h.getLevel());
            cow.moveTo(g.getX() + 2, g.getY(), g.getZ());
            h.getLevel().addFreshEntity(cow);
            cow.hurt(h.getLevel().damageSources().mobAttack(g), 1000);
            h.assertTrue(rec.food > 10, "a kill fed him: " + rec.food);
            done(h, p);
        });
    }

    // Loyalty

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_loyalty", timeoutTicks = 200)
    public void loyaltyRisesFallsSulksAndTurns(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 22);
        IronGolem g = golem(h, at, 0, 0);
        ServerPlayer owner = PlayersGameTests.join(h, "owner", false);
        owner.teleportTo(h.getLevel(), g.getX() + 5, g.getY(), g.getZ(), 0, 0);
        GiantsBridge.put(g, "owner", owner.getStringUUID());
        h.runAfterDelay(3, () -> {
            sweep(h);
            set(h, s, "loyalty", "on");
            Look.lookForTests(s);
            SystemsData.G rec = SystemsData.get(s).s.giants.get(g.getStringUUID());
            h.assertTrue(rec != null && rec.loyalty >= 60 && rec.owner.equals(owner.getStringUUID()), "60 to start");
            double was = rec.loyalty;
            Look.lookForTests(s);
            h.assertTrue(rec.loyalty > was, "kept near: up");
            // the owner's hits
            was = rec.loyalty;
            Systems.hurt(s, "{\"sys\":1,\"giant\":\"fakesys\",\"id\":\"" + g.getStringUUID() + "\",\"by\":\""
                    + owner.getStringUUID() + "\",\"amount\":3,\"weak\":false}");
            h.assertTrue(rec.loyalty <= was - 5 + 1e-9, "a hit: down 5");
            // devoted beside them: hits harder
            rec.loyalty = 90;
            Look.lookForTests(s);
            h.assertTrue(Math.abs(SystemsTags.sys(g, "damage") - 1.15) < 1e-6, "devoted: " + g.getTags());
            // sulking: hunting
            rec.loyalty = 20;
            Look.lookForTests(s);
            h.assertTrue(rec.sulking && GiantsBridge.get(g, "mood", "").equals("hunting"), "sulks");
            // none left: he turns on them
            rec.loyalty = 0.01;
            Systems.hurt(s, "{\"sys\":1,\"giant\":\"fakesys\",\"id\":\"" + g.getStringUUID() + "\",\"by\":\""
                    + owner.getStringUUID() + "\",\"amount\":3,\"weak\":false}");
            Look.lookForTests(s);
            h.assertTrue(rec.turned && GiantsBridge.get(g, "turnedOn", "").equals(owner.getStringUUID()),
                    "turned on his owner: " + GiantsBridge.calls);
            // a new owner starts again at 60
            GiantsBridge.put(g, "owner", java.util.UUID.randomUUID().toString());
            Look.lookForTests(s);
            h.assertTrue(rec.loyalty == 60 && !rec.turned, "a new owner: 60");
            // Can turn on you off: he never turns
            set(h, s, "loyaltyTurns", "off");
            rec.loyalty = 0;
            GiantsBridge.calls.clear();
            Look.lookForTests(s);
            h.assertTrue(!GiantsBridge.called("turn"), "never turns");
            done(h, owner);
        });
    }

    // Herds

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_herds", timeoutTicks = 200)
    public void aStrayWalksBackToTheLeader(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 23);
        IronGolem lead = golem(h, at, 0, 0), near = golem(h, at, 20, 0), far = golem(h, at, 58, 0);
        lead.addTag(GiantsBridge.TAG + ".own");
        ServerPlayer p = PlayersGameTests.join(h, "shepherd", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            Look.lookForTests(s);
            h.assertTrue(!GiantsBridge.called("go"), "herds off: nobody sent");
            set(h, s, "herd", "on");
            set(h, s, "herdRange", "32");
            Look.lookForTests(s);
            String goes = GiantsBridge.get(far, "goes", "");
            h.assertTrue(!goes.isEmpty(), "the stray walks back: " + GiantsBridge.calls);
            String[] xz = goes.split(",");
            double dx = Double.parseDouble(xz[0]) - lead.getX(), dz = Double.parseDouble(xz[1]) - lead.getZ();
            // (the spread: Herd spread or the leader's own reach, the stand-in's 50, whichever is more)
            h.assertTrue(Math.hypot(dx, dz) <= 26, "to within half the spread of the leader: " + goes);
            h.assertTrue(GiantsBridge.get(near, "goes", "").isEmpty() && GiantsBridge.get(lead, "goes", "").isEmpty(),
                    "the leader and the one close by stay");
            // an owned one isn't herded
            GiantsBridge.put(far, "goes", null);
            GiantsBridge.put(far, "owner", p.getStringUUID());
            Look.lookForTests(s);
            h.assertTrue(GiantsBridge.get(far, "goes", "").isEmpty(), "an owned one goes his own way");
            done(h, p);
        });
    }

    // Grief healing

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_grief", timeoutTicks = 300)
    public void brokenBlocksGrowBackButNotAGiantsOwnOrWhereSomeoneBuilt(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 24);
        ServerLevel l = h.getLevel();
        BlockPos a = at.offset(3, 0, 3), b = at.offset(5, 0, 3), c = at.offset(7, 0, 3), d = at.offset(9, 0, 3);
        h.runAfterDelay(2, () -> {
            sweep(h);
            for (BlockPos x : List.of(a, c, d)) l.setBlock(x, Blocks.OAK_PLANKS.defaultBlockState(), 3);
            // off: nothing noted
            Stats.current = "fakesys";
            try {
                l.setBlock(d, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                Stats.current = null;
            }
            set(h, s, "griefHeal", "instant");
            Grief.tick(s);
            int before = Grief.waiting();
            Stats.current = "fakesys";
            try {
                // a giant breaks a plank, and fills an empty spot then clears it again (his own)
                l.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(b, Blocks.ICE.defaultBlockState(), 3);
                l.setBlock(b, Blocks.AIR.defaultBlockState(), 3);
                l.setBlock(c, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                Stats.current = null;
            }
            h.assertTrue(Grief.waiting() == before + 2, "two noted: " + Grief.waiting());
            // a player builds where one was
            l.setBlock(c, Blocks.COBBLESTONE.defaultBlockState(), 3);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(l.getBlockState(a).is(Blocks.OAK_PLANKS), "grew back: " + l.getBlockState(a));
            h.assertTrue(l.getBlockState(b).isAir(), "his own ice stays gone");
            h.assertTrue(l.getBlockState(c).is(Blocks.COBBLESTONE), "the player's block stays");
            h.assertTrue(l.getBlockState(d).isAir(), "broken while off: stays broken");
            // turned off: the queue forgotten
            Stats.current = "fakesys";
            try {
                l.setBlock(a, Blocks.AIR.defaultBlockState(), 3);
            } finally {
                Stats.current = null;
            }
            set(h, s, "griefHeal", "off");
            Grief.tick(s);
            h.assertTrue(Grief.waiting() == 0, "off: none waiting");
            FunGameTests.unspot(h, at);
            done(h);
        });
    }

    // remains

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_remains", timeoutTicks = 300)
    public void aSkeletonStaysAndACorpseRotsAway(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 25);
        ServerLevel l = h.getLevel();
        IronGolem a = golem(h, at, -25, 0), b = golem(h, at, 25, 0);
        h.runAfterDelay(3, () -> {
            sweep(h);
            // each kind's body plan has bones; a corpse has flesh too
            for (String k : AdminCatalog.KEYS) {
                Map<Long, net.minecraft.world.level.block.state.BlockState> sk = Remains.bodyForTests(k, 40, false),
                        co = Remains.bodyForTests(k, 40, true);
                h.assertTrue(sk.size() > 40 && co.size() > sk.size(), k + ": " + sk.size() + " / " + co.size());
            }
            Systems.fell(a);
            h.assertTrue(SystemsData.get(s).s.remains.isEmpty(), "drops only: no remains");
            set(h, s, "deathRule", "skeleton");
            Systems.fell(b);
        });
        h.runAfterDelay(40, () -> {
            int bones = count(l, b.blockPosition(), 40, Blocks.BONE_BLOCK);
            h.assertTrue(bones > 20, "his bones lie there: " + bones);
            h.assertTrue(SystemsData.get(s).s.remains.isEmpty(), "a skeleton is left for good (not kept)");
            set(h, s, "deathRule", "corpse");
            IronGolem c = golem(h, at, 0, 30);
            Systems.fell(c);
        });
        h.runAfterDelay(80, () -> {
            sweep(h);
            List<SystemsData.Remains> rs = SystemsData.get(s).s.remains;
            h.assertTrue(rs.size() == 1 && rs.get(0).rule.equals("corpse"), "a corpse rotting: " + rs.size());
            int flesh = 0;
            for (int i = 0; i < rs.get(0).pos.size(); i++) if (rs.get(0).layer.get(i) > 0) flesh++;
            h.assertTrue(flesh > 20, "flesh over his bones: " + flesh);
            // a long time later: rotted away
            rs.get(0).fell -= rs.get(0).rotTicks + 10;
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(SystemsData.get(s).s.remains.isEmpty(), "rotted away");
            done(h);
        });
    }

    /** a stand-in giant for the rating: every setting at his jar's value but these */
    static AdminInsight.View view(Map<String, Double> changed) {
        Map<String, Double> jar = new java.util.HashMap<>(Map.of("healthMultiplier", 1.0, "damageMultiplier", 1.0,
                "health", 6000.0, "worldCount", 1.0, "worldScale", 1.0));
        Map<String, Double> now = new java.util.HashMap<>(jar);
        now.putAll(changed);
        return AdminInsight.view("wreckback", "Wreckback", now, jar, Set.of("all.health", "all.damage", "all.size"));
    }

    static int count(ServerLevel l, BlockPos c, int r, net.minecraft.world.level.block.Block b) {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-r, -2, -r), c.offset(r, r, r))) if (l.getBlockState(p).is(b)) n++;
        return n;
    }

    // a player's death

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_death", timeoutTicks = 200)
    public void playersKeepTheirThingsOrHeSwallowsThem(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 26);
        IronGolem g = golem(h, at, 0, 0);
        ServerPlayer p = PlayersGameTests.join(h, "victim", false), q = PlayersGameTests.join(h, "victim2", false),
                after = PlayersGameTests.join(h, "victimAfter", false);
        // (after a player's first moments, when nothing hurts them)
        h.runAfterDelay(80, () -> {
            sweep(h);
            for (ServerPlayer x : List.of(p, q)) {
                x.setGameMode(GameType.SURVIVAL);
                x.teleportTo(h.getLevel(), g.getX() + 3, g.getY(), g.getZ(), 0, 0);
                x.getInventory().add(new ItemStack(Items.DIAMOND, 5));
                x.experienceLevel = 7;
            }
            set(h, s, "playerDeath", "keep");
            p.hurt(h.getLevel().damageSources().mobAttack(g), 10000);
            h.assertTrue(p.isDeadOrDying(), "dead");
            h.assertTrue(PlayerDeaths.keeps(p), "kept");
            h.assertTrue(h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition()).inflate(8))
                    .isEmpty(), "nothing dropped");
            PlayerDeaths.copy(p, after, false);
            h.assertTrue(after.getInventory().countItem(Items.DIAMOND) == 5 && after.experienceLevel == 7,
                    "back with them");
            set(h, s, "playerDeath", "swallow");
            q.hurt(h.getLevel().damageSources().mobAttack(g), 10000);
            SystemsData.G rec = SystemsData.get(s).s.giants.get(g.getStringUUID());
            h.assertTrue(rec != null && !rec.belly.isEmpty(), "swallowed (their diamonds and their Guide)");
            h.assertTrue(q.getInventory().countItem(Items.DIAMOND) == 0, "gone from them");
            // he falls: out it comes
            Systems.fell(g);
            int got = 0;
            for (ItemEntity it : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(g.blockPosition())
                    .inflate(4))) if (it.getItem().is(Items.DIAMOND)) got += it.getItem().getCount();
            h.assertTrue(got == 5, "given back where he fell: " + got);
            for (ItemEntity it : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(g.blockPosition())
                    .inflate(8))) it.discard();
            done(h, p, q, after);
        });
    }

    // fear and awe, the glints

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_fear", timeoutTicks = 200)
    public void huntingGiantsFrightenAndOwnersStandTall(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 27);
        IronGolem hunter = golem(h, at, 0, 0), mine = golem(h, at, 50, 0);
        GiantsBridge.put(hunter, "mood", "hunting");
        // (size 0.1: about 10 blocks tall, so fear reaches 22 blocks and awe 37)
        GiantsBridge.put(hunter, "size", "0.1");
        GiantsBridge.put(mine, "size", "0.1");
        ServerPlayer p = PlayersGameTests.join(h, "scared", false), o = PlayersGameTests.join(h, "proud", false);
        h.runAfterDelay(3, () -> {
            sweep(h);
            p.setGameMode(GameType.SURVIVAL);
            o.setGameMode(GameType.SURVIVAL);
            p.teleportTo(h.getLevel(), hunter.getX() + 10, hunter.getY(), hunter.getZ(), 0, 0);
            o.teleportTo(h.getLevel(), mine.getX() + 5, mine.getY(), mine.getZ(), 0, 0);
            GiantsBridge.put(mine, "owner", o.getStringUUID());
            Watch.forget();
            Fear.tick(s);
            h.assertTrue(!p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "off: nothing");
            set(h, s, "fear", "strong");
            set(h, s, "awe", "on");
            Watch.forget();
            Fear.tick(s);
            h.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) && p.hasEffect(MobEffects.DARKNESS), "afraid");
            h.assertTrue(o.hasEffect(MobEffects.DAMAGE_BOOST) && o.hasEffect(MobEffects.DAMAGE_RESISTANCE), "proud: "
                    + Watch.rows(s) + " o at " + o.position() + " " + o.getStringUUID() + " " + o.isCreative()
                    + " " + o.isSpectator() + " " + Systems.on(s, "awe"));
            h.assertTrue(!o.hasEffect(MobEffects.DARKNESS), "not afraid of his own");
            // the weak points' glint (his spots asked of him)
            set(h, s, "weakPoints", "on");
            h.assertTrue(Weak.glint(s, FAKE, h.getLevel(), hunter.getStringUUID()) == 1, "one glint");
            h.assertTrue(GiantsBridge.all.get("all.weakPoints").getAsBoolean(), "the giants told");
            done(h, p, o);
        });
    }

    // the tags, shared with the fun tools

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_tags", timeoutTicks = 100)
    public void theSystemsTagsComposeWithAGiantTag(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 28);
        IronGolem g = golem(h, at, 0, 0);
        h.runAfterDelay(3, () -> {
            sweep(h);
            FunWorld fw = FunWorld.get(s);
            FunWorld.Tag t = new FunWorld.Tag();
            t.name = "P8 boss";
            t.damage = 1.5;
            fw.s.tags.put(t.name, t);
            g.addTag("jj_giants.tag=P8 boss");
            g.addTag("jj_giants.damage=1.5");
            SystemsTags.set(s, g, "damage", 0.5);
            h.assertTrue(has(g, "jj_giants.damage=0.75") && has(g, "jj_giants.sys.damage=0.5"), "1.5 x 0.5: "
                    + g.getTags());
            SystemsTags.set(s, g, "damage", 1);
            h.assertTrue(has(g, "jj_giants.damage=1.5") && SystemsTags.sys(g, "damage") == 1, "back: " + g.getTags());
            fw.s.tags.remove(t.name);
            done(h);
        });
    }

    // the book's page, the danger rating and the clash

    @GameTest(template = EMPTY_STRUCTURE, batch = "p8_book", timeoutTicks = 100)
    public void theBookPageDangerAndClash(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 29);
        IronGolem g = golem(h, at, 0, 0);
        h.runAfterDelay(3, () -> {
            sweep(h);
            set(h, s, "growth", "on");
            Look.lookForTests(s);
            JsonObject page = SystemsAdmin.json(s);
            h.assertTrue(page.getAsJsonObject("now").has("season") && page.getAsJsonArray("giants").size() == 1,
                    "now and each giant: " + page);
            h.assertTrue(page.getAsJsonObject("weak").getAsJsonArray("fakesys").get(0).getAsString()
                    .equals("his nose"), "his weak points: " + page.get("weak"));
            h.assertTrue(SystemsAdmin.page(s).kind().equals("systems"), "its kind");
            List<String> lines = net.jj.giants.systems.SystemsCommand.nowLines(s);
            h.assertTrue(lines.get(0).startsWith("Systems on: Growth"), "the command: " + lines);
            // danger: growing giants are worse; weak points to aim for, a little easier
            Map<String, Double> none = Map.of(), grow = Map.of("all.growth", 1.0, "all.growthMost", 4.0);
            AdminInsight.View v = view(Map.of());
            double base = AdminInsight.danger(List.of(v), none).score(),
                    grown = AdminInsight.danger(List.of(v), grow).score(),
                    weak = AdminInsight.danger(List.of(v), Map.of("all.weakPoints", 1.0, "all.weakHit", 4.0)).score();
            h.assertTrue(grown > base && weak < base, "danger " + base + " grown " + grown + " weak " + weak);
            // herds with one of each: the clash
            AdminInsight.View one = view(Map.of("worldCount", 1.0));
            h.assertTrue(AdminInsight.warnings(List.of(one), Map.of("all.herd", 1.0)).stream()
                    .anyMatch(w -> w.rule().equals("herd_alone")), "herd alone");
            done(h);
        });
    }
}
