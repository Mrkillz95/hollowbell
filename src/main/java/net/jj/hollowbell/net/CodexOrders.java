package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Mood;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.world.BellWorld;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What the buttons in the book actually do, once the press reaches the server. Laid out like the Mountain's. */
public final class CodexOrders {
    private CodexOrders() {}

    /** the Hollowbell the book reaches: the nearest one, within the book's range */
    public static @Nullable HollowbellEntity his(Player p) {
        HollowbellEntity best = null;
        double bd = Math.pow(HollowbellConfig.V.bookRange, 2);
        if (!(p.level() instanceof ServerLevel sl)) return null;
        for (HollowbellEntity m : sl.getEntities(ModEntities.HOLLOWBELL, m -> !m.isDeadOrDying())) {
            double d = m.distanceToSqr(p);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.hollowbell." + key, args), true);
    }

    private static void say2(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.hollowbell." + key, args), false);
    }

    /** whatever the player is looking at, out to a long way: a creature if there is one, otherwise ground */
    private static @Nullable HitResult looking(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition(), dir = p.getViewVector(1f), to = from.add(dir.scale(range));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, from, to, new AABB(from, to).inflate(2),
                e -> !e.isSpectator() && e.isPickable() && !(e instanceof HollowbellEntity) && e != p);
        if (eh != null) return eh;
        BlockHitResult bh = p.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return bh.getType() == HitResult.Type.MISS ? null : bh;
    }

    // ------------------------------------------------------------------ how often the book can ask for a move
    public static final int ANY_COOL = 60;

    /** how long before the book can ask for the same move again: light ones soon, heavy ones not for a good while */
    public static int attackCool(int move) {
        return switch (Moves.tier(move)) {
            case Moves.LIGHT -> 120;
            case Moves.MEDIUM -> 300;
            default -> 700;
        };
    }
    private static final java.util.Map<java.util.UUID, long[]> lastUse = new java.util.HashMap<>();

    private static long[] book(ServerPlayer p) {
        return lastUse.computeIfAbsent(p.getUUID(), u -> {
            long[] a = new long[Moves.NAMES.length];
            java.util.Arrays.fill(a, Long.MIN_VALUE / 4);
            return a;
        });
    }

    public static long coolLeft(ServerPlayer p, int move) {
        long[] t = book(p);
        long now = p.level().getGameTime();
        return Math.max(0, Math.max(attackCool(move) - (now - t[move]), ANY_COOL - (now - t[0])));
    }

    /** what each move takes out of him, 1 being all his wind */
    /**
     * What each move takes out of him, 1 being all his wind. Like the Mountain's: the light ones are cheap, the heavy
     * ones take nearly half of him each.
     */
    private static final float[] MOVE_WIND = new float[Moves.NAMES.length];
    static {
        float[][] w = {
                {Moves.GRAB, 0.10f}, {Moves.HARVEST, 0.08f}, {Moves.VOLLEY, 0.07f}, {Moves.LASH, 0.07f}, {Moves.FLASH, 0.09f},
                {Moves.CURTAIN, 0.18f}, {Moves.SWEEP, 0.15f}, {Moves.SLAM, 0.15f}, {Moves.WRAP, 0.16f}, {Moves.PULSE, 0.22f},
                {Moves.SHED, 0.22f}, {Moves.SPORES, 0.18f}, {Moves.POD_BURST, 0.20f}, {Moves.EGG_RAIN, 0.22f},
                {Moves.DROP, 0.42f}, {Moves.WHIRLPOOL, 0.45f}, {Moves.SKY_DIVE, 0.55f}, {Moves.DEEP_TOLL, 0.50f}, {Moves.ARM_STORM, 0.48f},
                {Moves.STINGER_STORM, 0.45f}, {Moves.SUN_LANCES, 0.48f}, {Moves.UNDERTOW, 0.46f}};
        for (float[] e : w) MOVE_WIND[(int) e[0]] = e[1];
    }

    public static float windCost(int action, int arg) {
        return switch (action) {
            case CodexPayload.ATTACK_MOVE -> arg > 0 && arg < MOVE_WIND.length ? MOVE_WIND[arg] : 0.10f;
            case CodexPayload.RIDE -> 0.06f;
            case CodexPayload.CALM, CodexPayload.HUNTER, CodexPayload.GUARDIAN -> 0.04f;
            case CodexPayload.ATTACK_THAT -> 0.03f;
            case CodexPayload.COME, CodexPayload.GO_THERE, CodexPayload.GO_TO_XZ -> 0.02f;
            default -> 0f;
        };
    }

    /** the moves he takes badly: the ones that cost him pieces of himself */
    public static boolean takesItBadly(int action, int arg) {
        return action == CodexPayload.ATTACK_MOVE && (arg == Moves.SHED || arg == Moves.EGG_RAIN || arg == Moves.POD_BURST || Moves.tier(arg) == Moves.HEAVY);
    }

    private static float sourCost(int action, int arg) {
        if (action != CodexPayload.ATTACK_MOVE) return 0f;
        if (arg == Moves.SHED || arg == Moves.EGG_RAIN) return 0.2f;
        if (arg == Moves.POD_BURST) return 0.1f;
        return Moves.tier(arg) == Moves.HEAVY ? 0.08f : 0f;
    }

    /** a move pressed while riding his crown: the same clock the book uses */
    public static void moveFromCrown(ServerPlayer p, HollowbellEntity m, int which) {
        if (which <= 0 || which >= Moves.NAMES.length) return;
        if (coolLeft(p, which) > 0) return;
        float cost = windCost(CodexPayload.ATTACK_MOVE, which);
        if (!m.mood().hasWind(cost)) { say(p, "codex_winded"); return; }
        if (!m.forceMove(which)) { say(p, "codex_cannot_attack"); return; }
        m.mood().spendWind(cost);
        long[] t = book(p);
        t[which] = t[0] = p.level().getGameTime();
    }

    private record Later(java.util.UUID who, CodexPayload pay, long at) {}
    private static final java.util.List<Later> waiting = new java.util.ArrayList<>();

    public static void forgetEverything() { waiting.clear(); lastUse.clear(); }

    public static void serverTick(net.minecraft.server.MinecraftServer server) {
        if (!waiting.isEmpty()) {
            long now = server.overworld().getGameTime();
            java.util.List<Later> due = new java.util.ArrayList<>();
            waiting.removeIf(l -> { if (l.at() <= now) { due.add(l); return true; } return false; });
            for (Later l : due) {
                ServerPlayer p = server.getPlayerList().getPlayer(l.who());
                if (p != null) handle(p, l.pay(), true);
            }
        }
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (CodexItem.carriedBy(p)) sendMood(p, his(p));
    }

    public static void sendMood(ServerPlayer p, @Nullable HollowbellEntity m) {
        float wind = m == null ? 1f : m.mood().wind();
        float sour = m == null ? 0f : m.mood().sourOf(p.getUUID());
        int stage = m == null ? 0 : m.mood().stage(p.getUUID());
        int days = m == null ? 0 : m.mood().huntDaysLeft(p.getUUID());
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new MoodPayload(wind, sour, stage, days, 0));
    }

    /** his wind has to cover it, and how he feels about you decides whether he does it now, later, or not at all */
    private static boolean gate(ServerPlayer p, HollowbellEntity m, CodexPayload pay) {
        Mood mood = m.mood();
        int action = pay.action();
        int stage = mood.stage(p.getUUID());
        if (stage == Mood.TURNED) { say(p, "codex_turned"); return false; }
        if (action == CodexPayload.ATTACK_MOVE) {
            int which = pay.arg();
            if (which <= 0 || which >= Moves.NAMES.length) return false;
            long left = coolLeft(p, which);
            if (left > 0) { say(p, "codex_cooling", (int) Math.ceil(left / 20.0)); return false; }
        }
        float cost = windCost(action, pay.arg());
        if (cost > 0f && !mood.hasWind(cost)) {
            mood.sour(p.getUUID(), 0.07f);
            say(p, "codex_winded");
            sendMood(p, m);
            return false;
        }
        float sting = sourCost(action, pay.arg());
        if (sting > 0f) mood.sour(p.getUUID(), sting);
        if (stage >= Mood.BALKY && p.getRandom().nextInt(3) == 0) {
            mood.spendWind(cost * 0.25f);
            say(p, "codex_ignored");
            sendMood(p, m);
            return false;
        }
        mood.spendWind(cost);
        if (stage >= Mood.SLOW) {
            if (waiting.size() < 64) waiting.add(new Later(p.getUUID(), pay, p.level().getGameTime() + 20 + p.getRandom().nextInt(21)));
            say(p, "codex_slow");
            sendMood(p, m);
            return false;
        }
        sendMood(p, m);
        return true;
    }

    public static void handle(ServerPlayer p, CodexPayload pay) { handle(p, pay, false); }

    private static void handle(ServerPlayer p, CodexPayload pay, boolean already) {
        int action = pay.action();
        if (!(p.level() instanceof ServerLevel)) return;
        if (!CodexItem.carriedBy(p)) { say(p, "codex_lost"); return; }
        HollowbellEntity m = his(p);
        switch (action) {
            case CodexPayload.SPARE_LOOK -> { spareLooked(p); return; }
            case CodexPayload.SPARE_ME -> { spare(p, p); return; }
            case CodexPayload.SPARE_NEAR -> { spareNear(p); return; }
            case CodexPayload.SAFE_GET -> { sendSafeList(p); return; }
            case CodexPayload.WHERE -> { where(p, m); return; }
            default -> {}
        }
        if (m == null) { say(p, "codex_none"); return; }
        if (!already && !gate(p, m, pay)) return;
        int mind = m.mood().stage(p.getUUID());
        switch (action) {
            case CodexPayload.ATTACK_MOVE -> {
                int which = pay.arg();
                if (which <= 0 || which >= Moves.NAMES.length) return;
                LivingEntity at = m.getTarget();
                HitResult h = looking(p, 320);
                if (h instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) at = le;
                if (!m.forceMove(which, at)) { m.mood().refund(windCost(action, which)); say(p, "codex_cannot_attack"); return; }
                long[] t = book(p);
                t[which] = t[0] = p.level().getGameTime();
                say(p, "codex_attacking", Component.translatable("move.hollowbell." + Moves.NAMES[which]));
            }
            case CodexPayload.BREAK_BLOCKS -> {
                if (!p.hasPermissions(2)) { say(p, "codex_not_yours"); return; }
                boolean on = pay.arg() != 0;
                if (HollowbellConfig.V.griefing != on) { HollowbellConfig.V.griefing = on; HollowbellConfig.save(); }
                say(p, on ? "codex_break_on" : "codex_break_off");
            }
            case CodexPayload.HARVEST -> {
                if (!p.hasPermissions(2)) { say(p, "codex_not_yours"); return; }
                boolean on = pay.arg() != 0;
                if (HollowbellConfig.V.harvest != on) { HollowbellConfig.V.harvest = on; HollowbellConfig.save(); }
                say(p, on ? "codex_harvest_on" : "codex_harvest_off");
            }
            case CodexPayload.GO_TO_XZ -> {
                Vec3 at = new Vec3(pay.x(), m.groundAt(pay.x(), pay.z()), pay.z());
                m.setGoal(at);
                say(p, "codex_goto", (int) pay.x(), (int) pay.z());
            }
            case CodexPayload.FORGIVE -> { m.forgiveAll(); say(p, "codex_forgive"); }
            case CodexPayload.LET_GO -> { m.moves().letGoOfEverything(false); say(p, "codex_let_go"); }
            case CodexPayload.COME -> { m.clearHitList(); m.callTo(p); say(p, "codex_come", (int) Math.sqrt(m.distanceToSqr(p))); }
            case CodexPayload.GO_THERE -> {
                HitResult h = looking(p, 320);
                if (h == null) { say(p, "codex_nowhere"); return; }
                Vec3 at = h.getLocation();
                m.setGoal(at);
                say(p, "codex_goto", (int) at.x, (int) at.z);
            }
            case CodexPayload.ATTACK_THAT -> {
                HitResult h = looking(p, 320);
                if (h instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le) {
                    if (mind >= Mood.WILFUL) { m.clearHitList(); say(p, "codex_own_target"); }
                    else { m.sendAfter(le); say(p, "codex_kill", le.getDisplayName().getString()); }
                } else say(p, "codex_nothing_there");
            }
            case CodexPayload.STAY -> { m.setStay(!m.staying()); say(p, m.staying() ? "codex_stay" : "codex_free"); }
            case CodexPayload.CALM, CodexPayload.HUNTER, CodexPayload.GUARDIAN -> {
                int v = action == CodexPayload.CALM ? HollowbellEntity.CALM : action == CodexPayload.HUNTER ? HollowbellEntity.HUNTER : HollowbellEntity.GUARDIAN;
                m.setVariant(v);
                if (v == HollowbellEntity.GUARDIAN) m.setHome(m.position());
                say(p, "codex_mode", Component.translatable("mode.hollowbell." + v));
            }
            case CodexPayload.RIDE -> {
                if (m.carrying()) { m.dropRider(); say(p, "codex_off"); }
                else if (m.comingForSomebody()) { m.stopFetch(); say(p, "codex_never_mind"); }
                else if (m.comeAndGetMe(p)) say(p, "codex_coming");
                else say(p, "codex_cannot_ride");
            }
            case CodexPayload.CALL_OFF -> { m.clearHitList(); m.setGoal(null); say(p, "codex_calloff"); }
            default -> {}
        }
    }

    /** whatever you are looking at goes on your list: a player by name, anything else by its kind */
    private static void spareLooked(ServerPlayer p) {
        HitResult h = looking(p, 120);
        if (!(h instanceof EntityHitResult eh)) { say(p, "codex_no_player"); return; }
        Entity who = eh.getEntity();
        if (who instanceof Player pl) { spare(p, pl); return; }
        if (!(who instanceof LivingEntity)) { say(p, "codex_no_player"); return; }
        BellWorld w = BellWorld.get(p.server);
        boolean on = w.toggleKind(p.getUUID(), who.getType());
        say(p, on ? "codex_spared_kind" : "codex_unspared_kind", who.getType().getDescription());
        sendSafeList(p);
    }

    private static void spare(ServerPlayer p, Player who) {
        BellWorld w = BellWorld.get(p.server);
        w.rememberName(who.getUUID(), who.getGameProfile().getName());
        boolean on = w.toggleFriend(p.getUUID(), who.getUUID());
        say(p, on ? "codex_spared" : "codex_unspared", who.getGameProfile().getName());
        sendSafeList(p);
    }

    private static void spareNear(ServerPlayer p) {
        BellWorld w = BellWorld.get(p.server);
        int n = 0;
        for (Player o : p.level().players()) {
            if (o == p || o.distanceToSqr(p) > 48 * 48) continue;
            w.rememberName(o.getUUID(), o.getGameProfile().getName());
            w.addFriend(p.getUUID(), o.getUUID());
            n++;
        }
        say(p, "codex_spared_near", n);
        sendSafeList(p);
    }

    public static void dropFromSafeList(ServerPlayer p, String id) {
        BellWorld w = BellWorld.get(p.server);
        if (id.startsWith("who:")) {
            try {
                java.util.UUID who = java.util.UUID.fromString(id.substring(4));
                w.dropFriend(p.getUUID(), who);
                say(p, "codex_unspared", w.nameOf(who));
            } catch (IllegalArgumentException ignored) { }
        } else if (id.startsWith("kind:")) {
            String kind = id.substring(5);
            w.dropKind(p.getUUID(), kind);
            say(p, "codex_unspared_kind", BellWorld.kindName(kind));
        }
        sendSafeList(p);
    }

    public static void sendSafeList(ServerPlayer p) {
        BellWorld w = BellWorld.get(p.server);
        java.util.List<String> names = new java.util.ArrayList<>(), ids = new java.util.ArrayList<>();
        for (java.util.UUID u : w.listOf(p.getUUID())) { names.add(w.nameOf(u)); ids.add("who:" + u); }
        for (String id : w.kindsOf(p.getUUID())) { names.add(BellWorld.kindName(id)); ids.add("kind:" + id); }
        HollowbellEntity m = his(p);
        boolean inForce = m != null && m.bookHolder() == p;
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new SafeListPayload(names, ids, inForce, CodexItem.carriedBy(p)));
        sendMood(p, m);
    }

    private static void where(ServerPlayer p, @Nullable HollowbellEntity m) {
        if (m == null) { say(p, "codex_none"); return; }
        double dx = m.getX() - p.getX(), dz = m.getZ() - p.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        int seg = (int) Math.round(Math.atan2(dx, dz) / (Math.PI / 4)) & 7;
        String[] way = {"south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"};
        say(p, "compass", dist, way[seg], (int) m.getX(), (int) m.getZ());
        say2(p, "compass_doing", Component.translatable("doing.hollowbell." + doing(m)));
    }

    /** what the book says he is doing */
    public static String doing(HollowbellEntity m) {
        if (m.carrying()) return "ridden";
        if (m.sunk()) return "sunk";
        if (m.tired()) return "tired";
        if (m.moveNow() == Moves.DROP) return "down";
        if (m.moveNow() != Moves.NONE) return "fighting";
        if (m.staying()) return "still";
        if (m.getTarget() != null) return "hunting";
        return "drifting";
    }
}
