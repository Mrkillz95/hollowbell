package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakefight.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fights.FightCatalog;
import net.jj.giants.fights.FightData;
import net.jj.giants.fights.FightEdit;
import net.jj.giants.fights.FightScript;
import net.jj.giants.fights.FightWatch;
import net.jj.giants.fights.Fights;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.SystemsCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Part 10B (ADMIN_BRIDGE.md §27), the Guide's side with a stand-in giant (net.jj.fakefight: iron golems tagged
 * "fakefight"): the giant-fight settings (as shipped nothing is sent, asked or followed), the fight script (a chosen
 * winner wins with each loser's ending, let them fight, rounds, a tag team, Stop, bets, spectators and players joining
 * a side, the arena's blocks), and ordinary fights followed (a chase caught and spared, owned giants who may not fight,
 * Stand down). Each test has its own batch and spot. Left out with the real giants loaded (FightGiantsGameTests).
 */
public class FightGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakefight", "fakefight", "fakefight", "net.jj.fakefight.GiantsBridge",
            List.of(), Set.of());

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        FunGiants.TEST_KINDS.put(GiantsBridge.TAG, "fakefight");
        GiantsBridge.reset();
        if (FightScript.busy()) FightScript.stop(s, "Tester");
        Fights.forget();
        off(s);
        FightData.get(s).s = new FightData.State();
        return s;
    }

    /** every giant-fight setting back to its default */
    static void off(MinecraftServer s) {
        AdminWorld w = AdminWorld.get(s);
        for (AdminSetting x : FightCatalog.SETTINGS) w.remove("all", x.id());
        AdminServer.push(s, w);
    }

    static void done(GameTestHelper h, BlockPos spot, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        if (FightScript.busy()) FightScript.stop(s, "Tester");
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
        }
        for (IronGolem g : GiantsBridge.golems(s)) g.discard();
        off(s);
        Fights.forget();
        if (spot != null) FunGameTests.unspot(h, spot);
        AdminServer.useGiants(null);
        s.tickRateManager().setTickRate(20f);
        WAITS.remove(h);
        h.succeed();
    }

    static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    /** waits for each test (one tick hook per test: hooks added from inside a hook would upset the game test's own) */
    private static final Map<GameTestHelper, List<Object[]>> WAITS = new java.util.concurrent.ConcurrentHashMap<>();

    /** runs then once cond holds (checked every tick), or fails after so many ticks */
    static void when(GameTestHelper h, BooleanSupplier cond, int most, String what, Runnable then) {
        List<Object[]> q = WAITS.get(h);
        if (q == null) {
            List<Object[]> nq = new java.util.ArrayList<>();
            WAITS.put(h, nq);
            h.onEachTick(() -> {
                for (Object[] w : List.copyOf(nq)) {
                    boolean ok;
                    try {
                        ok = ((BooleanSupplier) w[0]).getAsBoolean();
                    } catch (RuntimeException e) {
                        ok = false;
                    }
                    if (ok) {
                        nq.remove(w);
                        ((Runnable) w[3]).run();
                    } else if (++((int[]) w[1])[0] > (int) w[4]) {
                        nq.remove(w);
                        h.fail("waited " + w[4] + " ticks for: " + w[2] + " (calls: " + GiantsBridge.calls + ")");
                    }
                }
            });
            q = nq;
        }
        q.add(new Object[] {cond, new int[] {0}, what, then, most});
    }

    /** a script: one fresh fake a side (A1 and B1), no countdown, as the test names */
    static FightData.Setup script(MinecraftServer s, String winner, String ending) {
        FightData.Setup d = new FightData.Setup();
        h(FightEdit.giant(s, d, "a", 1, "fakefight", 0.5, null));
        h(FightEdit.giant(s, d, "b", 1, "fakefight", 0.5, null));
        h(FightEdit.set(d, "winner", winner));
        h(FightEdit.set(d, "ending", ending));
        h(FightEdit.set(d, "countdown", "off"));
        return d;
    }

    private static void h(AdminServer.Result r) {
        if (!r.ok()) throw new IllegalStateException(String.join(" ", r.lines()));
    }

    static void start(GameTestHelper h, MinecraftServer s, FightData.Setup d, BlockPos at) {
        AdminServer.Result r = FightScript.start(s, "Tester", java.util.UUID.randomUUID(), d, h.getLevel(), at,
                new Vec3(0, 0, 1));
        h.assertTrue(r.ok(), "start: " + r.lines());
    }

    /** the golem fighting on this side (slot 1 or 2) of the staged fight */
    static IronGolem side(MinecraftServer s, String side, int slot) {
        int n = 0;
        for (IronGolem g : GiantsBridge.golems(s))
            if (g.getTags().contains("jj_giants.fight.team=" + side) && ++n == slot) return g;
        return null;
    }

    static String phase(MinecraftServer s) {
        JsonObject o = Fights.state(s, null).getAsJsonObject("now");
        return o == null ? "none" : o.get("phase").getAsString();
    }

    static boolean fighting(MinecraftServer s) {
        return phase(s).equals("fight");
    }

    static void hp(IronGolem g, double share) {
        g.setHealth((float) (g.getMaxHealth() * share));
    }

    static String tag(IronGolem g, String head) {
        for (String t : g.getTags()) if (t.startsWith(head)) return t.substring(head.length());
        return null;
    }

    // the settings

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "f10b_settings")
    public void asShippedNothingIsSentAskedOrFollowed(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        h.assertTrue(FightCatalog.SETTINGS.size() == 41, "41 settings: " + FightCatalog.SETTINGS.size());
        for (AdminSetting x : FightCatalog.SETTINGS) {
            h.assertTrue(x.what().length() <= 240, x.id() + " says too much: " + x.what().length());
            h.assertTrue(SystemsCatalog.has(x.id()) && AdminCatalog.shared(x.id()) != null, "a systems setting: "
                    + x.id());
            h.assertTrue(AdminSetting.same(x.def(), x.jar()), "the default is the jar's: " + x.id());
        }
        int grouped = 0;
        for (String[] g : FightCatalog.GROUPS) grouped += g.length - 1;
        h.assertTrue(grouped == 41, "every one in a group");
        h.assertTrue(AdminCatalog.shared("all.ownedFightTo").jar().getAsString().equals("death")
                && AdminCatalog.shared("all.wildFightTo").jar().getAsString().equals("back_down"), "today's jars");
        Map<String, JsonElement> all = new HashMap<>(AdminWorld.get(s).values(AdminCatalog.ALL));
        h.assertTrue(Fights.forGiants(s, all) == all, "as shipped the giants get exactly what came in");
        h.assertFalse(GiantsBridge.all.has("all.fightAsk") || GiantsBridge.all.has("all.fightReport"),
                "no flags sent: " + GiantsBridge.all);
        // a fight told while nothing needs it: not followed
        BlockPos at = FunGameTests.spot(h, 60);
        IronGolem a = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5),
                b = GiantsBridge.spawn(h.getLevel(), at.getX() + 20.5, at.getY(), at.getZ() + 0.5);
        Fights.news(s, news(a, b, "start", false));
        h.assertFalse(FightWatch.followingForTests(a.getUUID(), b.getUUID()), "followed as shipped");
        h.assertTrue(Fights.ok(s, ok(a, b, false)).equals(List.of("ok")), "asked as shipped: ok");
        // a setting that needs it: the flags go out
        set(h, s, "all.wildEscape", "chased");
        h.assertTrue(GiantsBridge.all.has("all.fightReport") && !GiantsBridge.all.has("all.fightAsk"), "report: "
                + GiantsBridge.all);
        set(h, s, "all.ownedMayFight", "never");
        h.assertTrue(GiantsBridge.all.has("all.fightAsk"), "ask: " + GiantsBridge.all);
        set(h, s, "all.wildFightTo", "death");
        h.assertTrue(GiantsBridge.all.has("all.wildFightTo"), "a giant's own one is sent: " + GiantsBridge.all);
        done(h, at);
    }

    static String news(IronGolem self, IronGolem other, String event, boolean ordered) {
        return "{\"fight\":1,\"giant\":\"fakefight\",\"id\":\"" + self.getUUID() + "\",\"other\":\"" + other.getUUID()
                + "\",\"event\":\"" + event + "\",\"ordered\":" + ordered + "}";
    }

    static String ok(IronGolem self, IronGolem other, boolean ordered) {
        return "{\"fight\":1,\"giant\":\"fakefight\",\"id\":\"" + self.getUUID() + "\",\"other\":\"" + other.getUUID()
                + "\",\"ordered\":" + ordered + ",\"by\":\"-\"}";
    }

    // the fight script

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "f10b_death")
    public void theChosenWinnerWinsToTheDeathWithHisFinisherInSlowMotion(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 61);
        FightData.Setup d = script(s, "a", "death");
        start(h, s, d, at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            h.assertTrue(a != null && b != null && a.getTags().contains("jj_giants.fight.script"), "both tagged");
            h.assertTrue(Double.parseDouble(tag(a, "jj_giants.fight.floor=")) >= 0.37
                    && Double.parseDouble(tag(b, "jj_giants.fight.floor=")) == 0.25, "the floors: the winner well"
                    + " above the break point, the loser at it");
            h.assertTrue(GiantsBridge.called("fightact fight " + a.getUUID() + " " + b.getUUID())
                    && GiantsBridge.called("fightact fight " + b.getUUID() + " " + a.getUUID()), "each goes for the"
                    + " other");
            hp(b, 0.25);
            when(h, () -> GiantsBridge.called("fightact do " + a.getUUID() + " " + b.getUUID() + " slam"), 60,
                    "the winner's finisher", () -> {
                        h.assertTrue("0.02".equals(tag(b, "jj_giants.fight.floor=")), "he holds on above nothing");
                        hp(b, 0.02);
                        when(h, () -> !b.isAlive() || b.isRemoved(), 40, "the final blow", () -> {
                            h.assertTrue(s.tickRateManager().tickrate() < 20f, "slow motion: "
                                    + s.tickRateManager().tickrate());
                            when(h, () -> !FightScript.busy(), 400, "the end", () -> {
                                h.assertTrue(GiantsBridge.called("fightact roar " + a.getUUID()), "the winner roars");
                                h.assertTrue(GiantsBridge.called("liveact home " + a.getUUID()), "and goes home");
                                h.assertTrue(tag(a, "jj_giants.fight.") == null, "his tags are off");
                                done(h, at);
                            });
                        });
                    });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "f10b_escape")
    public void theLoserFleesAndEscapes(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 62);
        start(h, s, script(s, "b", "escape"), at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            hp(a, 0.24);
            when(h, () -> GiantsBridge.called("fightact yield " + a.getUUID()), 60, "he runs", () -> {
                h.assertFalse(GiantsBridge.called("fightact yield " + a.getUUID() + " " + b.getUUID() + " bow"),
                        "he runs, not bows");
                h.assertTrue(GiantsBridge.called("fightact stop " + b.getUUID()), "the winner lets him go");
                when(h, () -> !FightScript.busy(), 400, "the end", () -> {
                    h.assertTrue(a.isAlive(), "he got away alive");
                    h.assertFalse(GiantsBridge.called("liveact home " + a.getUUID()), "he went his own way home");
                    h.assertTrue(GiantsBridge.called("liveact home " + b.getUUID()), "the winner goes home");
                    done(h, at);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "f10b_bow")
    public void theLoserSurrendersAndBowsOut(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 63);
        start(h, s, script(s, "a", "bow"), at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            hp(b, 0.2);
            when(h, () -> GiantsBridge.called("fightact yield " + b.getUUID() + " " + a.getUUID() + " bow"), 60,
                    "he bows out", () -> when(h, () -> !FightScript.busy(), 400, "the end", () -> {
                        h.assertTrue(b.isAlive() && GiantsBridge.called("liveact home " + b.getUUID()), "he bowed and"
                                + " goes home");
                        done(h, at);
                    }));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "f10b_caught")
    public void theLoserFleesAndIsCaughtAndKilled(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 64);
        start(h, s, script(s, "a", "caught"), at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            hp(b, 0.25);
            when(h, () -> GiantsBridge.called("fightact chase " + a.getUUID() + " " + b.getUUID()), 60,
                    "the chase", () -> {
                        h.assertTrue(GiantsBridge.called("fightact yield " + b.getUUID()), "he ran first");
                        h.assertTrue("0.7".equals(tag(b, "jj_giants.fightx.speed=")) && "1.3".equals(tag(a,
                                "jj_giants.fightx.speed=")), "the chase is run down: slower and faster");
                        // the winner catches up with him
                        a.teleportTo(b.getX() + 3, b.getY(), b.getZ());
                        when(h, () -> "3".equals(tag(b, "jj_giants.fight.taken=")), 40, "caught", () -> {
                            hp(b, 0.02);
                            when(h, () -> !b.isAlive(), 40, "killed", () -> when(h, () -> !FightScript.busy(), 400,
                                    "the end", () -> {
                                        h.assertTrue(tag(a, "jj_giants.fightx.") == null, "speed back");
                                        done(h, at);
                                    }));
                        });
                    });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "f10b_free")
    public void letThemFightTheFirstToBreakLoses(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 65);
        start(h, s, script(s, "fight", "bow"), at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            h.assertTrue("0.25".equals(tag(a, "jj_giants.fight.floor=")) && "0.25".equals(tag(b,
                    "jj_giants.fight.floor=")), "both may break");
            hp(a, 0.25);
            when(h, () -> GiantsBridge.called("fightact yield " + a.getUUID() + " " + b.getUUID() + " bow"), 60,
                    "A broke first and lost", () -> done(h, at));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 2400, batch = "f10b_rounds")
    public void bestOfThreeTheChosenWinnerTakesItTwoToOne(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 66);
        FightData.Setup d = script(s, "a", "bow");
        h(FightEdit.set(d, "rounds", "3"));
        h(FightEdit.set(d, "rest", "3"));
        h(FightEdit.set(d, "roundheal", "0.5"));
        start(h, s, d, at);
        when(h, () -> fighting(s), 200, "round 1", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            hp(b, 0.2);
            when(h, () -> phase(s).equals("rest"), 60, "round 1 over", () -> {
                h.assertTrue(GiantsBridge.called("fightact health " + b.getUUID()), "healed between rounds");
                when(h, () -> fighting(s), 200, "round 2", () -> {
                    // round 2 goes to the loser's side, for the show: A's floor is the break point now
                    h.assertTrue("0.25".equals(tag(a, "jj_giants.fight.floor=")), "A may lose round 2: "
                            + tag(a, "jj_giants.fight.floor="));
                    hp(a, 0.2);
                    when(h, () -> phase(s).equals("rest"), 60, "round 2 over", () -> when(h, () -> fighting(s), 200,
                            "round 3", () -> {
                                JsonObject now = Fights.state(s, null).getAsJsonObject("now");
                                h.assertTrue(now.get("winsA").getAsInt() == 1 && now.get("winsB").getAsInt() == 1,
                                        "one each: " + now);
                                hp(b, 0.2);
                                when(h, () -> GiantsBridge.called("fightact yield " + b.getUUID() + " "
                                        + a.getUUID() + " bow") && phase(s).equals("after") || !FightScript.busy(), 80,
                                        "A takes the match", () -> done(h, at));
                            }));
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "f10b_tag")
    public void aTagTeamTwoAgainstOne(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 67);
        FightData.Setup d = script(s, "b", "bow");
        h(FightEdit.giant(s, d, "a", 2, "fakefight", 0.5, null));
        start(h, s, d, at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a1 = side(s, "a", 1), a2 = side(s, "a", 2), b = side(s, "b", 1);
            h.assertTrue(a1 != null && a2 != null && b != null, "three of them");
            h.assertTrue(GiantsBridge.called("fightact fight " + a1.getUUID()) && GiantsBridge.called(
                    "fightact fight " + a2.getUUID()), "both of side A go for B");
            hp(a1, 0.2);
            when(h, () -> GiantsBridge.called("fightact yield " + a1.getUUID()), 60, "A1 out of it", () -> {
                h.assertTrue(fighting(s), "his partner fights on");
                hp(a2, 0.2);
                when(h, () -> GiantsBridge.called("fightact yield " + a2.getUUID() + " " + b.getUUID() + " bow"), 60,
                        "B wins", () -> done(h, at));
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "f10b_stop")
    public void stopSendsEveryGiantHome(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 68);
        FightData.Setup d = script(s, "a", "death");
        h(FightEdit.set(d, "arena", "off"));
        start(h, s, d, at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            // the arena: a fighting giant breaks no blocks
            BlockPos stone = at.offset(30, -1, 30);
            h.assertTrue(h.getLevel().getBlockState(stone).is(Blocks.STONE), "stone there");
            h.assertFalse(Fights.blockChange(h.getLevel(), stone, Blocks.AIR.defaultBlockState(), a),
                    "arena off: the block stays");
            IronGolem other = GiantsBridge.spawn(h.getLevel(), at.getX() + 40, at.getY(), at.getZ());
            h.assertTrue(Fights.blockChange(h.getLevel(), stone, Blocks.AIR.defaultBlockState(), other),
                    "a giant not in it breaks as always");
            // only each other: another giant may not start a fight with them
            h.assertTrue(Fights.ok(s, ok(other, a, false)).get(0).equals("no"), "they are in a staged fight");
            AdminServer.Result r = FightScript.stop(s, "Tester");
            h.assertTrue(r.ok() && !FightScript.busy(), "stopped");
            for (IronGolem g : List.of(a, b)) {
                h.assertTrue(GiantsBridge.called("fightact stop " + g.getUUID()) && GiantsBridge.called(
                        "liveact home " + g.getUUID()), "stands down and goes home");
                h.assertTrue(tag(g, "jj_giants.fight.") == null, "tags off");
            }
            h.assertTrue(s.tickRateManager().tickrate() == 20f, "normal speed");
            done(h, at);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "f10b_players")
    public void betsSpectatorsAndJoiningASide(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 69);
        ServerPlayer p1 = FunGameTests.op(h, "Better1", at.offset(0, 0, 20)),
                p2 = FunGameTests.op(h, "Better2", at.offset(0, 0, 24));
        p1.getInventory().add(new ItemStack(Items.EMERALD, 10));
        p2.getInventory().add(new ItemStack(Items.EMERALD, 10));
        FightData.Setup d = script(s, "a", "bow");
        h(FightEdit.set(d, "bets", "on"));
        h(FightEdit.set(d, "join", "on"));
        start(h, s, d, at);
        when(h, () -> fighting(s), 200, "the fight", () -> {
            IronGolem a = side(s, "a", 1), b = side(s, "b", 1);
            h.assertTrue(FightScript.bet(s, p1, "a", 4).ok() && FightScript.bet(s, p2, "b", 6).ok(), "two bets");
            h.assertFalse(FightScript.bet(s, p1, "b", 1).ok(), "one bet each");
            h.assertTrue(p1.getInventory().countItem(Items.EMERALD) == 6, "the stake taken");
            // spectators are safe from the fight's giants; one who joins A is safe from A only
            h.assertFalse(net.jj.giants.fights.FightScript.allowForTests(p1, a), "a spectator is safe");
            h.assertTrue(FightScript.join(p1, "a").ok(), "joined A");
            h.assertFalse(FightScript.allowForTests(p1, a) || !FightScript.allowForTests(p1, b), "A's blows pass"
                    + " him by, B's don't");
            hp(b, 0.2);
            when(h, () -> !FightScript.busy(), 600, "the end", () -> {
                h.assertTrue(p1.getInventory().countItem(Items.EMERALD) == 16, "the winner takes the pot: "
                        + p1.getInventory().countItem(Items.EMERALD));
                h.assertTrue(p2.getInventory().countItem(Items.EMERALD) == 4, "the loser's stake is gone");
                FightData.Board row = FightData.get(s).s.bets.get(p1.getStringUUID());
                h.assertTrue(row != null && row.right == 1 && row.won == 6, "the betting board");
                done(h, at, p1, p2);
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10b_save")
    public void aSetupIsSavedByNameAndReplayedThere(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 70);
        ServerPlayer p = FunGameTests.op(h, "Saver", at);
        FightData.Setup d = FightData.get(s).draft(p.getStringUUID());
        h(FightEdit.giant(s, d, "a", 1, "fakefight", 0.4, p));
        h(FightEdit.giant(s, d, "b", 1, "fakefight", 0.6, p));
        h(FightEdit.moves(s, d, "a", 1, "stomp,slam"));
        h.assertFalse(FightEdit.moves(s, d, "a", 1, "moonwalk").ok(), "a move he hasn't");
        h(FightEdit.health(d, "b", 1, 60));
        h(FightEdit.set(d, "length", "epic"));
        h.assertTrue(FightEdit.save(s, p, "Harbour Showdown").ok(), "saved");
        FightData.Setup sv = FightData.get(s).saved("harbour showdown");
        h.assertTrue(sv != null && sv.length.equals("epic") && sv.fighters.size() == 2 && sv.fighters.get(1).health
                == 0.6 && (int) sv.x == at.getX(), "kept with its spot: " + FightData.GSON_FOR_TESTS.toJson(sv));
        FightData.get(s).s.drafts.clear();
        h.assertTrue(FightEdit.load(s, p, "Harbour Showdown").ok() && FightData.get(s).draft(p.getStringUUID())
                .length.equals("epic"), "loaded");
        AdminServer.Result r = FightScript.replay(s, "Tester", p.getUUID(), "Harbour Showdown");
        h.assertTrue(r.ok() && FightScript.busy(), "replayed: " + r.lines());
        h.assertFalse(FightEdit.delete(s, "Tester", "nothing").ok(), "no such setup");
        h.assertTrue(FightEdit.delete(s, "Tester", "Harbour Showdown").ok(), "deleted");
        done(h, at, p);
    }

    // ordinary fights

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "f10b_watch")
    public void aWildLoserIsChasedCaughtAndSpared(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 71);
        set(h, s, "all.wildEscape", "chased");
        set(h, s, "all.wildHeal", "full");
        IronGolem a = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5),
                b = GiantsBridge.spawn(h.getLevel(), at.getX() + 30.5, at.getY(), at.getZ() + 0.5);
        Fights.news(s, news(a, b, "start", false));
        h.assertTrue(FightWatch.followingForTests(a.getUUID(), b.getUUID()), "followed");
        hp(b, 0.2);
        Fights.news(s, news(b, a, "yield", false));
        h.assertTrue(GiantsBridge.called("fightact chase " + a.getUUID() + " " + b.getUUID()), "the winner goes after"
                + " him: " + GiantsBridge.calls);
        h.assertTrue(GiantsBridge.called("fightact health " + a.getUUID()), "healed after the fight");
        h.assertTrue(FightWatch.chaseForTests(b.getUUID()) == Boolean.FALSE, "chasing");
        a.teleportTo(b.getX() + 2, b.getY(), b.getZ());
        when(h, () -> FightWatch.chaseForTests(b.getUUID()) == null, 60, "caught and spared", () -> {
            h.assertTrue(GiantsBridge.called("fightact stop " + a.getUUID()), "he lets him go");
            h.assertTrue(b.isAlive(), "spared");
            done(h, at);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "f10b_owned")
    public void ownedGiantsRulesAndStandDown(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = FunGameTests.spot(h, 72);
        IronGolem a = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5),
                b = GiantsBridge.spawn(h.getLevel(), at.getX() + 30.5, at.getY(), at.getZ() + 0.5);
        String oa = java.util.UUID.randomUUID().toString(), ob = java.util.UUID.randomUUID().toString();
        a.addTag(GiantsBridge.TAG + ".owner=" + oa);
        b.addTag(GiantsBridge.TAG + ".owner=" + ob);
        net.jj.giants.fights.FightGiants.freshRows();
        h.assertTrue(Fights.ok(s, ok(a, b, true)).equals(List.of("ok")), "as shipped owned giants may fight");
        set(h, s, "all.ownedMayFight", "never");
        h.assertTrue(Fights.ok(s, ok(a, b, true)).get(0).equals("no"), "never");
        set(h, s, "all.ownedMayFight", "both_ok");
        FightData.get(s).s.allow.add(oa);
        h.assertTrue(Fights.ok(s, ok(a, b, true)).get(0).equals("no"), "one owner said yes");
        FightData.get(s).s.allow.add(ob);
        h.assertTrue(Fights.ok(s, ok(a, b, true)).equals(List.of("ok")), "both said yes");
        set(h, s, "all.fightsSleepers", "false");
        b.addTag(GiantsBridge.TAG + ".asleep");
        net.jj.giants.fights.FightGiants.freshRows();
        h.assertTrue(Fights.ok(s, ok(a, b, true)).get(0).equals("no"), "never with a sleeper");
        b.removeTag(GiantsBridge.TAG + ".asleep");
        set(h, s, "all.fightsSleepers", "true");
        set(h, s, "all.ownedRest", "5");
        set(h, s, "all.ownedNews", "true");
        Fights.news(s, news(a, b, "start", true));
        h.assertTrue(FightWatch.followingForTests(a.getUUID(), b.getUUID()), "followed");
        FightWatch.standDownForTests(s, a);
        h.assertTrue(GiantsBridge.called("fightact stop " + a.getUUID()) && GiantsBridge.called("fightact stop "
                + b.getUUID()), "both stand down");
        h.assertFalse(FightWatch.followingForTests(a.getUUID(), b.getUUID()), "over");
        h.assertTrue(Fights.ok(s, ok(a, b, true)).get(0).equals("no"), "resting before the next");
        done(h, at);
    }
}
