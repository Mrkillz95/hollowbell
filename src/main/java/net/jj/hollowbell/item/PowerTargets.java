package net.jj.hollowbell.item;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * Who the armour powers and the Stinger may hurt or hold. Other players only when the server allows PvP (and the
 * teams let you), never you, your pets, your mount, villagers or golems. Powers that hit an area also leave animals
 * and armour stands alone; anything hostile is still fair game.
 */
public final class PowerTargets {
    private PowerTargets() {}

    /** may a power used by this player hurt or hold it */
    public static boolean ok(LivingEntity user, Entity e) {
        if (e == user || !e.isAlive()) return false;
        if (e instanceof Player q) {
            if (q.isCreative() || q.isSpectator()) return false;
            if (!(user instanceof Player u)) return true;
            MinecraftServer s = q.getServer();
            return s != null && s.isPvpAllowed() && u.canHarmPlayer(q);
        }
        if (e instanceof OwnableEntity o && user.getUUID().equals(o.getOwnerUUID())) return false;
        if (e.isPassengerOfSameVehicle(user)) return false;
        if (e instanceof Enemy) return true;
        return !(e instanceof AbstractVillager || e instanceof AbstractGolem);
    }

    /** the same, for a power that hits everything around: animals and armour stands are left out too */
    public static boolean area(LivingEntity user, Entity e) {
        if (!ok(user, e) || e instanceof ArmorStand) return false;
        if (e instanceof Enemy) return true;
        return !(e instanceof Animal || e instanceof WaterAnimal || e instanceof AmbientCreature);
    }

    /** how long a stun, pin or hold lasts on it: half as long on a player */
    public static int holdTicks(Entity e, int ticks) {
        return e instanceof Player ? ticks / 2 : ticks;
    }
}
