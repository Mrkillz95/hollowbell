package net.jj.hollowbell.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * (Part 6, ADMIN_BRIDGE.md §22) The admin book's respawn timers over his bridge: "respawn" says when the world's own
 * one comes back; "respawnact" brings him back now or sets the wait, lifting a fall that had ended his kind.
 */
public class RespawnTimerTests implements FabricGameTest {

    static JsonObject state(MinecraftServer s) {
        List<String> l = GiantsBridge.giants(s, "respawn", "1");
        return JsonParser.parseString(l.get(0)).getAsJsonObject();
    }

    static List<String> act(MinecraftServer s, String what, long ticks) {
        return GiantsBridge.giants(s, "respawnact", "{\"respawn\":1,\"do\":\"" + what + "\",\"ticks\":" + ticks + "}");
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "respawn_timer")
    public void respawnTimersReadAndSet(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        WorldOne w = WorldOne.get(s);
        long now = s.overworld().getGameTime();
        boolean comes = BellConfig.V.oneInTheWorld, again = BellConfig.V.respawn;
        long[] was = w.respawnTestState(false, false, now + 48000);
        try {
            BellConfig.V.oneInTheWorld = true;
            BellConfig.V.respawn = true;
            JsonObject o = state(s);
            h.assertTrue(o.get("respawn").getAsInt() == 1 && o.get("name").getAsString().equals("Hollowbell"),
                    "the header: " + o);
            h.assertTrue(o.get("state").getAsString().equals("due"), "counting down: " + o);
            h.assertTrue(Math.abs(o.get("ticks").getAsLong() - 48000) <= 5, "two days left: " + o);
            h.assertTrue(o.get("comes").getAsBoolean() && o.get("again").getAsBoolean(), "his switches: " + o);
            // a new wait
            List<String> r = act(s, "in", 1200 * 10);
            h.assertTrue(r.get(0).equals("ok") && r.get(1).contains("about 10 minutes"), "set: " + r);
            h.assertTrue(Math.abs(state(s).get("ticks").getAsLong() - 12000) <= 5, "10 minutes left");
            // now
            r = act(s, "now", 0);
            h.assertTrue(r.get(0).equals("ok") && r.get(1).contains("within a few seconds"), "now: " + r);
            h.assertTrue(state(s).get("ticks").getAsLong() <= 5, "due at once: " + state(s));
            // fallen with Comes back after he falls off: gone for good, until an admin brings him back
            w.respawnTestState(false, true, now + 1000);
            BellConfig.V.respawn = false;
            h.assertTrue(state(s).get("state").getAsString().equals("gone"), "gone: " + state(s));
            r = act(s, "now", 0);
            h.assertTrue(r.get(0).equals("ok"), "brought back: " + r);
            h.assertTrue(state(s).get("state").getAsString().equals("due"), "due again: " + state(s));
            // refusals: standing, not coming on his own, a wait too long; nonsense
            w.respawnTestState(true, false, -1);
            h.assertTrue(state(s).get("state").getAsString().equals("standing"), "standing");
            r = act(s, "now", 0);
            h.assertTrue(r.get(0).equals("no") && r.get(1).contains("already out there"), "" + r);
            w.respawnTestState(false, false, now + 100);
            BellConfig.V.oneInTheWorld = false;
            h.assertTrue(state(s).get("state").getAsString().equals("off"), "off");
            r = act(s, "now", 0);
            h.assertTrue(r.get(0).equals("no") && r.get(1).contains("Comes on his own is off"), "" + r);
            BellConfig.V.oneInTheWorld = true;
            r = act(s, "in", 1001L * 24000L);
            h.assertTrue(r.get(0).equals("no"), "too long: " + r);
            h.assertTrue(act(s, "explode", 0) == null, "an unknown action");
            h.assertTrue(GiantsBridge.giants(s, "respawnact", "not json") == null, "unreadable");
            h.assertTrue(GiantsBridge.giants(s, "respawnact", "{\"respawn\":2,\"do\":\"now\"}") == null, "newer");
            h.assertTrue(GiantsBridge.giants(s, "respawn", "2") == null, "a newer list");
        } finally {
            w.respawnTestState(was[0] == 1, was[1] == 1, was[2]);
            BellConfig.V.oneInTheWorld = comes;
            BellConfig.V.respawn = again;
        }
        h.succeed();
    }
}
