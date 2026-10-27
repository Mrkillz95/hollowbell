package net.jj.giants.perf;

import java.util.List;
import java.util.Locale;

/**
 * One setting on the Giants Performance screen: one of the guide's own (shared by every giant) or one a giant's
 * mod reported over the bridge (see PERFORMANCE_BRIDGE.md). Values are kept as numbers: a toggle is 1 (on) or 0
 * (off), a slider is its number, a choice is the index of its option (0 = the first, the best looking).
 *
 * @param id      its name, as the giants read it ("fx.particles", "gloamwick.hush")
 * @param section {@link #EFFECTS}, {@link #DETAIL}, {@link #GIANT} (the owner's own section) or {@link #FEATURES}
 * @param levels  its value in each built-in preset, Highest first; Highest is always the default
 * @param server  set for the whole world by an operator (else each player sets it for themselves)
 * @param name    a language key of the guide's, or plain words from a giant's mod
 * @param owner   the giant it belongs to ("" for the guide's own)
 * @param perGiant one copy of it per installed giant ("detail.renderDistance.gloamwick"); each giant is sent its own
 *                 copy under the plain id ("detail.renderDistance")
 */
public record PerfSetting(
        String id,
        String section,
        Kind kind,
        double min,
        double max,
        double step,
        double def,
        double[] levels,
        Cost cost,
        boolean server,
        String name,
        String what,
        String loses,
        List<String> options,
        String unit,
        String owner,
        boolean perGiant) {
    public static final String EFFECTS = "effects", DETAIL = "detail", GIANT = "giant", FEATURES = "features";

    public enum Kind {
        TOGGLE,
        SLIDER,
        CHOICE;

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Kind of(String s) {
            return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
                case "toggle" -> TOGGLE;
                case "slider" -> SLIDER;
                case "choice" -> CHOICE;
                default -> null;
            };
        }
    }

    public enum Cost {
        LOW,
        MED,
        HIGH;

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Cost of(String s) {
            return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
                case "high" -> HIGH;
                case "med", "medium" -> MED;
                default -> LOW;
            };
        }
    }

    /** operators only: everything set for the whole world is */
    public boolean ops() {
        return server;
    }

    /**
     * Whether a player may move it on the screen: their own settings always; the world's only when the server has
     * the guide and says they may (an operator, or the owner of a single-player world).
     */
    public boolean editable(boolean serverHasGuide, boolean allowed) {
        return !server || (serverHasGuide && allowed);
    }

    /** its value in built-in preset p (0 = Highest), always inside its range */
    public double level(int p) {
        if (p <= 0 || levels == null || levels.length == 0) return def;
        return clamp(levels[Math.min(p, levels.length - 1)]);
    }

    /** a value pulled into range and onto its steps (NaN or infinity: the default) */
    public double clamp(double v) {
        if (!Double.isFinite(v)) return def;
        return switch (kind) {
            case TOGGLE -> v >= 0.5 ? 1 : 0;
            case CHOICE -> Math.max(0, Math.min(options.size() - 1, Math.round(v)));
            case SLIDER -> {
                double c = Math.max(min, Math.min(max, v));
                if (step > 0) c = Math.max(min, Math.min(max, min + Math.round((c - min) / step) * step));
                // (no 0.30000000000000004)
                yield Math.round(c * 10000.0) / 10000.0;
            }
        };
    }

    /** whether value v gives up something compared with the default: a toggle turned off, a slider down, a choice on */
    public boolean lower(double v) {
        v = clamp(v);
        return switch (kind) {
            case TOGGLE -> def >= 0.5 && v < 0.5;
            case CHOICE -> v > def;
            case SLIDER -> v < def - 1e-9;
        };
    }

    /** whether value v takes the thing away altogether (a toggle off, a slider at a zero bottom, the last choice) */
    public boolean removes(double v) {
        v = clamp(v);
        return switch (kind) {
            case TOGGLE -> def >= 0.5 && v < 0.5;
            case CHOICE -> options.size() > 1 && v >= options.size() - 1 && def < options.size() - 1
                    && options.get(options.size() - 1).toLowerCase(Locale.ROOT).startsWith("off");
            case SLIDER -> min <= 0 && v <= 0 && def > 0;
        };
    }

    /** a value as words: "75%", "1.5x", "300 blocks", "On", an option's name */
    public String show(double v) {
        v = clamp(v);
        return switch (kind) {
            case TOGGLE -> v >= 0.5 ? "On" : "Off";
            case CHOICE -> options.get((int) v);
            case SLIDER -> switch (unit) {
                case "%" -> Math.round(v * 100) + "%";
                case "x" -> trim(v) + "x";
                case "" -> trim(v);
                default -> trim(v) + " " + unit;
            };
        };
    }

    private static String trim(double v) {
        if (Math.abs(v - Math.rint(v)) < 1e-9) return String.valueOf((long) Math.rint(v));
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** the id a giant reads it by (a per-giant copy loses its ".giant" end) */
    public String plainId() {
        return perGiant && !owner.isEmpty() && id.endsWith("." + owner)
                ? id.substring(0, id.length() - owner.length() - 1)
                : id;
    }
}
