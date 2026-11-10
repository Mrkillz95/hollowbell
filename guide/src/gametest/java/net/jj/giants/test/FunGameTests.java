package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakefun.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.Brush;
import net.jj.giants.fun.Builds;
import net.jj.giants.fun.Fights;
import net.jj.giants.fun.Fun;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.fun.Marks;
import net.jj.giants.fun.PlayAs;
import net.jj.giants.fun.Settle;
import net.jj.giants.fun.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Part 7, "Admin fun and world tools" (ADMIN_BRIDGE.md 23), the Guide's side with a stand-in giant (net.jj.fakefun:
 * iron golems tagged "fakefun"): watching, playing as, staging a fight, names and titles, loot, giant tags, the
 * biome brush's erase, building an arena and taking it down, giant mixing, admins only, every action logged. Each test
 * has its own batch, its own spot far from the others, and puts the world back. Left out with the real giants loaded
 * (FunGiantsGameTests covers them there).
 */
public class FunGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakefun", "fakefun", "fakefun", "net.jj.fakefun.GiantsBridge", List.of(),
            Set.of());

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        FunGiants.TEST_KINDS.put(GiantsBridge.TAG, "fakefun");
        GiantsBridge.reset();
        FunWorld.get(s).s = new FunWorld.State();
        FunWorld.get(s).s.madeEventBoss = false;
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
        }
        for (IronGolem g : golems(h)) g.discard();
        AdminServer.useGiants(null);
        h.succeed();
    }

    static List<IronGolem> golems(GameTestHelper h) {
        List<IronGolem> out = new ArrayList<>();
        for (var e : h.getLevel().getAllEntities())
            if (e instanceof IronGolem g && g.getTags().contains(GiantsBridge.TAG) && !g.isRemoved()) out.add(g);
        return out;
    }

    /** a spot of its own, far out, with its land loaded and kept */
    static BlockPos spot(GameTestHelper h, int slot) {
        ServerLevel l = h.getLevel();
        int x = 52000 + slot * 600, z = -52000;
        for (int cx = (x >> 4) - 7; cx <= (x >> 4) + 7; cx++)
            for (int cz = (z >> 4) - 7; cz <= (z >> 4) + 7; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        // a flat stone floor at y 100, air over it
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -60; dx <= 60; dx++)
            for (int dz = -60; dz <= 60; dz++) {
                l.setBlock(m.set(x + dx, 99, z + dz), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 100; y < 112; y++) l.setBlock(m.set(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
            }
        return new BlockPos(x, 100, z);
    }

    static void unspot(GameTestHelper h, BlockPos at) {
        for (int cx = (at.getX() >> 4) - 7; cx <= (at.getX() >> 4) + 7; cx++)
            for (int cz = (at.getZ() >> 4) - 7; cz <= (at.getZ() >> 4) + 7; cz++)
                h.getLevel().setChunkForced(cx, cz, false);
    }

    static ServerPlayer op(GameTestHelper h, String name, BlockPos at) {
        ServerPlayer p = PlayersGameTests.join(h, name, true);
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        return p;
    }

    static boolean called(String start) {
        for (String c : GiantsBridge.calls) if (c.startsWith(start)) return true;
        return false;
    }

    // watching

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_watch")
    public void watchingKeepsAndPutsBackAndFollows(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 0);
        ServerPlayer p = op(h, "watcher", at);
        p.setGameMode(GameType.SURVIVAL);
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 20.5, at.getY(), at.getZ() + 0.5);
        Vec3[] seat = new Vec3[1];
        h.runAfterDelay(5, () -> {
            var r = Watch.start(s, p, g, "free");
            h.assertTrue(r.ok() && r.lines().get(0).startsWith("Watching the Fakefun"), "" + r.lines());
            h.assertTrue(p.isSpectator(), "a spectator now (hidden from players)");
            h.assertTrue(FunWorld.get(s).s.watching.containsKey(p.getStringUUID()), "where to come back kept");
            seat[0] = p.position();
            g.teleportTo(g.getX() + 6, g.getY(), g.getZ() + 3);
        });
        h.runAfterDelay(15, () -> {
            Vec3 moved = p.position().subtract(seat[0]);
            h.assertTrue(Math.abs(moved.x - 6) < 0.5 && Math.abs(moved.z - 3) < 0.5,
                    "carried along as far as he went: " + moved);
            // through his eyes
            h.assertTrue(Watch.start(s, p, g, "eyes").ok() && p.getCamera() == g, "his eyes");
            var r = Watch.stop(s, p);
            h.assertTrue(r.ok() && r.lines().equals(List.of("Back where you were.")), "" + r.lines());
            h.assertTrue(!p.isSpectator() && p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                    "their own game mode again");
            h.assertTrue(p.position().distanceTo(Vec3.atBottomCenterOf(at)) < 0.6, "where they were: " + p.position());
            h.assertFalse(Watch.stop(s, p).ok(), "not watching");
            // left while watching: put back on the next join
            Watch.start(s, p, g, "free");
            net.jj.giants.fun.FunTestAccess.left(p);
            net.jj.giants.fun.FunTestAccess.joined(s, p);
            h.assertTrue(!p.isSpectator() && FunWorld.get(s).s.watching.isEmpty(), "put back on joining");
            unspot(h, at);
            done(h, p);
        });
    }

    // playing as

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_play")
    public void playAsBringsThemThenHisModSeatsThem(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 1);
        ServerPlayer p = op(h, "player1", at.offset(0, 0, -50));
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(5, () -> {
            var r = PlayAs.start(s, p, g);
            h.assertTrue(r.ok(), "" + r.lines());
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(called("playas") && p.getVehicle() == g, "his mod seated them: " + GiantsBridge.calls);
            h.assertTrue(PlayAs.of(p) != null, "kept as playing");
            h.assertTrue(Fun.state(s, p).getAsJsonObject("me").has("playing"), "the page says so");
            var r = PlayAs.stop(s, p);
            h.assertTrue(r.ok() && p.getVehicle() == null, "stopped: " + r.lines());
            h.assertFalse(PlayAs.stop(s, p).ok(), "not playing");
            unspot(h, at);
            done(h, p);
        });
    }

    // (Part 6 with Part 7) Pause all giants: none can be played or set fighting, and anyone playing one steps out

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p7_paused")
    public void pausedGiantsCantBePlayedOrStaged(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 1);
        ServerPlayer p = op(h, "pauser", at.offset(0, 0, -20));
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(5, () -> h.assertTrue(PlayAs.start(s, p, g).ok(), "playing to start with"));
        h.runAfterDelay(20, () -> {
            h.assertTrue(PlayAs.of(p) != null && p.getVehicle() == g, "seated");
            h.assertTrue(AdminServer.set(s, "Tester", "all", "paused", "on").ok(), "paused");
            var r = PlayAs.start(s, p, g);
            h.assertTrue(!r.ok() && r.lines().get(0).contains("paused"), "refused while paused: " + r.lines());
            var f = Fights.stage(s, p, new Fights.Side(FAKE, 0.2, null), new Fights.Side(FAKE, 0.2, g), false);
            h.assertTrue(!f.ok() && f.lines().get(0).contains("paused"), "no fight while paused: " + f.lines());
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(PlayAs.of(p) == null && p.getVehicle() == null, "stepped out when paused");
            AdminServer.set(s, "Tester", "all", "paused", "off");
            h.assertTrue(PlayAs.start(s, p, g).ok(), "playable again once unpaused");
        });
        h.runAfterDelay(90, () -> {
            h.assertTrue(PlayAs.of(p) != null, "playing again");
            PlayAs.stop(s, p);
            unspot(h, at);
            done(h, p);
        });
    }

    // a fight

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p7_fight")
    public void aFightSummonsBothAndSetsThemOnEachOther(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 2);
        ServerPlayer p = op(h, "stager", at);
        IronGolem old = GiantsBridge.spawn(h.getLevel(), at.getX() + 40.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(5, () -> {
            var r = Fights.stage(s, p, new Fights.Side(FAKE, 0.2, null), new Fights.Side(FAKE, 0.2, old), false);
            h.assertTrue(r.ok() && r.lines().get(0).startsWith("Setting up the fight"), "" + r.lines());
            h.assertTrue(called("summon") && called("move"), "a fresh one summoned, the other moved: "
                    + GiantsBridge.calls);
            h.assertTrue(golems(h).size() == 2, "two of them");
            h.assertTrue(old.position().distanceTo(Vec3.atBottomCenterOf(at)) < 40, "brought to the spot");
            var same = Fights.stage(s, p, new Fights.Side(FAKE, 0.2, old), new Fights.Side(FAKE, 0.2, old), false);
            h.assertFalse(same.ok(), "the same giant twice is refused");
        });
        h.runAfterDelay(60, () -> {
            long meets = GiantsBridge.calls.stream().filter(c -> c.startsWith("meet ")).count();
            h.assertTrue(meets == 2, "each told to go for the other: " + GiantsBridge.calls);
            String a = golems(h).get(0).getStringUUID(), b = golems(h).get(1).getStringUUID();
            h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.contains(a) && c.contains(b) && c.startsWith("meet")),
                    "by their ids");
            h.assertTrue(AdminWorld.get(s).log().stream().anyMatch(c -> c.id().equals("fun:fight")), "logged");
            unspot(h, at);
            done(h, p);
        });
    }

    // names, loot, tags

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_name")
    public void namesTitlesAndColours(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 3);
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        g.setCustomName(net.minecraft.network.chat.Component.literal("Rusty"));
        h.runAfterDelay(3, () -> {
            var r = Marks.name(s, "Tester", g, "Grukk", "Terror of the Bay", "gold", false);
            h.assertTrue(r.ok() && g.getCustomName().getString().equals("Grukk, Terror of the Bay"), "" + r.lines());
            h.assertTrue(g.getCustomName().getStyle().getColor() != null, "in gold");
            Marks.name(s, "Tester", g, "", null, null, null);
            h.assertTrue(g.getCustomName().getString().equals("Fakefun, Terror of the Bay"), "no name: his own");
            h.assertFalse(Marks.name(s, "Tester", g, null, null, "pink", null).ok(), "not a colour");
            Marks.name(s, "Tester", g, "", "", "white", false);
            h.assertTrue(g.getCustomName().getString().equals("Rusty"), "cleared: his name tag's name back");
            h.assertTrue(AdminWorld.get(s).log().stream().anyMatch(c -> c.id().equals("fun:name")), "logged");
            g.discard();
            unspot(h, at);
            done(h);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_loot")
    public void hisLootDropsWhereHeFalls(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 4);
        ServerPlayer p = op(h, "looter", at);
        // a chest at eye height straight ahead
        BlockPos chest = BlockPos.containing(p.getEyePosition().add(0, 0, 2));
        h.getLevel().setBlock(chest, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) h.getLevel().getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, 7));
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 20.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(3, () -> {
            p.setYRot(0);
            p.setXRot(0);
            var r = Marks.loot(s, "Tester", p, g, "replace", "chest", "");
            h.assertTrue(r.ok() && g.getTags().contains(Marks.NO_DROPS), "replace: his own off: " + r.lines());
            h.assertFalse(Marks.loot(s, "Tester", p, g, "add", "table", "minecraft:nonsense/x").ok(), "no such table");
            Marks.loot(s, "Tester", p, g, "add", "chest", "");
            h.assertFalse(g.getTags().contains(Marks.NO_DROPS), "add: his own back");
            for (ItemEntity it : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(40)))
                it.discard();
            g.kill();
        });
        h.runAfterDelay(30, () -> {
            int diamonds = 0;
            for (ItemEntity it : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(40)))
                if (it.getItem().is(Items.DIAMOND)) {
                    diamonds += it.getItem().getCount();
                    it.discard();
                }
            h.assertTrue(diamonds == 7, "the chest's loot where he fell: " + diamonds);
            h.assertTrue(FunWorld.get(s).markOf(g.getStringUUID()) == null, "his marks gone with him");
            ((ChestBlockEntity) h.getLevel().getBlockEntity(chest)).clearContent();
            h.getLevel().setBlock(chest, Blocks.AIR.defaultBlockState(), 3);
            unspot(h, at);
            done(h, p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_tags")
    public void giantTagsGiveChangeAndTakeOff(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 5);
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(3, () -> {
            FunWorld.Tag boss = FunWorld.get(s).tag("Event boss");
            h.assertTrue(boss != null && boss.health == 3 && boss.glow, "the built-in tag is there");
            var r = Marks.give(s, "Tester", g, boss, true);
            h.assertTrue(r.ok(), "" + r.lines());
            h.assertTrue(g.getTags().containsAll(List.of("jj_giants.tag=Event boss", "jj_giants.health=3",
                    "jj_giants.damage=1.5", "jj_giants.speed=1.1")), "his tags: " + g.getTags());
            h.assertTrue(called("stats") && g.hasGlowingTag(), "his mod read them; glowing");
            h.assertTrue(g.getCustomName().getString().equals("Fakefun, Event Boss"), "the title on his name");
            // changing the tag changes him
            FunWorld.Tag t = boss.copyForTests();
            t.health = 5;
            h.assertTrue(Marks.saveTag(s, "Tester", "Event boss", t).ok(), "changed");
            h.assertTrue(g.getTags().contains("jj_giants.health=5"), "the wearer changed: " + g.getTags());
            // a stale one coming into the world is put right
            g.removeTag("jj_giants.health=5");
            g.addTag("jj_giants.health=2");
            net.jj.giants.fun.FunTestAccess.loaded(s, g);
            h.assertTrue(g.getTags().contains("jj_giants.health=5"), "put right on load: " + g.getTags());
            // bad tags refused
            FunWorld.Tag bad = new FunWorld.Tag();
            bad.name = "x!";
            h.assertFalse(Marks.saveTag(s, "Tester", "", bad).ok(), "a bad name");
            bad.name = "Tiny";
            bad.health = 50;
            h.assertFalse(Marks.saveTag(s, "Tester", "", bad).ok(), "health past 20");
            // taken off: as before
            h.assertTrue(Marks.take(s, "Tester", g, true).ok(), "taken off");
            h.assertTrue(g.getTags().stream().noneMatch(x -> x.startsWith("jj_giants.")) && !g.hasGlowingTag()
                    && g.getCustomName() == null, "x1, no glow, no title: " + g.getTags());
            // deleting a tag takes it off its wearers
            Marks.give(s, "Tester", g, FunWorld.get(s).tag("Event boss"), true);
            h.assertTrue(Marks.deleteTag(s, "Tester", "Event boss").ok() && Marks.wears(g) == null, "deleted");
            g.discard();
            unspot(h, at);
            done(h);
        });
    }

    // the biome brush: erase

    /** what a command said back */
    static final class Heard implements net.minecraft.commands.CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(net.minecraft.network.chat.Component m) {
            lines.add(m.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    /** a command as that player (what it said back in heard) */
    static void run(MinecraftServer s, ServerPlayer p, Heard heard, String cmd) {
        heard.lines.clear();
        s.getCommands().performPrefixedCommand(p.createCommandSourceStack().withSource(heard), cmd);
    }

    // (tags) giving a tag: to the giant you look at, the nearest, every one of a kind

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_tagto")
    public void tagsGoToTheOneLookedAtTheNearestAndEveryOne(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 10);
        ServerPlayer p = op(h, "tagger", at);
        // one straight ahead (facing south, +z), a nearer one off to the side, one behind
        IronGolem ahead = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 30.5);
        IronGolem side = GiantsBridge.spawn(h.getLevel(), at.getX() + 8.5, at.getY(), at.getZ() + 0.5);
        IronGolem behind = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() - 40.5);
        Heard heard = new Heard();
        h.runAfterDelay(20, () -> {
            // (none left over from a run that stopped part way: their land is loaded by now)
            for (IronGolem g : golems(h)) if (g != ahead && g != side && g != behind) g.discard();
            p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            h.assertTrue(Marks.lookedAt(p, 256) == ahead, "looked at: the one ahead");
            h.assertTrue(Marks.nearest(s, h.getLevel(), p.position()) == side, "nearest: the one to the side");
            // the command with no giant: the one looked at
            run(s, p, heard, "giants admin tag give \"Event boss\"");
            h.assertTrue("Event boss".equals(Marks.wears(ahead)) && Marks.wears(side) == null
                    && Marks.wears(behind) == null, "only the one looked at: " + heard.lines);
            h.assertTrue(heard.lines.size() > 0 && heard.lines.get(0).matches("The Fakefun \\d now wears Event boss: "
                    + "health x3, damage x1\\.5, speed x1\\.1\\. His bar reads \"Fakefun, Event Boss\"\\."),
                    "said plainly: " + heard.lines);
            // nearest
            run(s, p, heard, "giants admin tag give \"Event boss\" nearest");
            h.assertTrue("Event boss".equals(Marks.wears(side)) && Marks.wears(behind) == null, "nearest: "
                    + heard.lines);
            // a wall between: not through it
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
            for (int dx = -6; dx <= 6; dx++)
                for (int y = 0; y < 8; y++)
                    h.getLevel().setBlock(m.set(at.getX() + dx, at.getY() + y, at.getZ() + 12),
                            Blocks.STONE.defaultBlockState(), 2);
            h.assertTrue(Marks.lookedAt(p, 256) == null, "not through a wall");
            run(s, p, heard, "giants admin tag give \"Event boss\"");
            h.assertTrue(heard.lines.size() == 1 && heard.lines.get(0).startsWith("Look at a giant first"),
                    "said why: " + heard.lines);
            for (int dx = -6; dx <= 6; dx++)
                for (int y = 0; y < 8; y++)
                    h.getLevel().setBlock(m.set(at.getX() + dx, at.getY() + y, at.getZ() + 12),
                            Blocks.AIR.defaultBlockState(), 2);
            // looking a little off him still finds him (within a few degrees)
            p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 3, -2);
            p.setYHeadRot(3);
            h.assertTrue(Marks.lookedAt(p, 256) == ahead, "near the look");
            // the book's request: the one looked at
            FunWorld.Tag pocket = new FunWorld.Tag();
            pocket.name = "Pocket menace";
            pocket.damage = 2.5;
            h.assertTrue(Marks.saveTag(s, "Tester", "", pocket).ok(), "made");
            JsonObject o = new JsonObject();
            o.addProperty("tag", "Pocket menace");
            o.addProperty("to", "look");
            var r = Fun.request(s, p, "tagger", "tag", o);
            h.assertTrue(r != null && r.ok() && "Pocket menace".equals(Marks.wears(ahead)), "the book: " + (r == null
                    ? null : r.lines()));
            // every one of a kind
            run(s, p, heard, "giants admin tag give \"Pocket menace\" every fakefun");
            h.assertTrue(golems(h).stream().allMatch(g -> "Pocket menace".equals(Marks.wears(g))), "every one");
            h.assertTrue(heard.lines.size() > 0 && heard.lines.get(0).equals(
                    "All 3 Fakefuns now wear Pocket menace: damage x2.5."), "said: " + heard.lines);
            o = new JsonObject();
            o.addProperty("tag", "Event boss");
            o.addProperty("to", "every");
            o.addProperty("giant", "fakefun");
            r = Fun.request(s, p, "tagger", "tag", o);
            h.assertTrue(r != null && r.ok() && golems(h).stream().allMatch(g -> "Event boss".equals(Marks.wears(g))),
                    "the book: every one");
            // the old form still works, and a giant that isn't there is refused
            run(s, p, heard, "giants admin tag give \"Pocket menace\" " + behind.getStringUUID());
            h.assertTrue("Pocket menace".equals(Marks.wears(behind)), "by his id: " + heard.lines);
            h.assertFalse(Marks.giveEvery(s, "Tester", "nothing", pocket).ok(), "no such kind");
            // looking at nothing
            p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90, 0);
            // (a player looks where his head turns)
            p.setYHeadRot(90);
            h.assertTrue(Marks.lookedAt(p, 256) == null, "nothing looked at");
            unspot(h, at);
            done(h, p);
        });
    }

    // (tags) his own name before his title, or the title alone

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_ownname")
    public void hisOwnNameCanBeLeftOff(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 11);
        ServerPlayer p = op(h, "namer", at);
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 10.5);
        Heard heard = new Heard();
        h.runAfterDelay(3, () -> {
            // old saved tags (no ownName in them) keep his name
            h.assertTrue(new com.google.gson.Gson().fromJson("{\"name\":\"Old\"}", FunWorld.Tag.class).ownName,
                    "an old tag shows his name");
            // the tag leaves his name off: the title alone on his bar
            run(s, p, heard, "giants admin tag set \"Event boss\" showname off");
            h.assertFalse(FunWorld.get(s).tag("Event boss").ownName, "tag set: " + heard.lines);
            var r = Marks.give(s, "Tester", g, FunWorld.get(s).tag("Event boss"), true);
            h.assertTrue(g.getCustomName().getString().equals("Event Boss"), "the title alone: "
                    + g.getCustomName().getString());
            h.assertTrue(r.lines().get(0).equals("The Fakefun now wears Event boss: health x3, damage x1.5, speed x1.1."
                    + " His bar reads \"Event Boss\"."), "" + r.lines());
            h.assertTrue(net.jj.giants.fights.FightGiants.shown(g).equals("Event Boss"), "a fight's bars too");
            // his own choice over his tag's
            run(s, p, heard, "giants admin showname " + g.getStringUUID() + " on");
            h.assertTrue(g.getCustomName().getString().equals("Fakefun, Event Boss"), "his own on: " + heard.lines);
            JsonObject page = Fun.state(s, p).getAsJsonObject("marks").getAsJsonObject(g.getStringUUID());
            h.assertTrue(page.get("ownNameNow").getAsBoolean() && page.get("ownName").getAsBoolean(), "the page: " + page);
            run(s, p, heard, "giants admin showname " + g.getStringUUID() + " tag");
            h.assertTrue(g.getCustomName().getString().equals("Event Boss"), "as his tag says again");
            // the tag changed: every wearer changes
            FunWorld.Tag t = FunWorld.get(s).tag("Event boss").copyForTests();
            t.ownName = true;
            Marks.saveTag(s, "Tester", "Event boss", t);
            h.assertTrue(g.getCustomName().getString().equals("Fakefun, Event Boss"), "tag on again");
            // a name of his own is never left off: only the plain one is
            Marks.name(s, "Tester", g, "Grukk", null, null, null, false, false);
            h.assertTrue(g.getCustomName().getString().equals("Grukk, Event Boss"), "his given name stays");
            // no tag: a title alone, from the book's request
            Marks.take(s, "Tester", g, true);
            JsonObject o = new JsonObject();
            o.addProperty("giant", "fakefun");
            o.addProperty("id", g.getStringUUID());
            o.addProperty("name", "");
            o.addProperty("title", "Terror of the Bay");
            o.addProperty("ownName", "false");
            var nr = Fun.request(s, p, "namer", "name", o);
            h.assertTrue(nr != null && nr.ok() && g.getCustomName().getString().equals("Terror of the Bay"),
                    "title alone: " + g.getCustomName().getString());
            // Clear: all of it back (his own name with a title again)
            o.addProperty("title", "");
            o.addProperty("ownName", "tag");
            Fun.request(s, p, "namer", "name", o);
            h.assertTrue(g.getCustomName() == null && FunWorld.get(s).markOf(g.getStringUUID()) == null,
                    "cleared, nothing kept");
            g.discard();
            unspot(h, at);
            done(h, p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p7_erase")
    public void eraseTakesOnlyGiantsBiomesBack(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 6);
        ServerLevel l = h.getLevel();
        Holder<Biome> fake = l.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.MUSHROOM_FIELDS);
        Brush.testGiantBiome = "minecraft:mushroom_fields";
        // a "giant's" biome laid over 3x3 chunks
        List<LevelChunk> cs = new ArrayList<>();
        for (int cx = -1; cx <= 1; cx++)
            for (int cz = -1; cz <= 1; cz++) {
                LevelChunk c = l.getChunk((at.getX() >> 4) + cx, (at.getZ() >> 4) + cz);
                c.fillBiomesFromNoise((x, y, z, sm) -> fake, l.getChunkSource().randomState().sampler());
                cs.add(c);
            }
        var r = Brush.erase(s, "Tester", l, at.getX(), at.getZ(), 16, null);
        h.assertTrue(r.ok(), "" + r.lines());
        h.runAfterDelay(40, () -> {
            h.assertFalse(Brush.erasing(), "done");
            h.assertFalse(l.getBiome(at).is(Biomes.MUSHROOM_FIELDS), "erased in the circle");
            h.assertTrue(l.getBiome(at.offset(24, 0, 24)).is(Biomes.MUSHROOM_FIELDS), "left outside it");
            Brush.testGiantBiome = null;
            unspot(h, at);
            done(h);
        });
    }

    // building

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "p7_build")
    public void anArenaIsBuiltAndTakenDownAgain(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 7);
        ServerLevel l = h.getLevel();
        // some things in the way, to be put back
        l.setBlock(at.offset(5, 0, 5), Blocks.OAK_LOG.defaultBlockState(), 2);
        l.setBlock(at.offset(5, 1, 5), Blocks.GLASS.defaultBlockState(), 2);
        l.setBlock(at.offset(-30, -1, 2), Blocks.AIR.defaultBlockState(), 2);
        Map<BlockPos, BlockState> before = new HashMap<>();
        for (int dx = -52; dx <= 52; dx += 3)
            for (int dz = -52; dz <= 52; dz += 3)
                for (int y = -4; y <= 12; y++) before.put(at.offset(dx, y, dz).immutable(), l.getBlockState(at.offset(dx,
                        y, dz)));
        ServerPlayer p = op(h, "builder", at.offset(0, 0, 0));
        h.runAfterDelay(3, () -> {
            var r = Builds.arena(s, "builder", p, "small");
            h.assertTrue(r.ok(), "" + r.lines());
            h.assertFalse(Builds.arena(s, "builder", p, "small").ok(), "one build at a time");
        });
        h.runAfterDelay(400, () -> {
            h.assertFalse(Builds.busy(), "built");
            FunWorld.Build b = FunWorld.get(s).s.builds.get(0);
            h.assertTrue(b.state.equals("built") && b.undo, "kept to take down: " + b.state);
            BlockPos floor = at.below();
            h.assertTrue(l.getBlockState(floor).is(Blocks.CHISELED_STONE_BRICKS), "the middle marked");
            h.assertTrue(l.getBlockState(at.offset(5, 0, 5)).isAir(), "cleared over the floor");
            BlockState wall = l.getBlockState(at.offset(36, 2, 21));
            h.assertTrue(wall.is(Blocks.STONE_BRICKS) || wall.is(Blocks.CRACKED_STONE_BRICKS)
                    || wall.is(Blocks.MOSSY_STONE_BRICKS), "the wall: " + wall);
            int seats = 0, banners = 0;
            for (int dx = -51; dx <= 51; dx++)
                for (int dz = -51; dz <= 51; dz++)
                    for (int y = 0; y <= 12; y++) {
                        BlockState q = l.getBlockState(at.offset(dx, y, dz));
                        if (q.is(Blocks.STONE_BRICK_STAIRS)) seats++;
                        if (q.is(net.minecraft.tags.BlockTags.BANNERS)) banners++;
                        if (Math.hypot(dx, dz) <= 40 && y >= 1 && !q.getCollisionShape(l, at).isEmpty())
                            h.fail("something standing on the floor at " + dx + " " + y + " " + dz + ": " + q);
                    }
            h.assertTrue(seats > 200 && banners > 20, "seats " + seats + ", banners " + banners);
            BlockState hole = l.getBlockState(at.offset(-30, -1, 2));
            h.assertTrue(hole.isSolidRender(l, at) && (hole.is(Blocks.STONE_BRICKS) || hole.is(Blocks.SMOOTH_STONE)
                    || hole.is(Blocks.POLISHED_ANDESITE) || hole.is(Blocks.CRACKED_STONE_BRICKS)
                    || hole.is(Blocks.MOSSY_STONE_BRICKS)), "the hole filled: " + hole);
            h.assertTrue(l.getBlockState(at.offset(-30, -2, 2)).is(Blocks.STONE_BRICKS)
                    || !l.getBlockState(at.offset(-30, -2, 2)).isAir(), "held up under the floor");
            h.assertTrue(Builds.takeDown(s, "builder", b.n).ok(), "taking it down");
        });
        h.runAfterDelay(800, () -> {
            h.assertFalse(Builds.busy(), "taken down");
            List<String> wrong = new ArrayList<>();
            for (var e : before.entrySet())
                if (l.getBlockState(e.getKey()) != e.getValue() && wrong.size() < 5) wrong.add(e.getKey()
                        .toShortString() + " " + l.getBlockState(e.getKey()) + " was " + e.getValue());
            h.assertTrue(wrong.isEmpty(), "every block back as it was: " + wrong);
            h.assertTrue(FunWorld.get(s).s.builds.get(0).state.equals("gone"), "gone");
            unspot(h, at);
            done(h, p);
        });
    }

    // dens

    /** each den's own blocks, to know it was built */
    static final Map<String, List<net.minecraft.world.level.block.Block>> DEN_MARKS = Map.of(
            "pitchgut", List.of(Blocks.OCHRE_FROGLIGHT, Blocks.BLACK_STAINED_GLASS, Blocks.BONE_BLOCK),
            "furrowmaw", List.of(Blocks.POINTED_DRIPSTONE, Blocks.ORANGE_TERRACOTTA),
            "cerberus", List.of(Blocks.BLUE_ICE, Blocks.MAGMA_BLOCK, Blocks.CHAIN),
            "hollowbell", List.of(Blocks.LIME_STAINED_GLASS, Blocks.BONE_BLOCK, Blocks.CHAIN),
            "lanternwillow", List.of(Blocks.MANGROVE_ROOTS, Blocks.LANTERN, Blocks.WATER),
            "wreckback", List.of(Blocks.WATER, Blocks.DARK_OAK_PLANKS, Blocks.SAND),
            "swarmforge", List.of(Blocks.IRON_BLOCK, Blocks.CAMPFIRE, Blocks.BRICKS),
            "gloamwick", List.of(Blocks.SOUL_LANTERN, Blocks.CHISELED_DEEPSLATE, Blocks.DARK_OAK_LOG));

    static final List<String> KINDS = List.of("pitchgut", "furrowmaw", "cerberus", "hollowbell", "lanternwillow",
            "wreckback", "swarmforge", "gloamwick");

    @SuppressWarnings("unchecked")
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "p7_denplans")
    public void densAreScaledSafeAndInBudget(GameTestHelper h) {
        List<String> wrong = new ArrayList<>();
        for (String kind : KINDS) {
            int lastR = 0, lastPieces = 0, lastTop = Integer.MIN_VALUE;
            for (double size : new double[] {0.1, 0.25, 1, 2}) {
                Map<String, Object> p = net.jj.giants.fun.FunTestAccess.plan(kind, size);
                int r = (int) p.get("r"), pieces = (int) p.get("pieces"), top = (int) p.get("top");
                long blocks = (long) p.get("blocks");
                int want = net.minecraft.util.Mth.clamp((int) Math.round(FunGiants.reach(kind, size) * 1.15), 10, 160);
                if (r != want) wrong.add(kind + " " + size + ": radius " + r + " not " + want);
                if (r < lastR) wrong.add(kind + " " + size + ": smaller than at a smaller size");
                if (pieces < lastPieces * 0.9) wrong.add(kind + " " + size + ": fewer pieces (" + pieces
                        + ") than smaller (" + lastPieces + ")");
                if (lastTop != Integer.MIN_VALUE && top < lastTop * 0.9 - 3) wrong.add(kind + " " + size + ": lower (" + top + ") than smaller (" + lastTop + ")");
                if (pieces < 30) wrong.add(kind + " " + size + ": only " + pieces + " pieces");
                if (blocks > Builds.MOST_UNDO / 2) wrong.add(kind + " " + size + ": " + blocks + " blocks, over budget");
                wrong.addAll((List<String>) p.get("problems"));
                Map<String, Integer> kinds = (Map<String, Integer>) p.get("kinds");
                if (kinds.size() < 18) wrong.add(kind + " " + size + ": only " + kinds.size() + " kinds of block");
                net.jj.giants.GiantsGuideMod.LOG.info("den plan {} {}: r {} reach {} pieces {} blocks {} top {} kinds {}",
                        kind, size, r, p.get("reach"), pieces, blocks, top, kinds.size());
                lastR = r;
                lastPieces = pieces;
                lastTop = top;
            }
            // the roost is as high as a quarter of him
            if (kind.equals("hollowbell")) {
                int top = (int) net.jj.giants.fun.FunTestAccess.plan(kind, 1).get("top");
                if (top < FunGiants.height(kind, 1) * 0.25 - 1) wrong.add("hollowbell: roost only " + top + " high");
            }
        }
        for (String size : List.of("small", "medium", "large")) {
            Map<String, Object> p = net.jj.giants.fun.FunTestAccess.plan("arena:" + size, 1);
            wrong.addAll((List<String>) p.get("problems"));
            if ((int) p.get("floorClutter") != 0) wrong.add("arena " + size + ": " + p.get("floorClutter")
                    + " blocks standing on the floor: " + p.get("clutterAt"));
            if ((long) p.get("blocks") > Builds.MOST_UNDO / 2) wrong.add("arena " + size + ": over budget");
            if (((Map<String, Integer>) p.get("kinds")).size() < 25) wrong.add("arena " + size + ": plain");
            net.jj.giants.GiantsGuideMod.LOG.info("arena plan {}: reach {} pieces {} blocks {} kinds {}", size,
                    p.get("reach"), p.get("pieces"), p.get("blocks"), ((Map<String, Integer>) p.get("kinds")).size());
        }
        for (String w : wrong) net.jj.giants.GiantsGuideMod.LOG.warn("den plan wrong: {}", w);
        h.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(8, wrong.size())));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "p7_dens")
    public void everyDenIsBuiltAroundThePlayerAndTakenDown(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        List<Giant> kinds = new ArrayList<>();
        for (String k : KINDS) kinds.add(new Giant(k, k, k, "net.jj.fakefun.GiantsBridge", List.of(), Set.of()));
        AdminServer.useGiants(kinds);
        BlockPos at = spot(h, 9);
        ServerLevel l = h.getLevel();
        l.setBlock(at.offset(4, 0, 3), Blocks.OAK_LOG.defaultBlockState(), 2);
        ServerPlayer p = op(h, "denner", at);
        h.runAfterDelay(3, () -> {
            List<String> wrong = new ArrayList<>();
            int n = 0;
            for (String kind : KINDS)
                for (double size : kind.equals("gloamwick") || kind.equals("wreckback") ? new double[] {0.1, 0.25}
                        : new double[] {0.1}) {
                    int reach = Math.min(60, net.jj.giants.fun.FunTestAccess.denRadius(kind, size) + 20);
                    Map<BlockPos, BlockState> before = new HashMap<>();
                    for (int dx = -reach; dx <= reach; dx++)
                        for (int dz = -reach; dz <= reach; dz++)
                            for (int y = -20; y <= 60; y++) {
                                BlockPos q = at.offset(dx, y, dz);
                                before.put(q, l.getBlockState(q));
                            }
                    var r = Builds.den(s, "denner", p, kind, size);
                    if (!r.ok()) {
                        wrong.add(kind + ": " + r.lines());
                        continue;
                    }
                    for (int i = 0; i < 4000 && Builds.busy(); i++) net.jj.giants.fun.FunTestAccess.buildTick(s);
                    FunWorld.Build b = FunWorld.get(s).s.builds.get(n++);
                    if (!b.state.equals("built") || !b.undo) wrong.add(kind + " " + size + ": " + b.state + " " + b.undo);
                    if (b.r != net.jj.giants.fun.FunTestAccess.denRadius(kind, size)) wrong.add(kind + ": radius " + b.r);
                    // his own blocks there, and nothing put where the player stands
                    Set<net.minecraft.world.level.block.Block> found = new java.util.HashSet<>();
                    for (var e : before.keySet()) found.add(l.getBlockState(e).getBlock());
                    for (var m : DEN_MARKS.get(kind)) if (!found.contains(m)) wrong.add(kind + " " + size + ": no " + m);
                    if (l.getBlockState(at).blocksMotion() || l.getBlockState(at.above()).blocksMotion())
                        wrong.add(kind + ": built into the player: " + l.getBlockState(at));
                    h.assertTrue(Builds.takeDown(s, "denner", b.n).ok(), kind + ": taking down");
                    for (int i = 0; i < 4000 && Builds.busy(); i++) net.jj.giants.fun.FunTestAccess.buildTick(s);
                    int bad = 0;
                    for (var e : before.entrySet())
                        if (l.getBlockState(e.getKey()) != e.getValue() && bad++ < 3) wrong.add(kind + " " + size + ": "
                                + e.getKey().subtract(at).toShortString() + " " + l.getBlockState(e.getKey()) + " was "
                                + e.getValue());
                }
            h.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong.subList(0, Math.min(8, wrong.size())));
            unspot(h, at);
            done(h, p);
        });
    }


    // mixing

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p7_settle")
    public void settledOnlyWhereHeCanLive(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos at = spot(h, 8);
        ServerPlayer p = op(h, "mixer", at);
        IronGolem g = GiantsBridge.spawn(h.getLevel(), at.getX() + 30.5, at.getY(), at.getZ() + 0.5);
        h.runAfterDelay(3, () -> {
            var r = Settle.settle(s, "mixer", p, g, "sky", null);
            h.assertTrue(!r.ok() && r.lines().get(0).contains("can't live in the sky"), "" + r.lines());
            r = Settle.settle(s, "mixer", p, g, "here", null);
            h.assertTrue(r.ok() && g.getTags().contains("fakefun_stays"), "here, staying: " + r.lines());
            h.assertTrue(g.position().distanceTo(Vec3.atBottomCenterOf(at)) < 1.5, "moved: " + g.position());
            GiantsBridge.places = List.of("land", "sky");
            r = Settle.settle(s, "mixer", p, g, "sky", null);
            h.assertTrue(r.ok() && g.getY() > at.getY() + 30, "in the sky for a flyer: " + g.getY());
            h.assertFalse(Settle.settle(s, "mixer", p, g, "cave", null).ok(), "not a burrower");
            g.discard();
            unspot(h, at);
            done(h, p);
        });
    }

    // admins only

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "p7_roles")
    public void moderatorsAndOthersAreRefused(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer mod = PlayersGameTests.join(h, "moddy", false);
        AdminWorld.get(s).addMod(mod.getUUID(), "moddy");
        // (JJ answers 2: a moderator may Watch now; everything else here is still for admins)
        var r = AdminBook.request(s, mod, AdminBook.role(mod), "{\"do\":\"playasstop\"}");
        h.assertTrue(!r.ok() && r.lines().get(0).contains("Only admins"), "a moderator: " + r.lines());
        ServerPlayer nobody = PlayersGameTests.join(h, "nobody", false);
        r = AdminBook.change(s, nobody, "{\"do\":\"brushget\"}");
        h.assertFalse(r.ok(), "anyone else: " + r.lines());
        h.assertFalse(nobody.getInventory().contains(new ItemStack(Brush.ITEM)), "no brush for them");
        AdminWorld.get(s).removeMod(mod.getUUID());
        done(h, mod, nobody);
    }
}
