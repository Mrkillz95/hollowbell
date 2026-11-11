package net.jj.giants.test;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakeplayers.GiantsBridge;
import net.jj.fakeplayers.OtherBridge;
import net.jj.giants.Giant;
import net.jj.giants.GuideBridge;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.players.Owns;
import net.jj.giants.players.PlayerData;
import net.jj.giants.players.Players;
import net.jj.giants.players.PlayersAdmin;
import net.jj.giants.players.RulesPage;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

/**
 * Max giants per player (ADMIN_BRIDGE.md 20.18), the Guide's side with two stand-in giants (net.jj.fakeplayers: his
 * GiantsBridge, and OtherBridge as a second kind, under Wreckback's key so the kit can name him): the setting and
 * per-player overrides, the counting ("owners", and an older giant's live list), "maygain", what the Guide itself
 * refuses (the starting kit's book, My Giant's book, Live control's Set owner), the Players page and the command.
 */
public class MaxGiantsGameTests implements FabricGameTest {
    static final Giant OTHER = new Giant("wreckback", "fakeother", "fakeother", "net.jj.fakeplayers.OtherBridge",
            List.of(), Set.of());
    static final Giant OLD = new Giant("fakeold", "fakeold", "fakeold", "net.jj.fakeold.GiantsBridge", List.of(),
            Set.of());

    private static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = PlayersGameTests.fresh(h);
        OtherBridge.reset();
        AdminServer.useGiants(List.of(PlayersGameTests.FAKE, OTHER));
        return s;
    }

    private static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    private static String arg(ServerPlayer p) {
        return "{\"players\":1,\"player\":\"" + p.getStringUUID() + "\",\"giant\":\"fakeplayers\"}";
    }

    private static List<String> mayGain(MinecraftServer s, ServerPlayer p) {
        return GuideBridge.guide(s, "maygain", arg(p));
    }

    private static void holds(ServerPlayer p) {
        GiantsBridge.holder = p.getStringUUID();
        GiantsBridge.holderName = p.getGameProfile().getName();
    }

    private static JsonObject person(MinecraftServer s, String name) {
        for (JsonElement e : PlayersAdmin.json(s).getAsJsonArray("rules"))
            if (e.getAsJsonObject().get("name").getAsString().equals(name)) return e.getAsJsonObject();
        return null;
    }

    /** a player who joins for real with this UUID (the join events, the kit, run) */
    private static ServerPlayer joinAs(GameTestHelper h, UUID id, String name) {
        MinecraftServer s = h.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer p = new ServerPlayer(s, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        s.getPlayerList().placeNewPlayer(c, p, cookie);
        return p;
    }

    private static final class Heard implements CommandSource {
        final List<String> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component m) {
            lines.add(m.getString());
        }

        @Override
        public boolean acceptsSuccess() {
            return true;
        }

        @Override
        public boolean acceptsFailure() {
            return true;
        }

        @Override
        public boolean shouldInformAdmins() {
            return false;
        }
    }

    private static List<String> run(MinecraftServer s, String cmd) {
        Heard heard = new Heard();
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withSource(heard).withPermission(4), cmd);
        return heard.lines;
    }

    // no limit: nothing changes, nothing is counted; an older world's data and payloads read as no limit

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_none")
    public void noLimitChangesNothingAndCountsNothing(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = PlayersGameTests.join(h, "nolim", false);
        holds(a);
        GiantsBridge.own.put(a.getStringUUID(), "nolim");
        OtherBridge.main = a.getStringUUID();
        OtherBridge.mainName = "nolim";
        h.assertTrue(Players.option(s, "maxGiants").equals("none"), "as shipped: no limit");
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "no limit: ok");
        h.assertTrue(GiantsBridge.ownersCalls == 0 && OtherBridge.ownersCalls == 0,
                "no limit: no giant asked who owns what");
        // the per-player rules go out exactly as before for a rule without a limit
        Players.setRule(s, "Tester", "nolim", "own", false);
        h.assertTrue(Players.rulesArg(PlayerData.get(s)).equals("{\"players\":1,\"rules\":{\"" + a.getStringUUID()
                + "\":{\"own\":false,\"ignored\":false}}}"), "the same payload: " + Players.rulesArg(PlayerData.get(s)));
        Players.setRule(s, "Tester", "nolim", "own", true);
        // an older world's rules (no "max") read as the world's limit
        PlayerData.State old = new Gson().fromJson("{\"rules\":{\"" + a.getStringUUID()
                + "\":{\"name\":\"nolim\",\"own\":false}}}", PlayerData.State.class);
        h.assertTrue(old.rules.get(a.getStringUUID()).max == -1, "missing max: the world's");
        h.assertTrue(Owns.cap(s, a.getStringUUID()) == 0, "no limit");
        // the Players page: what they own, no limit
        JsonObject p = person(s, "nolim");
        h.assertTrue(p != null && p.get("owns").getAsInt() == 3 && p.get("max").getAsInt() == 0
                && p.get("maxRule").getAsInt() == -1, "the page: " + p);
        h.assertTrue(p.getAsJsonArray("claims").toString().equals(
                "[\"Fakeplayers\",\"Fakeplayers (My Giant)\",\"Wreckback\"]"), "the claims: " + p);
        h.assertTrue(Owns.pageLine(3, 0).equals("Owns 3 giants (no limit)"), Owns.pageLine(3, 0));
        // a newer or unreadable maygain: null (the giant allows it)
        h.assertTrue(GuideBridge.guide(s, "maygain", "{\"players\":2,\"player\":\"" + a.getStringUUID() + "\"}")
                == null, "a newer version: null");
        h.assertTrue(GuideBridge.guide(s, "maygain", "not json") == null, "unreadable: null");
        PlayersGameTests.done(h, a);
    }

    // the world's limit: under it a gain is fine, at it refused with the same words from every giant

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_world")
    public void theWorldsLimitCountsEveryKindOfClaim(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = PlayersGameTests.join(h, "capped", false);
        set(h, s, "maxGiants", "2");
        h.assertTrue(GiantsBridge.all.has("all.maxGiants") && GiantsBridge.all.get("all.maxGiants").getAsString()
                .equals("2"), "the giants are sent it: " + GiantsBridge.all);
        // none owned, one owned: under the limit
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "owns none: ok");
        holds(a);
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "owns 1 of 2: ok");
        h.assertTrue(GiantsBridge.ownersCalls > 0 && OtherBridge.ownersCalls > 0, "every giant asked");
        // a My Giant own book of another kind is a second one: at the limit
        OtherBridge.own.put(a.getStringUUID(), "capped");
        h.assertTrue(List.of("no", "You already own 2 giants, the most this world allows.").equals(mayGain(s, a)),
                "at 2 of 2: " + mayGain(s, a));
        // his main book and his own book of the same kind count 2
        OtherBridge.own.clear();
        GiantsBridge.own.put(a.getStringUUID(), "capped");
        h.assertTrue(mayGain(s, a).get(0).equals("no"), "main and own of one kind: 2");
        GiantsBridge.own.clear();
        // one book that makes him Keeper of three of a kind (the Hearthstride's) counts 3
        GiantsBridge.mainCount = 3;
        set(h, s, "maxGiants", "3");
        h.assertTrue(List.of("no", "You already own 3 giants, the most this world allows.").equals(mayGain(s, a)),
                "one book for three: " + mayGain(s, a));
        GiantsBridge.mainCount = 0;
        set(h, s, "maxGiants", "1");
        h.assertTrue(List.of("no", "You already own 1 giant, the most this world allows.").equals(mayGain(s, a)),
                "one: " + mayGain(s, a));
        // the setting: world only, refused for all worlds, a choice of none and 1 to 9, kept by presets
        h.assertFalse(AdminServer.setDefault(s, "Tester", "all", "maxGiants", "2").ok(), "world only");
        h.assertFalse(AdminServer.set(s, "Tester", "all", "maxGiants", "10").ok(), "not 10");
        h.assertFalse(AdminServer.set(s, "Tester", "all", "maxGiants", "0").ok(), "not 0 (none is no limit)");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        h.assertTrue(Players.option(s, "maxGiants").equals("1"), "a preset keeps it");
        h.assertTrue(RulesPage.json(s).getAsJsonArray("players").toString().contains("Max giants per player: 1"),
                "the Rules page says it: " + RulesPage.json(s));
        set(h, s, "maxGiants", "none");
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "back to no limit");
        PlayersGameTests.done(h, a);
    }

    // a per-player limit, over the world's either way

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_player")
    public void aPlayersOwnLimitBeatsTheWorlds(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = PlayersGameTests.join(h, "alex", false), b = PlayersGameTests.join(h, "bob", false);
        ServerPlayer op = PlayersGameTests.join(h, "boss", true), nobody = PlayersGameTests.join(h, "nobody", false);
        set(h, s, "maxGiants", "2");
        holds(a);
        OtherBridge.own.put(a.getStringUUID(), "alex");
        GiantsBridge.own.put(b.getStringUUID(), "bob");
        OtherBridge.main = b.getStringUUID();
        OtherBridge.mainName = "bob";
        AdminServer.Result r = Players.setMax(s, "Tester", "alex", "5");
        h.assertTrue(r.ok() && r.lines().get(0).equals("alex may own at most 5 giants in this world."),
                r.lines().toString());
        h.assertTrue(r.announce().get(0).equals("Tester: alex may own at most 5 giants in this world."),
                "the other ops told: " + r.announce());
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "alex: 2 of 5");
        h.assertTrue(mayGain(s, b).get(0).equals("no"), "bob: 2 of the world's 2");
        // the giants get it as an override on the rule; bob, with no rule, isn't listed
        JsonObject sent = GiantsBridge.rules.getAsJsonObject(a.getStringUUID());
        h.assertTrue(sent != null && sent.get("max").getAsInt() == 5 && sent.get("own").getAsBoolean()
                && !GiantsBridge.rules.has(b.getStringUUID()), "pushed: " + GiantsBridge.rules);
        // no limit for bob, even under a world limit of 1
        Players.setMax(s, "Tester", "bob", "none");
        set(h, s, "maxGiants", "1");
        h.assertTrue(List.of("ok").equals(mayGain(s, b)), "bob: no limit");
        h.assertTrue(GiantsBridge.rules.getAsJsonObject(b.getStringUUID()).get("max").getAsInt() == 0, "0 sent");
        // back to the world's: the rule (only a limit) goes
        r = Players.setMax(s, "Tester", "bob", "default");
        h.assertTrue(r.ok() && r.lines().get(0).equals("bob follows the world's limit on giants (1) in this world."),
                r.lines().toString());
        h.assertTrue(!PlayerData.get(s).s.rules.containsKey(b.getStringUUID())
                && !GiantsBridge.rules.has(b.getStringUUID()), "no rule left for bob");
        h.assertTrue(mayGain(s, b).get(0).equals("no"), "bob at the world's again");
        // kept with the world; logged
        h.assertTrue(PlayerData.get(s).reread().rule(a.getStringUUID()).max == 5, "kept");
        List<AdminWorld.Change> log = AdminWorld.get(s).log();
        AdminWorld.Change c = log.get(log.size() - 1);
        h.assertTrue(c.id().equals("players:max") && AdminServer.changeText(c).equals(
                "bob follows the world's limit on giants (1)"), "logged: " + AdminServer.changeText(c));
        // bad values
        for (String bad : List.of("0", "10", "-1", "lots"))
            h.assertTrue(Players.setMax(s, "Tester", "alex", bad).lines().equals(
                    List.of("Pick 1 to 9, none or default.")), "refused " + bad);
        h.assertFalse(Players.setMax(s, "Tester", "nobody_like_that", "3").ok(), "an unknown player");
        // the command
        List<String> heard = run(s, "giants players alex max 3");
        h.assertTrue(PlayerData.get(s).rule(a.getStringUUID()).max == 3, "the command: " + heard);
        heard = run(s, "giants players alex max");
        h.assertTrue(heard.size() > 0 && heard.get(0).equals("alex owns 2 of 3 giants: Fakeplayers, Wreckback (My"
                + " Giant)."), "the command shows it: " + heard);
        heard = run(s, "giants players alex max 11");
        h.assertTrue(heard.contains("Pick 1 to 9, none or default."), "bad value: " + heard);
        heard = run(s, "giants players");
        h.assertTrue(String.join("|", heard).contains("alex: may own at most 3 giants"), "the list: " + heard);
        // the book's request, for admins only
        AdminServer.Result br = AdminBook.change(s, op, "{\"do\":\"pmax\",\"player\":\"" + b.getStringUUID()
                + "\",\"max\":\"4\"}");
        h.assertTrue(br.ok() && PlayerData.get(s).rule(b.getStringUUID()).max == 4, "the book: " + br.lines());
        h.assertFalse(AdminBook.change(s, nobody, "{\"do\":\"pmax\",\"player\":\"bob\",\"max\":\"1\"}").ok(),
                "not for players who aren't admins");
        JsonObject pb = person(s, "bob");
        h.assertTrue(pb.get("owns").getAsInt() == 2 && pb.get("max").getAsInt() == 4
                && pb.get("maxRule").getAsInt() == 4, "the page: " + pb);
        PlayersGameTests.done(h, a, b, op, nobody);
    }

    // lowering the limit takes nothing away

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_lower")
    public void loweringTheLimitKeepsWhatTheyOwn(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = PlayersGameTests.join(h, "hoarder", false);
        holds(a);
        GiantsBridge.own.put(a.getStringUUID(), "hoarder");
        OtherBridge.main = a.getStringUUID();
        OtherBridge.mainName = "hoarder";
        set(h, s, "maxGiants", "3");
        h.assertTrue(mayGain(s, a).get(0).equals("no"), "3 of 3");
        set(h, s, "maxGiants", "2");
        h.assertTrue(List.of("no", "You already own 3 giants, more than this world allows (2).").equals(
                mayGain(s, a)), "over: " + mayGain(s, a));
        h.assertTrue(a.getStringUUID().equals(GiantsBridge.holder) && GiantsBridge.own.containsKey(a.getStringUUID())
                && a.getStringUUID().equals(OtherBridge.main), "nothing taken away");
        JsonObject p = person(s, "hoarder");
        h.assertTrue(p.get("owns").getAsInt() == 3 && p.get("max").getAsInt() == 2, "the page: " + p);
        h.assertTrue(Owns.pageLine(3, 2).equals("Owns 3 of 2 giants (keeps them)")
                && Owns.pageLine(1, 2).equals("Owns 1 of 2 giants") && Owns.pageLine(0, 1).equals("Owns 0 of 1 giant"),
                "the page's words");
        h.assertTrue(Owns.opLine("hoarder", 3, 2).equals(
                "hoarder already owns 3 giants, more than this world allows for them (2)."), "op words");
        // dropping one: still at the limit; dropping two: room again
        OtherBridge.main = null;
        h.assertTrue(mayGain(s, a).get(0).equals("no"), "2 of 2");
        GiantsBridge.own.clear();
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "1 of 2: room");
        PlayersGameTests.done(h, a);
    }

    // what the Guide gives or does itself: the kit's book, My Giant's book, Live control's Set owner

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_guide", timeoutTicks = 100)
    public void theGuideHoldsItsOwnGainsToTheLimit(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos home = h.absolutePos(new BlockPos(2, 2, 2));
        GiantsBridge.gx = home.getX() + 0.5;
        GiantsBridge.gy = home.getY();
        GiantsBridge.gz = home.getZ() + 0.5;
        set(h, s, "maxGiants", "1");
        set(h, s, "kitBook", "wreckback");
        // the kit: one who owns a giant already gets no book; one who owns none does
        UUID full = UUID.randomUUID(), empty = UUID.randomUUID();
        GiantsBridge.holder = full.toString();
        GiantsBridge.holderName = "kitfull";
        ServerPlayer kf = joinAs(h, full, "kitfull"), ke = joinAs(h, empty, "kitempty");
        h.assertFalse(OtherBridge.gave.contains("book " + full), "at the limit: no book in the kit " + OtherBridge.gave);
        h.assertTrue(OtherBridge.gave.contains("book " + empty), "room: the kit's book " + OtherBridge.gave);
        GiantsBridge.holder = null;
        OtherBridge.main = null;
        // (no more kit books for the ones who join below)
        set(h, s, "kitBook", "none");
        // My Giant: at the limit he is asked with no book, and they are told why
        set(h, s, "myGiant", "on");
        ServerPlayer a = PlayersGameTests.join(h, "picker", false), c = PlayersGameTests.join(h, "repicker", false);
        OtherBridge.main = a.getStringUUID();
        OtherBridge.mainName = "picker";
        JsonObject list = Players.pickJson(s, a);
        h.assertTrue(list.getAsJsonArray("giants").get(0).getAsJsonObject().get("note").getAsString().equals(
                "You already own 1 giant, the most this world allows: you'd go to his ground without his book."),
                "the pick card says so: " + list);
        AdminServer.Result r = Players.pick(s, a, "fakeplayers");
        h.assertTrue(r.ok() && Boolean.FALSE.equals(GiantsBridge.lastBook), "no book: " + r.lines());
        h.assertTrue(r.lines().contains("You already own 1 giant, the most this world allows."), "told: " + r.lines());
        h.assertTrue(PlayerData.get(s).s.picks.get(a.getStringUUID()).outcome.equals("placed"), "placed only");
        // already holding one of his books: not one more (a re-pick keeps theirs)
        GiantsBridge.own.put(c.getStringUUID(), "repicker");
        h.assertFalse(Players.pickJson(s, c).toString().contains("without his book"), "no note for their own");
        Players.pick(s, c, "fakeplayers");
        h.assertTrue(Boolean.TRUE.equals(GiantsBridge.lastBook), "their own: the book flag stays");
        // Live control's Set owner on someone at the limit: refused before the giant is asked
        GiantsBridge.does.add("owner");
        GiantsBridge.holder = null;
        GiantsBridge.own.clear();
        ServerPlayer op = PlayersGameTests.join(h, "liveop", true);
        AdminServer.Result lr = AdminLive.act(s, op, "fakeplayers", UUID.randomUUID().toString(), "owner",
                a.getStringUUID());
        h.assertTrue(!lr.ok() && lr.lines().get(0).equals("picker already owns 1 giant, the most this world allows"
                + " for them. Raise their limit on the Players page first."), "refused: " + lr.lines());
        h.assertTrue(GiantsBridge.acts.isEmpty(), "the giant never asked");
        Players.setMax(s, "Tester", "picker", "2");
        lr = AdminLive.act(s, op, "fakeplayers", UUID.randomUUID().toString(), "owner", a.getStringUUID());
        h.assertTrue(lr.ok() && GiantsBridge.acts.size() == 1, "room: done " + lr.lines());
        // holding his main book already: giving it to them again is no gain
        Players.setMax(s, "Tester", "picker", "default");
        lr = AdminLive.act(s, op, "fakeplayers", UUID.randomUUID().toString(), "owner", a.getStringUUID());
        h.assertTrue(lr.ok() && GiantsBridge.acts.size() == 2, "his holder again: " + lr.lines());
        PlayersGameTests.done(h, kf, ke, a, c, op);
    }

    // an older giant (no "owners"): counted from his live list, each owner once

    @GameTest(template = EMPTY_STRUCTURE, batch = "maxown_old")
    public void anOlderGiantIsCountedFromHisLiveList(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        AdminServer.useGiants(List.of(PlayersGameTests.FAKE, OTHER, OLD));
        ServerPlayer a = PlayersGameTests.join(h, "oldowner", false);
        GiantsBridge.speaksOwners = false;
        holds(a);
        GiantsBridge.standing.put(UUID.randomUUID().toString(), true);
        GiantsBridge.standing.put(UUID.randomUUID().toString(), true);
        set(h, s, "maxGiants", "2");
        h.assertTrue(Owns.all(s).of(a.getStringUUID()).size() == 1, "two of him, one owner: counted once "
                + Owns.all(s).claims());
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "1 of 2");
        OtherBridge.main = a.getStringUUID();
        OtherBridge.mainName = "oldowner";
        h.assertTrue(mayGain(s, a).get(0).equals("no"), "2 of 2 with an older giant among them");
        JsonObject p = person(s, "oldowner");
        h.assertTrue(p.getAsJsonArray("claims").toString().equals("[\"Fakeplayers\",\"Wreckback\"]"), "page " + p);
        // nobody standing: an older giant's owner can't be seen (not counted)
        GiantsBridge.standing.clear();
        h.assertTrue(List.of("ok").equals(mayGain(s, a)), "none standing: not counted");
        PlayersGameTests.done(h, a);
    }
}
