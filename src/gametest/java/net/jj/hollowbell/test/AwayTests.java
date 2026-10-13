package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.Away;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Out of the world and back again. */
public class AwayTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "away")
    public void aloneHeStepsOutAndComesBackTheSame(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 116);
        var a = Away.get(h.getLevel().getServer());
        h.runAfterDelay(30, () -> {
            e.popPods(2);
            e.hurt(e.damageSources().generic(), 40f);
            final float hp = e.healthNow();
            final int pods = e.podsLeft();
            final UUID id = e.getUUID();
            final Vec3 was = e.position();
            e.setGoal(was.add(3000, 0, 0));
            h.assertTrue(e.stepAside(), "he should step out of the world");
            h.assertTrue(e.isRemoved(), "and be gone from it");
            var r = a.get(id);
            h.assertTrue(r != null, "and be written down instead");
            h.assertTrue(r.going && Math.abs(r.toX - (was.x + 3000)) < 2, "still going where he was going");
            h.runAfterDelay(40, () -> {
                Vec3 s = r.spot(h.getLevel().getGameTime());
                h.assertTrue(s.x > was.x + 1, "the sum should move him along: " + s.x + " from " + was.x);
                // somebody walks up to where the sum says he is: he comes back
                ServerPlayer p = player(h, new Vec3(s.x, e.groundAt(s.x, s.z) + 1, s.z));
                h.runAfterDelay(45, () -> {
                    HollowbellEntity back = null;
                    for (HollowbellEntity m : h.getLevel()
                            .getEntities(
                                    ModEntities.HOLLOWBELL,
                                    m -> !m.isRemoved() && m.getUUID().equals(id))) back = m;
                    h.assertTrue(back != null, "he should be back in the world");
                    h.assertTrue(a.get(id) == null, "and the sum finished with");
                    h.assertTrue(
                            Math.abs(back.healthNow() - hp) < 1f,
                            "with the health he left with: " + back.healthNow() + " want " + hp);
                    h.assertTrue(
                            back.podsLeft() == pods, "and the pods he left with: " + back.podsLeft() + " want " + pods);
                    h.assertTrue(back.getX() > was.x + 1, "further along than he left: " + back.getX());
                    h.assertTrue(
                            back.goal() != null && Math.abs(back.goal().x - (was.x + 3000)) < 2,
                            "and still going there");
                    h.assertTrue(back.getY() > back.groundAt(back.getX(), back.getZ()), "floating, not in the ground");
                    drop(p);
                    release(h, back);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "away_book")
    public void theBookStillReachesHimOutThere(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 118);
        var a = Away.get(h.getLevel().getServer());
        ServerLevel sl = h.getLevel();
        h.runAfterDelay(30, () -> {
            final UUID id = e.getUUID();
            Vec3 was = e.position();
            e.setGoal(was.add(4000, 0, 0));
            h.assertTrue(e.stepAside(), "out of the world he goes");
            var r = a.get(id);
            Vec3 second = was.add(-2500, 0, 0);
            h.assertTrue(a.send(sl, id, second), "the book should still reach him");
            h.assertTrue(Math.abs(r.toX - second.x) < 2, "and turn him for the new spot");
            h.assertTrue(r.minutesLeft(sl.getGameTime()) > 0, "with a way still to go");
            h.assertTrue(a.stay(sl, id, true), "he can be told to hold still out there");
            h.assertTrue(!r.going && r.stay, "and the sum stops");
            a.forget(id);
            h.assertTrue(a.get(id) == null, "and forgotten");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "bar_range")
    public void hisBarsShowFromFarOff(GameTestHelper h) {
        h.assertTrue(HollowbellEntity.barRange(1f) == 420, "full size: " + HollowbellEntity.barRange(1f));
        h.assertTrue(HollowbellEntity.barRange(0.1f) >= 200, "small: " + HollowbellEntity.barRange(0.1f));
        h.assertTrue(
                Away.awayRange(h.getLevel().getServer(), 1f) > HollowbellEntity.barRange(1f),
                "he never steps out while you can still see his bars");
        h.succeed();
    }
}
