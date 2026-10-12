package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;

/** One put down with a spawn egg, the way the game really does it (the egg's tag laid over a fresh body). */
public class SpawnEggTests implements FabricGameTest {

    /** loads the land round a far spot and returns the ground there */
    static BlockPos farGround(GameTestHelper h, int slot, float scale) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 20000 + slot * 400, z = o.getZ() + 5000;
        int rc = 3 + Math.round(3 * scale);
        for (int cx = (x >> 4) - rc; cx <= (x >> 4) + rc; cx++) for (int cz = (z >> 4) - rc; cz <= (z >> 4) + rc; cz++) {
            h.getLevel().setChunkForced(cx, cz, true);
            h.getLevel().getChunk(cx, cz);
        }
        HollowbellGameTests.runsEntities(h, x, z, rc - 2);
        return new BlockPos(x, h.getLevel().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "egg_health")
    public void aSmallEggOneHasHealthForHisSizeAndLivesThroughASecondPlayer(GameTestHelper h) {
        float scale = HollowbellConfig.V.smallEggScale;
        BlockPos at = farGround(h, 601, scale);
        HollowbellEntity e = ModEntities.HOLLOWBELL.spawn(h.getLevel(), new ItemStack(ModItems.SMALL_EGG), null, at,
                MobSpawnType.SPAWN_EGG, true, false);
        h.assertTrue(e != null, "the egg made nothing");
        e.setMoveCooldown(100000);
        h.runAfterDelay(5, () -> {
            float want = Math.max(40f, HollowbellConfig.V.health * scale);
            h.assertTrue(Math.abs(e.bellScale() - scale) < 1e-4, "the small egg made size " + e.bellScale());
            h.assertTrue(Math.abs(e.healthMax() - want) < 1f, "the small egg one has " + e.healthMax() + " most health, not " + want);
            ServerPlayer a = player(h, e.position().add(60 * scale + 20, 0, 0));
            ServerPlayer b = player(h, e.position().add(-(60 * scale + 20), 0, 0));
            try {
                e.hurt(e.damageSources().playerAttack(a), 2f);
                e.hurt(e.damageSources().playerAttack(b), 2f);
                h.assertTrue(e.isAlive() && !e.isDeadOrDying(), "a second player's first hit killed him");
                h.assertTrue(e.healthMax() >= want, "a second player lowered his most health to " + e.healthMax());
            } finally {
                drop(a);
                drop(b);
                release(h, e);
            }
            h.succeed();
        });
    }
}
