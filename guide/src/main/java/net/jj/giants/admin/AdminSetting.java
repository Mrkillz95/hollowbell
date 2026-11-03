package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * One admin setting (ADMIN_BRIDGE.md): one of a giant's own, as his mod reported it over the bridge, or one of the
 * guide's all-giants multipliers (giant "all"). Values are JSON: true/false, a number, a choice's option, or
 * [low, high] for a size range.
 *
 * @param giant   the guide's key for the giant ("wreckback"), or "all"
 * @param id      its id in that giant's mod ("healthMultiplier", "move.claw_snip.on", "all.health")
 * @param cat     its category: one of {@link #CATEGORIES}
 * @param def     the server's default (the giant's config file)
 * @param value   the value in use now
 * @param jar     the default the mod ships with
 * @param set     the world has its own value for it
 * @param when    when a change takes effect: one of {@link #WHENS}
 * @param name    its name in plain words
 * @param what    one line on what it does
 * @param key     the language key for a translated name (key + ".what" for the line), or ""
 * @param group   the move (or other thing) it belongs to, for grouping on a page, or ""
 */
public record AdminSetting(
        String giant,
        String id,
        String cat,
        Type type,
        double min,
        double max,
        double step,
        List<String> options,
        JsonElement def,
        JsonElement value,
        JsonElement jar,
        boolean set,
        String when,
        String name,
        String what,
        String key,
        String group) {

    public static final List<String> CATEGORIES = List.of(
            "spawning", "worldgen", "stats", "behaviour", "moves", "healing", "taming", "loot", "players", "effects",
            "load", "server", "systems");

    public static final String LIVE = "live", NEW_SPAWNS = "newSpawns", NEW_LAND = "newLand",
            WORLD_CREATION = "worldCreation", RESTART = "restart";
    public static final List<String> WHENS = List.of(LIVE, NEW_SPAWNS, NEW_LAND, WORLD_CREATION, RESTART);

    public enum Type {
        BOOL,
        INT,
        FLOAT,
        CHOICE,
        RANGE;

        public String word() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static @Nullable Type of(@Nullable String s) {
            return switch (s == null ? "" : s.toLowerCase(Locale.ROOT)) {
                case "bool" -> BOOL;
                case "int" -> INT;
                case "float" -> FLOAT;
                case "choice" -> CHOICE;
                case "range" -> RANGE;
                default -> null;
            };
        }
    }

    /** a value it can't take, with what to say about it */
    public static final class BadValue extends Exception {
        public BadValue(String message) {
            super(message);
        }
    }

    /** whether a change only takes full effect in a new world, or on land not made yet */
    public boolean creationOnly() {
        return when.equals(WORLD_CREATION) || when.equals(NEW_LAND);
    }

    /** the warning shown beside it and when it is changed in a world that already exists ("" for live ones) */
    public String whenNote() {
        return whenNote(when);
    }

    public static String whenNote(String when) {
        return switch (when) {
            case NEW_SPAWNS -> "Only affects new spawns.";
            case NEW_LAND -> "Only affects land not made yet.";
            case WORLD_CREATION -> "World creation only: takes full effect in a new world.";
            case RESTART -> "Takes effect when the world is next started.";
            default -> "";
        };
    }

    /** the short label for lists ("" for live ones) */
    public String whenLabel() {
        return switch (when) {
            case NEW_SPAWNS -> "new spawns";
            case NEW_LAND -> "new land";
            case WORLD_CREATION -> "world creation";
            case RESTART -> "restart";
            default -> "";
        };
    }

    /** a value typed in a command (or found in an import), checked and put into its form; throws with the reason */
    public JsonElement parse(String text) throws BadValue {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        switch (type) {
            case BOOL -> {
                return switch (t) {
                    case "on", "true", "yes" -> new JsonPrimitive(true);
                    case "off", "false", "no" -> new JsonPrimitive(false);
                    default -> throw new BadValue(name + " has to be on or off.");
                };
            }
            case CHOICE -> {
                for (String o : options) if (o.toLowerCase(Locale.ROOT).equals(t)) return new JsonPrimitive(o);
                throw new BadValue(name + " has to be one of: " + String.join(", ", options) + ".");
            }
            case RANGE -> {
                String[] w = t.replace("..", "-").replace("to", "-").split("\\s*-\\s*|\\s+");
                try {
                    if (w.length == 1) {
                        double v = number(w[0]);
                        return range(v, v);
                    }
                    if (w.length == 2) return range(number(w[0]), number(w[1]));
                } catch (NumberFormatException e) {
                    // said below
                }
                throw new BadValue(name + " has to be two sizes like " + plain(min) + "-" + plain(max) + ", each from "
                        + plain(min) + " to " + plain(max) + ".");
            }
            default -> {
                double v;
                try {
                    v = number(t.startsWith("x") ? t.substring(1) : t);
                } catch (NumberFormatException e) {
                    throw new BadValue(rangeWords());
                }
                if (v < min - 1e-9 || v > max + 1e-9) throw new BadValue(rangeWords());
                if (type == Type.INT) {
                    if (Math.abs(v - Math.rint(v)) > 1e-9) throw new BadValue(rangeWords());
                    return new JsonPrimitive((long) Math.rint(v));
                }
                return new JsonPrimitive(v);
            }
        }
    }

    private JsonElement range(double lo, double hi) throws BadValue {
        if (!(lo >= min - 1e-9 && hi <= max + 1e-9 && lo <= hi + 1e-9))
            throw new BadValue(name + " has to be two sizes like " + plain(min) + "-" + plain(max) + ", each from "
                    + plain(min) + " to " + plain(max) + ", the smaller first.");
        JsonArray a = new JsonArray();
        a.add(lo);
        a.add(hi);
        return a;
    }

    private static double number(String s) {
        double v = Double.parseDouble(s);
        if (!Double.isFinite(v)) throw new NumberFormatException(s);
        return v;
    }

    /** "Health multiplier has to be a number from 0.1 to 10." (WORDING.md) */
    public String rangeWords() {
        return name + " has to be " + (type == Type.INT ? "a whole number" : "a number") + " from " + plain(min)
                + " to " + plain(max) + ".";
    }

    /** whether a stored value (from the world, an import or a preset) is one it can take */
    public boolean accepts(@Nullable JsonElement v) {
        if (v == null || v.isJsonNull()) return false;
        try {
            parse(text(v));
            return true;
        } catch (BadValue | RuntimeException e) {
            return false;
        }
    }

    /** a value the way people write it: on/off, 1.5, an option, 0.5-1.5 */
    public static String text(@Nullable JsonElement v) {
        if (v == null || v.isJsonNull()) return "-";
        if (v.isJsonArray() && v.getAsJsonArray().size() == 2)
            return plain(v.getAsJsonArray().get(0).getAsDouble()) + "-"
                    + plain(v.getAsJsonArray().get(1).getAsDouble());
        if (v.isJsonPrimitive()) {
            JsonPrimitive p = v.getAsJsonPrimitive();
            if (p.isBoolean()) return p.getAsBoolean() ? "on" : "off";
            if (p.isNumber()) return plain(p.getAsDouble());
            return p.getAsString();
        }
        return v.toString();
    }

    public static String plain(double d) {
        if (!Double.isFinite(d)) return String.valueOf(d);
        return new BigDecimal(Double.toString(Math.round(d * 10000.0) / 10000.0)).stripTrailingZeros().toPlainString();
    }

    /** two values are the same setting value (1 and 1.0 are) */
    public static boolean same(@Nullable JsonElement a, @Nullable JsonElement b) {
        if (a == null || b == null) return a == b;
        return text(a).equals(text(b));
    }

    /** what it can take, for a list: "on/off", "0.1 to 10", "calm, hunting" */
    public String rangeText() {
        return switch (type) {
            case BOOL -> "on/off";
            case CHOICE -> String.join(", ", options);
            case RANGE -> plain(min) + "-" + plain(max);
            default -> plain(min) + " to " + plain(max);
        };
    }
}
