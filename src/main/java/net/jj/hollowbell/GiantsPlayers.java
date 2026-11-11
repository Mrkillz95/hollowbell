package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.jj.hollowbell.entity.HearthYards;
import net.jj.hollowbell.item.BookRule;
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
        "all.chatMoves", "all.chatBackDown", "all.books", "all.bookOrders", GiantsMix.MIX, "all.maxGiants"
    };

    /** (ADMIN_BRIDGE.md 20.18) the world's Max giants per player: "none" (absent) or "1".."9" */
    public static final String MAX = "all.maxGiants";

    /**
     * one player's rules: may they own giants (hold a book), do the giants leave them alone, and the most giants they
     * may own (-1: the world's Max giants per player, 0: no limit, 1..9)
     */
    public record Rule(boolean own, boolean ignored, int max) {
        public Rule(boolean own, boolean ignored) {
            this(own, ignored, -1);
        }
    }

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
        // (JJ answers 2) the dimension rules and giants mixing, for his spawner
        GiantsMix.receive(all);
    }

    /** the Guide's My Giant (all.myGiant, absent = off): every picker gets their own book and one of him */
    public static final String MY_GIANT = "all.myGiant";

    public static boolean myGiant() {
        return "true".equals(switches.get(MY_GIANT));
    }

    /** the server stopped (and the tests): every switch on, no rules */
    public static void clear() {
        GiantsMix.clear();
        switches = Map.of();
        rules = Map.of();
        lastNo.clear();
        noToldAt.clear();
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
        return r != null && r.ignored() || HearthYards.inside(p);
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
                // (20.18) absent: the world's Max giants per player; 0: no limit; 1..9 (clamped)
                int max = v.has("max") ? Math.max(0, Math.min(9, v.get("max").getAsInt())) : -1;
                m.put(UUID.fromString(e.getKey()), new Rule(own, ignored, max));
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
        lastNo.clear();
        noToldAt.clear();
    }

    // (ADMIN_BRIDGE.md 20.18) Max giants per player: the Guide counts, the giants ask before a gain

    /** each refused player's last line from the Guide ("You already own 2 giants, the most this world allows.") */
    private static final Map<UUID, String> lastNo = new HashMap<>();
    /** when each was last told it on the action bar (a pickup is tried every tick) */
    private static final Map<UUID, Long> noToldAt = new HashMap<>();
    /** (the tests, where the Guide isn't there) answers in its place: (action, arg) -> the Guide's answer */
    public static volatile @Nullable BiFunction<String, String, List<String>> guideForTests;

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    /** the most giants this player may own: their own rule, else the world's; 0 = no limit */
    public static int maxFor(@Nullable UUID id) {
        Rule r = id == null ? null : rules.get(id);
        if (r != null && r.max() >= 0) return r.max();
        String v = switches.get(MAX);
        if (v == null) return 0;
        try {
            return Math.max(0, Math.min(9, Integer.parseInt(v.trim())));
        } catch (NumberFormatException e) {
            return 0; // ("none")
        }
    }

    /** a limit is in force for them (with No limit nothing is ever asked) */
    public static boolean capped(@Nullable UUID id) {
        return maxFor(id) > 0;
    }

    /**
     * May this player GAIN a giant: become the book's holder, or get an own book they didn't have? Never asked for one
     * they already have, and never to take one away. With no limit for them: yes at once. Otherwise the Guide counts
     * every giant's owners and answers; no Guide, or an older one: yes. A no is remembered (capLine) and, with tell,
     * shown on the action bar at most every two seconds.
     */
    public static boolean mayGain(ServerPlayer p, boolean tell) {
        if (!mayOwn(p)) return false;
        UUID id = p.getUUID();
        if (!capped(id)) return true;
        JsonObject o = new JsonObject();
        o.addProperty("players", VERSION);
        o.addProperty("player", id.toString());
        o.addProperty("giant", "hollowbell");
        List<String> r = askGuide(p.server, "maygain", o.toString());
        if (r == null || r.isEmpty() || !"no".equals(r.get(0))) {
            lastNo.remove(id);
            return true;
        }
        String line = r.size() > 1 && r.get(1) != null && !r.get(1).isBlank() ? r.get(1)
                : "You already own as many giants as this world allows.";
        lastNo.put(id, line);
        if (tell) {
            long now = p.serverLevel().getGameTime();
            Long last = noToldAt.get(id);
            if (last == null || now - last >= 40 || now < last) {
                noToldAt.put(id, now);
                p.displayClientMessage(Component.literal(line), true);
            }
        }
        return false;
    }

    /** the last no the Guide gave this player, while a limit is in force for them (null: none) */
    public static @Nullable String capLine(UUID id) {
        return capped(id) ? lastNo.get(id) : null;
    }

    /** how many giants the Guide last said they own (the first number in its line; else their limit) */
    public static int ownedCount(UUID id) {
        String l = lastNo.get(id);
        if (l != null) {
            Matcher m = NUMBER.matcher(l);
            if (m.find()) return Integer.parseInt(m.group());
        }
        return maxFor(id);
    }

    /** an op's line about somebody at their limit: "Alex already owns 2 giants, the most this world allows for them" */
    public static String opLine(ServerPlayer p) {
        int n = ownedCount(p.getUUID());
        return p.getGameProfile().getName() + " already owns " + n + (n == 1 ? " giant" : " giants")
                + ", the most this world allows for them";
    }

    /** an op's /give at their limit: their copy stays quiet (message.hollowbell.players.max_op) */
    public static Component opGiveLine(ServerPlayer p) {
        int n = ownedCount(p.getUUID());
        String name = p.getGameProfile().getName();
        return n == 1
                ? Component.translatable("message." + HollowbellMod.MOD_ID + ".players.max_op_one", name)
                : Component.translatable("message." + HollowbellMod.MOD_ID + ".players.max_op", name,
                        String.valueOf(n));
    }

    private static @Nullable List<String> askGuide(MinecraftServer server, String action, String arg) {
        BiFunction<String, String, List<String>> stub = guideForTests;
        if (stub != null) return stub.apply(action, arg);
        if (server == null) return null;
        try {
            Class<?> c = Class.forName("net.jj.giants.GuideBridge", true, GiantsPlayers.class.getClassLoader());
            Method m = c.getMethod("guide", MinecraftServer.class, String.class, String.class);
            Object r = m.invoke(null, server, action, arg);
            if (!(r instanceof List<?> l)) return null;
            return l.stream().map(String::valueOf).toList();
        } catch (ClassNotFoundException e) {
            return null; // (no Guide: no limit)
        } catch (ReflectiveOperationException | RuntimeException e) {
            HollowbellMod.LOG.debug("Couldn't ask the Guide about a player's giants: {}", e.toString());
            return null;
        }
    }

    /**
     * GiantsBridge "owners": {"owners":1} -> ["ok", {"owners":1,"main":"<uuid>","mainName":"..","own":[{"id","name"}]}].
     * main is left out while nobody holds the book; own is empty while My Giant is off. Only the saved book data.
     */
    public static @Nullable List<String> owners(MinecraftServer server, String arg) {
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("owners").getAsInt() > VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        if (server == null) return null;
        BookRule r = BookRule.get(server);
        JsonObject out = new JsonObject();
        out.addProperty("owners", VERSION);
        if (r.holder() != null) {
            out.addProperty("main", r.holder().toString());
            out.addProperty("mainName", r.holderName());
        }
        JsonArray own = new JsonArray();
        if (myGiant())
            for (Map.Entry<UUID, BookRule.Own> e : r.owns().entrySet()) {
                JsonObject one = new JsonObject();
                one.addProperty("id", e.getKey().toString());
                one.addProperty("name", e.getValue().name());
                own.add(one);
            }
        out.add("own", own);
        return List.of("ok", out.toString());
    }
}
