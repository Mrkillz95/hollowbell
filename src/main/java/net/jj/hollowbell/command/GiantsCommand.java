package net.jj.hollowbell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * /giants: one command for all of JJ's bosses at once. Every one of the five mods carries this class, but only
 * one registers it: the first of the five bridges (in this fixed, sorted order) that is actually loaded. The
 * command then asks every loaded bridge in turn, and one broken mod can't stop the rest from answering.
 */
public final class GiantsCommand {
    private GiantsCommand() {}

    /** the five bridges, sorted; the same list in every mod */
    public static final String[] BRIDGES = {
            "net.jj.cerberus.GiantsBridge",
            "net.jj.furrowmaw.GiantsBridge",
            "net.jj.hollowbell.GiantsBridge",
            "net.jj.lanternwillow.GiantsBridge",
            "net.jj.mountain.GiantsBridge"};

    private static final String MINE = net.jj.hollowbell.GiantsBridge.class.getName();

    /** true if this mod is the one that registers /giants */
    public static boolean elected() {
        for (String b : BRIDGES) {
            if (find(b) != null) return b.equals(MINE);
        }
        return false;
    }

    private static Class<?> find(String name) {
        try { return Class.forName(name, false, GiantsCommand.class.getClassLoader()); }
        catch (Throwable t) { return null; }
    }

    /** every loaded bridge's answer, in order */
    @SuppressWarnings("unchecked")
    public static List<String> ask(net.minecraft.server.MinecraftServer server, String action, String arg) {
        List<String> out = new ArrayList<>();
        for (String b : BRIDGES) {
            Class<?> c = find(b);
            if (c == null) continue;
            try {
                Object r = c.getMethod("giants", net.minecraft.server.MinecraftServer.class, String.class, String.class).invoke(null, server, action, arg);
                if (r instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
            } catch (Throwable t) {
                String name = b.substring("net.jj.".length(), b.lastIndexOf('.'));
                out.add(name + ": didn't answer (" + t.getClass().getSimpleName() + ").");
            }
        }
        return out;
    }

    private static int run(CommandContext<CommandSourceStack> c, String action, String arg) {
        List<String> lines = ask(c.getSource().getServer(), action, arg);
        if (lines.isEmpty()) lines = List.of("No bosses answered.");
        boolean changes = !arg.isEmpty() || action.equals("kill") || action.equals("remove");
        for (String s : lines) c.getSource().sendSuccess(() -> Component.literal(s), changes);
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
                    if (!c.getSource().hasPermission(2)) { c.getSource().sendFailure(Component.literal("You need cheats on for that. /giants where works without.")); return 0; }
                    return run(c, "status", "");
                })
                .then(toggle("natural"))
                .then(Commands.literal("limit").requires(s -> s.hasPermission(2)).executes(c -> run(c, "limit", ""))
                        .then(Commands.argument("how many", IntegerArgumentType.integer(0, 20))
                                .executes(c -> run(c, "limit", String.valueOf(IntegerArgumentType.getInteger(c, "how many"))))))
                .then(toggle("fight"))
                .then(toggle("away"))
                .then(Commands.literal("volume").requires(s -> s.hasPermission(2)).executes(c -> run(c, "volume", ""))
                        .then(Commands.argument("x", FloatArgumentType.floatArg(0f, 2f))
                                .executes(c -> run(c, "volume", String.valueOf(FloatArgumentType.getFloat(c, "x"))))))
                .then(toggle("shake"))
                .then(Commands.literal("bossbar").requires(s -> s.hasPermission(2)).executes(c -> run(c, "bossbar", ""))
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                                .executes(c -> run(c, "bossbar", String.valueOf(IntegerArgumentType.getInteger(c, "blocks"))))))
                .then(toggle("griefing"))
                .then(Commands.literal("where").executes(c -> run(c, "where", "")))
                .then(Commands.literal("list").requires(s -> s.hasPermission(2)).executes(c -> run(c, "list", "")))
                .then(Commands.literal("kill").requires(s -> s.hasPermission(2)).executes(c -> run(c, "kill", "")))
                .then(Commands.literal("remove").requires(s -> s.hasPermission(2)).executes(c -> run(c, "remove", ""))));
    }
}
