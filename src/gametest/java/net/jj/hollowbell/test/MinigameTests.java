package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsMinigame;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.12) A minigame's giants (in a world the Giants Guide makes for one round,
 * jj_giants:mg_...; here the Nether stands in for it) don't belong to this world: one coming there never pushes out
 * one of this world at Most at once, isn't held to it, doesn't count toward it, and never steps out of the world.
 */
public class MinigameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 3000, batch = "mg_cap")
    public void aMinigamesGiantsDontCountAgainstTheWorldsLimit(GameTestHelper h) {
        MinecraftServer server = h.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        h.assertTrue(GiantsMinigame.in(ResourceLocation.parse("jj_giants:mg_abc_1"))
                && !GiantsMinigame.in(Level.OVERWORLD.location()) && !GiantsMinigame.in((String) null),
                "a minigame's world by its name");
        nether.setChunkForced(0, 0, true);
        // (the far one's land is held too: in a chunk only loaded by chance nothing counts him, and the limit took the
        // near one in his place)
        BlockPos bAt = h.absolutePos(new BlockPos(1, 2, 1)).east(40);
        ChunkPos bChunk = new ChunkPos(bAt);
        h.getLevel().setChunkForced(bChunk.x, bChunk.z, true);
        // (both held till it passes, not let go after each try: the next try then only waited for the land again and
        // hid what failed)
        h.succeedWhen(() -> {
            if (!nether.isPositionEntityTicking(new BlockPos(8, 70, 8))
                    || !h.getLevel().isPositionEntityTicking(bAt)) {
                // (land is made in real time while the test's ticks run back to back: each tick spent waiting is held
                // to about a game tick's length, or the tick limit could run out before the Nether's land is made)
                try {
                    Thread.sleep(25);
                } catch (InterruptedException x) {
                    Thread.currentThread().interrupt();
                }
                h.fail("the land there, with its creatures counted");
            }
            int was = BellConfig.V.maxInWorld;
            GiantsMinigame.testWorld = Level.NETHER.location();
            HollowbellEntity a = null, m = null, b = null;
            try {
                // (others' leftovers may stand in the test world: the limit is one more than stand now; in the tests
                // the limit isn't held on arriving, so it is asked for here as arriving does)
                int before = WorldOne.standing(server);
                BellConfig.V.maxInWorld = before + 1;
                a = ModEntities.HOLLOWBELL.spawn(h.getLevel(), new ItemStack(ModItems.SMALL_EGG), null,
                        h.absolutePos(new BlockPos(1, 2, 1)), MobSpawnType.SPAWN_EGG, true, false);
                h.assertTrue(a != null && !WorldOne.limitNow(a, h.getLevel()) && !a.isRemoved(),
                        "one of this world");
                int one = WorldOne.standing(server);
                m = ModEntities.HOLLOWBELL.spawn(nether, new ItemStack(ModItems.SMALL_EGG), null, new BlockPos(8, 70, 8),
                        MobSpawnType.SPAWN_EGG, true, false);
                h.assertTrue(m != null && !WorldOne.limitNow(m, nether) && !m.isRemoved(),
                        "the minigame's one stands (not held to the limit)");
                h.assertTrue(!a.isRemoved(), "this world's one wasn't pushed out by the minigame's");
                h.assertTrue(WorldOne.standing(server) == one && one == before + 1,
                        "the minigame's one doesn't count: " + WorldOne.standing(server) + " vs " + one);
                b = ModEntities.HOLLOWBELL.spawn(h.getLevel(), new ItemStack(ModItems.SMALL_EGG), null, bAt,
                        MobSpawnType.SPAWN_EGG, true, false);
                WorldOne.limitNow(b, h.getLevel());
                h.assertTrue(b != null && WorldOne.standing(server) == before + 1,
                        "the limit still holds in this world: " + WorldOne.standing(server));
                h.assertTrue(!m.isRemoved(), "and still leaves the minigame's one alone");
                h.assertTrue(WorldOne.anyOther(server, b) != m && WorldOne.anyOther(server, a) != m,
                        "the minigame's one is never taken for this world's own");
                h.assertTrue(!m.stepAside() && !m.isRemoved(), "the minigame's one never steps out of the world");
            } finally {
                BellConfig.V.maxInWorld = was;
                GiantsMinigame.testWorld = null;
                for (HollowbellEntity e : new HollowbellEntity[] {a, m, b}) if (e != null) e.discard();
            }
            nether.setChunkForced(0, 0, false);
            h.getLevel().setChunkForced(bChunk.x, bChunk.z, false);
        });
    }
}
