package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.Builds;
import net.jj.giants.fun.Fights;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.fun.Marks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Part 7 with the real giants (-Pgiants, ADMIN_BRIDGE.md 23.5): every giant answers "fun", takes his own stats from
 * his tags, is played as by a player and let go of, is moved, wears a name, and has a den built and taken down; and
 * a fight between two kinds is staged. Without the giants each test passes at once.
 */
public class FunGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    /** a flat stone pad far out, its land loaded and kept */
    static BlockPos pad(GameTestHelper h, int slot) {
        ServerLevel l = h.getLevel();
        int x = -61000 - slot * 900, z = 61000;
        for (int cx = (x >> 4) - 8; cx <= (x >> 4) + 8; cx++)
            for (int cz = (z >> 4) - 8; cz <= (z >> 4) + 8; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -70; dx <= 70; dx++)
            for (int dz = -70; dz <= 70; dz++) {
                l.setBlock(m.set(x + dx, 119, z + dz), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 120; y < 150; y++) l.setBlock(m.set(x + dx, y, z + dz), Blocks.AIR.defaultBlockState(), 2);
            }
        return new BlockPos(x, 120, z);
    }

    static void unpad(GameTestHelper h, BlockPos at) {
        for (int cx = (at.getX() >> 4) - 8; cx <= (at.getX() >> 4) + 8; cx++)
            for (int cz = (at.getZ() >> 4) - 8; cz <= (at.getZ() >> 4) + 8; cz++)
                h.getLevel().setChunkForced(cx, cz, false);
    }

    /** one of him, fresh, by his own summon at that spot (calm, small) */
    static LivingEntity summon(GameTestHelper h, Giant g, BlockPos at, double size) {
        MinecraftServer s = h.getLevel().getServer();
        Set<UUID> before = new HashSet<>();
        for (LivingEntity e : FunGiants.standing(s, g.key())) before.add(e.getUUID());
        AdminServer.call(s, g, "summon", "minecraft:overworld " + (at.getX() + 0.5) + " " + at.getY() + " "
                + (at.getZ() + 0.5) + " - calm " + size);
        for (LivingEntity e : FunGiants.standing(s, g.key())) if (!before.contains(e.getUUID())) return e;
        return null;
    }

    private void everything(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        Giant g = Giants.byKey(key);
        String name = AdminServer.nameOf(g);
        BlockPos at = pad(h, slot);
        LivingEntity e = summon(h, g, at, 0.15);
        h.assertTrue(e != null, key + ": his summon made one");
        ServerPlayer p = PlayersGameTests.join(h, "fun_" + key.substring(0, 4), true);
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() - 20.5, 0, 0);
        h.runAfterDelay(20, () -> {
            JsonObject head = FunGiants.head(s, g);
            h.assertTrue(head != null, key + ": answers fun");
            h.assertTrue(FunGiants.does(s, g).containsAll(List.of("playas", "stats", "move")), key + ": does "
                    + head);
            h.assertTrue(FunGiants.places(s, g).contains("land"), key + ": lives on land: " + head);
            // his own stats
            e.addTag("jj_giants.health=2");
            var r = FunGiants.act(s, g, e.getStringUUID(), "stats", new JsonObject());
            h.assertTrue(r.ok() && r.lines().get(0).contains("health x2"), key + ": stats " + r.lines());
            e.removeTag("jj_giants.health=2");
            FunGiants.act(s, g, e.getStringUUID(), "stats", new JsonObject());
            // a name and title
            h.assertTrue(Marks.name(s, "Tester", e, "Grukk", "Event Boss", "gold", false).ok()
                    && e.getCustomName() != null && e.getCustomName().getString().equals("Grukk, Event Boss"),
                    key + ": named");
            Marks.name(s, "Tester", e, "", "", "white", false);
            // played as, and let go of
            JsonObject on = new JsonObject();
            on.addProperty("by", p.getStringUUID());
            on.addProperty("on", true);
            r = FunGiants.act(s, g, e.getStringUUID(), "playas", on);
            h.assertTrue(r.ok() && r.lines().get(0).startsWith("You're playing as the " + name), key + ": playas "
                    + r.lines());
        });
        h.runAfterDelay(40, () -> {
            JsonObject off = new JsonObject();
            off.addProperty("by", p.getStringUUID());
            off.addProperty("on", false);
            var r = FunGiants.act(s, g, e.getStringUUID(), "playas", off);
            h.assertTrue(r.ok() && r.lines().get(0).startsWith("You've stepped out of the " + name), key + ": off "
                    + r.lines());
            // moved and staying
            JsonObject mv = new JsonObject();
            mv.addProperty("dim", "minecraft:overworld");
            mv.addProperty("x", at.getX() + 30.5);
            mv.addProperty("y", at.getY());
            mv.addProperty("z", at.getZ() + 10.5);
            mv.addProperty("where", "land");
            mv.addProperty("stay", true);
            r = FunGiants.act(s, g, e.getStringUUID(), "move", mv);
            h.assertTrue(r.ok() && Math.hypot(e.getX() - (at.getX() + 30.5), e.getZ() - (at.getZ() + 10.5)) < 2,
                    key + ": moved " + r.lines() + " to " + e.position());
            e.discard();
            // his den, built and taken down
            p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            BlockState under = h.getLevel().getBlockState(at.below());
            h.assertTrue(Builds.den(s, "Tester", p, key, 0.1).ok(), key + ": den building");
            for (int i = 0; i < 4000 && Builds.busy(); i++) net.jj.giants.fun.FunTestAccess.buildTick(s);
            h.assertFalse(Builds.busy(), key + ": den built");
            FunWorld.Build b = FunWorld.get(s).s.builds.get(FunWorld.get(s).s.builds.size() - 1);
            h.assertTrue(b.state.equals("built"), key + ": " + b.state);
            h.assertTrue(Builds.takeDown(s, "Tester", b.n).ok(), key + ": taking down");
            for (int i = 0; i < 4000 && Builds.busy(); i++) net.jj.giants.fun.FunTestAccess.buildTick(s);
            h.assertTrue(h.getLevel().getBlockState(at.below()) == under, key + ": the land back");
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
            unpad(h, at);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_pitchgut")
    public void pitchgut(GameTestHelper h) {
        everything(h, "pitchgut", 0);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_furrowmaw")
    public void furrowmaw(GameTestHelper h) {
        everything(h, "furrowmaw", 1);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_cerberus")
    public void cerberus(GameTestHelper h) {
        everything(h, "cerberus", 2);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_hollowbell")
    public void hollowbell(GameTestHelper h) {
        everything(h, "hollowbell", 3);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_lanternwillow")
    public void lanternwillow(GameTestHelper h) {
        everything(h, "lanternwillow", 4);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_wreckback")
    public void wreckback(GameTestHelper h) {
        everything(h, "wreckback", 5);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_swarmforge")
    public void swarmforge(GameTestHelper h) {
        everything(h, "swarmforge", 6);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p7g_gloamwick")
    public void gloamwick(GameTestHelper h) {
        everything(h, "gloamwick", 7);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "p7g_fight")
    public void aStagedFightOfTwoRealKinds(GameTestHelper h) {
        if (!here("wreckback") || !here("cerberus")) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        BlockPos at = pad(h, 8);
        ServerPlayer p = PlayersGameTests.join(h, "fun_fight", true);
        p.teleportTo(h.getLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        Giant w = Giants.byKey("wreckback"), c = Giants.byKey("cerberus");
        h.runAfterDelay(5, () -> h.assertTrue(Fights.stage(s, p, new Fights.Side(w, 0.15, null), new Fights.Side(c,
                0.15, null), false).ok(), "staged"));
        h.runAfterDelay(80, () -> {
            h.assertTrue(Fights.lastMet.size() >= 2 && Fights.lastMet.stream().allMatch(l -> l.contains("goes for")
                    || l.contains("go for") || l.contains("fight")), "both went for the other: " + Fights.lastMet);
            for (LivingEntity e : FunGiants.standing(s, "wreckback"))
                if (e.distanceToSqr(p) < 200 * 200) e.discard();
            for (LivingEntity e : FunGiants.standing(s, "cerberus"))
                if (e.distanceToSqr(p) < 200 * 200) e.discard();
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
            unpad(h, at);
            h.succeed();
        });
    }
}
