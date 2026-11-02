package net.jj.hollowbell.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * The admin settings bridge (ADMIN_BRIDGE.md in the handoff folder), with no Giants Guide here: his list covers every
 * config field, the world's values go on top of his config file and never into it, the old commands still change the
 * file, the all-giants multipliers and each move's switch, wait and wind work, and with nothing set nothing changes.
 * Each test sets and clears the values within one tick (they are the whole server's while set).
 */
public class AdminTests implements FabricGameTest {

    private static MinecraftServer server(GameTestHelper h) {
        return h.getLevel().getServer();
    }

    private static List<String> set(GameTestHelper h, String json) {
        return GiantsBridge.giants(server(h), "adminset", json);
    }

    private static JsonObject line(String id) {
        for (String l : GiantsAdmin.describe())
            if (l.contains("\"id\":\"" + id + "\"")) return JsonParser.parseString(l).getAsJsonObject();
        return null;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "admin_list")
    public void hisListCoversEveryConfigField(GameTestHelper h) {
        List<String> lines = GiantsBridge.giants(null, "admin", "1");
        h.assertTrue(lines != null && lines.get(0).startsWith("{\"admin\":1,\"giant\":\"hollowbell\""), "the header");
        Set<String> ids = new HashSet<>();
        for (GiantsAdmin.Row r : GiantsAdmin.rows()) {
            h.assertTrue(ids.add(r.id()), "one row per id: " + r.id());
            h.assertTrue(List.of("spawning", "worldgen", "stats", "behaviour", "moves", "healing", "taming", "loot",
                    "players", "effects", "load").contains(r.cat()), r.id() + " has a category");
            h.assertTrue(List.of(GiantsAdmin.LIVE, GiantsAdmin.NEW_SPAWNS, GiantsAdmin.NEW_LAND,
                    GiantsAdmin.WORLD_CREATION, GiantsAdmin.RESTART).contains(r.when()), r.id() + " says when");
            h.assertTrue(!r.name().isEmpty() && r.name().length() <= 64, r.id() + " has a name");
            h.assertTrue(!r.what().isEmpty() && r.what().length() <= 240, r.id() + " says what it does");
            h.assertTrue(r.id().matches("[A-Za-z0-9_.-]{1,64}") && !r.id().startsWith("all."), r.id() + " id");
            h.assertTrue(r.min() <= r.max(), r.id() + " range");
        }
        h.assertTrue(ids.size() <= 512, "at most 512 settings");
        // every config field is an admin setting or deliberately not one (a new field must pick)
        for (Field f : BellConfig.Values.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            h.assertTrue(ids.contains(f.getName()) ^ GiantsAdmin.NOT_ADMIN.contains(f.getName()),
                    f.getName() + " is either an admin setting or listed as not one");
        }
        // each fighting move: on, wait and wind
        for (int m : Moves.ORDER)
            for (String k : new String[] {".on", ".cooldown", ".wind"})
                h.assertTrue(ids.contains("move." + Moves.NAMES[m] + k), Moves.NAMES[m] + k);
        h.assertTrue(lines.size() == GiantsAdmin.rows().size() + 1, "one line each");
        JsonObject home = line("homeRadius");
        h.assertTrue(home.get("when").getAsString().equals("newLand")
                        && home.get("cat").getAsString().equals("worldgen"),
                "his ground's size only reaches new land");
        JsonObject grab = line("move.grab.on");
        h.assertTrue(grab.get("name").getAsString().equals("Grab and lift: on"),
                "the move's name from his language file");
        h.assertTrue(grab.get("group").getAsString().equals("grab"), "the move's rows are grouped");
        // nothing set: nothing is, and every value is the file's
        for (String l : lines.subList(1, lines.size())) {
            JsonObject o = JsonParser.parseString(l).getAsJsonObject();
            h.assertFalse(o.get("set").getAsBoolean(), o.get("id") + " isn't set");
            h.assertTrue(o.get("value").equals(o.get("default")), o.get("id") + " is the server default");
        }
        // no Giants Guide here: /giants has no admin, and he still answers the bridge on his own
        var giants = server(h).getCommands().getDispatcher().getRoot().getChild("giants");
        h.assertTrue(giants == null || giants.getChild("admin") == null, "no /giants admin without the guide");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "admin_values")
    public void worldValuesGoOnTopOfTheFileAndBack(GameTestHelper h) {
        float file = BellConfig.V.healthMultiplier;
        int limit = BellConfig.V.maxInWorld;
        boolean harvest = BellConfig.V.harvest;
        try {
            List<String> r = set(h, "{\"admin\":1,\"values\":{\"healthMultiplier\":2.5,\"maxInWorld\":4,"
                    + "\"harvest\":" + !harvest + ",\"move.grab.on\":false,\"move.sky_dive.cooldown\":2,"
                    + "\"move.sky_dive.wind\":0.5}}");
            h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
            h.assertTrue(BellConfig.V.healthMultiplier == 2.5f && BellConfig.V.maxInWorld == 4, "in use now");
            h.assertTrue(BellConfig.V.harvest == !harvest, "a switch too");
            h.assertTrue(BellConfig.fileValues().healthMultiplier == file, "the file keeps its own");
            JsonObject l = line("healthMultiplier");
            h.assertTrue(l.get("value").getAsDouble() == 2.5 && l.get("default").getAsDouble() == file
                    && l.get("set").getAsBoolean(), "the list: value, server default, set: " + l);
            h.assertFalse(GiantsAdmin.moveOn(Moves.GRAB), "grab off");
            h.assertTrue(GiantsAdmin.moveOn(Moves.LASH), "the others on");
            h.assertTrue(GiantsAdmin.moveCooldown(Moves.SKY_DIVE) == 2f, "the dive waits twice as long");
            h.assertTrue(CodexOrders.attackCool(Moves.SKY_DIVE) == 1400,
                    "the book's wait too: " + CodexOrders.attackCool(Moves.SKY_DIVE));
            h.assertTrue(Math.abs(CodexOrders.windCost(CodexPayload.ATTACK_MOVE, Moves.SKY_DIVE) - 0.275f) < 1e-5,
                    "half the wind");
            // a save (any old command) writes the file's values, never the world's
            BellConfig.save();
            h.assertTrue(BellConfig.fileValues().healthMultiplier == file && BellConfig.V.healthMultiplier == 2.5f,
                    "saving keeps them apart");
            // anything left out goes back to the file
            set(h, "{\"admin\":1,\"values\":{}}");
            h.assertTrue(BellConfig.V.healthMultiplier == file && BellConfig.V.maxInWorld == limit, "back");
            h.assertTrue(BellConfig.V.harvest == harvest, "the switch back");
            h.assertTrue(GiantsAdmin.moveOn(Moves.GRAB), "grab back on");
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "admin_old")
    public void theOldCommandsStillChangeTheServerDefault(GameTestHelper h) {
        float file = BellConfig.V.healthMultiplier;
        try {
            set(h, "{\"admin\":1,\"values\":{\"healthMultiplier\":3}}");
            List<String> said = GiantsBridge.giants(server(h), "healthmult", "1.5");
            // (no Giants Guide here: nothing said is the server default, and the reply says so)
            h.assertTrue(said.get(0).equals("Hollowbell: health x1.5 for all worlds (server default; per-world"
                    + " settings need the Giants Guide). This world keeps its own: 3"
                    + " (/giants admin reset hollowbell healthMultiplier)."), "" + said);
            h.assertTrue(BellConfig.fileValues().healthMultiplier == 1.5f, "the server default changed");
            h.assertTrue(BellConfig.V.healthMultiplier == 3f, "the world's own still wins");
            said = GiantsBridge.giants(server(h), "config", "healthmultiplier 2");
            h.assertTrue(said.get(0).startsWith("Hollowbell: healthmultiplier = 2.0 for all worlds (server default;")
                    && said.get(0).contains("This world keeps its own: 3"), "" + said);
            GiantsAdmin.clear();
            h.assertTrue(BellConfig.V.healthMultiplier == 2f, "without the world's, the file's");
            // without any world values the old commands say exactly what they said
            said = GiantsBridge.giants(server(h), "damagemult", "");
            h.assertTrue(said.get(0).equals("Hollowbell: damage x" + plain(BellConfig.V.damageMultiplier) + "."),
                    "" + said);
            said = GiantsBridge.giants(server(h), "config", "healthMultiplier 2");
            h.assertTrue(said.get(0).equals("Hollowbell: healthMultiplier = 2.0 for all worlds (server default;"
                    + " per-world settings need the Giants Guide)."), "" + said);
        } finally {
            GiantsAdmin.clear();
            BellConfig.V.healthMultiplier = file;
            BellConfig.save();
        }
        h.succeed();
    }

    private static String plain(float v) {
        return java.math.BigDecimal.valueOf(Math.round(v * 100.0f) / 100.0).stripTrailingZeros().toPlainString();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "admin_all")
    public void theAllGiantsMultipliersApply(GameTestHelper h) {
        HollowbellEntity w = ModEntities.HOLLOWBELL.create(h.getLevel());
        w.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        w.setBellScale(0.1f);
        w.setMoveCooldown(100000);
        w.skipArrival();
        h.getLevel().addFreshEntity(w);
        float max = w.healthMax();
        float cost = CodexOrders.windCost(CodexPayload.ATTACK_MOVE, Moves.GRAB);
        int cool = CodexOrders.attackCool(Moves.GRAB);
        try {
            set(h, "{\"admin\":1,\"values\":{},\"all\":{\"all.health\":2,\"all.damage\":3,\"all.speed\":1.5,"
                    + "\"all.size\":2,\"all.cooldowns\":0.5,\"all.healing\":0,\"all.windCost\":2,\"all.unknown\":9}}");
            h.assertTrue(Math.abs(w.healthMax() - max * 2) < 1f, "his health doubled at once: "
                    + w.healthMax() + " from " + max);
            h.assertTrue(HollowbellEntity.healthTimes() == BellConfig.V.healthMultiplier * 2, "health x2");
            h.assertTrue(GiantsAdmin.all("all.damage") == 3f && GiantsAdmin.all("all.speed") == 1.5f, "damage, speed");
            h.assertTrue(GiantsAdmin.all("all.healing") == 0f, "no healing");
            h.assertTrue(GiantsAdmin.sized(0.5f) == 1f && GiantsAdmin.sized(1.5f) == HollowbellEntity.MAX_SCALE,
                    "sizes doubled, within his biggest");
            h.assertTrue(CodexOrders.attackCool(Moves.GRAB) == cool / 2, "the book's waits halved");
            h.assertTrue(Math.abs(CodexOrders.windCost(CodexPayload.ATTACK_MOVE, Moves.GRAB) - cost * 2) < 1e-5,
                    "wind doubled");
            h.assertTrue(Math.abs(CodexOrders.windCost(CodexPayload.COME, 0) - 0.04f) < 1e-5,
                    "every order's wind doubled");
            h.assertTrue(GiantsAdmin.all("all.unknown") == 1f, "one he doesn't apply is left out");
            JsonObject head = JsonParser.parseString(GiantsAdmin.describe().get(0)).getAsJsonObject();
            h.assertTrue(head.getAsJsonObject("all").get("all.health").getAsDouble() == 2.0, "the header says so");
        } finally {
            set(h, "{\"admin\":1}");
            GiantsAdmin.clear();
        }
        h.assertTrue(Math.abs(w.healthMax() - max) < 1f, "his health back as it was");
        h.assertTrue(GiantsAdmin.sized(0.5f) == 0.5f, "x1: the very same number");
        h.assertTrue(CodexOrders.windCost(CodexPayload.ATTACK_MOVE, Moves.GRAB) == cost, "x1: the same wind");
        h.assertTrue(CodexOrders.attackCool(Moves.GRAB) == cool, "x1: the same wait");
        w.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "admin_moves")
    public void aMoveTurnedOffIsNeverPicked(GameTestHelper h) {
        HollowbellEntity w = ModEntities.HOLLOWBELL.create(h.getLevel());
        w.moveTo(h.absoluteVec(new net.minecraft.world.phys.Vec3(1, 2, 1)));
        w.setBellScale(0.1f);
        w.setMoveCooldown(100000);
        w.skipArrival();
        h.getLevel().addFreshEntity(w);
        net.minecraft.world.entity.animal.Pig pig =
                h.spawn(net.minecraft.world.entity.EntityType.PIG, new net.minecraft.core.BlockPos(2, 2, 2));
        // (a moment for his body to be put together where he hangs)
        h.runAfterDelay(40, () -> {
            try {
                int[] before = w.moves().weights(pig, null);
                StringBuilder off = new StringBuilder();
                for (int m = 1; m < before.length; m++)
                    if (before[m] > 0)
                        off.append(off.length() == 0 ? "" : ",")
                                .append("\"move.")
                                .append(Moves.NAMES[m])
                                .append(".on\":false");
                h.assertTrue(off.length() > 0, "some move could land on the pig");
                set(h, "{\"admin\":1,\"values\":{" + off + "}}");
                for (int x : w.moves().weights(pig, null))
                    h.assertTrue(x == 0, "a move turned off was in the running");
            } finally {
                GiantsAdmin.clear();
                w.discard();
                pig.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "admin_bad")
    public void badValuesAreRefusedOrPulledIntoRange(GameTestHelper h) {
        try {
            List<String> r = set(h, "{\"admin\":1,\"values\":{\"healthMultiplier\":50,\"maxInWorld\":\"lots\","
                    + "\"noSuchThing\":1,\"griefing\":3,\"move.grab.cooldown\":0.01}}");
            h.assertTrue(r.get(0).equals("ok"), "the rest is taken");
            h.assertTrue(r.size() == 4, "three refused: " + r);
            h.assertTrue(String.join(" ", r).contains("\"rejected\":\"noSuchThing\""), "the unknown one named");
            h.assertTrue(BellConfig.V.healthMultiplier == 10f, "50 is pulled to 10");
            h.assertTrue(GiantsAdmin.moveCooldown(Moves.GRAB) == 0.25f, "a wait no shorter than a quarter");
            h.assertTrue(GiantsBridge.giants(null, "adminset", "{\"admin\":2}") == null, "a newer protocol: null");
            h.assertTrue(GiantsBridge.giants(null, "adminset", "not json") == null, "nonsense: null");
        } finally {
            GiantsAdmin.clear();
        }
        h.succeed();
    }
}
