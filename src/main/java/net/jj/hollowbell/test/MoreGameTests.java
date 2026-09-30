package net.jj.hollowbell.test;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

import static net.jj.hollowbell.test.HollowbellGameTests.*;

/** More server-side checks (orders kept through a restart, the egg rain, meetings, advancements, sounds). */
public class MoreGameTests implements FabricGameTest {
    private static final float S = 0.1f;

    /** a fake player with a set id (so the same one can "come back") */
    @SuppressWarnings("deprecation")
    static ServerPlayer playerWithId(GameTestHelper h, Vec3 at, UUID id) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(id, "hb-t" + id.toString().substring(0, 6)), false);
        ServerPlayer p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Connection c = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    /** saves him, takes him out and puts a copy made from the save back in (a restart, as far as he can tell) */
    static HollowbellEntity reload(GameTestHelper h, HollowbellEntity e) {
        CompoundTag tag = new CompoundTag();
        e.saveWithoutId(tag);
        e.discard();
        HollowbellEntity b = ModEntities.HOLLOWBELL.create(h.getLevel());
        b.load(tag);
        b.setMoveCooldown(100000);
        h.getLevel().addFreshEntity(b);
        return b;
    }

    // ------------------------------------------------------------------ orders survive a restart

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_goto")
    public void aGoThereOrderSurvivesARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 320);
        h.runAfterDelay(20, () -> {
            Vec3 to = e.position().add(300, 0, 0);
            e.orderTo(to, null);
            HollowbellEntity b = reload(h, e);
            h.assertTrue(b.goal() != null && Math.abs(b.goal().x - to.x) < 2, "the spot he was sent to is lost: " + b.goal());
            h.assertTrue(b.holdsThere(), "he forgot to hold still once there");
            double x0 = b.getX();
            h.runAfterDelay(80, () -> {
                h.assertTrue(b.getX() > x0 + 1, "he isn't going there: " + x0 + " -> " + b.getX());
                release(h, b);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_stay")
    public void stayAndAreaSurviveARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 321);
        h.runAfterDelay(20, () -> {
            e.sendAfter(pig(h, under(e).add(3, 0, 0)));
            e.setStay(true);
            e.bindTo(e.getX(), e.getZ(), 64);
            HollowbellEntity b = reload(h, e);
            h.assertTrue(b.staying(), "he stopped staying");
            h.assertTrue(b.bound() && b.boundRadius() == 64, "his area is lost");
            release(h, b);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "keep_come")
    public void comeToMeWaitsForSomebodyWhoLeftThenCarriesOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 322);
        UUID id = UUID.randomUUID();
        h.runAfterDelay(20, () -> {
            ServerPlayer p = playerWithId(h, e.position().add(40, 0, 0), id);
            e.orderTo(p.position(), p);
            drop(p);                                          // they leave the game
            HollowbellEntity b = reload(h, e);
            h.assertTrue(id.equals(b.comingTo()), "who he's coming to is lost");
            h.runAfterDelay(30, () -> {
                h.assertTrue(b.waitingForSomebody(), "he should know they've gone and wait for them");
                h.assertTrue(id.equals(b.comingTo()), "he gave up at once");
                // they come back somewhere else: he carries on to them
                ServerPlayer back = playerWithId(h, b.position().add(-40, 0, 10), id);
                h.runAfterDelay(30, () -> {
                    h.assertTrue(!b.waitingForSomebody(), "he didn't notice they're back");
                    h.assertTrue(b.goal() != null && b.goal().distanceTo(back.position()) < 5, "he isn't coming to where they are now: " + b.goal());
                    // they go again, and this time he gives up (the 20 minutes cut short)
                    drop(back);
                    long was = HollowbellEntity.comeGiveUpMs;
                    HollowbellEntity.comeGiveUpMs = 1;
                    h.runAfterDelay(50, () -> {
                        HollowbellEntity.comeGiveUpMs = was;
                        h.assertTrue(b.comingTo() == null && !b.holdsThere(), "he never gave up waiting");
                        release(h, b);
                        h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "keep_away")
    public void anAwayTripAndItsOrderSurviveARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 323);
        var server = h.getLevel().getServer();
        var a = net.jj.hollowbell.world.Away.get(server);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            UUID who = UUID.randomUUID();
            Vec3 was = e.position();
            h.assertTrue(e.stepAside(), "he should step out of the world");
            h.assertTrue(a.order(h.getLevel(), id, was.add(2000, 0, 0), who), "the order didn't reach him out there");
            CompoundTag saved = a.save(new CompoundTag(), h.getLevel().registryAccess());
            var a2 = net.jj.hollowbell.world.Away.load(saved, h.getLevel().registryAccess());
            var r = a2.get(id);
            h.assertTrue(r != null, "the record is lost");
            h.assertTrue(r.going && Math.abs(r.toX - (was.x + 2000)) < 2, "the trip is lost");
            h.assertTrue(r.hold && who.equals(r.follow), "the order is lost");
            h.assertTrue(r.spot(h.getLevel().getGameTime() + 200).x > was.x + 1, "he isn't going anywhere");
            a.forget(id);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "keep_fetch")
    public void anOrderToOneInUnloadedLandSurvivesARestart(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 324);
        var server = h.getLevel().getServer();
        var a = net.jj.hollowbell.world.Away.get(server);
        h.runAfterDelay(20, () -> {
            UUID id = e.getUUID();
            Vec3 at = e.position();
            release(h, e);
            a.noteParked(e);             // (left in land nobody has loaded)
            Vec3 to = at.add(500, 0, 0);
            var t = new net.jj.hollowbell.world.FarOrders.Target(net.jj.hollowbell.world.FarOrders.Kind.PARKED, id, at.x, at.z, null);
            h.assertTrue(net.jj.hollowbell.world.FarOrders.order(h.getLevel(), t, to, null, 0f) != null, "the order went nowhere");
            CompoundTag saved = a.save(new CompoundTag(), h.getLevel().registryAccess());
            net.jj.hollowbell.world.FarOrders.forget();                        // the server stops
            net.jj.hollowbell.world.Away.load(saved, h.getLevel().registryAccess());   // and starts again
            Object[] f = net.jj.hollowbell.world.FarOrders.firstFetch();
            h.assertTrue(f != null && id.equals(f[0]) && Math.abs(((Vec3) f[1]).x - to.x) < 1, "the order to him is lost");
            net.jj.hollowbell.world.FarOrders.forget();
            a.unpark(id);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the egg rain

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 500, batch = "egg_rain_big")
    public void eggRainEggsAreBigMarkedAndHatch(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 325);
        net.minecraft.world.entity.animal.Pig[] pig = new net.minecraft.world.entity.animal.Pig[1];
        java.util.Set<net.jj.hollowbell.entity.Shot> landed = new java.util.HashSet<>();
        double[] worst = {0};
        h.runAfterDelay(20, () -> {
            e.setStay(true);
            net.jj.hollowbell.entity.Shot.hatchChance = 1f;
            Vec3 u = under(e).add(14, 0, 0);
            pig[0] = pig(h, new Vec3(u.x, e.groundAt(u.x, u.z), u.z));
            pig[0].setInvulnerable(true);
            e.setTarget(pig[0]);
            h.assertTrue(e.forceMove(net.jj.hollowbell.entity.Moves.EGG_RAIN, pig[0]), "no egg rain");
        });
        // the wind-up: nothing has come off yet
        h.runAfterDelay(20 + net.jj.hollowbell.entity.Moves.EGG_AT - 5, () ->
                h.assertTrue(e.moves().lastEggs.isEmpty(), "eggs came off before the wind-up was over"));
        h.runAfterDelay(20 + net.jj.hollowbell.entity.Moves.EGG_AT + 30, () -> {
            var eggs = e.moves().lastEggs;
            h.assertTrue(eggs.size() >= 4, "too few eggs: " + eggs.size());
            for (var sh : eggs) {
                h.assertTrue(Math.abs(sh.size() - 0.3f) < 1e-4, "an egg isn't drawn at his size");
                Vec3 l = sh.landing();
                h.assertTrue(Math.abs(l.y - e.groundAt(l.x, l.z)) < 1.5, "a marker isn't on the ground: " + l);
                // the eggs spread over a square round the target (each way up to 6 x size + 2), so its corners count too
                h.assertTrue(Math.hypot(l.x - pig[0].getX(), l.z - pig[0].getZ()) < (6 * 0.3 + 2) * 1.415 + 1, "a marker is off the target: " + l);
            }
        });
        // each egg comes down on its marker
        for (int k = 0; k < 120; k += 2) {
            h.runAfterDelay(20 + net.jj.hollowbell.entity.Moves.EGG_AT + k, () -> {
                for (var sh : e.moves().lastEggs) {
                    if (sh.stage() == net.jj.hollowbell.entity.Shot.FALLING || !landed.add(sh)) continue;
                    Vec3 l = sh.landing();
                    worst[0] = Math.max(worst[0], Math.hypot(sh.getX() - l.x, sh.getZ() - l.z));
                }
            });
        }
        h.runAfterDelay(20 + net.jj.hollowbell.entity.Moves.EGG_AT + 124, () -> {
            net.jj.hollowbell.entity.Shot.hatchChance = 0.65f;
            h.assertTrue(landed.size() >= 4, "the eggs never landed: " + landed.size());
            h.assertTrue(worst[0] < 3.2, "an egg came down off its marker by " + worst[0]);
            var bellings = h.getLevel().getEntitiesOfClass(net.jj.hollowbell.entity.Belling.class, e.bodyBox().inflate(40));
            h.assertTrue(bellings.size() >= 3, "the eggs didn't hatch: " + bellings.size());
            for (var sh : e.moves().lastEggs) h.assertTrue(sh.isRemoved(), "a splat never went away");
            for (var b : bellings) b.discard();
            pig[0].discard();
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ giants meeting

    /** a stand-in for another of JJ's giants, of that mod's kind */
    static net.minecraft.world.entity.monster.Zombie giantOfKind(GameTestHelper h, Vec3 at, String kind) {
        var z = fakeGiant(h, at);
        z.addTag(net.jj.hollowbell.entity.Meetings.KIND + kind);
        z.setInvulnerable(true);
        return z;
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_table")
    public void theMeetingTable(GameTestHelper h) {
        String[] k = {"mountain_breathes", "furrowmaw", "fire_ice_cerberus", "hollowbell", "lanternwillow"};
        String[] want = {"-ffaa", "f-faf", "ff-ff", "aaf-a", "affa-"};
        for (int a = 0; a < 5; a++) for (int b = 0; b < 5; b++) {
            var w = net.jj.hollowbell.entity.Meetings.way(k[a], k[b]);
            char c = want[a].charAt(b);
            var expect = c == 'f' ? net.jj.hollowbell.entity.Meetings.Way.FIGHT : c == 'a' ? net.jj.hollowbell.entity.Meetings.Way.AVOID : net.jj.hollowbell.entity.Meetings.Way.NONE;
            h.assertTrue(w == expect, k[a] + " meeting " + k[b] + ": " + w);
            h.assertTrue(w == net.jj.hollowbell.entity.Meetings.way(k[b], k[a]), "the table isn't the same both ways for " + k[a] + ", " + k[b]);
        }
        h.assertTrue(net.jj.hollowbell.entity.Meetings.way("hollowbell", "minecraft") == net.jj.hollowbell.entity.Meetings.Way.NONE, "an unknown kind");
        h.assertTrue(net.jj.hollowbell.entity.Meetings.name("fire_ice_cerberus", false).equals("the Cerberus"), "names");
        h.assertTrue(net.jj.hollowbell.entity.Meetings.range(1f) == 160 && net.jj.hollowbell.entity.Meetings.range(0.1f) == 48
                && net.jj.hollowbell.entity.Meetings.range(2f) == 320, "the range");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "meet_table")
    public void theFightLineIsSaidOnce(GameTestHelper h) {
        java.util.UUID a = java.util.UUID.randomUUID(), b = java.util.UUID.randomUUID();
        long t = 1_000_000L + h.getLevel().getGameTime();
        h.assertTrue(net.jj.hollowbell.entity.Meetings.claimMeetingLine(a, b, t), "the first claim didn't win");
        h.assertFalse(net.jj.hollowbell.entity.Meetings.claimMeetingLine(b, a, t), "the other one said it too");
        h.assertFalse(net.jj.hollowbell.entity.Meetings.claimMeetingLine(a, b, t + 6000), "said again too soon");
        h.assertTrue(net.jj.hollowbell.entity.Meetings.claimMeetingLine(b, a, t + 6001), "not said again after 6001 ticks");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_fight")
    public void heFightsAGiantTheTableSaysToFight(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 326);
        h.runAfterDelay(20, () -> {
            h.assertTrue(e.getTags().contains(net.jj.hollowbell.entity.Meetings.KIND + "hollowbell"), "he has no kind tag");
            var z = giantOfKind(h, under(e).add(20, 0, 0), "fire_ice_cerberus");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.getTarget() == z, "he didn't go for the Cerberus: " + e.getTarget());
                h.assertTrue(z.getUUID().equals(e.meetFoe()), "it isn't a meeting");
                h.assertTrue(e.meetCooldownLeft(z.getUUID()) > 20 * 60 * 9, "no wait before they meet again");
                z.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 300, batch = "meet_avoid")
    public void heKeepsAwayFromAGiantTheTableSaysToAvoid(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 327);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(16, 0, 0), "mountain_breathes");
            double d0 = e.horiz(z.position());
            h.runAfterDelay(160, () -> {
                h.assertTrue(e.getTarget() != z, "he went for Pitchgut");
                h.assertTrue(z.getUUID().equals(e.avoiding()) || e.horiz(z.position()) > net.jj.hollowbell.entity.Meetings.range(0.3f) * 1.5,
                        "he isn't keeping away");
                h.assertTrue(e.horiz(z.position()) > d0 + 3, "he didn't move away: " + d0 + " -> " + e.horiz(z.position()));
                // hit by it, he still hits back
                e.hurt(e.damageSources().mobAttack(z), 5f);
                h.assertTrue(e.getTarget() == z, "he didn't hit back");
                z.discard();
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_yield")
    public void broughtLowByAGiantHeBacksDown(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.HUNTER, 328);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            e.startMeeting(z);
            e.setHealthTo(e.healthMax() * 0.2f);
            e.hurt(e.damageSources().mobAttack(z), 5f);
            h.assertTrue(e.yielding() && e.getTags().contains("jj_giant_yield"), "he didn't back down");
            h.assertTrue(e.getTarget() != z && e.meetFoe() == null, "he's still after it");
            e.hurt(e.damageSources().mobAttack(z), 5f);
            h.assertTrue(e.getTarget() != z, "he hit back while backing down");
            h.runAfterDelay(60, () -> {
                h.assertTrue(e.getTarget() != z, "he went back for it");
                h.assertTrue(e.horiz(z.position()) > 12, "he isn't getting away from it");
                // and a fresh one, backing down, is left alone by him too: no giant is his while he yields
                var z2 = giantOfKind(h, under(e).add(-10, 0, 0), "fire_ice_cerberus");
                h.runAfterDelay(50, () -> {
                    h.assertTrue(e.getTarget() != z2, "backing down, he went for another giant");
                    z.discard(); z2.discard();
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 200, batch = "meet_won")
    public void aGiantThatBacksDownIsLeftAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 329);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            h.assertTrue(e.startMeeting(z), "the meeting didn't start");
            z.addTag("jj_giant_yield");
            h.runAfterDelay(10, () -> {
                h.assertTrue(e.meetFoe() == null && e.getTarget() != z, "he kept fighting one that backed down");
                h.runAfterDelay(60, () -> {
                    h.assertTrue(e.getTarget() != z, "he picked it again");
                    z.discard();
                    release(h, e);
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "meet_cooldown")
    public void heKeepsAwayAgainWhenAGiantHeAvoidsComesBack(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 330);
        h.runAfterDelay(20, () -> {
            var z = giantOfKind(h, under(e).add(16, 0, 0), "lanternwillow");
            h.runAfterDelay(60, () -> {
                h.assertTrue(z.getUUID().equals(e.avoiding()), "he never noticed the Willow");
                // it goes well off, and he stops keeping away
                Vec3 far = e.position().add(100, 0, 0);
                h.getLevel().setChunkForced(net.minecraft.util.Mth.floor(far.x) >> 4, net.minecraft.util.Mth.floor(far.z) >> 4, true);
                z.teleportTo(far.x, z.getY(), far.z);
                h.runAfterDelay(20, () -> {
                    h.assertTrue(e.avoiding() == null, "he is still keeping away from one far off");
                    // it comes back: keeping away has no wait, so he keeps away from it again (and never fights it)
                    Vec3 near = e.position().add(12, 0, 0);
                    z.teleportTo(near.x, z.getY(), near.z);
                    h.runAfterDelay(100, () -> {
                        h.assertTrue(z.getUUID().equals(e.avoiding()), "he let a giant he keeps away from come back close");
                        h.assertTrue(e.getTarget() != z, "he went for a giant he keeps away from");
                        h.assertTrue(e.meetCooldownLeft(z.getUUID()) == 0, "keeping away set a wait");
                        h.getLevel().setChunkForced(net.minecraft.util.Mth.floor(far.x) >> 4, net.minecraft.util.Mth.floor(far.z) >> 4, false);
                        z.discard();
                        release(h, e);
                        h.succeed();
                    });
                });
            });
        });
    }

    // ------------------------------------------------------------------ advancements for the gear

    static boolean done(ServerPlayer p, String name) {
        var adv = p.server.getAdvancements().get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("hollowbell", name));
        return adv != null && p.getAdvancements().getOrStartProgress(adv).isDone();
    }

    static void wearTheSet(ServerPlayer p) {
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.BELL_HELMET));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.BELL_CHESTPLATE));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.BELL_LEGGINGS));
        p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.BELL_BOOTS));
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_gear")
    public void theGearAdvancementsAreGiven(GameTestHelper h) {
        Vec3 at = h.absoluteVec(new Vec3(1, 2, 1));
        ServerPlayer p = HollowbellGameTests.player(h, at);
        h.assertTrue(!done(p, "bell_glass_set") && !done(p, "bell_toll") && !done(p, "stinger_throw"), "a new player has them already");
        wearTheSet(p);
        h.runAfterDelay(25, () -> {
            h.assertTrue(done(p, "bell_glass_set"), "wearing the set didn't give it");
            p.setOnGround(true);
            net.jj.hollowbell.item.BellPower.use(p);
            h.assertTrue(done(p, "bell_toll"), "the armour power didn't give it");
            var st = new net.minecraft.world.item.ItemStack(net.jj.hollowbell.ModItems.STINGER);
            p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, st);
            net.jj.hollowbell.item.StingerItem.throwIt(p, st, 1f, false);
            h.assertTrue(done(p, "stinger_throw"), "throwing the Stinger didn't give it");
            // his loot cache
            net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(at).offset(2, 0, 0);
            h.getLevel().setBlockAndUpdate(c, net.jj.hollowbell.ModBlocks.LOOT_CACHE.defaultBlockState());
            var hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(c), net.minecraft.core.Direction.UP, c, false);
            h.getLevel().getBlockState(c).useWithoutItem(h.getLevel(), p, hit);
            h.assertTrue(done(p, "loot_cache"), "opening the loot cache didn't give it");
            p.closeContainer();
            h.getLevel().setBlockAndUpdate(c, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            for (var hk : h.getLevel().getEntitiesOfClass(net.jj.hollowbell.entity.StingerHook.class, p.getBoundingBox().inflate(40))) hk.discard();
            drop(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_own_glass")
    public void killingHimInHisOwnGlassGivesIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.05f, HollowbellEntity.CALM, 331);
        h.runAfterDelay(20, () -> {
            ServerPlayer p = HollowbellGameTests.player(h, under(e).add(4, 0, 0));
            wearTheSet(p);
            e.hurt(e.damageSources().playerAttack(p), 1e9f);
            h.assertTrue(e.isDeadOrDying(), "he didn't die");
            h.assertTrue(done(p, "own_glass"), "killing him in his own glass didn't give it");
            drop(p);
            release(h, e);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "adv_watch")
    public void beingNearWhenGiantsFightGivesIt(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 332);
        h.runAfterDelay(20, () -> {
            ServerPlayer near = HollowbellGameTests.player(h, under(e).add(30, 0, 0));
            near.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            var z = giantOfKind(h, under(e).add(12, 0, 0), "fire_ice_cerberus");
            h.assertTrue(e.startMeeting(z), "no meeting");
            h.assertTrue(done(near, "watch_giants"), "watching two giants fight didn't give it");
            z.discard();
            drop(near);
            release(h, e);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ music and sounds

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sounds_files")
    public void everySoundFileIsThereAndDecodes(GameTestHelper h) {
        try (var in = MoreGameTests.class.getResourceAsStream("/assets/hollowbell/sounds.json")) {
            h.assertTrue(in != null, "no sounds.json");
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            int own = 0;
            for (var ev : json.entrySet()) {
                for (var snd : ev.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                    String name = snd.getAsJsonObject().get("name").getAsString();
                    if (!name.startsWith("hollowbell:")) continue;
                    String path = "/assets/hollowbell/sounds/" + name.substring(11) + ".ogg";
                    try (var o = MoreGameTests.class.getResourceAsStream(path)) {
                        h.assertTrue(o != null, ev.getKey() + ": " + path + " is missing");
                        byte[] head = o.readNBytes(64);
                        h.assertTrue(head.length >= 40 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S', path + " isn't an Ogg file");
                        // the Vorbis header: packet type 1, "vorbis", version, channels, sample rate
                        int at = 27 + (head[26] & 0xff);
                        h.assertTrue(head[at] == 1 && new String(head, at + 1, 6, java.nio.charset.StandardCharsets.US_ASCII).equals("vorbis"), path + " isn't Vorbis");
                        int ch = head[at + 11] & 0xff;
                        int rate = (head[at + 12] & 0xff) | (head[at + 13] & 0xff) << 8 | (head[at + 14] & 0xff) << 16 | (head[at + 15] & 0xff) << 24;
                        h.assertTrue(ch >= 1 && ch <= 2 && rate >= 8000 && rate <= 96000, path + ": " + ch + " channels at " + rate);
                        // a sound placed in the world must be mono (only then does it get quieter with distance)
                        if (!name.contains("music/") && !name.contains("ambient/ground")) h.assertTrue(ch == 1, path + " should be mono");
                        own++;
                    }
                }
            }
            h.assertTrue(own >= 15, "too few of his own sounds: " + own);
        } catch (java.io.IOException ex) { h.fail(ex.toString()); }
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sounds_board")
    public void onlyOneFightThemeAtATime(GameTestHelper h) {
        var b = new java.util.HashMap<String, double[]>();
        long now = 1_000_000L;
        h.assertTrue(!net.jj.hollowbell.sound.MusicBoard.plays(b, "hollowbell", now), "plays with nothing on the board");
        b.put("hollowbell", new double[]{400, now});
        h.assertTrue(net.jj.hollowbell.sound.MusicBoard.plays(b, "hollowbell", now), "alone, it should play");
        b.put("furrowmaw", new double[]{100, now - 500});
        h.assertTrue(!net.jj.hollowbell.sound.MusicBoard.plays(b, "hollowbell", now), "a nearer fight's theme should win");
        h.assertTrue(net.jj.hollowbell.sound.MusicBoard.plays(b, "furrowmaw", now), "the nearer one should play");
        b.put("furrowmaw", new double[]{100, now - 2500});
        h.assertTrue(net.jj.hollowbell.sound.MusicBoard.plays(b, "hollowbell", now), "a stale entry shouldn't count");
        b.put("fire_ice_cerberus", new double[]{400, now});
        h.assertTrue(net.jj.hollowbell.sound.MusicBoard.plays(b, "fire_ice_cerberus", now) && !net.jj.hollowbell.sound.MusicBoard.plays(b, "hollowbell", now),
                "a tie should go to the smaller mod id");
        h.assertTrue(net.jj.hollowbell.sound.MusicBoard.anyFresh(b, now) && !net.jj.hollowbell.sound.MusicBoard.anyFresh(b, now + 5000), "fresh");
        // the shared board lives in the system properties, where the other four mods find it
        net.jj.hollowbell.sound.MusicBoard.want("hollowbell-test", 1);
        h.assertTrue(System.getProperties().get("jj.giants.music") instanceof java.util.Map<?, ?> m && m.containsKey("hollowbell-test"), "not on the shared board");
        net.jj.hollowbell.sound.MusicBoard.drop("hollowbell-test");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 120, batch = "sounds_fight_flag")
    public void heSaysWhenAFightIsOn(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 333);
        h.runAfterDelay(20, () -> {
            h.assertTrue(!e.fighting(), "fighting nobody");
            ServerPlayer p = HollowbellGameTests.player(h, under(e).add(8, 0, 0));
            e.setTarget(p);
            h.runAfterDelay(25, () -> {
                h.assertTrue(e.fighting(), "after a player, the fight should be on");
                drop(p);
                release(h, e);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "meet_commands")
    public void theMeetingCommandsWork(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, 0.3f, HollowbellEntity.CALM, 334);
        var server = h.getLevel().getServer();
        h.runAfterDelay(20, () -> {
            var src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            server.getCommands().performPrefixedCommand(src, "hollowbell meetings off");
            h.assertTrue(!net.jj.hollowbell.HollowbellConfig.V.meetings, "/hollowbell meetings off didn't");
            server.getCommands().performPrefixedCommand(src, "giants meetings on");
            h.assertTrue(net.jj.hollowbell.HollowbellConfig.V.meetings, "/giants meetings on didn't");
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetRange 200");
            h.assertTrue(net.jj.hollowbell.HollowbellConfig.V.meetRange == 200, "meetRange can't be set");
            server.getCommands().performPrefixedCommand(src, "hollowbell config meetRange 160");
            // /giants meet asks each giant's mod with the other's id (and its own)
            var z = giantOfKind(h, under(e).add(10, 0, 0), "furrowmaw");
            var said = net.jj.hollowbell.GiantsBridge.giants(server, "meet", z.getUUID() + " " + e.getUUID());
            h.assertTrue(said != null && !said.isEmpty(), "the bridge didn't answer meet");
            h.assertTrue(z.getUUID().equals(e.meetFoe()) && e.getTarget() == z, "meet didn't start a fight (whatever the table says)");
            z.discard();
            release(h, e);
            h.succeed();
        });
    }

    /** his strands' stings and blows leave a giant he isn't fighting alone (else a pair meant to keep apart fights) */
    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 100, batch = "fighting_giant")
    public void hisStingsLeaveAGiantHeIsNotFightingAlone(GameTestHelper h) {
        HollowbellEntity e = spawnAway(h, S, HollowbellEntity.CALM, 361);
        h.runAfterDelay(10, () -> {
            var z = fakeGiant(h, e.position().add(20, 0, 0));
            z.addTag(net.jj.hollowbell.entity.Meetings.KIND + "lanternwillow");
            float was = z.getHealth();
            h.assertFalse(e.fightingGiant(z), "a giant drifting past counts as one he's fighting");
            e.moves().sting(z);
            h.assertTrue(z.getHealth() == was, "his sting hurt a giant he isn't fighting: " + z.getHealth() + " of " + was);
            // once it's the one he's going for, it's fair game
            e.setTarget(z);
            h.assertTrue(e.fightingGiant(z), "the giant he's going for doesn't count");
            z.invulnerableTime = 0;
            e.moves().sting(z);
            h.assertTrue(z.getHealth() < was, "his sting didn't touch the giant he's going for");
            e.setTarget(null);
            z.discard();
            release(h, e);
            h.succeed();
        });
    }
}
