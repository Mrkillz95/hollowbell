package net.jj.giants.test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.FirstJoin;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.net.AdminBookStatePayload;
import net.jj.giants.net.PlayersStatePayload;
import net.jj.giants.players.Boards;
import net.jj.giants.players.Challenges;
import net.jj.giants.players.PlayerData;
import net.jj.giants.players.Players;
import net.jj.giants.players.PlayersAdmin;
import net.jj.giants.players.RulesPage;
import net.jj.giants.players.Votes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * Part 5, "Players" (ADMIN_BRIDGE.md 20), the Guide's side with a stand-in giant (net.jj.fakeplayers): the player
 * settings and presets, My Giant's pick, per-player rules, the starting kit, the rules page, votes, leaderboards and
 * the challenges. Each test has its own batch and puts the world back.
 */
public class PlayersGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakeplayers", "fakeplayers", "fakeplayers", "net.jj.fakeplayers.GiantsBridge",
            List.of(), Set.of());

    private record Sent(ServerPlayer to, PlayersStatePayload page) {}

    private static final List<Sent> sent = new ArrayList<>();

    /**
     * With the real giants loaded (-Pgiants) these stand-in tests are left out: they put the stand-in giant in place
     * of the real ones and apply whole presets, which would undo the settings of the real-giant tests running beside
     * them. PlayersGiantsGameTests covers the real giants; the plain run covers these.
     */
    static boolean realGiants(GameTestHelper h) {
        if (net.jj.giants.Giants.installed().isEmpty()) return false;
        h.succeed();
        return true;
    }

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        AdminServer.useGiants(List.of(FAKE));
        AdminWorld w = AdminWorld.get(s);
        // (only what these tests touch: other batches run beside them with their own settings)
        for (String g : List.of("all", "fakeplayers")) w.clear(g);
        w.forgetExtras();
        PlayerData.get(s).s = new PlayerData.State();
        net.jj.fakeplayers.GiantsBridge.reset();
        AdminServer.push(s, w);
        AdminTransfer.forget();
        AdminBook.forget();
        Challenges.forget();
        sent.clear();
        Players.sender = (p, page) -> sent.add(new Sent(p, page));
        Players.runningForTests(s);
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            s.getPlayerList().remove(p);
        }
        AdminWorld w = AdminWorld.get(s);
        // (only what these tests touch: other batches run beside them with their own settings)
        for (String g : List.of("all", "fakeplayers")) w.clear(g);
        PlayerData.get(s).s = new PlayerData.State();
        AdminServer.push(s, w);
        Players.sender = (p, page) -> {
            if (net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p, PlayersStatePayload.TYPE))
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, page);
        };
        AdminServer.useGiants(null);
        h.succeed();
    }

    /** a player who joins for real (the join events run) */
    static ServerPlayer join(GameTestHelper h, String name, boolean op) {
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(),
                cookie.clientInformation());
        if (op) h.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4,
                false));
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        return p;
    }

    private static JsonObject last(ServerPlayer p, String kind) {
        JsonObject out = null;
        for (Sent x : sent)
            if (x.to() == p && x.page().kind().equals(kind)) out = JsonParser.parseString(x.page().json())
                    .getAsJsonObject();
        return out;
    }

    private static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item it) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(it)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    // the settings and the presets

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_settings")
    public void playerSettingsAreWorldSettingsThatPresetsLeaveAlone(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        List<String> ids = new ArrayList<>();
        for (AdminSetting x : AdminCatalog.players()) ids.add(x.id());
        for (String id : List.of("all.myGiant", "all.rulesPage", "all.votes", "all.boards", "all.boardSlayers",
                "all.boardOwners", "all.boardFriends", "all.achievements", "all.kitGuide", "all.kitFinders",
                "all.kitFinder", "all.kitArmour", "all.kitBook", "all.sit", "all.ride", "all.stand",
                "all.armourPowers", "all.bossBars", "all.fightMusic", "all.chatMeetings", "all.chatMoves",
                "all.chatBackDown", "all.books", "all.bookOrders", "all.findersCraftable"))
            h.assertTrue(ids.contains(id), "a player setting " + id);
        for (AdminSetting x : AdminCatalog.players())
            h.assertTrue(x.cat().equals("players") && x.when().equals("live"), x.id() + " is a live player setting");
        // as shipped: everything allowed, no My Giant
        h.assertTrue(!Players.on(s, "myGiant") && Players.on(s, "ride") && Players.option(s, "bossBars").equals("usual")
                && Players.option(s, "kitFinders").equals("none"), "as shipped");
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", "ride", "off");
        h.assertTrue(r.ok() && r.lines().get(0).contains("Riding giants set to off"), "no x on a switch: " + r.lines());
        h.assertTrue(net.jj.fakeplayers.GiantsBridge.all.get("all.ride").getAsBoolean() == false,
                "the giants are sent it with the multipliers: " + net.jj.fakeplayers.GiantsBridge.all);
        r = AdminServer.setDefault(s, "Tester", "all", "ride", "on");
        h.assertFalse(r.ok(), "no server default for a player setting");
        h.assertTrue(r.lines().get(0).contains("player settings are only kept for each world"), r.lines().get(0));
        // a preset changes the giants, not the player settings (except My Giant, which each one names)
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        h.assertFalse(Players.on(s, "ride"), "Titans left riding off");
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Titans"), "on Titans");
        set(h, s, "sit", "off");
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Titans"),
                "a player setting doesn't make it 'changed'");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("my_giant")));
        h.assertTrue(Players.on(s, "myGiant") && !Players.on(s, "ride"), "My Giant turns My Giant on, riding stays off");
        h.assertTrue(AdminTransfer.group("my_giant").equals("suggested"), "My Giant is a suggested preset now");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("classic")));
        h.assertTrue(!Players.on(s, "myGiant") && !Players.on(s, "ride"),
                "Classic turns My Giant off, keeps the other player settings");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("speedrun")));
        h.assertTrue(Players.option(s, "kitFinders").equals("all")
                && AdminTransfer.preset("speedrun").about().contains("finder"), "Speedrun puts finders in the kit");
        // the clashes
        set(h, s, "myGiant", "on");
        set(h, s, "books", "off");
        boolean warned = false;
        for (AdminInsight.Warning w : AdminInsight.warnings(List.of(), net.jj.giants.admin.AdminBook
                .allValuesForTests(AdminWorld.get(s)))) warned |= w.rule().equals("my_giant_books_off");
        h.assertTrue(warned, "My Giant with books off is a clash");
        done(h);
    }

    // My Giant

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_pick", timeoutTicks = 100)
    public void pickingTakesYouToHisGroundWithHisBookAndOneOfHim(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        BlockPos home = h.absolutePos(new BlockPos(2, 2, 2));
        net.jj.fakeplayers.GiantsBridge.gx = home.getX() + 0.5;
        net.jj.fakeplayers.GiantsBridge.gy = home.getY();
        net.jj.fakeplayers.GiantsBridge.gz = home.getZ() + 0.5;
        // My Giant off: no pick screen, picking refused
        ServerPlayer early = join(h, "early", false);
        h.assertTrue(last(early, PlayersStatePayload.PICK) == null, "no pick screen while My Giant is off");
        h.assertFalse(Players.pick(s, early, "fakeplayers").ok(), "no picking while it's off");
        set(h, s, "myGiant", "on");
        ServerPlayer a = join(h, "alex", false), b = join(h, "bea", false), c = join(h, "cal", false),
                d = join(h, "dot", false), e = join(h, "eve", false);
        JsonObject list = last(a, PlayersStatePayload.PICK);
        h.assertTrue(list != null, "joining a My Giant world opens the pick screen");
        JsonObject g0 = list.getAsJsonArray("giants").get(0).getAsJsonObject();
        h.assertTrue(g0.get("key").getAsString().equals("fakeplayers") && g0.has("danger") && g0.has("label")
                && g0.get("note").getAsString().isEmpty(), "the list: " + list);
        AdminServer.Result r = Players.pick(s, a, "fakeplayers");
        h.assertTrue(r.ok() && r.lines().get(0).contains("is yours"), "a picks: " + r.lines());
        h.assertTrue(a.blockPosition().equals(home), "taken to his ground: " + a.blockPosition());
        PlayerData.Pick pk = PlayerData.get(s).s.picks.get(a.getStringUUID());
        h.assertTrue(pk != null && pk.outcome.equals("own"), "the pick and its outcome kept");
        h.assertTrue(net.jj.fakeplayers.GiantsBridge.standing.size() == 1, "one of him came");
        h.assertFalse(Players.pick(s, a, "fakeplayers").ok(), "only one pick");
        // someone else holds the book, with room: b gets their own book and their own one (BOOK_RULE.md's My Giant
        // exception), and the list says so
        JsonObject forB = Players.pickJson(s, b);
        h.assertTrue(forB.getAsJsonArray("giants").get(0).getAsJsonObject().get("note").getAsString()
                .contains("alex has his book: you'd get your own book"), "the list says b gets their own: " + forB);
        h.assertTrue(Players.pick(s, b, "fakeplayers").lines().get(0).contains("is yours"), "b owns their own");
        h.assertTrue(PlayerData.get(s).s.picks.get(b.getStringUUID()).outcome.equals("own")
                && net.jj.fakeplayers.GiantsBridge.standing.size() == 2, "kept as own, a second one came");
        // the world's limit with someone holding the book: c shares (the list says so)
        net.jj.fakeplayers.GiantsBridge.most = 2;
        JsonObject forC = Players.pickJson(s, c);
        h.assertTrue(forC.getAsJsonArray("giants").get(0).getAsJsonObject().get("note").getAsString()
                .contains("you'd share alex's book"), "the list says c would share: " + forC);
        h.assertTrue(Players.pick(s, c, "fakeplayers").lines().get(0).contains("share alex's"), "c shares a's");
        h.assertTrue(PlayerData.get(s).s.picks.get(c.getStringUUID()).outcome.equals("shared")
                && net.jj.fakeplayers.GiantsBridge.standing.size() == 2, "kept as shared, none more came");
        // the world's limit with nobody holding the book: the next holder takes charge of the ones there are
        net.jj.fakeplayers.GiantsBridge.holder = null;
        h.assertTrue(Players.pick(s, e, "fakeplayers").lines().get(0).contains("take charge"), "e at the limit");
        h.assertTrue(PlayerData.get(s).s.picks.get(e.getStringUUID()).outcome.equals("own_cap")
                && net.jj.fakeplayers.GiantsBridge.standing.size() == 2, "own_cap, none more came");
        // may not own giants: only taken to his ground
        Players.setRule(s, "Tester", "dot", "own", false);
        Players.pick(s, d, "fakeplayers");
        h.assertTrue(PlayerData.get(s).s.picks.get(d.getStringUUID()).outcome.equals("placed"), "d placed only");
        // an admin lets a pick again: the screen opens again for them
        sent.clear();
        AdminServer.Result rp = Players.repick(s, "Tester", "alex");
        h.assertTrue(rp.ok() && !PlayerData.get(s).s.picks.containsKey(a.getStringUUID()), "a's pick forgotten");
        h.assertTrue(last(a, PlayersStatePayload.PICK) != null, "the pick screen opened again");
        done(h, early, a, b, c, d, e);
    }

    // per-player rules

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_rules")
    public void rulesArePushedToTheGiantsAndKeptWithTheWorld(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = join(h, "ann", false);
        AdminServer.Result r = Players.setRule(s, "Tester", "ann", "own", false);
        h.assertTrue(r.ok() && r.lines().get(0).equals("ann may not own giants in this world."), r.lines().toString());
        Players.setRule(s, "Tester", a.getStringUUID(), "ignored", true);
        Players.setRule(s, "Tester", "ann", "nokit", true);
        JsonObject rule = net.jj.fakeplayers.GiantsBridge.rules.getAsJsonObject(a.getStringUUID());
        h.assertTrue(rule != null && !rule.get("own").getAsBoolean() && rule.get("ignored").getAsBoolean(),
                "the giants have it: " + net.jj.fakeplayers.GiantsBridge.rules);
        h.assertFalse(rule.has("nokit"), "no kit is the Guide's own");
        PlayerData back = PlayerData.get(s).reread();
        h.assertTrue(!back.rule(a.getStringUUID()).own && back.rule(a.getStringUUID()).ignored
                && back.rule(a.getStringUUID()).noKit, "kept with the world");
        AdminWorld.Change c = AdminWorld.get(s).log().get(AdminWorld.get(s).log().size() - 1);
        h.assertTrue(c.id().equals("players:nokit") && AdminServer.changeText(c).equals("ann gets no starting kit"),
                "logged: " + AdminServer.changeText(c));
        Players.setRule(s, "Tester", "ann", "own", true);
        Players.setRule(s, "Tester", "ann", "ignored", false);
        h.assertTrue(net.jj.fakeplayers.GiantsBridge.rules.entrySet().isEmpty(), "rules cleared: none sent");
        h.assertFalse(Players.setRule(s, "Tester", "nobody_like_that", "own", false).ok(), "an unknown player");
        h.assertFalse(Players.setRule(s, "Tester", "ann", "fly", true).ok(), "an unknown rule");
        // through the admin book (an admin's request) the same
        ServerPlayer op = join(h, "op", true);
        AdminServer.Result br = AdminBook.change(s, op, "{\"do\":\"prule\",\"player\":\"" + a.getStringUUID()
                + "\",\"rule\":\"ignored\",\"on\":\"true\"}");
        h.assertTrue(br.ok() && PlayerData.get(s).rule(a.getStringUUID()).ignored, "the book's request");
        ServerPlayer nobody = join(h, "nobody", false);
        AdminServer.Result nr = AdminBook.change(s, nobody, "{\"do\":\"prule\",\"player\":\"ann\",\"rule\":\"own\","
                + "\"on\":\"false\"}");
        h.assertFalse(nr.ok(), "not for players who aren't admins");
        JsonObject page = PlayersAdmin.json(s);
        boolean listed = false;
        for (JsonElement e : page.getAsJsonArray("rules"))
            listed |= e.getAsJsonObject().get("name").getAsString().equals("ann")
                    && e.getAsJsonObject().get("ignored").getAsBoolean();
        h.assertTrue(listed, "the Players page lists her: " + page);
        done(h, a, op, nobody);
    }

    // the starting kit

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_kit")
    public void theKitIsWhatTheWorldSays(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        set(h, s, "kitArmour", "iron");
        set(h, s, "kitFinders", "all");
        ServerPlayer a = join(h, "kitted", false);
        h.assertTrue(count(a, GiantsGuideMod.GUIDE) == 1, "the Guide");
        h.assertTrue(a.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                && a.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS), "iron armour on");
        h.assertTrue(net.jj.fakeplayers.GiantsBridge.gave.contains("finder " + a.getStringUUID())
                , "the finder: "
                + net.jj.fakeplayers.GiantsBridge.gave);
        h.assertTrue(FirstJoin.get(s).had(a.getUUID()), "given once");
        ServerPlayer b = join(h, "second", false);
        // the Guide off, and the no-kit rule
        set(h, s, "kitGuide", "off");
        set(h, s, "kitArmour", "none");
        ServerPlayer c = join(h, "noguide", false);
        h.assertTrue(count(c, GiantsGuideMod.GUIDE) == 0, "no Guide when it's off");
        UUID id = UUID.randomUUID();
        PlayerData.Rule rule = new PlayerData.Rule();
        rule.noKit = true;
        rule.name = "nokit";
        PlayerData.get(s).s.rules.put(id.toString(), rule);
        set(h, s, "kitGuide", "on");
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "nokit"), false);
        ServerPlayer d = new ServerPlayer(s, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        Connection cn = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(cn);
        s.getPlayerList().placeNewPlayer(cn, d, cookie);
        h.assertTrue(count(d, GiantsGuideMod.GUIDE) == 0 && !net.jj.fakeplayers.GiantsBridge.gave.contains("finder "
                + id), "no kit at all for them");
        done(h, a, b, c, d);
    }

    // the rules page

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_rulespage")
    public void theRulesPageSaysWhatIsDifferent(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        JsonObject r = RulesPage.json(s);
        // (with the real giants loaded, other batches may have their own settings in this world just now)
        if (AdminWorld.get(s).giantsWithValues().isEmpty()) {
            h.assertTrue(r.get("preset").getAsString().equals("Classic")
                    && r.get("about").getAsString().contains("usual")
                    && r.getAsJsonObject("danger").get("level").getAsInt() == 3, "Classic: " + r);
            h.assertTrue(r.getAsJsonArray("rules").get(0).getAsString().contains("as in Classic"),
                    "nothing different");
        }
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        set(h, s, "ride", "off");
        r = RulesPage.json(s);
        String rules = r.getAsJsonArray("rules").toString(), players = r.getAsJsonArray("players").toString();
        h.assertTrue(r.get("preset").getAsString().equals("Titans") && r.get("about").getAsString().contains("Twice"),
                "the preset and its about: " + r);
        h.assertTrue(r.getAsJsonObject("danger").get("level").getAsInt() >= 4, "Titans: dangerous " + r);
        h.assertTrue(rules.contains("Every giant's health x2.5") && rules.contains("Every giant's damage x3"),
                "the multipliers: " + rules);
        h.assertTrue(players.contains("Riding giants: off"), "the player settings: " + players);
        set(h, s, "rulesPage", "basic");
        r = RulesPage.json(s);
        h.assertTrue(r.has("preset") && !r.has("rules") && !r.has("danger"), "basic: the preset only " + r);
        set(h, s, "rulesPage", "hidden");
        r = RulesPage.json(s);
        h.assertTrue(r.has("hidden") && !r.has("preset"), "hidden " + r);
        done(h);
    }

    // votes

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_votes")
    public void aVoteOpensCountsAndAppliesTheWinner(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = join(h, "va", false), b = join(h, "vb", false), c = join(h, "vc", false);
        h.assertFalse(Votes.open(s, "Tester", List.of("titans"), 5, true).ok(), "one option is no vote");
        h.assertFalse(Votes.open(s, "Tester", List.of("titans", "nonsense"), 5, true).ok(), "an unknown preset");
        h.assertFalse(Votes.vote(s, a, 1).ok(), "no vote open");
        AdminServer.Result r = Votes.open(s, "Tester", List.of("titans", "Gentle Giants", "lite"), 5, true);
        h.assertTrue(r.ok(), "open: " + r.lines());
        h.assertFalse(Votes.open(s, "Tester", List.of("titans", "lite"), 5, true).ok(), "one at a time");
        h.assertTrue(Votes.vote(s, a, 1).lines().get(0).equals("You voted for Titans."), "a votes");
        Votes.vote(s, b, 1);
        Votes.vote(s, c, 2);
        h.assertTrue(Votes.vote(s, c, 1).lines().get(0).startsWith("You changed your vote"), "c changes");
        h.assertFalse(Votes.vote(s, c, 9).ok(), "not an option");
        JsonObject v = Votes.json(s, a).getAsJsonObject("open");
        h.assertTrue(v.get("mine").getAsInt() == 0 && v.getAsJsonArray("options").get(0).getAsJsonObject()
                .get("votes").getAsInt() == 3, "the count: " + v);
        // kept with the world
        h.assertTrue(PlayerData.get(s).reread().s.vote.votes.size() == 3, "the open vote is saved");
        r = Votes.end(s, "Tester", true);
        h.assertTrue(r.lines().get(0).equals("Vote: Titans won with 3 of 3 votes."), r.lines().get(0));
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Titans"),
                "the winner applied by itself");
        AdminWorld.Change ch = AdminWorld.get(s).log().get(AdminWorld.get(s).log().size() - 1);
        h.assertTrue(ch.who().equals("Vote"), "applied by the Vote");
        // a tie goes to the one listed first; ask: nothing applied
        Votes.open(s, "Tester", List.of("lite", "nomads"), 1, false);
        Votes.vote(s, a, 2);
        Votes.vote(s, b, 1);
        r = Votes.end(s, "Tester", true);
        h.assertTrue(r.lines().get(0).startsWith("Vote: Lite won with 1 of 2 votes (a tie"), r.lines().get(0));
        h.assertTrue(AdminInsight.presetName(AdminWorld.get(s).log(), true).equals("Titans"),
                "ask: not applied");
        // running out of time
        Votes.open(s, "Tester", List.of("lite", "nomads"), 1, false);
        PlayerData.get(s).s.vote.endsAt = s.overworld().getGameTime();
        Votes.tick(s);
        h.assertTrue(PlayerData.get(s).s.vote == null && PlayerData.get(s).s.last.text.contains("nobody voted"),
                "ended on time, nobody voted");
        // votes off: none opens, an open one is called off
        Votes.open(s, "Tester", List.of("lite", "nomads"), 1, false);
        set(h, s, "votes", "off");
        Votes.tick(s);
        h.assertTrue(PlayerData.get(s).s.vote == null, "called off");
        h.assertFalse(Votes.open(s, "Tester", List.of("lite", "nomads"), 1, false).ok(), "votes off");
        done(h, a, b, c);
    }

    // leaderboards

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_boards")
    public void leaderboardsCountAndAreKept(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = join(h, "slayer", false), b = join(h, "keeper", false);
        Players.slain(s, "wreckback", a);
        Players.slain(s, "wreckback", a);
        Players.slain(s, "cerberus", a);
        Players.slain(s, "cerberus", b);
        Players.slain(s, "cerberus", null);
        PlayerData.Stats st = PlayerData.get(s).s.stats.get(a.getStringUUID());
        h.assertTrue(st.killsAll() == 3 && st.kills.get("wreckback") == 2, "by kind and in all: " + st.kills);
        // owners: b holds the book while two of him stand; two looks in a row make a streak
        net.jj.fakeplayers.GiantsBridge.holder = b.getStringUUID();
        net.jj.fakeplayers.GiantsBridge.holderName = "keeper";
        net.jj.fakeplayers.GiantsBridge.standing.put(UUID.randomUUID().toString(), true);
        net.jj.fakeplayers.GiantsBridge.standing.put(UUID.randomUUID().toString(), true);
        Boards.lookAtOwners(s);
        PlayerData.Stats sb = PlayerData.get(s).s.stats.get(b.getStringUUID());
        h.assertTrue(sb.owned.size() == 2 && sb.streak == 0, "two befriended, no streak yet");
        sb.last -= 600;
        Boards.lookAtOwners(s);
        h.assertTrue(sb.streak == 600 && sb.longest == 600, "the streak: " + sb.streak + " last " + sb.last + " now "
                + s.overworld().getGameTime() + " owned " + sb.owned.size());
        net.jj.fakeplayers.GiantsBridge.holder = null;
        Boards.lookAtOwners(s);
        h.assertTrue(sb.streak == 0 && sb.longest == 600, "a look without ends it, the best stays");
        PlayerData back = PlayerData.get(s).reread();
        h.assertTrue(back.s.stats.get(a.getStringUUID()).kills.get("cerberus") == 1
                && back.s.stats.get(b.getStringUUID()).longest == 600, "kept with the world");
        JsonObject j = Boards.json(s, a.getStringUUID(), 10);
        JsonArray boards = j.getAsJsonArray("boards");
        h.assertTrue(boards.size() == 3, "three boards");
        JsonObject slayers = boards.get(0).getAsJsonObject();
        h.assertTrue(slayers.getAsJsonArray("rows").get(0).getAsJsonObject().get("name").getAsString()
                .equals("slayer") && slayers.get("myPlace").getAsInt() == 1, "slayer first: " + slayers);
        // a sign at spawn
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        h.getLevel().setBlockAndUpdate(at, Blocks.OAK_SIGN.defaultBlockState());
        h.assertTrue(Boards.setSign(s, h.getLevel(), at, "slayers").ok(), "made a board");
        SignBlockEntity sign = (SignBlockEntity) h.getLevel().getBlockEntity(at);
        h.assertTrue(sign.getFrontText().getMessage(0, false).getString().equals("Giant slayers")
                && sign.getFrontText().getMessage(1, false).getString().equals("1 slayer 3"),
                "the sign: " + sign.getFrontText().getMessage(1, false).getString());
        set(h, s, "boardSlayers", "off");
        Boards.updateSigns(s);
        h.assertTrue(sign.getFrontText().getMessage(1, false).getString().equals("Turned off"), "hidden board");
        h.assertTrue(Boards.json(s, null, 5).getAsJsonArray("boards").size() == 2, "only the boards shown");
        h.getLevel().setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        Boards.updateSigns(s);
        h.assertTrue(PlayerData.get(s).s.signs.isEmpty(), "a sign that's gone is dropped");
        done(h, a, b);
    }

    // challenges

    @GameTest(template = EMPTY_STRUCTURE, batch = "p5_challenges")
    public void challengesOnlyCountInTheirPreset(GameTestHelper h) {
        if (realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = join(h, "hero", false);
        Players.slain(s, "wreckback", a);
        h.assertFalse(Challenges.done(a, "titan_slayer"), "not in Classic");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("titans")));
        Players.slain(s, "wreckback", a);
        h.assertTrue(Challenges.done(a, "titan_slayer") && Challenges.done(a, Challenges.ROOT), "Titan Slayer");
        h.assertFalse(Challenges.done(a, "against_the_odds"), "not Hardcore's");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, AdminTransfer.preset("boss_rush")));
        set(h, s, "achievements", "off");
        for (int i = 0; i < 3; i++) Players.slain(s, "wreckback", a);
        h.assertFalse(Challenges.done(a, "rush_hour"), "challenges off");
        set(h, s, "achievements", "on");
        for (int i = 0; i < 3; i++) Players.slain(s, "wreckback", a);
        h.assertTrue(Challenges.done(a, "rush_hour"), "three in Boss Rush");
        done(h, a);
    }
}
