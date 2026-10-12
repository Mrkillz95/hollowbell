package net.jj.hollowbell;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Advancements granted from code. */
public final class Adv {
    private Adv() {}

    /** gives the player this mod's advancement of that name, every criterion of it */
    public static void grant(ServerPlayer p, String name) {
        var adv = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, name));
        if (adv == null) return;
        var progress = p.getAdvancements().getOrStartProgress(adv);
        for (String c : progress.getRemainingCriteria()) p.getAdvancements().award(adv, c);
    }
}
