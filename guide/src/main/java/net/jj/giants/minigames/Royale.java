package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.jj.giants.fun.MgShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.8) Giant Royale (a minigame only, never a world preset): every installed giant waits
 * in his own den round the edge of a field with an arena in the middle, all of them hunting; the players start between
 * them with a light kit (better things in chests round the field), the safe zone (the world border) closes in toward
 * the arena in two steps, and the last player standing wins. Players may hit each other. Alone, you win by lasting
 * until the end.
 */
public final class Royale extends MgMode {
    static final int[] HALF = {96, 128, 160}, ARENA = {28, 36, 44};
    static final double RING = 0.7;

    @Override
    public String id() {
        return "royale";
    }

    @Override
    public String name() {
        return "Giant Royale";
    }

    @Override
    public String about() {
        return "Every giant hunts from his den while the safe zone closes in: the last player standing wins.";
    }

    @Override
    public String goal() {
        return "Be the last one standing. The zone closes in!";
    }

    @Override
    public boolean pvp() {
        return true;
    }

    /** the dens' ring and how big each den may be, for these many kinds on this map */
    static int[] ring(int sizeIndex, int kinds) {
        int half = HALF[sizeIndex];
        int ring = (int) Math.round(half * RING);
        int most = (int) Math.floor(ring * Math.sin(Math.PI / Math.max(3, kinds)) * 0.85);
        most = Math.min(most, half - 6 - ring);
        return new int[] {ring, Math.max(10, most)};
    }

    @Override
    public MgMap map(MgGame g) {
        MgMap m = new MgMap();
        int s = g.sizeIndex;
        m.half = HALF[s];
        int arena = ARENA[s];
        m.add(MgShapes.arena(arena), 0, 0);
        List<String> kinds = Minigames.kinds();
        int[] rr = ring(s, kinds.size());
        for (int i = 0; i < kinds.size(); i++) {
            String k = kinds.get(i);
            double a = i * Math.PI * 2 / kinds.size();
            int x = (int) Math.round(Math.cos(a) * rr[0]), z = (int) Math.round(Math.sin(a) * rr[0]);
            int r = Math.min(rr[1], MgShapes.denRadius(k, g.giantSize));
            MgShapes.Shape den = MgShapes.den(k, r, Minigames.height(k, g.giantSize));
            if (den != null) m.add(den, x, z);
            m.spots.put(k, new Vec3(x, 0, z));
        }
        // the players start between the arena's stands and the dens, half a step round from each den
        int reach = MgShapes.arena(arena).reach();
        double start = (reach + rr[0] - rr[1]) / 2.0;
        int n = Math.max(8, g.players().size());
        double off = kinds.isEmpty() ? 0 : Math.PI / kinds.size();
        m.ring(n, start, off);
        m.watch = new Vec3(0, 40, 0);
        return m;
    }

    @Override
    public void begin(MgGame g) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp != null) MgKits.royale(sp);
        }
        MgKits.royaleChests(g, 10 + g.players().size());
        for (String k : Minigames.kinds()) {
            Vec3 at = g.map.spots.get(k);
            if (at != null) g.giants.summon(k, at, "hunting", g.giantSize, 1, 0.6, "hunter", -1, null);
        }
        g.data.put("stage", 0);
    }

    /** the zone: whole at first, then to 55% of the field, then down to the arena's middle, by the round's clock */
    @Override
    public void tick(MgGame g) {
        ServerLevel l = g.level;
        if (l == null || g.map == null) return;
        int stage = (int) g.data.getOrDefault("stage", 0);
        double t = g.roundTicks / (double) g.roundMax();
        WorldBorder b = l.getWorldBorder();
        double full = 2 * g.map.half + 1, mid = Math.max(48, full * 0.55), end = Math.max(24, ARENA[g.sizeIndex] * 0.8);
        long ms = (long) (g.roundMax() * 50L * 0.25);
        if (stage == 0 && t >= 0.2) {
            b.lerpSizeBetween(b.getSize(), mid, ms);
            closing(g, "The safe zone is closing in!");
            g.data.put("stage", 1);
        } else if (stage == 1 && t >= 0.6) {
            b.lerpSizeBetween(b.getSize(), end, ms);
            closing(g, "The safe zone closes on the arena!");
            g.data.put("stage", 2);
        }
    }

    private void closing(MgGame g, String line) {
        g.borderChanged();
        for (MgGame.P p : g.people.values()) {
            ServerPlayer sp = g.online(p);
            if (sp == null || !p.in) continue;
            MgGame.title(sp, Component.empty(), Component.literal(line).withStyle(ChatFormatting.RED), 5, 50, 10);
            sp.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.MASTER, 0.6f, 1.2f);
        }
        g.tellRound(Component.literal(line).withStyle(ChatFormatting.RED));
    }

    @Override
    public MgGame.End check(MgGame g) {
        List<MgGame.P> alive = g.alive();
        int all = g.players().size();
        if (all >= 2 && alive.size() == 1)
            return new MgGame.End(List.of(alive.get(0).id), alive.get(0).name + " is the last one standing!");
        if (alive.isEmpty())
            return new MgGame.End(List.of(), all >= 2 ? "Nobody was left standing: the giants won."
                    : "The giants got you: they won.");
        return null;
    }

    @Override
    public MgGame.End timeUp(MgGame g) {
        List<MgGame.P> alive = new ArrayList<>(g.alive());
        if (alive.isEmpty()) return new MgGame.End(List.of(), "Nobody was left standing: the giants won.");
        if (g.players().size() == 1)
            return new MgGame.End(List.of(alive.get(0).id), alive.get(0).name + " outlasted the giants!");
        // most health left wins (all who share it)
        alive.sort(Comparator.comparingDouble((MgGame.P p) -> -health(g, p)));
        double best = health(g, alive.get(0));
        List<UUID> w = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (MgGame.P p : alive)
            if (health(g, p) >= best - 0.01) {
                w.add(p.id);
                names.add(p.name);
            }
        return new MgGame.End(w, "Time's up: " + String.join(" and ", names) + (names.size() == 1 ? " wins" : " win")
                + " with the most health left.");
    }

    private static double health(MgGame g, MgGame.P p) {
        ServerPlayer sp = g.online(p);
        return sp == null ? 0 : sp.getHealth();
    }

    @Override
    public String bar(MgGame g) {
        return name() + " · " + g.alive().size() + " left · " + g.giants.standing().size() + " giants · " + g.clock();
    }

    @Override
    public Component status(MgGame g, MgGame.P p) {
        ServerLevel l = g.level;
        int zone = l == null ? 0 : (int) Math.round(l.getWorldBorder().getSize());
        return Component.literal(g.alive().size() + " left · zone " + zone + " blocks · knock-outs " + p.kos)
                .withStyle(ChatFormatting.YELLOW);
    }
}
