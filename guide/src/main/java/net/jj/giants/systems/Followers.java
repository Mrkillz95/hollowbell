package net.jj.giants.systems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.tools.Tools;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.4) Mob followers: a wild giant gathers a band of creatures of his own kind that keep near
 * him and go for whatever he fights; a new one rises beside him every so often (only with a player near, never in
 * peaceful) until he has as many as he may. They are ordinary creatures with a mark ("jj_giants.follower=<his id>"):
 * his blows and his band's don't hurt them, theirs don't hurt him. When he falls, is owned, goes away, or the system
 * goes off, they crumble away. Only loaded land is looked at.
 */
public final class Followers {
    private Followers() {}

    public static final String MARK = "jj_giants.follower", OF = "jj_giants.follower=", KIND = "jj_giants.followerkind=";
    /** the most followers standing in the whole world at once */
    static final int MOST = 160;

    /** each giant's own kind of follower (another kind: zombies) */
    static final Map<String, EntityType<? extends Mob>> TYPES = Map.of(
            "pitchgut", EntityType.MAGMA_CUBE, "furrowmaw", EntityType.HUSK, "cerberus", EntityType.WITHER_SKELETON,
            "hollowbell", EntityType.PHANTOM, "lanternwillow", EntityType.SLIME, "wreckback", EntityType.DROWNED,
            "swarmforge", EntityType.VINDICATOR, "gloamwick", EntityType.VEX);

    public static EntityType<? extends Mob> typeOf(String kind) {
        return TYPES.getOrDefault(kind, EntityType.ZOMBIE);
    }

    /** a follower's giant (his id), or null */
    public static @Nullable String followerOf(@Nullable Entity e) {
        if (e == null) return null;
        for (String t : e.getTags()) if (t.startsWith(OF)) return t.substring(OF.length());
        return null;
    }

    static @Nullable String kindOfFollower(Entity e) {
        for (String t : e.getTags()) if (t.startsWith(KIND)) return t.substring(KIND.length());
        return null;
    }

    /** the tick a new one last joined each giant */
    private static final Map<String, Integer> lastJoin = new HashMap<>();
    /** anything left to sweep away (true at start: followers kept in the save while the system was turned off) */
    private static boolean sweepDue = true;
    /** on now (read by the damage check, which runs for every hurt in the game) */
    private static volatile boolean active;
    public static int standing;

    static void forget() {
        lastJoin.clear();
        sweepDue = true;
        active = false;
        standing = 0;
    }

    public static void tick(MinecraftServer server) {
        if (!Systems.on(server, "followers")) {
            active = false;
            if (sweepDue) {
                sweepDue = false;
                sweep(server, Map.of());
            }
            standing = 0;
            return;
        }
        active = true;
        sweepDue = true;
        int count = (int) Systems.num(server, "followerCount");
        int every = Math.max(10, (int) Systems.num(server, "followerEvery")) * 20;
        int now = server.getTickCount();
        Map<String, LivingEntity> keep = new HashMap<>();
        int total = 0;
        List<Runnable> joins = new ArrayList<>();
        for (Map.Entry<String, List<Watch.Row>> e : Watch.rows(server).entrySet())
            for (Watch.Row r : e.getValue()) {
                if (!"here".equals(r.state()) || !AdminJson.str(r.r(), "ownerId", 40).isEmpty()) continue;
                LivingEntity giant = FunGiants.find(server, r.id());
                if (giant == null || !(giant.level() instanceof ServerLevel sl)) continue;
                keep.put(r.id(), giant);
                double size = AdminJson.num(r.r(), "size", FunGiants.sizeOf(giant));
                double reach = FunGiants.reach(e.getKey(), size);
                double range = range(reach);
                List<Mob> band = band(sl, giant, range, reach);
                total += band.size();
                for (Mob m : band) steer(m, giant, range, reach);
                if (band.size() >= count || sl.getDifficulty() == Difficulty.PEACEFUL) continue;
                if (!playerNear(sl, giant, 96 + reach)) continue;
                Integer last = lastJoin.get(r.id());
                if (last != null && now - last < every && now >= last) continue;
                String kind = e.getKey();
                joins.add(() -> {
                    if (join(sl, giant, kind, range, reach) != null) lastJoin.put(giant.getStringUUID(), now);
                });
            }
        // (new ones only while the world holds fewer than the most)
        for (Runnable j : joins) if (total++ < MOST) j.run();
        standing = Math.min(total, MOST);
        lastJoin.keySet().retainAll(keep.keySet());
        // every ten seconds: followers whose giant isn't standing wild here any more crumble away
        if (now % 200 < 20 || forced) sweep(server, keep);
    }

    /** how far his band keeps from him */
    static double range(double reach) {
        return Math.min(96, 10 + reach * 0.6);
    }

    /** his band standing near him */
    static List<Mob> band(ServerLevel sl, LivingEntity giant, double range, double reach) {
        String of = OF + giant.getStringUUID();
        double r = Math.min(range * 3 + reach * 0.5 + 64, 192);
        return sl.getEntitiesOfClass(Mob.class, new AABB(giant.blockPosition()).inflate(r, 64, r),
                m -> m.isAlive() && m.getTags().contains(of));
    }

    private static boolean playerNear(ServerLevel sl, LivingEntity giant, double r) {
        for (ServerPlayer p : sl.players())
            if (!p.isSpectator() && p.distanceToSqr(giant) <= r * r) return true;
        return false;
    }

    /** keep near him, and go for what he goes for */
    static void steer(Mob m, LivingEntity giant, double range, double reach) {
        double d = Math.hypot(m.getX() - giant.getX(), m.getZ() - giant.getZ());
        if (d > Math.min(range * 3 + reach * 0.5, 150)) {
            if (!bringNear(m, giant, range, reach)) poof(m);
            return;
        }
        LivingEntity his = giant instanceof Mob gm ? gm.getTarget() : null;
        if (his != null && his.isAlive() && his != m && !giant.getStringUUID().equals(followerOf(his))) {
            if (m.getTarget() != his) m.setTarget(his);
            return;
        }
        LivingEntity mine = m.getTarget();
        if (mine != null && (!mine.isAlive() || mine.distanceTo(giant) > range * 1.5 + reach)) {
            m.setTarget(null);
            mine = null;
        }
        if (m instanceof Phantom ph && mine == null) {
            String kind = FunGiants.kindOf(giant);
            double tall = kind == null ? giant.getBbHeight() : FunGiants.height(kind, FunGiants.sizeOf(giant));
            ((net.jj.giants.systemsmixin.PhantomAccess) ph).jjGiants$setAnchorPoint(BlockPos.containing(giant.getX(),
                    giant.getY() + Math.min(tall, 120) + 8, giant.getZ()));
        }
        if (m instanceof Vex v) v.setBoundOrigin(giant.blockPosition().above(3));
        if (mine != null || d <= range) return;
        if (m instanceof Slime) {
            if (d > range * 2 && !bringNear(m, giant, range, reach)) poof(m);
        } else if (m instanceof PathfinderMob pm && !(m instanceof Vex))
            pm.getNavigation().moveTo(giant.getX(), giant.getY(), giant.getZ(), 1.15);
    }

    /** a new one rises beside him: null when there's no room near him */
    static @Nullable Mob join(ServerLevel sl, LivingEntity giant, String kind, double range, double reach) {
        EntityType<? extends Mob> type = typeOf(kind);
        Mob m = type.create(sl);
        if (m == null) return null;
        boolean flies = m instanceof Phantom || m instanceof Vex;
        BlockPos at = spot(sl, giant, m, range, reach, flies);
        if (at == null) {
            m.discard();
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sl.random.nextFloat() * 360f, 0);
        dress(m, giant, kind);
        sl.addFreshEntity(m);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        return m;
    }

    private static void dress(Mob m, LivingEntity giant, String kind) {
        m.addTag(MARK);
        m.addTag(OF + giant.getStringUUID());
        m.addTag(KIND + kind);
        m.setPersistenceRequired();
        m.setCanPickUpLoot(false);
        if (m instanceof Slime s) s.setSize(2, true);
        ItemStack hand = m.getType() == EntityType.WITHER_SKELETON ? new ItemStack(Items.STONE_SWORD)
                : m.getType() == EntityType.VINDICATOR ? new ItemStack(Items.IRON_AXE)
                : m.getType() == EntityType.VEX ? new ItemStack(Items.IRON_SWORD) : ItemStack.EMPTY;
        if (!hand.isEmpty()) {
            m.setItemSlot(EquipmentSlot.MAINHAND, hand);
            m.setDropChance(EquipmentSlot.MAINHAND, 0f);
        }
        // (the ones the sun burns keep going by day: FollowerSunMixin)
        if (m instanceof Vex v) {
            if (giant instanceof Mob gm) v.setOwner(gm);
            v.setBoundOrigin(giant.blockPosition().above(3));
        }
    }

    /** a spot near him (out from under him) with room for it: ground with air over it, or the air for a flyer */
    static @Nullable BlockPos spot(ServerLevel sl, LivingEntity giant, Mob m, double range, double reach,
            boolean flies) {
        double inner = Math.min(range * 0.8, reach * 0.35 + 3);
        for (int i = 0; i < 10; i++) {
            double a = sl.random.nextDouble() * Math.PI * 2;
            double d = inner + sl.random.nextDouble() * Math.max(2, range - inner);
            int x = (int) Math.floor(giant.getX() + Math.cos(a) * d), z = (int) Math.floor(giant.getZ() + Math.sin(a) * d);
            if (!sl.hasChunkAt(new BlockPos(x, giant.getBlockY(), z))) continue;
            BlockPos ground = ground(sl, x, z, giant.getBlockY());
            if (ground == null) continue;
            BlockPos at = flies ? ground.above(4 + sl.random.nextInt(5)) : ground;
            m.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
            if (sl.noCollision(m) && sl.getWorldBorder().isWithinBounds(at)) return at;
        }
        return null;
    }

    /** the first standing room going down from a little over this height: two air blocks over something solid */
    static @Nullable BlockPos ground(ServerLevel sl, int x, int z, int fromY) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        // (a flyer may be high over the land: look well down under him)
        int top = Math.min(sl.getMaxBuildHeight() - 2, fromY + 12), bottom = Math.max(sl.getMinBuildHeight() + 1,
                fromY - 96);
        for (int y = top; y >= bottom; y--) {
            BlockState here = sl.getBlockState(p.set(x, y, z)), up = sl.getBlockState(p.set(x, y + 1, z)),
                    under = sl.getBlockState(p.set(x, y - 1, z));
            if (!here.getCollisionShape(sl, p.set(x, y, z)).isEmpty() || !up.getCollisionShape(sl, p.set(x, y + 1, z))
                    .isEmpty()) continue;
            if (here.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) continue;
            if (under.isFaceSturdy(sl, p.set(x, y - 1, z), net.minecraft.core.Direction.UP)) return new BlockPos(x, y, z);
        }
        return null;
    }

    /** brought back beside him when he left them far behind; false when there's no room */
    private static boolean bringNear(Mob m, LivingEntity giant, double range, double reach) {
        if (!(giant.level() instanceof ServerLevel sl) || m.level() != giant.level()) return false;
        BlockPos at = spot(sl, giant, m, range, reach, m instanceof Phantom || m instanceof Vex);
        if (at == null) return false;
        m.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        m.getNavigation().stop();
        return true;
    }

    static void poof(Entity m) {
        if (m.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.POOF, m.getX(), m.getY() + m.getBbHeight() / 2, m.getZ(), 12,
                    m.getBbWidth() / 2, m.getBbHeight() / 2, m.getBbWidth() / 2, 0.02);
        m.discard();
    }

    /**
     * every follower in loaded land whose giant isn't one of these crumbles away; one of these giants' that strayed out
     * of his band's reach is brought back beside him
     */
    static void sweep(MinecraftServer server, Map<String, LivingEntity> keep) {
        for (ServerLevel l : server.getAllLevels()) {
            List<Entity> gone = new ArrayList<>();
            List<Mob> strayed = new ArrayList<>();
            for (Entity e : l.getAllEntities())
                if (e.getTags().contains(MARK)) {
                    String of = followerOf(e);
                    LivingEntity giant = of == null ? null : keep.get(of);
                    if (giant == null || !(e instanceof Mob)) gone.add(e);
                    else if (e.level() != giant.level() || Math.hypot(e.getX() - giant.getX(), e.getZ() - giant.getZ())
                            > far(giant)) strayed.add((Mob) e);
                }
            for (Entity e : gone) poof(e);
            for (Mob m : strayed) {
                LivingEntity giant = keep.get(followerOf(m));
                double reach = reachOf(giant);
                if (m.level() != giant.level() || !bringNear(m, giant, range(reach), reach)) poof(m);
            }
        }
    }

    private static double reachOf(LivingEntity giant) {
        String kind = FunGiants.kindOf(giant);
        return FunGiants.reach(kind == null ? "" : kind, FunGiants.sizeOf(giant));
    }

    /** further than this from him, one of his band is brought back beside him */
    static double far(LivingEntity giant) {
        double reach = reachOf(giant);
        return Math.min(range(reach) * 3 + reach * 0.5, 150);
    }

    /**
     * (every hurt in the game, while the system is on) a follower isn't hurt by his own giant or his band, nor his
     * giant by them
     */
    public static boolean allowDamage(LivingEntity victim, DamageSource src, float amount) {
        if (!active) return true;
        Entity by = src.getEntity(), direct = src.getDirectEntity();
        if (by == null && direct == null) return true;
        String vOf = followerOf(victim);
        if (vOf != null) {
            String kind = kindOfFollower(victim);
            for (Entity x : new Entity[] {by, direct}) {
                if (x == null) continue;
                if (x.getStringUUID().equals(vOf) || vOf.equals(followerOf(x))) return false;
                if (kind != null && kind.equals(Tools.kindOf(x))) return false;
            }
            return true;
        }
        String id = victim.getStringUUID();
        for (Entity x : new Entity[] {by, direct}) if (x != null && id.equals(followerOf(x))) return false;
        return true;
    }

    // (the tests)

    private static boolean forced;

    public static void lookForTests(MinecraftServer server) {
        forced = true;
        try {
            Watch.forget();
            tick(server);
        } finally {
            forced = false;
        }
    }

    /** (the tests) the next one may come at once */
    public static void hurryForTests() {
        lastJoin.clear();
    }
}
