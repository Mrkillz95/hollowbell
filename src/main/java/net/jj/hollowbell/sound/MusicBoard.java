package net.jj.hollowbell.sound;

import java.util.Map;

/**
 * Only one giant's fight theme plays at a time, across all five of JJ's boss mods. They share a board in the
 * system properties (the same code in all five): every client tick a mod that wants its theme puts
 * {distance squared to the nearest fighting boss, the time now} under its mod id, and takes it off when it doesn't.
 * A mod plays only if no other entry newer than two seconds is nearer (a tie goes to the smaller mod id).
 */
public final class MusicBoard {
    private MusicBoard() {}

    public static final String KEY = "jj.giants.music";
    /** how old an entry can be and still count */
    public static final long FRESH_MS = 2000;

    @SuppressWarnings("unchecked")
    public static Map<String, double[]> musicBoard() {
        java.util.Properties p = System.getProperties();
        Object o = p.get(KEY);
        if (!(o instanceof java.util.Map)) { p.putIfAbsent(KEY, new java.util.concurrent.ConcurrentHashMap<String, double[]>()); o = p.get(KEY); }
        return (java.util.Map<String, double[]>) o;
    }

    /** this mod wants its theme: it is this far (squared) from the nearest boss fighting */
    public static void want(String mod, double distSq) { musicBoard().put(mod, new double[]{distSq, System.currentTimeMillis()}); }

    public static void drop(String mod) { musicBoard().remove(mod); }

    /** does this mod's theme win the board right now */
    public static boolean plays(Map<String, double[]> board, String mod, long now) {
        double[] me = board.get(mod);
        if (me == null || now - (long) me[1] > FRESH_MS) return false;
        for (var e : board.entrySet()) {
            if (e.getKey().equals(mod)) continue;
            double[] v = e.getValue();
            if (v == null || v.length < 2 || now - (long) v[1] > FRESH_MS) continue;
            if (v[0] < me[0] || (v[0] == me[0] && e.getKey().compareTo(mod) < 0)) return false;
        }
        return true;
    }

    /** any giant's theme wants to play right now (the game's own music keeps quiet) */
    public static boolean anyFresh(Map<String, double[]> board, long now) {
        for (double[] v : board.values()) if (v != null && v.length >= 2 && now - (long) v[1] <= FRESH_MS) return true;
        return false;
    }
}
