package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.Look;
import net.jj.giants.systems.Remains;
import net.jj.giants.systems.Systems;
import net.jj.giants.systems.remains.Carcass;
import net.jj.giants.systems.SystemsData;
import net.jj.giants.systems.SystemsTags;
import net.jj.giants.systems.Weak;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Part 8 with the real giants (-Pgiants, ADMIN_BRIDGE.md 24): every giant answers "sys" (his weak points, what he
 * does), shows where his weak spots are on his body, takes a mood, a spot to walk to, grows with Growth (his own size
 * change), weakens with Hunger (the tags he reads) and is told to apply the weak points. Without the giants each test
 * passes at once.
 */
public class SystemsGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    private void everything(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        Giant g = Giants.byKey(key);
        BlockPos at = FunGiantsGameTests.pad(h, 20 + slot);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.15);
        h.assertTrue(e != null, key + ": his summon made one");
        h.runAfterDelay(20, () -> {
            try {
                // his header
                List<String> head = AdminServer.call(s, g, "sys", "1");
                h.assertTrue(head != null && head.size() == 1, key + ": answers sys: " + head);
                JsonObject o = net.jj.giants.admin.AdminJson.object(head.get(0));
                h.assertTrue(o != null && o.getAsJsonArray("weak").size() >= 1
                        && o.getAsJsonArray("does").toString().equals("[\"spots\",\"mood\",\"turn\",\"go\",\"counter\"]"),
                        key + ": " + o);
                // his weak spots, on his body
                JsonObject none = new JsonObject();
                none.addProperty("sys", 1);
                none.addProperty("do", "spots");
                none.addProperty("id", e.getStringUUID());
                List<String> sp = AdminServer.call(s, g, "sysact", none.toString());
                h.assertTrue(sp != null && sp.get(0).equals("ok"), key + ": spots " + sp);
                JsonObject spots = net.jj.giants.admin.AdminJson.object(sp.get(1));
                double reach = 2 * FunGiants.reach(key, FunGiants.sizeOf(e)) + 4; // (a worm lies stretched out)
                for (var x : spots.getAsJsonArray("spots")) {
                    JsonObject j = x.getAsJsonObject();
                    double d = Math.hypot(j.get("x").getAsDouble() - e.getX(), j.get("z").getAsDouble() - e.getZ());
                    h.assertTrue(d < reach && j.get("r").getAsDouble() > 0, key + ": a spot on him: " + j + " (" + d
                            + " of " + reach + ")");
                }
                h.assertTrue(Weak.glint(s, g, h.getLevel(), e.getStringUUID()) >= 1, key + ": a glint");
                // the weak points reach him with the all-giants values
                AdminServer.set(s, "Tester", "all", "weakPoints", "on");
                String uses = AdminServer.call(s, g, "admin", "1").get(0);
                h.assertTrue(uses.contains("all.weakPoints") && uses.contains("all.bodyHit"), key + ": uses " + uses);
                AdminWorld.get(s).remove("all", "all.weakPoints");
                // his mood, and a spot of his own to walk to
                JsonObject mood = new JsonObject();
                mood.addProperty("value", "hunting");
                List<String> mr = act(s, g, e, "mood", mood);
                h.assertTrue(mr != null && mr.get(0).equals("ok"), key + ": mood " + mr);
                // Fear: a player close by a hunting one slows and the dark closes in
                AdminServer.set(s, "Tester", "all", "fear", "strong");
                net.minecraft.server.level.ServerPlayer near = PlayersGameTests.join(h, "fear_" + key.substring(0, 4),
                        false);
                near.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                near.teleportTo(h.getLevel(), e.getX() + 4, e.getY(), e.getZ(), 0, 0);
                net.jj.giants.tools.Watch.forget();
                net.jj.giants.systems.Fear.tick(s);
                boolean afraid = near.hasEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
                String row = String.valueOf(net.jj.giants.tools.Watch.rows(s).get(key));
                s.getPlayerList().remove(near);
                AdminWorld.get(s).remove("all", "all.fear");
                h.assertTrue(afraid, key + ": fear near a hunting one: " + row);
                act(s, g, e, "mood", moodOf("calm"));
                JsonObject go = new JsonObject();
                go.addProperty("x", e.getX() + 25);
                go.addProperty("z", e.getZ());
                List<String> gr = act(s, g, e, "go", go);
                h.assertTrue(gr != null && gr.get(0).equals("ok"), key + ": go " + gr);
                // Growth: a win grows him (his own size change)
                AdminServer.set(s, "Tester", "all", "growth", "on");
                AdminServer.set(s, "Tester", "all", "growthPerDay", "0");
                AdminServer.set(s, "Tester", "all", "growthPerWin", "0.5");
                Look.lookForTests(s);
                SystemsData.G rec = SystemsData.get(s).s.giants.get(e.getStringUUID());
                h.assertTrue(rec != null, key + ": followed");
                rec.start = FunGiants.sizeOf(e);
                rec.wins = 1;
                for (int i = 0; i < 3; i++) Look.lookForTests(s);
                h.assertTrue(Math.abs(FunGiants.sizeOf(e) - rec.start * 1.5) < 0.02, key + ": grown to x1.5: "
                        + FunGiants.sizeOf(e) + " from " + rec.start);
                // (1.13.0) an admin's size from Live control sticks, and he grows on from it
                double admin = Math.round(rec.start * 1.2 * 1000) / 1000.0;
                net.minecraft.server.level.ServerPlayer op = PlayersGameTests.join(h, "size_" + key.substring(0, 4),
                        false);
                AdminServer.Result rr = net.jj.giants.admin.AdminLive.act(s, op, key, e.getStringUUID(), "size",
                        net.jj.giants.admin.AdminSetting.plain(admin));
                s.getPlayerList().remove(op);
                h.assertTrue(rr.ok(), key + ": Live control's size: " + rr.lines());
                for (int i = 0; i < 6; i++) Look.lookForTests(s);
                h.assertTrue(Math.abs(FunGiants.sizeOf(e) - admin) < 0.02 * admin + 0.002, key
                        + ": the admin's size sticks: " + FunGiants.sizeOf(e) + " not " + admin);
                rec.wins++;
                for (int i = 0; i < 3; i++) Look.lookForTests(s);
                h.assertTrue(Math.abs(FunGiants.sizeOf(e) - admin * 1.5) < 0.02 * admin + 0.002, key
                        + ": grows on from it: " + FunGiants.sizeOf(e) + " not " + admin * 1.5);
                // Hunger: starving, the tags he reads say so
                AdminServer.set(s, "Tester", "all", "growth", "off");
                AdminServer.set(s, "Tester", "all", "hunger", "on");
                AdminServer.set(s, "Tester", "all", "hungerWeak", "0.5");
                rec.food = 0;
                Look.lookForTests(s);
                h.assertTrue(e.getTags().contains("jj_giants.damage=0.5") && SystemsTags.sys(e, "speed") == 0.5,
                        key + ": weaker: " + e.getTags());
                // (1.13.0) grief healing on: a block he breaks drops nothing (it grows back); off: it drops
                net.minecraft.server.level.ServerLevel l = h.getLevel();
                BlockPos b = at.offset(14, 2, 14), c = at.offset(-14, 2, 14);
                l.setBlock(b, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 3);
                l.setBlock(c, net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState(), 3);
                AdminServer.set(s, "Tester", "all", "griefHeal", "slow");
                net.jj.giants.systems.Grief.tick(s);
                int waited = net.jj.giants.systems.Grief.waiting();
                net.jj.giants.tools.Stats.entityStarts(e);
                try {
                    l.destroyBlock(b, true);
                } finally {
                    net.jj.giants.tools.Stats.entityEnds();
                }
                h.assertTrue(l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(b).inflate(3)).isEmpty(), key + ": his break dropped something");
                h.assertTrue(net.jj.giants.systems.Grief.waiting() == waited + 1, key + ": it waits to grow back");
                AdminWorld.get(s).remove("all", "all.griefHeal");
                AdminServer.push(s, AdminWorld.get(s));
                net.jj.giants.systems.Grief.tick(s);
                net.jj.giants.tools.Stats.entityStarts(e);
                try {
                    l.destroyBlock(c, true);
                } finally {
                    net.jj.giants.tools.Stats.entityEnds();
                }
                var dropped = l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                        new net.minecraft.world.phys.AABB(c).inflate(3));
                h.assertTrue(!dropped.isEmpty(), key + ": grief healing off: his break drops");
                dropped.forEach(net.minecraft.world.entity.Entity::discard);
            } finally {
                for (String id : List.of("all.growth", "all.growthPerDay", "all.growthPerWin", "all.hunger",
                        "all.hungerWeak", "all.weakPoints", "all.griefHeal"))
                    AdminWorld.get(s).remove("all", id);
                AdminServer.push(s, AdminWorld.get(s));
                Look.lookForTests(s);
                e.discard();
                FunGiantsGameTests.unpad(h, at);
            }
            h.assertTrue(!e.getTags().contains("jj_giants.damage=0.5"), key + ": back to x1");
            h.succeed();
        });
    }

    private static JsonObject moodOf(String m) {
        JsonObject o = new JsonObject();
        o.addProperty("value", m);
        return o;
    }

    private static List<String> act(MinecraftServer s, Giant g, LivingEntity e, String what, JsonObject more) {
        JsonObject o = more.deepCopy();
        o.addProperty("sys", 1);
        o.addProperty("do", what);
        o.addProperty("id", e.getStringUUID());
        return AdminServer.call(s, g, "sysact", o.toString());
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_pitchgut")
    public void pitchgut(GameTestHelper h) {
        everything(h, "pitchgut", 0);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_furrowmaw")
    public void furrowmaw(GameTestHelper h) {
        everything(h, "furrowmaw", 1);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_cerberus")
    public void cerberus(GameTestHelper h) {
        everything(h, "cerberus", 2);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_hollowbell")
    public void hollowbell(GameTestHelper h) {
        everything(h, "hollowbell", 3);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_lanternwillow")
    public void lanternwillow(GameTestHelper h) {
        everything(h, "lanternwillow", 4);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_wreckback")
    public void wreckback(GameTestHelper h) {
        everything(h, "wreckback", 5);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_swarmforge")
    public void swarmforge(GameTestHelper h) {
        everything(h, "swarmforge", 6);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "p8_real_gloamwick")
    public void gloamwick(GameTestHelper h) {
        everything(h, "gloamwick", 7);
    }

    // his remains, from his own model (the bones pass)

    private void remains(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        ServerLevel l = h.getLevel();
        h.assertTrue(Remains.ownDesign(key), key + ": his remains are made from his own model");
        Carcass.Out big = Remains.planForTests(key, 1, 0), small = Remains.planForTests(key, 0.25, 0);
        // built: a fresh corpse is a lot of him; the skeleton less; each stage less again
        h.assertTrue(big.count(0) > 40000 && big.count(4) > 4000, key + ": " + big.count(0) + " / " + big.count(4));
        h.assertTrue(big.count(0) >= big.count(1) && big.count(1) > big.count(2) && big.count(2) >= big.count(3) * 0.95,
                key + ": rots stage by stage " + big.count(0) + " " + big.count(1) + " " + big.count(2) + " "
                        + big.count(3));
        // scaled: a quarter the size is about a sixteenth of the blocks, a quarter as long
        double ratio = small.count(0) / (double) big.count(0);
        h.assertTrue(ratio > 1 / 40.0 && ratio < 1 / 6.0, key + ": scaled blocks " + ratio);
        int bigW = width(big), smallW = width(small);
        h.assertTrue(smallW > bigW / 6 && smallW < bigW / 2.5, key + ": scaled width " + smallW + " / " + bigW);
        // as wide as he is (lying, give or take)
        double his = 2 * FunGiants.reach(key, 1);
        h.assertTrue(bigW > his * 0.4 && bigW < his * 2.2, key + ": " + bigW + " wide, he is " + his);
        // and down in the world, a small one: over several ticks, rotting away a stage at a time
        BlockPos at = FunGiantsGameTests.pad(h, 30 + slot);
        // (the test world is kept between runs: an earlier run's skeleton, left for good, may stand taller than the
        // pad clears, or reach under it)
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dx = -90; dx <= 90; dx++)
            for (int dz = -90; dz <= 90; dz++)
                for (int y = 90; y < 260; y++)
                    if (y != 119 && !l.getBlockState(mp.set(at.getX() + dx, y, at.getZ() + dz)).isAir())
                        l.setBlock(mp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
        Systems.forget();
        SystemsData.get(s).s.remains.clear();
        long[] slow = new long[1];
        SystemsData.Remains r = Remains.lay(l, key, at.getX(), at.getZ(), at.getY(), 0, 0.2, "corpse", 3, 0, 5L);
        int[] counted = new int[5];
        h.startSequence().thenWaitUntil(() -> h.assertTrue(!Remains.busy(), key + ": going down")).thenExecute(() -> {
            counted[0] = Remains.ours(l, r);
            h.assertTrue(counted[0] > 500, key + ": his corpse lies there: " + counted[0]);
            h.assertTrue(Remains.slowestTickMs() < 60, key + ": no tick held up: " + Remains.slowestTickMs() + " ms");
            // mined like any block
            BlockPos one = Remains.anyBlock(l, r);
            h.assertTrue(one != null && l.destroyBlock(one, false) && l.getBlockState(one).isAir(), key + ": mined");
            r.fell -= r.rotTicks / 4 + 5;
        }).thenWaitUntil(() -> h.assertTrue(r.stage == 1 && !Remains.busy(), key + ": stage 1")).thenExecute(() -> {
            counted[1] = Remains.ours(l, r);
            r.fell -= r.rotTicks / 4 + 5;
        }).thenWaitUntil(() -> h.assertTrue(r.stage == 2 && !Remains.busy(), key + ": stage 2")).thenExecute(() -> {
            counted[2] = Remains.ours(l, r);
            h.assertTrue(counted[2] < counted[0], key + ": rotting " + counted[0] + " -> " + counted[2]);
            r.fell -= r.rotTicks + 5;
        }).thenWaitUntil(() -> h.assertTrue(SystemsData.get(s).s.remains.isEmpty() && !Remains.busy(),
                key + ": gone")).thenExecute(() -> {
            int left = Remains.ours(l, r);
            h.assertTrue(left == 0, key + ": a corpse goes in the end: " + left + " left, e.g. " + Remains.anyBlock(l, r)
                    + " " + (Remains.anyBlock(l, r) == null ? "" : l.getBlockState(Remains.anyBlock(l, r))));
            // the skeleton stays
            Remains.lay(l, key, at.getX(), at.getZ(), at.getY(), 90, 0.2, "skeleton", 3, 4, 6L);
        }).thenWaitUntil(() -> h.assertTrue(!Remains.busy(), key + ": bones going down")).thenExecute(() -> {
            h.assertTrue(SystemsData.get(s).s.remains.isEmpty(), key + ": a skeleton is left for good");
            FunGiantsGameTests.unpad(h, at);
            h.succeed();
        });
    }

    static int width(Carcass.Out o) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, lz = Integer.MAX_VALUE, hz = Integer.MIN_VALUE;
        for (int i = 0; i < o.n; i++) {
            if (o.st[0][i] == 0) continue;
            lo = Math.min(lo, o.x[i]);
            hi = Math.max(hi, o.x[i]);
            lz = Math.min(lz, o.z[i]);
            hz = Math.max(hz, o.z[i]);
        }
        return Math.max(hi - lo, hz - lz) + 1;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_pitchgut")
    public void pitchgutRemains(GameTestHelper h) {
        remains(h, "pitchgut", 0);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_furrowmaw")
    public void furrowmawRemains(GameTestHelper h) {
        remains(h, "furrowmaw", 1);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_cerberus")
    public void cerberusRemains(GameTestHelper h) {
        remains(h, "cerberus", 2);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_hollowbell")
    public void hollowbellRemains(GameTestHelper h) {
        remains(h, "hollowbell", 3);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_lanternwillow")
    public void lanternwillowRemains(GameTestHelper h) {
        remains(h, "lanternwillow", 4);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_wreckback")
    public void wreckbackRemains(GameTestHelper h) {
        remains(h, "wreckback", 5);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_swarmforge")
    public void swarmforgeRemains(GameTestHelper h) {
        remains(h, "swarmforge", 6);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1600, batch = "bones_gloamwick")
    public void gloamwickRemains(GameTestHelper h) {
        remains(h, "gloamwick", 7);
    }
}
