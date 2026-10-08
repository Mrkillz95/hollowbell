package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/** (temporary: runs a few tests on their own) */
public class TmpRerun implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "no_snap")
    public void blend(GameTestHelper h) { new HollowbellGameTests().movesBlendInAndOutEvenCutOffHalfway(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "sweep_reach")
    public void sweep(GameTestHelper h) { new HollowbellGameTests().hisSweepReachesAllTheWayOut(h); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "tp_parked")
    public void tpParked(GameTestHelper h) { new HollowbellGameTests().tpAndWhereFindOneLyingUnloaded(h); }
}
