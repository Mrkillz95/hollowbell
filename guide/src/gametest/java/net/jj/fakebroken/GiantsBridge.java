package net.jj.fakebroken;

import java.util.List;
import net.minecraft.server.MinecraftServer;

/** A giant's bridge that breaks: it throws, or says nonsense. Only it is left out. */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static List<String> giants(MinecraftServer server, String action, String arg) {
        if (action.equals("perf")) return List.of("this is not json", "{\"perf\":1}");
        throw new IllegalStateException("broken on purpose");
    }
}
