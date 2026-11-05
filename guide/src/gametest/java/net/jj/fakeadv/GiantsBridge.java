package net.jj.fakeadv;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 11) a stand-in giant for the adventure tests: iron golems tagged "fakeadv" are him. He walks for real when
 * sent somewhere (sysact go: eight blocks toward the spot each time he's told), keeps a mood, a target player, sleep,
 * a fight and an owner the tests set (listed on his live rows), answers summon, fun/funact (move, stats) and liveact
 * (sleep, wake, remove), the way ADMIN_BRIDGE.md says.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final String TAG = "fakeadv";
    public static final List<String> calls = new ArrayList<>();
    public static final Set<String> asleep = new HashSet<>(), fighting = new HashSet<>();
    public static final Map<String, String> owner = new HashMap<>(), mood = new HashMap<>(), turned = new HashMap<>();

    public static void reset() {
        calls.clear();
        asleep.clear();
        fighting.clear();
        owner.clear();
        mood.clear();
        turned.clear();
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
        g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        g.setHealth(200f);
        g.setPersistenceRequired();
        l.addFreshEntity(g);
        return g;
    }

    private static IronGolem find(MinecraftServer s, String id) {
        for (IronGolem g : golems(s)) if (g.getStringUUID().equals(id)) return g;
        return null;
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakeadv\",\"name\":\"Fakeadv\",\"moods\":[\"calm\",\"hunting\","
                        + "\"guardian\"],\"size\":[0.03,2],\"does\":[\"sleep\",\"wake\",\"remove\",\"kill\"]}");
                for (IronGolem g : golems(s)) {
                    JsonObject j = new JsonObject();
                    String id = g.getStringUUID();
                    j.addProperty("id", id);
                    j.addProperty("state", "here");
                    j.addProperty("dim", g.level().dimension().location().toString());
                    j.addProperty("x", g.getX());
                    j.addProperty("y", g.getY());
                    j.addProperty("z", g.getZ());
                    j.addProperty("health", g.getHealth());
                    j.addProperty("max", g.getMaxHealth());
                    j.addProperty("mood", mood.getOrDefault(id, "calm"));
                    j.addProperty("asleep", asleep.contains(id));
                    j.addProperty("fighting", fighting.contains(id));
                    if (owner.containsKey(id)) j.addProperty("owner", owner.get(id));
                    out.add(j.toString());
                }
            }
            case "liveact" -> {
                calls.add("liveact " + arg);
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                IronGolem e = find(s, o.get("id").getAsString());
                if (e == null) return List.of("no", "He isn't there any more.");
                switch (o.get("do").getAsString()) {
                    case "remove" -> e.discard();
                    case "sleep" -> asleep.add(e.getStringUUID());
                    case "wake" -> asleep.remove(e.getStringUUID());
                    case "kill" -> e.kill();
                    default -> {
                        return null;
                    }
                }
                return List.of("ok", "Fakeadv: done.");
            }
            case "summon" -> {
                String[] w = arg.trim().split("\\s+");
                ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(w[0])));
                calls.add("summon " + arg);
                if (l == null) return List.of("Fakeadv: there's no world called " + w[0] + ".");
                IronGolem g = spawn(l, Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
                if (w.length > 6) mood.put(g.getStringUUID(), w[5]);
                out.add("Fakeadv: summoned.");
            }
            case "fun" -> out.add("{\"fun\":1,\"giant\":\"fakeadv\",\"name\":\"Fakeadv\",\"does\":[\"stats\",\"move\"],"
                    + "\"places\":[\"land\"]}");
            case "funact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String what = o.get("do").getAsString();
                calls.add(what + " " + arg);
                IronGolem e = find(s, o.get("id").getAsString());
                if (e == null) return List.of("no", "He isn't there any more.");
                if (what.equals("move")) {
                    e.teleportTo(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                    return List.of("ok", "Fakeadv: moved.");
                }
                if (what.equals("stats")) return List.of("ok", "Fakeadv: stats read.");
                return null;
            }
            case "sys" -> out.add("{\"sys\":1,\"giant\":\"fakeadv\",\"name\":\"Fakeadv\",\"weak\":[],\"does\":[\"go\","
                    + "\"mood\",\"turn\"]}");
            case "sysact" -> {
                calls.add("sysact " + arg);
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                IronGolem e = find(s, o.get("id").getAsString());
                if (e == null) return List.of("no", "He isn't there any more.");
                switch (o.get("do").getAsString()) {
                    case "go" -> {
                        Vec3 to = new Vec3(o.get("x").getAsDouble() - e.getX(), 0, o.get("z").getAsDouble() - e.getZ());
                        Vec3 at = e.position().add(to.length() < 8 ? to : to.normalize().scale(8));
                        e.teleportTo(at.x, e.getY(), at.z);
                        return List.of("ok", "Fakeadv: on his way.");
                    }
                    case "mood" -> {
                        mood.put(e.getStringUUID(), o.get("value").getAsString());
                        return List.of("ok", "Fakeadv: mood " + o.get("value").getAsString() + ".");
                    }
                    case "turn" -> {
                        turned.put(e.getStringUUID(), o.get("player").getAsString());
                        return List.of("ok", "Fakeadv turned.");
                    }
                    default -> {
                        return null;
                    }
                }
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
