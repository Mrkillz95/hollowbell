package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Boss Rush: the players stand together in the arena, and the giants come through
 * its gates one after another (two at once in the second half), each hunting, each falling quicker than usual. Every
 * one that falls drops a reward for everyone still in. Bring down the target number before the time runs out; the
 * team shares its lives.
 */
public final class BossRush extends Coop {
    @Override
    public String id() {
        return "bossrush";
    }

    @Override
    public String name() {
        return "Boss Rush";
    }

    @Override
    public String about() {
        return "Giant after giant comes through the arena's gates: bring down enough of them together before time"
                + " runs out.";
    }

    @Override
    public String goal() {
        return "Bring down the giants together, one after another!";
    }

    static int target(MgGame g) {
        return Math.min(8, 3 + g.players().size() / 2);
    }

    @Override
    public MgMap map(MgGame g) {
        return arenaMap(g, ARENA);
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.fighter(sp);
        }
        giveLives(g);
        g.data.put("felled", 0);
        g.data.put("target", target(g));
        g.data.put("queue", new ArrayList<>(shuffled(g)));
        g.data.put("next", 60);
        g.data.put("gate", 0);
    }

    @SuppressWarnings("unchecked")
    private void comeIn(MgGame g) {
        List<String> q = (List<String>) g.data.get("queue");
        if (q.isEmpty()) q.addAll(shuffled(g));
        if (q.isEmpty()) return;
        String kind = q.remove(0);
        int gate = (int) g.data.getOrDefault("gate", 0);
        g.data.put("gate", gate + 1);
        int felled = (int) g.data.getOrDefault("felled", 0);
        // a little tougher each time
        double hp = (90 + 45 * n(g)) * (1 + 0.12 * felled);
        g.giants.summonHp(kind, gate(g, gate), "hunting", g.giantSize, hp, 0.5, "boss", -1, x -> {});
        g.tellRound(Component.literal("The " + Minigames.name(kind) + " comes through the gate!")
                .withStyle(ChatFormatting.GOLD));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        int next = (int) g.data.getOrDefault("next", 0);
        int most = g.roundTicks > g.roundMax() / 2 ? 2 : 1;
        int standing = g.giants.standing().size() + (g.giants.waiting() ? 1 : 0);
        if (standing >= most) {
            g.data.put("next", 60);
            return;
        }
        next -= 20;
        g.data.put("next", next);
        if (next <= 0) {
            comeIn(g);
            g.data.put("next", 100);
        }
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        int felled = (int) g.data.getOrDefault("felled", 0) + 1;
        g.data.put("felled", felled);
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score++;
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " falls" + (by != null ? " to " + by.name : "")
                + "! (" + felled + " of " + (int) g.data.get("target") + ")").withStyle(ChatFormatting.GREEN));
        reward(g, new ItemStack(Items.GOLDEN_APPLE), new ItemStack(Items.ARROW, 12), new ItemStack(Items.COOKED_BEEF, 4));
    }

    @Override
    public MgGame.End check(MgGame g) {
        int felled = (int) g.data.getOrDefault("felled", 0), target = (int) g.data.getOrDefault("target", 3);
        if (felled >= target)
            return new MgGame.End(everyone(g), "All " + target + " giants brought down together!" + mvpLine(g));
        return wiped(g, "The giants won: " + felled + " of " + target + " brought down.");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        int felled = (int) g.data.getOrDefault("felled", 0), target = (int) g.data.getOrDefault("target", 3);
        return felled >= target ? new MgGame.End(everyone(g), "Done in time!" + mvpLine(g))
                : new MgGame.End(List.of(), "Time's up: " + felled + " of " + target + " brought down.");
    }

    @Override
    public String bar(MgGame g) {
        return name() + " · " + g.data.getOrDefault("felled", 0) + " of " + g.data.getOrDefault("target", 0)
                + " down · " + lives(g) + " lives · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        return Component.literal("Your blows: " + Math.round(p.dealt) + " · giants felled by you: " + p.score)
                .withStyle(ChatFormatting.YELLOW);
    }
}
