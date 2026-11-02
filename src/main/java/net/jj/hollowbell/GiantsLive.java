package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.FarOrders;
import net.jj.hollowbell.world.TakeMe;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The Giants Guide's live control (ADMIN_BRIDGE.md, "Live control"): every one of him the world knows, floating in
 * the world, out of it as a sum or lying in land nobody has loaded, with where he is and how he is ("live"), and one
 * admin action on one of them by his id ("liveact"). The Guide checks who may ask; this only does it, the way his own
 * commands do.
 */
public final class GiantsLive {
    private GiantsLive() {}

    public static final int VERSION = 1;
    /** his moods in his own order (what /hollowbell mood takes) */
    static final String[] MOODS = {"calm", "hunting", "guardian"};

    static final List<String> DOES =
            List.of("tp", "heal", "size", "sleep", "wake", "calm", "forgive", "owner", "home", "remove", "kill");
    private static final String WHO = "Hollowbell";
    private static final int MOST = 64;

    /** "live": a header line, then one line per one of him */
    public static @Nullable List<String> describe(MinecraftServer server, String arg) {
        if (server == null) return null;
        try {
            if (arg != null && !arg.isBlank() && Integer.parseInt(arg.trim()) > VERSION) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        List<String> out = new ArrayList<>();
        JsonObject head = new JsonObject();
        head.addProperty("live", VERSION);
        head.addProperty("giant", HollowbellMod.MOD_ID);
        head.addProperty("name", WHO);
        JsonArray moods = new JsonArray();
        for (String m : MOODS) moods.add(m);
        head.add("moods", moods);
        JsonArray size = new JsonArray();
        size.add(round(HollowbellEntity.MIN_SCALE));
        size.add(round(HollowbellEntity.MAX_SCALE));
        head.add("size", size);
        JsonArray does = new JsonArray();
        for (String d : DOES) does.add(d);
        head.add("does", does);
        out.add(head.toString());
        BookRule book = BookRule.get(server);
        WorldOne w1 = WorldOne.get(server);
        UUID one = w1.oneId();
        List<UUID> seen = new ArrayList<>();
        int n = 0;
        for (Object o : TakeMe.listed(server)) {
            JsonObject j = new JsonObject();
            UUID id;
            if (o instanceof HollowbellEntity h) {
                if (h.isDeadOrDying()) continue;
                id = h.getUUID();
                j.addProperty("id", id.toString());
                j.addProperty("state", "here");
                j.addProperty("dim", h.level().dimension().location().toString());
                j.addProperty("x", round(h.getX()));
                j.addProperty("y", round(h.getY()));
                j.addProperty("z", round(h.getZ()));
                j.addProperty("health", round(h.healthNow()));
                j.addProperty("max", round(h.healthMax()));
                j.addProperty("size", round(h.bellScale()));
                j.addProperty("mood", mood(h.variant()));
                j.addProperty("asleep", h.asleep());
                j.addProperty("fighting", h.getTarget() != null);
                if (h.hasCustomName() && h.getCustomName() != null)
                    j.addProperty("name", h.getCustomName().getString());
            } else if (o instanceof Away.Rec r) {
                id = r.id;
                Vec3 s = r.spot(level(server, r.dim).getGameTime());
                j.addProperty("id", id.toString());
                j.addProperty("state", "away");
                j.addProperty("dim", r.dim);
                j.addProperty("x", round(s.x));
                j.addProperty("z", round(s.z));
                j.addProperty("health", round(r.hp));
                j.addProperty("max", round(r.hpMax));
                j.addProperty("size", round(r.scale));
                j.addProperty("mood", mood(r.variant));
                if (r.body != null && r.body.contains("CustomName")) {
                    try {
                        var name = net.minecraft.network.chat.Component.Serializer.fromJson(
                                r.body.getString("CustomName"), server.registryAccess());
                        if (name != null) j.addProperty("name", name.getString());
                    } catch (RuntimeException unreadable) {
                        // (left out)
                    }
                }
            } else if (o instanceof TakeMe.Lying k) {
                id = k.id();
                j.addProperty("id", id.toString());
                j.addProperty("state", "unloaded");
                j.addProperty("dim", k.at().dim());
                j.addProperty("x", round(k.at().x()));
                j.addProperty("z", round(k.at().z()));
                if (k.at().scale() > 0) j.addProperty("size", round(k.at().scale()));
            } else continue;
            if (seen.contains(id)) continue;
            seen.add(id);
            j.addProperty("own", id.equals(one));
            owner(j, book);
            if (n++ < MOST) out.add(j.toString());
        }
        // the world's own one, saved away with his chunk and written down nowhere else
        if (w1.aliveNow() && one != null && !seen.contains(one) && w1.where() != null && n < MOST) {
            JsonObject j = new JsonObject();
            j.addProperty("id", one.toString());
            j.addProperty("state", "unloaded");
            j.addProperty("dim", "minecraft:overworld");
            j.addProperty("x", w1.where().getX() + 0.5);
            j.addProperty("z", w1.where().getZ() + 0.5);
            j.addProperty("own", true);
            owner(j, book);
            out.add(j.toString());
        }
        return out;
    }

    private static void owner(JsonObject j, BookRule book) {
        if (book.holder() == null) return;
        j.addProperty("owner", book.holderName());
        j.addProperty("ownerId", book.holder().toString());
    }

    private static String mood(int v) {
        return v >= 0 && v < MOODS.length ? MOODS[v] : "calm";
    }

    private static double round(double d) {
        return Math.round(d * 100) / 100.0;
    }

    private static ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation key = ResourceLocation.tryParse(dim);
        ServerLevel l = key == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        return l != null ? l : server.overworld();
    }

    /** "liveact": one action on one of him; ["ok", line] or ["no", why]; null for what it can't read */
    public static @Nullable List<String> act(MinecraftServer server, String arg) {
        if (server == null || arg == null) return null;
        UUID id;
        String what, value, by;
        try {
            JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("live").getAsInt() > VERSION) return null;
            id = UUID.fromString(o.get("id").getAsString());
            what = o.get("do").getAsString().toLowerCase(Locale.ROOT);
            value = o.has("value") ? o.get("value").getAsString().trim() : "";
            by = o.has("by") ? o.get("by").getAsString() : "-";
        } catch (RuntimeException e) {
            return null;
        }
        if (!DOES.contains(what)) return null;
        Object target = null;
        for (Object o : TakeMe.listed(server)) {
            UUID oid = o instanceof HollowbellEntity h ? h.getUUID()
                    : o instanceof Away.Rec r ? r.id
                    : o instanceof TakeMe.Lying k ? k.id() : null;
            if (target == null && id.equals(oid) && !(o instanceof HollowbellEntity h && h.isDeadOrDying()))
                target = o;
        }
        WorldOne w1 = WorldOne.get(server);
        boolean ownUnloaded = target == null && w1.aliveNow() && w1.isTheOne(id) && w1.where() != null;
        if (target == null && !ownUnloaded) return no("He isn't there any more.");
        HollowbellEntity h = target instanceof HollowbellEntity e ? e : null;
        Away away = Away.get(server);
        switch (what) {
            case "tp" -> {
                // (as /hollowbell tp: safe ground just outside his bell, the land got ready first)
                ServerPlayer p = player(server, by);
                if (p == null) return no("Only a player can be taken to him.");
                return ok(TakeMe.tpTo(p, target).getString());
            }
            case "owner" -> {
                // (as an op's /give of a new book: whoever has a fresh copy holds it, and the old one goes quiet)
                ServerPlayer p = player(server, value);
                if (p == null) return no("Pick a player who is online.");
                ItemStack book = new ItemStack(ModItems.CODEX);
                BookRule.get(server).given(p, book);
                if (!p.getInventory().add(book)) p.drop(book, false);
                return ok(WHO + ": " + p.getGameProfile().getName() + " has his book now.");
            }
            case "remove" -> {
                // (as /hollowbell remove, for this one only)
                if (h != null) {
                    h.discard();
                    w1.removed(server.overworld(), id);
                } else if (target instanceof Away.Rec r) {
                    w1.removed(server.overworld(), r.id);
                    away.forget(r.id);
                } else if (target instanceof TakeMe.Lying k) {
                    if (!FarOrders.fetchOne(server, k.id(), false)) return no("He isn't there any more.");
                } else return no("He's lying where nobody is near. Go to him first (Teleport).");
                return ok("Removed 1 " + WHO + ".");
            }
            case "kill" -> {
                // (as /hollowbell kill, for this one only: out of the world he's brought back and killed; lying in
                // unloaded land he's fetched and killed when it loads)
                if (target instanceof Away.Rec r) {
                    ServerLevel l = level(server, r.dim);
                    h = away.bringBack(l, r);
                    if (h == null) return no("He isn't there any more.");
                } else if (target instanceof TakeMe.Lying k) {
                    if (!FarOrders.fetchOne(server, k.id(), true)) return no("He isn't there any more.");
                    return ok("Killed 1 " + WHO + ".");
                }
                if (h == null) return no("He's lying where nobody is near. Go to him first (Teleport).");
                FarOrders.kill((ServerLevel) h.level(), h);
                return ok("Killed 1 " + WHO + ".");
            }
            case "home" -> {
                // (as /hollowbell goto to the middle of his ground: in the world he flies there; out of it the sum
                // is aimed there; in unloaded land he's fetched and sent)
                if (!w1.homeClaimed()) return no(WHO + ": he has no ground yet.");
                FarOrders.Target t;
                ServerLevel l;
                if (h != null) {
                    l = (ServerLevel) h.level();
                    t = new FarOrders.Target(FarOrders.Kind.LOADED, id, h.getX(), h.getZ(), h);
                } else if (target instanceof Away.Rec r) {
                    l = level(server, r.dim);
                    Vec3 s = r.spot(l.getGameTime());
                    t = new FarOrders.Target(FarOrders.Kind.AWAY, r.id, s.x, s.z, null);
                } else if (target instanceof TakeMe.Lying k) {
                    l = level(server, k.at().dim());
                    t = new FarOrders.Target(FarOrders.Kind.PARKED, k.id(), k.at().x(), k.at().z(), null);
                } else return no("He's lying where nobody is near. Go to him first (Teleport).");
                if (l != server.overworld()) return no(WHO + ": his ground is in the Overworld and he isn't.");
                Vec3 to = new Vec3(w1.homeX() + 0.5, 0, w1.homeZ() + 0.5);
                if (FarOrders.order(l, t, to, null, 0f) == null) return no("He isn't there any more.");
                return ok(WHO + ": on his way home.");
            }
            default -> {
                // the rest need him floating in the world
                if (h == null) return no("He's out of the world right now. Go to him first (Teleport).");
                return switch (what) {
                    case "heal" -> {
                        // (as /hollowbell heal: his health, and his pods back)
                        h.heal();
                        h.mendPods();
                        yield ok(WHO + ": healed.");
                    }
                    case "size" -> {
                        float s;
                        try {
                            s = Float.parseFloat(value);
                        } catch (NumberFormatException e) {
                            s = Float.NaN;
                        }
                        if (!Float.isFinite(s) || s < HollowbellEntity.MIN_SCALE || s > HollowbellEntity.MAX_SCALE)
                            yield no("Size has to be a number from " + plain(HollowbellEntity.MIN_SCALE) + " to "
                                    + plain(HollowbellEntity.MAX_SCALE) + ".");
                        h.setBellScale(s);
                        yield ok(WHO + ": size " + plain(h.bellScale()) + ".");
                    }
                    case "sleep" -> {
                        if (!h.asleep()) h.goToSleep();
                        yield h.asleep() ? ok(WHO + ": asleep.") : no(WHO + ": he can't lie down right now.");
                    }
                    case "wake" -> {
                        h.wakeUp();
                        yield ok(WHO + ": awake.");
                    }
                    case "calm" -> {
                        // (as /giants calm)
                        h.setVariant(HollowbellEntity.CALM);
                        h.clearHitList();
                        h.forgiveAll();
                        yield ok(WHO + ": calmed, grudges forgotten.");
                    }
                    case "forgive" -> {
                        h.forgiveAll();
                        yield ok(WHO + ": grudges forgotten.");
                    }
                    default -> null;
                };
            }
        }
    }

    /** a size the way people write it: 1, 0.5, 0.25 */
    static String plain(double v) {
        return java.math.BigDecimal.valueOf(Math.round(v * 100) / 100.0).stripTrailingZeros().toPlainString();
    }

    private static List<String> ok(String line) {
        return List.of("ok", line);
    }

    private static List<String> no(String why) {
        return List.of("no", why);
    }

    private static @Nullable ServerPlayer player(MinecraftServer server, String id) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
