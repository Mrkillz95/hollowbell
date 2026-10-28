package net.jj.hollowbell.test;

import java.util.List;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.BookRule;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.GiantsBridge;
import net.jj.hollowbell.solid.SitSeat;
import net.jj.hollowbell.solid.SitSettings;
import net.jj.hollowbell.solid.Sitting;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.TripleCrouch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Sit anywhere on him: only his owner (the book holder) and their safe list may; the seat stays on its spot of him
 * through his moves and his walking; jumping gets you up on that spot; nothing of his hurts you while you sit; it ends
 * safely when he goes or dies, when you're taken off the list and when you leave; the triple crouch's counter; and the
 * Giants Guide's Controls bridge for the gesture's setting.
 */
public class SitTests implements FabricGameTest {
    // what's his own in this mod

static void after(GameTestHelper h, int t, Runnable r) {
        SolidTests.after(h, t, r);
    }

    static ServerPlayer player(GameTestHelper h, Vec3 at) {
        return HollowbellGameTests.player(h, at);
    }

    /** a stone pad at y 200, far out where nothing else is; returns its middle, on it */
    static BlockPos pad(GameTestHelper h, int slot) {
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 1200, z = o.getZ() - 9000, y = 199;
        net.minecraft.server.level.ServerLevel l = h.getLevel();
        for (int cx = (x >> 4) - 8; cx <= (x >> 4) + 8; cx++)
            for (int cz = (z >> 4) - 8; cz <= (z >> 4) + 8; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int r = 72;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 1; dy <= 60; dy++)
                    if (!l.getBlockState(p.set(x + dx, y + dy, z + dz)).isAir())
                        l.setBlock(
                                p,
                                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                l.setBlock(
                        p.set(x + dx, y, z + dz),
                        net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        return new BlockPos(x, y + 1, z);
    }

    static void unpad(GameTestHelper h, BlockPos at) {
        for (int cx = (at.getX() >> 4) - 8; cx <= (at.getX() >> 4) + 8; cx++)
            for (int cz = (at.getZ() >> 4) - 8; cz <= (at.getZ() >> 4) + 8; cz++)
                h.getLevel().setChunkForced(cx, cz, false);
    }

    static HollowbellEntity giant(GameTestHelper h, BlockPos pad) {
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(pad.getX() + 0.5, pad.getY(), pad.getZ() + 0.5, 0f, 0f);
        e.setBellScale(0.3f);
        e.setVariant(HollowbellEntity.CALM);
        e.setMoveCooldown(100000);
        e.skipArrival();
        e.setStay(true);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    static void done(GameTestHelper h, HollowbellEntity e, BlockPos pad, Entity... others) {
        for (Entity o : others)
            if (o instanceof ServerPlayer p) HollowbellGameTests.drop(p);
            else if (o != null) o.discard();
        e.discard();
        unpad(h, pad);
        h.succeed();
    }

    /** p holds his book (his owner now) */
    static void makeOwner(ServerPlayer p) {
        BookRule.get(p.server).free();
        p.getInventory().add(new ItemStack(ModItems.CODEX));
        BookRule.get(p.server).look(List.of(p));
    }

    static void putOnList(ServerPlayer owner, ServerPlayer who) {
        BellWorld.get(owner.server).addFriend(owner.getUUID(), who.getUUID());
    }

    static void takeOffList(ServerPlayer owner, ServerPlayer who) {
        BellWorld.get(owner.server).dropFriend(owner.getUUID(), who.getUUID());
    }

    /** one of his big moves: he slams down */
    static boolean bigMove(GameTestHelper h, HollowbellEntity e) {
        return e.forceMove(Moves.SLAM, (LivingEntity) null);
    }

    static int moveLength() {
        return Moves.length(Moves.SLAM);
    }


    /** he walks a few blocks and turns (here: moved and turned at once) */
    static void walk(GameTestHelper h, HollowbellEntity e) {
        e.setYRot(e.getYRot() + 90f);
        e.setYBodyRot(e.getYRot());
        e.moveTo(e.getX() + 6, e.getY(), e.getZ(), e.getYRot(), 0f);
    }

    static int walkTicks() {
        return 3;
    }


    /** he is ready to be stood on (a short wait) */
    static void ready(GameTestHelper h, HollowbellEntity e, Runnable r) {
        after(h, 40, r);
    }

    // the same in every giant

    /** a spot on him to stand on (the highest with room), and a player put there */
    static Vec3 spot(GameTestHelper h, HollowbellEntity e) {
        List<Vec3> all = Sitting.spots(e);
        h.assertTrue(!all.isEmpty(), "there's somewhere on him to stand");
        return all.get(0);
    }

    static ServerPlayer onHim(GameTestHelper h, Vec3 at) {
        ServerPlayer p = player(h, at);
        p.teleportTo(h.getLevel(), at.x, at.y + 0.01, at.z, 0f, 0f);
        return p;
    }

    static void standAt(ServerPlayer p, Vec3 at) {
        p.teleportTo(p.serverLevel(), at.x, at.y + 0.01, at.z, 0f, 0f);
    }

    /** the first tick they are up (checked at once: a part of him that moves on would leave them in the air) */
    static void whenUp(GameTestHelper h, ServerPlayer p, int ticks, String what, Runnable then) {
        after(h, 1, () -> {
            if (!p.isPassenger()) then.run();
            else if (ticks <= 1) h.fail(what + ": still sitting");
            else whenUp(h, p, ticks - 1, what, then);
        });
    }

    static SitSeat seatOf(LivingEntity p) {
        return p.getVehicle() instanceof SitSeat s ? s : null;
    }

    /** the seat is on its spot of him (from his pose now), and the sitter on the seat */
    static void onItsSpot(GameTestHelper h, HollowbellEntity e, ServerPlayer p, String when) {
        SitSeat s = seatOf(p);
        h.assertTrue(s != null && !s.isRemoved(), when + ": still sitting");
        Vec3 want = Solid.anchorWorld(e, new Solid.Anchor(s.frame(), s.rest()));
        h.assertTrue(
                s.position().distanceTo(want) < 0.05,
                when + ": the seat is on its spot of him (off by " + s.position().distanceTo(want) + ")");
        h.assertTrue(
                Math.abs(p.getY() - (s.getY() + SitSeat.SIT_OVER - 0.6)) < 0.05
                        && Math.abs(p.getX() - s.getX()) < 0.05
                        && Math.abs(p.getZ() - s.getZ()) < 0.05,
                when + ": the sitter is on the seat");
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_1")
    public void onlyHisOwnerAndTheirSafeListSit(GameTestHelper h) {
        BlockPos pad = pad(h, 300);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at), friend = onHim(h, at), stranger = onHim(h, at);
            // wild: nobody has his book
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.NOT_ALLOWED, "nobody sits on a wild one");
            h.assertTrue(!owner.isPassenger(), "not sitting on a wild one");
            makeOwner(owner);
            putOnList(owner, friend);
            h.assertTrue(Sitting.trySit(stranger, e) == Sitting.Result.NOT_ALLOWED, "a stranger can't sit");
            h.assertTrue(!stranger.isPassenger(), "the stranger isn't sitting");
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            h.assertTrue(seatOf(owner) != null, "his owner is on a seat");
            h.assertTrue(Sitting.trySit(friend, e) == Sitting.Result.SAT, "someone on the safe list sits");
            h.assertTrue(seatOf(friend) != null, "the friend is on a seat");
            // standing on the ground, not on him: no
            ServerPlayer below = player(h, Vec3.atBottomCenterOf(pad).add(60, 0, 0));
            putOnList(owner, below);
            h.assertTrue(Sitting.trySit(below, e) == Sitting.Result.NOT_ON_HIM, "only standing on him");
            after(h, 2, () -> done(h, e, pad, owner, friend, stranger, below));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1400, batch = "sit_2")
    public void theSeatStaysOnItsSpotThroughHisMovesAndHisWalkingAndNothingOfHisHurts(GameTestHelper h) {
        BlockPos pad = pad(h, 301);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            float hp = owner.getHealth();
            Vec3 first = seatOf(owner).position();
            Vector3f rest = seatOf(owner).rest();
            h.assertTrue(
                    seatOf(owner).position().distanceTo(at) < 1.3,
                    "sat where they stood: " + seatOf(owner).position() + " / " + at);
            // sneaking doesn't get them up
            owner.setShiftKeyDown(true);
            after(h, 5, () -> {
                h.assertTrue(owner.isPassenger(), "crouching doesn't get them up");
                owner.setShiftKeyDown(false);
                onItsSpot(h, e, owner, "sat still");
                // his whole body moves in a big move: the seat goes with that part of him
                h.assertTrue(bigMove(h, e), "his big move starts");
                float[] most = {0f};
                for (int t = 4; t < moveLength(); t += 6) {
                    int tt = t;
                    after(h, t, () -> {
                        onItsSpot(h, e, owner, "in his move, tick " + tt);
                        most[0] = (float) Math.max(most[0], seatOf(owner).position().distanceTo(first));
                    });
                }
                after(h, moveLength() + 4, () -> {
                    h.assertTrue(most[0] > 0.3f, "the seat moved with him in his move (" + most[0] + ")");
                    h.assertTrue(owner.getHealth() >= hp, "nothing of his move hurt the sitter");
                    h.assertTrue(
                            seatOf(owner).rest().distance(rest) < 1e-4, "still the same spot of him after the move");
                    // walking and turning: moved and turned, the seat goes along
                    Vec3 before = seatOf(owner).position();
                    walk(h, e);
                    after(h, walkTicks(), () -> {
                        onItsSpot(h, e, owner, "after he walked and turned");
                        h.assertTrue(
                                seatOf(owner).position().distanceTo(before) > 1.0,
                                "the seat went with him when he walked and turned");
                        // his blows, his fire, falling, being squeezed into him: none of it hurts while sitting
                        float hp2 = owner.getHealth();
                        owner.hurt(h.getLevel().damageSources().mobAttack(e), 30f);
                        owner.hurt(h.getLevel().damageSources().fall(), 30f);
                        owner.hurt(h.getLevel().damageSources().inWall(), 30f);
                        owner.hurt(h.getLevel().damageSources().explosion(e, e), 30f);
                        h.assertTrue(owner.getHealth() >= hp2, "his blows don't hurt a sitter");
                        h.assertTrue(
                                !owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100), e)
                                        && !owner.hasEffect(MobEffects.POISON),
                                "his effects don't take hold on a sitter");
                        // (not everything: the sitter can still be hurt by other things)
                        owner.hurt(h.getLevel().damageSources().magic(), 5f);
                        h.assertTrue(owner.getHealth() < hp2, "other harm still lands on a sitter");
                        done(h, e, pad, owner);
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_3")
    public void jumpingGetsYouUpStandingOnTheSameSpot(GameTestHelper h) {
        BlockPos pad = pad(h, 302);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            after(h, 5, () -> {
                Vec3 seat = seatOf(owner).spotNow(e);
                float hp = owner.getHealth();
                owner.setJumping(true);
                whenUp(h, owner, 4, "jumping", () -> {
                    h.assertTrue(
                            owner.position().distanceTo(seat) < 2.6,
                            "up on the same spot: " + owner.position() + " / " + seat);
                    h.assertTrue(Solid.onTop(e, owner, 0.3, 0.3), "standing on him, on the spot");
                    h.assertTrue(
                            Solid.clear(e, owner, owner.getBoundingBox().deflate(0.05)),
                            "not inside him or the ground");
                    h.assertTrue(owner.fallDistance == 0f && owner.getHealth() >= hp, "no fall damage");
                    h.assertTrue(
                            h.getLevel().getEntitiesOfClass(SitSeat.class, e.getBoundingBox().inflate(300)).isEmpty(),
                            "the seat is gone");
                    // and sitting again works
                    owner.setJumping(false);
                    h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "sits again");
                    done(h, e, pad, owner);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_4")
    public void takenOffTheSafeListYouGetUpSafely(GameTestHelper h) {
        BlockPos pad = pad(h, 303);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = player(h, Vec3.atBottomCenterOf(pad).add(40, 0, 0)), friend = onHim(h, at);
            makeOwner(owner);
            putOnList(owner, friend);
            h.assertTrue(Sitting.trySit(friend, e) == Sitting.Result.SAT, "the friend sits");
            float hp = friend.getHealth();
            after(h, 5, () -> {
                takeOffList(owner, friend);
                whenUp(h, friend, 12, "off the list", () -> {
                    h.assertTrue(
                            Solid.onTop(e, friend, 0.3, 0.3) || friend.getY() < e.getY() + 3,
                            "set down standing on him or on the ground beside him: " + friend.position() + ", he is at "
                                    + e.position());
                    h.assertTrue(friend.fallDistance == 0f && friend.getHealth() >= hp, "unhurt");
                    h.assertTrue(Sitting.trySit(friend, e) != Sitting.Result.SAT, "and can't sit again");
                    done(h, e, pad, owner, friend);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_5")
    public void whenHeGoesTheSitterIsSetDownBesideHim(GameTestHelper h) {
        BlockPos pad = pad(h, 304);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            float hp = owner.getHealth();
            after(h, 3, () -> {
                e.discard();
                after(h, 4, () -> {
                    h.assertTrue(!owner.isPassenger(), "he went: not sitting any more");
                    h.assertTrue(
                            owner.getY() < pad.getY() + 4 && owner.getY() > pad.getY() - 2,
                            "set down on the ground: " + owner.position());
                    h.assertTrue(owner.getHealth() >= hp && owner.fallDistance == 0f, "unhurt");
                    h.assertTrue(
                            h.getLevel().getEntitiesOfClass(SitSeat.class, owner.getBoundingBox().inflate(300)).isEmpty(),
                            "no seat left behind");
                    done(h, e, pad, owner);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "sit_6")
    public void whenHeDiesOrYouLeaveTheSittingEnds(GameTestHelper h) {
        BlockPos pad = pad(h, 305);
        HollowbellEntity e = giant(h, pad);
        ready(h, e, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at), friend = onHim(h, at);
            makeOwner(owner);
            putOnList(owner, friend);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            h.assertTrue(Sitting.trySit(friend, e) == Sitting.Result.SAT, "the friend sits");
            // the friend leaves the game: put down on the ground first
            h.getLevel().getServer().getPlayerList().remove(friend);
            h.assertTrue(!friend.isPassenger(), "leaving the game ends the sitting");
            float hp = owner.getHealth();
            after(h, 3, () -> {
                e.kill();
                after(h, 4, () -> {
                    h.assertTrue(!owner.isPassenger(), "he died: not sitting any more");
                    h.assertTrue(owner.getHealth() >= hp && owner.fallDistance == 0f, "unhurt");
                    after(h, 4, () -> {
                        h.assertTrue(
                                h.getLevel().getEntitiesOfClass(SitSeat.class, e.getBoundingBox().inflate(300)).isEmpty(),
                                "no seat left behind");
                        done(h, e, pad, owner);
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sit_7")
    public void theTripleCrouchCountsThreePressesInTwoSeconds(GameTestHelper h) {
        TripleCrouch c = new TripleCrouch();
        h.assertTrue(TripleCrouch.WINDOW_MS == 2000, "the window should be two seconds");
        h.assertTrue(!c.press(0) && !c.press(300) && c.press(600), "three quick presses sit you down");
        c.reset();
        h.assertTrue(!c.press(0) && !c.press(900) && c.press(1900), "three presses within two seconds sit you down");
        c.reset();
        h.assertTrue(!c.press(0) && !c.press(800), "two presses don't");
        h.assertTrue(!c.press(3000), "and a third long after doesn't either");
        c.reset();
        h.assertTrue(!c.press(0) && !c.press(1200) && !c.press(2400), "slow presses over two seconds don't");
        h.assertTrue(c.press(3000), "the last three of a run do, once within two seconds");
        h.assertTrue(c.count() == 0, "the run starts again after it sits you");
        h.assertTrue(!c.press(4000) && !c.press(4100), "a fresh run");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sit_8")
    public void theControlsTabReadsAndSetsTheGesture(GameTestHelper h) {
        try {
            Path f = Files.createTempFile("sit", ".json");
            Files.writeString(f, "{\"other\":5}");
            SitSettings.file = f;
            SitSettings.reload();
            h.assertTrue(
                    GiantsBridge.giants(null, "controlget", "tripleCrouchSit").equals(List.of("true")),
                    "on to start with");
            h.assertTrue(
                    GiantsBridge.giants(null, "controlset", "{\"controls\":1,\"config\":\"tripleCrouchSit\",\"value\":false}")
                            != null,
                    "set answers");
            h.assertTrue(
                    GiantsBridge.giants(null, "controlget", "tripleCrouchSit").equals(List.of("false")), "now off");
            SitSettings.reload();
            h.assertTrue(!SitSettings.tripleCrouchSit(), "kept in the file");
            JsonObject kept = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            h.assertTrue(kept.get("other").getAsInt() == 5, "the file's other settings are kept");
            h.assertTrue(GiantsBridge.giants(null, "controlget", "somethingElse") == null, "not mine: null");
            h.assertTrue(
                    GiantsBridge.giants(null, "controlset", "{\"controls\":1,\"config\":\"nope\",\"value\":true}")
                            == null,
                    "not mine: null");
            Files.deleteIfExists(f);
            SitSettings.file = null;
            SitSettings.reload();
            // the guide's file: the gesture on the sit key's row
            String mod = Sitting.SEAT.builtInRegistryHolder().key().location().getNamespace();
            var in = SitTests.class.getResourceAsStream("/assets/" + mod + "/giants_controls.json");
            h.assertTrue(in != null, "giants_controls.json is in the jar");
            JsonObject c = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject g = c.getAsJsonArray("gestures").get(0).getAsJsonObject();
            h.assertTrue(c.get("controls").getAsInt() == 1, "version 1");
            h.assertTrue(g.get("config").getAsString().equals("tripleCrouchSit"), "the gesture's setting");
            h.assertTrue(g.get("key").getAsString().equals("key." + mod + ".sit_on_giant"), "on the sit key's row");
            h.assertTrue(c.getAsJsonObject("keys").has("key." + mod + ".sit_on_giant"), "the sit key says what it does");
            var lang = SitTests.class.getResourceAsStream("/assets/" + mod + "/lang/en_us.json");
            JsonObject l = JsonParser.parseReader(new InputStreamReader(lang, StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertTrue(l.get("key." + mod + ".sit_on_giant").getAsString().equals("Sit on giant"), "its name");
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
        h.succeed();
    }
}
