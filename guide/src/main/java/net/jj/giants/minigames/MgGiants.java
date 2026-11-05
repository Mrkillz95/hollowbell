package net.jj.giants.minigames;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.Marks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.4) The giants of one round: each brought in by his own summon (the same as /<giant>
 * summon, into the round's world), found once he stands, given the round's strength (his own stat tags, Part 7, read
 * again by his funact stats), kept inside the map, and taken away (his own liveact remove) when the round ends.
 */
public final class MgGiants {
    /** one giant of the round */
    public static final class G {
        public UUID id;
        public String kind = "", role = "";
        public int team = -1;
        public double health = 1, damage = 1;
        public boolean fell, gone;
        public @Nullable UUID lastBy;
        public long lastAt;
        /** who hurt him how much (players' ids), and how many blows each landed */
        public final Map<UUID, Double> dealt = new LinkedHashMap<>();
        public final Map<UUID, Integer> hits = new LinkedHashMap<>();
        boolean wasDying;
        public long born;
    }

    private static final class Ask {
        String kind, mood, role;
        Vec3 at;
        double size, health, damage;
        /** (when above 0) the most health he should have, whatever his own is at this size */
        double wantHp;
        int team, ticks;
        final long since = System.currentTimeMillis();
        java.util.Set<UUID> before;
        @Nullable Consumer<G> then;
    }

    private final MgGame game;
    public final Map<UUID, G> all = new LinkedHashMap<>();
    private final List<Ask> asks = new ArrayList<>();

    MgGiants(MgGame game) {
        this.game = game;
    }

    /** the ground's height at x, z in the round's world (the top block + 1) */
    static double ground(ServerLevel l, double x, double z) {
        return l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    /**
     * One of him at that spot (his own summon), in that mood, at that size, with these health and damage multipliers
     * on him alone; then is told when he stands. False if his mod isn't here.
     */
    public boolean summon(String kind, Vec3 at, String mood, double size, double health, double damage, String role,
            int team, @Nullable Consumer<G> then) {
        return summon(kind, at, mood, size, health, 0, damage, role, team, then);
    }

    /** the same, with his most health set to wantHp (the multiplier worked out from his own when he stands) */
    public boolean summonHp(String kind, Vec3 at, String mood, double size, double wantHp, double damage, String role,
            int team, @Nullable Consumer<G> then) {
        return summon(kind, at, mood, size, 1, wantHp, damage, role, team, then);
    }

    private boolean summon(String kind, Vec3 at, String mood, double size, double health, double wantHp,
            double damage, String role, int team, @Nullable Consumer<G> then) {
        Giant g = AdminServer.giant(kind);
        ServerLevel l = game.level;
        if (g == null || l == null) return false;
        Ask a = new Ask();
        a.kind = kind;
        a.mood = mood;
        a.role = role;
        a.at = new Vec3(at.x, ground(l, at.x, at.z), at.z);
        a.size = size;
        a.health = health;
        a.wantHp = wantHp;
        a.damage = damage;
        a.team = team;
        a.then = then;
        a.before = new java.util.HashSet<>();
        for (Entity e : l.getAllEntities()) if (FunGiants.kindOf(e) != null) a.before.add(e.getUUID());
        List<String> moods = moods(game.server, g);
        String m = moods.contains(mood) ? " " + mood : "";
        String arg = l.dimension().location() + " " + a.at.x + " " + a.at.y + " " + a.at.z + " -" + m + " "
                + AdminSetting.plain(Math.round(size * 1000) / 1000.0);
        List<String> said = AdminServer.call(game.server, g, "summon", arg);
        GiantsGuideMod.LOG.debug("Giants minigames: summon {} {}: {}", kind, arg, said);
        asks.add(a);
        return true;
    }

    private static final Map<String, List<String>> MOODS = new java.util.HashMap<>();

    static List<String> moods(MinecraftServer server, Giant g) {
        List<String> m = MOODS.get(g.key());
        if (m != null) return m;
        List<String> out = new ArrayList<>();
        List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
        if (lines != null && !lines.isEmpty()) {
            JsonObject head = AdminJson.object(lines.get(0));
            if (head != null && head.get("moods") instanceof com.google.gson.JsonArray a)
                for (var e : a) out.add(e.getAsString());
        }
        MOODS.put(g.key(), out);
        return out;
    }

    static void forgetMoods() {
        MOODS.clear();
        SYS.clear();
    }

    private static final Map<String, List<String>> SYS = new java.util.HashMap<>();

    /** what his "sys" line says he does (go, mood, ...) */
    static List<String> sysDoes(MinecraftServer server, Giant g) {
        List<String> d = SYS.get(g.key());
        if (d != null) return d;
        List<String> out = new ArrayList<>();
        List<String> l = AdminServer.call(server, g, "sys", "1");
        JsonObject o = l == null || l.isEmpty() ? null : AdminJson.object(l.get(0));
        if (o != null && o.get("does") instanceof com.google.gson.JsonArray a) for (var e : a) out.add(e.getAsString());
        SYS.put(g.key(), out);
        return out;
    }

    /**
     * Sent walking toward a spot (his own wander spot there, as the systems' Herds send them: fights and orders win);
     * one whose mod can't do that is moved a few blocks that way instead.
     */
    public static void goToward(MgGame game, G k, double x, double z) {
        Giant gi = AdminServer.giant(k.kind);
        ServerLevel l = game.level;
        if (gi == null || l == null) return;
        if (sysDoes(game.server, gi).contains("go")) {
            JsonObject o = new JsonObject();
            o.addProperty("sys", 1);
            o.addProperty("do", "go");
            o.addProperty("id", k.id.toString());
            o.addProperty("x", x);
            o.addProperty("z", z);
            AdminServer.call(game.server, gi, "sysact", o.toString());
            return;
        }
        LivingEntity e = l.getEntity(k.id) instanceof LivingEntity le ? le : null;
        if (e == null) return;
        Vec3 d = new Vec3(x - e.getX(), 0, z - e.getZ());
        if (d.lengthSqr() < 16) return;
        Vec3 to = e.position().add(d.normalize().scale(Math.min(6, d.length())));
        JsonObject o = new JsonObject();
        o.addProperty("dim", l.dimension().location().toString());
        o.addProperty("x", to.x);
        o.addProperty("y", ground(l, to.x, to.z));
        o.addProperty("z", to.z);
        o.addProperty("where", "land");
        o.addProperty("stay", false);
        FunGiants.act(game.server, gi, k.id.toString(), "move", o);
    }

    /** one giant told to go for another (his own meet: a fight a player started, neither backs down) */
    public static void meet(MgGame game, G who, G other) {
        Giant gi = AdminServer.giant(who.kind);
        if (gi != null) AdminServer.call(game.server, gi, "meet", other.id + " " + who.id);
    }

    /** each tick: the summoned ones found; the fallen and the gone noted */
    void tick() {
        ServerLevel l = game.level;
        if (l == null) return;
        if (!asks.isEmpty()) {
            for (Ask a : List.copyOf(asks)) {
                a.ticks++;
                if (a.ticks < 1) continue;
                LivingEntity best = null;
                double bd = Double.MAX_VALUE;
                for (Entity e : l.getAllEntities()) {
                    if (!(e instanceof LivingEntity le) || a.before.contains(e.getUUID()) || all.containsKey(e.getUUID()))
                        continue;
                    if (!a.kind.equals(FunGiants.kindOf(le)) || le.isRemoved()) continue;
                    double d = le.position().distanceToSqr(a.at);
                    if (d < bd) {
                        bd = d;
                        best = le;
                    }
                }
                if (best == null) {
                    // (his land may still be loading: given up after half a minute, however fast the ticks run)
                    if (a.ticks > 200 && System.currentTimeMillis() - a.since > 30_000) {
                        asks.remove(a);
                        GiantsGuideMod.LOG.warn("Giants minigames: the {}'s summon left none of him in {}", a.kind,
                                l.dimension().location());
                    }
                    continue;
                }
                asks.remove(a);
                G g = new G();
                g.id = best.getUUID();
                g.kind = a.kind;
                g.role = a.role;
                g.team = a.team;
                g.health = a.health;
                if (a.wantHp > 0) {
                    // (his own most health at this size, as his mod lists it (some keep their own), decides the
                    // multiplier)
                    double own = maxOf(best) / Math.max(0.01, tag(best, "health"));
                    g.health = Math.max(0.1, Math.min(20, a.wantHp / Math.max(1, own)));
                }
                g.damage = a.damage;
                g.born = game.server.getTickCount();
                all.put(g.id, g);
                strength(best, g.health, a.damage);
                if (a.then != null) a.then.accept(g);
            }
        }
        if (game.server.getTickCount() % 5 != 0) return;
        for (G g : all.values()) {
            if (g.fell || g.gone) continue;
            Entity e = l.getEntity(g.id);
            if (e instanceof LivingEntity le && !le.isRemoved()) {
                g.wasDying = dying(le);
                if (le.isDeadOrDying()) fell(g);
                continue;
            }
            // not here any more: fell (he was dying), or gone some other way (stepped out of the world)
            if (g.wasDying) fell(g);
            else {
                g.gone = true;
                takeAway(g);
                game.mode.giantGone(game, g);
            }
        }
    }

    private void fell(G g) {
        if (g.fell) return;
        g.fell = true;
        game.mode.giantFell(game, g);
        Minigames.changed();
    }

    /** (the tests) */
    public double maxOfForTests(LivingEntity e) {
        return maxOf(e);
    }

    /** his most health as his own mod lists it (Live control's "max"), else the game's */
    double maxOf(LivingEntity e) {
        Giant g = AdminServer.giant(FunGiants.kindOf(e));
        List<String> lines = g == null ? null : AdminServer.call(game.server, g, AdminLive.LIST,
                String.valueOf(AdminLive.VERSION));
        if (lines != null)
            for (int i = 1; i < lines.size(); i++) {
                JsonObject r = AdminJson.object(lines.get(i));
                if (r != null && e.getStringUUID().equals(AdminJson.str(r, "id", 40)) && r.has("max"))
                    return AdminJson.num(r, "max", e.getMaxHealth());
            }
        return e.getMaxHealth();
    }

    /** the multiplier his own stat tag holds now (1 without one) */
    static double tag(LivingEntity e, String stat) {
        String head = Marks.TAG + stat + "=";
        for (String t : e.getTags())
            if (t.startsWith(head))
                try {
                    double v = Double.parseDouble(t.substring(head.length()));
                    return Double.isFinite(v) && v > 0 ? v : 1;
                } catch (NumberFormatException bad) {
                    return 1;
                }
        return 1;
    }

    /** his health and damage for this round: his own stat tags, read again by his mod */
    void strength(LivingEntity e, double health, double damage) {
        String hh = Marks.TAG + "health=", dd = Marks.TAG + "damage=";
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(hh) || t.startsWith(dd)) e.removeTag(t);
        if (Math.abs(health - 1) > 1e-6) e.addTag(hh + AdminSetting.plain(Math.round(health * 1000) / 1000.0));
        if (Math.abs(damage - 1) > 1e-6) e.addTag(dd + AdminSetting.plain(Math.round(damage * 1000) / 1000.0));
        Giant g = AdminServer.giant(FunGiants.kindOf(e));
        if (g != null && (Math.abs(health - 1) > 1e-6 || Math.abs(damage - 1) > 1e-6))
            FunGiants.act(game.server, g, e.getStringUUID(), "stats", new JsonObject());
        e.setHealth(((LivingEntity) e).getMaxHealth());
    }

    private static final Map<Class<?>, Method> SIT = new java.util.HashMap<>();

    /** dying (the game's own death, or his own death that ends with him leaving the world) */
    static boolean dying(LivingEntity e) {
        if (e.isDeadOrDying()) return true;
        Method m = SIT.computeIfAbsent(e.getClass(), c -> {
            try {
                Method x = c.getMethod("sitDying");
                return x.getReturnType() == boolean.class ? x : null;
            } catch (ReflectiveOperationException ex) {
                return null;
            }
        });
        if (m == null) return false;
        try {
            return (boolean) m.invoke(e);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    public @Nullable LivingEntity entity(G g) {
        ServerLevel l = game.level;
        return l != null && l.getEntity(g.id) instanceof LivingEntity le && !le.isRemoved() ? le : null;
    }

    public @Nullable G of(UUID id) {
        return all.get(id);
    }

    /** the ones still standing */
    public List<G> standing() {
        List<G> out = new ArrayList<>();
        for (G g : all.values()) if (!g.fell && !g.gone) out.add(g);
        return out;
    }

    public boolean waiting() {
        return !asks.isEmpty();
    }

    /** one taken away by his own mod (out of the world too), and anything left of him in the round's world */
    void takeAway(G g) {
        Giant k = AdminServer.giant(g.kind);
        if (k != null) {
            JsonObject o = new JsonObject();
            o.addProperty("live", AdminLive.VERSION);
            o.addProperty("id", g.id.toString());
            o.addProperty("do", "remove");
            o.addProperty("value", "");
            o.addProperty("by", "-");
            AdminServer.call(game.server, k, AdminLive.ACT, o.toString());
        }
        ServerLevel l = game.level;
        if (l != null && l.getEntity(g.id) instanceof Entity e && !e.isRemoved()) e.discard();
    }

    /** every giant of the round taken away (and every one of any giant mod still in the world) */
    void removeAll(@Nullable ServerLevel l) {
        // (one just summoned and not found yet is taken away by his own remove too)
        if (l != null)
            for (Entity e : List.copyOf(collect(l))) {
                String kind = FunGiants.kindOf(e);
                if (kind != null && !all.containsKey(e.getUUID())) {
                    G g = new G();
                    g.id = e.getUUID();
                    g.kind = kind;
                    all.put(g.id, g);
                }
            }
        for (G g : all.values()) takeAway(g);
        asks.clear();
        if (l == null) return;
        for (Entity e : List.copyOf(collect(l))) e.discard();
    }

    private static List<Entity> collect(ServerLevel l) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : l.getAllEntities())
            if (!(e instanceof net.minecraft.world.entity.player.Player) && !e.isRemoved()
                    && (net.jj.giants.tools.Tools.kindOf(e) != null || FunGiants.kindOf(e) != null)) out.add(e);
        return out;
    }

    /** a player's blow on one of the round's giants: who hit him last and how hard */
    void hurt(G g, UUID by, double amount) {
        g.lastBy = by;
        g.lastAt = game.server.getTickCount();
        g.dealt.merge(by, amount, Double::sum);
        g.hits.merge(by, 1, Integer::sum);
    }
}
