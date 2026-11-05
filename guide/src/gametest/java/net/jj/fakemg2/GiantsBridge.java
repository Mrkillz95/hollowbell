package net.jj.fakemg2;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * (Part 10A) a stand-in giant for the minigames' tests: iron golems tagged "fakemg" are him. He answers "live" (every
 * one of him in any world, with its world), "liveact" (remove: gone), "summon" (a golem at the spot, his mood and size
 * written down), "fun"/"funact" (stats, move, playas), "sys"/"sysact" (go) and "meet", the way ADMIN_BRIDGE.md says.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final String TAG = "fakemg2";
    public static final List<String> calls = new ArrayList<>();
    /** what the golems are made with: their most health */
    public static double health = 200;

    public static void reset() {
        calls.clear();
        health = 200;
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
        g.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        g.setHealth((float) health);
        g.setPersistenceRequired();
        l.addFreshEntity(g);
        return g;
    }

    private static Entity find(MinecraftServer s, String id) {
        for (IronGolem g : golems(s)) if (g.getStringUUID().equals(id)) return g;
        return null;
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakemg2\",\"name\":\"Fakemgtwo\",\"moods\":[\"calm\",\"hunting\"],"
                        + "\"size\":[0.03,2],\"does\":[\"tp\",\"heal\",\"size\",\"remove\",\"kill\"]}");
                for (IronGolem g : golems(s)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", g.getStringUUID());
                    j.addProperty("state", "here");
                    j.addProperty("dim", g.level().dimension().location().toString());
                    j.addProperty("x", g.getX());
                    j.addProperty("y", g.getY());
                    j.addProperty("z", g.getZ());
                    j.addProperty("health", g.getHealth());
                    j.addProperty("max", g.getMaxHealth());
                    out.add(j.toString());
                }
            }
            case "liveact" -> {
                calls.add("liveact " + arg);
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                Entity e = find(s, o.get("id").getAsString());
                if (e == null) return List.of("no", "He isn't there any more.");
                if (o.get("do").getAsString().equals("remove")) {
                    e.discard();
                    return List.of("ok", "Removed 1 Fakemgtwo.");
                }
                out.add("ok");
                out.add("Fakemgtwo: done.");
            }
            case "summon" -> {
                String[] w = arg.trim().split("\\s+");
                ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(w[0])));
                calls.add("summon " + arg);
                if (l == null) return List.of("Fakemgtwo: there's no world called " + w[0] + ".");
                spawn(l, Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
                out.add("Fakemgtwo: summoned.");
            }
            case "fun" -> out.add("{\"fun\":1,\"giant\":\"fakemg2\",\"name\":\"Fakemgtwo\",\"does\":[\"playas\",\"stats\","
                    + "\"move\"],\"places\":[\"land\"]}");
            case "funact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String what = o.get("do").getAsString();
                calls.add(what + " " + arg);
                Entity e = find(s, o.get("id").getAsString());
                if (e == null) return List.of("no", "He isn't there any more.");
                switch (what) {
                    case "playas" -> {
                        ServerPlayer p = s.getPlayerList().getPlayer(UUID.fromString(o.get("by").getAsString()));
                        if (p == null) return List.of("no", "Only a player can play as him.");
                        if (o.get("on").getAsBoolean()) {
                            p.startRiding(e, true);
                            return List.of("ok", "You're playing as the Fakemgtwo.");
                        }
                        p.stopRiding();
                        return List.of("ok", "You've stepped out of the Fakemgtwo.");
                    }
                    case "stats" -> {
                        return List.of("ok", "Fakemgtwo: stats read.");
                    }
                    case "move" -> {
                        e.teleportTo(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                        return List.of("ok", "Fakemgtwo: moved.");
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case "sys" -> out.add("{\"sys\":1,\"giant\":\"fakemg2\",\"name\":\"Fakemgtwo\",\"weak\":[],\"does\":[\"go\","
                    + "\"mood\"]}");
            case "sysact" -> {
                calls.add("sysact " + arg);
                out.add("ok");
                out.add("Fakemgtwo: on his way.");
            }
            case "meet" -> {
                calls.add("meet " + arg);
                out.add("Fakemgtwo: he goes for the other one.");
            }
            default -> {
                return null;
            }
        }
        return out;
    }
}
