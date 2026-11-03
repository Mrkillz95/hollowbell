package net.jj.hollowbell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.jj.hollowbell.solid.SitHost;
import net.jj.hollowbell.solid.Sitting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * The Giants Guide's player rules and switches (ADMIN_BRIDGE.md 20.3, 20.4, 20.6). The switches come with the
 * all-giants values in "adminset" (absent = allowed, as always); the per-player rules with "players"; the fight music
 * choice on each player's own game with "playersclient". Nothing is saved: the Guide sends it all again. Without the
 * Guide every switch is on and nobody has a rule, so everything works as it always did.
 */
public final class GiantsPlayers {
    private GiantsPlayers() {}

    public static final int VERSION = 1;

    /** the player switches this mod applies (listed in his admin header's "uses" after the multipliers) */
    public static final String[] USES = {
        "all.sit", "all.ride", "all.stand", "all.armourPowers", "all.bossBars", "all.fightMusic", "all.chatMeetings",
        "all.chatMoves", "all.chatBackDown", "all.books", "all.bookOrders"
    };

    /** one player's rules: may they own giants (hold a book), and do the giants leave them alone */
    public record Rule(boolean own, boolean ignored) {}

    private static volatile Map<String, String> switches = Map.of();
    private static volatile Map<UUID, Rule> rules = Map.of();
    /** (the player's own game) the server's fight music choice: usual, on or off */
    public static volatile String clientMusic = "usual";

    public static boolean uses(String id) {
        return List.of(USES).contains(id);
    }

    /** the switches out of adminset's "all" (every value as text: true, false or an option) */
    static void receiveAll(JsonObject all) {
        Map<String, String> m = new HashMap<>();
        for (String id : USES) {
            JsonElement v = all.get(id);
            if (v == null || !v.isJsonPrimitive()) continue;
            m.put(id, v.getAsString().toLowerCase(Locale.ROOT));
        }
        // (1.11.0, BOOK_RULE.md) My Giant: read beside the switches, not one of the "uses"
        JsonElement mg = all.get(MY_GIANT);
        if (mg != null && mg.isJsonPrimitive()) m.put(MY_GIANT, mg.getAsString().toLowerCase(Locale.ROOT));
        switches = Map.copyOf(m);
    }

    /** the Guide's My Giant (all.myGiant, absent = off): every picker gets their own book and one of him */
    public static final String MY_GIANT = "all.myGiant";

    public static boolean myGiant() {
        return "true".equals(switches.get(MY_GIANT));
    }

    /** the server stopped (and the tests): every switch on, no rules */
    public static void clear() {
        switches = Map.of();
        rules = Map.of();
    }

    private static boolean on(String id) {
        return !"false".equals(switches.get(id));
    }

    private static String option(String id, String or) {
        String v = switches.get(id);
        return v == null ? or : v;
    }

    public static boolean sit() {
        return on("all.sit");
    }

    public static boolean ride() {
        return on("all.ride");
    }

    public static boolean stand() {
        return on("all.stand");
    }

    public static boolean armourPowers() {
        return on("all.armourPowers");
    }

    /** usual (his own Boss bars setting), on or off */
    public static String bossBars() {
        return option("all.bossBars", "usual");
    }

    /** whether he shows his boss bar, given his own setting */
    public static boolean bossBar(boolean own) {
        return switch (bossBars()) {
            case "on" -> true;
            case "off" -> false;
            default -> own;
        };
    }

    public static boolean chatMeetings() {
        return on("all.chatMeetings");
    }

    public static boolean chatMoves() {
        return on("all.chatMoves");
    }

    public static boolean chatBackDown() {
        return on("all.chatBackDown");
    }

    public static boolean books() {
        return on("all.books");
    }

    /** the book's attack orders may be given (bookOrders isn't "noattacks") */
    public static boolean attackOrders() {
        return !"noattacks".equals(option("all.bookOrders", "all"));
    }

    /** (the player's own game) fight music plays: the server's choice, else the player's own */
    public static boolean music(boolean own) {
        return switch (clientMusic) {
            case "on" -> true;
            case "off" -> false;
            default -> own;
        };
    }

    /** a line one of his moves sends a player (a warning, or what it did to them): only while move notices are on */
    public static void moveLine(Player p, Component c, boolean bar) {
        if (chatMoves()) p.displayClientMessage(c, bar);
    }

    /** the same, in chat */
    public static void moveChat(ServerPlayer p, Component c) {
        if (chatMoves()) p.sendSystemMessage(c);
    }

    // per-player rules

    public static boolean mayOwn(@Nullable UUID id) {
        Rule r = id == null ? null : rules.get(id);
        return r == null || r.own();
    }

    public static boolean mayOwn(Player p) {
        return mayOwn(p.getUUID());
    }

    /** a player the giants leave alone: he never picks them as a target, as for a creative-mode player */
    public static boolean ignored(@Nullable Entity e) {
        if (!(e instanceof Player p)) return false;
        Rule r = rules.get(p.getUUID());
        return r != null && r.ignored();
    }

    /** GiantsBridge "players": {"players":1,"rules":{"<uuid>":{"own":false,"ignored":true}}}; the list replaces the last */
    public static @Nullable List<String> rules(String arg) {
        Map<UUID, Rule> m = new HashMap<>();
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("players").getAsInt() > VERSION) return null;
            JsonObject r = o.has("rules") ? o.getAsJsonObject("rules") : new JsonObject();
            for (var e : r.entrySet()) {
                JsonObject v = e.getValue().getAsJsonObject();
                boolean own = !v.has("own") || v.get("own").getAsBoolean();
                boolean ignored = v.has("ignored") && v.get("ignored").getAsBoolean();
                m.put(UUID.fromString(e.getKey()), new Rule(own, ignored));
            }
        } catch (RuntimeException e) {
            return null;
        }
        rules = Map.copyOf(m);
        return List.of("ok");
    }

    /** GiantsBridge "playersclient" (on the player's own game): {"fightMusic":"usual|on|off"}; {} = back to usual */
    public static List<String> client(String arg) {
        String music = "usual";
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.has("fightMusic")) music = o.get("fightMusic").getAsString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            // (unreadable: back to each player's own choice)
        }
        clientMusic = List.of("on", "off").contains(music) ? music : "usual";
        return List.of("ok");
    }

    /** one line on the action bar: message.hollowbell.players.<key> */
    public static void say(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message." + HollowbellMod.MOD_ID + ".players." + key, args), true);
    }

    // standing on him (all.stand off)

    /** each player standing on one of him: how many looks in a row */
    private static final Map<UUID, Integer> standing = new HashMap<>();

    /**
     * Every 10 ticks: with standing off, a player on him (not riding or sitting) for two looks in a row (a second) is
     * set down beside him, his own set-down onto clear ground.
     */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        if (stand()) {
            standing.clear();
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            SitHost h = p.isPassenger() || p.isSpectator() ? null : Sitting.standingOn(p);
            if (h == null) {
                standing.remove(p.getUUID());
                continue;
            }
            int n = standing.merge(p.getUUID(), 1, Integer::sum);
            if (n < 2) continue;
            standing.remove(p.getUUID());
            h.sitSetDown(p);
            say(p, "stand_off");
        }
    }

    public static void forget() {
        standing.clear();
    }
}
