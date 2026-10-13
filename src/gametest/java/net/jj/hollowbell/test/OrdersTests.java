package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.command.GiantsCommand;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.FinderItem;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.BellWorld;
import net.jj.hollowbell.world.FarOrders;
import net.jj.hollowbell.world.NoWait;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.TakeMe;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Sleeping, the book and the safe list, orders, tp and the commands. */
public class OrdersTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "safe")
    public void theBookAndTheSafeList(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 50);
        ServerPlayer[] pl = new ServerPlayer[2];
        h.runAfterDelay(20, () -> {
            pl[0] = player(h, e.position().add(3, 0, 3));
            pl[1] = player(h, e.position().add(-3, 0, 3));
            h.assertTrue(!e.spares(pl[0]) && !e.spares(pl[1]), "he spares players with no book about");
            pl[0].getInventory().add(new ItemStack(ModItems.CODEX));
            h.assertTrue(e.spares(pl[0]), "he doesn't spare the one holding the book");
            h.assertTrue(!e.spares(pl[1]), "the other player is spared without being on the list");
            BellWorld.get(h.getLevel().getServer()).toggleFriend(pl[0].getUUID(), pl[1].getUUID());
            h.assertTrue(e.spares(pl[1]), "the safe list didn't protect the other player");
            pl[0].getInventory().clearContent();
            h.assertTrue(!e.spares(pl[1]), "the list still counts with the book put down");
            BellWorld.get(h.getLevel().getServer()).dropFriend(pl[0].getUUID(), pl[1].getUUID());
            drop(pl[0]);
            drop(pl[1]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "drift")
    public void heDriftsWhereHeIsSent(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 62);
        Vec3[] start = new Vec3[1];
        h.runAfterDelay(20, () -> {
            start[0] = e.position();
            e.setGoal(e.position().add(25, 0, 0));
        });
        h.runAfterDelay(360, () -> {
            double moved = e.getX() - start[0].x;
            h.assertTrue(moved > 8, "he only drifted " + moved + " toward where he was sent");
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 800, batch = "area")
    public void keptToACircleHeStaysInIt(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 209);
        Vec3[] c = new Vec3[1];
        double[] d0 = new double[1];
        h.runAfterDelay(20, () -> {
            // the circle's middle 60 blocks off, 32 across: he is outside it, and walks back in
            c[0] = e.position().add(-60, 0, 0);
            e.bindTo(c[0].x, c[0].z, 32);
            h.assertTrue(e.bound() && e.boundRadius() == 32, "not bound");
            d0[0] = Math.hypot(e.getX() - c[0].x, e.getZ() - c[0].z);
            // told to go way past the edge, the place he aims for is inside
            e.setGoal(e.position().add(300, 0, 0));
            h.assertTrue(
                    e.goal() != null && Math.hypot(e.goal().x - c[0].x, e.goal().z - c[0].z) <= 32,
                    "sent past the edge: " + e.goal());
            e.setGoal(null);
        });
        h.runAfterDelay(320, () -> {
            double d1 = Math.hypot(e.getX() - c[0].x, e.getZ() - c[0].z);
            h.assertTrue(d1 < d0[0] - 1, "outside his circle he didn't head back in: " + d0[0] + " -> " + d1);
            // freed, he goes where he is sent
            e.unbind();
            Vec3 far = e.position().add(300, 0, 0);
            e.setGoal(far);
            h.assertTrue(e.goal() != null && Math.abs(e.goal().x - far.x) < 1, "freed, he's still held to the circle");
            // the command binds the nearest one, and saving keeps it
            run(h, e.position(), "hollowbell area " + (int) e.getX() + " " + (int) e.getZ() + " 64");
            h.assertTrue(e.bound() && e.boundRadius() == 64, "/hollowbell area didn't bind him");
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(b.bound() && b.boundRadius() == 64, "his circle didn't survive a save");
            b.discard();
            run(h, e.position(), "hollowbell area off");
            h.assertTrue(!e.bound(), "/hollowbell area off didn't free him");
            // the book: keep to here, then let him roam
            ServerPlayer p = player(h, e.position().add(4, 0, 4));
            p.getInventory().add(new ItemStack(ModItems.CODEX));
            CodexOrders.handle(p, new CodexPayload(CodexPayload.BIND_HERE, 75));
            h.assertTrue(
                    e.bound()
                            && e.boundRadius() == 75
                            && Math.hypot(e.boundCentre().x - p.getX(), e.boundCentre().z - p.getZ()) < 1,
                    "the book didn't keep him to the player's spot");
            CodexOrders.handle(p, new CodexPayload(CodexPayload.FREE_ROAM));
            h.assertTrue(!e.bound(), "the book didn't let him roam");
            // out of the world, a bound one's trip keeps to his circle too
            e.bindTo(e.getX(), e.getZ(), 100);
            UUID id = e.getUUID();
            Vec3 mid = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            var a = Away.get(h.getLevel().getServer());
            a.send(h.getLevel(), id, mid.add(5000, 0, 0));
            var r = a.get(id);
            h.assertTrue(
                    r != null && Math.hypot(r.toX - mid.x, r.toZ - mid.z) <= 100.5,
                    "out of the world he's sent past his circle");
            a.forget(id);
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "giants_cmd")
    public void theGiantsCommandReachesHim(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        h.assertTrue(GiantsCommand.elected(), "on his own, Hollowbell should run /giants");
        int was = BellConfig.V.maxInWorld;
        run(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))), "giants limit 2");
        h.assertTrue(BellConfig.V.maxInWorld == 2, "/giants limit 2 left the limit at " + BellConfig.V.maxInWorld);
        var lines = GiantsCommand.ask(server, "status", "");
        h.assertTrue(lines.stream().anyMatch(s -> s.startsWith("Hollowbell: ")), "no answer from Hollowbell: " + lines);
        h.assertTrue(GiantsBridge.giants(server, "nonsense", "") == null, "an unknown action should give null");
        h.assertTrue(parses(h, "giants where", 0), "/giants where needs cheats");
        h.assertTrue(!parses(h, "giants limit 2", 0), "/giants limit works without cheats");
        h.assertTrue(
                parses(h, "giants natural off", 2) && parses(h, "giants volume 1", 2),
                "/giants doesn't parse with cheats on");
        BellConfig.V.maxInWorld = was;
        BellConfig.save();
        h.succeed();
    }

    /** a player's own command source (for the commands that act on the one running them) */
    private static CommandSourceStack as(ServerPlayer p) {
        return p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
    }

    private static void runAs(ServerPlayer p, String cmd) {
        p.server.getCommands().performPrefixedCommand(as(p), cmd);
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 700, batch = "sleep_quiet")
    public void heSleepsWhenLeftAloneAndWakesWhenHit(GameTestHelper h) {
        int keep = HollowbellEntity.quietBeforeSleep;
        HollowbellEntity.quietBeforeSleep = 40;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 300);
        h.runAfterDelay(5, () -> h.assertTrue(!e.asleep(), "he started asleep"));
        h.runAfterDelay(160, () -> {
            h.assertTrue(e.asleep(), "left alone, he didn't fall asleep");
            h.assertTrue(e.sleepiness() > 0.5f, "asleep but not settling: " + e.sleepiness());
            h.assertTrue(CodexOrders.doing(e).equals("asleep"), "the book says he's " + CodexOrders.doing(e));
            // it's kept in his save
            var tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity copy = ModEntities.HOLLOWBELL.create(h.getLevel());
            copy.load(tag);
            h.assertTrue(copy.asleep() && copy.sleepiness() > 0.5f, "sleep wasn't saved");
            copy.discard();
            // a hit wakes him
            ServerPlayer p = player(h, e.position().add(0, 2, 0));
            e.hurt(e.damageSources().playerAttack(p), 5f);
            h.assertTrue(!e.asleep(), "a hit didn't wake him");
            drop(p);
        });
        // and he gets up over a couple of seconds, not all at once
        h.runAfterDelay(165, () -> h.assertTrue(e.sleepiness() > 0.3f, "he woke up in a blink"));
        h.runAfterDelay(260, () -> {
            h.assertTrue(e.sleepiness() == 0f, "he never finished getting up: " + e.sleepiness());
            h.assertTrue(!e.asleep(), "he fell straight back asleep after being hit");
            HollowbellEntity.quietBeforeSleep = keep;
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "sleep_hunter")
    public void aSleepingHunterWakesWhenYouComeNear(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 301);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(10, () -> {
            e.goToSleep();
            p[0] = player(h, e.position().add(150, 2, 0));
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(e.asleep(), "he woke with nobody near");
            p[0].teleportTo(e.getX() + 32 * S + 4, e.getY(), e.getZ());
        });
        h.runAfterDelay(80, () -> {
            h.assertTrue(!e.asleep(), "a hunter slept on with somebody right beside him");
            drop(p[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "sleep_hitbox")
    public void aSleepingOnesHitboxesAreWhereHeLies(GameTestHelper h) {
        float sc = 0.3f;
        HollowbellEntity e = spawnAway(h, sc, HollowbellEntity.CALM, 302);
        Vec3[] awake = new Vec3[1];
        h.runAfterDelay(60, () -> {
            e.setStay(true);
            e.ensurePose();
            awake[0] = e.crownWorld().subtract(e.position());
            e.goToSleep();
        });
        h.runAfterDelay(360, () -> {
            e.ensurePose();
            Vec3 crown = e.crownWorld();
            Vec3 rel = crown.subtract(e.position());
            h.assertTrue(e.asleep() && e.sleepiness() >= 0.99f, "he isn't fast asleep");
            h.assertTrue(rel.distanceTo(awake[0]) > 0.5, "asleep, his body is posed just as when awake");
            // a player standing right over his crown where it's drawn asleep, swinging down at it, lands the blow
            ServerPlayer p = player(h, crown.add(0.2, 2.5 - 1.62, 0.2));
            p.setYRot(0f);
            p.setXRot(90f);
            p.setYHeadRot(0f);
            float before = e.healthNow();
            h.assertTrue(
                    e.raycast(p.getEyePosition(), p.getViewVector(1f), 6) != null,
                    "nothing of him where he's drawn asleep");
            e.hitBy(p, -1);
            h.assertTrue(e.healthNow() < before, "a swing at his sleeping body didn't land");
            h.assertTrue(!e.asleep(), "the blow didn't wake him");
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "sleep_orders")
    public void theCommandAndTheBookPutHimToSleep(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 303);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(10, () -> {
            run(h, e.position(), "hollowbell sleep");
            h.assertTrue(e.asleep(), "/hollowbell sleep didn't put him to sleep");
            run(h, e.position(), "hollowbell sleep");
            h.assertTrue(!e.asleep(), "/hollowbell sleep again didn't wake him");
            p[0] = player(h, e.position().add(20, 2, 0));
            p[0].getInventory().add(new ItemStack(ModItems.CODEX));
            float wind = e.mood().wind();
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.SLEEP));
            h.assertTrue(e.asleep(), "the book's Sleep didn't put him to sleep");
            h.assertTrue(e.mood().wind() < wind || !BellConfig.V.bookCosts, "sleep cost him no wind");
            // any other order wakes him
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.FORGIVE));
            h.assertTrue(!e.asleep(), "an order didn't wake him");
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.SLEEP));
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.SLEEP));
            h.assertTrue(!e.asleep(), "the book's Wake didn't wake him");
            drop(p[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "safe_mobs")
    public void creaturesOnTheSafeListAreLeftAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 304);
        ServerPlayer[] p = new ServerPlayer[1];
        Pig[] pigs = new Pig[2];
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            p[0] = player(h, e.position().add(40, 2, 0));
            p[0].getInventory().add(new ItemStack(ModItems.CODEX));
            pigs[0] = pig(h, under(e));
            pigs[1] = pig(h, under(e).add(1.5, 0, 0));
            var w = BellWorld.get(h.getLevel().getServer());
            CodexOrders.spareOne(p[0], pigs[0], true);
            h.assertTrue(
                    w.onList(p[0].getUUID(), pigs[0].getUUID()) && w.isMob(pigs[0].getUUID()),
                    "the pig isn't on the list");
            h.assertTrue(
                    !e.fairGame(pigs[0]) && e.fairGame(pigs[1]),
                    "the spared pig and the other one are treated the same");
            h.assertTrue(e.forceMove(Moves.PULSE, pigs[1]), "the pulse wouldn't start");
        });
        h.runAfterDelay(20 + Moves.length(Moves.PULSE) + 10, () -> {
            h.assertTrue(pigs[0].isAlive() && pigs[0].getHealth() >= pigs[0].getMaxHealth(), "the spared pig was hurt");
            h.assertTrue(
                    !pigs[1].isAlive() || pigs[1].getHealth() < pigs[1].getMaxHealth(), "the other pig wasn't hurt");
            // the whole kind: no pig at all
            Pig third = pig(h, under(e).add(-1.5, 0, 0));
            CodexOrders.spareKind(p[0], EntityType.PIG, true);
            h.assertTrue(!e.fairGame(third), "all pigs are spared, but this one isn't");
            // the ✕ takes them off again
            CodexOrders.dropFromSafeList(p[0], "kind:minecraft:pig");
            CodexOrders.dropFromSafeList(p[0], "who:" + pigs[0].getUUID());
            h.assertTrue(e.fairGame(pigs[0]) && e.fairGame(third), "taken off the list, they're still spared");
            // your own tamed animals, always
            var wolf = EntityType.WOLF.create(h.getLevel());
            wolf.moveTo(under(e).x + 2, under(e).y, under(e).z, 0, 0);
            wolf.setNoAi(true);
            h.getLevel().addFreshEntity(wolf);
            wolf.tame(p[0]);
            h.assertTrue(!e.fairGame(wolf), "the book holder's own wolf isn't safe");
            // and the list is kept
            var tag = BellWorld.get(h.getLevel().getServer())
                    .save(new CompoundTag(), h.getLevel().registryAccess());
            h.assertTrue(tag.contains("Names"), "the list isn't saved");
            wolf.discard();
            third.discard();
            for (Pig pg : pigs) if (pg.isAlive()) pg.discard();
            drop(p[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "safe_command")
    public void theSpareCommandAddsAndTakesOff(GameTestHelper h) {
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        ServerPlayer p = player(h, Vec3.atCenterOf(o));
        Pig pg = pig(h, Vec3.atCenterOf(o).add(2, 0, 0));
        var w = BellWorld.get(h.getLevel().getServer());
        runAs(p, "hollowbell spare add @e[type=minecraft:pig,limit=1,sort=nearest]");
        h.assertTrue(w.onList(p.getUUID(), pg.getUUID()), "/hollowbell spare add didn't put the pig on");
        runAs(p, "hollowbell spare add kind minecraft:cow");
        h.assertTrue(w.kindOnList(p.getUUID(), EntityType.COW), "/hollowbell spare add kind didn't put cows on");
        runAs(p, "hollowbell spare");
        runAs(p, "hollowbell spare remove kind minecraft:cow");
        runAs(p, "hollowbell spare remove @e[type=minecraft:pig,limit=1,sort=nearest]");
        h.assertTrue(
                !w.onList(p.getUUID(), pg.getUUID()) && !w.kindOnList(p.getUUID(), EntityType.COW),
                "/hollowbell spare remove didn't take them off");
        h.assertTrue(
                "Wolves".equals(BellWorld.plural("Wolf"))
                        && "Sheep".equals(BellWorld.plural("Sheep"))
                        && "Cows".equals(BellWorld.plural("Cow")),
                "the kinds' names come out wrong");
        pg.discard();
        drop(p);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "far_come")
    public void comeToMeReachesHimOutOfTheWorld(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 305);
        var a = Away.get(h.getLevel().getServer());
        ServerPlayer[] p = new ServerPlayer[1];
        UUID[] id = new UUID[1];
        Vec3[] first = new Vec3[1];
        h.runAfterDelay(20, () -> {
            id[0] = e.getUUID();
            h.assertTrue(e.stepAside(), "he wouldn't step out");
            var r = a.get(id[0]);
            p[0] = player(h, new Vec3(r.fromX + 3000, e.getY() + 2, r.fromZ));
            p[0].getInventory().add(new ItemStack(ModItems.CODEX));
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.COME));
            h.assertTrue(
                    r.going && Math.abs(r.toX - p[0].getX()) < 1 && Math.abs(r.toZ - p[0].getZ()) < 1,
                    "the trip isn't aimed at the player");
            h.assertTrue(p[0].getUUID().equals(r.follow) && r.hold, "he isn't coming to the player");
            first[0] = r.spot(h.getLevel().getGameTime());
            // the finder says he's coming
            var said = FinderItem.answer(h.getLevel(), p[0]);
            h.assertTrue(key(said).equals("message.hollowbell.coming"), "the finder said " + key(said));
        });
        h.runAfterDelay(80, () -> {
            var r = a.get(id[0]);
            Vec3 now = r.spot(h.getLevel().getGameTime());
            h.assertTrue(
                    now.distanceTo(p[0].position().multiply(1, 0, 1))
                            < first[0].distanceTo(p[0].position().multiply(1, 0, 1)) - 2,
                    "he isn't getting any nearer");
            // the player moves: ten seconds on, the trip is aimed at where they are now
            p[0].teleportTo(p[0].getX(), p[0].getY(), p[0].getZ() + 500);
        });
        h.runAfterDelay(300 - 10, () -> {
            var r = a.get(id[0]);
            h.assertTrue(
                    Math.abs(r.toZ - p[0].getZ()) < 1,
                    "the trip wasn't aimed again at the player (" + r.toZ + " vs " + p[0].getZ() + ")");
            // brought back near somebody, the order carries on
            HollowbellEntity back = a.bringBack(h.getLevel(), r);
            h.assertTrue(
                    back != null && p[0].getUUID().equals(back.comingTo()) && back.holdsThere() && back.goal() != null,
                    "the order was lost coming back");
            back.discard();
            drop(p[0]);
            h.getLevel().setChunkForced(0, 0, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "far_ward")
    public void aTripStopsAtAWokenCrown(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = WorldOne.get(server);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 306);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            e.stepAside();
            var a = Away.get(server);
            var r = a.get(id);
            int keep = BellConfig.V.wardBlocks;
            BellConfig.V.wardBlocks = 300;
            // a crown awake half way between him and where he's sent
            w.startWard(h.getLevel(), BlockPos.containing(r.fromX + 2000, 64, r.fromZ), 20000, 0);
            a.order(h.getLevel(), id, new Vec3(r.fromX + 4000, 0, r.fromZ), null);
            double edge = r.fromX + 2000 - WorldOne.wardRange();
            h.assertTrue(
                    r.toX <= edge + 1,
                    "the trip goes into the crown's circle: to " + (int) r.toX + ", edge " + (int) edge);
            h.assertTrue(r.toX > r.fromX + 1000, "the trip stopped far short of the crown");
            w.forgetWard();
            BellConfig.V.wardBlocks = keep;
            a.forget(id);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "far_parked")
    public void oneInUnloadedLandIsFetchedAsOneSum(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var a = Away.get(server);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 307);
        UUID id = e.getUUID();
        Vec3 at = e.position();
        ServerPlayer[] p = new ServerPlayer[1];
        boolean[] ordered = {false};
        // his chunks are let go: he's put away with them, and noted
        h.runAfterDelay(20, () -> {
            int x = e.getBlockX(), z = e.getBlockZ();
            for (int cx = (x >> 4) - 4; cx <= (x >> 4) + 4; cx++)
                for (int cz = (z >> 4) - 4; cz <= (z >> 4) + 4; cz++)
                    h.getLevel().setChunkForced(cx, cz, false);
        });
        for (int t = 40; t < 1100; t += 20) {
            h.runAfterDelay(t, () -> {
                if (!ordered[0]) {
                    if (!a.parked().containsKey(id) || h.getLevel().getEntity(id) != null) return;
                    ordered[0] = true;
                    p[0] = player(h, at.add(3000, 2, 0));
                    var target = new FarOrders.Target(FarOrders.Kind.PARKED, id, at.x, at.z, null);
                    var said = FarOrders.order(h.getLevel(), target, p[0].position(), p[0], 0f);
                    h.assertTrue(
                            said != null && key(said).equals("message.hollowbell.coming"),
                            "the order said " + (said == null ? "nothing" : key(said)));
                    return;
                }
                var r = a.get(id);
                if (r == null) return;
                // one sum, and no body of him left behind anywhere
                h.assertTrue(h.getLevel().getEntity(id) == null, "he's out as a sum and still standing in the world");
                h.assertTrue(
                        h.getLevel()
                                .getEntities(
                                        ModEntities.HOLLOWBELL, x -> x.getUUID().equals(id))
                                .isEmpty(),
                        "two of him");
                h.assertTrue(r.going && p[0].getUUID().equals(r.follow), "the fetched one isn't coming to the player");
                h.assertTrue(!a.parked().containsKey(id), "he's still noted as left in unloaded land");
                a.forget(id);
                drop(p[0]);
                h.succeed();
            });
        }
        h.runAfterDelay(
                1150,
                () -> h.fail("never fetched: ordered " + ordered[0] + ", parked "
                        + a.parked().containsKey(id) + ", in the world "
                        + (h.getLevel().getEntity(id) != null) + ", as a sum " + (a.get(id) != null) + ", fetching "
                        + FarOrders.fetching()));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "far_goto")
    public void gotoAndGiantsGotoSendHimAnywhere(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 308);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            e.stepAside();
            var a = Away.get(h.getLevel().getServer());
            var r = a.get(id);
            run(h, new Vec3(r.fromX, 80, r.fromZ), "hollowbell goto " + (int) (r.fromX + 1500) + " " + (int) r.fromZ);
            h.assertTrue(
                    r.going && Math.abs(r.toX - (int) (r.fromX + 1500)) < 2, "/hollowbell goto didn't send the sum");
            var lines =
                    GiantsCommand.ask(h.getLevel().getServer(), "goto", (int) r.fromX + " " + (int) (r.fromZ + 900));
            h.assertTrue(
                    lines.stream().anyMatch(s -> s.startsWith("Hollowbell") && s.contains("on the way")),
                    "/giants goto: " + lines);
            h.assertTrue(Math.abs(r.toZ - (int) (r.fromZ + 900)) < 2, "/giants goto didn't send him");
            a.forget(id);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tp_loaded")
    public void tpTakesYouToHimOnSafeGround(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 309);
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
            var said = TakeMe.tp(p, 0);
            h.assertTrue(key(said).equals("command.hollowbell.tp"), "tp said " + key(said));
            h.assertTrue(
                    !e.bodyBox().contains(p.position())
                            || Math.hypot(p.getX() - e.getX(), p.getZ() - e.getZ()) > e.bellRadius(),
                    "landed inside him");
            double d = Math.hypot(p.getX() - e.getX(), p.getZ() - e.getZ());
            h.assertTrue(
                    d > e.bellRadius() && d < e.bellRadius() * 1.05 + 20 * 0.3 + 3 + 40,
                    "landed " + (int) d + " blocks from him");
            var under = h.getLevel().getBlockState(p.blockPosition().below());
            h.assertTrue(
                    under.isFaceSturdy(h.getLevel(), p.blockPosition().below(), Direction.UP)
                            && under.getFluidState().isEmpty(),
                    "landed on " + under);
            h.assertTrue(h.getLevel().getBlockState(p.blockPosition()).isAir(), "landed inside a block");
            // needs cheats
            var d0 = h.getLevel().getServer().getCommands().getDispatcher();
            var noOp = h.getLevel()
                    .getServer()
                    .createCommandSourceStack()
                    .withLevel(h.getLevel())
                    .withPermission(0)
                    .withSuppressedOutput();
            h.assertTrue(d0.parse("hollowbell tp", noOp).getReader().canRead(), "/hollowbell tp works without cheats");
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    /** put away with his chunk where nobody was near: /giants where and tp still find him */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "tp_parked")
    public void tpAndWhereFindOneLyingUnloaded(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 362);
        ServerPlayer[] pp = new ServerPlayer[1];
        int[] at = new int[2];
        UUID[] ids = new UUID[1];
        h.runAfterDelay(20, () -> {
            var server = h.getLevel().getServer();
            var a = Away.get(server);
            ids[0] = e.getUUID();
            int lx = e.getBlockX(), lz = e.getBlockZ();
            at[0] = lx;
            at[1] = lz;
            ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
            pp[0] = p;
            release(h, e);
            a.noteParked(e); // as his chunk saving him away does
            var where = GiantsBridge.giants(server, "where", "");
            h.assertTrue(
                    where != null && where.stream().anyMatch(x -> x.contains("lying still near " + lx)),
                    "/giants where said " + where);
            var said = TakeMe.tp(p, 0);
            // (if the land beside him isn't loaded, you go the moment it is: the server never waits for it)
            h.assertTrue(
                    key(said).equals("command.hollowbell.tp_waiting")
                            || key(said).equals("command.hollowbell.tp_soon"),
                    "tp said " + key(said));
        });
        boolean[] done = {false};
        for (int t = 25; t < 1350; t += 5) {
            h.runAfterDelay(t, () -> {
                ServerPlayer p = pp[0];
                if (done[0] || p == null || NoWait.onTheWay(p)) return;
                done[0] = true;
                var server = h.getLevel().getServer();
                try {
                    double d = Math.hypot(p.getX() - at[0], p.getZ() - at[1]);
                    h.assertTrue(
                            d > 4 && d < 88 * 1.05 + 20 + 3 + 40, "landed " + (int) d + " blocks from where he lies");
                    var lines =
                            GiantsCommand.ask(server, "tp", p.getUUID().toString(), "net.jj.hollowbell.GiantsBridge");
                    h.assertTrue(
                            lines.stream()
                                    .anyMatch(x -> x.startsWith("Hollowbell")
                                            && (x.contains("Took you") || x.contains("in a moment"))),
                            "/giants tp: " + lines);
                } finally {
                    Away.get(server).unpark(ids[0]);
                    drop(p);
                }
                h.succeed();
            });
        }
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "tp_away")
    public void tpTakesYouToOneOutOfTheWorld(GameTestHelper h) {
        clearAll(h);
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 310);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            e.stepAside();
            var a = Away.get(h.getLevel().getServer());
            var r = a.get(id);
            ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
            // /giants tp routes to this mod
            var lines = GiantsCommand.ask(
                    h.getLevel().getServer(), "tp", p.getUUID().toString(), "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(
                    lines.stream().anyMatch(s -> s.startsWith("Hollowbell") && s.contains("Took you")),
                    "/giants tp: " + lines);
            Vec3 s = r.spot(h.getLevel().getGameTime());
            double d = Math.hypot(p.getX() - s.x, p.getZ() - s.z);
            h.assertTrue(d < 88 * S * 1.05 + 20 * S + 3 + 40, "landed " + (int) d + " blocks from where he is");
            a.forget(id);
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "commands_standard")
    public void theCommandsTakeTheSameFormsAsTheOtherGiants(GameTestHelper h) {
        var d = h.getLevel().getServer().getCommands().getDispatcher();
        var op = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withPermission(2)
                .withSuppressedOutput();
        for (String c : new String[] {
            "hollowbell bossbar",
            "hollowbell bossbar off",
            "hollowbell bossbar on",
            "hollowbell bossbar 400",
            "hollowbell volume",
            "hollowbell volume off",
            "hollowbell volume 1.5",
            "hollowbell health",
            "hollowbell health 8000",
            "hollowbell damage",
            "hollowbell damage 2",
            "hollowbell damage mobs",
            "hollowbell damage mobs 3",
            "hollowbell griefing",
            "hollowbell griefing on",
            "hollowbell griefing false",
            "hollowbell shake",
            "hollowbell shake off",
            "hollowbell stay",
            "hollowbell stay on",
            "hollowbell stay true",
            "hollowbell sethealth 100",
            "hollowbell sleep",
            "hollowbell spare",
            "hollowbell spare add kind minecraft:cow",
            "hollowbell come",
            "hollowbell tp",
            "hollowbell tp 2",
            "hollowbell ground new",
            "hollowbell summon",
            "hollowbell summon hunting 0.5",
            "hollowbell where",
            "giants goto 10 10",
            "giants tp hollowbell"
        }) {
            var p = d.parse(c, op);
            h.assertTrue(!p.getReader().canRead() && p.getExceptions().isEmpty(), "/" + c + " doesn't parse");
        }
        var nobody = h.getLevel()
                .getServer()
                .createCommandSourceStack()
                .withLevel(h.getLevel())
                .withPermission(0)
                .withSuppressedOutput();
        h.assertTrue(
                !d.parse("hollowbell where", nobody).getReader().canRead(),
                "/hollowbell where should work without cheats");
        // the answers are words from the lang file, never English written in the code
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        for (String c : new String[] {
            "hollowbell ward",
            "hollowbell ground",
            "hollowbell natural",
            "hollowbell limit",
            "hollowbell bossbar",
            "hollowbell volume",
            "hollowbell griefing",
            "hollowbell area"
        }) {
            for (var line : said(h, at, c))
                h.assertTrue(
                        line.getContents() instanceof TranslatableContents
                                || line.getSiblings().stream()
                                        .anyMatch(x -> x.getContents() instanceof TranslatableContents),
                        "/" + c + " answered in plain English: " + line.getString());
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "config_bad")
    public void aBrokenSettingsFileIsNeverWrittenOver(GameTestHelper h) {
        var file = FabricLoader.getInstance().getConfigDir().resolve("hollowbell.json");
        String keep;
        try {
            keep = Files.readString(file);
        } catch (IOException ex) {
            h.fail("no settings file: " + ex);
            return;
        }
        var was = BellConfig.V;
        try {
            Files.writeString(file, "{ this is not json");
            h.assertTrue(!BellConfig.load(), "a broken file read as fine");
            h.assertTrue(BellConfig.locked(), "a broken file isn't guarded");
            BellConfig.V.soundVolume = 0.3f;
            BellConfig.save();
            h.assertTrue(Files.readString(file).equals("{ this is not json"), "the broken file was written over");
            h.assertTrue(Files.exists(file.resolveSibling("hollowbell.json.bad")), "no .bad copy was kept");
            Files.writeString(file, keep);
            h.assertTrue(BellConfig.load() && !BellConfig.locked(), "a good file after a bad one isn't read");
            h.assertTrue(BellConfig.V.configVersion >= 7, "the version isn't stamped");
        } catch (IOException ex) {
            h.fail("couldn't write the settings: " + ex);
        } finally {
            try {
                Files.writeString(file, keep);
            } catch (IOException e) {
                HollowbellMod.LOG.warn("Couldn't put the settings file back: {}", e.toString());
            }
            BellConfig.load();
            BellConfig.V = was;
        }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "kill_all")
    public void killLeavesNothingOfHimBehind(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 311);
        HollowbellEntity b = spawnAway(h, S, HollowbellEntity.HUNTER, 312);
        HollowbellEntity c = spawnAway(h, S, HollowbellEntity.CALM, 313);
        h.runAfterDelay(20, () -> {
            h.assertTrue(c.stepAside(), "one should step out of the world");
            var lines = GiantsCommand.ask(server, "kill", "", "net.jj.hollowbell.GiantsBridge");
            h.assertTrue(lines.stream().anyMatch(x -> x.contains("killed 3")), "/giants kill: " + lines);
            h.assertTrue(a.isDeadOrDying() && b.isDeadOrDying(), "the ones in the world aren't dying");
            h.assertTrue(Away.get(server).count() == 0, "the one out of the world is still out there");
        });
        h.runAfterDelay(20 + HollowbellEntity.DEATH_LENGTH + 40, () -> {
            var left = h.getLevel().getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved());
            h.assertTrue(left.isEmpty(), left.size() + " of him still there after they died");
            var seats =
                    h.getLevel().getEntities(ModEntities.SEAT, x -> !x.isRemoved() && x.distanceToSqr(a) < 200 * 200);
            h.assertTrue(seats.isEmpty(), "seats left behind");
            release(h, a);
            release(h, b);
            release(h, c);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "paint")
    public void paintTurnsEverythingInTheCircle(GameTestHelper h) {
        var l = h.getLevel();
        BlockPos c = groundSpot(h, 228);
        var chunk = l.getChunkAt(c);
        chunk.setInhabitedTime(50000); // people have lived here a long time
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        // somebody's floor: planks right on the ground
        List<BlockPos> planks = new ArrayList<>();
        for (int dx = 4; dx < 8; dx++)
            for (int dz = 4; dz < 8; dz++) {
                BlockPos t = new BlockPos(x0 + dx, top(h, x0 + dx, z0 + dz), z0 + dz);
                l.setBlock(t, Blocks.OAK_PLANKS.defaultBlockState(), 2);
                planks.add(t);
            }
        ServerPlayer p = player(h, Vec3.atCenterOf(new BlockPos(x0 + 8, top(h, x0 + 8, z0 + 8) + 2, z0 + 8)));
        var lines =
                GiantsCommand.ask(l.getServer(), "paint", "20 full " + p.getUUID(), "net.jj.hollowbell.GiantsBridge");
        h.assertTrue(
                lines.stream().anyMatch(x -> x.startsWith("Hollowbell") && x.contains("painting")),
                "/giants paint: " + lines);
        for (int t = 20; t < 380; t += 10)
            h.runAfterDelay(t, () -> {
                if (Painter.busy()) return;
                int ours = 0;
                for (int dx = 0; dx < 16; dx++)
                    for (int dz = 0; dz < 16; dz++)
                        if (ours(l.getBlockState(new BlockPos(x0 + dx, top(h, x0 + dx, z0 + dz), z0 + dz)))) ours++;
                h.assertTrue(ours > 150, "only " + ours + " columns of a lived-in chunk were painted");
                int plank = 0;
                for (BlockPos b : planks)
                    for (int y = b.getY() - 10; y <= b.getY() + 20; y++)
                        if (l.getBlockState(new BlockPos(b.getX(), y, b.getZ())).is(Blocks.OAK_PLANKS)) plank++;
                h.assertTrue(plank < planks.size(), "the built floor wasn't painted over");
                h.assertTrue(hollowsAt(h, x0 + 8, top(h, x0 + 8, z0 + 8), z0 + 8), "the biome wasn't changed");
                h.assertTrue(hollowsAt(h, x0 + 8, -40, z0 + 8), "the paint didn't reach all the way down the column");
                drop(p);
                force(h, c.getX(), c.getZ(), 1, false);
                h.succeed();
            });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "order_spot")
    public void aGoToOrderToNowhereIsRefused(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.2f, HollowbellEntity.CALM, 620);
        ServerPlayer[] p = new ServerPlayer[1];
        h.runAfterDelay(10, () -> {
            p[0] = player(h, e.position().add(60, 0, 0));
            p[0].getInventory().add(new ItemStack(ModItems.CODEX));
            CodexOrders.handle(p[0], new CodexPayload(CodexPayload.GO_TO_XZ, 0, Double.POSITIVE_INFINITY, Double.NaN));
            e.setGoal(new Vec3(Double.NaN, 0, Double.POSITIVE_INFINITY));
        });
        h.runAfterDelay(50, () -> {
            boolean ok = Double.isFinite(e.getX() + e.getY() + e.getZ()) && !e.isRemoved();
            boolean noGoal = e.goal() == null || Double.isFinite(e.goal().x + e.goal().z);
            drop(p[0]);
            release(h, e);
            h.assertTrue(ok, "an order to nowhere put him at " + e.position());
            h.assertTrue(noGoal, "an order to nowhere was taken: " + e.goal());
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 900, batch = "follow")
    public void followMeKeepsHimNearYouTillYouSayStop(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 650);
        h.runAfterDelay(20, () -> {
            ServerPlayer p = player(h, e.position().add(20, 0, 0));
            p.getInventory().add(new ItemStack(ModItems.CODEX));
            CodexOrders.handle(p, new CodexPayload(CodexPayload.FOLLOW));
            h.assertTrue(e.following() && p.getUUID().equals(e.followingWho()), "the book didn't make him follow");
            // kept through a save
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            boolean kept = p.getUUID().equals(b.followingWho()) && b.following();
            b.discard();
            h.assertTrue(kept, "following was lost in a save");
            // you go off: he comes after you
            Vec3 to = e.position().add(0, 0, 90);
            p.teleportTo(to.x, e.groundAt(to.x, to.z), to.z);
            double d0 = e.horiz(p.position());
            h.runAfterDelay(700, () -> {
                double d1 = e.horiz(p.position());
                boolean still = e.following();
                // asked again, he stops; and Stay stops it too
                CodexOrders.handle(p, new CodexPayload(CodexPayload.FOLLOW));
                boolean stopped = !e.following();
                e.follow(p);
                e.setStay(true);
                boolean stayStops = !e.following();
                drop(p);
                release(h, e);
                h.assertTrue(still, "he stopped following on his own");
                h.assertTrue(d1 < d0 - 15, "he didn't come after you: " + (int) d0 + " then " + (int) d1 + " blocks");
                h.assertTrue(stopped, "pressing it again didn't stop him following");
                h.assertTrue(stayStops, "Stay didn't stop him following");
                h.succeed();
            });
        });
    }
}
