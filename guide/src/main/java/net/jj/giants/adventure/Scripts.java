package net.jj.giants.adventure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.3) Scripted giants: a giant given a script does its steps one after another, every
 * half second. Steps that take time (walking a path, waiting, standing guard) hold the script there until they are
 * done; the others (a line in chat, his title, a mood, a command) are done at once. How far he got is kept with the
 * world, so a script carries on after a restart. Everything a step does to him goes through his own mod (AdvGiants).
 */
public final class Scripts {
    private Scripts() {}

    /** every step a script can have: its word, what follows it, and what it does (the book and /giants admin script) */
    public static final String[][] VERBS = {
        {"walk", "<path> [until area <area> or until players <blocks>]", "walks the path once, point by point"},
        {"escort", "<path> [blocks]", "walks the path once with the players: a few blocks at a time, at a pace they"
                + " keep up with, waiting whenever no player is that close (24)"},
        {"patrol", "<path> [until area <area> or until players <blocks>]", "walks the path round and round"},
        {"wait", "<seconds>", "stays where he is for a while"},
        {"waitfor", "players <blocks> or area <area>", "stays where he is until a player comes that close, or into the area"},
        {"guard", "[blocks] [until area <area> or until players <blocks>]", "stands guard here: goes for anyone who comes that close (24), then back to his post"},
        {"fight", "[blocks]", "starts fighting the nearest player (within 64 blocks)"},
        {"mood", "<mood>", "his mood (calm, hunting, guardian...)"},
        {"say", "<words>", "says a line in chat to the players near him (96 blocks)"},
        {"title", "<words> or clear", "the title on his boss bar"},
        {"sleep", "", "lies down to sleep"},
        {"wake", "", "wakes up"},
        {"run", "<command>", "runs a command as the server, @s = him, with the script author's permission level"},
        {"repeat", "", "starts the script again from the first step"},
        {"end", "", "the script ends here"}};

    /** what one line means, or why it can't be read (null: fine) */
    public static @Nullable String check(String line) {
        String[] w = words(line);
        if (w.length == 0) return "An empty step.";
        String v = w[0];
        switch (v) {
            case "walk", "patrol" -> {
                if (w.length < 2) return v + " needs a path: " + v + " <path>.";
                if (AdvData.name(w[1]) == null) return "A path's name is letters, digits, _ and -.";
                return until(w, 2);
            }
            case "escort" -> {
                if (w.length < 2) return "escort needs a path: escort <path> [blocks].";
                if (AdvData.name(w[1]) == null) return "A path's name is letters, digits, _ and -.";
                if (w.length > 2 && (num(w[2]) <= 0 || num(w[2]) > 128)) return "escort's blocks: 1 to 128.";
                return null;
            }
            case "wait" -> {
                if (w.length < 2 || num(w[1]) < 0 || num(w[1]) > 3600) return "wait needs seconds: wait 10 (0 to 3600).";
                return null;
            }
            case "waitfor" -> {
                if (w.length >= 3 && w[1].equals("players") && num(w[2]) > 0 && num(w[2]) <= 512) return null;
                if (w.length >= 3 && w[1].equals("area") && AdvData.name(w[2]) != null) return null;
                return "waitfor players <blocks> or waitfor area <area>.";
            }
            case "guard" -> {
                int i = 1;
                if (w.length > 1 && !w[1].equals("until")) {
                    if (num(w[1]) <= 0 || num(w[1]) > 256) return "guard's blocks: 1 to 256.";
                    i = 2;
                }
                return until(w, i);
            }
            case "fight" -> {
                if (w.length > 1 && (num(w[1]) <= 0 || num(w[1]) > 512)) return "fight's blocks: 1 to 512.";
                return null;
            }
            case "mood" -> {
                return w.length < 2 ? "mood needs a mood: mood hunting." : null;
            }
            case "say", "run" -> {
                return rest(line).isEmpty() ? v + " needs words after it." : null;
            }
            case "title" -> {
                return rest(line).isEmpty() ? "title needs words (or title clear)." : null;
            }
            case "sleep", "wake", "repeat", "end" -> {
                return null;
            }
            default -> {
                return "No step called " + v + ". Steps: walk, escort, patrol, wait, waitfor, guard, fight, mood, say, title,"
                        + " sleep, wake, run, repeat, end.";
            }
        }
    }

    private static @Nullable String until(String[] w, int i) {
        if (w.length <= i) return null;
        if (w.length == i + 3 && w[i].equals("until")) {
            if (w[i + 1].equals("area") && AdvData.name(w[i + 2]) != null) return null;
            if (w[i + 1].equals("players") && num(w[i + 2]) > 0 && num(w[i + 2]) <= 512) return null;
        }
        return "After the path: until area <area> or until players <blocks>.";
    }

    static String[] words(String line) {
        String t = line == null ? "" : line.strip();
        return t.isEmpty() ? new String[0] : t.split("\\s+");
    }

    /** everything after the step's word */
    static String rest(String line) {
        String t = line.strip();
        int i = t.indexOf(' ');
        return i < 0 ? "" : t.substring(i + 1).strip();
    }

    static double num(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // running

    /** what a giant running a script is doing now (not kept: worked out again after a restart) */
    static final class Live {
        int step = -1;
        @Nullable Vec3 anchor;
        long started;
        int pt;
        int dir = 1;
        double best = Double.MAX_VALUE;
        long bestAt;
        long lastGo;
        @Nullable UUID fighting;
        /** (escort) waiting for the players to come up */
        boolean waiting;
    }

    static final Map<UUID, Live> live = new HashMap<>();

    static void forget() {
        live.clear();
    }

    /** the script's runs: the world's, and the adventure round's */
    record Entry(UUID id, AdvData.Run run, AdvData.State in, boolean world) {}

    static List<Entry> entries(MinecraftServer server) {
        List<Entry> out = new ArrayList<>();
        AdvData d = AdvData.get(server);
        for (var e : d.s.runs.entrySet()) {
            try {
                out.add(new Entry(UUID.fromString(e.getKey()), e.getValue(), d.s, true));
            } catch (IllegalArgumentException bad) {
                // not an id
            }
        }
        Adventure.Round r = Adventure.round;
        if (r != null)
            for (var e : r.state.runs.entrySet())
                out.add(new Entry(UUID.fromString(e.getKey()), e.getValue(), r.state, false));
        return out;
    }

    static void tick(MinecraftServer server) {
        if (server.getTickCount() % 10 != 3) return;
        if (net.jj.giants.tools.Tools.on(server, "paused")) return;
        List<Entry> es = entries(server);
        if (es.isEmpty()) return;
        long now = server.getTickCount();
        boolean dirty = false;
        for (Entry en : es) {
            AdvData.Run run = en.run();
            if (run.done) continue;
            LivingEntity e = FunGiants.find(server, en.id());
            if (e == null || e.isDeadOrDying()) continue;
            AdvData.Script sc = en.in().scripts.get(run.script);
            if (sc == null || sc.steps.isEmpty()) continue;
            Live lv = live.computeIfAbsent(en.id(), k -> new Live());
            int before = run.step;
            try {
                step(server, e, run, sc, lv, en.in(), now);
            } catch (RuntimeException ex) {
                GiantsGuideMod.LOG.warn("Giants adventure: script {} on a giant stopped at step {}: {}", run.script,
                        run.step + 1, ex.toString());
                run.done = true;
            }
            if (run.step != before || run.done) dirty |= en.world();
        }
        if (dirty) {
            AdvData.get(server).setDirty();
            Adventure.changed();
        }
    }

    private static void next(AdvData.Run run, Live lv) {
        run.step++;
        lv.step = -1;
    }

    /** one look at a scripted giant: the step he is on, done or carried on */
    static void step(MinecraftServer server, LivingEntity e, AdvData.Run run, AdvData.Script sc, Live lv,
            AdvData.State in, long now) {
        // (at most a handful of quick steps in one go, so a loop of quick steps can't hold the server)
        for (int quick = 0; quick < 8; quick++) {
            if (run.step < 0) run.step = 0;
            if (run.step >= sc.steps.size()) {
                run.done = true;
                return;
            }
            String line = sc.steps.get(run.step);
            String[] w = words(line);
            if (w.length == 0 || check(line) != null) {
                next(run, lv);
                continue;
            }
            boolean fresh = lv.step != run.step;
            if (fresh) {
                lv.step = run.step;
                lv.anchor = e.position();
                lv.started = now;
                lv.pt = 0;
                lv.dir = 1;
                lv.best = Double.MAX_VALUE;
                lv.bestAt = now;
                lv.lastGo = -100;
                lv.fighting = null;
            }
            switch (w[0]) {
                case "walk", "patrol" -> {
                    if (walk(server, e, run, w, lv, in, now, w[0].equals("patrol"))) next(run, lv);
                    return;
                }
                case "escort" -> {
                    if (escort(server, e, w, lv, in, now)) next(run, lv);
                    return;
                }
                case "wait" -> {
                    hold(server, e, lv, now);
                    if (now - lv.started >= num(w[1]) * 20) next(run, lv);
                    return;
                }
                case "waitfor" -> {
                    hold(server, e, lv, now);
                    if (w[1].equals("players") ? AdvGiants.nearest(e, num(w[2]), null) != null
                            : playerIn(server, e, in, w[2])) next(run, lv);
                    return;
                }
                case "guard" -> {
                    double r = w.length > 1 && !w[1].equals("until") ? num(w[1]) : 24;
                    guard(server, e, lv, r, fresh, now);
                    int u = w.length > 1 && !w[1].equals("until") ? 2 : 1;
                    if (w.length > u && until(server, e, in, w, u)) next(run, lv);
                    return;
                }
                case "fight" -> {
                    double r = w.length > 1 ? num(w[1]) : 64;
                    AdvGiants.mood(server, e, "hunting");
                    ServerPlayer p = AdvGiants.nearest(e, r, null);
                    if (p != null) AdvGiants.turn(server, e, p);
                    next(run, lv);
                }
                case "mood" -> {
                    AdvGiants.mood(server, e, w[1].toLowerCase(Locale.ROOT));
                    next(run, lv);
                }
                case "say" -> {
                    say(e, rest(line));
                    next(run, lv);
                }
                case "title" -> {
                    String t = rest(line);
                    net.jj.giants.fun.Marks.scriptTitle(server, e, t.equalsIgnoreCase("clear") ? "" : t);
                    next(run, lv);
                }
                case "sleep" -> {
                    AdvGiants.live(server, e, "sleep");
                    next(run, lv);
                }
                case "wake" -> {
                    AdvGiants.live(server, e, "wake");
                    next(run, lv);
                }
                case "run" -> {
                    Hooks.run(server, e, rest(line), run.by.isEmpty() ? 2 : level(server, run), Map.of(),
                            "script " + run.script);
                    next(run, lv);
                }
                case "repeat" -> {
                    run.step = 0;
                    lv.step = -1;
                    // (a script of only quick steps waits a moment before going round again)
                    return;
                }
                case "end" -> {
                    run.done = true;
                    return;
                }
                default -> next(run, lv);
            }
        }
    }

    /** a script's commands run with its author's level, kept on the script's run */
    private static int level(MinecraftServer server, AdvData.Run run) {
        int i = run.by.lastIndexOf('#');
        if (i >= 0) try {
            return Math.max(0, Math.min(4, Integer.parseInt(run.by.substring(i + 1))));
        } catch (NumberFormatException e) {
            return 2;
        }
        return 2;
    }

    /** walks to the path's next point; true when he's at the end (a patrol never ends but by its until) */
    private static boolean walk(MinecraftServer server, LivingEntity e, AdvData.Run run, String[] w, Live lv,
            AdvData.State in, long now, boolean patrol) {
        AdvData.Path p = in.paths.get(w[1].toLowerCase(Locale.ROOT));
        if (p == null || p.pts.isEmpty() || !p.dim.equals(e.level().dimension().location().toString())) {
            if (now - lv.started > 200) return true;
            return false;
        }
        if (w.length > 2 && until(server, e, in, w, 2)) return true;
        int n = p.pts.size();
        if (lv.pt >= n) lv.pt = n - 1;
        double[] at = p.pts.get(lv.pt);
        double d = Math.sqrt(AdvGiants.horizontal(e.position(), new Vec3(at[0], 0, at[2])));
        if (d <= AdvGiants.near(e)) {
            if (lv.pt == n - 1 && !patrol) return true;
            // a patrol goes round: back to the first point after the last (a path that ends where it began loops)
            lv.pt = patrol ? (lv.pt + 1) % n : lv.pt + 1;
            lv.best = Double.MAX_VALUE;
            lv.bestAt = now;
            lv.lastGo = -100;
            at = p.pts.get(lv.pt);
            d = Math.sqrt(AdvGiants.horizontal(e.position(), new Vec3(at[0], 0, at[2])));
        }
        if (d < lv.best - 1) {
            lv.best = d;
            lv.bestAt = now;
        }
        if (now - lv.lastGo >= 40) {
            AdvGiants.go(server, e, at[0], at[2]);
            lv.lastGo = now;
        }
        // no nearer for fifteen seconds (stuck on something): a step along the way
        if (now - lv.bestAt > 300) {
            Vec3 to = new Vec3(at[0] - e.getX(), 0, at[2] - e.getZ());
            AdvGiants.nudge(server, e, e.position().add(to.normalize().scale(Math.min(8, to.length()))));
            lv.bestAt = now;
        }
        return false;
    }

    /** how far ahead of himself an escorted giant is sent each time (a short way, so he keeps a walking pace) */
    static final double ESCORT_LEAD = 8;

    /**
     * (lagfix, JJ "no road follow") walks the path with the players: a spot a few blocks along the road, given again
     * each second, so he keeps to the road at a pace they can keep up with; no player within the blocks (24): he
     * stops where he is and waits for them, then goes on from where he stopped. True at the path's end.
     */
    private static boolean escort(MinecraftServer server, LivingEntity e, String[] w, Live lv, AdvData.State in,
            long now) {
        AdvData.Path p = in.paths.get(w[1].toLowerCase(Locale.ROOT));
        if (p == null || p.pts.isEmpty() || !p.dim.equals(e.level().dimension().location().toString()))
            return now - lv.started > 200;
        double range = w.length > 2 ? num(w[2]) : 24;
        int n = p.pts.size();
        if (lv.pt >= n) lv.pt = n - 1;
        // (from the nearest point on: after a fight or a push he picks up the road where he is)
        if (lv.lastGo < 0) lv.pt = nearestAhead(p, e, lv.pt);
        double near = Math.min(AdvGiants.near(e), 4);
        double[] at = p.pts.get(lv.pt);
        double d = Math.sqrt(AdvGiants.horizontal(e.position(), new Vec3(at[0], 0, at[2])));
        while (d <= near) {
            if (lv.pt == n - 1) return true;
            lv.pt++;
            lv.best = Double.MAX_VALUE;
            lv.bestAt = now;
            at = p.pts.get(lv.pt);
            d = Math.sqrt(AdvGiants.horizontal(e.position(), new Vec3(at[0], 0, at[2])));
        }
        if (AdvGiants.nearest(e, range, null) == null) {
            // nobody with him: he waits here (sent to his own spot so he stops), and the stuck clock stops
            if (!lv.waiting) {
                lv.waiting = true;
                AdvGiants.go(server, e, e.getX(), e.getZ());
            }
            lv.bestAt = now;
            return false;
        }
        if (lv.waiting) {
            lv.waiting = false;
            lv.lastGo = -100;
        }
        if (d < lv.best - 0.5) {
            lv.best = d;
            lv.bestAt = now;
        }
        if (now - lv.lastGo >= 20) {
            Vec3 to = new Vec3(at[0] - e.getX(), 0, at[2] - e.getZ());
            Vec3 spot = to.length() > ESCORT_LEAD ? e.position().add(to.normalize().scale(ESCORT_LEAD))
                    : new Vec3(at[0], 0, at[2]);
            AdvGiants.go(server, e, spot.x, spot.z);
            lv.lastGo = now;
        }
        // no nearer for ten seconds (stuck on something): a step along the way
        if (now - lv.bestAt > 200) {
            Vec3 to = new Vec3(at[0] - e.getX(), 0, at[2] - e.getZ());
            AdvGiants.nudge(server, e, e.position().add(to.normalize().scale(Math.min(4, to.length()))));
            lv.bestAt = now;
        }
        return false;
    }

    /** the path point to make for: the nearest to him from this one on (never back along the road) */
    private static int nearestAhead(AdvData.Path p, LivingEntity e, int from) {
        int best = from;
        double bd = Double.MAX_VALUE;
        for (int i = from; i < p.pts.size(); i++) {
            double[] q = p.pts.get(i);
            double d = AdvGiants.horizontal(e.position(), new Vec3(q[0], 0, q[2]));
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        // (past that point already, along the road to the next: the next)
        if (best + 1 < p.pts.size()) {
            double[] a = p.pts.get(best), b = p.pts.get(best + 1);
            if ((e.getX() - a[0]) * (b[0] - a[0]) + (e.getZ() - a[2]) * (b[2] - a[2]) > 0) best++;
        }
        return best;
    }

    /** stays at the spot the step began (walked back when he wanders) */
    private static void hold(MinecraftServer server, LivingEntity e, Live lv, long now) {
        if (lv.anchor == null || now - lv.lastGo < 40) return;
        if (Math.sqrt(AdvGiants.horizontal(e.position(), lv.anchor)) > AdvGiants.near(e) + 2) {
            AdvGiants.go(server, e, lv.anchor.x, lv.anchor.z);
            lv.lastGo = now;
        }
    }

    /** stands guard at his post: goes for anyone who comes too close, then back */
    private static void guard(MinecraftServer server, LivingEntity e, Live lv, double r, boolean fresh, long now) {
        if (fresh) AdvGiants.mood(server, e, "guardian");
        ServerPlayer p = AdvGiants.nearest(e, r, lv.anchor);
        if (p != null) {
            if (!p.getUUID().equals(lv.fighting)) {
                AdvGiants.mood(server, e, "hunting");
                AdvGiants.turn(server, e, p);
                lv.fighting = p.getUUID();
            }
            return;
        }
        if (lv.fighting != null) {
            lv.fighting = null;
            AdvGiants.mood(server, e, "guardian");
        }
        hold(server, e, lv, now);
    }

    private static boolean until(MinecraftServer server, LivingEntity e, AdvData.State in, String[] w, int i) {
        if (w.length < i + 3 || !w[i].equals("until")) return false;
        if (w[i + 1].equals("players")) return AdvGiants.nearest(e, num(w[i + 2]), null) != null;
        return playerIn(server, e, in, w[i + 2]);
    }

    /** a player (not watching) is inside the area */
    static boolean playerIn(MinecraftServer server, LivingEntity e, AdvData.State in, String area) {
        AdvData.Area a = in.areas.get(area.toLowerCase(Locale.ROOT));
        if (a == null || !(e.level() instanceof ServerLevel l)) return false;
        String dim = l.dimension().location().toString();
        for (ServerPlayer p : l.players())
            if (!p.isSpectator() && p.isAlive() && a.has(dim, p.getX(), p.getY(), p.getZ())) return true;
        return false;
    }

    /** his line in chat, to the players within 96 blocks of him */
    static void say(LivingEntity e, String words) {
        if (!(e.level() instanceof ServerLevel l)) return;
        Component line = Component.literal("<").append(e.getDisplayName().copy().withStyle(ChatFormatting.GOLD))
                .append(Component.literal("> " + words));
        for (ServerPlayer p : l.players()) if (p.distanceToSqr(e) < 96 * 96) p.sendSystemMessage(line);
    }

    // giving and taking scripts

    /** the giant now runs this script from its first step */
    static void give(MinecraftServer server, AdvData.State in, LivingEntity e, String script, String by, int level) {
        AdvData.Run r = new AdvData.Run();
        r.script = script;
        r.kind = FunGiants.kindOf(e);
        r.by = by + "#" + level;
        in.runs.put(e.getStringUUID(), r);
        live.remove(e.getUUID());
        e.addTag(TAG);
    }

    static final String TAG = "jj_giants.scripted";

    static void take(AdvData.State in, String id, @Nullable LivingEntity e) {
        in.runs.remove(id);
        try {
            live.remove(UUID.fromString(id));
        } catch (IllegalArgumentException bad) {
            // not an id
        }
        if (e != null) e.removeTag(TAG);
    }

    /** which step he is on, in words ("step 2 of 5: patrol shore", "done") */
    static String where(AdvData.State in, AdvData.Run r) {
        AdvData.Script sc = in.scripts.get(r.script);
        if (sc == null) return "its script is gone";
        if (r.done || r.step >= sc.steps.size()) return "done";
        int s = Math.max(0, r.step);
        return "step " + (s + 1) + " of " + sc.steps.size() + ": " + sc.steps.get(s);
    }

    static @Nullable Giant giantOf(AdvData.Run r) {
        return r.kind == null ? null : net.jj.giants.admin.AdminServer.giant(r.kind);
    }
}
