package net.jj.hollowbell;

import java.lang.ref.WeakReference;
import java.util.Properties;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * (leak-fix, as the Tickmoor's: "once I died and left the biome I could still hear its music and was lagging") His
 * land's music plays only while you are alive and standing in his land. The game's own music player lets a biome's
 * track play to its end wherever you go, and only another track that takes over cuts it short, which the game's
 * overworld music never does; so his track is played as his own sound (the client's LandSong), which fades out over
 * four seconds once you leave his land, die or respawn somewhere else, and comes back up if you step in again before
 * it's gone. Changing worlds and logging out stop every sound as ever.
 *
 * <p>The same rules decide whether his music starts at all (BiomeMusicMixin), so it never starts again on the
 * death screen. The parts here are the client's rules, kept where the game tests can reach them.
 */
public final class LandMusic {
    private LandMusic() {}

    /** ticks to come up when you step back in, and to fade right out once you've left */
    public static final float FADE_IN = 20f, FADE_OUT = 80f;

    /**
     * the mark every giant's mod shares (in the system properties, like the music board) for the last player whose
     * left-behind biome loops were faded out, so only the first mod to notice a respawn does it (LeftBehind)
     */
    public static final String LEFT_BEHIND_KEY = "jj.giants.leftBehind";

    /** one of his land's tracks (not his fight theme, which plays its own way: FightMusic) */
    public static boolean isHisSong(@Nullable ResourceLocation sound) {
        return sound != null && sound.getNamespace().equals(HollowbellMod.MOD_ID) && sound.getPath().startsWith("music.")
                && !sound.getPath().startsWith("music.fight");
    }

    /** one of his land's biomes */
    public static boolean isHisLand(@Nullable Holder<Biome> b) {
        return b != null && b.unwrapKey().map(k -> k.location().getNamespace().equals(HollowbellMod.MOD_ID)).orElse(false);
    }

    /** should his land's music be playing for this player now: alive, and standing in his land */
    public static boolean wanted(@Nullable Player p) {
        return p != null && !p.isRemoved() && p.isAlive() && isHisLand(p.level().getBiome(p.blockPosition()));
    }

    /** one tick of the fade (0..1): up while wanted, down while not */
    public static float fade(float f, boolean want) {
        // (snapped at the ends: 80 steps of 1/80 leave a crumb of float behind)
        float n = want ? f + 1f / FADE_IN : f - 1f / FADE_OUT;
        return n > 1f - 1e-4f ? 1f : n < 1e-4f ? 0f : n;
    }

    /**
     * has a looping sound of ours gone for good: stopped by us, or no longer playing in the game's sound engine (it
     * stops every sound on changing worlds, on a resource reload, and when it couldn't start one at all, without
     * telling the sound itself)
     */
    public static boolean gone(boolean stopped, boolean active) {
        return stopped || !active;
    }

    /**
     * this player has been replaced (a respawn): is this mod the first of the giants' mods to deal with what it left
     * behind? Marks it for the others. (Fading the loops twice does no harm either: the game's fade-out only ever
     * turns them down.)
     */
    public static boolean claimLeftBehind(Object oldPlayer) {
        Properties p = System.getProperties();
        synchronized (p) {
            Object o = p.get(LEFT_BEHIND_KEY);
            if (o instanceof WeakReference<?> w && w.get() == oldPlayer) return false;
            p.put(LEFT_BEHIND_KEY, new WeakReference<>(oldPlayer));
            return true;
        }
    }
}
