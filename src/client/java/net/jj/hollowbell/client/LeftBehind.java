package net.jj.hollowbell.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.jj.hollowbell.LandMusic;
import net.jj.hollowbell.fx.client.BigFxClient;
import net.jj.hollowbell.fx.client.MoveFx;
import net.jj.hollowbell.mixin.BiomeLoopsAccess;
import net.jj.hollowbell.mixin.PlayerAmbientAccess;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;

/**
 * (leak-fix, as the Tickmoor's) What dying, respawning or changing worlds used to leave behind on your game.
 *
 * <ul>
 *   <li><b>His land's ambient loop.</b> The Bell Hollows has a loop. The game plays a biome's
 *       loop through a handler that belongs to your player, and only that handler ever fades it out, as you leave the
 *       biome. Respawning in the same world gives you a new player: the old one's handler is never ticked again, so
 *       its loop went on for ever at full volume wherever you were, one more each time you died there, each holding
 *       one of the game's streaming channels (the ones music needs). Now, when your player is replaced, every loop the
 *       old one was playing fades out as if you'd walked out (two seconds); the new player's handler starts the loop
 *       again if you're still in a biome with one. Every giant's mod does this, for any biome: the first to notice
 *       marks the old player (LandMusic.claimLeftBehind) and the rest leave it be.</li>
 *   <li><b>Another world.</b> The game never tells a creature its world has gone: the ones of him from the last world
 *       stayed on the solid list (holding the whole old world in memory, and, as they have the same ids as the ones
 *       here, keeping those from counting); his hum and drift loops were kept by his id, so one here with the same id
 *       never got its own (the game had stopped the old ones without telling them); and his move effects and glass
 *       domes could go on at their old places. Now they are let go as the world changes, as his dust and sparks
 *       already were (FxWorld).</li>
 * </ul>
 * (Changing worlds and logging out stop every sound already; the music: LandMusic, FightMusic.)
 */
public final class LeftBehind {
    private LeftBehind() {}

    private static LocalPlayer lastPlayer;
    private static ClientLevel lastLevel;
    /** loops faded and worlds let go so far */
    static int loopsFaded, worldsLetGo;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(LeftBehind::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            lastPlayer = null;
            lastLevel = null;
        });
    }

    static void tick(Minecraft mc) {
        if (mc.player != lastPlayer) {
            if (lastPlayer != null && LandMusic.claimLeftBehind(lastPlayer)) loopsFaded += fadeOut(lastPlayer);
            lastPlayer = mc.player;
        }
        if (mc.level != lastLevel) {
            if (lastLevel != null) letGoOfTheLastWorld(mc.level);
            lastLevel = mc.level;
        }
    }

    /** every loop this (old) player's ambient handlers are playing: faded out; how many */
    public static int fadeOut(LocalPlayer p) {
        int n = 0;
        for (AmbientSoundHandler h : ((PlayerAmbientAccess) p).hollowbell$ambientHandlers()) {
            if (!(h instanceof BiomeAmbientSoundsHandler b)) continue;
            for (BiomeAmbientSoundsHandler.LoopSoundInstance s : ((BiomeLoopsAccess) b).hollowbell$loops().values()) {
                s.fadeOut();
                n++;
            }
        }
        return n;
    }

    /** the world changed: what belonged to the last one goes */
    static void letGoOfTheLastWorld(ClientLevel now) {
        worldsLetGo++;
        Solid.forgetOtherWorlds(now);
        BigFxClient.clear();
        MoveFx.clear();
        BellSounds.clear();
        GlassBellClient.clear();
    }
}
