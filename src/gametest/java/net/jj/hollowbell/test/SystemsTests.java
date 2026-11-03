package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsSystems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.rig.BellRig;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 8, ADMIN_BRIDGE.md 24.4) The Giants Guide's new systems, his side: weak points (the glowing spots on his bell
 * and his crown), and the "sys"/"sysact" bridge (his weak spots now, his mood, turning on a player, a spot to wander
 * to). Weak points off: the very same number, nothing told.
 */
public class SystemsTests implements FabricGameTest {

    static List<String> act(MinecraftServer s, UUID id, String what, String more) {
        String json = "{\"sys\":1,\"do\":\"" + what + "\",\"id\":\"" + id + "\"" + (more.isEmpty() ? "" : "," + more)
                + "}";
        return GiantsBridge.giants(s, "sysact", json);
    }

    static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    /** the first bone of this kind */
    static int boneOf(HollowbellEntity e, BellRig.Kind k) {
        for (int i = 0; i < e.rig.kind.length; i++) if (e.rig.kind[i] == k) return i;
        return -1;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "sys_head")
    public void sysSaysHisWeakPointsAndWhatHeDoes(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<String> r = GiantsBridge.giants(s, "sys", "1");
        h.assertTrue(r != null && r.size() == 1, "one line: " + r);
        JsonObject o = JsonParser.parseString(r.get(0)).getAsJsonObject();
        h.assertTrue(o.get("sys").getAsInt() == 1 && o.get("name").getAsString().equals("Hollowbell")
                && o.get("giant").getAsString().equals("hollowbell"), "" + o);
        h.assertTrue(o.getAsJsonArray("does").toString().equals("[\"spots\",\"mood\",\"turn\",\"go\",\"counter\"]"), "" + o);
        h.assertTrue(o.getAsJsonArray("weak").size() == 2, "his spots and his crown: " + o);
        h.assertTrue(GiantsBridge.giants(s, "sysact", "nonsense") == null, "unreadable: null");
        h.assertTrue(GiantsBridge.giants(s, "sysact", "{\"sys\":2,\"do\":\"spots\",\"id\":\"" + UUID.randomUUID()
                + "\"}") == null, "a newer protocol: null");
        h.assertTrue(act(s, UUID.randomUUID(), "dance", "") == null, "an unknown do: null");
        h.assertTrue(act(s, UUID.randomUUID(), "spots", "").equals(List.of("no", "He isn't there any more.")),
                "nobody there");
        String head = GiantsBridge.giants(s, "admin", "1").get(0);
        h.assertTrue(head.contains("all.weakPoints") && head.contains("all.weakHit") && head.contains("all.bodyHit"),
                "uses: " + head);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sys_off")
    public void weakPointsOffChangeNothing(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.CALM, 1640);
        ServerPlayer p = player(h, e.position().add(30, 1, 0));
        h.runAfterDelay(5, () -> {
            all(h, "");
            h.assertTrue(!GiantsSystems.active() && !GiantsSystems.reportsForTests(), "nothing on as shipped");
            DamageSource src = p.damageSources().playerAttack(p);
            float a = 7.3f;
            h.assertTrue(GiantsSystems.hit(e, src, a, true, null) == a, "a weak spot: the same number");
            h.assertTrue(GiantsSystems.hit(e, src, a, false, null) == a, "elsewhere: the same number");
            float before = e.healthNow();
            e.applyDamage(src, 10f, e.rig.spots[0].bone(), false);
            h.assertTrue(Math.abs((before - e.healthNow()) - 25f) < 0.01f, "a spot takes its own x2.5 as always: "
                    + (before - e.healthNow()));
            release(h, e);
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sys_weak")
    public void weakPointsTakeMoreAndTheRestTheShare(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.CALM, 1641);
        ServerPlayer p = player(h, e.position().add(30, 1, 0));
        h.runAfterDelay(5, () -> {
            try {
                all(h, "\"all.weakPoints\":true,\"all.weakHit\":3,\"all.bodyHit\":0.5");
                DamageSource src = p.damageSources().playerAttack(p);
                h.assertTrue(GiantsSystems.active() && GiantsSystems.weakHitForTests() == 3f, "on");
                h.assertTrue(GiantsSystems.hit(e, src, 4f, true, null) == 12f, "a weak spot x3");
                h.assertTrue(GiantsSystems.hit(e, src, 4f, false, null) == 2f, "elsewhere x0.5");
                h.assertTrue(GiantsSystems.hit(e, e.damageSources().genericKill(), 4f, false, null) == 4f,
                        "a kill is a kill");
                // through his own hurt path: a spot x2.5 x3, his crown x3 x3, the copper x0.1 x0.5
                float before = e.healthNow();
                e.applyDamage(src, 10f, e.rig.spots[0].bone(), false);
                h.assertTrue(Math.abs((before - e.healthNow()) - 75f) < 0.05f, "a spot: " + (before - e.healthNow()));
                e.heal();
                int crown = boneOf(e, BellRig.Kind.CROWN), rim = boneOf(e, BellRig.Kind.RIM);
                h.assertTrue(crown >= 0 && rim >= 0, "a crown and a rim");
                before = e.healthNow();
                e.applyDamage(src, 10f, crown, false);
                h.assertTrue(Math.abs((before - e.healthNow()) - 90f) < 0.05f, "his crown: " + (before - e.healthNow()));
                e.heal();
                before = e.healthNow();
                e.applyDamage(src, 10f, rim, false);
                h.assertTrue(Math.abs((before - e.healthNow()) - 0.5f) < 0.01f, "the copper: " + (before - e.healthNow()));
            } finally {
                GiantsAdmin.clear();
                release(h, e);
                drop(p);
            }
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sys_spots")
    public void hisSpotsAreOnHisBodyAndAHitAimedThereIsWeak(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, 0.5f, HollowbellEntity.CALM, 1642);
        h.runAfterDelay(5, () -> {
            List<String> r = act(s, e.getUUID(), "spots", "");
            h.assertTrue(r != null && r.get(0).equals("ok"), "" + r);
            JsonArray a = JsonParser.parseString(r.get(1)).getAsJsonObject().getAsJsonArray("spots");
            h.assertTrue(a.size() == e.rig.spots.length + 1, "each spot and his crown: " + a.size());
            double reach = 220 * e.bellScale(); // (his bell hangs high: about 200 blocks tall at size 1)
            for (var j : a) {
                JsonObject o = j.getAsJsonObject();
                Vec3 at = new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble());
                h.assertTrue(at.distanceTo(e.position()) < reach && o.get("r").getAsDouble() > 0, "on him: " + o);
            }
            List<GiantsSystems.Spot> spots = GiantsSystems.spots(e);
            GiantsSystems.Spot one = spots.get(0);
            ServerPlayer p = player(h, one.at().add(0, 0, 40));
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, one.at());
            h.assertTrue(GiantsSystems.nearForTests(spots, p), "aimed at a weak spot");
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, one.at().add(0, -60, 0));
            h.assertTrue(!GiantsSystems.nearForTests(List.of(one), p), "looking away: not");
            release(h, e);
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sys_mood")
    public void moodTurnAndGo(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1643);
        ServerPlayer p = player(h, e.position().add(30, 1, 0));
        h.runAfterDelay(5, () -> {
            try {
                h.assertTrue(act(s, e.getUUID(), "mood", "\"value\":\"hunting\"")
                        .equals(List.of("ok", "Hollowbell: mood hunting.")), "mood");
                h.assertTrue(e.variant() == HollowbellEntity.HUNTER, "hunting now");
                h.assertTrue(act(s, e.getUUID(), "mood", "\"value\":\"cross\"").get(0).equals("no"), "not a mood");
                act(s, e.getUUID(), "mood", "\"value\":\"calm\"");
                p.getInventory().add(new ItemStack(net.jj.hollowbell.ModItems.CODEX));
                BookRule.get(s).look(List.of(p));
                h.assertTrue(e.spares(p), "the book holder is spared");
                List<String> r = act(s, e.getUUID(), "turn", "\"player\":\"" + p.getUUID() + "\"");
                h.assertTrue(r.get(0).equals("ok") && r.get(1).startsWith("Hollowbell turned on "), "" + r);
                h.assertTrue(!e.spares(p) && e.mood().hunting(p.getUUID()), "no longer spared, hunted");
                h.assertTrue(e.getTarget() == p, "he goes for them: " + e.getTarget());
                h.assertTrue(act(s, e.getUUID(), "turn", "\"player\":\"" + UUID.randomUUID() + "\"")
                        .equals(List.of("no", "That player isn't online.")), "nobody");
                e.mood().settle(null);
                e.setTarget(null);
                Vec3 to = e.position().add(40, 0, 0);
                r = act(s, e.getUUID(), "go", "\"x\":" + to.x + ",\"z\":" + to.z);
                h.assertTrue(r.equals(List.of("ok", "Hollowbell: on his way.")), "" + r);
                Vec3 w = e.wandering();
                h.assertTrue(w != null && Math.abs(w.x - to.x) < 1 && Math.abs(w.z - to.z) < 1, "his own spot: " + w);
            } finally {
                BookRule.get(s).free();
                release(h, e);
                drop(p);
            }
            h.succeed();
        });
    }

    /** (Part 9, ADMIN_BRIDGE.md 25.8) Mirror: what the Guide has learned cuts the players' tactic it names, nothing else */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "sys_mirror")
    public void mirrorCutsTheTacticPlayersLeanOn(GameTestHelper h) {
        net.minecraft.server.MinecraftServer s = h.getLevel().getServer();
        net.minecraft.world.entity.monster.Zombie z = net.minecraft.world.entity.EntityType.ZOMBIE.create(h.getLevel());
        net.fabricmc.fabric.api.entity.FakePlayer p = net.fabricmc.fabric.api.entity.FakePlayer.get(h.getLevel());
        net.minecraft.world.damagesource.DamageSource melee = p.damageSources().playerAttack(p),
                shot = p.damageSources().thrown(p, p), blast = p.damageSources().explosion(p, p),
                magic = p.damageSources().indirectMagic(p, p), mob = z.damageSources().mobAttack(z);
        GiantsBridge.giants(s, "adminset", "{\"admin\":1,\"values\":{},\"all\":{}}");
        h.assertTrue(!GiantsSystems.active(), "off as shipped");
        h.assertTrue(GiantsSystems.hit(z, melee, 4.3f, false, null) == 4.3f, "off: the very same number");
        h.assertTrue(GiantsSystems.tactic(melee).equals("melee") && GiantsSystems.tactic(shot).equals("arrows")
                && GiantsSystems.tactic(blast).equals("blasts") && GiantsSystems.tactic(magic).equals("magic"),
                "the four tactics");
        GiantsBridge.giants(s, "adminset", "{\"admin\":1,\"values\":{},\"all\":{\"all.mirror\":true}}");
        h.assertTrue(GiantsSystems.active(), "Mirror on: the hook runs");
        h.assertTrue(GiantsSystems.hit(z, melee, 4f, false, null) == 4f, "nothing learned yet: all of it");
        java.util.List<String> r = GiantsBridge.giants(s, "sysact", "{\"sys\":1,\"do\":\"counter\",\"id\":\"-\","
                + "\"counter\":{\"melee\":0.5,\"blasts\":0.8}}");
        h.assertTrue(r != null && r.size() == 2 && r.get(0).equals("ok"), "taken: " + r);
        h.assertTrue(GiantsSystems.hit(z, melee, 4f, false, null) == 2f, "melee: half");
        h.assertTrue(GiantsSystems.hit(z, blast, 5f, false, null) == 4f, "blasts x0.8");
        h.assertTrue(GiantsSystems.hit(z, shot, 4f, false, null) == 4f, "arrows: all of it");
        h.assertTrue(GiantsSystems.hit(z, mob, 4f, false, null) == 4f, "only players' hits are cut");
        h.assertTrue(GiantsBridge.giants(s, "sysact", "{\"sys\":2,\"do\":\"counter\",\"id\":\"-\"}") == null,
                "a newer protocol: null");
        // Mirror off: all of it again, and what he learned forgotten
        GiantsBridge.giants(s, "adminset", "{\"admin\":1,\"values\":{},\"all\":{}}");
        h.assertTrue(!GiantsSystems.active() && GiantsSystems.hit(z, melee, 4.3f, false, null) == 4.3f
                && GiantsSystems.counter("melee") == 1f, "off again");
        z.discard();
        h.succeed();
    }
}
