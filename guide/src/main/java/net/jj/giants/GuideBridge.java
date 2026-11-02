package net.jj.giants;

import java.util.List;
import net.jj.giants.admin.AdminServer;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/**
 * What the giants ask of the Giants Guide: the other way round from each giant's GiantsBridge (ADMIN_BRIDGE.md,
 * "Command scope"). A giant calls {@code guide(server, action, arg)} by name, so neither needs the other installed;
 * an action the guide doesn't know answers null.
 *
 * <p>{@code adminworld}: one of the giant's settings changed for this world by one of his own commands or a switch in
 * his book. arg {@code {"admin":1,"giant":"<his mod id or key>","id":"<setting>","value":<value>,"by":"<player
 * UUID>|-"}}. It goes through exactly what /giants admin set does (checked, kept with the world, logged, told to the
 * other operators, sent to the giants). Answers {@code ["ok", <what /giants admin set says>...]} or {@code ["no", <why
 * not>]}; null for an argument it can't read or a newer protocol.
 */
public final class GuideBridge {
    private GuideBridge() {}

    public static final int API = 1;

    public static @Nullable List<String> guide(@Nullable MinecraftServer server, String action, String arg) {
        if (server == null || action == null || arg == null) return null;
        return switch (action) {
            case "adminworld" -> AdminServer.fromGiant(server, arg);
            default -> null;
        };
    }
}
