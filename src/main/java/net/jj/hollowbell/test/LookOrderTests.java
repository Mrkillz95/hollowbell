package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.LookTrace;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

/** "Go after what I look at": the look finds the right one, and he really goes after it (or says why not). */
public class LookOrderTests implements FabricGameTest {
    private static final float S = 0.1f;

    /** a player up in the air near him, holding the book, looking at that */
    static ServerPlayer looker(GameTestHelper h, Vec3 at, Entity lookAt) {
        ServerPlayer p = player(h, at);
        p.getInventory().add(new ItemStack(ModItems.CODEX));
        p.lookAt(EntityAnchorArgument.Anchor.EYES, lookAt.getBoundingBox().getCenter());
        return p;
    }

    static Entity look(ServerPlayer p) {
        return LookTrace.trace(p.serverLevel(), p.getEyePosition(), p.getViewVector(1f), 512, p, null);
    }

    static Pig floatingPig(GameTestHelper h, Vec3 at) {
        Pig pig = pig(h, at);
        pig.setNoGravity(true);
        return pig;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "look_mob")
    public void lookAtAMob60BlocksOffAndHeGoesAfterIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 341);
        h.runAfterDelay(20, () -> {
            e.goToSleep();
            Vec3 c = under(e).add(0, 45, 0);
            Pig pig = floatingPig(h, c.add(60, 0, 0));
            ServerPlayer p = looker(h, c.add(0, 0, 3), pig);
            h.assertTrue(look(p) == pig, "the look at the pig 60 blocks off found " + look(p));
            String said = CodexOrders.attackLooked(p, new CodexPayload(CodexPayload.ATTACK_THAT, pig.getId()), false);
            h.assertTrue(said.startsWith("look_going"), "the order was refused: " + said);
            h.assertTrue(e.getTarget() == pig, "he isn't after the pig: " + e.getTarget());
            h.assertFalse(e.asleep(), "he didn't wake for it");
            double d0 = Math.hypot(e.getX() - pig.getX(), e.getZ() - pig.getZ());
            h.runAfterDelay(160, () -> {
                double d1 = Math.hypot(e.getX() - pig.getX(), e.getZ() - pig.getZ());
                h.assertTrue(e.getTarget() == pig, "he let the pig go: " + e.getTarget());
                h.assertTrue(d1 < d0 - 5, "he isn't going to it: " + d0 + " -> " + d1);
                // it dies: the order is done
                pig.kill();
                h.runAfterDelay(5, () -> {
                    h.assertTrue(e.huntedByOrder() == null, "he's still after a dead pig");
                    drop(p);
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "look_server")
    public void withNothingSentTheServerLooksItselfAndANewOrderEndsIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 342);
        h.runAfterDelay(20, () -> {
            Vec3 c = under(e).add(0, 45, 0);
            Pig pig = floatingPig(h, c.add(-45, 0, 10));
            ServerPlayer p = looker(h, c.add(0, 0, 3), pig);
            String said = CodexOrders.attackLooked(p, new CodexPayload(CodexPayload.ATTACK_THAT, 0), false);
            h.assertTrue(said.startsWith("look_going") && e.getTarget() == pig, "the server's own look missed: " + said + " " + e.getTarget());
            e.orderTo(c, null);
            h.assertTrue(e.huntedByOrder() == null, "a new order didn't end it");
            pig.discard();
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "look_giant")
    public void lookingAtAnotherGiantsPartSendsHimAfterThatGiant(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 343);
        h.runAfterDelay(20, () -> {
            Vec3 c = under(e).add(0, 45, 0);
            var giant = fakeGiant(h, c.add(40, 0, 0));
            giant.setNoGravity(true);
            HollowbellGameTests.FakePart part = new HollowbellGameTests.FakePart(h.getLevel(), giant);
            part.moveTo(c.x + 25, c.y, c.z);
            h.getLevel().addFreshEntity(part);
            ServerPlayer p = looker(h, c.add(0, 0, 0.5), part);
            Entity seen = look(p);
            h.assertTrue(seen == part, "the look didn't land on the part: " + seen);
            String said = CodexOrders.attackLooked(p, new CodexPayload(CodexPayload.ATTACK_THAT, part.getId()), false);
            h.assertTrue(said.startsWith("look_going"), "the order was refused: " + said);
            h.assertTrue(e.getTarget() == giant, "he isn't after the giant the part belongs to: " + e.getTarget());
            h.assertTrue(e.fightingGiant(giant), "his blows wouldn't land on it");
            part.discard();
            giant.discard();
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "look_safe")
    public void aPlayerOnYourSafeListIsRefused(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 344);
        h.runAfterDelay(20, () -> {
            Vec3 c = under(e).add(0, 45, 0);
            ServerPlayer friend = player(h, c.add(20, 0, 0));
            ServerPlayer p = looker(h, c.add(0, 0, 0.5), friend);
            BellWorld.get(h.getLevel().getServer()).addFriend(p.getUUID(), friend.getUUID());
            h.assertTrue(look(p) == friend, "the look missed the friend: " + look(p));
            String said = CodexOrders.attackLooked(p, new CodexPayload(CodexPayload.ATTACK_THAT, friend.getId()), false);
            h.assertTrue("look_safe".equals(said), "a friend on the safe list wasn't refused: " + said);
            h.assertTrue(e.getTarget() != friend && e.huntedByOrder() == null, "he went after the friend anyway");
            BellWorld.get(h.getLevel().getServer()).dropFriend(p.getUUID(), friend.getUUID());
            drop(friend);
            drop(p);
            release(h, e);
            h.succeed();
        });
    }
}
