package net.jj.giants.minigames;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Rival Tamers: the players split into teams (two to four, the arena's red, blue,
 * yellow and green sides), each with a giant of its own. The giants go for each other; each team takes turns at its
 * giant's controls (forty seconds each, Play as), the rest of the team fights on foot beside him (their own giant and
 * team mates are safe from them). The last team with its giant standing wins.
 */
public final class RivalTamers extends MgMode {
    static final int[] ARENA = {48, 64, 80};
    static final int TURN = 800;
    static final String[] TEAM = {"Red", "Blue", "Yellow", "Green"};
    static final ChatFormatting[] COLOUR = {ChatFormatting.RED, ChatFormatting.BLUE, ChatFormatting.YELLOW,
        ChatFormatting.GREEN};

    @Override
    public String id() {
        return "rivals";
    }

    @Override
    public String name() {
        return "Rival Tamers";
    }

    @Override
    public String about() {
        return "Teams each tame a giant: take turns at his controls and fight beside him. The last giant standing wins.";
    }

    @Override
    public String goal() {
        return "Keep your team's giant standing: bring down theirs!";
    }

    @Override
    public int fewest() {
        return 2;
    }

    @Override
    public boolean pvp() {
        return true;
    }

    @Override
    public boolean teams() {
        return true;
    }

    static int teams(MgGame g) {
        return Math.max(2, Math.min(4, g.players().size()));
    }

    /** the middle of a team's side (north red, east blue, south yellow, west green) */
    static Vec3 side(MgGame g, int team, double f) {
        double r = ARENA[g.sizeIndex] * f;
        return switch (team) {
            case 0 -> new Vec3(0, 0, -r);
            case 1 -> new Vec3(r, 0, 0);
            case 2 -> new Vec3(0, 0, r);
            default -> new Vec3(-r, 0, 0);
        };
    }

    @Override
    public MgMap map(MgGame g) {
        return Coop.arenaMap(g, ARENA);
    }

    @Override
    public void begin(MgGame g) {
        int t = teams(g), i = 0;
        List<String> kinds = Coop.shuffled(g);
        for (MgGame.P p : g.players()) {
            p.team = i++ % t;
            ServerPlayer sp = g.online(p);
            if (sp == null || !p.in) continue;
            MgKits.light(sp);
            Vec3 s = side(g, p.team, 0.78).add((i % 3 - 1) * 2.5, 0, (i % 2) * 2.5);
            g.place(sp, p, s);
            MgGame.title(sp, Component.literal("Team " + TEAM[p.team]).withStyle(COLOUR[p.team]),
                    Component.literal("Keep your giant standing!"), 5, 40, 10);
        }
        for (int k = 0; k < t && !kinds.isEmpty(); k++) {
            int size = 0;
            for (MgGame.P p : g.players()) if (p.team == k) size++;
            final int team = k;
            g.giants.summonHp(kinds.get(k % kinds.size()), side(g, k, 0.55), "calm", g.giantSize,
                    220 + 90 * Math.max(1, size), 0.6, "team", k, x -> g.data.put("giant" + team, x.id));
            g.data.put("turn" + k, 0);
            g.data.put("ends" + k, 0);
        }
    }

    static @Nullable MgGiants.G giantOf(MgGame g, int team) {
        return g.data.get("giant" + team) instanceof UUID u ? g.giants.of(u) : null;
    }

    static boolean standing(MgGiants.G k) {
        return k != null && !k.fell && !k.gone;
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        int t = teams(g);
        // the giants go for the nearest of the others, again every ten seconds
        if (g.roundTicks % 200 == 20)
            for (int a = 0; a < t; a++) {
                MgGiants.G ga = giantOf(g, a);
                LivingEntity ea = ga == null ? null : g.giants.entity(ga);
                if (ea == null || !standing(ga) || driver(g, a) != null) continue;
                MgGiants.G best = null;
                double bd = Double.MAX_VALUE;
                for (int b = 0; b < t; b++) {
                    MgGiants.G gb = giantOf(g, b);
                    LivingEntity eb = gb == null ? null : g.giants.entity(gb);
                    if (b == a || eb == null || !standing(gb)) continue;
                    double d = ea.distanceToSqr(eb);
                    if (d < bd) {
                        bd = d;
                        best = gb;
                    }
                }
                if (best != null) MgGiants.meet(g, ga, best);
            }
        // each team takes its turn at its giant's controls
        for (int k = 0; k < t; k++) {
            if (g.data.get("seat" + k) instanceof Object[] s && g.roundTicks >= (int) s[1]) {
                g.data.remove("seat" + k);
                seat(g, k, (UUID) s[0]);
            }
            MgGiants.G gk = giantOf(g, k);
            if (!standing(gk)) continue;
            UUID d = driver(g, k);
            ServerPlayer dp = d == null ? null : g.server.getPlayerList().getPlayer(d);
            boolean off = d != null && (dp == null || !dp.isPassenger());
            if ((d == null || off || g.roundTicks >= (int) g.data.getOrDefault("ends" + k, 0))
                    && g.data.get("seat" + k) == null) nextDriver(g, k);
        }
    }

    static @Nullable UUID driver(MgGame g, int team) {
        return g.data.get("driver" + team) instanceof UUID u ? u : null;
    }

    private void release(MgGame g, int team) {
        UUID d = driver(g, team);
        g.data.remove("driver" + team);
        MgGiants.G k = giantOf(g, team);
        ServerPlayer sp = d == null ? null : g.server.getPlayerList().getPlayer(d);
        Giant gi = k == null ? null : AdminServer.giant(k.kind);
        if (sp == null || gi == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("by", sp.getStringUUID());
        o.addProperty("on", false);
        FunGiants.act(g.server, gi, k.id.toString(), "playas", o);
    }

    private void nextDriver(MgGame g, int team) {
        release(g, team);
        MgGiants.G k = giantOf(g, team);
        LivingEntity e = k == null ? null : g.giants.entity(k);
        Giant gi = k == null ? null : AdminServer.giant(k.kind);
        if (e == null || gi == null || !FunGiants.does(g.server, gi).contains("playas")) return;
        List<MgGame.P> mine = new ArrayList<>();
        for (MgGame.P p : g.players()) if (p.team == team && p.alive && !p.down && p.in) mine.add(p);
        if (mine.isEmpty()) return;
        int turn = (int) g.data.getOrDefault("turn" + team, 0);
        MgGame.P p = mine.get(Math.floorMod(turn, mine.size()));
        g.data.put("turn" + team, turn + 1);
        ServerPlayer sp = g.online(p);
        if (sp == null) return;
        if (sp.distanceTo(e) > 40) g.place(sp, p, e.position().add(6, 0, 0));
        g.data.put("seat" + team, new Object[] {p.id, g.roundTicks + 5});
    }

    private void seat(MgGame g, int team, UUID id) {
        MgGiants.G k = giantOf(g, team);
        MgGame.P p = g.of(id);
        ServerPlayer sp = p == null ? null : g.online(p);
        Giant gi = k == null ? null : AdminServer.giant(k.kind);
        if (sp == null || gi == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("by", sp.getStringUUID());
        o.addProperty("on", true);
        if (!FunGiants.act(g.server, gi, k.id.toString(), "playas", o).ok()) return;
        g.data.put("driver" + team, id);
        g.data.put("ends" + team, g.roundTicks + TURN);
        MgGame.title(sp, Component.literal("Your turn at the controls!").withStyle(COLOUR[team]),
                Component.literal("40 seconds as your team's " + Minigames.name(k.kind)), 5, 40, 10);
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        if (p.team >= 0 && p.id.equals(driver(g, p.team))) release(g, p.team);
        g.down(p, sp, 5, "Knocked out!");
    }

    @Override
    public boolean giantMayBeHurt(MgGame g, MgGiants.G giant, DamageSource src, MgGame.P by) {
        // a team never hurts its own giant
        return by == null || giant.team != by.team;
    }

    @Override
    public boolean playerMayBeHurt(MgGame g, MgGame.P p, DamageSource src) {
        // a team's own giant (and anything of his) never hurts the team
        if (p.team < 0) return true;
        MgGiants.G own = giantOf(g, p.team);
        if (own == null) return true;
        String kind = net.jj.giants.tools.Tools.kindOf(src.getEntity());
        if (kind == null) kind = net.jj.giants.tools.Tools.kindOf(src.getDirectEntity());
        return !own.kind.equals(kind);
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        if (giant.team < 0) return;
        release(g, giant.team);
        g.tellRound(Component.literal("Team " + TEAM[giant.team] + "'s " + Minigames.name(giant.kind) + " falls!")
                .withStyle(COLOUR[giant.team]));
    }

    private static List<UUID> team(MgGame g, int t) {
        List<UUID> out = new ArrayList<>();
        for (MgGame.P p : g.players()) if (p.team == t) out.add(p.id);
        return out;
    }

    @Override
    public MgGame.End check(MgGame g) {
        if (g.giants.waiting() || g.roundTicks < 40) return null;
        int t = teams(g), left = 0, last = -1;
        for (int k = 0; k < t; k++)
            if (standing(giantOf(g, k))) {
                left++;
                last = k;
            }
        if (left == 1) return new MgGame.End(team(g, last), "Team " + TEAM[last] + "'s giant is the last standing!");
        if (left == 0) return new MgGame.End(List.of(), "Every team's giant fell: nobody won.");
        return null;
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        int t = teams(g), best = -1;
        double bh = -1;
        for (int k = 0; k < t; k++) {
            MgGiants.G gk = giantOf(g, k);
            LivingEntity e = gk == null || !standing(gk) ? null : g.giants.entity(gk);
            double h = e == null ? -1 : e.getHealth() / Math.max(1, e.getMaxHealth());
            if (h > bh) {
                bh = h;
                best = k;
            }
        }
        if (best < 0) return new MgGame.End(List.of(), "Time's up: no giant left standing.");
        return new MgGame.End(team(g, best), "Time's up: Team " + TEAM[best] + "'s giant is the healthiest.");
    }

    @Override
    public String bar(MgGame g) {
        StringBuilder b = new StringBuilder(name());
        for (int k = 0; k < teams(g); k++) {
            MgGiants.G gk = giantOf(g, k);
            LivingEntity e = gk == null || !standing(gk) ? null : g.giants.entity(gk);
            b.append(" · ").append(TEAM[k]).append(' ').append(e == null ? "down"
                    : Math.round(e.getHealth() / Math.max(1, e.getMaxHealth()) * 100) + "%");
        }
        return b.append(" · ").append(g.clock()).toString();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        if (p.team < 0) return null;
        UUID d = driver(g, p.team);
        MgGame.P dp = d == null ? null : g.of(d);
        return Component.literal("Team " + TEAM[p.team] + (dp == null ? "" : " · at the controls: " + dp.name))
                .withStyle(COLOUR[p.team]);
    }
}
