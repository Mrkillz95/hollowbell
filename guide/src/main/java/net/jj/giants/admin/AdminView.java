package net.jj.giants.admin;

import net.jj.giants.Giant;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * Where a page, a preset preview or a plan reads its values from: a running world (its saved values and each giant's
 * own report), or the Create World screen's draft (Part 3, {@link AdminDraft}: no world yet, the values are only the
 * creator's choices so far). The same page and preview code serves both.
 */
public interface AdminView {
    /** the values this world (or draft) has set */
    AdminWorld world();

    /** a giant's settings with this world's (or draft's) values in; null if he doesn't speak the protocol */
    @Nullable AdminJson.Report report(Giant g);

    /** a running world */
    static AdminView of(MinecraftServer server) {
        return new AdminView() {
            @Override
            public AdminWorld world() {
                return AdminWorld.get(server);
            }

            @Override
            public @Nullable AdminJson.Report report(Giant g) {
                return AdminServer.report(server, g);
            }
        };
    }

    /** the same, from the overworld being loaded (before the server lists it: the world-creation step) */
    static AdminView of(MinecraftServer server, net.minecraft.server.level.ServerLevel overworld) {
        return new AdminView() {
            @Override
            public AdminWorld world() {
                return AdminWorld.get(overworld);
            }

            @Override
            public @Nullable AdminJson.Report report(Giant g) {
                return AdminServer.report(server, g);
            }
        };
    }
}
