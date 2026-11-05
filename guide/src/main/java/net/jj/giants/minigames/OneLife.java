package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) One Life: every installed kind of giant comes, each only once (up to four at a
 * time), and every player has one life. Bring them all down together; whoever is still standing at the end wins.
 */
public final class OneLife extends Coop {
    static final int[] ARENA_R = {48, 64, 80};

    @Override
    public String id() {
        return "onelife";
    }

    @Override
    public String name() {
        return "One Life";
    }

    @Override
    public String about() {
        return "Every kind of giant comes once, and you each have one life: bring them all down together.";
    }

    @Override
    public String goal() {
        return "One life each: bring down every giant!";
    }

    @Override
    public MgMap map(MgGame g) {
        return arenaMap(g, ARENA_R);
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.champion(sp);
        }
        List<String> all = shuffled(g);
        g.data.put("queue", new ArrayList<>(all));
        g.data.put("total", all.size());
        g.data.put("felled", 0);
        g.data.put("gate", 0);
    }

    @SuppressWarnings("unchecked")
    private void comeIn(MgGame g) {
        List<String> q = (List<String>) g.data.get("queue");
        if (q.isEmpty()) return;
        String kind = q.remove(0);
        int gate = (int) g.data.getOrDefault("gate", 0);
        g.data.put("gate", gate + 1);
        g.giants.summonHp(kind, gate(g, gate), "hunting", g.giantSize, 80 + 40 * n(g), 0.5, "once", -1, x -> {});
        g.tellRound(Component.literal("The " + Minigames.name(kind) + " comes, once only.").withStyle(
                ChatFormatting.GOLD));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        int most = Math.min(4, 1 + g.roundTicks / 1200);
        if (g.giants.standing().size() + (g.giants.waiting() ? 1 : 0) < most && g.roundTicks % 100 == 0) comeIn(g);
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        g.data.put("felled", (int) g.data.getOrDefault("felled", 0) + 1);
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score++;
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " falls for good" + (by == null ? ""
                : " to " + by.name) + " (" + g.data.get("felled") + " of " + g.data.get("total") + ").")
                .withStyle(ChatFormatting.GREEN));
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        g.out(p, sp, "Your one life is spent!");
    }

    @Override
    public MgGame.End check(MgGame g) {
        int felled = (int) g.data.getOrDefault("felled", 0), total = (int) g.data.getOrDefault("total", 1);
        if (felled >= total && !g.alive().isEmpty()) {
            List<UUID> w = new ArrayList<>();
            for (MgGame.P p : g.alive()) w.add(p.id);
            return new MgGame.End(w, "Every giant has fallen: " + g.alive().size() + " of " + g.players().size()
                    + " made it through!" + mvpLine(g));
        }
        return wiped(g, "Every life was spent: " + felled + " of " + total + " giants fell.");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        return new MgGame.End(List.of(), "Time's up: " + g.data.getOrDefault("felled", 0) + " of "
                + g.data.getOrDefault("total", 0) + " giants fell.");
    }

    @Override
    public String bar(MgGame g) {
        return name() + " · " + g.data.getOrDefault("felled", 0) + " of " + g.data.getOrDefault("total", 0)
                + " down · " + g.alive().size() + " alive · " + g.clock();
    }
}
