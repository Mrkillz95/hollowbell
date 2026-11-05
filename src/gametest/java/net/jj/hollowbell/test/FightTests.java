package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsAdmin;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.GiantsFight;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27) Giant fights, his side: as shipped every hook gives back the same thing; the world's
 * "Fight to", giant damage, owned giants who may not kill, loot; a staged fight's tags (no backing down, pace, floor,
 * team, moves, chase), a helper's blow counting as its giant's; and "fights"/"fightact" (his backing down is his own
 * climb and flight, or settling where he is). Each test has a batch of its own (the settings are the world's); slots
 * 1800-1807 are these tests' own.
 */
public class FightTests implements FabricGameTest {

    static List<String> act(MinecraftServer s, UUID id, String what, String more) {
        String json = "{\"fight\":1,\"do\":\"" + what + "\",\"id\":\"" + id + "\"" + (more.isEmpty() ? "" : ","
                + more) + "}";
        return GiantsBridge.giants(s, "fightact", json);
    }

    static void all(GameTestHelper h, String all) {
        List<String> r = GiantsBridge.giants(h.getLevel().getServer(), "adminset",
                "{\"admin\":1,\"values\":{},\"all\":{" + all + "}}");
        h.assertTrue(r != null && r.equals(List.of("ok")), "taken: " + r);
    }

    /** a still cow standing in for another giant of that kind */
    static Mob standIn(GameTestHelper h, HollowbellEntity e, double dx, String kind) {
        Mob o = EntityType.COW.create(h.getLevel());
        o.moveTo(Math.floor(e.getX() + dx) + 0.5, e.getY(), Math.floor(e.getZ()) + 0.5, 0f, 0f);
        o.setNoAi(true);
        o.setInvulnerable(true);
        o.setPersistenceRequired();
        o.addTag(Giants.TAG);
        o.addTag(Meeting.KIND + kind);
        h.getLevel().addFreshEntity(o);
        return o;
    }

    /** a still cow standing in for that giant's helper (a robot, a mudling...): kin, and jj_giant_of its giant */
    static Mob helperOf(GameTestHelper h, HollowbellEntity e, double dz, Entity giant) {
        Mob o = EntityType.COW.create(h.getLevel());
        o.moveTo(Math.floor(e.getX()) + 0.5, e.getY(), Math.floor(e.getZ() + dz) + 0.5, 0f, 0f);
        o.setNoAi(true);
        o.setInvulnerable(true);
        o.setPersistenceRequired();
        o.addTag(Giants.KIN);
        o.addTag(Giants.OF + giant.getUUID());
        h.getLevel().addFreshEntity(o);
        return o;
    }

    private static HollowbellEntity free(GameTestHelper h, int slot) {
        BellConfig.V.meetings = true;
        BellConfig.V.fightGiants = true;
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, slot);
        e.setStay(false);
        return e;
    }

    /** a stand-in giant's blow at him */
    private static void hit(HollowbellEntity e, Mob by, float amount) {
        e.invulnerableTime = 0;
        e.hurt(e.damageSources().mobAttack(by), amount);
    }

    private static void health(HollowbellEntity e, float share) {
        e.setHealthTo(e.healthMax() * share);
    }

    private static void tidy(GameTestHelper h, HollowbellEntity e, Entity... others) {
        GiantsAdmin.clear();
        for (String t : List.copyOf(e.getTags())) if (t.startsWith("jj_giants.")) e.removeTag(t);
        for (Entity o : others) if (o != null) o.discard();
        release(h, e);
    }

    /** then, t ticks on: a failure tidies up too */
    private static void later(GameTestHelper h, int t, HollowbellEntity e, Entity[] others, Runnable r) {
        h.runAfterDelay(t, () -> {
            try {
                r.run();
            } catch (RuntimeException x) {
                tidy(h, e, others);
                throw x;
            }
        });
    }

    private static void finish(GameTestHelper h, HollowbellEntity e, Entity... others) {
        tidy(h, e, others);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_head")
    public void asShippedEveryHookChangesNothing(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        List<String> r = GiantsBridge.giants(s, "fights", "1");
        h.assertTrue(r != null && r.size() == 1, "one line: " + r);
        JsonObject o = JsonParser.parseString(r.get(0)).getAsJsonObject();
        h.assertTrue(o.get("fight").getAsInt() == 1 && o.get("name").getAsString().equals("Hollowbell")
                && o.get("giant").getAsString().equals("hollowbell"), "" + o);
        h.assertTrue(o.getAsJsonArray("moves").toString().contains("\"" + GiantsFight.FINISHER + "\""),
                "his finisher is one of his moves: " + o);
        h.assertFalse(o.getAsJsonArray("moves").toString().contains("harvest"), "only fight moves: " + o);
        h.assertTrue(o.getAsJsonArray("does").toString().equals(
                "[\"hp\",\"health\",\"fight\",\"yield\",\"chase\",\"stop\",\"do\",\"roar\"]"), "" + o);
        h.assertTrue(GiantsBridge.giants(s, "fightact", "nonsense") == null, "unreadable: null");
        h.assertTrue(GiantsBridge.giants(s, "fightact", "{\"fight\":2,\"do\":\"hp\",\"id\":\"" + UUID.randomUUID()
                + "\"}") == null, "a newer protocol: null");
        h.assertTrue(act(s, UUID.randomUUID(), "dance", "") == null, "an unknown do: null");
        h.assertTrue(act(s, UUID.randomUUID(), "hp", "").equals(List.of("no", "He isn't there any more.")),
                "nobody there");
        String head = GiantsBridge.giants(s, "admin", "1").get(0);
        for (String u : GiantsFight.USES) h.assertTrue(head.contains(u), "uses " + u + ": " + head);
        HollowbellEntity e = free(h, 1800);
        Mob cer = standIn(h, e, 30, "fire_ice_cerberus");
        Mob helper = helperOf(h, e, 30, cer);
        Entity[] others = {cer, helper};
        later(h, 5, e, others, () -> {
            all(h, "");
            h.assertTrue(GiantsFight.stateForTests().equals("back_down,death,1.0,1.0,true,true,true,false,false,false"),
                    "the jar: " + GiantsFight.stateForTests());
            h.assertFalse(GiantsFight.active(e), "nothing to do for him");
            DamageSource src = e.damageSources().mobAttack(cer);
            float a = 7.3f, hp = e.healthNow(), max = e.healthMax();
            for (boolean ordered : new boolean[] {false, true}) {
                h.assertTrue(GiantsFight.taken(e, src, cer, a, hp, max, ordered, 0.25f) == a, "the same number");
                h.assertTrue(GiantsFight.taken(e, src, cer, hp * 2, hp, max, ordered, 0.25f) == hp * 2,
                        "a killing blow untouched");
                h.assertTrue(GiantsFight.yieldNow(e, cer, ordered, max * 0.1f, max, 0.25f) == null,
                        "the old rule decides (ordered " + ordered + ")");
            }
            h.assertTrue(GiantsFight.mayStart(e, cer, false) && GiantsFight.mayStart(e, cer, true), "nobody asked");
            h.assertTrue(GiantsFight.moveOk(e, "sky_dive") && GiantsFight.moveOk(e, "strand_lash")
                    && !GiantsFight.chasing(e, cer), "every move, no chase");
            float before = e.healthNow();
            hit(e, helper, 6f);
            h.assertTrue(e.healthNow() < before, "a helper's blow lands as ever");
        });
        later(h, 7, e, others, () -> {
            // a real wild fight as ever: he backs down at a quarter
            health(e, 0.24f);
            hit(e, cer, 1f);
            h.assertTrue(Meeting.yielding(e) && e.flee().running(),
                    "as shipped he backs down from a wild fight at a quarter, and flies off");
            h.assertFalse(e.getTags().contains(GiantsFight.NO_DROPS), "no tag");
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_wildto")
    public void wildFightToDeathAndFlee(GameTestHelper h) {
        HollowbellEntity e = free(h, 1801);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus"), b = standIn(h, e, -30, "furrowmaw");
        Entity[] others = {a, b};
        later(h, 5, e, others, () -> {
            all(h, "\"all.wildFightTo\":\"death\"");
            health(e, 0.12f);
            hit(e, a, 4f);
            h.assertFalse(Meeting.yielding(e), "to the death: no backing down at 12%");
            h.assertTrue(GiantsFight.yieldNow(e, a, false, 1f, 100f, 0.25f) == Boolean.FALSE, "never");
            all(h, "\"all.wildFightTo\":\"flee\"");
            health(e, 0.55f);
            hit(e, b, 4f);
            h.assertFalse(Meeting.yielding(e), "flee: not above half");
        });
        // (one giant's blows land once a tick)
        later(h, 7, e, others, () -> {
            health(e, 0.48f);
            hit(e, b, 4f);
            h.assertTrue(Meeting.yielding(e), "flee: he flies off below half");
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_ownedto")
    public void anOwnersFightBacksDownOnlyWhenTheWorldSays(GameTestHelper h) {
        HollowbellEntity e = free(h, 1802);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus");
        Entity[] others = {a};
        later(h, 5, e, others, () -> {
            all(h, "");
            e.sendAfter(a);
            h.assertTrue(e.flee().playerFight(a), "a book order is an owner's fight");
            health(e, 0.15f);
            hit(e, a, 4f);
            h.assertFalse(Meeting.yielding(e), "as shipped: an owner's fight goes on");
            all(h, "\"all.ownedFightTo\":\"back_down\"");
        });
        later(h, 7, e, others, () -> {
            hit(e, a, 4f);
            h.assertTrue(Meeting.yielding(e), "back_down: he backs down at his Backs-down-at");
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_damage")
    public void giantDamageAndLoot(GameTestHelper h) {
        HollowbellEntity e = free(h, 1803);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus");
        Entity[] others = {a};
        float[] plainHit = {0f};
        later(h, 5, e, others, () -> {
            DamageSource src = e.damageSources().mobAttack(a);
            float hp = e.healthNow(), max = e.healthMax();
            all(h, "\"all.wildDamage\":2,\"all.ownedDamage\":0.5");
            h.assertTrue(Math.abs(GiantsFight.taken(e, src, a, 10f, hp, max, false, 0.25f) - 20f) < 1e-4, "wild x2");
            h.assertTrue(Math.abs(GiantsFight.taken(e, src, a, 10f, hp, max, true, 0.25f) - 5f) < 1e-4, "owned x0.5");
            h.assertTrue(GiantsFight.taken(e, e.damageSources().generic(), null, 10f, hp, max, false, 0.25f) == 10f,
                    "not a giant's blow: untouched");
            // a real blow lands twice as hard
            all(h, "");
            float before = e.healthNow();
            hit(e, a, 6f);
            plainHit[0] = before - e.healthNow();
            all(h, "\"all.wildDamage\":2");
        });
        later(h, 7, e, others, () -> {
            float before = e.healthNow();
            hit(e, a, 6f);
            float twice = before - e.healthNow(), plain = plainHit[0];
            h.assertTrue(plain > 0 && Math.abs(twice - plain * 2) < plain * 0.05 + 0.01, plain + " then " + twice);
            DamageSource src = e.damageSources().mobAttack(a);
            float hp = e.healthNow(), max = e.healthMax();
            // loot off: a giant's killing blow takes his drops away
            all(h, "\"all.wildLoot\":false");
            GiantsFight.taken(e, src, a, hp * 3, hp, max, false, 0.25f);
            h.assertTrue(e.getTags().contains(GiantsFight.NO_DROPS), "no drops after a giant's killing blow");
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_owned")
    public void ownedGiantsWhoMayNotKillStopAtTheBreakPoint(GameTestHelper h) {
        HollowbellEntity e = free(h, 1804);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus");
        Entity[] others = {a};
        later(h, 5, e, others, () -> {
            all(h, "\"all.ownedMayKill\":false");
            DamageSource src = e.damageSources().mobAttack(a);
            float max = e.healthMax();
            // wild giants: no floor
            h.assertTrue(GiantsFight.taken(e, src, a, max, max * 0.5f, max, true, 0.25f) == max, "wild: all of it");
            e.addTag(GiantsFight.OWNER + UUID.randomUUID());
            a.addTag(GiantsFight.OWNER + UUID.randomUUID());
            float d = GiantsFight.taken(e, src, a, max, max * 0.5f, max, true, 0.25f);
            h.assertTrue(Math.abs(d - max * 0.25f) < 1e-3, "owned: down to his Backs-down-at only: " + d);
            h.assertTrue(GiantsFight.yieldNow(e, a, true, max * 0.25f, max, 0.25f) == Boolean.TRUE, "and backs down");
            e.sendAfter(a);
            health(e, 0.3f);
            hit(e, a, max);
            h.assertTrue(e.healthNow() > 0 && e.isAlive() && Meeting.yielding(e), "he lived and backed down: "
                    + e.healthNow());
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_tags")
    public void aStagedFightsTags(GameTestHelper h) {
        HollowbellEntity e = free(h, 1805);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus"), mate = standIn(h, e, -30, "furrowmaw");
        Mob mateHelper = helperOf(h, e, 30, mate), foeHelper = helperOf(h, e, -30, a);
        Entity[] others = {a, mate, mateHelper, foeHelper};
        float[] plain = {0f};
        later(h, 5, e, others, () -> {
            all(h, "");
            DamageSource src = e.damageSources().mobAttack(a);
            float max = e.healthMax();
            e.addTag(GiantsFight.SCRIPT);
            h.assertTrue(GiantsFight.active(e), "tagged: active");
            h.assertTrue(GiantsFight.yieldNow(e, a, false, 1f, max, 0.25f) == Boolean.FALSE, "no backing down");
            health(e, 0.1f);
            hit(e, a, 4f);
            h.assertFalse(Meeting.yielding(e), "a staged fight's giant didn't hold on");
            e.addTag(GiantsFight.TAKEN + "0.5");
            h.assertTrue(Math.abs(GiantsFight.taken(e, src, a, 10f, max, max, false, 0.25f) - 5f) < 1e-4, "paced");
            e.addTag(GiantsFight.FLOOR + "0.4");
            float d = GiantsFight.taken(e, src, a, max * 4f, max * 0.5f, max, false, 0.25f);
            h.assertTrue(Math.abs(d - max * 0.1f) < 1e-3, "the floor: " + d);
            h.assertTrue(GiantsFight.taken(e, e.damageSources().generic(), null, max, max * 0.3f, max, false, 0.25f)
                    == 0f, "the floor holds against anything");
            e.addTag(GiantsFight.TEAM + "a");
            mate.addTag(GiantsFight.TEAM + "a");
            h.assertTrue(GiantsFight.taken(e, e.damageSources().mobAttack(mate), mate, 9f, max, max, false, 0.25f)
                    == 0f, "nothing from his own side");
            // a helper's blow is its giant's: his team mate's helper does nothing, a foe's helper is paced
            for (String t : List.copyOf(e.getTags()))
                if (t.startsWith(GiantsFight.FLOOR) || t.startsWith(GiantsFight.TAKEN)) e.removeTag(t);
            health(e, 0.8f);
            float before = e.healthNow();
            hit(e, mateHelper, 6f);
            h.assertTrue(e.healthNow() == before, "his team mate's helper's blow did something: "
                    + (before - e.healthNow()));
            hit(e, foeHelper, 6f);
            plain[0] = before - e.healthNow();
            h.assertTrue(plain[0] > 0, "a foe's helper's blow lands");
            e.addTag(GiantsFight.TAKEN + "2");
            e.addTag(GiantsFight.MOVES + "strand_lash,sky_dive");
            h.assertTrue(GiantsFight.moveOk(e, "sky_dive") && !GiantsFight.moveOk(e, "deep_toll"), "his moves");
            int[] w = e.moves().weights(a, null);
            for (int m = 1; m < w.length; m++)
                h.assertTrue(w[m] == 0 || m == Moves.LASH || m == Moves.SKY_DIVE, "a move not on his list: "
                        + Moves.NAMES[m]);
        });
        later(h, 7, e, others, () -> {
            float before = e.healthNow();
            hit(e, foeHelper, 6f);
            float twice = before - e.healthNow();
            h.assertTrue(Math.abs(twice - plain[0] * 2) < plain[0] * 0.05 + 0.01, "a helper's blow paced like its "
                    + "giant's: " + plain[0] + " then " + twice);
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "fight_act")
    public void fightactDoesWhatTheGuideAsks(GameTestHelper h) {
        HollowbellEntity e = free(h, 1806);
        Mob a = standIn(h, e, 40, "fire_ice_cerberus");
        MinecraftServer s = h.getLevel().getServer();
        String foe = "\"foe\":\"" + a.getUUID() + "\"";
        Belling[] bell = new Belling[1];
        Entity[] others = {a, null};
        later(h, 5, e, others, () -> {
            all(h, "");
            List<String> r = act(s, e.getUUID(), "hp", "");
            h.assertTrue(r.get(0).equals("ok") && r.get(1).contains("\"max\""), "hp: " + r);
            r = act(s, e.getUUID(), "health", "\"share\":0.6,\"set\":true");
            h.assertTrue(r.get(0).equals("ok") && Math.abs(e.healthNow() / e.healthMax() - 0.6f) < 0.01,
                    "health set: " + r);
            r = act(s, e.getUUID(), "health", "\"share\":0.1");
            h.assertTrue(Math.abs(e.healthNow() / e.healthMax() - 0.7f) < 0.01, "healed: " + r);
            r = act(s, e.getUUID(), "fight", foe);
            h.assertTrue(r.get(0).equals("ok") && a.getUUID().equals(e.meetFoe()), "fight: " + r);
            r = act(s, e.getUUID(), "yield", "\"by\":\"" + a.getUUID() + "\",\"how\":\"bow\"");
            h.assertTrue(r.get(0).equals("ok") && Meeting.yielding(e) && !e.flee().running()
                    && e.flee().resting(), "bow: backed down, settles and rests: " + r);
            r = act(s, e.getUUID(), "fight", foe + ",\"wild\":true");
            h.assertTrue(r.get(0).equals("ok") && !Meeting.yielding(e) && !e.flee().resting()
                    && !e.flee().playerFight(a), "a fresh round, taken up as a wild fight: " + r);
            r = act(s, e.getUUID(), "fight", foe);
            h.assertTrue(r.get(0).equals("ok") && e.flee().playerFight(a), "an owner's fight: " + r);
            r = act(s, e.getUUID(), "yield", "\"by\":\"" + a.getUUID() + "\",\"how\":\"run\"");
            h.assertTrue(Meeting.yielding(e) && e.flee().running(), "run: backed down and flying off: " + r);
            r = act(s, e.getUUID(), "yield", "\"how\":\"run\"");
            h.assertTrue(r.get(0).equals("no"), "nobody to back down from: " + r);
        });
        later(h, 10, e, others, () -> {
            // he chases a loser down: the cow backs down, he goes for it all the same
            act(s, e.getUUID(), "fight", foe);
            a.addTag(Meeting.YIELD);
            h.assertFalse(e.fairGame(a), "a backed-down one is off limits");
            List<String> r = act(s, e.getUUID(), "chase", foe);
            h.assertTrue(r.get(0).equals("ok") && GiantsFight.chasing(e, a) && e.fairGame(a), "chase: " + r);
        });
        later(h, 40, e, others, () -> {
            h.assertTrue(e.getTarget() == a, "he went after the one he chases: " + e.getTarget());
            List<String> r = act(s, e.getUUID(), "do", "\"move\":\"strand_lash\"," + foe);
            h.assertTrue(r != null && r.size() == 2, "do answers: " + r);
            r = act(s, e.getUUID(), "do", "\"move\":\"moonwalk\"");
            h.assertTrue(r.get(0).equals("no"), "no such move: " + r);
            r = act(s, e.getUUID(), "roar", "");
            h.assertTrue(r.get(0).equals("ok"), "roar: " + r);
            // one of his Bellings on the giant he's after: it is called off with him
            a.removeTag(Meeting.YIELD);
            bell[0] = ModEntities.BELLING.create(h.getLevel());
            bell[0].moveTo(e.getX() + 10, e.getY() + 5, e.getZ(), 0f, 0f);
            bell[0].addTag(Giants.OF + e.getUUID());
            h.getLevel().addFreshEntity(bell[0]);
            others[1] = bell[0];
            bell[0].setTarget(a);
            h.assertTrue(bell[0].getTarget() == a, "his Belling is on it");
            r = act(s, e.getUUID(), "stop", "");
            h.assertTrue(r.get(0).equals("ok") && !GiantsFight.chasing(e, a) && e.getTarget() == null
                    && e.meetFoe() == null && e.huntedByOrder() == null && !e.holdsGrudge(a), "stood down: " + r);
            h.assertTrue(bell[0].getTarget() == null, "his Belling was called off: " + bell[0].getTarget());
            finish(h, e, others);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "fight_ask")
    public void withoutTheGuideNobodyIsAsked(GameTestHelper h) {
        HollowbellEntity e = free(h, 1807);
        Mob a = standIn(h, e, 30, "fire_ice_cerberus");
        Entity[] others = {a};
        later(h, 5, e, others, () -> {
            // (the Guide isn't in this test world: asking finds nobody, and the fight goes ahead)
            all(h, "\"all.fightAsk\":true,\"all.fightReport\":true");
            h.assertTrue(e.startMeeting(a, true), "no Guide to ask: the fight starts");
            h.assertTrue(GiantsFight.stateForTests().contains(",false,false,"), "stopped asking: "
                    + GiantsFight.stateForTests());
            finish(h, e, others);
        });
    }
}
