package net.jj.hollowbell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.12) A minigame's world: the Giants Guide makes one for each minigame round
 * (jj_giants:mg_...) and throws it away when the round ends. A giant there belongs to the round, not to this world:
 * he never counts toward Most at once (maxInWorld), is never held to it, and never holds back the world's own one
 * or his companions. The same small class in every giant.
 */
public final class GiantsMinigame {
    private GiantsMinigame() {}

    /** (the tests) another world that counts as a minigame's (the Nether), so no Guide is needed to test it */
    public static volatile @Nullable ResourceLocation testWorld;

    public static boolean in(@Nullable ResourceLocation id) {
        if (id == null) return false;
        if (id.equals(testWorld)) return true;
        return id.getNamespace().equals("jj_giants") && id.getPath().startsWith("mg_");
    }

    public static boolean in(@Nullable Level l) {
        return l != null && in(l.dimension().location());
    }

    public static boolean in(@Nullable Entity e) {
        return e != null && in(e.level());
    }

    public static boolean in(@Nullable String dim) {
        return dim != null && in(ResourceLocation.tryParse(dim));
    }
}
