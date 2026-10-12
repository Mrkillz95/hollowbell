package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Orders from a player's game are checked: a spot that isn't a real place never reaches him. */
public class OrderTests implements FabricGameTest {

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "order_spot")
    public void aGoToOrderToNowhereIsRefused(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 620);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(10, () -> {
            p[0] = player(h, e.position().add(60, 0, 0));
            p[0].getInventory().add(new ItemStack(ModItems.CODEX));
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.GO_TO_XZ, 0, Double.POSITIVE_INFINITY, Double.NaN));
            e.setGoal(new Vec3(Double.NaN, 0, Double.POSITIVE_INFINITY));
        });
        h.runAfterDelay(50, () -> {
            boolean ok = Double.isFinite(e.getX() + e.getY() + e.getZ()) && !e.isRemoved();
            boolean noGoal = e.goal() == null || Double.isFinite(e.goal().x + e.goal().z);
            drop(p[0]);
            release(h, e);
            h.assertTrue(ok, "an order to nowhere put him at " + e.position());
            h.assertTrue(noGoal, "an order to nowhere was taken: " + e.goal());
            h.succeed();
        });
    }
}
