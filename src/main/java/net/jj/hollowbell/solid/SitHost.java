package net.jj.hollowbell.solid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A giant you can sit on (the solid kit's "sit anywhere"): the two creature-specific parts. Who may sit on him, and how
 * he sets someone down on clear ground beside him (his own set-down, the one "ride" again uses).
 */
public interface SitHost extends SolidBody {
    /**
     * May this player sit on him: he has an owner (whoever holds his book) and they are the owner or on the owner's
     * safe list. A wild giant: nobody.
     */
    boolean sitAllowed(Player p);

    /** onto clear ground beside him, never under or inside him, no fall damage */
    void sitSetDown(LivingEntity who);

    /** he is dying (his own death, if he has one): whoever sits on him is set down */
    default boolean sitDying() {
        return solidSelf() instanceof LivingEntity le && le.isDeadOrDying();
    }
}
