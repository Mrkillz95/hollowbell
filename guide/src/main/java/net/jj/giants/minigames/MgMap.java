package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.jj.giants.fun.MgShapes;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 10A) One round's map, as a mode plans it: the pieces to build (each round its middle), how far the play area
 * reaches (the world border: a square this many blocks each way from the middle), where players start, where watchers
 * look from, and any spots the mode keeps for itself (a giant's den, the road's ends...). Heights are found on the
 * built map when used.
 */
public final class MgMap {
    public record Piece(MgShapes.Shape shape, int x, int z) {}

    public final List<Piece> pieces = new ArrayList<>();
    /** half the side of the play area (the border), from (0, 0) */
    public int half = 64;
    /** where the players start (x, z), handed out in turn */
    public final List<Vec3> starts = new ArrayList<>();
    /** where watchers come in: above the middle */
    public Vec3 watch = new Vec3(0, 24, 0);
    /** the mode's own spots */
    public final Map<String, Vec3> spots = new LinkedHashMap<>();

    public MgMap add(MgShapes.Shape s, int x, int z) {
        pieces.add(new Piece(s, x, z));
        return this;
    }

    /** n starts spread round a ring of this radius (a quarter turn off the axes, clear of an arena's gates) */
    public MgMap ring(int n, double radius, double turn) {
        for (int i = 0; i < n; i++) {
            double a = turn + i * Math.PI * 2 / Math.max(1, n);
            starts.add(new Vec3(Math.cos(a) * radius, 0, Math.sin(a) * radius));
        }
        return this;
    }

    public Vec3 start(int i) {
        return starts.isEmpty() ? Vec3.ZERO : starts.get(Math.floorMod(i, starts.size()));
    }
}
