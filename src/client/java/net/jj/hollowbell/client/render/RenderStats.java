package net.jj.hollowbell.client.render;

import java.lang.management.ManagementFactory;

/**
 * Counters for measuring how much drawing him costs (draw calls, render-thread time, memory made per frame, glass
 * sorts). Off unless the dev autotest turns it on; when off each counter is a single false check.
 */
public final class RenderStats {
    private RenderStats() {}

    /** set only by the dev autotest */
    public static volatile boolean on;

    public static long frames, renderNs, glassNs, draws, glassDraws, sortsStarted, sortUploads, allocBytes;
    /** the first time his meshes went to the GPU: how long the frame spent on it, and how many frames it took */
    public static long uploadNs, uploadMaxFrameNs;

    public static int uploadFrames;

    private static final com.sun.management.ThreadMXBean MX =
            ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean t ? t : null;

    public static long allocated() {
        return MX == null ? 0 : MX.getCurrentThreadAllocatedBytes();
    }

    public static void reset() {
        frames = renderNs = glassNs = draws = glassDraws = sortsStarted = sortUploads = allocBytes = 0;
    }
}
