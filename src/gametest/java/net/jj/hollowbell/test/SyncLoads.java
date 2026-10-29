package net.jj.hollowbell.test;

import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.level.chunk.status.ChunkStatus;

/**
 * For the stall probe: the server thread asking for chunks that aren't loaded (and so waiting while they're loaded
 * or made), counted by the first line of this mod that asked. Only while on.
 */
public final class SyncLoads {
    private SyncLoads() {}

    public static volatile boolean on;
    /** what the probe is doing now (each wait is counted under it) */
    public static volatile String label = "";

    static final Map<String, Integer> BY = new TreeMap<>();
    /** the first chunk each was waited for at */
    static final Map<String, String> AT = new TreeMap<>();

    public static synchronized void note(int x, int z, ChunkStatus st) {
        String who = StackWalker.getInstance()
                .walk(s -> s.filter(f -> f.getClassName().startsWith("net.jj.hollowbell.")
                                && !f.getClassName().startsWith("net.jj.hollowbell.test."))
                        .findFirst()
                        .map(f -> f.getClassName() + "." + f.getMethodName() + ":" + f.getLineNumber())
                        .orElse("(not this mod)"));
        String k = label + ": " + who + " [" + st.getName() + "]";
        BY.merge(k, 1, Integer::sum);
        AT.putIfAbsent(k, "chunk " + x + ", " + z);
    }

    public static synchronized String report() {
        StringBuilder b = new StringBuilder("chunks waited for on the server thread:\n");
        if (BY.isEmpty()) b.append("  none\n");
        BY.forEach((k, v) -> b.append("  ").append(v).append(' ').append(k).append(" (first at ").append(AT.get(k))
                .append(")\n"));
        return b.toString();
    }

    public static synchronized void clear() {
        BY.clear();
        AT.clear();
    }
}
