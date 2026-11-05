package net.jj.giants.adventure;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.net.AdminBookStatePayload;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.fun.Marks;
import net.jj.giants.minigames.AdvMode;
import net.jj.giants.minigames.Minigames;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Part 11 of the admin book (ADMIN_BRIDGE.md Â§28): adventure maps. Scripted giants (Scripts), command hooks (Hooks),
 * paths and areas placed with the Adventure Wand (Wand), and adventure maps saved from the world and played as
 * minigame rounds (AdvMaps, minigames.AdvMode), with one built in (WreckCove). Admins only (operators), from the book's
 * Adventure page or /giants admin script|path|area|wand|adventure and /giants hook; every change logged and told to the
 * other operators.
 */
public final class Adventure {
    private Adventure() {}

    public static final String PAGE = "adventure";
    /** the book's requests this part answers */
    public static final Set<String> DOS = Set.of("advwand", "advscript", "advgive", "advtake", "advrestart",
            "advpath", "advarea", "advhook", "advscores", "advplay", "advmap");

    /** an adventure round on now: its world and its own paths, areas, scripts, runs and hooks (in its coordinates) */
    public static final class Round {
        public final ServerLevel level;
        public final AdvData.State state;

        Round(ServerLevel level, AdvData.State state) {
            this.level = level;
            this.state = state;
        }
    }

    public static @Nullable Round round;

    public static void register() {
        Wand.register();
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> AdvCommand.register(d));
        ServerTickEvents.END_SERVER_TICK.register(Adventure::tick);
        ServerLivingEntityEvents.AFTER_DEATH.register((e, src) -> {
            if (e.getServer() != null && FunGiants.kindOf(e) != null) Hooks.died(e.getServer(), e);
        });
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            AdvData d = AdvData.get(s);
            if (d.s.scores) Hooks.scores(s, true);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
    }

    public static void forget() {
        round = null;
        Scripts.forget();
        Hooks.forget();
        Wand.forget();
        AdvMaps.forget();
        AdvGiants.forget();
        dirty = false;
    }

    static boolean dirty;

    /** the Adventure page changed: the open books get it fresh (at most twice a second) */
    public static void changed() {
        dirty = true;
    }

    static void tick(MinecraftServer server) {
        // the round's world went (the round or the game is over): its scripts and hooks with it
        Round r = round;
        if (r != null) {
            var g = Minigames.current();
            if (g == null || g.level != r.level || !(g.mode instanceof AdvMode)) round = null;
        }
        AdvMaps.tick(server);
        Scripts.tick(server);
        Hooks.tick(server);
        Wand.show(server);
        if (dirty && server.getTickCount() % 10 == 0) {
            dirty = false;
            AdminBook.changed(PAGE);
        }
    }

    /** one line in the admin change log (no undo) */
    static void log(MinecraftServer server, String who, String what, String name, String now) {
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, "all", "adventure:" + what,
                name, "", now, ""));
    }

    /** the script a giant runs (the world's or the round's), or null */
    static @Nullable AdvData.Run runOf(LivingEntity e) {
        Round r = round;
        if (r != null && e.level() == r.level) return r.state.runs.get(e.getStringUUID());
        MinecraftServer s = e.getServer();
        return s == null ? null : AdvData.get(s).s.runs.get(e.getStringUUID());
    }

    /** the adventure state a giant in this world uses */
    static AdvData.State stateFor(MinecraftServer server, @Nullable ServerLevel l) {
        Round r = round;
        return r != null && l == r.level ? r.state : AdvData.get(server).s;
    }

    // the round

    /** how a round summons one of its giants: kind, spot, mood, size, then (him, once he stands) */
    public interface Summoner {
        void summon(String kind, Vec3 at, String mood, double size, java.util.function.Consumer<LivingEntity> then);
    }

    /** an adventure round begins in its world: the map's own state there, its chests and signs, its giants */
    public static void beginRound(MinecraftServer server, ServerLevel level, AdvMaps.Loaded m, Summoner summoner) {
        AdvData.State st = AdvData.copy(m.state, AdvData.State.class);
        st.fill();
        st.runs.clear();
        String dim = level.dimension().location().toString();
        int ground = net.jj.giants.minigames.MgLevels.GROUND;
        for (AdvData.Path p : st.paths.values()) {
            p.dim = dim;
            for (double[] pt : p.pts) pt[1] = ground + pt[1];
        }
        for (AdvData.Area a : st.areas.values()) {
            a.dim = dim;
            a.y1 += ground;
            a.y2 += ground;
        }
        round = new Round(level, st);
        for (Object[] be : m.blockEntities) {
            BlockPos at = new BlockPos((int) be[0], ground + (int) be[1], (int) be[2]);
            BlockEntity b = level.getBlockEntity(at);
            if (b == null) continue;
            CompoundTag t = ((CompoundTag) be[3]).copy();
            t.putInt("x", at.getX());
            t.putInt("y", at.getY());
            t.putInt("z", at.getZ());
            try {
                b.loadWithComponents(t, server.registryAccess());
                b.setChanged();
                level.sendBlockUpdated(at, b.getBlockState(), b.getBlockState(), 3);
            } catch (RuntimeException e) {
                GiantsGuideMod.LOG.debug("Giants adventure: a saved block couldn't be put back: {}", e.toString());
            }
        }
        for (AdvMaps.MapGiant g : m.giants) {
            String kind = AdminServer.giant(g.kind) != null ? g.kind : null;
            if (kind == null) for (String k : g.or) if (AdminServer.giant(k) != null) {
                kind = k;
                break;
            }
            if (kind == null) {
                var all = AdminServer.giants();
                if (all.isEmpty()) continue;
                kind = all.get(0).key();
            }
            Round mine = round;
            summoner.summon(kind, new Vec3(g.x, 0, g.z), g.mood.isEmpty() ? "calm" : g.mood, g.size, e -> {
                for (String t : g.tags) e.addTag(t);
                if (g.protect) e.addTag(AdvMaps.PROTECT);
                if (!g.wears.isEmpty()) {
                    FunWorld.Tag tag = FunWorld.get(server).tag(g.wears);
                    if (tag != null) Marks.give(server, "Adventure", e, tag, false);
                }
                if (!g.script.isEmpty() && mine != null && mine.state.scripts.containsKey(g.script))
                    Scripts.give(server, mine.state, e, g.script, "Adventure", 2);
            });
        }
        changed();
    }

    /** a player (not watching) stands in one of the round's areas */
    public static boolean playerInRoundArea(ServerLevel level, String area) {
        Round r = round;
        if (r == null || r.level != level) return false;
        AdvData.Area a = r.state.areas.get(area);
        if (a == null) return false;
        String dim = level.dimension().location().toString();
        for (ServerPlayer p : level.players())
            if (!p.isSpectator() && p.isAlive() && a.has(dim, p.getX(), p.getY(), p.getZ())) return true;
        return false;
    }

    // paths and areas

    public static AdminServer.Result pathAdd(MinecraftServer server, String who, String name, String dim, double x,
            double y, double z) {
        String n = AdvData.name(name);
        if (n == null) return AdminServer.Result.fail("A path's name is letters, digits, _ and - (24 at most).");
        AdvData d = AdvData.get(server);
        AdvData.Path p = d.s.paths.get(n);
        if (p == null) {
            if (d.s.paths.size() >= AdvData.MOST_NAMED) return AdminServer.Result.fail("This world has 100 paths already.");
            p = new AdvData.Path();
            p.dim = dim;
            d.s.paths.put(n, p);
        }
        if (!p.dim.equals(dim)) return AdminServer.Result.fail("The path " + n + " is in another dimension.");
        if (p.pts.size() >= AdvData.MOST_POINTS) return AdminServer.Result.fail("A path has 64 points at most.");
        p.pts.add(new double[] {x, y, z});
        d.setDirty();
        changed();
        if (p.pts.size() == 1) log(server, who, "path", n, "started");
        return AdminServer.Result.said(List.of("Path " + n + ": point " + p.pts.size() + " at " + (int) Math.floor(x)
                + " " + (int) Math.floor(y) + " " + (int) Math.floor(z) + "."));
    }

    public static AdminServer.Result pathUndo(MinecraftServer server, String who, String name) {
        AdvData d = AdvData.get(server);
        AdvData.Path p = d.s.paths.get(AdvData.name(name) == null ? "" : AdvData.name(name));
        if (p == null || p.pts.isEmpty()) return AdminServer.Result.fail("The path " + name + " has no points.");
        p.pts.remove(p.pts.size() - 1);
        d.setDirty();
        changed();
        return AdminServer.Result.said(List.of("Path " + name + ": took its last point off (" + p.pts.size()
                + " left)."));
    }

    public static AdminServer.Result pathRemove(MinecraftServer server, String who, String name) {
        AdvData d = AdvData.get(server);
        if (d.s.paths.remove(name.toLowerCase(Locale.ROOT)) == null)
            return AdminServer.Result.fail("There's no path called " + name + ".");
        d.setDirty();
        log(server, who, "path", name, "removed");
        changed();
        return new AdminServer.Result(true, List.of("Removed the path " + name + "."), List.of(who
                + " removed the adventure path " + name + "."));
    }

    public static AdminServer.Result areaSet(MinecraftServer server, String who, String name, String dim, BlockPos a,
            BlockPos b) {
        String n = AdvData.name(name);
        if (n == null) return AdminServer.Result.fail("An area's name is letters, digits, _ and - (24 at most).");
        AdvData d = AdvData.get(server);
        if (!d.s.areas.containsKey(n) && d.s.areas.size() >= AdvData.MOST_NAMED)
            return AdminServer.Result.fail("This world has 100 areas already.");
        AdvData.Area ar = new AdvData.Area();
        ar.dim = dim;
        ar.x1 = a.getX();
        ar.y1 = a.getY();
        ar.z1 = a.getZ();
        ar.x2 = b.getX();
        ar.y2 = b.getY();
        ar.z2 = b.getZ();
        if (ar.sx() > 2000 || ar.sz() > 2000) return AdminServer.Result.fail("An area is 2,000 blocks across at most.");
        d.s.areas.put(n, ar);
        d.setDirty();
        log(server, who, "area", n, ar.sx() + "x" + ar.sy() + "x" + ar.sz());
        changed();
        return new AdminServer.Result(true, List.of("Area " + n + ": " + ar.sx() + " x " + ar.sy() + " x " + ar.sz()
                + " blocks."), List.of(who + " set the adventure area " + n + "."));
    }

    public static AdminServer.Result areaRemove(MinecraftServer server, String who, String name) {
        AdvData d = AdvData.get(server);
        if (d.s.areas.remove(name.toLowerCase(Locale.ROOT)) == null)
            return AdminServer.Result.fail("There's no area called " + name + ".");
        d.setDirty();
        log(server, who, "area", name, "removed");
        changed();
        return new AdminServer.Result(true, List.of("Removed the area " + name + "."), List.of(who
                + " removed the adventure area " + name + "."));
    }

    // scripts

    /** adds a step at the end (n = 0) or puts it in place of step n */
    public static AdminServer.Result scriptStep(MinecraftServer server, String who, String name, int n, String line) {
        String k = AdvData.name(name);
        if (k == null) return AdminServer.Result.fail("A script's name is letters, digits, _ and - (24 at most).");
        String bad = Scripts.check(line);
        if (bad != null) return AdminServer.Result.fail(bad);
        AdvData d = AdvData.get(server);
        AdvData.Script sc = d.s.scripts.get(k);
        if (sc == null) {
            if (d.s.scripts.size() >= AdvData.MOST_NAMED) return AdminServer.Result.fail("This world has 100 scripts already.");
            sc = new AdvData.Script();
            d.s.scripts.put(k, sc);
        }
        String clean = line.strip().replaceAll("\\s+", " ");
        if (n > 0) {
            if (n > sc.steps.size()) return AdminServer.Result.fail("The script " + k + " has " + sc.steps.size()
                    + " steps.");
            sc.steps.set(n - 1, clean);
        } else {
            if (sc.steps.size() >= AdvData.MOST_STEPS) return AdminServer.Result.fail("A script has 40 steps at most.");
            sc.steps.add(clean);
        }
        d.setDirty();
        log(server, who, "script", k, (n > 0 ? "step " + n + ": " : "+ ") + clean);
        changed();
        int at = n > 0 ? n : sc.steps.size();
        return new AdminServer.Result(true, List.of("Script " + k + ", step " + at + ": " + clean),
                List.of(who + " changed the script " + k + "."));
    }

    public static AdminServer.Result scriptRemoveStep(MinecraftServer server, String who, String name, int n) {
        AdvData d = AdvData.get(server);
        AdvData.Script sc = d.s.scripts.get(name.toLowerCase(Locale.ROOT));
        if (sc == null) return AdminServer.Result.fail("There's no script called " + name + ".");
        if (n < 1 || n > sc.steps.size()) return AdminServer.Result.fail("The script " + name + " has "
                + sc.steps.size() + " steps.");
        String was = sc.steps.remove(n - 1);
        d.setDirty();
        log(server, who, "script", name, "- " + was);
        changed();
        return AdminServer.Result.said(List.of("Script " + name + ": took off step " + n + " (" + was + ")."));
    }

    public static AdminServer.Result scriptDelete(MinecraftServer server, String who, String name) {
        AdvData d = AdvData.get(server);
        String k = name.toLowerCase(Locale.ROOT);
        if (d.s.scripts.remove(k) == null) return AdminServer.Result.fail("There's no script called " + name + ".");
        int n = 0;
        for (var e : List.copyOf(d.s.runs.entrySet()))
            if (e.getValue().script.equals(k)) {
                Scripts.take(d.s, e.getKey(), FunGiants.find(server, e.getKey()));
                n++;
            }
        d.setDirty();
        log(server, who, "script", k, "deleted");
        changed();
        return new AdminServer.Result(true, List.of("Deleted the script " + k + (n > 0 ? " (" + n
                + (n == 1 ? " giant was" : " giants were") + " running it)." : ".")), List.of(who
                + " deleted the script " + k + "."));
    }

    public static AdminServer.Result give(MinecraftServer server, String who, int level, LivingEntity e, String script) {
        AdvData d = AdvData.get(server);
        String k = script.toLowerCase(Locale.ROOT);
        AdvData.State st = stateFor(server, (ServerLevel) e.level());
        if (!st.scripts.containsKey(k)) return AdminServer.Result.fail("There's no script called " + script + ".");
        Scripts.give(server, st, e, k, who, level);
        if (st == d.s) d.setDirty();
        log(server, who, "script", k, "given to " + FunGiants.label(server, e));
        changed();
        String name = FunGiants.label(server, e);
        return new AdminServer.Result(true, List.of(name + " runs the script " + k + " now."),
                List.of(who + " gave a " + name + " the script " + k + "."));
    }

    public static AdminServer.Result take(MinecraftServer server, String who, LivingEntity e) {
        AdvData.State st = stateFor(server, (ServerLevel) e.level());
        String name = FunGiants.label(server, e);
        if (!st.runs.containsKey(e.getStringUUID())) return AdminServer.Result.fail(name + " runs no script.");
        Scripts.take(st, e.getStringUUID(), e);
        AdvData.get(server).setDirty();
        log(server, who, "script", "-", "taken from " + name);
        changed();
        return new AdminServer.Result(true, List.of(name + " runs no script now (he's free again)."),
                List.of(who + " took the script off a " + name + "."));
    }

    public static AdminServer.Result restart(MinecraftServer server, String who, LivingEntity e) {
        AdvData.State st = stateFor(server, (ServerLevel) e.level());
        AdvData.Run r = st.runs.get(e.getStringUUID());
        String name = FunGiants.label(server, e);
        if (r == null) return AdminServer.Result.fail(name + " runs no script.");
        r.step = 0;
        r.done = false;
        Scripts.live.remove(e.getUUID());
        AdvData.get(server).setDirty();
        changed();
        return AdminServer.Result.said(List.of(name + " starts the script " + r.script + " again."));
    }

    // hooks

    public static AdminServer.Result hookAdd(MinecraftServer server, String who, int level, String target, String event,
            String command) {
        String t = target.strip();
        String ev = event.strip().toLowerCase(Locale.ROOT).replace('.', ':');
        if (t.isEmpty() || t.length() > 48 || t.contains(" "))
            return AdminServer.Result.fail("The target is all, a giant (wreckback), a tag, script:<name> or an id.");
        if (!Hooks.known(ev)) return AdminServer.Result.fail("No event called " + event + ". Events: dies, wakes,"
                + " sleeps, fight, calm, half, quarter, tamed, enters, leaves, sees (enters:<area>, leaves:<area>).");
        String cmd = command.strip();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty() || cmd.length() > 400) return AdminServer.Result.fail("The command is 1 to 400 letters.");
        AdvData d = AdvData.get(server);
        if (d.s.hooks.size() >= AdvData.MOST_HOOKS) return AdminServer.Result.fail("This world has 200 hooks already.");
        AdvData.Hook h = new AdvData.Hook();
        h.id = d.s.nextHook++;
        h.target = t;
        h.event = ev;
        h.command = cmd;
        h.by = who;
        h.level = Math.max(0, Math.min(4, level));
        d.s.hooks.add(h);
        d.setDirty();
        log(server, who, "hook", "#" + h.id, t + " " + ev + ": " + cmd);
        changed();
        return new AdminServer.Result(true, List.of("Hook " + h.id + ": when " + t + " " + words(ev) + ", it runs: "
                + cmd + " (as the server, @s = the giant, permission level " + h.level + ")."),
                List.of(who + " added hook " + h.id + " (" + t + " " + ev + ")."));
    }

    static String words(String ev) {
        String head = ev.contains(":") ? ev.substring(0, ev.indexOf(':')) : ev;
        String area = ev.contains(":") ? ev.substring(ev.indexOf(':') + 1) : "";
        return switch (head) {
            case "dies" -> "dies";
            case "wakes" -> "wakes";
            case "sleeps" -> "lies down to sleep";
            case "fight" -> "starts a fight";
            case "calm" -> "ends a fight";
            case "half" -> "drops below half health";
            case "quarter" -> "drops below a quarter health";
            case "tamed" -> "is tamed";
            case "enters" -> area.isEmpty() ? "enters an area" : "enters the area " + area;
            case "leaves" -> area.isEmpty() ? "leaves an area" : "leaves the area " + area;
            case "sees" -> "sees a player";
            default -> ev;
        };
    }

    public static AdminServer.Result hookRemove(MinecraftServer server, String who, int id) {
        AdvData d = AdvData.get(server);
        AdvData.Hook h = d.hook(id);
        if (h == null) return AdminServer.Result.fail("There's no hook " + id + ".");
        d.s.hooks.remove(h);
        d.setDirty();
        log(server, who, "hook", "#" + id, "removed");
        changed();
        return new AdminServer.Result(true, List.of("Removed hook " + id + "."), List.of(who + " removed hook " + id
                + "."));
    }

    /** runs a hook's command now on the nearest giant it matches (to try it) */
    public static AdminServer.Result hookTest(MinecraftServer server, ServerLevel level, Vec3 at, int id) {
        AdvData.Hook h = AdvData.get(server).hook(id);
        if (h == null) return AdminServer.Result.fail("There's no hook " + id + ".");
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : FunGiants.standing(server, null)) {
            if (!Hooks.matches(h.target, e)) continue;
            double d = e.position().distanceToSqr(at) + (e.level() == level ? 0 : 1e18);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return AdminServer.Result.fail("No giant standing now matches " + h.target + ".");
        Hooks.run(server, best, h.command, h.level, Map.of(), "hook " + id + " (test)");
        return AdminServer.Result.said(List.of("Ran hook " + id + " once on a " + FunGiants.label(server, best) + "."));
    }

    public static AdminServer.Result scores(MinecraftServer server, String who, boolean on) {
        AdvData d = AdvData.get(server);
        d.s.scores = on;
        d.setDirty();
        Hooks.scores(server, on);
        log(server, who, "scores", "scoreboard", on ? "on" : "off");
        changed();
        return AdminServer.Result.said(List.of(on ? "Scoreboard on: giants_health (each giant's health %),"
                + " giants_alive (how many of each kind stand, and all), giants_events (how often each event fired)."
                : "Scoreboard off: the giants_ objectives are gone."));
    }

    // maps

    public static AdminServer.Result play(MinecraftServer server, String who, String map, boolean everyone,
            int rounds) {
        String n = map.toLowerCase(Locale.ROOT);
        AdvData.MapInfo info = AdvData.get(server).s.maps.get(n);
        if (!n.equals(AdvMaps.BUILT_IN) && info == null)
            return AdminServer.Result.fail("There's no adventure map called " + map + ".");
        String goal = n.equals(AdvMaps.BUILT_IN) ? "Bring the lost giant home safe to the harbour."
                : info.goal.isEmpty() ? "Find your way through." : info.goal;
        return Minigames.start(server, who, new AdvMode(n, AdvMaps.title(n), goal), everyone, rounds);
    }

    public static AdminServer.Result mapSet(MinecraftServer server, String who, String map, String what, String value) {
        AdvData d = AdvData.get(server);
        AdvData.MapInfo info = d.s.maps.get(map.toLowerCase(Locale.ROOT));
        if (info == null) return AdminServer.Result.fail("There's no saved map called " + map + ".");
        String v = value == null ? "" : value.strip();
        switch (what) {
            case "goal" -> {
                if (v.isEmpty() || v.length() > 200) return AdminServer.Result.fail("The goal is 1 to 200 letters.");
                info.goal = v;
            }
            case "timeup" -> {
                if (!v.equals("win") && !v.equals("lose")) return AdminServer.Result.fail("Time up: win or lose.");
                info.timeUp = v;
            }
            case "winarea" -> {
                if (v.equals("none") || v.isEmpty()) v = "";
                else if (AdvData.name(v) == null) return AdminServer.Result.fail("An area's name, or none.");
                info.winArea = v.toLowerCase(Locale.ROOT);
            }
            default -> {
                return AdminServer.Result.fail("A map has goal, timeup and winarea.");
            }
        }
        d.setDirty();
        log(server, who, "map", info.name, what + " " + v);
        changed();
        return AdminServer.Result.said(List.of("Map " + info.name + ": " + what + " " + (v.isEmpty() ? "none" : v)
                + "."));
    }

    // the book

    public static AdminBookStatePayload page(MinecraftServer server, @Nullable ServerPlayer p) {
        return new AdminBookStatePayload(PAGE, state(server, p).toString());
    }

    public static JsonObject state(MinecraftServer server, @Nullable ServerPlayer p) {
        AdvData d = AdvData.get(server);
        JsonObject o = new JsonObject();
        if (p != null) {
            Wand.Setting w = Wand.of(p);
            JsonObject wj = new JsonObject();
            wj.addProperty("mode", w.mode);
            wj.addProperty("name", w.name);
            wj.addProperty("first", w.first != null);
            o.add("wand", wj);
        }
        o.addProperty("saving", AdvMaps.saving());
        o.addProperty("scores", d.s.scores);
        var g = Minigames.current();
        o.addProperty("busy", g != null);
        o.addProperty("playing", g != null && g.mode instanceof AdvMode ? g.mode.name() : "");
        JsonArray maps = new JsonArray();
        JsonObject b = new JsonObject();
        b.addProperty("name", AdvMaps.BUILT_IN);
        b.addProperty("title", AdvMaps.title(AdvMaps.BUILT_IN));
        b.addProperty("builtIn", true);
        b.addProperty("goal", "A young giant is lost in Wreck Cove, where a Wreckback guards the wreck. Reach him,"
                + " then bring him home along the shore road while the guard comes for you.");
        maps.add(b);
        for (AdvData.MapInfo m : d.s.maps.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", m.name);
            j.addProperty("title", AdvMaps.title(m.name));
            j.addProperty("goal", m.goal);
            j.addProperty("by", m.by);
            j.addProperty("time", m.time);
            j.addProperty("size", m.sx + " x " + m.sy + " x " + m.sz);
            j.addProperty("giants", m.giants);
            j.addProperty("timeUp", m.timeUp);
            j.addProperty("winArea", m.winArea);
            maps.add(j);
        }
        o.add("maps", maps);
        JsonArray scripts = new JsonArray();
        for (var e : d.s.scripts.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", e.getKey());
            JsonArray steps = new JsonArray();
            for (String s : e.getValue().steps) steps.add(s);
            j.add("steps", steps);
            int n = 0;
            for (AdvData.Run r : d.s.runs.values()) if (r.script.equals(e.getKey())) n++;
            j.addProperty("giants", n);
            scripts.add(j);
        }
        o.add("scripts", scripts);
        JsonArray runs = new JsonArray();
        for (var e : d.s.runs.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", e.getKey());
            j.addProperty("kind", e.getValue().kind);
            j.addProperty("script", e.getValue().script);
            j.addProperty("where", Scripts.where(d.s, e.getValue()));
            LivingEntity le = FunGiants.find(server, e.getKey());
            j.addProperty("label", le == null ? AdminServer.giant(e.getValue().kind) == null ? e.getValue().kind
                    : AdminServer.nameOf(AdminServer.giant(e.getValue().kind)) + " (not loaded)"
                    : FunGiants.label(server, le));
            j.addProperty("here", le != null);
            runs.add(j);
        }
        o.add("runs", runs);
        JsonArray paths = new JsonArray();
        for (var e : d.s.paths.entrySet()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", e.getKey());
            j.addProperty("points", e.getValue().pts.size());
            j.addProperty("dim", e.getValue().dim);
            double len = 0;
            for (int i = 1; i < e.getValue().pts.size(); i++) {
                double[] a = e.getValue().pts.get(i - 1), c = e.getValue().pts.get(i);
                len += Math.hypot(c[0] - a[0], c[2] - a[2]);
            }
            j.addProperty("length", Math.round(len));
            if (!e.getValue().pts.isEmpty()) {
                double[] f = e.getValue().pts.get(0);
                j.addProperty("at", (int) f[0] + " " + (int) f[1] + " " + (int) f[2]);
            }
            paths.add(j);
        }
        o.add("paths", paths);
        JsonArray areas = new JsonArray();
        for (var e : d.s.areas.entrySet()) {
            AdvData.Area a = e.getValue();
            JsonObject j = new JsonObject();
            j.addProperty("name", e.getKey());
            j.addProperty("size", a.sx() + " x " + a.sy() + " x " + a.sz());
            j.addProperty("at", Math.min(a.x1, a.x2) + " " + Math.min(a.y1, a.y2) + " " + Math.min(a.z1, a.z2));
            areas.add(j);
        }
        o.add("areas", areas);
        JsonArray hooks = new JsonArray();
        for (AdvData.Hook h : d.s.hooks) {
            JsonObject j = new JsonObject();
            j.addProperty("id", h.id);
            j.addProperty("target", h.target);
            j.addProperty("event", h.event);
            j.addProperty("words", words(h.event));
            j.addProperty("command", h.command);
            j.addProperty("by", h.by);
            j.addProperty("level", h.level);
            j.addProperty("fired", h.fired);
            hooks.add(j);
        }
        o.add("hooks", hooks);
        JsonArray verbs = new JsonArray();
        for (String[] v : Scripts.VERBS) {
            JsonArray a = new JsonArray();
            for (String s : v) a.add(s);
            verbs.add(a);
        }
        o.add("verbs", verbs);
        JsonArray events = new JsonArray();
        for (String[] v : Hooks.EVENTS) {
            JsonArray a = new JsonArray();
            for (String s : v) a.add(s);
            events.add(a);
        }
        o.add("events", events);
        return o;
    }

    /** the permission level a player has on this server (0-4) */
    static int levelOf(ServerPlayer p) {
        return p.server.getProfilePermissions(p.getGameProfile());
    }

    /** the book's requests (Admins only: AdminBook checked the role) */
    public static @Nullable AdminServer.Result request(MinecraftServer server, ServerPlayer p, String who, String what,
            JsonObject o) {
        return switch (what) {
            case "advwand" -> Wand.set(p, AdminJson.str(o, "mode", 8), AdminJson.str(o, "name", 32));
            case "advscript" -> {
                String name = AdminJson.str(o, "name", 32), op = AdminJson.str(o, "op", 8);
                int n = (int) AdminJson.num(o, "n", 0);
                yield switch (op) {
                    case "add" -> scriptStep(server, who, name, 0, AdminJson.str(o, "line", 300));
                    case "set" -> scriptStep(server, who, name, n, AdminJson.str(o, "line", 300));
                    case "remove" -> scriptRemoveStep(server, who, name, n);
                    case "delete" -> scriptDelete(server, who, name);
                    default -> AdminServer.Result.fail("The book asked for something the server doesn't know.");
                };
            }
            case "advgive", "advtake", "advrestart" -> {
                LivingEntity e = giant(server, p, AdminJson.str(o, "giant", 64));
                if (e == null) yield AdminServer.Result.fail(whyNot(server, p, AdminJson.str(o, "giant", 64)));
                yield what.equals("advgive") ? give(server, who, levelOf(p), e, AdminJson.str(o, "script", 32))
                        : what.equals("advtake") ? take(server, who, e) : restart(server, who, e);
            }
            case "advpath" -> pathRemove(server, who, AdminJson.str(o, "name", 32));
            case "advarea" -> areaRemove(server, who, AdminJson.str(o, "name", 32));
            case "advhook" -> switch (AdminJson.str(o, "op", 8)) {
                case "add" -> hookAdd(server, who, levelOf(p), AdminJson.str(o, "target", 64),
                        AdminJson.str(o, "event", 40), AdminJson.str(o, "command", 400));
                case "remove" -> hookRemove(server, who, (int) AdminJson.num(o, "id", -1));
                case "test" -> hookTest(server, p.serverLevel(), p.position(), (int) AdminJson.num(o, "id", -1));
                default -> AdminServer.Result.fail("The book asked for something the server doesn't know.");
            };
            case "advscores" -> scores(server, who, AdvGiants.bool(o, "on", false));
            case "advplay" -> play(server, who, AdminJson.str(o, "map", 32), AdminJson.str(o, "kind", 12)
                    .equals("everyone"), (int) AdminJson.num(o, "rounds", 0));
            case "advmap" -> {
                String op = AdminJson.str(o, "op", 8), map = AdminJson.str(o, "map", 32);
                yield op.equals("delete") ? AdvMaps.delete(server, who, map)
                        : mapSet(server, who, map, op, AdminJson.str(o, "value", 200));
            }
            default -> null;
        };
    }

    /** a giant by the book's or a command's words: "wreckback" (nearest), "wreckback 2", or his id */
    static @Nullable LivingEntity giant(MinecraftServer server, @Nullable ServerPlayer p, String words) {
        String w = words.replace('.', ' ');
        try {
            return FunGiants.find(server, UUID.fromString(w.strip()));
        } catch (IllegalArgumentException notId) {
            return FunGiants.which(server, w, p == null ? null : p.serverLevel(), p == null ? null : p.position())
                    .giant();
        }
    }

    static String whyNot(MinecraftServer server, @Nullable ServerPlayer p, String words) {
        String w = words.replace('.', ' ');
        return FunGiants.which(server, w, p == null ? null : p.serverLevel(), p == null ? null : p.position()).why();
    }

    /** (the tests) the round's state, or null */
    public static @Nullable AdvData.State roundState() {
        Round r = round;
        return r == null ? null : r.state;
    }

    /** (the tests) names of what is kept */
    public static List<String> namesForTests(MinecraftServer server) {
        List<String> out = new ArrayList<>(AdvData.get(server).s.scripts.keySet());
        out.addAll(AdvData.get(server).s.paths.keySet());
        return out;
    }
}
