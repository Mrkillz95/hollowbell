package net.jj.fakefun;

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
import net.minecraft.world.entity.animal.IronGolem;

/**
 * (Part 7) a stand-in giant for the Fun and World tools' tests: iron golems tagged "fakefun" are him. He answers
 * "live", "fun"/"funact" (playas, stats, move: done simply and written down), "summon" (a golem at the spot), "meet",
 * "paint" and "grounds" the way ADMIN_BRIDGE.md 23.2 says.
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static final String TAG = "fakefun";
    public static final List<String> calls = new ArrayList<>();
    public static List<String> places = List.of("land");
    public static double gx, gz;

    public static void reset() {
        calls.clear();
        places = List.of("land");
    }

    static List<IronGolem> golems(MinecraftServer s) {
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

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakefun\",\"name\":\"Fakefun\",\"moods\":[\"calm\",\"hunting\"],"
                        + "\"size\":[0.1,2],\"does\":[\"size\"]}");
                for (IronGolem g : golems(s)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("id", g.getStringUUID());
                    j.addProperty("state", "here");
                    j.addProperty("dim", g.level().dimension().location().toString());
                    j.addProperty("x", g.getX());
                    j.addProperty("y", g.getY());
                    j.addProperty("z", g.getZ());
                    out.add(j.toString());
                }
            }
            case "liveact" -> {
                calls.add("liveact " + arg);
                out.add("ok");
                out.add("Fakefun: done.");
            }
            case "fun" -> out.add("{\"fun\":1,\"giant\":\"fakefun\",\"name\":\"Fakefun\",\"does\":[\"playas\",\"stats\","
                    + "\"move\"],\"places\":" + new com.google.gson.Gson().toJson(places) + "}");
            case "funact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                if (o.get("fun").getAsInt() > 1) return null;
                String what = o.get("do").getAsString();
                calls.add(what + " " + arg);
                Entity e = null;
                for (IronGolem g : golems(s)) if (g.getStringUUID().equals(o.get("id").getAsString())) e = g;
                if (e == null) return List.of("no", "He isn't there any more.");
                switch (what) {
                    case "playas" -> {
                        ServerPlayer p = s.getPlayerList().getPlayer(UUID.fromString(o.get("by").getAsString()));
                        boolean on = o.get("on").getAsBoolean();
                        if (p == null) return List.of("no", "Only a player can play as him.");
                        if (on) {
                            p.startRiding(e, true);
                            return List.of("ok", "You're playing as the Fakefun. G gets you off.");
                        }
                        if (p.getVehicle() != e) return List.of("no", "You aren't playing as him.");
                        p.stopRiding();
                        return List.of("ok", "You've stepped out of the Fakefun.");
                    }
                    case "stats" -> {
                        return List.of("ok", "Fakefun: stats read.");
                    }
                    case "move" -> {
                        if (!places.contains(o.get("where").getAsString())) return List.of("no", "He can't live there.");
                        e.teleportTo(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                        if (o.get("stay").getAsBoolean()) e.addTag("fakefun_stays");
                        return List.of("ok", "Fakefun: moved.");
                    }
                    default -> {
                        return null;
                    }
                }
            }
            case "summon" -> {
                String[] w = arg.trim().split("\\s+");
                ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(w[0])));
                calls.add("summon " + arg);
                spawn(l, Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
                out.add("Fakefun: summoned.");
            }
            case "meet" -> {
                calls.add("meet " + arg);
                out.add("Fakefun: he goes for the other one.");
            }
            case "paint" -> {
                calls.add("paint " + arg);
                out.add("Fakefun: painting.");
            }
            case "grounds" -> out.add("{\"x\":" + gx + ",\"z\":" + gz + ",\"r\":300,\"n\":0}");
            default -> {
                return null;
            }
        }
        return out;
    }
}
