package net.jj.giants.minigames;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.10) /giants minigame (what's on, the modes, the last results; for admins and
 * moderators also start, stop and now) and, for every player, /giants join, /giants spectate and /giants leave.
 */
public final class MgCommand {
    private MgCommand() {}

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Server";
    }

    private static boolean staff(CommandSourceStack s) {
        if (s.hasPermission(AdminCommand.OPS)) return true;
        return s.getEntity() instanceof ServerPlayer p && Minigames.staff(p);
    }

    private static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String l : r.lines()) {
            Component line = Component.literal(l);
            if (r.ok()) c.getSource().sendSuccess(() -> line, false);
            else c.getSource().sendFailure(line);
        }
        if (!r.announce().isEmpty()) AdminServer.announce(c.getSource().getServer(),
                c.getSource().getEntity() instanceof ServerPlayer p ? p : null, r.announce());
        return r.ok() ? 1 : 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var mode = Commands.argument("mode", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(Minigames.MODES.keySet(), b));
        d.register(Commands.literal("giants")
                .then(Commands.literal("minigame")
                        .executes(c -> say(c, status(c.getSource().getServer(), staff(c.getSource()))))
                        .then(Commands.literal("modes").executes(c -> say(c, modes())))
                        .then(Commands.literal("results").executes(c -> say(c, results(c.getSource().getServer()))))
                        .then(Commands.literal("start").requires(MgCommand::staff)
                                .then(mode.executes(c -> start(c, "open", 0))
                                        .then(Commands.argument("who", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("open", "everyone"), b))
                                                .executes(c -> start(c, StringArgumentType.getString(c, "who"), 0))
                                                .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 5))
                                                        .executes(c -> start(c, StringArgumentType.getString(c, "who"),
                                                                IntegerArgumentType.getInteger(c, "rounds")))))))
                        .then(Commands.literal("stop").requires(MgCommand::staff)
                                .executes(c -> say(c, Minigames.stop(c.getSource().getServer(), who(c)))))
                        .then(Commands.literal("now").requires(MgCommand::staff)
                                .executes(c -> say(c, Minigames.now(c.getSource().getServer(), who(c))))))
                .then(Commands.literal("join")
                        .executes(c -> say(c, Minigames.join(c.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("spectate")
                        .executes(c -> say(c, Minigames.join(c.getSource().getPlayerOrException(), true))))
                .then(Commands.literal("leave")
                        .executes(c -> say(c, Minigames.leave(c.getSource().getPlayerOrException())))));
    }

    private static int start(CommandContext<CommandSourceStack> c, String who, int rounds) {
        String w = who.toLowerCase(Locale.ROOT);
        if (!w.equals("open") && !w.equals("everyone") && !w.equals("force"))
            return say(c, AdminServer.Result.fail("Say open (players choose) or everyone (everyone online is taken)."));
        return say(c, Minigames.start(c.getSource().getServer(), who(c), StringArgumentType.getString(c, "mode"),
                !w.equals("open"), rounds));
    }

    static AdminServer.Result status(MinecraftServer server, boolean staff) {
        List<String> l = new ArrayList<>();
        MgGame g = Minigames.current;
        if (g == null) l.add("No minigame is on." + (staff ? " /giants minigame start <mode> [open|everyone]"
                + " [rounds] opens one." : ""));
        else {
            l.add(g.mode.name() + " (" + (g.force ? "everyone online" : "open") + ", by " + g.by + "): "
                    + switch (g.phase) {
                        case LOBBY -> "starts in " + g.lobbyLeft / 20 + " s";
                        case READY -> "round " + g.round + " is about to start";
                        case PLAY -> "round " + g.round + " of " + g.rounds + ", " + g.clock() + " left";
                        case ROUND_END, NEXT -> "between rounds";
                        default -> "ending";
                    } + ".");
            List<String> in = new ArrayList<>(), watch = new ArrayList<>();
            for (MgGame.P p : g.people.values()) {
                if (p.left) continue;
                if (p.watcher) watch.add(p.name);
                else in.add(p.name + (g.phase == MgGame.Phase.LOBBY ? "" : p.alive ? "" : " (out)"));
            }
            l.add("Playing (" + in.size() + "): " + (in.isEmpty() ? "nobody yet" : String.join(", ", in)) + ".");
            if (!watch.isEmpty()) l.add("Watching: " + String.join(", ", watch) + ".");
            if (g.phase != MgGame.Phase.LOBBY) l.add("Giants standing: " + g.giants.standing().size() + ".");
        }
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result modes() {
        List<String> l = new ArrayList<>(List.of("Minigames:"));
        for (MgMode m : Minigames.MODES.values()) l.add("  " + m.id() + ": " + m.name() + ". " + m.about());
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result results(MinecraftServer server) {
        MgData d = MgData.get(server);
        List<String> l = new ArrayList<>();
        if (d.s.results.isEmpty()) return AdminServer.Result.said(List.of("No minigame has been played here yet."));
        l.add("Last minigames (newest first):");
        SimpleDateFormat f = new SimpleDateFormat("MMM d HH:mm", Locale.ENGLISH);
        for (int i = d.s.results.size() - 1, n = 0; i >= 0 && n < 5; i--, n++) {
            MgData.Result r = d.s.results.get(i);
            l.add("  " + f.format(new Date(r.time)) + " " + r.line);
        }
        return AdminServer.Result.said(l);
    }
}
