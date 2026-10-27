package net.jj.giants.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The five built-in presets. Highest is the default and is the giants exactly as they are made: every setting at
 * its default. The rest turn things down, and Low and Very Low start taking things away.
 */
public final class PerfPresets {
    private PerfPresets() {}

    public static final List<String> BUILT_IN = List.of("Highest", "High", "Medium", "Low", "Very Low");
    public static final String CUSTOM = "Custom";
    public static final int HIGHEST = 0;

    /** the index of a built-in preset by name (any case), or -1 */
    public static int builtIn(String name) {
        for (int i = 0; i < BUILT_IN.size(); i++) if (BUILT_IN.get(i).equalsIgnoreCase(name.strip())) return i;
        return -1;
    }

    /** every setting's value in built-in preset p */
    public static Map<String, Double> values(int p, List<PerfSetting> settings) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (PerfSetting s : settings) out.put(s.id(), s.level(p));
        return out;
    }

    /**
     * One thing a preset does differently from Highest.
     *
     * @param removes it takes the thing away altogether (a toggle off, a slider at nothing)
     */
    public record Change(PerfSetting setting, double from, double to, boolean removes) {}

    /** what these values turn down or take away compared with Highest, in the settings' order */
    public static List<Change> changes(Map<String, Double> values, List<PerfSetting> settings) {
        List<Change> out = new ArrayList<>();
        for (PerfSetting s : settings) {
            double v = s.clamp(values.getOrDefault(s.id(), s.def()));
            if (Math.abs(v - s.def()) < 1e-9) continue;
            out.add(new Change(s, s.def(), v, s.removes(v)));
        }
        return out;
    }

    /** the changes a built-in preset makes */
    public static List<Change> changes(int p, List<PerfSetting> settings) {
        return changes(values(p, settings), settings);
    }
}
