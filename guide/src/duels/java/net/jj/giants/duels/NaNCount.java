package net.jj.giants.duels;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** how many times an entity was kept from moving to a NaN or infinite position (see mixin.NoNaNPosition) */
public final class NaNCount {
    private NaNCount() {}

    public static final AtomicInteger STOPPED = new AtomicInteger();
    /** what they were (entity type ids) */
    public static final Set<String> WHO = ConcurrentHashMap.newKeySet();
}
