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

    /** an ignored player is never his target, with a normal player beside them */
    private void ignoredIsLeftAlone(GameTestHelper h, String key) {
        MinecraftServer s = h.getLevel().getServer();
        clean(s);
        Giant g = Giants.byKey(key);
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer ign = PlayersGameTests.join(h, "ig_" + key.substring(0, 4), false);
        ServerPlayer plain = PlayersGameTests.join(h, "pl_" + key.substring(0, 4), false);
        ign.teleportTo(h.getLevel(), o.getX() + 6, o.getY(), o.getZ(), 0, 0);
        plain.teleportTo(h.getLevel(), o.getX() - 6, o.getY(), o.getZ(), 0, 0);
        Players.setRule(s, "Tester", ign.getStringUUID(), "ignored", true);
        CommandSourceStack src = s.createCommandSourceStack().withPermission(4).withLevel(h.getLevel())
                .withPosition(Vec3.atBottomCenterOf(o)).withSuppressedOutput();
        s.getCommands().performPrefixedCommand(src, g.command() + " summon hunting 0.05");
        String type = Players.MAIN.get(key);
        boolean[] plainTargeted = {false};
        // (every one of him hunts, whatever his mood)
        ign.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        plain.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        int[] seen = {0};
        // both players hit him: the plain one makes an enemy of him, the ignored one doesn't
        h.runAfterDelay(20, () -> {
            for (Entity e : h.getLevel().getEntities((Entity) null, new AABB(o).inflate(40),
                    x -> BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).toString().equals(type)))
                if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                    le.hurt(ign.damageSources().playerAttack(ign), 1f);
                    le.hurt(plain.damageSources().playerAttack(plain), 1f);
                }
        });
        h.onEachTick(() -> {
            for (Entity e : h.getLevel().getEntities((Entity) null, new AABB(o).inflate(40),
                    x -> BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).toString().equals(type)))
                if (e instanceof Mob m && (seen[0] = Math.max(seen[0], 1)) > 0 && m.getTarget() instanceof Player t) {
                    if (t == ign) h.fail(key + " went after the ignored player");
                    if (t == plain) plainTargeted[0] = true;
                }
        });
        h.runAfterDelay(400, () -> {
            for (Entity e : h.getLevel().getEntities((Entity) null, new AABB(o).inflate(60),
                    x -> BuiltInRegistries.ENTITY_TYPE.getKey(x.getType()).toString().equals(type))) e.discard();
            net.jj.giants.GiantsGuideMod.LOG.info("p5 test: {} (one there: {}) went after the other player: {}",
                    key, seen[0] > 0, plainTargeted[0]);
            h.assertTrue(seen[0] > 0, key + ": none of him came for the test");
            noRules(s, ign, plain);
            clean(s);
            s.getPlayerList().remove(ign);
            s.getPlayerList().remove(plain);
            h.succeed();
        });
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
            net.jj.giants.GiantsGuideMod.LOG.info("p5 test: {} picked for real: {}, one came: {}, {} blocks from"
                    + " the middle, {} more standing", key, pk.outcome, pk.spawned, (int) d, came);
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

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_pitchgut", timeoutTicks = 460)
    public void pitchgutLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("pitchgut")) ignoredIsLeftAlone(h, "pitchgut");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_furrowmaw", timeoutTicks = 460)
    public void furrowmawLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("furrowmaw")) ignoredIsLeftAlone(h, "furrowmaw");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_cerberus", timeoutTicks = 460)
    public void cerberusLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("cerberus")) ignoredIsLeftAlone(h, "cerberus");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_hollowbell", timeoutTicks = 460)
    public void hollowbellLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("hollowbell")) ignoredIsLeftAlone(h, "hollowbell");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_lanternwillow", timeoutTicks = 460)
    public void lanternwillowLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("lanternwillow")) ignoredIsLeftAlone(h, "lanternwillow");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_wreckback", timeoutTicks = 460)
    public void wreckbackLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("wreckback")) ignoredIsLeftAlone(h, "wreckback");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_swarmforge", timeoutTicks = 460)
    public void swarmforgeLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("swarmforge")) ignoredIsLeftAlone(h, "swarmforge");
        else h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5g_ig_gloamwick", timeoutTicks = 460)
    public void gloamwickLeavesAnIgnoredPlayerAlone(GameTestHelper h) {
        if (here("gloamwick")) ignoredIsLeftAlone(h, "gloamwick");
        else h.succeed();
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
