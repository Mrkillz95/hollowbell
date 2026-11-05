package net.jj.giants.adventure;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.Marks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.4) Command hooks: when a giant a hook's target matches has the hook's event, its
 * command runs once, as the server with @s = the giant and at his place, with the permission level of whoever added
 * the hook (a command block: 2). The Guide watches every giant standing in loaded land (and in an adventure round)
 * twice a second; the giants themselves are not changed. Also the scoreboard: giants_health (each giant's health in %),
 * giants_alive (how many of each kind stand, and "all") and giants_events (how often each event has fired).
 */
public final class Hooks {
    private Hooks() {}

    /** every event: its word and when it fires */
    public static final String[][] EVENTS = {
        {"dies", "he dies"},
        {"wakes", "he wakes up"},
        {"sleeps", "he lies down to sleep"},
        {"fight", "he starts a fight (has someone to go for)"},
        {"calm", "his fight is over"},
        {"half", "his health drops below half"},
        {"quarter", "his health drops below a quarter"},
        {"tamed", "a player takes his book (he has an owner now)"},
        {"enters", "he walks into an area (enters:<area> for one area)"},
        {"leaves", "he walks out of an area (leaves:<area> for one area)"},
        {"sees", "he sees a player (in sight, within 48 blocks x his size)"}};

    public static final String HEALTH = "giants_health", ALIVE = "giants_alive", EVENTS_OBJ = "giants_events";

    static boolean known(String event) {
        String e = event.toLowerCase(Locale.ROOT);
        int c = e.indexOf(':');
        String head = c < 0 ? e : e.substring(0, c);
        if (c >= 0 && !(head.equals("enters") || head.equals("leaves"))) return false;
        if (c >= 0 && AdvData.name(e.substring(c + 1)) == null) return false;
        for (String[] v : EVENTS) if (v[0].equals(head)) return true;
        return false;
    }

    /** what the Guide saw of one giant last time */
    static final class Seen {
        boolean asleep, fighting, half, quarter, owned, seeing, dead, lastDying;
        /** where he was last seen, and his "dies" hooks then (for a death that takes him out of the world) */
        @Nullable Vec3 pos;
        @Nullable ServerLevel level;
        List<AdvData.Hook> dies = new ArrayList<>();
        String holder = "";
        Set<String> areas = new HashSet<>();
        long seeingSince;
        boolean first = true;
    }

    static final Map<UUID, Seen> seen = new HashMap<>();
    /** hook commands running now (a hook's command can't set off hooks of its own more than this deep) */
    private static int depth;
    private static int ranThisTick, tickOf = -1;

    static void forget() {
        seen.clear();
        depth = 0;
    }

    /** the hooks that apply in this giant's world: an adventure round's world has only its map's own */
    static List<AdvData.Hook> hooksFor(MinecraftServer server, LivingEntity e) {
        Adventure.Round r = Adventure.round;
        if (r != null && e.level() == r.level) return r.state.hooks;
        return AdvData.get(server).s.hooks;
    }

    /** whether a hook's target matches him: all, his kind, a giant tag (Part 7), an entity tag, a script, or his id */
    static boolean matches(String target, LivingEntity e) {
        String t = target.toLowerCase(Locale.ROOT);
        if (t.equals("all")) return true;
        String kind = FunGiants.kindOf(e);
        if (t.equals(kind)) return true;
        if (t.equals(e.getStringUUID())) return true;
        String wears = Marks.wears(e);
        if (wears != null && wears.equalsIgnoreCase(target)) return true;
        for (String tag : e.getTags()) if (tag.equalsIgnoreCase(target)) return true;
        if (t.startsWith("script:") || t.startsWith("script.")) {
            AdvData.Run r = Adventure.runOf(e);
            return r != null && r.script.equals(t.substring(7));
        }
        return false;
    }

    // watching

    static void tick(MinecraftServer server) {
        int now = server.getTickCount();
        if (now % 10 != 7) return;
        AdvData d = AdvData.get(server);
        Adventure.Round round = Adventure.round;
        boolean any = !d.s.hooks.isEmpty() || (round != null && !round.state.hooks.isEmpty());
        if (!any && !d.s.scores) {
            if (!seen.isEmpty()) seen.clear();
            return;
        }
        Map<String, Map<String, JsonObject>> rows = new HashMap<>();
        Set<UUID> here = new HashSet<>();
        Map<String, Integer> alive = new LinkedHashMap<>();
        for (LivingEntity e : FunGiants.standing(server, null)) {
            here.add(e.getUUID());
            String kind = FunGiants.kindOf(e);
            alive.merge(kind, 1, Integer::sum);
            alive.merge("all", 1, Integer::sum);
            if (d.s.scores) score(server, HEALTH, e.getScoreboardName(),
                    (int) Math.round(e.getHealth() / Math.max(1, e.getMaxHealth()) * 100));
            if (!any) continue;
            List<AdvData.Hook> hs = hooksFor(server, e);
            boolean mine = false;
            for (AdvData.Hook h : hs) if (matches(h.target, e)) mine = true;
            if (!mine) continue;
            look(server, e, rows, hs);
        }
        // the ones that died where nothing saw it (his own death that ends with him leaving the world)
        for (UUID id : List.copyOf(seen.keySet()))
            if (!here.contains(id)) {
                Seen s = seen.remove(id);
                if (s != null && !s.holder.isEmpty()) {
                    Objective ho = server.getScoreboard().getObjective(HEALTH);
                    if (ho != null) server.getScoreboard().resetSinglePlayerScore(ScoreHolder.forNameOnly(s.holder), ho);
                }
                // (his own death that took him out of the world: his hooks run where he was, @s nobody)
                if (s != null && !s.dead && s.lastDying) fireGone(server, s);
            }
        if (d.s.scores) {
            Scoreboard sb = server.getScoreboard();
            Objective o = sb.getObjective(ALIVE);
            if (o != null) {
                for (net.jj.giants.Giant g : AdminServer.giants()) alive.putIfAbsent(g.key(), 0);
                alive.putIfAbsent("all", 0);
                for (var en : alive.entrySet()) sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(en.getKey()), o)
                        .set(en.getValue());
            }
        }
    }

    /** one giant, looked at: the events since last time */
    private static void look(MinecraftServer server, LivingEntity e, Map<String, Map<String, JsonObject>> rows,
            List<AdvData.Hook> hs) {
        Seen s = seen.computeIfAbsent(e.getUUID(), k -> new Seen());
        s.pos = e.position();
        s.level = e.level() instanceof ServerLevel sl ? sl : null;
        s.holder = e.getScoreboardName();
        s.dies.clear();
        for (AdvData.Hook h : hs) if (h.event.equals("dies") && matches(h.target, e)) s.dies.add(h);
        boolean needRow = false;
        for (AdvData.Hook h : hs)
            if (matches(h.target, e) && (h.event.equals("wakes") || h.event.equals("sleeps")
                    || h.event.equals("fight") || h.event.equals("calm") || h.event.equals("tamed"))) needRow = true;
        JsonObject row = needRow ? AdvGiants.row(server, e, rows) : null;
        boolean asleep = row != null && AdvGiants.bool(row, "asleep", false);
        boolean fighting = row != null && row.has("fighting") ? AdvGiants.bool(row, "fighting", false)
                : e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive();
        boolean owned = row != null && !AdminJson.str(row, "owner", 64).isEmpty();
        double hp = e.getHealth() / Math.max(1, e.getMaxHealth());
        boolean half = hp < 0.5, quarter = hp < 0.25;
        Set<String> areas = areasOf(server, e);
        ServerPlayer sees = sees(e);
        s.lastDying = dying(e);
        if (s.first) {
            // (the first look only learns how he is)
            s.first = false;
            s.asleep = asleep;
            s.fighting = fighting;
            s.owned = owned;
            s.half = half;
            s.quarter = quarter;
            s.areas = areas;
            s.seeing = sees != null;
            return;
        }
        Map<String, String> more = new HashMap<>();
        if (sees != null) more.put("player", sees.getGameProfile().getName());
        if (s.lastDying && !s.dead) {
            s.dead = true;
            fire(server, e, "dies", more);
            return;
        }
        if (asleep != s.asleep) fire(server, e, asleep ? "sleeps" : "wakes", more);
        if (fighting != s.fighting) fire(server, e, fighting ? "fight" : "calm", more);
        if (owned && !s.owned) fire(server, e, "tamed", more);
        if (half && !s.half) fire(server, e, "half", more);
        if (quarter && !s.quarter) fire(server, e, "quarter", more);
        for (String a : areas)
            if (!s.areas.contains(a)) {
                Map<String, String> m = new HashMap<>(more);
                m.put("area", a);
                fire(server, e, "enters", m);
                fire(server, e, "enters:" + a, m);
            }
        for (String a : s.areas)
            if (!areas.contains(a)) {
                Map<String, String> m = new HashMap<>(more);
                m.put("area", a);
                fire(server, e, "leaves", m);
                fire(server, e, "leaves:" + a, m);
            }
        // sees a player: once, then again only after nobody was in sight for ten seconds
        long now = server.getTickCount();
        if (sees != null) {
            if (!s.seeing) fire(server, e, "sees", more);
            s.seeing = true;
            s.seeingSince = now;
        } else if (s.seeing && now - s.seeingSince > 200) s.seeing = false;
        s.asleep = asleep;
        s.fighting = fighting;
        s.owned = owned;
        // (half and quarter fire again only after he's healed back over)
        s.half = half;
        s.quarter = quarter;
        s.areas = areas;
    }

    /** a giant died (the game's own death): his "dies" hooks, once */
    static void died(MinecraftServer server, LivingEntity e) {
        if (FunGiants.kindOf(e) == null) return;
        Seen s = seen.computeIfAbsent(e.getUUID(), k -> new Seen());
        if (s.dead) return;
        s.dead = true;
        s.first = false;
        fire(server, e, "dies", Map.of());
    }

    private static void fireGone(MinecraftServer server, Seen s) {
        s.dead = true;
        for (AdvData.Hook h : List.copyOf(s.dies)) {
            h.fired++;
            h.last = System.currentTimeMillis();
            AdvData.get(server).setDirty();
            run(server, null, h.command, h.level, Map.of(), "hook " + h.id, s.level, s.pos);
        }
        Adventure.changed();
    }

    /** the areas he stands in now (the world's and the round's) */
    static Set<String> areasOf(MinecraftServer server, LivingEntity e) {
        Set<String> out = new HashSet<>();
        String dim = e.level().dimension().location().toString();
        Adventure.Round r = Adventure.round;
        Map<String, AdvData.Area> as = r != null && e.level() == r.level ? r.state.areas : AdvData.get(server).s.areas;
        for (var a : as.entrySet()) if (a.getValue().has(dim, e.getX(), e.getY(), e.getZ())) out.add(a.getKey());
        return out;
    }

    /** a player he can see (in his line of sight from his eyes, within 48 blocks times his size, at least 16) */
    static @Nullable ServerPlayer sees(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel l)) return null;
        double far = Math.max(16, 48 * FunGiants.sizeOf(e));
        Vec3 eye = e.getEyePosition();
        ServerPlayer best = null;
        double bd = far * far;
        for (ServerPlayer p : l.players()) {
            if (p.isSpectator() || !p.isAlive()) continue;
            double d = p.distanceToSqr(eye);
            if (d > bd) continue;
            var hit = l.clip(new ClipContext(eye, p.getEyePosition(), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                    e));
            if (hit.getType() != HitResult.Type.MISS) continue;
            bd = d;
            best = p;
        }
        return best;
    }

    private static final Map<Class<?>, Method> SIT = new HashMap<>();

    /** dying: the game's own death, or his own death that ends with him leaving the world */
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

    // firing

    /** every hook for this event whose target matches him runs its command */
    static void fire(MinecraftServer server, LivingEntity e, String event, Map<String, String> more) {
        boolean any = false;
        for (AdvData.Hook h : List.copyOf(hooksFor(server, e)))
            if (h.event.equalsIgnoreCase(event) && matches(h.target, e)) {
                runHook(server, e, h, more);
                any = true;
            }
        if (any || !event.contains(":")) {
            AdvData d = AdvData.get(server);
            if (d.s.scores && !event.contains(":")) {
                Objective o = server.getScoreboard().getObjective(EVENTS_OBJ);
                if (o != null) server.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(event), o).add(1);
            }
        }
    }

    private static void runHook(MinecraftServer server, @Nullable LivingEntity e, AdvData.Hook h,
            Map<String, String> more) {
        h.fired++;
        h.last = System.currentTimeMillis();
        if (AdvData.get(server).s.hooks.contains(h)) AdvData.get(server).setDirty();
        run(server, e, h.command, h.level, more, "hook " + h.id);
        Adventure.changed();
    }

    /**
     * Runs a command as the server, @s = the giant, at his place, with this permission level (never more than the
     * server's own); {giant} {name} {id} {x} {y} {z} {health} {player} {area} put in. At most 64 a tick, 4 deep.
     */
    static void run(MinecraftServer server, @Nullable LivingEntity e, String command, int level,
            Map<String, String> more, String what) {
        run(server, e, command, level, more, what, null, null);
    }

    static void run(MinecraftServer server, @Nullable LivingEntity e, String command, int level,
            Map<String, String> more, String what, @Nullable ServerLevel at, @Nullable Vec3 pos) {
        int tick = server.getTickCount();
        if (tick != tickOf) {
            tickOf = tick;
            ranThisTick = 0;
        }
        if (depth >= 4 || ++ranThisTick > 64) {
            GiantsGuideMod.LOG.warn("Giants adventure: {} didn't run (too many at once): {}", what, command);
            return;
        }
        String cmd = fill(server, e, command, more);
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        CommandSourceStack src = server.createCommandSourceStack().withPermission(Math.max(0, Math.min(4, level)))
                .withSuppressedOutput();
        if (e != null && e.level() instanceof ServerLevel l)
            src = src.withEntity(e).withLevel(l).withPosition(e.position()).withRotation(e.getRotationVector());
        else if (at != null && pos != null) src = src.withLevel(at).withPosition(pos);
        src = src.withSource(net.minecraft.commands.CommandSource.NULL);
        depth++;
        try {
            server.getCommands().performPrefixedCommand(src, cmd);
        } catch (RuntimeException ex) {
            GiantsGuideMod.LOG.warn("Giants adventure: {} failed: {} ({})", what, cmd, ex.toString());
        } finally {
            depth--;
        }
    }

    static String fill(MinecraftServer server, @Nullable LivingEntity e, String command, Map<String, String> more) {
        String c = command;
        if (e != null) {
            String kind = FunGiants.kindOf(e);
            net.jj.giants.Giant g = kind == null ? null : AdminServer.giant(kind);
            c = c.replace("{giant}", kind == null ? "giant" : kind)
                    .replace("{name}", g == null ? "giant" : e.getDisplayName().getString())
                    .replace("{id}", e.getStringUUID())
                    .replace("{x}", String.valueOf((int) Math.floor(e.getX())))
                    .replace("{y}", String.valueOf((int) Math.floor(e.getY())))
                    .replace("{z}", String.valueOf((int) Math.floor(e.getZ())))
                    .replace("{health}", String.valueOf(Math.round(e.getHealth() / Math.max(1, e.getMaxHealth())
                            * 100)));
        }
        String player = more.get("player");
        if (player == null && e != null) {
            ServerPlayer p = AdvGiants.nearest(e, 0, null);
            player = p == null ? "" : p.getGameProfile().getName();
        }
        c = c.replace("{player}", player == null ? "" : player).replace("{area}", more.getOrDefault("area", ""));
        return c;
    }

    // the scoreboard

    /** the three objectives made (on) or taken away (off) */
    static void scores(MinecraftServer server, boolean on) {
        Scoreboard sb = server.getScoreboard();
        String[][] objs = {{HEALTH, "Giant health %"}, {ALIVE, "Giants standing"}, {EVENTS_OBJ, "Giant events"}};
        for (String[] o : objs) {
            Objective have = sb.getObjective(o[0]);
            if (on && have == null) sb.addObjective(o[0], ObjectiveCriteria.DUMMY, Component.literal(o[1]),
                    ObjectiveCriteria.RenderType.INTEGER, false, null);
            if (!on && have != null) sb.removeObjective(have);
        }
    }

    private static void score(MinecraftServer server, String obj, String holder, int v) {
        Scoreboard sb = server.getScoreboard();
        Objective o = sb.getObjective(obj);
        if (o != null) sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(holder), o).set(v);
    }
}
