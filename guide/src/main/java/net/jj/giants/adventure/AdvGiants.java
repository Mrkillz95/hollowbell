package net.jj.giants.adventure;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11) What the scripts and hooks ask of a giant, each through his own bridge (ADMIN_BRIDGE.md §13, §23, §24):
 * walk toward a spot (his own wander spot, `sysact go`; a mod without it is moved a few blocks at a time with
 * `funact move`), a mood (`sysact mood`), turn on a player (`sysact turn`), sleep and wake (`liveact`). The Guide never
 * steers a giant itself.
 */
public final class AdvGiants {
    private AdvGiants() {}

    private static final Map<String, List<String>> SYS = new HashMap<>(), MOODS = new HashMap<>();

    static void forget() {
        SYS.clear();
        MOODS.clear();
    }

    static List<String> sysDoes(MinecraftServer server, Giant g) {
        return SYS.computeIfAbsent(g.key(), k -> {
            List<String> out = new ArrayList<>();
            List<String> l = AdminServer.call(server, g, "sys", "1");
            JsonObject o = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
            if (o != null && o.get("does") instanceof com.google.gson.JsonArray a)
                for (var e : a) out.add(e.getAsString());
            return out;
        });
    }

    /** his mood ids, as his live header lists them */
    public static List<String> moods(MinecraftServer server, Giant g) {
        return MOODS.computeIfAbsent(g.key(), k -> {
            List<String> out = new ArrayList<>();
            List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
            if (lines != null && !lines.isEmpty()) {
                JsonObject head = AdminJson.object(lines.get(0));
                if (head != null && head.get("moods") instanceof com.google.gson.JsonArray a)
                    for (var e : a) out.add(e.getAsString());
            }
            return out;
        });
    }

    static boolean bool(JsonObject o, String k, boolean or) {
        try {
            return o.has(k) ? o.get(k).getAsBoolean() : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    static @Nullable Giant kind(LivingEntity e) {
        String k = FunGiants.kindOf(e);
        return k == null ? null : AdminServer.giant(k);
    }

    static double ground(ServerLevel l, double x, double z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /** sent walking toward x, z (fights and orders still win) */
    static void go(MinecraftServer server, LivingEntity e, double x, double z) {
        Giant g = kind(e);
        if (g == null) return;
        if (sysDoes(server, g).contains("go")) {
            JsonObject o = new JsonObject();
            o.addProperty("sys", 1);
            o.addProperty("do", "go");
            o.addProperty("id", e.getStringUUID());
            o.addProperty("x", x);
            o.addProperty("z", z);
            AdminServer.call(server, g, "sysact", o.toString());
            return;
        }
        Vec3 d = new Vec3(x - e.getX(), 0, z - e.getZ());
        if (d.lengthSqr() < 4) return;
        nudge(server, e, e.position().add(d.normalize().scale(Math.min(6, d.length()))));
    }

    /** moved a little, to a spot on the ground (when he's stuck, or his mod can't walk to a spot) */
    static void nudge(MinecraftServer server, LivingEntity e, Vec3 to) {
        Giant g = kind(e);
        if (g == null || !(e.level() instanceof ServerLevel l)) return;
        JsonObject o = new JsonObject();
        o.addProperty("dim", l.dimension().location().toString());
        o.addProperty("x", to.x);
        o.addProperty("y", ground(l, to.x, to.z));
        o.addProperty("z", to.z);
        o.addProperty("where", "land");
        o.addProperty("stay", false);
        FunGiants.act(server, g, e.getStringUUID(), "move", o);
    }

    /** his mood (one his mod knows; "guardian" falls back to calm) */
    static boolean mood(MinecraftServer server, LivingEntity e, String mood) {
        Giant g = kind(e);
        if (g == null) return false;
        List<String> m = moods(server, g);
        String to = mood;
        if (!m.isEmpty() && !m.contains(to)) to = to.equals("guardian") && m.contains("calm") ? "calm" : null;
        if (to == null) return false;
        JsonObject o = new JsonObject();
        o.addProperty("sys", 1);
        o.addProperty("do", "mood");
        o.addProperty("id", e.getStringUUID());
        o.addProperty("value", to);
        List<String> r = AdminServer.call(server, g, "sysact", o.toString());
        return r != null && !r.isEmpty() && r.get(0).equals("ok");
    }

    /** he goes for this player (his own "done with you": he hunts them) */
    static void turn(MinecraftServer server, LivingEntity e, ServerPlayer p) {
        Giant g = kind(e);
        if (g == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("sys", 1);
        o.addProperty("do", "turn");
        o.addProperty("id", e.getStringUUID());
        o.addProperty("player", p.getStringUUID());
        AdminServer.call(server, g, "sysact", o.toString());
    }

    /** one of his Live control actions (sleep, wake, calm...) */
    static boolean live(MinecraftServer server, LivingEntity e, String what) {
        Giant g = kind(e);
        if (g == null) return false;
        JsonObject o = new JsonObject();
        o.addProperty("live", AdminLive.VERSION);
        o.addProperty("id", e.getStringUUID());
        o.addProperty("do", what);
        o.addProperty("value", "");
        o.addProperty("by", "-");
        List<String> r = AdminServer.call(server, g, AdminLive.ACT, o.toString());
        return r != null && !r.isEmpty() && r.get(0).equals("ok");
    }

    /** his line on Live control (state: asleep, fighting, owner...), or null */
    static @Nullable JsonObject row(MinecraftServer server, LivingEntity e, Map<String, Map<String, JsonObject>> cache) {
        Giant g = kind(e);
        if (g == null) return null;
        Map<String, JsonObject> rows = cache.computeIfAbsent(g.key(), k -> {
            Map<String, JsonObject> out = new HashMap<>();
            List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
            if (lines != null)
                for (int i = 1; i < lines.size(); i++) {
                    JsonObject r = AdminJson.object(lines.get(i));
                    if (r != null && r.has("id")) out.put(AdminJson.str(r, "id", 40), r);
                }
            return out;
        });
        return rows.get(e.getStringUUID());
    }

    /** the nearest player in his world who can be met (not watching), within this far (0: anywhere), or null */
    static @Nullable ServerPlayer nearest(LivingEntity e, double far, @Nullable Vec3 from) {
        if (!(e.level() instanceof ServerLevel l)) return null;
        Vec3 at = from == null ? e.position() : from;
        ServerPlayer best = null;
        double bd = far <= 0 ? Double.MAX_VALUE : far * far;
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = horizontal(p.position(), at);
            if (d <= bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    /** about how close counts as "there" for him (a big giant stops short of a spot) */
    static double near(LivingEntity e) {
        String k = FunGiants.kindOf(e);
        if (k == null) return 3;
        double r = FunGiants.reach(k, FunGiants.sizeOf(e));
        return Mth.clamp(r * 0.35, 3, 24);
    }
}
