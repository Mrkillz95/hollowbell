package net.jj.giants.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Live news for the guide's "Where" page. The guide never touches a giant's own code: it asks his mod's
 * GiantsBridge (the same thing /giants uses) by name, and anything that goes wrong just means no news.
 */
public final class LiveNews {
    private static final Map<UUID, Long> lastAsked = new HashMap<>();

    private LiveNews() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(AskPayload.TYPE, AskPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ToldPayload.TYPE, ToldPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(AskPayload.TYPE, (p, ctx) -> ctx.server().execute(() -> {
            var player = ctx.player();
            long now = ctx.server().getTickCount();
            Long last = lastAsked.get(player.getUUID());
            if (last != null && now - last >= 0 && now - last < 10) return; // at most twice a second each
            lastAsked.put(player.getUUID(), now);
            Giant g = Giants.byKey(p.giant());
            if (g == null) return;
            if (ServerPlayNetworking.canSend(player, ToldPayload.TYPE))
                ServerPlayNetworking.send(player, new ToldPayload(g.key(), ask(ctx.server(), g)));
        }));
    }

    /** where he is and how he's set, in his own mod's words; empty when his mod isn't on this server or says nothing */
    public static List<String> ask(MinecraftServer server, Giant g) {
        List<String> out = new ArrayList<>();
        if (!Giants.loaded(g)) return out;
        for (String action : new String[]{"where", "status"}) {
            List<String> got = bridge(server, g, action);
            if (got != null) for (String s : got) if (s != null && !s.isBlank() && out.size() < 24) out.add(s.length() > 500 ? s.substring(0, 500) : s);
        }
        return out;
    }

    /** one call to his bridge; null if it isn't there, is older, or fails */
    public static List<String> bridge(MinecraftServer server, Giant g, String action) {
        try {
            Class<?> c = Class.forName(g.bridge(), true, LiveNews.class.getClassLoader());
            Method m = c.getMethod("giants", MinecraftServer.class, String.class, String.class);
            Object r = m.invoke(null, server, action, "");
            if (!(r instanceof List<?> l)) return null;
            List<String> out = new ArrayList<>();
            for (Object o : l) if (o != null) out.add(o.toString());
            return out;
        } catch (Throwable t) {
            GiantsGuideMod.LOG.debug("Giants Guide: no {} news from {}: {}", action, g.key(), t.toString());
            return null;
        }
    }
}
