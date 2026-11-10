package net.jj.giants.duels;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.phys.Vec3;

/**
 * How fast each giant goes, in blocks a second: sent somewhere (the Guide's "go") and going after a cow, at size 1 and
 * 0.25, on the duel arena's land and in its lake. Only run when asked: -Pduels -Pduel_pairs=none -Pduel_speed=true.
 * One line per giant, size and ground in the log ("SPEED ...").
 */
public class SpeedProbe implements FabricGameTest {
    static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("giant-speeds");
    static final boolean ON = Boolean.getBoolean("jj.duel.speed");
    /** the sizes (-Pduel_speedsizes=1,0.25) and giants (-Pduel_speedkinds=P,WB; none: all) to measure */
    static final float[] SIZES = sizes(System.getProperty("jj.duel.speedsizes", "1,0.25"));
    static final String KINDS = System.getProperty("jj.duel.speedkinds", "");

    static float[] sizes(String v) {
        String[] p = v.split(",");
        float[] out = new float[p.length];
        for (int i = 0; i < p.length; i++) out[i] = Float.parseFloat(p[i].trim());
        return out;
    }
    static boolean built;

    @GameTestGenerator
    public Collection<TestFunction> speeds() {
        List<TestFunction> out = new ArrayList<>();
        if (!ON) return out;
        int n = 0;
        for (GiantDuelTests.Kind k : GiantDuelTests.KINDS)
            for (float s : SIZES) {
                if (!KINDS.isBlank() && !java.util.Arrays.asList(KINDS.split(",")).contains(k.code())) continue;
                for (boolean water : new boolean[] {false, true}) {
                    String name = String.format(Locale.ROOT, "speed_%02d_%s_%s_%s", n++, k.code(),
                            String.valueOf(s).replace('.', '_'), water ? "water" : "land");
                    out.add(new TestFunction(name, name, EMPTY_STRUCTURE, 900, 0, false, h -> run(h, k, s, water)));
                }
            }
        return out;
    }

    static void run(GameTestHelper h, GiantDuelTests.Kind k, float s, boolean water) {
        ServerLevel l = h.getLevel();
        if (!built) {
            GiantDuelTests.setUp(l.getServer());
            for (GiantDuelTests.Kind x : GiantDuelTests.KINDS) GiantDuelTests.bridge(l.getServer(), x, "meetings", "off");
            GiantDuelTests.build(l);
            built = true;
        }
        GiantDuelTests.clear(l);
        // land: well west of the middle, going east; water: the west side of the lake, going east across it
        double x0 = water ? GiantDuelTests.LAKE_X - GiantDuelTests.LAKE_R + 25 : GiantDuelTests.CX - 300;
        double z0 = water ? GiantDuelTests.LAKE_Z : GiantDuelTests.CZ + 200;
        double y0 = water ? GiantDuelTests.TOP - GiantDuelTests.LAKE_DEPTH : GiantDuelTests.TOP;
        LivingEntity g = spawn(l, k, new Vec3(x0, y0, z0), s);
        double[] at = new double[4];
        Cow[] cow = {null};
        h.runAfterDelay(40, () -> {
            at[0] = g.getX();
            at[1] = g.getZ();
            String go = String.format(Locale.ROOT, "{\"sys\":1,\"id\":\"%s\",\"do\":\"go\",\"x\":%.1f,\"z\":%.1f}",
                    g.getUUID(), x0 + 2000, z0);
            List<String> said = GiantDuelTests.bridge(l.getServer(), k, "sysact", go);
            if (said == null || said.isEmpty() || !"ok".equals(said.get(0)))
                LOG.info("SPEED " + k.code() + " go said " + said);
        });
        // (his going speed once he's up to it: the first 2.5 s after the order are his getting going)
        h.runAfterDelay(90, () -> {
            at[0] = g.getX();
            at[1] = g.getZ();
        });
        h.runAfterDelay(140, () -> {
            at[2] = Math.hypot(g.getX() - at[0], g.getZ() - at[1]) / 2.5;
            // then something to go after, far ahead: a cow that doesn't move
            Cow c = EntityType.COW.create(l);
            c.moveTo(g.getX() + 150 * s + 60, water ? y0 + GiantDuelTests.LAKE_DEPTH : y0, g.getZ(), 0f, 0f);
            c.setNoAi(true);
            c.setInvulnerable(true);
            l.addFreshEntity(c);
            cow[0] = c;
            if (g instanceof Mob m) m.setTarget(c);
            at[0] = g.getX();
            at[1] = g.getZ();
        });
        h.runAfterDelay(160, () -> {
            at[0] = g.getX();
            at[1] = g.getZ();
            if (g instanceof Mob m && m.getTarget() == null) m.setTarget(cow[0]);
        });
        h.runAfterDelay(190, () -> {
            at[0] = g.getX();
            at[1] = g.getZ();
        });
        h.runAfterDelay(260, () -> {
            at[3] = Math.hypot(g.getX() - at[0], g.getZ() - at[1]) / 3.5;
            String line = String.format(Locale.ROOT, "SPEED %-2s size %.2f %-5s go %.2f b/s, after a cow %.2f b/s%s",
                    k.code(), s, water ? "water" : "land", at[2], at[3],
                    g instanceof Mob m && m.getTarget() != cow[0] ? " (not after it at the end)" : "");
            LOG.info(line);
            if (cow[0] != null) cow[0].discard();
            g.discard();
            h.succeed();
        });
    }

    static LivingEntity spawn(ServerLevel l, GiantDuelTests.Kind k, Vec3 at, float s) {
        var type = EntityType.byString(k.id()).orElseThrow();
        var e = type.create(l);
        e.moveTo(at.x, at.y, at.z, -90f, 0f);
        GiantDuelTests.set(e, k.setSize(), float.class, s);
        l.addFreshEntity(e);
        return (LivingEntity) e;
    }
}
