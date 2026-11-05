package net.jj.giants.minigames;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Possession: one giant stands in the arena, and each player in turn takes his
 * controls for thirty seconds (the admin book's Play as, Part 7, for a round): steering him and using his moves.
 * Everyone else fights on foot. Knocking a player out with the giant scores 2, on foot 1; bringing the giant down
 * scores 3 (another comes). Most points when the time runs out wins.
 */
public final class Possession extends MgMode {
    static final int TURN = 600;
    static final int[] ARENA = {40, 56, 72};

    @Override
    public String id() {
        return "possession";
    }

    @Override
    public String name() {
        return "Possession";
    }

    @Override
    public String about() {
        return "Take turns controlling a giant with all his moves for thirty seconds; everyone else fights on foot.";
    }

    @Override
    public String goal() {
        return "Score the most: knock players out, as the giant or on foot!";
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
    public @Nullable String cannot(MinecraftServer server) {
        for (Giant g : AdminServer.giants()) if (FunGiants.does(server, g).contains("playas")) return null;
        return "No installed giant can be played (Play as).";
    }

    @Override
    public MgMap map(MgGame g) {
        return Coop.arenaMap(g, ARENA);
    }

    static List<String> playable(MgGame g) {
        List<String> out = new ArrayList<>();
        for (String k : Coop.shuffled(g)) {
            Giant gi = AdminServer.giant(k);
            if (gi != null && FunGiants.does(g.server, gi).contains("playas")) out.add(k);
        }
        return out;
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.light(sp);
        }
        List<UUID> q = new ArrayList<>();
        for (MgGame.P p : g.players()) q.add(p.id);
        Collections.shuffle(q, new java.util.Random(g.level == null ? 0 : g.level.random.nextLong()));
        g.data.put("queue", q);
        g.data.put("turn", 0);
        g.data.put("kinds", new ArrayList<>(playable(g)));
        vessel(g);
    }

    @SuppressWarnings("unchecked")
    private void vessel(MgGame g) {
        List<String> kinds = (List<String>) g.data.get("kinds");
        if (kinds.isEmpty()) kinds.addAll(playable(g));
        if (kinds.isEmpty()) return;
        String kind = kinds.remove(0);
        g.data.put("vesselKind", kind);
        g.giants.summonHp(kind, Vec3.ZERO, "calm", g.giantSize, 140 + 50 * Coop.n(g), 0.8, "vessel", -1,
                k -> g.data.put("vessel", k.id));
    }

    static @Nullable MgGiants.G vesselOf(MgGame g) {
        return g.data.get("vessel") instanceof UUID u ? g.giants.of(u) : null;
    }

    static @Nullable UUID holder(MgGame g) {
        return g.data.get("holder") instanceof UUID u ? u : null;
    }

    private void release(MgGame g) {
        UUID h = holder(g);
        g.data.remove("holder");
        MgGiants.G v = vesselOf(g);
        if (h == null || v == null) return;
        ServerPlayer sp = g.server.getPlayerList().getPlayer(h);
        Giant gi = AdminServer.giant(v.kind);
        if (sp == null || gi == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("by", sp.getStringUUID());
        o.addProperty("on", false);
        FunGiants.act(g.server, gi, v.id.toString(), "playas", o);
    }

    @SuppressWarnings("unchecked")
    private void nextTurn(MgGame g) {
        release(g);
        MgGiants.G v = vesselOf(g);
        LivingEntity e = v == null ? null : g.giants.entity(v);
        if (e == null) return;
        List<UUID> q = (List<UUID>) g.data.get("queue");
        int turn = (int) g.data.getOrDefault("turn", 0);
        for (int tries = 0; tries < q.size(); tries++) {
            UUID id = q.get(Math.floorMod(turn + tries, q.size()));
            MgGame.P p = g.of(id);
            ServerPlayer sp = p == null ? null : g.online(p);
            if (sp == null || !p.alive || p.down || !p.in) continue;
            g.data.put("turn", turn + tries + 1);
            // beside him first when far (as Play as does), then his own mod seats them a few ticks later
            if (sp.distanceTo(e) > 40) g.place(sp, p, e.position().add(8, 0, 0));
            g.data.put("seat", new Object[] {id, g.roundTicks + 5});
            return;
        }
    }

    private void seat(MgGame g, UUID id) {
        MgGiants.G v = vesselOf(g);
        MgGame.P p = g.of(id);
        ServerPlayer sp = p == null ? null : g.online(p);
        Giant gi = v == null ? null : AdminServer.giant(v.kind);
        if (sp == null || gi == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("by", sp.getStringUUID());
        o.addProperty("on", true);
        AdminServer.Result r = FunGiants.act(g.server, gi, v.id.toString(), "playas", o);
        if (!r.ok()) return;
        g.data.put("holder", id);
        g.data.put("turnEnds", g.roundTicks + TURN);
        MgGame.title(sp, Component.literal("You're the " + Minigames.name(v.kind) + "!").withStyle(ChatFormatting.GOLD),
                Component.literal("30 seconds: knock them out with his moves"), 5, 40, 10);
        g.tellRound(Component.literal(p.name + " takes over the " + Minigames.name(v.kind) + "!")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    @Override
    public void go(MgGame g) {
        g.data.put("turnEnds", 0);
    }

    @Override
    public void tick(MgGame g) {
        if (g.data.get("seat") instanceof Object[] s && g.roundTicks >= (int) s[1]) {
            g.data.remove("seat");
            seat(g, (UUID) s[0]);
        }
        if (g.roundTicks % 20 != 0) return;
        MgGiants.G v = vesselOf(g);
        if ((v == null || v.fell || v.gone) && !g.giants.waiting()) {
            if (holder(g) != null) g.data.remove("holder");
            if (v != null && g.roundTicks % 60 == 0) vessel(g);
            if (v == null && !g.giants.waiting() && g.data.get("vessel") == null) vessel(g);
            return;
        }
        UUID h = holder(g);
        ServerPlayer hp = h == null ? null : g.server.getPlayerList().getPlayer(h);
        boolean gone = h != null && (hp == null || !hp.isPassenger());
        int ends = (int) g.data.getOrDefault("turnEnds", 0);
        if ((h == null || gone || g.roundTicks >= ends) && g.data.get("seat") == null) nextTurn(g);
    }

    /** the player behind a blow: on foot, or the one at the giant's controls */
    private @Nullable MgGame.P blame(MgGame g, DamageSource src, MgGame.P victim) {
        if (src.getEntity() instanceof ServerPlayer by) {
            MgGame.P q = g.of(by.getUUID());
            if (q != null && q != victim && !by.isPassenger()) return q;
        }
        MgGiants.G v = vesselOf(g);
        UUID h = holder(g);
        if (v == null || h == null) return null;
        String kind = net.jj.giants.tools.Tools.kindOf(src.getEntity());
        if (kind == null) kind = net.jj.giants.tools.Tools.kindOf(src.getDirectEntity());
        if (kind == null && src.getEntity() instanceof ServerPlayer by && by.getUUID().equals(h)) kind = v.kind;
        return v.kind.equals(kind) && !h.equals(victim.id) ? g.of(h) : null;
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        MgGame.P by = blame(g, src, p);
        boolean giant = by != null && by.id.equals(holder(g));
        if (by != null) by.score += giant ? 2 : 1;
        if (p.id.equals(holder(g))) release(g);
        g.down(p, sp, 4, "Knocked out!");
        g.tellRound(Component.literal(p.name + " was knocked out" + (by == null ? "" : " by " + by.name
                + (giant ? " (as the giant)" : ""))).withStyle(ChatFormatting.RED));
    }

    @Override
    public boolean giantMayBeHurt(MgGame g, MgGiants.G giant, DamageSource src, MgGame.P by) {
        return by == null || !by.id.equals(holder(g));
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score += 3;
        g.data.remove("holder");
        g.data.remove("vessel");
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " falls" + (by == null ? "" : " to " + by.name
                + " (+3)") + "! Another comes.").withStyle(ChatFormatting.GREEN));
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        return mostPoints(g, "points");
    }

    /** the players with the most points win (all who share it) */
    static MgGame.End mostPoints(MgGame g, String word) {
        int best = Integer.MIN_VALUE;
        for (MgGame.P p : g.players()) best = Math.max(best, p.score);
        List<UUID> w = new ArrayList<>();
        List<String> names = new ArrayList<>();
        if (best > 0)
            for (MgGame.P p : g.players())
                if (p.score == best) {
                    w.add(p.id);
                    names.add(p.name);
                }
        if (w.isEmpty()) return new MgGame.End(List.of(), "Nobody scored.");
        return new MgGame.End(w, String.join(" and ", names) + (names.size() == 1 ? " wins" : " win") + " with "
                + best + " " + word + ".");
    }

    @Override
    public String bar(MgGame g) {
        UUID h = holder(g);
        MgGame.P p = h == null ? null : g.of(h);
        MgGiants.G v = vesselOf(g);
        String who = p == null || v == null ? "the giant waits"
                : p.name + " is the " + Minigames.name(v.kind) + " (" + Math.max(0, ((int) g.data.getOrDefault(
                        "turnEnds", 0) - g.roundTicks) / 20) + " s)";
        return name() + " · " + who + " · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        return Component.literal("Your points: " + p.score + (p.id.equals(holder(g)) ? " · you're the giant!" : ""))
                .withStyle(ChatFormatting.YELLOW);
    }
}
