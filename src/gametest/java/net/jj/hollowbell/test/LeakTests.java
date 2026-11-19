package net.jj.hollowbell.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.LandMusic;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.solid.Solid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;

/**
 * (leak-fix, as the Tickmoor's: "once I died and left the biome I could still hear its music and was lagging") His
 * land's music plays only while you're alive in his land and fades out once you leave or die; the biome loops a
 * respawn leaves behind are faded out once, whichever giant's mod sees it first; his fight theme doesn't hold once you
 * die and starts clean after the game stopped its sounds; one of him left on his lists from another world never hides
 * the one here (same id).
 */
public class LeakTests implements FabricGameTest {

    private static ResourceLocation id(String ns, String path) {
        return ResourceLocation.fromNamespaceAndPath(ns, path);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "leak_music_songs")
    public void onlyHisLandsTracksAreHisSongs(GameTestHelper h) {
        String me = HollowbellMod.MOD_ID;
        h.assertTrue(LandMusic.isHisSong(id(me, "music.ground")), "his land's music isn't his land's");
        for (String take : new String[] {"music.fight", "music.fight_dark", "music.fight_last", "music.fight_sunk"})
            h.assertTrue(!LandMusic.isHisSong(id(me, take)), take + " counted as his land's music");
        h.assertTrue(!LandMusic.isHisSong(id(me, "ambient.ground")), "his land's loop counted as music");
        h.assertTrue(!LandMusic.isHisSong(id("minecraft", "music.game")), "the game's music counted as his");
        h.assertTrue(!LandMusic.isHisSong(id("tickmoor", "music.stillfields")), "another giant's music counted as his");
        h.assertTrue(!LandMusic.isHisSong(null), "nothing counted as his music");
        var reg = h.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        for (String b : new String[] {"bell_hollows"}) {
            var k = ResourceKey.create(Registries.BIOME, id(me, b));
            var holder = reg.getHolderOrThrow(k);
            h.assertTrue(LandMusic.isHisLand(holder), b + " isn't his land");
            // (every one of his biomes' own music fades as you leave)
            var m = holder.value().getBackgroundMusic();
            h.assertTrue(m.isEmpty() || LandMusic.isHisSong(m.get().getEvent().value().getLocation()),
                    b + "'s music wouldn't fade as you leave");
        }
        h.assertTrue(reg.getHolderOrThrow(ResourceKey.create(Registries.BIOME, id(me, "bell_hollows")))
                .value().getBackgroundMusic().isPresent(), "the Bell Hollows has no music to fade (no good)");
        h.assertTrue(!LandMusic.isHisLand(reg.getHolderOrThrow(Biomes.PLAINS)), "plains counted as his land");
        h.assertTrue(!LandMusic.isHisLand(null), "nowhere counted as his land");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "leak_music_fade")
    public void hisLandsMusicFadesOutInFourSecondsAndComesBackInOne(GameTestHelper h) {
        float f = 1f;
        int n = 0;
        while (f > 0f && n < 1000) {
            f = LandMusic.fade(f, false);
            n++;
        }
        h.assertTrue(n == 80, "it took " + n + " ticks to fade out");
        // stepping back in halfway through: it comes back up, the track never stopped
        f = 0.5f;
        n = 0;
        while (f < 1f && n < 1000) {
            f = LandMusic.fade(f, true);
            n++;
        }
        h.assertTrue(n == 10, "it took " + n + " ticks to come back up from half");
        h.assertTrue(LandMusic.fade(1f, true) == 1f && LandMusic.fade(0f, false) == 0f, "the fade left 0..1");
        // a sound the game stopped itself (new world, resource reload) is gone though its own flag says it isn't
        h.assertTrue(LandMusic.gone(false, false), "a sound the engine stopped counts as playing");
        h.assertTrue(LandMusic.gone(true, true), "a sound that stopped itself counts as playing");
        h.assertTrue(!LandMusic.gone(false, true), "a playing sound counts as gone");
        h.succeed();
    }

    private static void run(GameTestHelper h, String cmd) {
        var server = h.getLevel().getServer();
        server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withLevel(h.getLevel()).withSuppressedOutput().withPermission(4), cmd);
    }

    /** round c, from its height up (the test ground lies at the bottom of the world: none of it below) */
    private static String box(BlockPos c, int r) {
        return String.format("%d %d %d %d %d %d", c.getX() - r, c.getY(), c.getZ() - r, c.getX() + r,
                c.getY() + r, c.getZ() + r);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "leak_music_wanted")
    public void hisLandsMusicIsWantedOnlyWhileYoureAliveInHisLand(GameTestHelper h) {
        BlockPos pad = h.absolutePos(new BlockPos(1, 2, 1));
        BlockPos away = pad.offset(48, 0, 0);
        ResourceLocation was = h.getLevel().getBiome(pad).unwrapKey().map(ResourceKey::location)
                .orElse(Biomes.PLAINS.location());
        run(h, "fillbiome " + box(pad, 8) + " " + HollowbellMod.MOD_ID + ":bell_hollows");
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        Player out = h.makeMockPlayer(GameType.SURVIVAL);
        p.setPos(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
        out.setPos(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(LandMusic.isHisLand(h.getLevel().getBiome(pad)), "the test's ground isn't his land (no good)");
                h.assertTrue(!LandMusic.isHisLand(h.getLevel().getBiome(away)), "the far spot is his land (no good)");
                h.assertTrue(LandMusic.wanted(p), "his music isn't wanted in his land");
                h.assertTrue(!LandMusic.wanted(out), "his music is wanted outside his land");
                h.assertTrue(!LandMusic.wanted(null), "his music is wanted with nobody there");
                // walking out: no longer wanted (the client's song fades out)
                p.setPos(away.getX() + 0.5, away.getY(), away.getZ() + 0.5);
                h.assertTrue(!LandMusic.wanted(p), "his music is still wanted after leaving his land");
                p.setPos(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5);
                h.assertTrue(LandMusic.wanted(p), "his music isn't wanted on coming back");
                // dying in his land: not wanted, so it fades and doesn't start again on the death screen
                p.setHealth(0f);
                h.assertTrue(!p.isAlive(), "the test player didn't die (no good)");
                h.assertTrue(!LandMusic.wanted(p), "his music is still wanted by a dead player");
            } finally {
                run(h, "fillbiome " + box(pad, 8) + " " + was);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "leak_solid_same_id")
    public void oneOfHimLeftFromAnotherWorldNeverHidesTheOneHere(GameTestHelper h) {
        HollowbellEntity e = h.spawn(ModEntities.HOLLOWBELL, new BlockPos(1, 2, 1));
        e.setBellScale(0.1f);
        h.runAfterDelay(5, () -> {
            // a stand-in for the one left over from the world you were in before: the same id, another creature
            HollowbellEntity old = ModEntities.HOLLOWBELL.create(h.getLevel());
            old.setId(e.getId());
            h.assertTrue(old.equals(e), "two of him with one id aren't equal to the game (the test means nothing)");
            Solid.untrack(e);
            Solid.track(old);
            Solid.track(e);
            boolean both = Solid.bodies(false).stream().anyMatch(b -> b == e)
                    && Solid.bodies(false).stream().anyMatch(b -> b == old);
            Solid.untrack(old);
            boolean stays = Solid.bodies(false).stream().anyMatch(b -> b == e);
            boolean gone = Solid.bodies(false).stream().noneMatch(b -> b == old);
            h.assertTrue(both, "the one here wasn't listed beside the leftover with his id");
            h.assertTrue(stays, "letting go of the leftover let go of the one here");
            h.assertTrue(gone, "the leftover is still listed");
            e.discard();
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "leak_left_behind_mark")
    public void aRespawnsLeftoversAreDealtWithOnceAcrossTheGiants(GameTestHelper h) {
        var props = System.getProperties();
        Object was = props.get(LandMusic.LEFT_BEHIND_KEY);
        try {
            props.remove(LandMusic.LEFT_BEHIND_KEY);
            Object a = new Object(), b = new Object(), c = new Object();
            h.assertTrue(LandMusic.claimLeftBehind(a), "the first to see a player go didn't get it");
            h.assertTrue(!LandMusic.claimLeftBehind(a), "a second mod got the same player's leftovers too");
            h.assertTrue(LandMusic.claimLeftBehind(b), "the next player to go wasn't dealt with");
            // another giant's mod marked this one first (the mark is the same in every mod)
            props.put(LandMusic.LEFT_BEHIND_KEY, new java.lang.ref.WeakReference<>(c));
            h.assertTrue(!LandMusic.claimLeftBehind(c), "this mod didn't see another giant's mark");
            // something else under the key (an old or foreign value): taken over, not broken
            props.put(LandMusic.LEFT_BEHIND_KEY, "junk");
            h.assertTrue(LandMusic.claimLeftBehind(a), "a foreign value under the mark stopped it");
        } finally {
            if (was == null) props.remove(LandMusic.LEFT_BEHIND_KEY);
            else props.put(LandMusic.LEFT_BEHIND_KEY, was);
        }
        h.succeed();
    }

    /** the client's mixins, as the jar lists them (a mixin left out of the list never runs) */
    private static List<String> clientMixins() throws IOException {
        Path root = Path.of(System.getProperty("user.dir")).resolve("../..").normalize();
        JsonArray a = JsonParser.parseString(Files.readString(
                        root.resolve("src/client/resources/hollowbell.client.mixins.json"), StandardCharsets.UTF_8))
                .getAsJsonObject()
                .getAsJsonArray("client");
        List<String> out = new ArrayList<>();
        a.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "leak_music_mixins")
    public void theFadeOutsAreHookedIn(GameTestHelper h) throws IOException {
        List<String> m = clientMixins();
        for (String need : new String[] {"BiomeMusicMixin", "LandMusicMixin", "PlayerAmbientAccess",
            "BiomeLoopsAccess", "MusicMixin"})
            h.assertTrue(m.contains(need), need + " isn't in the client's mixins");
        h.succeed();
    }
}
