package net.jj.hollowbell.world;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Out of the world, like the Mountain. When nobody is anywhere near him, he's written down in full (health, pods,
 * mood, the lot) and taken out of the game. From then on he's a line on a map: where he set off from, where he's
 * going, when he left and how fast he drifts. The book and /hollowbell where work out where he ought to be from
 * that, and the moment anybody comes near that spot he's put back there, whole, still going the same way.
 */
public final class Away extends SavedData {
    public static final class Rec {
        public UUID id;
        public CompoundTag body;
        public String dim = "minecraft:overworld";
        public double fromX, fromZ, toX, toZ, speed, lift;
        public long start;
        public boolean going, stay;
        public float scale, hp, hpMax;
        public int variant;

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", id);
            t.put("Body", body);
            t.putString("Dim", dim);
            t.putDouble("FromX", fromX); t.putDouble("FromZ", fromZ);
            t.putDouble("ToX", toX); t.putDouble("ToZ", toZ);
            t.putDouble("Speed", speed); t.putDouble("Lift", lift);
            t.putLong("Start", start);
            t.putBoolean("Going", going); t.putBoolean("Stay", stay);
            t.putFloat("Scale", scale); t.putFloat("Hp", hp); t.putFloat("HpMax", hpMax);
            t.putInt("Variant", variant);
            return t;
        }

        static Rec load(CompoundTag t) {
            Rec r = new Rec();
            r.id = t.getUUID("Id");
            r.body = t.getCompound("Body");
            r.dim = t.getString("Dim");
            r.fromX = t.getDouble("FromX"); r.fromZ = t.getDouble("FromZ");
            r.toX = t.getDouble("ToX"); r.toZ = t.getDouble("ToZ");
            r.speed = t.getDouble("Speed"); r.lift = t.getDouble("Lift");
            r.start = t.getLong("Start");
            r.going = t.getBoolean("Going"); r.stay = t.getBoolean("Stay");
            r.scale = t.getFloat("Scale"); r.hp = t.getFloat("Hp"); r.hpMax = t.getFloat("HpMax");
            r.variant = t.getInt("Variant");
            return r;
        }

        /** where he ought to be by now: how long he's been going, times how fast he goes */
        public Vec3 spot(long now) {
            if (!going) return new Vec3(fromX, 0, fromZ);
            double dx = toX - fromX, dz = toZ - fromZ, total = Math.sqrt(dx * dx + dz * dz);
            if (total < 1) return new Vec3(toX, 0, toZ);
            double f = Math.min(1.0, Math.max(0, now - start) * speed / total);
            return new Vec3(fromX + dx * f, 0, fromZ + dz * f);
        }

        /** whole minutes of going he has left */
        public int minutesLeft(long now) {
            if (!going || speed <= 0) return 0;
            double total = Math.hypot(toX - fromX, toZ - fromZ);
            double left = Math.max(0, total - Math.max(0, now - start) * speed);
            return (int) Math.ceil(left / speed / 1200.0);
        }

        /** he's got there: the sum stops and he's left floating at the spot */
        void settle(long now) {
            if (!going) return;
            Vec3 at = spot(now);
            if (Math.hypot(toX - at.x, toZ - at.z) > 0.5) return;
            fromX = at.x; fromZ = at.z; going = false;
        }
    }

    private final Map<UUID, Rec> recs = new LinkedHashMap<>();
    private static final SavedData.Factory<Away> FACTORY = new SavedData.Factory<>(Away::new, Away::load, null);

    public static Away get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "hollowbell_away");
    }

    /** the test world has nobody in it, so every Hollowbell in it would step straight out. The away tests do it themselves. */
    private static final boolean IN_TESTS = System.getProperty("fabric-api.gametest") != null;

    /**
     * How far the nearest player has to be before he steps out of the world. At least the edge of what the game
     * keeps running, and never while you could still see his boss bars. A number in the settings wins.
     */
    public static double awayRange(@Nullable MinecraftServer server, float scale) {
        int set = HollowbellConfig.V.awayBlocks;
        if (set > 0) return Math.max(96, set);
        int sim = server == null ? 10 : Math.max(2, server.getPlayerList().getSimulationDistance());
        return Math.max(sim * 16 + 64, Math.max(300 * Math.max(0.15f, scale) + 140, HollowbellEntity.barRange(scale) + 16));
    }

    /** and how close somebody has to come to where the sum says he is before he's put back */
    public static double backRange(@Nullable MinecraftServer server, float scale) { return Math.max(64, awayRange(server, scale) - 48); }

    public List<Rec> all() { return new ArrayList<>(recs.values()); }
    public int count() { return recs.size(); }
    public @Nullable Rec get(UUID id) { return recs.get(id); }
    public void forget(UUID id) { if (recs.remove(id) != null) setDirty(); }
    public void forgetAll() { if (!recs.isEmpty()) { recs.clear(); setDirty(); } }

    /** the nearest one out there in this dimension, from this spot */
    public @Nullable Rec nearest(ServerLevel l, Vec3 from) {
        String dim = l.dimension().location().toString();
        long now = l.getGameTime();
        Rec best = null; double bd = Double.MAX_VALUE;
        for (Rec r : recs.values()) {
            if (!r.dim.equals(dim)) continue;
            Vec3 s = r.spot(now);
            double d = Mth.square(s.x - from.x) + Mth.square(s.z - from.z);
            if (d < bd) { bd = d; best = r; }
        }
        return best;
    }

    /** write him down and take him out of the game */
    public void takeAway(ServerLevel l, HollowbellEntity h, CompoundTag body, @Nullable Vec3 dest, double speed, double lift) {
        Rec r = new Rec();
        r.id = h.getUUID();
        r.body = body;
        r.dim = l.dimension().location().toString();
        r.fromX = h.getX(); r.fromZ = h.getZ();
        r.start = l.getGameTime();
        r.speed = Math.max(0.01, speed);
        r.lift = Math.max(0, lift);
        r.stay = h.staying();
        r.scale = h.bellScale(); r.hp = h.healthNow(); r.hpMax = h.healthMax(); r.variant = h.variant();
        if (dest != null && Math.hypot(dest.x - h.getX(), dest.z - h.getZ()) > 8) { r.going = true; r.toX = dest.x; r.toZ = dest.z; }
        recs.put(r.id, r);
        setDirty();
        HollowbellMod.LOG.info("A Hollowbell has stepped out of the world at {}, {}{}", Mth.floor(h.getX()), Mth.floor(h.getZ()),
                r.going ? " going to " + Mth.floor(r.toX) + ", " + Mth.floor(r.toZ) : "");
    }

    /** the book reaching him while he's a sum: he turns where the sum says he is and sets off for the new spot */
    public boolean send(ServerLevel l, UUID id, Vec3 to) {
        Rec r = recs.get(id);
        if (r == null) return false;
        long now = l.getGameTime();
        Vec3 at = r.spot(now);
        r.fromX = at.x; r.fromZ = at.z; r.start = now;
        r.toX = to.x; r.toZ = to.z;
        r.going = Math.hypot(to.x - at.x, to.z - at.z) > 8;
        r.stay = false;
        setDirty();
        return true;
    }

    /** stop where you are: the sum stops and he's left out there (or, again, let him go on his way) */
    public boolean stay(ServerLevel l, UUID id, boolean on) {
        Rec r = recs.get(id);
        if (r == null) return false;
        long now = l.getGameTime();
        Vec3 at = r.spot(now);
        r.fromX = at.x; r.fromZ = at.z; r.start = now;
        if (on) r.going = false;
        r.stay = on;
        setDirty();
        return true;
    }

    // ------------------------------------------------------------------ once a second

    private static int sweepTick;

    /**
     * Once a second: every Hollowbell still in the world with nobody near him steps out, and every one out there
     * that somebody has come near is put back. This runs off the server's own clock rather than his, so one the
     * game has stopped ticking is still found.
     */
    public static void tick(MinecraftServer server) {
        if (--sweepTick > 0) return;
        sweepTick = 20;
        if (IN_TESTS) { get(server).bringBackNear(server); return; }
        if (!HollowbellConfig.V.offscreenTravel) { get(server).bringBackAll(server); return; }
        for (ServerLevel l : server.getAllLevels()) {
            List<HollowbellEntity> here = new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && !e.isDeadOrDying()));
            for (HollowbellEntity h : here) h.stepAsideIfAlone(20);
        }
        get(server).bringBackNear(server);
    }

    private void bringBackNear(MinecraftServer server) {
        if (recs.isEmpty()) return;
        for (Rec r : new ArrayList<>(recs.values())) {
            ServerLevel l = level(server, r.dim);
            if (l == null) continue;
            long now = l.getGameTime();
            r.settle(now);
            Vec3 s = r.spot(now);
            double back = backRange(server, r.scale);
            for (ServerPlayer p : l.players()) {
                if (p.isSpectator()) continue;
                if (Mth.square(p.getX() - s.x) + Mth.square(p.getZ() - s.z) < back * back) { bringBack(l, r); break; }
            }
        }
    }

    /** the setting was turned off: every one out there comes back where the sum says he is */
    private void bringBackAll(MinecraftServer server) {
        for (Rec r : new ArrayList<>(recs.values())) {
            ServerLevel l = level(server, r.dim);
            if (l != null) bringBack(l, r);
        }
    }

    private static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation key = ResourceLocation.tryParse(dim);
        if (key == null) return server.overworld();
        ServerLevel l = server.getLevel(ResourceKey.create(Registries.DIMENSION, key));
        return l != null ? l : server.overworld();
    }

    /** somebody has come to where the sum says he is: he's put back there, exactly as he was */
    public @Nullable HollowbellEntity bringBack(ServerLevel l, Rec r) {
        recs.remove(r.id);
        setDirty();
        HollowbellEntity h = ModEntities.HOLLOWBELL.create(l);
        if (h == null) return null;
        Vec3 s = r.spot(l.getGameTime());
        int bx = Mth.floor(s.x), bz = Mth.floor(s.z);
        l.getChunk(bx >> 4, bz >> 4);
        h.load(r.body);
        if (l.getEntity(h.getUUID()) != null) h.setUUID(UUID.randomUUID());   // never two of the same
        int ground = l.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
        double y = Mth.clamp(ground + r.lift, l.getMinBuildHeight() + 1, l.getMaxBuildHeight() - 1);
        h.moveTo(s.x, y, s.z, h.getYRot(), 0f);
        h.backFromAway(r.going ? new Vec3(r.toX, ground, r.toZ) : null, r.stay);
        l.addFreshEntity(h);
        HollowbellMod.LOG.info("A Hollowbell is back in the world at {}, {}, {}", bx, Mth.floor(y), bz);
        return h;
    }

    // ------------------------------------------------------------------ saving

    private static Away load(CompoundTag tag, HolderLookup.Provider p) {
        Away a = new Away();
        ListTag l = tag.getList("Away", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            Rec r = Rec.load(l.getCompound(i));
            a.recs.put(r.id, r);
        }
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider p) {
        ListTag l = new ListTag();
        for (Rec r : recs.values()) l.add(r.save());
        tag.put("Away", l);
        return tag;
    }
}
