package net.jj.giants.perf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.net.PerfAskPayload;
import net.jj.giants.net.PerfOpenPayload;
import net.jj.giants.net.PerfSetPayload;
import net.jj.giants.net.PerfStatePayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The world's half of Giants Performance: the Features, which only an operator may change. A player's screen sends
 * a request; this checks who sent it, keeps the values with the world (PerfWorld), hands them to every giant that
 * speaks the protocol, and tells every player what they are now. Works the same on a dedicated server.
 */
public final class PerfServer {
    private PerfServer() {}

    private static List<Giant> giants = List.of();
    private static List<PerfJson.Report> reports = List.of();
    private static List<PerfSetting> settings = List.of();
    private static final Map<UUID, Long> lastAsked = new HashMap<>();

    public static void register() {
        PayloadTypeRegistry.playC2S().register(PerfAskPayload.TYPE, PerfAskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PerfSetPayload.TYPE, PerfSetPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfStatePayload.TYPE, PerfStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PerfOpenPayload.TYPE, PerfOpenPayload.CODEC);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> command(d));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> refresh(s, Giants.installed()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            giants = List.of();
            reports = List.of();
            settings = List.of();
            lastAsked.clear();
        });
        ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> send(h.getPlayer(), ""));
        ServerPlayNetworking.registerGlobalReceiver(PerfAskPayload.TYPE, (p, ctx) -> ctx.server().execute(() -> {
            ServerPlayer player = ctx.player();
            long now = ctx.server().getTickCount();
            Long last = lastAsked.get(player.getUUID());
            if (last != null && now - last >= 0 && now - last < 10) return;
            lastAsked.put(player.getUUID(), now);
            send(player, "");
        }));
        ServerPlayNetworking.registerGlobalReceiver(PerfSetPayload.TYPE, (p, ctx) -> ctx.server().execute(() -> {
            ServerPlayer player = ctx.player();
            String said = request(ctx.server(), player, PerfJson.values(p.values()));
            send(player, said);
        }));
    }

    /**
     * /giants performance and /giants effects, for every player. They join the giants' own /giants (Brigadier puts
     * the two under the one /giants, whichever comes first), and only open the screen on the player's own game.
     */
    public static void command(CommandDispatcher<CommandSourceStack> d) {
        for (String word : WORDS)
            d.register(Commands.literal("giants").then(Commands.literal(word).executes(c -> open(c.getSource()))));
    }

    /** the words after /giants that open the screen */
    public static final String[] WORDS = {"performance", "effects"};

    private static int open(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        if (!ServerPlayNetworking.canSend(p, PerfOpenPayload.TYPE)) {
            src.sendFailure(Component.literal(
                    "The Giants Performance screen is in the Giants Guide, and your game doesn't have it."));
            return 0;
        }
        ServerPlayNetworking.send(p, new PerfOpenPayload());
        send(p, "");
        return 1;
    }

    /** asks every giant here again what it has, and hands each the world's values (on start, and in the tests) */
    public static void refresh(MinecraftServer server, List<Giant> here) {
        giants = List.copyOf(here);
        reports = PerfBridges.askAll(server, giants);
        settings = worldSettings(PerfBridges.settings(reports, giants));
        // forget what no longer means anything (a giant taken out, a setting gone)
        PerfWorld w = PerfWorld.get(server);
        int before = w.values().size();
        w.values().keySet().removeIf(id -> settings.stream().noneMatch(s -> s.id().equals(id)));
        if (w.values().size() != before) w.setDirty();
        int n = PerfBridges.push(server, reports, giants, true, settings, w.values());
        GiantsGuideMod.LOG.info(
                "Giants Performance: {} of {} giants speak it, {} world settings, {} set away from default",
                n,
                giants.size(),
                settings.size(),
                w.values().size());
    }

    /** the world's settings only (the Features, and any a giant keeps for the whole world) */
    public static List<PerfSetting> worldSettings(List<PerfSetting> all) {
        List<PerfSetting> out = new ArrayList<>();
        for (PerfSetting s : all) if (s.server()) out.add(s);
        return out;
    }

    public static List<PerfSetting> settings() {
        return settings;
    }

    public static List<PerfJson.Report> reports() {
        return reports;
    }

    /** an operator, or the one whose single-player world it is */
    public static boolean allowed(ServerPlayer p) {
        return may(p.hasPermissions(2), p.server.isSingleplayerOwner(p.getGameProfile()));
    }

    /**
     * The rule itself: an operator (permission level 2, cheats on in single player), or the owner of their own
     * single-player world even with cheats off. Nobody else.
     */
    public static boolean may(boolean operator, boolean singlePlayerOwner) {
        return operator || singlePlayerOwner;
    }

    /**
     * A player asks for the world's settings to change. Nothing changes unless they may; only the world's settings
     * are taken, each pulled into its range. Returns what to tell them.
     */
    public static String request(MinecraftServer server, ServerPlayer p, Map<String, Double> asked) {
        if (!allowed(p)) {
            GiantsGuideMod.LOG.info(
                    "Giants Performance: {} asked to change the world's features, but isn't an operator",
                    p.getGameProfile().getName());
            return "Only an operator can change the world's features. Ask an operator.";
        }
        PerfWorld w = PerfWorld.get(server);
        int n = 0;
        for (PerfSetting s : settings) {
            Double v = asked.get(s.id());
            if (v == null) continue;
            w.put(s, v);
            n++;
        }
        if (n == 0) return "Nothing to change.";
        PerfBridges.push(server, reports, giants, true, settings, w.values());
        GiantsGuideMod.LOG.info(
                "Giants Performance: {} changed {} world feature(s), now {}",
                p.getGameProfile().getName(),
                n,
                w.values());
        // every other player's screen shows the new values
        for (ServerPlayer o : server.getPlayerList().getPlayers()) if (o != p) send(o, "");
        return "World features saved.";
    }

    /** tells one player the world's settings, if their game has the guide */
    public static void send(ServerPlayer p, String message) {
        if (!ServerPlayNetworking.canSend(p, PerfStatePayload.TYPE)) return;
        PerfWorld w = PerfWorld.get(p.server);
        ServerPlayNetworking.send(
                p,
                new PerfStatePayload(
                        allowed(p),
                        PerfJson.writeAll(settings),
                        PerfJson.values(w.values(), settings).toString(),
                        message));
    }
}
