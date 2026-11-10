package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 8, ADMIN_BRIDGE.md §24) The new systems, the server's half: Herds, Growth, Hunger, Loyalty, Grief healing,
 * the rules for a giant's fall and a player's death near one, difficulty scaling, weak points, fear and awe, seasons
 * and the full moon. Each is an ordinary all-giants setting (category "systems"); all off, nothing here runs. The
 * Guide does what it can with the giants' own bridges (live, liveact, funact, their tags and the all-giants
 * multipliers) and each giant's small "sys" bridge for the rest.
 */
public final class Systems {
    private Systems() {}

    public static final String HEAD = "sys", ACT = "sysact";
    public static final int VERSION = 1;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Systems::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> Grief.load(s));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
        ServerLivingEntityEvents.AFTER_DEATH.register(Systems::died);
        // (a player's death near a giant: the blow that kills them, which the game notes only after)
        ServerLivingEntityEvents.ALLOW_DEATH.register(PlayerDeaths::dying);
        ServerPlayerEvents.COPY_FROM.register(PlayerDeaths::copy);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> SystemsCommand.register(d));
        // (Part 9) a follower and his giant don't hurt each other; sacred ground; the weather on players' games
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(Followers::allowDamage);
        Sacred.register();
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
                net.jj.giants.net.WeatherPayload.TYPE, net.jj.giants.net.WeatherPayload.CODEC);
    }

    public static void forget() {
        Look.forget();
        Grief.forget();
        Remains.forget();
        Scaling.forget();
        PlayerDeaths.forget();
        SystemsAdmin.forget();
        // (Part 9)
        Followers.forget();
        Weather.forget();
        Underworld.forget();
        Sacred.forget();
        Mirror.forget();
        Chaos.forget();
    }

    // the settings (their world value, else the server's default from the systems file)

    public static JsonElement value(MinecraftServer server, String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get(full);
        AdminSetting s = AdminCatalog.shared(full);
        if (v != null && (s == null || s.accepts(v))) return v;
        return s == null ? new JsonPrimitive(0) : s.def();
    }

    public static boolean on(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static double num(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsDouble();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static String option(MinecraftServer server, String id) {
        JsonElement v = value(server, id);
        return v.isJsonPrimitive() ? v.getAsString() : "";
    }

    /** does any system that follows each giant (his record) run now */
    static boolean followsGiants(MinecraftServer server) {
        return on(server, "growth") || on(server, "hunger") || on(server, "loyalty");
    }

    /**
     * What the giants are sent ("adminset"'s "all"): the world's all-giants values with each system's server default
     * filled in where it isn't the jar's, the multipliers times the difficulty scaling, the full moon and the season,
     * and whether the Guide wants their hits told. All off: exactly what came in.
     */
    public static Map<String, JsonElement> forGiants(@Nullable MinecraftServer server, Map<String, JsonElement> all) {
        if (server == null) return all;
        Map<String, JsonElement> out = null;
        // a server default for a system the giants apply (the weak points)
        for (String id : List.of("all.weakPoints", "all.weakHit", "all.bodyHit", "all.mirror")) {
            if (all.containsKey(id)) continue;
            AdminSetting s = AdminCatalog.shared(id);
            if (s == null || AdminSetting.same(s.def(), s.jar())) continue;
            if (out == null) out = new LinkedHashMap<>(all);
            out.put(id, s.def());
        }
        Scaling.Factors f = Scaling.factors(server);
        if (!f.none()) {
            if (out == null) out = new LinkedHashMap<>(all);
            times(out, "all.health", f.health());
            times(out, "all.damage", f.damage());
            times(out, "all.speed", f.speed());
            times(out, "all.healing", f.healing());
        }
        if (on(server, "growth") || on(server, "loyalty")) {
            if (out == null) out = new LinkedHashMap<>(all);
            out.put("all.sysReport", new JsonPrimitive(true));
        }
        // (Part 9) Underworld: the giants' own spawners keep the world's own ones out of the Overworld
        if (Underworld.on(server)) {
            if (out == null) out = new LinkedHashMap<>(all);
            out.put("all.dimOverworld", new JsonPrimitive("nogiants"));
        }
        // (Part 10B) the giant-fight settings the giants apply, and whether the Guide asks and listens
        return net.jj.giants.fights.Fights.forGiants(server, out == null ? all : out);
    }

    /** (Part 9) one giant's own values as he is sent them: Random chaos's resting moves off for the day */
    public static Map<String, JsonElement> forGiant(@Nullable MinecraftServer server, Giant g,
            Map<String, JsonElement> values) {
        return server == null ? values : Chaos.forGiant(server, g, values);
    }

    private static void times(Map<String, JsonElement> m, String id, double f) {
        if (Math.abs(f - 1) < 1e-9) return;
        double now = 1;
        JsonElement v = m.get(id);
        try {
            if (v != null) now = v.getAsDouble();
        } catch (RuntimeException e) {
            now = 1;
        }
        m.put(id, new JsonPrimitive(Math.round(now * f * 1000) / 1000.0));
    }

    // the clock

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        Grief.tick(server);
        Remains.tick(server);
        Weather.everyTick(server);
        if (t % 20 == 0) {
            // (Part 9) every second: villagers and golems, followers, weather, the underworld
            Villages.tick(server);
            Followers.tick(server);
            Weather.tick(server);
            Underworld.tick(server);
            if (t % 100 == 0) Sacred.tick(server);
            if (t % 200 == 0) {
                Mirror.tick(server);
                Chaos.tick(server);
            }
            Weak.tick(server);
            if (t % 40 == 0) Fear.tick(server);
            if (t % 200 == 0) {
                Scaling.tick(server);
                Look.tick(server);
            }
            SystemsAdmin.tick(server);
        }
    }

    // a giant fell, a player or a creature died

    private static final java.util.Set<String> fallen = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > 256;
                }
            });

    /**
     * A giant fell (the game's own death, or his own death ending: called from the Guide's Slayers catch). His remains,
     * what he swallowed, the win for whoever brought him down; his record is dropped.
     */
    /**
     * (1.13.0) an admin changed this giant's size (Live control's Size, a giant tag's size): Growth keeps it and grows
     * on from there (ADMIN_BRIDGE.md 24.9)
     */
    public static void resized(MinecraftServer server, String id, String value) {
        try {
            Look.rebase(server, id, Double.parseDouble(value.trim()));
        } catch (RuntimeException bad) {
            // (not a number: his mod refused it anyway)
        }
    }

    public static void fell(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        if (!fallen.add(e.getStringUUID())) return;
        MinecraftServer server = sl.getServer();
        String kind = FunGiants.kindOf(e);
        if (kind == null) return;
        SystemsData d = SystemsData.get(server);
        SystemsData.G g = d.s.giants.remove(e.getStringUUID());
        if (g != null) {
            d.setDirty();
            PlayerDeaths.dropBelly(sl, e, g);
            // the win: whoever last hurt him, within 30 s, if a giant
            if (!g.lastBy.isEmpty() && sl.getGameTime() - g.lastByAt < 600) Look.win(server, g.lastBy);
        }
        // (Part 10B) a fight he was in is over; a staged fight's loser leaves the remains its script asks for
        net.jj.giants.fights.Fights.fell(e);
        String rule = net.jj.giants.fights.FightScript.remains(e, option(server, "deathRule"));
        if (!rule.equals("drops") && !Remains.OWN_RUIN.contains(kind))
            Remains.build(sl, kind, e, rule, num(server, "corpseDays"));
    }

    /** (Part 10B) a giant won a fight (the loser backed down): a win for his growth while Growth is on */
    public static void winFromFight(MinecraftServer server, String id) {
        Look.win(server, id);
    }

    /** (Part 10B) a giant won a fight: his loyalty up while Loyalty is on and he is owned */
    public static void loyaltyFromFight(MinecraftServer server, String id, int by) {
        if (!on(server, "loyalty")) return;
        SystemsData d = SystemsData.get(server);
        SystemsData.G g = d.s.giants.get(id);
        if (g == null || g.loyalty < 0) return;
        g.loyalty = Math.min(100, g.loyalty + by);
        d.setDirty();
    }

    /** a player or a creature died: a giant's kill counts for his growth, and a creature feeds him */
    private static void died(LivingEntity dead, DamageSource src) {
        if (!(dead.level() instanceof ServerLevel sl) || FunGiants.kindOf(dead) != null) return;
        MinecraftServer server = sl.getServer();
        if (!on(server, "growth") && !on(server, "hunger")) return;
        LivingEntity giant = giantBehind(sl, src);
        if (giant == null) return;
        if (dead instanceof ServerPlayer) {
            if (on(server, "growth")) Look.win(server, giant.getStringUUID());
            return;
        }
        if (on(server, "hunger")) Look.ate(server, giant, Math.min(40, dead.getMaxHealth() * 2), null, 0);
    }

    /** the giant behind a blow: him, or the nearest of the kind whose part, creature or shot it was */
    static @Nullable LivingEntity giantBehind(ServerLevel sl, DamageSource src) {
        for (Entity x : new Entity[] {src.getEntity(), src.getDirectEntity()}) {
            if (x == null) continue;
            if (FunGiants.kindOf(x) != null && x instanceof LivingEntity le) return le;
        }
        String kind = net.jj.giants.tools.Tools.kindOf(src.getEntity());
        if (kind == null) kind = net.jj.giants.tools.Tools.kindOf(src.getDirectEntity());
        if (kind == null) return null;
        Entity at = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        return nearest(sl, kind, at, 400);
    }

    static @Nullable LivingEntity nearest(ServerLevel sl, String kind, Entity at, double most) {
        LivingEntity best = null;
        double bd = most * most;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at.blockPosition()).inflate(most),
                x -> kind.equals(FunGiants.kindOf(x)) && x.isAlive())) {
            double dd = le.distanceToSqr(at);
            if (dd < bd) {
                bd = dd;
                best = le;
            }
        }
        return best;
    }

    // GuideBridge "syshurt": a giant tells what a hit on him did (while Growth or Loyalty needs it)

    public static @Nullable List<String> hurt(MinecraftServer server, String arg) {
        JsonObject o = AdminJson.object(arg);
        if (o == null || !o.has("id")) return null;
        try {
            if (o.get("sys").getAsInt() > VERSION) return null;
        } catch (RuntimeException e) {
            return null;
        }
        String id = AdminJson.str(o, "id", 40), by = AdminJson.str(o, "by", 40);
        Look.hurt(server, id, by.equals("-") ? "" : by);
        // (Part 9) Mirror: a player's hit, by its tactic
        if (o.has("tactic"))
            Mirror.hurt(server, id, AdminJson.str(o, "tactic", 16), o.has("player") && o.get("player").getAsBoolean(),
                    AdminJson.num(o, "amount", 0));
        return List.of("ok");
    }

    // calling a giant

    /** one "sysact" call to a giant: ["ok", line] / ["no", why]; null if his mod doesn't answer it */
    static @Nullable List<String> act(MinecraftServer server, Giant g, String id, String what, JsonObject more) {
        JsonObject o = more == null ? new JsonObject() : more.deepCopy();
        o.addProperty(HEAD, VERSION);
        o.addProperty("do", what);
        o.addProperty("id", id);
        return AdminServer.call(server, g, ACT, o.toString());
    }

    /** his "sys" header: his weak points and what he does (null: his mod is older) */
    static @Nullable JsonObject header(MinecraftServer server, Giant g) {
        List<String> r = AdminServer.call(server, g, HEAD, String.valueOf(VERSION));
        if (r == null || r.isEmpty()) return null;
        JsonObject o = AdminJson.object(r.get(0));
        return o != null && o.has(HEAD) ? o : null;
    }

    /** one "liveact" call (as the server) */
    static @Nullable List<String> live(MinecraftServer server, Giant g, String id, String what, String value) {
        JsonObject o = new JsonObject();
        o.addProperty("live", 1);
        o.addProperty("id", id);
        o.addProperty("do", what);
        o.addProperty("value", value);
        o.addProperty("by", "-");
        return AdminServer.call(server, g, "liveact", o.toString());
    }

    static List<String> names(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) if (e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    static void log(String what, Object... args) {
        GiantsGuideMod.LOG.info("Giants systems: " + what, args);
    }
}
