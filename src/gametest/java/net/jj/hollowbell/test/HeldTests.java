package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.run;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Seat;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What he holds: saved with the world, never fought over by two of him, and a tree left from an old save drops. */
public class HeldTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "held_saved")
    public void aCreatureHeOnlyHoldsIsSavedWithTheWorld(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 616);
        h.runAfterDelay(10, () -> {
            Seat seat = new Seat(h.getLevel(), e);
            seat.moveTo(e.getX(), e.getY() + 20, e.getZ());
            h.getLevel().addFreshEntity(seat);
            Cow cow = EntityType.COW.create(h.getLevel());
            cow.moveTo(seat.getX(), seat.getY(), seat.getZ());
            h.getLevel().addFreshEntity(cow);
            cow.startRiding(seat, true);
            ServerPlayer p = player(h, seat.position().add(0, 0, 3));
            Seat seat2 = new Seat(h.getLevel(), e);
            seat2.moveTo(p.getX(), p.getY(), p.getZ());
            h.getLevel().addFreshEntity(seat2);
            p.startRiding(seat2, true);
            h.assertTrue(
                    seat.shouldBeSaved(), "a seat with a cow on it isn't saved, so the cow is lost with the chunk");
            h.assertTrue(!seat2.shouldBeSaved(), "a seat with a player on it is saved");
            // loaded again it has no owner: the cow is let go and floats down
            h.runAfterDelay(8, () -> {
                h.assertTrue(!cow.isPassenger(), "the cow is still held by a seat with nobody holding it");
                h.assertTrue(cow.hasEffect(MobEffects.SLOW_FALLING), "the cow let go doesn't float down");
                cow.discard();
                drop(p);
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "held_tree")
    public void aTreeBlockLeftFromAnOldSaveDrops(GameTestHelper h) {
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 4, 1)));
        Display.BlockDisplay d = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, h.getLevel());
        d.setBlockState(Blocks.OAK_LOG.defaultBlockState());
        d.setPos(at.x, at.y, at.z);
        d.addTag("hollowbell_tree");
        h.getLevel().addFreshEntity(d);
        h.runAfterDelay(5, () -> {
            h.assertTrue(d.isRemoved(), "a tree block nobody holds still hangs there");
            var items = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3));
            h.assertTrue(items.stream().anyMatch(i -> i.getItem().is(Items.OAK_LOG)), "the log didn't drop");
            items.forEach(ItemEntity::discard);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "held_fair")
    public void heLeavesAloneWhatAnotherOneHoldsAndAnythingRidden(GameTestHelper h) {
        HollowbellEntity a = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 617);
        HollowbellEntity b = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 618);
        h.runAfterDelay(10, () -> {
            Seat seat = new Seat(h.getLevel(), a);
            seat.moveTo(b.getX() + 6, b.getY() + 2, b.getZ());
            h.getLevel().addFreshEntity(seat);
            Cow cow = EntityType.COW.create(h.getLevel());
            cow.moveTo(seat.getX(), seat.getY(), seat.getZ());
            h.getLevel().addFreshEntity(cow);
            cow.startRiding(seat, true);
            Horse horse = EntityType.HORSE.create(h.getLevel());
            horse.moveTo(b.getX() + 8, b.getY(), b.getZ());
            h.getLevel().addFreshEntity(horse);
            Cow rider = EntityType.COW.create(h.getLevel());
            rider.moveTo(horse.getX(), horse.getY(), horse.getZ());
            h.getLevel().addFreshEntity(rider);
            rider.startRiding(horse, true);
            boolean takes = b.fairGame(cow);
            boolean carries = HollowbellEntity.canCarry(horse);
            cow.discard();
            rider.discard();
            horse.discard();
            seat.discard();
            release(h, a);
            release(h, b);
            h.assertTrue(!takes, "he goes after a cow another Hollowbell holds");
            h.assertTrue(!carries, "he would carry off a horse with somebody on it");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "held_hurt")
    public void theHurtCommandTakesWhatItSays(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 619);
        h.runAfterDelay(10, () -> {
            e.setStay(true);
            float before = e.healthNow();
            run(h, e.position(), "hollowbell hurt 100");
            float took = before - e.healthNow();
            release(h, e);
            h.assertTrue(took >= 99f && took <= 131f, "/hollowbell hurt 100 took " + took);
            h.succeed();
        });
    }
}
