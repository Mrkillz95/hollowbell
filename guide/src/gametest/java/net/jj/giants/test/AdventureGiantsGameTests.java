package net.jj.giants.test;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.adventure.AdvData;
import net.jj.giants.adventure.Adventure;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.jj.giants.adventure.AdvMaps;
import net.jj.giants.minigames.MgData;
import net.jj.giants.minigames.MgGame;
import net.jj.giants.minigames.Minigames;

/**
 * Part 11 with the real giants (-Pgiants): a real Wreckback (or the first giant installed) given a script walks its
 * path by his own wander spots (his `sysact go`) and gets his boss bar title from it.
 */
public class AdventureGiantsGameTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 4000, batch = "advg_walk")
    public void aRealGiantWalksHisScriptsPath(GameTestHelper h) {
        Giant g = Giants.byKey("wreckback");
        if (g == null || !Giants.loaded(g)) g = Giants.installed().isEmpty() ? null : Giants.installed().get(0);
        if (g == null) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        BlockPos at = FunGiantsGameTests.pad(h, 61);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.15);
        h.assertTrue(e != null, "summoned");
        String dim = h.getLevel().dimension().location().toString();
        double x0 = e.getX();
        Adventure.pathAdd(s, "Tester", "walk_east", dim, e.getX(), e.getY(), e.getZ());
        Adventure.pathAdd(s, "Tester", "walk_east", dim, e.getX() + 60, e.getY(), e.getZ());
        Adventure.scriptStep(s, "Tester", "walker", 0, "title The Walker");
        Adventure.scriptStep(s, "Tester", "walker", 0, "walk walk_east");
        h.assertTrue(Adventure.give(s, "Tester", 2, e, "walker").ok(), "given");
        Giant k = g;
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(e.getX() - x0 > 15 || AdvData.get(s).s.runs.get(e.getStringUUID())
                        .done, "walking east: " + Math.round(e.getX() - x0)))
                .thenExecute(() -> {
                    h.assertTrue(e.getCustomName() != null && e.getCustomName().getString().contains("The Walker"),
                            "his title: " + e.getCustomName());
                    JsonObject o = new JsonObject();
                    o.addProperty("live", AdminLive.VERSION);
                    o.addProperty("id", e.getStringUUID());
                    o.addProperty("do", "remove");
                    o.addProperty("value", "");
                    o.addProperty("by", "-");
                    AdminServer.call(s, k, AdminLive.ACT, o.toString());
                    AdvData.get(s).s = new AdvData.State();
                    Adventure.forget();
                    h.succeed();
                });
    }

    /** (lagfix) the lost giant's blows that landed on a player, and the guard's on him */
    static final java.util.concurrent.atomic.AtomicInteger lostHits = new java.util.concurrent.atomic.AtomicInteger(),
            guardOnLost = new java.util.concurrent.atomic.AtomicInteger();
    private static boolean listening;

    /** (cove2) how far a spot is from the round's shore road */
    static double offRoad(double x, double z) {
        var rs = Adventure.roundState();
        var pts = rs.paths.get("shore_road").pts;
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] a = pts.get(i), b = pts.get(i + 1);
            double vx = b[0] - a[0], vz = b[2] - a[2], len2 = vx * vx + vz * vz;
            double t = Math.max(0, Math.min(1, ((x - a[0]) * vx + (z - a[2]) * vz) / len2));
            best = Math.min(best, Math.hypot(a[0] + vx * t - x, a[2] + vz * t - z));
        }
        return best;
    }

    /** (cove2) the round at the size a game takes when nobody picks one (medium), as JJ plays it */
    static MinecraftServer freshCove(GameTestHelper h) {
        listen();
        lostHits.set(0);
        guardOnLost.set(0);
        MinecraftServer s = MinigameGiantsGameTests.fresh(h);
        MinigameGameTests.set(h, s, "mgSize", "medium");
        AdvData.get(s).s = new AdvData.State();
        Adventure.forget();
        return s;
    }

    static void listen() {
        if (listening) return;
        listening = true;
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register(
                (e, src, base, taken, blocked) -> {
                    if (taken <= 0) return;
                    var by = src.getEntity();
                    boolean fromLost = by != null && by.getTags().contains("adv_lost");
                    if (e instanceof ServerPlayer && fromLost) lostHits.incrementAndGet();
                    if (e.getTags().contains("adv_lost") && by != null && by.getTags().contains("adv_guard"))
                        guardOnLost.incrementAndGet();
                });
    }

    /**
     * (lagfix, JJ's playtest; cove2) The Wreck Cove Rescue played through with the real giants: the lost giant stands
     * where the map puts him (by the road's end on the beach, not where his own summon drops him, 23 blocks off); a
     * player slips in while the guard is at the far end of his beat and keeps near him (a few blocks ahead); he walks
     * the shore road to the harbour by himself, on the road all the way (the escort step), the round is won by his
     * hook, and on the way the guard never hurts him and he never hurts the player.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 24000, batch = "advg_cove")
    public void theLostGiantWalksTheShoreRoadHomeInPeace(GameTestHelper h) {
        if (!MinigameGiantsGameTests.real(h)) return;
        MinecraftServer s = freshCove(h);
        ServerPlayer p = MinigameGameTests.join(h, "advcovereal", false).p();
        h.assertTrue(Adventure.play(s, "Tester", AdvMaps.BUILT_IN, false, 1).ok(), "opened");
        h.assertTrue(Minigames.join(p, false).ok(), "joined");
        Minigames.now(s, "Tester");
        LivingEntity[] lost = {null}, guard = {null};
        double[] startX = {0}, minShare = {1}, maxOff = {0};
        int[] ticks = {0}, guardStep = {0};
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(MinigameGiantsGameTests.game().phase() == MgGame.Phase.PLAY,
                        "playing"))
                .thenExecute(() -> MinigameGameTests.arrived(p))
                .thenWaitUntil(() -> {
                    MgGame g = MinigameGiantsGameTests.game();
                    for (var k : g.giants.standing()) {
                        var e = g.giants.entity(k);
                        if (e != null && e.getTags().contains("adv_lost")) lost[0] = e;
                        if (e != null && e.getTags().contains("adv_guard")) guard[0] = e;
                    }
                    var rs = Adventure.roundState();
                    h.assertTrue(lost[0] != null && guard[0] != null && rs != null && rs.runs.size() == 2,
                            "both map giants stand");
                })
                .thenExecute(() -> {
                    startX[0] = lost[0].getX();
                    net.jj.giants.GiantsGuideMod.LOG.info("cove test: the lost giant starts at {} {}, the player at {}",
                            Math.round(lost[0].getX()), Math.round(lost[0].getZ()), Math.round(p.getX()));
                    h.assertTrue(startX[0] > -40, "he starts on the cove's beach, not at the harbour: " + startX[0]);
                    // (cove2) where the map puts him: by the road's end (his own summon put him 23 blocks south)
                    h.assertTrue(offRoad(lost[0].getX(), lost[0].getZ()) < 6, "he starts by the road: "
                            + Math.round(lost[0].getX()) + " " + Math.round(lost[0].getZ()));
                    double gs = net.jj.giants.fun.FunGiants.sizeOf(guard[0]),
                            ls = net.jj.giants.fun.FunGiants.sizeOf(lost[0]);
                    h.assertTrue(gs > ls * 1.8, "the guard is twice his size: " + gs + " to " + ls);
                })
                // (cove2) the player slips in while the guard is at the far end of his beat
                .thenWaitUntil(() -> h.assertTrue(guard[0].distanceTo(lost[0]) > 28, "the guard far off: "
                        + Math.round(guard[0].distanceTo(lost[0]))))
                // the player walks with him: a few blocks ahead (west, the harbour side, the way players come), once a
                // second, until he's home
                .thenWaitUntil(() -> {
                    MgGame g = Minigames.current();
                    if (g == null || g.phase() != MgGame.Phase.PLAY) return;
                    if (++ticks[0] % 20 == 0 && !lost[0].isRemoved() && g.level != null) {
                        ServerLevel l = g.level;
                        double x = lost[0].getX() - 6, z = lost[0].getZ();
                        p.teleportTo(l, x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x),
                                (int) Math.floor(z)), z, -90, 0);
                        p.setHealth(p.getMaxHealth());
                        minShare[0] = Math.min(minShare[0], lost[0].getHealth() / Math.max(1, lost[0].getMaxHealth()));
                        // (cove2) on the road all the way, once he's off (his first steps are from the beach to it)
                        if (lost[0].getX() < startX[0] - 6)
                            maxOff[0] = Math.max(maxOff[0], offRoad(lost[0].getX(), lost[0].getZ()));
                        var rs = Adventure.roundState();
                        if (rs != null) for (var run : rs.runs.values())
                            if (run.script.equals("cove_guard") && run.step > guardStep[0]) {
                                guardStep[0] = run.step;
                                net.jj.giants.GiantsGuideMod.LOG.info("cove test: the guard is on step {}, the player at {} {}",
                                        run.step, Math.round(p.getX()), Math.round(p.getZ()));
                            }
                        if (ticks[0] % 200 == 0) net.jj.giants.GiantsGuideMod.LOG.info("cove test: he is at {} {}",
                                Math.round(lost[0].getX()), Math.round(lost[0].getZ()));
                    }
                    h.assertTrue(false, "on the road: x " + Math.round(lost[0].getX()) + " (from "
                            + Math.round(startX[0]) + ")");
                })
                .thenWaitUntil(() -> h.assertTrue(Minigames.current() == null, "the game over"))
                .thenExecute(() -> {
                    var res = MgData.get(s).s.results;
                    String line = res.isEmpty() ? "" : res.get(res.size() - 1).line;
                    h.assertTrue(line.contains("home safe"), "won by reaching the harbour: " + line);
                    h.assertTrue(lostHits.get() == 0, "the lost giant never hurt the player: " + lostHits.get());
                    h.assertTrue(guardOnLost.get() == 0, "the guard never hurt him: " + guardOnLost.get());
                    h.assertTrue(minShare[0] > 0.95, "he was never in a fight: health down to " + minShare[0]);
                    h.assertTrue(maxOff[0] < 6, "he kept to the road: at most " + Math.round(maxOff[0]) + " off it");
                    MinigameGiantsGameTests.done(h, p);
                });
    }

    /**
     * (cove2, JJ "posed no threat") The Wreck Cove Rescue's guard with the real giants: a player who wades out into the
     * lagoon is seen (his patrol ends), hunted (he comes for them), and given up on once they are well away (back to his
     * beat); the lost giant he never touches.
     */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 12000, batch = "advg_cove_guard")
    public void theCoveGuardHuntsAPlayerWhoWadesIntoTheCove(GameTestHelper h) {
        if (!MinigameGiantsGameTests.real(h)) return;
        MinecraftServer s = freshCove(h);
        ServerPlayer p = MinigameGameTests.join(h, "advcoveguard", false).p();
        h.assertTrue(Adventure.play(s, "Tester", AdvMaps.BUILT_IN, false, 1).ok(), "opened");
        h.assertTrue(Minigames.join(p, false).ok(), "joined");
        Minigames.now(s, "Tester");
        LivingEntity[] lost = {null}, guard = {null};
        double[] first = {0}, nearest = {Double.MAX_VALUE};
        int[] ticks = {0};
        java.util.function.IntSupplier step = () -> {
            var rs = Adventure.roundState();
            if (rs != null) for (var run : rs.runs.values()) if (run.script.equals("cove_guard")) return run.step;
            return -1;
        };
        java.util.function.BiConsumer<Double, Double> put = (x, z) -> {
            MgGame g = Minigames.current();
            if (g == null || g.level == null) return;
            ServerLevel l = g.level;
            p.teleportTo(l, x, l.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)),
                    z, 90, 0);
            p.setHealth(p.getMaxHealth());
        };
        h.startSequence()
                .thenWaitUntil(() -> h.assertTrue(MinigameGiantsGameTests.game().phase() == MgGame.Phase.PLAY,
                        "playing"))
                .thenExecute(() -> MinigameGameTests.arrived(p))
                .thenWaitUntil(() -> {
                    MgGame g = MinigameGiantsGameTests.game();
                    for (var k : g.giants.standing()) {
                        var e = g.giants.entity(k);
                        if (e != null && e.getTags().contains("adv_lost")) lost[0] = e;
                        if (e != null && e.getTags().contains("adv_guard")) guard[0] = e;
                    }
                    h.assertTrue(lost[0] != null && guard[0] != null && step.getAsInt() == 2, "the guard on his beat");
                })
                // out into the lagoon, the cove's middle (40, 0), and stays there
                .thenWaitUntil(() -> {
                    if (++ticks[0] % 20 == 1) put.accept(40.0, 0.0);
                    h.assertTrue(step.getAsInt() >= 4, "seen: his step " + step.getAsInt());
                })
                .thenExecute(() -> {
                    first[0] = guard[0].distanceTo(p);
                    net.jj.giants.GiantsGuideMod.LOG.info("cove guard test: the hunt begins {} blocks off",
                            Math.round(first[0]));
                })
                .thenWaitUntil(() -> {
                    if (++ticks[0] % 20 == 0) put.accept(40.0, 0.0);
                    nearest[0] = Math.min(nearest[0], guard[0].distanceTo(p));
                    h.assertTrue(nearest[0] < Math.min(8, first[0] - 5), "he comes for the player: "
                            + Math.round(guard[0].distanceTo(p)) + " blocks off (from " + Math.round(first[0]) + ")");
                })
                .thenExecute(() -> net.jj.giants.GiantsGuideMod.LOG.info("cove guard test: he came within {} blocks",
                        Math.round(nearest[0])))
                // away to the harbour: he gives up and goes back to his beat
                .thenWaitUntil(() -> {
                    if (++ticks[0] % 20 == 0) put.accept(-72.0, 0.0);
                    h.assertTrue(step.getAsInt() <= 2, "gave up: his step " + step.getAsInt());
                })
                .thenExecute(() -> {
                    h.assertTrue(guardOnLost.get() == 0, "the guard never hurt the lost giant: " + guardOnLost.get());
                    MinigameGiantsGameTests.done(h, p);
                });
    }
}
