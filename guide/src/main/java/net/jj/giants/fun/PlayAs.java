package net.jj.giants.fun;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Play as a giant") An admin takes a giant's controls, with no time limit: the Guide
 * brings them beside him, and a few ticks later his own mod seats them (funact playas, the same as his ride command,
 * but not held to the players' riding switches). His get-off key, Stop, him dying or leaving the world end it.
 */
public final class PlayAs {
    private PlayAs() {}

    /** who is playing as which giant */
    public record Playing(String kind, UUID giant, String label) {}

    static final Map<UUID, Playing> playing = new HashMap<>();
    /** off his controls twice in a row: done */
    private static final Map<UUID, Integer> offCount = new HashMap<>();

    private record Pending(UUID player, String kind, UUID giant, int[] ticks) {}

    private static final List<Pending> pending = new ArrayList<>();

    public static @Nullable Playing of(ServerPlayer p) {
        return playing.get(p.getUUID());
    }

    public static AdminServer.Result start(MinecraftServer server, ServerPlayer p, LivingEntity giant) {
        String kind = FunGiants.kindOf(giant);
        Giant g = kind == null ? null : AdminServer.giant(kind);
        if (g == null) return AdminServer.Result.fail("That isn't a giant.");
        String name = AdminServer.nameOf(g);
        // (Part 6 with Part 7) a paused giant can't be played: he stands frozen until the pause ends
        if (paused(server)) return AdminServer.Result.fail(PAUSED);
        if (!FunGiants.does(server, g).contains("playas"))
            return AdminServer.Result.fail(name + ": this version can't be played. Update the " + name + " mod.");
        // (watching first: back in their own game mode, then to him)
        if (Watch.watching(p)) Watch.stop(server, p);
        stopQuietly(server, p);
        double size = FunGiants.sizeOf(giant);
        if (p.level() != giant.level() || p.distanceTo(giant) > 48 + FunGiants.reach(kind, size)) {
            // beside him, out past his body, on the ground there if it is loaded
            Vec3 face = Vec3.directionFromRotation(0, giant.getYRot()).multiply(1, 0, 1);
            if (face.lengthSqr() < 1e-4) face = new Vec3(1, 0, 0);
            Vec3 side = new Vec3(-face.z, 0, face.x).normalize().scale(FunGiants.reach(kind, size) + 4);
            Vec3 to = giant.position().add(side);
            ServerLevel l = (ServerLevel) giant.level();
            double y = Spots.ground(l, Mth.floor(to.x), Mth.floor(to.z));
            p.teleportTo(l, to.x, Double.isNaN(y) ? giant.getY() + 2 : y, to.z, p.getYRot(), p.getXRot());
            p.fallDistance = 0;
        }
        pending.add(new Pending(p.getUUID(), kind, giant.getUUID(), new int[] {5}));
        return new AdminServer.Result(true, List.of("Taking you to the " + name + "'s controls…"), List.of());
    }

    /** a few ticks after arriving: his own mod seats them */
    private static void seat(MinecraftServer server, Pending pd) {
        ServerPlayer p = server.getPlayerList().getPlayer(pd.player());
        Giant g = AdminServer.giant(pd.kind());
        if (p == null || g == null) return;
        if (paused(server)) {
            p.displayClientMessage(Component.literal(PAUSED), false);
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("by", p.getStringUUID());
        o.addProperty("on", true);
        AdminServer.Result r = FunGiants.act(server, g, pd.giant().toString(), "playas", o);
        p.displayClientMessage(Component.literal(String.join(" ", r.lines())), false);
        if (!r.ok()) return;
        LivingEntity e = FunGiants.find(server, pd.giant());
        playing.put(p.getUUID(), new Playing(pd.kind(), pd.giant(), e == null ? AdminServer.nameOf(g)
                : FunGiants.label(server, e)));
        offCount.remove(p.getUUID());
        Fun.log(server, p.getGameProfile().getName(), pd.kind(), "playas", "play as",
                "playing as a " + AdminServer.nameOf(g));
        Fun.tell(server, p, p.getGameProfile().getName() + " is playing as a " + AdminServer.nameOf(g) + ".");
        Fun.changed();
    }

    public static AdminServer.Result stop(MinecraftServer server, ServerPlayer p) {
        Playing pl = playing.remove(p.getUUID());
        pending.removeIf(x -> x.player().equals(p.getUUID()));
        if (pl == null) return AdminServer.Result.fail("You aren't playing as a giant.");
        Fun.changed();
        Giant g = AdminServer.giant(pl.kind());
        if (g == null) return AdminServer.Result.said(List.of("Done."));
        JsonObject o = new JsonObject();
        o.addProperty("by", p.getStringUUID());
        o.addProperty("on", false);
        AdminServer.Result r = FunGiants.act(server, g, pl.giant().toString(), "playas", o);
        return r.ok() ? r : AdminServer.Result.said(List.of("You've stepped out of the " + AdminServer.nameOf(g)
                + "."));
    }

    /** off any giant they play, saying nothing (starting to watch, or another giant) */
    static void stopQuietly(MinecraftServer server, ServerPlayer p) {
        if (playing.containsKey(p.getUUID())) stop(server, p);
    }

    static void tick(MinecraftServer server) {
        if (!pending.isEmpty())
            for (Pending pd : List.copyOf(pending))
                if (--pd.ticks()[0] <= 0) {
                    pending.remove(pd);
                    seat(server, pd);
                }
        if (playing.isEmpty() || server.getTickCount() % 20 != 0) return;
        // (Part 6 with Part 7) pausing every giant sets down everyone playing one, back on their feet beside him
        if (paused(server)) {
            for (UUID id : List.copyOf(playing.keySet())) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) {
                    playing.remove(id);
                    continue;
                }
                AdminServer.Result r = stop(server, p);
                p.displayClientMessage(Component.literal("All giants were paused, so "
                        + String.join(" ", r.lines()).replaceFirst("^You", "you")), false);
            }
            return;
        }
        for (var e : List.copyOf(playing.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            LivingEntity g = FunGiants.find(server, e.getValue().giant());
            boolean off = p == null || g == null || g.isDeadOrDying() || !p.isPassenger();
            int n = off ? offCount.merge(e.getKey(), 1, Integer::sum) : 0;
            if (!off) offCount.remove(e.getKey());
            if (n >= 2) {
                playing.remove(e.getKey());
                offCount.remove(e.getKey());
                if (p != null && (g == null || g.isDeadOrDying()))
                    p.displayClientMessage(Component.literal("Your giant is gone."), true);
                Fun.changed();
            }
        }
    }

    /** the line for playing as a giant (or staging a fight) while every giant is paused */
    static final String PAUSED = "All giants are paused right now. Unpause them first (Server tools).";

    /** Part 6's Pause all giants is on */
    static boolean paused(MinecraftServer server) {
        return net.jj.giants.tools.Tools.on(server, "paused");
    }

    static void left(ServerPlayer p) {
        playing.remove(p.getUUID());
        pending.removeIf(x -> x.player().equals(p.getUUID()));
    }

    static void forget() {
        playing.clear();
        pending.clear();
        offCount.clear();
    }
}
