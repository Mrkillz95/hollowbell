package net.jj.hollowbell.net;

import net.jj.hollowbell.world.BellWorld;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * "Go after what I look at", on the server: which creature the player meant (the one their screen found, checked
 * here, or a look of its own when it was sent none), whether he may go after it, and the glow on it that only they see.
 */
public final class LookOrder {
    private LookOrder() {}

    /** what the order found: the creature (a giant's part already turned into its giant), or why there is none */
    public record Found(@Nullable LivingEntity target, @Nullable String why) {}

    /**
     * The creature this player means. id is what their screen found (an entity id), or 0 or less for none, when the
     * server looks along their view itself.
     */
    public static Found find(ServerPlayer p, int id) {
        ServerLevel sl = p.serverLevel();
        Entity e;
        if (id > 0) {
            e = sl.getEntity(id);
            // (their screen saw it, but it isn't here: gone already, or in another world)
            if (e == null) return new Found(null, "look_cant_see");
        } else {
            e = LookTrace.trace(sl, p.getEyePosition(), p.getViewVector(1f), range(p), p, null);
            if (e == null) return new Found(null, "look_none");
        }
        LivingEntity t = LookTrace.ownerOf(e);
        if (t == null || !LookTrace.candidate(e)) return new Found(null, "look_none");
        if (t == p) return new Found(null, "look_you");
        if (t.level() != p.level()) return new Found(null, "look_cant_see");
        if (!t.isAlive() || t.isDeadOrDying() || t.isRemoved()) return new Found(null, "look_dead");
        // a look can't reach past what anybody's screen shows: a far id is somebody making things up
        if (e.distanceToSqr(p) > range(p) * range(p) + 256 * 256) return new Found(null, "look_cant_see");
        if (onSafeList(p, t)) return new Found(null, "look_safe");
        return new Found(t, null);
    }

    /** as far as the player can see, and never less than 512 blocks */
    public static double range(ServerPlayer p) {
        return Math.max(LookTrace.RANGE, p.server.getPlayerList().getViewDistance() * 16 + 32);
    }

    /** on this player's own safe list: a player or a creature on it, its whole kind, or their own tamed animal */
    public static boolean onSafeList(ServerPlayer p, LivingEntity t) {
        BellWorld w = BellWorld.get(p.server);
        if (w.onList(p.getUUID(), t.getUUID())) return true;
        if (t instanceof Player) return false;
        if (t instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) return true;
        return w.kindOnList(p.getUUID(), t.getType());
    }

    /** a refusal, said plainly (a key with ":n" carries a number for the message) */
    public static void refuse(ServerPlayer p, String why, @Nullable LivingEntity t) {
        String name = t == null ? "" : t.getDisplayName().getString();
        int c = why.indexOf(':');
        Component msg = c > 0 ? Component.translatable("message.hollowbell." + why.substring(0, c), name, why.substring(c + 1))
                : Component.translatable("message.hollowbell." + why, name);
        p.displayClientMessage(msg, true);
    }

    /** the glow on it, for this player only, and a little sparkle over it */
    public static void mark(ServerPlayer p, LivingEntity t) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new LookMarkPayload(t.getId()));
        double h = Math.min(t.getBbHeight(), 12);
        p.serverLevel().sendParticles(p, ParticleTypes.END_ROD, true, t.getX(), t.getY() + h + 0.5, t.getZ(), 12,
                Math.min(t.getBbWidth(), 6) * 0.4, 0.3, Math.min(t.getBbWidth(), 6) * 0.4, 0.02);
    }
}
