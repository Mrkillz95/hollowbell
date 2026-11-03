package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Votes on the next preset (ADMIN_BRIDGE.md 20.11): one at a time, opened by an admin with 2 to 6 presets, a time in
 * minutes and whether the winner is applied by itself. Kept with the world; the time counts the overworld's ticks.
 */
public final class Votes {
    private Votes() {}

    public static final int MOST_OPTIONS = 6, MOST_MINUTES = 60;

    /** opens one: options are preset ids or names, built in or saved on this server */
    public static AdminServer.Result open(MinecraftServer server, String who, List<String> options, int minutes,
            boolean auto) {
        if (!Players.on(server, "votes")) return AdminServer.Result.fail("Votes are turned off in this world.");
        // (Part 6) a modpack that keeps its preset
        String pack = net.jj.giants.tools.PackMode.refusal();
        if (pack != null) return AdminServer.Result.fail(pack);
        PlayerData d = PlayerData.get(server);
        if (d.s.vote != null)
            return AdminServer.Result.fail("A vote is open already. /giants vote close ends it.");
        if (minutes < 1 || minutes > MOST_MINUTES)
            return AdminServer.Result.fail("A vote lasts 1 to " + MOST_MINUTES + " minutes.");
        PlayerData.Vote v = new PlayerData.Vote();
        for (String o : options) {
            String t = o.trim();
            if (t.isEmpty()) continue;
            String id = AdminTransfer.builtInId(t);
            AdminTransfer.Bundle b = AdminTransfer.preset(t);
            if (b == null) return AdminServer.Result.fail("There's no preset called " + t + ".");
            String key = id != null ? id : t;
            if (v.ids.contains(key)) continue;
            v.ids.add(key);
            v.names.add(b.name());
        }
        if (v.ids.size() < 2 || v.ids.size() > MOST_OPTIONS)
            return AdminServer.Result.fail("A vote needs 2 to " + MOST_OPTIONS + " different presets.");
        v.minutes = minutes;
        v.auto = auto;
        v.by = who;
        v.endsAt = server.overworld().getGameTime() + minutes * 1200L;
        d.s.vote = v;
        d.setDirty();
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, AdminCatalog.ALL,
                "players:vote", "Vote opened: " + String.join(", ", v.names), "", minutes + " min", ""));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) announce(p, v);
        AdminBook.changed("players");
        return new AdminServer.Result(true, List.of("Vote open for " + minutes + " minutes: "
                + String.join(", ", v.names) + (auto ? ". The winner is applied by itself." : ". You apply the winner.")),
                List.of());
    }

    /** the vote, told to one player with a link per option */
    static void announce(ServerPlayer p, PlayerData.Vote v) {
        MutableComponent m = Component.literal("Vote on the next preset (" + v.minutes + " min): ")
                .withStyle(ChatFormatting.GOLD);
        for (int i = 0; i < v.ids.size(); i++) {
            int n = i + 1;
            m.append(Component.literal("[" + n + " " + v.names.get(i) + "] ").withStyle(s -> s
                    .withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/giants vote " + n))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Vote for " + v.names.get(n - 1))))));
        }
        p.sendSystemMessage(m);
    }

    /** a player votes for option n (1 based); voting again changes it */
    public static AdminServer.Result vote(MinecraftServer server, ServerPlayer p, int n) {
        if (!Players.on(server, "votes")) return AdminServer.Result.fail("Votes are turned off in this world.");
        PlayerData d = PlayerData.get(server);
        PlayerData.Vote v = d.s.vote;
        if (v == null) return AdminServer.Result.fail("There's no vote open.");
        if (n < 1 || n > v.ids.size())
            return AdminServer.Result.fail("Pick a number from 1 to " + v.ids.size() + ".");
        Integer was = v.votes.put(p.getStringUUID(), n - 1);
        d.setDirty();
        AdminBook.changed("players");
        return AdminServer.Result.said(List.of((was == null ? "You voted for " : "You changed your vote to ")
                + v.names.get(n - 1) + "."));
    }

    /** ends the open vote: counted (close) or not (cancel) */
    public static AdminServer.Result end(MinecraftServer server, String who, boolean count) {
        PlayerData d = PlayerData.get(server);
        PlayerData.Vote v = d.s.vote;
        if (v == null) return AdminServer.Result.fail("There's no vote open.");
        d.s.vote = null;
        d.setDirty();
        AdminBook.changed("players");
        if (!count) {
            say(server, "The vote was called off.");
            return AdminServer.Result.said(List.of("Vote cancelled."));
        }
        return AdminServer.Result.said(List.of(finish(server, v)));
    }

    /** counts it, tells everyone, and applies the winner if it was meant to; the result line */
    static String finish(MinecraftServer server, PlayerData.Vote v) {
        int[] c = v.counts();
        int total = 0, best = -1;
        for (int i = 0; i < c.length; i++) {
            total += c[i];
            if (c[i] > 0 && (best < 0 || c[i] > c[best])) best = i;
        }
        PlayerData d = PlayerData.get(server);
        PlayerData.Result r = new PlayerData.Result();
        r.time = System.currentTimeMillis();
        if (best < 0) {
            r.text = "Vote: nobody voted, so nothing changes.";
            d.s.last = r;
            d.setDirty();
            say(server, r.text);
            return r.text;
        }
        boolean tie = false;
        for (int i = 0; i < c.length; i++) if (i != best && c[i] == c[best]) tie = true;
        r.winner = v.ids.get(best);
        r.text = "Vote: " + v.names.get(best) + " won with " + c[best] + " of " + total + " vote"
                + (total == 1 ? "" : "s") + (tie ? " (a tie goes to the one listed first)" : "") + ".";
        d.s.last = r;
        d.setDirty();
        say(server, r.text);
        if (v.auto) {
            AdminTransfer.Bundle b = AdminTransfer.preset(v.ids.get(best));
            if (b != null) {
                // (as any preset: planned, then applied as "Vote", a snapshot first)
                AdminTransfer.Plan pl = AdminTransfer.plan(server, b);
                int n = AdminTransfer.apply(server, "Vote", pl);
                AdminServer.announce(server, null, List.of("The vote applied the preset " + v.names.get(best) + " ("
                        + n + " changes)."));
                say(server, "The preset " + v.names.get(best) + " is on now.");
            }
        } else {
            String win = v.ids.get(best);
            Component link = Component.literal("Apply it: /giants admin preset " + win)
                    .withStyle(s -> s.withColor(ChatFormatting.AQUA).withClickEvent(new ClickEvent(
                            ClickEvent.Action.SUGGEST_COMMAND, "/giants admin preset " + win)));
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (p.hasPermissions(AdminCommand.OPS)) p.sendSystemMessage(link);
        }
        return r.text;
    }

    private static void say(MinecraftServer server, String line) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            p.sendSystemMessage(Component.literal(line).withStyle(ChatFormatting.GOLD));
        net.jj.giants.GiantsGuideMod.LOG.info("Giants: {}", line);
    }

    /** once a second: a vote whose time ran out ends; votes turned off cancel it */
    public static void tick(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        PlayerData.Vote v = d.s.vote;
        if (v == null) return;
        if (!Players.on(server, "votes")) {
            d.s.vote = null;
            d.setDirty();
            say(server, "Votes are turned off in this world, so the vote was called off.");
            return;
        }
        if (server.overworld().getGameTime() >= v.endsAt) {
            d.s.vote = null;
            d.setDirty();
            AdminBook.changed("players");
            finish(server, v);
        }
    }

    /** the Guide's Votes page (and the book's): the open vote with its count, your choice, and the last result */
    public static JsonObject json(MinecraftServer server, ServerPlayer viewer) {
        JsonObject o = new JsonObject();
        o.addProperty("on", Players.on(server, "votes"));
        PlayerData d = PlayerData.get(server);
        PlayerData.Vote v = d.s.vote;
        if (v != null) {
            JsonObject j = new JsonObject();
            JsonArray opts = new JsonArray();
            int[] c = v.counts();
            for (int i = 0; i < v.ids.size(); i++) {
                JsonObject op = new JsonObject();
                op.addProperty("id", v.ids.get(i));
                op.addProperty("name", v.names.get(i));
                op.addProperty("votes", c[i]);
                opts.add(op);
            }
            j.add("options", opts);
            long left = Math.max(0, v.endsAt - server.overworld().getGameTime());
            j.addProperty("secondsLeft", left / 20);
            j.addProperty("auto", v.auto);
            j.addProperty("by", v.by);
            Integer mine = viewer == null ? null : v.votes.get(viewer.getStringUUID());
            j.addProperty("mine", mine == null ? -1 : mine);
            o.add("open", j);
        }
        if (d.s.last != null) o.addProperty("last", d.s.last.text);
        return o;
    }

    /** the presets a vote may offer (for the book): every built-in one, then the saved ones */
    public static List<String[]> choices() {
        List<String[]> out = new ArrayList<>();
        for (String id : AdminTransfer.presets()) {
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            if (b != null) out.add(new String[] {id, b.name()});
        }
        for (var s : net.jj.giants.admin.AdminPresets.list()) out.add(new String[] {s.name(), s.name()});
        return out;
    }
}
