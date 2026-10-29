package net.jj.hollowbell.solid;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.mixin.SitJumpAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Sit anywhere on him (the solid kit). A player on his safe list (he has an owner, and they are it or on its list)
 * crouches three times within two seconds standing on him, or presses "Sit on giant": they sit down exactly where they
 * stand, on whatever part of him that is, down to a claw's top. The seat is fixed to that part (SitSeat) and goes
 * where it goes; nothing of his hurts them while they sit. Jumping gets them up, standing on the same spot, or set
 * down on clear ground beside him when that spot can't be stood on now. It also ends, safely, when he dies or goes, when
 * that part of him isn't there any more, when they're taken off the safe list, and when they leave the game.
 *
 * Sitting is not riding him: the book's "ride" takes a sitter off their spot, and a rider can't sit.
 */
public final class Sitting {
    private Sitting() {}

    public static EntityType<SitSeat> SEAT;
    /** his mod id and the start of his classes' names (what of his can't hurt a sitter) */
    static String modId = "", pkg = "";

    /** how far over or under the top of him your feet may be to sit there (the server sees you a tick late) */
    public static final double REACH = 1.2;
    /** how often (ticks) a sitter's place on the safe list is looked at again */
    static final int CHECK_EVERY = 5;

    public enum Result {
        SAT,
        NOT_ALLOWED,
        NOT_ON_HIM,
        BUSY
    }

    /** once, from the mod's init: the seat type, the gesture's message, and nothing of his hurting a sitter */
    public static void init(EntityType<SitSeat> seat) {
        SEAT = seat;
        modId = BuiltInRegistries.ENTITY_TYPE.getKey(seat).getNamespace();
        String p = Sitting.class.getPackageName();
        pkg = p.substring(0, p.lastIndexOf('.') + 1);
        PayloadTypeRegistry.playC2S().register(SitPayload.TYPE, SitPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                SitPayload.TYPE, (pay, ctx) -> ctx.server().execute(() -> asked(ctx.player(), pay)));
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((e, src, amount) -> !sparedNow(e, src));
    }

    /**
     * how far (blocks) the spot their game says they stand on may be from where the server has them: the server sees
     * them a tick or two late, and a part of him that moves (a leg stepping, a head swinging) carries them on meanwhile
     */
    public static final double GAME_REACH = 3.0;

    /** the gesture or the key, from a player's game */
    public static void asked(ServerPlayer p, SitPayload pay) {
        if (!pay.sit()) {
            if (p.getVehicle() instanceof SitSeat s) standUp(s, p);
            return;
        }
        if (p.isPassenger()) return;
        Entity e = p.level().getEntity(pay.giant());
        if (e instanceof SitHost h && trySit(p, h, pay.spot()) == Result.SAT) return;
        // (their game and the server may disagree on which of him they stand on, by a tick)
        SitHost h = under(p);
        if (h != null && h != e) trySit(p, h);
    }

    /** sit down on him where they stand, if they may */
    public static Result trySit(ServerPlayer p, SitHost h) {
        return trySit(p, h, null);
    }

    /**
     * sit down on him where they stand, if they may: on the spot of him their own game has them standing on (it moves
     * them with the part under them, so it knows), if it says one and that's near where the server has them; or on
     * the spot the server finds under them
     */
    public static Result trySit(ServerPlayer p, SitHost h, @Nullable Solid.Anchor fromGame) {
        if (p.isPassenger() || p.isSpectator() || !p.isAlive() || p.isSleeping()) return Result.BUSY;
        Entity s = h.solidSelf();
        if (s.level() != p.level() || s.isRemoved() || h.sitDying())
            return Result.NOT_ON_HIM;
        if (!h.sitAllowed(p)) return Result.NOT_ALLOWED;
        Solid.Anchor a = fromGame != null ? theirSpot(h, p, fromGame) : null;
        if (a == null) a = spotUnder(h, p);
        if (a == null) return Result.NOT_ON_HIM;
        sit(p, h, a);
        return Result.SAT;
    }

    /** the spot their game says they stand on, if it's a solid part of him that's there now, near them; or null */
    static @Nullable Solid.Anchor theirSpot(SitHost h, Entity p, Solid.Anchor a) {
        if (!Solid.live(h, p)) return null;
        SolidShape sh = h.solidShape();
        int f = a.frame();
        if (f < 0 || f >= sh.frames() || sh.kind[f] != SolidShape.SOLID) return null;
        SolidCache c = Solid.frames(h);
        if (f >= c.on.length || !c.on[f]) return null;
        Vector3f r = a.rest();
        if (!Float.isFinite(r.x) || !Float.isFinite(r.y) || !Float.isFinite(r.z)) return null;
        Vec3 w = Solid.toWorld(c, f, r);
        if (w.distanceToSqr(p.position()) > GAME_REACH * GAME_REACH) return null;
        return new Solid.Anchor(f, new Vector3f(r));
    }

    /** the one of him (this mod's) they stand on now, or null */
    public static @Nullable SitHost under(Entity p) {
        for (SolidBody b : Solid.bodies(p.level().isClientSide))
            if (b instanceof SitHost h && footing(h, p, REACH, REACH) != null) return h;
        return null;
    }

    /** standing on him (as their own game sees it: close over a top of his) */
    public static @Nullable SitHost standingOn(Entity p) {
        for (SolidBody b : Solid.bodies(p.level().isClientSide))
            if (b instanceof SitHost h && footing(h, p, 0.6, 0.6) != null) return h;
        return null;
    }

    /**
     * The top of his their feet are on: over any of the columns their feet reach into, down to a sliver of them on
     * his edge (where crouching lets you stand, or a thin leg is under one side of you), the one nearest their feet,
     * from `over` blocks under their feet to `under` over them (a top a little under them first: one far over their
     * feet in a column at their side is a wall they're against, not a floor). Fixed to the part that carries it; its
     * spot is right under them (their middle, at that top's height). Null if none.
     */
    public static @Nullable Solid.Anchor footing(SitHost h, Entity p, double over, double under) {
        if (!Solid.live(h, p) || !h.solidBox().inflate(0, over + 1, 0).intersects(p.getBoundingBox())) return null;
        double feet = p.getY(), cw = Solid.columnWidth(h);
        AABB box = p.getBoundingBox().deflate(1e-3, 0, 1e-3);
        int xa = Mth.floor(box.minX / cw),
                xb = Mth.floor((box.maxX - 1e-7) / cw),
                za = Mth.floor(box.minZ / cw),
                zb = Mth.floor((box.maxZ - 1e-7) / cw);
        if ((long) (xb - xa + 1) * (zb - za + 1) > 64) {
            xa = xb = Mth.floor(p.getX() / cw);
            za = zb = Mth.floor(p.getZ() / cw);
        }
        Solid.Runs r = new Solid.Runs();
        int bestF = -1;
        double bestY = feet, bestD = Double.MAX_VALUE;
        // (the line right under their middle first, then the columns under the rest of them)
        for (int i = xa - 1; i <= xb; i++)
            for (int k = za; k <= zb; k++) {
                if (i < xa && k > za) break;
                boolean mid = i < xa;
                if (mid) Solid.column(h, p.getX(), p.getZ(), feet - over - 1, feet + under, false, r);
                else Solid.column(h, (i + 0.5) * cw, (k + 0.5) * cw, feet - over - 1, feet + under, false, r);
                for (int j = 0; j < r.n; j++) {
                    double top = r.top[j];
                    if (top > feet + under - 1e-3 || top < feet - over) continue;
                    // (a top over their feet only holds them in the column under their middle; a column at their side
                    // reaching up past their feet is him beside them)
                    if (!mid && top > feet + 0.6) continue;
                    double d = Math.abs(top - feet) + (Solid.headroom(r, j, 1.0) ? 0 : 4);
                    if (d < bestD) {
                        bestD = d;
                        bestF = r.frame[j];
                        bestY = top;
                    }
                }
            }
        if (bestF < 0) return null;
        SolidShape sh = h.solidShape();
        int cf = sh.frameOfBone[h.solidCarryBone(sh.bone[bestF])];
        if (cf >= 0) bestF = cf;
        return new Solid.Anchor(
                bestF, Solid.toRest(Solid.frames(h), bestF, p.getX(), bestY, p.getZ(), new Vector3f()));
    }

    /** the spot of him under their feet as their own game has it (sent with the gesture), or null */
    public static @Nullable Solid.Anchor gameSpot(SitHost h, Entity p) {
        return footing(h, p, 0.6, 0.6);
    }

    /** the spot of him under their feet: on the top nearest their feet, fixed to the part that carries it */
    public static @Nullable Solid.Anchor spotUnder(SitHost h, Entity p) {
        return footing(h, p, REACH, REACH);
    }

    /** sits them on that spot (no checks: trySit makes them) */
    public static SitSeat sit(LivingEntity p, SitHost h, Solid.Anchor a) {
        SitSeat s = new SitSeat(SEAT, p.level());
        s.setup(h.solidSelf(), a.frame(), a.rest());
        Vec3 at = Solid.anchorWorld(h, a);
        s.moveTo(at.x, at.y, at.z, p.getYRot(), 0f);
        s.follow(h);
        p.level().addFreshEntity(s);
        p.stopRiding();
        p.startRiding(s, true);
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
        return s;
    }

    /** whom he has sitting on him now */
    public static List<LivingEntity> sitters(SitHost h) {
        List<LivingEntity> out = new ArrayList<>();
        Entity s = h.solidSelf();
        for (SitSeat seat : s.level().getEntitiesOfClass(SitSeat.class, h.solidBox().inflate(4)))
            if (seat.ownerId() == s.getId() && seat.getFirstPassenger() instanceof LivingEntity le) out.add(le);
        return out;
    }

    public static boolean isSitting(@Nullable Entity e) {
        return e != null && e.getVehicle() instanceof SitSeat;
    }

    /** sitting on this one of him */
    public static boolean sittingOn(@Nullable Entity e, Entity giant) {
        return e != null && e.getVehicle() instanceof SitSeat s && s.ownerId() == giant.getId();
    }

    static boolean isLocal(Entity e) {
        return e instanceof Player p && p.isLocalPlayer();
    }

    // each tick of a seat (server)

    static void seatTick(SitSeat s) {
        Entity p = s.getFirstPassenger();
        if (p == null) {
            if (++s.empty > 2) s.discard();
            return;
        }
        s.empty = 0;
        Entity o = s.level().getEntity(s.ownerId());
        SitHost h = o instanceof SitHost x && !o.isRemoved() ? x : null;
        // he's gone (dead, away, stepped out of the world) or a seat read back from a save
        if (h == null || h.sitDying()) {
            setDownWithout(s, p, h);
            return;
        }
        // that part of him isn't there to sit on (broken off, burrowed, too small)
        if (!s.spotThere(h)) {
            standUp(s, p);
            return;
        }
        s.follow(h);
        if (p instanceof LivingEntity le) {
            le.resetFallDistance();
            if (le.isOnFire()) le.clearFire();
        }
        // taken off the safe list (or he lost his owner): up, safely
        if (p instanceof Player pl && ++s.checked >= CHECK_EVERY) {
            s.checked = 0;
            if (!h.sitAllowed(pl)) {
                standUp(s, p);
                if (pl instanceof ServerPlayer sp)
                    sp.displayClientMessage(Component.translatable("message." + modId + ".sit.off_list"), true);
                return;
            }
        }
        if (p instanceof LivingEntity le && jumping(le)) standUp(s, p);
    }

    /** the jump key (a sitter's game sends it every tick while they ride) */
    static boolean jumping(LivingEntity le) {
        return ((SitJumpAccess) le).hollowbell$sitJumping();
    }

    /** up from the seat: standing on the same spot, or set down beside him when it can't be stood on now */
    public static void standUp(SitSeat s, Entity p) {
        SitHost h = s.host();
        if (h == null || !h.solidReady()) {
            setDownWithout(s, p, h);
            return;
        }
        Vec3 spot = s.spotThere(h) ? s.spotNow(h) : s.position();
        p.stopRiding();
        s.discard();
        if (p instanceof LivingEntity le) le.setJumping(false);
        Vec3 to = standingSpot(h, p, spot);
        if (to != null) place(p, to);
        else if (p instanceof LivingEntity le) h.sitSetDown(le);
        else place(p, spot.add(0, 0.5, 0));
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** where on that spot (or right by it, on him) someone can stand now, or null */
    static @Nullable Vec3 standingSpot(SitHost h, Entity p, Vec3 spot) {
        AABB box = p.getDimensions(Pose.STANDING).makeBoundingBox(spot.add(0, 0.01, 0));
        if (Solid.clear(h, p, box) && floorUnder(h, box)) return spot.add(0, 0.01, 0);
        Vec3 f = Solid.freeSpot(h, p, spot.add(0, 0.01, 0), Vec3.ZERO, true);
        AABB fb = p.getDimensions(Pose.STANDING).makeBoundingBox(f);
        if (f.distanceToSqr(spot) < 2.5 * 2.5 && floorUnder(h, fb) && Solid.clear(h, p, fb)) return f;
        return null;
    }

    /** a top of his up to a block under a box's feet, under any of it (standing there, it would stand on him) */
    static boolean floorUnder(SitHost h, AABB box) {
        double cw = Solid.columnWidth(h), y = box.minY;
        AABB b = box.deflate(1e-3, 0, 1e-3);
        if (Solid.floorUnder(h, new Vec3((b.minX + b.maxX) / 2, y, (b.minZ + b.maxZ) / 2))) return true;
        Solid.Runs r = new Solid.Runs();
        for (int i = Mth.floor(b.minX / cw); i <= Mth.floor((b.maxX - 1e-7) / cw); i++)
            for (int k = Mth.floor(b.minZ / cw); k <= Mth.floor((b.maxZ - 1e-7) / cw); k++) {
                Solid.column(h, (i + 0.5) * cw, (k + 0.5) * cw, y - 1.0, y + 0.05, false, r);
                for (int j = 0; j < r.n; j++) if (r.top[j] >= y - 1.0 && r.top[j] <= y + 0.05) return true;
            }
        return false;
    }

    /** he's gone: whoever sat on him goes onto the ground beside where he was (his set-down if he's still there) */
    static void setDownWithout(SitSeat s, Entity p, @Nullable SitHost h) {
        Vec3 at = s.position();
        p.stopRiding();
        s.discard();
        if (p instanceof LivingEntity le) le.setJumping(false);
        if (h != null && p instanceof LivingEntity le && h.solidSelf().level() == p.level()) {
            h.sitSetDown(le);
        } else {
            double g = Solid.groundNow(p.level(), at.x, at.z, at.y);
            place(p, new Vec3(at.x, g + 0.1, at.z));
            if (p instanceof LivingEntity le)
                le.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false));
        }
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
    }

    static void place(Entity p, Vec3 to) {
        if (p instanceof ServerPlayer sp)
            sp.teleportTo(sp.serverLevel(), to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
        else p.teleportTo(to.x, to.y, to.z);
    }

    /** a player leaving the game while sitting on him: set down beside him first, so they're saved on the ground */
    public static void leaving(ServerPlayer p) {
        if (!(p.getVehicle() instanceof SitSeat s)) return;
        setDownWithout(s, p, s.host());
    }

    /** he is going (dying, stepping out of the world, removed): everyone sitting on him is set down beside him */
    public static void setDownAll(SitHost h) {
        for (LivingEntity le : sitters(h))
            if (le.getVehicle() instanceof SitSeat s) setDownWithout(s, le, h);
    }

    // nothing of his hurts a sitter

    /** the damage is his (his blows, his shots, his fire, his own kind of damage) or what sitting on him does */
    static boolean sparedNow(LivingEntity e, DamageSource src) {
        if (!(e.getVehicle() instanceof SitSeat s)) return false;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        Entity giant = s.level().getEntity(s.ownerId());
        if (giant == null) return false;
        if (isHis(behind(src), giant) || isHis(src.getDirectEntity(), giant) || isHis(src.getEntity(), giant))
            return true;
        var key = src.typeHolder().unwrapKey();
        if (key.isPresent() && key.get().location().getNamespace().equals(modId)) return true;
        // what riding along on him can do to you on its own: his parts closing round you, the ground he goes into,
        // his fire and water and frost
        if (src.getEntity() == null
                && (src.is(DamageTypeTags.IS_FALL)
                        || src.is(DamageTypes.IN_WALL)
                        || src.is(DamageTypes.CRAMMING)
                        || src.is(DamageTypes.FLY_INTO_WALL)
                        || src.is(DamageTypeTags.IS_FIRE)
                        || src.is(DamageTypeTags.IS_DROWNING)
                        || src.is(DamageTypeTags.IS_FREEZING)
                        || src.is(DamageTypes.CACTUS)
                        || src.is(DamageTypes.SWEET_BERRY_BUSH)
                        || src.is(DamageTypes.STALAGMITE)
                        || src.is(DamageTypes.FALLING_BLOCK)
                        || src.is(DamageTypes.FALLING_STALACTITE))) return true;
        return false;
    }

    /** a harmful effect from him (or from nowhere, while sitting on him) */
    public static boolean blocksEffect(LivingEntity e, MobEffectInstance fx, @Nullable Entity from) {
        if (!(e.getVehicle() instanceof SitSeat s) || e.level().isClientSide) return false;
        if (fx.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return false;
        Entity giant = s.level().getEntity(s.ownerId());
        return giant != null && (from == null || isHis(from, giant) || isHis(ownerOf(from), giant));
    }

    /** the giant himself, or anything of his mod (his shots, parts, helpers, effects) */
    static boolean isHis(@Nullable Entity e, Entity giant) {
        if (e == null) return false;
        if (e == giant) return true;
        String cls = e.getClass().getName();
        if (cls.startsWith(pkg)) return true;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        return key.getNamespace().equals(modId);
    }

    static @Nullable Entity behind(DamageSource src) {
        Entity e = src.getEntity();
        if (e == null) e = src.getDirectEntity();
        for (int i = 0; i < 3 && e != null; i++) {
            Entity o = ownerOf(e);
            if (o == null || o == e) break;
            e = o;
        }
        return e;
    }

    static @Nullable Entity ownerOf(@Nullable Entity e) {
        if (e instanceof Projectile p) return p.getOwner();
        if (e instanceof TraceableEntity t) return t.getOwner();
        if (e instanceof OwnableEntity o) return o.getOwner();
        return null;
    }

    // for tests and the autotest: spots on him to stand on

    /** a spot on the top of a frame of his: the highest top over its middle with room to stand over it, or null */
    public static @Nullable Vec3 topOfFrame(SitHost h, int f) {
        SolidCache c = Solid.frames(h);
        if (f < 0 || f >= c.on.length || !c.on[f]) return null;
        SolidShape sh = h.solidShape();
        float[] b = sh.bounds[f];
        for (float[] k : new float[][] {
            {0.5f, 0.5f}, {0.4f, 0.6f}, {0.6f, 0.4f}, {0.3f, 0.3f}, {0.7f, 0.7f}, {0.5f, 0.25f}, {0.5f, 0.75f}
        }) {
            Vec3 m = Solid.toWorld(
                    c,
                    f,
                    new Vector3f(b[0] + (b[3] - b[0]) * k[0], (b[1] + b[4]) / 2, b[2] + (b[5] - b[2]) * k[1]));
            double reach = Math.max(8, (b[4] - b[1]) * h.solidScale() + 8);
            Solid.Runs r = Solid.column(h, m.x, m.z, m.y - reach, m.y + reach, false, new Solid.Runs());
            double best = Double.NaN;
            for (int j = 0; j < r.n; j++)
                if (r.frame[j] == f && (Double.isNaN(best) || r.top[j] > best)) best = r.top[j];
            if (Double.isNaN(best)) continue;
            boolean room = true;
            for (int j = 0; j < r.n; j++) if (r.bot[j] > best && r.bot[j] < best + 2.2) room = false;
            if (room) return new Vec3(m.x, best, m.z);
        }
        return null;
    }

    /** spots to stand on him: one per frame that has one, highest first */
    public static List<Vec3> spots(SitHost h) {
        List<Vec3> out = new ArrayList<>();
        int n = h.solidShape().frames();
        for (int f = 0; f < n; f++) {
            Vec3 v = topOfFrame(h, f);
            if (v != null) out.add(v);
        }
        out.sort((a, b) -> Double.compare(b.y, a.y));
        return out;
    }

    /**
     * An odd spot to sit (a small part: a claw's top, a horn, a ledge): the frame with the smallest top that still has
     * room to stand, and that is well up off the ground. Or the highest spot, if none is.
     */
    public static @Nullable Vec3 oddSpot(SitHost h) {
        SolidShape sh = h.solidShape();
        Entity s = h.solidSelf();
        Vec3 best = null;
        double bestArea = Double.MAX_VALUE;
        for (int f = 0; f < sh.frames(); f++) {
            if (sh.kind[f] == SolidShape.SOFT) continue;
            // (not an eye or the like, that rolls under you and is carried by another part)
            if (sh.frameOfBone[h.solidCarryBone(sh.bone[f])] != f) continue;
            Vec3 v = topOfFrame(h, f);
            if (v == null || v.y < s.getY() + 2.5) continue;
            // (open to the sky: nothing of him over it, so the sitter can be seen)
            if (Solid.column(h, v.x, v.z, v.y + 0.2, v.y + 48, false, new Solid.Runs()).n > 0) continue;
            float[] b = sh.bounds[f];
            double area = (b[3] - b[0]) * (b[5] - b[2]) * h.solidScale() * h.solidScale();
            if (area < 2.0) continue;
            if (area < bestArea) {
                bestArea = area;
                best = v;
            }
        }
        if (best != null) return best;
        List<Vec3> all = spots(h);
        return all.isEmpty() ? null : all.get(0);
    }
}
