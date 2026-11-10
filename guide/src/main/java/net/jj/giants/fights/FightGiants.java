package net.jj.giants.fights;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27.2) The giants as the fight code sees them: each kind's "fights" line (his moves and
 * his finisher), one "fightact" on one of him, his health now, and his live row (owner, asleep).
 */
public final class FightGiants {
    private FightGiants() {}

    public static final int VERSION = 1;
    public static final String HEAD = "fights", ACT = "fightact";

    private static final Map<String, JsonObject> heads = new HashMap<>();

    static void forget() {
        heads.clear();
        rows.clear();
        rowsAt = -1;
    }

    /** his mod's "fights" line (null: this version has none) */
    public static @Nullable JsonObject head(@Nullable MinecraftServer server, Giant g) {
        if (heads.containsKey(g.key())) return heads.get(g.key());
        List<String> l = AdminServer.call(server, g, HEAD, String.valueOf(VERSION));
        JsonObject o = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
        if (o != null && !o.has("fight")) o = null;
        if (server != null) heads.put(g.key(), o);
        return o;
    }

    static List<String> list(@Nullable JsonObject head, String key) {
        List<String> out = new ArrayList<>();
        if (head != null && head.get(key) instanceof JsonArray a)
            for (JsonElement e : a)
                try {
                    out.add(e.getAsString());
                } catch (RuntimeException bad) {
                    // left out
                }
        return out;
    }

    public static List<String> moves(@Nullable MinecraftServer server, Giant g) {
        return list(head(server, g), "moves");
    }

    public static String finisher(@Nullable MinecraftServer server, Giant g) {
        JsonObject h = head(server, g);
        return h == null ? "" : AdminJson.str(h, "finisher", 64);
    }

    /** (the tests) moves taken as switched off, by kind */
    public static final Map<String, java.util.Set<String>> TEST_OFF = new java.util.concurrent.ConcurrentHashMap<>();

    /** (1.15) his moves the world switched off (his "move.<id>.on" set off, or resting in Random chaos today) */
    public static java.util.Set<String> movesOff(@Nullable MinecraftServer server, Giant g) {
        java.util.Set<String> off = new java.util.HashSet<>(TEST_OFF.getOrDefault(g.key(), java.util.Set.of()));
        if (server == null) return off;
        net.jj.giants.admin.AdminJson.Report rep = net.jj.giants.admin.AdminServer.report(server, g);
        if (rep != null)
            for (net.jj.giants.admin.AdminSetting s : rep.settings()) {
                String id = s.id();
                if (!id.startsWith("move.") || !id.endsWith(".on") || s.type() != net.jj.giants.admin.AdminSetting.Type.BOOL)
                    continue;
                try {
                    if (!s.value().getAsBoolean()) off.add(id.substring(5, id.length() - 3));
                } catch (RuntimeException e) {
                    // (unreadable: left as on)
                }
            }
        try {
            List<String> rest = net.jj.giants.systems.SystemsData.get(server).s.rests.get(g.key());
            if (rest != null && net.jj.giants.systems.Systems.on(server, "chaos")) off.addAll(rest);
        } catch (RuntimeException e) {
            // (no chaos data)
        }
        return off;
    }

    /**
     * (1.15) the finisher he may really do: the one wanted unless the world switched it off, else his next big move
     * still on (his heavy moves, then his medium ones; from the script's list when it has one); "" when none is left
     */
    public static String allowedFinisher(@Nullable MinecraftServer server, Giant g, String want, List<String> only) {
        java.util.Set<String> off = movesOff(server, g);
        if (want.isEmpty() || !off.contains(want)) return want;
        List<String> fights = moves(server, g);
        for (List<String> from : only.isEmpty() ? List.of(only) : List.of(only, List.<String>of()))
            for (int tier : new int[] {net.jj.giants.Giants.HEAVY, net.jj.giants.Giants.MEDIUM})
                for (Giant.Move m : g.moves())
                    if (m.tier() == tier && !m.id().equals(want) && !off.contains(m.id())
                            && (fights.isEmpty() || fights.contains(m.id())) && (from.isEmpty() || from.contains(m.id())))
                        return m.id();
        return "";
    }

    public static boolean speaks(@Nullable MinecraftServer server, Giant g) {
        return head(server, g) != null;
    }

    static @Nullable Giant kind(LivingEntity e) {
        String k = FunGiants.kindOf(e);
        return k == null ? null : AdminServer.giant(k);
    }

    /** one fightact on one of him: ["ok", line] / ["no", why], or null when his mod doesn't answer */
    public static @Nullable List<String> act(MinecraftServer server, LivingEntity e, String what,
            @Nullable JsonObject more) {
        Giant g = kind(e);
        if (g == null || !speaks(server, g)) return null;
        JsonObject o = more == null ? new JsonObject() : more.deepCopy();
        o.addProperty("fight", VERSION);
        o.addProperty("do", what);
        o.addProperty("id", e.getStringUUID());
        List<String> r = AdminServer.call(server, g, ACT, o.toString());
        if (r != null && !r.isEmpty() && !r.get(0).equals("ok"))
            Fights.log("{} {}: {}", AdminServer.nameOf(g), what, r);
        return r;
    }

    static boolean ok(@Nullable List<String> r) {
        return r != null && !r.isEmpty() && r.get(0).equals("ok");
    }

    static JsonObject with(String k, String v) {
        JsonObject o = new JsonObject();
        o.addProperty(k, v);
        return o;
    }

    static JsonObject foe(LivingEntity foe) {
        return with("foe", foe.getStringUUID());
    }

    /** his health now: {now, most}; his mod's own numbers, else the game's */
    public record Hp(double now, double most, boolean yielding, boolean running, String target) {
        double share() {
            return most <= 0 ? 0 : Math.max(0, Math.min(1, now / most));
        }
    }

    public static Hp hp(MinecraftServer server, LivingEntity e) {
        List<String> r = act(server, e, "hp", null);
        if (ok(r) && r.size() > 1) {
            JsonObject j = AdminJson.object(r.get(1));
            if (j != null)
                return new Hp(AdminJson.num(j, "hp", 0), AdminJson.num(j, "max", 1), bool(j, "yielding"),
                        bool(j, "running"), AdminJson.str(j, "target", 40));
        }
        return new Hp(e.getHealth(), e.getMaxHealth(), false, false, e.getLastHurtByMob() == null ? "-"
                : e.getLastHurtByMob().getStringUUID());
    }

    static boolean bool(JsonObject j, String k) {
        try {
            return j.has(k) && j.get(k).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // Live control's rows (owner, asleep), looked at again at most once a second

    private static final Map<String, JsonObject> rows = new HashMap<>();
    private static long rowsAt = -1;

    public static @Nullable JsonObject row(MinecraftServer server, String id) {
        long now = server.getTickCount();
        if (rowsAt < 0 || now - rowsAt >= 20 || now < rowsAt) {
            rowsAt = now;
            rows.clear();
            for (Giant g : AdminServer.giants()) {
                List<String> lines = AdminServer.call(server, g, "live", "1");
                if (lines == null) continue;
                for (int i = 1; i < lines.size(); i++) {
                    JsonObject o = AdminJson.object(lines.get(i));
                    if (o != null && o.has("id")) rows.put(AdminJson.str(o, "id", 40), o);
                }
            }
        }
        return rows.get(id);
    }

    /** whom Live control says owns him ("" for nobody) */
    public static String owner(MinecraftServer server, String id) {
        JsonObject r = row(server, id);
        return r == null ? "" : AdminJson.str(r, "ownerId", 40);
    }

    public static String ownerName(MinecraftServer server, String id) {
        JsonObject r = row(server, id);
        return r == null ? "" : AdminJson.str(r, "owner", 40);
    }

    public static boolean asleep(MinecraftServer server, String id) {
        JsonObject r = row(server, id);
        return r != null && bool(r, "asleep");
    }

    /** (the tests) the rows are read again next time */
    public static void freshRows() {
        rowsAt = -1;
    }

    /** about how far his body reaches at his size (the Guide's size lines) */
    public static double reach(LivingEntity e) {
        String k = FunGiants.kindOf(e);
        return k == null ? 8 : FunGiants.reach(k, FunGiants.sizeOf(e));
    }

    public static String name(LivingEntity e) {
        Giant g = kind(e);
        return g == null ? "giant" : AdminServer.nameOf(g);
    }

    /** the kinds whose plain name takes "the" ("the Cerberus"), as the giants' own meeting lines say it */
    private static final java.util.Set<String> THE = java.util.Set.of("cerberus", "hollowbell", "lanternwillow",
            "swarmforge", "gloamwick", "hearthstride");

    /**
     * how a line calls him: a name an admin gave him as it is, else his plain name with "the" where it takes one
     * ("The Cerberus" at the start of a sentence, "the Cerberus" in the middle)
     */
    public static String called(LivingEntity e, boolean start) {
        if (e.hasCustomName()) return e.getCustomName().getString();
        String k = FunGiants.kindOf(e);
        String n = name(e);
        if (k == null || !THE.contains(k)) return n;
        return (start ? "The " : "the ") + n;
    }

    /** his name as his boss bar shows it (a custom name and title, else his own) */
    public static String shown(LivingEntity e) {
        return e.hasCustomName() ? e.getCustomName().getString() : name(e);
    }
}
