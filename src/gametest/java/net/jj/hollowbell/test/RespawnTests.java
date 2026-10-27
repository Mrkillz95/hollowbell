package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.force;
import static net.jj.hollowbell.test.HollowbellGameTests.hollowsAt;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.BellGen;
import net.jj.hollowbell.world.RespawnPaint;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ChunkPos;

/**
 * The next one comes down within respawnBlocks of where the last one died: after a few days, or after longer if the
 * one killed was a player's. Then that player's book burns, and the book passes to the next player carrying a copy
 * (the next one is theirs), or nobody's.
 */
public class RespawnTests implements FabricGameTest {

    private static BookRule rule(GameTestHelper h) {
        return BookRule.get(h.getLevel().getServer());
    }

    private static int books(ServerPlayer p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++)
            if (p.getInventory().getItem(i).is(ModItems.CODEX)) n++;
        return n;
    }

    /** a copy made before this one was held (it has gone quiet) */
    private static ItemStack oldCopy() {
        ItemStack s = new ItemStack(ModItems.CODEX);
        UUID id = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, s, t -> t.putUUID("HollowbellBook", id));
        return s;
    }

    /** kills him for the world's count, and checks where and when the next one comes down */
    private static void diesAndComesBack(GameTestHelper h, HollowbellEntity e, int days) {
        var l = h.getLevel();
        WorldOne w = WorldOne.get(l.getServer());
        w.died(l, e);
        BlockPos d = w.deathSpot(), next = w.where();
        h.assertTrue(d != null && d.getX() == e.getBlockX() && d.getZ() == e.getBlockZ(), "death spot not kept");
        h.assertTrue(next != null, "no spot for the next one");
        double out = Math.hypot(next.getX() - e.getX(), next.getZ() - e.getZ());
        h.assertTrue(out <= BellConfig.V.respawnBlocks + 1, "the next one comes down " + out + " blocks off");
        long due = w.dueIn(l);
        h.assertTrue(
                Math.abs(due - days * 24000L) < 40, "the next one is due in " + due + " ticks, not " + days + " days");
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "respawn_wild")
    public void aWildOneComesBackInThreeDaysNearby(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 795);
        h.runAfterDelay(10, () -> {
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            rule(h).free();
            diesAndComesBack(h, e, BellConfig.V.worldRespawnDays);
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "respawn_owned")
    public void aPlayersOneTakesLongerHisBookBurnsAndPassesOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 796);
        h.runAfterDelay(10, () -> {
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            rule(h).free();
            ServerPlayer a = player(h, e.position().add(5, 0, 0)),
                    b = player(h, e.position().add(8, 0, 0));
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            rule(h).look(List.of(a));
            b.getInventory().add(oldCopy());
            rule(h).look(List.of(a, b));
            h.assertTrue(a.getUUID().equals(rule(h).holder()), "the first player doesn't hold the book");
            diesAndComesBack(h, e, BellConfig.V.ownedRespawnDays);
            h.assertTrue(books(a) == 0, "the owner's book didn't burn");
            h.assertTrue(books(b) == 1, "the other player's copy burned too");
            h.assertTrue(b.getUUID().equals(rule(h).holder()), "the book didn't pass to the next player with a copy");
            drop(a);
            drop(b);
            rule(h).free();
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "respawn_alone")
    public void withNobodyElseCarryingOneTheNextIsWild(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 797);
        h.runAfterDelay(10, () -> {
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            rule(h).free();
            ServerPlayer a = player(h, e.position().add(5, 0, 0));
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            rule(h).look(List.of(a));
            diesAndComesBack(h, e, BellConfig.V.ownedRespawnDays);
            h.assertTrue(books(a) == 0, "the owner's book didn't burn");
            h.assertTrue(rule(h).holder() == null, "somebody still holds the dead one's book");
            drop(a);
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "respawn_offline")
    public void anOwnerAwayHasTheirBookBurnWhenTheyComeBack(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 798);
        h.runAfterDelay(10, () -> {
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            rule(h).free();
            ServerPlayer a = player(h, e.position().add(5, 0, 0));
            a.getInventory().add(new ItemStack(ModItems.CODEX));
            rule(h).look(List.of(a));
            // (logged off with it: still the holder, and nobody can look in their pack)
            drop(a);
            diesAndComesBack(h, e, BellConfig.V.ownedRespawnDays);
            h.assertTrue(rule(h).burning(a.getUUID()), "the book isn't set to burn for when they're back");
            rule(h).look(List.of(a));
            h.assertTrue(books(a) == 0, "the book didn't burn when they came back");
            h.assertFalse(rule(h).burning(a.getUUID()), "the book is still set to burn");
            rule(h).free();
            WorldOne.get(h.getLevel().getServer()).clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "respawn_paint")
    public void madeLandInHisNewGroundIsPaintedHis(GameTestHelper h) {
        var l = h.getLevel();
        var server = l.getServer();
        WorldOne w = WorldOne.get(server);
        w.clearForTests();
        // (well off the row the other tests use, so only the land made here lies in the new ground)
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int cx = ((o.getX() + 20000) >> 4 << 4) + 8, cz = ((o.getZ() - 30000) >> 4 << 4) + 8;
        force(h, cx, cz, 1, true);
        // (his biome is laid from y 0 up; the test world's own land lies lower)
        boolean grief = BellConfig.V.griefing;
        try {
            BellConfig.V.griefing = true;
            h.assertFalse(hollowsAt(h, cx, 64, cz), "the land was his before any claim");
            w.claimHome(l, cx, cz, 0x5EED0700L);
            h.assertFalse(hollowsAt(h, cx, 64, cz), "claiming alone painted made land");
            int n = RespawnPaint.start(l);
            var r = RespawnPaint.get(server);
            h.assertTrue(n >= 9, "only " + n + " made chunks found in his new ground");
            h.assertTrue(r.waiting(new ChunkPos(cx >> 4, cz >> 4)), "the made chunk in the middle isn't waiting");
            // a little a tick, then all of it
            RespawnPaint.tick(server);
            h.assertTrue(r.done() > 0, "a tick painted nothing");
            RespawnPaint.finishAll(server);
            h.assertTrue(r.left() == 0, r.left() + " chunks still waiting");
            h.assertTrue(hollowsAt(h, cx, 64, cz), "the made land didn't become the Bell Hollows");
            h.assertTrue(r.landDone() > 0, "his ground wasn't laid on any made chunk");
            h.assertTrue(BellGen.made(l.getChunk(cx >> 4, cz >> 4)), "the middle chunk isn't marked as his");
            // the world's spawn is never painted
            h.assertTrue(
                    RespawnPaint.nearSpawn(l, new ChunkPos(l.getSharedSpawnPos())), "the spawn isn't kept clear");
            // a ground let go drops whatever is still waiting
            force(h, cx + 64, cz, 0, true);
            RespawnPaint.start(l);
            w.clearForTests();
            RespawnPaint.tick(server);
            h.assertTrue(r.left() == 0, "a let-go ground is still being painted");
        } finally {
            BellConfig.V.griefing = grief;
            w.clearForTests();
            force(h, cx, cz, 1, false);
            force(h, cx + 64, cz, 0, false);
        }
        h.succeed();
    }
}
