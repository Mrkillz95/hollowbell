package net.jj.giants.systems.remains;

import java.util.LinkedHashMap;
import java.util.Map;

/** (remains) Each giant's remains design, and making the blocks of one fallen giant. */
public final class Designs {
    private Designs() {}

    private static final Map<String, Design> ALL = new LinkedHashMap<>();

    static {
        add(new GloamwickRemains());
        add(new CerberusRemains());
        add(new WreckbackRemains());
        add(new SwarmforgeRemains());
        add(new HollowbellRemains());
        add(new LanternWillowRemains());
        add(new PitchgutRemains());
        add(new FurrowmawRemains());
    }

    private static void add(Design d) {
        ALL.put(d.kind, d);
    }

    public static Design of(String kind) {
        return ALL.get(kind);
    }

    public static Iterable<Design> all() {
        return ALL.values();
    }

    /**
     * The blocks of his remains at this size (model blocks times size = world blocks), turned to his heading (the
     * game's yaw), every rot stage. Pure work on his model: safe off the server thread.
     */
    public static Carcass.Out plan(Design d, GiantModel m, double size, float yaw, long seed) {
        // (one at a time per design: a design may keep its layout while it works)
        synchronized (d) {
            return planNow(d, m, size, yaw, seed);
        }
    }

    private static Carcass.Out planNow(Design d, GiantModel m, double size, float yaw, long seed) {
        Pose p = new Pose(m);
        d.pose(m, p);
        p.solve();
        double[] lie = d.lie(m, p);
        Carcass c = new Carcass(m, p, d, size, lie, yaw, seed);
        c.body();
        d.bones(m, p, c);
        return c.compose();
    }
}
