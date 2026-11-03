package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.players.Players;
import net.jj.giants.tools.Respawns;
import net.jj.giants.tools.Stats;
import net.jj.giants.tools.Tools;
import net.jj.giants.tools.ToolsData;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Part 6 with the real giants loaded (-Pgiants; each skips itself without them): every giant answers the respawn
 * timers (ADMIN_BRIDGE.md §22.2), the pause freezes a real one and lets him go, a real one near spawn is sent home and
 * then taken away, and what each one costs the server is measured from his own ticks.
 */
public class ToolsGiantsGameTests implements FabricGameTest {

    static boolean none(GameTestHelper h) {
        if (!Giants.installed().isEmpty()) return false;
        h.succeed();
        return true;
    }

    /** one of him at size 0.1, calm, here (his own summon command), or null */
    static Entity summon(GameTestHelper h, Giant g, BlockPos at) {
        MinecraftServer s = h.getLevel().getServer();
        String type = Players.MAIN.get(g.key());
        List<Entity> before = new ArrayList<>(h.getLevel().getEntities(
                BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(type)), e -> true));
        s.getCommands().performPrefixedCommand(s.createCommandSourceStack().withLevel(h.getLevel())
                .withPosition(Vec3.atBottomCenterOf(at)), g.command() + " summon calm 0.1");
        for (Entity e : h.getLevel().getEntities(BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(type)),
                e -> true))
            if (!before.contains(e)) return e;
        return null;
    }

    static void removeById(MinecraftServer s, Giant g, Entity e) {
        AdminServer.call(s, g, "liveact", "{\"live\":1,\"id\":\"" + e.getStringUUID() + "\",\"do\":\"remove\","
                + "\"value\":\"\",\"by\":\"-\"}");
        if (!e.isRemoved()) e.discard();
    }

    static boolean frozen(Entity e) {
        // (each giant keeps it his own way: a field, a frozen() of his, or his orders' frozen())
        try {
            return e.getClass().getField("frozen").getBoolean(e);
        } catch (ReflectiveOperationException x) {
            // next way
        }
        try {
            return (boolean) e.getClass().getMethod("frozen").invoke(e);
        } catch (ReflectiveOperationException x) {
            // next way
        }
        try {
            Object orders = e.getClass().getMethod("orders").invoke(e);
            return (boolean) orders.getClass().getMethod("frozen").invoke(orders);
        } catch (ReflectiveOperationException x) {
            throw new IllegalStateException("no frozen on " + e.getClass().getName());
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tools_real_respawn")
    public void everyGiantAnswersHisRespawnTimer(GameTestHelper h) {
        if (none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        List<String> said = new ArrayList<>();
        for (Giant g : Giants.installed()) {
            JsonObject o = Respawns.state(s, g);
            h.assertTrue(o != null, AdminServer.nameOf(g) + " has no respawn timer");
            String state = o.get("state").getAsString();
            h.assertTrue(List.of("standing", "due", "gone", "off", "waiting").contains(state), g.key() + ": " + o);
            h.assertTrue(o.get("name").getAsString().equals(AdminServer.nameOf(g)), "his name: " + o);
            AdminServer.Result r = Respawns.act(s, "Tester", g.key(), 12000);
            if (state.equals("standing") || state.equals("off")) {
                h.assertTrue(!r.ok(), g.key() + " refused: " + r.lines());
                continue;
            }
            h.assertTrue(r.ok() && r.lines().get(0).contains("10 minutes"), g.key() + ": " + r.lines());
            JsonObject now = Respawns.state(s, g);
            h.assertTrue(now.get("state").getAsString().equals("due")
                    && Math.abs(now.get("ticks").getAsLong() - 12000) <= 5, g.key() + " after: " + now);
            // (as it was: a wait of what it had, or the default)
            long back = o.has("ticks") ? o.get("ticks").getAsLong() : 48000;
            Respawns.act(s, "Tester", g.key(), Math.max(1, back));
            said.add(g.key());
        }
        h.assertTrue(!said.isEmpty() || Giants.installed().size() > 0, "none answered");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "tools_real_pause")
    public void pauseFreezesTheRealGiantsAndLetsThemGo(GameTestHelper h) {
        if (none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        List<Giant> gs = Giants.installed();
        List<Entity> them = new ArrayList<>();
        List<Giant> which = new ArrayList<>();
        for (int i = 0; i < gs.size(); i++) {
            Entity e = summon(h, gs.get(i), h.absolutePos(new BlockPos(2 + i * 6, 2, 2)));
            // (one that doesn't stand there at once after his summon, rising or coming down: left out)
            if (e == null) continue;
            them.add(e);
            which.add(gs.get(i));
        }
        h.assertTrue(them.size() >= Math.min(2, gs.size()), "summoned: " + which);
        AdminServer.set(s, "Tester", "all", "paused", "on");
        h.runAfterDelay(5, () -> {
            Watch.lookNow(s);
            for (Entity e : them) h.assertTrue(frozen(e), "frozen: " + e.getType());
            h.assertTrue(ToolsData.get(s).s.frozen.contains(them.get(0).getStringUUID()), "noted");
            AdminServer.set(s, "Tester", "all", "paused", "off");
            Watch.lookNow(s);
            for (Entity e : them) h.assertTrue(!frozen(e), "let go: " + e.getType());
            // (other batches' giants may come and go meanwhile: only these are checked)
            for (Entity e : them) h.assertTrue(!ToolsData.get(s).s.frozen.contains(e.getStringUUID()), "none left");
            for (int i = 0; i < them.size(); i++) removeById(s, which.get(i), them.get(i));
            AdminWorld.get(s).remove("all", "all.paused");
            AdminServer.push(s, AdminWorld.get(s));
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "tools_real_area")
    public void aRealGiantNearSpawnIsSentHomeThenTakenAway(GameTestHelper h) {
        if (none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        Giant g = AdminServer.giant("wreckback") != null ? AdminServer.giant("wreckback") : Giants.installed().get(0);
        BlockPos sp = s.overworld().getSharedSpawnPos();
        // (an area round where he stands: the test's own spot, as "spawn" is somewhere else)
        Entity e = summon(h, g, h.absolutePos(new BlockPos(4, 2, 4)));
        h.assertTrue(e != null, "summoned");
        ToolsData.Area a = new ToolsData.Area();
        a.id = "test";
        a.name = "Test area";
        a.kind = "keepout";
        a.dim = "minecraft:overworld";
        a.x = e.getBlockX();
        a.z = e.getBlockZ();
        a.r = 64;
        ToolsData.get(s).s.areas.add(a);
        h.runAfterDelay(5, () -> {
            Watch.lookNow(s);
            h.assertTrue(ToolsData.get(s).s.alerts.stream().anyMatch(x -> x.kind.equals("area")
                    && x.text.contains("sent home")), "sent home");
            Watch.sentOutForTests(g.key() + "/" + e.getStringUUID(), 1300, s);
            Watch.lookNow(s);
            h.assertTrue(e.isRemoved(), "taken away");
            ToolsData.get(s).s.areas.clear();
            ToolsData.get(s).s.alerts.clear();
            h.succeed();
        });
        h.assertTrue(sp != null, "spawn");
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "tools_real_cost")
    public void whatEachGiantCostsIsMeasured(GameTestHelper h) {
        if (none(h)) return;
        MinecraftServer s = h.getLevel().getServer();
        Giant g = AdminServer.giant("wreckback") != null ? AdminServer.giant("wreckback") : Giants.installed().get(0);
        Entity e = summon(h, g, h.absolutePos(new BlockPos(4, 2, 4)));
        h.assertTrue(e != null, "summoned");
        h.assertTrue(g.key().equals(Tools.kindOf(e)), "his kind: " + Tools.kindOf(e));
        h.runAfterDelay(420, () -> {
            h.assertTrue(Stats.costMs(g.key()) > 0, "measured: " + Stats.costMs(g.key()));
            removeById(s, g, e);
            h.succeed();
        });
    }
}
