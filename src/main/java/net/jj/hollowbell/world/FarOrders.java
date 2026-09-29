package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Orders that move him, at any distance: "Come to me", "Go where I look", "Send him to a spot", /hollowbell goto
 * and come, /giants goto. He may be in the world (near or far), out of it as a sum (Away), or left standing in a
 * chunk nobody has loaded since. In the world he just goes; as a sum the trip is aimed there; in an unloaded chunk
 * the chunk is woken for a moment, he steps out of the world as a sum (the same step as when nobody is near, so
 * there is never a second of him), and the trip is aimed there. He comes back as himself when somebody is near.
 */
public final class FarOrders {
    private FarOrders() {}

    private static final TicketType<ChunkPos> FETCH = TicketType.create("hollowbell_fetch", java.util.Comparator.comparingLong(ChunkPos::toLong), 100);

    /** which one an order reaches, and where he is */
    public enum Kind { LOADED, AWAY, PARKED }
    public record Target(Kind kind, UUID id, double x, double z, @Nullable HollowbellEntity entity) {}

    /** the nearest one in this dimension, at any distance: in the world, out of it, or in land not loaded */
    public static @Nullable Target nearest(ServerLevel l, Vec3 from) {
        Target best = null;
        double bd = Double.MAX_VALUE;
        for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying())) {
            double d = Mth.square(h.getX() - from.x) + Mth.square(h.getZ() - from.z);
            if (d < bd) { bd = d; best = new Target(Kind.LOADED, h.getUUID(), h.getX(), h.getZ(), h); }
        }
        Away a = Away.get(l.getServer());
        String dim = l.dimension().location().toString();
        long now = l.getGameTime();
        for (Away.Rec r : a.all()) {
            if (!r.dim.equals(dim)) continue;
            Vec3 s = r.spot(now);
            double d = Mth.square(s.x - from.x) + Mth.square(s.z - from.z);
            if (d < bd) { bd = d; best = new Target(Kind.AWAY, r.id, s.x, s.z, null); }
        }
        for (var e : a.parked().entrySet()) {
            var p = e.getValue();
            if (!p.dim().equals(dim) || l.getEntity(e.getKey()) != null || a.get(e.getKey()) != null) continue;
            double d = Mth.square(p.x() - from.x) + Mth.square(p.z() - from.z);
            if (d < bd) { bd = d; best = new Target(Kind.PARKED, e.getKey(), p.x(), p.z(), null); }
        }
        return best;
    }

    /** every one in this dimension: /giants goto */
    public static List<Target> all(ServerLevel l) {
        List<Target> out = new ArrayList<>();
        for (HollowbellEntity h : l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()))
            out.add(new Target(Kind.LOADED, h.getUUID(), h.getX(), h.getZ(), h));
        Away a = Away.get(l.getServer());
        String dim = l.dimension().location().toString();
        long now = l.getGameTime();
        for (Away.Rec r : a.all()) if (r.dim.equals(dim)) { Vec3 s = r.spot(now); out.add(new Target(Kind.AWAY, r.id, s.x, s.z, null)); }
        for (var e : a.parked().entrySet())
            if (e.getValue().dim().equals(dim) && l.getEntity(e.getKey()) == null && a.get(e.getKey()) == null)
                out.add(new Target(Kind.PARKED, e.getKey(), e.getValue().x(), e.getValue().z(), null));
        return out;
    }

    /**
     * Sends this one there (to a player: comes to them, and keeps coming as they move). Returns what to tell the
     * one who gave the order, or null when there was nobody to send.
     */
    public static @Nullable Component order(ServerLevel l, Target t, Vec3 to, @Nullable Player follow, float windCost) {
        Away a = Away.get(l.getServer());
        double dist = Math.hypot(to.x - t.x(), to.z - t.z());
        switch (t.kind()) {
            case LOADED -> {
                HollowbellEntity h = t.entity();
                if (h == null || h.isRemoved()) return null;
                if (windCost > 0 && !h.mood().hasWind(windCost)) return Component.translatable("message.hollowbell.codex_winded");
                h.mood().spendWind(windCost);
                h.orderTo(new Vec3(to.x, h.groundAt(to.x, to.z), to.z), follow);
                return coming(dist, h.travelSpeed(), follow != null);
            }
            case AWAY -> {
                Away.Rec r = a.get(t.id());
                if (r == null) return null;
                if (!spendInBody(r.body, windCost)) return Component.translatable("message.hollowbell.codex_winded");
                a.order(l, t.id(), to, follow == null ? null : follow.getUUID());
                return coming(Math.hypot(r.toX - r.fromX, r.toZ - r.fromZ), r.speed, follow != null);
            }
            case PARKED -> {
                var p = a.parked().get(t.id());
                if (p == null) return null;
                fetches.add(new Fetch(t.id(), l.dimension().location().toString(), p.x(), p.z(), to, follow == null ? null : follow.getUUID(),
                        l.getGameTime() + 20 * 20, windCost));
                ChunkPos cp = new ChunkPos(Mth.floor(p.x()) >> 4, Mth.floor(p.z()) >> 4);
                l.getChunkSource().addRegionTicket(FETCH, cp, 2, cp);
                return coming(dist, p.speed() > 0 ? p.speed() : 0.2, follow != null);
            }
        }
        return null;
    }

    /** "The Hollowbell is coming. About N blocks, M minutes away." */
    public static Component coming(double dist, double speed, boolean toYou) {
        int n = net.jj.hollowbell.item.FinderItem.tens(dist);
        int mins = (int) Math.max(1, Math.ceil(dist / Math.max(0.01, speed) / 1200.0));
        return Component.translatable(toYou ? "message.hollowbell.coming" : "message.hollowbell.going", n, mins);
    }

    /** the wind an order costs, out of the saved body of one out of the world */
    private static boolean spendInBody(net.minecraft.nbt.CompoundTag body, float cost) {
        if (cost <= 0 || !HollowbellConfig.V.bookCosts) return true;
        var mood = body.getCompound("Mood");
        float wind = mood.contains("Wind") ? mood.getFloat("Wind") : 1f;
        if (wind < cost) return false;
        mood.putFloat("Wind", wind - cost);
        body.put("Mood", mood);
        return true;
    }

    // ------------------------------------------------------------------ fetching one from land nobody has loaded

    private record Fetch(UUID id, String dim, double x, double z, Vec3 to, @Nullable UUID follow, long until, float cost, boolean kill) {
        Fetch(UUID id, String dim, double x, double z, Vec3 to, @Nullable UUID follow, long until, float cost) { this(id, dim, x, z, to, follow, until, cost, false); }
    }
    private static final List<Fetch> fetches = new ArrayList<>();

    /** for the tests: is anybody still being fetched out of an unloaded chunk? */
    public static int fetching() { return fetches.size(); }

    /** every tick: the ones being fetched, once their chunk has brought them in, step out of the world and go */
    public static void tick(MinecraftServer server) {
        if (fetches.isEmpty()) return;
        for (Iterator<Fetch> it = fetches.iterator(); it.hasNext(); ) {
            Fetch f = it.next();
            ServerLevel l = null;
            for (ServerLevel s : server.getAllLevels()) if (s.dimension().location().toString().equals(f.dim())) l = s;
            if (l == null) { it.remove(); continue; }
            ChunkPos cp = new ChunkPos(Mth.floor(f.x()) >> 4, Mth.floor(f.z()) >> 4);
            Away a = Away.get(server);
            if (a.get(f.id()) != null) {                        // already out as a sum (something else did it)
                a.order(l, f.id(), f.to(), f.follow());
                it.remove();
                continue;
            }
            if (f.kill() && l.getEntity(f.id()) instanceof HollowbellEntity h && !h.isRemoved()) {
                kill(l, h);
                a.unpark(f.id());
                it.remove();
                continue;
            }
            if (l.getEntity(f.id()) instanceof HollowbellEntity h && !h.isRemoved()) {
                Player who = f.follow() == null ? null : server.getPlayerList().getPlayer(f.follow());
                h.mood().spendWind(f.cost());
                h.orderTo(new Vec3(f.to().x, h.groundAt(f.to().x, f.to().z), f.to().z), who);
                // nobody near him: out of the world he goes, with the order, in this same tick
                if (!someoneNear(l, h)) h.stepAside();
                a.unpark(f.id());
                it.remove();
                continue;
            }
            if (l.getGameTime() > f.until()) {
                HollowbellMod.LOG.info("Couldn't find the Hollowbell last seen at {}, {}; forgetting that spot", Mth.floor(f.x()), Mth.floor(f.z()));
                a.unpark(f.id());
                it.remove();
                continue;
            }
            if (l.getGameTime() % 40 == 0) l.getChunkSource().addRegionTicket(FETCH, cp, 2, cp);
        }
    }

    private static boolean someoneNear(ServerLevel l, HollowbellEntity h) {
        double r = Away.backRange(l.getServer(), h.bellScale());
        for (ServerPlayer p : l.players())
            if (!p.isSpectator() && Mth.square(p.getX() - h.getX()) + Mth.square(p.getZ() - h.getZ()) < r * r) return true;
        return false;
    }

    public static void forget() { fetches.clear(); }

    // ------------------------------------------------------------------ killing every one of him, wherever he is

    private static final TicketType<Integer> DYING = TicketType.create("hollowbell_dying", Integer::compare, HollowbellEntity.DEATH_LENGTH + 60);

    /**
     * /hollowbell kill and /giants kill: every one in the world (kept moving until his death is over, so nobody is
     * left half dead in a chunk that has stopped), every one out of the world (brought back and killed), and every
     * one left in land nobody has loaded (fetched and killed). Returns how many.
     */
    public static int killAll(MinecraftServer server) {
        int n = 0;
        Away a = Away.get(server);
        for (ServerLevel l : server.getAllLevels()) {
            for (HollowbellEntity h : new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()))) { kill(l, h); n++; }
            String dim = l.dimension().location().toString();
            for (Away.Rec r : a.all()) {
                if (!r.dim.equals(dim)) continue;
                HollowbellEntity h = a.bringBack(l, r);
                if (h != null) { kill(l, h); n++; }
            }
            for (var e : new ArrayList<>(a.parked().entrySet())) {
                var p = e.getValue();
                if (!p.dim().equals(dim) || l.getEntity(e.getKey()) != null) continue;
                fetches.add(new Fetch(e.getKey(), dim, p.x(), p.z(), Vec3.ZERO, null, l.getGameTime() + 20 * 20, 0f, true));
                ChunkPos cp = new ChunkPos(Mth.floor(p.x()) >> 4, Mth.floor(p.z()) >> 4);
                l.getChunkSource().addRegionTicket(FETCH, cp, 2, cp);
                n++;
            }
        }
        return n;
    }

    /** dead, and his chunk kept going until he's gone (his death takes a while) */
    public static void kill(ServerLevel l, HollowbellEntity h) {
        l.getChunkSource().addRegionTicket(DYING, new ChunkPos(h.blockPosition()), 2, h.getId());
        h.hurt(h.damageSources().genericKill(), Float.MAX_VALUE);
    }

    /** where a look goes when it hits nothing loaded: 512 blocks along it, on the ground the generator makes there */
    public static Vec3 farLook(ServerPlayer p) {
        Vec3 d = p.getViewVector(1f).multiply(1, 0, 1);
        if (d.lengthSqr() < 1e-6) d = new Vec3(0, 0, 1);
        Vec3 at = p.position().add(d.normalize().scale(512));
        ServerLevel l = p.serverLevel();
        var src = l.getChunkSource();
        int y = src.getGenerator().getBaseHeight(Mth.floor(at.x), Mth.floor(at.z), Heightmap.Types.MOTION_BLOCKING, l, src.randomState());
        return new Vec3(at.x, y, at.z);
    }
}
