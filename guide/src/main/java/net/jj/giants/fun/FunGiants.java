package net.jj.giants.fun;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.players.Players;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7) The giants as the fun tools see them: which kind an entity is, finding one by his id or "the nearest" or
 * "the n-th on Live control", what each giant's mod says it can do here (its "fun" line: does, places), how big each
 * is, and one "funact" call.
 */
public final class FunGiants {
    private FunGiants() {}

    public static final int VERSION = 1;
    public static final String HEAD = "fun", ACT = "funact";

    /** each giant's height and length at full size, in blocks (the Guide's size lines, as the book's pictures) */
    static final Map<String, int[]> SIZE = Map.of(
            "pitchgut", new int[] {185, 265}, "furrowmaw", new int[] {45, 600}, "cerberus", new int[] {200, 280},
            "hollowbell", new int[] {200, 200}, "lanternwillow", new int[] {200, 200},
            "wreckback", new int[] {208, 323}, "swarmforge", new int[] {152, 225}, "gloamwick", new int[] {200, 277});

    /** his height at this size, in blocks */
    public static double height(String kind, double size) {
        int[] s = SIZE.getOrDefault(kind, new int[] {100, 100});
        return Math.max(2, s[0] * size);
    }

    /** about how far his body reaches from his middle at this size */
    public static double reach(String kind, double size) {
        int[] s = SIZE.getOrDefault(kind, new int[] {100, 100});
        return Math.max(2, Math.max(s[0], s[1]) * size * 0.5);
    }

    /** (the tests) stand-in giants: an entity with this scoreboard tag counts as that kind */
    public static final Map<String, String> TEST_KINDS = new java.util.concurrent.ConcurrentHashMap<>();

    /** the guide's key for a giant's main body, or null */
    public static @Nullable String kindOf(@Nullable Entity e) {
        if (e == null) return null;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        for (var m : Players.MAIN.entrySet()) if (m.getValue().equals(type)) return m.getKey();
        if (!TEST_KINDS.isEmpty()) for (var t : TEST_KINDS.entrySet()) if (e.getTags().contains(t.getKey())) return t.getValue();
        return null;
    }

    /** his size, asked of his own mod (size(), getScale() or scale()), else 1 */
    public static double sizeOf(Entity e) {
        for (String m : new String[] {"size", "scale", "bellScale", "mountainScale", "getScale"}) {
            try {
                Object v = e.getClass().getMethod(m).invoke(e);
                if (v instanceof Number n) return n.doubleValue();
            } catch (ReflectiveOperationException | RuntimeException x) {
                // not this one
            }
        }
        return 1;
    }

    /** a giant standing in the world, by his id (any loaded dimension) */
    public static @Nullable LivingEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels())
            if (l.getEntity(id) instanceof LivingEntity le && kindOf(le) != null && !le.isRemoved()) return le;
        return null;
    }

    public static @Nullable LivingEntity find(MinecraftServer server, String id) {
        try {
            return find(server, UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** every giant standing in loaded land, by kind (null: every kind) */
    public static List<LivingEntity> standing(MinecraftServer server, @Nullable String kind) {
        List<LivingEntity> out = new ArrayList<>();
        for (ServerLevel l : server.getAllLevels())
            for (Entity e : l.getAllEntities())
                if (e instanceof LivingEntity le && !le.isRemoved() && !le.isDeadOrDying()) {
                    String k = kindOf(le);
                    if (k != null && (kind == null || k.equals(kind))) out.add(le);
                }
        return out;
    }

    /** what finding one came to: him, or why not */
    public record Found(@Nullable LivingEntity giant, @Nullable Giant kind, String why) {
        static Found no(String why) {
            return new Found(null, null, why);
        }
    }

    /**
     * "wreckback" (the nearest one to here: this dimension first), "wreckback 2" (the second as Live control numbers
     * them) or his id
     */
    public static Found which(MinecraftServer server, String words, @Nullable ServerLevel level, @Nullable Vec3 at) {
        String[] w = words.trim().split("\\s+");
        if (w.length == 0 || w[0].isEmpty()) return Found.no("Which giant?");
        try {
            UUID id = UUID.fromString(w[0]);
            LivingEntity e = find(server, id);
            if (e == null) return Found.no(outOrGone(server, id));
            return new Found(e, AdminServer.giant(kindOf(e)), "");
        } catch (IllegalArgumentException notAnId) {
            // a kind
        }
        Giant g = AdminServer.giant(w[0].toLowerCase(Locale.ROOT));
        if (g == null) return Found.no("No giant called " + w[0] + ".");
        String name = AdminServer.nameOf(g);
        if (w.length > 1) {
            int n;
            try {
                n = Integer.parseInt(w[1]);
            } catch (NumberFormatException e) {
                return Found.no("Which " + name + "? A number, as Live control numbers them.");
            }
            List<String> ids = liveIds(server, g);
            if (n < 1 || n > ids.size())
                return Found.no(ids.isEmpty() ? "There's no " + name + " in this world."
                        : "There are " + ids.size() + " " + name + " here: pick 1 to " + ids.size() + ".");
            LivingEntity e = find(server, ids.get(n - 1));
            if (e == null) return Found.no("He's out of the world right now. Go to him first (Teleport).");
            return new Found(e, g, "");
        }
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : standing(server, g.key())) {
            double d = at == null ? 0 : e.position().distanceToSqr(at);
            if (level != null && e.level() != level) d += 1e18;
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return Found.no("There's no " + name + " standing in the world now.");
        return new Found(best, g, "");
    }

    /** a giant not standing in loaded land: out of the world, or gone */
    static String outOrGone(MinecraftServer server, UUID id) {
        for (Giant g : AdminServer.giants())
            if (liveIds(server, g).contains(id.toString()))
                return "He's out of the world right now. Go to him first (Teleport).";
        return "He isn't there any more.";
    }

    /** the ids Live control lists for him, in its order */
    public static List<String> liveIds(MinecraftServer server, Giant g) {
        List<String> out = new ArrayList<>();
        List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
        if (lines == null) return out;
        for (int i = 1; i < lines.size(); i++) {
            JsonObject o = AdminJson.object(lines.get(i));
            if (o != null && o.has("id")) out.add(AdminJson.str(o, "id", 40));
        }
        return out;
    }

    /** his label as Live control shows it ("Wreckback 2", or his own name) */
    public static String label(MinecraftServer server, LivingEntity e) {
        String kind = kindOf(e);
        Giant g = kind == null ? null : AdminServer.giant(kind);
        String name = g == null ? "giant" : AdminServer.nameOf(g);
        if (g == null) return name;
        List<String> ids = liveIds(server, g);
        int i = ids.indexOf(e.getStringUUID());
        return ids.size() > 1 && i >= 0 ? name + " " + (i + 1) : name;
    }

    /** what his mod says it can do here: its "fun" line (null: this version has none) */
    public static @Nullable JsonObject head(@Nullable MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, HEAD, String.valueOf(VERSION));
        JsonObject o = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
        if (o == null || !o.has(HEAD)) return null;
        try {
            if (o.get(HEAD).getAsInt() > VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
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

    public static List<String> does(@Nullable MinecraftServer server, Giant g) {
        return list(head(server, g), "does");
    }

    public static List<String> places(@Nullable MinecraftServer server, Giant g) {
        return list(head(server, g), "places");
    }

    /** one funact on one of him: ok and his line, or why not */
    public static AdminServer.Result act(MinecraftServer server, Giant g, String id, String what, JsonObject more) {
        String name = AdminServer.nameOf(g);
        if (!does(server, g).contains(what))
            return AdminServer.Result.fail(name + ": this version can't do that. Update the " + name + " mod.");
        JsonObject o = more == null ? new JsonObject() : more.deepCopy();
        o.addProperty(HEAD, VERSION);
        o.addProperty("do", what);
        o.addProperty("id", id);
        List<String> r = AdminServer.call(server, g, ACT, o.toString());
        if (r == null || r.isEmpty()) return AdminServer.Result.fail(name + ": his mod didn't answer.");
        String line = r.size() > 1 ? r.get(1) : "";
        if (!r.get(0).equals("ok")) return AdminServer.Result.fail(line.isEmpty() ? name + ": he can't." : line);
        return AdminServer.Result.said(List.of(line));
    }
}
