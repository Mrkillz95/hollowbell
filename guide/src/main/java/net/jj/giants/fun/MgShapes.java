package net.jj.giants.fun;

import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.5) The Part 7 arena and dens, and the minigames' own pieces, for the minigames' map
 * builder (package net.jj.giants.minigames): each a plan worked out column by column round its middle, exactly as the
 * World tools build them, only into a minigame's own world and with nothing written down for taking it down (the
 * world is thrown away after the round).
 */
public final class MgShapes {
    private MgShapes() {}

    /** what one column gets */
    public interface Out {
        /** a block at this height */
        void set(int y, BlockState s);

        /** everything from this height up cleared (before the blocks are set) */
        void clearAbove(int y);

        /** this under the lowest block set, down to the ground (after the blocks are set) */
        void support(BlockState s);
    }

    /** a plan of a place: how far it reaches from its middle, and each column of it */
    public static final class Shape {
        private final Builds.Shape s;

        Shape(Builds.Shape s) {
            this.s = s;
        }

        public int reach() {
            return s.reach();
        }

        /** the column dx, dz from the middle, its floor at height f */
        public void column(int dx, int dz, int f, Out out) {
            Builds.Col c = new Builds.Col();
            s.column(dx, dz, Math.sqrt((double) dx * dx + dz * dz), f, c);
            if (c.clearFrom != Integer.MAX_VALUE) out.clearAbove(c.clearFrom);
            for (int i = 0; i < c.ys.size(); i++) out.set(c.ys.get(i)[0], c.states.get(i));
            if (c.support != null) out.support(c.support);
        }
    }

    /** (Part 11) a plan given column by column by its maker (an adventure map's saved blocks) */
    public interface Columns {
        int reach();

        void column(int dx, int dz, int f, Out out);
    }

    /** (Part 11) a shape from a plan of one's own */
    public static Shape custom(Columns c) {
        return new Shape(new Builds.Shape() {
            @Override
            public void column(int dx, int dz, double d, int f, Builds.Col out) {
                c.column(dx, dz, f, new Out() {
                    @Override
                    public void set(int y, BlockState s) {
                        out.set(y, s);
                    }

                    @Override
                    public void clearAbove(int y) {
                        out.clearAbove(y);
                    }

                    @Override
                    public void support(BlockState s) {
                        out.support = s;
                    }
                });
            }

            @Override
            public int reach() {
                return c.reach();
            }
        });
    }

    /** the World tools' arena, r round (its stands reach further: {@link Shape#reach()}) */
    public static Shape arena(int r) {
        return new Shape(new Arena(Math.max(24, r)).made());
    }

    /** where an arena's floor ends, its wall stands and its stands begin (for spots) */
    public static int arenaWall(int r) {
        return Math.max(24, r) + 2;
    }

    /** a giant's den r round, made for him at this height (null: no den for that kind) */
    public static @Nullable Shape den(String kind, int r, double height) {
        Plan p = Dens.shape(kind, Mth.clamp(r, Dens.LEAST, Dens.MOST), height);
        return p == null ? null : new Shape(p);
    }

    /** the radius the World tools give his den at this size */
    public static int denRadius(String kind, double size) {
        return Mth.clamp((int) Math.round(FunGiants.reach(kind, size) * Dens.ROOM), Dens.LEAST, Dens.MOST);
    }

    /**
     * (Part 11, the Wreck Cove example) where Wreckback's cove den r round has its galleon (x, z from the middle), how
     * far out its shore is, and the anchor's spot on the beach: {wreckX, wreckZ, shore, anchorX, anchorZ}
     */
    public static double[] cove(int r) {
        r = Mth.clamp(r, Dens.LEAST, Dens.MOST);
        double shore = r * 0.7, a1 = Plan.rnd(r, 3, 177) * Math.PI * 2, a3 = a1 + Math.PI * 0.6;
        double place = shore * 0.78, ar = shore + (r - shore) * 0.55;
        return new double[] {Math.cos(a1) * place, Math.sin(a1) * place, shore, Math.cos(a3) * ar, Math.sin(a3) * ar};
    }

    /** (Siege) the keep the players hold: a walled yard round a tower, r round */
    public static Shape keep(int r) {
        return new Shape(new MgKeep(Math.max(12, r)).made());
    }

    /** (Escort) a road length long from west to east, with its banks, gates and lanterns */
    public static Shape road(int half, int width) {
        return new Shape(new MgRoad(Math.max(24, half), Math.max(5, width)).made());
    }

    /** (Escort) where the middle of the road is at x, for a road half this long */
    public static double roadZ(int half, double x) {
        return MgRoad.roadZ(Math.max(24, half), x);
    }

    /** a number from 0 to 1 for a spot (the same each time) */
    public static double hash(int x, int z, int salt) {
        return Builds.hash(x, z, salt);
    }
}
