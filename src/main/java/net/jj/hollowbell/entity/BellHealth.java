package net.jj.hollowbell.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.mixin.TridentAccess;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.rig.BellRig;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.world.Shrine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** His health: hits, pods, stings, arrows, the slow heal and his death. */
final class BellHealth {
    private final HollowbellEntity hb;

    /** per giant that hit him: the game tick, and the biggest blow it landed that tick */
    final Map<UUID, float[]> giantHits = new HashMap<>();

    /** health last tick and the last hurt, for the slow heal */
    float healSeen = -1f;

    long healHurtAt = Long.MIN_VALUE / 4;

    BellHealth(HollowbellEntity hb) {
        this.hb = hb;
    }

    /**
     * A player's swing that the client says landed on him. The server checks the player is in reach of that bone
     * (or looks for itself along the player's own line of sight) and passes the hit through the game's own attack
     * so weapons, enchantments and crits all count.
     */
    public void hitBy(ServerPlayer p, int claimedBone) {
        if (hb.isDeadOrDying() && !hb.isRemoved()) return;
        // (one swing every few ticks at most: the game's own pause between hits on a mob never reaches him)
        long now = hb.level().getGameTime();
        Long last = hb.lastSwing.get(p.getUUID());
        if (last != null && now - last < HollowbellEntity.SWING_GAP && now >= last) return;
        hb.ensurePose();
        double reach = p.entityInteractionRange() + 1.5;
        Vec3 eye = p.getEyePosition(), look = p.getViewVector(1f);
        int bone = -1;
        // the bone the client saw it land on, if the player is in reach of that bone as it is or was a moment ago
        // (his arms and strands swing far in the time between the two sides); else what's along the player's look
        if (claimedBone >= 0
                && claimedBone < hb.rig.boneCount()
                && inReach(claimedBone, eye, p.entityInteractionRange() + 1.0)) bone = claimedBone;
        if (bone < 0) {
            BellRig.Hit h = hb.raycast(eye, look, reach);
            if (h != null) bone = h.bone();
        }
        if (bone < 0) return;
        if (hb.lastSwing.size() > 64) hb.lastSwing.values().removeIf(t -> now - t > HollowbellEntity.SWING_GAP);
        hb.lastSwing.put(p.getUUID(), now);
        hb.pendingBone = bone;
        hb.pendingInside = hb.moves.caught(p);
        try {
            p.attack(hb);
        } finally {
            hb.pendingBone = -1;
            hb.pendingInside = false;
        }
    }

    @Nullable
    Vec3 boneCentreWorld(int b) {
        float[] bb = BellModel.get().bounds[b];
        if (bb == null) return null;
        return hb.boneWorld(b, new Vector3f((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2));
    }

    /**
     * Is a spot (a swinger's eye) within reach of a bone of his, now or over the last half second? What a player sees
     * of him is a moment behind him here, and his arms and strands swing a long way in that time.
     */
    boolean inReach(int b, Vec3 eye, double reach) {
        float s = hb.bellScale();
        Matrix4f inv = new Matrix4f();
        if (hb.rig.within(hb.pose, hb.slicesNow(), b, hb.toModel(eye), (float) (reach / s), inv)) return true;
        for (int k = 0; k < recentCount; k++) {
            int i = Math.floorMod(recentNext - 1 - k, RECENT);
            Vector3f m = hb.toModel(eye.subtract(recentAt[i]).add(hb.position()));
            if (hb.rig.within(recent[i], null, b, m, (float) (reach / s), inv)) return true;
        }
        return false;
    }

    private static final int RECENT = 10;
    private final Matrix4f[][] recent = new Matrix4f[RECENT][];
    private final Vec3[] recentAt = new Vec3[RECENT];
    private int recentNext, recentCount;

    /** his pose this tick, kept for a half second (server) */
    void recordPose() {
        Matrix4f[] m = recent[recentNext];
        if (m == null) m = recent[recentNext] = hb.rig.newPose();
        for (int i = 0; i < m.length; i++) m[i].set(hb.pose[i]);
        recentAt[recentNext] = hb.position();
        recentNext = (recentNext + 1) % RECENT;
        recentCount = Math.min(recentCount + 1, RECENT);
    }

    /** how much of a hit reaches him, by the block it landed on */
    public float worth(int bone, boolean inside) {
        if (bone < 0) return 0.1f;
        return switch (hb.rig.kind[bone]) {
            case CROWN -> inside ? 4f : 3f;
            case SPOT -> inside ? 3.5f : 2.5f;
            case POD -> 1f;
            case EGG -> 0.3f;
            default -> 0.1f; // the copper and bone take very little
        };
    }

    public boolean applyDamage(DamageSource src, float amount, int bone, boolean inside) {
        if (hb.level().isClientSide || hb.isDeadOrDying() || hb.isInvulnerableTo(src)) return false;
        Entity att = src.getEntity();
        float dealt = amount * worth(bone, inside);
        if (Giants.fromGiant(src, hb)) {
            // another giant's blow (or its blast, or what it threw): by his armour against giants, wherever it lands
            dealt = amount * BellConfig.V.giantArmor * Giants.fightPace(hb.bellScale(), 1f);
            // a giant made of many parts (every ring of a Furrowmaw) lands one blow on him, not one per part: once
            // per giant per tick, the biggest
            dealt = onceATick(Giants.behind(src), dealt);
            if (dealt <= 0f) return false;
        } else if (src.is(DamageTypeTags.IS_EXPLOSION)) {
            dealt = amount * BellConfig.V.explosionArmor; // any other blast: the same for all the giants
        } else if (bone < 0 && !(att instanceof Player)) {
            // the weak-spot rule is for players with a sword: the Unmake's burning, a creature's blow that isn't
            // aimed at any one block of him lands in full
            dealt = amount;
        }
        if (hb.resting()) dealt *= 1.3f; // down on the ground he can really be hurt
        if (bone >= 0) {
            BellRig.Kind k = hb.rig.kind[bone];
            int part = hb.rig.part[bone];
            Vec3 at = boneCentreWorld(bone);
            if (k == BellRig.Kind.POD && !hb.state.podPopped[part]) {
                hb.podHp[part] -= amount / Math.max(4f, hb.healthMax() * 0.012f);
                if (at != null)
                    hb.particles(
                            new BlockParticleOption(
                                    ParticleTypes.BLOCK, Blocks.WHITE_STAINED_GLASS.defaultBlockState()),
                            at,
                            16,
                            hb.rig.pods[part].radius() * hb.bellScale() * 0.5,
                            0.3);
                if (hb.podHp[part] <= 0f) popPod(part, att);
            } else if (k == BellRig.Kind.STRAND) {
                hb.moves.strandHit(part, amount, att);
            } else if (k == BellRig.Kind.ARM) {
                hb.moves.armHit(part, amount, att);
            }
            if ((k == BellRig.Kind.SPOT || k == BellRig.Kind.CROWN) && at != null)
                hb.particles(ParticleTypes.GLOW, at, 20, 4 * hb.bellScale() + 0.5, 0.1);
        }
        if (att instanceof LivingEntity le && hb.moves.caught(le)) hb.moves.hurtFromInside(dealt);
        return takeDamage(src, dealt);
    }

    /** how much of this blow is new: all of it the first time this tick, only what it's over the last one after that */
    float onceATick(@Nullable Entity by, float dealt) {
        if (by == null) return dealt;
        long now = hb.level().getGameTime();
        if (giantHits.size() > 16) giantHits.values().removeIf(v -> v[0] < now);
        float[] v = giantHits.get(by.getUUID());
        if (v == null || v[0] != now) {
            giantHits.put(by.getUUID(), new float[] {now, dealt});
            return dealt;
        }
        float more = dealt - v[1];
        if (more <= 0f) return 0f;
        v[1] = dealt;
        return more;
    }

    boolean takeDamage(DamageSource src, float dealt) {
        Entity att = src.getEntity();
        hb.hurtAt = hb.level().getGameTime();
        hb.wakeUp();
        hb.lastHitBig = dealt > hb.healthMax() * 0.02f;
        if (HollowbellEntity.DEBUG) HollowbellMod.LOG.info("Hurt {} by {} ({})", dealt, src.getMsgId(), att);
        hb.hp = Math.max(0f, hb.healthNow() - dealt);
        hb.getEntityData().set(HollowbellEntity.DATA_HP, hb.hp);
        hb.level().broadcastDamageEvent(hb, src);
        // struck by another giant's helper (its jj_giant_of tag): held against that giant, not the helper
        LivingEntity boss = hb.level() instanceof ServerLevel sl ? Giants.helperOwner(src, sl) : null;
        if (boss == hb) boss = null;
        if (boss != null) {
            if (!hb.spares(boss)) hb.targets.remember(boss);
            // and in a giants' meeting fight, the giant is what he goes for
            if (hb.meetFoe != null && !hb.yielding() && hb.targets.fairGame(boss)) {
                hb.setTarget(boss);
                hb.angerTicks = Math.max(hb.angerTicks, 600);
            }
        } else if (att instanceof LivingEntity le && !(att instanceof Player p && p.isCreative()) && !hb.spares(le))
            hb.targets.remember(le);
        if (att instanceof Player p && !p.isCreative()) {
            boolean had = hb.fighters.containsKey(p.getUUID());
            hb.fighters.put(p.getUUID(), hb.level().getGameTime());
            // (more of them raise his most health, not his health: that would be a heal. Untouched, he stays full.)
            if (!had && hb.fighters.size() > 1 && hb.baseMax > 0f) {
                boolean full = hb.healthNow() >= hb.healthMax() - 0.01f;
                float max = hb.baseMax * hb.playerBoost();
                hb.setMax(max, full ? 1f : Math.min(1f, hb.healthNow() / Math.max(1f, max)));
            }
            hb.setLastHurtByPlayer(p);
            if (hb.spares(p) && !p.isSpectator()) {
                if (hb.mood.struck(p.getUUID())) {
                    hb.setTarget(p);
                    hb.angerTicks = 2400;
                    p.displayClientMessage(Component.translatable("message.hollowbell.had_enough"), false);
                } else {
                    int left = hb.mood.strikesLeft(p.getUUID());
                    if (left < Integer.MAX_VALUE)
                        p.displayClientMessage(Component.translatable("message.hollowbell.lets_it_go", left), true);
                }
            } else if (!hb.moves.caught(p)) {
                hb.setTarget(p);
                hb.angerTicks = 1200;
            }
        } else if (att instanceof LivingEntity le && !hb.spares(le)) {
            hb.setLastHurtByMob(le);
            boolean giant = Giants.isGiant(le);
            // (a giant's helper is the giant's: in a meeting fight he goes for the giant (above), and backing down he
            // stops for none of them)
            boolean helper = boss != null || le.getTags().contains(Giants.KIN);
            if (hb.getTarget() == null
                    && !(giant && (hb.yielding() || Meeting.yielding(le)))
                    && !(helper && (hb.yielding() || (boss != null && hb.meetFoe != null)))) {
                hb.setTarget(le);
                hb.angerTicks = Math.max(hb.angerTicks, 600);
            }
        }
        // brought low by another giant: he backs down (see meetTick)
        Entity by = Giants.behind(src);
        if (by != null
                && by != hb
                && Giants.isGiant(by)
                && !hb.yielding()
                && BellConfig.V.meetings
                && BellConfig.V.yieldAt > 0f
                && hb.hp > 0f
                && hb.hp < hb.healthMax() * BellConfig.V.yieldAt) {
            LivingEntity o = Giants.ownerOf(by);
            hb.backDown(o != null ? o : by);
        }
        if (hb.hp <= 0f) {
            hb.setHealth(0f);
            hb.die(src);
        }
        return true;
    }

    public void popPod(int i, @Nullable Entity by) {
        if (hb.state.podPopped[i]) return;
        hb.state.podPopped[i] = true;
        hb.state.podGrowth[i] = 0f;
        hb.podHp[i] = 0f;
        hb.podRegrowAt[i] = hb.level().getGameTime() + Math.max(1, BellConfig.V.podRegrowSeconds) * 20L;
        hb.partsDirty = true;
        Vec3 c = hb.podWorld(i);
        hb.sound(c, ModSounds.POD_POP, 3f, 1f);
        hb.sound(c, SoundEvents.GLASS_BREAK, 2f, 0.5f);
        hb.particles(ParticleTypes.SQUID_INK, c, 40, hb.rig.pods[i].radius() * hb.bellScale() * 0.6, 0.2);
        float ps = hb.bellScale();
        double size = 4 * ps + 2 * Math.sqrt(Math.min(1f, ps));
        BigFx.send(hb.level(), BigFx.SPARKS, c, (float) size, new Vec3(0, -1, 0), 0x9CFF6A);
        BigFx.send(
                hb.level(), BigFx.CLOUD, new Vec3(c.x, hb.groundAt(c.x, c.z), c.z), (float) size, Vec3.ZERO, 0x9CFF6A);
        // a pod out of his twenty-three is worth a piece of him
        float worth = hb.healthMax() * Mth.clamp(BellConfig.V.podPopShare, 0f, 0.2f);
        hb.hp = Math.max(0f, hb.healthNow() - worth);
        hb.getEntityData().set(HollowbellEntity.DATA_HP, hb.hp);
        if (by instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.translatable("message.hollowbell.pod_popped", hb.podsLeft()), true);
        if (hb.level() instanceof ServerLevel sl) {
            ItemStack drop = new ItemStack(ModItems.POD);
            sl.addFreshEntity(new ItemEntity(sl, c.x, c.y, c.z, drop));
        }
        if (hb.hp <= 0f) {
            hb.setHealth(0f);
            hb.die(
                    by instanceof Player pl
                            ? hb.damageSources().playerAttack(pl)
                            : hb.damageSources().generic());
        } else hb.hangFromPods();
    }

    /** /hollowbell popped n: pops the first n pods */
    public void popPods(int n) {
        for (int i = 0; i < hb.rig.pods.length && n > 0 && !hb.isDeadOrDying(); i++)
            if (!hb.state.podPopped[i]) {
                popPod(i, null);
                n--;
            }
    }

    /** grows every pod back straight away */
    public void mendPods() {
        for (int i = 0; i < hb.rig.pods.length; i++) {
            hb.state.podPopped[i] = false;
            hb.state.podGrowth[i] = 1f;
            hb.podHp[i] = 1f;
            hb.podRegrowAt[i] = 0;
        }
        hb.sunkUntil = 0;
        hb.partsDirty = true;
        hb.hangFromPods();
    }

    void eggGone(int i) {
        hb.state.eggGone[i] = true;
        hb.eggBack[i] = 20 * 60 * 5;
        hb.partsDirty = true;
    }

    public void heal() {
        hb.hp = hb.healthMax();
        hb.getEntityData().set(HollowbellEntity.DATA_HP, hb.hp);
    }

    public void setHealthTo(float v) {
        hb.hp = Mth.clamp(v, 1f, hb.healthMax());
        hb.getEntityData().set(HollowbellEntity.DATA_HP, hb.hp);
    }

    public void hurtBy(float amount) {
        applyDamage(hb.damageSources().generic(), amount, -1, false);
    }

    /** touching a strand stings: poison and slowness */
    void stingTick() {
        if (hb.tickCount % 5 != 0) return;
        float s = hb.bellScale();
        AABB box = new AABB(
                hb.getX() - 100 * s,
                hb.getY() - 2,
                hb.getZ() - 100 * s,
                hb.getX() + 100 * s,
                hb.getY() + hb.rig.rimY * s,
                hb.getZ() + 100 * s);
        List<LivingEntity> near = hb.level()
                .getEntitiesOfClass(LivingEntity.class, box, e -> hb.fairGame(e) && !(e instanceof HollowbellEntity));
        if (near.isEmpty()) return;
        Matrix4f inv = new Matrix4f();
        for (LivingEntity e : near) {
            Vector3f m = hb.toModel(e.position().add(0, e.getBbHeight() * 0.5, 0));
            int pad = Math.max(1, Math.round(0.7f / s));
            if (pad > 6) pad = 6;
            boolean hit = false;
            for (var S : hb.rig.strands) {
                // cheap first: is it anywhere near this strand's line
                Vector3f top = S.joints()[0];
                if (Math.abs(m.x - top.x) > 40 || Math.abs(m.z - top.z) > 40) continue;
                for (int b : S.bones())
                    if (hb.rig.touches(hb.pose, hb.slicesNow(), b, m, pad, inv)) {
                        hit = true;
                        break;
                    }
                if (hit) break;
            }
            // a pod hangs from its strand right where you stand on it: you're stung there, but not knocked off
            Solid.Anchor on = Solid.anchorUnder(hb, e, 1.5, 1.5);
            if (!hit && on != null) hit = onStrandPod(BellPieces.get().bone[hb.solidShape().bone[on.frame()]]);
            if (hit) hb.moves.sting(e, on == null);
        }
    }

    /** is bone b a pod hanging from a strand */
    boolean onStrandPod(int b) {
        int p = hb.rig.parent[b];
        return hb.rig.kind[b] == BellRig.Kind.POD && p >= 0 && hb.rig.kind[p] == BellRig.Kind.STRAND;
    }

    /** arrows and other things thrown at him: the game can't find him for them, so he looks for them himself */
    void arrowsTick() {
        List<Projectile> ps = hb.level()
                .getEntitiesOfClass(
                        Projectile.class,
                        hb.bodyBox(),
                        p -> p.getDeltaMovement().lengthSqr() > 0.04
                                && !(p.getOwner() instanceof HollowbellEntity)
                                && stopsOnHim(p));
        for (var p : ps) {
            if (p instanceof AbstractArrow a
                    && a.isNoGravity()
                    && a.getDeltaMovement().lengthSqr() < 0.05) continue;
            Vec3 v = p.getDeltaMovement();
            BellRig.Hit h = hb.raycast(p.position(), v, v.length() + 0.5);
            if (h == null) continue;
            float dmg;
            // (a trident hits for 8 whatever its speed, as it does a mob)
            if (p instanceof ThrownTrident) dmg = 8f;
            else if (p instanceof AbstractArrow a) dmg = (float) (a.getBaseDamage() * v.length());
            else dmg = 2f;
            DamageSource src = p.getOwner() instanceof LivingEntity le
                    ? hb.damageSources().mobProjectile(p, le)
                    : hb.damageSources().generic();
            applyDamage(src, Math.max(1f, dmg), h.bone(), false);
            Vec3 at = p.position().add(v.normalize().scale(h.t()));
            hb.sound(at, SoundEvents.ARROW_HIT, 1f, 0.8f);
            if (p instanceof ThrownTrident tr) {
                // stops where it struck and drops: picked up again, or Loyalty brings it back
                tr.setPos(at.subtract(v.normalize().scale(0.5)));
                tr.setDeltaMovement(v.multiply(-0.01, -0.1, -0.01));
                ((TridentAccess) tr).hollowbell$setDealtDamage(true);
                continue;
            }
            if (p instanceof AbstractArrow a
                    && a.pickup == AbstractArrow.Pickup.ALLOWED
                    && hb.level() instanceof ServerLevel sl) {
                sl.addFreshEntity(new ItemEntity(
                        sl, at.x, at.y, at.z, a.getPickupItemStackOrigin().copy()));
            }
            p.discard();
        }
    }

    /** what stops on his body: arrows, snowballs, eggs and fireballs (potions, pearls and the like go their own way) */
    static boolean stopsOnHim(Projectile p) {
        if (p instanceof StingerHook) return false;
        return p instanceof AbstractArrow
                || p instanceof Snowball
                || p instanceof ThrownEgg
                || p instanceof AbstractHurtingProjectile && !(p instanceof AbstractWindCharge);
    }

    void tickDeath() {
        hb.deathTime++;
        if (hb.level().isClientSide) return;
        if (hb.deathBy != null && hb.level().getPlayerByUUID(hb.deathBy) instanceof Player killer)
            hb.setLastHurtByPlayer(killer);
        float s = hb.bellScale();
        hb.ensurePose();
        double small = Math.sqrt(Math.min(1f, s));
        // his light goes out, then two weak spasms of the bell
        if (hb.deathTime == 1)
            BigFx.send(hb.level(), BigFx.FLASH, hb.crownWorld(), (float) (30 * s + 8 * small), Vec3.ZERO, 0xBFFFD8);
        if (hb.deathTime == 8) hb.pulse(0.6f);
        if (hb.deathTime == 28) hb.pulse(0.35f);
        if (hb.deathTime == 60 || hb.deathTime == 100 || hb.deathTime == 130) {
            for (int i = 0; i < 6; i++) {
                var S = hb.rig.strands[hb.getRandom().nextInt(hb.rig.strands.length)];
                Vec3 c = hb.strandTipWorld(S.k());
                hb.sound(
                        c,
                        SoundEvents.BONE_BLOCK_BREAK,
                        2f,
                        0.5f + hb.getRandom().nextFloat() * 0.3f);
            }
        }
        if (hb.deathTime == 140) {
            Vec3 c = hb.position();
            hb.sound(c, SoundEvents.GENERIC_EXPLODE.value(), 3f, 0.4f);
            hb.particles(ParticleTypes.CLOUD, c.add(0, 2, 0), 120, 80 * s, 0.1);
            // (he comes down: the whole bell on the ground, dust or a splash and a wave, a ring running out)
            Vec3 g = new Vec3(c.x, hb.groundAt(c.x, c.z), c.z);
            // (the fixed parts shrink too for a small one: see BellMoves.px)
            float slam = (float) (70 * s + 8 * small);
            BigFx.send(hb.level(), slam >= 12 ? BigFx.SLAM : BigFx.STOMP, g, slam);
            BigFx.send(hb.level(), BigFx.SHOCKWAVE, g, (float) (140 * s + 20 * small), Vec3.ZERO, 30);
            // his glass breaking, and the bell's own low note with the glass ringing on
            BigFx.send(hb.level(), BigFx.SPARKS, g, (float) (20 * s + 4 * small), new Vec3(0, 1, 0), 0xA8E6B0);
            hb.sound(c, ModSounds.TOLL, 4f, 0.5f);
        }
        if (hb.deathTime == 146) hb.sound(hb.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 4f, 0.5f);
        if (hb.deathTime > 190 && hb.deathTime % 8 == 0)
            hb.particles(
                    new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OXIDIZED_COPPER.defaultBlockState()),
                    hb.position().add(0, 1, 0),
                    60,
                    70 * s,
                    0.2);
        if (hb.deathTime >= HollowbellEntity.DEATH_LENGTH && !hb.isRemoved()) {
            if (!hb.lootDropped && hb.level() instanceof ServerLevel sl) {
                // everything he drops goes into his loot cache, on a pedestal where he fell
                hb.caught = new ArrayList<>();
                try {
                    hb.dropLootNow(
                            sl,
                            hb.deathLoot != null
                                    ? hb.deathLoot
                                    : hb.damageSources().generic());
                } finally {
                    List<ItemStack> loot = hb.caught;
                    hb.caught = null;
                    if (!loot.isEmpty()) {
                        boolean grief =
                                BellConfig.V.griefing && sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
                        BlockPos at = Shrine.build(sl, hb.getX(), hb.getY(), hb.getZ(), loot, grief);
                        HollowbellMod.LOG.info("His loot is in the cache at {}", at.toShortString());
                    }
                }
            }
            hb.lootDropped = true;
            hb.particles(ParticleTypes.POOF, hb.position().add(0, 1, 0), 120, 60 * s, 0.05);
            hb.level().broadcastEntityEvent(hb, (byte) 60);
            hb.remove(Entity.RemovalReason.KILLED);
        }
    }

    /** passiveHealPerMinute percent of his most health a minute, a little each second, paused after any hurt */
    void passiveHeal() {
        long now = hb.level().getGameTime();
        float hpNow = hb.healthNow(), max = hb.healthMax();
        if (healSeen >= 0f && hpNow < healSeen - 0.001f) healHurtAt = now;
        if (BellConfig.V.passiveHeal
                && hb.tickCount % 20 == 0
                && !(hb.isDeadOrDying())
                && hpNow > 0f
                && hpNow < max
                && now - healHurtAt >= HollowbellEntity.HEAL_PAUSE) {
            float v = Math.min(max, hpNow + max * Mth.clamp(BellConfig.V.passiveHealPerMinute, 0f, 100f) / 100f / 60f);
            hb.hp = v;
            hb.getEntityData().set(HollowbellEntity.DATA_HP, v);
            hpNow = v;
        }
        healSeen = hpNow;
    }

    /** ticks since he was last hurt, as the slow heal sees it */
    public long sinceHurtForHeal() {
        return hb.level().getGameTime() - healHurtAt;
    }
}
