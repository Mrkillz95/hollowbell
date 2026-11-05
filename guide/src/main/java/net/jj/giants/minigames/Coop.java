package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.jj.giants.fun.MgShapes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A) What the modes where the players stand together against the giants share: a pool of lives for the
 * whole team (a knock-out spends one: down for a few seconds, then back in; none left: out for good), the arena map,
 * the kinds in a shuffled order, and everyone in it winning together.
 */
abstract class Coop extends MgMode {
    /** the arena's radius for each map size */
    static final int[] ARENA = {40, 56, 72};

    /** lives for the team, for this many players */
    int lives(int players) {
        return 2 * players + 1;
    }

    int downSeconds() {
        return 6;
    }

    static int lives(MgGame g) {
        return (int) g.data.getOrDefault("lives", 0);
    }

    /** the arena map: players round its floor, a quarter turn off the gates */
    static MgMap arenaMap(MgGame g, int[] radii) {
        MgMap m = new MgMap();
        int r = radii[g.sizeIndex];
        MgShapes.Shape a = MgShapes.arena(r);
        m.add(a, 0, 0);
        m.half = a.reach() + 4;
        m.ring(Math.max(8, g.players().size()), r * 0.72, Math.PI / 4);
        m.watch = new Vec3(0, 30, 0);
        // the four gates' insides (where giants come in): north, east, south, west
        int in = MgShapes.arenaWall(r) - 6;
        m.spots.put("gate0", new Vec3(0, 0, -in));
        m.spots.put("gate1", new Vec3(in, 0, 0));
        m.spots.put("gate2", new Vec3(0, 0, in));
        m.spots.put("gate3", new Vec3(-in, 0, 0));
        return m;
    }

    static Vec3 gate(MgGame g, int i) {
        Vec3 v = g.map == null ? null : g.map.spots.get("gate" + Math.floorMod(i, 4));
        return v == null ? Vec3.ZERO : v;
    }

    /** every installed kind, shuffled (with the round's own random) */
    static List<String> shuffled(MgGame g) {
        List<String> k = new ArrayList<>(Minigames.kinds());
        if (g.level != null) Collections.shuffle(k, new java.util.Random(g.level.random.nextLong()));
        return k;
    }

    void giveLives(MgGame g) {
        g.data.put("lives", lives(g.players().size()));
    }

    @Override
    public void knockedOut(MgGame g, MgGame.P p, ServerPlayer sp, DamageSource src) {
        int lives = lives(g);
        if (lives > 0) {
            g.data.put("lives", lives - 1);
            g.down(p, sp, downSeconds(), "Knocked down!");
            g.tellRound(Component.literal(p.name + " is down (" + (lives - 1) + (lives - 1 == 1 ? " life" : " lives")
                    + " left for the team).").withStyle(ChatFormatting.RED));
        } else g.out(p, sp, "No lives left!");
    }

    /** everyone who played (a team win) */
    static List<UUID> everyone(MgGame g) {
        List<UUID> out = new ArrayList<>();
        for (MgGame.P p : g.players()) out.add(p.id);
        return out;
    }

    /** nobody left standing: the giants won */
    static @Nullable MgGame.End wiped(MgGame g, String line) {
        return g.alive().isEmpty() ? new MgGame.End(List.of(), line) : null;
    }

    /** a reward to everyone still in (after a giant falls) */
    static void reward(MgGame g, net.minecraft.world.item.ItemStack... items) {
        for (MgGame.P p : g.alive()) {
            ServerPlayer sp = g.online(p);
            if (sp == null) continue;
            for (var s : items) MgKits.give(sp, s.copy());
            sp.inventoryMenu.broadcastChanges();
        }
    }

    /** the player who hurt him most this round (for the "most damage" line), or null */
    static @Nullable MgGame.P mvp(MgGame g) {
        MgGame.P best = null;
        for (MgGame.P p : g.players()) if (p.dealt > 0 && (best == null || p.dealt > best.dealt)) best = p;
        return best;
    }

    static String mvpLine(MgGame g) {
        MgGame.P b = mvp(g);
        return b == null ? "" : " Most damage: " + b.name + " (" + Math.round(b.dealt) + ").";
    }

    /** how many players there are (at least one) */
    static int n(MgGame g) {
        return Math.max(1, g.players().size());
    }
}
