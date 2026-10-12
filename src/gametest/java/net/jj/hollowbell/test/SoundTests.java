package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModSounds;
import net.jj.hollowbell.MusicBoard;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

/** His sounds and the fight music. */
public class SoundTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "sounds")
    public void hisSoundsAreAllThere(GameTestHelper h) {
        var json = JsonParser.parseReader(new InputStreamReader(
                        HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/sounds.json"),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
        var lang = JsonParser.parseReader(new InputStreamReader(
                        HollowbellGameTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json"),
                        StandardCharsets.UTF_8))
                .getAsJsonObject();
        for (var ev : ModSounds.ALL) {
            var id = ev.getLocation();
            h.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(id), "not registered: " + id);
            h.assertTrue(json.has(id.getPath()), "no sounds.json entry for " + id);
            var o = json.getAsJsonObject(id.getPath());
            h.assertTrue(o.getAsJsonArray("sounds").size() > 0, "no sounds for " + id);
            h.assertTrue(lang.has(o.get("subtitle").getAsString()), "no subtitle text for " + id);
            for (var snd : o.getAsJsonArray("sounds")) {
                String name = snd.getAsJsonObject().get("name").getAsString();
                if (name.startsWith("hollowbell:"))
                    h.assertTrue(
                            HollowbellGameTests.class.getResource(
                                            "/assets/hollowbell/sounds/" + name.substring(11) + ".ogg")
                                    != null,
                            "missing file for " + name);
            }
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sounds_files")
    public void everySoundFileIsThereAndDecodes(GameTestHelper h) {
        try (var in = SoundTests.class.getResourceAsStream("/assets/hollowbell/sounds.json")) {
            h.assertTrue(in != null, "no sounds.json");
            var json = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject();
            int own = 0;
            for (var ev : json.entrySet()) {
                for (var snd : ev.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = snd.getAsJsonObject().get("name").getAsString();
                    if (!name.startsWith("hollowbell:")) continue;
                    String path = "/assets/hollowbell/sounds/" + name.substring(11) + ".ogg";
                    try (var o = SoundTests.class.getResourceAsStream(path)) {
                        h.assertTrue(o != null, ev.getKey() + ": " + path + " is missing");
                        byte[] head = o.readNBytes(64);
                        h.assertTrue(
                                head.length >= 40
                                        && head[0] == 'O'
                                        && head[1] == 'g'
                                        && head[2] == 'g'
                                        && head[3] == 'S',
                                path + " isn't an Ogg file");
                        // the Vorbis header: packet type 1, "vorbis", version, channels, sample rate
                        int at = 27 + (head[26] & 0xff);
                        h.assertTrue(
                                head[at] == 1
                                        && new String(head, at + 1, 6, StandardCharsets.US_ASCII).equals("vorbis"),
                                path + " isn't Vorbis");
                        int ch = head[at + 11] & 0xff;
                        int rate = (head[at + 12] & 0xff)
                                | (head[at + 13] & 0xff) << 8
                                | (head[at + 14] & 0xff) << 16
                                | (head[at + 15] & 0xff) << 24;
                        h.assertTrue(
                                ch >= 1 && ch <= 2 && rate >= 8000 && rate <= 96000,
                                path + ": " + ch + " channels at " + rate);
                        // a sound placed in the world must be mono (only then does it get quieter with distance)
                        if (!name.contains("music/") && !name.contains("ambient/ground"))
                            h.assertTrue(ch == 1, path + " should be mono");
                        own++;
                    }
                }
            }
            h.assertTrue(own >= 15, "too few of his own sounds: " + own);
        } catch (IOException ex) {
            h.fail(ex.toString());
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sounds_board")
    public void onlyOneFightThemeAtATime(GameTestHelper h) {
        var b = new HashMap<String, double[]>();
        long now = 1_000_000L;
        h.assertTrue(!MusicBoard.plays(b, "hollowbell", now), "plays with nothing on the board");
        b.put("hollowbell", new double[] {400, now});
        h.assertTrue(MusicBoard.plays(b, "hollowbell", now), "alone, it should play");
        b.put("furrowmaw", new double[] {100, now - 500});
        h.assertTrue(!MusicBoard.plays(b, "hollowbell", now), "a nearer fight's theme should win");
        h.assertTrue(MusicBoard.plays(b, "furrowmaw", now), "the nearer one should play");
        b.put("furrowmaw", new double[] {100, now - 2500});
        h.assertTrue(MusicBoard.plays(b, "hollowbell", now), "a stale entry shouldn't count");
        b.put("fire_ice_cerberus", new double[] {400, now});
        h.assertTrue(
                MusicBoard.plays(b, "fire_ice_cerberus", now) && !MusicBoard.plays(b, "hollowbell", now),
                "a tie should go to the smaller mod id");
        h.assertTrue(MusicBoard.anyFresh(b, now) && !MusicBoard.anyFresh(b, now + 5000), "fresh");
        // the shared board lives in the system properties, where the other four mods find it
        MusicBoard.want("hollowbell-test", 1);
        h.assertTrue(
                System.getProperties().get("jj.giants.music") instanceof Map<?, ?> m
                        && m.containsKey("hollowbell-test"),
                "not on the shared board");
        MusicBoard.drop("hollowbell-test");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "sounds_fight_flag")
    public void heSaysWhenAFightIsOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 333);
        h.runAfterDelay(20, () -> {
            h.assertTrue(!e.fighting(), "fighting nobody");
            ServerPlayer p = HollowbellGameTests.player(h, under(e).add(8, 0, 0));
            e.setTarget(p);
            h.runAfterDelay(25, () -> {
                h.assertTrue(e.fighting(), "after a player, the fight should be on");
                drop(p);
                release(h, e);
                h.succeed();
            });
        });
    }
}
