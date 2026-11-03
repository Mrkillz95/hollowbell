package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminDraft;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Part 4 (ADMIN_BRIDGE.md §19): the re-tune presets. Each one reads, is listed in its group, and applied over eight
 * giants that speak Part 4's settings (a stand-in list for each, with each giant's own jar values) it takes every
 * value it names, previews, rates as JJ's plan says (Titans and Hardcore 5, Gentle 1-2, Builder 1, Classic 3) and
 * leaves no clash. Also the "keep" field, the new danger parts and the new clash rules both ways. (-Pgiants does the
 * same with the real giants: GiantsPresetGameTests.)
 */
public class PresetGameTests implements FabricGameTest {

    /** the presets and the danger rating each must have over eight giants as shipped */
    static final Map<String, Integer> RATED = new LinkedHashMap<>();

    static {
        RATED.put("classic", 3);
        RATED.put("my_giant", 3);
        RATED.put("pocket_giants", 2);
        RATED.put("titans", 5);
        RATED.put("gentle_giants", 1);
        RATED.put("kaiju_war", 3);
        RATED.put("night_terrors", 4);
        RATED.put("wild_sizes", 3);
        RATED.put("siege", 4);
        RATED.put("one_life", 3);
        RATED.put("boss_rush", 3);
        RATED.put("nomads", 3);
        RATED.put("hardcore", 5);
        RATED.put("builder_showcase", 1);
        RATED.put("public_server_safe", 2);
        RATED.put("lite", 3);
        RATED.put("speedrun", 2);
        RATED.put("close_quarters", 3);
        RATED.put("far_frontier", 3);
    }

    // a stand-in for a giant that speaks Part 4 (the rows the presets and the rating read, as each mod ships them)

    private static String row(String id, String cat, String type, double min, double max, String jar, String when) {
        String range = type.equals("bool") ? "" : ",\"min\":" + min + ",\"max\":" + max + ",\"step\":0.01";
        String opts = switch (id) {
            case "temper" -> ",\"options\":[\"peaceful\",\"normal\",\"hunter\"]";
            case "roam" -> ",\"options\":[\"home\",\"wander\",\"stay\"]";
            case "sleepSchedule" -> ",\"options\":[\"usual\",\"day\",\"never\"]";
            default -> "";
        };
        if (type.equals("choice")) range = "";
        return "{\"id\":\"" + id + "\",\"cat\":\"" + cat + "\",\"type\":\"" + type + "\"" + range + opts
                + ",\"default\":" + jar + ",\"value\":" + jar + ",\"jar\":" + jar + ",\"set\":false,\"when\":\""
                + when + "\",\"name\":\"" + id + "\",\"what\":\"" + id + "\"}";
    }

    static AdminJson.Report standIn(String key) {
        List<String> l = new ArrayList<>();
        l.add("{\"admin\":1,\"giant\":\"" + key + "\",\"name\":\"" + key + "\",\"uses\":[\"all.health\",\"all.damage\","
                + "\"all.speed\",\"all.size\",\"all.cooldowns\",\"all.healing\",\"all.windCost\"],\"all\":{}}");
        l.add(row("oneInTheWorld", "spawning", "bool", 0, 1, "true", "live"));
        l.add(row("maxInWorld", "spawning", "int", 0, 20, "1", "newSpawns"));
        l.add(row("worldScale", "spawning", "float", 0.01, 2, "1", "newSpawns"));
        l.add(row("worldRespawnDays", "spawning", "int", 0, 1000, "3", "newSpawns"));
        l.add(row("ownedRespawnDays", "spawning", "int", 0, 1000, "15", "newSpawns"));
        l.add(row("spawnSize", "spawning", "range", 0.01, 4, "[1,1]", "newSpawns"));
        l.add(row("worldCount", "spawning", "int", 1, 8, "1", "newSpawns"));
        l.add(row("respawn", "spawning", "bool", 0, 1, "true", "live"));
        l.add(row("groundNearest", "worldgen", "int", 0, 100000, "2100", "worldCreation"));
        l.add(row("groundFarthest", "worldgen", "int", 0, 100000, "14100", "worldCreation"));
        l.add(row("groundGap", "worldgen", "int", 0, 20000, "0", "newLand"));
        l.add(row("homeRadius", "worldgen", "int", 200, 2000, "900", "newLand"));
        l.add(row(key.equals("cerberus") ? "headHealth" : "health", "stats", "float", 10, 1000000,
                key.equals("cerberus") ? "2000" : "6000", "live"));
        l.add(row("healthMultiplier", "stats", "float", 0.1, 10, "1", "live"));
        l.add(row("damageMultiplier", "stats", "float", 0.1, 10, "1", "live"));
        l.add(row("nightStrength", "stats", "float", 0.5, 4, "1", "live"));
        l.add(row("fightGiants", "behaviour", "bool", 0, 1, "true", "live"));
        l.add(row("meetings", "behaviour", "bool", 0, 1, "true", "live"));
        l.add(row("meetCooldown", "behaviour", "int", 0, 1440, "10", "live"));
        l.add(row("yieldAt", "behaviour", "float", 0, 0.9, "0.25", "live"));
        l.add(row("fleeInWildFights", "behaviour", "bool", 0, 1, "true", "live"));
        l.add(row("grudgeRate", "behaviour", "float", 0, 5, "1", "live"));
        l.add(row("griefing", "behaviour", "bool", 0, 1, key.equals("swarmforge") ? "false" : "true", "live"));
        l.add(row("temper", "behaviour", "choice", 0, 2, "\"normal\"", "live"));
        l.add(row("roam", "behaviour", "choice", 0, 2, "\"home\"", "live"));
        l.add(row("seekGiants", "behaviour", "bool", 0, 1, "false", "live"));
        l.add(row("raidVillages", "behaviour", "bool", 0, 1, "false", "live"));
        l.add(row("sleepSchedule", "behaviour", "choice", 0, 2, "\"usual\"", "live"));
        l.add(row("heavyDelaySeconds", "moves", "int", 0, 600, "60", "live"));
        l.add(row("passiveHeal", "healing", "bool", 0, 1, "true", "live"));
        l.add(row("healHomePerMinute", "healing", "float", 0, 100, "2", "live"));
        l.add(row("healAwayPerMinute", "healing", "float", 0, 100, "1", "live"));
        l.add(row("bookCosts", "taming", "bool", 0, 1, "true", "live"));
        l.add(row("windSeconds", "taming", "int", 10, 3600, "90", "live"));
        l.add(row("freeHits", "taming", "int", 0, 100, "5", "live"));
        l.add(row("wardBlocks", "taming", "int", 0, 10000, "700", "live"));
        l.add(row("wardSeconds", "taming", "int", 0, 36000, "1200", "live"));
        l.add(row("lootMultiplier", "loot", "float", 0, 10, "1", "live"));
        l.add(row("bossBar", "effects", "bool", 0, 1, "true", "live"));
        l.add(row("ambientSounds", "effects", "bool", 0, 1, "true", "live"));
        l.add(row("chunkLoading", "load", "bool", 0, 1, "true", "live"));
        if (key.equals("gloamwick")) l.add(row("eclipseOften", "moves", "bool", 0, 1, "false", "live"));
        if (key.equals("swarmforge")) {
            l.add(row("robotLimit", "load", "int", 0, 1000, "240", "live"));
            l.add(row("armyPoints", "load", "int", 0, 2000, "300", "live"));
        }
        return AdminJson.read(key, key, l);
    }

    /** a draft over the eight stand-ins (no server, nothing installed needed) */
    static AdminDraft eight() {
        return new AdminDraft(g -> standIn(g.key()));
    }

    private static AdminInsight.Danger rating(AdminDraft d) {
        AdminTransfer.Plan none = AdminTransfer.plan(d, new AdminTransfer.Bundle("x", "", "", false, Map.of()));
        return AdminTransfer.dangerBeforeAfter(d, none)[0];
    }

    private static List<AdminInsight.Warning> clashes(AdminDraft d) {
        List<AdminInsight.View> vs = new ArrayList<>();
        for (Giant g : AdminServer.giants()) vs.add(AdminInsight.view(d.report(g)));
        Map<String, Double> all = new HashMap<>();
        for (AdminSetting s : AdminServer.shared(d.world()))
            if (!AdminCatalog.player(s)) all.put(s.id(), s.value().getAsDouble());
        return AdminInsight.warnings(vs, all);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_list")
    public void everyPresetIsThereInItsGroup(GameTestHelper h) {
        AdminServer.useGiants(net.jj.giants.Giants.ALL);
        List<String> ids = new ArrayList<>(AdminTransfer.presets());
        h.assertTrue(ids.equals(new ArrayList<>(RATED.keySet())), "the presets and their order: " + ids);
        h.assertTrue(AdminTransfer.SUGGESTED.equals(List.of("my_giant", "pocket_giants", "titans", "gentle_giants", "kaiju_war",
                "night_terrors", "wild_sizes")), "the suggested group");
        for (String id : ids) {
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            h.assertTrue(b != null && !b.name().isEmpty() && !b.about().isEmpty(), id + " reads");
            h.assertTrue(b.about().length() <= 240, id + "'s about is short enough: " + b.about().length());
            String group = AdminTransfer.group(id);
            h.assertTrue(id.equals("classic") ? group.equals("default")
                    : AdminTransfer.SUGGESTED.contains(id) ? group.equals("suggested") : group.equals("all"),
                    id + " group " + group);
            boolean retune = !List.of("classic", "close_quarters", "far_frontier").contains(id);
            h.assertTrue(!retune || b.replace() && b.keep().equals(List.of("worldgen", "players", "server")),
                    id + ": a re-tune preset replaces and keeps the grounds");
            // (by name too, as the command takes it)
            h.assertTrue(AdminTransfer.builtInId(b.name()) != null || id.equals("builder_showcase"), id + " by name");
        }
        // the Presets page and the picker read the groups
        JsonObject page = AdminBook.presetsJson(eight(), "", "Classic");
        int suggested = 0;
        for (JsonElement e : page.getAsJsonArray("builtin"))
            if (e.getAsJsonObject().get("group").getAsString().equals("suggested")) suggested++;
        AdminServer.useGiants(null);
        h.assertTrue(suggested == 7, "seven suggested on the page (My Giant too): " + suggested);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_apply", timeoutTicks = 200)
    public void eachPresetAppliesRatesAndHasNoClash(GameTestHelper h) {
        AdminServer.useGiants(net.jj.giants.Giants.ALL);
        StringBuilder bad = new StringBuilder();
        JsonObject page = AdminBook.presetsJson(eight(), "", "Classic");
        Map<String, Integer> listed = new HashMap<>();
        for (JsonElement e : page.getAsJsonArray("builtin"))
            listed.put(e.getAsJsonObject().get("id").getAsString(), e.getAsJsonObject().get("level").getAsInt());
        for (var e : RATED.entrySet()) {
            String id = e.getKey();
            AdminDraft d = eight();
            AdminTransfer.Plan p = d.plan(AdminTransfer.preset(id), "preset");
            if (!p.skipped().isEmpty()) bad.append(id).append(" left out ").append(p.skipped()).append("; ");
            if (!id.equals("classic") && p.changes().isEmpty()) bad.append(id).append(" changes nothing; ");
            if (p.lines().isEmpty()) bad.append(id).append(" has no preview; ");
            AdminInsight.Danger before = AdminTransfer.dangerBeforeAfter(d, p)[0],
                    after = AdminTransfer.dangerBeforeAfter(d, p)[1];
            if (before.level() != 3) bad.append(id).append(" before ").append(before.level()).append("; ");
            d.apply();
            // every value it names is now the draft's
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            for (var g : b.values().entrySet())
                for (var v : g.getValue().entrySet()) {
                    JsonElement now = d.world().values(g.getKey()).get(v.getKey());
                    // (Part 5: a player setting named at its default stays the default)
                    if (now == null && AdminCatalog.player(v.getKey())
                            && AdminSetting.same(v.getValue(), AdminCatalog.shared(v.getKey()).def())) continue;
                    if (now == null || !AdminSetting.same(now, v.getValue()))
                        bad.append(id).append(" ").append(g.getKey()).append(" ").append(v.getKey())
                                .append(" not taken; ");
                }
            AdminInsight.Danger r = rating(d);
            if (r.level() != e.getValue())
                bad.append(id).append(" rates ").append(r.level()).append(" (").append(r.score()).append(": ")
                        .append(r.reason()).append(") not ").append(e.getValue()).append("; ");
            if (after.level() != r.level()) bad.append(id).append(" preview says ").append(after.level()).append("; ");
            if (listed.getOrDefault(id, -1) != r.level())
                bad.append(id).append(" listed at ").append(listed.get(id)).append("; ");
            List<AdminInsight.Warning> w = clashes(d);
            if (!w.isEmpty()) bad.append(id).append(" clashes: ").append(w.get(0).text()).append("; ");
        }
        AdminServer.useGiants(null);
        h.assertTrue(bad.length() == 0, bad.toString());
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_keep")
    public void aRetuneReplacesEverythingButWhereTheGroundsGo(GameTestHelper h) {
        AdminServer.useGiants(net.jj.giants.Giants.ALL);
        AdminDraft d = eight();
        d.pick("far_frontier");
        d.plan(AdminTransfer.preset("gentle_giants"), "preset");
        d.apply();
        d.plan(AdminTransfer.preset("titans"), "preset");
        d.apply();
        Map<String, JsonElement> w = d.world().values("wreckback");
        h.assertTrue(w.get("groundNearest") != null && w.get("groundNearest").getAsInt() == 5000,
                "Far Frontier's ground stayed: " + w);
        h.assertTrue(w.get("temper") != null && w.get("temper").getAsString().equals("hunter"), "Titans' temper");
        h.assertTrue(w.get("grudgeRate") == null && w.get("freeHits") == null, "Gentle Giants' values went: " + w);
        // Classic still puts everything back, the grounds too
        d.plan(AdminTransfer.preset("classic"), "preset");
        d.apply();
        h.assertTrue(d.world().giantsWithValues().isEmpty(), "Classic left " + d.world().giantsWithValues());
        // the field survives writing and reading
        AdminTransfer.Bundle b = AdminTransfer.read(AdminTransfer.write(AdminTransfer.preset("nomads")));
        AdminServer.useGiants(null);
        h.assertTrue(b.keep().equals(List.of("worldgen", "players", "server")) && b.replace(), "keep written and read");
        h.succeed();
    }

    // the rating's new parts and the new clash rules, each way

    private static AdminInsight.View g(Map<String, Double> now) {
        Map<String, Double> jar = new HashMap<>(Map.of("temper", 1.0, "nightStrength", 1.0, "roam", 0.0,
                "seekGiants", 0.0, "raidVillages", 0.0, "worldScale", 1.0, "spawnSize", 1.0, "worldCount", 1.0,
                "maxInWorld", 1.0, "oneInTheWorld", 1.0));
        Map<String, Double> n = new HashMap<>(jar);
        n.put("fightGiants", 1.0);
        n.put("meetings", 1.0);
        n.putAll(now);
        return AdminInsight.view("g", "Giant", n, jar, java.util.Set.of("all.size"));
    }

    private static double score(Map<String, Double> now) {
        return AdminInsight.danger(List.of(g(now)), Map.of()).score();
    }

    private static List<String> rules(Map<String, Double> now) {
        return AdminInsight.warnings(List.of(g(now)), Map.of()).stream().map(AdminInsight.Warning::rule).toList();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_rating")
    public void theNewPartsMoveTheRating(GameTestHelper h) {
        double base = score(Map.of());
        h.assertTrue(Math.abs(base - 3) < 1e-9, "as shipped 3: " + base);
        h.assertTrue(Math.abs(score(Map.of("temper", 0.0)) - 1.5) < 1e-9, "peaceful -1.5");
        h.assertTrue(Math.abs(score(Map.of("temper", 2.0)) - 3.5) < 1e-9, "hunter +0.5");
        h.assertTrue(Math.abs(score(Map.of("nightStrength", 2.0)) - 3.625) < 1e-9, "x2 at night +0.625");
        h.assertTrue(Math.abs(score(Map.of("raidVillages", 1.0)) - 3.5) < 1e-9, "raids +0.5");
        h.assertTrue(Math.abs(score(Map.of("seekGiants", 1.0)) - 3.25) < 1e-9, "seeking +0.25");
        h.assertTrue(Math.abs(score(Map.of("roam", 1.0)) - 3.25) < 1e-9, "wandering +0.25");
        h.assertTrue(Math.abs(score(Map.of("roam", 2.0)) - 2.75) < 1e-9, "staying -0.25");
        h.assertTrue(Math.abs(score(Map.of("worldScale", 0.25)) - 2) < 1e-9, "a quarter size -1");
        h.assertTrue(Math.abs(score(Map.of("worldScale", 0.01)) - 2) < 1e-9, "no lower than -1");
        h.assertTrue(Math.abs(score(Map.of("spawnSize", 2.0)) - 3.5) < 1e-9, "the spread's middle x2 +0.5");
        h.assertTrue(Math.abs(score(Map.of("worldCount", 2.0, "maxInWorld", 2.0)) - 3.75) < 1e-9, "two each +0.75");
        h.assertTrue(Math.abs(score(Map.of("worldCount", 8.0, "maxInWorld", 8.0)) - 3.75) < 1e-9, "at most +0.75");
        String why = AdminInsight.danger(List.of(g(Map.of("temper", 2.0, "nightStrength", 2.0))), Map.of()).reason();
        h.assertTrue(why.toLowerCase().contains("stronger at night (x2)") && why.contains("always hunting"), why);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_clash")
    public void theNewClashRulesHoldEachWay(GameTestHelper h) {
        h.assertTrue(rules(Map.of()).isEmpty(), "as shipped no clash: " + rules(Map.of()));
        h.assertTrue(rules(Map.of("worldCount", 4.0, "maxInWorld", 2.0)).contains("count_capped"), "capped");
        h.assertFalse(rules(Map.of("worldCount", 4.0, "maxInWorld", 0.0)).contains("count_capped"), "0 = no cap");
        h.assertFalse(rules(Map.of("worldCount", 4.0, "maxInWorld", 6.0)).contains("count_capped"), "room enough");
        h.assertTrue(rules(Map.of("worldCount", 3.0, "maxInWorld", 3.0, "oneInTheWorld", 0.0)).contains("count_alone"),
                "alone");
        h.assertFalse(rules(Map.of("worldCount", 3.0, "maxInWorld", 3.0)).contains("count_alone"), "not alone");
        h.assertTrue(rules(Map.of("seekGiants", 1.0, "meetings", 0.0)).contains("seek_no_fight"), "no meetings");
        h.assertFalse(rules(Map.of("seekGiants", 1.0)).contains("seek_no_fight"), "seeking and fighting");
        h.assertTrue(rules(Map.of("raidVillages", 1.0, "temper", 0.0)).contains("raid_peaceful"), "peaceful raids");
        h.assertFalse(rules(Map.of("raidVillages", 1.0, "temper", 2.0)).contains("raid_peaceful"), "hunter raids");
        h.assertTrue(rules(Map.of("roam", 2.0, "seekGiants", 1.0)).contains("stay_roams"), "staying seeker");
        h.assertFalse(rules(Map.of("roam", 1.0, "seekGiants", 1.0)).contains("stay_roams"), "wandering seeker");
        h.assertTrue(rules(Map.of("seekGiants", 1.0, "worldScale", 0.05)).contains("tiny_war"), "tiny seekers");
        h.assertFalse(rules(Map.of("seekGiants", 1.0, "worldScale", 0.5)).contains("tiny_war"), "big seekers");
        h.succeed();
    }
}
