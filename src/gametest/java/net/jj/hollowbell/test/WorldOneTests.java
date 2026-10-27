package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.item.FinderItem;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** The world's own one, the limit, spawn eggs and the crown's ward. */
public class WorldOneTests implements FabricGameTest {
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "egg")
    public void theSmallSpawnEggMakesASmallOne(GameTestHelper h) {
        CompoundTag t = new CompoundTag();
        t.putInt("HollowbellEgg", 3);
        HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
        b.readAdditionalSaveData(t);
        h.assertTrue(
                Math.abs(b.bellScale() - BellConfig.V.smallEggScale) < 1e-4 && b.isHunter(),
                "small egg made " + b.bellScale());
        t.putInt("HollowbellEgg", 0);
        HollowbellEntity c = ModEntities.HOLLOWBELL.create(h.getLevel());
        c.readAdditionalSaveData(t);
        h.assertTrue(c.variant() == HollowbellEntity.CALM, "calm egg made " + c.variant());
        b.discard();
        c.discard();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 40, batch = "natural_cmd")
    public void theNaturalCommandTurnsHimOnAndOff(GameTestHelper h) {
        boolean was = BellConfig.V.oneInTheWorld;
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        run(h, at, "hollowbell natural off");
        h.assertTrue(!BellConfig.V.oneInTheWorld, "natural off didn't turn it off");
        run(h, at, "hollowbell natural");
        run(h, at, "hollowbell natural on");
        h.assertTrue(BellConfig.V.oneInTheWorld, "natural on didn't turn it on");
        for (String c : new String[] {
            "hollowbell natural",
            "hollowbell natural off",
            "hollowbell limit",
            "hollowbell limit 3",
            "hollowbell ward",
            "hollowbell ward on",
            "hollowbell ward off",
            "hollowbell ward minutes 5",
            "hollowbell ward rest 0",
            "hollowbell ward blocks 300",
            "hollowbell area",
            "hollowbell area off",
            "hollowbell area 10 20 64"
        }) {
            h.assertTrue(parses(h, c, 2), "/" + c + " doesn't parse with cheats on");
            h.assertTrue(!parses(h, c, 0), "/" + c + " works without cheats");
        }
        BellConfig.V.oneInTheWorld = was;
        BellConfig.save();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "natural_death")
    public void whenTheWorldsOwnOneDiesTheNextIsDue(GameTestHelper h) {
        clearAll(h);
        var w = WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        // (nobody's: a player's one takes longer, see RespawnTests)
        BookRule.get(h.getLevel().getServer()).free();
        boolean was = BellConfig.V.oneInTheWorld;
        BellConfig.V.oneInTheWorld = true;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 200);
        h.runAfterDelay(20, () -> {
            e.markWorldOne();
            w.seen(e);
            h.assertTrue(w.aliveNow() && w.where() != null, "the world didn't note him");
            Vec3 fell = e.position();
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(e.isDeadOrDying(), "he should be dying");
            h.assertTrue(!w.aliveNow(), "the world still thinks he is out there");
            BlockPos next = w.where();
            double d = Math.hypot(next.getX() - fell.x, next.getZ() - fell.z);
            h.assertTrue(d > 1000 && d < 40000, "the next one comes down " + (int) d + " blocks from where he fell");
            int days = w.daysLeft(h.getLevel());
            h.assertTrue(days == BellConfig.V.worldRespawnDays, "the next one is due in " + days + " days");
            // and the finder says when, and which way
            ServerPlayer p = player(h, fell.add(0, 2, 0));
            var said = FinderItem.answer(h.getLevel(), p);
            h.assertTrue(
                    key(said)
                            .equals(
                                    days == 1
                                            ? "message.hollowbell.finder_dead_day"
                                            : "message.hollowbell.finder_dead"),
                    "the finder said " + key(said));
            h.assertTrue(((Number) args(said)[0]).intValue() == days, "the finder counts " + args(said)[0] + " days");
            h.assertTrue(
                    ((Number) args(said)[1]).intValue() % 10 == 0,
                    "the finder's distance isn't to the nearest ten: " + args(said)[1]);
            drop(p);
            release(h, e);
            w.clearForTests();
            BellConfig.V.oneInTheWorld = was;
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "natural_removed")
    public void removingHimStartsTheCountToTheNext(GameTestHelper h) {
        clearAll(h);
        var w = WorldOne.get(h.getLevel().getServer());
        w.clearForTests();
        // a remove that can't reach him (asleep far off) changes nothing
        HollowbellEntity far = ModEntities.HOLLOWBELL.create(h.getLevel());
        far.moveTo(h.absolutePos(BlockPos.ZERO).getX() + 9000, 0, 0);
        w.adopt(far);
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        run(h, Vec3.atCenterOf(o), "hollowbell remove");
        h.assertTrue(
                w.aliveNow() && w.isTheOne(far.getUUID()), "a remove that didn't reach him ended the world's own one");
        far.discard();
        // one that does reach him starts the count
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 214);
        h.runAfterDelay(20, () -> {
            w.clearForTests();
            w.adopt(e);
            run(h, Vec3.atCenterOf(o), "hollowbell remove");
            h.assertTrue(!w.aliveNow(), "removed, the world still thinks he is out there");
            h.assertTrue(
                    w.daysLeft(h.getLevel()) == BellConfig.V.worldRespawnDays,
                    "the next one is due in " + w.daysLeft(h.getLevel()) + " days");
            w.clearForTests();
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "natural_adopts_away")
    public void theSpawnerTakesOnOneAlreadyOutThere(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = WorldOne.get(server);
        w.clearForTests();
        boolean was = BellConfig.V.oneInTheWorld;
        int wasMax = BellConfig.V.maxInWorld;
        BellConfig.V.oneInTheWorld = true;
        BellConfig.V.maxInWorld = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.HUNTER, 215);
        h.runAfterDelay(20, () -> {
            var away = Away.get(server);
            UUID id = e.getUUID();
            Vec3 at = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            away.get(id).body.remove("BornAt"); // one from before ages were kept
            ServerPlayer p = player(h, Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1))));
            w.tick(h.getLevel());
            h.assertTrue(away.count() == 1 && away.get(id) != null, "the one out of the world was forgotten");
            h.assertTrue(
                    away.get(id).body.getBoolean("WorldOne") && w.isTheOne(id) && w.aliveNow(),
                    "he wasn't taken on as the world's own");
            int standing = 0;
            for (var l : server.getAllLevels())
                standing += l.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved())
                        .size();
            h.assertTrue(standing == 0, "the spawner put a new one down anyway (" + standing + ")");
            // another one dying now (not the world's own) doesn't start the count
            HollowbellEntity other = spawnAway(h, S, HollowbellEntity.CALM, 216);
            other.applyDamage(other.damageSources().generic(), other.healthMax() * 100f, -1, false);
            h.assertTrue(
                    w.aliveNow() && w.isTheOne(id), "another one dying ended the world's own one, out of the world");
            release(h, other);
            away.forget(id);
            drop(p);
            force(h, (int) at.x, (int) at.z, 4, false);
            w.clearForTests();
            BellConfig.V.oneInTheWorld = was;
            BellConfig.V.maxInWorld = wasMax;
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "natural_copies")
    public void anOldCopyOrADyingOneIsNotTheWorldsOwn(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = WorldOne.get(server);
        w.clearForTests();
        int wasMax = BellConfig.V.maxInWorld;
        BellConfig.V.maxInWorld = 1;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 217);
        h.runAfterDelay(20, () -> {
            w.adopt(e);
            // an old copy of him, still marked, loads: it loses the mark, and nobody is pushed out
            CompoundTag tag = new CompoundTag();
            e.saveWithoutId(tag);
            HollowbellEntity copy = ModEntities.HOLLOWBELL.create(h.getLevel());
            copy.load(tag);
            copy.setUUID(UUID.randomUUID());
            copy.moveTo(e.getX() + 20, e.getY(), e.getZ());
            h.getLevel().addFreshEntity(copy);
            WorldOne.joinedNow(copy, h.getLevel());
            h.assertTrue(!copy.isWorldOne() && w.isTheOne(e.getUUID()), "the old copy kept the mark");
            h.assertTrue(!e.isRemoved() && !copy.isRemoved(), "a copy loading pushed somebody out");
            copy.discard();
            // seen from the nether he writes nothing down about the overworld
            HollowbellEntity nether = ModEntities.HOLLOWBELL.create(server.getLevel(Level.NETHER));
            nether.moveTo(e.getX() + 5000, 64, e.getZ() + 5000);
            BlockPos before = w.where();
            WorldOne.get(server).clearForTests();
            w.noteSpot(before.getX(), before.getZ(), true, -1);
            w.adopt(nether);
            h.assertTrue(
                    w.where().getX() == before.getX() && w.where().getZ() == before.getZ(),
                    "nether spot written as the overworld spot: " + w.where());
            nether.discard();
            // he dies and is saved mid-fold: loading him again doesn't bring the world's own one back
            w.clearForTests();
            w.adopt(e);
            e.applyDamage(e.damageSources().generic(), e.healthMax() * 100f, -1, false);
            h.assertTrue(!w.aliveNow(), "his death didn't count");
            CompoundTag dying = new CompoundTag();
            e.saveWithoutId(dying);
            HollowbellEntity back = ModEntities.HOLLOWBELL.create(h.getLevel());
            back.load(dying);
            h.assertTrue(!back.isWorldOne(), "saved mid-fold he is still the world's own");
            WorldOne.joinedNow(back, h.getLevel());
            w.seen(e);
            h.assertTrue(!w.aliveNow(), "reloading him mid-fold brought him back");
            back.discard();
            w.clearForTests();
            BellConfig.V.maxInWorld = wasMax;
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 60, batch = "upgrade")
    public void upgradingNeverThrowsAnybodyAway(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        // the settings: an old file with no limit gets the new limit of 1; a new file set to 0 keeps 0
        var old = new BellConfig.Values();
        old.configVersion = 4;
        old.maxHollowbells = 0;
        BellConfig.migrate(old);
        h.assertTrue(old.maxInWorld == 1 && old.maxHollowbells == null, "an old file's limit became " + old.maxInWorld);
        var mine = new BellConfig.Values();
        mine.configVersion = 5;
        mine.maxHollowbells = 0;
        BellConfig.migrate(mine);
        h.assertTrue(
                mine.maxInWorld == 0 && mine.maxHollowbells == null,
                "a player's own 0 was changed to " + mine.maxInWorld);
        var now = new BellConfig.Values();
        now.configVersion = 7;
        now.maxInWorld = 3;
        BellConfig.migrate(now);
        h.assertTrue(now.maxInWorld == 3, "a new file's limit was changed to " + now.maxInWorld);
        // the old name never goes back into the file
        h.assertTrue(!new Gson().toJson(now).contains("maxHollowbells"), "the old name is still written");
        int wasMax = BellConfig.V.maxInWorld;
        BellConfig.V.maxInWorld = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 218);
        HollowbellEntity b = spawnAway(h, S, HollowbellEntity.HUNTER, 219);
        HollowbellEntity c = spawnAway(h, S, HollowbellEntity.GUARDIAN, 220);
        h.runAfterDelay(20, () -> {
            var away = Away.get(server);
            h.assertTrue(c.stepAside(), "he should step out of the world");
            for (var r : away.all()) r.body.remove("BornAt");
            // three from an older version, all loading back with the limit now at 1
            List<HollowbellEntity> back = new ArrayList<>();
            for (HollowbellEntity x : new HollowbellEntity[] {a, b}) {
                CompoundTag tag = new CompoundTag();
                x.saveWithoutId(tag);
                tag.remove("BornAt");
                HollowbellEntity y = ModEntities.HOLLOWBELL.create(h.getLevel());
                y.load(tag);
                h.assertTrue(!y.freshSpawn() && y.bornAt() == 0, "an old one reads as fresh, born " + y.bornAt());
                y.setUUID(UUID.randomUUID());
                y.moveTo(x.getX() + 30, x.getY(), x.getZ());
                h.getLevel().addFreshEntity(y);
                WorldOne.joinedNow(y, h.getLevel());
                back.add(y);
            }
            h.assertTrue(
                    back.stream().noneMatch(HollowbellEntity::isRemoved) && !a.isRemoved() && !b.isRemoved(),
                    "an old one loading pushed somebody out");
            h.assertTrue(away.count() == 1, "the one out of the world was thrown away on upgrade");
            for (var y : back) y.discard();
            for (var r : away.all()) away.forget(r.id);
            BellConfig.V.maxInWorld = wasMax;
            release(h, a);
            release(h, b);
            force(h, (int) c.getX(), (int) c.getZ(), 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 80, batch = "cap_egg")
    public void aSpawnEggMeetsTheLimit(GameTestHelper h) {
        clearAll(h);
        int wasMax = BellConfig.V.maxInWorld;
        BellConfig.V.maxInWorld = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 221);
        h.runAfterDelay(20, () -> {
            BlockPos at = BlockPos.containing(a.getX() + 30, a.getY(), a.getZ());
            // the real egg path: the egg's tag is merged into a fresh save of him
            HollowbellEntity egg = ModEntities.HOLLOWBELL.spawn(
                    h.getLevel(), new ItemStack(ModItems.SMALL_EGG), null, at, MobSpawnType.SPAWN_EGG, true, false);
            h.assertTrue(egg != null, "the egg made nothing");
            h.assertTrue(egg.freshSpawn(), "an egg one reads as loaded from a save, so it slips past the limit");
            WorldOne.joinedNow(egg, h.getLevel());
            h.assertTrue(a.isRemoved() && !egg.isRemoved(), "the egg one didn't push the older one out");
            egg.discard();
            BellConfig.V.maxInWorld = wasMax;
            release(h, a);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "cap_fresh")
    public void pastTheLimitTheOldestMakesWay(GameTestHelper h) {
        clearAll(h);
        int was = BellConfig.V.maxInWorld;
        BellConfig.V.maxInWorld = 1;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 202);
        HollowbellEntity[] b = new HollowbellEntity[1];
        Vec3[] aAt = new Vec3[1];
        h.runAfterDelay(20, () -> {
            aAt[0] = a.position();
            b[0] = spawnAway(h, S, HollowbellEntity.HUNTER, 203);
            h.assertTrue(!WorldOne.limitNow(b[0], h.getLevel()), "the new one was turned away");
            h.assertTrue(a.isRemoved(), "the old one is still there past the limit");
            h.assertTrue(!b[0].isRemoved(), "the new one went instead");
            // at the limit is fine: nobody more goes
            h.assertTrue(
                    !WorldOne.limitNow(null, h.getLevel()) && !b[0].isRemoved(), "at the limit, somebody still went");
        });
        h.runAfterDelay(40, () -> {
            h.assertTrue(
                    h.getLevel()
                            .getEntitiesOfClass(ItemEntity.class, new AABB(aAt[0], aAt[0]).inflate(40))
                            .isEmpty(),
                    "the one who made way dropped loot");
            BellConfig.V.maxInWorld = was;
            release(h, b[0]);
            force(h, (int) aAt[0].x, (int) aAt[0].z, 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "cap_reload")
    public void aSavedOneLoadingIsNotANewArrival(GameTestHelper h) {
        clearAll(h);
        var server = h.getLevel().getServer();
        var w = WorldOne.get(server);
        w.clearForTests();
        int was = BellConfig.V.maxInWorld;
        BellConfig.V.maxInWorld = 2;
        HollowbellEntity a = spawnAway(h, S, HollowbellEntity.CALM, 204);
        h.runAfterDelay(20, () -> {
            a.markWorldOne();
            a.bindTo(a.getX(), a.getZ(), 90);
            long born = a.bornAt();
            h.assertTrue(born >= 0 && a.freshSpawn(), "a fresh one: born " + born);
            CompoundTag tag = new CompoundTag();
            a.saveWithoutId(tag);
            HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
            b.load(tag);
            h.assertTrue(!b.freshSpawn(), "read back from a save, he counts as a new arrival");
            h.assertTrue(b.bornAt() == born, "born " + b.bornAt() + ", want " + born);
            h.assertTrue(b.isWorldOne(), "being the world's own one was lost");
            h.assertTrue(b.bound() && b.boundRadius() == 90, "his circle was lost");
            b.discard();
            Vec3 aAt = a.position();
            // one out of the world and one standing, the limit at two: nobody goes
            h.assertTrue(a.stepAside(), "he should step out of the world");
            var away = Away.get(server);
            HollowbellEntity c = spawnAway(h, S, HollowbellEntity.HUNTER, 205);
            h.assertTrue(!WorldOne.limitNow(c, h.getLevel()), "turned away at the limit");
            h.assertTrue(away.count() == 1, "the one out of the world was thrown away at exactly the limit");
            // a third, past it: the oldest (the one out there) goes, and the newest takes over as the world's own
            HollowbellEntity d = spawnAway(h, S, HollowbellEntity.HUNTER, 206);
            WorldOne.limitNow(d, h.getLevel());
            h.assertTrue(away.count() == 0, "the oldest, out of the world, should have gone");
            h.assertTrue(!c.isRemoved() && !d.isRemoved(), "the wrong one went");
            h.assertTrue(d.isWorldOne(), "the world's own one wasn't passed on to the newest");
            BellConfig.V.maxInWorld = was;
            w.clearForTests();
            release(h, c);
            release(h, d);
            force(h, (int) aAt.x, (int) aAt.z, 4, false);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "limit_cmd")
    public void theLimitCommandSetsIt(GameTestHelper h) {
        clearAll(h);
        int was = BellConfig.V.maxInWorld;
        Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(1, 2, 1)));
        run(h, at, "hollowbell limit 3");
        h.assertTrue(BellConfig.V.maxInWorld == 3, "limit is " + BellConfig.V.maxInWorld);
        run(h, at, "hollowbell limit");
        run(h, at, "hollowbell limit 0");
        h.assertTrue(BellConfig.V.maxInWorld == 0, "limit is " + BellConfig.V.maxInWorld);
        BellConfig.V.maxInWorld = was;
        BellConfig.save();
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "ward")
    public void aWokenCrownHoldsHimOff(GameTestHelper h) {
        clearAll(h);
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        int wasBlocks = BellConfig.V.wardBlocks;
        BellConfig.V.wardBlocks = 48;
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 208);
        ServerPlayer[] pl = new ServerPlayer[1];
        BlockPos[] crown = new BlockPos[2];
        double[] d0 = new double[1];
        h.runAfterDelay(20, () -> {
            Vec3 at = e.position();
            int cx = (int) Math.floor(at.x) + 100, cz = (int) Math.floor(at.z);
            force(h, cx, cz, 2, true);
            int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz);
            crown[0] = new BlockPos(cx, gy, cz);
            l.setBlockAndUpdate(crown[0], ModBlocks.CROWN.defaultBlockState());
            run(h, Vec3.atCenterOf(crown[0].above()), "hollowbell ward on");
            h.assertTrue(w.warding(l), "the crown didn't wake");
            h.assertTrue(e.warded(cx, cz) && !e.warded(at.x, at.z), "the circle is in the wrong place");
            // told to go right to it, he won't
            e.setGoal(new Vec3(cx + 0.5, gy, cz + 0.5));
            // a pig by the crown is out of his reach
            Pig pg = pig(h, new Vec3(cx + 2.5, gy, cz + 0.5));
            h.assertTrue(!e.fairGame(pg), "he can still go after something by the crown");
            pg.discard();
            pl[0] = player(h, at.add(5, 0, 5));
            pl[0].getInventory().add(new ItemStack(ModItems.CODEX));
        });
        h.runAfterDelay(23, () -> {
            h.assertTrue(e.goal() == null, "he still means to go into the circle: " + e.goal());
            // the book can't send him in there either
            CodexOrders.handle(
                    pl[0], new CodexPayload(CodexPayload.GO_TO_XZ, 0, crown[0].getX() + 0.5, crown[0].getZ() + 0.5));
            h.assertTrue(e.goal() == null, "the book sent him into the circle");
            // wake a crown right beside him instead: he makes for the edge
            run(h, e.position(), "hollowbell ward off");
            BlockPos near = BlockPos.containing(e.getX() + 12, e.groundAt(e.getX() + 12, e.getZ()), e.getZ());
            crown[1] = near;
            l.setBlockAndUpdate(near, ModBlocks.CROWN.defaultBlockState());
            run(h, Vec3.atCenterOf(near.above()), "hollowbell ward on");
            h.assertTrue(w.warding(l) && e.warded(e.getX(), e.getZ()), "the second crown didn't wake round him");
            d0[0] = Math.hypot(e.getX() - near.getX(), e.getZ() - near.getZ());
        });
        h.runAfterDelay(300, () -> {
            double d1 = Math.hypot(e.getX() - crown[1].getX(), e.getZ() - crown[1].getZ());
            h.assertTrue(d1 > d0[0] + 1, "inside the circle he didn't make for the edge: " + d0[0] + " -> " + d1);
            // taking the crown up ends it
            l.setBlockAndUpdate(crown[1], Blocks.AIR.defaultBlockState());
            h.assertTrue(!w.warding(l), "the ward kept going with the crown taken up");
            l.setBlockAndUpdate(crown[0], Blocks.AIR.defaultBlockState());
            force(h, crown[0].getX(), crown[0].getZ(), 2, false);
            BellConfig.V.wardBlocks = wasBlocks;
            w.clearForTests();
            drop(pl[0]);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "ward_zero")
    public void wardBlocksZeroIsNoWard(GameTestHelper h) {
        var l = h.getLevel();
        var w = WorldOne.get(l.getServer());
        w.clearForTests();
        int was = BellConfig.V.wardBlocks;
        BlockPos o = h.absolutePos(new BlockPos(1, 2, 1));
        w.startWard(l, o, 2000, 0);
        BellConfig.V.wardBlocks = 100;
        h.assertTrue(w.warded(l, o.getX() + 10, o.getZ()), "the ward isn't holding");
        BellConfig.V.wardBlocks = 0;
        h.assertTrue(!w.warded(l, o.getX() + 10, o.getZ()), "ward blocks 0 still holds him off");
        BellConfig.V.wardBlocks = was;
        w.clearForTests();
        h.succeed();
    }
}
