package net.jj.hollowbell.entity;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Something he throws: a stinger flicked off a strand end (sting volley, stinger storm), or an egg clump dropped
 * from his strands (egg rain) that bursts where it lands and may hatch a Belling.
 */
public class Shot extends ThrowableItemProjectile {
    public static final int STINGER = 0, EGG = 1;
    private static final EntityDataAccessor<Integer> DATA_KIND = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.INT);
    /** an egg: which of his egg clumps it is (drawn as that clump), and how big (his size) */
    private static final EntityDataAccessor<Integer> DATA_EGG = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.FLOAT);
    /** an egg: where it will land (the marker on the ground), and the height it was dropped from */
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_LAND = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_FROM_Y = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.FLOAT);
    /** an egg: 0 falling, 1 splatted on the ground, 2 splatted and hatching; and the tick that began */
    private static final EntityDataAccessor<Integer> DATA_STAGE = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STAGE_AT = SynchedEntityData.defineId(Shot.class, EntityDataSerializers.INT);
    public static final int FALLING = 0, SPLAT = 1, HATCHING = 2;
    /** how long a splat lies there, and how long a hatching egg shakes before the Belling comes out */
    public static final int SPLAT_TICKS = 50, HATCH_TICKS = 26;
    /** the share of eggs that hatch (the tests set it to 1 or 0) */
    public static float hatchChance = 0.65f;
    private float damage = 4f, radius = 2f;
    private int life = 200;

    public Shot(EntityType<? extends Shot> type, Level level) { super(type, level); }

    public Shot(Level level, HollowbellEntity owner, int kind, float damage, float radius) {
        super(ModEntities.SHOT, owner, level);
        entityData.set(DATA_KIND, kind);
        setItem(new ItemStack(kind == EGG ? Items.GRAY_CONCRETE : ModItems.STINGER));
        this.damage = damage;
        this.radius = radius;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(DATA_KIND, STINGER);
        b.define(DATA_EGG, 0);
        b.define(DATA_SIZE, 1f);
        b.define(DATA_LAND, new org.joml.Vector3f());
        b.define(DATA_FROM_Y, 0f);
        b.define(DATA_STAGE, FALLING);
        b.define(DATA_STAGE_AT, 0);
    }
    public int kind() { return entityData.get(DATA_KIND); }
    public int egg() { return entityData.get(DATA_EGG); }
    public float size() { return entityData.get(DATA_SIZE); }
    public Vec3 landing() { org.joml.Vector3f v = entityData.get(DATA_LAND); return new Vec3(v.x, v.y, v.z); }
    public float fromY() { return entityData.get(DATA_FROM_Y); }
    public int stage() { return entityData.get(DATA_STAGE); }
    /** ticks since the egg splatted (or started hatching) */
    public float stageAge(float partial) { return tickCount - entityData.get(DATA_STAGE_AT) + partial; }
    /** how far it has fallen, 0 just dropped to 1 landing */
    public float fallen() {
        double top = fromY(), land = landing().y;
        if (top - land < 0.5) return 1f;
        return (float) net.minecraft.util.Mth.clamp((top - getY()) / (top - land), 0, 1);
    }

    /** an egg clump: which one of his, how big he is, where it will land (see BellMoves.dropEgg) */
    public void setEgg(int egg, float size, Vec3 land) {
        entityData.set(DATA_EGG, egg);
        entityData.set(DATA_SIZE, size);
        entityData.set(DATA_LAND, new org.joml.Vector3f((float) land.x, (float) land.y, (float) land.z));
        entityData.set(DATA_FROM_Y, (float) getY());
    }

    /** eggs are big: seen from a long way off */
    @Override public boolean shouldRenderAtSqrDistance(double d) { return kind() == EGG ? d < 256 * 256 : super.shouldRenderAtSqrDistance(d); }
    @Override protected Item getDefaultItem() { return ModItems.STINGER; }
    @Override protected double getDefaultGravity() { return kind() == EGG ? 0.05 : 0.02; }

    private @Nullable HollowbellEntity bell() { return getOwner() instanceof HollowbellEntity h ? h : null; }

    @Override
    public void tick() {
        if (kind() == EGG && stage() != FALLING) { landedTick(); return; }
        super.tick();
        // an egg coming down into water (which it would sink through) or past its mark splats there
        if (!level().isClientSide && kind() == EGG && stage() == FALLING && !isRemoved() && fromY() != 0f
                && (isInWater() || isInLava() || getY() < landing().y - 1.0)) {
            if (getY() < landing().y) setPos(getX(), landing().y, getZ());
            burst();
            return;
        }
        if (!level().isClientSide && --life <= 0) discard();
        if (level().isClientSide && kind() == STINGER && tickCount % 2 == 0)
            level().addParticle(ParticleTypes.ITEM_SLIME, getX(), getY(), getZ(), 0, 0, 0);
        if (level().isClientSide && kind() == EGG) eggFx.accept(this);
    }

    /** the client's own sparkle for a falling egg and its marker (set by the client; nothing on a server) */
    public static java.util.function.Consumer<Shot> eggFx = s -> {};

    /** splatted on the ground: it lies there, and a hatching one shakes and cracks until the Belling comes out */
    private void landedTick() {
        setDeltaMovement(Vec3.ZERO);
        float age = stageAge(0f);
        if (level().isClientSide) { eggFx.accept(this); return; }
        if (!(level() instanceof ServerLevel sl)) return;
        if (stage() == HATCHING) {
            float s = Math.max(1f, size() * 3f);
            if ((int) age % 6 == 0) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY() + 0.6 * s, getZ(), 8, 0.4 * s, 0.4 * s, 0.4 * s, 0.1);
                sl.playSound(null, getX(), getY(), getZ(), net.minecraft.sounds.SoundEvents.SNIFFER_EGG_CRACK, SoundSource.HOSTILE,
                        1.2f * HollowbellConfig.V.soundVolume, 1.1f + random.nextFloat() * 0.3f);
            }
            if (age >= HATCH_TICKS) { hatch(sl); discard(); }
        } else if (age >= SPLAT_TICKS) discard();
    }

    /** a Belling bursts out of the egg: shell flies, it jumps up, and it goes for whatever he's after */
    private void hatch(ServerLevel sl) {
        HollowbellEntity h = bell();
        Vec3 c = position();
        sl.playSound(null, c.x, c.y, c.z, ModSounds.EGG_HATCH, SoundSource.HOSTILE, 1.6f * HollowbellConfig.V.soundVolume, 0.9f + random.nextFloat() * 0.2f);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y + 0.8, c.z, 40, 0.8, 0.6, 0.8, 0.25);
        sl.sendParticles(ParticleTypes.GLOW, c.x, c.y + 1, c.z, 20, 0.6, 0.6, 0.6, 0.1);
        Belling b = new Belling(ModEntities.BELLING, sl);
        b.setPos(c.x, c.y + 0.6, c.z);
        b.setDeltaMovement(0, 0.55, 0);
        b.justHatched();
        if (h != null) {
            b.setOwner(h);
            if (h.getTarget() != null) b.setTarget(h.getTarget());
        }
        sl.addFreshEntity(b);
    }

    @Override
    protected boolean canHitEntity(Entity e) {
        if (e instanceof HollowbellEntity || e instanceof Belling || e instanceof Seat || e instanceof Shot) return false;
        HollowbellEntity h = bell();
        if (h != null && (h.spares(e) || h.moves().caught(e))) return false;
        return super.canHitEntity(e);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (level().isClientSide) return;
        // (a part box of another giant is that giant)
        LivingEntity le = r.getEntity().getTags().contains(Giants.TAG) ? Giants.ownerOf(r.getEntity()) : r.getEntity() instanceof LivingEntity l ? l : null;
        if (le == null || le instanceof HollowbellEntity || !canHitEntity(le)) return;
        if (kind() == STINGER) {
            sting(le);
            playSound(ModSounds.STING, 1f, 0.8f + random.nextFloat() * 0.3f);
        }
    }

    private void sting(LivingEntity le) {
        HollowbellEntity h = bell();
        float d = h != null ? h.dmg(damage, le) : damage;
        le.invulnerableTime = 0;
        le.hurt(damageSources().mobProjectile(this, h), d);
        le.addEffect(new MobEffectInstance(MobEffects.POISON, 80, 1));
        le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
    }

    @Override
    protected void onHit(HitResult r) {
        if (kind() == EGG && stage() != FALLING) return;
        super.onHit(r);
        if (level().isClientSide) return;
        if (kind() == EGG) { burst(); return; }
        else if (level() instanceof ServerLevel sl)
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
        discard();
    }

    /**
     * An egg clump lands: a heavy splat that hurts what's round it and shakes the ground; the clump lies there
     * squashed for a couple of seconds, and most of the time a Belling hatches out of it.
     */
    private void burst() {
        if (!(level() instanceof ServerLevel sl)) return;
        HollowbellEntity h = bell();
        // it lies on the ground where it came down (on whatever it hit, the ground under that)
        int gy = sl.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, getBlockX(), getBlockZ());
        if (gy > getY() - 6 && gy <= getY() + 2) setPos(getX(), gy, getZ());
        Vec3 c = position();
        float vol = HollowbellConfig.V.soundVolume;
        sl.playSound(null, c.x, c.y, c.z, ModSounds.EGG_SPLAT, SoundSource.HOSTILE, 3f * vol, 0.8f + random.nextFloat() * 0.25f);
        sl.playSound(null, c.x, c.y, c.z, ModSounds.EGG_BURST, SoundSource.HOSTILE, 1.5f * vol, 0.8f + random.nextFloat() * 0.3f);
        // seen from well off: the splat is sent to everyone who can see the egg, not only those right by it
        for (net.minecraft.server.level.ServerPlayer p : sl.players()) {
            if (p.distanceToSqr(c) > 200 * 200) continue;
            sl.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIME_CONCRETE.defaultBlockState()), true, c.x, c.y + 0.3, c.z, 60, radius * 0.45, 0.3, radius * 0.45, 0.2);
            sl.sendParticles(p, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), true, c.x, c.y + 0.3, c.z, 30, radius * 0.3, 0.4, radius * 0.3, 0.25);
            sl.sendParticles(p, ParticleTypes.ITEM_SLIME, true, c.x, c.y + 0.3, c.z, 40, radius * 0.5, 0.3, radius * 0.5, 0.15);
            sl.sendParticles(p, ParticleTypes.EXPLOSION, true, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            // a thump underfoot for anyone close
            double d = Math.sqrt(p.distanceToSqr(c));
            if (d < radius * 4 + 8 && HollowbellConfig.V.screenShake)
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.jj.hollowbell.net.ThumpPayload(c.x, c.y, c.z, 0.25f));
        }
        java.util.List<LivingEntity> hit = new java.util.ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius), e -> canHitEntity(e) && e.isAlive() && e.position().distanceTo(c) <= radius));
        // (another giant's part boxes in the burst count as that giant)
        for (Entity p : sl.getEntities((Entity) null, new AABB(c, c).inflate(radius), x -> !(x instanceof LivingEntity) && x.getTags().contains(Giants.TAG))) {
            LivingEntity o = Giants.ownerOf(p);
            if (o != null && !(o instanceof HollowbellEntity) && o.isAlive() && canHitEntity(o) && !hit.contains(o)) hit.add(o);
        }
        for (LivingEntity e : hit) {
            e.invulnerableTime = 0;
            e.hurt(damageSources().mobProjectile(this, h), h != null ? h.dmg(damage, e) : damage);
            e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        }
        // it lies there squashed; most of the time something is moving in it
        boolean hatches = h != null && random.nextFloat() < hatchChance && sl.getEntitiesOfClass(Belling.class, new AABB(c, c).inflate(48)).size() < 10;
        entityData.set(DATA_STAGE, hatches ? HATCHING : SPLAT);
        entityData.set(DATA_STAGE_AT, tickCount);
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
        life = SPLAT_TICKS + HATCH_TICKS + 20;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Kind", kind()); tag.putFloat("Damage", damage); tag.putFloat("Radius", radius); tag.putInt("Life", life);
        tag.putInt("Egg", egg()); tag.putFloat("Size", size());
        Vec3 l = landing(); tag.putDouble("LandX", l.x); tag.putDouble("LandY", l.y); tag.putDouble("LandZ", l.z);
        tag.putFloat("FromY", fromY());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(DATA_KIND, tag.getInt("Kind")); damage = tag.getFloat("Damage"); radius = tag.getFloat("Radius");
        if (tag.contains("Life")) life = tag.getInt("Life");     // an old save without it keeps the usual life
        entityData.set(DATA_EGG, tag.getInt("Egg"));
        entityData.set(DATA_SIZE, tag.contains("Size") ? tag.getFloat("Size") : 1f);
        entityData.set(DATA_LAND, new org.joml.Vector3f((float) tag.getDouble("LandX"), (float) tag.getDouble("LandY"), (float) tag.getDouble("LandZ")));
        entityData.set(DATA_FROM_Y, tag.getFloat("FromY"));
    }

    @Override public boolean isPickable() { return false; }
}
