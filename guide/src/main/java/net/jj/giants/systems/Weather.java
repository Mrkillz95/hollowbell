package net.jj.giants.systems;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.net.WeatherPayload;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.5) Weather Gods, the server's half, every second: which giant's weather each player is
 * in (the strongest near them: inside seven tenths of his area all of it, fading out to its edge), told to their Guide
 * (fog, colour, what falls), and the rain and thunder as the game's own weather packets to that player alone, eased a
 * step a second, so the world's weather is untouched for everyone else. Storm giants throw lightning for show (no fire,
 * no harm) round them while someone is in their weather. Nothing is loaded; players leaving get the world's own weather
 * back.
 */
public final class Weather {
    private Weather() {}

    /** what one player is in now: the weather, how strong, and the rain and thunder they were last sent */
    public record In(String kind, float strength, float rain, float thunder, String giant) {}

    private static final Map<UUID, In> in = new HashMap<>();
    /** each storm giant's last bolt (server tick) */
    private static final Map<String, Integer> bolts = new HashMap<>();
    public static int boltsThrown;

    static void forget() {
        in.clear();
        bolts.clear();
        boltsThrown = 0;
    }

    /** how far his weather reaches: bigger giants, bigger areas */
    public static double radius(String kind, double size, double area) {
        return Math.min(320, (48 + FunGiants.height(kind, size)) * area);
    }

    public static void tick(MinecraftServer server) {
        if (!Systems.on(server, "weather")) {
            if (!in.isEmpty()) {
                for (UUID u : List.copyOf(in.keySet())) {
                    ServerPlayer p = server.getPlayerList().getPlayer(u);
                    if (p != null) restore(p);
                }
                in.clear();
            }
            return;
        }
        double area = Systems.num(server, "weatherArea"), thick = Systems.num(server, "weatherStrength");
        Map<String, List<Watch.Row>> rows = Watch.rows(server);
        int now = server.getTickCount();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String dim = p.level().dimension().location().toString();
            String best = null;
            Watch.Row at = null;
            double s = 0;
            for (Map.Entry<String, List<Watch.Row>> e : rows.entrySet())
                for (Watch.Row r : e.getValue()) {
                    if (!"here".equals(r.state()) || !dim.equals(r.dim())) continue;
                    double size = AdminJson.num(r.r(), "size", 1);
                    double rad = radius(e.getKey(), size, area);
                    double d = Math.hypot(p.getX() - r.x(), p.getZ() - r.z());
                    if (d >= rad) continue;
                    double f = d <= rad * 0.7 ? 1 : (rad - d) / (rad * 0.3);
                    if (f > s) {
                        s = f;
                        best = e.getKey();
                        at = r;
                    }
                }
            In was = in.get(p.getUUID());
            if (best == null) {
                if (was != null) leave(p, was);
                continue;
            }
            WeatherKinds k = WeatherKinds.of(best);
            float strength = (float) Math.round(s * thick * 100) / 100f;
            float rain = step(was == null ? worldRain(p) : was.rain(), k.rain() * strength);
            float thunder = step(was == null ? worldThunder(p) : was.thunder(), k.thunder() * strength);
            In now2 = new In(k.id(), strength, rain, thunder, at.id());
            in.put(p.getUUID(), now2);
            send(p, k.id(), strength);
            sendRain(p, rain, thunder, was == null);
            if (k.lightning() && strength > 0.5f) lightning(server, p, best, at, area, now);
        }
        in.keySet().removeIf(u -> server.getPlayerList().getPlayer(u) == null);
    }

    /**
     * (every tick) while the world's own weather is changing, the game tells every player its rain each tick: the ones
     * in a giant's weather get theirs again after it
     */
    public static void everyTick(MinecraftServer server) {
        if (in.isEmpty()) return;
        for (Map.Entry<UUID, In> e : in.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            ServerLevel l = p.serverLevel();
            if (l.getRainLevel(1f) != l.getRainLevel(0f) || l.getThunderLevel(1f) != l.getThunderLevel(0f))
                sendRain(p, e.getValue().rain(), e.getValue().thunder(), false);
        }
    }

    /** a step a second toward where it's going (a fifth of the way at most) */
    private static float step(float from, float to) {
        float d = to - from;
        if (Math.abs(d) <= 0.2f) return to;
        return from + Math.signum(d) * 0.2f;
    }

    private static float worldRain(ServerPlayer p) {
        return p.serverLevel().getRainLevel(1f);
    }

    private static float worldThunder(ServerPlayer p) {
        return p.serverLevel().getThunderLevel(1f);
    }

    /** out of every giant's weather: eased back to the world's own, then the world's own state */
    private static void leave(ServerPlayer p, In was) {
        float rain = step(was.rain(), worldRain(p)), thunder = step(was.thunder(), worldThunder(p));
        if (rain == worldRain(p) && thunder == worldThunder(p)) {
            in.remove(p.getUUID());
            restore(p);
            return;
        }
        in.put(p.getUUID(), new In("", 0, rain, thunder, ""));
        send(p, "", 0);
        sendRain(p, rain, thunder, false);
    }

    /** the world's own weather again (and no giant weather on their game) */
    static void restore(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        send(p, "", 0);
        p.connection.send(new ClientboundGameEventPacket(l.isRaining() ? ClientboundGameEventPacket.START_RAINING
                : ClientboundGameEventPacket.STOP_RAINING, 0));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE,
                l.getRainLevel(1f)));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE,
                l.getThunderLevel(1f)));
    }

    private static void sendRain(ServerPlayer p, float rain, float thunder, boolean first) {
        // (the game shows rain only while it thinks it is raining: say so once, it starts at none and we set it)
        if (first && !p.serverLevel().isRaining())
            p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.START_RAINING, 0));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
        p.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
    }

    private static void send(ServerPlayer p, String kind, float strength) {
        if (ServerPlayNetworking.canSend(p, WeatherPayload.TYPE))
            ServerPlayNetworking.send(p, new WeatherPayload(kind, strength));
    }

    /** a bolt for show somewhere in a storm giant's area now and then (one every ten seconds or so) */
    private static void lightning(MinecraftServer server, ServerPlayer p, String kind, Watch.Row r, double area,
            int now) {
        Integer last = bolts.get(r.id());
        if (last != null && now - last < 60 && now >= last) return;
        if (!(p.level() instanceof ServerLevel sl) || sl.dimensionType().hasCeiling()) return;
        if (sl.random.nextInt(2) != 0) return;
        bolts.put(r.id(), now);
        double rad = radius(kind, AdminJson.num(r.r(), "size", 1), area) * 0.6;
        double a = sl.random.nextDouble() * Math.PI * 2, d = sl.random.nextDouble() * rad;
        int x = (int) Math.floor(r.x() + Math.cos(a) * d), z = (int) Math.floor(r.z() + Math.sin(a) * d);
        if (!sl.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) return;
        int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt == null) return;
        bolt.moveTo(x + 0.5, y, z + 0.5);
        bolt.setVisualOnly(true);
        sl.addFreshEntity(bolt);
        boltsThrown++;
    }

    /** (the tests and the book) the weather this player is in now, or null */
    public static In of(ServerPlayer p) {
        return in.get(p.getUUID());
    }

    /** how many players are in a giant's weather now */
    public static int players() {
        int n = 0;
        for (In i : in.values()) if (!i.kind().isEmpty()) n++;
        return n;
    }

    public static void lookForTests(MinecraftServer server) {
        Watch.forget();
        tick(server);
    }
}
