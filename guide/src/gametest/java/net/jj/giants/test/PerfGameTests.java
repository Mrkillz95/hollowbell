package net.jj.giants.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.tree.CommandNode;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.net.PerfStatePayload;
import net.jj.giants.perf.PerfBridges;
import net.jj.giants.perf.PerfCatalog;
import net.jj.giants.perf.PerfJson;
import net.jj.giants.perf.PerfPresets;
import net.jj.giants.perf.PerfProfile;
import net.jj.giants.perf.PerfServer;
import net.jj.giants.perf.PerfSetting;
import net.jj.giants.perf.PerfWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;

/**
 * Giants Performance: the guide's settings and presets, the bridge protocol (with giants that speak it, older ones
 * that don't, a newer one and a broken one), players' own presets, and the world's Features (operators only).
 */
public class PerfGameTests implements FabricGameTest {
    private static final Giant NEW = fake("fakeperf"), OLD = fake("fakeold"), FUTURE = fake("fakefuture"),
            BROKEN = fake("fakebroken");

    private static Giant fake(String key) {
        return new Giant(key, key, key, "net.jj." + key + ".GiantsBridge", List.of(), Set.of());
    }

    @SuppressWarnings("deprecation")
    private static ServerPlayer player(GameTestHelper h, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(id, "pf-" + id.toString().substring(0, 8)), false);
        ServerPlayer p = new ServerPlayer(
                h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        return p;
    }

    private static List<PerfSetting> all() {
        return PerfCatalog.forGiants(Giants.ALL);
    }

    /** quality goes down (or stays) from a to b: less of a slider, a later choice, a toggle not turned back on */
    private static boolean notBetter(PerfSetting s, double a, double b) {
        return switch (s.kind()) {
            case SLIDER, TOGGLE -> b <= a + 1e-9;
            case CHOICE -> b >= a - 1e-9;
        };
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void highestChangesNothing(GameTestHelper h) {
        List<PerfSetting> all = all();
        for (PerfSetting s : all) {
            h.assertTrue(s.level(PerfPresets.HIGHEST) == s.def(), s.id() + ": Highest is its default");
            h.assertTrue(s.clamp(s.def()) == s.def(), s.id() + ": its default is in range");
        }
        h.assertTrue(PerfPresets.changes(PerfPresets.HIGHEST, all).isEmpty(), "Highest changes nothing");
        // what a giant is sent at Highest is every default: a 1 for every slider that scales his own, true for
        // every toggle, choice 0, and the world's the same
        for (boolean server : new boolean[] {false, true}) {
            JsonObject o = PerfBridges.valuesFor("pitchgut", server, all, PerfPresets.values(0, all));
            for (PerfSetting s : all) {
                if (s.server() != server || !(s.owner().isEmpty() || s.owner().equals("pitchgut"))) continue;
                String id = s.plainId();
                h.assertTrue(o.has(id), "pitchgut is sent " + id);
                double v = o.get(id).getAsJsonPrimitive().isBoolean()
                        ? (o.get(id).getAsBoolean() ? 1 : 0)
                        : o.get(id).getAsDouble();
                h.assertTrue(v == s.def(), id + " at Highest is its default (" + v + ")");
            }
            // only his own copy of the per-giant ones
            h.assertFalse(o.has(PerfCatalog.RENDER_DISTANCE + ".cerberus"), "no one else's render distance");
        }
        // the guide's own sliders that scale a giant's settings are all 100% at Highest
        for (PerfSetting s : PerfCatalog.common())
            if (s.kind() == PerfSetting.Kind.SLIDER && "%".equals(s.unit()))
                h.assertTrue(s.def() == 1.0, s.id() + " is 100% at Highest");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void presetsStayInRangeAndOnlyGoDown(GameTestHelper h) {
        List<PerfSetting> all = all();
        h.assertTrue(PerfPresets.BUILT_IN.equals(List.of("Highest", "High", "Medium", "Low", "Very Low")), "the five");
        for (PerfSetting s : all)
            for (int p = 0; p < PerfPresets.BUILT_IN.size(); p++) {
                double v = s.level(p);
                h.assertTrue(v == s.clamp(v), s.id() + " in range in " + PerfPresets.BUILT_IN.get(p));
                if (p > 0)
                    h.assertTrue(notBetter(s, s.level(p - 1), v),
                            s.id() + " is no better in " + PerfPresets.BUILT_IN.get(p) + " than the one before");
            }
        // High and Medium only turn the look down; the world's features start going at Low
        for (int p = 1; p <= 2; p++)
            for (PerfPresets.Change c : PerfPresets.changes(p, all))
                h.assertFalse(c.setting().server(), PerfPresets.BUILT_IN.get(p) + " leaves the world alone, not "
                        + c.setting().id());
        List<PerfPresets.Change> veryLow = PerfPresets.changes(4, all);
        h.assertTrue(veryLow.stream().anyMatch(c -> c.setting().id().equals("feature.meetings") && c.removes()),
                "Very Low removes meetings");
        h.assertTrue(veryLow.stream().anyMatch(c -> c.setting().id().equals("detail.farSight") && c.removes()),
                "Very Low removes the skyline giants");
        h.assertTrue(PerfPresets.changes(1, all).size() < PerfPresets.changes(4, all).size(), "lower does more");
        // every preset but Highest says what it turns down
        for (int p = 1; p < 5; p++) h.assertFalse(PerfPresets.changes(p, all).isEmpty(), "preset " + p + " does something");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void everyGuideSettingHasItsWords(GameTestHelper h) {
        JsonObject l;
        try (var in = PerfGameTests.class.getResourceAsStream("/assets/jj_giants/lang/en_us.json")) {
            l = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        List<String> wrong = new ArrayList<>();
        List<PerfSetting> mine = new ArrayList<>(PerfCatalog.common());
        mine.add(PerfCatalog.renderDistance("pitchgut"));
        for (PerfSetting s : mine)
            for (String k : new String[] {s.name(), s.what(), s.loses()}) {
                if (!l.has(k) || l.get(k).getAsString().isBlank()) wrong.add("no " + k);
                else if (!Language.getInstance().has(k)) wrong.add("not loaded " + k);
            }
        // a stray % breaks a translated line in the game: every % is %s or %%
        for (var e : l.entrySet())
            if (e.getKey().startsWith("perf.jj_giants.") && e.getValue().getAsString().replace("%%", "").replace("%s", "").contains("%"))
                wrong.add("stray % in " + e.getKey());
        for (String k : new String[] {"perf.jj_giants.title", "key.jj_giants.performance", "key.categories.jj_giants",
            "perf.jj_giants.section.effects", "perf.jj_giants.section.detail", "perf.jj_giants.section.features",
            "perf.jj_giants.features.not_op", "perf.jj_giants.ask_op", "perf.jj_giants.highest"})
            if (!l.has(k)) wrong.add("no " + k);
        // every section the guide has is one of the four
        for (PerfSetting s : mine)
            if (!Set.of(PerfSetting.EFFECTS, PerfSetting.DETAIL, PerfSetting.FEATURES).contains(s.section()))
                wrong.add(s.id() + " in " + s.section());
            else if (s.section().equals(PerfSetting.FEATURES) != s.server())
                wrong.add(s.id() + ": features are the world's, and only they");
        h.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void theBridgeLeavesOutOldBrokenAndNewerMods(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> giants = List.of(NEW, OLD, FUTURE, BROKEN);
        List<PerfJson.Report> r = PerfBridges.askAll(s, giants);
        h.assertTrue(r.size() == 1 && r.get(0).giant().equals("fakeperf"), "only the one that speaks v1: " + r);
        PerfJson.Report rep = r.get(0);
        h.assertTrue(rep.uses().equals(List.of("fx.particles", "fx.bigImpacts", "detail.renderDistance",
                "feature.meetings")), "its uses, the bad id left out: " + rep.uses());
        List<String> ids = rep.settings().stream().map(PerfSetting::id).toList();
        h.assertTrue(ids.equals(List.of("fakeperf.sparks", "fakeperf.hush", "fakeperf.mode")),
                "three good settings, bad and repeated ones left out: " + ids);
        PerfSetting sparks = rep.settings().get(0), hush = rep.settings().get(1), mode = rep.settings().get(2);
        h.assertTrue(sparks.level(0) == 1.0, "Highest is its default even though it said 0.5");
        h.assertTrue(sparks.level(2) == 0.75 && sparks.level(4) == 0.0, "its other levels kept");
        h.assertTrue(hush.server() && hush.ops() && hush.section().equals(PerfSetting.FEATURES), "the Hush is the world's");
        h.assertTrue(mode.kind() == PerfSetting.Kind.CHOICE && mode.options().size() == 3, "a choice of three");
        h.assertTrue(mode.removes(2), "its last choice is Off");
        h.assertTrue(sparks.owner().equals("fakeperf") && sparks.section().equals(PerfSetting.GIANT), "his own");

        // the old one was asked (and said null), and isn't sent anything after
        int oldCalls = net.jj.fakeold.GiantsBridge.calls;
        List<PerfSetting> settings = PerfBridges.settings(r, giants);
        h.assertTrue(settings.stream().anyMatch(x -> x.id().equals("detail.renderDistance.fakeperf")),
                "a render distance for the one that speaks it");
        h.assertFalse(settings.stream().anyMatch(x -> x.id().equals("detail.renderDistance.fakeold")),
                "none for the old one");
        Map<String, Double> v = new HashMap<>();
        v.put("fx.particles", 0.5);
        v.put("fakeperf.sparks", 0.3);
        v.put("detail.renderDistance.fakeperf", 0.6);
        v.put("detail.farSight", 0.0);
        v.put("fakeperf.mode", 7.0);
        int n = PerfBridges.push(s, r, giants, false, settings, v);
        h.assertTrue(n == 1, "sent to the one");
        h.assertTrue(net.jj.fakeold.GiantsBridge.calls == oldCalls, "nothing sent to the old one");
        JsonObject got = net.jj.fakeperf.GiantsBridge.client;
        h.assertTrue(got != null, "it got its values");
        h.assertTrue(got.get("fx.particles").getAsDouble() == 0.5, "particles 0.5");
        h.assertTrue(got.get("fakeperf.sparks").getAsDouble() == 0.25, "on its 0.25 steps: " + got.get("fakeperf.sparks"));
        h.assertTrue(got.get("detail.renderDistance").getAsDouble() == 0.6, "its own render distance, plain id");
        h.assertTrue(!got.get("detail.farSight").getAsBoolean(), "a toggle is true/false");
        h.assertTrue(got.get("fakeperf.mode").getAsDouble() == 2.0, "a choice kept to its options");
        h.assertTrue(got.get("fx.bigImpacts").getAsDouble() == 1.0, "what isn't set is sent as its default");
        h.assertFalse(got.has("fakeperf.hush") || got.has("feature.meetings"), "no world settings to the player's side");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void playersOwnPresets(GameTestHelper h) {
        List<PerfSetting> all = new ArrayList<>(all());
        PerfSetting particles = all.stream().filter(s -> s.id().equals("fx.particles")).findFirst().orElseThrow();
        PerfSetting meetings = all.stream().filter(s -> s.id().equals("feature.meetings")).findFirst().orElseThrow();
        PerfProfile p = new PerfProfile();
        h.assertTrue(p.preset().equals("Highest") && !p.custom(), "starts at Highest");
        p.set(particles, 0.4);
        h.assertTrue(p.custom() && p.preset().equals(PerfPresets.CUSTOM), "moving one makes it Custom");
        h.assertTrue(p.get(particles) == 0.4, "and keeps it");
        Map<String, Double> low = p.apply("low", all);
        h.assertTrue(low != null && p.preset().equals("Low") && p.base().equals("Low"), "Low picked (any case)");
        h.assertTrue(p.get(particles) == particles.level(3), "Low's particles");
        h.assertTrue(low.get("feature.meetings") == meetings.level(3), "its world values are handed back for an op");
        h.assertFalse(p.values().containsKey("feature.meetings"), "but not kept as the player's own");
        // a section put back
        p.set(particles, 0.9);
        p.reset(s -> s.section().equals(PerfSetting.EFFECTS), all);
        h.assertTrue(p.get(particles) == particles.level(3) && p.preset().equals("Low"), "reset to Low, label back");

        // their own: save, the names they can't have, rename, delete
        Map<String, Double> snap = new HashMap<>();
        for (PerfSetting s : all) snap.put(s.id(), p.get(s));
        snap.put("feature.meetings", 0.0);
        h.assertTrue(p.saveAs("  My PC ", snap) == null && p.preset().equals("My PC"), "saved as My PC");
        h.assertTrue(p.saveAs("Highest", snap) != null, "not a built-in's name");
        h.assertTrue(p.saveAs("custom", snap) != null, "not Custom");
        h.assertTrue(p.saveAs("my pc", snap) != null, "not twice");
        h.assertTrue(p.saveAs("", snap) != null, "not empty");
        h.assertTrue(p.saveAs("x".repeat(PerfProfile.LONGEST_NAME + 1), snap) != null, "not too long");
        h.assertTrue(p.all().equals(List.of("Highest", "High", "Medium", "Low", "Very Low", "My PC")), "listed after");
        p.apply("Highest", all);
        h.assertTrue(p.get(particles) == 1.0, "back to Highest");
        Map<String, Double> mine = p.apply("My PC", all);
        h.assertTrue(p.get(particles) == particles.level(3) && mine.get("feature.meetings") == 0.0, "mine again");
        h.assertTrue(p.rename("My PC", "Laptop") == null && p.preset().equals("Laptop"), "renamed");
        h.assertTrue(p.rename("Laptop", "High") != null, "can't be renamed to a built-in");
        h.assertTrue(p.rename("nope", "x") != null, "can't rename what isn't there");

        // kept in its file and read back
        try {
            Path f = Files.createTempFile("jj_giants_performance", ".json");
            p.save(f);
            PerfProfile q = PerfProfile.load(f);
            h.assertTrue(q.preset().equals("Laptop") && q.mine().equals(List.of("Laptop")), "read back: " + q.preset());
            h.assertTrue(q.get(particles) == p.get(particles), "values read back");
            h.assertTrue(q.presetValues("Laptop", all).get("feature.meetings") == 0.0, "its world values read back");
            Files.writeString(f, "{ this is broken");
            h.assertTrue(PerfProfile.load(f).preset().equals("Highest"), "a broken file starts at Highest");
            Files.deleteIfExists(f);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        h.assertTrue(p.delete("Laptop") == null, "deleted");
        h.assertTrue(p.custom() && p.base().equals("Highest") && p.mine().isEmpty(), "Custom from Highest after");
        h.assertTrue(p.delete("Laptop") != null, "gone");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void worldFeaturesNeedAnOperator(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        PerfWorld w = PerfWorld.get(s);
        Map<String, Double> before = new HashMap<>(w.values());
        ServerPlayer p = player(h, UUID.randomUUID());
        try {
            PerfServer.refresh(s, List.of(NEW, OLD));
            h.assertTrue(PerfServer.settings().stream().allMatch(PerfSetting::server), "only the world's settings");
            h.assertTrue(PerfServer.settings().stream().anyMatch(x -> x.id().equals("fakeperf.hush")), "with the Hush");
            JsonObject sent = net.jj.fakeperf.GiantsBridge.server;
            h.assertTrue(sent != null && sent.get("fakeperf.hush").getAsBoolean(), "the giant was told on refresh");

            Map<String, Double> ask = new HashMap<>();
            ask.put("fakeperf.hush", 0.0);
            ask.put("feature.keepAwake", 5.0);
            ask.put("fx.particles", 0.1);
            ask.put("no.such.thing", 1.0);
            int sets = net.jj.fakeperf.GiantsBridge.sets;
            // not an operator: nothing changes, nobody is told
            h.assertFalse(PerfServer.allowed(p), "a new player isn't an operator");
            String said = PerfServer.request(s, p, ask);
            h.assertTrue(said.contains("operator"), "told to ask an operator: " + said);
            h.assertTrue(w.values().equals(before), "nothing changed");
            h.assertTrue(net.jj.fakeperf.GiantsBridge.sets == sets, "no giant was told anything");

            // an operator: the world's ones change (pulled into range), the rest are left out
            // (the game test server's own op() gives level 0: an entry of level 4, as on a real server's ops.json)
            s.getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4, false));
            h.assertTrue(PerfServer.allowed(p), "now an operator");
            said = PerfServer.request(s, p, ask);
            h.assertTrue(said.startsWith("World features saved"), said);
            h.assertTrue(w.values().get("fakeperf.hush") == 0.0, "the Hush off");
            h.assertTrue(!w.values().containsKey("feature.keepAwake"),
                    "keep-awake pulled back to 100% (its default, so not even kept)");
            h.assertFalse(w.values().containsKey("fx.particles"), "a player's own setting is no world setting");
            h.assertFalse(w.values().containsKey("no.such.thing"), "an unknown id is left out");
            sent = net.jj.fakeperf.GiantsBridge.server;
            h.assertTrue(!sent.get("fakeperf.hush").getAsBoolean(), "the giant was told the Hush is off");
            h.assertTrue(sent.get("feature.meetings").getAsBoolean(), "and the rest as they were");
            // the same request from an operator who has since lost it: refused again
            s.getPlayerList().getOps().remove(p.getGameProfile());
            ask.put("fakeperf.hush", 1.0);
            PerfServer.request(s, p, ask);
            h.assertTrue(w.values().get("fakeperf.hush") == 0.0, "deopped: unchanged");
            // the state a player is sent says what they may do, and reads back
            PerfStatePayload st = new PerfStatePayload(
                    PerfServer.allowed(p),
                    PerfJson.writeAll(PerfServer.settings()),
                    PerfJson.values(w.values(), PerfServer.settings()).toString(),
                    "hi");
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            PerfStatePayload.CODEC.encode(buf, st);
            PerfStatePayload back = PerfStatePayload.CODEC.decode(buf);
            h.assertTrue(!back.op() && back.message().equals("hi"), "the state goes over the wire");
            List<PerfSetting> read = PerfJson.readAll(back.settings());
            h.assertTrue(read.size() == PerfServer.settings().size(), "every world setting read back: " + read.size());
            h.assertTrue(read.stream().anyMatch(x -> x.id().equals("fakeperf.hush") && x.ops()), "the Hush read back");
            h.assertTrue(PerfJson.values(back.values()).get("fakeperf.hush") == 0.0, "its value read back");
        } finally {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            w.values().clear();
            w.values().putAll(before);
            w.setDirty();
            PerfServer.refresh(s, Giants.installed());
            s.getPlayerList().remove(p);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void giantsPerformanceIsACommandForEveryone(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        CommandNode<CommandSourceStack> giants = s.getCommands().getDispatcher().getRoot().getChild("giants");
        h.assertTrue(giants != null, "/giants is there");
        for (String w : PerfServer.WORDS) h.assertTrue(giants.getChild(w) != null, "/giants " + w + " is there");
        // with a giant here, his own /giants words are all still there beside it
        if (!Giants.installed().isEmpty()) {
            h.assertTrue(giants.getChild("where") != null, "/giants where is still there");
            h.assertTrue(giants.getCommand() != null, "/giants on its own still runs");
        }
        // any player may type it; a game without the guide is told why nothing opens (and nothing breaks)
        ServerPlayer p = player(h, UUID.randomUUID());
        try {
            CommandSourceStack src = p.createCommandSourceStack().withPermission(0);
            h.assertTrue(giants.getChild("performance").getRequirement().test(src), "no cheats needed");
            s.getCommands().performPrefixedCommand(src, "giants performance");
            s.getCommands().performPrefixedCommand(src, "giants effects");
        } finally {
            s.getPlayerList().remove(p);
        }
        // the real giants here: each is asked, and whatever it says, nothing breaks
        for (Giant g : Giants.installed())
            GiantsGuideMod.LOG.info("performance protocol, {}: {}", g.key(),
                    PerfBridges.ask(s, g) == null ? "not yet (left out)" : "speaks it");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void settingValuesAreKeptSafe(GameTestHelper h) {
        PerfSetting particles = PerfCatalog.common().stream().filter(x -> x.id().equals("fx.particles")).findFirst().orElseThrow();
        h.assertTrue(particles.clamp(Double.NaN) == 1.0, "NaN is the default");
        h.assertTrue(particles.clamp(-3) == 0 && particles.clamp(9) == 1, "pulled into range");
        h.assertTrue(particles.clamp(0.33) == 0.35, "onto its 5% steps: " + particles.clamp(0.33));
        h.assertTrue(particles.show(0.35).equals("35%"), "shown as 35%");
        h.assertTrue(particles.lower(0.5) && !particles.lower(1) && particles.removes(0) && !particles.removes(0.1),
                "lower and removes");
        PerfSetting rd = PerfCatalog.renderDistance("gloamwick");
        h.assertTrue(rd.plainId().equals("detail.renderDistance") && rd.perGiant(), "his own copy, read by plain id");
        // values from anywhere: only numbers and true/false with good ids
        Map<String, Double> v = PerfJson.values("{\"a.b\":1,\"c\":true,\"d\":\"x\",\"bad id\":2,\"e\":null}");
        h.assertTrue(v.size() == 2 && v.get("a.b") == 1.0 && v.get("c") == 1.0, "two good values: " + v);
        h.assertTrue(PerfJson.values("not json").isEmpty() && PerfJson.values(null).isEmpty(), "nonsense is nothing");
        h.assertTrue(PerfJson.read("x", null) == null && PerfJson.read("x", List.of()) == null, "no answer is old");
        h.assertTrue(PerfJson.read("x", List.of("{\"perf\":0}")) == null, "version 0 is no version");
        h.succeed();
    }
}
