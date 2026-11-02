package net.jj.hollowbell.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.world.BellGen;
import net.jj.hollowbell.world.GroundRules;
import net.jj.hollowbell.world.HomeGround;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biomes;

/**
 * Part 3's world-gen settings (ADMIN_BRIDGE.md §18): where a new world puts his ground (the band from spawn), the room
 * kept from the other giants' grounds, and extra copies of his ground. Each test chooses his ground the way a
 * brand-new world does (WorldOne.newWorldGround), with the settings put straight into the config in use and put back
 * at the end of the same tick.
 */
public class WorldGenRuleTests implements FabricGameTest {

    /** the settings in use now, to put back */
    private record Was(int near, int far, int gap, int copies, int radius) {
        static Was now() {
            var v = BellConfig.V;
            return new Was(v.groundNearest, v.groundFarthest, v.groundGap, v.groundCopies, v.homeRadius);
        }

        void back() {
            var v = BellConfig.V;
            v.groundNearest = near;
            v.groundFarthest = far;
            v.groundGap = gap;
            v.groundCopies = copies;
            v.homeRadius = radius;
            GroundRules.testOthers = null;
        }
    }

    private static double edge(ServerLevel lv, int x, int z, int r) {
        BlockPos sp = lv.getSharedSpawnPos();
        return Math.hypot(x - sp.getX(), z - sp.getZ()) - r;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "wgrules_shipped")
    public void atTheShippedNumbersHeChoosesExactlyAsBefore(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        WorldOne w = WorldOne.get(lv.getServer());
        Was was = Was.now();
        try {
            h.assertTrue(GroundRules.asShipped() && GroundRules.gap() == 0 && GroundRules.copies() == 1,
                    "the config in use isn't the shipped one");
            GroundRules.testOthers = List.of();
            for (long seed : new long[] {11L, 20261102L, -5L}) {
                // the release's own way (WorldOne.pickFirstSpot then claimFresh, as shipped in 1.12.33)
                w.clearForTests();
                lv.random.setSeed(seed);
                BlockPos spawn = lv.getSharedSpawnPos();
                double a = lv.random.nextDouble() * Math.PI * 2;
                double d = 3000 + lv.random.nextDouble() * 12000;
                w.claimFreshNear(lv, Mth.floor(spawn.getX() + Math.cos(a) * d),
                        Mth.floor(spawn.getZ() + Math.sin(a) * d));
                int ox = w.homeX(), oz = w.homeZ();
                long os = w.homeSeed();
                // now
                w.clearForTests();
                lv.random.setSeed(seed);
                BlockPos now = w.newWorldGround(lv);
                h.assertTrue(now.getX() == ox && now.getZ() == oz && w.homeSeed() == os,
                        "seed " + seed + ": the release chose " + ox + ", " + oz + " but now " + now.getX() + ", "
                                + now.getZ());
                h.assertTrue(w.extrasInUse().isEmpty() && BellGen.extras().length == 0,
                        "one ground asked for, but copies came too");
            }
        } finally {
            w.clearForTests();
            was.back();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "wgrules_band")
    public void hisGroundBeginsInsideTheBandFromSpawn(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        WorldOne w = WorldOne.get(lv.getServer());
        Was was = Was.now();
        try {
            GroundRules.testOthers = List.of();
            int[][] bands = {{500, 1500}, {5000, 20000}, {3000, 3000}};
            for (int[] b : bands) {
                BellConfig.V.groundNearest = b[0];
                BellConfig.V.groundFarthest = b[1];
                for (long seed = 1; seed <= 3; seed++) {
                    w.clearForTests();
                    lv.random.setSeed(seed * 7919);
                    w.newWorldGround(lv);
                    double e = edge(lv, w.homeX(), w.homeZ(), w.homeRadius());
                    h.assertTrue(e >= b[0] - 2 && e <= b[1] + 2,
                            "band " + b[0] + "-" + b[1] + ": his ground begins " + Math.round(e) + " from spawn");
                }
            }
            // the two the wrong way round are the same band
            BellConfig.V.groundNearest = 6000;
            BellConfig.V.groundFarthest = 4000;
            w.clearForTests();
            w.newWorldGround(lv);
            double e = edge(lv, w.homeX(), w.homeZ(), w.homeRadius());
            h.assertTrue(e >= 3998 && e <= 6002, "a band given the wrong way round: " + Math.round(e));
        } finally {
            w.clearForTests();
            was.back();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "wgrules_gap")
    public void hisGroundKeepsTheRoomFromAnotherGiants(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        WorldOne w = WorldOne.get(lv.getServer());
        Was was = Was.now();
        try {
            BellConfig.V.groundNearest = 4000;
            BellConfig.V.groundFarthest = 6000;
            BlockPos sp = lv.getSharedSpawnPos();
            // another giant's ground right in the middle of the band, east of spawn
            int ox = sp.getX() + 900 + 5000, oz = sp.getZ();
            for (int[] gaps : new int[][] {{1500, 0}, {0, 1500}, {800, 1500}}) {
                BellConfig.V.groundGap = gaps[0];
                GroundRules.testOthers = List.of(new GroundRules.Ground("pitchgut", ox, oz, 900, gaps[1]));
                for (long seed = 1; seed <= 4; seed++) {
                    w.clearForTests();
                    lv.random.setSeed(seed * 104729);
                    w.newWorldGround(lv);
                    double between = Math.hypot(w.homeX() - ox, w.homeZ() - oz) - w.homeRadius() - 900;
                    h.assertTrue(between >= 1500 - 2,
                            "gap " + gaps[0] + " (theirs " + gaps[1] + "): only " + Math.round(between)
                                    + " blocks between the grounds");
                    double e = edge(lv, w.homeX(), w.homeZ(), w.homeRadius());
                    h.assertTrue(e >= 3998 && e <= 6002, "keeping the room broke the band: " + Math.round(e));
                }
            }
            // and the bridge says the room he keeps
            BellConfig.V.groundGap = 700;
            List<String> lines = GiantsBridge.giants(lv.getServer(), "grounds", "");
            h.assertTrue(lines.size() == 1 && lines.get(0).contains("\"gap\":700")
                    && lines.get(0).contains("\"giant\":\"hollowbell\""), "grounds said " + lines);
        } finally {
            w.clearForTests();
            was.back();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "wgrules_copies")
    public void copiesAreMoreGroundsChosenWithoutLoadingAnyLand(GameTestHelper h) {
        ServerLevel lv = h.getLevel();
        WorldOne w = WorldOne.get(lv.getServer());
        Was was = Was.now();
        try {
            GroundRules.testOthers = List.of();
            BellConfig.V.groundNearest = 3000;
            BellConfig.V.groundFarthest = 9000;
            BellConfig.V.groundCopies = 3;
            w.clearForTests();
            int loaded = lv.getChunkSource().getLoadedChunksCount();
            w.newWorldGround(lv);
            h.assertTrue(lv.getChunkSource().getLoadedChunksCount() == loaded,
                    "choosing the grounds loaded land: " + loaded + " -> " + lv.getChunkSource().getLoadedChunksCount());
            var ex = w.extrasInUse();
            h.assertTrue(ex.size() == 2, "copies 3 made " + (ex.size() + 1) + " grounds");
            var notes = BellGen.extras();
            h.assertTrue(notes.length == 2, "the world's makers weren't told about the copies");
            int[][] all = {{w.homeX(), w.homeZ(), w.homeRadius()}, {ex.get(0).x(), ex.get(0).z(), ex.get(0).r()},
                {ex.get(1).x(), ex.get(1).z(), ex.get(1).r()}};
            for (int i = 0; i < 3; i++) {
                double e = edge(lv, all[i][0], all[i][1], all[i][2]);
                h.assertTrue(e >= 2998 && e <= 9002, "ground " + i + " begins " + Math.round(e) + " from spawn");
                for (int j = i + 1; j < 3; j++) {
                    double between = Math.hypot(all[i][0] - all[j][0], all[i][1] - all[j][1]) - all[i][2] - all[j][2];
                    h.assertTrue(between >= 198, "grounds " + i + " and " + j + " are " + Math.round(between)
                            + " apart");
                }
            }
            var plains = lv.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
            var src = lv.getChunkSource().getGenerator().getBiomeSource();
            for (int i = 0; i < 2; i++) {
                var c = notes[i];
                h.assertTrue(c.claimed && c.cx == ex.get(i).x() && c.cz == ex.get(i).z() && c.seed == ex.get(i).seed(),
                        "copy " + i + " told wrong");
                h.assertTrue(lv.getChunkSource().getChunkNow(c.cx >> 4, c.cz >> 4) == null,
                        "copy " + i + "'s land was made or loaded now");
                // the world's biome hook makes the copy his, as it makes the land
                h.assertTrue(
                        BellGen.biome(src, plains, QuartPos.fromBlock(c.cx), QuartPos.fromBlock(70),
                                        QuartPos.fromBlock(c.cz))
                                .is(HomeGround.BELL_HOLLOWS),
                        "the biome hook doesn't make copy " + i + " his");
                h.assertTrue(BellGen.inGround(c.cx, c.cz), "copy " + i + " isn't his ground");
            }
            h.assertTrue(GiantsBridge.giants(lv.getServer(), "grounds", "").size() == 3, "grounds doesn't list 3");
            // kept with the world
            WorldOne back = WorldOne.readForTest(w.save(new CompoundTag(), lv.registryAccess()), lv.registryAccess());
            h.assertTrue(back.extrasInUse().equals(ex), "the copies weren't saved");
            // one copy asked for: the others wait (kept, not made)
            BellConfig.V.groundCopies = 2;
            h.assertTrue(w.extrasInUse().size() == 1, "copies 2 still makes " + w.extrasInUse().size() + " copies");
        } finally {
            w.clearForTests();
            was.back();
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "wgrules_rows")
    public void theWorldGenSettingsAreAdminSettingsWithTheirMarkers(GameTestHelper h) {
        String[][] want = {{"groundNearest", "worldCreation"}, {"groundFarthest", "worldCreation"},
            {"groundGap", "newLand"}, {"groundCopies", "newLand"}, {"homeRadius", "newLand"}};
        for (String[] w : want) {
            var r = GiantsAdmin.row(w[0]);
            h.assertTrue(r != null && r.cat().equals("worldgen") && r.when().equals(w[1]),
                    w[0] + " isn't a worldgen setting marked " + w[1]);
        }
        var d = new BellConfig.Values();
        h.assertTrue(d.groundNearest == GroundRules.SHIPPED_NEAREST && d.groundFarthest == GroundRules.SHIPPED_FARTHEST
                && d.groundGap == 0 && d.groundCopies == 1, "the shipped numbers aren't today's");
        h.succeed();
    }
}
