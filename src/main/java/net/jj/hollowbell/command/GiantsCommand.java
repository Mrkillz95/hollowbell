package net.jj.hollowbell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.jj.hollowbell.GiantsBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * /giants: one command for all the giants at once. Every one of the giant mods carries this class, but only one
 * registers it: the first of the bridges (in this fixed order) that is actually loaded.
 *
 * The command asks every loaded bridge in turn, by name. Each one is asked on its own, and whatever goes wrong
 * inside one (a missing method, an old version, a crash, a strange answer) only costs that one line: the rest
 * still answer. Every part of it needs cheats (op level 2).
 */
public final class GiantsCommand {
    private GiantsCommand() {}

    private static final Logger LOG = LoggerFactory.getLogger("giants");

    /**
     * the bridges, in the order every copy elects them; the same list in every mod. New giants go on the end, not
     * in their sorted place, so the election doesn't change.
     */
    public static final String[] BRIDGES = {
        "net.jj.cerberus.GiantsBridge",
        "net.jj.furrowmaw.GiantsBridge",
        "net.jj.hollowbell.GiantsBridge",
        "net.jj.lanternwillow.GiantsBridge",
        "net.jj.mountain.GiantsBridge",
        "net.jj.wreckback.GiantsBridge",
        "net.jj.swarmforge.GiantsBridge",
        "net.jj.gloamwick.GiantsBridge",
        "net.jj.hearthstride.GiantsBridge"
    };

    /**
     * which /giants this copy builds: a copy with a higher number has commands an older one lacks (3: flee; 4: "world"
     * or "allworlds" on the end of every change, ADMIN_BRIDGE.md "Command scope")
     */
    public static final int TREE = 4;

    private static final String MINE = GiantsBridge.class.getName();

    /** most lines one bridge may add, so a broken one can't flood the chat */
    private static final int MOST_LINES = 40;

    private static final String NO_ANSWER = ": this version doesn't answer /giants.";
    private static final String DOESNT_KNOW = ": doesn't know \"";
    private static final String DIDNT_ANSWER = ": didn't answer (";

    /** true if this mod is the one that registers /giants */
    public static boolean elected() {
        for (String b : BRIDGES) {
            if (find(b) != null) {
                return b.equals(MINE);
            }
        }

        return false;
    }

    /**
     * True when another mod's copy registers /giants but that copy is older: its BRIDGES list doesn't have this
     * giant, or it builds an older tree. Then this copy registers /giants as well, after it, so every /giants
     * command is answered by this newer copy, which asks all of them. Where both have the same command, the one
     * registered last runs.
     */
    public static boolean electedIsOld() {
        for (String b : BRIDGES) {
            if (find(b) == null) continue;
            if (b.equals(MINE)) return false;
            Class<?> theirs = find(b.substring(0, b.lastIndexOf('.')) + ".command.GiantsCommand");
            if (theirs == null) return false;
            try {
                String[] list = (String[]) theirs.getField("BRIDGES").get(null);
                if (!Arrays.asList(list).contains(MINE)) return true;
            } catch (ReflectiveOperationException | ClassCastException e) {
                // another mod's copy without a list of its own
                LOG.debug("No /giants list in {}: {}", theirs.getName(), e.toString());
                return false;
            }
            try {
                return theirs.getField("TREE").getInt(null) < TREE;
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                // a copy from before the trees were numbered
                return true;
            }
        }
        return false;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, GiantsCommand.class.getClassLoader());
        } catch (Throwable t) {
            // that giant's mod isn't installed (or is an older one that can't load)
            return null;
        }
    }

    private static String nameOf(String bridge) {
        String mid = bridge.substring("net.jj.".length(), bridge.lastIndexOf('.'));
        return switch (mid) {
            case "mountain" -> "Pitchgut";
            case "lanternwillow" -> "Lantern Willow";
            default -> Character.toUpperCase(mid.charAt(0)) + mid.substring(1);
        };
    }

    /** the bridge's giants method, or null when it has none of the right shape (or a broken API number) */
    private static Method method(Class<?> c) throws NoSuchMethodException {
        try {
            Object api = c.getField("API").get(null);
            if (api instanceof Integer v && v < 1) {
                return null;
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // no API number: try it anyway
        }

        Method m = c.getMethod("giants", MinecraftServer.class, String.class, String.class);
        return Modifier.isStatic(m.getModifiers()) ? m : null;
    }

    /** the name /giants takes for each bridge, in the same order as BRIDGES */
    public static final String[] TP_NAMES = {
        "cerberus", "furrowmaw", "hollowbell", "willow", "pitchgut", "wreckback", "swarmforge", "gloamwick",
        "hearthstride"
    };

    /** every loaded bridge's answer, in order */
    public static List<String> ask(MinecraftServer server, String action, String arg) {
        return ask(server, action, arg, null);
    }

    /** the same, from just one bridge when only is set (one of BRIDGES) */
    public static List<String> ask(MinecraftServer server, String action, String arg, String only) {
        List<String> out = new ArrayList<>();
        for (String b : BRIDGES) {
            if (only != null && !only.equals(b)) {
                continue;
            }
            Class<?> c = find(b);
            if (c == null) {
                continue;
            }

            try {
                Method m = method(c);
                if (m == null) {
                    out.add(nameOf(b) + NO_ANSWER);
                    continue;
                }

                Object r = m.invoke(null, server, action, arg);
                if (r == null) {
                    out.add(nameOf(b) + DOESNT_KNOW + action + "\".");
                } else if (r instanceof Iterable<?> lines) {
                    int n = 0;
                    for (Object o : lines) {
                        if (o != null && n++ < MOST_LINES) {
                            out.add(String.valueOf(o));
                        }
                    }
                }
            } catch (Throwable t) {
                Throwable why =
                        t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                LOG.warn("/giants: {} failed on {}", b, action, why);
                out.add(nameOf(b) + DIDNT_ANSWER + why.getClass().getSimpleName() + ").");
            }
        }

        return out;
    }

    /** true for a line ask() wrote itself because a bridge couldn't answer */
    private static boolean trouble(String line) {
        return line.endsWith(NO_ANSWER) || line.contains(DOESNT_KNOW) || line.contains(DIDNT_ANSWER);
    }

    private static int say(CommandContext<CommandSourceStack> c, List<String> lines, boolean tellOps) {
        if (lines.isEmpty()) {
            lines = List.of("No giants answered.");
        }
        for (String s : lines) {
            c.getSource().sendSuccess(() -> Component.literal(s), tellOps);
        }
        return lines.size();
    }

    // where a change goes (ADMIN_BRIDGE.md, "Command scope"): "world" = this world only (kept by the Giants Guide),
    // "allworlds" = the server default (each giant's config file). Nothing said = this world, or the server default
    // when the Guide isn't here (no per-world settings without it).

    public static final String WORLD = "world", ALLWORLDS = "allworlds";

    /** the Giants Guide's side of the bridge: it keeps each world's settings */
    public static final String GUIDE = "net.jj.giants.GuideBridge";

    public static final String NEED_GUIDE =
            "Per-world settings need the Giants Guide. Add allworlds to change the server default.";

    /** the tests' stand-in for the Giants Guide is in place */
    public static volatile boolean testGuide;

    /** true when the Giants Guide is installed (so a world can keep settings of its own) */
    public static boolean guideHere() {
        return testGuide || find(GUIDE) != null;
    }

    /** true when a change with this scope ("", world, allworlds) goes to this world's own settings */
    private static boolean toWorld(String scope) {
        return scope.equals(WORLD) || (scope.isEmpty() && guideHere());
    }

    /** a bridge that takes the scope words (its GiantsBridge has SCOPE >= 1) */
    public static boolean takesScope(Class<?> bridge) {
        try {
            Object v = bridge.getField("SCOPE").get(null);
            return v instanceof Integer i && i >= 1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            // an older giant: it only knows the server default
            return false;
        }
    }

    /**
     * What a bridge is sent for a change: the value, then "scope=world|allworlds" (nothing for nothing said) and
     * "by=<player UUID>|-" (who, for the Guide's log). A bridge that doesn't take them gets the value alone.
     */
    public static String scopedArg(Class<?> bridge, String value, String scope, String by) {
        if (!takesScope(bridge)) return value;
        return value + (scope.isEmpty() ? "" : " scope=" + scope) + " by=" + by;
    }

    /** the words for where a change went, in place of a full stop */
    public static String where(String scope) {
        if (scope.equals(ALLWORLDS)) return " for all worlds (server default).";
        if (toWorld(scope)) return " in this world.";
        return " for all worlds (server default; per-world settings need the Giants Guide).";
    }

    private static String by(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() == null ? "-" : c.getSource().getPlayer().getUUID().toString();
    }

    /**
     * A change asked of every loaded bridge (or only one), with its scope. An older bridge that can't keep a value
     * for one world is left out of an explicit "world" (and says so); with nothing said it changes the server default
     * as it always did, and the answer says that too.
     */
    public static List<String> askScoped(
            MinecraftServer server, String action, String value, String only, String scope, String by) {
        List<String> out = new ArrayList<>();
        for (String b : BRIDGES) {
            if (only != null && !only.equals(b)) continue;
            Class<?> c = find(b);
            if (c == null) continue;
            boolean takes = takesScope(c);
            if (!takes && scope.equals(WORLD)) {
                out.add(nameOf(b) + ": this version can't keep a setting for one world. Add allworlds to change the"
                        + " server default.");
                continue;
            }
            List<String> said = ask(server, action, scopedArg(c, value, scope, by), b);
            out.addAll(said);
            if (!takes && !said.isEmpty() && scope.isEmpty() && guideHere())
                out.add(nameOf(b) + ": this version only changes the server default, for all worlds.");
        }
        return out;
    }

    /** a change from /giants: explicit "world" with no Guide is refused; the Guide tells the other ops of a world one */
    private static int change(CommandContext<CommandSourceStack> c, String action, String value, String only,
            String scope) {
        if (scope.equals(WORLD) && !guideHere()) {
            c.getSource().sendFailure(Component.literal(NEED_GUIDE));
            return 0;
        }
        return say(c, askScoped(c.getSource().getServer(), action, value, only, scope, by(c)), !toWorld(scope));
    }

    /** a change with "world" or "allworlds" after it, or nothing */
    private interface Scoped {
        int run(CommandContext<CommandSourceStack> c, String scope) throws CommandSyntaxException;
    }

    private static <T extends com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, T>> T scoped(
            T node, Scoped run) {
        return node.executes(c -> run.run(c, ""))
                .then(Commands.literal(WORLD).executes(c -> run.run(c, WORLD)))
                .then(Commands.literal(ALLWORLDS).executes(c -> run.run(c, ALLWORLDS)));
    }

    /** the actions that change something even with nothing after them (other ops are told of those) */
    private static final List<String> CHANGES = List.of("kill", "remove", "heal", "calm", "sleep", "wake");

    private static int run(CommandContext<CommandSourceStack> c, String action, String arg) {
        boolean changes = !arg.isEmpty() || CHANGES.contains(action);
        return say(c, ask(c.getSource().getServer(), action, arg), changes);
    }

    /** /giants tp <name>: takes you to that one giant, asking only its own mod */
    private static int tp(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return one(c, "tp", c.getSource().getPlayerOrException().getUUID().toString());
    }

    /** /giants home <name>: takes you to the middle of that giant's ground, onto safe land */
    private static int home(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return one(c, "home", c.getSource().getPlayerOrException().getUUID().toString());
    }

    /** how deep /giants paint lays a giant's ground when no depth is given, and the most and least it takes */
    public static final int PAINT_DEPTH = 5, PAINT_DEPTH_MIN = 1, PAINT_DEPTH_MAX = 64;

    /**
     * /giants paint <name> [radius] [full|biome] [depth]: turns the land round you into that giant's ground (asks
     * only its own mod). The arg is "radius mode playerUUID depth=N": the depth goes last, as "depth=N", so a
     * giant's older mod (which reads only the first three words, or takes any bare number for the radius) still
     * paints, at its own old depth.
     */
    private static int paint(CommandContext<CommandSourceStack> c, int radius, String mode, int depth)
            throws CommandSyntaxException {
        return one(
                c,
                "paint",
                radius + " " + mode + " " + c.getSource().getPlayerOrException().getUUID() + " depth=" + depth);
    }

    /**
     * /giants paint stop: every giant's paint jobs stop. Each bridge stops its own and says so; old ones that don't
     * know the action are left out of the answer.
     */
    private static int paintStop(CommandContext<CommandSourceStack> c) {
        boolean any =
                ask(c.getSource().getServer(), "paintstop", "").stream().anyMatch(s -> s.endsWith("stopped painting."));
        String line = any ? "Stopped painting." : "Nothing is painting.";
        c.getSource().sendSuccess(() -> Component.literal(line), true);
        return any ? 1 : 0;
    }

    /** the depth a paint command was given */
    private static int depth(CommandContext<CommandSourceStack> c) {
        return IntegerArgumentType.getInteger(c, "depth");
    }

    /** the depths offered when typing one */
    private static final String[] PAINT_DEPTHS = {"1", "3", "5", "8", "10", "16", "32", "64"};

    /** a giant's own command name, or its mod's, for the name /giants knows it by */
    private static String alias(String name) {
        return switch (name) {
            case "lanternwillow", "lantern_willow" -> "willow";
            case "mountain", "mountain_breathes" -> "pitchgut";
            case "fire_ice_cerberus" -> "cerberus";
            default -> name;
        };
    }

    /** the bridge of the giant named in the command, or null (having said why) */
    private static String picked(CommandContext<CommandSourceStack> c) {
        String which = alias(StringArgumentType.getString(c, "which").toLowerCase(Locale.ROOT));
        int i = Arrays.asList(TP_NAMES).indexOf(which);
        if (i < 0) {
            c.getSource()
                    .sendFailure(Component.literal(
                            "No giant called " + which + ". Try one of: " + String.join(", ", TP_NAMES) + "."));
            return null;
        }
        if (find(BRIDGES[i]) == null) {
            c.getSource().sendFailure(Component.literal(nameOf(BRIDGES[i]) + " isn't installed."));
            return null;
        }
        return BRIDGES[i];
    }

    /** one action, asked of just the named giant's mod */
    private static int one(CommandContext<CommandSourceStack> c, String action, String arg) {
        String b = picked(c);
        if (b == null) return 0;
        boolean changes = !action.equals("tp") && !action.equals("home") && !action.equals("info");
        List<String> lines = ask(c.getSource().getServer(), action, arg, b);
        for (String s : lines) {
            c.getSource().sendSuccess(() -> Component.literal(s), changes);
        }
        return lines.size();
    }

    /** the main body of each giant, by entity type id; the mod id before the colon picks its bridge */
    public static final String[] MAIN_TYPES = {
        "fire_ice_cerberus:cerberus",
        "furrowmaw:furrowmaw",
        "hollowbell:hollowbell",
        "lanternwillow:lanternwillow",
        "mountain_breathes:mountain",
        "wreckback:wreckback",
        "swarmforge:swarmforge",
        "gloamwick:gloamwick",
        "hearthstride:hearthstride"
    };

    /** which bridge (index into BRIDGES) owns this entity as a giant's main body, or -1 */
    public static int bridgeOf(Entity e) {
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        return Arrays.asList(MAIN_TYPES).indexOf(id);
    }

    /**
     * /giants meet: the two giants nearest you start a meeting fight now, whatever the table says. Each one's own
     * mod is asked with action "meet" and arg "otherUUID selfUUID" (the first word is the other one; the second
     * says which of that mod's giants it is).
     */
    private static int meet(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        ServerLevel level = src.getLevel();
        Vec3 at = src.getPosition();
        List<Entity> found = new ArrayList<>();
        for (Entity e : level.getAllEntities()) {
            if (e != null && e.isAlive() && !e.isRemoved() && bridgeOf(e) >= 0 && find(BRIDGES[bridgeOf(e)]) != null) {
                found.add(e);
            }
        }
        if (found.size() < 2) {
            src.sendFailure(Component.literal("There need to be two giants in this world for that."));
            return 0;
        }
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        Entity a = found.get(0), b = found.get(1);
        List<String> lines = new ArrayList<>();
        lines.addAll(ask(src.getServer(), "meet", b.getUUID() + " " + a.getUUID(), BRIDGES[bridgeOf(a)]));
        lines.addAll(ask(src.getServer(), "meet", a.getUUID() + " " + b.getUUID(), BRIDGES[bridgeOf(b)]));
        if (lines.isEmpty()) {
            lines = List.of("Neither of them answered.");
        }
        for (String s : lines) {
            src.sendSuccess(() -> Component.literal(s), true);
        }
        return lines.size();
    }

    /** like run, but a key only some mods have is just their lines (a bare look changes nothing) */
    private static int runKeep(CommandContext<CommandSourceStack> c, String action, String arg) {
        List<String> lines = ask(c.getSource().getServer(), action, arg);
        if (lines.isEmpty()) {
            lines = List.of("No giant has a setting called " + arg + ".");
        }
        return say(c, lines, false);
    }

    /** the multipliers /giants health and damage take */
    public static final float MULT_MIN = 0.1f, MULT_MAX = 10.0f;

    /** easy, normal, hard: the health and the damage multiplier each sets */
    public static final String[] PRESETS = {"easy", "normal", "hard"};

    public static final float[][] PRESET_MULTS = {{0.75f, 0.75f}, {1.0f, 1.0f}, {1.5f, 1.35f}};

    /** /giants preset easy|normal|hard [name] [world|allworlds]: health and damage multipliers together */
    private static int preset(CommandContext<CommandSourceStack> c, int which, String only, String scope) {
        if (scope.equals(WORLD) && !guideHere()) {
            c.getSource().sendFailure(Component.literal(NEED_GUIDE));
            return 0;
        }
        float health = PRESET_MULTS[which][0], damage = PRESET_MULTS[which][1];
        MinecraftServer server = c.getSource().getServer();
        List<String> lines =
                new ArrayList<>(askScoped(server, "healthmult", String.valueOf(health), only, scope, by(c)));
        lines.addAll(askScoped(server, "damagemult", String.valueOf(damage), only, scope, by(c)));
        if (lines.isEmpty()) {
            return say(c, lines, false);
        }
        List<String> out = new ArrayList<>();
        out.add("Preset " + PRESETS[which] + (only == null ? "" : " for " + nameOf(only)) + ": health x" + health
                + ", damage x" + damage + where(scope));
        // each giant's own line only when it has more to say: trouble, a refusal, or the world keeping its own
        for (String s : lines) {
            boolean plain = (s.contains(": health x") || s.contains(": damage x")) && !s.contains("keeps its own");
            if (!plain && !out.contains(s)) out.add(s);
        }
        return say(c, out, !toWorld(scope));
    }

    /**
     * where a /giants summon is given from, for the bridges: "dimension x y z entityUUID" (a dash for no entity),
     * then whatever was typed after the name (the mood and size, as that giant's own summon takes them)
     */
    private static String from(CommandContext<CommandSourceStack> c, String how) {
        CommandSourceStack src = c.getSource();
        Vec3 p = src.getPosition();
        String who = src.getEntity() == null ? "-" : src.getEntity().getUUID().toString();
        String at = src.getLevel().dimension().location() + " " + p.x + " " + p.y + " " + p.z + " " + who;
        return how.isEmpty() ? at : at + " " + how;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> which() {
        return Commands.argument("which", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(TP_NAMES, b));
    }

    /** /giants <name> [giant]: the action for every giant, or only the named one */
    private static LiteralArgumentBuilder<CommandSourceStack> forAll(String name, String action, String arg) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .executes(c -> run(c, action, arg))
                .then(which().executes(c -> one(c, action, arg)));
    }

    /** /giants health|damage [multiplier [giant]]: bare, it says each one's multiplier */
    private static LiteralArgumentBuilder<CommandSourceStack> mult(String name, String action) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .executes(c -> say(c, ask(c.getSource().getServer(), action, ""), false))
                .then(scoped(
                                Commands.argument("multiplier", FloatArgumentType.floatArg(MULT_MIN, MULT_MAX)),
                                (c, s) -> change(c, action, multiplier(c), null, s))
                        .then(scoped(which(), (c, s) -> {
                            String b = picked(c);
                            return b == null ? 0 : change(c, action, multiplier(c), b, s);
                        })));
    }

    private static String multiplier(CommandContext<CommandSourceStack> c) {
        return String.valueOf(FloatArgumentType.getFloat(c, "multiplier"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> presets() {
        LiteralArgumentBuilder<CommandSourceStack> p =
                Commands.literal("preset").requires(s -> s.hasPermission(2));
        for (int i = 0; i < PRESETS.length; i++) {
            int k = i;
            p.then(scoped(Commands.literal(PRESETS[i]), (c, s) -> preset(c, k, null, s))
                    .then(scoped(which(), (c, s) -> {
                        String b = picked(c);
                        return b == null ? 0 : preset(c, k, b, s);
                    })));
        }
        return p;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> summon() {
        return Commands.literal("summon")
                .requires(s -> s.hasPermission(2))
                .executes(c -> run(c, "summon", from(c, "")))
                .then(which()
                        .executes(c -> one(c, "summon", from(c, "")))
                        .then(Commands.argument("mood and size", StringArgumentType.greedyString())
                                .executes(c -> one(
                                        c, "summon", from(c, StringArgumentType.getString(c, "mood and size"))))));
    }

    /**
     * /giants flee [on|off [giant]]: whether the giants back down from a wild fight (one they started on their own)
     * when they're losing it. Bare, it says each one's setting. A fight a player started they never back down from.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> flee() {
        LiteralArgumentBuilder<CommandSourceStack> f = Commands.literal("flee")
                .requires(s -> s.hasPermission(2))
                .executes(c -> say(c, ask(c.getSource().getServer(), "flee", ""), false));
        for (String v : new String[] {"on", "off"}) {
            f.then(scoped(Commands.literal(v), (c, s) -> change(c, "flee", v, null, s))
                    .then(scoped(which(), (c, s) -> {
                        String b = picked(c);
                        return b == null ? 0 : change(c, "flee", v, b, s);
                    })));
        }
        return f;
    }

    /** /giants <name> [on|off [world|allworlds]]: a switch every giant has */
    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .executes(c -> run(c, name, ""))
                .then(scoped(Commands.literal("on"), (c, s) -> change(c, name, "on", null, s)))
                .then(scoped(Commands.literal("off"), (c, s) -> change(c, name, "off", null, s)));
    }

    /** /giants shake [on|off]: how it looks on each game, not a world setting (no scope) */
    private static LiteralArgumentBuilder<CommandSourceStack> lookToggle(String name) {
        return Commands.literal(name)
                .requires(s -> s.hasPermission(2))
                .executes(c -> run(c, name, ""))
                .then(Commands.literal("on").executes(c -> run(c, name, "on")))
                .then(Commands.literal("off").executes(c -> run(c, name, "off")));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("giants")
                .executes(c -> {
                    if (!c.getSource().hasPermission(2)) {
                        c.getSource()
                                .sendFailure(Component.literal(
                                        "You need cheats on for that. A giant's Finder shows the way to him."));
                        return 0;
                    }

                    return run(c, "status", "");
                })
                .then(toggle("natural"))
                .then(Commands.literal("limit")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> run(c, "limit", ""))
                        .then(scoped(Commands.argument("how many", IntegerArgumentType.integer(0, 20)), (c, s) -> change(
                                c, "limit", String.valueOf(IntegerArgumentType.getInteger(c, "how many")), null, s))))
                .then(toggle("fight"))
                .then(toggle("away"))
                .then(Commands.literal("volume")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> run(c, "volume", ""))
                        .then(Commands.argument("x", FloatArgumentType.floatArg(0.0f, 2.0f))
                                .executes(c -> run(c, "volume", String.valueOf(FloatArgumentType.getFloat(c, "x"))))))
                .then(lookToggle("shake"))
                .then(Commands.literal("bossbar")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> run(c, "bossbar", ""))
                        .then(scoped(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000)), (c, s) -> change(
                                c, "bossbar", String.valueOf(IntegerArgumentType.getInteger(c, "blocks")), null, s))))
                .then(toggle("griefing"))
                .then(Commands.literal("heavydelay")
                        .requires(s -> s.hasPermission(2))
                        .then(scoped(Commands.argument("seconds", IntegerArgumentType.integer(0, 600)), (c, s) -> change(
                                c, "heavydelay", String.valueOf(IntegerArgumentType.getInteger(c, "seconds")), null,
                                s))))
                .then(toggle("meetings"))
                .then(Commands.literal("meet").requires(s -> s.hasPermission(2)).executes(GiantsCommand::meet))
                .then(flee())
                .then(Commands.literal("where").requires(s -> s.hasPermission(2)).executes(c -> run(c, "where", "")))
                .then(Commands.literal("goto")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(c -> run(
                                                c,
                                                "goto",
                                                DoubleArgumentType.getDouble(c, "x") + " "
                                                        + DoubleArgumentType.getDouble(c, "z"))))))
                .then(Commands.literal("config")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> run(c, "config", ""))
                        .then(Commands.argument("key", StringArgumentType.word())
                                .executes(c -> runKeep(c, "config", StringArgumentType.getString(c, "key")))
                                .then(scoped(Commands.argument("value", StringArgumentType.word()), (c, s) -> change(
                                        c,
                                        "config",
                                        StringArgumentType.getString(c, "key") + " "
                                                + StringArgumentType.getString(c, "value"),
                                        null,
                                        s)))))
                .then(Commands.literal("paint")
                        .requires(s -> s.hasPermission(2))
                        .then(Commands.literal("stop").executes(GiantsCommand::paintStop))
                        .then(which()
                                .executes(c -> paint(c, 64, "full", PAINT_DEPTH))
                                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
                                        .executes(c -> paint(
                                                c, IntegerArgumentType.getInteger(c, "radius"), "full", PAINT_DEPTH))
                                        .then(Commands.literal("full")
                                                .executes(c -> paint(
                                                        c,
                                                        IntegerArgumentType.getInteger(c, "radius"),
                                                        "full",
                                                        PAINT_DEPTH))
                                                .then(Commands.argument(
                                                                "depth",
                                                                IntegerArgumentType.integer(
                                                                        PAINT_DEPTH_MIN, PAINT_DEPTH_MAX))
                                                        .suggests((c, b) ->
                                                                SharedSuggestionProvider.suggest(PAINT_DEPTHS, b))
                                                        .executes(c -> paint(
                                                                c,
                                                                IntegerArgumentType.getInteger(c, "radius"),
                                                                "full",
                                                                depth(c)))))
                                        .then(Commands.literal("biome")
                                                .executes(c -> paint(
                                                        c,
                                                        IntegerArgumentType.getInteger(c, "radius"),
                                                        "biome",
                                                        PAINT_DEPTH))))))
                .then(Commands.literal("tp")
                        .requires(s -> s.hasPermission(2))
                        .then(which().executes(GiantsCommand::tp)))
                .then(Commands.literal("home")
                        .requires(s -> s.hasPermission(2))
                        .then(which().executes(GiantsCommand::home)))
                .then(mult("health", "healthmult"))
                .then(mult("damage", "damagemult"))
                .then(presets())
                .then(forAll("heal", "heal", ""))
                .then(forAll("calm", "calm", ""))
                .then(forAll("sleep", "sleep", ""))
                .then(forAll("wake", "wake", ""))
                .then(forAll("freeze", "freeze", "on"))
                .then(forAll("unfreeze", "freeze", "off"))
                .then(forAll("info", "info", ""))
                .then(summon())
                .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(c -> run(c, "list", "")))
                .then(Commands.literal("kill").requires(s -> s.hasPermission(2)).executes(c -> run(c, "kill", "")))
                .then(Commands.literal("remove")
                        .requires(s -> s.hasPermission(2))
                        .executes(c -> run(c, "remove", ""))));
    }
}
