package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.8) The admin book's Systems page, the server's half: what the systems are doing right
 * now (the season, the moon, the scaling, blocks waiting to grow back, remains rotting) and each giant they follow.
 * Sent with the other pages when anything changes, and every two seconds to whoever is looking at it. Admins only.
 */
public final class SystemsAdmin {
    private SystemsAdmin() {}

    public static final String KIND = "systems";

    private static final Set<UUID> watching = new LinkedHashSet<>();
    private static boolean dirty;

    static void forget() {
        watching.clear();
        dirty = false;
    }

    /** something on the page changed (sent with the next look, not at once: it changes often) */
    public static void changed() {
        dirty = true;
    }

    public static void watch(MinecraftServer server, ServerPlayer p, boolean on) {
        if (!on) {
            watching.remove(p.getUUID());
            return;
        }
        if (AdminBook.role(p) != AdminBook.Role.ADMIN) return;
        watching.add(p.getUUID());
        AdminBook.sender.accept(p, page(server));
    }

    static void tick(MinecraftServer server) {
        if (watching.isEmpty()) return;
        if (server.getTickCount() % 40 != 0 && !dirty) return;
        dirty = false;
        AdminBookStatePayload pg = null;
        for (UUID u : List.copyOf(watching)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p == null || AdminBook.role(p) != AdminBook.Role.ADMIN) {
                watching.remove(u);
                continue;
            }
            if (pg == null) pg = page(server);
            AdminBook.sender.accept(p, pg);
        }
    }

    public static AdminBookStatePayload page(MinecraftServer server) {
        return new AdminBookStatePayload(KIND, json(server).toString());
    }

    public static JsonObject json(MinecraftServer server) {
        JsonObject o = new JsonObject();
        Scaling.Factors f = Scaling.factors(server);
        JsonObject now = new JsonObject();
        now.addProperty("season", f.season() < 0 ? "" : Scaling.SEASONS.get(f.season()));
        int len = Math.max(1, (int) Systems.num(server, "seasonDays"));
        long day = server.overworld().getDayTime() / 24000L;
        now.addProperty("seasonDay", (int) (day % len) + 1);
        now.addProperty("seasonDays", len);
        now.addProperty("moonPhase", server.overworld().getMoonPhase());
        now.addProperty("fullMoon", f.fullMoon());
        now.addProperty("scale", f.scale());
        now.addProperty("players", server.getPlayerList().getPlayerCount());
        now.addProperty("days", server.overworld().getGameTime() / 24000L);
        now.addProperty("armour", Math.round(f.armour() * 100) / 100.0);
        now.addProperty("health", f.health());
        now.addProperty("damage", f.damage());
        now.addProperty("speed", f.speed());
        now.addProperty("healing", f.healing());
        now.addProperty("grief", Grief.waiting());
        now.addProperty("rotting", Remains.rotting(server));
        // (Part 9) new systems B
        now.addProperty("weatherPlayers", Weather.players());
        now.addProperty("followers", Followers.standing);
        now.add("underworld", Underworld.json(server));
        now.add("mirror", Mirror.json(server));
        now.add("chaos", Chaos.json(server));
        if (Systems.on(server, "sacred")) {
            JsonArray sacred = new JsonArray();
            for (String k : Sacred.living()) sacred.add(k);
            now.add("sacred", sacred);
        }
        o.add("now", now);
        JsonArray gs = new JsonArray();
        SystemsData d = SystemsData.get(server);
        int n = 0;
        for (var e : d.s.giants.entrySet()) {
            if (n++ >= 48) break;
            SystemsData.G g = e.getValue();
            JsonObject j = new JsonObject();
            j.addProperty("id", e.getKey());
            j.addProperty("kind", g.kind);
            Giant k = AdminServer.giant(g.kind);
            j.addProperty("name", k == null ? g.kind : AdminServer.nameOf(k));
            LivingEntity le = FunGiants.find(server, e.getKey());
            double size = le == null ? 0 : FunGiants.sizeOf(le);
            j.addProperty("size", Math.round(size * 100) / 100.0);
            j.addProperty("start", Math.round(g.start * 100) / 100.0);
            j.addProperty("here", le != null);
            j.addProperty("days", Math.round((server.overworld().getGameTime() - g.born) / 2400.0) / 10.0);
            j.addProperty("wins", g.wins);
            j.addProperty("food", Math.round(g.food));
            j.addProperty("loyalty", Math.round(g.loyalty));
            if (!g.owner.isEmpty()) {
                ServerPlayer p = Look.player(server, g.owner);
                j.addProperty("owner", p == null ? "" : p.getGameProfile().getName());
            }
            j.addProperty("titan", g.titan);
            j.addProperty("sulking", g.sulking);
            j.addProperty("turned", g.turned);
            j.addProperty("belly", g.belly.size());
            gs.add(j);
        }
        o.add("giants", gs);
        JsonObject weak = new JsonObject();
        for (Giant g : AdminServer.giants()) {
            JsonObject h = Systems.header(server, g);
            if (h == null) continue;
            JsonArray a = h.getAsJsonArray("weak");
            if (a != null) weak.add(g.key(), a);
        }
        o.add("weak", weak);
        return o;
    }
}
