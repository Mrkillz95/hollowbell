package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Mood;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.CodexItem;
import net.jj.hollowbell.world.Away;
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
            case CodexPayload.ATTACK_THAT, CodexPayload.BIND_HERE, CodexPayload.FREE_ROAM -> 0.03f;
            case CodexPayload.COME, CodexPayload.GO_THERE, CodexPayload.GO_TO_XZ, CodexPayload.SLEEP -> 0.02f;
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
        sendMood(p, m);
    }

    private record Later(java.util.UUID who, CodexPayload pay, long at) {}
    private static final java.util.List<Later> waiting = new java.util.ArrayList<>();

    public static void forgetEverything() { waiting.clear(); lastUse.clear(); }

    /** a player has left: nothing of theirs is kept hanging about */
    public static void forgetPlayer(java.util.UUID who) {
        lastUse.remove(who);
        waiting.removeIf(l -> l.who().equals(who));
    }

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
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // riding him, his wind and grudge show on screen too, book or not
            HollowbellEntity r = riding(p);
            if (r != null) sendMood(p, r);
            else if (CodexItem.carriedBy(p)) sendMood(p, his(p));
        }
    }

    /** the Hollowbell this player is riding, if any */
    public static @Nullable HollowbellEntity riding(ServerPlayer p) {
        if (p.getVehicle() instanceof net.jj.hollowbell.entity.Seat seat
                && p.level().getEntity(seat.ownerId()) instanceof HollowbellEntity h && h.rider() == p) return h;
        return null;
    }

    public static void sendMood(ServerPlayer p, @Nullable HollowbellEntity m) {
        float wind = m == null ? 1f : m.mood().wind();
        float sour = m == null ? 0f : m.mood().sourOf(p.getUUID());
        int stage = m == null ? 0 : m.mood().stage(p.getUUID());
        int days = m == null ? 0 : m.mood().huntDaysLeft(p.getUUID());
        int flags = (m != null ? MoodPayload.IN_REACH : 0) | (HollowbellConfig.V.griefing ? MoodPayload.GRIEF : 0)
                | (HollowbellConfig.V.harvest ? MoodPayload.HARVEST : 0) | (m != null && m.asleep() ? MoodPayload.ASLEEP : 0);
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new MoodPayload(wind, sour, stage, days, flags));
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
            case CodexPayload.SPARE_KIND -> { spareKindLooked(p); return; }
            case CodexPayload.SPARE_ME -> { spare(p, p); return; }
            case CodexPayload.SPARE_NEAR -> { spareNear(p); return; }
            case CodexPayload.SAFE_GET -> { sendSafeList(p); return; }
            case CodexPayload.WHERE -> { if (m != null) where(p, m); else awayOrder(p, pay); return; }
            // "go after what I look at": at any distance, out of the world too
            case CodexPayload.ATTACK_THAT -> { attackLooked(p, pay, already); return; }
            default -> {}
        }
        if (m == null) {
            // a movement order reaches him anywhere: out of the world, far off, or in land nobody has loaded
            if (action == CodexPayload.COME || action == CodexPayload.GO_THERE || action == CodexPayload.GO_TO_XZ) farOrder(p, pay);
            else awayOrder(p, pay);
            return;
        }
        // a woken crown's circle: the book cannot send him in there
        Vec3 warded = wardedDest(p, pay);
        if (warded != null) { say(p, "ward_refused"); return; }
        if (!already && !gate(p, m, pay)) return;
        int mind = m.mood().stage(p.getUUID());
        // any order but the one to sleep wakes him first
        if (action != CodexPayload.SLEEP) m.wakeUp();
        switch (action) {
            case CodexPayload.SLEEP -> {
                if (m.asleep()) { m.wakeUp(); say(p, "codex_wake"); }
                else { m.goToSleep(); say(p, "codex_sleep"); }
                sendMood(p, m);
            }
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
                m.orderTo(at, null);
                say(p, "codex_goto", net.minecraft.util.Mth.floor(pay.x()), net.minecraft.util.Mth.floor(pay.z()));
            }
            case CodexPayload.FORGIVE -> { m.forgiveAll(); say(p, "codex_forgive"); }
            case CodexPayload.LET_GO -> { m.moves().letGoOfEverything(false); say(p, "codex_let_go"); }
            case CodexPayload.COME -> { m.clearHitList(); m.orderTo(p.position(), p); say(p, "codex_come", (int) Math.sqrt(m.distanceToSqr(p))); }
            case CodexPayload.GO_THERE -> {
                HitResult h = looking(p, 320);
                Vec3 at = h != null ? h.getLocation() : net.jj.hollowbell.world.FarOrders.farLook(p);
                m.orderTo(at, null);
                say(p, "codex_goto", net.minecraft.util.Mth.floor(at.x), net.minecraft.util.Mth.floor(at.z));
            }
            case CodexPayload.STAY -> { m.setStay(!m.staying()); if (m.staying()) m.endOrderedHunt(); say(p, m.staying() ? "codex_stay" : "codex_free"); }
            case CodexPayload.CALM, CodexPayload.HUNTER, CodexPayload.GUARDIAN -> {
                int v = action == CodexPayload.CALM ? HollowbellEntity.CALM : action == CodexPayload.HUNTER ? HollowbellEntity.HUNTER : HollowbellEntity.GUARDIAN;
                m.setVariant(v);
                if (v == HollowbellEntity.GUARDIAN) m.setHome(m.position());
                say(p, "codex_mode", Component.translatable("mode.hollowbell." + v));
            }
            case CodexPayload.RIDE -> {
                if (m.carrying()) { m.setMeDown(p); say(p, "codex_off"); }
                else if (m.comingForSomebody()) { m.stopFetch(); say(p, "codex_never_mind"); }
                else if (m.comeAndGetMe(p)) say(p, "codex_coming");
                else say(p, "codex_cannot_ride");
            }
            case CodexPayload.CALL_OFF -> { m.clearHitList(); m.setGoal(null); say(p, "codex_calloff"); }
            case CodexPayload.BIND_HERE -> {
                int r = net.minecraft.util.Mth.clamp(pay.arg(), 32, 100000);
                m.bindTo(p.getX(), p.getZ(), r);
                say(p, "codex_bound", r);
            }
            case CodexPayload.FREE_ROAM -> { m.unbind(); say(p, "codex_roam"); }
            default -> {}
        }
    }

    /**
     * "Go after what I look at". pay.arg() is the creature the player's screen found (its id; 0 or less: the server
     * looks itself). He goes after it from any distance, asleep (he wakes), out of the world (his trip is aimed at it
     * and he takes it up when he's back), ridden or not. What it said, as a message key (for the tests).
     */
    public static String attackLooked(ServerPlayer p, CodexPayload pay, boolean already) {
        LookOrder.Found f = LookOrder.find(p, pay.arg());
        LivingEntity t = f.target();
        if (t == null) { LookOrder.refuse(p, f.why(), null); return f.why(); }
        ServerLevel sl = p.serverLevel();
        HollowbellEntity m = his(p);
        var far = m == null ? net.jj.hollowbell.world.FarOrders.nearest(sl, p.position()) : null;
        if (m == null && far != null && far.kind() == net.jj.hollowbell.world.FarOrders.Kind.LOADED) m = far.entity();
        if (m == null && far == null) { say(p, "codex_none"); return "codex_none"; }
        if (m == null) {
            // out of the world, or in land nobody has loaded
            if (t instanceof HollowbellEntity) { LookOrder.refuse(p, "look_kin", t); return "look_kin"; }
            if (t instanceof net.jj.hollowbell.entity.Belling) { LookOrder.refuse(p, "look_his_own", t); return "look_his_own"; }
            Component said = net.jj.hollowbell.world.FarOrders.hunt(sl, far, t, windCost(CodexPayload.ATTACK_THAT, 0));
            if (said == null) { say(p, "codex_none"); return "codex_none"; }
            p.displayClientMessage(said, true);
            LookOrder.mark(p, t);
            return "look_going_away";
        }
        String why = m.huntRefusal(t);
        if (why != null) { LookOrder.refuse(p, why, t); return why; }
        if (!already && !gate(p, m, pay)) return "gate";
        if (m.mood().stage(p.getUUID()) >= Mood.WILFUL) { m.clearHitList(); say(p, "codex_own_target"); return "codex_own_target"; }
        m.sendAfter(t);
        LookOrder.mark(p, t);
        double d = Math.hypot(t.getX() - m.getX(), t.getZ() - m.getZ());
        if (d > 64 + 40 * m.bellScale()) say(p, "look_going_far", t.getDisplayName().getString(), net.jj.hollowbell.item.FinderItem.tens(d));
        else say(p, "look_going", t.getDisplayName().getString());
        return "look_going";
    }

    /** a movement order to one out of reach of the book: at any distance, the same as near */
    private static void farOrder(ServerPlayer p, CodexPayload pay) {
        ServerLevel sl = p.serverLevel();
        var t = net.jj.hollowbell.world.FarOrders.nearest(sl, p.position());
        if (t == null) { say(p, "codex_none"); return; }
        if (wardedDest(p, pay) != null) { say(p, "ward_refused"); return; }
        Vec3 to;
        Player follow = null;
        switch (pay.action()) {
            case CodexPayload.COME -> { to = p.position(); follow = p; }
            case CodexPayload.GO_TO_XZ -> to = new Vec3(pay.x(), p.getY(), pay.z());
            default -> {
                HitResult h = looking(p, 320);
                to = h != null ? h.getLocation() : net.jj.hollowbell.world.FarOrders.farLook(p);
            }
        }
        Component said = net.jj.hollowbell.world.FarOrders.order(sl, t, to, follow, windCost(pay.action(), pay.arg()));
        if (said == null) { say(p, "codex_none"); return; }
        p.displayClientMessage(said, true);
    }

    /** where this order would send him, if that spot is inside a woken crown's circle; null when it is fine */
    private static @Nullable Vec3 wardedDest(ServerPlayer p, CodexPayload pay) {
        Vec3 dest = switch (pay.action()) {
            case CodexPayload.COME -> p.position();
            case CodexPayload.GO_TO_XZ -> new Vec3(pay.x(), 0, pay.z());
            case CodexPayload.GO_THERE -> {
                HitResult h = looking(p, 320);
                yield h == null ? null : h.getLocation();
            }
            default -> null;
        };
        if (dest == null) return null;
        return net.jj.hollowbell.world.WorldOne.get(p.server).warded(p.level(), dest.x, dest.z) ? dest : null;
    }

    /**
     * No Hollowbell in reach, but one out of the world, drifting on his own as a sum: the book still reaches him
     * wherever he is. Moving him works; anything that needs him right in front of you says so.
     */
    private static void awayOrder(ServerPlayer p, CodexPayload pay) {
        ServerLevel sl = (ServerLevel) p.level();
        Away a = Away.get(p.server);
        Away.Rec r = a.nearest(sl, p.position());
        if (r == null) { say(p, "codex_none"); return; }
        if (wardedDest(p, pay) != null) { say(p, "ward_refused"); return; }
        long now = sl.getGameTime();
        switch (pay.action()) {
            case CodexPayload.WHERE -> {
                Vec3 s = r.spot(now);
                p.displayClientMessage(net.jj.hollowbell.item.FinderItem.found(sl, p.position(), s.x, s.z), false);
                if (r.going) say2(p, "compass_away_going", (int) r.toX, (int) r.toZ, Math.max(1, r.minutesLeft(now)));
                else say2(p, "compass_away_still");
                return;
            }
            case CodexPayload.COME -> { a.send(sl, r.id, p.position()); sayFar(p, r, now); }
            case CodexPayload.GO_TO_XZ -> { a.send(sl, r.id, new Vec3(pay.x(), 0, pay.z())); sayFar(p, r, now); }
            case CodexPayload.GO_THERE -> {
                HitResult h = looking(p, 320);
                if (h == null) { say(p, "codex_nowhere"); return; }
                a.send(sl, r.id, h.getLocation());
                sayFar(p, r, now);
            }
            case CodexPayload.STAY -> { a.stay(sl, r.id, !r.stay); say(p, r.stay ? "codex_stay" : "codex_free"); }
            case CodexPayload.CALL_OFF -> { a.stay(sl, r.id, false); r.going = false; a.setDirty(); say(p, "codex_calloff"); }
            // his circle is written on the body he carries, so it holds out there and when he comes back
            case CodexPayload.BIND_HERE -> {
                int rad = net.minecraft.util.Mth.clamp(pay.arg(), 32, 100000);
                r.body.putDouble("BoundX", p.getX()); r.body.putDouble("BoundZ", p.getZ()); r.body.putInt("BoundR", rad);
                a.setDirty();
                if (r.going) a.send(sl, r.id, new Vec3(r.toX, 0, r.toZ));      // his trip, kept inside it
                say(p, "codex_bound", rad);
            }
            case CodexPayload.FREE_ROAM -> { r.body.remove("BoundR"); a.setDirty(); say(p, "codex_roam"); }
            default -> say(p, "codex_far_away");
        }
    }

    private static void sayFar(ServerPlayer p, Away.Rec r, long now) {
        Vec3 s = r.spot(now);
        say(p, "codex_away_sent", (int) Math.hypot(s.x - p.getX(), s.z - p.getZ()), (int) r.toX, (int) r.toZ, Math.max(1, r.minutesLeft(now)));
    }

    /** whoever you are looking at goes on your list (or off it): a player, or this one creature (pets, named mobs, your horse) */
    private static void spareLooked(ServerPlayer p) {
        HitResult h = looking(p, 120);
        if (!(h instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity who) || who instanceof HollowbellEntity) {
            say(p, "codex_no_player"); return;
        }
        if (who instanceof Player pl) { spare(p, pl); return; }
        spareOne(p, who, null);
    }

    /** one creature on (or off) your list, kept by who it is; on = null toggles */
    public static boolean spareOne(ServerPlayer p, LivingEntity who, @Nullable Boolean on) {
        BellWorld w = BellWorld.get(p.server);
        w.rememberMob(who.getUUID(), who.getName().getString());
        boolean now = on == null ? w.toggleFriend(p.getUUID(), who.getUUID()) : on;
        if (on != null) { if (on) w.addFriend(p.getUUID(), who.getUUID()); else w.dropFriend(p.getUUID(), who.getUUID()); }
        say(p, now ? "codex_spared_one" : "codex_unspared_one", who.getName());
        sendSafeList(p);
        return now;
    }

    /** every creature of the kind you are looking at goes on your list (or off it) */
    private static void spareKindLooked(ServerPlayer p) {
        HitResult h = looking(p, 120);
        if (!(h instanceof EntityHitResult eh) || !(eh.getEntity() instanceof LivingEntity who) || who instanceof Player || who instanceof HollowbellEntity) {
            say(p, "codex_no_creature"); return;
        }
        spareKind(p, who.getType(), null);
    }

    /** a whole kind on (or off) your list; on = null toggles */
    public static boolean spareKind(ServerPlayer p, net.minecraft.world.entity.EntityType<?> type, @Nullable Boolean on) {
        BellWorld w = BellWorld.get(p.server);
        boolean now;
        if (on == null) now = w.toggleKind(p.getUUID(), type);
        else {
            now = on;
            boolean has = w.kindOnList(p.getUUID(), type);
            if (has != on) w.toggleKind(p.getUUID(), type);
        }
        say(p, now ? "codex_spared_kind" : "codex_unspared_kind", BellWorld.kindsName(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()));
        sendSafeList(p);
        return now;
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
            say(p, "codex_unspared_kind", BellWorld.kindsName(kind));
        }
        sendSafeList(p);
    }

    public static void sendSafeList(ServerPlayer p) {
        BellWorld w = BellWorld.get(p.server);
        java.util.List<String> names = new java.util.ArrayList<>(), ids = new java.util.ArrayList<>();
        for (java.util.UUID u : w.listOf(p.getUUID())) {
            names.add(w.isMob(u) ? Component.translatable("codex.hollowbell.list_one", w.nameOf(u)).getString() : w.nameOf(u));
            ids.add("who:" + u);
        }
        for (String id : w.kindsOf(p.getUUID())) {
            names.add(Component.translatable("codex.hollowbell.list_kind", BellWorld.kindsName(id)).getString());
            ids.add("kind:" + id);
        }
        HollowbellEntity m = his(p);
        boolean inForce = m != null && m.bookHolder() == p;
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new SafeListPayload(names, ids, inForce, CodexItem.carriedBy(p)));
        sendMood(p, m);
    }

    private static void where(ServerPlayer p, @Nullable HollowbellEntity m) {
        if (m == null) { say(p, "codex_none"); return; }
        p.displayClientMessage(net.jj.hollowbell.item.FinderItem.found(p.serverLevel(), p.position(), m.getX(), m.getZ()), false);
        say2(p, "compass_doing", Component.translatable("doing.hollowbell." + doing(m)));
    }

    /** what the book says he is doing */
    public static String doing(HollowbellEntity m) {
        if (m.asleep()) return "asleep";
        if (m.sleepiness() > 0.05f) return "waking";
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
