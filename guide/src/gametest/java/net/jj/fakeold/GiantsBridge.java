package net.jj.fakeold;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;

/** A giant's bridge from before the performance protocol: it knows /giants, and answers null to anything else. */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static int calls;

    public static List<String> giants(MinecraftServer server, String action, String arg) {
        calls++;
        if (action.equals("status")) return new ArrayList<>(List.of("Old: fine."));
        return null;
    }
}
