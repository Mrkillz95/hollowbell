package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsPlayers;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.world.MyGiant;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * (ADMIN_BRIDGE.md 20.18) Max giants per player, the giant's side: the Guide's world value and per-player override
 * arrive by the bridge, and every gain (taking up the book, an op's copy, the kit, a My Giant pick, Live control's
 * owner) asks the Guide first. The Guide isn't here, so GiantsPlayers.guideForTests answers for it. Every test has its
 * own batch and puts everything back.
 */
public class MaxGiantsTests implements FabricGameTest {

    /** the Guide's stand-in: each "maygain" counted; a player with a line here is refused with it */
    static final class Stub {
        final Map<UUID, String> no = new HashMap<>();
        final List<String> asked = new ArrayList<>();

        void install() {
            GiantsPlayers.guideForTests = (action, arg) -> {
                if (!action.equals("maygain")) return null;
                asked.add(arg);
                JsonObject o = JsonParser.parseString(arg).getAsJsonObject();
                String line = no.get(UUID.fromString(o.get("player").getAsString()));
                return line == null ? List.of("ok") : List.of("no", line);
            };
        }
    }

    private static final String AT_ONE = "You already own 1 giant, the most this world allows.";

    private static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    private static void rules(GameTestHelper h, String rules) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "players",
                "{\"players\":1,\"rules\":{" + rules + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "rules taken: " + r);
    }

    private static void putBack(MinecraftServer server) {
        GiantsPlayers.guideForTests = null;
        GiantsAdmin.clear();
        BookRule r = BookRule.get(server);
        r.free();
        r.forgetOwn();
    }

    private static Vec3 at(GameTestHelper h, int dx) {
        return Vec3.atCenterOf(h.absolutePos(new BlockPos(1 + dx, 2, 1)));
    }

    private static ItemStack oldCopy() {
        ItemStack s = new ItemStack(ModItems.CODEX);
        UUID id = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putUUID("HollowbellBook", id));
        return s;
    }

    private static int books(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.CODEX)) n++;
        if (p.containerMenu.getCarried().is(ModItems.CODEX)) n++;
        return n;
    }

    private static ItemStack firstBook(ServerPlayer p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.CODEX)) return p.getInventory().getItem(i);
        return ItemStack.EMPTY;
    }

    /** every translation key in a line (sendFailure wraps the line it's given) */
    private static void keys(Component c, List<String> out) {
        if (c.getContents() instanceof TranslatableContents t) out.add(t.getKey());
        for (Component sib : c.getSiblings()) keys(sib, out);
    }

    private static List<String> give(MinecraftServer s, ServerPlayer p) {
        return GiantsBridge.giants(s, "give", "{\"players\":1,\"player\":\"" + p.getUUID() + "\",\"what\":\"book\"}");
    }

    // an older Guide, or none: no limit at all

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_old")
    public void olderPayloadsWithoutTheNewFieldsMeanNoLimit(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            stub.install();
            stub.no.put(a.getUUID(), AT_ONE);
            // the setting is one he applies (the header's "uses")
            JsonObject head = JsonParser.parseString(GiantsAdmin.describe().get(0)).getAsJsonObject();
            h.assertTrue(head.getAsJsonArray("uses").toString().contains("\"all.maxGiants\""), "uses lists it");
            // an older Guide: the rules without "max", the switches without all.maxGiants
            rules(h, "\"" + a.getUUID() + "\":{\"own\":true,\"ignored\":false}");
            all(h, "\"all.sit\":true");
            h.assertTrue(GiantsPlayers.maxFor(a.getUUID()) == 0 && !GiantsPlayers.capped(a.getUUID()), "no limit");
            ItemStack fresh = new ItemStack(ModItems.CODEX);
            h.assertTrue(r.mayTake(a, fresh) && a.getUUID().equals(r.holder()), "takes the free book");
            h.assertTrue(stub.asked.isEmpty(), "the Guide is never asked: " + stub.asked);
            // "none" from a new Guide is the same
            r.free();
            all(h, "\"all.maxGiants\":\"none\"");
            h.assertTrue(!GiantsPlayers.capped(a.getUUID()) && r.mayTake(a, fresh) && stub.asked.isEmpty(),
                    "none: no limit, nobody asked");
            // no Guide at all (no stub): a limit in force still lets them (nobody to count)
            r.free();
            GiantsPlayers.guideForTests = null;
            all(h, "\"all.maxGiants\":\"1\"");
            h.assertTrue(GiantsPlayers.capped(a.getUUID()) && r.mayTake(a, new ItemStack(ModItems.CODEX)),
                    "no Guide to answer: allowed");
            // the players payload stays version 1; a newer one is still refused
            h.assertTrue(GiantsBridge.giants(server, "players", "{\"players\":2,\"rules\":{}}") == null, "newer: null");
        } finally {
            putBack(server);
            drop(a);
        }
        h.succeed();
    }

    // under the limit

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_under")
    public void underTheLimitEveryGainGoesThrough(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            stub.install();
            all(h, "\"all.maxGiants\":\"2\"");
            h.assertTrue(GiantsPlayers.maxFor(a.getUUID()) == 2, "the world's limit: " + GiantsPlayers.maxFor(a.getUUID()));
            // picking up a free book
            h.assertTrue(r.mayTake(a, new ItemStack(ModItems.CODEX)) && a.getUUID().equals(r.holder()), "taken");
            h.assertTrue(stub.asked.size() == 1, "asked once: " + stub.asked);
            JsonObject q = JsonParser.parseString(stub.asked.get(0)).getAsJsonObject();
            h.assertTrue(q.get("players").getAsInt() == 1 && q.get("player").getAsString().equals(a.getUUID().toString())
                    && q.get("giant").getAsString().equals("hollowbell"), "the question: " + q);
            // an op's copy in their pack, seen by the once-a-second look
            r.free();
            a.getInventory().clearContent();
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(a));
            h.assertTrue(a.getUUID().equals(r.holder()) && CodexItem.carriedBy(a), "an op's copy works");
            // the kit's book
            r.free();
            a.getInventory().clearContent();
            h.assertTrue(give(server, a).equals(List.of("ok")) && CodexItem.carriedBy(a), "the kit's book");
            h.assertTrue(GiantsPlayers.capLine(a.getUUID()) == null, "no refusal kept");
        } finally {
            putBack(server);
            drop(a);
        }
        h.succeed();
    }

    // at the limit

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "max_at")
    public void atTheLimitEveryGainIsRefusedWithTheGuidesLine(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        PlayerTests.Heard heard = new PlayerTests.Heard();
        ServerPlayer a = PlayerTests.listening(h, at(h, 0), heard);
        BookRule r = BookRule.get(server);
        ItemEntity it = null;
        try {
            r.free();
            stub.install();
            stub.no.put(a.getUUID(), AT_ONE);
            all(h, "\"all.maxGiants\":\"1\"");
            // a free book on the ground stays there, and they're told why
            it = new ItemEntity(h.getLevel(), a.getX(), a.getY(), a.getZ(), oldCopy());
            it.setNoPickUpDelay();
            h.getLevel().addFreshEntity(it);
            it.playerTouch(a);
            h.assertTrue(books(a) == 0 && !it.isRemoved() && r.holder() == null, "the book stayed on the ground");
            h.assertTrue(heard.keys.contains(AT_ONE), "told the Guide's line: " + heard.keys);
            // told at most every two seconds (a pickup is tried every tick)
            int told = heard.keys.size();
            it.playerTouch(a);
            h.assertTrue(heard.keys.size() == told, "not told again at once: " + heard.keys);
            // nor out of a chest
            SimpleContainer chest = new SimpleContainer(27);
            chest.setItem(0, oldCopy());
            ChestMenu menu = ChestMenu.threeRows(1, a.getInventory(), chest);
            a.containerMenu = menu;
            menu.clicked(0, 0, ClickType.QUICK_MOVE, a);
            menu.clicked(0, 0, ClickType.PICKUP, a);
            h.assertTrue(books(a) == 0 && chest.getItem(0).is(ModItems.CODEX), "took a book out of a chest");
            a.containerMenu = a.inventoryMenu;
            // an op's copy (the creative menu) stays quiet in their pack, and says why
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(a));
            h.assertTrue(r.holder() == null && !CodexItem.carriedBy(a) && BookRule.quiet(firstBook(a)),
                    "an op's copy stays quiet");
            heard.keys.clear();
            r.sayQuiet(a, firstBook(a));
            h.assertTrue(heard.keys.equals(List.of(AT_ONE)), "why it's quiet: " + heard.keys);
            // the first one seen with a copy: passed over
            r.look(List.of(a));
            h.assertTrue(r.holder() == null, "never made holder by the look");
            // using a brand-new copy doesn't claim it either
            ItemStack fresh = new ItemStack(ModItems.CODEX);
            h.assertFalse(r.works(a, fresh), "a new copy doesn't work");
            h.assertTrue(r.holder() == null && BookRule.quiet(fresh) && BookRule.idOf(fresh) != null,
                    "stamped and quiet");
            // given (as MyGiant and Live control give it): a quiet copy
            ItemStack g = new ItemStack(ModItems.CODEX);
            h.assertFalse(r.given(a, g), "given refused");
            h.assertTrue(r.holder() == null && BookRule.quiet(g), "quiet");
            // an op's /give: a quiet copy, and the op is told (to a player with a name of their own)
            ServerPlayer c = player(h, at(h, 2));
            stub.no.put(c.getUUID(), AT_ONE);
            List<String> opHeard = new ArrayList<>();
            CommandSource cs = new CommandSource() {
                @Override
                public void sendSystemMessage(Component c) {
                    keys(c, opHeard);
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
            };
            CommandSourceStack src = new CommandSourceStack(cs, Vec3.ZERO, Vec2.ZERO, h.getLevel(), 4, "op",
                    Component.literal("op"), server, null);
            server.getCommands().performPrefixedCommand(src,
                    "give " + c.getGameProfile().getName() + " hollowbell:hollowbell_codex");
            drop(c);
            h.assertTrue(books(c) == 1, "given one: " + books(c) + " " + opHeard);
            h.assertTrue(r.holder() == null, "not the holder: " + r.holderName());
            h.assertTrue(BookRule.quiet(firstBook(c)), "the given copy is quiet: " + firstBook(c).getComponents());
            h.assertTrue(opHeard.contains("message.hollowbell.players.max_op_one"), "the op told: " + opHeard);
            // the starting kit's book
            List<String> k = give(server, a);
            h.assertTrue(k.equals(List.of("no", AT_ONE)), "the kit's book refused: " + k);
            // the line stays theirs only while a limit is in force
            h.assertTrue(AT_ONE.equals(GiantsPlayers.capLine(a.getUUID())), "kept");
            all(h, "\"all.maxGiants\":\"none\"");
            h.assertTrue(GiantsPlayers.capLine(a.getUUID()) == null, "no limit: no line");
            it.playerTouch(a);
            h.assertTrue(a.getUUID().equals(r.holder()), "no limit: picked up and held");
        } finally {
            if (it != null) it.discard();
            putBack(server);
            drop(a);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "max_live")
    public void liveControlsOwnerIsRefusedAtTheLimit(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        HollowbellEntity e = spawnAway(h, HollowbellGameTests.S, HollowbellEntity.CALM, 1720);
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        BookRule r = BookRule.get(server);
        h.runAfterDelay(30, () -> {
            try {
                r.free();
                stub.install();
                stub.no.put(a.getUUID(), AT_ONE);
                all(h, "\"all.maxGiants\":\"1\"");
                List<String> res = LiveTests.act(server, e.getUUID(), "owner", a.getUUID().toString(), "-");
                h.assertTrue(res.equals(List.of("no", a.getGameProfile().getName() + " already owns 1 giant, the most"
                        + " this world allows for them. Raise their limit on the Players page first.")), "" + res);
                h.assertTrue(books(a) == 0 && r.holder() == null, "nothing added to their pack");
                // somebody under it still can
                res = LiveTests.act(server, e.getUUID(), "owner", b.getUUID().toString(), "-");
                h.assertTrue(res.get(0).equals("ok") && b.getUUID().equals(r.holder()), "b takes it: " + res);
                // the holder given another copy: no gain, never asked
                int asked = stub.asked.size();
                stub.no.put(b.getUUID(), AT_ONE);
                res = LiveTests.act(server, e.getUUID(), "owner", b.getUUID().toString(), "-");
                h.assertTrue(res.get(0).equals("ok") && stub.asked.size() == asked, "the holder again: " + res);
            } finally {
                putBack(server);
                e.discard();
                drop(a);
                drop(b);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "max_mygiant")
    public void aMyGiantPickAtTheLimitOnlyPlacesThem(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = SitTests.pad(h, 336);
        WorldOne w = WorldOne.get(server);
        boolean claimed = w.homeClaimed();
        int hx = w.homeX(), hz = w.homeZ();
        long seed = w.homeSeed();
        int capWas = BellConfig.V.maxInWorld;
        float scaleWas = BellConfig.V.worldScale;
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            w.claimHome(h.getLevel(), pad.getX(), pad.getZ());
            BellConfig.V.maxInWorld = 0;
            BellConfig.V.worldScale = 0.3f;
            stub.install();
            stub.no.put(a.getUUID(), AT_ONE);
            all(h, "\"all.maxGiants\":\"1\"");
            int before = WorldOne.standing(server);
            // nobody holds his book: not given, no one of him
            List<String> res = GiantsBridge.giants(server, "mygiant", "{\"players\":1,\"player\":\"" + a.getUUID()
                    + "\",\"book\":true,\"finder\":true,\"giant\":true}");
            h.assertTrue(res.equals(List.of("ok", "Welcome to the Hollowbell's ground. " + AT_ONE)), "main: " + res);
            h.assertTrue(MyGiant.lastDone.startsWith("placed false"), "told the Guide: " + MyGiant.lastDone);
            h.assertTrue(books(a) == 0 && r.holder() == null && WorldOne.standing(server) == before, "nothing gained");
            // My Giant on and b holds the main book: no own book either (and not shared: they own nothing)
            all(h, "\"all.maxGiants\":\"1\",\"all.myGiant\":true");
            b.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(b));
            h.assertTrue(b.getUUID().equals(r.holder()), "b holds it");
            res = GiantsBridge.giants(server, "mygiant", "{\"players\":1,\"player\":\"" + a.getUUID()
                    + "\",\"book\":true,\"finder\":true,\"giant\":true}");
            h.assertTrue(res.equals(List.of("ok", "Welcome to the Hollowbell's ground. " + AT_ONE)), "own: " + res);
            h.assertTrue(MyGiant.lastDone.startsWith("placed"), "placed: " + MyGiant.lastDone);
            h.assertTrue(books(a) == 0 && r.own(a.getUUID()) == null && WorldOne.standing(server) == before,
                    "no own book, no one of him");
        } finally {
            BellConfig.V.maxInWorld = capWas;
            BellConfig.V.worldScale = scaleWas;
            putBack(server);
            if (claimed) w.claimHome(server.overworld(), hx, hz, seed);
            else w.dropHome();
            drop(a);
            drop(b);
            SitTests.unpad(h, pad);
        }
        h.succeed();
    }

    // lowering the limit takes nothing away

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_lower")
    public void loweringTheLimitKeepsWhatTheyOwn(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            all(h, "\"all.myGiant\":true");
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(a));
            ItemStack own = r.makeOwn(a, null);
            a.getInventory().add(own.copy()); // (adding empties the stack added)
            h.assertTrue(a.getUUID().equals(r.holder()) && r.ownWorks(a, own), "a owns two");
            // the limit drops under what they own
            stub.install();
            stub.no.put(a.getUUID(), "You already own 2 giants, more than this world allows (1).");
            all(h, "\"all.myGiant\":true,\"all.maxGiants\":\"1\"");
            r.look(List.of(a));
            h.assertTrue(a.getUUID().equals(r.holder()) && CodexItem.carriedMainBy(a), "still the holder");
            h.assertTrue(r.ownWorks(a, own) && r.own(a.getUUID()) != null, "their own book still works");
            // their working copy moving about, or an op's new copy for the holder: no gain
            ItemStack working = firstBook(a).copy();
            h.assertTrue(r.mayTake(a, working), "re-takes their working copy");
            h.assertTrue(r.mayTake(a, own), "re-takes their own book");
            h.assertTrue(r.given(a, new ItemStack(ModItems.CODEX)), "the holder given a fresh copy");
            h.assertTrue(stub.asked.isEmpty(), "never asked: " + stub.asked);
            // the Guide's count sees both
            List<String> o = GiantsBridge.giants(server, "owners", "{\"owners\":1}");
            JsonObject ans = JsonParser.parseString(o.get(1)).getAsJsonObject();
            h.assertTrue(o.get(0).equals("ok") && ans.get("main").getAsString().equals(a.getUUID().toString())
                    && ans.getAsJsonArray("own").size() == 1, "owners: " + o);
            // once the book has gone: taking a free one is a gain again, and refused
            r.free();
            h.assertFalse(r.mayTake(a, new ItemStack(ModItems.CODEX)), "refused now");
            h.assertTrue(stub.asked.size() == 1 && r.holder() == null, "asked: " + stub.asked);
        } finally {
            putBack(server);
            drop(a);
        }
        h.succeed();
    }

    // each player's own limit

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_override")
    public void aPlayersOwnLimitBeatsTheWorlds(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        BookRule r = BookRule.get(server);
        UUID c = UUID.randomUUID(), d = UUID.randomUUID(), e = UUID.randomUUID();
        try {
            r.free();
            stub.install();
            all(h, "\"all.maxGiants\":\"2\"");
            rules(h, "\"" + a.getUUID() + "\":{\"max\":5},\"" + b.getUUID() + "\":{\"max\":0},\"" + c
                    + "\":{\"max\":12},\"" + d + "\":{\"own\":true,\"ignored\":false},\"" + e + "\":{\"max\":-4}");
            h.assertTrue(GiantsPlayers.maxFor(a.getUUID()) == 5, "a's own 5");
            h.assertTrue(GiantsPlayers.maxFor(b.getUUID()) == 0 && !GiantsPlayers.capped(b.getUUID()), "b: no limit");
            h.assertTrue(GiantsPlayers.maxFor(c) == 9 && GiantsPlayers.maxFor(e) == 0, "clamped to 0..9");
            h.assertTrue(GiantsPlayers.maxFor(d) == 2 && GiantsPlayers.maxFor(UUID.randomUUID()) == 2, "the world's");
            h.assertTrue(GiantsPlayers.mayOwn(a) && !GiantsPlayers.ignored(a), "a rule with only a max: may own");
            // b has no limit even with the world's at 2: never asked
            stub.no.put(b.getUUID(), AT_ONE);
            h.assertTrue(r.mayTake(b, new ItemStack(ModItems.CODEX)) && b.getUUID().equals(r.holder()), "b takes it");
            h.assertTrue(stub.asked.isEmpty(), "b's gain not asked: " + stub.asked);
            // a is asked (their own limit is in force); the Guide says yes
            r.free();
            h.assertTrue(r.mayTake(a, new ItemStack(ModItems.CODEX)) && a.getUUID().equals(r.holder()), "a takes it");
            h.assertTrue(stub.asked.size() == 1, "a asked: " + stub.asked);
            // the override taken away: the world's again; the world's turned off: no limit
            rules(h, "");
            h.assertTrue(GiantsPlayers.maxFor(a.getUUID()) == 2 && GiantsPlayers.maxFor(b.getUUID()) == 2, "world");
            all(h, "");
            h.assertTrue(GiantsPlayers.maxFor(a.getUUID()) == 0, "absent: no limit");
        } finally {
            putBack(server);
            drop(a);
            drop(b);
        }
        h.succeed();
    }

    // the first one seen with a copy (and a burnt book passing on): one at the limit is passed over

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_pass")
    public void theBookPassesOverOneAtTheLimitToTheNext(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        Stub stub = new Stub();
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            stub.install();
            stub.no.put(a.getUUID(), AT_ONE);
            all(h, "\"all.maxGiants\":\"1\"");
            a.getInventory().add(oldCopy());
            b.getInventory().add(oldCopy());
            r.look(List.of(a, b));
            h.assertTrue(b.getUUID().equals(r.holder()), "b, the next with a copy, holds it: " + r.holderName());
            h.assertTrue(BookRule.quiet(firstBook(a)) && !BookRule.quiet(firstBook(b)), "a's copy quiet");
            h.assertTrue(stub.asked.size() == 2, "both asked: " + stub.asked);
        } finally {
            putBack(server);
            drop(a);
            drop(b);
        }
        h.succeed();
    }

    // the Guide's count

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "max_owners")
    public void ownersTellsTheGuideWhoOwnsHim(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerPlayer a = player(h, at(h, 0)), b = player(h, at(h, 2));
        BookRule r = BookRule.get(server);
        try {
            r.free();
            r.forgetOwn();
            List<String> o = GiantsBridge.giants(server, "owners", "{\"owners\":1}");
            JsonObject ans = JsonParser.parseString(o.get(1)).getAsJsonObject();
            h.assertTrue(o.get(0).equals("ok") && ans.get("owners").getAsInt() == 1 && !ans.has("main")
                    && ans.getAsJsonArray("own").isEmpty(), "nobody: " + o);
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(a));
            r.makeOwn(b, null);
            o = GiantsBridge.giants(server, "owners", "{\"owners\":1}");
            ans = JsonParser.parseString(o.get(1)).getAsJsonObject();
            h.assertTrue(ans.get("main").getAsString().equals(a.getUUID().toString())
                    && ans.get("mainName").getAsString().equals(a.getGameProfile().getName())
                    && ans.getAsJsonArray("own").isEmpty(), "My Giant off: own books not counted: " + o);
            all(h, "\"all.myGiant\":true");
            o = GiantsBridge.giants(server, "owners", "{\"owners\":1}");
            ans = JsonParser.parseString(o.get(1)).getAsJsonObject();
            JsonObject one = ans.getAsJsonArray("own").get(0).getAsJsonObject();
            h.assertTrue(one.get("id").getAsString().equals(b.getUUID().toString())
                    && one.get("name").getAsString().equals(b.getGameProfile().getName()), "own: " + o);
            h.assertTrue(GiantsBridge.giants(server, "owners", "{\"owners\":2}") == null, "newer: null");
            h.assertTrue(GiantsBridge.giants(server, "owners", "nonsense") == null, "unreadable: null");
        } finally {
            putBack(server);
            drop(a);
            drop(b);
        }
        h.succeed();
    }
}
