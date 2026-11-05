package net.jj.giants.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;

/**
 * (1.15) The Guide's say over a giant's own boss bars, so a screen never stacks five of them: while a staged fight
 * shows its one bar, the fighters' own bars are hidden ("jj_giants.bars.off=<until>"); in a minigame each player sees
 * the bars of the nearest giant or two only ("jj_giants.bars.for=<until>:<player ids>"). Each tag holds till a game
 * time of his own world, so one left behind (a crash) runs out by itself; the giant's own bar code reads it
 * (GiantsFight.barHidden in every giant).
 */
public final class GiantBars {
    private GiantBars() {}

    public static final String HEAD = "jj_giants.bars.", OFF = HEAD + "off=", FOR = HEAD + "for=";

    /** till when a tag written now holds: a few seconds on, in steps (so it isn't written again every second) */
    static long until(LivingEntity e) {
        return (e.level().getGameTime() / 100 + 2) * 100;
    }

    /** his own bars hidden from everyone for a while */
    public static void hide(LivingEntity e) {
        put(e, OFF + until(e));
    }

    /** his own bars shown only to these players for a while (none: to nobody) */
    public static void only(LivingEntity e, Collection<UUID> players) {
        List<String> ids = new ArrayList<>();
        for (UUID u : players) ids.add(u.toString());
        java.util.Collections.sort(ids);
        put(e, FOR + until(e) + ":" + String.join(",", ids));
    }

    /** his own bars as always */
    public static void clear(LivingEntity e) {
        put(e, null);
    }

    private static void put(LivingEntity e, @org.jetbrains.annotations.Nullable String tag) {
        if (tag != null && e.getTags().contains(tag)) return;
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(HEAD)) e.removeTag(t);
        if (tag != null) e.addTag(tag);
    }
}
