package net.jj.mountain.net;

import net.jj.mountain.ModItems;
import net.jj.mountain.entity.MountainEntity;
import net.jj.mountain.entity.MountainPart;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** What the buttons in the book actually do, once the press reaches the server. */
public final class CodexOrders {
    private CodexOrders() {}

    private static boolean holdingBook(ServerPlayer p) {
        return net.jj.mountain.item.MountainCodexItem.heldBy(p);
    }

    private static @Nullable MountainEntity his(ServerPlayer p) {
        MountainEntity best = null;
        double bd = Double.MAX_VALUE;
        for (MountainEntity m : p.serverLevel().getEntities(net.jj.mountain.ModEntities.MOUNTAIN, m -> !m.isDeadOrDying())) {
            double d = m.distanceToSqr(p);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    /** a second line, in the chat rather than over the hotbar, so the first one isn't wiped out */
    private static void say2(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.mountain_breathes." + key, args), false);
    }

    private static void say(ServerPlayer p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.mountain_breathes." + key, args), true);
    }

    /** whatever the player is looking at, out to a long way: a creature if there is one, otherwise ground */
    private static @Nullable HitResult looking(ServerPlayer p, double range) {
        Vec3 from = p.getEyePosition(), dir = p.getViewVector(1f), to = from.add(dir.scale(range));
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p.level(), p, from, to, new AABB(from, to).inflate(2),
                e -> !e.isSpectator() && e.isPickable() && !(e instanceof MountainPart) && !(e instanceof MountainEntity) && e != p);
        if (eh != null) return eh;
        BlockHitResult bh = p.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return bh.getType() == HitResult.Type.MISS ? null : bh;
    }

    // ------------------------------------------------------------------ how often the book can ask for an attack
    /** ticks before the same attack can be asked for again, and before any attack can be asked for again */
    public static final int ATTACK_COOL = 400, ANY_COOL = 60;
    private static final java.util.Map<java.util.UUID, long[]> lastUse = new java.util.HashMap<>();

    private static long[] book(ServerPlayer p) {
        return lastUse.computeIfAbsent(p.getUUID(), u -> {
            long[] a = new long[net.jj.mountain.entity.MountainAttacks.NAMES.length + 1];
            java.util.Arrays.fill(a, Long.MIN_VALUE / 4);
            return a;
        });
    }

    /** ticks left before this attack can be asked for again */
    public static long coolLeft(ServerPlayer p, int attack) {
        long[] t = book(p);
        long now = p.level().getGameTime();
        long mine = ATTACK_COOL - (now - t[attack]);
        long any = ANY_COOL - (now - t[0]);
        return Math.max(0, Math.max(mine, any));
    }

    /** setting off an attack from inside him: the same clock the book uses */
    public static void attackFromInside(ServerPlayer p, MountainEntity m, int which) {
        if (which <= 0 || which >= net.jj.mountain.entity.MountainAttacks.NAMES.length) return;
        if (coolLeft(p, which) > 0) return;
        float cost = windCost(CodexPayload.ATTACK_MOVE, which);
        if (!m.mood().hasWind(cost)) { say(p, "codex_winded"); return; }
        if (!m.forceAttack(which)) return;
        m.mood().spendWind(cost);
        long[] t = book(p);
        t[which] = p.level().getGameTime();
        t[0] = p.level().getGameTime();
    }

    // ------------------------------------------------------------------ what the book takes out of him
    /** how much of his wind each attack is worth, by the attack's number */
    /**
     * What each attack takes out of him, 1 being everything he has. The small ones are cheap and the big ones are
     * now properly dear: the stare, the goo storm and the eye storm are half his wind or better between them, and
     * the two he can barely stand to do cost him most of a bar each.
     */
    private static final float[] ATTACK_WIND = {
            0f, 0.12f, 0.07f, 0.09f, 0.34f, 0.15f, 0.18f, 0.07f, 0.20f, 0.18f, 0.15f, 0.18f, 0.12f, 0.38f, 0.48f,
            0.55f, 0.70f};

    /** what one line of the book costs him, 1 being everything he has */
    public static float windCost(int action, int arg) {
        return switch (action) {
            case CodexPayload.ATTACK_MOVE -> arg > 0 && arg < ATTACK_WIND.length ? ATTACK_WIND[arg] : 0.10f;
            case CodexPayload.BURROW, CodexPayload.BURROW_XZ -> 0.35f;
            case CodexPayload.POP_EYE -> 0.15f;
            case CodexPayload.BREATHE -> 0.10f;
            case CodexPayload.RIDE -> 0.06f;
            case CodexPayload.COUGH -> 0.05f;
            case CodexPayload.CALM, CodexPayload.HUNTER, CodexPayload.GUARDIAN -> 0.04f;
            case CodexPayload.ATTACK_THAT -> 0.03f;
            case CodexPayload.COME, CodexPayload.GO_THERE, CodexPayload.GO_TO_XZ -> 0.02f;
            default -> 0f;
        };
    }

    /** the orders he takes badly: the ones that cost him a piece of himself rather than just his breath */
    private static float sourCost(int action, int arg, MountainEntity m) {
        return switch (action) {
            case CodexPayload.POP_EYE -> 0.10f * Math.max(1, Math.min(8, arg));
            case CodexPayload.BURROW, CodexPayload.BURROW_XZ -> 0.03f;
            // the two that cost him a piece of himself: he takes those personally
            case CodexPayload.ATTACK_MOVE -> arg == net.jj.mountain.rig.RigState.SPLIT ? 0.30f
                    : arg == net.jj.mountain.rig.RigState.DRAW ? 0.12f
                    : arg == net.jj.mountain.rig.RigState.EYE_STORM || arg == net.jj.mountain.rig.RigState.GAZE ? 0.06f
                    : m.brokenLegs() >= 3 ? 0.03f : 0f;
            default -> 0f;
        };
    }

    /** the orders he holds against you: the book can mark those before you give them */
    public static boolean takesItBadly(int action, int arg) {
        return action == CodexPayload.ATTACK_MOVE
                && (arg == net.jj.mountain.rig.RigState.SPLIT || arg == net.jj.mountain.rig.RigState.DRAW
                || arg == net.jj.mountain.rig.RigState.EYE_STORM || arg == net.jj.mountain.rig.RigState.GAZE);
    }

    /** an order he has not got round to yet */
    private record Later(java.util.UUID who, CodexPayload pay, long at) {}
    private static final java.util.List<Later> waiting = new java.util.ArrayList<>();

    private static void later(ServerPlayer p, CodexPayload pay, int ticks) {
        if (waiting.size() >= 64) return;
        waiting.add(new Later(p.getUUID(), pay, p.level().getGameTime() + ticks));
    }

    /** every clock and every waiting order belongs to the world that made it */
    public static void forgetEverything() {
        waiting.clear();
        lastUse.clear();
    }

    /** the orders he was slow about, and the two bars in the book */
    public static void serverTick(net.minecraft.server.MinecraftServer server) {
        if (!waiting.isEmpty()) {
            long now = server.overworld().getGameTime();
            java.util.List<Later> due = null;
            for (java.util.Iterator<Later> it = waiting.iterator(); it.hasNext(); ) {
                Later l = it.next();
                if (l.at() > now) continue;
                it.remove();
                (due == null ? due = new java.util.ArrayList<>() : due).add(l);
            }
            if (due != null) for (Later l : due) {
                ServerPlayer p = server.getPlayerList().getPlayer(l.who());
                if (p != null) handle(p, l.pay(), true);
            }
        }
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!holdingBook(p)) continue;
            sendMood(p, his(p));
        }
    }

    /** the two bars: what he has left in him, and what he holds against you */
    public static void sendMood(ServerPlayer p, @Nullable MountainEntity m) {
        float wind = m == null ? 1f : m.mood().wind();
        float sour = m == null ? 0f : m.mood().sourOf(p.getUUID());
        int stage = m == null ? 0 : m.mood().stage(p.getUUID());
        int days = m == null ? 0 : m.mood().huntDaysLeft(p.getUUID());
        int end = m == null ? 3 : m.unmakeState();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new MoodPayload(wind, sour, stage, days, end));
    }

    /**
     * Whether the order happens at all. His wind has to cover it, and how he feels about the person holding the
     * book decides whether he does it now, does it in his own time, or simply doesn't.
     */
    private static boolean gate(ServerPlayer p, MountainEntity m, CodexPayload pay) {
        var mood = m.mood();
        int action = pay.action();
        int stage = mood.stage(p.getUUID());
        if (stage == net.jj.mountain.entity.Mood.TURNED) { say(p, "codex_turned"); return false; }
        // anything with a clock of its own is checked first: he should not blow his wind on an order that
        // was never going to happen
        if (action == CodexPayload.ATTACK_MOVE) {
            int which = pay.arg();
            if (which <= 0 || which >= net.jj.mountain.entity.MountainAttacks.NAMES.length) return false;
            long left = coolLeft(p, which);
            if (left > 0) { say(p, "codex_cooling", (int) Math.ceil(left / 20.0)); return false; }
            // down for good he is still swinging at anything in reach, so the book can still point him
            if ((m.knockedDown() && !m.crippled()) || m.sleeping()) { say(p, "codex_cannot_attack"); return false; }
        }
        // nothing that asks him to go anywhere, while he has no legs left to go on
        if (movesHim(action) && m.crippled()) { say(p, "crippled_refused"); return false; }
        // and nowhere near his own heart while it is beating
        if (movesHim(action)) {
            double gx = action == CodexPayload.GO_TO_XZ || action == CodexPayload.BURROW_XZ ? pay.x() : p.getX();
            double gz = action == CodexPayload.GO_TO_XZ || action == CodexPayload.BURROW_XZ ? pay.z() : p.getZ();
            if (action != CodexPayload.STAY && m.warded(gx, gz)) { say(p, "ward_refused"); return false; }
        }
        if ((action == CodexPayload.BURROW || action == CodexPayload.BURROW_XZ) && m.forcedUnderLeft() > 0) {
            say(p, "codex_under_wait", (int) Math.ceil(m.forcedUnderLeft() / 24000.0));
            return false;
        }
        float cost = windCost(action, pay.arg());
        if (cost > 0f && !mood.hasWind(cost)) {
            mood.sour(p.getUUID(), 0.07f);
            say(p, "codex_winded");
            sendMood(p, m);
            return false;
        }
        float sting = sourCost(action, pay.arg(), m);
        if (sting > 0f) mood.sour(p.getUUID(), sting);
        if (stage >= net.jj.mountain.entity.Mood.BALKY && p.getRandom().nextInt(3) == 0) {
            mood.spendWind(cost * 0.25f);
            say(p, "codex_ignored");
            sendMood(p, m);
            return false;
        }
        mood.spendWind(cost);
        if (stage >= net.jj.mountain.entity.Mood.SLOW) {
            later(p, pay, 20 + p.getRandom().nextInt(21));
            say(p, "codex_slow");
            sendMood(p, m);
            return false;
        }
        sendMood(p, m);
        return true;
    }

    /** the lines that ask him to put one foot in front of the other */
    private static boolean movesHim(int action) {
        return action == CodexPayload.COME || action == CodexPayload.GO_THERE || action == CodexPayload.GO_TO_XZ
                || action == CodexPayload.BURROW || action == CodexPayload.BURROW_XZ || action == CodexPayload.RIDE;
    }

    public static void handle(ServerPlayer p, int action) { handle(p, new CodexPayload(action), false); }

    public static void handle(ServerPlayer p, CodexPayload pay) { handle(p, pay, false); }

    private static void handle(ServerPlayer p, CodexPayload pay, boolean already) {
        int action = pay.action();
        if (!(p.level() instanceof ServerLevel)) return;
        if (!holdingBook(p)) { say(p, "codex_lost"); return; }
        MountainEntity m = his(p);
        if (action == CodexPayload.WHERE) { where(p, m); return; }
        if (action == CodexPayload.SPARE_LOOK) { spareLooked(p); return; }
        if (action == CodexPayload.SPARE_ME) { spare(p, p); return; }
        if (action == CodexPayload.SAFE_GET) { sendSafeList(p); return; }

        if (m == null) {
            // he is not in the world at all: he is being kept as a sum. The book still reaches him for
            // anything to do with where he is going.
            if (farOrder(p, action, pay)) return;
            say(p, "codex_none");
            return;
        }
        if (!already && !gate(p, m, pay)) return;
        int mind = m.mood().stage(p.getUUID());
        if (m.sleeping() && action != CodexPayload.SLEEP) m.wakeUp(p);
        switch (action) {
            case CodexPayload.ATTACK_MOVE -> {
                int which = pay.arg();
                if (which <= 0 || which >= net.jj.mountain.entity.MountainAttacks.NAMES.length) return;
                long left = coolLeft(p, which);
                if (left > 0) { say(p, "codex_cooling", (int) Math.ceil(left / 20.0)); return; }
                if (!m.forceAttack(which)) { m.mood().refund(windCost(action, which)); say(p, "codex_cannot_attack"); return; }
                long[] t = book(p);
                t[which] = p.level().getGameTime();
                t[0] = p.level().getGameTime();
                say(p, "codex_attacking", Component.translatable("attack.mountain_breathes." + net.jj.mountain.entity.MountainAttacks.NAMES[which]));
            }
            case CodexPayload.GOO -> {
                if (!p.hasPermissions(2)) { say(p, "codex_not_yours"); return; }
                boolean on = pay.arg() != 0;
                if (net.jj.mountain.MountainConfig.V.gooTrail != on) {
                    net.jj.mountain.MountainConfig.V.gooTrail = on;
                    net.jj.mountain.MountainConfig.save();
                }
                say(p, on ? "codex_goo_on" : "codex_goo_off");
            }
            case CodexPayload.BREAK_BLOCKS -> {
                if (!p.hasPermissions(2)) { say(p, "codex_not_yours"); return; }
                boolean on = pay.arg() != 0;
                if (net.jj.mountain.MountainConfig.V.griefing != on) {
                    net.jj.mountain.MountainConfig.V.griefing = on;
                    net.jj.mountain.MountainConfig.save();
                }
                say(p, on ? "codex_break_on" : "codex_break_off");
            }
            case CodexPayload.GO_TO_XZ, CodexPayload.BURROW_XZ -> {
                double gx = pay.x(), gz = pay.z();
                int gy = m.groundAt((int) Math.floor(gx), (int) Math.floor(gz));
                Vec3 at = new Vec3(gx, gy, gz);
                if (action == CodexPayload.GO_TO_XZ) { m.setGoal(at); say(p, "codex_goto", (int) gx, (int) gz); }
                else if (!burrowNow(p, m, at)) return;
                else say(p, "codex_under", (int) gx, (int) gz);
            }
            case CodexPayload.BREATHE -> { m.forceBreath(); say(p, "codex_breathe"); }
            case CodexPayload.COUGH -> { m.coughUp(); say(p, "codex_cough"); }
            case CodexPayload.WHERE -> where(p, m);
            case CodexPayload.SPARE_LOOK -> spareLooked(p);
            case CodexPayload.SPARE_ME -> spare(p, p);
            case CodexPayload.FORGIVE -> { m.forgiveAll(); say(p, "codex_forgive"); }
            case CodexPayload.POP_EYE -> { m.popEyes(Mth.clamp(pay.arg(), 1, 8), p); say(p, "codex_popped"); }
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
                    if (mind >= net.jj.mountain.entity.Mood.WILFUL) { m.clearHitList(); say(p, "codex_own_target"); }
                    else { m.sendAfter(java.util.List.of(le)); say(p, "codex_kill", le.getDisplayName().getString()); }
                } else say(p, "codex_nothing_there");
            }
            case CodexPayload.STAY -> { m.setStay(!m.staying()); say(p, m.staying() ? "codex_stay" : "codex_free"); }
            case CodexPayload.CALM, CodexPayload.HUNTER, CodexPayload.GUARDIAN -> {
                int v = action == CodexPayload.CALM ? MountainEntity.CALM : action == CodexPayload.HUNTER ? MountainEntity.HUNTER : MountainEntity.GUARDIAN;
                m.setVariant(v);
                say(p, "codex_mode", Component.translatable("mode.mountain_breathes." + v));
            }
            case CodexPayload.RIDE -> {
                if (m.ridden()) { if (!m.setMeDown()) m.dropRider(); say(p, "codex_off"); }
                else if (m.comingForSomebody()) { m.stopFetch(); say(p, "codex_never_mind"); }
                else if (!m.comeAndGetMe(p)) say(p, "codex_cannot_ride");
            }
            case CodexPayload.SLEEP -> {
                if (m.sleeping()) { m.wakeUp(p); say(p, "codex_wake"); }
                else { m.goToSleep(); say(p, "codex_sleep"); }
            }
            case CodexPayload.BURROW -> {
                HitResult h = looking(p, 320);
                Vec3 at = h != null ? h.getLocation() : p.position();
                if (burrowNow(p, m, at)) say(p, "codex_under", (int) at.x, (int) at.z);
            }
            case CodexPayload.CALL_OFF -> { m.clearHitList(); m.setGoal(null); say(p, "codex_calloff"); }
            case CodexPayload.UNMAKE -> {
                if (pay.arg() != CodexPayload.UNMAKE_MEANT) return;
                int why = m.unmakeState();
                if (why == 1) { say(p, "codex_end_off"); return; }
                if (why == 2) { say(p, "codex_end_whole", (int) Math.ceil(100f * m.healthNow() / Math.max(1f, m.healthMax()))); return; }
                if (why != 0 || !m.unmakeIt(p)) { say(p, "codex_end_not_now"); return; }
                sendMood(p, m);
            }
            default -> {}
        }
    }

    /**
     * Orders for a Mountain who is out of the world. He is a line on a map out there — where he set off from,
     * where he is going and how fast — so anything about where he is going can still be changed. Anything that
     * needs him standing in front of you cannot.
     */
    private static boolean farOrder(ServerPlayer p, int action, CodexPayload pay) {
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        if (!w.isAway()) return false;
        ServerLevel sl = p.serverLevel();
        switch (action) {
            case CodexPayload.COME -> {
                w.sendAway(sl, p.position(), false);
                say(p, "codex_far_come", farOff(p, w, sl), w.tripLeftMinutes(sl));
                return true;
            }
            case CodexPayload.GO_THERE -> {
                HitResult h = looking(p, 320);
                if (h == null) { say(p, "codex_nowhere"); return true; }
                Vec3 at = h.getLocation();
                w.sendAway(sl, at, false);
                say(p, "codex_far_goto", (int) at.x, (int) at.z, w.tripLeftMinutes(sl));
                return true;
            }
            case CodexPayload.GO_TO_XZ -> {
                Vec3 at = new Vec3(pay.x(), 0, pay.z());
                w.sendAway(sl, at, false);
                say(p, "codex_far_goto", (int) at.x, (int) at.z, w.tripLeftMinutes(sl));
                return true;
            }
            case CodexPayload.BURROW, CodexPayload.BURROW_XZ -> {
                long left = w.awayUnderLeft(sl);
                if (left > 0) { say(p, "codex_under_wait", (int) Math.ceil(left / 24000.0)); return true; }
                Vec3 at;
                if (action == CodexPayload.BURROW_XZ) at = new Vec3(pay.x(), 0, pay.z());
                else {
                    HitResult h = looking(p, 320);
                    if (h == null) { say(p, "codex_nowhere"); return true; }
                    at = h.getLocation();
                }
                w.sendAway(sl, at, true);
                w.awayWentUnder(sl);
                say(p, "codex_far_under", (int) at.x, (int) at.z, w.tripLeftMinutes(sl));
                return true;
            }
            case CodexPayload.STAY, CodexPayload.CALL_OFF -> {
                w.haltAway(sl);
                say(p, "codex_far_stop", farOff(p, w, sl));
                return true;
            }
            default -> {
                say(p, "codex_far_no", farOff(p, w, sl));
                return true;
            }
        }
    }

    private static int farOff(ServerPlayer p, net.jj.mountain.world.MountainWorld w, ServerLevel sl) {
        var at = w.where(sl);
        if (at == null) return 0;
        double dx = at.getX() - p.getX(), dz = at.getZ() - p.getZ();
        return (int) Math.sqrt(dx * dx + dz * dz);
    }

    /** he will only be made to dig in every few days; he still does it himself whenever he likes */
    private static boolean burrowNow(ServerPlayer p, MountainEntity m, Vec3 at) {
        long left = m.forcedUnderLeft();
        if (left > 0) { say(p, "codex_under_wait", (int) Math.ceil(left / 24000.0)); return false; }
        if (!m.goUnderOnCommand(at)) {
            m.mood().refund(windCost(CodexPayload.BURROW, 0));
            say(p, "codex_cannot_burrow");
            return false;
        }
        m.sentUnderBy(p.getUUID());                 // so he can tell you where he came up
        return true;
    }

    /** whatever you are looking at goes on your list: a player by name, anything else by its kind */
    private static void spareLooked(ServerPlayer p) {
        HitResult h = looking(p, 120);
        if (!(h instanceof EntityHitResult eh)) { say(p, "codex_no_player"); return; }
        Entity who = eh.getEntity();
        if (who instanceof net.minecraft.world.entity.player.Player pl) { spare(p, pl); return; }
        if (!(who instanceof net.minecraft.world.entity.LivingEntity)) { say(p, "codex_no_player"); return; }
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        boolean on = w.toggleKind(p.getUUID(), who.getType());
        say(p, on ? "codex_spared_kind" : "codex_unspared_kind", who.getType().getDescription());
        sendSafeList(p);
    }

    private static void spare(ServerPlayer p, net.minecraft.world.entity.player.Player who) {
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        w.rememberName(who.getUUID(), who.getGameProfile().getName());
        boolean on = w.toggleFriend(p.getUUID(), who.getUUID());
        say(p, on ? "codex_spared" : "codex_unspared", who.getGameProfile().getName());
        sendSafeList(p);
    }

    /** taking one line off your own safe list, named rather than counted to */
    public static void dropFromSafeList(ServerPlayer p, String id) {
        if (!(p.level() instanceof ServerLevel)) return;
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        if (id.startsWith("who:")) {
            try {
                java.util.UUID who = java.util.UUID.fromString(id.substring(4));
                w.dropFriend(p.getUUID(), who);
                say(p, "codex_unspared", w.nameOf(who));
            } catch (IllegalArgumentException ignored) { }
        } else if (id.startsWith("kind:")) {
            String kind = id.substring(5);
            w.dropKind(p.getUUID(), kind);
            say(p, "codex_unspared_kind", net.jj.mountain.world.MountainWorld.kindName(kind));
        }
        sendSafeList(p);
    }

    /** the player's own safe list, for the book's page */
    public static void sendSafeList(ServerPlayer p) {
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        java.util.List<String> names = new java.util.ArrayList<>(), ids = new java.util.ArrayList<>();
        for (java.util.UUID u : w.listOf(p.getUUID())) { names.add(w.nameOf(u)); ids.add("who:" + u); }
        for (String id : w.kindsOf(p.getUUID())) { names.add(net.jj.mountain.world.MountainWorld.kindName(id)); ids.add("kind:" + id); }
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
                new net.jj.mountain.net.SafeListPayload(names, ids, w.listInForce(p.getUUID()), holdingBook(p)));
        sendMood(p, his(p));
    }

    /** where he is, or where the next one will get up and how long that takes */
    private static void where(ServerPlayer p, @Nullable MountainEntity m) {
        var w = net.jj.mountain.world.MountainWorld.get(p.server.overworld());
        net.minecraft.core.BlockPos at = m != null ? m.blockPosition() : w.where(p.server.overworld());
        if (at == null || (m == null && !w.aliveNow())) {
            int days = w.daysLeft(p.serverLevel());
            say(p, days > 0 ? "codex_wait" : "codex_nowhere_yet", days);
            return;
        }
        double dx = at.getX() - p.getX(), dz = at.getZ() - p.getZ();
        int dist = (int) Math.sqrt(dx * dx + dz * dz);
        int seg = (int) Math.round(Math.atan2(dx, dz) / (Math.PI / 4)) & 7;
        String[] way = {"south", "south-east", "east", "north-east", "north", "north-west", "west", "south-west"};
        say(p, "compass", dist, way[seg], at.getX(), at.getZ());
        // and what he is actually doing out there, so a number that doesn't change tells you why
        if (m != null) {
            say2(p, "compass_doing", Component.translatable("doing.mountain_breathes." + m.doingNow()));
        } else if (w.tripping() && !w.tripDone(p.server.overworld())) {
            var end = w.tripEnd();
            say2(p, "compass_walking", (int) end.x, (int) end.z, w.tripLeftMinutes(p.server.overworld()));
        } else {
            say2(p, "compass_last", Component.translatable("doing.mountain_breathes.standing"));
        }
    }

    /** what the book shows about him: near, how far, mood, health, and what he is up to */
    public static Entity nearestFor(ServerPlayer p) { return his(p); }
}
