package net.jj.hollowbell.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public class TmpRerun implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "b1")
    public void b1(GameTestHelper h) { HollowbellGameTests.blendCheck(h, 83); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "b2")
    public void b2(GameTestHelper h) { HollowbellGameTests.blendCheck(h, 84); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "b3")
    public void b3(GameTestHelper h) { HollowbellGameTests.blendCheck(h, 85); }
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "b4")
    public void b4(GameTestHelper h) { HollowbellGameTests.blendCheck(h, 86); }
}
