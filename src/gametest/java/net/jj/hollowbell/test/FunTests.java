package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.pig;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsFun;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.world.HomeGround;
import net.jj.hollowbell.world.Painter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.2) The Giants Guide's fun and world tools over his bridge: "fun" says what he does
 * and where he can live; "funact" lets an admin play as him on his crown (whatever the riding switches say), reads
 * his own stats from the Guide's tags, and moves him somewhere to live. No tags on him: exactly as before.
 */
public class FunTests implements FabricGameTest {

    static List<String> act(MinecraftServer s, UUID id, String what, String more) {
        String json = "{\"fun\":1,\"do\":\"" + what + "\",\"id\":\"" + id + "\"" + (more.isEmpty() ? "" : "," + more)
                + "}";
        return GiantsBridge.giants(s, "funact", json);
    }

    private static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    private static int ground(GameTestHelper h, double x, double z) {
        return h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fun_head")
    public void funSaysWhatHeDoesAndWhereHeLives(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<String> r = GiantsBridge.giants(s, "fun", "1");
        h.assertTrue(r != null && r.size() == 1, "one line: " + r);
        JsonObject o = JsonParser.parseString(r.get(0)).getAsJsonObject();
        h.assertTrue(o.get("fun").getAsInt() == 1 && o.get("name").getAsString().equals("Hollowbell")
                && o.get("giant").getAsString().equals("hollowbell"), "" + o);
        h.assertTrue(o.getAsJsonArray("does").toString().equals("[\"playas\",\"stats\",\"move\"]"), "does: " + o);
        h.assertTrue(o.getAsJsonArray("places").toString().equals("[\"land\",\"sky\"]"), "places: " + o);
        h.assertTrue(GiantsBridge.giants(s, "fun", "2") != null, "an older Guide's ask still answered");
        h.assertTrue(GiantsBridge.giants(s, "funact", "nonsense") == null, "unreadable: null");
        h.assertTrue(GiantsBridge.giants(s, "funact", "{\"fun\":2,\"do\":\"stats\",\"id\":\"" + UUID.randomUUID()
                + "\"}") == null, "a newer protocol: null");
        h.assertTrue(act(s, UUID.randomUUID(), "dance", "") == null, "an unknown do: null");
        List<String> gone = act(s, UUID.randomUUID(), "stats", "");
        h.assertTrue(gone.equals(List.of("no", "He isn't there any more.")), "nobody: " + gone);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fun_stats")
    public void hisOwnStatsComeFromHisTags(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.CALM, 1620);
        Pig pig = pig(h, e.position().add(40, 0, 0));
        h.runAfterDelay(5, () -> {
            float max = e.healthMax(), hit = e.dmg(10f, pig);
            double travel = e.travelSpeed(), fly = e.maxSpeed();
            // no tags: x1 exactly, the old path
            h.assertTrue(GiantsFun.mult(e, "health") == 1f && GiantsFun.mult(e, "damage") == 1f
                    && GiantsFun.mult(e, "speed") == 1f, "x1 with no tags");
            h.assertTrue(max > 80f, "big enough that 40 isn't his floor: " + max);
            e.hurtBy(max * 0.3f);
            float share = e.healthNow() / max;
            e.addTag("jj_giants.health=2");
            e.addTag("jj_giants.damage=2");
            e.addTag("jj_giants.speed=1.5");
            List<String> r = act(s, e.getUUID(), "stats", "");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: health x2, damage x2, speed x1.5.")), "" + r);
            h.assertTrue(Math.abs(e.healthMax() - max * 2) < 0.5f,
                    "his most health doubled: " + max + " -> " + e.healthMax());
            h.assertTrue(Math.abs(e.healthNow() / e.healthMax() - share) < 0.02f, "his share of it kept");
            h.assertTrue(Math.abs(e.dmg(10f, pig) - hit * 2) < 1e-3, "his hits doubled: " + hit + " -> "
                    + e.dmg(10f, pig));
            h.assertTrue(Math.abs(e.travelSpeed() - travel * 1.5) < 1e-6, "his travel x1.5");
            h.assertTrue(Math.abs(e.maxSpeed() - fly * 1.5) < 1e-6, "his flying x1.5: " + fly + " -> " + e.maxSpeed());
            // a bad or out-of-range value: read safely
            e.removeTag("jj_giants.speed=1.5");
            e.addTag("jj_giants.speed=99");
            h.assertTrue(GiantsFun.mult(e, "speed") == 4f, "speed kept to 4 at most");
            e.removeTag("jj_giants.speed=99");
            e.addTag("jj_giants.speed=fast");
            h.assertTrue(GiantsFun.mult(e, "speed") == 1f, "nonsense: x1");
            // taken off again: as before
            for (String t : List.copyOf(e.getTags())) if (t.startsWith("jj_giants.")) e.removeTag(t);
            act(s, e.getUUID(), "stats", "");
            h.assertTrue(Math.abs(e.healthMax() - max) < 0.01f && e.dmg(10f, pig) == hit
                    && e.travelSpeed() == travel && e.maxSpeed() == fly, "back to as he was");
            pig.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fun_play")
    public void anAdminPlaysAsHimWhateverTheRidingSwitchesSay(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1621);
        ServerPlayer p = player(h, e.position().add(20, 1, 0));
        ServerPlayer q = player(h, e.position().add(24, 1, 0));
        h.runAfterDelay(5, () -> {
            all(h, "\"all.ride\":false,\"all.bookOrders\":\"noattacks\"");
            List<String> r = act(s, e.getUUID(), "playas", "\"by\":\"" + p.getUUID() + "\",\"on\":true");
            h.assertTrue(r.equals(List.of("ok", "You're playing as the Hollowbell. G gets you off.")), "" + r);
            h.assertTrue(e.rider() == p && CodexOrders.riding(p) == e, "on his crown");
            h.assertTrue(GiantsFun.playing(p), "kept as playing");
            List<String> second = act(s, e.getUUID(), "playas", "\"by\":\"" + q.getUUID() + "\",\"on\":true");
            h.assertTrue(second.equals(List.of("no", "Someone is already at his controls.")), "" + second);
            List<String> notHim = act(s, e.getUUID(), "playas", "\"by\":\"" + q.getUUID() + "\",\"on\":false");
            h.assertTrue(notHim.equals(List.of("no", "You aren't playing as him.")), "q isn't playing him: " + notHim);
        });
        h.runAfterDelay(35, () -> {
            try {
                h.assertTrue(e.rider() == p && !e.moves().settingDown(),
                        "riding off doesn't set an admin playing him down");
                h.assertTrue(e.getTarget() != p, "he doesn't go after them");
                // a move key works for them, even with no attack orders
                e.moves().stopNow();
                CodexOrders.forgetPlayer(p.getUUID());
                e.mood().setWind(1f);
                CodexOrders.moveFromCrown(p, e, Moves.PULSE);
                h.assertTrue(e.moves().move() == Moves.PULSE, "his pulse set off from the crown");
                e.moves().stopNow();
                List<String> r = act(s, e.getUUID(), "playas", "\"by\":\"" + p.getUUID() + "\",\"on\":false");
                h.assertTrue(r.equals(List.of("ok", "You've stepped out of the Hollowbell.")), "" + r);
                h.assertTrue(e.rider() == null && p.getVehicle() == null, "off him");
                h.assertTrue(!GiantsFun.playing(p), "no longer playing");
                double out = Math.hypot(p.getX() - e.getX(), p.getZ() - e.getZ());
                h.assertTrue(out >= e.bellRadius() && p.fallDistance == 0,
                        "set down beside him, out from under his bell: " + out);
                h.assertTrue(Math.abs(p.getY() - ground(h, p.getX(), p.getZ())) < 2,
                        "on the ground: " + p.getY() + " / " + ground(h, p.getX(), p.getZ()));
                // too far: refused
                p.teleportTo(h.getLevel(), e.getX() + 600, e.getY() + 2, e.getZ(), 0, 0);
                h.assertTrue(act(s, e.getUUID(), "playas", "\"by\":\"" + p.getUUID() + "\",\"on\":true")
                        .equals(List.of("no", "Go to him first.")), "far: go to him first");
            } finally {
                GiantsAdmin.clear();
                drop(p);
                drop(q);
                release(h, e);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fun_move")
    public void movedToLiveSomewhereElse(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1622);
        e.setStay(false);
        ServerPlayer p = player(h, e.position().add(20, 1, 0));
        Vec3[] to = new Vec3[1];
        h.runAfterDelay(5, () -> {
            h.assertTrue(e.possess(p), "on his crown");
            to[0] = e.position().add(30, 0, 20);
            double g = ground(h, to[0].x, to[0].z);
            List<String> r = act(s, e.getUUID(), "move", "\"dim\":\"minecraft:overworld\",\"x\":" + to[0].x
                    + ",\"y\":" + (g + 1) + ",\"z\":" + to[0].z + ",\"where\":\"land\",\"stay\":true");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: moved.")), "" + r);
            h.assertTrue(Math.hypot(e.getX() - to[0].x, e.getZ() - to[0].z) < 0.01, "there: " + e.position());
            h.assertTrue(e.getY() >= g && e.getY() <= g + 60 * S + 4, "drifting over the ground there: " + e.getY()
                    + " over " + g);
            h.assertTrue(e.staying() && e.home() != null && e.home().distanceTo(e.position()) < 1, "stays, home");
            h.assertTrue(e.rider() == null && p.getVehicle() == null, "his rider set down first");
            h.assertTrue(act(s, e.getUUID(), "move", "\"dim\":\"minecraft:the_nether\",\"x\":0,\"y\":64,\"z\":0")
                    .equals(List.of("no", "He's in another world. Move him within his own world.")), "dimension");
            h.assertTrue(act(s, e.getUUID(), "move", "\"x\":0,\"y\":64,\"z\":0,\"where\":\"sea\"")
                    .equals(List.of("no", "He can't live there.")), "not at sea");
            h.assertTrue(act(s, e.getUUID(), "move", "\"x\":0,\"y\":64,\"z\":0,\"where\":\"cave\"")
                    .equals(List.of("no", "He can't live there.")), "not in a cave");
            h.assertTrue(act(s, e.getUUID(), "move", "\"where\":\"land\"").get(0).equals("no"), "no spot");
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(e.staying(), "still holding still");
            h.assertTrue(Math.hypot(e.getX() - to[0].x, e.getZ() - to[0].z) < 1, "still there: " + e.position());
            double g = ground(h, e.getX(), e.getZ());
            h.assertTrue(e.getY() >= g - 1, "not in the ground: " + e.getY() + " / " + g);
            // up into the sky: flying there, at that height
            Vec3 up = to[0].add(-10, 0, 0);
            double gu = ground(h, up.x, up.z);
            List<String> r = act(s, e.getUUID(), "move", "\"x\":" + up.x + ",\"y\":" + (gu + 40) + ",\"z\":" + up.z
                    + ",\"where\":\"sky\",\"stay\":true");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: moved.")), "" + r);
            h.assertTrue(Math.abs(e.getY() - (gu + 40)) < 0.01, "up in the sky: " + e.getY());
        });
        h.runAfterDelay(60, () -> {
            double gu = ground(h, e.getX(), e.getZ());
            h.assertTrue(e.getY() > gu + 30, "he stays up there: " + e.getY() + " over " + gu);
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60 + HollowbellEntity.DEATH_LENGTH + 40, batch = "fun_drops")
    public void noDropsLeavesOutHisOwnDrops(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1623);
        e.addTag(GiantsFun.NO_DROPS);
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(20, () -> {
            at[0] = e.position();
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(e.isDeadOrDying(), "he didn't die");
        });
        h.runAfterDelay(20 + HollowbellEntity.DEATH_LENGTH + 20, () -> {
            h.assertTrue(e.isRemoved(), "he should be gone");
            var l = h.getLevel();
            BlockPos c = BlockPos.containing(at[0]);
            int caches = 0;
            for (BlockPos q : BlockPos.betweenClosed(c.offset(-8, -80, -8), c.offset(8, 80, 8)))
                if (l.getBlockState(q).is(ModBlocks.LOOT_CACHE)) caches++;
            List<String> his = new ArrayList<>();
            for (ItemEntity d : l.getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(24, 100, 24))) {
                var key = BuiltInRegistries.ITEM.getKey(d.getItem().getItem());
                if (key.getNamespace().equals(HollowbellMod.MOD_ID) || caches > 0) his.add(key.toString());
                d.discard();
            }
            h.assertTrue(caches == 0, "no loot cache of his");
            h.assertTrue(his.isEmpty(), "nothing of his dropped: " + his);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fun_name")
    public void aCustomNameShowsOnHisBar(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1624);
        h.runAfterDelay(5, () -> e.setCustomName(Component.literal("Dongle, Bell of the Deep")));
        // (his bars are looked after every 10 ticks)
        h.runAfterDelay(30, () -> {
            Component n = e.barName();
            h.assertTrue(n != null && n.getString().equals("Dongle, Bell of the Deep"), "on his bar: " + n);
            e.setCustomName(null);
        });
        h.runAfterDelay(50, () -> {
            Component n = e.barName();
            h.assertTrue(n != null && !n.getString().contains("Dongle"), "the name gone again: " + n);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "fun_paint")
    public void paintTakesASpot(GameTestHelper h) {
        var lv = h.getLevel();
        MinecraftServer s = lv.getServer();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int px = o.getX() + 20000 + 1625 * 400, pz = o.getZ() + 5000;
        int ax = px + 90, az = pz + 20;
        for (int x = (px - 48) >> 4; x <= (ax + 48) >> 4; x++)
            for (int z = (pz - 48) >> 4; z <= (az + 48) >> 4; z++) lv.getChunk(x, z);
        ServerPlayer p = player(h, new Vec3(px + 0.5, ground(h, px, pz) + 1, pz + 0.5));
        h.runAfterDelay(5, () -> {
            try {
                List<String> r = GiantsBridge.giants(s, "paint", "16 biome " + p.getUUID() + " depth=5 at=" + ax + ","
                        + az);
                h.assertTrue(r != null && !r.isEmpty() && Painter.busy(), "painting: " + r);
                // (the test's land may have been let go meanwhile: what is waiting is painted now)
                for (int k = 0; k < 50 && Painter.busy(); k++) Painter.tick(s);
                Painter.finishAll(s);
                h.assertTrue(!Painter.busy(), "the painting never finished");
                h.assertTrue(lv.getBiome(new BlockPos(ax, ground(h, ax, az), az)).is(HomeGround.BELL_HOLLOWS),
                        "his biome at the spot");
                h.assertTrue(!lv.getBiome(p.blockPosition()).is(HomeGround.BELL_HOLLOWS), "not round the player");
            } finally {
                Painter.forget();
                drop(p);
            }
            h.succeed();
        });
    }
}
