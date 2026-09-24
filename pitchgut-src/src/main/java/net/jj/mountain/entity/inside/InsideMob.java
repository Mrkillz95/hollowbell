package net.jj.mountain.entity.inside;

import net.jj.mountain.entity.GooGlob;
import net.jj.mountain.entity.HeartEntity;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.innards.Innards;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Things that live inside him: his acid doesn't burn them, and they only go for the living visitors. */
public interface InsideMob {
    static boolean fairGame(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || e instanceof InsideMob || e instanceof HeartEntity || e instanceof MountainEntity) return false;
        if (e instanceof Player p) return !p.isCreative() && !p.isSpectator();
        return true;
    }

    /** as fairGame, but a guardian Mountain's creatures leave players alone unless he's been provoked */
    static boolean fairGame(Entity e, @org.jetbrains.annotations.Nullable MountainEntity summoner) {
        return fairGame(e) && (summoner == null || !summoner.spares(e));
    }

    /** true when this one is inside a room of his (they also work anywhere else, for testing) */
    default boolean inGut(Entity self) { return Innards.isInside(self); }

    static boolean isGlob(Entity e) { return e instanceof GooGlob; }
}
