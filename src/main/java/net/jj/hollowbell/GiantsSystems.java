package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellRig;
import org.joml.Vector3f;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.4) The Giants Guide's new systems, his side: weak points in his own hurt path (the
 * glowing spots on his bell and his crown take more, the rest the world's share), what a hit did told to the Guide while
 * its Growth or Loyalty needs it ("syshurt"), and a bridge pair for what only he can do: where his weak spots are now,
 * his mood, turning on a player, and a spot of his own to walk to ("sys", "sysact"). With weak points off (as
 * shipped) {@link #hit} gives back the very same number and nothing is told.
 */
public final class GiantsSystems {
    private GiantsSystems() {}

    public static final int VERSION = 1;
    /** the all-giants settings he applies (listed in his admin header's "uses") */
    public static final String[] USES = {"all.weakPoints", "all.weakHit", "all.bodyHit"};
    static final List<String> DOES = List.of("spots", "mood", "turn", "go");
    /** his weak points in plain words (the Guide's book and lines) */
    static final List<String> WEAK = List.of("the glowing spots on his bell", "his crown");
    private static final String WHO = "Hollowbell";
    private static final String[] MOODS = {"calm", "hunting", "guardian"};

    private static volatile boolean weakOn, report;
    private static volatile float weakHit = 2.5f, bodyHit = 1f;

    /** the switches out of adminset's "all" (absent: as shipped) */
    static void receiveAll(JsonObject all) {
        weakOn = bool(all.get("all.weakPoints"));
        weakHit = num(all.get("all.weakHit"), 2.5f, 1f, 5f);
        bodyHit = num(all.get("all.bodyHit"), 1f, 0.1f, 1f);
        report = bool(all.get("all.sysReport"));
    }

    public static void clear() {
        weakOn = false;
        report = false;
        weakHit = 2.5f;
        bodyHit = 1f;
    }

    private static boolean bool(@Nullable JsonElement v) {
        try {
            return v != null && v.isJsonPrimitive() && v.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static float num(@Nullable JsonElement v, float or, float lo, float hi) {
        try {
            if (v == null || !v.isJsonPrimitive()) return or;
            float f = v.getAsFloat();
            return Float.isFinite(f) ? Math.max(lo, Math.min(hi, f)) : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    public static boolean weakPoints() {
        return weakOn;
    }

    /** anything for the hook to do: weak points on, or the Guide wants his hits told (off: the hook isn't run) */
    public static boolean active() {
        return weakOn || report;
    }

    // the hook

    /**
     * A hit he takes, after his own factors: with weak points on, x the weak point damage when it landed on one of his
     * weak points (crit sparks and a sharp sound there, "Weak point!" to a player who did it), else x the world's
     * share for the rest of him. Off: the very same number. Told to the Guide while it needs it.
     */
    public static float hit(LivingEntity g, DamageSource src, float amount, boolean weak, @Nullable Vec3 at) {
        if (!weakOn && !report) return amount;
        if (src.is(DamageTypes.GENERIC_KILL)) return amount;
        float dealt = amount;
        if (weakOn) {
            dealt = amount * (weak ? weakHit : bodyHit);
            if (weak && g.level() instanceof ServerLevel sl) {
                Vec3 p = at != null ? at : g.position().add(0, g.getBbHeight() * 0.6, 0);
                sl.sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 18, 0.4, 0.4, 0.4, 0.3);
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 10, 0.3, 0.3, 0.3, 0.2);
                sl.playSound(null, p.x, p.y, p.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.5f, 0.7f);
                if (src.getEntity() instanceof ServerPlayer sp)
                    sp.displayClientMessage(Component.translatable("message.hollowbell.systems.weak"), true);
            }
        }
        if (report && g.level() instanceof ServerLevel sl) tell(sl.getServer(), g, src, dealt, weak);
        return dealt;
    }

    /**
     * Did this hit land on one of these spots: a projectile where it is, else the eye ray of whoever struck (a player
     * or a creature) passing within a spot's radius.
     */
    public static boolean near(List<Spot> spots, DamageSource src) {
        if (spots.isEmpty()) return false;
        Entity d = src.getDirectEntity();
        if (d instanceof Projectile pr) {
            for (Spot s : spots) if (pr.position().distanceTo(s.at()) <= s.r() + 0.6) return true;
            return false;
        }
        if (src.getEntity() instanceof LivingEntity le && d == le) {
            Vec3 eye = le.getEyePosition(), look = le.getViewVector(1f);
            for (Spot s : spots) {
                Vec3 to = s.at().subtract(eye);
                double along = to.dot(look);
                if (along < 0) continue;
                if (to.subtract(look.scale(along)).length() <= s.r()) return true;
            }
        }
        return false;
    }

    /** a weak spot of his: its name, the middle of it now and how wide it is */
    public record Spot(String name, Vec3 at, double r) {}

    /** his weak spots now: each glowing spot on his bell, and his crown */
    public static List<Spot> spots(HollowbellEntity hb) {
        List<Spot> out = new ArrayList<>();
        float sc = hb.bellScale();
        for (BellRig.BlobDef b : hb.rig.spots)
            out.add(new Spot("a glowing spot on his bell", hb.boneWorld(b.bone(), new Vector3f(b.centre())),
                    Math.max(0.6, b.radius() * sc)));
        // his crown: the middle of its blocks, as wide as they spread
        float[][] bounds = BellModel.get().bounds;
        List<Vec3> crown = new ArrayList<>();
        for (int i = 0; i < hb.rig.kind.length; i++) {
            if (hb.rig.kind[i] != BellRig.Kind.CROWN || bounds[i] == null) continue;
            float[] bb = bounds[i];
            crown.add(hb.boneWorld(i, new Vector3f((bb[0] + bb[3]) / 2, (bb[1] + bb[4]) / 2, (bb[2] + bb[5]) / 2)));
        }
        if (!crown.isEmpty()) {
            Vec3 sum = Vec3.ZERO;
            for (Vec3 v : crown) sum = sum.add(v);
            Vec3 mid = sum.scale(1.0 / crown.size());
            double r = 0;
            for (Vec3 v : crown) r = Math.max(r, v.distanceTo(mid));
            out.add(new Spot("his crown", mid, Math.max(0.8, r + 1.5 * sc)));
        }
        return out;
    }

    /** is this bone one of his weak points (a glowing spot or his crown) */
    public static boolean weakBone(HollowbellEntity hb, int bone) {
        if (bone < 0) return false;
        BellRig.Kind k = hb.rig.kind[bone];
        return k == BellRig.Kind.SPOT || k == BellRig.Kind.CROWN;
    }

    private static void tell(MinecraftServer server, LivingEntity g, DamageSource src, float amount, boolean weak) {
        Entity by = src.getEntity() != null ? src.getEntity() : src.getDirectEntity();
        JsonObject o = new JsonObject();
        o.addProperty("sys", VERSION);
        o.addProperty("giant", HollowbellMod.MOD_ID);
        o.addProperty("id", g.getStringUUID());
        o.addProperty("by", by == null ? "-" : by.getStringUUID());
        o.addProperty("amount", amount);
        o.addProperty("weak", weak);
        try {
            Class<?> c = Class.forName("net.jj.giants.GuideBridge", true, GiantsSystems.class.getClassLoader());
            Method m = c.getMethod("guide", MinecraftServer.class, String.class, String.class);
            m.invoke(null, server, "syshurt", o.toString());
        } catch (ClassNotFoundException e) {
            report = false; // (no Guide: nobody to tell)
        } catch (ReflectiveOperationException | RuntimeException e) {
            HollowbellMod.LOG.debug("Couldn't tell the Guide about a hit: {}", e.toString());
        }
    }

    // the bridge

    /** "sys": the one header line */
    public static @Nullable List<String> describe(@Nullable MinecraftServer server, String arg) {
        if (server == null) return null;
        try {
            if (arg != null && !arg.isBlank() && Integer.parseInt(arg.trim()) < 1) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        JsonObject head = new JsonObject();
        head.addProperty("sys", VERSION);
        head.addProperty("giant", HollowbellMod.MOD_ID);
        head.addProperty("name", WHO);
        JsonArray weak = new JsonArray();
        for (String s : WEAK) weak.add(s);
        head.add("weak", weak);
        JsonArray does = new JsonArray();
        for (String d : DOES) does.add(d);
        head.add("does", does);
        return List.of(head.toString());
    }

    /** "sysact": one thing on one of him; ["ok", line] or ["no", why]; null for what it can't read */
    public static @Nullable List<String> act(@Nullable MinecraftServer server, String arg) {
        if (server == null || arg == null) return null;
        UUID id;
        String what;
        JsonObject o;
        try {
            o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("sys").getAsInt() > VERSION) return null;
            id = UUID.fromString(o.get("id").getAsString());
            what = o.get("do").getAsString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
        if (!DOES.contains(what)) return null;
        HollowbellEntity h = null;
        for (ServerLevel l : server.getAllLevels())
            if (l.getEntity(id) instanceof HollowbellEntity e && !e.isRemoved() && !e.isDeadOrDying()) h = e;
        if (h == null) {
            for (Object x : net.jj.hollowbell.world.TakeMe.listed(server))
                if (x instanceof net.jj.hollowbell.world.Away.Rec r && id.equals(r.id)
                        || x instanceof net.jj.hollowbell.world.TakeMe.Lying k && id.equals(k.id()))
                    return no("He's out of the world right now.");
            return no("He isn't there any more.");
        }
        switch (what) {
            case "spots" -> {
                JsonArray a = new JsonArray();
                for (Spot s : spots(h)) {
                    JsonObject j = new JsonObject();
                    j.addProperty("name", s.name());
                    j.addProperty("x", round(s.at().x));
                    j.addProperty("y", round(s.at().y));
                    j.addProperty("z", round(s.at().z));
                    j.addProperty("r", round(s.r()));
                    a.add(j);
                }
                JsonObject out = new JsonObject();
                out.add("spots", a);
                return List.of("ok", out.toString());
            }
            case "mood" -> {
                String v = o.has("value") ? o.get("value").getAsString().trim().toLowerCase(Locale.ROOT) : "";
                int m = List.of(MOODS).indexOf(v);
                if (m < 0) return no("His moods are: " + String.join(", ", MOODS) + ".");
                h.setVariant(m);
                if (h.isGuardian()) h.setHome(h.position());
                return ok(WHO + ": mood " + MOODS[m] + ".");
            }
            case "turn" -> {
                ServerPlayer p;
                try {
                    p = server.getPlayerList().getPlayer(UUID.fromString(o.get("player").getAsString()));
                } catch (RuntimeException e) {
                    p = null;
                }
                if (p == null) return no("That player isn't online.");
                // his own end of patience: book or no book, he hunts them for a game day
                h.mood().hunt(p.getUUID(), 24000);
                if (p.level() == h.level() && p.distanceTo(h) < 256) h.sendAfter(p);
                return ok(WHO + " turned on " + p.getGameProfile().getName() + ".");
            }
            case "go" -> {
                double x, z;
                try {
                    x = o.get("x").getAsDouble();
                    z = o.get("z").getAsDouble();
                } catch (RuntimeException e) {
                    return null;
                }
                if (!Double.isFinite(x) || !Double.isFinite(z)) return null;
                h.wanderToward(new Vec3(x, h.getY(), z));
                return ok(WHO + ": on his way.");
            }
            default -> {
                return null;
            }
        }
    }

    private static double round(double d) {
        return Math.round(d * 100) / 100.0;
    }

    private static List<String> ok(String line) {
        return List.of("ok", line);
    }

    private static List<String> no(String why) {
        return List.of("no", why);
    }

    /** (the tests) what he holds now */
    public static float weakHitForTests() {
        return weakOn ? weakHit : 0f;
    }

    /** (the tests) whether a hit would be told to the Guide */
    public static boolean reportsForTests() {
        return report;
    }

    /** (the tests) a player with a hit landing straight at a spot: is it on one of his weak points */
    public static boolean nearForTests(List<Spot> spots, Player p) {
        return near(spots, p.damageSources().playerAttack(p));
    }
}
