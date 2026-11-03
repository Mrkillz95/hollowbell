package net.jj.giants.tools;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (Part 6, ADMIN_BRIDGE.md §22.14) The server tools' commands, each the twin of a button on the book's Server tools
 * page, under /giants admin (operators): pause, quiet, event, schedule, area, dimension, respawn, stats, alerts, goto,
 * profile, pack, tools.
 */
public final class ToolsCommand {
    private ToolsCommand() {}

    private static final List<String> HELP = List.of(
            "/giants admin pause [on|off]: every giant stops where he is (or carries on).",
            "/giants admin quiet [on|off]: no giant chat for players, no chat lines for admins.",
            "/giants admin event [war|siege|bossrush [minutes]|stop]: an event that ends by itself.",
            "/giants admin schedule [add <preset> weekends|days <days>|fullmoon|date <MM-DD>|remove <n>|on <n>|off <n>|"
                    + "stop]: presets that go on by themselves.",
            "/giants admin area [add keepout|safe <radius> [name]|remove <n>]: areas round where you stand.",
            "/giants admin dimension <overworld|nether|end> <allowed|nogiants>: giants kept out of a dimension.",
            "/giants admin respawn [<giant> now|<wait like 30m, 2h, 3d>]: when each giant's own one comes back.",
            "/giants admin stats [reset], alerts [clear], goto <giant> <id>: what's happening.",
            "/giants admin profile [save|apply|delete], pack: this world's profile file, the modpack's preset.");

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("giants")
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(AdminCommand.OPS))
                        .then(Commands.literal("tools").executes(c -> said(c, AdminServer.Result.said(HELP))))
                        .then(onOff("pause", "paused"))
                        .then(onOff("quiet", "quiet"))
                        .then(Commands.literal("event")
                                .executes(c -> said(c, Events.status(server(c))))
                                .then(Commands.literal("stop").executes(c -> said(c, Events.stop(server(c), who(c)))))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Events.KINDS.keySet(), b))
                                        .executes(c -> event(c, -1))
                                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                                                .executes(c -> event(c, IntegerArgumentType.getInteger(c,
                                                        "minutes"))))))
                        .then(Commands.literal("schedule")
                                .executes(c -> said(c, Schedules.list(server(c))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("preset", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        AdminTransfer.presets(), b))
                                                .then(Commands.argument("when", StringArgumentType.greedyString())
                                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
                                                                "weekends", "days mon,fri", "fullmoon", "date 12-25"),
                                                                b))
                                                        .executes(ToolsCommand::scheduleAdd))))
                                .then(Commands.literal("remove").then(number("n").executes(c -> said(c,
                                        Schedules.remove(server(c), who(c), word(c, "n"))))))
                                .then(Commands.literal("on").then(number("n").executes(c -> said(c,
                                        Schedules.turn(server(c), who(c), word(c, "n"), true)))))
                                .then(Commands.literal("off").then(number("n").executes(c -> said(c,
                                        Schedules.turn(server(c), who(c), word(c, "n"), false)))))
                                .then(Commands.literal("stop").executes(c -> said(c,
                                        Schedules.stopNow(server(c), who(c))))))
                        .then(Commands.literal("area")
                                .executes(c -> said(c, Areas.list(server(c))))
                                .then(Commands.literal("add")
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
                                                        Areas.KEEP_OUT, Areas.SAFE), b))
                                                .then(Commands.argument("radius", IntegerArgumentType.integer(
                                                                Areas.LEAST_R, Areas.MOST_R))
                                                        .executes(c -> areaAdd(c, ""))
                                                        .then(Commands.argument("name",
                                                                        StringArgumentType.greedyString())
                                                                .executes(c -> areaAdd(c, StringArgumentType.getString(
                                                                        c, "name")))))))
                                .then(Commands.literal("remove").then(number("n").executes(c -> said(c,
                                        Areas.remove(server(c), who(c), word(c, "n")))))))
                        .then(Commands.literal("dimension")
                                .then(Commands.argument("dim", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("overworld",
                                                "nether", "end"), b))
                                        .then(Commands.argument("rule", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of(
                                                        "allowed", "nogiants"), b))
                                                .executes(ToolsCommand::dimension))))
                        .then(Commands.literal("respawn")
                                .executes(c -> said(c, Respawns.lines(server(c))))
                                .then(Commands.argument("giant", StringArgumentType.word())
                                        .suggests((c, b) -> {
                                            List<String> out = new ArrayList<>();
                                            for (Giant g : AdminServer.giants()) out.add(g.key());
                                            return SharedSuggestionProvider.suggest(out, b);
                                        })
                                        .then(Commands.argument("when", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("now",
                                                        "30m", "2h", "3d"), b))
                                                .executes(ToolsCommand::respawn))))
                        .then(Commands.literal("stats")
                                .executes(c -> said(c, Stats.lines(server(c))))
                                .then(Commands.literal("reset").executes(c -> said(c, Stats.reset(server(c),
                                        who(c))))))
                        .then(Commands.literal("alerts")
                                .executes(c -> said(c, Alerts.lines(server(c))))
                                .then(Commands.literal("clear").executes(c -> said(c, Alerts.clear(server(c),
                                        who(c))))))
                        .then(Commands.literal("goto")
                                .then(Commands.argument("giant", StringArgumentType.word())
                                        .then(Commands.argument("id", StringArgumentType.word())
                                                .executes(ToolsCommand::goTo))))
                        .then(Commands.literal("profile")
                                .executes(c -> said(c, Profiles.status(server(c))))
                                .then(Commands.literal("save").executes(c -> said(c, Profiles.save(server(c),
                                        who(c)))))
                                .then(Commands.literal("apply").executes(c -> said(c, Profiles.applyNow(server(c),
                                        who(c)))))
                                .then(Commands.literal("delete").executes(c -> said(c, Profiles.delete(server(c),
                                        who(c))))))
                        .then(Commands.literal("pack").executes(c -> said(c, PackMode.status())))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> number(String n) {
        return Commands.argument(n, StringArgumentType.word());
    }

    /** pause / quiet: the setting, as /giants admin set all <id> on|off */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> onOff(String word,
            String id) {
        return Commands.literal(word)
                .executes(c -> said(c, AdminServer.get(server(c), "all", id)))
                .then(Commands.literal("on").executes(c -> said(c, AdminServer.set(server(c), who(c), "all", id,
                        "on"))))
                .then(Commands.literal("off").executes(c -> said(c, AdminServer.set(server(c), who(c), "all", id,
                        "off"))));
    }

    private static int event(CommandContext<CommandSourceStack> c, int minutes) {
        String kind = word(c, "kind").toLowerCase(java.util.Locale.ROOT);
        Events.Kind k = Events.KINDS.get(kind);
        int m = minutes > 0 ? minutes : k != null ? k.minutes() : 30;
        return said(c, Events.start(server(c), who(c), kind, m));
    }

    private static int scheduleAdd(CommandContext<CommandSourceStack> c) {
        String when = StringArgumentType.getString(c, "when").strip();
        int sp = when.indexOf(' ');
        String kind = sp < 0 ? when : when.substring(0, sp), rest = sp < 0 ? "" : when.substring(sp + 1);
        return said(c, Schedules.add(server(c), who(c), word(c, "preset"), kind, rest));
    }

    private static int areaAdd(CommandContext<CommandSourceStack> c, String name) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return said(c, AdminServer.Result.fail("An area is drawn round where you stand: in the game."));
        return said(c, Areas.add(server(c), p, word(c, "kind"), IntegerArgumentType.getInteger(c, "radius"), name));
    }

    private static int dimension(CommandContext<CommandSourceStack> c) {
        String id = switch (word(c, "dim").toLowerCase(java.util.Locale.ROOT)) {
            case "overworld", "minecraft:overworld" -> "dimOverworld";
            case "nether", "the_nether", "minecraft:the_nether" -> "dimNether";
            case "end", "the_end", "minecraft:the_end" -> "dimEnd";
            default -> null;
        };
        if (id == null) return said(c, AdminServer.Result.fail("The dimension is overworld, nether or end."));
        return said(c, AdminServer.set(server(c), who(c), "all", id, word(c, "rule")));
    }

    private static int respawn(CommandContext<CommandSourceStack> c) {
        long t;
        try {
            t = Respawns.ticks(word(c, "when"));
        } catch (IllegalArgumentException e) {
            return said(c, AdminServer.Result.fail("When: now, or a wait like 30m, 2h or 3d (game days)."));
        }
        return said(c, Respawns.act(server(c), who(c), word(c, "giant"), t));
    }

    private static int goTo(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) return said(c, AdminServer.Result.fail("Only a player can be taken there."));
        return said(c, AdminLive.act(server(c), p, word(c, "giant"), word(c, "id"), "tp", ""));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() != null ? c.getSource().getPlayer().getGameProfile().getName() : "Server";
    }

    private static String word(CommandContext<CommandSourceStack> c, String n) {
        return StringArgumentType.getString(c, n);
    }

    private static int said(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        CommandSourceStack src = c.getSource();
        for (String s : r.lines()) {
            if (r.ok()) src.sendSuccess(() -> Component.literal(s), false);
            else src.sendFailure(Component.literal(s));
        }
        if (!r.announce().isEmpty()) AdminServer.announce(src.getServer(), src.getPlayer(), r.announce());
        return r.ok() ? 1 : 0;
    }
}
