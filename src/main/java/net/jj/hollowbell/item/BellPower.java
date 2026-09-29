package net.jj.hollowbell.item;

import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The bell glass set's own moves, on the "Armour power" key. On the ground: the Bell toll, you float up like he
 * does, hang for a moment ringing, then slam down and a ring goes out that knocks back and stuns everything round
 * you. In the air: a glide, you drift along the way you're looking and come down slowly. The wait after either
 * shows on the chestplate (and on the little icon by the hotbar).
 */
public final class BellPower {
    private BellPower() {}

    public static final int TOLL_COOLDOWN = 240, GLIDE_COOLDOWN = 140;
    public static final int RISE = 10, HANG = 6, SLAM_MAX = 40, GLIDE = 50;
    public static final double RING = 7;
    public static final float RING_DAMAGE = 8f;
    private static final Vector3f GLASS = new Vector3f(0.72f, 0.95f, 0.72f);

    public enum Kind { TOLL, GLIDE }
    private static final class Doing { Kind kind; int t; boolean rung; Doing(Kind k) { kind = k; } }
    private static final Map<UUID, Doing> DOING = new HashMap<>();

    /** what the key does right now (the tests call this straight); null when nothing happened */
    public static Kind use(ServerPlayer p) {
        if (p.isSpectator() || p.isPassenger() || !BellArmorItem.fullSet(p)) {
            if (!BellArmorItem.fullSet(p)) p.displayClientMessage(Component.translatable("message.hollowbell.power_no_set"), true);
            return null;
        }
        if (p.getCooldowns().isOnCooldown(ModItems.BELL_CHESTPLATE) || DOING.containsKey(p.getUUID())) return null;
        Kind k = p.onGround() || p.isInWater() ? Kind.TOLL : Kind.GLIDE;
        DOING.put(p.getUUID(), new Doing(k));
        p.getCooldowns().addCooldown(ModItems.BELL_CHESTPLATE, k == Kind.TOLL ? TOLL_COOLDOWN : GLIDE_COOLDOWN);
        ServerLevel l = p.serverLevel();
        if (k == Kind.TOLL) l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PULSE, SoundSource.PLAYERS, 1.2f, 1.4f);
        else l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DRIFT, SoundSource.PLAYERS, 1f, 1.5f);
        return k;
    }

    public static boolean busy(Player p) { return DOING.containsKey(p.getUUID()); }
    public static void clear() { DOING.clear(); }

    public static void tick(MinecraftServer server) {
        if (DOING.isEmpty()) return;
        DOING.entrySet().removeIf(en -> {
            ServerPlayer p = server.getPlayerList().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || p.isSpectator()) return true;
            Doing d = en.getValue();
            d.t++;
            p.resetFallDistance();
            return d.kind == Kind.TOLL ? toll(p, d) : glide(p, d);
        });
    }

    /** float up, hang ringing, slam down; true when done */
    private static boolean toll(ServerPlayer p, Doing d) {
        ServerLevel l = p.serverLevel();
        Vec3 v = p.getDeltaMovement();
        if (d.t <= RISE) {
            // up quick at first, easing off near the top
            double up = 0.75 * (1 - d.t / (double) (RISE + 2));
            p.setDeltaMovement(v.x * 0.6, up, v.z * 0.6);
            p.hurtMarked = true;
            if (d.t % 2 == 0) l.sendParticles(new DustParticleOptions(GLASS, 1.2f), p.getX(), p.getY(), p.getZ(), 6, 0.3, 0.1, 0.3, 0);
        } else if (d.t <= RISE + HANG) {
            // hanging: a soft bob and a hum, the wind-up before the slam
            p.setDeltaMovement(0, 0.02 * Math.sin(d.t), 0);
            p.hurtMarked = true;
            if (d.t == RISE + 1) l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.TOLL, SoundSource.PLAYERS, 0.9f, 1.6f);
            l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 2, 0.4, 0.4, 0.4, 0.01);
        } else {
            boolean landed = d.t > RISE + HANG + 2 && (p.onGround() || p.isInWater());
            if (!landed && d.t < RISE + HANG + SLAM_MAX) {
                p.setDeltaMovement(0, -2.2, 0);
                p.hurtMarked = true;
                return false;
            }
            if (!d.rung) { ring(p, 1f); d.rung = true; }
            return true;
        }
        return false;
    }

    /** drifting along the look, slowly down; true when done */
    private static boolean glide(ServerPlayer p, Doing d) {
        if (d.t > GLIDE || (d.t > 3 && (p.onGround() || p.isInWater()))) return true;
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() > 1e-4) flat = flat.normalize();
        double ease = d.t < 6 ? d.t / 6.0 : d.t > GLIDE - 8 ? (GLIDE - d.t) / 8.0 : 1;
        Vec3 h = flat.scale(0.55 * ease + 0.1);
        p.setDeltaMovement(h.x, -0.06 + 0.04 * Math.sin(d.t * 0.3), h.z);
        p.hurtMarked = true;
        if (d.t % 3 == 0) p.serverLevel().sendParticles(new DustParticleOptions(GLASS, 1f), p.getX(), p.getY() + 0.2, p.getZ(), 3, 0.3, 0.05, 0.3, 0);
        if (d.t == GLIDE || d.t > 3 && p.onGround()) p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, true, false, true));
        return false;
    }

    /**
     * The ring where you land: everything close (not you, not your pets, not other players in creative) is hurt a
     * little, thrown back and stunned for a moment. Strength 1 is the full toll; the set's last-ditch ring uses less.
     */
    public static int ring(ServerPlayer p, float strength) {
        ServerLevel l = p.serverLevel();
        double r = RING * (0.6 + 0.4 * strength);
        int n = 0;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r, 3, r), e -> e != p && e.isAlive())) {
            if (e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) continue;
            if (e instanceof Player q && (q.isCreative() || q.isSpectator())) continue;
            if (e instanceof HollowbellEntity || e.isPassengerOfSameVehicle(p)) continue;
            Vec3 d = e.position().subtract(p.position());
            double dist = Math.max(0.5, Math.sqrt(d.x * d.x + d.z * d.z));
            if (dist > r) continue;
            double fall = 1 - dist / r * 0.6;
            if (strength >= 1f) e.hurt(p.damageSources().playerAttack(p), RING_DAMAGE * (float) fall);
            Vec3 out = new Vec3(d.x / dist, 0, d.z / dist).scale((0.9 + 0.6 * strength) * fall);
            e.setDeltaMovement(e.getDeltaMovement().add(out.x, 0.35 + 0.2 * strength, out.z));
            e.hurtMarked = true;
            // stunned: rooted and weak for a moment
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (50 * strength) + 10, 4), p);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, (int) (60 * strength) + 10, 1), p);
            n++;
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.TOLL_BIG, SoundSource.PLAYERS, 1.4f * strength, 1.3f);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.5f, 0.6f);
        // the ring itself: a circle of glass dust going out along the ground
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI * 2 / 48;
            for (double rr = 1.5; rr <= r; rr += 1.8)
                l.sendParticles(new DustParticleOptions(GLASS, 1.6f), p.getX() + Math.cos(a) * rr, p.getY() + 0.15, p.getZ() + Math.sin(a) * rr, 1, 0, 0.02, 0, 0);
        }
        l.sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 0.3, p.getZ(), 1, 0, 0, 0, 0);
        return n;
    }
}
