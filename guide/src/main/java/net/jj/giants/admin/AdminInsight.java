package net.jj.giants.admin;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * What the admin book's Home page works out from the settings in use (ADMIN_BRIDGE.md, "Book payloads"): how
 * dangerous the giants are now (1 to 5, with the reasons), settings that clash, the preset the world is on, and the
 * settings Simple mode shows. Pure: it only reads what it is given, so the tests can hand it any setup.
 */
public final class AdminInsight {
    private AdminInsight() {}

    // Simple mode

    /**
     * The settings Simple mode shows (about twenty, across the categories): every all-giants multiplier, and on each
     * giant's page the ones most worth changing. Everything else is under Advanced, and the search always finds it.
     */
    public static final List<String> SIMPLE = List.of(
            // the all-giants page: all seven
            "all.health", "all.damage", "all.speed", "all.size", "all.cooldowns", "all.healing", "all.windCost",
            // each giant
            "oneInTheWorld", "maxInWorld", "worldScale", "healthMultiplier", "damageMultiplier", "griefing",
            "fightGiants", "fleeInWildFights", "passiveHeal", "bookCosts", "freeHits", "bossBar", "chunkLoading");

    public static boolean simple(String id) {
        return SIMPLE.contains(id);
    }

    // the giants as the book sees them

    /**
     * One giant's settings in use, as numbers (on = 1, off = 0; a choice is its option's place; a size range is
     * left out), beside what his jar ships with.
     *
     * @param key   the guide's key for him
     * @param name  his plain name
     * @param now   the value in use now (the world's own, else the server default)
     * @param jar   what his mod ships with
     * @param names each setting's plain name (for the warnings)
     * @param uses  the all-giants multipliers his mod applies
     */
    public record View(String key, String name, Map<String, Double> now, Map<String, Double> jar,
            Map<String, String> names, List<String> uses, Map<String, Double> most, Map<String, Double> high) {
        public View(String key, String name, Map<String, Double> now, Map<String, Double> jar,
                Map<String, String> names, List<String> uses, Map<String, Double> most) {
            this(key, name, now, jar, names, uses, most, Map.of());
        }

        /** a range's high end (or the value, or 1) */
        public double hi(String id) {
            Double h = high.get(id);
            return h != null ? h : v(id, 1);
        }

        public boolean has(String id) {
            return now.containsKey(id);
        }

        public double v(String id, double or) {
            Double d = now.get(id);
            return d == null ? or : d;
        }

        public boolean on(String id) {
            return v(id, 0) >= 0.5;
        }

        /** now / jar: how far a setting is turned up from what he ships with (1 when unknown) */
        public double ratio(String id) {
            Double n = now.get(id), j = jar.get(id);
            if (n == null || j == null || j <= 0 || n < 0) return 1;
            return Math.max(n, 1e-3) / j;
        }

        public String nameOf(String id) {
            return names.getOrDefault(id, id);
        }

        /** an all-giants multiplier, if his mod applies it (else x1) */
        public double all(Map<String, Double> all, String id) {
            return uses.contains(id) ? all.getOrDefault(id, 1.0) : 1.0;
        }
    }

    /** a giant's report as the book sees it */
    public static View view(AdminJson.Report r) {
        Map<String, Double> now = new HashMap<>(), jar = new HashMap<>(), most = new HashMap<>(),
                high = new HashMap<>();
        Map<String, String> names = new HashMap<>();
        for (AdminSetting s : r.settings()) {
            if (s.value() != null && s.value().isJsonArray() && s.value().getAsJsonArray().size() == 2)
                try {
                    high.put(s.id(), s.value().getAsJsonArray().get(1).getAsDouble());
                } catch (RuntimeException e) {
                    // (left out)
                }
            Double n = number(s, s.value()), j = number(s, s.jar());
            if (n == null) continue;
            now.put(s.id(), n);
            if (j != null) jar.put(s.id(), j);
            most.put(s.id(), s.max());
            names.put(s.id(), s.name());
        }
        return new View(r.giant(), r.name(), now, jar, names, r.uses(), most, high);
    }

    /** a value as a number (on 1, off 0, a choice its place, a size range its middle) */
    static @Nullable Double number(AdminSetting s, @Nullable JsonElement v) {
        // (Part 4) a size range counts as its middle
        if (v != null && v.isJsonArray() && v.getAsJsonArray().size() == 2) {
            try {
                return (v.getAsJsonArray().get(0).getAsDouble() + v.getAsJsonArray().get(1).getAsDouble()) / 2;
            } catch (RuntimeException e) {
                return null;
            }
        }
        if (v == null || !v.isJsonPrimitive()) return null;
        JsonPrimitive p = v.getAsJsonPrimitive();
        try {
            if (p.isBoolean()) return p.getAsBoolean() ? 1.0 : 0.0;
            if (p.isNumber()) return p.getAsDouble();
            int i = s.options().indexOf(p.getAsString());
            return i < 0 ? null : (double) i;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // the danger rating

    public static final String[] LABELS = {"Gentle", "Easy", "Normal", "Hard", "Brutal"};

    /**
     * How dangerous the giants are with these settings.
     *
     * @param level  1 (gentle) to 5 (brutal); 3 is every giant as his jar ships him
     * @param label  the level's word
     * @param reason the biggest reasons, in plain words ("Damage x3, health x2.5 and griefing on.")
     * @param score  the number the level is rounded from (for the tests)
     */
    public record Danger(int level, String label, String reason, double score) {}

    /** one reason for the rating, and how far it moves it */
    private record Part(String words, double by) {}

    private static double log2(double x) {
        return Math.log(Math.max(x, 1e-6)) / Math.log(2);
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * The danger rating (ADMIN_BRIDGE.md, "Book payloads"): 3, plus
     * <ul>
     *   <li>fight strength: 1.25 x log2 of the giants' average (geometric mean) of damage x sqrt(health) x
     *       sqrt(speed) / sqrt(waits) x healing factor, each the value in use over his jar's, times the all-giants
     *       multipliers he applies (health = health multiplier x his base health; healing factor = 1 + 0.15 x (rate
     *       - 1), 0.85 to 1.6, rate 0 when he doesn't heal on his own);</li>
     *   <li>griefing: + 0.5 x (share of giants breaking blocks now - share in their jars);</li>
     *   <li>hunting: + 0.5 x log2 of the average grudge rate over the jar's (at most 0.5 either way), + 0.25 x (share
     *       that never back down now - in their jars);</li>
     *   <li>how many come on their own (Comes on his own): none at all -1, else 0.75 x log2(now / in their jars),
     *       down to -0.75.</li>
     * </ul>
     * Rounded and kept to 1..5.
     */
    public static Danger danger(List<View> giants, Map<String, Double> all) {
        List<View> gs = giants;
        if (gs.isEmpty())
            // no giant speaks the protocol: the all-giants multipliers alone, as if one giant applied them all
            gs = List.of(new View("", "", Map.of(), Map.of(), Map.of(),
                    AdminCatalog.shared().stream().map(AdminSetting::id).toList(), Map.of()));
        int n = gs.size();
        double lh = 0, ld = 0, ls = 0, lc = 0, lheal = 0, noHeal = 0;
        double grief = 0, grief0 = 0, griefN = 0, grudge = 0, grudgeN = 0, stubborn = 0, stubborn0 = 0, fleeN = 0;
        double count = 0, count0 = 0;
        // (Part 4, ADMIN_BRIDGE.md 19.7) temper, strength at night, roaming, size, how many
        double temper = 0, temperN = 0, lnight = 0, nightN = 0, roam = 0, roamN = 0, lsize = 0, sizeN = 0, many = 0,
                manyN = 0;
        double hunters = 0, gentle = 0;
        for (View g : gs) {
            if (g.has("temper")) {
                temperN++;
                // (options peaceful, normal, hunter: a choice counts as its place)
                double t = g.v("temper", 1), t0 = g.jar.getOrDefault("temper", 1.0);
                double by = (t < 0.5 ? -1.5 : t > 1.5 ? 0.5 : 0) - (t0 < 0.5 ? -1.5 : t0 > 1.5 ? 0.5 : 0);
                temper += by;
                if (t < 0.5) gentle++;
                if (t > 1.5) hunters++;
            }
            if (g.has("nightStrength")) {
                nightN++;
                lnight += log2(g.ratio("nightStrength"));
            }
            if (g.has("roam") || g.has("seekGiants") || g.has("raidVillages")) {
                roamN++;
                if (g.on("raidVillages")) roam += 0.5;
                if (g.on("seekGiants")) roam += 0.25;
                double rm = g.v("roam", 0);
                if (rm > 0.5 && rm < 1.5) roam += 0.25;
                if (rm > 1.5) roam -= 0.25;
            }
            if (g.has("worldScale")) {
                sizeN++;
                lsize += log2(g.ratio("worldScale") * g.v("spawnSize", 1) / g.jar.getOrDefault("spawnSize", 1.0)
                        * g.all(all, "all.size"));
            }
            if (g.has("worldCount")) {
                manyN++;
                many += g.v("worldCount", 1) / g.jar.getOrDefault("worldCount", 1.0);
            }
        }
        for (View g : gs) {
            String base = g.has("health") ? "health" : "headHealth";
            lh += log2(g.ratio("healthMultiplier") * g.ratio(base) * g.all(all, "all.health"));
            ld += log2(g.ratio("damageMultiplier") * g.all(all, "all.damage"));
            ls += log2(g.all(all, "all.speed"));
            lc += log2(g.all(all, "all.cooldowns"));
            double rate = g.has("passiveHeal") && !g.on("passiveHeal")
                    ? 0
                    : g.all(all, "all.healing") * g.ratio("healHomePerMinute");
            if (rate <= 0) noHeal++;
            lheal += log2(clamp(1 + 0.15 * (rate - 1), 0.85, 1.6));
            if (g.has("griefing")) {
                griefN++;
                grief += g.on("griefing") ? 1 : 0;
                grief0 += g.jar.getOrDefault("griefing", 0.0) >= 0.5 ? 1 : 0;
            }
            if (g.has("grudgeRate")) {
                grudgeN++;
                grudge += log2(g.ratio("grudgeRate"));
            }
            if (g.has("fleeInWildFights")) {
                fleeN++;
                stubborn += g.on("fleeInWildFights") ? 0 : 1;
                stubborn0 += g.jar.getOrDefault("fleeInWildFights", 1.0) >= 0.5 ? 0 : 1;
            }
            if (g.has("oneInTheWorld")) {
                count += g.on("oneInTheWorld") ? 1 : 0;
                count0 += g.jar.getOrDefault("oneInTheWorld", 1.0) >= 0.5 ? 1 : 0;
            }
        }
        lh /= n;
        ld /= n;
        ls /= n;
        lc /= n;
        lheal /= n;
        List<Part> parts = new ArrayList<>();
        parts.add(new Part("health x" + times(Math.pow(2, lh)), 1.25 * 0.5 * lh));
        parts.add(new Part("damage x" + times(Math.pow(2, ld)), 1.25 * ld));
        parts.add(new Part("speed x" + times(Math.pow(2, ls)), 1.25 * 0.5 * ls));
        parts.add(new Part((lc < 0 ? "shorter" : "longer") + " waits between moves (x" + times(Math.pow(2, lc)) + ")",
                -1.25 * 0.5 * lc));
        parts.add(new Part(noHeal >= n ? "no healing on their own" : lheal > 0 ? "faster healing" : "slower healing",
                1.25 * lheal));
        if (griefN > 0)
            parts.add(new Part(grief > grief0 ? "griefing on" : "griefing off", 0.5 * (grief - grief0) / griefN));
        double hunt = (grudgeN > 0 ? clamp(0.5 * grudge / grudgeN, -0.5, 0.5) : 0)
                + (fleeN > 0 ? 0.25 * (stubborn - stubborn0) / fleeN : 0);
        parts.add(new Part(hunt > 0 ? "longer grudges, fewer back down" : "shorter grudges", hunt));
        if (count0 > 0) {
            if (count <= 0) parts.add(new Part("none come on their own", -1));
            else parts.add(new Part("only " + AdminSetting.plain(count) + " of " + AdminSetting.plain(count0)
                    + " come on their own", clamp(0.75 * log2(count / count0), -0.75, 0)));
        }
        if (temperN > 0 && temper != 0)
            parts.add(new Part(temper > 0 ? (hunters >= temperN ? "always hunting" : "more hunters")
                    : gentle >= temperN ? "peaceful giants" : "fewer start fights", temper / temperN));
        if (nightN > 0 && lnight != 0)
            parts.add(new Part((lnight > 0 ? "stronger" : "weaker") + " at night (x"
                    + times(Math.pow(2, lnight / nightN)) + ")", 1.25 * 0.5 * lnight / nightN));
        if (roamN > 0 && roam != 0)
            parts.add(new Part(roam > 0 ? "giants that come looking" : "giants that stay put", roam / roamN));
        if (sizeN > 0 && lsize != 0)
            parts.add(new Part((lsize > 0 ? "bigger" : "smaller") + " giants (x" + times(Math.pow(2, lsize / sizeN))
                    + ")", clamp(0.5 * lsize / sizeN, -1, 0.75)));
        if (manyN > 0 && many / manyN > 1)
            parts.add(new Part(AdminSetting.plain(Math.round(many / manyN * 10) / 10.0) + " of each kind",
                    clamp(0.75 * log2(many / manyN), 0, 0.75)));
        double score = 3;
        for (Part p : parts) score += p.by();
        int level = (int) clamp(Math.floor(score + 0.5), 1, 5);
        parts.removeIf(p -> Math.abs(p.by()) < 0.15);
        parts.sort((a, b) -> Double.compare(Math.abs(b.by()), Math.abs(a.by())));
        // (the reasons that push the same way as the rating come first; the rest only when they are big)
        String reason;
        if (parts.isEmpty()) reason = "Every giant at his usual strength.";
        else {
            List<String> words = new ArrayList<>();
            for (Part p : parts.subList(0, Math.min(3, parts.size()))) words.add(p.words());
            reason = join(words) + ".";
            reason = Character.toUpperCase(reason.charAt(0)) + reason.substring(1);
        }
        return new Danger(level, LABELS[level - 1], reason, score);
    }

    /** "x2.5", rounded to what a reader cares about */
    private static String times(double x) {
        return AdminSetting.plain(x >= 10 ? Math.round(x) : Math.round(x * 100) / 100.0);
    }

    private static String join(List<String> words) {
        if (words.size() <= 1) return String.join("", words);
        return String.join(", ", words.subList(0, words.size() - 1)) + " and " + words.get(words.size() - 1);
    }

    // settings that clash

    /**
     * One clash.
     *
     * @param rule   its rule's id (see {@link #RULES})
     * @param giants the giants it is about ("all" for the all-giants settings)
     * @param text   what to tell the admin
     */
    public record Warning(String rule, List<String> giants, String text) {}

    /**
     * A rule: when it holds for a giant, and what it says ({who} = his name, or the names; {he}/{his}/{comes} turn
     * plural for several).
     */
    private record Rule(String id, Predicate<Ctx> when, Function<Ctx, String> says) {}

    private record Ctx(View g, Map<String, Double> all) {
        double v(String id) {
            return g.v(id, Double.NaN);
        }

        boolean has(String... ids) {
            for (String id : ids) if (!g.has(id)) return false;
            return true;
        }
    }

    /**
     * The clash rules (ADMIN_BRIDGE.md, "Book payloads"). Each is a pair of settings where one undoes the other, so a
     * change does nothing (or not what the admin meant).
     */
    private static final List<Rule> RULES = List.of(
            new Rule("grief_unloaded",
                    c -> c.has("griefing", "chunkLoading") && c.g.on("griefing") && !c.g.on("chunkLoading"),
                    c -> "{who}: " + c.g.nameOf("griefing") + " is on, but " + c.g.nameOf("chunkLoading")
                            + " is off: far from players {he} can freeze where the ground isn't loaded, with what {he}"
                            + " was breaking half done."),
            new Rule("heal_rates_zero",
                    c -> c.has("passiveHeal", "healHomePerMinute", "healAwayPerMinute") && c.g.on("passiveHeal")
                            && c.v("healHomePerMinute") == 0 && c.v("healAwayPerMinute") == 0,
                    c -> "{who}: " + c.g.nameOf("passiveHeal") + " is on, but both healing rates are 0, so {he} never"
                            + " {heals}."),
            new Rule("heal_rates_unused",
                    c -> c.has("passiveHeal") && !c.g.on("passiveHeal")
                            && (c.g.ratio("healHomePerMinute") != 1 || c.g.ratio("healAwayPerMinute") != 1),
                    c -> "{who}: the healing rates are changed, but " + c.g.nameOf("passiveHeal")
                            + " is off, so they do nothing."),
            new Rule("never_backs_down",
                    c -> c.has("fleeInWildFights", "yieldAt") && c.g.on("fleeInWildFights") && c.v("yieldAt") == 0,
                    c -> "{who}: " + c.g.nameOf("fleeInWildFights") + " is on, but " + c.g.nameOf("yieldAt")
                            + " is 0 (to the death), so {he} never {backs} down."),
            new Rule("ward_no_time",
                    c -> c.has("wardBlocks", "wardSeconds") && c.v("wardBlocks") > 0 && c.v("wardSeconds") == 0,
                    c -> "{who}: " + c.g.nameOf("wardBlocks") + " is " + AdminSetting.plain(c.v("wardBlocks"))
                            + ", but " + c.g.nameOf("wardSeconds") + " is 0, so the ward never works."),
            new Rule("free_orders",
                    c -> c.has("bookCosts") && c.g.on("bookCosts") && c.g.uses().contains("all.windCost")
                            && c.all.getOrDefault("all.windCost", 1.0) == 0,
                    c -> "{who}: " + c.g.nameOf("bookCosts") + " is on, but the all-giants Wind cost is x0, so"
                            + " orders cost nothing anyway."),
            new Rule("wind_unused",
                    c -> c.has("bookCosts", "windSeconds") && !c.g.on("bookCosts") && c.g.ratio("windSeconds") != 1,
                    c -> "{who}: " + c.g.nameOf("windSeconds") + " is changed, but " + c.g.nameOf("bookCosts")
                            + " is off, so it does nothing."),
            new Rule("size_capped",
                    c -> c.has("worldScale") && c.g.uses().contains("all.size")
                            && c.all.getOrDefault("all.size", 1.0) > 1
                            && c.v("worldScale") * c.all.getOrDefault("all.size", 1.0)
                                    > c.g.most().getOrDefault("worldScale", Double.MAX_VALUE) + 1e-9,
                    c -> "{who}: " + c.g.nameOf("worldScale") + " " + AdminSetting.plain(c.v("worldScale"))
                            + " x the all-giants Spawn size x" + AdminSetting.plain(c.all.get("all.size"))
                            + " is past {his} biggest (" + AdminSetting.plain(c.g.most().get("worldScale"))
                            + "), so {he} {comes} at that."),
            // Part 4 (ADMIN_BRIDGE.md 19.7)
            new Rule("count_capped",
                    c -> c.has("worldCount", "maxInWorld") && c.v("worldCount") > 1 && c.v("maxInWorld") > 0
                            && c.v("maxInWorld") < c.v("worldCount"),
                    c -> "{who}: " + c.g.nameOf("worldCount") + " is " + AdminSetting.plain(c.v("worldCount"))
                            + ", but " + c.g.nameOf("maxInWorld") + " is " + AdminSetting.plain(c.v("maxInWorld"))
                            + ", so only " + AdminSetting.plain(c.v("maxInWorld")) + " {comes}."),
            new Rule("count_alone",
                    c -> c.has("worldCount", "oneInTheWorld") && c.v("worldCount") > 1 && !c.g.on("oneInTheWorld"),
                    c -> "{who}: companions come beside the world's own one, but " + c.g.nameOf("oneInTheWorld")
                            + " is off, so none come."),
            new Rule("seek_no_fight",
                    c -> c.has("seekGiants", "fightGiants", "meetings") && c.g.on("seekGiants")
                            && (!c.g.on("fightGiants") || !c.g.on("meetings")),
                    c -> "{who}: " + c.g.nameOf("seekGiants") + " is on, but " + c.g.nameOf("fightGiants") + " or "
                            + c.g.nameOf("meetings") + " is off, so {he} {walks} to other giants and {leaves} them"
                            + " be."),
            new Rule("raid_peaceful",
                    c -> c.has("raidVillages", "temper") && c.g.on("raidVillages") && c.v("temper") < 0.5,
                    c -> "{who}: " + c.g.nameOf("raidVillages") + " is on, but " + c.g.nameOf("temper")
                            + " is peaceful, so {he} {walks} to villages and players and {leaves} them be."),
            new Rule("stay_roams",
                    c -> c.has("roam") && c.v("roam") > 1.5 && (c.g.on("seekGiants") || c.g.on("raidVillages")),
                    c -> "{who}: " + c.g.nameOf("roam") + " is stay, so " + c.g.nameOf("seekGiants") + " and "
                            + c.g.nameOf("raidVillages") + " do nothing."),
            new Rule("tiny_war",
                    c -> c.has("seekGiants", "worldScale") && c.g.on("seekGiants")
                            && c.v("worldScale") * c.g.hi("spawnSize") * (c.g.uses().contains("all.size")
                                    ? c.all.getOrDefault("all.size", 1.0) : 1) < 0.1,
                    c -> "{who}: " + c.g.nameOf("seekGiants") + " is on at a tiny size, so {he} mostly {gets}"
                            + " crushed."));

    /** the rules' ids, in the order they are checked */
    public static List<String> ruleIds() {
        return RULES.stream().map(Rule::id).toList();
    }

    /**
     * Every clash in this setup, one line per rule (the giants it holds for named together), plus the all-giants
     * healing multiplier when no giant heals on his own.
     */
    public static List<Warning> warnings(List<View> giants, Map<String, Double> all) {
        List<Warning> out = new ArrayList<>();
        for (Rule r : RULES) {
            Map<String, List<View>> byText = new LinkedHashMap<>();
            for (View g : giants) {
                Ctx c = new Ctx(g, all);
                boolean holds;
                try {
                    holds = r.when().test(c);
                } catch (RuntimeException e) {
                    holds = false;
                }
                if (holds) byText.computeIfAbsent(r.says().apply(c), k -> new ArrayList<>()).add(g);
            }
            // (the same words for several giants: one line naming them all)
            for (var e : byText.entrySet()) {
                List<String> keys = new ArrayList<>(), names = new ArrayList<>();
                for (View g : e.getValue()) {
                    keys.add(g.key());
                    names.add(g.name());
                }
                out.add(new Warning(r.id(), keys, words(e.getKey(), names)));
            }
        }
        // the all-giants healing multiplier, with nobody to heal
        double heal = all.getOrDefault("all.healing", 1.0);
        List<View> healers = giants.stream().filter(g -> g.uses().contains("all.healing") && g.has("passiveHeal"))
                .toList();
        if (heal != 1 && !healers.isEmpty() && healers.stream().noneMatch(g -> g.on("passiveHeal")))
            out.add(new Warning("healing_unused", List.of(AdminCatalog.ALL),
                    "All giants: Healing is x" + AdminSetting.plain(heal) + ", but no giant heals on his own (Heals on"
                            + " his own is off for every one), so it changes nothing."));
        return out;
    }

    /** a rule's words for one giant or several */
    private static String words(String text, List<String> names) {
        boolean many = names.size() > 1;
        return text.replace("{who}", join(names))
                .replace("{he}", many ? "they" : "he")
                .replace("{his}", many ? "their" : "his")
                .replace("{comes}", many ? "come" : "comes")
                .replace("{heals}", many ? "heal" : "heals")
                .replace("{backs}", many ? "back" : "backs")
                .replace("{walks}", many ? "walk" : "walks")
                .replace("{leaves}", many ? "leave" : "leaves")
                .replace("{gets}", many ? "get" : "gets");
    }

    // the preset

    /**
     * The preset the world is on, from its change log: nothing set is Classic; else the last preset applied (or
     * "Imported settings"), with ", changed" when something was changed after it; else "Custom". Changes to the server
     * default (all worlds) don't count: they aren't the world's.
     */
    public static String presetName(List<AdminWorld.Change> log, boolean anySet) {
        if (!anySet) return "Classic";
        boolean after = false;
        for (int i = log.size() - 1; i >= 0; i--) {
            AdminWorld.Change c = log.get(i);
            if (c.undo().isEmpty()) continue;
            // (Part 3) the world's settings from the Create World screen or new_world.txt: the preset chosen there
            if (c.id().equals(AdminCreation.LOG_ID)) {
                String name = c.old().isEmpty() ? "Custom" : c.old();
                return after && !name.endsWith(", changed") ? name + ", changed" : name;
            }

            if (c.id().startsWith("preset:") || c.id().equals("import") || c.id().equals("snapshot")) {
                String name = c.id().equals("import") ? "Imported settings"
                        : c.id().equals("snapshot") ? "Restored snapshot"
                        : c.name().toLowerCase(Locale.ROOT).startsWith("preset ") ? c.name().substring(7) : c.name();
                return after ? name + ", changed" : name;
            }
            after = true;
        }
        return "Custom";
    }

    /** the ids Simple mode shows that a page has (for the "more in Advanced" count) */
    public static int hiddenInSimple(List<AdminSetting> settings) {
        int n = 0;
        for (AdminSetting s : settings) if (!simple(s.id())) n++;
        return n;
    }

    /** (for the tests) a giant view from plain maps */
    public static View view(String key, String name, Map<String, Double> now, Map<String, Double> jar,
            Set<String> uses) {
        Map<String, String> names = new HashMap<>();
        for (String id : now.keySet()) names.put(id, id);
        return new View(key, name, now, jar, names, List.copyOf(uses), Map.of("worldScale", 2.0));
    }
}
