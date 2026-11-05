package net.jj.giants.fights;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fights.FightData.Setup;
import net.jj.giants.fun.FunGiants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 10B) The commands: the fight script under /giants admin fight (operators; the book's twins), and the players'
 * own under /giants fight (bets, joining a side, Stand down, saying yes to owned giants fighting, the boards).
 */
public final class FightsCommand {
    private FightsCommand() {}

    private static final SuggestionProvider<CommandSourceStack> OPTION =
            (c, b) -> SharedSuggestionProvider.suggest(FightEdit.OPTIONS, b);
    private static final SuggestionProvider<CommandSourceStack> VALUE = (c, b) -> SharedSuggestionProvider.suggest(
            FightEdit.values(StringArgumentType.getString(c, "option")), b);
    private static final SuggestionProvider<CommandSourceStack> SAVED = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (Setup s : FightData.get(c.getSource().getServer()).s.saved.values())
            out.add(s.name.contains(" ") ? "\"" + s.name + "\"" : s.name);
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> GIANTS = (c, b) -> {
        List<String> out = new ArrayList<>(List.of("none"));
        for (Giant g : AdminServer.giants()) out.add(g.key());
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> SIDES =
            (c, b) -> SharedSuggestionProvider.suggest(List.of("a", "b"), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> fight = Commands.literal("fight");
        fight.then(Commands.literal("start").executes(c -> say(c, FightEdit.start(server(c), player(c), false)))
                .then(Commands.literal("here").executes(c -> say(c, FightEdit.start(server(c), player(c), false))))
                .then(Commands.literal("look").executes(c -> say(c, FightEdit.start(server(c), player(c), true)))));
        fight.then(Commands.literal("stop").executes(c -> say(c, FightScript.stop(server(c), who(c)))));
        fight.then(Commands.literal("status").executes(FightsCommand::status));
        fight.then(Commands.literal("script").executes(c -> say(c, AdminServer.Result.said(FightEdit.describe(
                server(c), draft(c))))));
        fight.then(Commands.literal("reset").executes(c -> {
            FightData.get(server(c)).s.drafts.put(player(c).getStringUUID(), new Setup());
            FightData.get(server(c)).setDirty();
            Fights.changed();
            return say(c, AdminServer.Result.said(List.of("A fresh fight script.")));
        }));
        fight.then(Commands.literal("set").then(Commands.argument("option", StringArgumentType.word()).suggests(OPTION)
                .then(Commands.argument("value", StringArgumentType.word()).suggests(VALUE).executes(c -> edit(c,
                        FightEdit.set(draft(c), StringArgumentType.getString(c, "option"),
                                StringArgumentType.getString(c, "value")))))));
        fight.then(Commands.literal("giant").then(side().then(slot()
                .then(Commands.argument("who", StringArgumentType.word()).suggests(GIANTS)
                        .executes(c -> edit(c, FightEdit.giant(server(c), draft(c), sideOf(c), slotOf(c),
                                StringArgumentType.getString(c, "who"), 0.5, player(c))))
                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.01, 4))
                                .executes(c -> edit(c, FightEdit.giant(server(c), draft(c), sideOf(c), slotOf(c),
                                        StringArgumentType.getString(c, "who"), DoubleArgumentType.getDouble(c,
                                                "size"), player(c)))))))));
        fight.then(Commands.literal("moves").then(side().then(slot()
                .then(Commands.argument("moves", StringArgumentType.greedyString())
                        .executes(c -> edit(c, FightEdit.moves(server(c), draft(c), sideOf(c), slotOf(c),
                                StringArgumentType.getString(c, "moves"))))))));
        fight.then(Commands.literal("finisher").then(side().then(slot()
                .then(Commands.argument("move", StringArgumentType.word())
                        .executes(c -> edit(c, FightEdit.finisher(server(c), draft(c), sideOf(c), slotOf(c),
                                StringArgumentType.getString(c, "move"))))))));
        fight.then(Commands.literal("health").then(side().then(slot()
                .then(Commands.argument("share", DoubleArgumentType.doubleArg(0.05, 100))
                        .executes(c -> edit(c, FightEdit.health(draft(c), sideOf(c), slotOf(c),
                                DoubleArgumentType.getDouble(c, "share"))))))));
        fight.then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.greedyString())
                .executes(c -> say(c, FightEdit.save(server(c), player(c), StringArgumentType.getString(c, "name"))))));
        fight.then(Commands.literal("load").then(Commands.argument("name", StringArgumentType.greedyString())
                .suggests(SAVED)
                .executes(c -> say(c, FightEdit.load(server(c), player(c), unquote(c, "name"))))));
        fight.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.greedyString())
                .suggests(SAVED)
                .executes(c -> say(c, FightEdit.delete(server(c), who(c), unquote(c, "name"))))));
        fight.then(Commands.literal("replay").then(Commands.argument("name", StringArgumentType.greedyString())
                .suggests(SAVED)
                .executes(c -> say(c, FightScript.replay(server(c), who(c), c.getSource().getEntity() == null
                        ? new UUID(0, 0) : c.getSource().getEntity().getUUID(), unquote(c, "name"))))));
        fight.then(Commands.literal("list").executes(FightsCommand::list));
        fight.then(Commands.literal("standdown").then(Commands.argument("giant", StringArgumentType.word())
                .executes(c -> {
                    FunGiants.Found f = FunGiants.which(server(c), StringArgumentType.getString(c, "giant")
                            .replace('.', ' '), c.getSource().getLevel(), c.getSource().getPosition());
                    if (f.giant() == null) return fail(c, f.why());
                    FightWatch.standDown(server(c), f.giant());
                    return say(c, AdminServer.Result.said(List.of(FightGiants.called(f.giant(), true)
                            + " stands down.")));
                })));
        d.register(Commands.literal("giants").then(Commands.literal("admin").requires(s -> s.hasPermission(
                AdminCommand.OPS)).then(fight)));

        // the players' own
        LiteralArgumentBuilder<CommandSourceStack> p = Commands.literal("fight");
        p.then(Commands.literal("bet").then(Commands.argument("side", StringArgumentType.word()).suggests(SIDES)
                .executes(c -> say(c, FightScript.bet(server(c), player(c), sideOf(c, "side"), 0)))
                .then(Commands.argument("emeralds", IntegerArgumentType.integer(0, 64))
                        .executes(c -> say(c, FightScript.bet(server(c), player(c), sideOf(c, "side"),
                                IntegerArgumentType.getInteger(c, "emeralds")))))));
        p.then(Commands.literal("join").then(Commands.argument("side", StringArgumentType.word()).suggests(SIDES)
                .executes(c -> say(c, FightScript.join(player(c), sideOf(c, "side"))))));
        p.then(Commands.literal("leave").executes(c -> say(c, FightScript.join(player(c), "leave"))));
        p.then(Commands.literal("standdown").executes(c -> standDown(c, null))
                .then(Commands.argument("giant", StringArgumentType.word()).executes(c -> standDown(c,
                        StringArgumentType.getString(c, "giant")))));
        p.then(Commands.literal("allow").executes(c -> allow(c, null))
                .then(Commands.literal("on").executes(c -> allow(c, true)))
                .then(Commands.literal("off").executes(c -> allow(c, false))));
        p.then(Commands.literal("top").executes(FightsCommand::top));
        d.register(Commands.literal("giants").then(p));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> side() {
        return Commands.argument("side", StringArgumentType.word()).suggests(SIDES);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> slot() {
        return Commands.argument("slot", IntegerArgumentType.integer(1, 2));
    }

    private static String sideOf(CommandContext<CommandSourceStack> c) {
        return sideOf(c, "side");
    }

    private static String sideOf(CommandContext<CommandSourceStack> c, String arg) {
        return StringArgumentType.getString(c, arg).toLowerCase(java.util.Locale.ROOT);
    }

    private static int slotOf(CommandContext<CommandSourceStack> c) {
        return IntegerArgumentType.getInteger(c, "slot");
    }

    private static String unquote(CommandContext<CommandSourceStack> c, String arg) {
        String s = StringArgumentType.getString(c, arg).trim();
        return s.length() > 1 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Server";
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    private static Setup draft(CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return FightData.get(server(c)).draft(player(c).getStringUUID());
    }

    private static int edit(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        FightData.get(server(c)).setDirty();
        Fights.changed();
        return say(c, r);
    }

    static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String s : r.lines())
            if (r.ok()) c.getSource().sendSuccess(() -> Component.literal(s), false);
            else c.getSource().sendFailure(Component.literal(s));
        if (!r.announce().isEmpty())
            AdminServer.announce(server(c), c.getSource().getEntity() instanceof ServerPlayer p ? p : null,
                    r.announce());
        return r.ok() ? 1 : 0;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String why) {
        c.getSource().sendFailure(Component.literal(why));
        return 0;
    }

    private static int status(CommandContext<CommandSourceStack> c) {
        List<String> out = new ArrayList<>();
        var now = FightScript.state(server(c));
        if (now != null) {
            out.add("A staged fight (" + now.get("phase").getAsString() + ", round " + now.get("round").getAsInt()
                    + " of " + now.get("rounds").getAsInt() + ", " + now.get("winsA").getAsInt() + " to "
                    + now.get("winsB").getAsInt() + "):");
            for (var e : now.getAsJsonArray("giants")) {
                var j = e.getAsJsonObject();
                out.add("  " + j.get("side").getAsString().toUpperCase(java.util.Locale.ROOT) + ": "
                        + j.get("name").getAsString() + " " + Math.round(j.get("share").getAsDouble() * 100) + "%"
                        + (j.get("dead").getAsBoolean() ? " (down)" : j.get("broken").getAsBoolean() ? " (broke)"
                                : ""));
            }
        } else out.add("No staged fight is on.");
        List<String> going = FightWatch.describe(server(c));
        out.add(going.isEmpty() ? "No other giant fights followed now." : "Giant fights followed now: "
                + String.join("; ", going) + ".");
        return say(c, AdminServer.Result.said(out));
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<String> out = new ArrayList<>();
        for (Setup s : FightData.get(server(c)).s.saved.values())
            out.add("  " + s.name + ": " + FightScript.sideNames(s) + (s.dim.isEmpty() ? "" : " at " + (int) s.x + " "
                    + (int) s.y + " " + (int) s.z));
        if (out.isEmpty()) out.add("No fight setups saved. /giants admin fight save <name> saves yours.");
        else out.add(0, "Fight setups:");
        return say(c, AdminServer.Result.said(out));
    }

    /** an owner calls his giant off (the chat button names him; with none, every giant of theirs fighting now) */
    private static int standDown(CommandContext<CommandSourceStack> c, String id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(c);
        MinecraftServer s = server(c);
        int n = 0;
        for (LivingEntity e : FunGiants.standing(s, null)) {
            if (id != null && !e.getStringUUID().equals(id)) continue;
            if (!p.getStringUUID().equals(FightGiants.owner(s, e.getStringUUID()))) continue;
            if (FightScript.in(e.getUUID())) continue;
            FightWatch.standDown(s, e);
            n++;
            p.sendSystemMessage(Component.literal("Your " + FightGiants.name(e) + " stands down."));
        }
        if (n == 0) return fail(c, id == null ? "None of your giants is fighting." : "That isn't your giant, or he"
                + " isn't here.");
        return 1;
    }

    private static int allow(CommandContext<CommandSourceStack> c, Boolean on)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(c);
        FightData d = FightData.get(server(c));
        String id = p.getStringUUID();
        if (on == null) on = !d.s.allow.contains(id);
        d.s.allow.remove(id);
        if (on) d.s.allow.add(id);
        d.setDirty();
        return say(c, AdminServer.Result.said(List.of(on ? "Your giants may fight other owners' giants (when their"
                + " owners say yes too)." : "Your giants won't fight other owners' giants.")));
    }

    private static int top(CommandContext<CommandSourceStack> c) {
        FightData d = FightData.get(server(c));
        List<String> out = new ArrayList<>();
        out.add("Owners' giant wins:");
        board(out, d.s.wins, b -> b.wins, b -> b.wins + " win" + (b.wins == 1 ? "" : "s"));
        out.add("Best at betting:");
        board(out, d.s.bets, b -> b.right * 1000L + b.won, b -> b.right + " right, " + b.wrong + " wrong, "
                + b.won + " emerald" + (b.won == 1 ? "" : "s") + " won");
        return say(c, AdminServer.Result.said(out));
    }

    private static void board(List<String> out, Map<String, FightData.Board> m,
            java.util.function.ToLongFunction<FightData.Board> score,
            java.util.function.Function<FightData.Board, String> text) {
        List<FightData.Board> rows = new ArrayList<>(m.values());
        rows.removeIf(b -> score.applyAsLong(b) <= 0 && b.wrong <= 0);
        rows.sort(Comparator.comparingLong(score).reversed());
        if (rows.isEmpty()) out.add("  nobody yet");
        for (int i = 0; i < Math.min(10, rows.size()); i++)
            out.add("  " + (i + 1) + ". " + rows.get(i).name + ": " + text.apply(rows.get(i)));
    }
}
