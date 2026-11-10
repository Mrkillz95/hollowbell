package net.jj.hollowbell.test;

import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.GiantsMinigame;
import net.jj.hollowbell.GiantsSystems;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.fx.BigFx;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Him at his smallest (0.01, 0.03, 0.06), on land and in water, with a player and a cow beside him: the player can
 * hurt him with a sword, an arrow and a trident, again and again, and he takes a fair number of blows to fell; then
 * he walks and does every move of his, and nothing he does throws the player or the cow about, carries them off or
 * hurts them more than a small thing could, and a stand further off is never touched. He doesn't jump about either.
 *
 * <p>The same test is in every giant's mod (it came from Pitchgut's); only the part marked "the giant" at the end
 * differs.
 */
public class TinySizeTests implements FabricGameTest {
    static final Logger LOG = LoggerFactory.getLogger("tiny-size-tests");
    static final float[] SIZES = {0.01f, 0.03f, 0.06f};
    /** the furthest a tiny one may move anybody in a tick (out of his way when he walks into them) */
    static final double MAX_MOVED = 2.0;

    @GameTestGenerator
    public Collection<TestFunction> tiny() {
        List<TestFunction> out = new ArrayList<>();
        int slot = SLOT;
        for (float s : SIZES)
            for (boolean water : new boolean[] {false, true}) {
                String name = "tiny_" + (water ? "water_" : "land_") + String.valueOf(s).replace('.', '_');
                int at = slot;
                slot += 2;
                out.add(new TestFunction("tiny", name, EMPTY_STRUCTURE, 6000, 0, true, h -> run(h, s, water, at)));
            }
        return out;
    }

    /** how far off the stand nothing of his may reach is */
    static double farFor(double reach) {
        return Math.max(8.0, 3 * reach + 4);
    }

    static HollowbellEntity spawnIn(GameTestHelper h, float s, boolean water, int slot) {
        ServerLevel l = h.getLevel();
        BlockPos o = h.absolutePos(BlockPos.ZERO);
        int x = o.getX() + 30000 + slot * 600, z = o.getZ();
        for (int cx = (x >> 4) - 5; cx <= (x >> 4) + 5; cx++)
            for (int cz = (z >> 4) - 5; cz <= (z >> 4) + 5; cz++) {
                l.setChunkForced(cx, cz, true);
                l.getChunk(cx, cz);
            }
        int g = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int flat = (int) Math.max(16, Math.min(48, farFor(FULL_REACH * s) + 4));
        // flat ground (or a pool three deep) all round, so nothing but him moves anybody
        for (int dx = -flat; dx <= flat; dx++)
            for (int dz = -flat; dz <= flat; dz++)
                for (int y = g - 5; y <= g + 8; y++) {
                    BlockPos p = new BlockPos(x + dx, y, z + dz);
                    if (y < g - 3) l.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
                    else if (y < g)
                        l.setBlock(p, water ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState(), 2);
                    else l.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
                }
        return spawn(h, new Vec3(x + 0.5, water ? g - 3 : g, z + 0.5), s);
    }

    /** which of his code did it: the first line of the mod (not of the tests) on the stack */
    static String culprit() {
        for (StackTraceElement f : Thread.currentThread().getStackTrace()) {
            String c = f.getClassName();
            if (c.startsWith(MOD) && !c.startsWith(MOD + ".test")) {
                return c.substring(c.lastIndexOf('.') + 1) + "." + f.getMethodName() + ":" + f.getLineNumber();
            }
        }
        return "?";
    }

    /** what threw, carried or hurt whom, and how much at most: "what culprit" -> most */
    static final java.util.Map<Object, java.util.Map<String, Double>> WHY = new java.util.IdentityHashMap<>();

    static synchronized void why(Object who, String what, double v) {
        WHY.computeIfAbsent(who, k -> new java.util.TreeMap<>()).merge(what + " " + culprit(), v, Math::max);
    }

    static synchronized String whyOf(Object who, double over) {
        var m = WHY.remove(who);
        if (m == null) return "";
        StringBuilder sb = new StringBuilder();
        m.forEach((k, v) -> {
            if (v > over && !k.endsWith(" ?")) sb.append(String.format("%n      %s %.2f", k, v));
        });
        return sb.toString();
    }

    /** the hardest single blow each one took since last looked at (several at once are several blows) */
    static final java.util.Map<Object, Double> BLOW = new java.util.concurrent.ConcurrentHashMap<>();

    /** how hard something was thrown: sideways and up together (a fall is the ground's doing, not his) */
    static double thrown(Vec3 v) {
        double up = Math.max(0, v.y);
        return Math.sqrt(v.x * v.x + up * up + v.z * v.z);
    }

    /** a test player that notes what of his set it moving, moved it or hurt it */
    @SuppressWarnings("deprecation")
    static ServerPlayer player(GameTestHelper h, Vec3 at, String name) {
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), name), false);
        var p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(),
                cookie.clientInformation()) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public void setDeltaMovement(Vec3 v) {
                why(this, "threw", thrown(v));
                super.setDeltaMovement(v);
            }

            @Override
            public void teleportTo(double x, double y, double z) {
                why(this, "moved", position().distanceTo(new Vec3(x, y, z)));
                super.teleportTo(x, y, z);
            }

            @Override
            public void teleportTo(ServerLevel l, double x, double y, double z, float yr, float xr) {
                why(this, "moved", position().distanceTo(new Vec3(x, y, z)));
                super.teleportTo(l, x, y, z, yr, xr);
            }

            @Override
            public boolean hurt(net.minecraft.world.damagesource.DamageSource src, float amount) {
                why(this, "hurt(" + src.getMsgId() + ")", amount);
                BLOW.merge(this, (double) amount, Math::max);
                return super.hurt(src, amount);
            }
        };
        var c = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(c);
        h.getLevel().getServer().getPlayerList().placeNewPlayer(c, p, cookie);
        p.teleportTo(h.getLevel(), at.x, at.y, at.z, 0f, 0f);
        try {
            var f = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            f.setAccessible(true);
            f.setInt(p, 0);
        } catch (ReflectiveOperationException ignored) {
        }
        p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        return p;
    }

    static Cow cow(GameTestHelper h, Vec3 at) {
        Cow c = new Cow(EntityType.COW, h.getLevel()) {
            @Override
            public void setDeltaMovement(Vec3 v) {
                why(this, "threw", thrown(v));
                super.setDeltaMovement(v);
            }

            @Override
            public boolean hurt(net.minecraft.world.damagesource.DamageSource src, float amount) {
                why(this, "hurt(" + src.getMsgId() + ")", amount);
                BLOW.merge(this, (double) amount, Math::max);
                return super.hurt(src, amount);
            }
        };
        c.moveTo(at.x, at.y, at.z, 0f, 0f);
        c.setNoAi(true);
        c.setPersistenceRequired();
        h.getLevel().addFreshEntity(c);
        return c;
    }

    /**
     * the player stands on the ground beside him where a look at him from their eyes meets him in reach: from just
     * off his side, inward step by step (under his belly, if that is where he can be reached)
     */
    static void standBy(ServerPlayer p, HollowbellEntity e) {
        AABB b = box(e);
        Vec3 c = b.getCenter();
        double feet = e.getY();
        for (double dz : new double[] {0, 0.2, -0.2, 0.4, -0.4})
        for (double x = b.maxX + 1.5; x > c.x; x -= 0.25) {
            double z = c.z + dz * b.getZsize();
            Vec3 eye = new Vec3(x, feet + 1.62, z);
            for (double pitch : new double[] {0, 20, 40, 60, -15, -30, -45, -60}) {
                double r = Math.toRadians(pitch);
                Vec3 look = new Vec3(-Math.cos(r), Math.sin(r), 0);
                Double t = lookMeets(e, eye, look, 2.8);
                if (t != null && t > 0.3) {
                    p.teleportTo(p.serverLevel(), x, feet, z, 0f, 0f);
                    p.lookAt(EntityAnchorArgument.Anchor.EYES, eye.add(look));
                    return;
                }
            }
        }
        p.teleportTo(p.serverLevel(), b.maxX + 1.5, feet, c.z, 0f, 0f);
        p.lookAt(EntityAnchorArgument.Anchor.EYES, c);
    }

    /** a full swing: the test player isn't ticked, so its swing never charges by itself */
    static void charged(ServerPlayer p) {
        try {
            var f = LivingEntity.class.getDeclaredField("attackStrengthTicker");
            f.setAccessible(true);
            f.setInt(p, 200);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Method fluidUpdate;

    /** the test player isn't ticked by a game: whether it is in water, as a game would work it out */
    static void wet(ServerPlayer p) {
        try {
            if (fluidUpdate == null) {
                fluidUpdate = Entity.class.getDeclaredMethod("updateInWaterStateAndDoFluidPushing");
                fluidUpdate.setAccessible(true);
            }
            fluidUpdate.invoke(p);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void run(GameTestHelper h, float s, boolean water, int slot) {
        HollowbellEntity e = spawnIn(h, s, water, slot);
        String where = "size " + s + (water ? " in water" : " on land");
        ServerPlayer[] pl = {null};
        Cow[] cows = {null};
        ArmorStand[] farStand = {null};
        Vec3[] spot = {null, null, null};
        float[] hp = {0};
        double[] most = new double[8];
        String[] mostWhen = new String[8];
        String[] now = {"standing"};
        StringBuilder log = new StringBuilder(where + ": ");
        int[] hits = {0};
        boolean[] measuring = {false};
        Vec3[] lastHim = {null};
        Vec3[] lastAt = {Vec3.ZERO};
        boolean[] wasHeld = {false};
        Entity[] thrownAt = {null};
        double[] tall = {0}, longest = {0};

        h.runAfterDelay(60, () -> {
            stay(e, true);
            Vec3 c = box(e).getCenter();
            double back = Math.max(0.5, box(e).getXsize() / 2) + 1.8;
            Vec3 feet = new Vec3(c.x + back, e.getY(), c.z);
            ServerPlayer p = player(h, feet, "tiny-" + slot);
            pl[0] = p;
            spot[0] = feet;
            p.lookAt(EntityAnchorArgument.Anchor.EYES, c);
            p.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(7.0); // an iron sword
            cows[0] = cow(h, new Vec3(c.x - back, e.getY(), c.z));
            spot[1] = cows[0].position();
            double far = farFor(reach(e));
            // (a stand, not a creature: he may well go after a cow anywhere near, which is fair)
            var st = new ArmorStand(h.getLevel(), c.x, e.getY(), c.z + far) {
                @Override
                public void setDeltaMovement(Vec3 v) {
                    why(this, "threw", thrown(v));
                    super.setDeltaMovement(v);
                }
            };
            h.getLevel().addFreshEntity(st);
            farStand[0] = st;
            hp[0] = healthNow(e);
            tall[0] = box(e).getYsize();
            longest[0] = Math.max(box(e).getXsize(), Math.max(box(e).getYsize(), box(e).getZsize()));
            log.append(String.format("his reach %.2f, box %.2f x %.2f x %.2f, health %.0f; ", reach(e),
                    box(e).getXsize(), box(e).getYsize(), box(e).getZsize(), healthMax(e)));
        });
        // a sword: the look at him finds him, and the swing takes health; again after the game's pause between hits
        for (int k = 0; k < 3; k++) {
            int n = k;
            h.runAfterDelay(70 + 12 * k, () -> {
                ServerPlayer p = pl[0];
                standBy(p, e); // (he may have moved: the player keeps up)
                Entity part = lookedAt(h, e, p.getEyePosition(), p.getViewVector(1f), p.entityInteractionRange());
                if (part == null) LOG.info("tiny {}", log);
                h.assertTrue(part != null, where + ": a look at him from "
                        + String.format("%.1f", p.getEyePosition().distanceTo(box(e).getCenter()))
                        + " blocks off his middle doesn't find him (" + state(e) + ")");
                float before = healthNow(e);
                charged(p);
                swing(e, p, part);
                h.assertTrue(healthNow(e) < before, where + ": sword hit " + (n + 1) + " didn't hurt him ("
                        + before + " -> " + healthNow(e) + ")");
                if (n == 0) {
                    float took = before - healthNow(e);
                    hits[0] = (int) Math.ceil(healthMax(e) / took);
                    log.append(String.format("a sword takes %.1f, %d blows to fell him; ", took, hits[0]));
                }
            });
        }
        // an arrow and a trident from a few blocks off
        h.runAfterDelay(110, () -> {
            hp[0] = struck(e);
            Vec3 c = box(e).getCenter();
            // (from the player's eyes, along their look at him: where their sword landed)
            standBy(pl[0], e);
            Vec3 eye = pl[0].getEyePosition(), look = pl[0].getViewVector(1f);
            Arrow a = new Arrow(h.getLevel(), pl[0], new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a.setPos(eye.x - look.x * 0.3, eye.y - look.y * 0.3, eye.z - look.z * 0.3);
            a.shoot(look.x, look.y, look.z, 2.5f, 0f);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(a);
            // (and one at the middle of him, as a player would loose a second)
            Vec3 mid = box(e).getCenter().subtract(eye).normalize();
            Arrow a2 = new Arrow(h.getLevel(), pl[0], new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a2.setPos(eye.x, eye.y, eye.z);
            a2.shoot(mid.x, mid.y, mid.z, 2.5f, 0f);
            a2.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(a2);
        });
        h.runAfterDelay(120, () -> {
            if (struck(e) < hp[0]) return; // (a second volley, if the first went by him)
            Vec3 c = box(e).getCenter();
            // (from the player's eyes, along their look at him: where their sword landed)
            standBy(pl[0], e);
            Vec3 eye = pl[0].getEyePosition(), look = pl[0].getViewVector(1f);
            Arrow a = new Arrow(h.getLevel(), pl[0], new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a.setPos(eye.x - look.x * 0.3, eye.y - look.y * 0.3, eye.z - look.z * 0.3);
            a.shoot(look.x, look.y, look.z, 2.5f, 0f);
            a.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(a);
            // (and one at the middle of him, as a player would loose a second)
            Vec3 mid = box(e).getCenter().subtract(eye).normalize();
            Arrow a2 = new Arrow(h.getLevel(), pl[0], new ItemStack(Items.ARROW), new ItemStack(Items.BOW));
            a2.setPos(eye.x, eye.y, eye.z);
            a2.shoot(mid.x, mid.y, mid.z, 2.5f, 0f);
            a2.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(a2);
        });
        h.runAfterDelay(130, () -> {
            h.assertTrue(struck(e) < hp[0], where + ": an arrow didn't hurt him");
            hp[0] = struck(e);
            Vec3 c = box(e).getCenter();
            standBy(pl[0], e);
            Vec3 eye = pl[0].getEyePosition(), look = pl[0].getViewVector(1f);
            e.invulnerableTime = 0; // (the arrow's hurt time is over by now, as it would be for a player's next throw)
            ThrownTrident t = new ThrownTrident(h.getLevel(), pl[0], new ItemStack(Items.TRIDENT));
            t.setPos(eye.x - look.x * 0.3, eye.y - look.y * 0.3, eye.z - look.z * 0.3);
            t.shoot(look.x, look.y, look.z, 2.5f, 0f);
            t.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(t);
            // (and one at the middle of him)
            Vec3 mid = box(e).getCenter().subtract(eye).normalize();
            ThrownTrident t2 = new ThrownTrident(h.getLevel(), pl[0], new ItemStack(Items.TRIDENT));
            t2.setPos(eye.x, eye.y, eye.z);
            t2.shoot(mid.x, mid.y, mid.z, 2.5f, 0f);
            t2.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(t2);
            thrownAt[0] = t;
        });
        h.runAfterDelay(140, () -> {
            if (struck(e) < hp[0]) return; // (a second throw, if the first went by him)
            Vec3 c = box(e).getCenter();
            standBy(pl[0], e);
            Vec3 eye = pl[0].getEyePosition(), look = pl[0].getViewVector(1f);
            e.invulnerableTime = 0; // (the arrow's hurt time is over by now, as it would be for a player's next throw)
            ThrownTrident t = new ThrownTrident(h.getLevel(), pl[0], new ItemStack(Items.TRIDENT));
            t.setPos(eye.x - look.x * 0.3, eye.y - look.y * 0.3, eye.z - look.z * 0.3);
            t.shoot(look.x, look.y, look.z, 2.5f, 0f);
            t.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(t);
            // (and one at the middle of him)
            Vec3 mid = box(e).getCenter().subtract(eye).normalize();
            ThrownTrident t2 = new ThrownTrident(h.getLevel(), pl[0], new ItemStack(Items.TRIDENT));
            t2.setPos(eye.x, eye.y, eye.z);
            t2.shoot(mid.x, mid.y, mid.z, 2.5f, 0f);
            t2.pickup = AbstractArrow.Pickup.DISALLOWED;
            h.getLevel().addFreshEntity(t2);
            thrownAt[0] = t;
        });
        h.runAfterDelay(150, () -> {
            // (on land: in water a thrown trident sinks short, as it does at anything)
            h.assertTrue(water || struck(e) < hp[0], where + ": a trident didn't hurt him (it is at "
                    + thrownAt[0].position() + ", he is in " + box(e) + ")");
            h.assertTrue(hits[0] >= 4, where + ": one sword fells him in " + hits[0] + " blows");
            h.assertTrue(hits[0] <= 400, where + ": a sword needs " + hits[0] + " blows to fell him");
            healUp(e);
            stay(e, false);
            e.setTarget(pl[0]);
            measuring[0] = true;
            spot[2] = null;
            lastAt[0] = pl[0].position();
            now[0] = "walking";
        });

        // every tick: how hard anybody was set moving, how far they were carried, what they were hurt for
        h.onEachTick(() -> {
            if (!measuring[0] || pl[0] == null) return;
            ServerPlayer p = pl[0];
            if (water) wet(p);
            Vec3 v = p.getDeltaMovement();
            note(most, mostWhen, 0, thrown(v), now[0]);
            // (lifted and carried in his hold, when he is big enough to hold a player, is his to do: not counted)
            // (or set down from it: the tick he lets go)
            boolean inHold = p.isPassenger() && canHold(e, p);
            if (!inHold && !wasHeld[0]) note(most, mostWhen, 1, p.position().distanceTo(lastAt[0]), now[0]);
            wasHeld[0] = inHold;
            Double blow = BLOW.remove(p);
            note(most, mostWhen, 2, blow == null ? 0 : blow, now[0]);
            p.setDeltaMovement(Vec3.ZERO);
            // (one he is big enough to lift may stay in his hold: that carry is his to make, block by block)
            boolean held = p.isPassenger() && canHold(e, p);
            if (p.isPassenger() && !held) p.stopRiding();
            // back beside him if he has left them behind, or they were carried off somewhere
            if (!held && (p.position().distanceTo(box(e).getCenter()) > 4 + box(e).getXsize() || p.level() != h.getLevel())) {
                standBy(p, e);
            }
            lastAt[0] = p.position();
            p.setHealth(p.getMaxHealth());
            p.invulnerableTime = 0;
            p.lookAt(EntityAnchorArgument.Anchor.EYES, box(e).getCenter());
            Cow c = cows[0];
            Vec3 cv = c.getDeltaMovement();
            note(most, mostWhen, 3, thrown(cv), now[0]);
            note(most, mostWhen, 4, c.position().distanceTo(spot[1]), now[0]);
            Double cb = BLOW.remove(c);
            note(most, mostWhen, 5, cb == null ? 0 : cb, now[0]);
            c.setHealth(c.getMaxHealth());
            c.invulnerableTime = 0;
            if (c.isPassenger()) c.stopRiding();
            if (c.position().distanceTo(spot[1]) > 0.3) {
                c.teleportTo(spot[1].x, spot[1].y, spot[1].z);
                c.setDeltaMovement(Vec3.ZERO);
            }
            var f = farStand[0];
            Vec3 fv = f.getDeltaMovement();
            if (spot[2] == null) spot[2] = f.position(); // (where it stands once settled)
            note(most, mostWhen, 6, Math.max(Math.hypot(fv.x, fv.z), fv.y)
                    + Math.hypot(f.getX() - spot[2].x, f.getZ() - spot[2].z), now[0]);
            Vec3 him = e.position();
            // (a move that is a jump through a rift on purpose is left out)
            String doing = moveNow(e);
            if (lastHim[0] != null && !TELEPORTS.contains(now[0]) && !TELEPORTS.contains(doing))
                note(most, mostWhen, 7, Math.hypot(him.x - lastHim[0].x, him.z - lastHim[0].z) /* (sideways: stepping up onto something is no jump) */, now[0] + (doing.isEmpty() ? "" : "/" + doing));
            lastHim[0] = him;
        });

        // he walks about a while, then does each of his moves at the player
        int t = 150 + 200;
        for (int move : moves()) {
            int mv = move;
            h.runAfterDelay(t, () -> {
                now[0] = moveName(mv);
                force(e, mv, pl[0]);
            });
            t += moveLength(mv) + 25;
        }
        h.runAfterDelay(t, () -> {
            measuring[0] = false;
            // (a move of his may have called up a storm: the game tests after these need the plain day back)
            h.getLevel().setWeatherParameters(6000, 0, false, false);
            log.append(String.format(
                    "most: player speed %.2f (%s), carried %.2f (%s), hurt %.1f (%s); cow speed %.2f (%s), carried"
                            + " %.2f (%s), hurt %.1f (%s); far stand %.2f (%s); his step %.2f (%s)",
                    most[0], mostWhen[0], most[1], mostWhen[1], most[2], mostWhen[2], most[3], mostWhen[3], most[4],
                    mostWhen[4], most[5], mostWhen[5], most[6], mostWhen[6], most[7], mostWhen[7]));
            LOG.info("tiny {}{}{}{}{}", log, byMove(most), "%n    player:".formatted() + whyOf(pl[0], 0.5),
                    "%n    cow:".formatted() + whyOf(cows[0], 0.5), "%n    far stand:".formatted()
                            + whyOf(farStand[0], 0.1));
            boolean holds = canHold(e, pl[0]);
            pl[0].getServer().getPlayerList().remove(pl[0]);
            for (Cow c : cows) c.discard();
            farStand[0].discard();
            release(h, e);
            List<String> bad = new ArrayList<>();
            double maxSpeed = shoveCap(s) + 0.1;
            if (most[0] > maxSpeed) bad.add("threw the player at " + fmt(most[0]) + " (" + mostWhen[0] + ")");
            // (one big enough to lift a player may reel them in or throw them a short way)
            // (and one his body keeps coming down on is put up on top of him, out of its way)
            double maxMoved = MAX_MOVED + (holds ? holdReach(box(e), s) : 0) + tall[0];
            if (most[1] > maxMoved) bad.add("carried the player " + fmt(most[1]) + " (" + mostWhen[1] + ")");
            // (no more than his hardest blow at full size, on the giants' shared size curve; a cow takes mobDamage
            // times more)
            double maxHurt = FULL_MAX_HIT * sizeHit(s) + 0.5;
            if (most[2] > maxHurt) bad.add("hit the player for " + fmt(most[2]) + " in one blow (" + mostWhen[2] + ")");
            if (most[3] > maxSpeed) bad.add("threw the cow at " + fmt(most[3]) + " (" + mostWhen[3] + ")");
            if (most[4] > MAX_MOVED + (canHold(e, cows[0]) ? holdReach(box(e), s) : 0)) bad.add("carried the cow " + fmt(most[4]) + " (" + mostWhen[4] + ")");
            if (most[5] > maxHurt * mobDamage()) bad.add("hurt the cow " + fmt(most[5]) + " at once (" + mostWhen[5] + ")");
            if (most[6] > 0.15) bad.add("reached the far stand: " + fmt(most[6]) + " (" + mostWhen[6] + ")");
            // (no more than a quarter of his own length in one tick: a burrower coming up where he went down)
            if (most[7] > Math.max(1.2, 0.25 * longest[0])) bad.add("he jumped " + fmt(most[7]) + " in a tick (" + mostWhen[7] + ")");
            h.assertTrue(bad.isEmpty(), where + ": " + String.join("; ", bad));
            h.succeed();
        });
    }

    /** how far one he may hold can be carried in a move: what he reaches across his own length, or 25 a block of size */
    static double holdReach(AABB b, double s) {
        return Math.max(25 * s + 1, Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize())) + 1);
    }

    static String fmt(double d) {
        return String.format("%.2f", d);
    }

    static final java.util.Map<double[], java.util.Map<String, double[]>> BY_MOVE = new java.util.IdentityHashMap<>();

    /** each move's most, as a line per move (only the moves that did anything) */
    static synchronized String byMove(double[] most) {
        StringBuilder sb = new StringBuilder();
        var m = BY_MOVE.remove(most);
        if (m == null) return "";
        m.forEach((k, v) -> {
            if (v[0] + v[1] + v[2] + v[3] + v[4] + v[5] + v[6] > 0.2)
                sb.append(String.format("%n   %-18s player %.2f/%.2f/%.1f cow %.2f/%.2f/%.1f far %.2f step %.2f", k,
                        v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7]));
        });
        return sb.toString();
    }

    static synchronized void note(double[] most, String[] when, int i, double v, String now) {
        double[] row = BY_MOVE.computeIfAbsent(most, k -> new java.util.LinkedHashMap<>())
                .computeIfAbsent(now, k -> new double[8]);
        row[i] = Math.max(row[i], v);
        if (v > most[i]) {
            most[i] = v;
            when[i] = now;
        }
    }

    // ---- the giant: everything above is the same in every giant's copy ----

    /** his moves that are jumps on purpose (through a rift): not counted as jumping about */
    static final java.util.Set<String> TELEPORTS = java.util.Set.of();

    static final String MOD = "net.jj.hollowbell";
    static final int SLOT = 1700;
    /** how far his model reaches at full size (blocks), for the room the test makes him */
    static final double FULL_REACH = 80;
    /** his hardest blow on a player at full size (his dive from the sky: 70) */
    static final double FULL_MAX_HIT = 75;

    static HollowbellEntity spawn(GameTestHelper h, Vec3 at, float s) {
        HollowbellEntity e = ModEntities.HOLLOWBELL.create(h.getLevel());
        e.moveTo(at.x, at.y, at.z, 0f, 0f);
        e.setVariant(HollowbellEntity.HUNTER);
        e.setBellScale(s);
        h.getLevel().addFreshEntity(e);
        return e;
    }

    static void release(GameTestHelper h, HollowbellEntity e) {
        e.discard();
    }

    /** the box round where he is drawn */
    static AABB box(HollowbellEntity e) {
        AABB b = e.modelBox();
        return b == null ? e.getBoundingBox() : b;
    }

    static double reach(HollowbellEntity e) {
        AABB b = box(e);
        return Math.max(Math.max(b.getXsize(), b.getZsize()) / 2, b.getYsize());
    }

    /** how far along a look it meets him (the crosshair, as a game picks it), or null */
    static Double lookMeets(HollowbellEntity e, Vec3 eye, Vec3 look, double len) {
        var hit = e.raycast(eye, look, len);
        return hit == null ? null : (double) hit.t();
    }

    /** what a swing along that look lands on (him), or null */
    static Entity lookedAt(GameTestHelper h, HollowbellEntity e, Vec3 eye, Vec3 look, double reach) {
        return lookMeets(e, eye, look, reach) != null ? e : null;
    }

    /** a player's swing at what their crosshair is on, the way their game sends it (his own hit packet) */
    static void swing(HollowbellEntity e, ServerPlayer p, Entity at) {
        e.hitBy(p, -1);
    }

    /** what he is doing, for a failure's message */
    static String state(HollowbellEntity e) {
        return "box " + box(e);
    }

    static float struck(HollowbellEntity e) {
        return healthNow(e);
    }

    /** the move he is in now, by name ("" if none or not known) */
    static String moveNow(HollowbellEntity e) {
        return "";
    }

    static float healthNow(HollowbellEntity e) {
        return e.healthNow();
    }

    static float healthMax(HollowbellEntity e) {
        return e.healthMax();
    }

    static void healUp(HollowbellEntity e) {
        e.setHealthTo(e.healthMax());
    }

    static void stay(HollowbellEntity e, boolean on) {
        e.setStay(on);
    }

    static List<Integer> moves() {
        List<Integer> l = new ArrayList<>();
        for (int i = 1; i < Moves.NAMES.length; i++) l.add(i);
        return l;
    }

    static String moveName(int mv) {
        return Moves.NAMES[mv];
    }

    static int moveLength(int mv) {
        return Moves.length(mv);
    }

    static void force(HollowbellEntity e, int mv, ServerPlayer p) {
        e.setStay(true);
        e.setTarget(p);
        e.forceMove(mv, p);
    }

    static float sizeHit(float s) {
        return HollowbellEntity.sizeHit(s);
    }

    static double shoveCap(float s) {
        return HollowbellEntity.shoveCap(s);
    }

    static boolean canHold(HollowbellEntity e, Entity who) {
        return e.canHold(who);
    }

    static float mobDamage() {
        return BellConfig.V.mobDamage;
    }
}
