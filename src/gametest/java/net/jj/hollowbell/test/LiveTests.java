package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.S;
import static net.jj.hollowbell.test.HollowbellGameTests.drop;
import static net.jj.hollowbell.test.HollowbellGameTests.player;
import static net.jj.hollowbell.test.HollowbellGameTests.release;
import static net.jj.hollowbell.test.HollowbellGameTests.spawnAway;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.FarOrders;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Giants Guide's live control over his bridge (ADMIN_BRIDGE.md, "Live control"): "live" lists every one of him
 * with where he is and how he is; "liveact" does one thing to one of them, found by his id, and nothing to the others.
 */
public class LiveTests implements FabricGameTest {

    static JsonObject row(MinecraftServer s, UUID id) {
        List<String> lines = GiantsBridge.giants(s, "live", "1");
        for (String l : lines.subList(1, lines.size())) {
            JsonObject o = JsonParser.parseString(l).getAsJsonObject();
            if (o.get("id").getAsString().equals(id.toString())) return o;
        }
        return null;
    }

    static List<String> act(MinecraftServer s, UUID id, String what, String value, String by) {
        JsonObject o = new JsonObject();
        o.addProperty("live", 1);
        o.addProperty("id", id.toString());
        o.addProperty("do", what);
        o.addProperty("value", value);
        o.addProperty("by", by);
        return GiantsBridge.giants(s, "liveact", o.toString());
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "live_list")
    public void liveListsEveryOneOfHimAsHeIs(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1310);
        HollowbellEntity f = spawnAway(h, S, HollowbellEntity.HUNTER, 1311);
        h.runAfterDelay(30, () -> {
            List<String> lines = GiantsBridge.giants(s, "live", "1");
            JsonObject head = JsonParser.parseString(lines.get(0)).getAsJsonObject();
            h.assertTrue(head.get("live").getAsInt() == 1 && head.get("name").getAsString().equals("Hollowbell")
                    && head.get("giant").getAsString().equals("hollowbell"), "the header: " + head);
            h.assertTrue(head.getAsJsonArray("does").size() == 11, "what he can do: " + head);
            h.assertTrue(head.getAsJsonArray("moods").get(1).getAsString().equals("hunting"), "his moods: " + head);
            h.assertTrue(Math.abs(head.getAsJsonArray("size").get(0).getAsDouble() - 0.03) < 1e-6
                    && Math.abs(head.getAsJsonArray("size").get(1).getAsDouble() - 2) < 1e-6, "his sizes: " + head);
            JsonObject a = row(s, e.getUUID()), b = row(s, f.getUUID());
            h.assertTrue(a != null && b != null, "both listed: " + lines);
            h.assertTrue(a.get("state").getAsString().equals("here"), "in the world");
            h.assertTrue(Math.abs(a.get("x").getAsDouble() - e.getX()) < 0.01
                    && Math.abs(a.get("z").getAsDouble() - e.getZ()) < 0.01, "where he is: " + a);
            h.assertTrue(a.get("dim").getAsString().equals("minecraft:overworld"), "the dimension");
            h.assertTrue(Math.abs(a.get("health").getAsDouble() - e.healthNow()) < 0.01
                    && Math.abs(a.get("max").getAsDouble() - e.healthMax()) < 0.01, "health: " + a);
            h.assertTrue(Math.abs(a.get("size").getAsDouble() - e.bellScale()) < 0.01, "size: " + a);
            h.assertTrue(a.get("mood").getAsString().equals("calm") && b.get("mood").getAsString().equals("hunting"),
                    "moods: " + a + " " + b);
            h.assertTrue(!a.get("asleep").getAsBoolean(), "awake");
            h.assertTrue(!a.get("own").getAsBoolean(), "not the world's own");
            // out of the world: still listed, with where the sum is
            UUID fid = f.getUUID();
            h.assertTrue(f.stepAside(), "he didn't step out of the world");
            JsonObject away = row(s, fid);
            h.assertTrue(away != null && away.get("state").getAsString().equals("away"), "away: " + away);
            h.assertTrue(away.get("mood").getAsString().equals("hunting"), "his mood kept out there");
            h.assertTrue(Math.abs(away.get("size").getAsDouble() - S) < 0.01, "his size kept out there");
            // an action he can't do out there says so
            List<String> r = act(s, fid, "heal", "", "-");
            h.assertTrue(r.get(0).equals("no") && r.get(1).contains("out of the world"), "" + r);
            Away.get(s).forget(fid);
            h.assertTrue(row(s, fid) == null, "gone from the list");
            // nonsense
            h.assertTrue(GiantsBridge.giants(s, "live", "2") == null, "a newer list");
            h.assertTrue(GiantsBridge.giants(s, "liveact", "not json") == null, "unreadable");
            h.assertTrue(act(s, e.getUUID(), "explode", "", "-") == null, "an unknown action");
            h.assertTrue(GiantsBridge.giants(s, "liveact", "{\"live\":2,\"id\":\"" + e.getUUID() + "\",\"do\":\"heal\"}")
                    == null, "a newer protocol");
            r = act(s, UUID.randomUUID(), "heal", "", "-");
            h.assertTrue(r.equals(List.of("no", "He isn't there any more.")), "nobody by that id: " + r);
            release(h, e);
            release(h, f);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "live_act")
    public void eachActionDoesWhatItSaysToThatOneOnly(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1312);
        HollowbellEntity other = spawnAway(h, S, HollowbellEntity.CALM, 1313);
        h.runAfterDelay(30, () -> {
            UUID id = e.getUUID();
            // heal
            e.hurtBy(e.healthMax() / 2);
            other.hurtBy(other.healthMax() / 2);
            List<String> r = act(s, id, "heal", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: healed.")), "" + r);
            h.assertTrue(e.healthNow() >= e.healthMax() - 0.01, "healed: " + e.healthNow());
            h.assertTrue(other.healthNow() < other.healthMax() - 1, "the other one wasn't");
            // size
            r = act(s, id, "size", "0.2", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: size 0.2.")) && Math.abs(e.bellScale() - 0.2f) < 1e-4,
                    "size: " + r);
            h.assertTrue(Math.abs(other.bellScale() - S) < 1e-4, "the other one's size stays");
            r = act(s, id, "size", "9", "-");
            h.assertTrue(r.equals(List.of("no", "Size has to be a number from 0.03 to 2.")), "too big: " + r);
            h.assertTrue(act(s, id, "size", "big", "-").get(0).equals("no"), "not a number");
            // mood: hunting, then calm (grudges gone)
            e.setVariant(HollowbellEntity.HUNTER);
            other.setVariant(HollowbellEntity.HUNTER);
            r = act(s, id, "calm", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: calmed, grudges forgotten.")) && !e.isHunter(), "" + r);
            h.assertTrue(other.isHunter(), "the other one still hunting");
            r = act(s, id, "forgive", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: grudges forgotten.")), "forgive: " + r);
            // sleep and wake
            r = act(s, id, "sleep", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: asleep.")) && e.asleep(), "sleep: " + r);
            h.assertTrue(!other.asleep(), "the other one awake");
            r = act(s, id, "sleep", "", "-");
            h.assertTrue(r.get(0).equals("ok") && e.asleep(), "sleep never wakes him");
            r = act(s, id, "wake", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: awake.")) && !e.asleep(), "awake: " + r);
            // the owner: an online player gets his book and holds it
            ServerPlayer alex = player(h, e.position().add(30, 0, 0));
            r = act(s, id, "owner", alex.getUUID().toString(), "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: " + alex.getGameProfile().getName()
                    + " has his book now.")), "owner: " + r);
            h.assertTrue(alex.getUUID().equals(BookRule.get(s).holder()), "she holds it");
            h.assertTrue(alex.getInventory().items.stream().anyMatch(st -> st.is(ModItems.CODEX)),
                    "the book in her pack");
            h.assertTrue(row(s, id).get("owner").getAsString().equals(alex.getGameProfile().getName())
                    && row(s, id).get("ownerId").getAsString().equals(alex.getUUID().toString()), "listed");
            r = act(s, id, "owner", UUID.randomUUID().toString(), "-");
            h.assertTrue(r.equals(List.of("no", "Pick a player who is online.")), "offline: " + r);
            // teleport: the player who asked goes to him
            alex.teleportTo(h.getLevel(), e.getX() + 200, e.getY(), e.getZ(), 0, 0);
            r = act(s, id, "tp", "", alex.getUUID().toString());
            h.assertTrue(r.get(0).equals("ok"), "tp: " + r);
            h.assertTrue(alex.position().distanceTo(e.position()) < 80, "beside him: " + alex.position());
            h.assertTrue(act(s, id, "tp", "", "-").get(0).equals("no"), "nobody to take");
            BookRule.get(s).free();
            drop(alex);
            // home: no ground in a test world, or on his way there
            r = act(s, id, "home", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Hollowbell: on his way home."))
                    || r.equals(List.of("no", "Hollowbell: he has no ground yet.")), "home: " + r);
            // remove: that one only
            r = act(s, id, "remove", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Removed 1 Hollowbell.")) && e.isRemoved(), "removed: " + r);
            h.assertTrue(!other.isRemoved(), "the other one stays");
            h.assertTrue(row(s, id) == null, "gone from the list");
            // kill: that one
            r = act(s, other.getUUID(), "kill", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Killed 1 Hollowbell.")), "kill: " + r);
            h.assertTrue(other.isDeadOrDying() || other.isRemoved(), "dead");
        });
        h.runAfterDelay(90, () -> {
            release(h, e);
            release(h, other);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "live_away")
    public void killRemoveAndTeleportReachOnesOutOfTheWorld(GameTestHelper h) {
        MinecraftServer s = h.getLevel().getServer();
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 1314);
        HollowbellEntity f = spawnAway(h, S, HollowbellEntity.CALM, 1315);
        h.runAfterDelay(30, () -> {
            UUID a = e.getUUID(), b = f.getUUID();
            Vec3 fAt = f.position();
            h.assertTrue(e.stepAside() && f.stepAside(), "both stepped out");
            Away away = Away.get(s);
            List<String> r = act(s, a, "remove", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Removed 1 Hollowbell.")) && away.get(a) == null,
                    "removed from out there: " + r);
            h.assertTrue(away.get(b) != null, "the other one is still out there");
            r = act(s, b, "size", "0.5", "-");
            h.assertTrue(r.equals(List.of("no", "He's out of the world right now. Go to him first (Teleport).")),
                    "size out there: " + r);
            ServerPlayer alex = player(h, fAt.add(0, 0, 900));
            r = act(s, b, "tp", "", alex.getUUID().toString());
            h.assertTrue(r.get(0).equals("ok"), "tp to one out of the world: " + r);
            h.assertTrue(alex.position().distanceTo(fAt) < 80, "near where the sum is: " + alex.position());
            drop(alex);
            r = act(s, b, "kill", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Killed 1 Hollowbell.")), "kill: " + r);
            h.assertTrue(away.get(b) == null, "not out there any more");
            // one written down lying in land nobody has loaded: listed, and reached
            UUID lying = UUID.randomUUID();
            away.noteParked(lying, "minecraft:overworld", fAt.x + 5000, fAt.z, S);
            JsonObject row = row(s, lying);
            h.assertTrue(row != null && row.get("state").getAsString().equals("unloaded"), "lying: " + row);
            r = act(s, lying, "heal", "", "-");
            h.assertTrue(r.get(0).equals("no"), "heal needs him in the world: " + r);
            r = act(s, lying, "remove", "", "-");
            h.assertTrue(r.equals(List.of("ok", "Removed 1 Hollowbell.")), "remove one lying: " + r);
            h.assertTrue(FarOrders.fetching() > 0, "fetched to be taken away");
            FarOrders.forget();
            away.unpark(lying);
        });
        h.runAfterDelay(80, () -> {
            for (var x : h.getLevel().getEntities(ModEntities.HOLLOWBELL,
                    new AABB(f.position(), f.position()).inflate(300), x -> !x.isDeadOrDying())) x.discard();
            release(h, e);
            release(h, f);
            h.succeed();
        });
    }
}
