package net.jj.giants.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakefight.GiantsBridge;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.admin.ShareCodes;
import net.jj.giants.editor.Editor;
import net.jj.giants.editor.EditorWorld;
import net.jj.giants.editor.LookSets;
import net.jj.giants.editor.SoundCatalog;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.animal.IronGolem;

/**
 * Part 12 (ADMIN_BRIDGE.md §29), the Guide's side: share codes (a round trip gives back exactly what went in, damaged,
 * cut short, newer and plain wrong codes say so, settings this version doesn't know are left out and named, old GA1
 * strings still import); the sound and music picker's settings (as shipped nothing is sent; set, they reach every
 * player's game; values checked; presets and export carry them); the material sets (each built-in set worked out for a
 * palette: shade kept, glow, glass and shapes kept, only blocks the game knows); and the giant editor's looks kept for a
 * kind, a tag and one giant (his own first, then his tag's, then his kind's), custom sets, the log, the page.
 */
public class EditorGameTests implements FabricGameTest {

    /** a bundle with a bit of everything: all-giants values, a giant's, a name, keep, not replace */
    static AdminTransfer.Bundle sample() {
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>();
        Map<String, JsonElement> all = new LinkedHashMap<>();
        all.put("all.health", new JsonPrimitive(2.5));
        all.put("all.damage", new JsonPrimitive(1.35));
        all.put("all.music.wreckback", new JsonPrimitive("gloamwick"));
        all.put("all.ambient.pitchgut", new JsonPrimitive(false));
        all.put("all.voicePitch.cerberus", new JsonPrimitive(0.75));
        all.put("all.myGiant", new JsonPrimitive(true));
        v.put("all", all);
        Map<String, JsonElement> wb = new LinkedHashMap<>();
        wb.put("healthMultiplier", new JsonPrimitive(3));
        wb.put("move.claw_snip.on", new JsonPrimitive(false));
        wb.put("spawnSize", com.google.gson.JsonParser.parseString("[0.5,2]"));
        v.put("wreckback", wb);
        return new AdminTransfer.Bundle("big_night", "Big night", "For Friday's event", false, v, List.of("worldgen"));
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_codes", timeoutTicks = 100)
    public void shareCodesComeBackExactly(GameTestHelper h) {
        AdminTransfer.Bundle b = sample();
        String code = ShareCodes.encode(b);
        h.assertTrue(code.startsWith("GS1-") && ShareCodes.looksLike(code), "a GS1 code: " + code);
        ShareCodes.Read r = ShareCodes.read(code);
        h.assertTrue(r.bundle() != null, "read back: " + r.why());
        AdminTransfer.Bundle back = r.bundle();
        h.assertTrue(back.values().equals(b.values()), "the same values: " + back.values());
        h.assertTrue(back.name().equals(b.name()) && back.replace() == b.replace() && back.keep().equals(b.keep())
                && back.id().equals(b.id()), "the same name, preset, replace and keep");
        h.assertTrue(back.about().startsWith(b.about()) && back.about().contains("share code"), back.about());
        // (exactly what went in: written out again, the same text but the note)
        AdminTransfer.Bundle noNote = new AdminTransfer.Bundle(back.id(), back.name(), b.about(), back.replace(),
                back.values(), back.keep());
        h.assertTrue(AdminTransfer.write(noNote).equals(AdminTransfer.write(b)), "identical JSON");
        // shorter than the export string of the same, and pasted with spaces or line breaks it still reads
        String ga1 = AdminTransfer.encode(b);
        h.assertTrue(code.length() < ga1.length(), "shorter: " + code.length() + " vs " + ga1.length());
        String broken = code.substring(0, 20) + "\n  " + code.substring(20);
        h.assertTrue(ShareCodes.read(broken).bundle() != null, "line breaks and spaces are ignored");
        // through the import path too (the command and the book)
        h.assertTrue(AdminTransfer.decode(code) != null && AdminTransfer.decode(code).values().equals(b.values()),
                "AdminTransfer.decode reads a share code");
        // an empty world's settings
        AdminTransfer.Bundle empty = new AdminTransfer.Bundle("export", "", "", true, new LinkedHashMap<>());
        ShareCodes.Read er = ShareCodes.read(ShareCodes.encode(empty));
        h.assertTrue(er.bundle() != null && er.bundle().values().isEmpty() && er.bundle().replace(), "empty: " + er.why());
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_codes", timeoutTicks = 100)
    public void badAndOldCodesSaySo(GameTestHelper h) {
        String code = ShareCodes.encode(sample());
        // a letter changed in the middle (any letter: the check catches it)
        int at = code.length() / 2;
        char c = code.charAt(at);
        String flipped = code.substring(0, at) + (c == 'A' ? 'B' : 'A') + code.substring(at + 1);
        ShareCodes.Read r = ShareCodes.read(flipped);
        h.assertTrue(r.bundle() == null && r.why().contains("damaged"), "a changed letter: " + r.why());
        // the check itself changed
        String check = code.substring(0, 5) + (code.charAt(5) == 'A' ? 'B' : 'A') + code.substring(6);
        h.assertTrue(ShareCodes.read(check).bundle() == null, "a changed check");
        // cut short
        h.assertTrue(ShareCodes.read(code.substring(0, code.length() - 6)).bundle() == null, "cut short");
        h.assertTrue(ShareCodes.read("GS1-abc").why().contains("too short"), ShareCodes.read("GS1-abc").why());
        // a newer kind of code
        ShareCodes.Read nw = ShareCodes.read("GS2-" + code.substring(4));
        h.assertTrue(nw.bundle() == null && nw.why().contains("newer"), nw.why());
        // not a code at all
        h.assertTrue(!ShareCodes.looksLike("hello") && ShareCodes.read("hello").why().contains("isn't a share code"),
                "not a code");
        h.assertTrue(AdminTransfer.decode("GS1-!!!!!!!!!!") == null, "garbage reads as nothing");
        // the old GA1 export still imports
        AdminTransfer.Bundle old = AdminTransfer.decode(AdminTransfer.encode(sample()));
        h.assertTrue(old != null && old.values().equals(sample().values()), "GA1 still reads");
        // a code from a newer Guide: read as far as this one can, and the preview names what it leaves out
        Map<String, Map<String, JsonElement>> v = new LinkedHashMap<>(sample().values());
        Map<String, JsonElement> all = new LinkedHashMap<>(v.get("all"));
        all.put("all.fromTheFuture", new JsonPrimitive(7));
        v.put("all", all);
        v.put("hearthstride", Map.of("walkSpeed", new JsonPrimitive(2)));
        String json = AdminTransfer.write(new AdminTransfer.Bundle("future", "Future", "", false, v, List.of()))
                .replace("{\"admin\":1", "{\"admin\":9");
        AdminTransfer.Bundle fb = AdminTransfer.read(json);
        h.assertTrue(fb == null, "a newer bundle isn't read as a GA1 string");
        String futureCode = ShareCodes.encode(new AdminTransfer.Bundle("future", "Future", "", false, v, List.of()));
        ShareCodes.Read fr = ShareCodes.read(futureCode);
        h.assertTrue(fr.bundle() != null, fr.why());
        MinecraftServer s = h.getLevel().getServer();
        AdminTransfer.Plan plan = AdminTransfer.plan(s, fr.bundle());
        String skipped = String.join(" | ", plan.skipped());
        h.assertTrue(skipped.contains("all.fromTheFuture") && skipped.contains("hearthstride"),
                "unknown settings and giants are left out and named: " + skipped);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_sound", timeoutTicks = 100)
    public void soundSettingsReachThePlayers(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminWorld w = AdminWorld.get(s);
        for (var x : SoundCatalog.SETTINGS) w.remove("all", x.id());
        AdminServer.push(s, w);
        // as shipped: nothing to send, every one a systems setting kept by the Guide, live
        h.assertTrue(SoundCatalog.SETTINGS.size() == 32, "4 for each of the 8: " + SoundCatalog.SETTINGS.size());
        for (var x : SoundCatalog.SETTINGS)
            h.assertTrue(AdminCatalog.shared(x.id()) != null && AdminCatalog.systems(x) && x.when().equals("live"),
                    x.id());
        JsonObject none = SoundCatalog.sync(s);
        h.assertTrue(none.getAsJsonObject("giants").isEmpty(), "as shipped nothing is sent: " + none);
        // set (as the book's rows do): the sync says it
        h.assertTrue(AdminServer.set(s, "Tester", "all", "all.music.wreckback", "gloamwick").ok(), "music");
        h.assertTrue(AdminServer.set(s, "Tester", "all", "all.ambient.pitchgut", "off").ok(), "ambient");
        h.assertTrue(AdminServer.set(s, "Tester", "all", "all.ambientVolume.hollowbell", "0.5").ok(), "volume");
        h.assertTrue(AdminServer.set(s, "Tester", "all", "all.voicePitch.cerberus", "1.5").ok(), "pitch");
        JsonObject sync = SoundCatalog.sync(s);
        h.assertTrue(SoundCatalog.of(sync, "wreckback", "music").getAsString().equals("gloamwick"), sync.toString());
        h.assertTrue(!SoundCatalog.of(sync, "pitchgut", "ambient").getAsBoolean(), sync.toString());
        h.assertTrue(SoundCatalog.of(sync, "hollowbell", "ambientVolume").getAsDouble() == 0.5, sync.toString());
        h.assertTrue(SoundCatalog.of(sync, "cerberus", "voicePitch").getAsDouble() == 1.5, sync.toString());
        h.assertTrue(SoundCatalog.of(sync, "furrowmaw", "music") == null, "the rest as shipped: " + sync);
        // values checked
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "all.music.wreckback", "loud").ok(), "music must be a choice");
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "all.voicePitch.cerberus", "3").ok(), "pitch 0.5-2");
        h.assertTrue(!AdminServer.set(s, "Tester", "all", "all.ambientVolume.hollowbell", "-1").ok(), "volume 0-2");
        // logged, exported, and a share code of the world carries them
        h.assertTrue(w.log().stream().anyMatch(c -> c.id().equals("all.music.wreckback")), "logged");
        AdminTransfer.Bundle now = AdminTransfer.current(s);
        h.assertTrue(now.values().get("all").get("all.music.wreckback").getAsString().equals("gloamwick"), "export");
        AdminTransfer.Bundle back = ShareCodes.read(ShareCodes.encode(now)).bundle();
        h.assertTrue(back != null && back.values().equals(now.values()), "the share code carries them");
        // a preset that names them sets them; Classic (a replace) puts them back
        Map<String, Map<String, JsonElement>> pv = new LinkedHashMap<>();
        pv.put("all", Map.of("all.music.furrowmaw", new JsonPrimitive("off")));
        AdminTransfer.Plan p = AdminTransfer.plan(s, new AdminTransfer.Bundle("quiet_worm", "Quiet worm", "", false, pv));
        AdminTransfer.apply(s, "Tester", p);
        h.assertTrue(SoundCatalog.of(SoundCatalog.sync(s), "furrowmaw", "music").getAsString().equals("off"),
                "a preset's music");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        h.assertTrue(SoundCatalog.sync(s).getAsJsonObject("giants").isEmpty(), "Classic puts them back: "
                + SoundCatalog.sync(s));
        // what each sound is
        h.assertTrue(SoundCatalog.kindOf("mountain_breathes").equals("pitchgut") && SoundCatalog.kindOf("minecraft")
                == null, "namespaces");
        h.assertTrue(SoundCatalog.fightMusic("music.fight_dark") && !SoundCatalog.fightMusic("music.ground"), "music");
        h.assertTrue(SoundCatalog.trackFor("wreckback", "music.fight_last", "gloamwick").equals("music.fight_last")
                && SoundCatalog.trackFor("wreckback", "music.fight_sea", "gloamwick").equals("music.fight_eclipse")
                && SoundCatalog.trackFor("wreckback", "music.fight_sea", "cerberus").equals("music.fight_dark"),
                "each part to the same part");
        h.assertTrue(SoundCatalog.ambient("cerberus", "mood.frost") && SoundCatalog.ambient("wreckback",
                "ambient.shallows") && !SoundCatalog.ambient("wreckback", "roar"), "ground sounds");
        h.assertTrue(SoundCatalog.voice("wreckback", "roar") && !SoundCatalog.voice("wreckback", "cannon"), "voice");
        for (var x : SoundCatalog.SETTINGS) w.remove("all", x.id());
        AdminServer.push(s, w);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_sets", timeoutTicks = 100)
    public void setsKeepShadeGlowGlassAndShapes(GameTestHelper h) {
        String[] palette = {"minecraft:black_concrete", "minecraft:stone", "minecraft:white_concrete",
            "minecraft:glowstone", "minecraft:glass", "minecraft:oak_fence[east=true,north=false,south=false,"
                    + "waterlogged=false,west=true]", "minecraft:chain[axis=y]", "minecraft:oak_log[axis=x]",
            "minecraft:red_concrete", "minecraft:lantern[hanging=true]"};
        double[] share = {0.3, 0.3, 0.3, 0.02, 0.02, 0.02, 0.01, 0.02, 0.005, 0.005};
        for (String set : LookSets.BUILT_IN) {
            Map<String, String> m = LookSets.map(set, palette, share);
            h.assertTrue(m.size() >= 4, set + " changes most of them: " + m);
            for (var e : m.entrySet())
                h.assertTrue(LookSets.state(e.getValue()) != null, set + ": " + e.getValue() + " is a block");
            h.assertTrue(!m.containsKey("minecraft:chain[axis=y]"), set + ": the chain stays");
            h.assertTrue(m.equals(LookSets.map(set, palette, share)), set + ": the same every time");
            // what glows still glows, a light block stays lighter than a dark one
            String glow = m.get("minecraft:glowstone");
            h.assertTrue(glow != null && LookSets.state(glow).getLightEmission() > 0, set + ": glow " + glow);
            int dark = LookSets.rgb(m.get("minecraft:black_concrete")), light = LookSets.rgb(m.get(
                    "minecraft:white_concrete"));
            h.assertTrue(lum(dark) < lum(light), set + ": shade kept");
        }
        Map<String, String> frost = LookSets.map("frost", palette, share);
        h.assertTrue(frost.get("minecraft:glass").equals("minecraft:light_blue_stained_glass"), "frost glass");
        h.assertTrue(frost.get(palette[5]).startsWith("minecraft:warped_fence[") && frost.get(palette[5]).contains(
                "east=true"), "a fence stays a fence, turned the same: " + frost.get(palette[5]));
        h.assertTrue(frost.get("minecraft:lantern[hanging=true]").equals("minecraft:soul_lantern[hanging=true]"),
                "frost lantern");
        h.assertTrue(frost.get("minecraft:black_concrete").equals("minecraft:blue_ice")
                && frost.get("minecraft:white_concrete").equals("minecraft:snow_block"), "frost ends: " + frost);
        // (1.16) Frost leans light: even his darkest block is a pale icy one
        for (String k : List.of("minecraft:black_concrete", "minecraft:stone", "minecraft:oak_log[axis=x]"))
            h.assertTrue(lum(LookSets.rgb(frost.get(k))) > 140, "frost is light: " + k + " -> " + frost.get(k));
        // (1.16) Ember and Crystal are built in too
        h.assertTrue(LookSets.BUILT_IN.containsAll(List.of("ember", "crystal")), "ember and crystal");
        // (1.16) followers take their giant's look's colour
        h.assertTrue(LookSets.tint(EditorWorld.get(h.getLevel().getServer()), "frost") == 0xD6ECFF
                && LookSets.tint(EditorWorld.get(h.getLevel().getServer()), "") == -1, "follower tints");
        h.assertTrue(LookSets.map("ember", palette, share).get("minecraft:glass").equals(
                "minecraft:orange_stained_glass"), "ember glass");
        h.assertTrue(LookSets.map("crystal", palette, share).get(palette[5]).startsWith("minecraft:cherry_fence["),
                "crystal fence");
        Map<String, String> gold = LookSets.map("gold", palette, share);
        h.assertTrue(gold.get("minecraft:white_concrete").startsWith("minecraft:smooth_sandstone")
                || gold.get("minecraft:white_concrete").equals("minecraft:gold_block"), "gold's light: " + gold);
        // a small strongly coloured part takes the accent (gold: rubies)
        h.assertTrue(gold.getOrDefault("minecraft:red_concrete", "minecraft:red_concrete").contains("red")
                && !gold.getOrDefault("minecraft:red_concrete", "").contains("terracotta"), "accent: " + gold);
        // custom: a base and the admin's own blocks on top; "same" as the block itself takes it back off
        Map<String, String> c = LookSets.custom("frost", Map.of("minecraft:stone", "minecraft:gold_block",
                "minecraft:white_concrete", "minecraft:white_concrete"), palette, share);
        h.assertTrue(c.get("minecraft:stone").equals("minecraft:gold_block"), "own block on top");
        h.assertTrue(!c.containsKey("minecraft:white_concrete"), "kept as it is");
        h.assertTrue(c.get("minecraft:glass").equals("minecraft:light_blue_stained_glass"), "the rest from frost");
        // a look's key changes with its blocks (a changed custom set is drawn afresh)
        h.assertTrue(!LookSets.key("custom:A", c).equals(LookSets.key("custom:A", frost)), "keys");
        // blocks typed in
        h.assertTrue("minecraft:gold_block".equals(LookSets.valid("gold_block")) && LookSets.valid("goldd_block")
                == null && LookSets.valid("minecraft:oak_stairs[facing=east]") != null, "valid blocks");
        h.succeed();
    }

    private static double lum(int rgb) {
        return 0.299 * (rgb >> 16 & 255) + 0.587 * (rgb >> 8 & 255) + 0.114 * (rgb & 255);
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_looks", timeoutTicks = 200)
    public void looksAreKeptForKindTagAndOneGiant(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = FightGameTests.fresh(h);
        EditorWorld w = EditorWorld.get(s);
        w.s = new EditorWorld.State();
        IronGolem g = GiantsBridge.spawn(h.getLevel(), h.absolutePos(net.minecraft.core.BlockPos.ZERO).getX() + 2,
                h.absolutePos(net.minecraft.core.BlockPos.ZERO).getY() + 1,
                h.absolutePos(net.minecraft.core.BlockPos.ZERO).getZ() + 2);
        h.assertTrue(FunGiants.kindOf(g) != null, "a stand-in giant");
        h.assertTrue(Editor.lookOf(s, g).isEmpty(), "nothing set: his own look");
        // his kind
        AdminServer.Result r = Editor.setKind(s, "Tester", "fakefight", "frost");
        h.assertTrue(r.ok() && r.lines().get(0).contains("Frost"), r.lines().toString());
        h.assertTrue(w.s.kinds.get("fakefight").equals("frost") && Editor.lookOf(s, g).equals("frost"), "kind");
        // a giant tag beats his kind
        FunWorld.Tag lt = new FunWorld.Tag();
        lt.name = "Look test";
        FunWorld.get(s).s.tags.put(lt.name, lt);
        g.addTag("jj_giants.tag=Look test");
        r = Editor.setTag(s, "Tester", "look test", "Gold");
        h.assertTrue(r.ok() && Editor.lookOf(s, g).equals("gold"), "tag: " + r.lines());
        // his own beats both
        r = Editor.setGiant(s, "Tester", g, "shadow");
        h.assertTrue(r.ok() && Editor.lookOf(s, g).equals("shadow"), "his own: " + r.lines());
        h.assertTrue(w.s.giants.get(g.getStringUUID()).kind.equals("fakefight"), "kept with his kind");
        // the page lists all three
        JsonObject page = Editor.state(s);
        h.assertTrue(page.getAsJsonObject("kinds").get("fakefight").getAsString().equals("frost")
                && page.getAsJsonObject("tags").get("Look test").getAsString().equals("gold")
                && page.toString().contains(g.getStringUUID()) && page.getAsJsonObject("takes").get("fakefight")
                        .getAsBoolean(), page.toString());
        // a custom set: made from a base with blocks; worn; changed; deleted (whoever wore it: his own again)
        r = Editor.saveCustom(s, "Tester", "Ice palace", "frost", Map.of("stone", "gold_block"));
        h.assertTrue(r.ok() && w.custom("ice palace") != null && w.custom("Ice palace").blocks.get(
                "minecraft:stone").equals("minecraft:gold_block"), r.lines().toString());
        r = Editor.setGiant(s, "Tester", g, "Ice palace");
        h.assertTrue(r.ok() && Editor.lookOf(s, g).equals("custom:Ice palace"), r.lines().toString());
        h.assertTrue(!Editor.saveCustom(s, "Tester", "Frost", "", Map.of()).ok(), "a built-in set's name");
        h.assertTrue(!Editor.saveCustom(s, "Tester", "Bad", "", Map.of("stone", "goldd_block")).ok(), "a bad block");
        h.assertTrue(!Editor.saveCustom(s, "Tester", "", "", Map.of()).ok(), "no name");
        r = Editor.deleteCustom(s, "Tester", "ice palace");
        h.assertTrue(r.ok() && w.custom("Ice palace") == null && Editor.lookOf(s, g).equals("gold"),
                "deleted: back to his tag's " + Editor.lookOf(s, g));
        // refusals
        h.assertTrue(!Editor.setKind(s, "Tester", "fakefight", "plaid").ok(), "no such set");
        h.assertTrue(!Editor.setKind(s, "Tester", "nobody", "frost").ok(), "no such giant");
        h.assertTrue(!Editor.setTag(s, "Tester", "No such tag", "frost").ok(), "no such tag");
        // back to his own, step by step
        Editor.setGiant(s, "Tester", g, "none");
        Editor.setTag(s, "Tester", "Look test", "none");
        h.assertTrue(Editor.lookOf(s, g).equals("frost"), "his kind's again");
        Editor.setKind(s, "Tester", "fakefight", "");
        h.assertTrue(Editor.lookOf(s, g).isEmpty(), "his own look");
        // every change is in the log as a look line
        long looks = AdminWorld.get(s).log().stream().filter(c -> c.id().equals("fun:look")).count();
        h.assertTrue(looks >= 9, "logged: " + looks);
        // the world's data reads back as it was saved
        Editor.setKind(s, "Tester", "fakefight", "festive");
        CompoundTag tag = w.save(new CompoundTag(), s.registryAccess());
        h.assertTrue(tag.getString("Json").contains("festive"), tag.toString());
        Editor.setKind(s, "Tester", "fakefight", "");
        g.discard();
        FunWorld.get(s).s.tags.remove("Look test");
        w.s = new EditorWorld.State();
        AdminServer.useGiants(null);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "p12_looks2", timeoutTicks = 100)
    public void aGiantThatCantWearLooksIsRefused(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = SystemsGameTests.fresh(h);
        AdminServer.Result r = Editor.setKind(s, "Tester", "fakesys", "gold");
        h.assertTrue(!r.ok() && r.lines().get(0).contains("can't wear another look"), r.lines().toString());
        h.assertTrue(!EditorWorld.get(s).s.kinds.containsKey("fakesys"), "nothing kept");
        // (as shipped every player's game is sent no looks)
        h.assertTrue(Editor.looksJson(s).getAsJsonObject("kinds").isEmpty(), Editor.looksJson(s).toString());
        AdminServer.useGiants(null);
        h.succeed();
    }
}
