package net.jj.giants.test;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakeadv.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.adventure.AdvData;
import net.jj.giants.adventure.AdvMaps;
import net.jj.giants.adventure.Adventure;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.minigames.AdvMode;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.MgSnap;
import net.jj.giants.minigames.Minigames;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/**
 * Part 11 (ADMIN_BRIDGE.md §28): adventure maps with a stand-in giant (net.jj.fakeadv). A script walks its path and
 * waits for a player to come into an area; every hook event runs its command exactly once; a box of the world saved
 * as a map (blocks, a chest, a scripted giant, his hook) is played as a minigame round, its hook ends it and the
 * player gets everything back; the built-in Wreck Cove Rescue starts with both its scripted giants. Each its own batch.
 */
public class AdventureGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakeadv", "fakeadv", "fakeadv", "net.jj.fakeadv.GiantsBridge", List.of(),
            Set.of());

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        AdminServer.useGiants(List.of(FAKE));
        FunGiants.TEST_KINDS.put(GiantsBridge.TAG, "fakeadv");
        GiantsBridge.reset();
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "fakeadv")) w.clear(g);
        AdminServer.push(s, w);
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        MgData.get(s).s = new MgData.State();
        MinigameGameTests.set(h, s, "mgSize", "small");
        MinigameGameTests.set(h, s, "mgLobby", "10");
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... ps) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        for (ServerPlayer p : ps) {
            if (s.getPlayerList().getPlayer(p.getUUID()) != null) s.getPlayerList().remove(p);
            MgSnap.delete(s, p.getUUID());
        }
        for (IronGolem g : GiantsBridge.golems(s)) g.discard();
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "fakeadv")) w.clear(g);
        AdminServer.push(s, w);
        AdminServer.useGiants(null);
        FunGiants.TEST_KINDS.remove(GiantsBridge.TAG);
        h.succeed();
    }

    static AdvData.Run run(MinecraftServer s, IronGolem g) {
        return AdvData.get(s).s.runs.get(g.getStringUUID());
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000, batch = "adv_script")
    public void scriptWalksItsPathThenWaitsForAPlayerInTheArea(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        String dim = l.dimension().location().toString();
        for (int cx = (o.getX() >> 4) - 1; cx <= ((o.getX() + 112) >> 4); cx++)
            for (int cz = (o.getZ() >> 4) - 1; cz <= (o.getZ() >> 4) + 1; cz++) l.setChunkForced(cx, cz, true);
        IronGolem g = GiantsBridge.spawn(l, o.getX() + 0.5, floor(l, o), o.getZ() + 0.5);
        g.addTag("advwalker");
        // a path of three points east, an area further on, and the script
        for (int i = 0; i < 3; i++)
            h.assertTrue(Adventure.pathAdd(s, "Tester", "east", dim, o.getX() + 0.5 + 40 * i, o.getY(), o.getZ() + 0.5)
                    .ok(), "point " + i);
        h.assertTrue(Adventure.areaSet(s, "Tester", "gate", dim, o.offset(90, -2, -6), o.offset(100, 6, 6)).ok(),
                "the area");
        for (String step : List.of("walk east", "waitfor area gate", "say You made it", "mood hunting"))
            h.assertTrue(Adventure.scriptStep(s, "Tester", "patrol1", 0, step).ok(), step);
        h.assertTrue(!Adventure.scriptStep(s, "Tester", "patrol1", 0, "dance wildly").ok(), "a bad step refused");
        h.assertTrue(Adventure.give(s, "Tester", 2, g, "patrol1").ok(), "given");
        // a player near (keeps the land loaded), not in the area yet
        ServerPlayer p = MinigameGameTests.join(h, "advwalk", false).p();
        p.teleportTo(l, o.getX() + 40, o.getY() + 1, o.getZ() + 20, 0, 0);
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(run(s, g).step == 1, "walked the path: step " + run(s, g).step
                        + ", at x " + Math.round(g.getX() - o.getX())))
                .thenExecute(() -> {
                    h.assertTrue(g.getX() - o.getX() > 80 - 18, "at the path's end: " + (g.getX() - o.getX()));
                    h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.contains("\"do\":\"go\"")),
                            "walked by his own go");
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    h.assertTrue(run(s, g).step == 1 && !run(s, g).done, "still waiting for the area");
                    p.teleportTo(l, o.getX() + 95, o.getY() + 1, o.getZ(), 0, 0);
                })
                .thenWaitUntil(() -> h.assertTrue(run(s, g).done, "the area let him carry on"))
                .thenExecute(() -> {
                    h.assertTrue("hunting".equals(GiantsBridge.mood.get(g.getStringUUID())), "mood hunting");
                    h.assertTrue(g.getTags().contains("jj_giants.scripted"), "marked scripted");
                    h.assertTrue(Adventure.take(s, "Tester", g).ok(), "taken off");
                    h.assertTrue(run(s, g) == null && !g.getTags().contains("jj_giants.scripted"), "free again");
                    for (int cx = (o.getX() >> 4) - 1; cx <= ((o.getX() + 112) >> 4); cx++)
                        for (int cz = (o.getZ() >> 4) - 1; cz <= (o.getZ() >> 4) + 1; cz++)
                            l.setChunkForced(cx, cz, false);
                    done(h, p);
                });
    }

    /** the top of the ground at the test's corner (stand-ins stand on it, not in it) */
    static int floor(ServerLevel l, BlockPos o) {
        return l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, o.getX(), o.getZ());
    }

    static int score(MinecraftServer s, String holder) {
        Objective o = s.getScoreboard().getObjective("advt");
        var sc = o == null ? null : s.getScoreboard().getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), o);
        return sc == null ? 0 : sc.value();
    }

    static final List<String> EVENTS = List.of("sleeps", "wakes", "fight", "calm", "tamed", "half", "enters",
            "leaves", "sees", "dies");

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000, batch = "adv_hooks")
    public void everyHookEventRunsItsCommandOnce(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        String dim = l.dimension().location().toString();
        var sb = s.getScoreboard();
        if (sb.getObjective("advt") != null) sb.removeObjective(sb.getObjective("advt"));
        sb.addObjective("advt", ObjectiveCriteria.DUMMY, Component.literal("advt"), ObjectiveCriteria.RenderType.INTEGER,
                false, null);
        h.assertTrue(Adventure.areaSet(s, "Tester", "pen", dim, o.offset(14, -10, -4), o.offset(20, 60, 4)).ok(), "area");
        for (int cx = (o.getX() >> 4) - 1; cx <= (o.getX() >> 4) + 2; cx++)
            for (int cz = (o.getZ() >> 4) - 1; cz <= (o.getZ() >> 4) + 1; cz++) l.setChunkForced(cx, cz, true);
        for (String e : EVENTS) {
            String ev = e.equals("enters") ? "enters.pen" : e;
            AdminServer.Result r = Adventure.hookAdd(s, "Tester", 2, "advhk", ev, "scoreboard players add " + e
                    + " advt 1");
            h.assertTrue(r.ok(), "hook " + e + ": " + r.lines());
        }
        h.assertTrue(!Adventure.hookAdd(s, "Tester", 2, "advhk", "explodes", "say x").ok(), "unknown event refused");
        h.assertTrue(Adventure.scores(s, "Tester", true).ok(), "scoreboard on");
        int fy = floor(l, o);
        IronGolem g = GiantsBridge.spawn(l, o.getX() + 0.5, fy, o.getZ() + 0.5);
        g.addTag("advhk");
        String id = g.getStringUUID();
        ServerPlayer[] p = {null};
        Runnable none = () -> {};
        h.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    for (String e : EVENTS) h.assertTrue(score(s, e) == 0, "nothing before: " + e);
                    GiantsBridge.asleep.add(id);
                })
                .thenIdle(25)
                .thenExecute(() -> GiantsBridge.asleep.remove(id))
                .thenIdle(25)
                .thenExecute(() -> GiantsBridge.fighting.add(id))
                .thenIdle(25)
                .thenExecute(() -> GiantsBridge.fighting.remove(id))
                .thenIdle(25)
                .thenExecute(() -> GiantsBridge.owner.put(id, "Steve"))
                .thenIdle(25)
                .thenExecute(() -> g.setHealth(90f))
                .thenIdle(25)
                .thenExecute(() -> g.teleportTo(o.getX() + 17.5, fy, o.getZ() + 0.5))
                .thenIdle(25)
                .thenExecute(() -> g.teleportTo(o.getX() + 0.5, fy, o.getZ() + 0.5))
                .thenIdle(25)
                .thenExecute(() -> {
                    p[0] = MinigameGameTests.join(h, "advsee", false).p();
                    p[0].teleportTo(l, o.getX() + 6.5, fy, o.getZ() + 0.5, 90, 0);
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    Objective hp = sb.getObjective("giants_health");
                    var sc = hp == null ? null : sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(g.getScoreboardName()), hp);
                    int want = Math.round(g.getHealth() / g.getMaxHealth() * 100);
                    h.assertTrue(sc != null && Math.abs(sc.value() - want) <= 1 && sc.value() < 50,
                            "health % on the scoreboard: " + (sc == null ? "-" : sc.value()) + " vs " + want);
                    g.kill();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    for (String e : EVENTS) h.assertTrue(score(s, e) == 1, "ran once: " + e + " = " + score(s, e));
                    Objective ev = sb.getObjective("giants_events");
                    var d = ev == null ? null : sb.getPlayerScoreInfo(ScoreHolder.forNameOnly("dies"), ev);
                    h.assertTrue(d != null && d.value() >= 1, "events counted on the scoreboard");
                    h.assertTrue(AdvData.get(s).s.hooks.stream().allMatch(x -> x.fired == 1), "each hook fired once");
                    Adventure.scores(s, "Tester", false);
                    h.assertTrue(sb.getObjective("giants_health") == null, "scoreboard off");
                    sb.removeObjective(sb.getObjective("advt"));
                    for (int cx = (o.getX() >> 4) - 1; cx <= (o.getX() >> 4) + 2; cx++)
                        for (int cz = (o.getZ() >> 4) - 1; cz <= (o.getZ() >> 4) + 1; cz++)
                            l.setChunkForced(cx, cz, false);
                    none.run();
                    done(h, p[0]);
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "adv_map")
    public void aSavedMapIsPlayedAndItsHookEndsTheRound(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(new BlockPos(0, 0, 0));
        int y0 = o.getY() - 1;
        // a little place: a stone floor, a gold block, a chest with a diamond in it
        for (int x = 0; x < 12; x++)
            for (int z = 0; z < 12; z++) {
                l.setBlockAndUpdate(new BlockPos(o.getX() + x, y0, o.getZ() + z), Blocks.STONE.defaultBlockState());
                for (int y = 1; y <= 5; y++) l.setBlockAndUpdate(new BlockPos(o.getX() + x, y0 + y, o.getZ() + z),
                        Blocks.AIR.defaultBlockState());
            }
        l.setBlockAndUpdate(new BlockPos(o.getX() + 3, y0 + 1, o.getZ() + 4), Blocks.GOLD_BLOCK.defaultBlockState());
        BlockPos chest = new BlockPos(o.getX() + 6, y0 + 1, o.getZ() + 6);
        l.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((ChestBlockEntity) l.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 3));
        IronGolem g = GiantsBridge.spawn(l, o.getX() + 8.5, y0 + 1, o.getZ() + 8.5);
        g.addTag("advm");
        h.assertTrue(Adventure.scriptStep(s, "Tester", "greeter", 0, "say Welcome to the test map").ok(), "step 1");
        h.assertTrue(Adventure.scriptStep(s, "Tester", "greeter", 0, "waitfor players 300").ok(), "step 2");
        h.assertTrue(Adventure.give(s, "Tester", 2, g, "greeter").ok(), "given");
        h.assertTrue(Adventure.hookAdd(s, "Tester", 2, "advm", "dies", "giants adventure win Golem down").ok(), "hook");
        h.assertTrue(Adventure.hookAdd(s, "Tester", 2, "all", "dies", "say a world-wide hook").ok(), "a world hook");
        h.assertTrue(AdvMaps.save(s, null, "testmap", new BlockPos(o.getX(), y0 - 1, o.getZ()),
                new BlockPos(o.getX() + 11, y0 + 5, o.getZ() + 11)).ok(), "saving");
        MinigameGameTests.Joined[] j = {null};
        String[] inv = {""};
        Vec3[] at = {null};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(!AdvMaps.saving(), "saved"))
                .thenExecute(() -> {
                    AdvData.MapInfo info = AdvData.get(s).s.maps.get("testmap");
                    h.assertTrue(info != null && info.giants == 1, "the map, one giant: " + (info == null ? "-"
                            : info.giants));
                    try {
                        h.assertTrue(Files.exists(s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                                .resolve("jj_giants_adventures/testmap.nbt")), "its file");
                    } catch (RuntimeException e) {
                        throw e;
                    }
                    j[0] = MinigameGameTests.join(h, "advmapper", false);
                    MinigameGameTests.kit(j[0].p(), o.offset(-4, 0, -4));
                    inv[0] = MinigameGameTests.items(j[0].p());
                    at[0] = j[0].p().position();
                    AdminServer.Result r = Adventure.play(s, "Tester", "testmap", false, 1);
                    h.assertTrue(r.ok(), "opened: " + r.lines());
                    h.assertTrue(Minigames.join(j[0].p(), false).ok(), "joined");
                    Minigames.now(s, "Tester");
                })
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing: " + game().phase()))
                .thenExecute(() -> MinigameGameTests.arrived(j[0].p()))
                .thenWaitUntil(() -> h.assertTrue(!game().giants.standing().isEmpty(), "his summon came"))
                .thenWaitUntil(() -> {
                    var rs = Adventure.roundState();
                    h.assertTrue(rs != null && rs.runs.size() == 1, "he runs his script in the round");
                })
                .thenExecute(() -> {
                    ServerLevel rl = game().level;
                    ours[0] = new java.lang.ref.WeakReference<>(rl);
                    // (lagfix) the round's world sends and moves only its map's land, not the server's whole distance
                    int reach = net.jj.giants.minigames.MgLevels.reachOf(rl);
                    h.assertTrue(reach >= 3 && reach <= 14, "the round's world's own view distance: " + reach);
                    String gold = "none";
                    for (BlockPos b : BlockPos.betweenClosed(-12, 50, -12, 12, 80, 12))
                        if (rl.getBlockState(b).is(Blocks.GOLD_BLOCK)) gold = b.toShortString();
                    h.assertTrue(rl.getBlockState(new BlockPos(-3, 64, -2)).is(Blocks.GOLD_BLOCK), "the gold block: "
                            + rl.getBlockState(new BlockPos(-3, 64, -2)) + ", gold at " + gold);
                    h.assertTrue(rl.getBlockState(new BlockPos(-3, 63, -2)).is(Blocks.STONE), "the floor");
                    h.assertTrue(rl.getBlockEntity(new BlockPos(0, 64, 0)) instanceof ChestBlockEntity c
                            && c.getItem(0).is(Items.DIAMOND) && c.getItem(0).getCount() == 3, "the chest and its"
                            + " diamonds");
                    var k = game().giants.standing().get(0);
                    var e = game().giants.entity(k);
                    h.assertTrue(e != null && e.getTags().contains("advm"), "his tag came with him");
                    h.assertTrue(Adventure.roundState().hooks.size() == 1, "only his hook came with the map");
                    e.kill();
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "the hook ended it"))
                .thenExecute(() -> {
                    var res = MgData.get(s).s.results;
                    var r = res.get(res.size() - 1);
                    h.assertTrue(r.line.contains("Golem down"), "the hook's words: " + r.line);
                    h.assertTrue(r.winners.contains("advmapper"), "the player won");
                    h.assertTrue(net.jj.giants.players.Challenges.done(j[0].p(), "adventurer"),
                            "winning an adventure map gives the Adventurer challenge");
                    MinigameGameTests.same(h, j[0].p(), inv[0], at[0], "after the adventure");
                    h.assertTrue(Adventure.roundState() == null, "the round's scripts and hooks are gone");
                    h.assertTrue(AdvMaps.delete(s, "Tester", "testmap").ok(), "deleted");
                    done(h, j[0].p());
                });
    }

    static MgGame game() {
        return MinigameGameTests.game();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "adv_cove")
    public void theWreckCoveRescueStartsWithBothScriptedGiants(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        MinigameGameTests.Joined j = MinigameGameTests.join(h, "advcove", false);
        @SuppressWarnings("unchecked")
        java.lang.ref.WeakReference<ServerLevel>[] ours = new java.lang.ref.WeakReference[] {
                new java.lang.ref.WeakReference<ServerLevel>(null)};
        // (1.16) the map's own small giant size, so both fit the cove; a smaller game size still wins
        if (AdvMaps.load(s, AdvMaps.BUILT_IN, 1, 0.3) instanceof AdvMaps.Loaded big)
            for (AdvMaps.MapGiant mg : big.giants)
                h.assertTrue(mg.size <= 0.08 + 1e-9, mg.kind + " fits the cove: " + mg.size);
        else h.fail("the cove loads");
        if (AdvMaps.load(s, AdvMaps.BUILT_IN, 1, 0.05) instanceof AdvMaps.Loaded small)
            h.assertTrue(small.giants.stream().allMatch(mg -> mg.size <= 0.05 + 1e-9), "a smaller game size wins");
        h.assertTrue(Adventure.play(s, "Tester", AdvMaps.BUILT_IN, false, 1).ok(), "opened");
        h.assertTrue(Minigames.join(j.p(), false).ok(), "joined");
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing: " + game().phase()))
                .thenExecute(() -> MinigameGameTests.arrived(j.p()))
                .thenWaitUntil(() -> {
                    var rs = Adventure.roundState();
                    h.assertTrue(rs != null && rs.runs.size() == 2, "both giants run their scripts");
                })
                .thenExecute(() -> {
                    var rs = Adventure.roundState();
                    h.assertTrue(rs.scripts.keySet().containsAll(List.of("lost_giant", "cove_guard")), "the scripts");
                    h.assertTrue(rs.hooks.size() == 5, "the five hooks");
                    h.assertTrue(rs.areas.keySet().containsAll(List.of("cove", "harbour")), "the areas");
                    ServerLevel rl = game().level;
                    h.assertTrue(rl.getBlockState(new BlockPos(-76, 63, 0)).is(Blocks.SPRUCE_PLANKS), "the quay: "
                            + rl.getBlockState(new BlockPos(-76, 63, 0)));
                    h.assertTrue(j.p().getX() < -60, "the player starts at the harbour: " + j.p().getX());
                    boolean lost = false, guard = false;
                    for (var k : game().giants.standing()) {
                        var e = game().giants.entity(k);
                        if (e == null) continue;
                        lost |= e.getTags().contains("adv_lost") && e.getTags().contains(AdvMaps.PROTECT);
                        guard |= e.getTags().contains("adv_guard");
                    }
                    h.assertTrue(lost && guard, "the lost giant (kept safe from players) and the guard");
                })
                .thenWaitUntil(() -> h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.contains("\"do\":\"turn\"")
                        || c.contains("patrol") || c.contains("\"do\":\"go\"")), "the guard patrols"))
                .thenExecute(() -> h.assertTrue(AdvMode.end(true, "Home safe (test)").ok(), "won by command"))
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "over"))
                .thenExecute(() -> {
                    var res = MgData.get(s).s.results;
                    h.assertTrue(res.get(res.size() - 1).line.contains("Home safe"), "the line");
                })
                // (lagfix) the round's world thrown away leaves nothing behind: not in the server's worlds, not
                // ticking, no adventure round, and nothing holding on to it once the garbage is collected
                .thenWaitUntil(() -> h.assertTrue(net.jj.giants.minigames.MgLevels.standing(s).isEmpty(),
                        "its world thrown away"))
                .thenExecute(() -> h.assertTrue(Adventure.roundState() == null, "no adventure round left"))
                // (a collection now and then until it's gone: the timeout is the failure, a world something keeps)
                .thenWaitUntil(() -> {
                    if (s.getTickCount() % 20 == 0) System.gc();
                    ServerLevel held = ours[0].get();
                    // (what holds it, in the log, now and then: the way to the leak)
                    if (held != null && s.getTickCount() % 1000 == 0)
                        for (String path : LeakScan.paths(s, held, 3, 5_000_000))
                            net.jj.giants.GiantsGuideMod.LOG.warn("still held: {}", path);
                    h.assertTrue(held == null, "nothing still holds the round's thrown-away world");
                })
                .thenExecute(() -> done(h, j.p()));
    }
}
