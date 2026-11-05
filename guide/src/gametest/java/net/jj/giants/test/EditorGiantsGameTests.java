package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.editor.Editor;
import net.jj.giants.editor.EditorWorld;
import net.jj.giants.editor.LookSets;
import net.jj.giants.systems.remains.GiantModel;
import net.jj.giants.systems.remains.GiantModels;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Part 12 with the real giants (-Pgiants, ADMIN_BRIDGE.md §29): every giant takes looks ("look"); every built-in set
 * worked out for his own model changes most of his blocks, only into blocks the game knows; a look for his kind and one
 * for one of him (summoned small) are both sent to the players' games with his own look first, and his own mod takes
 * what is sent ("lookclient", and {} to put him back). Without the giants each test passes at once.
 */
public class EditorGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    private void each(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        Giant g = Giants.byKey(key);
        EditorWorld w = EditorWorld.get(s);
        h.assertTrue(Editor.takesLooks(s, g), key + ": answers look");
        GiantModel m = GiantModels.get(key);
        h.assertTrue(m != null, key + ": his model is read");
        for (String set : LookSets.BUILT_IN) {
            Map<String, String> map = Editor.mapFor(s, key, set);
            h.assertTrue(map != null, key + " " + set + ": a map");
            int changed = 0;
            for (String e : m.palette) if (map.containsKey(e) && !map.get(e).equals(e)) changed++;
            h.assertTrue(changed * 2 >= m.palette.length, key + " " + set + ": changes " + changed + " of "
                    + m.palette.length);
            for (String to : map.values()) h.assertTrue(LookSets.state(to) != null, key + " " + set + ": " + to);
        }
        BlockPos at = FunGiantsGameTests.pad(h, slot);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.1);
        h.assertTrue(e != null, key + ": none summoned");
        h.runAfterDelay(10, () -> {
            h.assertTrue(Editor.setKind(s, "Tester", key, "frost").ok(), key + ": kind");
            h.assertTrue(Editor.setGiant(s, "Tester", e, "gold").ok(), key + ": one giant");
            h.assertTrue(Editor.lookOf(s, e).equals("gold"), key + ": his own first");
            JsonObject k = Editor.looksJson(s).getAsJsonObject("kinds").getAsJsonObject(key);
            h.assertTrue(k != null && k.get("kind").getAsString().startsWith("frost#"), key + ": kind sent " + k);
            String his = k.getAsJsonObject("giants").get(e.getStringUUID()).getAsString();
            h.assertTrue(his.startsWith("gold#") && k.getAsJsonObject("maps").getAsJsonObject(his).size() > 0, key
                    + ": his own sent");
            // his mod takes it (the same code each player's game runs), and {} puts him back
            List<String> r = AdminServer.call(s, g, "lookclient", k.toString());
            h.assertTrue(r != null && r.get(0).equals("ok"), key + ": lookclient " + r);
            // (1.16) his minions wear his look: a block of theirs he hasn't got takes the look's block nearest in colour
            try {
                Class<?> gl = Class.forName(g.bridge().substring(0, g.bridge().lastIndexOf('.')) + ".GiantsLook");
                var any = gl.getMethod("blockAny", String.class, String.class);
                String kindKey = k.get("kind").getAsString();
                java.util.Map.Entry<String, com.google.gson.JsonElement> one = k.getAsJsonObject("maps")
                        .getAsJsonObject(kindKey).entrySet().iterator().next();
                h.assertTrue(any.invoke(null, kindKey, one.getKey()).equals(one.getValue().getAsString()), key
                        + ": his own block in the look");
                String other = (String) any.invoke(null, kindKey, "minecraft:emerald_block");
                h.assertTrue(net.jj.giants.editor.LookSets.state(other) != null, key + ": a minion's block " + other);
                h.assertTrue(any.invoke(null, null, "minecraft:emerald_block").equals("minecraft:emerald_block"),
                        key + ": no look, as always");
                h.assertTrue(gl.getMethod("ofMinion", net.minecraft.world.entity.Entity.class, Class.class) != null,
                        key + ": ofMinion");
            } catch (ReflectiveOperationException ex) {
                h.fail(key + ": GiantsLook.blockAny/ofMinion: " + ex);
            }
            List<String> off = AdminServer.call(s, g, "lookclient", "{}");
            h.assertTrue(off != null && off.get(0).equals("ok"), key + ": lookclient {} " + off);
            List<String> newer = AdminServer.call(s, g, "lookclient", "{\"look\":9}");
            h.assertTrue(newer == null, key + ": a newer look is left alone");
            Editor.setGiant(s, "Tester", e, "");
            Editor.setKind(s, "Tester", key, "");
            h.assertTrue(!Editor.looksJson(s).getAsJsonObject("kinds").has(key), key + ": back to none");
            e.discard();
            FunGiantsGameTests.unpad(h, at);
            w.setDirty();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_pitchgut")
    public void pitchgutLooks(GameTestHelper h) {
        each(h, "pitchgut", 60);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_furrowmaw")
    public void furrowmawLooks(GameTestHelper h) {
        each(h, "furrowmaw", 61);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_cerberus")
    public void cerberusLooks(GameTestHelper h) {
        each(h, "cerberus", 62);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_hollowbell")
    public void hollowbellLooks(GameTestHelper h) {
        each(h, "hollowbell", 63);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_lanternwillow")
    public void lanternwillowLooks(GameTestHelper h) {
        each(h, "lanternwillow", 64);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_wreckback")
    public void wreckbackLooks(GameTestHelper h) {
        each(h, "wreckback", 65);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_swarmforge")
    public void swarmforgeLooks(GameTestHelper h) {
        each(h, "swarmforge", 66);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "p12g_gloamwick")
    public void gloamwickLooks(GameTestHelper h) {
        each(h, "gloamwick", 67);
    }
}
