package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.tools.Tools;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.9) Random chaos: every "Shuffle every" game days (at once when it is turned on) the
 * giants are shuffled: each one standing gets a new size (his world's size times 0.4 to 1.8, kept to his own smallest
 * and biggest), each wild one a new mood (owned ones keep theirs: their owners decide), and each kind uses only some of
 * his moves that day (a third or so rest, at least two always left; a move the world turned off stays off). The resting
 * moves are put on what the giants are sent, never on the world's own values (the book and the change log are
 * untouched). Off: every move back at once. Everyone is told when the giants change (not in quiet mode).
 */
public final class Chaos {
    private Chaos() {}

    private static final Random RANDOM = new Random();
    public static int shuffles;

    static void forget() {}

    /** (the tests) the same shuffle each time */
    public static void seedForTests(long seed) {
        RANDOM.setSeed(seed);
    }

    /** every ten seconds */
    public static void tick(MinecraftServer server) {
        SystemsData d = SystemsData.get(server);
        if (!Systems.on(server, "chaos")) {
            if (!d.s.rests.isEmpty() || d.s.chaosDay >= 0) {
                d.s.rests.clear();
                d.s.chaosDay = -1;
                d.setDirty();
                AdminServer.push(server, AdminWorld.get(server));
            }
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        long day = server.overworld().getDayTime() / 24000L;
        int every = Math.max(1, (int) Systems.num(server, "chaosDays"));
        if (d.s.chaosDay >= 0 && day >= d.s.chaosDay && day - d.s.chaosDay < every) return;
        shuffle(server, day);
    }

    /** the shuffle now */
    public static void shuffle(MinecraftServer server, long day) {
        SystemsData d = SystemsData.get(server);
        d.s.chaosDay = day;
        d.s.rests.clear();
        for (Giant g : AdminServer.giants()) {
            AdminJson.Report rep = AdminServer.report(server, g);
            if (rep == null) continue;
            List<String> on = new ArrayList<>();
            for (AdminSetting s : rep.settings())
                if (s.id().startsWith("move.") && s.id().endsWith(".on") && s.type() == AdminSetting.Type.BOOL)
                    try {
                        if (s.value().getAsBoolean()) on.add(s.id().substring(5, s.id().length() - 3));
                    } catch (RuntimeException e) {
                        // left as it is
                    }
            List<String> rest = new ArrayList<>();
            int keep = Math.min(on.size(), 2);
            java.util.Collections.shuffle(on, RANDOM);
            for (int i = keep; i < on.size(); i++) if (RANDOM.nextInt(3) == 0) rest.add(on.get(i));
            if (!rest.isEmpty()) d.s.rests.put(g.key(), rest);
        }
        d.setDirty();
        AdminServer.push(server, AdminWorld.get(server));
        // sizes and moods
        for (Look.Kind k : Look.read(server)) {
            double base = worldSize(server, k.g());
            for (JsonObject r : k.rows()) {
                if (!Look.here(r)) continue;
                LivingEntity e = FunGiants.find(server, AdminJson.str(r, "id", 40));
                if (e == null) continue;
                double was = AdminJson.num(r, "size", FunGiants.sizeOf(e));
                double size = base * Math.exp(Math.log(0.4) + RANDOM.nextDouble() * (Math.log(1.8) - Math.log(0.4)));
                size = Math.max(k.min(), Math.min(k.max(), Math.round(size * 1000) / 1000.0));
                if (k.does().contains("size") && was > 0 && Math.abs(size - was) / was >= 0.02) {
                    Systems.live(server, k.g(), e.getStringUUID(), "size", AdminSetting.plain(size));
                    // (Growth follows his new size from here)
                    SystemsData.G rec = d.s.giants.get(e.getStringUUID());
                    if (rec != null && rec.start > 0) rec.start = rec.start * size / was;
                }
                if (AdminJson.str(r, "ownerId", 40).isEmpty() && !k.moods().isEmpty()) {
                    String mood = k.moods().get(RANDOM.nextInt(k.moods().size()));
                    if (Look.mood(server, k, e, mood)) {
                        SystemsData.G rec = d.s.giants.get(e.getStringUUID());
                        if (rec != null) rec.moodSet = mood;
                    }
                }
            }
        }
        shuffles++;
        if (!Tools.quiet(server))
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                p.sendSystemMessage(Component.literal("Chaos: the giants have changed. New sizes, new moods, and"
                        + " today each uses only some of his moves.").withStyle(ChatFormatting.LIGHT_PURPLE));
        SystemsAdmin.changed();
        Systems.log("chaos: shuffled on day {}: {}", day, d.s.rests);
    }

    /** his size as the world has it: the world's own size times the all-giants size */
    static double worldSize(MinecraftServer server, Giant g) {
        double base = 1, all = 1;
        AdminJson.Report rep = AdminServer.report(server, g);
        if (rep != null) {
            AdminSetting w = rep.setting("worldScale");
            if (w != null) try {
                base = w.value().getAsDouble();
            } catch (RuntimeException e) {
                base = 1;
            }
        }
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get("all.size");
        if (v != null) try {
            all = v.getAsDouble();
        } catch (RuntimeException e) {
            all = 1;
        }
        return base * all;
    }

    /** what one giant is sent: the world's values with today's resting moves off */
    public static Map<String, JsonElement> forGiant(MinecraftServer server, Giant g, Map<String, JsonElement> values) {
        if (server == null || !Systems.on(server, "chaos") || server.overworld() == null) return values;
        List<String> rest = SystemsData.get(server).s.rests.get(g.key());
        if (rest == null || rest.isEmpty()) return values;
        Map<String, JsonElement> out = new LinkedHashMap<>(values);
        for (String m : rest) out.put("move." + m + ".on", new JsonPrimitive(false));
        return out;
    }

    /** (the book) the day shuffled, the next, and each kind's resting moves */
    static JsonObject json(MinecraftServer server) {
        JsonObject o = new JsonObject();
        SystemsData d = SystemsData.get(server);
        if (!Systems.on(server, "chaos")) return o;
        o.addProperty("day", d.s.chaosDay);
        o.addProperty("next", d.s.chaosDay < 0 ? -1 : d.s.chaosDay + Math.max(1, (int) Systems.num(server,
                "chaosDays")));
        JsonObject r = new JsonObject();
        for (var e : d.s.rests.entrySet()) {
            JsonArray a = new JsonArray();
            for (String m : e.getValue()) a.add(m);
            r.add(e.getKey(), a);
        }
        o.add("rests", r);
        return o;
    }
}
