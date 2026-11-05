package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Kaiju War: three giants fight each other all round the arena (each goes for the
 * nearest of the others, again and again; one falls, another comes through a gate), and the players are the
 * survivors in between, one life each. The last blow on a falling giant scores 3, every half minute alive scores 1;
 * everyone still standing when it ends wins.
 */
public final class KaijuWar extends MgMode {
    static final int[] ARENA = {56, 72, 96};

    @Override
    public String id() {
        return "kaiju";
    }

    @Override
    public String name() {
        return "Kaiju War";
    }

    @Override
    public String about() {
        return "Giants fight each other all round you: survive the war in between them, one life each.";
    }

    @Override
    public String goal() {
        return "Survive the giants' war!";
    }

    @Override
    public String cannot(net.minecraft.server.MinecraftServer server) {
        return Minigames.kinds().size() < 2 ? "It needs at least two kinds of giant installed." : null;
    }

    @Override
    public MgMap map(MgGame g) {
        return Coop.arenaMap(g, ARENA);
    }

    static int war(MgGame g) {
        return Math.min(3, Minigames.kinds().size());
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.fighter(sp);
        }
        g.data.put("queue", new ArrayList<>(Coop.shuffled(g)));
        g.data.put("gate", 0);
        for (int i = 0; i < war(g); i++) comeIn(g);
    }

    @SuppressWarnings("unchecked")
    private void comeIn(MgGame g) {
        List<String> q = (List<String>) g.data.get("queue");
        if (q.isEmpty()) q.addAll(Coop.shuffled(g));
        // never two of a kind at once
        List<String> here = new ArrayList<>();
        for (MgGiants.G k : g.giants.standing()) here.add(k.kind);
        String kind = null;
        for (String k : q) if (!here.contains(k)) {
            kind = k;
            break;
        }
        if (kind == null) return;
        q.remove(kind);
        int gate = (int) g.data.getOrDefault("gate", 0);
        g.data.put("gate", gate + 1);
        g.giants.summonHp(kind, Coop.gate(g, gate), "hunting", g.giantSize, 180 + 40 * Coop.n(g), 0.5, "kaiju", -1,
                x -> {});
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        if (g.roundTicks % 600 == 0) for (MgGame.P p : g.alive()) p.score++;
        List<MgGiants.G> st = g.giants.standing();
        if (st.size() + (g.giants.waiting() ? 1 : 0) < war(g) && g.roundTicks % 300 == 0) comeIn(g);
        if (g.roundTicks % 200 != 20) return;
        // each goes for the nearest of the others
        for (MgGiants.G a : st) {
            LivingEntity ea = g.giants.entity(a);
            if (ea == null) continue;
            MgGiants.G best = null;
            double bd = Double.MAX_VALUE;
            for (MgGiants.G b : st) {
                LivingEntity eb = g.giants.entity(b);
                if (b == a || eb == null) continue;
                double d = ea.distanceToSqr(eb);
                if (d < bd) {
                    bd = d;
                    best = b;
                }
            }
            if (best != null) MgGiants.meet(g, a, best);
        }
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        MgGame.P by = giant.lastBy == null || g.server.getTickCount() - giant.lastAt > 200 ? null : g.of(giant.lastBy);
        if (by != null) by.score += 3;
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " falls in the war" + (by == null ? ""
                : ": " + by.name + " struck last (+3)") + "!").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        g.out(p, sp, "Caught in the war!");
    }

    @Override
    public MgGame.End check(MgGame g) {
        return g.alive().isEmpty() ? new MgGame.End(List.of(), "Nobody survived the war.") : null;
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        List<UUID> w = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (MgGame.P p : g.alive()) {
            w.add(p.id);
            names.add(p.name);
        }
        return w.isEmpty() ? new MgGame.End(List.of(), "Nobody survived the war.")
                : new MgGame.End(w, String.join(", ", names) + " survived the war!");
    }

    @Override
    public String bar(MgGame g) {
        return name() + " · " + g.giants.standing().size() + " giants fighting · " + g.alive().size() + " survivors · "
                + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        return Component.literal(p.score + " points · stay alive!").withStyle(ChatFormatting.YELLOW);
    }
}
