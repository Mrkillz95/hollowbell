package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.SitTests.after;
import static net.jj.hollowbell.test.SitTests.done;
import static net.jj.hollowbell.test.SitTests.giant;
import static net.jj.hollowbell.test.SitTests.pad;

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
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * 1.11.0 (ADMIN_BRIDGE.md 20.17): My Giant gives every picker their own book and their own one of him (BOOK_RULE.md
 * "Exception"), and bookOrders noattacks refuses every attack move pressed on his crown as it does the book's. Each
 * test has its own batch and puts everything back.
 */
public class OwnBookTests implements FabricGameTest {

    private static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    static final class Heard {
        final List<String> keys = new ArrayList<>();

        boolean said(String keyPart) {
            synchronized (keys) {
                for (String k : keys) if (k.contains(keyPart)) return true;
            }
            return false;
        }

        void clear() {
            synchronized (keys) {
                keys.clear();
            }
        }
    }

    private static int names;

    /** a player (with a name of their own) whose lines are kept */
    static ServerPlayer listening(GameTestHelper h, Vec3 at, Heard heard) {
        UUID id = UUID.randomUUID();
        CommonListenerCookie cookie =
                CommonListenerCookie.createInitial(new GameProfile(id, "hb-own" + (++names)), false);
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

    private static List<String> pick(MinecraftServer server, ServerPlayer p) {
        return GiantsBridge.giants(server, "mygiant", "{\"players\":1,\"player\":\"" + p.getUUID()
                + "\",\"book\":true,\"finder\":true,\"giant\":true}");
    }

    private static List<HollowbellEntity> near(GameTestHelper h, BlockPos pad) {
        return new ArrayList<>(h.getLevel().getEntities(ModEntities.HOLLOWBELL,
                x -> !x.isRemoved() && Math.hypot(x.getX() - pad.getX(), x.getZ() - pad.getZ()) < 500));
    }

    private static ItemStack ownBookOf(ServerPlayer p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++)
            if (inv.getItem(i).is(ModItems.CODEX) && BookRule.ownerOf(inv.getItem(i)) != null) return inv.getItem(i);
        return ItemStack.EMPTY;
    }

    /** the world, the cap and the book as they were */
    private record Was(boolean claimed, int x, int z, long seed, int cap, float scale) {}

    private static Was setUp(GameTestHelper h, BlockPos pad) {
        MinecraftServer server = h.getLevel().getServer();
        WorldOne w = WorldOne.get(server);
        Was was = new Was(w.homeClaimed(), w.homeX(), w.homeZ(), w.homeSeed(), BellConfig.V.maxInWorld,
                BellConfig.V.worldScale);
        BookRule.get(server).free();
        BookRule.get(server).forgetOwn();
        w.claimHome(h.getLevel(), pad.getX(), pad.getZ());
        BellConfig.V.maxInWorld = 0;
        // (small ones, so where they come down is in the land held round the pad)
        BellConfig.V.worldScale = 0.3f;
        return was;
    }

    private static void putBack(GameTestHelper h, BlockPos pad, Was was, ServerPlayer... players) {
        MinecraftServer server = h.getLevel().getServer();
        // (the settings first: clearing the Guide's values takes whatever V holds then as the file's own)
        BellConfig.V.maxInWorld = was.cap();
        BellConfig.V.worldScale = was.scale();
        GiantsAdmin.clear();
        for (HollowbellEntity e : near(h, pad)) e.discard();
        BookRule.get(server).free();
        BookRule.get(server).forgetOwn();
        WorldOne w = WorldOne.get(server);
        if (was.claimed()) w.claimHome(server.overworld(), was.x(), was.z(), was.seed());
        else w.dropHome();
        for (ServerPlayer p : players) drop(p);
        SitTests.unpad(h, pad);
    }

    // My Giant: everyone's own book

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_own")
    public void twoPickersWithRoomEachHaveTheirOwnBookAndOneOfHim(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 330);
        Was was = setUp(h, pad);
        Vec3 mid = Vec3.atBottomCenterOf(pad).add(0, 1, 0);
        Heard ha = new Heard(), hb = new Heard();
        ServerPlayer a = listening(h, mid, ha), b = listening(h, mid, hb);
        BookRule r = BookRule.get(server);
        try {
            all(h, "\"all.myGiant\":true");
            h.assertTrue(GiantsPlayers.myGiant() && !GiantsPlayers.uses(GiantsPlayers.MY_GIANT),
                    "My Giant read, and not one of the uses");
            List<String> got = pick(server, a);
            h.assertTrue(got.get(1).contains("is yours") && r.holder().equals(a.getUUID()), "a: the main book " + got);
            List<HollowbellEntity> first = near(h, pad);
            h.assertTrue(first.size() == 1 && first.get(0).myOwner() == null, "a's one came, nobody's own");
            HollowbellEntity hisA = first.get(0);
            got = pick(server, b);
            h.assertTrue(got.get(1).equals("The Hollowbell is yours: his book is in your pack, and he's coming to you."),
                    "b: their own " + got);
            h.assertTrue(MyGiant.lastDone.equals("own true " + b.getGameProfile().getName()), "told the Guide: "
                    + MyGiant.lastDone);
            h.assertTrue(r.holder().equals(a.getUUID()), "the main holder slot is a's still");
            h.assertTrue(CodexItem.carriedBy(a) && CodexItem.carriedBy(b), "both books work");
            h.assertTrue(CodexItem.carriedMainBy(a) && !CodexItem.carriedMainBy(b), "b's is an own book");
            HollowbellEntity hisB = null;
            for (HollowbellEntity e : near(h, pad)) if (b.getUUID().equals(e.myOwner())) hisB = e;
            h.assertTrue(hisB != null && hisB != hisA, "b has a distinct one of him, bound to them");
            h.assertTrue(r.own(b.getUUID()) != null && hisB.getUUID().equals(r.own(b.getUUID()).giant()),
                    "b's entry, with his id");
            // a's orders never reach b's one, even standing nearer to it
            Vec3 at = a.position();
            hisA.moveTo(at.x + 40, hisA.getY(), at.z, 0f, 0f);
            hisB.moveTo(at.x + 12, hisB.getY(), at.z, 0f, 0f);
            CodexOrders.handle(a, new CodexPayload(CodexPayload.FOLLOW));
            h.assertTrue(a.getUUID().equals(hisA.followingWho()), "a's order reached a's one: " + ha.keys);
            h.assertFalse(a.getUUID().equals(hisB.followingWho()), "a's order reached b's one");
            h.assertTrue(hisB.bookHolder() == b && hisA.bookHolder() == a, "each answers his own: "
                    + hisB.bookHolder() + " " + hisA.bookHolder());
            h.assertTrue(hisB.spares(b) && hisA.spares(a), "each spares his own");
            // and b's reach only b's own one
            CodexOrders.handle(b, new CodexPayload(CodexPayload.FOLLOW));
            h.assertTrue(b.getUUID().equals(hisB.followingWho()), "b's order reached b's one: " + hb.keys);
            h.assertTrue(a.getUUID().equals(hisA.followingWho()), "and not a's one");
            // b's own book: nobody else may take it, and it is quiet in anyone else's hands
            ItemStack bs = ownBookOf(b);
            h.assertFalse(bs.isEmpty(), "b carries their own book");
            h.assertFalse(r.mayTake(a, bs), "a can't pick up b's own book");
            h.assertTrue(r.mayTake(b, bs), "b can take theirs back");
            r.refuse(a, bs);
            h.assertTrue(ha.said("own_book_theirs"), "told whose it is: " + ha.keys);
            h.assertFalse(r.works(a, bs.copy()), "b's own book is quiet in a's hands");
            h.assertTrue(r.ownNames().equals(List.of(b.getGameProfile().getName())), "book who lists it: "
                    + r.ownNames());
            // the live list: b's one is b's
            List<String> live = GiantsBridge.giants(server, "live", "1");
            boolean listed = false;
            for (String line : live.subList(1, live.size())) {
                JsonObject j = JsonParser.parseString(line).getAsJsonObject();
                if (j.get("id").getAsString().equals(hisB.getUUID().toString()))
                    listed = j.has("ownerId") && j.get("ownerId").getAsString().equals(b.getUUID().toString());
            }
            h.assertTrue(listed, "the live list's owner of b's one is b");
            // picking again while their own book and giant still stand keeps them
            int standing = WorldOne.standing(server);
            got = pick(server, b);
            h.assertTrue(got.get(1).contains("is yours") && WorldOne.standing(server) == standing, "kept: " + got);
        } finally {
            putBack(h, pad, was, a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_own_cap")
    public void atTheCapTheSecondSharesAndIsToldWhy(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 331);
        Was was = setUp(h, pad);
        Vec3 mid = Vec3.atBottomCenterOf(pad).add(0, 1, 0);
        ServerPlayer a = listening(h, mid, new Heard()), b = listening(h, mid, new Heard());
        BookRule r = BookRule.get(server);
        try {
            all(h, "\"all.myGiant\":true");
            pick(server, a);
            BellConfig.V.maxInWorld = WorldOne.standing(server);
            List<String> got = pick(server, b);
            String an = a.getGameProfile().getName();
            h.assertTrue(got.get(1).equals("This world already has as many of the Hollowbell as it allows, so you share "
                    + an + "'s: you're on " + an + "'s safe list and he won't hurt you."), "b: " + got);
            h.assertTrue(MyGiant.lastDone.startsWith("shared"), "told the Guide: " + MyGiant.lastDone);
            h.assertTrue(BellWorld.get(server).onList(a.getUUID(), b.getUUID()), "b on a's safe list");
            h.assertTrue(r.own(b.getUUID()) == null && !CodexItem.carriedBy(b), "no own book");
        } finally {
            putBack(h, pad, was, a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_own_off")
    public void myGiantOffQuietsOwnBooksAndTheBoundOnesAnswerTheMainBook(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 332);
        Was was = setUp(h, pad);
        Vec3 mid = Vec3.atBottomCenterOf(pad).add(0, 1, 0);
        Heard hb = new Heard();
        ServerPlayer a = listening(h, mid, new Heard()), b = listening(h, mid, hb);
        BookRule r = BookRule.get(server);
        try {
            all(h, "\"all.myGiant\":true");
            pick(server, a);
            pick(server, b);
            HollowbellEntity hisB = null;
            for (HollowbellEntity e : near(h, pad)) if (b.getUUID().equals(e.myOwner())) hisB = e;
            h.assertTrue(hisB != null && CodexItem.carriedBy(b), "b has their own");
            // (b further off than a, so the nearest main book is a's either way)
            b.teleportTo(h.getLevel(), hisB.getX() + 200, b.getY(), hisB.getZ(), 0f, 0f);
            a.teleportTo(h.getLevel(), hisB.getX() + 10, a.getY(), hisB.getZ(), 0f, 0f);
            h.assertTrue(hisB.bookHolder() == b, "on: b's one answers b alone, though a is nearer: "
                    + hisB.bookHolder());
            all(h, "");
            h.assertFalse(GiantsPlayers.myGiant(), "absent = off");
            h.assertFalse(CodexItem.carriedBy(b), "b's own book is quiet");
            r.look(List.of(a, b));
            h.assertTrue(BookRule.quiet(ownBookOf(b)), "and marked quiet on b's game");
            r.sayQuiet(b, ownBookOf(b));
            h.assertTrue(hb.said("own_book_off"), "b told why: " + hb.keys);
            h.assertTrue(hisB.bookHolder() == a, "b's one answers the main holder now: " + hisB.bookHolder());
            // (the others that answer a's main book, a's own companion among them, can land anywhere round the pad:
            // gone, so b's one is surely the nearest)
            for (HollowbellEntity e : near(h, pad)) if (e != hisB) e.discard();
            CodexOrders.handle(a, new CodexPayload(CodexPayload.FOLLOW));
            h.assertTrue(a.getUUID().equals(hisB.followingWho()), "a's order reaches him, the nearest");
            h.assertTrue(r.own(b.getUUID()) != null, "b's entry kept");
            all(h, "\"all.myGiant\":true");
            h.assertTrue(CodexItem.carriedBy(b), "on again: b's own book works again");
        } finally {
            putBack(h, pad, was, a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_own_fall")
    public void aBoundOnesFallQuietsHisOwnersBookOnly(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 333);
        Was was = setUp(h, pad);
        Vec3 mid = Vec3.atBottomCenterOf(pad).add(0, 1, 0);
        Heard hb = new Heard();
        ServerPlayer a = listening(h, mid, new Heard()), b = listening(h, mid, hb);
        BookRule r = BookRule.get(server);
        boolean burnWas = BellConfig.V.burnOwnersBook;
        try {
            BellConfig.V.burnOwnersBook = true;
            all(h, "\"all.myGiant\":true");
            pick(server, a);
            pick(server, b);
            HollowbellEntity hisB = null;
            for (HollowbellEntity e : near(h, pad)) if (b.getUUID().equals(e.myOwner())) hisB = e;
            h.assertTrue(hisB != null && CodexItem.carriedBy(b), "b has their own");
            WorldOne w = WorldOne.get(server);
            boolean fellWas = w.fellOnce();
            hisB.die(h.getLevel().damageSources().genericKill());
            h.assertFalse(CodexItem.carriedBy(b), "b's own book has gone quiet");
            h.assertTrue(r.own(b.getUUID()) == null, "b's entry dropped");
            h.assertTrue(hb.said("own_book_fell"), "b told: " + hb.keys);
            h.assertTrue(BookRule.quiet(ownBookOf(b)), "marked quiet");
            h.assertTrue(a.getUUID().equals(r.holder()) && CodexItem.carriedBy(a) && !r.burning(a.getUUID()),
                    "the main book isn't burned");
            h.assertTrue(w.fellOnce() == fellWas, "not the world's own one falling");
            // b may now get the main book by the usual rule
            r.free();
            h.assertTrue(r.mayTake(b, new ItemStack(ModItems.CODEX)), "b may get the main book");
        } finally {
            BellConfig.V.burnOwnersBook = burnWas;
            putBack(h, pad, was, a, b);
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_own_asshipped")
    public void withMyGiantOffAPickIsAsShipped(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 334);
        Was was = setUp(h, pad);
        Vec3 mid = Vec3.atBottomCenterOf(pad).add(0, 1, 0);
        ServerPlayer a = listening(h, mid, new Heard()), b = listening(h, mid, new Heard());
        try {
            pick(server, a);
            int standing = WorldOne.standing(server);
            List<String> got = pick(server, b);
            String an = a.getGameProfile().getName();
            h.assertTrue(got.get(1).equals(an + " has the Hollowbell book, so you share theirs: you're on " + an
                    + "'s safe list and he won't hurt you."), "b shares as shipped: " + got);
            h.assertTrue(WorldOne.standing(server) == standing && BookRule.get(server).own(b.getUUID()) == null,
                    "none came, no own book");
        } finally {
            putBack(h, pad, was, a, b);
        }
        h.succeed();
    }

    // bookOrders noattacks: his crown

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "p11_crown")
    public void noAttacksRefusesEveryMoveOnHisCrown(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        BlockPos pad = pad(h, 335);
        HollowbellEntity e = giant(h, pad);
        Heard heard = new Heard();
        ServerPlayer a = listening(h, e.position().add(40 * 0.3 + 5, 0, 0), heard);
        SitTests.makeOwner(a);
        SitTests.ready(h, e, () -> {
            try {
                Vec3 by = e.position().add(40 * 0.3 + 5, 0, 0);
                a.teleportTo(h.getLevel(), by.x, by.y, by.z, 0f, 0f);
                h.assertTrue(e.possess(a) && e.carrying(), "riding his crown");
                h.assertTrue(CodexOrders.riding(a) == e || e.rider() == a, "a is his rider");
                all(h, "\"all.bookOrders\":\"noattacks\"");
                float wind = e.mood().wind();
                for (int which : Moves.ORDER) {
                    heard.clear();
                    CodexOrders.moveFromCrown(a, e, which);
                    h.assertTrue(heard.said("players.attacks_off"), Moves.NAMES[which] + " refused: " + heard.keys);
                    h.assertTrue(e.moves().move() == Moves.NONE, Moves.NAMES[which] + " set off");
                }
                h.assertTrue(e.mood().wind() == wind, "no wind spent");
                // the book's moods: guardian allowed, hunting refused
                heard.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.GUARDIAN));
                h.assertTrue(!heard.said("attacks_off") && e.variant() == HollowbellEntity.GUARDIAN,
                        "guardian allowed: " + heard.keys);
                heard.clear();
                CodexOrders.handle(a, new CodexPayload(CodexPayload.HUNTER));
                h.assertTrue(heard.said("players.attacks_off") && e.variant() == HollowbellEntity.GUARDIAN,
                        "hunting refused: " + heard.keys);
                // with every order allowed the same keys work again
                all(h, "\"all.bookOrders\":\"all\"");
                CodexOrders.forgetPlayer(a.getUUID());
                heard.clear();
                CodexOrders.moveFromCrown(a, e, Moves.PULSE);
                h.assertTrue(e.moves().move() == Moves.PULSE, "the pulse goes off: " + heard.keys);
                for (int which : Moves.ORDER) {
                    e.moves().stopNow();
                    CodexOrders.forgetPlayer(a.getUUID());
                    heard.clear();
                    CodexOrders.moveFromCrown(a, e, which);
                    h.assertFalse(heard.said("attacks_off"), Moves.NAMES[which] + " still refused");
                }
                heard.clear();
                // (the moves above spent his wind: a fresh breath, so only the switch can say no)
                e.mood().setWind(1f);
                CodexOrders.handle(a, new CodexPayload(CodexPayload.HUNTER));
                h.assertTrue(!heard.said("attacks_off") && e.variant() == HollowbellEntity.HUNTER,
                        "hunting allowed again: " + heard.keys);
            } finally {
                // (put back even when a check fails: nobody left on with a book for the tests after)
                GiantsAdmin.clear();
                e.moves().stopNow();
                BookRule.get(server).free();
                drop(a);
                e.discard();
                SitTests.unpad(h, pad);
            }
            h.succeed();
        });
    }
}
