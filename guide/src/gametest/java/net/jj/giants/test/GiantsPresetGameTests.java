package net.jj.giants.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.util.RandomSource;

/**
 * Part 4 with the eight giants' own mods (-Pgiants; without them each test passes at once): every re-tune preset
 * applies to every real giant with nothing left out, and a real giant of each kind does what the preset says: Pocket
 * Giants come about two blocks tall, Titans have two and a half times the health and come twice the size, Gentle
 * Giants leave an idle player be, Night Terrors lie down by day.
 */
public class GiantsPresetGameTests implements FabricGameTest {

    private static List<Giant> here() {
        AdminServer.useGiants(null);
        return AdminServer.giants();
    }

    private static ServerPlayer op(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        h.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4, false));
        return p;
    }

    /**
     * the i-th giant's own spot and idle player, 40 blocks apart in a row (the land held loaded), so the giants keep
     * out of each other's way
     */
    private static ServerPlayer opAt(GameTestHelper h, int i) {
        ServerPlayer p = op(h);
        var l = h.getLevel();
        net.minecraft.core.BlockPos o = h.absolutePos(net.minecraft.core.BlockPos.ZERO).offset(i * 16, 0, 0);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                l.setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, true);
                l.getChunk((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz);
            }
        int y = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, o.getX(), o.getZ());
        p.moveTo(o.getX() + 0.5, y, o.getZ() + 0.5, 0f, 0f);
        return p;
    }

    private static void unforce(GameTestHelper h, int n) {
        var l = h.getLevel();
        for (int i = 0; i < n; i++) {
            net.minecraft.core.BlockPos o = h.absolutePos(net.minecraft.core.BlockPos.ZERO).offset(i * 16, 0, 0);
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++)
                    l.setChunkForced((o.getX() >> 4) + dx, (o.getZ() >> 4) + dz, false);
        }
    }

    private static void apply(MinecraftServer s, String preset) {
        AdminTransfer.apply(s, "Server", AdminTransfer.plan(s, AdminTransfer.preset(preset)));
    }

    /** a giant mod's own class in his main package, by name */
    private static Object call(Giant g, String cls, String method, Class<?>[] types, Object... args) throws Exception {
        String pkg = g.bridge().substring(0, g.bridge().lastIndexOf('.'));
        Method m = Class.forName(pkg + "." + cls).getMethod(method, types);
        return m.invoke(null, args);
    }

    private static JsonObject kind(MinecraftServer s, String key) {
        for (JsonElement g : AdminLive.state(s).getAsJsonArray("giants"))
            if (g.getAsJsonObject().get("key").getAsString().equals(key)) return g.getAsJsonObject();
        return null;
    }

    /** the standing one of this kind nearest the player (the one just summoned there) */
    /** the standing one of this kind nearest a spot (one summoned there, found once he's in the list) */
    private static JsonObject nearestAt(MinecraftServer s, String key, net.minecraft.world.phys.Vec3 at) {
        JsonObject k = kind(s, key), best = null;
        double bd = 48;
        if (k == null || !k.has("rows") || at == null) return null;
        for (JsonElement r : k.getAsJsonArray("rows")) {
            JsonObject o = r.getAsJsonObject();
            if (!o.get("state").getAsString().equals("here") || !o.has("x")) continue;
            double d = Math.hypot(o.get("x").getAsDouble() - at.x, o.get("z").getAsDouble() - at.z);
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return best;
    }

    private static JsonObject nearest(MinecraftServer s, String key, ServerPlayer p) {
        JsonObject k = kind(s, key), best = null;
        double bd = Double.MAX_VALUE;
        if (k == null || !k.has("rows")) return null;
        for (JsonElement r : k.getAsJsonArray("rows")) {
            JsonObject o = r.getAsJsonObject();
            if (!o.get("state").getAsString().equals("here") || !o.has("x")) continue;
            double d = Math.hypot(o.get("x").getAsDouble() - p.getX(), o.get("z").getAsDouble() - p.getZ());
            if (d < bd) {
                bd = d;
                best = o;
            }
        }
        return bd < 64 ? best : null;
    }

    private static JsonObject row(MinecraftServer s, String key, String id) {
        JsonObject k = kind(s, key);
        if (k == null || !k.has("rows")) return null;
        for (JsonElement r : k.getAsJsonArray("rows"))
            if (r.getAsJsonObject().get("id").getAsString().equals(id)) return r.getAsJsonObject();
        return null;
    }

    private static String summon(MinecraftServer s, ServerPlayer p, Giant g, String size, String mood) {
        AdminServer.Result r = AdminLive.summon(s, p, g.key(), size, mood);
        // (what the command answered aside: the one standing beside the player is the one)
        JsonObject o = nearest(s, g.key(), p);
        return o == null ? null : o.get("id").getAsString();
    }

    private static void removeAll(MinecraftServer s, ServerPlayer p, Map<String, String> ids) {
        for (var e : ids.entrySet()) if (e.getValue() != null) AdminLive.act(s, p, e.getKey(), e.getValue(), "remove", "");
    }

    private static void done(GameTestHelper h, ServerPlayer p) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.reset(s, "Server", "everything", null);
        s.getPlayerList().getOps().remove(p.getGameProfile());
        s.getPlayerList().remove(p);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_real_apply", timeoutTicks = 100)
    public void everyPresetTakesWithEveryRealGiant(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> here = here();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        StringBuilder bad = new StringBuilder();
        for (String id : AdminTransfer.presets()) {
            AdminServer.reset(s, "Server", "everything", null);
            AdminTransfer.Plan p = AdminTransfer.plan(s, AdminTransfer.preset(id));
            for (String sk : p.skipped())
                // (a giant not installed is no fault of the preset's)
                if (!sk.endsWith("(not here)")) bad.append(id).append(": left out ").append(sk).append("; ");
            AdminTransfer.apply(s, "Server", p);
            AdminTransfer.Bundle b = AdminTransfer.preset(id);
            for (Giant g : here) {
                AdminJson.Report r = AdminServer.report(s, g);
                for (var v : b.values().getOrDefault(g.key(), Map.of()).entrySet()) {
                    var st = r.setting(v.getKey());
                    if (st == null || !net.jj.giants.admin.AdminSetting.same(st.value(), v.getValue()))
                        bad.append(id).append(": ").append(g.key()).append(" ").append(v.getKey()).append(" is ")
                                .append(st == null ? "missing" : st.value()).append("; ");
                }
            }
        }
        AdminServer.reset(s, "Server", "everything", null);
        h.assertTrue(bad.length() == 0, bad.toString());
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_real_pocket_titans", timeoutTicks = 300)
    public void pocketGiantsAreSmallAndTitansAreBig(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> here = here();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        ServerPlayer p = op(h);
        StringBuilder bad = new StringBuilder();
        Map<String, String> ids = new HashMap<>();
        Map<String, Double> max0 = new HashMap<>();
        try {
            // Pocket Giants: the world's own one comes about two blocks tall (his size as the world draws it), and one
            // that small stands in the world
            apply(s, "pocket_giants");
            for (Giant g : here) {
                AdminJson.Report r = AdminServer.report(s, g);
                double ws = r.setting("worldScale").value().getAsDouble();
                float drawn = (Float) call(g, "GiantsBehave", "spawnScale", new Class<?>[] {RandomSource.class},
                        RandomSource.create(9));
                float sized = (Float) call(g, "GiantsAdmin", "sized", new Class<?>[] {float.class}, drawn);
                if (sized < ws * 0.84 || sized > ws * 1.26 || ws > 0.05)
                    bad.append(g.key()).append(" pocket size ").append(sized).append(" (worldScale ").append(ws)
                            .append("); ");
                String id = summon(s, p, g, String.valueOf(ws), null);
                JsonObject o = id == null ? null : row(s, g.key(), id);
                if (o == null || Math.abs(o.get("size").getAsDouble() - ws) > 0.006)
                    bad.append(g.key()).append(" no pocket-sized one stands: ").append(o).append("; ");
                if (id != null) AdminLive.act(s, p, g.key(), id, "remove", "");
            }
            // Titans: health x2.5 on one standing (worked out again at once), twice the size for new ones
            AdminServer.reset(s, "Server", "everything", null);
            for (Giant g : here) {
                String id = summon(s, p, g, "0.1", null);
                ids.put(g.key(), id);
                JsonObject o = id == null ? null : row(s, g.key(), id);
                if (o == null || !o.has("max")) bad.append(g.key()).append(" no titan stand-in; ");
                else max0.put(g.key(), o.get("max").getAsDouble());
            }
            apply(s, "titans");
            for (Giant g : here) {
                String id = ids.get(g.key());
                JsonObject o = id == null ? null : row(s, g.key(), id);
                Double m0 = max0.get(g.key());
                if (o != null && m0 != null && o.has("max")) {
                    double ratio = o.get("max").getAsDouble() / m0;
                    if (Math.abs(ratio - 2.5) > 0.15)
                        bad.append(g.key()).append(" titan health x").append(ratio).append("; ");
                }
                float big = (Float) call(g, "GiantsAdmin", "sized", new Class<?>[] {float.class}, 1f);
                if (Math.abs(big - 2f) > 1e-4) bad.append(g.key()).append(" titan size ").append(big).append("; ");
                Object hunt = call(g, "GiantsBehave", "hunterTemper", new Class<?>[0]);
                if (!Boolean.TRUE.equals(hunt)) bad.append(g.key()).append(" titan isn't a hunter; ");
                AdminJson.Report r = AdminServer.report(s, g);
                if (r.all().getOrDefault("all.damage", 1.0) != 3.0)
                    bad.append(g.key()).append(" titan damage ").append(r.all()).append("; ");
            }
        } catch (Exception e) {
            bad.append(e);
        } finally {
            removeAll(s, p, ids);
            done(h, p);
        }
        h.assertTrue(bad.length() == 0, bad.toString());
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_real_gentle", timeoutTicks = 400)
    public void gentleGiantsLeaveAnIdlePlayerBe(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> here = here();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        ServerPlayer p = op(h);
        apply(s, "gentle_giants");
        Map<String, String> ids = new HashMap<>();
        Map<String, net.minecraft.world.phys.Vec3> spots = new HashMap<>();
        List<ServerPlayer> idle = new ArrayList<>();
        int i = 0;
        for (Giant g : here) {
            AdminServer.set(s, "Server", g.key(), "meetings", "off");
            AdminServer.set(s, "Server", g.key(), "fightGiants", "off");
            JsonObject k = kind(s, g.key());
            String hunting = k != null && k.has("moods") && k.getAsJsonArray("moods").size() > 1
                    ? k.getAsJsonArray("moods").get(1).getAsString() : null;
            ServerPlayer q = opAt(h, i++);
            idle.add(q);
            // (a hunting one, summoned right beside an idle player)
            spots.put(g.key(), q.position());
            ids.put(g.key(), summon(s, q, g, "0.1", hunting));
        }
        h.runAfterDelay(200, () -> {
            StringBuilder bad = new StringBuilder();
            for (Giant g : here) {
                String id = ids.get(g.key());
                JsonObject o = id == null ? null : row(s, g.key(), id);
                if (o == null) o = nearestAt(s, g.key(), spots.get(g.key()));
                if (o != null) ids.put(g.key(), o.get("id").getAsString());
                if (o == null) bad.append(g.key()).append(" none summoned (").append(kind(s, g.key())).append("); ");
                else if (o.has("fighting") && o.get("fighting").getAsBoolean())
                    bad.append(g.key()).append(" went for the idle player; ");
                try {
                    if (!Boolean.TRUE.equals(call(g, "GiantsBehave", "peaceful", new Class<?>[0])))
                        bad.append(g.key()).append(" isn't peaceful; ");
                } catch (Exception e) {
                    bad.append(g.key()).append(" ").append(e).append("; ");
                }
            }
            removeAll(s, p, ids);
            for (ServerPlayer q : idle) {
                s.getPlayerList().getOps().remove(q.getGameProfile());
                s.getPlayerList().remove(q);
            }
            unforce(h, here.size());
            done(h, p);
            h.assertTrue(bad.length() == 0, bad.toString());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p4_real_night", timeoutTicks = 1400)
    public void nightTerrorsLieDownByDay(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> here = here();
        if (here.isEmpty()) {
            h.succeed();
            return;
        }
        long was = h.getLevel().getDayTime();
        h.getLevel().setDayTime(1000);
        ServerPlayer p = op(h);
        apply(s, "night_terrors");
        Map<String, String> ids = new HashMap<>();
        Map<String, net.minecraft.world.phys.Vec3> spots = new HashMap<>();
        List<ServerPlayer> by = new ArrayList<>();
        int i = 0;
        for (Giant g : here) {
            AdminServer.set(s, "Server", g.key(), "meetings", "off");
            AdminServer.set(s, "Server", g.key(), "fightGiants", "off");
            AdminServer.set(s, "Server", g.key(), "temper", "peaceful");
            ServerPlayer q = opAt(h, i++);
            by.add(q);
            spots.put(g.key(), q.position());
            ids.put(g.key(), summon(s, q, g, "0.1", null));
            // (the summoner steps away, so nobody is near enough to keep him up)
            q.moveTo(q.getX(), q.getY() + 60, q.getZ(), 0f, 0f);
        }
        // (the mock player is creative-like enough to be left be; quiet a while, then ten seconds by day)
        h.runAfterDelay(1200, () -> {
            StringBuilder bad = new StringBuilder();
            for (Giant g : here) {
                String id = ids.get(g.key());
                JsonObject o = id == null ? null : row(s, g.key(), id);
                if (o == null) o = nearestAt(s, g.key(), spots.get(g.key()));
                if (o != null) ids.put(g.key(), o.get("id").getAsString());
                if (o == null) bad.append(g.key()).append(" none summoned (").append(kind(s, g.key())).append("); ");
                else if (!o.has("asleep") || !o.get("asleep").getAsBoolean())
                    bad.append(g.key()).append(" awake by day: ").append(o).append("; ");
            }
            removeAll(s, p, ids);
            for (ServerPlayer q : by) {
                s.getPlayerList().getOps().remove(q.getGameProfile());
                s.getPlayerList().remove(q);
            }
            unforce(h, here.size());
            h.getLevel().setDayTime(was);
            done(h, p);
            h.assertTrue(bad.length() == 0, bad.toString());
            h.succeed();
        });
    }
}
