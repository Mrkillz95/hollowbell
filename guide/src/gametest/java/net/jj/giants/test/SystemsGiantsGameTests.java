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
import net.jj.giants.systems.SystemsData;
import net.jj.giants.systems.SystemsTags;
import net.jj.giants.systems.Weak;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
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
                        && o.getAsJsonArray("does").toString().equals("[\"spots\",\"mood\",\"turn\",\"go\"]"),
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
                // Hunger: starving, the tags he reads say so
                AdminServer.set(s, "Tester", "all", "growth", "off");
                AdminServer.set(s, "Tester", "all", "hunger", "on");
                AdminServer.set(s, "Tester", "all", "hungerWeak", "0.5");
                rec.food = 0;
                Look.lookForTests(s);
                h.assertTrue(e.getTags().contains("jj_giants.damage=0.5") && SystemsTags.sys(e, "speed") == 0.5,
                        key + ": weaker: " + e.getTags());
            } finally {
                for (String id : List.of("all.growth", "all.growthPerDay", "all.growthPerWin", "all.hunger",
                        "all.hungerWeak", "all.weakPoints"))
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
}
