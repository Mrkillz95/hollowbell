package net.jj.hollowbell.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.command.GiantsCommand;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * The world-gen settings for where his grounds go (ADMIN_BRIDGE.md §18, Part 3): how far from the world's spawn his
 * ground may begin (groundNearest to groundFarthest, its nearest edge), the room kept between his grounds and every
 * other giant's (groundGap, edge to edge), and how many grounds of his biome the world has (groundCopies). The same file
 * in every giant (only the config class, his id and the shipped numbers differ).
 *
 * <p>At the shipped numbers (and gap 0, one copy) nothing here moves anything: he chooses exactly as he always has.
 */
public final class GroundRules {
    private GroundRules() {}

    /** his id on the bridges ("grounds", "groundspot") */
    public static final String ME = "hollowbell";
    /** the shipped band, nearest edge from spawn: his old 3000 to 15000 to the middle, less his shipped 900 */
    public static final int SHIPPED_NEAREST = 2100, SHIPPED_FARTHEST = 14100;
    /** the room a ground chosen after a fall always kept from the others (OtherGrounds.GAP) */
    public static final int FALL_GAP = OtherGrounds.GAP;

    public static int nearest() {
        return Math.max(0, Math.min(BellConfig.V.groundNearest, BellConfig.V.groundFarthest));
    }

    public static int farthest() {
        return Math.max(0, Math.max(BellConfig.V.groundNearest, BellConfig.V.groundFarthest));
    }

    /** the band is as shipped: the first ground is chosen the old way, nothing pulled into it */
    public static boolean asShipped() {
        return BellConfig.V.groundNearest == SHIPPED_NEAREST && BellConfig.V.groundFarthest == SHIPPED_FARTHEST;
    }

    public static int gap() {
        return Mth.clamp(BellConfig.V.groundGap, 0, 20000);
    }

    public static int copies() {
        return Mth.clamp(BellConfig.V.groundCopies, 1, 5);
    }

    /** the room a ground chosen after a fall keeps: the old 200, or more if the world asks */
    public static int fallGap() {
        return Math.max(FALL_GAP, gap());
    }

    /** the room an extra copy keeps from every ground (his own too) */
    public static int copyGap() {
        return Math.max(FALL_GAP, gap());
    }

    /** how far from spawn the middle of a new ground goes, drawn inside the band (for a ground this big) */
    public static double draw(RandomSource rnd, int radius) {
        return radius + nearest() + rnd.nextDouble() * (farthest() - nearest());
    }

    /**
     * One ground the world has: whose, where its middle is, how far it reaches, and the room its giant keeps (gap).
     */
    public record Ground(String giant, int x, int z, int r, int gap) {}

    /** (the tests) the other giants' grounds as given here instead of asking their bridges */
    public static @Nullable List<Ground> testOthers;

    /**
     * Every other giant's grounds (their extra copies too), from their bridges: "grounds" (JSON lines, with the room
     * each keeps), else the older "groundspot" line (room 0). Mine left out.
     */
    public static List<Ground> others(@Nullable MinecraftServer server, String me) {
        if (testOthers != null) return List.copyOf(testOthers);
        List<Ground> out = new ArrayList<>();
        if (server == null) return out;
        Set<String> said = new HashSet<>();
        for (String line : GiantsCommand.ask(server, "grounds", "")) {
            String t = line.trim();
            if (!t.startsWith("{\"ground\"")) continue;
            try {
                JsonObject o = JsonParser.parseString(t).getAsJsonObject();
                String g = o.get("giant").getAsString();
                said.add(g);
                if (g.equals(me)) continue;
                out.add(new Ground(g, o.get("x").getAsInt(), o.get("z").getAsInt(), o.get("r").getAsInt(),
                        o.has("gap") ? o.get("gap").getAsInt() : 0));
            } catch (RuntimeException e) {
                // (a line that can't be read is left out)
            }
        }
        for (String line : GiantsCommand.ask(server, "groundspot", "")) {
            String[] w = line.trim().split("\\s+");
            if (w.length != 5 || !w[0].equals("groundspot") || w[1].equals(me) || said.contains(w[1])) continue;
            try {
                out.add(new Ground(w[1], Integer.parseInt(w[2]), Integer.parseInt(w[3]), Integer.parseInt(w[4]), 0));
            } catch (NumberFormatException e) {
                // (left out)
            }
        }
        return out;
    }

    /** one "grounds" line for the bridge */
    public static String line(int x, int z, int r, int copy) {
        JsonObject o = new JsonObject();
        o.addProperty("ground", 1);
        o.addProperty("giant", ME);
        o.addProperty("x", x);
        o.addProperty("z", z);
        o.addProperty("r", r);
        o.addProperty("gap", gap());
        o.addProperty("copy", copy);
        return o.toString();
    }

    /** the room two grounds keep: the bigger of the two giants' (0 and 0: no rule) */
    static int room(int mine, int theirs) {
        return Math.max(mine, theirs);
    }

    /**
     * How well a ground here keeps the rules: below 0 it breaks one (by that many blocks), 0 or more it keeps all.
     * band: inside the nearest/farthest band from spawn (sx, sz). Each other ground: at least room(gap, its gap)
     * edge to edge, where that room is above 0.
     */
    public static double slack(int sx, int sz, int x, int z, int r, List<Ground> others, int gap, boolean band) {
        double worst = Double.MAX_VALUE;
        if (band) {
            double d = Math.hypot(x - sx, z - sz);
            worst = Math.min(worst, Math.min(d - (r + nearest()), (r + farthest()) - d));
        }
        for (Ground o : others) {
            int room = room(gap, o.gap());
            if (room <= 0) continue;
            worst = Math.min(worst, Math.hypot(x - o.x(), z - o.z()) - (r + o.r() + room));
        }
        return worst;
    }

    public static boolean fits(int sx, int sz, int x, int z, int r, List<Ground> others, int gap, boolean band) {
        return slack(sx, sz, x, z, r, others, gap, band) >= 0;
    }

    /**
     * The spot as it is if it keeps the rules (so with nothing asked, nothing moves and nothing random is drawn).
     * Else: pushed straight out of whatever it is too near and back into the band, a few rounds; then spots drawn
     * in the band (or round the spot) until one keeps them all; if none does (a band too full for the room asked),
     * the one that breaks them least.
     */
    public static int[] settle(RandomSource rnd, int sx, int sz, int x, int z, int r, List<Ground> others, int gap,
            boolean band) {
        if (fits(sx, sz, x, z, r, others, gap, band)) return new int[] {x, z};
        int px = x, pz = z;
        for (int round = 0; round < 6; round++) {
            for (Ground o : others) {
                int room = room(gap, o.gap());
                if (room <= 0) continue;
                double dx = px - o.x(), dz = pz - o.z(), len = Math.hypot(dx, dz), keep = r + o.r() + room;
                if (len >= keep) continue;
                if (len < 1) {
                    dx = 1;
                    dz = 0;
                    len = 1;
                }
                px = Mth.floor(o.x() + dx / len * keep) + (dx >= 0 ? 1 : -1);
                pz = Mth.floor(o.z() + dz / len * keep) + (dz >= 0 ? 1 : -1);
            }
            if (band) {
                int[] in = intoBand(sx, sz, px, pz, r);
                px = in[0];
                pz = in[1];
            }
            if (fits(sx, sz, px, pz, r, others, gap, band)) return new int[] {px, pz};
        }
        int bx = px, bz = pz;
        double best = slack(sx, sz, px, pz, r, others, gap, band);
        for (int i = 0; i < 160; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            int tx, tz;
            if (band) {
                double d = draw(rnd, r);
                tx = Mth.floor(sx + Math.cos(a) * d);
                tz = Mth.floor(sz + Math.sin(a) * d);
            } else {
                double d = 400 + rnd.nextDouble() * (4000 + 50.0 * i);
                tx = Mth.floor(x + Math.cos(a) * d);
                tz = Mth.floor(z + Math.sin(a) * d);
            }
            double s = slack(sx, sz, tx, tz, r, others, gap, band);
            if (s >= 0) return new int[] {tx, tz};
            if (s > best) {
                best = s;
                bx = tx;
                bz = tz;
            }
        }
        return new int[] {bx, bz};
    }

    /** a spot pulled straight in (or out) from spawn into the band, for a ground this big */
    public static int[] intoBand(int sx, int sz, int x, int z, int r) {
        double dx = x - sx, dz = z - sz, len = Math.hypot(dx, dz);
        double lo = r + nearest(), hi = r + farthest();
        if (len >= lo && len <= hi) return new int[] {x, z};
        if (len < 1) {
            dx = 1;
            dz = 0;
            len = 1;
        }
        double want = hi - lo < 4 ? (lo + hi) / 2 : len < lo ? lo + 2 : hi - 2;
        return new int[] {Mth.floor(sx + dx / len * want), Mth.floor(sz + dz / len * want)};
    }
}
