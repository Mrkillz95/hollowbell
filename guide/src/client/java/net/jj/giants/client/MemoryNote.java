package net.jj.giants.client;

import net.jj.giants.GiantsGuideMod;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * (lagfix, JJ's "extreme lag" after a minigame) The game's memory too small for the giants and the distance it shows:
 * the launcher's 2 GB with a view distance of 32 spends most of its time collecting garbage (the game and its own
 * server both freeze over and over, worst after a minigame's or a far trip's land loads). Said once a game, in chat
 * and the log, with how to give it more.
 */
public final class MemoryNote {
    private MemoryNote() {}

    private static boolean said;

    /** the memory the giants want at this view distance (GB) */
    static int wanted(int viewDistance) {
        return viewDistance > 16 ? 6 : 4;
    }

    static void joined(Minecraft mc) {
        if (said || mc.player == null) return;
        long max = Runtime.getRuntime().maxMemory();
        int view = mc.options.renderDistance().get();
        int want = wanted(view);
        // (a few hundred MB under the round number still counts: -Xmx4G reports a little less)
        if (max >= (want - 0.5) * 1024L * 1024 * 1024) return;
        said = true;
        String has = String.format(java.util.Locale.ROOT, "%.1f", max / (1024.0 * 1024 * 1024));
        GiantsGuideMod.LOG.warn("Giants Guide: the game has {} GB of memory; with a view distance of {} the giants want"
                + " about {} GB (-Xmx{}G), or the game stutters as it frees memory", has, view, want, want);
        mc.player.displayClientMessage(Component.translatableWithFallback("guide.jj_giants.memory",
                "[Giants] This game has only %s GB of memory. At a view distance of %s the giants want about %s GB, or"
                        + " the game and its world freeze again and again. In the launcher: Installations, this one,"
                        + " More options, JVM arguments: change -Xmx2G to -Xmx%sG (or lower the view distance).",
                has, view, want, want).withStyle(ChatFormatting.GOLD), false);
    }
}
