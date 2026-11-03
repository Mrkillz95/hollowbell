package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.tools.Watch;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) Weak points: the giants do the damage in their own hurt paths; the Guide only shows,
 * once a second while a giant fights, a glint at each of his weak spots so players can find them.
 */
public final class Weak {
    private Weak() {}

    static void tick(MinecraftServer server) {
        if (!Systems.on(server, "weakPoints") || server.getPlayerList().getPlayers().isEmpty()) return;
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        for (var e : rows.entrySet()) {
            Giant g = AdminServer.giant(e.getKey());
            if (g == null || !Look.sys(server, g).contains("spots")) continue;
            for (Watch.Row r : e.getValue()) {
                if (!"here".equals(r.state()) || !r.fighting()) continue;
                ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.tryParse(r.dim())));
                if (l == null) continue;
                glint(server, g, l, r.id());
            }
        }
    }

    /** a glint at each of his weak spots now */
    public static int glint(MinecraftServer server, Giant g, ServerLevel l, String id) {
        List<String> said = Systems.act(server, g, id, "spots", new JsonObject());
        if (said == null || said.size() < 2 || !said.get(0).equals("ok")) return 0;
        JsonObject o = AdminJson.object(said.get(1));
        if (o == null || !(o.get("spots") instanceof JsonArray a)) return 0;
        int n = 0;
        for (JsonElement x : a) {
            if (!x.isJsonObject()) continue;
            JsonObject s = x.getAsJsonObject();
            double sx = AdminJson.num(s, "x", 0), sy = AdminJson.num(s, "y", 0), sz = AdminJson.num(s, "z", 0);
            double r = Math.max(0.3, AdminJson.num(s, "r", 1));
            l.sendParticles(ParticleTypes.END_ROD, sx, sy, sz, 3, r * 0.4, r * 0.4, r * 0.4, 0.01);
            l.sendParticles(ParticleTypes.CRIT, sx, sy, sz, 6, r * 0.5, r * 0.5, r * 0.5, 0.05);
            n++;
        }
        return n;
    }
}
