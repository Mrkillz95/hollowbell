package net.jj.giants.fights;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fights.FightData.Fighter;
import net.jj.giants.fights.FightData.Setup;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.Systems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27.5) A staged fight run by its script (ADMIN_BOOK.md "Fight script" 1-14): the giants
 * put in place facing each other (one or two a side), a countdown, then each round fought with real blows paced so
 * the chosen winner wins in about the length asked (or left to the fight), the loser breaking at the break point and
 * ending as asked (to the death with the winner's finisher in slow motion, running off, run down and killed, or
 * bowing out), the show (both bars, the announcer, fight music), bets, players joining a side, and what comes after.
 * One staged fight at a time; Stop sends every giant in it home.
 */
public final class FightScript {
    private FightScript() {}

    /** the tags the giants read (ADMIN_BRIDGE.md §27.2) */
    static final String TAG = "jj_giants.fight.", SCRIPT = TAG + "script", TAKEN = TAG + "taken=",
            FLOOR = TAG + "floor=", TEAM = TAG + "team=", MOVES = TAG + "moves=", CHASE = TAG + "chase=";

    /** one giant in the run */
    static final class Slot {
        final Fighter f;
        final Giant g;
        final Vec3 at, facing;
        @Nullable UUID id;
        /** the last of him seen (for his name in lines) */
        @Nullable LivingEntity last;
        @Nullable Set<UUID> before;
        boolean broken, dead;
        double start = 1, share = 1, pace = 1;

        Slot(Fighter f, Giant g, Vec3 at, Vec3 facing) {
            this.f = f;
            this.g = g;
            this.at = at;
            this.facing = facing;
        }

        String side() {
            return f.side;
        }
    }

    /** one player's bet: the side, the emeralds put down */
    record Bet(String side, int stake, String name) {}

    /** the staged fight going on */
    static final class Run {
        final Setup s;
        final String who;
        final UUID by;
        final ServerLevel level;
        final Vec3 spot;
        final List<Slot> slots = new ArrayList<>();
        final FightShow.Show show = new FightShow.Show();
        final Map<UUID, String> joined = new HashMap<>();
        final Map<UUID, Bet> bets = new LinkedHashMap<>();
        String phase = "place";
        long phaseAt;
        int count;
        int round = 1, winsA, winsB;
        long roundAt;
        /** decided once it is over: the side that won, the loser who ends it, the winner finishing him */
        String won = "";
        @Nullable Slot loser, finisher;
        boolean finisherDone, roared, betsOpen = true, chasing;
        /** bets close this game time (a few seconds into the first round) */
        long betsAt = -1;
        long finishAt;
        /** (1.15) the loser running off a little faster for a few seconds (a slow giant reads as running) */
        @Nullable Slot boosted;
        long boostUntil;
        /** the area: its middle and how far players count as near */
        double radius;

        Run(Setup s, String who, UUID by, ServerLevel level, Vec3 spot) {
            this.s = s;
            this.who = who;
            this.by = by;
            this.level = level;
            this.spot = spot;
        }

        long now() {
            return level.getGameTime();
        }

        List<Slot> side(String side) {
            List<Slot> out = new ArrayList<>();
            for (Slot x : slots) if (x.side().equals(side)) out.add(x);
            return out;
        }

        @Nullable Slot slot(UUID id) {
            for (Slot x : slots) if (id.equals(x.id)) return x;
            return null;
        }

        int need() {
            return s.rounds / 2 + 1;
        }
    }

    static @Nullable Run run;

    static void forget() {
        run = null;
        cooling.clear();
    }

    /** a giant in the staged fight now */
    public static boolean in(UUID id) {
        Run r = run;
        return r != null && r.slot(id) != null && !r.phase.equals("done");
    }

    /** the side a giant fights on in the staged fight ("" when he isn't in it) */
    static String sideOf(UUID id) {
        Run r = run;
        Slot x = r == null ? null : r.slot(id);
        return x == null ? "" : x.side();
    }

    /** the staged fight is in a round (not counting down, resting or over) */
    static boolean fightingNow() {
        Run r = run;
        return r != null && (r.phase.equals("fight") || r.phase.equals("finish") || r.phase.equals("chase"));
    }

    /** (the picture runs) the staged fight's phase now ("none" when there is none) */
    public static String phaseForTests() {
        Run r = run;
        return r == null ? "none" : r.phase;
    }

    public static boolean busy() {
        return run != null;
    }

    // starting it

    static String called(Slot x, boolean start) {
        LivingEntity e = x.last;
        return e != null ? FightGiants.called(e, start) : (start ? "The " : "the ") + AdminServer.nameOf(x.g);
    }

    /** his size: one out there as he is, a fresh one as the script says */
    static double size(MinecraftServer server, Fighter f) {
        LivingEntity e = f.id.isEmpty() ? null : FunGiants.find(server, f.id);
        return e != null ? FunGiants.sizeOf(e) : f.size > 0 ? f.size : 0.5;
    }

    /** a side's name for lines: one giant's, or both ("Wreckback and the Cerberus") */
    static String sideName(Run r, String side) {
        return sideName(r, side, true);
    }

    /** start: at the start of a sentence ("The Cerberus"), else "the Cerberus" */
    static String sideName(Run r, String side, boolean start) {
        List<String> n = new ArrayList<>();
        for (Slot x : r.side(side)) n.add(called(x, start && n.isEmpty()));
        return String.join(" and ", n);
    }

    /** what is wrong with a script, or null */
    static @Nullable String check(MinecraftServer server, Setup s) {
        List<Fighter> a = s.side("a"), b = s.side("b");
        if (a.isEmpty() || b.isEmpty()) return "A fight needs a giant on each side.";
        if (a.size() > 2 || b.size() > 2) return "At most two giants a side.";
        Set<String> ids = new HashSet<>();
        for (Fighter f : s.fighters) {
            Giant g = AdminServer.giant(f.kind);
            if (g == null) return "No giant called " + f.kind + " is installed.";
            if (!FightGiants.speaks(server, g))
                return "The " + AdminServer.nameOf(g) + " mod is too old for a fight script. Update it.";
            if (!f.id.isEmpty() && !ids.add(f.id)) return "The same giant can't be in it twice.";
        }
        return null;
    }

    /** a staged fight from this script, at this spot (facing as the admin looks: A on the left, B on the right) */
    public static AdminServer.Result start(MinecraftServer server, String who, UUID by, Setup s0, ServerLevel l,
            BlockPos at, Vec3 look) {
        Setup s = s0.copy();
        if (run != null) return AdminServer.Result.fail("A staged fight is on already. Stop it first (Stop, or"
                + " /giants admin fight stop).");
        if (net.jj.giants.tools.Tools.on(server, "paused"))
            return AdminServer.Result.fail("All giants are paused right now. Unpause them first (Server tools).");
        String bad = check(server, s);
        if (bad != null) return AdminServer.Result.fail(bad);
        Vec3 fwd = look.multiply(1, 0, 1);
        if (fwd.lengthSqr() < 1e-4) fwd = new Vec3(0, 0, 1);
        fwd = fwd.normalize();
        Vec3 across = new Vec3(-fwd.z, 0, fwd.x);
        double ra = 0, rb = 0;
        for (Fighter f : s.fighters) {
            double rr = FunGiants.reach(f.kind, size(server, f));
            if (f.side.equals("a")) ra = Math.max(ra, rr);
            else rb = Math.max(rb, rr);
        }
        double gap = (ra + rb) * 0.6 + 8;
        Vec3 mid = Vec3.atBottomCenterOf(at);
        Run r = new Run(s, who, by, l, mid);
        r.radius = Math.max(96, (ra + rb) * 2 + 32);
        for (String side : List.of("a", "b")) {
            List<Fighter> fs = s.side(side);
            double rr = side.equals("a") ? ra : rb;
            for (int i = 0; i < fs.size(); i++) {
                // side by side as you look (A on your left), a pair one behind the other's shoulder
                double along = fs.size() == 1 ? 0 : (i == 0 ? -1 : 1) * (rr * 1.1 + 4);
                Vec3 p = mid.add(across.scale((side.equals("a") ? -1 : 1) * gap / 2)).add(fwd.scale(along));
                Vec3 face = mid.add(fwd.scale(along));
                Giant g = AdminServer.giant(fs.get(i).kind);
                r.slots.add(new Slot(fs.get(i), g, p, face));
            }
        }
        List<BlockPos> spots = new ArrayList<>();
        spots.add(at);
        for (Slot x : r.slots) spots.add(BlockPos.containing(x.at));
        run = r;
        r.phaseAt = r.now();
        // (the giants are told at once: a staged fight's giants fight only each other)
        Fights.pushFlags(server);
        net.jj.giants.fun.Spots.whenLoaded(l, spots, () -> place(server, r),
                () -> {
                    if (run == r) run = null;
                    ServerPlayer p = server.getPlayerList().getPlayer(by);
                    if (p != null) p.displayClientMessage(Component.literal("The land there isn't ready yet. Try"
                            + " again in a moment."), false);
                });
        return AdminServer.Result.said(List.of("Setting up the staged fight: " + sideNames(s) + "…"));
    }

    static String sideNames(Setup s) {
        List<String> a = new ArrayList<>(), b = new ArrayList<>();
        for (Fighter f : s.fighters) {
            Giant g = AdminServer.giant(f.kind);
            (f.side.equals("a") ? a : b).add(g == null ? f.kind : AdminServer.nameOf(g));
        }
        return String.join(" and ", a) + " against " + String.join(" and ", b);
    }

    /** each giant brought to his spot: one out there moved, a fresh one summoned (found next tick) */
    private static void place(MinecraftServer server, Run r) {
        if (run != r) return;
        for (Slot x : r.slots) {
            double y = net.jj.giants.fun.Spots.ground(r.level, Mth.floor(x.at.x), Mth.floor(x.at.z));
            if (Double.isNaN(y)) y = x.at.y;
            LivingEntity e = x.f.id.isEmpty() ? null : FunGiants.find(server, x.f.id);
            if (e != null && e.level() == r.level) {
                JsonObject o = new JsonObject();
                o.addProperty("dim", r.level.dimension().location().toString());
                o.addProperty("x", x.at.x);
                o.addProperty("y", y);
                o.addProperty("z", x.at.z);
                o.addProperty("where", "land");
                o.addProperty("stay", false);
                AdminServer.Result m = FunGiants.act(server, x.g, e.getStringUUID(), "move", o);
                if (m.ok()) {
                    claim(server, x, e);
                    continue;
                }
            }
            // a fresh one: his own summon at the spot, calm when he has that mood, at the script's size
            x.before = new HashSet<>();
            for (LivingEntity o : FunGiants.standing(server, x.g.key())) x.before.add(o.getUUID());
            String arg = r.level.dimension().location() + " " + x.at.x + " " + y + " " + x.at.z + " - calm "
                    + AdminSetting.plain(size(server, x.f));
            AdminServer.call(server, x.g, "summon", arg);
            // (1.15) found at once when his summon stands him there now: marked as the staged fight's from the start
            findOne(server, r, x);
        }
        r.phase = "find";
        r.phaseAt = r.now();
    }

    private static void find(MinecraftServer server, Run r) {
        boolean all = true;
        for (Slot x : r.slots) if (x.id == null && !findOne(server, r, x)) all = false;
        if (!all) {
            if (r.now() - r.phaseAt > 60) fail(server, r, "a giant's own summon left none of him standing there (the"
                    + " world may already hold as many of him as it allows: pick one out there instead)");
            return;
        }
        ready(server, r);
    }

    /** a fresh one of the slot's kind standing there now (not one from before): his, and marked at once */
    private static boolean findOne(MinecraftServer server, Run r, Slot x) {
        if (x.id != null) return true;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : FunGiants.standing(server, x.g.key())) {
            if (x.before != null && x.before.contains(e.getUUID()) || e.level() != r.level || r.slot(e.getUUID())
                    != null) continue;
            double d = e.position().distanceToSqr(x.at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return false;
        claim(server, x, best);
        return true;
    }

    /**
     * (1.15) a giant of the staged fight from the moment he is found: its tags at once (so no wild giant starts on him
     * before the bell, and he starts on none) and standing down
     */
    private static void claim(MinecraftServer server, Slot x, LivingEntity e) {
        x.id = e.getUUID();
        x.last = e;
        e.addTag(SCRIPT);
        e.addTag(TEAM + x.side());
        FightGiants.act(server, e, "stop", null);
    }

    private static void fail(MinecraftServer server, Run r, String why) {
        ServerPlayer p = server.getPlayerList().getPlayer(r.by);
        if (p != null) p.displayClientMessage(Component.literal("The staged fight couldn't be set up: " + why + ".")
                .withStyle(ChatFormatting.RED), false);
        clean(server, r, true);
        run = null;
    }

    /** all in place: tagged, their health set, facing each other; the countdown (or the fight) begins */
    private static void ready(MinecraftServer server, Run r) {
        for (Slot x : r.slots) {
            LivingEntity e = find(server, x);
            if (e == null) continue;
            e.addTag(SCRIPT);
            e.addTag(TEAM + x.side());
            if (!x.f.moves.isEmpty()) {
                List<String> mv = new ArrayList<>(x.f.moves);
                String fin = finisher(server, x);
                if (!fin.isEmpty() && !mv.contains(fin)) mv.add(fin);
                e.addTag(MOVES + String.join(",", mv));
            }
            FightGiants.act(server, e, "stop", null);
            if (x.f.health < 0.999) {
                JsonObject o = new JsonObject();
                o.addProperty("share", Math.max(0.05, x.f.health));
                o.addProperty("set", true);
                FightGiants.act(server, e, "health", o);
            }
            face(e, x.facing.add(x.at.subtract(x.facing).scale(-2)));
        }
        List<ServerPlayer> near = near(r);
        say(server, r, near, sideName(r, "a") + " against " + sideName(r, "b", false) + "!" + (r.s.rounds > 1
                ? " Best of "
                + r.s.rounds + "." : ""));
        if (r.s.bets) offerBets(server, r, near);
        Fun.told(server, r);
        if (r.s.countdown) {
            r.phase = "count";
            r.count = 3;
            r.phaseAt = r.now();
        } else startRound(server, r);
    }

    static void face(LivingEntity e, Vec3 to) {
        Vec3 d = to.subtract(e.position());
        float yaw = (float) (Mth.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }

    /** the giant of a slot standing in the world now (and kept as the last seen) */
    static @Nullable LivingEntity find(MinecraftServer server, @Nullable Slot x) {
        if (x == null || x.id == null) return null;
        LivingEntity e = FunGiants.find(server, x.id);
        if (e != null) x.last = e;
        return e;
    }

    static String finisher(MinecraftServer server, Slot x) {
        String want = !x.f.finisher.isEmpty() ? x.f.finisher : FightGiants.finisher(server, x.g);
        // (1.15) never a move the world switched off: then his next big move still on
        return FightGiants.allowedFinisher(server, x.g, want, x.f.moves);
    }

    // the clock

    static void tick(MinecraftServer server) {
        Run r = run;
        if (r == null) return;
        long now = r.now();
        if (server.getTickCount() % 20 == 0) ownBars(server, r);
        switch (r.phase) {
            case "find" -> find(server, r);
            case "count" -> {
                if ((now - r.phaseAt) % 20 != 0) return;
                List<ServerPlayer> near = near(r);
                // (1.15) the fight's one bar from the countdown on
                show(server, r);
                FightShow.count(near, r.count, r.s.rounds > 1 ? "Round " + r.round : sideName(r, "a") + " vs "
                        + sideName(r, "b", false));
                holdStill(server, r);
                if (r.count-- <= 0) startRound(server, r);
            }
            case "fight" -> {
                if (server.getTickCount() % 20 == 0) fight(server, r);
                else if (server.getTickCount() % 5 == 0) show(server, r);
            }
            case "rest" -> {
                if (server.getTickCount() % 20 == 0) {
                    show(server, r);
                    holdStill(server, r);
                }
                if (now - r.phaseAt >= r.s.rest * 20L) {
                    r.round++;
                    if (r.s.countdown) {
                        r.phase = "count";
                        r.count = 3;
                        r.phaseAt = now;
                    } else startRound(server, r);
                }
            }
            case "finish", "chase" -> {
                if (server.getTickCount() % 5 == 0) finishing(server, r);
            }
            case "after" -> {
                if (server.getTickCount() % 20 == 0) after(server, r);
            }
            default -> {}
        }
    }

    /** before the bell and between rounds: nobody starts on his own */
    private static void holdStill(MinecraftServer server, Run r) {
        for (Slot x : r.slots) {
            LivingEntity e = find(server, x);
            if (e != null && !x.dead) FightGiants.act(server, e, "stop", null);
        }
    }

    // a round

    private static void startRound(MinecraftServer server, Run r) {
        r.phase = "fight";
        r.phaseAt = r.now();
        r.roundAt = r.now();
        for (Slot x : r.slots) {
            x.broken = false;
            LivingEntity e = find(server, x);
            if (e == null) continue;
            x.start = x.share = FightGiants.hp(server, e).share();
            x.pace = 1;
            setTag(e, TAKEN, null);
            setTag(e, FLOOR, AdminSetting.plain(floorOf(r, x)));
        }
        pairUp(server, r);
        if (r.round == 1 && r.s.bets) r.betsAt = r.now() + 200;
    }

    /** whom the side that must win this round is (the chosen winner wins, but lets one round go for the show) */
    static String roundWinner(Run r) {
        if (r.s.winner.equals("fight")) return "";
        String w = r.s.winner, l = w.equals("a") ? "b" : "a";
        // best of 3 or 5: the loser takes the second round, then the winner takes the rest
        return r.s.rounds > 1 && r.round == 2 ? l : w;
    }

    /** the most a giant may lose this round: the winner's side never comes near breaking */
    static double floorOf(Run r, Slot x) {
        String w = roundWinner(r);
        if (w.isEmpty() || !x.side().equals(w)) return r.s.breakAt;
        double f = Math.max(r.s.breakAt + 0.12, 0.2);
        return Math.max(0.05, Math.min(f, x.start - 0.03));
    }

    /** each giant goes for the nearest giant of the other side still up */
    private static void pairUp(MinecraftServer server, Run r) {
        for (Slot x : r.slots) {
            if (x.broken || x.dead) continue;
            LivingEntity e = find(server, x), foe = nearestFoe(server, r, x);
            if (e == null || foe == null) continue;
            FightGiants.act(server, e, "fight", FightGiants.foe(foe));
        }
    }

    static @Nullable LivingEntity nearestFoe(MinecraftServer server, Run r, Slot x) {
        LivingEntity e = find(server, x), best = null;
        if (e == null) return null;
        double bd = Double.MAX_VALUE;
        for (Slot o : r.slots) {
            if (o.side().equals(x.side()) || o.broken || o.dead) continue;
            LivingEntity f = find(server, o);
            if (f == null) continue;
            double d = f.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = f;
            }
        }
        return best;
    }

    /** the length: about how long a round lasts, in ticks */
    static long length(Setup s) {
        boolean many = s.rounds > 1;
        return switch (s.length) {
            case "quick" -> many ? 400 : 600;
            case "epic" -> many ? 1800 : 4800;
            default -> many ? 900 : 1800;
        };
    }

    /** once a second in a round: health, the pace, the announcer, who breaks */
    private static void fight(MinecraftServer server, Run r) {
        long t = r.now() - r.roundAt, T = length(r.s);
        double p = Math.min(1, t / (double) T);
        String w = roundWinner(r);
        List<ServerPlayer> near = near(r);
        for (Slot x : r.slots) {
            if (x.broken || x.dead) continue;
            LivingEntity e = find(server, x);
            if (e == null || !e.isAlive()) {
                x.dead = true;
                x.broken = true;
                say(server, r, near, called(x, true) + " is down!");
                continue;
            }
            FightGiants.Hp hp = FightGiants.hp(server, e);
            x.share = hp.share();
            if (r.s.announcer) {
                String line = FightShow.hurtLine(r.show, e, x.share, called(x, true));
                if (line != null) say(server, r, near, line);
            }
            // the pace: hit harder while he is healthier than the plan, softer while he is behind it
            double end = w.isEmpty() || !x.side().equals(w) ? r.s.breakAt
                    : Math.min(x.start, Math.max(r.s.breakAt + 0.25, x.start * 0.5));
            double plan = x.start - (x.start - end) * p;
            double err = x.share - plan;
            // (past the length the pace may go further: the fight must come to its end)
            x.pace = Mth.clamp(x.pace * Math.exp(4.0 * err), t > T ? 0.02 : 0.05, t > T ? 40 : 15);
            if (t < 100) x.pace = Math.max(x.pace, 0.5);
            setTag(e, TAKEN, Math.abs(x.pace - 1) < 0.02 ? null : AdminSetting.plain(Math.round(x.pace * 100) / 100.0));
            // (twice the length gone and still well above the break point: the blows he takes wear him down a little
            // more each second, so the fight comes to its end however little his foe's blows do)
            if (t > 2 * T && (w.isEmpty() || !x.side().equals(w)) && x.share > r.s.breakAt + 0.01) {
                JsonObject o = new JsonObject();
                o.addProperty("share", Math.max(r.s.breakAt, x.share - 0.03));
                o.addProperty("set", true);
                FightGiants.act(server, e, "health", o);
            }
            // keep each one on the nearest foe still up (a foe who broke, or a pair split up)
            if ((t / 20) % 5 == 0) {
                LivingEntity foe = nearestFoe(server, r, x);
                if (foe != null && !hp.target().equals(foe.getStringUUID()))
                    FightGiants.act(server, e, "fight", FightGiants.foe(foe));
            }
        }
        show(server, r);
        if (r.betsOpen && r.betsAt > 0 && r.now() >= r.betsAt) closeBets(server, r, near);
        // who breaks: down to the break point (a giant of the side that wins this round never gets there)
        for (Slot x : r.slots) {
            if (x.broken || x.dead || x.share > r.s.breakAt + 0.006) continue;
            if (!w.isEmpty() && x.side().equals(w) && t < T * 3) continue;
            x.broken = true;
            boolean sideDown = r.side(x.side()).stream().allMatch(o -> o.broken || o.dead);
            if (!sideDown) {
                // a pair: he bows out and stands aside; his partner fights on
                LivingEntity e = find(server, x), foe = nearestFoe(server, r, x);
                if (e != null) FightGiants.act(server, e, "yield", bow(foe));
                say(server, r, near, called(x, true) + " is out of it!");
                pairUp(server, r);
                continue;
            }
            roundOver(server, r, x, near);
            return;
        }
        // far too long (the pace couldn't make it): the side worse off loses the round
        if (t > T * 3) {
            Slot worst = null;
            for (Slot x : r.slots)
                if (!x.broken && !x.dead && (w.isEmpty() || !x.side().equals(w)) && (worst == null
                        || x.share < worst.share)) worst = x;
            if (worst != null) {
                for (Slot x : r.side(worst.side())) x.broken = true;
                roundOver(server, r, worst, near);
            }
        }
    }

    /** backing down where he stands (a round lost, or out of a pair's fight): no run home */
    private static JsonObject bow(@Nullable LivingEntity foe) {
        JsonObject o = foe == null ? new JsonObject() : with("by", foe);
        o.addProperty("how", "bow");
        return o;
    }

    private static JsonObject with(String k, LivingEntity e) {
        return FightGiants.with(k, e.getStringUUID());
    }

    /** a side is down: the round is theirs to lose; the match goes on, or it is over */
    private static void roundOver(MinecraftServer server, Run r, Slot last, List<ServerPlayer> near) {
        String lost = last.side(), wonSide = lost.equals("a") ? "b" : "a";
        if (wonSide.equals("a")) r.winsA++;
        else r.winsB++;
        boolean over = r.winsA >= r.need() || r.winsB >= r.need() || r.s.rounds <= 1;
        if (!over) {
            say(server, r, near, "Round " + r.round + " to " + sideName(r, wonSide) + "! (" + r.winsA + " to " + r.winsB
                    + ")");
            for (Slot x : r.slots) {
                LivingEntity e = find(server, x);
                if (e == null) continue;
                FightGiants.act(server, e, "stop", null);
                if (x.side().equals(lost) && !x.dead) FightGiants.act(server, e, "yield", bow(nearestFoe(server, r,
                        x)));
                JsonObject o = new JsonObject();
                o.addProperty("share", Math.max(0, r.s.roundHeal));
                if (r.s.roundHeal > 0) FightGiants.act(server, e, "health", o);
            }
            for (Slot x : r.side(wonSide)) {
                LivingEntity e = find(server, x);
                if (e != null) FightGiants.act(server, e, "roar", null);
            }
            r.phase = "rest";
            r.phaseAt = r.now();
            return;
        }
        r.won = wonSide;
        r.loser = last;
        if (r.s.rounds > 1)
            say(server, r, near, sideName(r, wonSide) + " take" + (r.side(wonSide).size() == 1 ? "s" : "")
                    + " the match, " + Math.max(r.winsA, r.winsB) + " to " + Math.min(r.winsA, r.winsB) + "!");
        ending(server, r, near);
    }

    /** the loser's ending */
    private static void ending(MinecraftServer server, Run r, List<ServerPlayer> near) {
        Slot l = r.loser;
        LivingEntity le = l == null ? null : find(server, l);
        r.finisher = null;
        double bd = Double.MAX_VALUE;
        for (Slot x : r.side(r.won)) {
            LivingEntity e = find(server, x);
            if (e == null || x.dead || le == null) continue;
            double d = e.distanceToSqr(le);
            if (d < bd) {
                bd = d;
                r.finisher = x;
            }
        }
        LivingEntity w = r.finisher == null ? null : find(server, r.finisher);
        r.finishAt = r.now();
        // the other winners stop where they are; teammates who broke earlier stand bowed
        for (Slot x : r.side(r.won))
            if (x != r.finisher) {
                LivingEntity e = find(server, x);
                if (e != null) FightGiants.act(server, e, "stop", null);
            }
        if (le == null || w == null || l.dead) {
            say(server, r, near, sideName(r, r.won) + (r.side(r.won).size() == 1 ? " wins!" : " win!"));
            toAfter(server, r);
            return;
        }
        switch (r.s.ending) {
            case "escape" -> {
                FightGiants.act(server, w, "stop", null);
                FightGiants.act(server, le, "yield", with("by", w));
                say(server, r, near, called(l, true) + " runs for it! " + called(r.finisher, true) + " lets him go.");
                toAfter(server, r);
                boost(server, r, l, le, 1.6, 120);
            }
            case "bow" -> {
                FightGiants.act(server, w, "stop", null);
                JsonObject o = with("by", w);
                o.addProperty("how", "bow");
                FightGiants.act(server, le, "yield", o);
                say(server, r, near, called(l, true) + " bows out. " + sideName(r, r.won) + (r.side(r.won).size() == 1
                        ? " wins!" : " win!"));
                toAfter(server, r);
            }
            case "caught" -> {
                // he runs; the winner lets him get a few blocks away, then goes after him
                FightGiants.act(server, w, "stop", null);
                FightGiants.act(server, le, "yield", with("by", w));
                setTag(le, FLOOR, "0.02");
                // (his head start is a real run: faster for it, till the chase slows him)
                boost(server, r, l, le, 1.5, 60);
                say(server, r, near, called(l, true) + " runs for it!");
                r.phase = "chase";
            }
            default -> {
                // to the death: he holds on just above nothing until the winner's finisher lands
                setTag(le, FLOOR, "0.02");
                setTag(le, TAKEN, "3");
                setTag(w, FLOOR, AdminSetting.plain(Math.max(0.05, r.finisher.share - 0.05)));
                finish(server, r, w, le, near);
                r.phase = "finish";
            }
        }
    }

    /** (1.15) the loser runs off faster for a while (Fights.speed, put back by unboost) */
    private static void boost(MinecraftServer server, Run r, Slot l, LivingEntity le, double f, int ticks) {
        Fights.speed(server, le, f);
        r.boosted = l;
        r.boostUntil = r.now() + ticks;
    }

    /** his boost over (time up, or now) */
    private static void unboost(MinecraftServer server, Run r, boolean now) {
        if (r.boosted == null || !now && r.now() < r.boostUntil) return;
        LivingEntity e = find(server, r.boosted);
        r.boosted = null;
        if (e != null && !r.chasing) Fights.speed(server, e, 1);
    }

    /** the winner's finisher, at the loser */
    private static void finish(MinecraftServer server, Run r, LivingEntity w, LivingEntity le, List<ServerPlayer> near) {
        String move = finisher(server, r.finisher);
        JsonObject o = FightGiants.foe(le);
        o.addProperty("move", move);
        List<String> did = move.isEmpty() ? null : FightGiants.act(server, w, "do", o);
        if (FightGiants.ok(did)) {
            r.finisherDone = true;
            if (r.s.announcer) say(server, r, near, called(r.finisher, true) + " goes for the finish: "
                    + move.replace('_', ' ') + "!");
        } else FightGiants.act(server, w, "fight", FightGiants.foe(le));
    }

    /** the finish (or the chase): the final blow in slow motion, the loser down */
    private static void finishing(MinecraftServer server, Run r) {
        Slot l = r.loser;
        LivingEntity le = l == null ? null : find(server, l), w = r.finisher == null ? null
                : find(server, r.finisher);
        List<ServerPlayer> near = near(r);
        if (server.getTickCount() % 20 == 0) show(server, r);
        if (le == null || !le.isAlive()) {
            if (l != null) l.dead = true;
            done(server, r, near);
            return;
        }
        long t = r.now() - r.finishAt;
        double share = FightGiants.hp(server, le).share();
        if (r.phase.equals("chase")) {
            if (w == null) {
                toAfter(server, r);
                return;
            }
            if (!r.chasing && t >= 60) {
                r.boosted = null;
                r.chasing = true;
                say(server, r, near, called(r.finisher, true) + " goes after him!");
                FightGiants.act(server, w, "chase", FightGiants.foe(le));
                // the chase is run down: he is slower now, the winner faster
                Fights.speed(server, le, 0.7);
                Fights.speed(server, w, 1.3);
            }
            double gap = Math.hypot(w.getX() - le.getX(), w.getZ() - le.getZ());
            // (he gets a head start: five seconds of running before he can be caught)
            boolean caught = t > 100 && (gap < FightGiants.reach(w) * 0.6 + 6 || share < l.share - 0.01);
            if (caught) {
                say(server, r, near, called(r.finisher, true) + " has caught " + called(l, false) + "!");
                setTag(le, TAKEN, "3");
                r.phase = "finish";
                r.finishAt = r.now();
                finish(server, r, w, le, near);
            } else if (t > 900) Fights.speed(server, le, 0.45);
            return;
        }
        // the final blow: he is at the floor (the finisher landed), or it has taken too long
        // (the finisher landing hardest: past 8 s every blow on him counts for more)
        if (t > 160 && t < 170) setTag(le, TAKEN, "8");
        if (share <= 0.035 || t > 400) {
            if (r.s.slowmo) FightShow.slowMo(server, 2600);
            r.loser.dead = true;
            Fights.kill(server, le);
            done(server, r, near);
        } else if (!r.finisherDone && t % 100 == 0 && w != null) finish(server, r, w, le, near);
        else if (r.finisherDone && t % 200 == 0 && w != null) {
            // (the finisher missed: he fights on with every move till it lands)
            FightGiants.act(server, w, "fight", FightGiants.foe(le));
            r.finisherDone = false;
        }
    }

    private static void done(MinecraftServer server, Run r, List<ServerPlayer> near) {
        say(server, r, near, sideName(r, r.won) + (r.side(r.won).size() == 1 ? " wins!" : " win!"));
        toAfter(server, r);
    }

    private static void toAfter(MinecraftServer server, Run r) {
        r.phase = "after";
        r.phaseAt = r.now();
        payBets(server, r);
        for (Slot x : r.slots) {
            LivingEntity e = find(server, x);
            if (e != null) {
                setTag(e, TAKEN, null);
                setTag(e, FLOOR, null);
                Fights.speed(server, e, 1);
                // (1.16) over: nobody is his target any more (players near the spot included)
                if (e instanceof Mob m) m.setTarget(null);
                e.setLastHurtByMob(null);
                live(server, x.g, e, "forgive");
            }
        }
        if (r.s.grow && !r.won.isEmpty() && (r.loser == null || !r.loser.dead))
            for (Slot x : r.side(r.won)) {
                LivingEntity e = find(server, x);
                if (e != null) Fights.growthWin(server, e);
            }
        Fun.log(server, r, "staged fight over: " + (r.won.isEmpty() ? "stopped" : sideName(r, r.won) + " won"));
    }

    /** after: the winner roars, then goes home, stays, or turns on the players near; the loser goes home */
    private static void after(MinecraftServer server, Run r) {
        long t = r.now() - r.phaseAt;
        unboost(server, r, t >= 160);
        if (!r.roared) {
            r.roared = true;
            for (Slot x : r.side(r.won)) {
                LivingEntity e = find(server, x);
                if (e != null && !x.dead) FightGiants.act(server, e, "roar", null);
            }
        }
        if (t < 160) {
            show(server, r);
            return;
        }
        for (Slot x : r.slots) {
            LivingEntity e = find(server, x);
            if (e == null || x.dead) continue;
            boolean winner = x.side().equals(r.won);
            untag(e);
            if (winner && r.s.after.equals("stay")) settle(server, x.g, e, false);
            else if (winner && r.s.after.equals("turn")) {
                cooling.remove(e.getUUID());
                FightGiants.act(server, e, "stop", null);
                Fights.sys(server, x.g, e, "mood", FightGiants.with("value", "hunting"));
                say(server, r, near(r), called(x, true) + " turns on the crowd!");
            } else settle(server, x.g, e, true);
        }
        if (r.s.healBlocks) Fights.healBlocks(server);
        FightShow.end(r.show, server);
        r.phase = "done";
        run = null;
    }

    /** stop it now: every giant in it stands down and goes home; bets back */
    public static AdminServer.Result stop(MinecraftServer server, String who) {
        Run r = run;
        if (r == null) return AdminServer.Result.fail("No staged fight is on.");
        say(server, r, near(r), "The fight was stopped.");
        clean(server, r, true);
        refund(server, r);
        run = null;
        Fun.log(server, r, "staged fight stopped by " + who);
        return new AdminServer.Result(true, List.of("The staged fight is stopped: every giant in it is on his way"
                + " home."), List.of(who + " stopped the staged fight."));
    }

    /** every giant in it: tags off, standing down, (home) */
    private static void clean(MinecraftServer server, Run r, boolean home) {
        FightShow.normal(server);
        FightShow.end(r.show, server);
        for (Slot x : r.slots) {
            LivingEntity e = x.id == null ? null : find(server, x);
            if (e == null) continue;
            untag(e);
            Fights.speed(server, e, 1);
            settle(server, x.g, e, home);
        }
        if (r.s.healBlocks) Fights.healBlocks(server);
        r.phase = "done";
    }

    // (1.16) after the fight: a minute of calm

    /** how long the fighters stay calm after a staged fight is stopped or over (game ticks; tests shorten it) */
    public static int coolTicks = 1200;

    /** one fighter calming down: his mood before, until when, whether he walks home */
    static final class Cool {
        final UUID id;
        final Giant g;
        final String mood;
        final long until;
        final boolean home;

        Cool(UUID id, Giant g, String mood, long until, boolean home) {
            this.id = id;
            this.g = g;
            this.mood = mood;
            this.until = until;
            this.home = home;
        }
    }

    static final Map<UUID, Cool> cooling = new LinkedHashMap<>();

    /** the fighters calming down now (tests) */
    public static Set<UUID> cooling() {
        return Set.copyOf(cooling.keySet());
    }

    /**
     * a staged fight is stopped or over: he drops every target and grudge (players and the other fighters), calms down
     * and (home) walks home; for a minute he is calmed again whenever he goes after anyone, then his mood comes back
     */
    static void settle(MinecraftServer server, Giant g, LivingEntity e, boolean home) {
        if (g == null) return;
        Cool was = cooling.get(e.getUUID());
        String mood = was != null ? was.mood : "";
        if (was == null) {
            FightGiants.freshRows();
            JsonObject row = FightGiants.row(server, e.getStringUUID());
            if (row != null) mood = net.jj.giants.admin.AdminJson.str(row, "mood", 32);
        }
        Cool c = new Cool(e.getUUID(), g, mood, server.overworld().getGameTime() + coolTicks, home);
        cooling.put(e.getUUID(), c);
        calmDown(server, c, e);
    }

    private static void calmDown(MinecraftServer server, Cool c, LivingEntity e) {
        if (e instanceof Mob m) m.setTarget(null);
        e.setLastHurtByMob(null);
        FightGiants.act(server, e, "stop", null);
        live(server, c.g, e, "calm");
        if (c.home) Fights.home(server, c.g, e);
    }

    /** once a second: anyone calming down who goes after someone is calmed again; after the minute, his mood back */
    static void cool(MinecraftServer server) {
        if (cooling.isEmpty()) return;
        long now = server.overworld().getGameTime();
        boolean fresh = false;
        for (Cool c : List.copyOf(cooling.values())) {
            LivingEntity e = FunGiants.find(server, c.id);
            if (e == null || !e.isAlive()) {
                cooling.remove(c.id);
                continue;
            }
            if (run != null && run.slots.stream().anyMatch(x -> c.id.equals(x.id))) {
                // (in a new staged fight now)
                cooling.remove(c.id);
                continue;
            }
            if (now >= c.until) {
                cooling.remove(c.id);
                if (!c.mood.isEmpty() && !c.mood.equals("calm"))
                    Fights.sys(server, c.g, e, "mood", FightGiants.with("value", c.mood));
                continue;
            }
            if (!fresh) {
                FightGiants.freshRows();
                fresh = true;
            }
            JsonObject row = FightGiants.row(server, e.getStringUUID());
            boolean after = e instanceof Mob m && m.getTarget() != null
                    || row != null && FightGiants.bool(row, "fighting");
            if (after) calmDown(server, c, e);
        }
    }

    private static void live(MinecraftServer server, Giant g, LivingEntity e, String what) {
        if (g == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("live", 1);
        o.addProperty("id", e.getStringUUID());
        o.addProperty("do", what);
        o.addProperty("value", "");
        o.addProperty("by", "-");
        AdminServer.call(server, g, "liveact", o.toString());
    }

    static void untag(LivingEntity e) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(TAG)) e.removeTag(t);
        net.jj.giants.tools.GiantBars.clear(e);
    }

    /**
     * (1.15) while the fight's one bar shows (Both bars on), each fighter's own bars are hidden, so the watchers see
     * the fight's bar alone
     */
    private static void ownBars(MinecraftServer server, Run r) {
        boolean one = r.s.bars && !r.phase.equals("place") && !r.phase.equals("find") && !r.phase.equals("done");
        for (Slot x : r.slots) {
            LivingEntity e = x.id == null ? null : find(server, x);
            if (e == null) continue;
            if (one && !x.dead) net.jj.giants.tools.GiantBars.hide(e);
            else net.jj.giants.tools.GiantBars.clear(e);
        }
    }

    static void setTag(LivingEntity e, String head, @Nullable String value) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(head)) e.removeTag(t);
        if (value != null) e.addTag(head + value);
    }

    // the show

    static List<ServerPlayer> near(Run r) {
        return FightShow.near(r.level, r.spot, r.radius);
    }

    static void say(MinecraftServer server, Run r, List<ServerPlayer> near, String line) {
        if (r.s.announcer) FightShow.say(server, near, line);
    }

    private static void show(MinecraftServer server, Run r) {
        List<LivingEntity> gs = new ArrayList<>();
        Map<UUID, Double> share = new HashMap<>();
        Map<UUID, String> side = new HashMap<>();
        for (Slot x : r.slots) {
            LivingEntity e = x.id == null ? null : find(server, x);
            if (e == null) continue;
            gs.add(e);
            if (server.getTickCount() % 20 != 0 || !r.phase.equals("fight")) x.share = FightGiants.hp(server, e).share();
            share.put(e.getUUID(), x.share);
            side.put(e.getUUID(), x.side());
        }
        if (r.s.bars) {
            double sa = 0, sb = 0;
            int na = 0, nb = 0;
            for (Slot x : r.slots) {
                if (x.side().equals("a")) {
                    sa += x.dead ? 0 : x.share;
                    na++;
                } else {
                    sb += x.dead ? 0 : x.share;
                    nb++;
                }
            }
            FightShow.bar(r.show, r.level, r.spot, r.radius, sideName(r, "a", true), sa / Math.max(1, na),
                    sideName(r, "b", false), sb / Math.max(1, nb));
        }
        FightShow.music(r.show, server, r.level, r.spot, r.radius, r.s.music ? "on" : "");
    }

    // bets

    static void offerBets(MinecraftServer server, Run r, List<ServerPlayer> near) {
        MutableComponent line = Component.literal("[Fight] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal("Bets are open: ").withStyle(ChatFormatting.GOLD));
        for (String side : List.of("a", "b"))
            line.append(Component.literal("[Bet on " + sideName(r, side, false) + "] ").withStyle(st -> st
                    .withColor(side.equals("a") ? ChatFormatting.RED : ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/giants fight bet " + side + " 1"))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                            "Bet emeralds from your pack on " + sideName(r, side) + " (0 for just the board).")))));
        for (ServerPlayer p : near) p.sendSystemMessage(line);
    }

    /** a player's bet (emeralds from their pack; 0 for the board only) */
    public static AdminServer.Result bet(MinecraftServer server, ServerPlayer p, String side, int stake) {
        Run r = run;
        if (r == null || !r.s.bets) return AdminServer.Result.fail("There's no staged fight taking bets now.");
        if (!r.betsOpen) return AdminServer.Result.fail("Bets are closed for this fight.");
        if (!side.equals("a") && !side.equals("b")) return AdminServer.Result.fail("Bet on a or b.");
        if (r.bets.containsKey(p.getUUID())) return AdminServer.Result.fail("You've already bet on this fight.");
        if (stake < 0 || stake > 64) return AdminServer.Result.fail("Bet from 0 to 64 emeralds.");
        if (stake > 0 && !Fights.take(p, stake)) return AdminServer.Result.fail("You don't have " + stake
                + " emerald" + (stake == 1 ? "" : "s") + ".");
        r.bets.put(p.getUUID(), new Bet(side, stake, p.getGameProfile().getName()));
        return AdminServer.Result.said(List.of("You bet " + stake + " emerald" + (stake == 1 ? "" : "s") + " on "
                + sideName(r, side) + "."));
    }

    private static void closeBets(MinecraftServer server, Run r, List<ServerPlayer> near) {
        r.betsOpen = false;
        if (!r.bets.isEmpty()) say(server, r, near, "Bets are closed: " + r.bets.size() + " bet"
                + (r.bets.size() == 1 ? "" : "s") + " down.");
    }

    /** the winners share the whole pot by their stakes; nobody on the winner: everyone gets theirs back */
    private static void payBets(MinecraftServer server, Run r) {
        r.betsOpen = false;
        if (r.bets.isEmpty() || r.won.isEmpty()) {
            refund(server, r);
            return;
        }
        int pot = 0, onWinner = 0;
        for (Bet b : r.bets.values()) {
            pot += b.stake();
            if (b.side().equals(r.won)) onWinner += b.stake();
        }
        boolean anyRight = r.bets.values().stream().anyMatch(b -> b.side().equals(r.won));
        FightData d = FightData.get(server);
        for (var e : r.bets.entrySet()) {
            Bet b = e.getValue();
            FightData.Board row = d.board(d.s.bets, e.getKey().toString(), b.name());
            boolean right = b.side().equals(r.won);
            if (right) row.right++;
            else row.wrong++;
            int back = !anyRight ? b.stake() : !right ? 0 : onWinner == 0 ? 0
                    : (int) Math.floor(pot * (double) b.stake() / onWinner);
            if (right) row.won += Math.max(0, back - b.stake());
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                if (back > 0) Fights.give(p, back);
                p.sendSystemMessage(Component.literal(right ? "Your bet won: " + back + " emerald" + (back == 1 ? ""
                        : "s") + "." : anyRight ? "Your bet lost." : "Nobody bet on the winner: your "
                        + b.stake() + " back.").withStyle(right ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
        }
        r.bets.clear();
    }

    private static void refund(MinecraftServer server, Run r) {
        for (var e : r.bets.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && e.getValue().stake() > 0) Fights.give(p, e.getValue().stake());
        }
        r.bets.clear();
    }

    // players joining a side

    public static AdminServer.Result join(ServerPlayer p, String side) {
        Run r = run;
        if (r == null || !r.s.join) return AdminServer.Result.fail("There's no staged fight to join now.");
        if (!side.equals("a") && !side.equals("b") && !side.equals("leave"))
            return AdminServer.Result.fail("Join a or b.");
        if (side.equals("leave")) {
            r.joined.remove(p.getUUID());
            return AdminServer.Result.said(List.of("You left the fight."));
        }
        r.joined.put(p.getUUID(), side);
        return AdminServer.Result.said(List.of("You fight beside " + sideName(r, side) + ": their blows pass you by;"
                + " the other side's don't."));
    }

    /**
     * a giant of the staged fight hurting a player: their own side's never; a watcher's never while spectators are
     * safe (near the fight); else as always
     */
    static boolean allow(ServerPlayer p, LivingEntity giant) {
        Run r = run;
        if (r == null || r.phase.equals("done")) return true;
        Slot x = r.slot(giant.getUUID());
        if (x == null) return true;
        String joined = r.joined.get(p.getUUID());
        if (joined != null) return !joined.equals(x.side());
        if (!r.s.spectators.equals("safe")) return true;
        return p.level() != r.level || p.position().distanceTo(r.spot) > r.radius;
    }

    /** (the tests) may this giant of the staged fight hurt this player */
    public static boolean allowForTests(ServerPlayer p, LivingEntity giant) {
        return allow(p, giant);
    }

    // the loser's remains (Part 8 "When a giant falls", for this one only)

    /** the remains this giant leaves when he falls: the script's choice for a staged fight's loser, else the rule */
    public static String remains(LivingEntity e, String rule) {
        Run r = run;
        if (r == null || r.loser == null || !e.getUUID().equals(r.loser.id) || r.s.remains.equals("rule")) return rule;
        return r.s.remains;
    }

    // the book and the commands

    /** the staged fight now, for the book: phase, round and score, each giant's side, health and name */
    static @Nullable JsonObject state(MinecraftServer server) {
        Run r = run;
        if (r == null) return null;
        JsonObject o = new JsonObject();
        o.addProperty("phase", r.phase);
        o.addProperty("round", r.round);
        o.addProperty("rounds", r.s.rounds);
        o.addProperty("winsA", r.winsA);
        o.addProperty("winsB", r.winsB);
        o.addProperty("won", r.won);
        o.addProperty("who", r.who);
        o.addProperty("bets", r.bets.size());
        com.google.gson.JsonArray a = new com.google.gson.JsonArray();
        for (Slot x : r.slots) {
            JsonObject j = new JsonObject();
            j.addProperty("side", x.side());
            j.addProperty("kind", x.g.key());
            j.addProperty("name", x.id == null ? AdminServer.nameOf(x.g) : called(x, true));
            j.addProperty("share", Math.round(x.share * 100) / 100.0);
            j.addProperty("broken", x.broken);
            j.addProperty("dead", x.dead);
            a.add(j);
        }
        o.add("giants", a);
        return o;
    }

    /** where a saved setup is fought: its own spot (its world loaded) */
    static @Nullable ServerLevel level(MinecraftServer server, Setup s) {
        if (s.dim.isEmpty()) return null;
        try {
            return server.getLevel(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    ResourceLocation.parse(s.dim)));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** a saved setup started where it was saved (a schedule, an event, the book's Replay) */
    public static AdminServer.Result replay(MinecraftServer server, String who, UUID by, String name) {
        Setup s = FightData.get(server).saved(name);
        if (s == null) return AdminServer.Result.fail("No fight setup called " + name + ".");
        ServerLevel l = level(server, s);
        if (l == null) return AdminServer.Result.fail("The setup " + s.name + " has no spot (save it where it should"
                + " be fought), or its world isn't there.");
        float yaw = s.yaw * Mth.DEG_TO_RAD;
        Vec3 look = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        return start(server, who, by, s, l, BlockPos.containing(s.x, s.y, s.z), look);
    }

    // (small helpers kept here for the run)

    /** the Fun page and the change log */
    static final class Fun {
        static void told(MinecraftServer server, Run r) {
            AdminServer.announce(server, server.getPlayerList().getPlayer(r.by), List.of(r.who
                    + " staged a scripted fight: " + sideNames(r.s) + "."));
            log(server, r, "staged fight: " + sideNames(r.s));
        }

        static void log(MinecraftServer server, Run r, String now) {
            AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), r.who, "all", "fun:fight",
                    "fight script", "", now, ""));
            Fights.changed();
        }
    }
}
