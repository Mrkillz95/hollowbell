package net.jj.hollowbell;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Where a command's change goes (ADMIN_BRIDGE.md, "Command scope"). Every command that changes one of his settings
 * takes a word at the end: {@code world} = this world only, kept by the Giants Guide with the world (saved, logged,
 * told to the other operators and sent to every giant, exactly like /giants admin set); {@code allworlds} = the
 * server default, his config file, as the commands always did. Nothing said = this world; without the Guide there are
 * no per-world settings, so then it is the server default and the reply says so (an explicit {@code world} is refused).
 */
public final class AdminScope {
    private AdminScope() {}

    public enum Scope {
        WORLD,
        ALLWORLDS,
        /** nothing said: this world, or the server default without the Guide */
        UNSAID;

        public static Scope of(@Nullable String word) {
            if (word == null) return UNSAID;
            return switch (word.toLowerCase(Locale.ROOT)) {
                case "world" -> WORLD;
                case "allworlds" -> ALLWORLDS;
                default -> UNSAID;
            };
        }
    }

    /** the Giants Guide's side of the bridge: {@code List<String> guide(MinecraftServer, String action, String arg)} */
    public static final String GUIDE = "net.jj.giants.GuideBridge";

    public static final String WHERE_WORLD = " in this world.", WHERE_ALL = " for all worlds (server default).",
            WHERE_NO_GUIDE = " for all worlds (server default; per-world settings need the Giants Guide).";
    public static final String NEED_GUIDE =
            "Per-world settings need the Giants Guide. Add allworlds to change the server default.";
    public static final String NO_ANSWER = "The Giants Guide didn't answer, so nothing changed.";

    /** the tests' stand-in for the Giants Guide (action, arg) -> answer; null: the real one, if it's installed */
    public static volatile @Nullable BiFunction<String, String, List<String>> testGuide;

    private static volatile @Nullable Method guideMethod;
    private static volatile boolean looked;

    private static @Nullable Method guideMethod() {
        if (!looked) {
            try {
                Class<?> c = Class.forName(GUIDE, true, AdminScope.class.getClassLoader());
                Method m = c.getMethod("guide", MinecraftServer.class, String.class, String.class);
                guideMethod = Modifier.isStatic(m.getModifiers()) ? m : null;
            } catch (Throwable t) {
                // the Giants Guide isn't installed (or is one from before the command scope)
                guideMethod = null;
            }
            looked = true;
        }
        return guideMethod;
    }

    /** true when the Giants Guide is here to keep per-world settings */
    public static boolean guideHere() {
        return testGuide != null || guideMethod() != null;
    }

    /** one call into the Giants Guide; null if it isn't there, doesn't know the action, or fails */
    static @Nullable List<String> guide(@Nullable MinecraftServer server, String action, String arg) {
        BiFunction<String, String, List<String>> fake = testGuide;
        if (fake != null) return fake.apply(action, arg);
        Method m = guideMethod();
        if (m == null) return null;
        try {
            Object r = m.invoke(null, server, action, arg);
            if (!(r instanceof List<?> l)) return null;
            List<String> out = new ArrayList<>();
            for (Object o : l) if (o != null) out.add(o.toString());
            return out;
        } catch (Throwable t) {
            HollowbellMod.LOG.warn("The Giants Guide failed on {}: {}", action, t.toString());
            return null;
        }
    }

    // what /giants sends: "<value> scope=world|allworlds by=<player UUID>|-" (both words left off for nothing said)

    /** an argument from /giants with its scope words taken off */
    public record Scoped(String rest, Scope scope, @Nullable UUID by) {}

    public static Scoped scoped(@Nullable String arg) {
        if (arg == null || arg.startsWith("{") || (!arg.contains("scope=") && !arg.contains("by=")))
            return new Scoped(arg, Scope.UNSAID, null);
        List<String> rest = new ArrayList<>();
        Scope s = Scope.UNSAID;
        UUID by = null;
        for (String w : arg.trim().split("\\s+")) {
            if (w.startsWith("scope=")) s = Scope.of(w.substring(6));
            else if (w.startsWith("by=")) {
                try {
                    by = UUID.fromString(w.substring(3));
                } catch (IllegalArgumentException e) {
                    by = null; // "-": the server's console or a command block
                }
            } else rest.add(w);
        }
        return new Scoped(String.join(" ", rest), s, by);
    }

    /**
     * What a change did.
     *
     * @param why   why nothing changed (not ok)
     * @param where the words that take the place of the reply's full stop: " in this world." and so on
     * @param after said after that: "This world keeps its own: ...", the Guide's note on when it takes effect
     * @param world it went to this world's settings (the Guide told the other operators)
     */
    public record Done(boolean ok, String why, String where, String after, boolean world) {
        static Done fail(String why) {
            return new Done(false, why, "", "", false);
        }

        /** a reply with where it went in place of its full stop: "Hollowbell: health x2 in this world." */
        public String say(String line) {
            String base = line.endsWith(".") ? line.substring(0, line.length() - 1) : line;
            return base + where + after;
        }

        public Component say(Component line) {
            return Component.literal(say(line.getString()));
        }
    }

    /** the player behind a command (null: the console, a command block) */
    public static @Nullable UUID by(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() == null ? null : c.getSource().getPlayer().getUUID();
    }

    /**
     * One change from a command or a book switch: each id (a BellConfig field) to its value (Boolean, Integer or
     * Float), for this world through the Guide or into the config file.
     */
    public static Done write(@Nullable MinecraftServer server, Scope scope, @Nullable UUID by, Object... idValue) {
        boolean guide = guideHere();
        boolean world = scope == Scope.WORLD || (scope == Scope.UNSAID && guide);
        if (scope == Scope.WORLD && !guide) return Done.fail(NEED_GUIDE);
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i + 1 < idValue.length; i += 2) values.put((String) idValue[i], idValue[i + 1]);
        if (world) {
            // a setting the world can't keep (how he looks or sounds on each game) only goes in the file
            for (String id : values.keySet()) {
                GiantsAdmin.Row r = GiantsAdmin.row(id);
                if (r == null || r.field() == null) {
                    if (scope == Scope.WORLD)
                        return Done.fail(id + " isn't a world setting (it is how he looks or sounds on each game)."
                                + " Leave out world.");
                    world = false;
                }
            }
        }
        if (!world) {
            Map<Field, Object> file = new LinkedHashMap<>();
            for (var e : values.entrySet()) {
                GiantsAdmin.Row rr = GiantsAdmin.row(e.getKey());
                // (a range: both its fields)
                if (rr != null && rr.field2() != null && e.getValue() instanceof float[] lh && lh.length == 2) {
                    file.put(rr.field(), lh[0]);
                    file.put(rr.field2(), lh[1]);
                    continue;
                }
                try {
                    file.put(BellConfig.Values.class.getField(e.getKey()), e.getValue());
                } catch (NoSuchFieldException ex) {
                    return Done.fail("There's no setting called " + e.getKey() + ".");
                }
            }
            BellConfig.setDefaults(file);
            StringBuilder notes = new StringBuilder();
            for (String id : values.keySet()) notes.append(GiantsAdmin.note(id));
            String where = guide || scope == Scope.ALLWORLDS ? WHERE_ALL : WHERE_NO_GUIDE;
            return new Done(true, "", where, notes.toString(), false);
        }
        StringBuilder after = new StringBuilder();
        for (var e : values.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("admin", GiantsAdmin.VERSION);
            o.addProperty("giant", GiantsAdmin.GIANT);
            o.addProperty("id", e.getKey());
            o.add("value", json(e.getValue()));
            o.addProperty("by", by == null ? "-" : by.toString());
            List<String> r = guide(server, "adminworld", o.toString());
            if (r == null || r.isEmpty()) return Done.fail(NO_ANSWER);
            if (!r.get(0).equals("ok")) return Done.fail(r.size() > 1 ? r.get(1) : NO_ANSWER);
            // (the Guide's first line is its own "set to"; anything after it: when it takes effect, or trouble)
            for (String s : r.subList(Math.min(2, r.size()), r.size())) after.append(' ').append(s);
        }
        return new Done(true, "", WHERE_WORLD, after.toString(), true);
    }

    /**
     * GiantsBridge "admindefault" (/giants admin set ... allworlds): {"admin":1,"id":"<setting>","value":<value>}, the
     * server default of one setting, into his config file; the world's own values stay as they are. Answers "ok", or
     * "no" and {"rejected":id,"why":"unknown"|"world only"|"bad value"}; null if it can't be read.
     */
    public static @Nullable List<String> receiveDefault(@Nullable MinecraftServer server, String arg) {
        String id;
        com.google.gson.JsonElement value;
        try {
            JsonObject o = com.google.gson.JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("admin").getAsInt() > GiantsAdmin.VERSION) return null;
            id = o.get("id").getAsString();
            value = o.get("value");
        } catch (RuntimeException e) {
            return null;
        }
        GiantsAdmin.Row r = GiantsAdmin.row(id);
        if (r == null) return List.of("no", rejected(id, "unknown"));
        // (a move's switch, wait and wind are only ever kept for a world: his file has none)
        if (r.field() == null) return List.of("no", rejected(id, "world only"));
        com.google.gson.JsonElement v = GiantsAdmin.check(r, value);
        if (v == null) return List.of("no", rejected(id, "bad value"));
        Object typed = switch (r.type()) {
            case "range" -> new float[] {
                v.getAsJsonArray().get(0).getAsFloat(), v.getAsJsonArray().get(1).getAsFloat()
            };
            case "choice" -> v.getAsString();
            case "bool" -> v.getAsBoolean();
            case "int" -> v.getAsInt();
            default -> v.getAsFloat();
        };
        float before = GiantsAdmin.fullHealth();
        write(server, Scope.ALLWORLDS, null, id, typed);
        if (server != null) GiantsAdmin.applyLive(server, before);
        return List.of("ok");
    }

    private static String rejected(String id, String why) {
        JsonObject o = new JsonObject();
        o.addProperty("rejected", id);
        o.addProperty("why", why);
        return o.toString();
    }

    private static com.google.gson.JsonElement json(Object v) {
        if (v instanceof float[] a) {
            com.google.gson.JsonArray x = new com.google.gson.JsonArray();
            for (float f : a) x.add(Double.parseDouble(Float.toString(f)));
            return x;
        }
        if (v instanceof Boolean b) return new JsonPrimitive(b);
        if (v instanceof Float f) return new JsonPrimitive(Double.parseDouble(Float.toString(f)));
        if (v instanceof Number n) return new JsonPrimitive(n);
        return new JsonPrimitive(String.valueOf(v));
    }

    /** a value typed for a config field (on/off, a number), in the field's own type; null if it isn't one */
    public static @Nullable Object parse(String id, String text) {
        GiantsAdmin.Row row = GiantsAdmin.row(id);
        if (row != null && row.type().equals("range")) {
            String[] w = text.trim().replace("..", "-").split("\s*-\s*|\s+");
            try {
                float lo = Float.parseFloat(w[0]), hi = w.length > 1 ? Float.parseFloat(w[1]) : lo;
                if (w.length > 2 || !Float.isFinite(lo) || !Float.isFinite(hi)) return null;
                return new float[] {Math.min(lo, hi), Math.max(lo, hi)};
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (row != null && row.type().equals("choice")) {
            String t = text.trim().toLowerCase(Locale.ROOT);
            return row.options().contains(t) ? t : null;
        }
        try {
            Field f = BellConfig.Values.class.getField(id);
            String v = text.trim().toLowerCase(Locale.ROOT);
            Class<?> t = f.getType();
            if (t == boolean.class)
                return switch (v) {
                    case "on", "true", "yes" -> Boolean.TRUE;
                    case "off", "false", "no" -> Boolean.FALSE;
                    default -> null;
                };
            if (t == int.class) return Integer.parseInt(v);
            if (t == float.class) {
                float x = Float.parseFloat(v);
                return Float.isFinite(x) ? x : null;
            }
            return null;
        } catch (NoSuchFieldException | NumberFormatException e) {
            return null;
        }
    }

    /** says what a change did: the reply with where it went, or why not (the Guide tells the other operators) */
    public static int reply(CommandContext<CommandSourceStack> c, Done d, Component line) {
        if (!d.ok()) {
            c.getSource().sendFailure(Component.literal(d.why()));
            return 0;
        }
        Component said = d.say(line);
        c.getSource().sendSuccess(() -> said, !d.world());
        return 1;
    }

    /** a command that changes a setting */
    public interface Run {
        int run(CommandContext<CommandSourceStack> c, Scope s) throws CommandSyntaxException;
    }

    /** the node runs as it is, and with "world" or "allworlds" after it */
    public static <T extends ArgumentBuilder<CommandSourceStack, T>> T withScope(T node, Run run) {
        return node.executes(c -> run.run(c, Scope.UNSAID))
                .then(Commands.literal("world").executes(c -> run.run(c, Scope.WORLD)))
                .then(Commands.literal("allworlds").executes(c -> run.run(c, Scope.ALLWORLDS)));
    }
}
