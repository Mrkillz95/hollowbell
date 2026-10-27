package net.jj.fakefuture;

import java.util.List;
import net.minecraft.server.MinecraftServer;

/** A giant's bridge that speaks a newer performance protocol than this guide: it is left out, not misread. */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static List<String> giants(MinecraftServer server, String action, String arg) {
        if (action.equals("perf"))
            return List.of(
                    "{\"perf\":99,\"giant\":\"fakefuture\"}",
                    "{\"id\":\"fakefuture.thing\",\"kind\":\"toggle\",\"default\":true}");
        return null;
    }
}
