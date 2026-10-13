package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.block.LootCacheBlock;
import net.jj.hollowbell.block.LootCacheBlockEntity;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.LootBeamsPayload;
import net.jj.hollowbell.world.LootBeams;
import net.jj.hollowbell.world.Shrine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** His death and his loot. */
public class LootTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 520, batch = "death")
    public void heDiesSinksAndDropsLoot(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 60);
        Vec3[] at = new Vec3[1];
        h.runAfterDelay(20, () -> {
            at[0] = e.position();
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
        });
        h.runAfterDelay(120, () -> {
            h.assertTrue(!e.isRemoved(), "he should still be folding down");
            h.assertTrue(
                    h.getLevel()
                            .getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(40))
                            .isEmpty(),
                    "loot before he has sunk");
        });
        h.runAfterDelay(20 + HollowbellEntity.DEATH_LENGTH + 20, () -> {
            h.assertTrue(e.isRemoved(), "he should be gone after sinking");
            // his loot is in a cache on a pedestal where he fell, and the beam is on
            var l = h.getLevel();
            BlockPos cache = null;
            BlockPos c = BlockPos.containing(at[0]);
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -80, -2), c.offset(2, 80, 2)))
                if (l.getBlockState(p).is(ModBlocks.LOOT_CACHE)) {
                    cache = p.immutable();
                    break;
                }
            h.assertTrue(cache != null, "no loot cache where he fell");
            h.assertTrue(l.getBlockState(cache).getValue(LootCacheBlock.LIT), "the cache's beam should be on");
            h.assertTrue(l.getBlockState(cache.offset(3, -1, 3)).is(Blocks.BONE_BLOCK), "no pedestal post");
            h.assertTrue(l.getBlockState(cache.below(2)).is(ModBlocks.BELL_CALCITE), "no pedestal floor");
            var be = (LootCacheBlockEntity) l.getBlockEntity(cache);
            boolean crown = false, stinger = false;
            for (int i = 0; i < be.getContainerSize(); i++) {
                crown |= be.getItem(i).is(ModItems.CROWN);
                stinger |= be.getItem(i).is(ModItems.STINGER);
            }
            h.assertTrue(crown, "no crown in the cache");
            h.assertTrue(stinger, "no stinger in the cache");
            // emptied, the beam goes out; the pedestal stays
            for (int i = 0; i < be.getContainerSize(); i++) be.setItem(i, ItemStack.EMPTY);
            be.setChanged();
            h.assertTrue(!l.getBlockState(cache).getValue(LootCacheBlock.LIT), "the beam should stop once it's empty");
            h.assertTrue(l.getBlockState(cache.offset(3, -1, 3)).is(Blocks.BONE_BLOCK), "the pedestal should stay");
            for (var d : l.getEntitiesOfClass(ItemEntity.class, new AABB(at[0], at[0]).inflate(40))) d.discard();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "loot_beams_far")
    public void aLootBeamIsSentFarOff(GameTestHelper h) {
        Vec3 at = onGround(h, 3, 3);
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(ModItems.POD, 3));
        BlockPos c = Shrine.build(h.getLevel(), at.x, at.y, at.z, loot, true);
        ServerPlayer p = player(h, new Vec3(c.getX() + 600.5, c.getY() + 4, c.getZ() + 0.5));
        h.runAfterDelay(3, () -> {
            var list = LootBeams.listFor(p);
            // through the wire and back, the way the player's game gets it
            var buf = new RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), h.getLevel().registryAccess());
            LootBeamsPayload.CODEC.encode(buf, new LootBeamsPayload(list));
            list = LootBeamsPayload.CODEC.decode(buf).all();
            h.assertTrue(list.contains(c), "a lit cache six hundred blocks off isn't sent: " + list);
            // past 1024 blocks it isn't
            Vec3 was = p.position();
            p.setPos(c.getX() + 1100.5, was.y, c.getZ() + 0.5);
            h.assertFalse(LootBeams.listFor(p).contains(c), "a cache 1100 blocks off is sent");
            p.setPos(was.x, was.y, was.z);
            // emptied, it's off
            var be = (LootCacheBlockEntity) h.getLevel().getBlockEntity(c);
            h.assertTrue(be != null, "no cache");
            for (int i = 0; i < be.getContainerSize(); i++) be.removeItem(i, 64);
            h.assertFalse(h.getLevel().getBlockState(c).getValue(LootCacheBlock.LIT), "the emptied cache is still lit");
            h.assertFalse(LootBeams.listFor(p).contains(c), "the emptied cache is still sent");
            // filled again it's on, and broken it's off
            be.add(new ItemStack(ModItems.POD));
            h.assertTrue(LootBeams.listFor(p).contains(c), "filled again, it isn't sent");
            h.getLevel().setBlockAndUpdate(c, Blocks.AIR.defaultBlockState());
            h.assertFalse(LootBeams.listFor(p).contains(c), "a broken cache is still sent");
            drop(p);
            for (var x : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(6))) x.discard();
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-3, -3, -3), c.offset(3, 3, 3)))
                if (b.getY() >= c.getY() - 2) h.getLevel().setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "loot_cache_open")
    public void theLootShrineKeepsWhatDoesntFit(GameTestHelper h) {
        Vec3 at = onGround(h, 3, 3);
        List<ItemStack> loot = new ArrayList<>();
        for (int i = 0; i < 30; i++) loot.add(new ItemStack(ModItems.STINGER)); // 30 won't stack into 27 slots
        BlockPos c = Shrine.build(h.getLevel(), at.x, at.y, at.z, loot, true);
        var be = (LootCacheBlockEntity) h.getLevel().getBlockEntity(c);
        h.assertTrue(be != null, "no cache");
        int in = 0;
        for (int i = 0; i < be.getContainerSize(); i++) in += be.getItem(i).getCount();
        h.assertTrue(in == 27, "the cache holds " + in);
        var spill = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(3));
        h.assertTrue(
                spill.stream().mapToInt(x -> x.getItem().getCount()).sum() == 3,
                "what didn't fit wasn't left on top: " + spill.size());
        // breaking the cache drops what's in it
        h.getLevel().destroyBlock(c, true);
        h.runAfterDelay(2, () -> {
            int all = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(4)).stream()
                    .filter(x -> x.getItem().is(ModItems.STINGER))
                    .mapToInt(x -> x.getItem().getCount())
                    .sum();
            h.assertTrue(all == 30, "breaking the cache lost things: " + all);
            for (var x : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(c).inflate(6))) x.discard();
            for (BlockPos b : BlockPos.betweenClosed(c.offset(-3, -3, -3), c.offset(3, 3, 3)))
                if (b.getY() >= c.getY() - 2) h.getLevel().setBlockAndUpdate(b, Blocks.AIR.defaultBlockState());
            h.succeed();
        });
    }
}
