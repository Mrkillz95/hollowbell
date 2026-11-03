package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.SitTests.after;
import static net.jj.hollowbell.test.SitTests.done;
import static net.jj.hollowbell.test.SitTests.giant;
import static net.jj.hollowbell.test.SitTests.pad;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsPlayers;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.solid.Sitting;
import net.jj.hollowbell.world.BellWorld;
import net.jj.hollowbell.world.MyGiant;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Part 5 (ADMIN_BRIDGE.md 20): the Giants Guide's player switches, per-player rules, My Giant and the starting kit,
 * the way the Guide sends them (the bridge). Every test has its own batch and puts everything back.
 */
public class PlayerTests implements FabricGameTest {

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

    /** a player whose lines (chat and action bar) are kept */
    static final class Heard {
        final List<String> keys = new ArrayList<>();

        boolean said(String keyPart) {
            for (String k : keys) if (k.contains(keyPart)) return true;
            return false;
        }
    }

    static ServerPlayer listening(GameTestHelper h, Vec3 at, Heard heard) {
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "hb-p5"), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void displayClientMessage(Component c, boolean bar) {
                synchronized (heard.keys) {
                    heard.keys.add(c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString());
                }
            }

            @Override
            public void sendSystemMessage(Component c) {
                synchronized (heard.keys) {
                    heard.keys.add(c.getContents() instanceof TranslatableContents t ? t.getKey() : c.getString());
                }
            }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    private static Vec3 at(GameTestHelper h) {
        return Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
    }

    // the switches

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "p5_switches")
    public void theSwitchesAreListedAndAsShippedEverythingIsAllowed(GameTestHelper h) {
        JsonObject head = JsonParser.parseString(GiantsAdmin.describe().get(0)).getAsJsonObject();
        JsonArray uses = head.getAsJsonArray("uses");
        for (String id : GiantsPlayers.USES)
            h.assertTrue(uses.toString().contains("\"" + id + "\""), "uses lists " + id);
        h.assertTrue(GiantsPlayers.sit() && GiantsPlayers.ride() && GiantsPlayers.stand()
                && GiantsPlayers.armourPowers() && GiantsPlayers.chatMeetings() && GiantsPlayers.chatMoves()
                && GiantsPlayers.chatBackDown() && GiantsPlayers.books() && GiantsPlayers.attackOrders()
                && GiantsPlayers.bossBars().equals("usual"), "every switch on as shipped");
        h.assertTrue(GiantsPlayers.bossBar(true) && !GiantsPlayers.bossBar(false), "boss bars: his own setting");
        try {
            all(h, "\"all.health\":2,\"all.sit\":false,\"all.ride\":false,\"all.stand\":false,"
                    + "\"all.armourPowers\":false,\"all.bossBars\":\"on\",\"all.chatMeetings\":false,"
                    + "\"all.chatMoves\":false,\"all.chatBackDown\":false,\"all.books\":false,"
                    + "\"all.bookOrders\":\"noattacks\"");
            h.assertTrue(GiantsAdmin.all("all.health") == 2.0f, "the multipliers still taken beside them");
            h.assertFalse(GiantsPlayers.sit() || GiantsPlayers.ride() || GiantsPlayers.stand()
                    || GiantsPlayers.armourPowers() || GiantsPlayers.chatMeetings() || GiantsPlayers.chatMoves()
                    || GiantsPlayers.chatBackDown() || GiantsPlayers.books() || GiantsPlayers.attackOrders(),
                    "every switch off");
            h.assertTrue(GiantsPlayers.bossBar(false), "boss bars on: shown even with his own off");
            all(h, "\"all.bossBars\":\"off\"");
            h.assertTrue(!GiantsPlayers.bossBar(true) && GiantsPlayers.sit(), "boss bars off; left out = on again");
        } finally {
            GiantsAdmin.clear();
        }
        h.assertTrue(GiantsPlayers.sit() && GiantsPlayers.bossBars().equals("usual"), "cleared: as shipped");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "p5_music")
    public void fightMusicOnThePlayersGame(GameTestHelper h) {
        h.assertTrue(GiantsPlayers.music(true) && !GiantsPlayers.music(false), "usual: the player's own");
        GiantsBridge.giants(null, "playersclient", "{\"fightMusic\":\"off\"}");
        h.assertTrue(!GiantsPlayers.music(true), "forced off");
        GiantsBridge.giants(null, "playersclient", "{\"fightMusic\":\"on\"}");
        h.assertTrue(GiantsPlayers.music(false), "forced on");
        GiantsBridge.giants(null, "playersclient", "{}");
        h.assertTrue(GiantsPlayers.music(true) && !GiantsPlayers.music(false), "left the server: usual again");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "p5_chat")
    public void moveNoticesGoQuiet(GameTestHelper h) {
        Heard heard = new Heard();
        ServerPlayer p = listening(h, at(h), heard);
        try {
            GiantsPlayers.moveLine(p, Component.translatable("message.hollowbell.grabbed"), true);
            h.assertTrue(heard.said("grabbed"), "a move notice as shipped");
            heard.keys.clear();
            all(h, "\"all.chatMoves\":false");
            GiantsPlayers.moveLine(p, Component.translatable("message.hollowbell.grabbed"), true);
            GiantsPlayers.moveChat(p, Component.translatable("warn.hollowbell.drop"));
            h.assertTrue(heard.keys.isEmpty(), "move notices off: nothing said " + heard.keys);
        } finally {
            GiantsAdmin.clear();
            drop(p);
        }
        h.succeed();
    }

    // per-player rules

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "p5_rules")
    public void aPlayerWhoMayNotOwnGetsNoBookAndAnIgnoredOneIsSpared(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 320);
        HollowbellEntity e = giant(h, pad);
        Heard heard = new Heard();
        ServerPlayer a = listening(h, Vec3.atBottomCenterOf(pad).add(40, 1, 0), heard);
        ServerPlayer b = player(h, Vec3.atBottomCenterOf(pad).add(44, 1, 0));
        BookRule r = BookRule.get(server);
        try {
            h.assertTrue(GiantsBridge.giants(server, "players", "{\"players\":9}") == null, "newer: null");
            h.assertTrue(GiantsBridge.giants(server, "players", "nonsense") == null, "unreadable: null");
            SitTests.makeOwner(a);
            h.assertTrue(CodexItem.carriedBy(a), "a holds the book");
            h.assertFalse(e.spares(b), "b is fair game as shipped");
            rules(h, "\"" + a.getUUID() + "\":{\"own\":false},\"" + b.getUUID() + "\":{\"ignored\":true}");
            r.look(List.of(a, b));
            h.assertTrue(r.holder() == null, "the holder who may not own is let go of");
            h.assertFalse(CodexItem.carriedBy(a), "their copy is quiet");
            ItemStack fresh = new ItemStack(ModItems.CODEX);
            h.assertFalse(r.mayTake(a, fresh), "they can't take a book");
            r.refuse(a);
            h.assertTrue(heard.said("players.no_own"), "told why: " + heard.keys);
            h.assertTrue(r.holder() == null, "and nobody holds it after the try");
            // an op's /give (a copy with no id) stays quiet in their hands
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            r.look(List.of(a));
            h.assertTrue(r.holder() == null && !CodexItem.carriedBy(a), "an op's copy stays quiet");
            h.assertTrue(e.spares(b), "an ignored player is spared");
            h.assertFalse(e.fairGame(b), "and never a target");
            rules(h, "");
            h.assertFalse(e.spares(b), "the next list replaces the last");
            h.assertTrue(r.mayTake(a, fresh), "may own again");
        } finally {
            GiantsPlayers.clear();
            r.free();
        }
        done(h, e, pad, a, b);
    }

    // the book's orders

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "p5_orders")
    public void booksOffAndNoAttackOrders(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 321);
        HollowbellEntity e = giant(h, pad);
        Heard heard = new Heard();
        ServerPlayer a = listening(h, e.position().add(4, 0, 4), heard);
        SitTests.makeOwner(a);
        after(h, 5, () -> {
            try {
                all(h, "\"all.bookOrders\":\"noattacks\"");
                CodexOrders.handle(a, new CodexPayload(CodexPayload.HUNTER));
                h.assertTrue(heard.said("players.attacks_off"), "the hunting mood refused: " + heard.keys);
                h.assertTrue(e.variant() == HollowbellEntity.CALM, "still calm");
                heard.keys.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.ATTACK_MOVE, 1));
                h.assertTrue(heard.said("players.attacks_off"), "a move refused: " + heard.keys);
                heard.keys.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.STAY));
                h.assertFalse(heard.said("players."), "staying still works: " + heard.keys);
                all(h, "\"all.books\":false");
                heard.keys.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.STAY));
                h.assertTrue(heard.said("players.books_off"), "books off: " + heard.keys);
                all(h, "\"all.ride\":false");
                heard.keys.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.RIDE));
                h.assertTrue(heard.said("players.ride_off") && !e.comingForSomebody(),
                        "riding off: " + heard.keys);
            } finally {
                GiantsAdmin.clear();
                BookRule.get(server).free();
            }
            done(h, e, pad, a);
        });
    }

    // sitting, standing, riding

    /** the first tick nobody is on his crown and they are on their feet (his strand set them down) */
    private static void whenOff(GameTestHelper h, HollowbellEntity e, ServerPlayer p, int ticks, Runnable then) {
        after(h, 1, () -> {
            if (!e.carrying() && !p.isPassenger()) then.run();
            else if (ticks <= 1) h.fail("riding turned off: still on his crown");
            else whenOff(h, e, p, ticks - 1, then);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p5_sit")
    public void sittingStandingAndRidingTurnedOff(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 322);
        HollowbellEntity e = giant(h, pad);
        SitTests.ready(h, e, () -> {
            Vec3 spot = SitTests.spot(h, e);
            ServerPlayer owner = SitTests.onHim(h, spot), stander = SitTests.onHim(h, spot);
            SitTests.makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "sits as shipped");
            all(h, "\"all.sit\":false");
            SitTests.whenUp(h, owner, 30, "sitting turned off", () -> {
                h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.NOT_ALLOWED, "can't sit again");
                all(h, "\"all.stand\":false");
                SitTests.standAt(stander, spot);
                after(h, 40, () -> {
                    h.assertTrue(Sitting.standingOn(stander) == null, "set down off him with standing off: "
                            + stander.position());
                    GiantsAdmin.clear();
                    Vec3 by = e.position().add(40 * 0.3 + 5, 0, 0);
                    owner.teleportTo(h.getLevel(), by.x, by.y, by.z, 0f, 0f);
                    h.assertTrue(e.possess(owner), "on his crown");
                    h.assertTrue(e.carrying(), "riding him");
                    all(h, "\"all.ride\":false");
                    whenOff(h, e, owner, 600, () -> {
                        h.assertTrue(e.rider() != owner && Sitting.standingOn(owner) == null,
                                "set down off him: " + owner.position());
                        GiantsAdmin.clear();
                        BookRule.get(server).free();
                        done(h, e, pad, owner, stander);
                    });
                });
            });
        });
    }

    // My Giant and the kit

    private record Home(boolean claimed, int x, int z, long seed) {}

    private static Home home(MinecraftServer server) {
        WorldOne w = WorldOne.get(server);
        return new Home(w.homeClaimed(), w.homeX(), w.homeZ(), w.homeSeed());
    }

    private static void putBack(GameTestHelper h, Home was) {
        WorldOne w = WorldOne.get(h.getLevel().getServer());
        if (was.claimed()) w.claimHome(h.getLevel().getServer().overworld(), was.x(), was.z(), was.seed());
        else w.dropHome();
    }

    private static List<String> pick(MinecraftServer server, ServerPlayer p, boolean book) {
        return GiantsBridge.giants(server, "mygiant", "{\"players\":1,\"player\":\"" + p.getUUID() + "\",\"book\":"
                + book + ",\"finder\":true,\"giant\":true}");
    }

    private static boolean has(ServerPlayer p, net.minecraft.world.item.Item item) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(item)) return true;
        return false;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p5_mygiant")
    public void pickingHimTakesYouHomeWithHisBookAndOneOfHim(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 323);
        Home was = home(server);
        int capWas = BellConfig.V.maxInWorld;
        float scaleWas = BellConfig.V.worldScale;
        BookRule.get(server).free();
        WorldOne.get(server).claimHome(h.getLevel(), pad.getX(), pad.getZ());
        ServerPlayer a = player(h, at(h)), b = player(h, at(h)), c = player(h, at(h)), d = player(h, at(h));
        try {
            BellConfig.V.maxInWorld = 0;
            // (a small one, so where he comes down is in the land held round the pad)
            BellConfig.V.worldScale = 0.3f;
            int before = WorldOne.standing(server);
            List<String> r = pick(server, a, true);
            h.assertTrue(r != null && r.get(0).equals("ok") && r.get(1).contains("is yours"), "a's pick: " + r);
            h.assertTrue(r.get(1).equals("The Hollowbell is yours: his book is in your pack, and he's coming to you."),
                    "the line: " + r);
            h.assertTrue(a.position().distanceTo(Vec3.atBottomCenterOf(pad)) < 60, "taken to his ground: "
                    + a.position());
            h.assertTrue(CodexItem.carriedBy(a) && has(a, ModItems.FINDER), "a has his book and finder");
            h.assertTrue(MyGiant.lastDone.startsWith("own true"), "told the Guide: " + MyGiant.lastDone);
            h.assertTrue(WorldOne.standing(server) == before + 1, "one of him came");
            // (where he comes down can be past the pad, on the land far under it: by the map, not straight-line)
            List<? extends HollowbellEntity> came = h.getLevel().getEntities(ModEntities.HOLLOWBELL,
                    x -> Math.hypot(x.getX() - a.getX(), x.getZ() - a.getZ()) < 400 && !x.isRemoved());
            h.assertTrue(!came.isEmpty() && came.get(0).spares(a), "he spares his owner: " + came.size());
            // someone else picks him: they share a's
            r = pick(server, b, true);
            h.assertTrue(r.get(1).contains("share theirs") && MyGiant.lastDone.startsWith("shared"), "b: " + r);
            h.assertTrue(BellWorld.get(server).onList(a.getUUID(), b.getUUID()), "b on a's safe list");
            h.assertFalse(has(b, ModItems.CODEX), "b has no book");
            h.assertTrue(WorldOne.standing(server) == before + 1, "none more came");
            // at the world's limit: the next holder takes charge of the ones there are
            BookRule.get(server).free();
            BellConfig.V.maxInWorld = WorldOne.standing(server);
            r = pick(server, c, true);
            h.assertTrue(r.get(1).contains("take charge") && MyGiant.lastDone.startsWith("own_cap"), "c: " + r);
            h.assertTrue(CodexItem.carriedBy(c) && WorldOne.standing(server) == before + 1, "c holds it, none came");
            // the Guide said no book (books off, or may not own)
            r = pick(server, d, false);
            h.assertTrue(r.get(1).equals("Welcome to the Hollowbell's ground.") && MyGiant.lastDone.startsWith("placed")
                    && has(d, ModItems.FINDER) && !has(d, ModItems.CODEX), "d: " + r);
            // the kit
            BookRule.get(server).free();
            List<String> g = GiantsBridge.giants(server, "give",
                    "{\"players\":1,\"player\":\"" + d.getUUID() + "\",\"what\":\"armour\"}");
            h.assertTrue(g.equals(List.of("ok")) && d.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.BELL_CHESTPLATE),
                    "armour worn: " + g);
            g = GiantsBridge.giants(server, "give", "{\"players\":1,\"player\":\"" + d.getUUID() + "\",\"what\":\"book\"}");
            h.assertTrue(g.equals(List.of("ok")) && CodexItem.carriedBy(d), "the kit's book: " + g);
            g = GiantsBridge.giants(server, "give", "{\"players\":1,\"player\":\"" + a.getUUID() + "\",\"what\":\"book\"}");
            h.assertTrue(g.get(0).equals("no") && g.get(1).contains("has the Hollowbell book"), "never a quiet copy: "
                    + g);
            g = GiantsBridge.giants(server, "give", "{\"players\":1,\"player\":\"" + a.getUUID() + "\",\"what\":\"finder\"}");
            h.assertTrue(g.equals(List.of("ok")), "the kit's finder: " + g);
            h.assertTrue(GiantsBridge.giants(server, "mygiant", "{\"players\":9}") == null, "newer: null");
            h.assertTrue(GiantsBridge.giants(server, "give", "{\"players\":9}") == null, "newer give: null");
            WorldOne.get(server).dropHome();
            r = pick(server, a, true);
            h.assertTrue(r.get(0).equals("no") && r.get(1).equals("Hollowbell has no ground yet."), "no ground yet: "
                    + r);
        } finally {
            BellConfig.V.maxInWorld = capWas;
            BellConfig.V.worldScale = scaleWas;
            for (HollowbellEntity e : h.getLevel().getEntities(ModEntities.HOLLOWBELL,
                    x -> x.distanceToSqr(Vec3.atBottomCenterOf(pad)) < 400 * 400)) e.discard();
            BookRule.get(server).free();
            putBack(h, was);
            for (ServerPlayer p : List.of(a, b, c, d)) drop(p);
            SitTests.unpad(h, pad);
        }
        h.succeed();
    }
}
