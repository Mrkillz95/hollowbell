package net.jj.mountain.innards;

import net.jj.mountain.ModBlocks;
import net.jj.mountain.ModEntities;
import net.jj.mountain.MountainMod;
import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The inside of the Mountain: a dimension of its own ("innards") with one fleshy room per Mountain, his heart hanging
 * in the middle of it. Swallowed players land here; hurting the heart hurts him, and enough of it makes him cough them up.
 * If he dies, is removed, or can't be found, everyone inside is put back where they came from.
 */
public final class Innards {
    private Innards() {}
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "innards"));
    /** room layout, relative to the room centre */
    public static final int FLOOR = -12, HEART_Y = -10;

    private record Visit(UUID mountain, ResourceKey<Level> dim, Vec3 back) {}
    private static final Map<UUID, Visit> visits = new HashMap<>();
    private static final Map<UUID, Integer> missing = new HashMap<>();

    public static boolean isInnards(Level l) { return l.dimension() == KEY; }
    public static boolean isInside(Entity e) { return e.level().dimension() == KEY; }

    public static int roomOf(UUID m) { return Math.floorMod((int) (m.getLeastSignificantBits() ^ (m.getMostSignificantBits() >>> 17)), 1024); }
    public static BlockPos roomCenter(int idx) { return new BlockPos((idx % 32) * 400 + 200, 100, (idx / 32) * 400 + 200); }

    // ------------------------------------------------------------------ in and out
    public static boolean swallow(ServerPlayer p, MountainEntity m) {
        MinecraftServer server = p.getServer();
        if (server == null) return false;
        ServerLevel in = server.getLevel(KEY);
        if (in == null) { MountainMod.LOG.warn("The innards dimension is missing; spitting the player out instead"); return false; }
        int idx = roomOf(m.getUUID());
        BlockPos c = roomCenter(idx);
        ensureRoom(in, idx);
        if (!visits.containsKey(p.getUUID()) || !isInside(p))
            visits.put(p.getUUID(), new Visit(m.getUUID(), p.level().dimension(), p.position()));
        p.stopRiding();
        p.teleportTo(in, c.getX() + 17.5, c.getY() + FLOOR + 1.05, c.getZ() + 0.5, 90f, 5f);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0));
        in.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_BLOCK_FALL, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2f), 0.4f);
        return true;
    }

    /** He coughs: everyone inside him comes flying back out of his mouth. */
    /** is anybody actually inside this one right now? A note left by somebody who logged off does not count. */
    public static boolean anyoneIn(MountainEntity m) {
        MinecraftServer server = m.getServer();
        if (server == null) return false;
        for (Map.Entry<UUID, Visit> e : visits.entrySet()) {
            if (!e.getValue().mountain().equals(m.getUUID())) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && isInside(p)) return true;
        }
        return false;
    }

    public static void ejectAll(MountainEntity m) {
        MinecraftServer server = m.getServer();
        if (server == null) return;
        for (Iterator<Map.Entry<UUID, Visit>> it = visits.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Visit> e = it.next();
            if (!e.getValue().mountain().equals(m.getUUID())) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            it.remove();
            if (p == null || !isInside(p)) continue;
            m.spitOut(p);
            p.displayClientMessage(Component.translatable("message.mountain_breathes.coughed"), true);
        }
    }

    private static void sendBack(ServerPlayer p, @Nullable Visit v) {
        MinecraftServer server = p.getServer();
        if (server == null) return;
        ServerLevel lvl = v != null ? server.getLevel(v.dim()) : null;
        if (lvl == null) lvl = server.overworld();
        Vec3 back = v != null ? v.back() : Vec3.atBottomCenterOf(lvl.getSharedSpawnPos());
        lvl.getChunk(Mth.floor(back.x) >> 4, Mth.floor(back.z) >> 4);
        int y = lvl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(back.x), Mth.floor(back.z));
        p.teleportTo(lvl, back.x, Math.max(y, back.y) + 0.1, back.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
    }

    /** the Mountain a player is inside, if any */
    public static @Nullable MountainEntity mountainOf(ServerPlayer p) {
        Visit v = visits.get(p.getUUID());
        return v == null || p.getServer() == null ? null : findMountain(p.getServer(), v.mountain());
    }

    public static List<ServerPlayer> playersInside(MountainEntity m) {
        List<ServerPlayer> out = new ArrayList<>();
        MinecraftServer server = m.getServer();
        if (server == null) return out;
        for (Map.Entry<UUID, Visit> e : visits.entrySet()) {
            if (!e.getValue().mountain().equals(m.getUUID())) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null && isInside(p)) out.add(p);
        }
        return out;
    }

    public static @Nullable MountainEntity findMountain(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e instanceof MountainEntity m) return m;
        }
        return null;
    }

    public static boolean heartHit(HeartEntity h, DamageSource src, float amount) {
        MinecraftServer server = h.getServer();
        if (server == null || h.mountainId() == null) return false;
        MountainEntity m = findMountain(server, h.mountainId());
        if (m == null || m.isDeadOrDying()) return false;
        m.hurtFromHeart(src, amount);
        ServerLevel in = (ServerLevel) h.level();
        in.sendParticles(ParticleTypes.DAMAGE_INDICATOR, h.getX(), h.getY() + 6, h.getZ(), 6, 2, 3, 2, 0.1);
        in.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, h.getX(), h.getY() + 6, h.getZ(), 10, 3, 4, 3, 0.0);
        return true;
    }

    // ------------------------------------------------------------------ every tick
    public static void serverTick(MinecraftServer server) {
        if (visits.isEmpty()) return;
        int t = server.getTickCount();
        Set<Integer> rooms = new HashSet<>();
        if (t % 10 == 0) {
            for (Iterator<Map.Entry<UUID, Visit>> it = visits.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<UUID, Visit> e = it.next();
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p == null) continue;                                    // logged off: they are still inside
                if (!isInside(p)) { it.remove(); continue; }                // died, or left some other way
                MountainEntity m = findMountain(server, e.getValue().mountain());
                if (m == null || m.isDeadOrDying()) {
                    int miss = missing.merge(e.getKey(), 1, Integer::sum);
                    if (m != null || miss > 6) { missing.remove(e.getKey()); it.remove(); sendBack(p, e.getValue()); }
                } else missing.remove(e.getKey());
            }
        }
        ServerLevel in = server.getLevel(KEY);
        if (in == null) return;
        for (ServerPlayer p : in.players()) {
            Visit v = visits.get(p.getUUID());
            if (v != null) rooms.add(roomOf(v.mountain()));
        }
        RandomSource r = in.getRandom();
        if (t % 100 == 0) sweepEmptyRooms(in, rooms);
        for (int idx : rooms) {
            BlockPos c = roomCenter(idx);
            if (t % 20 == 0) {
                for (ServerPlayer p : in.players()) {
                    Visit v = visits.get(p.getUUID());
                    if (v != null && roomOf(v.mountain()) == idx) { ensureHeart(in, idx, v.mountain()); break; }
                }
            }
            Vec3 heart = new Vec3(c.getX() + 0.5, c.getY() + HEART_Y + 6, c.getZ() + 0.5);
            if (t % 24 == 0) {
                in.playSound(null, heart.x, heart.y, heart.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(4f), 0.5f);
            }
            if (t % 96 == 48) {                                     // a hard beat throws you back
                in.playSound(null, heart.x, heart.y, heart.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2f), 0.5f);
                in.sendParticles(ParticleTypes.SONIC_BOOM, heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
                for (ServerPlayer p : in.players()) {
                    Vec3 d = p.position().subtract(heart);
                    double dist = d.length();
                    if (dist > 14 || p.isCreative() || p.isSpectator()) continue;
                    Vec3 push = new Vec3(d.x, 0, d.z).normalize().scale(1.4 * (1 - dist / 16)).add(0, 0.45, 0);
                    p.setDeltaMovement(p.getDeltaMovement().add(push));
                    p.hurtMarked = true;
                    p.hurt(in.damageSources().magic(), 1f);
                }
            }
            heartAttacks(in, idx, c, heart, t, r);
            if (t % 20 == 7) director(in, idx, c, r);
            if (t % 5 == 0) {
                double a = r.nextDouble() * Math.PI * 2;
                in.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, c.getX() + Math.cos(a) * r.nextDouble() * 26, c.getY() + 17, c.getZ() + Math.sin(a) * r.nextDouble() * 26, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Logged in inside him with no Mountain to be inside of (after a restart): back to their spawn. */
    public static void onJoin(ServerPlayer p) {
        if (isInside(p) && !visits.containsKey(p.getUUID())) {
            p.getServer().execute(() -> sendBack(p, null));
        }
    }

    // ------------------------------------------------------------------ the room
    private static final int RX = 44, RY = 30, SHELL = 6;

    public static void ensureHeart(ServerLevel in, int idx, UUID mountain) {
        BlockPos c = roomCenter(idx);
        List<HeartEntity> hs = in.getEntitiesOfClass(HeartEntity.class, new AABB(c).inflate(24));
        HeartEntity h = hs.isEmpty() ? null : hs.get(0);
        for (int i = 1; i < hs.size(); i++) hs.get(i).discard();
        if (h == null) {
            h = ModEntities.HEART.create(in);
            if (h == null) return;
            h.moveTo(c.getX() + 0.5, c.getY() + HEART_Y, c.getZ() + 0.5, 0f, 0f);
            h.finalizeSpawn(in, in.getCurrentDifficultyAt(c), MobSpawnType.EVENT, null);
            in.addFreshEntity(h);
        }
        h.link(mountain, idx);
    }

    public static void ensureRoom(ServerLevel in, int idx) {
        BlockPos c = roomCenter(idx);
        BlockPos marker = c.above(RY + SHELL + 3), marker2 = marker.above();
        in.getChunkAt(marker);
        if (!in.getBlockState(marker).is(Blocks.BEDROCK)) {
            long t0 = System.currentTimeMillis();
            build(in, c);
            in.setBlock(marker, Blocks.BEDROCK.defaultBlockState(), 2);
            MountainMod.LOG.info("Built the room inside mountain {} in {} ms", idx, System.currentTimeMillis() - t0);
        }
        if (!in.getBlockState(marker2).is(Blocks.BEDROCK)) {             // rooms from before 1.1 get the mounds and holes added
            buildMoundsAndHoles(in, c);
            in.setBlock(marker2, Blocks.BEDROCK.defaultBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------ the things that live in him
    /** where leeches crawl out: holes low in the wall, the same in every room */
    private static Vec3 hole(int i) {
        double a = i * Math.PI * 2 / 8 + 0.3;
        double rr = RX * Math.sqrt(1 - sq((FLOOR + 2.0) / RY)) - 1.5;
        return new Vec3(Math.cos(a) * rr, FLOOR + 1, Math.sin(a) * rr);
    }

    /** the raised flesh mounds with no goo on them, where you can stand clear of the acid */
    private static final double[][] MOUNDS = {
            {13, 4, 0.2, 2.6}, {-14, 5, 1.4, 3.0}, {3, 15, 2.5, 2.4}, {-4, -16, 4.1, 2.8}, {17, -9, 5.2, 2.2}, {-12, 12, 0.0, 2.5},
            {26, 10, 3.0, 3.2}, {-25, -6, 1.1, 2.9}, {8, -27, 2.2, 3.4}, {-9, 26, 4.6, 3.0}, {30, -20, 0.7, 2.6}, {-30, 19, 5.8, 2.8},
            {21, 24, 2.9, 2.3}, {-20, -24, 3.7, 3.1}, {0, 0, 1.8, 2.0}};

    static boolean onMound(Vec3 local) {
        for (double[] m : MOUNDS) if (Math.hypot(local.x - m[0], local.z - m[1]) < 3.8 + m[2] * 0.2 && local.y > FLOOR + 1.5) return true;
        return false;
    }

    private static void buildMoundsAndHoles(ServerLevel in, BlockPos c) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState top = Blocks.PINK_WOOL.defaultBlockState(), side = Blocks.PINK_TERRACOTTA.defaultBlockState(), scar = Blocks.MAGENTA_TERRACOTTA.defaultBlockState();
        for (double[] md : MOUNDS) {
            double rad = 3.8 + md[2] * 0.2, h = md[3];
            int ri = (int) Math.ceil(rad) + 1;
            for (int dx = -ri; dx <= ri; dx++) for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.hypot(dx, dz) / rad;
                if (d > 1) continue;
                int height = (int) Math.round(h * Math.sqrt(1 - d * d) + 0.4 * Math.sin(dx * 1.3 + dz * 0.7));
                int x = c.getX() + (int) md[0] + dx, z = c.getZ() + (int) md[1] + dz;
                for (int y = 0; y <= height; y++) {
                    m.set(x, c.getY() + FLOOR + 1 + y, z);
                    in.setBlock(m, y == height ? ((dx * 3 + dz * 5) % 7 == 0 ? scar : top) : side, flags);
                }
            }
        }
        BlockState ring = Blocks.MAGENTA_TERRACOTTA.defaultBlockState(), ring2 = Blocks.PURPLE_TERRACOTTA.defaultBlockState(), dark = Blocks.BLACK_CONCRETE.defaultBlockState();
        for (int i = 0; i < 8; i++) {
            Vec3 hp = hole(i);
            Vec3 out = new Vec3(hp.x, 0, hp.z).normalize();
            for (int dy = 0; dy <= 3; dy++) for (int s = -2; s <= 2; s++) {
                Vec3 side2 = new Vec3(-out.z, 0, out.x).scale(s);
                double rr = Math.hypot(s, dy - 1.5);
                if (rr > 2.3) continue;
                for (int k = 1; k <= 3; k++) {
                    Vec3 p = hp.add(out.scale(k)).add(side2).add(0, dy, 0);
                    m.set(c.getX() + Mth.floor(p.x), c.getY() + Mth.floor(p.y), c.getZ() + Mth.floor(p.z));
                    if (in.getBlockState(m).isAir()) continue;
                    in.setBlock(m, rr < 1.2 ? (k == 3 ? dark : Blocks.AIR.defaultBlockState()) : (rr < 1.8 ? ring : ring2), flags);
                }
            }
        }
    }

    private static AABB roomBox(BlockPos c) { return new AABB(c).inflate(RX + 2, RY + 2, RX + 2); }

    private static List<ServerPlayer> playersIn(ServerLevel in, int idx) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : in.players()) {
            Visit v = visits.get(p.getUUID());
            if (v != null && roomOf(v.mountain()) == idx && !p.isSpectator()) out.add(p);
        }
        return out;
    }

    private static final Map<Integer, Integer> roomAge = new HashMap<>();

    /**
     * Keeps a fight going in a room with people in it: tentacles come up out of the floor near them, leeches crawl
     * out of the holes in the wall, loose eyes drift down from the roof. More of each for more people, and more
     * often as he gets weaker.
     */
    private static void director(ServerLevel in, int idx, BlockPos c, RandomSource r) {
        List<ServerPlayer> ps = playersIn(in, idx);
        if (ps.isEmpty()) { roomAge.remove(idx); return; }
        int age = roomAge.merge(idx, 20, Integer::sum);
        if (age < 80) return;                                           // a moment to land and look round first
        int n = ps.size();
        AABB box = roomBox(c);
        int tent = in.getEntitiesOfClass(net.jj.mountain.entity.inside.GutTentacle.class, box).size();
        int leech = in.getEntitiesOfClass(net.jj.mountain.entity.inside.GutLeech.class, box).size();
        int eyes = in.getEntitiesOfClass(net.jj.mountain.entity.inside.WatcherEye.class, box).size();
        MountainEntity m = null;
        Visit v = visits.get(ps.get(0).getUUID());
        if (v != null) m = findMountain(in.getServer(), v.mountain());
        float weak = m == null ? 1f : 1f + 0.8f * (1f - m.healthNow() / Math.max(1f, m.healthMax()));
        if (tent < Math.min(6, 2 + n) && r.nextFloat() < 0.16f * weak) {
            ServerPlayer p = ps.get(r.nextInt(n));
            for (int tries = 0; tries < 8; tries++) {
                double a = r.nextDouble() * Math.PI * 2, d = 4 + r.nextDouble() * 7;
                double x = p.getX() + Math.cos(a) * d, z = p.getZ() + Math.sin(a) * d;
                Vec3 local = new Vec3(x - c.getX() - 0.5, FLOOR + 2, z - c.getZ() - 0.5);
                if (Math.hypot(local.x, local.z) < 8 || Math.hypot(local.x, local.z) > RX * 0.78 || onMound(local)) continue;
                spawn(in, net.jj.mountain.ModEntities.GUT_TENTACLE, new Vec3(Math.floor(x) + 0.5, c.getY() + FLOOR + 1, Math.floor(z) + 0.5));
                break;
            }
        }
        if (leech < Math.min(10, 3 + 2 * n) && r.nextFloat() < 0.18f * weak) {
            Vec3 hp = hole(r.nextInt(8));
            spawn(in, net.jj.mountain.ModEntities.GUT_LEECH, new Vec3(c.getX() + 0.5 + hp.x, c.getY() + FLOOR + 1.2, c.getZ() + 0.5 + hp.z));
        }
        if (eyes < Math.min(4, 1 + n) && r.nextFloat() < 0.08f * weak) {
            double a = r.nextDouble() * Math.PI * 2;
            spawn(in, net.jj.mountain.ModEntities.WATCHER, new Vec3(c.getX() + 0.5 + Math.cos(a) * 14, c.getY() + 8, c.getZ() + 0.5 + Math.sin(a) * 14));
            in.playSound(null, c.getX() + Math.cos(a) * 14, c.getY() + 8, c.getZ() + Math.sin(a) * 14, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2f), 0.5f);
        }
    }

    private static void spawn(ServerLevel in, net.minecraft.world.entity.EntityType<? extends net.minecraft.world.entity.Mob> type, Vec3 at) {
        var e = type.create(in);
        if (e == null) return;
        e.moveTo(at.x, at.y, at.z, in.getRandom().nextFloat() * 360f, 0f);
        e.finalizeSpawn(in, in.getCurrentDifficultyAt(BlockPos.containing(at)), MobSpawnType.EVENT, null);
        in.addFreshEntity(e);
    }

    /** the heart squeezes hard every so often: a wave of blood rolls over the floor, and leeches come out with it */
    private static void heartAttacks(ServerLevel in, int idx, BlockPos c, Vec3 heart, int t, RandomSource r) {
        List<HeartEntity> hs = in.getEntitiesOfClass(HeartEntity.class, new AABB(c).inflate(24));
        if (hs.isEmpty()) return;
        HeartEntity h = hs.get(0);
        int cycle = t % 320;
        if (cycle >= 260 && cycle < 300) {
            h.setBurst(cycle - 259);
            if (cycle == 260) in.playSound(null, heart.x, heart.y, heart.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(3f), 0.4f);
            if (cycle % 4 == 0) in.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.55f, 0f, 0.05f), 3f),
                    heart.x, heart.y, heart.z, 12, 3, 4, 3, 0);
        } else if (cycle == 300) {
            h.setBurst(0);
            in.playSound(null, heart.x, heart.y, heart.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(2.5f), 0.5f);
            in.playSound(null, heart.x, heart.y, heart.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, net.jj.mountain.ModSounds.vol(5f), 0.3f);
            for (int k = 0; k < 90; k++) {
                double a = k * Math.PI * 2 / 90, d = 3 + r.nextDouble() * 14;
                in.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.6f, 0.02f, 0.05f), 2.5f),
                        heart.x + Math.cos(a) * d, c.getY() + FLOOR + 1.3, heart.z + Math.sin(a) * d, 2, 0.4, 0.2, 0.4, 0);
            }
            for (ServerPlayer p : playersIn(in, idx)) {
                Vec3 local = p.position().subtract(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
                if (p.isCreative() || Math.hypot(local.x, local.z) > 18 || onMound(local) || !p.onGround()) continue;    // up on a mound, or jumping, it misses you
                p.hurt(in.damageSources().magic(), 4f);
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2));
                Vec3 out = new Vec3(local.x, 0, local.z);
                if (out.lengthSqr() > 1e-3) p.setDeltaMovement(p.getDeltaMovement().add(out.normalize().scale(0.8)).add(0, 0.4, 0));
                p.hurtMarked = true;
            }
            for (int k = 0; k < 2; k++) {
                double a = r.nextDouble() * Math.PI * 2;
                spawn(in, net.jj.mountain.ModEntities.GUT_LEECH, new Vec3(heart.x + Math.cos(a) * 7, c.getY() + FLOOR + 1.2, heart.z + Math.sin(a) * 7));
            }
        } else if (h.burst() != 0) h.setBurst(0);
    }

    /** nobody left in a room: its creatures go back into the walls */
    private static void sweepEmptyRooms(ServerLevel in, Set<Integer> occupied) {
        for (net.minecraft.world.entity.Entity e : in.getAllEntities()) {
            if (!(e instanceof net.jj.mountain.entity.inside.InsideMob)) continue;
            int ix = Math.floorDiv((int) e.getX(), 400), iz = Math.floorDiv((int) e.getZ(), 400);
            int idx = ix + iz * 32;
            if (!occupied.contains(idx)) e.discard();
        }
    }

    private static double noise(double x, double y, double z) {
        return Math.sin(x * 0.21 + z * 0.13) + Math.sin(y * 0.19 - x * 0.11) + Math.sin(z * 0.23 + y * 0.09) + 0.5 * Math.sin((x + y - z) * 0.07);
    }

    private static void build(ServerLevel in, BlockPos c) {
        RandomSource r = RandomSource.create(c.asLong());
        BlockState[] flesh = {Blocks.CHERRY_PLANKS.defaultBlockState(), Blocks.PINK_TERRACOTTA.defaultBlockState(), Blocks.PINK_WOOL.defaultBlockState(),
                Blocks.WHITE_TERRACOTTA.defaultBlockState()};
        BlockState bruise = Blocks.MAGENTA_TERRACOTTA.defaultBlockState(), bruise2 = Blocks.PURPLE_TERRACOTTA.defaultBlockState();
        BlockState vein = Blocks.PINK_CONCRETE.defaultBlockState(), deep = Blocks.TERRACOTTA.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        double ox = RX + SHELL, oy = RY + SHELL;
        for (int dx = -(int) ox; dx <= ox; dx++) for (int dz = -(int) ox; dz <= ox; dz++) for (int dy = -(int) oy; dy <= oy; dy++) {
            double outer = sq(dx / ox) + sq(dy / oy) + sq(dz / ox);
            if (outer > 1) continue;
            double inner = sq(dx / (double) RX) + sq(dy / (double) RY) + sq(dz / (double) RX);
            boolean floor = dy <= FLOOR;
            if (inner < 1 && !floor) continue;                                        // the hollow
            m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            double n = noise(dx, dy, dz);
            BlockState st;
            if (inner < 0.75 && floor) st = deep;
            else if (Math.abs(n) < 0.12) st = vein;
            else if (n > 1.4) st = bruise; else if (n < -1.5) st = bruise2;
            else st = flesh[(int) Math.floorMod((long) (dx * 7 + dy * 3 + dz * 5), 11L) % 4];
            in.setBlock(m, st, flags);
        }
        // his goo lies in pools rather than over every inch of it: there is dry ground to fight on between them
        int fy = c.getY() + FLOOR + 1;
        for (int dx = -RX; dx <= RX; dx++) for (int dz = -RX; dz <= RX; dz++) {
            double pool = Math.sin(dx * 0.11) * Math.cos(dz * 0.13) + 0.55 * Math.sin((dx + dz) * 0.07) + 0.35 * Math.cos((dx - dz) * 0.19);
            if (pool < 0.15) continue;                                            // dry
            m.set(c.getX() + dx, fy, c.getZ() + dz);
            if (in.getBlockState(m).isAir() && in.getBlockState(m.below()).isFaceSturdy(in, m.below(), net.minecraft.core.Direction.UP))
                in.setBlock(m, ModBlocks.GOO.defaultBlockState(), flags);
        }
        // ribs arching over
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        for (int rx = -(RX - 6); rx <= RX - 6; rx += 8) {
            for (double a = -0.2; a <= Math.PI + 0.2; a += 0.02) {
                double yz = Math.sqrt(Math.max(0, 1 - sq(rx / (double) RX)));
                for (double in2 = 0; in2 < 2.5; in2 += 1) {
                    int y = (int) Math.round(Math.sin(a) * (RY * yz - in2)), z = (int) Math.round(Math.cos(a) * (RX * yz - in2));
                    if (y < FLOOR + 1) continue;
                    m.set(c.getX() + rx, c.getY() + y, c.getZ() + z); in.setBlock(m, bone, flags);
                    m.set(c.getX() + rx + 1, c.getY() + y, c.getZ() + z); in.setBlock(m, bone, flags);
                }
            }
        }
        // eyes set into the walls, all looking at the middle
        for (int e = 0; e < 34; e++) {
            double th = r.nextDouble() * Math.PI * 2, ph = -0.25 + r.nextDouble() * 1.05;
            double ex = Math.cos(th) * Math.cos(ph) * RX, ey = Math.sin(ph) * RY, ez = Math.sin(th) * Math.cos(ph) * RX;
            if (ey < FLOOR + 3) continue;
            int rad = 2 + r.nextInt(3);
            Vec3 center = new Vec3(ex, ey, ez).scale(1.02);
            Vec3 in2c = center.scale(-1).normalize();
            for (int x = -rad; x <= rad; x++) for (int y = -rad; y <= rad; y++) for (int z = -rad; z <= rad; z++) {
                if (x * x + y * y + z * z > rad * rad + 0.5) continue;
                Vec3 q = new Vec3(x, y, z);
                double along = q.dot(in2c);
                BlockState st = along > rad * 0.75 ? Blocks.BLACK_CONCRETE.defaultBlockState()
                        : along > rad * 0.35 ? Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState() : Blocks.WHITE_CONCRETE.defaultBlockState();
                m.set(c.getX() + Mth.floor(center.x + x), c.getY() + Mth.floor(center.y + y), c.getZ() + Mth.floor(center.z + z));
                in.setBlock(m, st, flags);
            }
        }
        // glowing veins crawling over the walls, and a few pustules of light
        BlockState glow = Blocks.CRYING_OBSIDIAN.defaultBlockState(), pus = Blocks.SHROOMLIGHT.defaultBlockState();
        for (int v = 0; v < 30; v++) {
            double th = r.nextDouble() * Math.PI * 2, ph = r.nextDouble() * 1.2 - 0.3;
            for (int s = 0; s < 60; s++) {
                th += (r.nextDouble() - 0.5) * 0.12; ph += (r.nextDouble() - 0.5) * 0.12;
                ph = Mth.clamp(ph, -0.4, 1.4);
                int x = (int) Math.round(Math.cos(th) * Math.cos(ph) * (RX + 0.6)), y = (int) Math.round(Math.sin(ph) * (RY + 0.6)), z = (int) Math.round(Math.sin(th) * Math.cos(ph) * (RX + 0.6));
                if (y <= FLOOR) continue;
                m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                if (!in.getBlockState(m).isAir()) in.setBlock(m, glow, flags);
            }
        }
        for (int q = 0; q < 34; q++) {
            double th = r.nextDouble() * Math.PI * 2, ph = r.nextDouble() * 1.1 - 0.2;
            m.set(c.getX() + (int) Math.round(Math.cos(th) * Math.cos(ph) * (RX + 0.5)), c.getY() + (int) Math.round(Math.sin(ph) * (RY + 0.5)),
                    c.getZ() + (int) Math.round(Math.sin(th) * Math.cos(ph) * (RX + 0.5)));
            in.setBlock(m, pus, flags);
        }
        ledges(in, m, c, r, flags);
        // the great vessels the heart hangs from
        BlockState artery = Blocks.RED_TERRACOTTA.defaultBlockState(), artery2 = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        vessel(in, m, c, new Vec3(0, HEART_Y + 10, 0), new Vec3(0, RY + 1, 0), 2.6, artery, flags);
        vessel(in, m, c, new Vec3(2, HEART_Y + 9, 1), new Vec3(10, RY - 2, 6), 1.6, artery2, flags);
        vessel(in, m, c, new Vec3(-2, HEART_Y + 9, -1), new Vec3(-9, RY - 2, -7), 1.6, artery2, flags);
        vessel(in, m, c, new Vec3(1, HEART_Y + 9, -2), new Vec3(4, RY, -11), 1.2, Blocks.BLUE_TERRACOTTA.defaultBlockState(), flags);
    }

    /**
     * Somewhere to stand that isn't the floor. A shelf of gristle runs round the wall half way up with gaps in
     * it, stairs of bone climb to it from the ground at four places, and a scattering of stumps and slabs sits
     * out over the pools so a fight in here is footwork rather than wading.
     */
    private static void ledges(ServerLevel in, BlockPos.MutableBlockPos m, BlockPos c, RandomSource r, int flags) {
        BlockState shelf = Blocks.BONE_BLOCK.defaultBlockState();
        BlockState lip = Blocks.CALCITE.defaultBlockState();
        BlockState stump = Blocks.PINK_TERRACOTTA.defaultBlockState(), stumpTop = Blocks.PINK_WOOL.defaultBlockState();

        // the shelf: two rings of it, at a third and two thirds of the way up, each broken into arcs
        for (int band = 0; band < 2; band++) {
            int y = FLOOR + (band == 0 ? 10 : 20);
            double rr = RX * Math.sqrt(Math.max(0.05, 1 - sq(y / (double) RY))) - 2.0;
            if (rr < 8) continue;
            for (double a = 0; a < Math.PI * 2; a += 0.012) {
                double gate = Math.sin(a * 3 + band * 1.7);
                if (gate > 0.55) continue;                                  // the gaps you have to jump
                for (int w = 0; w < 4; w++) {
                    double rad = rr - w;
                    int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
                    m.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                    in.setBlock(m, w == 3 ? lip : shelf, flags);
                    m.set(c.getX() + x, c.getY() + y + 1, c.getZ() + z);
                    if (!in.getBlockState(m).isAir()) in.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    m.set(c.getX() + x, c.getY() + y + 2, c.getZ() + z);
                    if (!in.getBlockState(m).isAir()) in.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }

        // stairs up to the lower shelf, at four places round it
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + 0.4;
            double rr = RX * Math.sqrt(Math.max(0.05, 1 - sq((FLOOR + 10) / (double) RY))) - 2.0;
            for (int step = 0; step <= 10; step++) {
                double rad = rr - 2 + step * 1.15;
                int y = FLOOR + 1 + step;
                for (int sd = -2; sd <= 2; sd++) {
                    double aa = a + sd * 0.035;
                    int x = (int) Math.round(Math.cos(aa) * rad), z = (int) Math.round(Math.sin(aa) * rad);
                    for (int d = 0; d < 2; d++) {
                        m.set(c.getX() + x, c.getY() + y - d, c.getZ() + z);
                        in.setBlock(m, d == 0 ? lip : shelf, flags);
                    }
                    for (int up = 1; up <= 3; up++) {
                        m.set(c.getX() + x, c.getY() + y + up, c.getZ() + z);
                        if (!in.getBlockState(m).isAir()) in.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                    }
                }
            }
        }

        // stumps standing out of the pools, at heights you can hop between
        for (int q = 0; q < 26; q++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 6 + r.nextDouble() * (RX - 12);
            int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
            int h = 2 + r.nextInt(5), wide = 2 + r.nextInt(2);
            for (int dx = -wide; dx <= wide; dx++) for (int dz = -wide; dz <= wide; dz++) {
                if (dx * dx + dz * dz > wide * wide + 1) continue;
                for (int y = 0; y <= h; y++) {
                    m.set(c.getX() + x + dx, c.getY() + FLOOR + 1 + y, c.getZ() + z + dz);
                    in.setBlock(m, y == h ? stumpTop : stump, flags);
                }
                for (int up = 1; up <= 3; up++) {
                    m.set(c.getX() + x + dx, c.getY() + FLOOR + 1 + h + up, c.getZ() + z + dz);
                    if (!in.getBlockState(m).isAir()) in.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
                }
            }
        }

        // cords hanging from the roof, close enough to the shelves to catch hold of
        BlockState cord = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        for (int q = 0; q < 14; q++) {
            double a = r.nextDouble() * Math.PI * 2, rad = 8 + r.nextDouble() * (RX - 14);
            int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
            int top = (int) Math.round(RY * Math.sqrt(Math.max(0, 1 - sq(rad / (double) RX)))) - 1;
            int len = 6 + r.nextInt(14);
            for (int y = 0; y < len; y++) {
                m.set(c.getX() + x, c.getY() + top - y, c.getZ() + z);
                if (in.getBlockState(m).isAir()) in.setBlock(m, cord, flags);
            }
        }
    }

    private static void vessel(ServerLevel in, BlockPos.MutableBlockPos m, BlockPos c, Vec3 a, Vec3 b, double r, BlockState st, int flags) {
        int n = (int) (a.distanceTo(b) * 2) + 1;
        int ri = Mth.ceil(r);
        for (int i = 0; i <= n; i++) {
            Vec3 p = a.lerp(b, i / (double) n);
            for (int x = -ri; x <= ri; x++) for (int y = -ri; y <= ri; y++) for (int z = -ri; z <= ri; z++) {
                if (x * x + y * y + z * z > r * r + 0.3) continue;
                m.set(c.getX() + Mth.floor(p.x + x), c.getY() + Mth.floor(p.y + y), c.getZ() + Mth.floor(p.z + z));
                if (in.getBlockState(m).isAir()) in.setBlock(m, st, flags);
            }
        }
    }

    private static double sq(double v) { return v * v; }
}
