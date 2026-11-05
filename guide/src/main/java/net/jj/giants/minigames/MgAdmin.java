package net.jj.giants.minigames;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.11) The admin book's Minigames page (admins, and moderators while Moderators run
 * minigames is on) and the players' This world → Minigames page: what's on, each mode with Start (open) and Start
 * (everyone), the last results and the winners' board; the requests the book sends (each the twin of a command).
 */
public final class MgAdmin {
    private MgAdmin() {}

    public static final String KIND = "minigames";
    public static final Set<String> DOS = Set.of("mgstart", "mgstop", "mgnow");

    /** who has the page open (sent every second while a minigame is on) */
    private static final Set<UUID> watching = new LinkedHashSet<>();

    static void forget() {
        watching.clear();
    }

    /** may see the page and start or stop minigames */
    public static boolean mayRun(ServerPlayer p) {
        return Minigames.staff(p);
    }

    /** the page looked at or not (the book's admin_ask "minigames" / "minigames_off") */
    public static void watch(MinecraftServer server, ServerPlayer p, boolean on) {
        if (!on || !mayRun(p)) {
            watching.remove(p.getUUID());
            return;
        }
        watching.add(p.getUUID());
        AdminBook.sender.accept(p, page(server));
    }

    /** the page to whoever looks at it */
    static void flush(MinecraftServer server) {
        if (watching.isEmpty()) return;
        AdminBookStatePayload pg = null;
        for (UUID u : List.copyOf(watching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null || !mayRun(p)) {
                watching.remove(u);
                continue;
            }
            if (pg == null) pg = page(server);
            AdminBook.sender.accept(p, pg);
        }
    }

    public static AdminBookStatePayload page(MinecraftServer server) {
        return new AdminBookStatePayload(KIND, json(server).toString());
    }

    static JsonObject json(MinecraftServer server) {
        JsonObject o = new JsonObject();
        o.addProperty("paused", net.jj.giants.tools.Tools.on(server, "paused"));
        o.addProperty("quiet", Minigames.quiet(server));
        JsonArray modes = new JsonArray();
        for (MgMode m : Minigames.MODES.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", m.id());
            j.addProperty("name", m.name());
            j.addProperty("about", m.about());
            j.addProperty("goal", m.goal());
            j.addProperty("fewest", m.fewest());
            j.addProperty("pvp", m.pvp());
            String why = m.cannot(server);
            if (why != null) j.addProperty("cannot", why);
            modes.add(j);
        }
        o.add("modes", modes);
        MgGame g = Minigames.current;
        if (g != null) o.add("game", game(g));
        MgData d = MgData.get(server);
        JsonArray res = new JsonArray();
        for (int i = d.s.results.size() - 1, n = 0; i >= 0 && n < 10; i--, n++) {
            MgData.Result r = d.s.results.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("mode", r.mode);
            j.addProperty("name", r.name);
            j.addProperty("line", r.line);
            j.addProperty("time", r.time);
            j.addProperty("players", r.players);
            res.add(j);
        }
        o.add("results", res);
        o.add("board", board(server, null, 10));
        return o;
    }

    static JsonObject game(MgGame g) {
        JsonObject j = new JsonObject();
        j.addProperty("mode", g.mode.id());
        j.addProperty("name", g.mode.name());
        j.addProperty("about", g.mode.about());
        j.addProperty("force", g.force);
        j.addProperty("by", g.by);
        j.addProperty("phase", g.phase.name().toLowerCase(java.util.Locale.ROOT));
        j.addProperty("lobby", Math.max(0, g.lobbyLeft / 20));
        j.addProperty("building", g.build == null ? 1 : g.build.progress());
        j.addProperty("round", g.round);
        j.addProperty("rounds", g.rounds);
        j.addProperty("clock", g.clock());
        j.addProperty("need", g.need);
        j.addProperty("most", g.most);
        j.addProperty("watching", g.watching);
        if (g.last != null) j.addProperty("last", g.last.line());
        JsonArray ps = new JsonArray();
        for (MgGame.P p : g.people.values()) {
            if (p.left) continue;
            JsonObject q = new JsonObject();
            q.addProperty("name", p.name);
            q.addProperty("watcher", p.watcher);
            q.addProperty("alive", p.alive);
            q.addProperty("down", p.down);
            q.addProperty("score", p.score);
            q.addProperty("wins", p.roundWins);
            q.addProperty("kos", p.kos);
            if (p.team >= 0) q.addProperty("team", p.team);
            ps.add(q);
        }
        j.add("players", ps);
        JsonArray gs = new JsonArray();
        for (MgGiants.G k : g.giants.all.values()) {
            JsonObject q = new JsonObject();
            q.addProperty("kind", k.kind);
            q.addProperty("name", Minigames.name(k.kind));
            q.addProperty("standing", !k.fell && !k.gone);
            q.addProperty("role", k.role);
            var e = g.giants.entity(k);
            if (e != null) {
                q.addProperty("health", Math.round(e.getHealth()));
                q.addProperty("max", Math.round(e.getMaxHealth()));
            }
            gs.add(q);
        }
        j.add("giants", gs);
        return j;
    }

    /** the winners' board: most minigame wins (and this player's place) */
    static JsonArray board(MinecraftServer server, @Nullable String viewer, int top) {
        JsonArray a = new JsonArray();
        List<MgData.Player> l = rows(server);
        for (int i = 0; i < l.size() && i < top; i++) {
            JsonObject r = new JsonObject();
            r.addProperty("place", i + 1);
            r.addProperty("name", l.get(i).name);
            r.addProperty("wins", l.get(i).wins);
            r.addProperty("games", l.get(i).games);
            a.add(r);
        }
        return a;
    }

    /** every player with a minigame win, most first */
    public static List<MgData.Player> rows(MinecraftServer server) {
        List<MgData.Player> l = new ArrayList<>();
        for (MgData.Player p : MgData.get(server).s.players.values()) if (p.wins > 0) l.add(p);
        l.sort((a, b) -> a.wins != b.wins ? Integer.compare(b.wins, a.wins) : a.name.compareTo(b.name));
        return l;
    }

    /** a book request: start, stop, start now (each the twin of /giants minigame ...) */
    public static AdminServer.Result request(MinecraftServer server, ServerPlayer p, String what, JsonObject o) {
        if (!mayRun(p))
            return AdminServer.Result.fail(net.jj.giants.admin.AdminWorld.get(server).isMod(p.getUUID())
                    ? "The admins have turned off minigames for moderators." : "Only admins can do that.");
        String who = p.getGameProfile().getName();
        AdminServer.Result r = switch (what) {
            case "mgstart" -> Minigames.start(server, who, AdminJson.str(o, "mode", 32),
                    !AdminJson.str(o, "kind", 16).equals("open"), (int) AdminJson.num(o, "rounds", 0));
            case "mgstop" -> Minigames.stop(server, who);
            default -> Minigames.now(server, who);
        };
        Minigames.changed();
        return r;
    }

    // the players' This world → Minigames page

    /** what a player's Guide shows: what's on, whether they're in, Join / Watch / Leave, the last results, the board */
    public static JsonObject guideJson(MinecraftServer server, ServerPlayer p) {
        JsonObject o = new JsonObject();
        MgGame g = Minigames.current;
        if (g != null) {
            JsonObject j = new JsonObject();
            j.addProperty("name", g.mode.name());
            j.addProperty("about", g.mode.about());
            j.addProperty("goal", g.mode.goal());
            j.addProperty("force", g.force);
            j.addProperty("phase", g.phase.name().toLowerCase(java.util.Locale.ROOT));
            j.addProperty("lobby", Math.max(0, g.lobbyLeft / 20));
            j.addProperty("round", g.round);
            j.addProperty("rounds", g.rounds);
            j.addProperty("players", g.playing());
            j.addProperty("need", g.need);
            j.addProperty("most", g.most);
            j.addProperty("watching", g.watching);
            MgGame.P me = g.of(p.getUUID());
            j.addProperty("in", me != null && !me.left && !me.watcher);
            j.addProperty("watch", me != null && !me.left && me.watcher);
            j.addProperty("inWorld", me != null && me.in);
            List<String> names = new ArrayList<>();
            for (MgGame.P q : g.people.values()) if (!q.left && !q.watcher) names.add(q.name);
            j.addProperty("names", String.join(", ", names));
            o.add("game", j);
        }
        MgData d = MgData.get(server);
        JsonArray res = new JsonArray();
        for (int i = d.s.results.size() - 1, n = 0; i >= 0 && n < 5; i--, n++) res.add(d.s.results.get(i).line);
        o.add("results", res);
        if (MgCatalog.on(server, "mgBoard") && net.jj.giants.players.Players.on(server, "boards"))
            o.add("board", board(server, p.getStringUUID(), 5));
        MgData.Player mine = d.s.players.get(p.getStringUUID());
        if (mine != null) {
            o.addProperty("myWins", mine.wins);
            o.addProperty("myGames", mine.games);
        }
        return o;
    }

    /** a player's Guide asked: mgjoin, mgwatch, mgleave */
    public static @Nullable AdminServer.Result ask(ServerPlayer p, String what) {
        return switch (what) {
            case "mgjoin" -> Minigames.join(p, false);
            case "mgwatch" -> Minigames.join(p, true);
            case "mgleave" -> Minigames.leave(p);
            default -> null;
        };
    }

    /** this player's This world pages again (something changed) */
    static void guidePage(ServerPlayer p) {
        if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p,
                net.jj.giants.net.PlayersStatePayload.TYPE))
            net.jj.giants.players.Players.sendWorld(p);
    }
}
