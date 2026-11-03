package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.TakeMe;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.2) The Giants Guide's fun and world tools: an admin playing as him ("playas", on his
 * crown), his own stats from the tags the Guide puts on him ("stats"), and moving him somewhere to live ("move"). The
 * Guide checks who may ask; this only does it, the way his own commands do. His tags (jj_giants.health=2 and the
 * rest) are read where his health, hits and speed are worked out: none on him and nothing changes.
 */
public final class GiantsFun {
    private GiantsFun() {}

    public static final int VERSION = 1;
    static final List<String> DOES = List.of("playas", "stats", "move");
    /** where he can live: over the land, and up in the sky (he flies, always; never in deep water or under ground) */
    static final List<String> PLACES = List.of("land", "sky");
    private static final String WHO = "Hollowbell";

    /** the tags the Guide writes on him */
    public static final String TAG = "jj_giants.", NO_DROPS = "jj_giants.nodrops";

    /** the admins on his crown now, playing as him (the riding and no-attack switches don't apply to them) */
    private static final Set<UUID> PLAYING = ConcurrentHashMap.newKeySet();

    /** this one's own multiplier from his tags: "health", "damage" or "speed" (1 when there's none) */
    public static float mult(Entity e, String what) {
        if (e == null || e.getTags().isEmpty()) return 1f;
        String head = TAG + what + "=";
        for (String t : e.getTags()) {
            if (!t.startsWith(head)) continue;
            try {
                float v = Float.parseFloat(t.substring(head.length()));
                if (!Float.isFinite(v)) return 1f;
                return what.equals("speed") ? Mth.clamp(v, 0.25f, 4f) : Mth.clamp(v, 0.1f, 20f);
            } catch (NumberFormatException bad) {
                return 1f;
            }
        }
        return 1f;
    }

    /** his own drops are left out (the Guide drops an admin's loot instead) */
    public static boolean noDrops(Entity e) {
        return e != null && e.getTags().contains(NO_DROPS);
    }

    /** an admin playing as him: steering and moves whatever the world's riding switches say */
    public static boolean playing(@Nullable Entity p) {
        return p != null && PLAYING.contains(p.getUUID());
    }

    public static void forget() {
        PLAYING.clear();
    }

    /** "fun": the one header line */
    public static @Nullable List<String> describe(@Nullable MinecraftServer server, String arg) {
        if (server == null) return null;
        try {
            if (arg != null && !arg.isBlank() && Integer.parseInt(arg.trim()) < 1) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        JsonObject head = new JsonObject();
        head.addProperty("fun", VERSION);
        head.addProperty("giant", HollowbellMod.MOD_ID);
        head.addProperty("name", WHO);
        JsonArray does = new JsonArray();
        for (String d : DOES) does.add(d);
        head.add("does", does);
        JsonArray places = new JsonArray();
        for (String p : PLACES) places.add(p);
        head.add("places", places);
        return List.of(head.toString());
    }

    /** "funact": one thing to one of him; ["ok", line] or ["no", why]; null for what it can't read */
    public static @Nullable List<String> act(@Nullable MinecraftServer server, String arg) {
        if (server == null || arg == null) return null;
        JsonObject o;
        UUID id;
        String what;
        try {
            o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("fun").getAsInt() > VERSION) return null;
            id = UUID.fromString(o.get("id").getAsString());
            what = o.get("do").getAsString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
        if (!DOES.contains(what)) return null;
        HollowbellEntity h = find(server, id);
        if (h == null) {
            boolean out = false;
            for (Object x : TakeMe.listed(server))
                if (x instanceof Away.Rec r && r.id.equals(id) || x instanceof TakeMe.Lying k && k.id().equals(id))
                    out = true;
            // (the world's own one saved with his chunk and noted nowhere else is out of the world too)
            var w1 = net.jj.hollowbell.world.WorldOne.get(server);
            if (w1.aliveNow() && w1.isTheOne(id) && w1.where() != null) out = true;
            return no(out ? "He's out of the world right now. Go to him first (Teleport)." : "He isn't there any more.");
        }
        return switch (what) {
            case "playas" -> playAs(server, h, o);
            case "stats" -> {
                h.funStats();
                yield ok(WHO + ": health x" + plain(mult(h, "health")) + ", damage x" + plain(mult(h, "damage"))
                        + ", speed x" + plain(mult(h, "speed")) + ".");
            }
            case "move" -> move(h, o);
            default -> null;
        };
    }

    /** how far off an admin may be to take his crown: 96 blocks and his own height */
    static double reach(HollowbellEntity h) {
        return 96 + 210 * h.bellScale();
    }

    private static List<String> playAs(MinecraftServer server, HollowbellEntity h, JsonObject o) {
        ServerPlayer p = player(server, str(o, "by"));
        if (p == null) return no("Only a player can play as him.");
        boolean on = !o.has("on") || o.get("on").getAsBoolean();
        if (!on) {
            if (h.rider() != p) {
                PLAYING.remove(p.getUUID());
                return no("You aren't playing as him.");
            }
            // (as /hollowbell ride again: set down on the ground beside him, gently)
            h.dropRider();
            PLAYING.remove(p.getUUID());
            return ok("You've stepped out of the " + WHO + ".");
        }
        if (h.rider() == p) {
            PLAYING.add(p.getUUID());
            return ok("You're playing as the " + WHO + ". G gets you off.");
        }
        if (h.rider() != null) return no("Someone is already at his controls.");
        double reach = reach(h);
        if (p.level() != h.level() || p.distanceToSqr(h) > reach * reach) return no("Go to him first.");
        PLAYING.add(p.getUUID());
        // (as /hollowbell ride: straight onto his crown)
        if (!h.possess(p)) {
            PLAYING.remove(p.getUUID());
            return no(WHO + ": he can't be ridden just now.");
        }
        return ok("You're playing as the " + WHO + ". G gets you off.");
    }

    /** while they play: dropped from the list once they leave his crown (any way at all) */
    public static void tick(MinecraftServer server) {
        if (PLAYING.isEmpty() || server.getTickCount() % 20 != 0) return;
        PLAYING.removeIf(u -> {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null) return true;
            for (ServerLevel l : server.getAllLevels())
                if (!l.getEntities(ModEntities.HOLLOWBELL, e -> e.rider() == p).isEmpty()) return false;
            return true;
        });
    }

    private static List<String> move(HollowbellEntity h, JsonObject o) {
        String dim = str(o, "dim"), where = str(o, "where").toLowerCase(Locale.ROOT);
        if (where.isEmpty()) where = "land";
        if (!PLACES.contains(where)) return no("He can't live there.");
        double x, y, z;
        try {
            x = o.get("x").getAsDouble();
            y = o.get("y").getAsDouble();
            z = o.get("z").getAsDouble();
        } catch (RuntimeException e) {
            return no("Move needs a spot.");
        }
        if (!Double.isFinite(x + y + z)) return no("Move needs a spot.");
        if (!dim.isEmpty() && !dim.equals(h.level().dimension().location().toString()))
            return no("He's in another world. Move him within his own world.");
        boolean stay = o.has("stay") && o.get("stay").getAsBoolean();
        h.funMoveTo(new Vec3(x, y, z), where.equals("sky"), stay);
        return ok(WHO + ": moved.");
    }

    static @Nullable HollowbellEntity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels())
            if (l.getEntity(id) instanceof HollowbellEntity h && !h.isRemoved() && !h.isDeadOrDying()) return h;
        return null;
    }

    private static String str(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    static String plain(double v) {
        return java.math.BigDecimal.valueOf(Math.round(v * 100) / 100.0).stripTrailingZeros().toPlainString();
    }

    private static List<String> ok(String line) {
        return List.of("ok", line);
    }

    private static List<String> no(String why) {
        return List.of("no", why);
    }

    private static @Nullable ServerPlayer player(MinecraftServer server, String id) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
