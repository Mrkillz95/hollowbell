package net.jj.giants.fun;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3 "Stage a fight") Two giants and a spot: each a fresh one (his own summon, calm, at
 * that size) or one already out there (moved by his own mod), standing facing each other across the spot, then each
 * told to go for the other (the meet bridge, a fight a player started: neither backs down).
 */
public final class Fights {
    private Fights() {}

    /** one side: a kind and a size for a fresh one, or one out there */
    public record Side(Giant kind, double size, @Nullable LivingEntity existing) {
        String name() {
            return AdminServer.nameOf(kind);
        }

        double reach() {
            return FunGiants.reach(kind.key(), existing != null ? FunGiants.sizeOf(existing) : size);
        }
    }

    private record Meeting(ServerLevel level, UUID a, UUID b, String ka, String kb, int[] ticks, String who) {}

    private static final List<Meeting> meetings = new ArrayList<>();

    public static AdminServer.Result stage(MinecraftServer server, ServerPlayer p, Side a, Side b, boolean look) {
        if (a.existing() != null && a.existing() == b.existing())
            return AdminServer.Result.fail("Pick two different giants.");
        BlockPos at = look ? Spots.looked(p, Spots.LOOK) : p.blockPosition();
        if (at == null) return AdminServer.Result.fail("Look at the ground where they should fight (256 blocks at most).");
        ServerLevel l = p.serverLevel();
        for (Side s : List.of(a, b))
            if (s.existing() != null && s.existing().level() != l)
                return AdminServer.Result.fail("The " + s.name() + " is in another world. Stage the fight there, or"
                        + " pick a fresh one.");
        Vec3 look3 = p.getViewVector(1f).multiply(1, 0, 1);
        if (look3.lengthSqr() < 1e-4) look3 = new Vec3(0, 0, 1);
        look3 = look3.normalize();
        // side by side as you look: A on your left, B on your right
        Vec3 across = new Vec3(-look3.z, 0, look3.x);
        double gap = a.reach() + b.reach() + 8;
        Vec3 mid = Vec3.atBottomCenterOf(at);
        Vec3 pa = mid.subtract(across.scale(gap / 2)), pb = mid.add(across.scale(gap / 2));
        BlockPos ba = BlockPos.containing(pa), bb = BlockPos.containing(pb);
        String who = p.getGameProfile().getName();
        Spots.whenLoaded(l, List.of(ba, bb, at), () -> {
            UUID ia = place(server, l, a, pa, pb), ib = place(server, l, b, pb, pa);
            if (ia == null || ib == null) {
                p.displayClientMessage(Component.literal("The fight couldn't be set up there: "
                        + (ia == null ? a.name() : b.name()) + " couldn't be put in place."), false);
                return;
            }
            meetings.add(new Meeting(l, ia, ib, a.kind().key(), b.kind().key(), new int[] {40}, who));
        }, () -> p.displayClientMessage(Component.literal("The land there isn't ready yet. Try again in a moment."),
                false));
        return AdminServer.Result.said(List.of("Setting up the fight: " + a.name() + " against " + b.name() + "…"));
    }

    /** puts one side at its spot facing the other; his id, or null */
    private static @Nullable UUID place(MinecraftServer server, ServerLevel l, Side s, Vec3 at, Vec3 facing) {
        double y = Spots.ground(l, Mth.floor(at.x), Mth.floor(at.z));
        if (Double.isNaN(y)) y = at.y;
        if (s.existing() != null) {
            JsonObject o = new JsonObject();
            o.addProperty("dim", l.dimension().location().toString());
            o.addProperty("x", at.x);
            o.addProperty("y", y);
            o.addProperty("z", at.z);
            o.addProperty("where", "land");
            o.addProperty("stay", false);
            AdminServer.Result r = FunGiants.act(server, s.kind(), s.existing().getStringUUID(), "move", o);
            if (!r.ok()) return null;
            face(s.existing(), facing);
            return s.existing().getUUID();
        }
        // a fresh one: his own summon at the spot (nobody named, so exactly there), calm when he has that mood
        Set<UUID> before = new HashSet<>();
        for (LivingEntity e : FunGiants.standing(server, s.kind().key())) before.add(e.getUUID());
        List<String> moods = moods(server, s.kind());
        String arg = l.dimension().location() + " " + at.x + " " + y + " " + at.z + " -"
                + (moods.contains("calm") ? " calm" : "") + " " + AdminSetting.plain(s.size());
        AdminServer.call(server, s.kind(), "summon", arg);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : FunGiants.standing(server, s.kind().key())) {
            if (before.contains(e.getUUID()) || e.level() != l) continue;
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        if (best == null) return null;
        face(best, facing);
        return best.getUUID();
    }

    private static void face(LivingEntity e, Vec3 to) {
        Vec3 d = to.subtract(e.position());
        float yaw = (float) (Mth.atan2(d.z, d.x) * 180 / Math.PI) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.setYBodyRot(yaw);
    }

    static List<String> moods(MinecraftServer server, Giant g) {
        List<String> lines = AdminServer.call(server, g, AdminLive.LIST, String.valueOf(AdminLive.VERSION));
        List<String> out = new ArrayList<>();
        if (lines == null || lines.isEmpty()) return out;
        JsonObject head = net.jj.giants.admin.AdminJson.object(lines.get(0));
        if (head != null && head.get("moods") instanceof com.google.gson.JsonArray a)
            for (var e : a) out.add(e.getAsString());
        return out;
    }

    /** two seconds after they're in place: each goes for the other */
    static void tick(MinecraftServer server) {
        if (meetings.isEmpty()) return;
        for (Meeting m : List.copyOf(meetings)) {
            if (--m.ticks()[0] > 0) continue;
            meetings.remove(m);
            Giant ga = AdminServer.giant(m.ka()), gb = AdminServer.giant(m.kb());
            if (ga == null || gb == null) continue;
            List<String> said = new ArrayList<>();
            List<String> r = AdminServer.call(server, ga, "meet", m.b() + " " + m.a());
            if (r != null) said.addAll(r);
            r = AdminServer.call(server, gb, "meet", m.a() + " " + m.b());
            if (r != null) said.addAll(r);
            String line = m.who() + " staged a fight: " + AdminServer.nameOf(ga) + " against " + AdminServer.nameOf(gb)
                    + ".";
            ServerPlayer p = server.getPlayerList().getPlayerByName(m.who());
            if (p != null) p.displayClientMessage(Component.literal("The fight is on: " + AdminServer.nameOf(ga)
                    + " against " + AdminServer.nameOf(gb) + "."), false);
            Fun.log(server, m.who(), m.ka(), "fight", "staged fight", AdminServer.nameOf(ga) + " against "
                    + AdminServer.nameOf(gb));
            Fun.tell(server, p, line);
            lastMet = said;
            Fun.changed();
        }
    }

    /** (the tests) what the two meet calls said */
    public static List<String> lastMet = List.of();

    public static boolean busy() {
        return !meetings.isEmpty();
    }

    static void forget() {
        meetings.clear();
    }
}
