package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.List;
import net.jj.giants.fun.MgShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Siege: at dusk the players hold a walled keep in the middle of the arena while
 * the giants come at it in waves through the arena's gates (each wave bigger), hunting whoever they find. Bows and
 * crossbows from the walls and the tower; the team shares its lives. Hold out through every wave (or until dawn: the
 * round's time) to win.
 */
public final class Siege extends Coop {
    static final int[] ARENA_R = {48, 64, 80};
    static final int WAVES = 4;

    @Override
    public String id() {
        return "siege";
    }

    @Override
    public String name() {
        return "Siege";
    }

    @Override
    public String about() {
        return "Hold the keep at dusk while the giants come at it in waves through the arena's gates.";
    }

    @Override
    public String goal() {
        return "Hold the keep through every wave!";
    }

    @Override
    public long dayTime() {
        return 13200;
    }

    static int keepR(MgGame g) {
        return Math.max(14, (int) Math.round(ARENA_R[g.sizeIndex] * 0.42));
    }

    @Override
    public MgMap map(MgGame g) {
        MgMap m = arenaMap(g, ARENA_R);
        int k = keepR(g);
        m.add(MgShapes.keep(k), 0, 0);
        m.starts.clear();
        // in the keep's yard, round the tower
        m.ring(Math.max(8, g.players().size()), k * 0.6, Math.PI / 8);
        m.watch = new Vec3(0, 40, 0);
        return m;
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.archer(sp);
        }
        giveLives(g);
        g.data.put("wave", 0);
        g.data.put("waveAt", 0);
        g.data.put("felled", 0);
    }

    @Override
    public void go(MgGame g) {
        nextWave(g);
    }

    static int size(MgGame g, int wave) {
        return Math.min(4, 1 + wave / 2 + g.players().size() / 3);
    }

    private void nextWave(MgGame g) {
        int wave = (int) g.data.getOrDefault("wave", 0) + 1;
        g.data.put("wave", wave);
        g.data.put("waveAt", g.roundTicks);
        List<String> kinds = shuffled(g);
        if (kinds.isEmpty()) return;
        int n = size(g, wave);
        for (int i = 0; i < n; i++) {
            String kind = kinds.get(i % kinds.size());
            double hp = (60 + 30 * n(g)) * (1 + 0.1 * wave);
            g.giants.summonHp(kind, gate(g, i + wave), "hunting", g.giantSize, hp, 0.45, "wave" + wave, -1, x -> {});
        }
        for (MgGame.P p : g.people.values()) {
            ServerPlayer sp = g.online(p);
            if (sp == null || !p.in) continue;
            MgGame.title(sp, Component.literal("Wave " + wave + " of " + WAVES).withStyle(ChatFormatting.RED),
                    Component.literal(n + (n == 1 ? " giant comes" : " giants come") + " at the keep!"), 5, 40, 10);
        }
        g.tellRound(Component.literal("Wave " + wave + " of " + WAVES + ": " + n + (n == 1 ? " giant" : " giants")
                + " at the gates!").withStyle(ChatFormatting.RED));
    }

    @Override
    public void tick(MgGame g) {
        if (g.roundTicks % 20 != 0) return;
        int wave = (int) g.data.getOrDefault("wave", 0);
        if (wave == 0 || wave > WAVES) return;
        boolean clear = g.giants.standing().isEmpty() && !g.giants.waiting();
        int since = g.roundTicks - (int) g.data.getOrDefault("waveAt", 0);
        if (clear && wave >= WAVES) {
            g.data.put("wave", WAVES + 1);
            return;
        }
        // the next wave: when this one is down (after a breath), or after a minute and a half regardless
        if ((clear && since >= 100) || since >= 1800) {
            if (clear) reward(g, new ItemStack(Items.ARROW, 24), new ItemStack(Items.GOLDEN_APPLE));
            nextWave(g);
        }
        // the giants march on the keep when they have nobody to chase
        if (g.roundTicks % 100 == 0 && g.level != null)
            for (MgGiants.G k : g.giants.standing()) {
                var e = g.giants.entity(k);
                if (e != null && e.position().horizontalDistance() > keepR(g) + 4) MgGiants.goToward(g, k, 0, 0);
            }
    }

    @Override
    public void giantFell(MgGame g, MgGiants.G giant) {
        g.data.put("felled", (int) g.data.getOrDefault("felled", 0) + 1);
        MgGame.P by = giant.lastBy == null ? null : g.of(giant.lastBy);
        if (by != null) by.score++;
        g.tellRound(Component.literal("The " + Minigames.name(giant.kind) + " falls" + (by != null ? " to " + by.name
                : "") + "!").withStyle(ChatFormatting.GREEN));
    }

    @Override
    public MgGame.End check(MgGame g) {
        if ((int) g.data.getOrDefault("wave", 0) > WAVES)
            return new MgGame.End(everyone(g), "The keep held through all " + WAVES + " waves!" + mvpLine(g));
        return wiped(g, "The keep fell in wave " + g.data.getOrDefault("wave", 1) + ".");
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        return g.alive().isEmpty() ? new MgGame.End(List.of(), "The keep fell.")
                : new MgGame.End(everyone(g), "Dawn came and the keep still stands!" + mvpLine(g));
    }

    @Override
    public String bar(MgGame g) {
        int wave = Math.min(WAVES, (int) g.data.getOrDefault("wave", 0));
        return name() + " · wave " + wave + " of " + WAVES + " · " + g.giants.standing().size() + " at the walls · "
                + lives(g) + " lives · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        return Component.literal("Your blows: " + Math.round(p.dealt) + " · giants felled by you: " + p.score)
                .withStyle(ChatFormatting.YELLOW);
    }

    static List<Integer> waves() {
        List<Integer> w = new ArrayList<>();
        for (int i = 1; i <= WAVES; i++) w.add(i);
        return w;
    }
}
