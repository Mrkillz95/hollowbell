package net.jj.giants.client.dev;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.adventure.AdvData;
import net.jj.giants.adventure.Adventure;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Development only (autotest line "coveplay MODE"): the Wreck Cove Rescue played as a player plays it. The player walks
 * (4.3 blocks a second, step by step on the ground) from where he stands along the shore road to the lost giant,
 * then leads him home: walks on along the road toward the harbour, keeping about 10 blocks ahead of him (stopping
 * when further). Mode "cove": once he is on his way, the player turns back and walks into the cove instead (the guard).
 * Every two seconds the lost giant's, the guard's and the player's places, the script steps and how far the lost
 * giant is off the road go to the log ("coveplay:").
 */
final class CovePlay {
    private CovePlay() {}

    static String mode = "";
    static int phase = -1, ticks;
    private static boolean registered;

    static void start(String m) {
        mode = m;
        phase = 0;
        ticks = 0;
        px = Double.NaN;
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(CovePlay::tick);
    }

    static double[][] road() {
        AdvData.State st = Adventure.roundState();
        if (st == null || !st.paths.containsKey("shore_road")) return null;
        var pts = st.paths.get("shore_road").pts;
        double[][] r = new double[pts.size()][];
        for (int i = 0; i < r.length; i++) r[i] = new double[] {pts.get(i)[0], pts.get(i)[2]};
        return r;
    }

    /** how far along the road (from its start at the beach) the spot is nearest, and how far off it */
    static double[] along(double[][] road, double x, double z) {
        double best = Double.MAX_VALUE, at = 0, run = 0;
        for (int i = 0; i + 1 < road.length; i++) {
            double[] a = road[i], b = road[i + 1];
            double vx = b[0] - a[0], vz = b[1] - a[1], len = Math.sqrt(vx * vx + vz * vz);
            double t = Mth.clamp(((x - a[0]) * vx + (z - a[1]) * vz) / (len * len), 0, 1);
            double d = Math.hypot(a[0] + vx * t - x, a[1] + vz * t - z);
            if (d < best) {
                best = d;
                at = run + len * t;
            }
            run += len;
        }
        return new double[] {at, best, run};
    }

    /** the spot so far along the road */
    static double[] spot(double[][] road, double s) {
        double run = 0;
        for (int i = 0; i + 1 < road.length; i++) {
            double[] a = road[i], b = road[i + 1];
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (s <= run + len) {
                double t = Mth.clamp((s - run) / len, 0, 1);
                return new double[] {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
            }
            run += len;
        }
        return road[road.length - 1];
    }

    static Entity tagged(ServerLevel l, String tag) {
        for (Entity e : l.getAllEntities()) if (e.getTags().contains(tag) && e.isAlive()) return e;
        return null;
    }

    static double px = Double.NaN, pz;

    static void tick(MinecraftServer srv) {
        if (phase < 0) return;
        Adventure.Round r = Adventure.round;
        if (r == null || srv.getPlayerList().getPlayers().isEmpty()) return;
        ServerLevel l = r.level;
        ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
        if (p.level() != l || p.isSpectator()) return;
        double[][] road = road();
        if (road == null) return;
        Entity lost = tagged(l, "adv_lost"), guard = tagged(l, "adv_guard");
        ticks++;
        if (Double.isNaN(px) || ticks == 1) {
            px = p.getX();
            pz = p.getZ();
        }
        double[] me = along(road, px, pz);
        double[] he = lost == null ? null : along(road, lost.getX(), lost.getZ());
        double tx = px, tz = pz, speed = 4.3 / 20;
        if (phase == 0) {
            // walking up the road to him (off it, straight to him, once at its end)
            if (lost != null && Math.hypot(lost.getX() - px, lost.getZ() - pz) < 12) phase = 1;
            else if (me[0] > 0.5) {
                double[] sp = spot(road, Math.max(0, me[0] - 3));
                tx = sp[0];
                tz = sp[1];
            } else if (lost != null) {
                tx = lost.getX();
                tz = lost.getZ();
            }
        }
        if (phase == 1 && he != null) {
            // leading him home: making for the spot 10 blocks ahead of him along the road
            double[] sp = spot(road, Math.min(me[2], he[0] + 10));
            tx = sp[0];
            tz = sp[1];
            if (mode.equals("cove") && he[0] > 20) phase = 2;
        }
        if (phase == 2) {
            // back into the cove: out into the lagoon (the guard's)
            tx = 40;
            tz = 0;
            if (guard != null && Math.hypot(guard.getX() - px, guard.getZ() - pz) < 8) phase = 3;
            if (ticks % 20 == 0 && Math.hypot(tx - px, tz - pz) < 2) phase = 3;
        }
        if (phase == 3) {
            // running for it: back along the road to the harbour, sprinting
            double[] sp = spot(road, me[2]);
            tx = sp[0];
            tz = sp[1];
            speed = 5.6 / 20;
        }
        double dx = tx - px, dz = tz - pz, d = Math.hypot(dx, dz);
        if (d > speed) {
            px += dx / d * speed;
            pz += dz / d * speed;
        } else {
            px = tx;
            pz = tz;
        }
        double x = px, z = pz;
        if (ticks % 40 == 0) {
            StringBuilder sb = new StringBuilder();
            AdvData.State st = Adventure.roundState();
            if (st != null) for (var e : st.runs.values()) sb.append(e.script).append('@').append(e.step + 1).append(' ');
            GiantsGuideMod.LOG.info("coveplay: t {}s phase {} player {} {} | lost {} | guard {}{} | {}", ticks / 20, phase,
                    Math.round(x), Math.round(z), lost == null ? "-" : Math.round(lost.getX()) + " " + Math.round(lost.getZ())
                            + " along " + Math.round(he[0]) + " off " + Math.round(he[1] * 10) / 10.0,
                    guard == null ? "-" : Math.round(guard.getX()) + " " + Math.round(guard.getZ()),
                    guard == null ? "" : " (" + Math.round(Math.hypot(guard.getX() - x, guard.getZ() - z)) + " from the player)",
                    sb);
        }
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        Entity look = phase >= 2 && guard != null ? guard : lost;
        float yaw = look == null ? 90 : (float) (Mth.atan2(look.getZ() - z, look.getX() - x) * Mth.RAD_TO_DEG) - 90f;
        p.teleportTo(l, x, y, z, yaw, 15);
    }
}
