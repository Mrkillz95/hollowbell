package net.jj.hollowbell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.jj.hollowbell.HollowbellMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * /giants: one command for all of JJ's bosses at once. Every one of the seven mods carries this class, but only
 * one registers it: the first of the seven bridges (in this fixed order) that is actually loaded. This
 * mod's bridge sorts first, so with the Cerberus installed it is this class that answers.
 *
 * The command asks every loaded bridge in turn, by name. Each one is asked on its own, and whatever goes wrong
 * inside one (a missing method, an old version, a crash, a strange answer) only costs that one line: the rest
 * still answer.
 */
public final class GiantsCommand {
    private GiantsCommand() {
    }

    /** the seven bridges, in the order every copy elects them; the same list in every mod. (The Swarmforge came
     *  seventh, so he goes on the end, not in his sorted place: the election doesn't change.) */
    public static final String[] BRIDGES = {
        "net.jj.cerberus.GiantsBridge",
        "net.jj.furrowmaw.GiantsBridge",
        "net.jj.hollowbell.GiantsBridge",
        "net.jj.lanternwillow.GiantsBridge",
        "net.jj.mountain.GiantsBridge",
        "net.jj.wreckback.GiantsBridge",
        "net.jj.swarmforge.GiantsBridge"};

    private static final String MINE = net.jj.hollowbell.GiantsBridge.class.getName();

    /** most lines one bridge may add, so a broken one can't flood the chat */
    private static final int MOST_LINES = 40;

    /** true if this mod is the one that registers /giants */
    public static boolean elected() {
        for (String b : BRIDGES) {
            if (find(b) != null) {
                return b.equals(MINE);
            }
        }

        return false;
    }

    private static Class<?> find(String name) {
        try {
            return Class.forName(name, false, GiantsCommand.class.getClassLoader());
        } catch (Throwable t) {
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

    /** the name /giants tp takes for each bridge, in the same order as BRIDGES */
    public static final String[] TP_NAMES = {"cerberus", "furrowmaw", "hollowbell", "willow", "pitchgut", "wreckback", "swarmforge"};

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
                    out.add(nameOf(b) + ": this version doesn't answer /giants.");
                    continue;
                }

                Object r = m.invoke(null, server, action, arg);
                if (r == null) {
                    out.add(nameOf(b) + ": doesn't know \"" + action + "\".");
                } else if (r instanceof Iterable<?> lines) {
                    int n = 0;
                    for (Object o : lines) {
                        if (o != null && n++ < MOST_LINES) {
                            out.add(String.valueOf(o));
                        }
                    }
                }
            } catch (Throwable t) {
                Throwable why = t instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : t;
                HollowbellMod.LOG.warn("/giants: {} failed on {}", b, action, why);
                out.add(nameOf(b) + ": didn't answer (" + why.getClass().getSimpleName() + ").");
            }
        }

        return out;
    }

    private static int run(CommandContext<CommandSourceStack> c, String action, String arg) {
        List<String> lines = ask(c.getSource().getServer(), action, arg);
        if (lines.isEmpty()) {
            lines = List.of("No bosses answered.");
        }

        boolean changes = !arg.isEmpty() || action.equals("kill") || action.equals("remove");
        for (String s : lines) {
            c.getSource().sendSuccess(() -> Component.literal(s), changes);
        }

        return lines.size();
    }

    /** /giants tp <name>: takes you to that one giant, asking only its own mod */
    private static int tp(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return one(c, "tp", c.getSource().getPlayerOrException().getUUID().toString());
    }

    /** how deep /giants paint lays a giant's ground when no depth is given, and the most and least it takes */
    public static final int PAINT_DEPTH = 5, PAINT_DEPTH_MIN = 1, PAINT_DEPTH_MAX = 64;

    /**
     * /giants paint <name> [radius] [full|biome] [depth]: turns the land round you into that giant's ground (asks
     * only its own mod). The arg is "radius mode playerUUID depth=N": the depth goes last, as "depth=N", so a
     * giant's older mod (which reads only the first three words, or takes any bare number for the radius) still
     * paints, at its own old depth.
     */
    private static int paint(CommandContext<CommandSourceStack> c, int radius, String mode, int depth) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return one(c, "paint", radius + " " + mode + " " + c.getSource().getPlayerOrException().getUUID() + " depth=" + depth);
    }

    /** the depth a paint command was given */
    private static int depth(CommandContext<CommandSourceStack> c) {
        return IntegerArgumentType.getInteger(c, "depth");
    }

    /** the depths offered when typing one */
    private static final String[] PAINT_DEPTHS = {"1", "3", "5", "8", "10", "16", "32", "64"};

    /** one action, asked of just the named giant's mod */
    private static int one(CommandContext<CommandSourceStack> c, String action, String arg) {
        String which = StringArgumentType.getString(c, "which").toLowerCase(java.util.Locale.ROOT);
        int i = java.util.Arrays.asList(TP_NAMES).indexOf(which);
        if (i < 0) {
            c.getSource().sendFailure(Component.literal("No giant called " + which + ". Try one of: " + String.join(", ", TP_NAMES) + "."));
            return 0;
        }
        if (find(BRIDGES[i]) == null) {
            c.getSource().sendFailure(Component.literal(nameOf(BRIDGES[i]) + " isn't installed."));
            return 0;
        }
        List<String> lines = ask(c.getSource().getServer(), action, arg, BRIDGES[i]);
        for (String s : lines) {
            c.getSource().sendSuccess(() -> Component.literal(s), false);
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
        "swarmforge:swarmforge"};

    /** which bridge (index into BRIDGES) owns this entity as a giant's main body, or -1 */
    public static int bridgeOf(net.minecraft.world.entity.Entity e) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        return java.util.Arrays.asList(MAIN_TYPES).indexOf(id);
    }

    /**
     * /giants meet: the two giants nearest you start a meeting fight now, whatever the table says. Each one's own
     * mod is asked with action "meet" and arg "otherUUID selfUUID" (the first word is the other one; the second
     * says which of that mod's giants it is).
     */
    private static int meet(CommandContext<CommandSourceStack> c) {
        CommandSourceStack src = c.getSource();
        net.minecraft.server.level.ServerLevel level = src.getLevel();
        net.minecraft.world.phys.Vec3 at = src.getPosition();
        List<net.minecraft.world.entity.Entity> found = new ArrayList<>();
        for (net.minecraft.world.entity.Entity e : level.getAllEntities()) {
            if (e != null && e.isAlive() && !e.isRemoved() && bridgeOf(e) >= 0 && find(BRIDGES[bridgeOf(e)]) != null) {
                found.add(e);
            }
        }
        if (found.size() < 2) {
            src.sendFailure(Component.literal("There need to be two giants in this world for that."));
            return 0;
        }
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        net.minecraft.world.entity.Entity a = found.get(0), b = found.get(1);
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
            lines = List.of("No boss has a setting called " + arg + ".");
        }
        for (String s : lines) {
            c.getSource().sendSuccess(() -> Component.literal(s), false);
        }
        return lines.size();
    }

    private static LiteralArgumentBuilder<CommandSourceStack> toggle(String name) {
        return Commands.literal(name).requires(s -> s.hasPermission(2)).executes(c -> run(c, name, ""))
            .then(Commands.literal("on").executes(c -> run(c, name, "on")))
            .then(Commands.literal("off").executes(c -> run(c, name, "off")));
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("giants")
            .executes(c -> {
                if (!c.getSource().hasPermission(2)) {
                    c.getSource().sendFailure(Component.literal("You need cheats on for that. /giants where works without."));
                    return 0;
                }

                return run(c, "status", "");
            })
            .then(toggle("natural"))
            .then(Commands.literal("limit").requires(s -> s.hasPermission(2)).executes(c -> run(c, "limit", ""))
                .then(Commands.argument("how many", IntegerArgumentType.integer(0, 20))
                    .executes(c -> run(c, "limit", String.valueOf(IntegerArgumentType.getInteger(c, "how many"))))))
            .then(toggle("fight"))
            .then(toggle("away"))
            .then(Commands.literal("volume").requires(s -> s.hasPermission(2)).executes(c -> run(c, "volume", ""))
                .then(Commands.argument("x", FloatArgumentType.floatArg(0.0F, 2.0F))
                    .executes(c -> run(c, "volume", String.valueOf(FloatArgumentType.getFloat(c, "x"))))))
            .then(toggle("shake"))
            .then(Commands.literal("bossbar").requires(s -> s.hasPermission(2)).executes(c -> run(c, "bossbar", ""))
                .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                    .executes(c -> run(c, "bossbar", String.valueOf(IntegerArgumentType.getInteger(c, "blocks"))))))
            .then(toggle("griefing"))
            .then(toggle("meetings"))
            .then(Commands.literal("meet").requires(s -> s.hasPermission(2)).executes(GiantsCommand::meet))
            .then(Commands.literal("where").executes(c -> run(c, "where", "")))
            .then(Commands.literal("goto").requires(s -> s.hasPermission(2))
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                        .executes(c -> run(c, "goto", DoubleArgumentType.getDouble(c, "x") + " " + DoubleArgumentType.getDouble(c, "z"))))))
            .then(Commands.literal("config").requires(s -> s.hasPermission(2))
                .executes(c -> run(c, "config", ""))
                .then(Commands.argument("key", StringArgumentType.word())
                    .executes(c -> runKeep(c, "config", StringArgumentType.getString(c, "key")))
                    .then(Commands.argument("value", StringArgumentType.word())
                        .executes(c -> run(c, "config", StringArgumentType.getString(c, "key") + " " + StringArgumentType.getString(c, "value"))))))
            .then(Commands.literal("paint").requires(s -> s.hasPermission(2))
                .then(Commands.argument("which", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TP_NAMES, b))
                    .executes(c -> paint(c, 64, "full", PAINT_DEPTH))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
                        .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "full", PAINT_DEPTH))
                        .then(Commands.literal("full").executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "full", PAINT_DEPTH))
                            .then(Commands.argument("depth", IntegerArgumentType.integer(PAINT_DEPTH_MIN, PAINT_DEPTH_MAX))
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(PAINT_DEPTHS, b))
                                .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "full", depth(c)))))
                        .then(Commands.literal("biome").executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "biome", PAINT_DEPTH))))))
            .then(Commands.literal("tp").requires(s -> s.hasPermission(2))
                .then(Commands.argument("which", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(TP_NAMES, b))
                    .executes(GiantsCommand::tp)))
            .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(c -> run(c, "list", "")))
            .then(Commands.literal("kill").requires(s -> s.hasPermission(2)).executes(c -> run(c, "kill", "")))
            .then(Commands.literal("remove").requires(s -> s.hasPermission(2)).executes(c -> run(c, "remove", ""))));
    }
}
