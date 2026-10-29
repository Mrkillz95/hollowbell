package net.jj.giants.perf;

import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.perf.PerfSetting.Cost;
import net.jj.giants.perf.PerfSetting.Kind;

/**
 * The guide's own settings: the ones every giant shares (Effects, Detail and the world's Features). Their words are
 * in the guide's language file under perf.jj_giants.s.ID.name, .what and .loses. Each giant's mod reads the ones it
 * has use for (it says which in its "uses" list); the five numbers after each are its value in Highest, High,
 * Medium, Low and Very Low. Highest is always the default, and the default always changes nothing.
 */
public final class PerfCatalog {
    private PerfCatalog() {}

    /** the per-giant render distance's id, before ".giant" */
    public static final String RENDER_DISTANCE = "detail.renderDistance";

    private static final List<PerfSetting> COMMON = List.of(
            // Effects: what each player sees, for themselves
            pct("fx.particles", PerfSetting.EFFECTS, Cost.HIGH, 1, .75, .5, .3, .15),
            pct("fx.bigImpacts", PerfSetting.EFFECTS, Cost.HIGH, 1, .8, .6, .35, 0),
            pct("fx.smokeTrails", PerfSetting.EFFECTS, Cost.MED, 1, 1, .6, .3, 0),
            pct("fx.flashes", PerfSetting.EFFECTS, Cost.MED, 1, 1, .75, .5, .25),
            pct("fx.screenShake", PerfSetting.EFFECTS, Cost.LOW, 1, 1, 1, .75, .5),
            pct("fx.screenEffects", PerfSetting.EFFECTS, Cost.LOW, 1, 1, 1, .75, .5),
            pct("fx.ambient", PerfSetting.EFFECTS, Cost.MED, 1, .75, .5, .25, 0),
            toggle("fx.fog", PerfSetting.EFFECTS, Cost.LOW, false, 1, 1, 1, 1, 0),
            // Detail: how finely, how far and how loud, for each player
            slider("detail.model", PerfSetting.DETAIL, Cost.HIGH, .25, 1, .05, "%", false, 1, .85, .65, .45, .3),
            choice("detail.animation", PerfSetting.DETAIL, Cost.MED, false,
                    List.of("Full", "Simpler far off", "Simpler"), 0, 0, 1, 1, 2),
            toggle("detail.farSight", PerfSetting.DETAIL, Cost.MED, false, 1, 1, 1, 1, 0),
            slider("detail.farSightDistance", PerfSetting.DETAIL, Cost.MED, .25, 1, .05, "%", false, 1, 1, .75, .5,
                    .25),
            choice("detail.lootBeams", PerfSetting.DETAIL, Cost.LOW, false, List.of("All", "Near only", "Off"), 0, 0,
                    0, 1, 2),
            // the giants' newer, faster way of drawing: Fast to start with (JJ, 1.6.7), Classic (the old drawing) for
            // anyone who sees a problem; no preset changes it, each player picks it
            presetFree(choice("detail.drawing", PerfSetting.DETAIL, Cost.HIGH, false, List.of("Classic", "Fast"), 1,
                    1, 1, 1, 1)),
            pct("detail.sounds", PerfSetting.DETAIL, Cost.LOW, 1, 1, 1, 1, 1),
            pct("detail.music", PerfSetting.DETAIL, Cost.LOW, 1, 1, 1, 1, 1),
            // Features: the whole world's, operators only
            toggle("feature.meetings", PerfSetting.FEATURES, Cost.HIGH, true, 1, 1, 1, 1, 0),
            slider("feature.summons", PerfSetting.FEATURES, Cost.HIGH, 0, 1, .05, "%", true, 1, 1, 1, .6, .3),
            slider("feature.keepAwake", PerfSetting.FEATURES, Cost.HIGH, 0, 1, .05, "%", true, 1, 1, 1, .6, .3),
            choice("feature.footPrecision", PerfSetting.FEATURES, Cost.MED, true,
                    List.of("Every tick", "Every 2nd tick", "Every 4th tick"), 0, 0, 0, 1, 2),
            choice("feature.hitSampling", PerfSetting.FEATURES, Cost.HIGH, true,
                    List.of("Full", "Half", "Quarter"), 0, 0, 0, 1, 2),
            slider("feature.landmarks", PerfSetting.FEATURES, Cost.MED, 0, 1, .05, "%", true, 1, 1, 1, .6, .3));

    /** the guide's own settings that aren't one per giant */
    public static List<PerfSetting> common() {
        return COMMON;
    }

    /** the guide's own settings with one copy each for these giants (their render distance) */
    public static List<PerfSetting> forGiants(List<Giant> giants) {
        List<PerfSetting> out = new ArrayList<>(COMMON);
        for (Giant g : giants) out.add(renderDistance(g.key()));
        return out;
    }

    /** one giant's render distance: how far off he is still drawn, times his own setting */
    public static PerfSetting renderDistance(String giant) {
        double[] lv = {1, 1, .85, .65, .45};
        return new PerfSetting(
                RENDER_DISTANCE + "." + giant,
                PerfSetting.DETAIL,
                Kind.SLIDER,
                .25,
                1,
                .05,
                1,
                lv,
                Cost.HIGH,
                false,
                key(RENDER_DISTANCE, "name"),
                key(RENDER_DISTANCE, "what"),
                key(RENDER_DISTANCE, "loses"),
                List.of(),
                "%",
                giant,
                true,
                false);
    }

    /** the language key for one of a guide setting's lines */
    public static String key(String id, String line) {
        return "perf.jj_giants.s." + id + "." + line;
    }

    private static PerfSetting pct(String id, String section, Cost cost, double... lv) {
        return slider(id, section, cost, 0, 1, .05, "%", false, lv);
    }

    private static PerfSetting slider(
            String id, String section, Cost cost, double min, double max, double step, String unit, boolean server,
            double... lv) {
        return make(id, section, Kind.SLIDER, min, max, step, cost, server, List.of(), unit, lv);
    }

    private static PerfSetting toggle(String id, String section, Cost cost, boolean server, double... lv) {
        return make(id, section, Kind.TOGGLE, 0, 1, 1, cost, server, List.of(), "", lv);
    }

    private static PerfSetting choice(
            String id, String section, Cost cost, boolean server, List<String> options, double... lv) {
        return make(id, section, Kind.CHOICE, 0, options.size() - 1, 1, cost, server, options, "", lv);
    }

    private static PerfSetting make(
            String id, String section, Kind kind, double min, double max, double step, Cost cost, boolean server,
            List<String> options, String unit, double[] lv) {
        return new PerfSetting(
                id, section, kind, min, max, step, lv[0], lv, cost, server, key(id, "name"), key(id, "what"),
                key(id, "loses"), options, unit, "", false, false);
    }

    /** the same setting, but no preset changes it (it stays as each player set it) */
    private static PerfSetting presetFree(PerfSetting s) {
        return new PerfSetting(
                s.id(), s.section(), s.kind(), s.min(), s.max(), s.step(), s.def(), s.levels(), s.cost(), s.server(),
                s.name(), s.what(), s.loses(), s.options(), s.unit(), s.owner(), s.perGiant(), true);
    }
}
