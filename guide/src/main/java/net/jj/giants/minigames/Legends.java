package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Legends: one giant at a time walks into the middle of the arena, each one bigger
 * and stronger than the last. Whoever is knocked down stays down until that legend falls (then everyone is back up);
 * if everyone is down at once, the legends won. Bring down all five (or as many kinds as there are, at least three)
 * to win.
 */
public final class Legends extends Coop {
    static final int[] ARENA_R = {48, 64, 80};

    @Override
    public String id() {
        return "legends";
    }

    @Override
    public String name() {
        return "Legends";
    }

    @Override
    public String about() {
        return "One giant at a time, each bigger and stronger than the last: bring down every legend together.";
    }

    @Override
    public String goal() {
        return "Bring down every legend. Fall, and you're down until it does!";
    }

    static int count(MgGame g) {
        return Math.max(3, Math.min(5, Minigames.kinds().size()));
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
        List<String> order = new ArrayList<>();
        List<String> k = shuffled(g);
        for (int i = 0; i < count(g) && !k.isEmpty(); i++) order.add(k.get(i % k.size()));
        g.data.put("order", order);
        g.data.put("legend", 0);
        g.data.put("next", 40);
    }

    @SuppressWarnings("unchecked")
    private void comeIn(MgGame g) {
        List<String> order = (List<String>) g.data.get("order");
        int i = (int) g.data.getOrDefault("legend", 0);
        if (i >= order.size()) return;
        String kind = order.get(i);
        double hp = (110 + 55 * n(g)) * (1 + 0.35 * i), dmg = 0.4 * (1 + 0.15 * i);
        double size = Math.min(0.5, g.giantSize * (1 + 0.12 * i));
        g.giants.summonHp(kind, Vec3.ZERO, "hunting", size, hp, dmg, "legend", -1, x -> {});
        for (MgGame.P p : g.people.values()) {
            ServerPlayer sp = g.online(p);
            if (sp == null || !p.in) continue;
            MgGame.title(sp, Component.literal("Legend " + (i + 1) + " of " + order.size()).withStyle(
                    ChatFormatting.GOLD), Component.literal("The " + Minigames.name(kind)
                            + (i == 0 ? "" : ", stronger than the last")), 5, 50, 10);
        }
        g.tellRound(Component.literal("Legend " + (i + 1) + " of " + order.size() + ": the " + Minigames.name(kind)
                + "!").withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        if (!g.giants.standing().isEmpty() || g.giants.waiting()) return;
        int next = (int) g.data.getOrDefault("next", 0) - 20;
        g.data.put("next", next);
        if (next <= 0) {
            comeIn(g);
            g.data.put("next", 80);
        }
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        // down until the legend falls
        g.down(p, sp, 600, "Down until the legend falls!");
        p.respawnAt = Integer.MAX_VALUE;
        g.tellRound(Component.literal(p.name + " is down.").withStyle(ChatFormatting.RED));
    }

    @Override
    @SuppressWarnings("unchecked")
    public void giantFell(MgGame g, MgGiants.G giant) {
        int i = (int) g.data.getOrDefault("legend", 0) + 1;
        g.data.put("legend", i);
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score++;
        List<String> order = (List<String>) g.data.get("order");
        g.tellRound(Component.literal("The legend falls" + (by != null ? " to " + by.name : "") + "! (" + i + " of "
                + order.size() + ")").withStyle(ChatFormatting.GREEN));
        // everyone down is back up
        for (MgGame.P p : g.people.values()) if (p.alive && p.down) p.respawnAt = g.roundTicks + 20;
        reward(g, new ItemStack(Items.GOLDEN_APPLE, 2), new ItemStack(Items.ARROW, 16));
    }

    @Override
    @SuppressWarnings("unchecked")
    public MgGame.End check(MgGame g) {
        List<String> order = (List<String>) g.data.get("order");
        int i = (int) g.data.getOrDefault("legend", 0);
        if (order != null && i >= order.size())
            return new MgGame.End(everyone(g), "Every legend has fallen!" + mvpLine(g));
        boolean anyUp = false;
        for (MgGame.P p : g.alive()) if (!p.down) anyUp = true;
        if (!anyUp && !g.alive().isEmpty() || g.alive().isEmpty())
            return new MgGame.End(List.of(), "Everyone was down at once: the legends won (" + i + " fell).");
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public MgGame.End timeUp(MgGame g) {
        List<String> order = (List<String>) g.data.get("order");
        return new MgGame.End(List.of(), "Time's up: " + g.data.getOrDefault("legend", 0) + " of "
                + (order == null ? 0 : order.size()) + " legends fell.");
    }

    @Override
    @SuppressWarnings("unchecked")
    public String bar(MgGame g) {
        List<String> order = (List<String>) g.data.get("order");
        int i = (int) g.data.getOrDefault("legend", 0);
        int n = order == null ? 0 : order.size();
        String now = order != null && i < n ? " · the " + Minigames.name(order.get(i)) : "";
        return name() + " · legend " + Math.min(n, i + 1) + " of " + n + now + " · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        return Component.literal("Your blows: " + Math.round(p.dealt) + " · legends felled by you: " + p.score)
                .withStyle(ChatFormatting.YELLOW);
    }
}
