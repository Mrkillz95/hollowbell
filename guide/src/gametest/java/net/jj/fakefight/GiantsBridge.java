package net.jj.fakefight;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * (Part 10B) a stand-in giant for the giant fights' tests: iron golems tagged "fakefight" are him (their health is
 * his, out of 100). He answers "admin"/"adminset" (keeping the all-giants values he was sent), "live"/"liveact" (an
 * owner from a "fakefight.owner=" tag; kill, home), "summon", "fun"/"funact" (move, stats), "sysact" (go, mood) and
 * "fights"/"fightact" (ADMIN_BRIDGE.md §27.2: done simply and written down: yield tags him backed down).
 */
public final class GiantsBridge {
    private GiantsBridge() {}

    public static final int API = 1;
    public static final String TAG = "fakefight";
    public static final List<String> calls = new ArrayList<>();
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
        g.setInvulnerable(false);
        g.addTag(TAG);
        l.addFreshEntity(g);
        return g;
    }

    public static boolean called(String start) {
        for (String c : List.copyOf(calls)) if (c.startsWith(start)) return true;
        return false;
    }

    public static int count(String start) {
        int n = 0;
        for (String c : List.copyOf(calls)) if (c.startsWith(start)) n++;
        return n;
    }

    private static IronGolem byId(MinecraftServer s, String id) {
        for (IronGolem g : golems(s)) if (g.getStringUUID().equals(id)) return g;
        return null;
    }

    private static String owner(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(TAG + ".owner=")) return t.substring(TAG.length() + 7);
        return "";
    }

    public static List<String> giants(MinecraftServer s, String action, String arg) {
        List<String> out = new ArrayList<>();
        switch (action) {
            case "admin" -> out.add("{\"admin\":1,\"giant\":\"fakefight\",\"name\":\"Fakefight\",\"uses\":["
                    + "\"all.wildFightTo\",\"all.ownedFightTo\",\"all.fightAsk\",\"all.fightReport\"],\"all\":{}}");
            // (Part 12) the giant editor: this stand-in takes looks
            case "look" -> out.add("{\"look\":1}");
            case "lookclient" -> out.add("ok");
            case "adminset" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                all = o.has("all") ? o.getAsJsonObject("all") : new JsonObject();
                out.add("ok");
            }
            case "live" -> {
                out.add("{\"live\":1,\"giant\":\"fakefight\",\"name\":\"Fakefight\",\"moods\":[\"calm\",\"hunting\"],"
                        + "\"size\":[0.1,2],\"does\":[\"size\"]}");
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
                    j.addProperty("size", 1);
                    j.addProperty("asleep", g.getTags().contains(TAG + ".asleep"));
                    String o = owner(g);
                    if (!o.isEmpty()) {
                        j.addProperty("owner", "Owner");
                        j.addProperty("ownerId", o);
                    }
                    out.add(j.toString());
                }
            }
            case "liveact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String what = o.get("do").getAsString();
                calls.add("liveact " + what + " " + o.get("id").getAsString());
                IronGolem g = byId(s, o.get("id").getAsString());
                if (g == null) return List.of("no", "He isn't there any more.");
                if (what.equals("kill")) g.kill();
                out.add("ok");
                out.add("Fakefight: " + what + ".");
            }
            case "summon" -> {
                // "dim x y z - mood size"
                String[] w = arg.trim().split("\\s+");
                ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(w[0])));
                calls.add("summon " + arg);
                spawn(l, Double.parseDouble(w[1]), Double.parseDouble(w[2]), Double.parseDouble(w[3]));
                out.add("Fakefight: summoned.");
            }
            case "fun" -> out.add("{\"fun\":1,\"giant\":\"fakefight\",\"name\":\"Fakefight\",\"does\":[\"stats\","
                    + "\"move\"],\"places\":[\"land\"]}");
            case "funact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String what = o.get("do").getAsString();
                calls.add("funact " + what + " " + o.get("id").getAsString());
                IronGolem g = byId(s, o.get("id").getAsString());
                if (g == null) return List.of("no", "He isn't there any more.");
                if (what.equals("move")) g.teleportTo(o.get("x").getAsDouble(), o.get("y").getAsDouble(),
                        o.get("z").getAsDouble());
                out.add("ok");
                out.add("Fakefight: " + what + ".");
            }
            case "sysact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                calls.add("sysact " + o.get("do").getAsString() + " " + o.get("id").getAsString());
                out.add("ok");
                out.add("Fakefight: ok.");
            }
            case "fights" -> out.add("{\"fight\":1,\"giant\":\"fakefight\",\"name\":\"Fakefight\",\"moves\":[\"stomp\","
                    + "\"roar\",\"slam\"],\"finisher\":\"slam\",\"does\":[\"hp\",\"health\",\"fight\",\"yield\",\"chase\","
                    + "\"stop\",\"do\",\"roar\"]}");
            case "fightact" -> {
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String what = o.get("do").getAsString().toLowerCase(Locale.ROOT);
                IronGolem g = byId(s, o.get("id").getAsString());
                if (g == null) return List.of("no", "He isn't there any more.");
                String foe = o.has("foe") ? o.get("foe").getAsString() : o.has("by") ? o.get("by").getAsString() : "-";
                if (!what.equals("hp"))
                    calls.add("fightact " + what + " " + g.getStringUUID() + " " + foe + (o.has("how") ? " "
                            + o.get("how").getAsString() : "") + (o.has("move") ? " " + o.get("move").getAsString() : "")
                            + (o.has("wild") ? " wild=" + o.get("wild").getAsBoolean() : ""));
                switch (what) {
                    case "hp" -> {
                        JsonObject j = new JsonObject();
                        j.addProperty("hp", g.getHealth());
                        j.addProperty("max", g.getMaxHealth());
                        j.addProperty("yielding", g.getTags().contains("jj_giant_yield"));
                        j.addProperty("running", false);
                        String t = "-";
                        for (String x : g.getTags()) if (x.startsWith(TAG + ".target=")) t = x.substring(TAG.length() + 8);
                        j.addProperty("target", t);
                        return List.of("ok", j.toString());
                    }
                    case "health" -> {
                        float share = o.get("share").getAsFloat();
                        boolean set = o.has("set") && o.get("set").getAsBoolean();
                        g.setHealth(set ? share * g.getMaxHealth() : Math.min(g.getMaxHealth(), g.getHealth() + share
                                * g.getMaxHealth()));
                    }
                    case "fight", "chase" -> {
                        g.removeTag("jj_giant_yield");
                        target(g, foe);
                    }
                    case "yield" -> {
                        g.addTag("jj_giant_yield");
                        target(g, "-");
                    }
                    case "stop" -> target(g, "-");
                    default -> {}
                }
                out.add("ok");
                out.add("Fakefight: " + what + ".");
            }
            default -> {
                return null;
            }
        }
        return out;
    }

    private static void target(IronGolem g, String id) {
        for (String t : List.copyOf(g.getTags())) if (t.startsWith(TAG + ".target=")) g.removeTag(t);
        g.addTag(TAG + ".target=" + id);
    }
}
