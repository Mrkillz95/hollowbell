package net.jj.hollowbell.test;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
public class TmpSubTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
    private final HollowbellGameTests t = new HollowbellGameTests();
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_trees")
    public void hisGroundLeavesTreesAndCabinsWhole(GameTestHelper h) { t.hisGroundLeavesTreesAndCabinsWhole(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_structure")
    public void aStructureReachingInIsLeftAlone(GameTestHelper h) { t.aStructureReachingInIsLeftAlone(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_paint")
    public void hisGroundTurnsAChunk(GameTestHelper h) { t.hisGroundTurnsAChunk(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "ground_made_before")
    public void landMadeBeforeTheClaimStays(GameTestHelper h) { t.landMadeBeforeTheClaimStays(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_outside")
    public void groundOutsideHisIsLeftAlone(GameTestHelper h) { t.groundOutsideHisIsLeftAlone(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_border")
    public void bigFeaturesComeOutWholeInAnyOrder(GameTestHelper h) { t.bigFeaturesComeOutWholeInAnyOrder(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_raised")
    public void raisedGroundIsNeverHollow(GameTestHelper h) { t.raisedGroundIsNeverHollow(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ground_upgrade")
    public void anOldGroundKeepsItsPlaceAndItsLand(GameTestHelper h) { t.anOldGroundKeepsItsPlaceAndItsLand(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ground_den")
    public void theDenStandsInTheMiddle(GameTestHelper h) { t.theDenStandsInTheMiddle(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "ground_generated")
    public void theWorldMakesHisGroundAsItMakesTheLand(GameTestHelper h) { t.theWorldMakesHisGroundAsItMakesTheLand(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "ground_biome_rule")
    public void theBiomeHookDecidesRight(GameTestHelper h) { t.theBiomeHookDecidesRight(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "ground_new")
    public void groundNewClaimsLandNotMadeYet(GameTestHelper h) { t.groundNewClaimsLandNotMadeYet(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "finder_here")
    public void theFinderPointsAtHim(GameTestHelper h) { t.theFinderPointsAtHim(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "finder_stored")
    public void theFinderKnowsWhereTheWorldKeepsHim(GameTestHelper h) { t.theFinderKnowsWhereTheWorldKeepsHim(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "natural_death")
    public void whenTheWorldsOwnOneDiesTheNextIsDue(GameTestHelper h) { t.whenTheWorldsOwnOneDiesTheNextIsDue(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "lang_keys")
    public void everyNewLineHasItsWords(GameTestHelper h) { t.everyNewLineHasItsWords(h); }
}
