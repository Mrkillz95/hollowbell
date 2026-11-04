package net.jj.giants.test;

import com.google.gson.JsonObject;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.systems.Followers;
import net.jj.giants.systems.SystemsData;
import net.jj.giants.systems.Underworld;
import net.jj.giants.systems.Villages;
import net.jj.giants.systems.Weather;
import net.jj.giants.systems.WeatherKinds;
import net.jj.giants.tools.Watch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Part 9 with the real giants (-Pgiants, ADMIN_BRIDGE.md 25): every giant takes Mirror's counters ("sysact" "counter")
 * and tells the Guide a player's hit with its tactic through his own hurt path; gathers his own kind of follower;
 * brings his own weather; frightens villagers; takes Random chaos's resting moves; his ground is a biome of his mod
 * (sacred ground); and his own summon brings him into the Nether, sized to fit (the Underworld). Without the giants
 * each test passes at once.
 */
public class SystemsBGiantsGameTests implements FabricGameTest {

    private static boolean here(String key) {
        Giant g = Giants.byKey(key);
        return g != null && Giants.loaded(g);
    }

    private void everything(GameTestHelper h, String key, int slot) {
        if (!here(key)) {
            h.succeed();
            return;
        }
        MinecraftServer s = h.getLevel().getServer();
        ServerLevel l = h.getLevel();
        Giant g = Giants.byKey(key);
        BlockPos at = FunGiantsGameTests.pad(h, 40 + slot);
        LivingEntity e = FunGiantsGameTests.summon(h, g, at, 0.15);
        h.assertTrue(e != null, key + ": his summon made one");
        ServerPlayer p = PlayersGameTests.join(h, "p9_" + key.substring(0, 4), false);
        Difficulty was = l.getDifficulty();
        h.runAfterDelay(20, () -> {
            AdminWorld w = AdminWorld.get(s);
            try {
                p.setGameMode(GameType.SURVIVAL);
                p.teleportTo(l, e.getX() + 6, e.getY(), e.getZ(), 0, 0);
                // Mirror: his header says he counters; a counter is taken
                List<String> head = AdminServer.call(s, g, "sys", "1");
                h.assertTrue(head != null && head.get(0).contains("\"counter\""), key + ": " + head);
                JsonObject c = new JsonObject();
                c.addProperty("sys", 1);
                c.addProperty("do", "counter");
                c.addProperty("id", "-");
                JsonObject k = new JsonObject();
                k.addProperty("melee", 0.5);
                c.add("counter", k);
                List<String> r = AdminServer.call(s, g, "sysact", c.toString());
                h.assertTrue(r != null && r.get(0).equals("ok"), key + ": counter " + r);
                // a player's blow through his own hurt path is told with its tactic
                AdminServer.set(s, "Tester", "all", "mirror", "on");
                String uses = AdminServer.call(s, g, "admin", "1").get(0);
                h.assertTrue(uses.contains("all.mirror"), key + ": uses " + uses);
                e.invulnerableTime = 0;
                e.hurt(p.damageSources().playerAttack(p), 4f);
                SystemsData.Learned m = SystemsData.get(s).s.mirror.get(key);
                h.assertTrue(m != null && m.score[0] > 0, key + ": a player's melee blow learned: "
                        + (m == null ? null : java.util.Arrays.toString(m.score)));
                AdminServer.set(s, "Tester", "all", "mirror", "off");
                // his ground is his own mod's biome (sacred ground)
                boolean biome = s.registryAccess().registryOrThrow(Registries.BIOME).keySet().stream()
                        .anyMatch(id -> id.getNamespace().equals(g.modId()));
                h.assertTrue(biome, key + ": a biome of his mod");
                // weather: his own round him
                AdminServer.set(s, "Tester", "all", "weather", "on");
                Weather.lookForTests(s);
                Weather.In in = Weather.of(p);
                h.assertTrue(in != null && in.kind().equals(WeatherKinds.of(key).id()), key + ": his weather " + in
                        + " on " + net.jj.giants.systems.Systems.on(s, "weather") + " p " + p.level().dimension()
                        .location() + " " + p.blockPosition().toShortString() + " e " + e.blockPosition()
                        .toShortString() + " rows " + Watch.rows(s).get(key));
                AdminServer.set(s, "Tester", "all", "weather", "off");
                Weather.lookForTests(s);
                // villagers near a wild one run (unless he sleeps)
                Villager v = SystemsBGameTests.villager(l, e.blockPosition(), 10, 0);
                AdminServer.set(s, "Tester", "all", "villagers", "flee");
                Villages.lookForTests(s);
                String row = String.valueOf(Watch.rows(s).get(key));
                h.assertTrue(Villages.tookForTests(v) == Villages.Took.FLED || row.contains("\"asleep\":true"),
                        key + ": the villager fled: " + Villages.tookForTests(v) + " " + row);
                v.discard();
                AdminServer.set(s, "Tester", "all", "villagers", "off");
                // followers: his own kind rises beside him
                s.setDifficulty(Difficulty.NORMAL, true);
                AdminServer.set(s, "Tester", "all", "followers", "on");
                AdminServer.set(s, "Tester", "all", "followerCount", "1");
                Followers.hurryForTests();
                Followers.lookForTests(s);
                List<Mob> band = l.getEntitiesOfClass(Mob.class, new AABB(e.blockPosition()).inflate(120, 60, 120),
                        x -> x.getTags().contains(Followers.OF + e.getStringUUID()));
                h.assertTrue(band.size() == 1 && band.get(0).getType() == Followers.typeOf(key), key
                        + ": his follower " + band);
                h.assertTrue(!Followers.allowDamage(band.get(0), e.damageSources().mobAttack(e), 5f), key
                        + ": his blows spare it");
                AdminServer.set(s, "Tester", "all", "followers", "off");
                Followers.tick(s);
                h.assertTrue(band.get(0).isRemoved(), key + ": gone when off");
                // Random chaos: his resting moves are taken (nothing refused)
                net.jj.giants.systems.Chaos.seedForTests(7);
                AdminServer.set(s, "Tester", "all", "chaos", "on");
                net.jj.giants.systems.Chaos.shuffle(s, s.overworld().getDayTime() / 24000L);
                List<String> took = AdminServer.push(s, w, g);
                h.assertTrue(took != null && took.size() == 1, key + ": took the day's moves: " + took + " resting "
                        + SystemsData.get(s).s.rests.get(key));
                AdminServer.set(s, "Tester", "all", "chaos", "off");
                net.jj.giants.systems.Chaos.tick(s);
            } finally {
                for (String id : List.of("all.mirror", "all.weather", "all.villagers", "all.followers",
                        "all.followerCount", "all.chaos"))
                    w.remove("all", id);
                AdminServer.push(s, w);
                s.setDifficulty(was, true);
                e.discard();
                FunGiantsGameTests.unpad(h, at);
            }
            // the Underworld: his own summon brings him below (the Nether; the End for one who can't live under a
            // roof), sized to fit under the Nether's roof
            String dim = Underworld.dimsFor(key, "nether").get(0);
            boolean roof = dim.equals(Underworld.NETHER);
            ServerLevel below = s.getLevel(roof ? Level.NETHER : Level.END);
            for (LivingEntity x : FunGiants.standing(s, null)) if (x.level() != l) x.discard();
            BlockPos room = SystemsBGameTests.room(s, below, 70000 + slot * 200, 70000);
            p.teleportTo(below, room.getX() + 0.5, room.getY(), room.getZ() + 0.5, 0, 0);
            Underworld.nearMin = 4;
            Underworld.nearMax = 10;
            // (only his kind: the others aren't brought down beside him in this test)
            Underworld.onlyForTests = key;
            AdminServer.set(s, "Tester", "all", "underworld", "nether");
            SystemsData.get(s).s.under.clear();
            String ukey = key + "|" + dim + "|0";
            Underworld.lookForTests(s);
            // (his own summon may take a few ticks to bring him up: look again for up to ten seconds; where he stands
            // just after he's put there)
            double[] firstY = {Double.NaN};
            // (one look scheduled at a time: many scheduled at once from inside a scheduled step ran that step again)
            Runnable[] poll = new Runnable[1];
            int[] left = {40};
            poll[0] = () -> {
                try {
                    SystemsData.Under kept = SystemsData.get(s).s.under.get(ukey);
                    if (kept == null || kept.id.isEmpty()) Underworld.lookForTests(s);
                    kept = SystemsData.get(s).s.under.get(ukey);
                    LivingEntity e1 = kept == null || kept.id.isEmpty() ? null : FunGiants.find(s, kept.id);
                    if (e1 != null && Double.isNaN(firstY[0])) firstY[0] = e1.getY();
                } catch (RuntimeException x) {
                    net.jj.giants.GiantsGuideMod.LOG.warn("p9 underworld look failed", x);
                }
                if (--left[0] > 0) h.runAfterDelay(5, poll[0]);
            };
            h.runAfterDelay(5, poll[0]);
            h.runAfterDelay(240, () -> {
                try {
                    SystemsData.Under u = SystemsData.get(s).s.under.get(ukey);
                    LivingEntity one = u == null || u.id.isEmpty() ? null : FunGiants.find(s, u.id);
                    h.assertTrue(one != null && one.level() == below && one.getTags().contains(Underworld.MARK),
                            key + ": one of him in " + dim + ": " + (u == null ? null : u.id) + " " + where(s, key));
                    if (roof) {
                        double tall = FunGiants.height(key, FunGiants.sizeOf(one));
                        h.assertTrue(tall <= 31, key + ": fits the room: " + tall + " blocks at size "
                                + FunGiants.sizeOf(one));
                        // (a flyer rises at once: he only has to be under the roof)
                        double most = key.equals("hollowbell") ? 123 : 70;
                        h.assertTrue(firstY[0] > 30 && firstY[0] < most, key + ": put in the room: " + firstY[0]);
                        h.assertTrue(one.getY() < 123, key + ": still under the roof: " + one.getY());
                    }
                    one.discard();
                } finally {
                    Underworld.nearMin = 48;
                    Underworld.nearMax = 160;
                    AdminWorld.get(s).remove("all", "all.underworld");
                    AdminServer.push(s, AdminWorld.get(s));
                    for (LivingEntity x : FunGiants.standing(s, null)) if (x.level() != l) x.discard();
                    Underworld.onlyForTests = null;
                    SystemsData.get(s).s.under.clear();
                    p.teleportTo(l, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, 0, 0);
                    SystemsBGameTests.unroom(s, below, room);
                    s.getPlayerList().remove(p);
                }
                h.succeed();
            });
        });
    }

    /** where every one of him is (for a failure's line) */
    private static String where(MinecraftServer s, String key) {
        StringBuilder b = new StringBuilder();
        for (LivingEntity x : FunGiants.standing(s, key))
            b.append(x.level().dimension().location()).append(' ').append(x.blockPosition().toShortString())
                    .append(x.getTags().contains(Underworld.MARK) ? " marked" : "").append("; ");
        return b + " rows " + Watch.rows(s).get(key);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_pitchgut")
    public void pitchgut(GameTestHelper h) {
        everything(h, "pitchgut", 0);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_furrowmaw")
    public void furrowmaw(GameTestHelper h) {
        everything(h, "furrowmaw", 1);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_cerberus")
    public void cerberus(GameTestHelper h) {
        everything(h, "cerberus", 2);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_hollowbell")
    public void hollowbell(GameTestHelper h) {
        everything(h, "hollowbell", 3);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_lanternwillow")
    public void lanternwillow(GameTestHelper h) {
        everything(h, "lanternwillow", 4);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_wreckback")
    public void wreckback(GameTestHelper h) {
        everything(h, "wreckback", 5);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_swarmforge")
    public void swarmforge(GameTestHelper h) {
        everything(h, "swarmforge", 6);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "p9_real_gloamwick")
    public void gloamwick(GameTestHelper h) {
        everything(h, "gloamwick", 7);
    }
}
