package net.jj.hollowbell.item;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.net.GlassBellPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The bell glass set's other power (Sneak + the Armour power key): the Glass Bell. A dome of his glass closes over
 * you where you stand for 20 seconds. Creatures can't come in (they're pushed back out; other players too, when PvP
 * is on), nothing shot or thrown gets through, and under water you breathe inside it. It doesn't stop you: you can
 * hit and shoot out of it.
 */
public final class GlassBell {
    private GlassBell() {}

    public static final int TICKS = 20 * 20, COOLDOWN = 120 * 20;
    public static final double RADIUS = 4;

    private static final Vector3f GLASS = new Vector3f(0.72f, 0.95f, 0.72f);

    /** a bell standing: whose, where, until when */
    record Bell(UUID owner, ServerLevel level, Vec3 at, long until) {}

    private static final List<Bell> bells = new ArrayList<>();

    /** Sneak + the key: true if the bell closed over you */
    public static boolean ring(ServerPlayer p) {
        if (!BellArmorItem.fullSet(p) || p.isSpectator() || p.isPassenger()) return false;
        if (p.getCooldowns().isOnCooldown(ModItems.BELL_LEGGINGS)) return false;
        p.getCooldowns().addCooldown(ModItems.BELL_LEGGINGS, COOLDOWN);
        ServerLevel l = p.serverLevel();
        bells.removeIf(b -> b.owner().equals(p.getUUID()));
        Bell b = new Bell(p.getUUID(), l, p.position(), l.getGameTime() + TICKS);
        bells.add(b);
        tell(l, b, TICKS);
        l.playSound(null, p.blockPosition(), ModSounds.TOLL, SoundSource.PLAYERS, 1.2f, 1.5f);
        l.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.8f);
        l.playSound(null, p.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.0f, 1.2f);
        shimmer(l, b, 80);
        return true;
    }

    /** is there a bell over this spot, and whose */
    public static boolean inside(Entity e) {
        for (Bell b : bells) if (b.level() == e.level() && e.position().distanceTo(b.at()) < RADIUS) return true;
        return false;
    }

    public static void stop(Player p) {
        bells.removeIf(b -> {
            if (!b.owner().equals(p.getUUID())) return false;
            tell(b.level(), b, 0);
            return true;
        });
    }

    public static void clear() {
        bells.clear();
    }

    /** everyone near hears of it, so their game draws the glass */
    private static void tell(ServerLevel l, Bell b, int ticks) {
        GlassBellPayload msg = new GlassBellPayload(b.at().x, b.at().y, b.at().z, (float) RADIUS, ticks);
        for (ServerPlayer o : l.players())
            if (o.position().distanceToSqr(b.at()) < 160 * 160
                    && ServerPlayNetworking.canSend(o, GlassBellPayload.TYPE))
                ServerPlayNetworking.send(o, msg);
    }

    public static void tick(MinecraftServer server) {
        if (bells.isEmpty()) return;
        Iterator<Bell> it = bells.iterator();
        while (it.hasNext()) {
            Bell b = it.next();
            ServerLevel l = b.level();
            if (l.getGameTime() >= b.until()) {
                it.remove();
                Vec3 c = b.at();
                l.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.4f, 1.2f);
                shimmer(l, b, 60);
                continue;
            }
            hold(server, b);
            if (l.getGameTime() % 10 == 0) shimmer(l, b, 10);
        }
    }

    /** one tick of the bell: what's outside stays out, and whoever is in it breathes */
    static void hold(MinecraftServer server, Bell b) {
        ServerLevel l = b.level();
        Vec3 c = b.at();
        AABB near = new AABB(c, c).inflate(RADIUS + 3);
        Player owner = server.getPlayerList().getPlayer(b.owner());
        for (Mob m : l.getEntitiesOfClass(Mob.class, near, Mob::isAlive)) {
            if (m instanceof OwnableEntity o && b.owner().equals(o.getOwnerUUID())) continue;
            if (Giants.isGiant(m) || m.isPassenger()) continue;
            Vec3 mid = m.position().add(0, m.getBbHeight() * 0.5, 0);
            Vec3 d = mid.subtract(c);
            double reach = RADIUS + m.getBbWidth() * 0.5;
            if (d.length() >= reach) continue;
            Vec3 out = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
            Vec3 to = c.add(out.scale(reach + 0.1)).subtract(0, m.getBbHeight() * 0.5, 0);
            m.teleportTo(to.x, Math.max(to.y, m.getY()), to.z);
            m.setDeltaMovement(out.x * 0.4, 0.1, out.z * 0.4);
            m.hurtMarked = true;
        }
        // other players are kept out too, but only when they could fight you anyway
        if (owner != null)
            for (ServerPlayer q : l.getEntitiesOfClass(ServerPlayer.class, near, q -> PowerTargets.ok(owner, q))) {
                Vec3 d = q.position().add(0, q.getBbHeight() * 0.5, 0).subtract(c);
                double reach = RADIUS + q.getBbWidth() * 0.5;
                if (d.length() >= reach || q.isPassenger()) continue;
                Vec3 out = d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
                Vec3 to = c.add(out.scale(reach + 0.1)).subtract(0, q.getBbHeight() * 0.5, 0);
                q.teleportTo(to.x, Math.max(to.y, q.getY()), to.z);
                q.setDeltaMovement(out.x * 0.4, 0.1, out.z * 0.4);
                q.hurtMarked = true;
            }
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, near, Entity::isAlive)) {
            Entity shooter = pr.getOwner();
            if (shooter != null && (shooter == owner || shooter.position().distanceTo(c) < RADIUS)) continue;
            if (pr.position().distanceTo(c) > RADIUS + 0.6) continue;
            // it rings off the glass and falls away
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, pr.getX(), pr.getY(), pr.getZ(), 6, 0.1, 0.1, 0.1, 0.1);
            l.playSound(null, pr.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.2f, 1.4f);
            pr.discard();
        }
        for (ServerPlayer in : l.players())
            if (in.position().distanceTo(c) < RADIUS && in.getAirSupply() < in.getMaxAirSupply())
                in.setAirSupply(in.getMaxAirSupply());
    }

    /** glints of glass across the dome */
    private static void shimmer(ServerLevel l, Bell b, int n) {
        for (int i = 0; i < n; i++) {
            double u = l.random.nextDouble() * Math.PI * 2;
            double v = Math.acos(l.random.nextDouble()); // the top half
            Vec3 dir = new Vec3(Math.sin(v) * Math.cos(u), Math.cos(v), Math.sin(v) * Math.sin(u));
            Vec3 at = b.at().add(dir.scale(RADIUS));
            l.sendParticles(new DustParticleOptions(GLASS, 1.0f), at.x, at.y, at.z, 1, 0, 0, 0, 0);
        }
    }
}
