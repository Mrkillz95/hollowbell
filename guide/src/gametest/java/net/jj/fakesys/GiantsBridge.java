package net.jj.fakesys;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * (Part 8) a stand-in giant for the new systems' tests: iron golems tagged "fakesys" are him. His size, mood, owner,
 * whether he fights and whether he is the world's own one are scoreboard tags ("fakesys.size=1.5"...). He answers
 * "admin"/"adminset" (keeping the all-giants values he was sent), "live"/"liveact" (size, sleep), "sys"/"sysact"
 * (spots, mood, turn, go) and "fun"/"funact" (stats), writing down every call.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static final String TAG = "fakesys";
    public static final List<String> calls = new ArrayList<>();
    /** the all-giants values last sent ("adminset") */
    public static volatile JsonObject all = new JsonObject();

    public static void reset() {
        calls.clear();
        all = new JsonObject();
    }

    public static List<IronGolem> golems(MinecraftServer s) {
        List<IronGolem> out = new ArrayList<>();
        for (ServerLevel l : s.getAllLevels())
            for (Entity e : l.getAllEntities())
                if (e instanceof IronGolem g && g.getTags().contains(TAG) && !g.isRemoved()) out.add(g);
        return out;
    }

    public static IronGolem spawn(ServerLevel l, double x, double y, double z) {
        IronGolem g = EntityType.IRON_GOLEM.create(l);
        g.moveTo(x, y, z, 0, 0);
        g.setNoAi(true);
        g.addTag(TAG);
        l.addFreshEntity(g);
        return g;
    }

    public static String get(Entity e, String what, String or) {
        for (String t : e.getTags()) if (t.startsWith(TAG + "." + what + "=")) return t.substring(TAG.length() + what.length() + 2);
        return or;
    }

    public static void put(Entity e, String what, String v) {
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(TAG + "." + what + "=")) e.removeTag(t);
        if (v != null) e.addTag(TAG + "." + what + "=" + v);
    }

    public static boolean called(String start) {
        for (String c : List.copyOf(calls)) if (c.startsWith(start)) return true;
        return false;
    }

    private static IronGolem byId(MinecraftServer s, String id) {
        for (IronGolem g : golems(s)) if (g.getStringUUID().equals(id)) return g;
        return null;
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> out.add("{\"admin\":1,\"giant\":\"fakesys\",\"name\":\"Fakesys\",\"uses\":[\"all.health\","
                    + "\"all.damage\",\"all.speed\",\"all.healing\",\"all.weakPoints\",\"all.weakHit\",\"all.bodyHit\"],"
                    + "\"all\":{}}");
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                all = o.has("all") ? o.getAsJsonObject("all") : new JsonObject();
                out.add("ok");
            }
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakesys\",\"name\":\"Fakesys\",\"moods\":[\"calm\",\"hunting\","
                        + "\"guardian\"],\"size\":[0.1,4],\"does\":[\"size\",\"sleep\"]}");
                for (IronGolem g : golems(s)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", g.getStringUUID());
                    j.addProperty("state", "here");
                    j.addProperty("dim", g.level().dimension().location().toString());
                    j.addProperty("x", g.getX());
                    j.addProperty("y", g.getY());
                    j.addProperty("z", g.getZ());
                    j.addProperty("size", Double.parseDouble(get(g, "size", "1")));
                    j.addProperty("mood", get(g, "mood", "calm"));
                    j.addProperty("asleep", false);
                    j.addProperty("fighting", g.getTags().contains(TAG + ".fighting"));
                    if (g.getTags().contains(TAG + ".own")) j.addProperty("own", true);
                    String owner = get(g, "owner", "");
                    if (!owner.isEmpty()) {
                        j.addProperty("ownerId", owner);
                        j.addProperty("owner", "someone");
                    }
                    out.add(j.toString());
                }
            }
            case "liveact" -> {
                calls.add("liveact " + arg);
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                IronGolem g = byId(s, o.get("id").getAsString());
                if (g == null) return List.of("no", "He isn't there any more.");
                if (o.get("do").getAsString().equals("size")) put(g, "size", o.get("value").getAsString());
                out.add("ok");
                out.add("Fakesys: done.");
            }
            case "sys" -> out.add("{\"sys\":1,\"giant\":\"fakesys\",\"name\":\"Fakesys\",\"weak\":[\"his nose\"],"
                    + "\"does\":[\"spots\",\"mood\",\"turn\",\"go\"]}");
            case "sysact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("sys").getAsInt() > 1) return null;
                String what = o.get("do").getAsString();
                calls.add(what + " " + arg);
                IronGolem g = byId(s, o.get("id").getAsString());
                if (g == null) return List.of("no", "He isn't there any more.");
                switch (what) {
                    case "spots" -> {
                        return List.of("ok", "{\"spots\":[{\"name\":\"his nose\",\"x\":" + g.getX() + ",\"y\":"
                                + g.getEyeY() + ",\"z\":" + g.getZ() + ",\"r\":0.5}]}");
                    }
                    case "mood" -> {
                        put(g, "mood", o.get("value").getAsString());
                        return List.of("ok", "Fakesys: mood " + o.get("value").getAsString() + ".");
                    }
                    case "turn" -> {
                        put(g, "turnedOn", o.get("player").getAsString());
                        return List.of("ok", "Fakesys turned on someone.");
                    }
                    case "go" -> {
                        put(g, "goes", o.get("x").getAsString() + "," + o.get("z").getAsString());
                        return List.of("ok", "Fakesys: on his way.");
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case "fun" -> out.add("{\"fun\":1,\"giant\":\"fakesys\",\"name\":\"Fakesys\",\"does\":[\"stats\"],"
                    + "\"places\":[\"land\"]}");
            case "funact" -> {
                calls.add("funact " + arg);
                out.add("ok");
                out.add("Fakesys: stats read.");
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
