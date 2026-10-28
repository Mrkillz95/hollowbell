package net.jj.hollowbell.test;

import static net.jj.hollowbell.test.BellKit.after;
import static net.jj.hollowbell.test.BellKit.bell;
import static net.jj.hollowbell.test.BellKit.done;
import static net.jj.hollowbell.test.BellKit.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.solid.SitSeat;
import net.jj.hollowbell.solid.SitSettings;
import net.jj.hollowbell.solid.Sitting;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.TripleCrouch;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sit anywhere on him: only his owner (whoever holds his book) and their safe list may; the seat stays on its spot of
 * him through his moves and his drifting; jumping gets you up on that spot; nothing of his hurts you while you sit;
 * it ends safely when he goes or dies, when you're taken off the list and when you leave; the triple crouch's
 * counter, and its setting kept on your own computer.
 */
public class SitTests implements FabricGameTest {
    static final float SIZE = 0.3f;

    static BlockPos at(GameTestHelper h, int slot) { return BellKit.spot(h, 40 + slot, BellKit.chunksFor(SIZE)); }

    static HollowbellEntity giant(GameTestHelper h, BlockPos at) {
        HollowbellEntity e = bell(h, at, SIZE);
        e.setStay(true);
        return e;
    }

    /** p holds his book (his owner now) */
    static void makeOwner(ServerPlayer p) { p.getInventory().add(new ItemStack(ModItems.CODEX)); }

    static void putOnList(ServerPlayer owner, ServerPlayer who) { BellWorld.get(owner.server).addFriend(owner.getUUID(), who.getUUID()); }

    static void takeOffList(ServerPlayer owner, ServerPlayer who) { BellWorld.get(owner.server).dropFriend(owner.getUUID(), who.getUUID()); }

    /** he is ready to be stood on (a short wait) */
    static void ready(GameTestHelper h, Runnable r) { after(h, 40, r); }

    /** a spot on him to stand on: the highest with room (the top of his crown) */
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

    /** the first tick they are up (checked at once: a part of him that moves on would leave them in the air) */
    static void whenUp(GameTestHelper h, ServerPlayer p, int ticks, String what, Runnable then) {
        after(h, 1, () -> {
            if (!p.isPassenger()) then.run();
            else if (ticks <= 1) h.fail(what + ": still sitting");
            else whenUp(h, p, ticks - 1, what, then);
        });
    }

    static SitSeat seatOf(LivingEntity p) { return p.getVehicle() instanceof SitSeat s ? s : null; }

    /** the seat is on its spot of him (from his pose now), and the sitter on the seat */
    static void onItsSpot(GameTestHelper h, HollowbellEntity e, ServerPlayer p, String when) {
        SitSeat s = seatOf(p);
        h.assertTrue(s != null && !s.isRemoved(), when + ": still sitting");
        Vec3 want = Solid.anchorWorld(e, new Solid.Anchor(s.frame(), s.rest()));
        h.assertTrue(s.position().distanceTo(want) < 0.05, when + ": the seat is on its spot of him (off by " + s.position().distanceTo(want) + ")");
        h.assertTrue(Math.abs(p.getY() - (s.getY() + SitSeat.SIT_OVER - 0.6)) < 0.05 && Math.abs(p.getX() - s.getX()) < 0.05
                && Math.abs(p.getZ() - s.getZ()) < 0.05, when + ": the sitter is on the seat");
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_1")
    public void onlyHisOwnerAndTheirSafeListSit(GameTestHelper h) {
        BlockPos pad = at(h, 0);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
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
            ServerPlayer below = player(h, new Vec3(e.getX() + 60, e.groundAt(e.getX() + 60, e.getZ()), e.getZ()));
            putOnList(owner, below);
            h.assertTrue(Sitting.trySit(below, e) == Sitting.Result.NOT_ON_HIM, "only standing on him");
            after(h, 2, () -> done(h, e, pad, BellKit.chunksFor(SIZE), owner, friend, stranger, below));
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 1200, batch = "sit_2")
    public void theSeatStaysOnItsSpotThroughHisMovesAndHisDriftingAndNothingOfHisHurts(GameTestHelper h) {
        BlockPos pad = at(h, 1);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            float hp = owner.getHealth();
            Vec3 first = seatOf(owner).position();
            Vector3f rest = seatOf(owner).rest();
            h.assertTrue(seatOf(owner).position().distanceTo(at) < 1.3, "sat where they stood: " + seatOf(owner).position() + " / " + at);
            owner.setShiftKeyDown(true);
            after(h, 5, () -> {
                h.assertTrue(owner.isPassenger(), "crouching doesn't get them up");
                owner.setShiftKeyDown(false);
                onItsSpot(h, e, owner, "sat still");
                // the drop: his whole bell comes down onto the ground and goes back up, the seat with it
                h.assertTrue(e.forceMove(Moves.DROP, null), "the drop starts");
                int len = Moves.length(Moves.DROP);
                float[] most = {0f};
                for (int t = 4; t < len; t += 9) {
                    int tt = t;
                    after(h, t, () -> {
                        onItsSpot(h, e, owner, "in the drop, tick " + tt);
                        most[0] = (float) Math.max(most[0], seatOf(owner).position().distanceTo(first));
                    });
                }
                after(h, len + 4, () -> {
                    h.assertTrue(most[0] > 1f, "the seat went down with him in the drop (" + most[0] + ")");
                    h.assertTrue(owner.getHealth() >= hp, "nothing of the drop hurt the sitter");
                    h.assertTrue(seatOf(owner).rest().distance(rest) < 1e-4, "still the same spot of him after the move");
                    // drifting along: the seat goes with him
                    Vec3 before = seatOf(owner).position();
                    e.moveTo(e.getX() + 6, e.getY(), e.getZ(), e.getYRot(), 0f);
                    after(h, 3, () -> {
                        onItsSpot(h, e, owner, "after he moved");
                        h.assertTrue(seatOf(owner).position().distanceTo(before) > 1.0, "the seat went with him when he moved");
                        // his blows, falling, being squeezed into him: none of it hurts while sitting
                        float hp2 = owner.getHealth();
                        owner.hurt(h.getLevel().damageSources().mobAttack(e), 30f);
                        owner.hurt(h.getLevel().damageSources().fall(), 30f);
                        owner.hurt(h.getLevel().damageSources().inWall(), 30f);
                        owner.hurt(h.getLevel().damageSources().explosion(e, e), 30f);
                        h.assertTrue(owner.getHealth() >= hp2, "his blows don't hurt a sitter");
                        h.assertTrue(!owner.addEffect(new MobEffectInstance(MobEffects.POISON, 100), e) && !owner.hasEffect(MobEffects.POISON),
                                "his stings don't take hold on a sitter");
                        owner.hurt(h.getLevel().damageSources().magic(), 5f);
                        h.assertTrue(owner.getHealth() < hp2, "other harm still lands on a sitter");
                        done(h, e, pad, BellKit.chunksFor(SIZE), owner);
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_3")
    public void jumpingGetsYouUpStandingOnTheSameSpot(GameTestHelper h) {
        BlockPos pad = at(h, 2);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            after(h, 5, () -> {
                Vec3 seat = seatOf(owner).spotNow(e);
                float hp = owner.getHealth();
                owner.setJumping(true);
                whenUp(h, owner, 4, "jumping", () -> {
                    h.assertTrue(owner.position().distanceTo(seat) < 2.6, "up on the same spot: " + owner.position() + " / " + seat);
                    h.assertTrue(Solid.onTop(e, owner, 0.3, 0.3), "standing on him, on the spot");
                    h.assertTrue(Solid.clear(e, owner, owner.getBoundingBox().deflate(0.05)), "not inside him or the ground");
                    h.assertTrue(owner.fallDistance == 0f && owner.getHealth() >= hp, "no fall damage");
                    h.assertTrue(h.getLevel().getEntitiesOfClass(SitSeat.class, e.bodyBox()).isEmpty(), "the seat is gone");
                    owner.setJumping(false);
                    h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "sits again");
                    done(h, e, pad, BellKit.chunksFor(SIZE), owner);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_4")
    public void takenOffTheSafeListYouGetUpSafely(GameTestHelper h) {
        BlockPos pad = at(h, 3);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = player(h, new Vec3(e.getX() + 40, e.groundAt(e.getX() + 40, e.getZ()), e.getZ())), friend = onHim(h, at);
            makeOwner(owner);
            putOnList(owner, friend);
            h.assertTrue(Sitting.trySit(friend, e) == Sitting.Result.SAT, "the friend sits");
            float hp = friend.getHealth();
            after(h, 5, () -> {
                takeOffList(owner, friend);
                whenUp(h, friend, 12, "off the list", () -> {
                    double g = e.groundAt(friend.getX(), friend.getZ());
                    h.assertTrue(Solid.onTop(e, friend, 0.3, 0.3) || Math.abs(friend.getY() - g) < 3,
                            "set down standing on him or on the ground beside him: " + friend.position() + ", he is at " + e.position());
                    h.assertTrue(friend.fallDistance == 0f && friend.getHealth() >= hp, "unhurt");
                    h.assertTrue(Sitting.trySit(friend, e) != Sitting.Result.SAT, "and can't sit again");
                    done(h, e, pad, BellKit.chunksFor(SIZE), owner, friend);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 400, batch = "sit_5")
    public void whenHeGoesTheSitterIsSetDownBesideHim(GameTestHelper h) {
        BlockPos pad = at(h, 4);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
            Vec3 at = spot(h, e);
            ServerPlayer owner = onHim(h, at);
            makeOwner(owner);
            h.assertTrue(Sitting.trySit(owner, e) == Sitting.Result.SAT, "his owner sits");
            float hp = owner.getHealth();
            after(h, 3, () -> {
                e.discard();
                after(h, 4, () -> {
                    h.assertTrue(!owner.isPassenger(), "he went: not sitting any more");
                    double g = e.groundAt(owner.getX(), owner.getZ());
                    h.assertTrue(Math.abs(owner.getY() - g) < 4, "set down on the ground: " + owner.position() + " (ground " + g + ")");
                    h.assertTrue(owner.getHealth() >= hp && owner.fallDistance == 0f, "unhurt");
                    h.assertTrue(h.getLevel().getEntitiesOfClass(SitSeat.class, owner.getBoundingBox().inflate(300)).isEmpty(), "no seat left behind");
                    done(h, e, pad, BellKit.chunksFor(SIZE), owner);
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 600, batch = "sit_6")
    public void whenHeDiesOrYouLeaveTheSittingEnds(GameTestHelper h) {
        BlockPos pad = at(h, 5);
        HollowbellEntity e = giant(h, pad);
        ready(h, () -> {
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
                        h.assertTrue(h.getLevel().getEntitiesOfClass(SitSeat.class, e.bodyBox().inflate(50)).isEmpty(), "no seat left behind");
                        done(h, e, pad, BellKit.chunksFor(SIZE), owner);
                    });
                });
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sit_7")
    public void theTripleCrouchCountsThreePressesInASecond(GameTestHelper h) {
        TripleCrouch c = new TripleCrouch();
        h.assertTrue(!c.press(0) && !c.press(300) && c.press(600), "three presses within a second sit you down");
        c.reset();
        h.assertTrue(!c.press(0) && !c.press(400), "two presses don't");
        h.assertTrue(!c.press(1500), "and a third long after doesn't either");
        c.reset();
        h.assertTrue(!c.press(0) && !c.press(600) && !c.press(1200), "slow presses don't");
        h.assertTrue(c.press(1500), "the last three of a run do, once within a second");
        h.assertTrue(c.count() == 0, "the run starts again after it sits you");
        h.assertTrue(!c.press(2000) && !c.press(2100), "a fresh run");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "sit_8")
    public void theTripleCrouchSettingIsKeptOnYourComputer(GameTestHelper h) {
        try {
            Path f = Files.createTempFile("sit", ".json");
            Files.writeString(f, "{\"other\":5}");
            SitSettings.file = f;
            SitSettings.reload();
            h.assertTrue(SitSettings.tripleCrouchSit(), "on to start with");
            SitSettings.setTripleCrouchSit(false);
            SitSettings.reload();
            h.assertTrue(!SitSettings.tripleCrouchSit(), "kept in the file");
            JsonObject kept = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            h.assertTrue(kept.get("other").getAsInt() == 5, "the file's other settings are kept");
            h.assertTrue(SitSettings.controlGet("tripleCrouchSit").equals(List.of("false")), "read back");
            h.assertTrue(SitSettings.controlGet("somethingElse") == null, "not mine: null");
            Files.deleteIfExists(f);
            SitSettings.file = null;
            SitSettings.reload();
            var lang = SitTests.class.getResourceAsStream("/assets/hollowbell/lang/en_us.json");
            JsonObject l = JsonParser.parseReader(new InputStreamReader(lang, StandardCharsets.UTF_8)).getAsJsonObject();
            h.assertTrue(l.get("key.hollowbell.sit_on_giant").getAsString().equals("Sit on giant"), "the key's name");
            h.assertTrue(l.has("message.hollowbell.sit.down") && l.has("message.hollowbell.sit.off_list"), "its messages");
        } catch (java.io.IOException ex) {
            throw new RuntimeException(ex);
        }
        h.succeed();
    }
}
