package net.jj.giants.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.fakemg.GiantsBridge;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminTransfer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.minigames.MgCatalog;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.MgLevels;
import net.jj.giants.minigames.MgSnap;
import net.jj.giants.minigames.Minigames;
import net.jj.giants.minigamesmixin.MgServerAccess;
import net.jj.giants.players.Boards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Part 10A, the minigames (ADMIN_BRIDGE.md §26), with a stand-in giant (net.jj.fakemg): a round's own world made
 * and thrown away (nothing saved, the folder gone), everything of a player's kept and put back exactly (on leaving, on
 * logging out, after a crash on their next login, when the server stops, when they leave the round's world another
 * way), the lobby (open and everyone), Giant Royale's winner and the results (board, challenge), the settings, the
 * book's page and its requests, moderators, and the damage rules. Each test has its own batch.
 */
public class MinigameGameTests implements FabricGameTest {
    static final Giant FAKE = new Giant("fakemg", "fakemg", "fakemg", "net.jj.fakemg.GiantsBridge", List.of(),
            Set.of());

    record Joined(ServerPlayer p, Connection c) {}

    static MinecraftServer fresh(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        AdminServer.useGiants(List.of(FAKE));
        FunGiants.TEST_KINDS.put(GiantsBridge.TAG, "fakemg");
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "fakemg")) w.clear(g);
        GiantsBridge.reset();
        MgData.get(s).s = new MgData.State();
        AdminServer.push(s, w);
        AdminTransfer.forget();
        AdminBook.forget();
        set(h, s, "mgSize", "small");
        set(h, s, "mgLobby", "10");
        return s;
    }

    static void done(GameTestHelper h, ServerPlayer... players) {
        MinecraftServer s = h.getLevel().getServer();
        if (Minigames.current() != null) Minigames.stopForTests(s);
        for (ServerPlayer p : players) {
            s.getPlayerList().getOps().remove(p.getGameProfile());
            if (s.getPlayerList().getPlayer(p.getUUID()) != null) s.getPlayerList().remove(p);
            MgSnap.delete(s, p.getUUID());
        }
        AdminWorld w = AdminWorld.get(s);
        for (String g : List.of("all", "fakemg")) w.clear(g);
        MgData.get(s).s = new MgData.State();
        for (var gm : GiantsBridge.golems(s)) gm.discard();
        AdminServer.push(s, w);
        AdminServer.useGiants(null);
        FunGiants.TEST_KINDS.remove(GiantsBridge.TAG);
        h.succeed();
    }

    static void set(GameTestHelper h, MinecraftServer s, String id, String v) {
        AdminServer.Result r = AdminServer.set(s, "Tester", "all", id, v);
        h.assertTrue(r.ok(), "set " + id + ": " + r.lines());
    }

    /** a player who joins for real (the join events run), whose connection we keep (to leave the game for real) */
    static Joined join(GameTestHelper h, String name, boolean op) {
        return join(h, UUID.randomUUID(), name, op);
    }

    static Joined join(GameTestHelper h, UUID id, String name, boolean op) {
        MinecraftServer s = h.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, name), false);
        ServerPlayer p = new ServerPlayer(s, s.overworld(), cookie.gameProfile(), cookie.clientInformation());
        if (op) s.getPlayerList().getOps().add(new ServerOpListEntry(p.getGameProfile(), 4, false));
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        s.getPlayerList().placeNewPlayer(c, p, cookie);
        return new Joined(p, c);
    }

    /** a player's things: a mix that must come back exactly */
    static void kit(ServerPlayer p, BlockPos at) {
        p.getInventory().clearContent();
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        sword.setDamageValue(17);
        p.getInventory().setItem(0, sword);
        p.getInventory().setItem(5, new ItemStack(Items.TORCH, 37));
        p.getInventory().setItem(20, new ItemStack(Items.ENDER_PEARL, 3));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        p.getInventory().selected = 5;
        p.setExperienceLevels(23);
        p.experienceProgress = 0.4f;
        p.setHealth(13f);
        p.getFoodData().setFoodLevel(11);
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 6000, 0));
        p.setGameMode(GameType.SURVIVAL);
        p.teleportTo(p.serverLevel(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 33f, 5f);
    }

    static String items(ServerPlayer p) {
        return p.getInventory().save(new ListTag()).toString();
    }

    /** what was kept equals what they have now: every slot, the hand, levels, health, food, effects, place */
    static void same(GameTestHelper h, ServerPlayer p, String inv, Vec3 at, String what) {
        h.assertTrue(items(p).equals(inv), what + ": things back exactly: " + items(p) + " vs " + inv);
        h.assertTrue(p.getInventory().selected == 5, what + ": the same slot in hand");
        h.assertTrue(p.experienceLevel == 23, what + ": levels back (" + p.experienceLevel + ")");
        h.assertTrue(Math.abs(p.getHealth() - 13f) < 0.01, what + ": health back (" + p.getHealth() + ")");
        h.assertTrue(p.getFoodData().getFoodLevel() == 11, what + ": food back");
        h.assertTrue(p.hasEffect(MobEffects.NIGHT_VISION), what + ": effects back");
        h.assertTrue(p.level().dimension() == Level.OVERWORLD, what + ": in the overworld");
        h.assertTrue(p.position().distanceTo(at) < 1.5, what + ": where they were (" + p.position() + " vs " + at
                + ")");
        h.assertTrue(p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, what + ": game mode back");
    }

    /** their game has arrived in the world they were sent to (a real one says so; the test players can't) */
    static void arrived(ServerPlayer... ps) {
        for (ServerPlayer p : ps) p.hasChangedDimension();
    }

    static MgGame game() {
        MgGame g = Minigames.current();
        if (g == null) throw new net.minecraft.gametest.framework.GameTestAssertException("no minigame on");
        return g;
    }

    // the settings

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "mg_settings")
    public void theMinigameSettingsAreOrdinarySettings(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        for (var x : MgCatalog.SETTINGS) {
            h.assertTrue(x.cat().equals(AdminCatalog.SERVER) && x.id().startsWith("all.mg"), x.id());
            h.assertTrue(AdminCatalog.shared(x.id()) != null, "listed: " + x.id());
            h.assertTrue(AdminCatalog.player(x.id()), "kept by presets: " + x.id());
        }
        set(h, s, "mgRounds", "3");
        h.assertTrue(MgCatalog.num(s, "mgRounds") == 3, "set");
        AdminServer.Result all = AdminServer.setDefault(s, "Tester", "all", "mgRounds", "2");
        h.assertTrue(!all.ok() && all.lines().get(0).contains("minigame settings"), "all worlds: " + all.lines());
        AdminServer.Result bad = AdminServer.set(s, "Tester", "all", "mgGiantSize", "9");
        h.assertTrue(!bad.ok(), "out of range refused");
        // a preset leaves them as they are
        AdminTransfer.Bundle b = AdminTransfer.preset("classic");
        AdminTransfer.apply(s, "Tester", AdminTransfer.plan(s, b));
        h.assertTrue(MgCatalog.num(s, "mgRounds") == 3, "Classic keeps the minigame settings");
        done(h);
    }

    // the round's own world

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 6000, batch = "mg_world")
    public void aRoundsWorldIsMadeAndThrownAwayLeavingNothing(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerLevel[] l = {MgLevels.create(s, "t" + System.nanoTime(), 6000)};
        var key = l[0].dimension();
        java.nio.file.Path dir = ((MgServerAccess) s).jjGiants$storage().getDimensionPath(key);
        h.assertTrue(MgLevels.temporary(l[0]) && s.getLevel(key) == l[0], "made and in the server's worlds");
        h.assertTrue(l[0].getDayTime() == 6000 && !l[0].isRaining(), "its own time of day, no rain");
        l[0].getChunkSource().addRegionTicket(MgLevels.TICKET, new net.minecraft.world.level.ChunkPos(0, 0), 3,
                new net.minecraft.world.level.ChunkPos(0, 0));
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(l[0].getChunkSource().getChunkNow(0, 0) != null, "land made"))
                .thenExecute(() -> {
                    h.assertTrue(l[0].getBlockState(new BlockPos(3, MgLevels.GROUND, 3)).is(Blocks.GRASS_BLOCK),
                            "flat grass at the ground's height");
                    h.assertTrue(l[0].getBlockState(new BlockPos(3, MgLevels.GROUND + 1, 3)).isAir(), "air over it");
                    // things in it: a block, a creature
                    l[0].setBlock(new BlockPos(1, MgLevels.GROUND + 1, 1), Blocks.GOLD_BLOCK.defaultBlockState(), 3);
                    GiantsBridge.spawn(l[0], 2, MgLevels.GROUND + 1, 2);
                    // a save (as the autosave does): nothing of it is written
                    s.saveAllChunks(true, true, true);
                    MgLevels.retire(l[0]);
                })
                .thenWaitUntil(() -> h.assertTrue(s.getLevel(key) == null, "thrown away"))
                .thenExecute(() -> {
                    h.assertTrue(!Files.exists(dir), "its folder is gone: " + dir);
                    h.assertTrue(GiantsBridge.golems(s).isEmpty(), "nothing of it left");
                    // a folder a crash left behind goes at the next start
                    try {
                        Files.createDirectories(dir.resolveSibling(MgLevels.PREFIX + "old").resolve("region"));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                    h.assertTrue(MgLevels.cleanOnStart(s) >= 1, "left-behind world deleted");
                    h.assertTrue(!Files.exists(dir.resolveSibling(MgLevels.PREFIX + "old")), "and gone");
                    done(h);
                });
    }

    // keeping a player's things

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "mg_snap")
    public void everythingKeptComesBackExactly(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgsnap", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        String inv = items(a.p());
        Vec3 pos = a.p().position();
        CompoundTag t = MgSnap.take(a.p());
        h.assertTrue(MgSnap.write(s, a.p().getUUID(), t), "written");
        MgSnap.strip(a.p());
        h.assertTrue(a.p().getInventory().isEmpty() && a.p().experienceLevel == 0
                && a.p().getActiveEffects().isEmpty(), "stripped");
        a.p().setGameMode(GameType.ADVENTURE);
        a.p().teleportTo(a.p().serverLevel(), pos.x + 40, pos.y + 5, pos.z, 0, 0);
        CompoundTag back = MgSnap.read(s, a.p().getUUID());
        h.assertTrue(back != null && back.equals(t), "read back the same");
        MgSnap.apply(a.p(), back);
        same(h, a.p(), inv, pos, "applied");
        MgSnap.delete(s, a.p().getUUID());
        h.assertTrue(!MgSnap.waiting(s, a.p().getUUID()), "file gone");
        done(h, a.p());
    }

    /** open a Royale with these players joined, skip the lobby, and wait until the round is being played */
    static net.minecraft.gametest.framework.GameTestSequence royaleWith(GameTestHelper h, MinecraftServer s,
            ServerPlayer... ps) {
        AdminServer.Result r = Minigames.start(s, "Tester", "royale", false, 1);
        h.assertTrue(r.ok(), "opened: " + r.lines());
        for (ServerPlayer p : ps) h.assertTrue(Minigames.join(p, false).ok(), "joined " + p.getGameProfile().getName());
        Minigames.now(s, "Tester");
        return h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing: " + game().phase()))
                .thenExecute(() -> arrived(ps));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_flow")
    public void aRoyaleRoundTakesPlayersInAndSendsThemHomeAsTheyWere(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgflowa", false), b = join(h, "mgflowb", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        kit(b.p(), at.east(3));
        String invA = items(a.p()), invB = items(b.p());
        Vec3 posA = a.p().position(), posB = b.p().position();
        ServerLevel[] world = {null};
        java.nio.file.Path[] dir = {null};
        AdminServer.Result r = Minigames.start(s, "Tester", "royale", false, 1);
        h.assertTrue(r.ok(), "opened: " + r.lines());
        h.assertTrue(Minigames.join(a.p(), false).ok() && Minigames.join(b.p(), false).ok(), "joined");
        h.assertTrue(!Minigames.join(a.p(), false).ok(), "joining twice says so");
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.READY
                        || game().phase() == MgGame.Phase.PLAY, "in: " + game().phase()))
                .thenExecute(() -> {
                    world[0] = game().level;
                    dir[0] = ((MgServerAccess) s).jjGiants$storage().getDimensionPath(world[0].dimension());
                    for (ServerPlayer p : List.of(a.p(), b.p())) {
                        h.assertTrue(p.level() == world[0], "in the round's world");
                        h.assertTrue(p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE, "adventure");
                        h.assertTrue(p.getInventory().contains(new ItemStack(Items.STONE_SWORD)),
                                "the Royale kit");
                        h.assertTrue(!p.getInventory().contains(new ItemStack(Items.TORCH)), "none of their own");
                        h.assertTrue(MgSnap.waiting(s, p.getUUID()), "their things kept on disk");
                    }
                    h.assertTrue(world[0].getWorldBorder().getSize() < 1000, "the round's border");
                })
                .thenWaitUntil(() -> h.assertTrue(!game().giants.all.isEmpty(), "his summon brought him"))
                .thenExecute(() -> {
                    h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.startsWith("summon "
                            + world[0].dimension().location())), "summoned into the round's world: "
                            + GiantsBridge.calls);
                    h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.contains(" calm ")),
                            "calm in his den at first: " + GiantsBridge.calls);
                    // the main world isn't touched: no golem there
                    for (var gm : GiantsBridge.golems(s)) h.assertTrue(gm.level() == world[0], "only in the round");
                    h.assertTrue(Minigames.stop(s, "Tester").ok(), "stopped");
                    same(h, a.p(), invA, posA, "A home");
                    same(h, b.p(), invB, posB, "B home");
                    h.assertTrue(!MgSnap.waiting(s, a.p().getUUID()) && !MgSnap.waiting(s, b.p().getUUID()),
                            "kept files gone");
                    h.assertTrue(Minigames.current() == null, "nothing on");
                })
                .thenWaitUntil(() -> h.assertTrue(s.getLevel(world[0].dimension()) == null, "its world thrown away"))
                .thenExecute(() -> {
                    h.assertTrue(!Files.exists(dir[0]), "its folder gone");
                    h.assertTrue(GiantsBridge.golems(s).isEmpty(), "his giant gone with it");
                    h.assertTrue(GiantsBridge.calls.stream().anyMatch(c -> c.startsWith("liveact")
                            && c.contains("remove")), "taken away by his own remove");
                    done(h, a.p(), b.p());
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_royale_win")
    public void theLastOneStandingWinsRoyale(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgwina", false), b = join(h, "mgwinb", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        kit(b.p(), at.east(2));
        String invB = items(b.p());
        Vec3 posB = b.p().position();
        AdminServer.Result r = Minigames.start(s, "Tester", "royale", false, 1);
        h.assertTrue(r.ok(), "opened");
        Minigames.join(a.p(), false);
        Minigames.join(b.p(), false);
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.READY, "ready"))
                .thenExecute(() -> {
                    arrived(a.p(), b.p());
                    // nobody is hurt before Go
                    float hp = b.p().getHealth();
                    b.p().hurt(b.p().damageSources().generic(), 5);
                    h.assertTrue(b.p().getHealth() == hp, "no harm in the countdown");
                })
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.PLAY, "playing"))
                .thenExecute(() -> {
                    // players may hit each other in Royale (where the server allows it)
                    boolean pvpWas = s.isPvpAllowed();
                    s.setPvpAllowed(true);
                    // (past the few seconds' grace at Go)
                    b.p().removeAllEffects();
                    float hp = b.p().getHealth();
                    b.p().hurt(b.p().damageSources().playerAttack(a.p()), 2);
                    h.assertTrue(b.p().getHealth() < hp, "PvP in Royale");
                    s.setPvpAllowed(pvpWas);
                    // a blow that would kill B: B lives, out of the round, watching
                    b.p().hurt(b.p().damageSources().genericKill(), Float.MAX_VALUE);
                    h.assertTrue(b.p().isAlive() && !b.p().isDeadOrDying(), "B doesn't die");
                    h.assertTrue(b.p().isSpectator(), "B watches");
                    h.assertTrue(!game().of(b.p().getUUID()).alive, "B is out");
                })
                .thenWaitUntil(() -> h.assertTrue(game().phase() == MgGame.Phase.ROUND_END
                        || game().phase() == MgGame.Phase.RESULTS, "round over: " + game().phase()))
                .thenExecute(() -> {
                    MgData.Player rec = MgData.get(s).s.players.get(a.p().getStringUUID());
                    h.assertTrue(game().of(a.p().getUUID()).roundWins == 1, "A won the round");
                    h.assertTrue(rec == null || rec.wins <= 1, "not counted twice");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "over"))
                .thenExecute(() -> {
                    MgData.Player rec = MgData.get(s).s.players.get(a.p().getStringUUID());
                    h.assertTrue(rec != null && rec.wins == 1 && rec.modeWins.getOrDefault("royale", 0) == 1,
                            "A's win kept");
                    MgData.Player lost = MgData.get(s).s.players.get(b.p().getStringUUID());
                    h.assertTrue(lost != null && lost.wins == 0 && lost.games == 1, "B played one");
                    h.assertTrue(MgData.get(s).s.results.size() == 1
                            && MgData.get(s).s.results.get(0).winners.contains("mgwina"), "the result kept");
                    var rows = Boards.rows(s, "minigames");
                    h.assertTrue(!rows.isEmpty() && rows.get(0).name().equals("mgwina"), "on the board: " + rows);
                    h.assertTrue(net.jj.giants.players.Challenges.done(a.p(), "mg_royale"), "A's challenge");
                    h.assertTrue(!net.jj.giants.players.Challenges.done(b.p(), "mg_royale"), "not B's");
                    same(h, b.p(), invB, posB, "B home after being out");
                    done(h, a.p(), b.p());
                });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_disconnect")
    public void leavingTheGameMidRoundPutsEverythingBackBeforeTheSave(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        UUID id = UUID.randomUUID();
        Joined a = join(h, id, "mgquit", false);
        Joined b = join(h, "mgstay", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        String inv = items(a.p());
        Vec3 pos = a.p().position();
        ServerPlayer[] again = {null};
        royaleWith(h, s, a.p(), b.p()).thenExecute(() -> {
            h.assertTrue(a.p().level() == game().level, "A in the round");
            // A leaves the game for real (the connection closes: the game's own leaving, its save)
            a.c().disconnect(Component.literal("bye"));
            a.c().handleDisconnection();
            h.assertTrue(s.getPlayerList().getPlayer(id) == null, "A has left");
            Optional<CompoundTag> saved = ((MgServerAccess) s).jjGiants$playerData().load(
                    new ServerPlayer(s, s.overworld(), new GameProfile(id, "mgquit"),
                            net.minecraft.server.level.ClientInformation.createDefault()));
            h.assertTrue(saved.isPresent(), "A's file saved");
            CompoundTag f = saved.get();
            h.assertTrue(f.getString("Dimension").equals("minecraft:overworld"), "saved in the overworld: "
                    + f.getString("Dimension"));
            h.assertTrue(f.getList("Inventory", 10).toString().equals(inv), "saved with their own things");
            h.assertTrue(!MgSnap.waiting(s, id), "nothing waiting");
            h.assertTrue(game().of(id).left, "A is out of the round");
            // back in: everything as it was
            again[0] = join(h, id, "mgquit", false).p();
            same(h, again[0], inv, pos, "A again");
            h.assertTrue(game().of(id).left && !game().of(id).in, "still out of the round");
            done(h, again[0], b.p());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_crash")
    public void afterACrashTheNextLoginPutsEverythingBack(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        UUID id = UUID.randomUUID();
        Joined a = join(h, id, "mgcrash", false);
        Joined b = join(h, "mgcrashb", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        String inv = items(a.p());
        Vec3 pos = a.p().position();
        royaleWith(h, s, a.p(), b.p()).thenExecute(() -> {
            // an autosave in the round: A's file holds the round's kit and world
            MgSnap.savePlayer(a.p());
            // the crash: A gone without the game's leaving (no disconnect), the round's world gone with nothing
            // put back; only the kept file is left
            s.getPlayerList().remove(a.p());
            Minigames.crashForTests(s);
            h.assertTrue(MgSnap.waiting(s, id), "A's things wait on disk");
            ServerPlayer back = join(h, id, "mgcrash", false).p();
            same(h, back, inv, pos, "after the crash");
            h.assertTrue(!MgSnap.waiting(s, id), "and the file is gone");
            done(h, back, b.p());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_stop")
    public void theServerStoppingPutsEveryoneBackFirst(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgstopa", false), w = join(h, "mgstopw", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        kit(w.p(), at.south(2));
        String invA = items(a.p()), invW = items(w.p());
        Vec3 posA = a.p().position(), posW = w.p().position();
        royaleWith(h, s, a.p()).thenExecute(() -> {
            // a watcher comes in mid-round
            h.assertTrue(Minigames.join(w.p(), true).ok(), "watching");
            h.assertTrue(w.p().isSpectator() && w.p().level() == game().level, "a spectator in the round");
            var key = game().level.dimension();
            Minigames.stoppingForTests(s);
            same(h, a.p(), invA, posA, "player at the stop");
            same(h, w.p(), invW, posW, "watcher at the stop");
            h.assertTrue(s.getLevel(key) == null, "the round's world closed at once");
            h.assertTrue(Minigames.current() == null, "nothing on");
            done(h, a.p(), w.p());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_otherway")
    public void leavingTheRoundsWorldAnotherWayPutsThemBack(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgaway", false), b = join(h, "mgawayb", false);
        BlockPos at = h.absolutePos(new BlockPos(1, 2, 1));
        kit(a.p(), at);
        String inv = items(a.p());
        Vec3 pos = a.p().position();
        royaleWith(h, s, a.p(), b.p()).thenExecute(() -> {
            // a command takes A to the overworld (not the game's own way)
            a.p().teleportTo(s.overworld(), pos.x + 100, pos.y + 10, pos.z, 0, 0);
            same(h, a.p(), inv, pos, "put back");
            h.assertTrue(game().of(a.p().getUUID()).left, "out of the round");
            done(h, a.p(), b.p());
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 8000, batch = "mg_coop")
    public void soloRoyaleTheGiantsWinWhenYouFall(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        Joined a = join(h, "mgsolo", false);
        royaleWith(h, s, a.p()).thenExecute(() -> {
            a.p().hurt(a.p().damageSources().genericKill(), Float.MAX_VALUE);
            h.assertTrue(!game().of(a.p().getUUID()).alive, "out");
        }).thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "over")).thenExecute(() -> {
            MgData.Player rec = MgData.get(s).s.players.get(a.p().getStringUUID());
            h.assertTrue(rec != null && rec.wins == 0 && rec.games == 1, "played, didn't win");
            h.assertTrue(MgData.get(s).s.results.get(0).line.contains("giants"), "the giants won: "
                    + MgData.get(s).s.results.get(0).line);
            done(h, a.p());
        });
    }

    // who may start them, the book, the lobby

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "mg_roles")
    public void adminsAndAllowedModeratorsRunMinigames(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer op = join(h, "mgop", true).p(), mod = join(h, "mgmod", false).p(),
                nobody = join(h, "mgnobody", false).p();
        AdminWorld.get(s).addMod(mod.getUUID(), mod.getGameProfile().getName());
        h.assertTrue(Minigames.staff(op) && Minigames.staff(mod) && !Minigames.staff(nobody), "who may");
        set(h, s, "mgMods", "off");
        h.assertTrue(!Minigames.staff(mod), "not moderators when it's off");
        AdminServer.Result r = AdminBook.request(s, mod, AdminBook.Role.MODERATOR,
                "{\"do\":\"mgstart\",\"mode\":\"royale\",\"kind\":\"open\"}");
        h.assertTrue(!r.ok() && Minigames.current() == null, "refused: " + r.lines());
        set(h, s, "mgMods", "on");
        r = AdminBook.request(s, mod, AdminBook.Role.MODERATOR, "{\"do\":\"mgstart\",\"mode\":\"royale\","
                + "\"kind\":\"open\"}");
        h.assertTrue(r.ok() && Minigames.current() != null, "a moderator opened it: " + r.lines());
        r = AdminBook.request(s, op, AdminBook.Role.ADMIN, "{\"do\":\"mgstart\",\"mode\":\"royale\"}");
        h.assertTrue(!r.ok(), "one at a time");
        // the page
        var page = com.google.gson.JsonParser.parseString(net.jj.giants.minigames.MgAdmin.page(s).json())
                .getAsJsonObject();
        h.assertTrue(page.getAsJsonArray("modes").size() == Minigames.MODES.size(), "every mode listed");
        h.assertTrue(page.getAsJsonObject("game").get("mode").getAsString().equals("royale"), "the one on");
        // the players' This world page
        var guide = net.jj.giants.minigames.MgAdmin.guideJson(s, nobody);
        h.assertTrue(guide.getAsJsonObject("game").get("phase").getAsString().equals("lobby")
                && !guide.getAsJsonObject("game").get("in").getAsBoolean(), "open to join");
        h.assertTrue(net.jj.giants.minigames.MgAdmin.ask(nobody, "mgjoin").ok(), "joined from the Guide");
        h.assertTrue(net.jj.giants.minigames.MgAdmin.guideJson(s, nobody).getAsJsonObject("game").get("in")
                .getAsBoolean(), "in now");
        h.assertTrue(net.jj.giants.minigames.MgAdmin.ask(nobody, "mgleave").ok(), "dropped out");
        r = AdminBook.request(s, mod, AdminBook.Role.MODERATOR, "{\"do\":\"mgstop\"}");
        h.assertTrue(r.ok() && Minigames.current() == null, "a moderator stopped it");
        done(h, op, mod, nobody);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "mg_force")
    public void everyoneOnlineIsTakenAndTooFewCancelsIt(GameTestHelper h) {
        if (PlayersGameTests.realGiants(h)) return;
        MinecraftServer s = fresh(h);
        ServerPlayer a = join(h, "mgforcea", false).p();
        AdminServer.Result r = Minigames.start(s, "Tester", "royale", true, 1);
        h.assertTrue(r.ok(), "opened for everyone");
        for (ServerPlayer p : s.getPlayerList().getPlayers())
            h.assertTrue(game().of(p.getUUID()) != null, "everyone online is in: " + p.getGameProfile().getName());
        h.assertTrue(!Minigames.leave(a).ok(), "can't drop out of an everyone one in the lobby");
        ServerPlayer late = join(h, "mgforcelate", false).p();
        h.assertTrue(game().of(late.getUUID()) != null, "one who comes online in the lobby is taken too");
        Minigames.stop(s, "Tester");
        // too few players: Fewest players 16, nobody can make it
        set(h, s, "mgMin", "16");
        set(h, s, "mgLobby", "10");
        Minigames.start(s, "Tester", "royale", false, 1);
        Minigames.join(a, false);
        Minigames.now(s, "Tester");
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "cancelled for too few"))
                .thenExecute(() -> {
                    h.assertTrue(a.level().dimension() == Level.OVERWORLD && !MgSnap.waiting(s, a.getUUID()),
                            "nobody was taken in");
                    done(h, a, late);
                });
    }
}
