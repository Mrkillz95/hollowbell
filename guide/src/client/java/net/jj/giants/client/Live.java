package net.jj.giants.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jj.giants.net.AskPayload;
import net.jj.giants.net.ToldPayload;
import net.minecraft.Util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the server last said about each giant (his place in the world and his settings), for the Where page. */
public final class Live {
    public enum State { ASKING, GOT, NO_SERVER }
    public record News(State state, List<String> lines, long at) {}

    private static final Map<String, News> NEWS = new HashMap<>();

    private Live() {}

    /** ask again if the last answer is more than a few seconds old */
    public static void ask(String giant) {
        News n = NEWS.get(giant);
        long now = Util.getMillis();
        if (n != null && now - n.at() < (n.state() == State.ASKING ? 3000 : 5000)) return;
        if (!ClientPlayNetworking.canSend(AskPayload.TYPE)) { NEWS.put(giant, new News(State.NO_SERVER, List.of(), now)); return; }
        ClientPlayNetworking.send(new AskPayload(giant));
        NEWS.put(giant, new News(State.ASKING, n != null ? n.lines() : List.of(), now));
    }

    public static News news(String giant) { return NEWS.get(giant); }

    static void told(ToldPayload p) { NEWS.put(p.giant(), new News(State.GOT, List.copyOf(p.lines()), Util.getMillis())); }

    static void forget() { NEWS.clear(); }
}
