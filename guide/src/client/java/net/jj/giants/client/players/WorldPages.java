package net.jj.giants.client.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/**
 * (Part 5) the Guide's This world pages, made from what the server sent (ADMIN_BRIDGE.md 20.14): Rules, Votes and
 * Leaderboards. Drawn through whatever page the Guide gives it (its own headings, lines and buttons).
 */
public final class WorldPages {
    private WorldPages() {}

    public static final String[] PAGES = {"rules", "votes", "top", "games"};

    static final int TEXT = 0xFFD8D8D8, GREY = 0xFF9A9A9A, KEY = 0xFFFFE080, GOOD = 0xFF80E080;

    /** where a page goes */
    public interface Out {
        void heading(Component c);

        void para(Component c, int colour);

        void bullet(Component c);

        void row(Component left, Component right, int colour);

        /** each: {words, Runnable, picked} */
        void buttons(List<Object[]> bs);

        void gap(int h);
    }

    private static String str(JsonObject o, String k) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static Component tr(String k, Object... a) {
        return Component.translatable("guide.jj_giants.world." + k, a);
    }

    public static void rules(JsonObject w, Out out) {
        JsonObject r = w.getAsJsonObject("rules");
        if (r == null) return;
        if (r.has("hidden")) {
            out.para(Component.literal(str(r, "hidden")), GREY);
            return;
        }
        out.heading(tr("preset", str(r, "preset")));
        out.para(Component.literal(str(r, "about")), TEXT);
        if (r.get("danger") instanceof JsonObject d) {
            int level = d.get("level").getAsInt();
            out.gap(2);
            out.row(tr("danger"), Component.literal(level + " " + str(d, "label") + " · " + str(d, "reason")),
                    PickScreen.DANGER[Math.max(0, Math.min(4, level - 1))]);
        }
        if (r.get("rules") instanceof JsonArray a) {
            out.heading(tr("differ"));
            for (JsonElement e : a) out.bullet(Component.literal(e.getAsString()));
            if (r.has("more")) out.para(tr("more", r.get("more").getAsInt()), GREY);
        }
        if (r.get("players") instanceof JsonArray a && !a.isEmpty()) {
            out.heading(tr("for_players"));
            for (JsonElement e : a) out.bullet(Component.literal(e.getAsString()));
        }
    }

    public static void votes(JsonObject w, Out out) {
        JsonObject v = w.getAsJsonObject("vote");
        if (v == null) return;
        if (!v.get("on").getAsBoolean()) {
            out.para(tr("votes.off"), GREY);
            return;
        }
        if (v.get("open") instanceof JsonObject o) {
            long secs = o.get("secondsLeft").getAsLong();
            out.heading(tr("votes.open"));
            out.para(tr("votes.left", secs / 60, secs % 60, str(o, "by")), GREY);
            out.para(tr(o.get("auto").getAsBoolean() ? "votes.auto" : "votes.ask"), GREY);
            int mine = o.get("mine").getAsInt();
            JsonArray opts = o.getAsJsonArray("options");
            int total = 0;
            for (JsonElement e : opts) total += e.getAsJsonObject().get("votes").getAsInt();
            for (int i = 0; i < opts.size(); i++) {
                JsonObject op = opts.get(i).getAsJsonObject();
                int n = i + 1;
                int votes = op.get("votes").getAsInt();
                List<Object[]> bs = new ArrayList<>();
                bs.add(new Object[] {Component.literal(n + "  " + str(op, "name")),
                    (Runnable) () -> PlayersClient.ask("vote", "n", n), i == mine});
                out.buttons(bs);
                out.para(tr("votes.count", votes, total, i == mine ? tr("votes.yours") : Component.empty()),
                        i == mine ? GOOD : GREY);
            }
        } else out.para(tr("votes.none"), GREY);
        if (v.has("last")) {
            out.heading(tr("votes.last"));
            out.para(Component.literal(str(v, "last")), TEXT);
        }
    }

    /** (Part 10A) This world â†’ Minigames: what's on (Join, Watch, Leave), the last results, the winners */
    public static void games(JsonObject w, Out out) {
        JsonObject m = w.getAsJsonObject("minigame");
        if (m == null) return;
        if (m.get("game") instanceof JsonObject g) {
            String phase = str(g, "phase");
            boolean in = g.get("in").getAsBoolean(), watch = g.get("watch").getAsBoolean(),
                    inWorld = g.get("inWorld").getAsBoolean(), force = g.get("force").getAsBoolean(),
                    watching = g.get("watching").getAsBoolean();
            out.heading(Component.literal(str(g, "name")));
            out.para(Component.literal(str(g, "about")), TEXT);
            int players = g.get("players").getAsInt(), most = g.get("most").getAsInt(),
                    need = g.get("need").getAsInt(), s = g.get("lobby").getAsInt();
            if (phase.equals("lobby"))
                out.para(Component.literal((force ? "For everyone online. " : "") + "Starts in " + s / 60 + ":"
                        + String.format("%02d", s % 60) + " Â· " + players + " of " + most + " in" + (players < need
                        ? " (needs " + need + ")" : "") + "."), KEY);
            else out.para(Component.literal(phase.equals("play") ? "Being played now"
                    + (g.get("rounds").getAsInt() > 1 ? " (round " + g.get("round").getAsInt() + " of "
                    + g.get("rounds").getAsInt() + ")" : "") + "." : "Between rounds or ending."), KEY);
            if (!str(g, "names").isEmpty()) out.para(Component.literal("Playing: " + str(g, "names")), GREY);
            List<Object[]> bs = new ArrayList<>();
            if (phase.equals("lobby") && !force) {
                if (!in) bs.add(new Object[] {Component.literal("Join"), (Runnable) () -> PlayersClient.ask("mgjoin"),
                    false});
                if (!watch && watching) bs.add(new Object[] {Component.literal("Watch"),
                    (Runnable) () -> PlayersClient.ask("mgwatch"), false});
                if (in || watch) bs.add(new Object[] {Component.literal("Drop out"),
                    (Runnable) () -> PlayersClient.ask("mgleave"), false});
            } else if (!phase.equals("lobby")) {
                if (!inWorld && watching) bs.add(new Object[] {Component.literal("Watch"),
                    (Runnable) () -> PlayersClient.ask("mgwatch"), false});
                if (inWorld) bs.add(new Object[] {Component.literal("Leave (back to where you were)"),
                    (Runnable) () -> PlayersClient.ask("mgleave"), false});
            }
            if (in && phase.equals("lobby")) out.para(Component.literal("You're in! Your things stay safe: you get"
                    + " them back, where you were, when you come out."), GOOD);
            if (!bs.isEmpty()) out.buttons(bs);
        } else {
            out.heading(Component.literal("No minigame right now"));
            out.para(Component.literal("When the admins open one (Giant Royale, Boss Rush, Siegeâ€¦) it shows here with"
                    + " Join and Watch, and in chat. Each is played in a world of its own: you come back where you"
                    + " were, with everything you had."), GREY);
        }
        if (m.has("myWins")) out.para(Component.literal("You: " + m.get("myWins").getAsInt() + " wins in "
                + m.get("myGames").getAsInt() + " games."), GOOD);
        if (m.get("results") instanceof JsonArray r && !r.isEmpty()) {
            out.heading(Component.literal("Last results"));
            for (JsonElement e : r) out.bullet(Component.literal(e.getAsString()));
        }
        if (m.get("board") instanceof JsonArray b && !b.isEmpty()) {
            out.heading(Component.literal("Minigame winners"));
            for (JsonElement e : b) {
                JsonObject r = e.getAsJsonObject();
                out.row(Component.literal(r.get("place").getAsInt() + "  " + str(r, "name")),
                        Component.literal(r.get("wins").getAsInt() + " wins"), KEY);
            }
        }
    }

    public static void top(JsonObject w, Out out) {
        JsonObject b = w.getAsJsonObject("boards");
        if (b == null) return;
        if (!b.get("on").getAsBoolean()) {
            out.para(tr("top.off"), GREY);
            return;
        }
        JsonArray boards = b.getAsJsonArray("boards");
        if (boards.isEmpty()) out.para(tr("top.none_shown"), GREY);
        for (JsonElement e : boards) {
            JsonObject o = e.getAsJsonObject();
            out.heading(Component.translatableWithFallback("guide.jj_giants.world.top." + str(o, "kind"),
                    str(o, "title")));
            JsonArray rows = o.getAsJsonArray("rows");
            if (rows.isEmpty()) out.para(tr("top.nobody"), GREY);
            for (JsonElement re : rows) {
                JsonObject r = re.getAsJsonObject();
                boolean me = r.get("me").getAsBoolean();
                out.row(Component.literal(r.get("place").getAsInt() + "  " + str(r, "name")),
                        Component.literal(str(r, "text")), me ? GOOD : KEY);
            }
            if (o.has("myPlace") && o.get("myPlace").getAsInt() > rows.size())
                out.para(tr("top.you", o.get("myPlace").getAsInt(), str(o, "myText")), GOOD);
            if (o.get("myKinds") instanceof JsonObject k) {
                List<String> parts = new ArrayList<>();
                for (var ke : k.entrySet()) parts.add(Component.translatable("guide.jj_giants." + ke.getKey()
                        + ".name").getString() + " " + ke.getValue().getAsInt());
                out.para(tr("top.yours", String.join(", ", parts)), GREY);
            }
        }
    }
}
