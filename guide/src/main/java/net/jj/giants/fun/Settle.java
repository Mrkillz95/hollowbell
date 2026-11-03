package net.jj.giants.fun;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Locale;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Giant mixing") A giant moved to live somewhere else and stay there: where you stand
 * or look, the nearest deep sea, a hollow under the ground, high in the sky, or the middle of another giant's ground;
 * each only where his movement allows (his mod's "places"). His own mod moves him (funact move, stay).
 */
public final class Settle {
    private Settle() {}

    public static final List<String> WHERE = List.of("here", "look", "sea", "cave", "sky", "ground");

    public static AdminServer.Result settle(MinecraftServer server, String who, ServerPlayer p, LivingEntity e,
            String where, @Nullable String groundKind) {
        String kind = FunGiants.kindOf(e);
        Giant g = kind == null ? null : AdminServer.giant(kind);
        if (g == null) return AdminServer.Result.fail("That isn't a giant.");
        String name = AdminServer.nameOf(g);
        if (!FunGiants.does(server, g).contains("move"))
            return AdminServer.Result.fail(name + ": this version can't be moved. Update the " + name + " mod.");
        List<String> places = FunGiants.places(server, g);
        where = where.toLowerCase(Locale.ROOT);
        double size = FunGiants.sizeOf(e), tall = FunGiants.height(kind, size);
        ServerLevel l = p.serverLevel();
        BlockPos spot;
        String kindOfSpot;
        String said;
        switch (where) {
            case "here", "look" -> {
                spot = where.equals("look") ? Spots.looked(p, Spots.LOOK) : p.blockPosition();
                if (spot == null) return AdminServer.Result.fail("Look at the ground (256 blocks at most).");
                int deep = Spots.waterDepth(l, spot.getX(), spot.getZ());
                kindOfSpot = deep >= 6 ? "sea" : "land";
                double y = kindOfSpot.equals("sea") ? Spots.floor(l, spot.getX(), spot.getZ())
                        : Spots.ground(l, spot.getX(), spot.getZ());
                if (Double.isNaN(y)) return AdminServer.Result.fail("That land isn't loaded.");
                spot = new BlockPos(spot.getX(), (int) y, spot.getZ());
                said = where.equals("look") ? "where you looked" : "where you stand";
            }
            case "sea" -> {
                spot = Spots.deepWater(l, p.blockPosition(), 6, 256);
                if (spot == null) return AdminServer.Result.fail("No deep water (6 blocks or more) within 256 blocks"
                        + " in loaded land. Stand nearer the sea.");
                kindOfSpot = "sea";
                said = "at sea";
            }
            case "cave" -> {
                int need = (int) Math.ceil(Math.min(tall, 60));
                spot = Spots.hollow(l, p.blockPosition(), Math.max(4, need), 64);
                if (spot == null) return AdminServer.Result.fail("No hollow under the ground here big enough for him ("
                        + Math.round(tall) + " blocks tall at this size). Make him smaller first, or try elsewhere.");
                kindOfSpot = "cave";
                said = "in a cave";
            }
            case "sky" -> {
                double y = Spots.ground(l, p.getBlockX(), p.getBlockZ());
                if (Double.isNaN(y)) y = p.getY();
                spot = new BlockPos(p.getBlockX(), (int) Math.min(l.getMaxBuildHeight() - 8, y + 40 + tall),
                        p.getBlockZ());
                kindOfSpot = "sky";
                said = "in the sky";
            }
            case "ground" -> {
                Giant other = groundKind == null ? null : AdminServer.giant(groundKind);
                if (other == null) return AdminServer.Result.fail("Whose ground? Name a giant.");
                List<String> lines = AdminServer.call(server, other, "grounds", "");
                JsonObject gr = lines == null || lines.isEmpty() ? null : AdminJson.object(lines.get(0));
                if (gr == null) return AdminServer.Result.fail("The " + AdminServer.nameOf(other) + " has no ground yet.");
                int x = (int) AdminJson.num(gr, "x", 0), z = (int) AdminJson.num(gr, "z", 0);
                ServerLevel over = server.overworld();
                if (e.level() != over) return AdminServer.Result.fail("His ground is in the Overworld and the " + name
                        + " isn't. Move him within his own world.");
                String place = "the " + AdminServer.nameOf(other) + "'s ground";
                Spots.whenLoaded(over, List.of(new BlockPos(x, 0, z)), () -> {
                    int deep = Spots.waterDepth(over, x, z);
                    String k = deep >= 6 ? "sea" : "land";
                    if (!places.contains(k)) {
                        p.displayClientMessage(Component.literal("The middle of " + place + " is "
                                + (k.equals("sea") ? "deep water" : "land") + ": the " + name + " can't live there."),
                                false);
                        return;
                    }
                    double y = k.equals("sea") ? Spots.floor(over, x, z) : Spots.ground(over, x, z);
                    AdminServer.Result r = move(server, who, g, e, over, new BlockPos(x, (int) y, z), k, place);
                    p.displayClientMessage(Component.literal(String.join(" ", r.lines())), false);
                    if (r.ok()) Fun.tell(server, p, who + " settled a " + name + " in " + place + ".");
                }, () -> p.displayClientMessage(Component.literal("The land there isn't ready yet. Try again in a"
                        + " moment."), false));
                return AdminServer.Result.said(List.of("Taking the " + name + " to " + place + "…"));
            }
            default -> {
                return AdminServer.Result.fail("Where? One of: here, look, sea, cave, sky, ground <giant>.");
            }
        }
        if (!places.contains(kindOfSpot))
            return AdminServer.Result.fail("The " + name + " can't live " + (kindOfSpot.equals("land") ? "on land"
                    : kindOfSpot.equals("sea") ? "at sea" : kindOfSpot.equals("cave") ? "in caves" : "in the sky")
                    + ".");
        if (e.level() != l) return AdminServer.Result.fail("He's in another world. Move him within his own world.");
        AdminServer.Result r = move(server, who, g, e, l, spot, kindOfSpot, said);
        return r.ok() ? new AdminServer.Result(true, r.lines(), List.of(who + " settled a " + name + " " + said + "."))
                : r;
    }

    private static AdminServer.Result move(MinecraftServer server, String who, Giant g, LivingEntity e, ServerLevel l,
            BlockPos at, String where, String said) {
        JsonObject o = new JsonObject();
        o.addProperty("dim", l.dimension().location().toString());
        o.addProperty("x", at.getX() + 0.5);
        o.addProperty("y", at.getY());
        o.addProperty("z", at.getZ() + 0.5);
        o.addProperty("where", where);
        o.addProperty("stay", true);
        AdminServer.Result r = FunGiants.act(server, g, e.getStringUUID(), "move", o);
        if (!r.ok()) return r;
        Fun.log(server, who, g.key(), "settle", "settled", AdminServer.nameOf(g) + " " + said);
        Fun.changed();
        return AdminServer.Result.said(List.of("The " + AdminServer.nameOf(g) + " lives " + said + " now (" + at.getX()
                + " " + at.getY() + " " + at.getZ() + ") and stays there."));
    }
}
