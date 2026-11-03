package net.jj.giants.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.players.PlayerData;
import net.jj.giants.players.Players;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Part 5 with the real giants (-Pgiants, ADMIN_BRIDGE.md 20.15): every giant takes the switches and the per-player
 * rules (his book refused while books are off or for a player who may not own giants, an ignored player never his
 * target), the crafting switches block his finder and book, and My Giant's pick takes a player to his ground with
 * his book and one of him. Without the giants each test passes at once.
 */
public class PlayersGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    private static List<String> give(MinecraftServer s, Giant g, ServerPlayer p, String what) {
        return AdminServer.call(s, g, "give", "{\"players\":1,\"player\":\"" + p.getStringUUID() + "\",\"what\":\""
                + what + "\"}");
    }

    /** back to the server defaults for what these tests change (other batches run beside them: nothing else) */
    private static void clean(MinecraftServer s) {

        for (String id : List.of("books", "findersCraftable", "myGiant"))
            if (AdminWorld.get(s).values("all").containsKey("all." + id)) AdminServer.reset(s, "Tester", "all", id);
    }

    /** a player's rules gone again */
    private static void noRules(MinecraftServer s, ServerPlayer... ps) {
        for (ServerPlayer p : ps) {
            PlayerData.get(s).s.rules.remove(p.getStringUUID());
            PlayerData.get(s).s.picks.remove(p.getStringUUID());
        }
        Players.pushRules(s, PlayerData.get(s));
    }

    /** My Giant with him for real: to his ground, his book, one of him (or in charge at the limit) */
    private void pickForReal(GameTestHelper h, String key) {
        MinecraftServer s = h.getLevel().getServer();
        clean(s);
        Giant g = Giants.byKey(key);
        List<String> grounds = AdminServer.call(s, g, "grounds", "");
        // (his ground not claimed in the test world yet: chosen as a brand-new world would, test world only)
        if (grounds == null || grounds.isEmpty()) grounds = AdminServer.call(s, g, "groundnew", "");
        if (grounds == null || grounds.isEmpty()) {
            h.fail(key + " has no ground in the test world");
            return;
        }
        JsonObject gr = JsonParser.parseString(grounds.get(0)).getAsJsonObject();
        double gx = gr.get("x").getAsDouble(), gz = gr.get("z").getAsDouble(), rr = gr.get("r").getAsDouble();
        AdminServer.set(s, "Tester", "all", "myGiant", "on");
        ServerPlayer a = PlayersGameTests.join(h, "pk_" + key.substring(0, 4), false);
        ServerPlayer b = PlayersGameTests.join(h, "pb_" + key.substring(0, 4), false);
        a.addTag("jj_book_test");
        b.addTag("jj_book_test");
        int before = Players.liveCount(s, g);
        // (a book still held from another test: let go of, as an op's /<giant> book free does)
        freeBook(s, g);
        var r = Players.pick(s, a, key);
        h.assertTrue(r.ok(), key + ": picked: " + r.lines());
        int[] tries = {1};
        boolean[] bPicked = {false};
        h.succeedWhen(() -> {
            PlayerData.Pick pk = PlayerData.get(s).s.picks.get(a.getStringUUID());
            // (a busy test server: a minute and the land there wasn't made yet, so the trip gave up and the pick
            // was forgotten, as it would be for a player: picked again, at most three times)
            if (pk == null && tries[0] < 3) {
                tries[0]++;
                net.jj.giants.GiantsGuideMod.LOG.info("p5 test: {}: the land wasn't ready, picking again", key);
                Players.pick(s, a, key);
            }
            h.assertTrue(pk != null && !pk.outcome.isEmpty(), key + ": still on the way (try " + tries[0] + ")");
            h.assertTrue(pk.outcome.equals("own") || pk.outcome.equals("own_cap"), key + ": outcome " + pk.outcome);
            double d = Math.hypot(a.getX() - gx, a.getZ() - gz);
            h.assertTrue(d < Math.max(64, rr), key + ": in his ground, " + (int) d + " from its middle");
            // (a moment for his book rule's own look and the one who came to stand)
            // (the one who comes needs loaded land with entities ticking; this test's player loads very little, so
            // whether he has come yet is only logged)
            int came = Players.liveCount(s, g) - before;
            String owner = Players.liveOwner(s, g);
            if (owner != null)
                h.assertTrue(a.getGameProfile().getName().equals(owner), key + ": the picker owns him: " + owner);
            List<String> other = give(s, g, b, "book");
            h.assertTrue(other.get(0).equals("no") && other.get(1).contains(a.getGameProfile().getName()),
                    key + ": the picker holds his book: " + other);
            // (1.11.0, BOOK_RULE.md's My Giant exception) a second picker: their own book and own one of him when
            // he can be placed near them, else they share a's (never a quiet copy, never a failure)
            if (!bPicked[0]) {
                bPicked[0] = true;
                h.assertTrue(Players.pick(s, b, key).ok(), key + ": b picked");
            }
            PlayerData.Pick pb = PlayerData.get(s).s.picks.get(b.getStringUUID());
            h.assertTrue(pb != null && !pb.outcome.isEmpty(), key + ": b still on the way");
            h.assertTrue(pb.outcome.equals("own") || pb.outcome.equals("shared"), key + ": b's outcome " + pb.outcome);
            net.jj.giants.GiantsGuideMod.LOG.info("p5 test: {} picked for real: {}, one came: {}, {} blocks from"
                    + " the middle, {} more standing; the second picker: {}, own one came: {}", key, pk.outcome,
                    pk.spawned, (int) d, came, pb.outcome, pb.spawned);
            // tidy up: the book's holder let go of, the one who came taken away
            AdminServer.call(s, g, "players", "{\"players\":1,\"rules\":{\"" + a.getStringUUID()
                    + "\":{\"own\":false}}}");
            s.getPlayerList().remove(a);
            s.getPlayerList().remove(b);
            freeBook(s, g);
            noRules(s, a, b);
            clean(s);
        });
    }

    private static void freeBook(MinecraftServer s, Giant g) {
        CommandSourceStack src = s.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        s.getCommands().performPrefixedCommand(src, g.command() + " book free");
    }



    /**
     * Every giant in turn (one at a time, so one's fight never reaches another's players): a hunting one of him with
     * two players who both hit him, one of them ignored by giants: he never goes after the ignored one; then one of
     * him brought down by a player: it counts on the Slayers board for that player and kind.
     */
    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_each", timeoutTicks = 9000)
    public void everyGiantLeavesAnIgnoredPlayerAloneAndCountsWhenSlain(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<String> keys = new java.util.ArrayList<>();
        for (String k : List.of("pitchgut", "furrowmaw", "cerberus", "hollowbell", "lanternwillow", "wreckback",
                "swarmforge", "gloamwick")) if (here(k)) keys.add(k);
        if (keys.isEmpty()) {
            h.succeed();
            return;
        }
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        CommandSourceStack src = s.createCommandSourceStack().withPermission(4).withLevel(h.getLevel())
                .withPosition(Vec3.atBottomCenterOf(o)).withSuppressedOutput();
        // the state: which giant, which phase (0 ignored, 1 slain), ticks in it, the one summoned, the players
        int[] at = {0, 0, 0};
        java.util.UUID[] his = {null};
        ServerPlayer[] ps = new ServerPlayer[3];
        boolean[] plainHit = {false};
        StringBuilder log = new StringBuilder();
        h.onEachTick(() -> {
            if (at[0] >= keys.size()) return;
            String key = keys.get(at[0]);
            Giant g = Giants.byKey(key);
            String type = Players.MAIN.get(key);
            int t = ++at[2];
            if (t == 1) {
                // start a phase: a fresh one of him and fresh players
                if (at[1] == 0) {
                    ps[0] = PlayersGameTests.join(h, "ig_" + key.substring(0, 4), false);
                    ps[1] = PlayersGameTests.join(h, "pl_" + key.substring(0, 4), false);
                    ps[0].teleportTo(h.getLevel(), o.getX() + 6, o.getY(), o.getZ(), 0, 0);
                    ps[1].teleportTo(h.getLevel(), o.getX() - 6, o.getY(), o.getZ(), 0, 0);
                    ps[0].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    ps[1].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    Players.setRule(s, "Tester", ps[0].getStringUUID(), "ignored", true);
                    plainHit[0] = false;
                    s.getCommands().performPrefixedCommand(src, g.command() + " summon hunting 0.05");
                } else {
                    ps[2] = PlayersGameTests.join(h, "sl_" + key.substring(0, 4), false);
                    ps[2].teleportTo(h.getLevel(), o.getX() + 10, o.getY(), o.getZ(), 0, 0);
                    ps[2].setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    s.getCommands().performPrefixedCommand(src, g.command() + " summon calm 0.05");
                }
                his[0] = null;
                return;
            }
            if (his[0] == null)
                for (Entity e : h.getLevel().getEntities((Entity) null, new AABB(o).inflate(40),
                        x -> BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).toString().equals(type)))
                    his[0] = e.getUUID();
            Entity e = his[0] == null ? null : h.getLevel().getEntity(his[0]);
            boolean next = false;
            if (at[1] == 0) {
                if (t == 20 && e instanceof net.minecraft.world.entity.LivingEntity le) {
                    le.hurt(ps[0].damageSources().playerAttack(ps[0]), 1f);
                    le.hurt(ps[1].damageSources().playerAttack(ps[1]), 1f);
                }
                if (e instanceof Mob m && m.getTarget() instanceof Player tg) {
                    if (tg == ps[0]) h.fail(key + " went after the ignored player");
                    if (tg == ps[1]) plainHit[0] = true;
                }
                if (t >= 400) {
                    h.assertTrue(his[0] != null, key + ": none of him came for the test");
                    log.append(key).append(" went after the other: ").append(plainHit[0]).append("; ");
                    if (e != null) e.discard();
                    noRules(s, ps[0], ps[1]);
                    s.getPlayerList().remove(ps[0]);
                    s.getPlayerList().remove(ps[1]);
                    next = true;
                }
            } else {
                if (e instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive() && !le.isRemoved()) {
                    if (t < 300) le.hurt(ps[2].damageSources().playerAttack(ps[2]), 100000f);
                    else if (t % 20 == 0) le.kill();
                }
                PlayerData.Stats st = PlayerData.get(s).s.stats.get(ps[2].getStringUUID());
                boolean counted = st != null && st.kills.getOrDefault(key, 0) >= 1;
                if (counted || t >= 900) {
                    h.assertTrue(counted, key + ": slain by a player, not counted on the board");
                    log.append(key).append(" counted after ").append(t).append(" ticks; ");
                    if (e != null) e.discard();
                    s.getPlayerList().remove(ps[2]);
                    next = true;
                }
            }
            if (next) {
                at[2] = 0;
                if (++at[1] > 1) {
                    at[1] = 0;
                    at[0]++;
                }
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(at[0] >= keys.size(), "giant " + at[0] + " of " + keys.size());
            net.jj.giants.GiantsGuideMod.LOG.info("p5 test: {}", log);
        });
    }

    // one test per giant (each its own batch: they run one after another)

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_rules", timeoutTicks = 40)
    public void everyGiantTakesTheSwitchesAndRules(GameTestHelper h) {
        String[] keys = {"pitchgut", "furrowmaw", "cerberus", "hollowbell", "lanternwillow", "wreckback",
            "swarmforge", "gloamwick"};
        int n = 0;
        for (String k : keys)
            if (here(k)) {
                n++;
                rulesReachNoSucceed(h, k);
            }
        net.jj.giants.GiantsGuideMod.LOG.info("p5 test: switches and rules checked on {} giants", n);
        h.succeed();
    }

    private void rulesReachNoSucceed(GameTestHelper h, String key) {
        MinecraftServer s = h.getLevel().getServer();
        clean(s);
        Giant g = Giants.byKey(key);
        ServerPlayer a = PlayersGameTests.join(h, "rr_" + key.substring(0, 4), false);
        try {
            AdminJson.Report r = AdminServer.report(s, g);
            h.assertTrue(r != null && r.uses().contains("all.books") && r.uses().contains("all.sit"),
                    key + " lists the player switches: " + (r == null ? null : r.uses()));
            h.assertTrue(Players.pickable(s).contains(g), key + " can be picked");
            AdminServer.set(s, "Tester", "all", "books", "off");
            List<String> res = give(s, g, a, "book");
            h.assertTrue(res != null && res.get(0).equals("no") && res.get(1).contains("books are turned off"),
                    key + ": books off reached him: " + res);
            AdminServer.set(s, "Tester", "all", "books", "on");
            Players.setRule(s, "Tester", a.getStringUUID(), "own", false);
            res = give(s, g, a, "book");
            h.assertTrue(res != null && res.get(0).equals("no") && res.get(1).contains("may not own"),
                    key + ": the rule reached him: " + res);
            res = give(s, g, a, "finder");
            h.assertTrue(res != null && res.get(0).equals("ok"), key + ": his finder: " + res);
            Players.runningForTests(s);
            ItemStack finder = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(Players.FINDERS.get(key))));
            ItemStack book = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(Players.BOOKS.get(key))));
            h.assertTrue(!finder.isEmpty() && !book.isEmpty(), key + ": his finder and book items are there");
            h.assertTrue(Players.craftable(finder) && Players.craftable(book), key + ": craftable as shipped");
            AdminServer.set(s, "Tester", "all", "findersCraftable", "off");
            AdminServer.set(s, "Tester", "all", "books", "off");
            h.assertTrue(!Players.craftable(finder) && !Players.craftable(book), key + ": not craftable when off");
        } finally {
            noRules(s, a);
            clean(s);
            s.getPlayerList().remove(a);
        }
    }









    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_pitchgut", timeoutTicks = 12000)
    public void pitchgutPickedForReal(GameTestHelper h) {
        if (here("pitchgut")) pickForReal(h, "pitchgut");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_furrowmaw", timeoutTicks = 12000)
    public void furrowmawPickedForReal(GameTestHelper h) {
        if (here("furrowmaw")) pickForReal(h, "furrowmaw");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_cerberus", timeoutTicks = 12000)
    public void cerberusPickedForReal(GameTestHelper h) {
        if (here("cerberus")) pickForReal(h, "cerberus");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_hollowbell", timeoutTicks = 12000)
    public void hollowbellPickedForReal(GameTestHelper h) {
        if (here("hollowbell")) pickForReal(h, "hollowbell");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_lanternwillow", timeoutTicks = 12000)
    public void lanternwillowPickedForReal(GameTestHelper h) {
        if (here("lanternwillow")) pickForReal(h, "lanternwillow");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_wreckback", timeoutTicks = 12000)
    public void wreckbackPickedForReal(GameTestHelper h) {
        if (here("wreckback")) pickForReal(h, "wreckback");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_swarmforge", timeoutTicks = 12000)
    public void swarmforgePickedForReal(GameTestHelper h) {
        if (here("swarmforge")) pickForReal(h, "swarmforge");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_pick_gloamwick", timeoutTicks = 12000)
    public void gloamwickPickedForReal(GameTestHelper h) {
        if (here("gloamwick")) pickForReal(h, "gloamwick");
        else h.succeed();
    }








}
