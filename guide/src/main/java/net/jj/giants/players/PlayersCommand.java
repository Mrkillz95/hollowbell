package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Part 5's commands under /giants (ADMIN_BRIDGE.md 20.8): for operators "players" (rules, re-pick, stats, signs) and
 * "vote open|close|cancel"; for everyone "pick", "vote", "rules" and "top".
 */
public final class PlayersCommand {
    private PlayersCommand() {}

    public static void registerAll() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Server";
    }

    private static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String l : r.lines()) {
            Component line = Component.literal(l);
            if (r.ok()) c.getSource().sendSuccess(() -> line, false);
            else c.getSource().sendFailure(line);
        }
        if (!r.announce().isEmpty()) AdminServer.announce(server(c),
                c.getSource().getEntity() instanceof ServerPlayer p ? p : null, r.announce());
        return r.ok() ? 1 : 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var rule = Commands.argument("rule", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("own", "ignored", "nokit"), b));
        d.register(Commands.literal("giants")
                .then(Commands.literal("players")
                        .requires(s -> s.hasPermission(AdminCommand.OPS))
                        .executes(c -> say(c, listRules(server(c))))
                        .then(Commands.literal("rule")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                c.getSource().getOnlinePlayerNames(), b))
                                        .then(rule.then(Commands.argument("on", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("on", "off"), b))
                                                .executes(c -> {
                                                    String on = StringArgumentType.getString(c, "on");
                                                    if (!on.equals("on") && !on.equals("off"))
                                                        return say(c, AdminServer.Result.fail(
                                                                "It has to be on or off."));
                                                    return say(c, Players.setRule(server(c), who(c),
                                                            StringArgumentType.getString(c, "player"),
                                                            StringArgumentType.getString(c, "rule"),
                                                            on.equals("on")));
                                                })))))
                        .then(Commands.literal("repick")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                c.getSource().getOnlinePlayerNames(), b))
                                        .executes(c -> say(c, Players.repick(server(c), who(c),
                                                StringArgumentType.getString(c, "player"))))))
                        .then(Commands.literal("stats")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(c -> say(c, stats(server(c), StringArgumentType.getString(c,
                                                "player"), false, who(c))))
                                        .then(Commands.literal("reset").executes(c -> say(c, stats(server(c),
                                                StringArgumentType.getString(c, "player"), true, who(c)))))))
                        .then(Commands.literal("boards").executes(c -> say(c, Boards.list(server(c)))))
                        .then(Commands.literal("board")
                                .then(Commands.literal("set")
                                        .then(Commands.argument("kind", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Boards.KINDS,
                                                        b))
                                                .then(Commands.argument("pos",
                                                                net.minecraft.commands.arguments.coordinates
                                                                        .BlockPosArgument.blockPos())
                                                        .executes(c -> say(c, Boards.setSign(server(c),
                                                                c.getSource().getLevel(),
                                                                net.minecraft.commands.arguments.coordinates
                                                                        .BlockPosArgument.getLoadedBlockPos(c, "pos"),
                                                                StringArgumentType.getString(c, "kind")))))
                                                .executes(c -> {
                                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                                    var pos = Boards.lookedAtSign(p);
                                                    if (pos == null) return say(c, AdminServer.Result.fail(
                                                            "Look at a sign (6 blocks) to make it a leaderboard."));
                                                    return say(c, Boards.setSign(server(c), p.serverLevel(), pos,
                                                            StringArgumentType.getString(c, "kind")));
                                                })))
                                .then(Commands.literal("remove").executes(c -> {
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    var pos = Boards.lookedAtSign(p);
                                    if (pos == null) return say(c, AdminServer.Result.fail(
                                            "Look at the leaderboard sign (6 blocks)."));
                                    return say(c, Boards.removeSign(server(c),
                                            p.level().dimension().location().toString(), pos));
                                }))))
                .then(Commands.literal("pick")
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if (!Players.on(server(c), "myGiant"))
                                return say(c, AdminServer.Result.fail("My Giant isn't on in this world."));
                            Players.offerPick(p);
                            return 1;
                        })
                        .then(Commands.argument("giant", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    List<String> out = new ArrayList<>();
                                    for (Giant g : Players.pickable(server(c))) out.add(g.key());
                                    return SharedSuggestionProvider.suggest(out, b);
                                })
                                .executes(c -> say(c, Players.pick(server(c), c.getSource().getPlayerOrException(),
                                        StringArgumentType.getString(c, "giant"))))))
                .then(Commands.literal("vote")
                        .executes(c -> say(c, voteStatus(server(c), c.getSource().getPlayer())))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, Votes.MOST_OPTIONS))
                                .executes(c -> say(c, Votes.vote(server(c), c.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("open")
                                .requires(s -> s.hasPermission(AdminCommand.OPS))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, Votes.MOST_MINUTES))
                                        .then(Commands.argument("apply", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        List.of("auto", "ask"), b))
                                                .then(Commands.argument("presets", StringArgumentType.greedyString())
                                                        .executes(c -> {
                                                            String a = StringArgumentType.getString(c, "apply");
                                                            if (!a.equals("auto") && !a.equals("ask"))
                                                                return say(c, AdminServer.Result.fail(
                                                                        "Say auto (the winner is applied by itself)"
                                                                                + " or ask (you apply it)."));
                                                            return say(c, Votes.open(server(c), who(c),
                                                                    Arrays.asList(StringArgumentType.getString(c,
                                                                            "presets").split(",")),
                                                                    IntegerArgumentType.getInteger(c, "minutes"),
                                                                    a.equals("auto")));
                                                        })))))
                        .then(Commands.literal("close")
                                .requires(s -> s.hasPermission(AdminCommand.OPS))
                                .executes(c -> say(c, Votes.end(server(c), who(c), true))))
                        .then(Commands.literal("cancel")
                                .requires(s -> s.hasPermission(AdminCommand.OPS))
                                .executes(c -> say(c, Votes.end(server(c), who(c), false)))))
                .then(Commands.literal("rules").executes(c -> say(c, rules(server(c)))))
                .then(Commands.literal("top").executes(c -> say(c, top(server(c), c.getSource().getPlayer())))));
    }

    static AdminServer.Result listRules(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        List<String> lines = new ArrayList<>();
        for (var e : d.s.rules.entrySet()) {
            PlayerData.Rule r = e.getValue();
            List<String> w = new ArrayList<>();
            if (!r.own) w.add("may not own giants");
            if (r.ignored) w.add("ignored by giants");
            if (r.noKit) w.add("no starting kit");
            lines.add("  " + r.name + ": " + String.join(", ", w));
        }
        if (lines.isEmpty()) return AdminServer.Result.said(List.of("No player has a rule in this world."));
        lines.add(0, "Player rules:");
        return AdminServer.Result.said(lines);
    }

    static AdminServer.Result stats(MinecraftServer server, String player, boolean reset, String who) {
        Players.Who w = Players.who(server, player);
        if (w == null) return AdminServer.Result.fail("No player called " + player + " has been on this server.");
        PlayerData d = PlayerData.get(server);
        PlayerData.Stats st = d.s.stats.get(w.id());
        if (reset) {
            if (st == null) return AdminServer.Result.said(List.of(w.name() + " has nothing on the leaderboards."));
            st.kills.clear();
            st.owned.clear();
            st.streak = st.longest = 0;
            st.last = -1;
            d.setDirty();
            net.jj.giants.admin.AdminBook.changed("players");
            return new AdminServer.Result(true, List.of(w.name() + "'s leaderboard numbers are back to nothing."),
                    List.of(who + " reset " + w.name() + "'s leaderboard numbers."));
        }
        if (st == null) return AdminServer.Result.said(List.of(w.name() + " has nothing on the leaderboards."));
        return AdminServer.Result.said(List.of(w.name() + ": " + st.killsAll() + " giants slain " + st.kills
                + ", longest owning " + Boards.time(Math.max(st.longest, st.streak)) + ", " + st.owned.size()
                + " befriended."));
    }

    static AdminServer.Result voteStatus(MinecraftServer server, ServerPlayer p) {
        JsonObject v = Votes.json(server, p);
        if (!v.get("on").getAsBoolean()) return AdminServer.Result.said(List.of("Votes are turned off in this world."));
        if (!v.has("open"))
            return AdminServer.Result.said(List.of(v.has("last") ? "No vote open. Last: " + v.get("last")
                    .getAsString() : "No vote open."));
        PlayerData.Vote vote = PlayerData.get(server).s.vote;
        if (p != null && vote != null) Votes.announce(p, vote);
        JsonObject o = v.getAsJsonObject("open");
        List<String> lines = new ArrayList<>();
        int i = 1;
        for (JsonElement e : o.getAsJsonArray("options"))
            lines.add("  " + i++ + " " + e.getAsJsonObject().get("name").getAsString() + ": "
                    + e.getAsJsonObject().get("votes").getAsInt());
        lines.add(0, "Vote open, " + (o.get("secondsLeft").getAsLong() / 60 + 1) + " min left:");
        return AdminServer.Result.said(lines);
    }

    static AdminServer.Result rules(MinecraftServer server) {
        JsonObject r = RulesPage.json(server);
        if (r.has("hidden")) return AdminServer.Result.said(List.of(r.get("hidden").getAsString()));
        List<String> lines = new ArrayList<>(List.of("This world: " + r.get("preset").getAsString() + ". "
                + r.get("about").getAsString()));
        if (r.has("danger")) {
            JsonObject d = r.getAsJsonObject("danger");
            lines.add("Danger " + d.get("level").getAsInt() + " " + d.get("label").getAsString() + ": "
                    + d.get("reason").getAsString());
        }
        if (r.has("rules")) for (JsonElement e : r.getAsJsonArray("rules")) lines.add("  " + e.getAsString());
        if (r.has("players")) for (JsonElement e : r.getAsJsonArray("players")) lines.add("  " + e.getAsString());
        return AdminServer.Result.said(lines);
    }

    static AdminServer.Result top(MinecraftServer server, ServerPlayer p) {
        JsonObject b = Boards.json(server, p == null ? null : p.getStringUUID(), 5);
        if (!b.get("on").getAsBoolean()) return AdminServer.Result.said(List.of("Leaderboards are turned off in this"
                + " world."));
        List<String> lines = new ArrayList<>();
        for (JsonElement e : b.getAsJsonArray("boards")) {
            JsonObject o = e.getAsJsonObject();
            lines.add(o.get("title").getAsString() + ":");
            JsonArray rows = o.getAsJsonArray("rows");
            if (rows.isEmpty()) lines.add("  nobody yet");
            for (JsonElement r : rows) {
                JsonObject x = r.getAsJsonObject();
                lines.add("  " + x.get("place").getAsInt() + " " + x.get("name").getAsString() + " "
                        + x.get("text").getAsString());
            }
        }
        return AdminServer.Result.said(lines);
    }

    static Component gold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }
}
