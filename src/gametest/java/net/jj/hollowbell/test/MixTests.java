package net.jj.hollowbell.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsMix;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * (JJ answers 2, ADMIN_BRIDGE.md 22.16 and 23.6) The Giants Guide's "no giants" dimension rule holds the world's own
 * one back (his respawn line says why), and "Giants in each other's grounds" lets him come in another giant's ground,
 * on a spot he can live on, or in his own when there is none. Nothing sent: as always.
 */
public class MixTests implements FabricGameTest {

    static JsonObject state(MinecraftServer s) {
        return JsonParser.parseString(GiantsBridge.giants(s, "respawn", "1").get(0)).getAsJsonObject();
    }

    private static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "ans2_held")
    public void noGiantsWorldHoldsTheWorldsOwnBack(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        WorldOne w = WorldOne.get(s);
        boolean comes = BellConfig.V.oneInTheWorld;
        long[] was = w.respawnTestState(false, false, s.overworld().getGameTime() + 100);
        try {
            BellConfig.V.oneInTheWorld = true;
            h.assertTrue(!GiantsMix.held(s.overworld()) && !state(s).has("held"), "as always: " + state(s));
            all(h, "\"all.dimOverworld\":\"nogiants\"");
            h.assertTrue(GiantsMix.held(s.overworld()), "held in the overworld");
            h.assertTrue(!GiantsMix.held(s.getLevel(Level.NETHER)), "not in the nether");
            JsonObject o = state(s);
            h.assertTrue(o.get("state").getAsString().equals("due")
                    && o.get("held").getAsString().equals("minecraft:overworld"), "said: " + o);
            all(h, "\"all.dimOverworld\":\"allowed\"");
            h.assertTrue(!GiantsMix.held(s.overworld()) && !state(s).has("held"), "lifted: " + state(s));
            all(h, "\"all.dimNether\":\"nogiants\"");
            h.assertTrue(!GiantsMix.held(s.overworld()) && !state(s).has("held"), "another world's rule");
        } finally {
            all(h, "");
            w.respawnTestState(was[0] == 1, was[1] == 1, was[2]);
            BellConfig.V.oneInTheWorld = comes;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "ans2_mix")
    public void heComesInAnotherGiantsGround(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        ServerLevel l = h.getLevel();
        WorldOne w = WorldOne.get(s);
        float scale = BellConfig.V.worldScale;
        boolean comes = BellConfig.V.oneInTheWorld, again = BellConfig.V.respawn;
        long[] was = w.respawnTestState(false, false, l.getGameTime());
        BlockPos mid = h.absolutePos(new BlockPos(4, 1, 4));
        try {
            BellConfig.V.worldScale = 0.05f;
            // (other batches may turn these off: he comes on his own here)
            BellConfig.V.oneInTheWorld = true;
            BellConfig.V.respawn = true;
            // off: his own ground, as always
            h.assertTrue(!w.mixStep(l), "off: his own");
            all(h, "\"all.spawnMix\":true");
            h.assertTrue(GiantsMix.mix(), "on");
            // his own drawn: his own
            GiantsMix.testOwn = true;
            h.assertTrue(!w.mixStep(l), "his own drawn");
            w.respawnTestState(false, false, l.getGameTime());
            GiantsMix.testOwn = false;
            // another's far off, not loaded: he waits there, and the respawn line says so
            GiantsMix.testPick = new GiantsMix.Pick("pitchgut", 3_000_000, 3_000_000, 300);
            h.assertTrue(w.mixStep(l), "waits for someone to be there");
            JsonObject o = state(s);
            h.assertTrue(o.get("in").getAsString().equals("pitchgut") && o.get("x").getAsInt() == 3_000_000, "" + o);
            h.assertTrue(l.getEntitiesOfClass(HollowbellEntity.class, new AABB(mid).inflate(40)).isEmpty(), "none yet");
            // (the test's barrier roof off over the middle, so the ground there is the top)
            for (int x = 1; x <= 7; x++)
                for (int z = 1; z <= 7; z++)
                    for (int y = 5; y <= 12; y++)
                        if (h.getBlockState(new BlockPos(x, y, z)).is(Blocks.BARRIER))
                            h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
            // another's here (loaded): he comes there, the world's own one
            w.respawnTestState(false, false, l.getGameTime());
            GiantsMix.testPick = new GiantsMix.Pick("pitchgut", mid.getX(), mid.getZ(), 2);
            h.assertTrue(w.mixStep(l), "came");
            List<HollowbellEntity> got = l.getEntitiesOfClass(HollowbellEntity.class, new AABB(mid).inflate(40));
            h.assertTrue(got.size() == 1, "one came: " + got.size());
            HollowbellEntity e = got.get(0);
            h.assertTrue(Math.hypot(e.getX() - mid.getX(), e.getZ() - mid.getZ()) < 4
                    && Math.abs(e.getY() - mid.getY()) < 2, "in that ground, on the ground: " + e.position());
            h.assertTrue(state(s).get("state").getAsString().equals("standing") && !state(s).has("in"),
                    "the world's own one, standing: " + state(s));
            e.discard();
            // a ground all water: one who swims comes in the water; a land-only one finds nowhere
            for (int x = 2; x <= 6; x++)
                for (int z = 2; z <= 6; z++) {
                    h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                    for (int y = 1; y <= 4; y++) h.setBlock(new BlockPos(x, y, z), Blocks.WATER);
                }
            GiantsMix.Pick wet = new GiantsMix.Pick("pitchgut", mid.getX(), mid.getZ(), 1);
            BlockPos dry = GiantsMix.spot(l, wet, List.of("land"), l.random);
            h.assertTrue(dry == null, "land only: nowhere " + dry);
            BlockPos at = GiantsMix.spot(l, wet, List.of("land", "sea"), l.random);
            h.assertTrue(at != null && at.getY() <= mid.getY(), "he swims: the sea floor " + at);
            // his own places: in the water only if he swims; else his own ground this time
            boolean swims = GiantsMix.places().contains("sea");
            h.assertTrue((GiantsMix.spot(l, wet, l.random) != null) == swims, "his places " + GiantsMix.places());
            w.respawnTestState(false, false, l.getGameTime());
            GiantsMix.testPick = wet;
            boolean came = w.mixStep(l);
            h.assertTrue(came == swims, "came in the water: " + came);
            if (!swims) h.assertTrue(state(s).get("state").getAsString().equals("due") && !state(s).has("in"),
                    "his own ground this time: " + state(s));
        } finally {
            GiantsMix.testPick = null;
            GiantsMix.testOwn = false;
            all(h, "");
            for (HollowbellEntity e : l.getEntitiesOfClass(HollowbellEntity.class, new AABB(mid).inflate(40)))
                e.discard();
            w.respawnTestState(was[0] == 1, was[1] == 1, was[2]);
            BellConfig.V.worldScale = scale;
            BellConfig.V.oneInTheWorld = comes;
            BellConfig.V.respawn = again;
        }
        h.succeed();
    }
}
