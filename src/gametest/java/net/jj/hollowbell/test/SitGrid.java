package net.jj.hollowbell.test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntFunction;
import net.jj.hollowbell.solid.SitHost;
import net.jj.hollowbell.solid.SitPayload;
import net.jj.hollowbell.solid.SitSeat;
import net.jj.hollowbell.solid.Sitting;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Sit anywhere, over the whole of him: every spot of him a player can stand on (a player's box resting on a top of
 * his, clear of him and of the world's blocks: where the player's own game would leave them standing), sampled over
 * every part, and sitting down there, then getting up again. The same in every giant.
 */
public final class SitGrid {
    private SitGrid() {}

    /** a spot a player can stand on him: their feet, and the part of him under them */
    public record Spot(Vec3 feet, int frame, String how) {}

    /**
     * Where a player can stand on him: over every column of him, their middle over the column's middle and just over
     * each of its edges (only a sliver of their feet on him), on each top of his there, wherever they fit.
     */
    public static List<Spot> standable(SitHost h, ServerPlayer p) {
        List<Spot> out = new ArrayList<>();
        AABB body = h.solidBox();
        double cw = Solid.columnWidth(h);
        var dim = p.getDimensions(Pose.STANDING);
        double half = dim.width() / 2, edge = cw / 2 + half - 0.05, part = cw / 2 + half - 0.25;
        int xa = Mth.floor(body.minX / cw), xb = Mth.floor(body.maxX / cw);
        int za = Mth.floor(body.minZ / cw), zb = Mth.floor(body.maxZ / cw);
        Solid.Runs r = new Solid.Runs();
        // (their middle over the column's; a third of their feet over its edge; a sliver of them over it)
        double[][] at = {{0, 0}, {part, 0}, {0, -part}, {-edge, 0}, {0, edge}};
        String[] how = {"middle", "part over the edge", "part over the edge", "a sliver on", "a sliver on"};
        for (int i = xa; i <= xb; i++)
            for (int k = za; k <= zb; k++) {
                double cx = (i + 0.5) * cw, cz = (k + 0.5) * cw;
                Solid.column(h, cx, cz, body.minY - 1, body.maxY + 1, false, r);
                if (r.n == 0) continue;
                double[] tops = java.util.Arrays.copyOf(r.top, r.n);
                int[] frames = java.util.Arrays.copyOf(r.frame, r.n);
                for (int j = 0; j < tops.length; j++)
                    for (int q = 0; q < at.length; q++) {
                        double[] o = at[q];
                        Vec3 feet = new Vec3(cx + o[0], tops[j], cz + o[1]);
                        AABB box = dim.makeBoundingBox(feet);
                        if (Solid.depthIn(h, box, 0.02, null) > 0.0) continue;
                        if (!p.level().noCollision(p, box)) continue;
                        // (resting on the world's ground there too, only just off it: that's standing on the ground)
                        if (!p.level().noCollision(p, box.move(0, -0.05, 0))) continue;
                        out.add(new Spot(feet, frames[j], how[q]));
                    }
            }
        return out;
    }

    /** up to `per` spots of each part of him, spread over it, so every part is tried */
    public static List<Spot> sample(List<Spot> all, int per) {
        Map<Integer, List<Spot>> by = new LinkedHashMap<>();
        for (Spot s : all) by.computeIfAbsent(s.frame(), f -> new ArrayList<>()).add(s);
        List<Spot> out = new ArrayList<>();
        for (List<Spot> l : by.values()) {
            int n = l.size();
            if (n <= per) out.addAll(l);
            else for (int q = 0; q < per; q++) out.add(l.get((int) ((q + 0.5) * n / per)));
        }
        return out;
    }

    /**
     * At each spot: they stand there, their game counts the triple crouch there (standing on him), the server sits
     * them down there, the seat is where they stood on that part of him, and jumping gets them up standing there (or
     * set down safely beside him). What went wrong, by part of him (empty: all good).
     */
    public static Map<String, List<String>> tryAll(SitHost h, ServerPlayer p, List<Spot> spots, IntFunction<String> name) {
        Map<String, List<String>> bad = new TreeMap<>();
        OLD_KIT.clear();
        for (Spot s : spots) {
            // (what the kit before sit-anywhere-on-every-part found there: only tops under all but the outer 0.05 of
            // their feet; for the record of what was wrong)
            clearUp(p);
            p.teleportTo(p.serverLevel(), s.feet().x, s.feet().y, s.feet().z, 0f, 0f);
            p.setOnGround(true);
            if (Solid.anchorUnder(h, p, 0.6, 0.6) == null)
                OLD_KIT.computeIfAbsent(name.apply(s.frame()), k -> new ArrayList<>()).add(at(s.feet(), h) + " " + s.how());
            String why = tryOne(h, p, s);
            if (why != null) bad.computeIfAbsent(name.apply(s.frame()), k -> new ArrayList<>()).add(why);
        }
        return bad;
    }

    /** by part of him: the spots the kit before couldn't sit you on (its triple crouch didn't count there) */
    public static final Map<String, List<String>> OLD_KIT = new TreeMap<>();

    static String at(Vec3 v, SitHost h) {
        Vec3 m = h.solidSelf().position();
        return String.format("(%.1f, %.1f, %.1f)", v.x - m.x, v.y - m.y, v.z - m.z);
    }

    static String tryOne(SitHost h, ServerPlayer p, Spot s) {
        clearUp(p);
        Vec3 f = s.feet();
        p.teleportTo(p.serverLevel(), f.x, f.y, f.z, 0f, 0f);
        p.setDeltaMovement(Vec3.ZERO);
        p.setOnGround(true);
        String where = at(f, h) + " " + s.how();
        if (Sitting.standingOn(p) != h) return where + ": the triple crouch isn't counted there";
        Sitting.Result res = Sitting.trySit(p, h);
        if (res != Sitting.Result.SAT) return where + ": " + res;
        if (!(p.getVehicle() instanceof SitSeat seat)) return where + ": no seat";
        double off = seat.spotNow(h).distanceTo(f);
        if (off > 1.3) return where + ": sat " + String.format("%.1f", off) + " away";
        Sitting.standUp(seat, p);
        String up = null;
        if (p.getVehicle() instanceof SitSeat) up = where + ": still sitting after jumping";
        else if (!p.isPassenger()) {
            // standing on that spot of him, or beside him on clear ground: never inside him
            if (!Solid.clear(h, p, p.getBoundingBox().deflate(0.05)))
                up = where + ": got up inside him at " + at(p.position(), h);
            else if (p.position().distanceTo(f) > 2.6 && Solid.onTop(h, p, 0.3, 0.3))
                up = where + ": got up somewhere else on him " + at(p.position(), h);
        }
        clearUp(p);
        if (up != null) return up;
        // the gesture from their game, with the spot under them as their game has it, while the server still has them
        // a tick behind (a part of him that moves carries them on in their game first)
        p.teleportTo(p.serverLevel(), f.x, f.y, f.z, 0f, 0f);
        p.setOnGround(true);
        Solid.Anchor game = Sitting.gameSpot(h, p);
        if (game == null) return where + ": their game finds no spot under them";
        p.teleportTo(p.serverLevel(), f.x + 1.2, f.y + 0.5, f.z - 0.8, 0f, 0f);
        Sitting.asked(p, new SitPayload(h.solidSelf().getId(), game));
        if (!(p.getVehicle() instanceof SitSeat seat2)) return where + ": a tick behind, not sat";
        double off2 = seat2.spotNow(h).distanceTo(f);
        clearUp(p);
        if (off2 > 1.3) return where + ": a tick behind, sat " + String.format("%.1f", off2) + " away";
        return null;
    }

    /** off any seat (his set-down's too), seats gone */
    static void clearUp(ServerPlayer p) {
        var v = p.getVehicle();
        if (v != null) {
            p.stopRiding();
            if (v instanceof SitSeat) v.discard();
        }
    }

    /** "part: n bad, e.g. ..." lines */
    public static String report(Map<String, List<String>> bad, int tried) {
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (var e : bad.entrySet()) {
            n += e.getValue().size();
            b.append("\n  ")
                    .append(e.getKey())
                    .append(": ")
                    .append(e.getValue().size())
                    .append(" spots, e.g. ")
                    .append(e.getValue().get(0));
        }
        Map<String, Integer> by = new TreeMap<>();
        for (var e : bad.values())
            for (String w : e)
                for (String k : new String[] {"middle", "part over the edge", "a sliver on"})
                    if (w.contains(") " + k + ":")) by.merge(k + " / " + w.substring(w.indexOf(':') + 2), 1, Integer::sum);
        return n + " of " + tried + " standing spots can't be sat on " + by + ":" + b;
    }
}
