package net.jj.hollowbell.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.Detail;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsPerf;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;

/**
 * The Giants Guide's performance screen: his list of settings, and the levels coming back. With every level at its
 * default (the Highest preset) nothing about him changes.
 */
public class PerfTests implements FabricGameTest {
    private static final String[] IDS = {
        "fx.particles",
        "fx.bigImpacts",
        "fx.smokeTrails",
        "fx.flashes",
        "fx.screenShake",
        "fx.screenEffects",
        "fx.ambient",
        "detail.model",
        "detail.animation",
        "detail.farSight",
        "detail.farSightDistance",
        "detail.lootBeams",
        "detail.sounds",
        "detail.music",
        "detail.renderDistance",
        "hollowbell.soundRings",
        "hollowbell.rimShiver",
        "hollowbell.venomClouds",
        "hollowbell.stingerStreaks",
        "hollowbell.deepTollFx",
        "hollowbell.sunLancesFx",
        "hollowbell.bellings"
    };

    /** everything his effects read from the levels, as numbers, to compare */
    private static List<Double> readings() {
        List<Double> out = new ArrayList<>();
        for (String id : IDS) {
            out.add((double) GiantsPerf.f(id));
            out.add(GiantsPerf.on(id) ? 1.0 : 0.0);
            out.add((double) GiantsPerf.choice(id));
            out.add((double) GiantsPerf.world(id));
            out.add(GiantsPerf.worldOn(id) ? 1.0 : 0.0);
        }
        for (double d = 10; d < 3000; d *= 1.7)
            for (float s : new float[] {0.05f, 0.3f, 1f, 2f}) out.add((double) Detail.lod(d, s));
        out.add((double) Detail.farSightBlocks());
        out.add(Detail.renderDistance());
        out.add((double) Detail.settleEvery(Vec3.ZERO, 1f));
        out.add((double) GiantsPerf.count(Math.max(0, BellConfig.V.shedCount), GiantsPerf.world("hollowbell.bellings")));
        out.add((double) GiantsPerf.count(10, GiantsPerf.world("hollowbell.bellings")));
        return out;
    }

    /** a perfset with every value at its default, the way the guide sends Highest */
    private static String highest(String side) {
        List<String> lines = GiantsBridge.giants(null, "perf", "1");
        JsonObject head = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        JsonObject values = new JsonObject();
        for (var u : head.getAsJsonArray("uses")) {
            String id = u.getAsString();
            if (id.equals("detail.animation") || id.equals("detail.lootBeams")) values.addProperty(id, 0);
            else if (id.equals("detail.farSight") || id.equals("fx.fog")) values.addProperty(id, true);
            else values.addProperty(id, 1);
        }
        for (int i = 1; i < lines.size(); i++) {
            JsonObject o = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            values.add(o.get("id").getAsString(), o.get("default"));
        }
        JsonObject msg = new JsonObject();
        msg.addProperty("perf", 1);
        msg.addProperty("side", side);
        msg.add("values", values);
        return msg.toString();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "perf_list")
    public void heListsWhatHeReads(GameTestHelper h) {
        List<String> lines = GiantsBridge.giants(null, "perf", "1");
        h.assertTrue(lines != null && !lines.isEmpty(), "no perf list");
        h.assertTrue(lines.get(0).startsWith("{\"perf\":1,"), "the header is " + lines.get(0));
        JsonObject head = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        h.assertTrue("hollowbell".equals(head.get("giant").getAsString()), "the header doesn't name him");
        List<String> uses = new ArrayList<>();
        for (var u : head.getAsJsonArray("uses")) uses.add(u.getAsString());
        for (String want : new String[] {"fx.particles", "fx.bigImpacts", "detail.model", "detail.renderDistance"})
            h.assertTrue(uses.contains(want), "he doesn't list " + want);
        h.assertFalse(uses.contains("feature.meetings"), "he lists a setting he doesn't read");
        h.assertTrue(lines.size() - 1 <= 64, "too many settings of his own");
        int server = 0;
        for (int i = 1; i < lines.size(); i++) {
            JsonObject o = JsonParser.parseString(lines.get(i)).getAsJsonObject();
            String id = o.get("id").getAsString();
            h.assertTrue(id.startsWith("hollowbell.") && id.length() <= 64, "a bad id: " + id);
            String kind = o.get("kind").getAsString();
            h.assertTrue(kind.equals("toggle") || kind.equals("slider"), id + " is a " + kind);
            h.assertTrue(o.getAsJsonArray("levels").size() == 5, id + " hasn't five levels");
            h.assertTrue(o.get("name").getAsString().length() <= 48, id + "'s name is too long");
            if ("server".equals(o.get("side").getAsString())) {
                server++;
                h.assertTrue("features".equals(o.get("section").getAsString()), id + " isn't under features");
                h.assertTrue(o.get("ops").getAsBoolean(), id + " isn't for operators");
            }
        }
        h.assertTrue(server == 1, server + " world settings, not just the Belling count");
        h.assertTrue(GiantsBridge.giants(null, "perfset", "not json") == null, "rubbish was taken");
        h.assertTrue(
                GiantsBridge.giants(null, "perfset", "{\"perf\":2,\"side\":\"client\",\"values\":{}}") == null,
                "a newer protocol was taken");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "perf_highest")
    public void highestChangesNothing(GameTestHelper h) {
        GiantsPerf.reset();
        try {
            List<Double> before = readings();
            h.assertTrue(GiantsBridge.giants(null, "perfset", highest("client")) != null, "the client levels weren't taken");
            h.assertTrue(GiantsBridge.giants(null, "perfset", highest("server")) != null, "the world levels weren't taken");
            List<Double> after = readings();
            h.assertTrue(before.equals(after), "Highest changed something: " + before + " -> " + after);
            // and turned down, it does change
            h.assertTrue(
                    GiantsBridge.giants(
                                    null,
                                    "perfset",
                                    "{\"perf\":1,\"side\":\"client\",\"values\":{\"detail.model\":0.3,"
                                            + "\"detail.farSight\":false,\"detail.renderDistance\":0.5,"
                                            + "\"detail.animation\":2,\"hollowbell.rimShiver\":0}}")
                            != null,
                    "lowered levels weren't taken");
            h.assertTrue(Detail.farSightBlocks() == 0, "far sight is still on");
            h.assertTrue(
                    Math.abs(Detail.renderDistance() - BellConfig.V.renderDistance * 0.5) < 1e-3,
                    "the render distance didn't halve");
            h.assertTrue(Detail.settleEvery(Vec3.ZERO, 1f) == 12, "the animation wasn't made simpler");
            h.assertTrue(
                    Detail.lod(BellConfig.V.simpleFarAwayAt * 0.5, 1f) != Detail.FULL,
                    "the simpler drawing doesn't start sooner");
            h.assertTrue(GiantsPerf.f("hollowbell.rimShiver") == 0f, "his own setting wasn't read");
            h.assertTrue(GiantsPerf.world("hollowbell.bellings") == 1f, "a client message changed the world");
            GiantsBridge.giants(
                    null, "perfset", "{\"perf\":1,\"side\":\"server\",\"values\":{\"hollowbell.bellings\":0.3}}");
            h.assertTrue(GiantsPerf.count(4, GiantsPerf.world("hollowbell.bellings")) == 1, "the Belling count is off");
            h.assertTrue(GiantsPerf.count(10, GiantsPerf.world("hollowbell.bellings")) == 3, "the hatch cap is off");
            h.assertTrue(GiantsPerf.count(0, 1f) == 0, "shedding turned off came back");
        } finally {
            GiantsPerf.reset();
        }
        h.succeed();
    }
}
