package net.jj.hollowbell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.jj.hollowbell.HollowbellConfig;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** /hollowbell ... (cheats on), the same pattern as Furrowmaw's. */
public final class HollowbellCommand {
    private HollowbellCommand() {}

    /** everything but detail needs cheats on */
    private static final java.util.function.Predicate<CommandSourceStack> OP = s -> s.hasPermission(2);

    private static int detail(CommandContext<CommandSourceStack> c, int what) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.jj.hollowbell.net.DetailPayload(what));
        return 1;
    }

    private static final String[] MOODS = {"calm", "hunting", "guardian"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hollowbell")
                // detail: anyone, cheats or not (it only changes how their own game draws him)
                .then(Commands.literal("detail").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.ASK))
                        .then(Commands.literal("on").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.ON)))
                        .then(Commands.literal("off").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.OFF))))
                .then(Commands.literal("summon").requires(OP).executes(c -> summon(c, HollowbellEntity.HUNTER, 1f))
                        .then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                                .executes(c -> summon(c, mood(c), 1f))
                                .then(Commands.argument("size", FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                                        .executes(c -> summon(c, mood(c), FloatArgumentType.getFloat(c, "size"))))))
                .then(Commands.literal("do").requires(OP).then(Commands.argument("move", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.copyOfRange(Moves.NAMES, 1, Moves.NAMES.length), b))
                        .executes(HollowbellCommand::doMove)))
                .then(Commands.literal("list").requires(OP).executes(HollowbellCommand::list))
                .then(Commands.literal("where").executes(HollowbellCommand::where))
                .then(Commands.literal("away").requires(OP).executes(c -> awaySay(c))
                        .then(Commands.literal("on").executes(c -> set(c, () -> HollowbellConfig.V.offscreenTravel = true, "offscreenTravel", true)))
                        .then(Commands.literal("off").executes(c -> set(c, () -> HollowbellConfig.V.offscreenTravel = false, "offscreenTravel", false)))
                        .then(Commands.literal("blocks").then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
                                .executes(c -> set(c, () -> HollowbellConfig.V.awayBlocks = IntegerArgumentType.getInteger(c, "n"), "awayBlocks", IntegerArgumentType.getInteger(c, "n")))))
                        .then(Commands.literal("now").executes(HollowbellCommand::awayNow)))
                .then(Commands.literal("bossbar").requires(OP).then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                        .executes(c -> set(c, () -> HollowbellConfig.V.bossBarRange = IntegerArgumentType.getInteger(c, "blocks"), "bossBarRange", IntegerArgumentType.getInteger(c, "blocks")))))
                .then(Commands.literal("mood").requires(OP).then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                        .executes(c -> near(c, h -> { h.setVariant(mood(c)); if (h.isGuardian()) h.setHome(h.position()); }, "mood"))))
                .then(Commands.literal("size").requires(OP).then(Commands.argument("size", FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                        .executes(c -> near(c, h -> h.setBellScale(FloatArgumentType.getFloat(c, "size")), "size"))))
                .then(Commands.literal("hurt").requires(OP).then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                        .executes(c -> near(c, h -> h.hurtBy(FloatArgumentType.getFloat(c, "amount")), "hurt"))))
                .then(Commands.literal("sethealth").requires(OP).then(Commands.argument("n", FloatArgumentType.floatArg(1f))
                        .executes(c -> near(c, h -> h.setHealthTo(FloatArgumentType.getFloat(c, "n")), "sethealth"))))
                .then(Commands.literal("heal").requires(OP).executes(c -> near(c, h -> { h.heal(); h.mendPods(); }, "heal")))
                .then(Commands.literal("popped").requires(OP).then(Commands.argument("n", IntegerArgumentType.integer(0, 64))
                        .executes(c -> near(c, h -> h.popPods(IntegerArgumentType.getInteger(c, "n")), "popped"))))
                .then(Commands.literal("goto").requires(OP).then(Commands.argument("x", FloatArgumentType.floatArg()).then(Commands.argument("z", FloatArgumentType.floatArg())
                        .executes(c -> near(c, h -> {
                            double x = FloatArgumentType.getFloat(c, "x"), z = FloatArgumentType.getFloat(c, "z");
                            h.setGoal(new Vec3(x, h.groundAt(x, z), z));
                        }, "goto")))))
                .then(Commands.literal("stay").requires(OP).then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> near(c, h -> h.setStay(BoolArgumentType.getBool(c, "on")), "stay"))))
                .then(Commands.literal("height").requires(OP).then(Commands.argument("blocks", FloatArgumentType.floatArg(0f, 400f))
                        .executes(c -> near(c, h -> h.setCruise(FloatArgumentType.getFloat(c, "blocks")), "height"))))
                .then(Commands.literal("ride").requires(OP).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    HollowbellEntity h = nearest(c.getSource());
                    if (h == null) return none(c);
                    if (h.carrying()) h.dropRider(); else h.possess(p);
                    return 1;
                }))
                .then(Commands.literal("carry").requires(OP).executes(c -> {
                    // he comes to you and a strand carries you up onto his crown, like "ride him" in the book
                    // (with /execute as, anything alive can be fetched: handy for watching it from the side)
                    if (!(c.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity p)) { c.getSource().getPlayerOrException(); return 0; }
                    HollowbellEntity h = nearest(c.getSource());
                    if (h == null) return none(c);
                    // already up there: he carries you back down the same way
                    if (h.rider() == p) {
                        h.setMeDown(p);
                        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_set_down"), false);
                        return 1;
                    }
                    if (h.carrying() || !h.comeAndGetMe(p)) { c.getSource().sendFailure(Component.translatable("command.hollowbell.cannot_carry")); return 0; }
                    c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_carry"), false);
                    return 1;
                }))
                .then(Commands.literal("natural").requires(OP).executes(HollowbellCommand::naturalSay)
                        .then(Commands.literal("on").executes(c -> setNatural(c, true)))
                        .then(Commands.literal("off").executes(c -> setNatural(c, false))))
                .then(Commands.literal("limit").requires(OP).executes(HollowbellCommand::limitSay)
                        .then(Commands.argument("how many", IntegerArgumentType.integer(0, 20)).executes(HollowbellCommand::setLimit)))
                .then(Commands.literal("ward").requires(OP).executes(HollowbellCommand::wardSay)
                        .then(Commands.literal("on").executes(HollowbellCommand::wardOn))
                        .then(Commands.literal("off").executes(c -> {
                            WorldOne.get(c.getSource().getServer()).forgetWard();
                            c.getSource().sendSuccess(() -> Component.literal("The crown is quiet. He can come back."), true);
                            return 1;
                        }))
                        .then(Commands.literal("minutes").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            HollowbellConfig.V.wardSeconds = v * 60;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("His crown holds him off for " + v + " minutes once it is woken."), true);
                            return 1;
                        })))
                        .then(Commands.literal("rest").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            HollowbellConfig.V.wardRestSeconds = v * 60;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v == 0
                                    ? "The crown is ready again the moment it stops."
                                    : "The crown sits dark for " + v + " minutes afterwards."), true);
                            return 1;
                        })))
                        .then(Commands.literal("blocks").then(Commands.argument("blocks", IntegerArgumentType.integer(0, 20000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            HollowbellConfig.V.wardBlocks = v;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v == 0
                                    ? "His crown holds him off nowhere now." : "His crown holds him off " + v + " blocks."), true);
                            return 1;
                        }))))
                .then(Commands.literal("area").requires(OP).executes(HollowbellCommand::areaSay)
                        .then(Commands.literal("off").executes(HollowbellCommand::areaFree))
                        .then(Commands.argument("x", FloatArgumentType.floatArg()).then(Commands.argument("z", FloatArgumentType.floatArg())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(32, 100000)).executes(HollowbellCommand::areaBind)))))
                .then(Commands.literal("giants").requires(OP)
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.translatable(HollowbellConfig.V.fightGiants ? "command.hollowbell.giants_is_on" : "command.hollowbell.giants_is_off"), false); return 1; })
                        .then(Commands.literal("on").executes(c -> set(c, () -> HollowbellConfig.V.fightGiants = true, "fightGiants", true)))
                        .then(Commands.literal("off").executes(c -> set(c, () -> HollowbellConfig.V.fightGiants = false, "fightGiants", false))))
                .then(Commands.literal("volume").requires(OP).then(Commands.argument("x", FloatArgumentType.floatArg(0f, 2f))
                        .executes(c -> set(c, () -> HollowbellConfig.V.soundVolume = FloatArgumentType.getFloat(c, "x"), "soundVolume", FloatArgumentType.getFloat(c, "x")))))
                .then(Commands.literal("kill").requires(OP).executes(c -> all(c, h -> h.hurt(h.damageSources().genericKill(), Float.MAX_VALUE), "kill")))
                .then(Commands.literal("remove").requires(OP).executes(c -> {
                    int out = Away.get(c.getSource().getServer()).count();
                    Away.get(c.getSource().getServer()).forgetAll();
                    if (allOf(c.getSource()).isEmpty() && out > 0) { c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_remove"), true); return out; }
                    return all(c, h -> h.discard(), "remove");
                }))
                .then(Commands.literal("health").requires(OP).then(Commands.argument("n", FloatArgumentType.floatArg(10f))
                        .executes(c -> set(c, () -> HollowbellConfig.V.health = FloatArgumentType.getFloat(c, "n"), "health", FloatArgumentType.getFloat(c, "n")))))
                .then(Commands.literal("damage").requires(OP).then(Commands.argument("x", FloatArgumentType.floatArg(0f, 100f))
                        .executes(c -> set(c, () -> HollowbellConfig.V.damageMultiplier = FloatArgumentType.getFloat(c, "x"), "damage", FloatArgumentType.getFloat(c, "x")))))
                .then(Commands.literal("griefing").requires(OP).then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> set(c, () -> HollowbellConfig.V.griefing = BoolArgumentType.getBool(c, "on"), "griefing", BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("shake").requires(OP).then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> set(c, () -> HollowbellConfig.V.screenShake = BoolArgumentType.getBool(c, "on"), "shake", BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("reload").requires(OP).executes(c -> { HollowbellConfig.load(); c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.reloaded"), true); return 1; })));
    }

    private static int mood(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String m = StringArgumentType.getString(c, "mood");
        for (int i = 0; i < MOODS.length; i++) if (MOODS[i].equals(m)) return i;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.translatable("command.hollowbell.bad_mood")).create();
    }

    private static int summon(CommandContext<CommandSourceStack> c, int mood, float size) {
        CommandSourceStack src = c.getSource();
        ServerLevel l = src.getLevel();
        HollowbellEntity h = ModEntities.HOLLOWBELL.create(l);
        if (h == null) return 0;
        Vec3 at = src.getPosition();
        if (src.getEntity() != null) {
            // in front of you, far enough that you are not under him
            Vec3 look = src.getEntity().getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() < 1e-4) look = new Vec3(0, 0, 1);
            at = at.add(look.normalize().scale(100 * size + 6));
        }
        h.moveTo(at.x, l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z)), at.z,
                l.getRandom().nextFloat() * 360f, 0f);
        h.setBellScale(size);
        h.setVariant(mood);
        h.setHome(h.position());
        l.addFreshEntity(h);
        src.sendSuccess(() -> Component.translatable("command.hollowbell.summoned", Component.translatable("mode.hollowbell." + mood), String.format("%.2f", size)), true);
        return 1;
    }

    private static int doMove(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "move");
        int which = Moves.byName(name);
        if (which < 0) { c.getSource().sendFailure(Component.translatable("command.hollowbell.bad_move", name)); return 0; }
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        LivingEntity at = c.getSource().getEntity() instanceof LivingEntity le ? le : h.getTarget();
        if (!h.forceMove(which, at)) { c.getSource().sendFailure(Component.translatable("command.hollowbell.cannot", name)); return 0; }
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.doing", name), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<HollowbellEntity> all = allOf(c.getSource());
        List<Away.Rec> out = Away.get(c.getSource().getServer()).all();
        if (all.isEmpty() && out.isEmpty()) return none(c);
        for (HollowbellEntity h : all) {
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.list_line", (int) h.getX(), (int) h.getY(), (int) h.getZ(),
                    String.format("%.2f", h.bellScale()), Component.translatable("mode.hollowbell." + h.variant()),
                    (int) h.healthNow(), (int) h.healthMax(), h.podsLeft(), h.rig.pods.length), false);
        }
        long now = c.getSource().getLevel().getGameTime();
        for (Away.Rec r : out) awayLine(c, r, now);
        return all.size() + out.size();
    }

    private static void awayLine(CommandContext<CommandSourceStack> c, Away.Rec r, long now) {
        Vec3 s = r.spot(now);
        Component line = r.going
                ? Component.translatable("command.hollowbell.away_going", (int) s.x, (int) s.z, r.dim, (int) r.toX, (int) r.toZ, Math.max(1, r.minutesLeft(now)),
                        String.format("%.2f", r.scale), (int) r.hp, (int) r.hpMax)
                : Component.translatable("command.hollowbell.away_still", (int) s.x, (int) s.z, r.dim, String.format("%.2f", r.scale), (int) r.hp, (int) r.hpMax);
        c.getSource().sendSuccess(() -> line, false);
    }

    /** where every one of them is: in the world, or out of it and where the sum says */
    private static int where(CommandContext<CommandSourceStack> c) {
        List<HollowbellEntity> all = allOf(c.getSource());
        List<Away.Rec> out = Away.get(c.getSource().getServer()).all();
        if (all.isEmpty() && out.isEmpty()) return none(c);
        for (HollowbellEntity h : all)
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.where_in", (int) h.getX(), (int) h.getY(), (int) h.getZ(),
                    h.level().dimension().location().toString(), (int) h.healthNow(), (int) h.healthMax()), false);
        long now = c.getSource().getLevel().getGameTime();
        for (Away.Rec r : out) awayLine(c, r, now);
        return all.size() + out.size();
    }

    private static int awaySay(CommandContext<CommandSourceStack> c) {
        int b = HollowbellConfig.V.awayBlocks;
        c.getSource().sendSuccess(() -> Component.translatable(HollowbellConfig.V.offscreenTravel ? "command.hollowbell.away_is_on" : "command.hollowbell.away_is_off",
                b > 0 ? String.valueOf(b) : (int) Away.awayRange(c.getSource().getServer(), 1f) + " (auto)", Away.get(c.getSource().getServer()).count()), false);
        return 1;
    }

    /** every one with nobody near steps out right now */
    private static int awayNow(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (HollowbellEntity h : allOf(c.getSource())) {
            boolean near = false;
            double r = Away.awayRange(c.getSource().getServer(), h.bellScale());
            for (ServerPlayer p : ((ServerLevel) h.level()).players()) if (p.distanceToSqr(h.getX(), p.getY(), h.getZ()) < r * r) { near = true; break; }
            if (!near && h.stepAside()) n++;
        }
        final int f = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.away_now", f), true);
        return n;
    }

    private interface Act { void on(HollowbellEntity h) throws CommandSyntaxException; }

    private static int near(CommandContext<CommandSourceStack> c, Act a, String what) throws CommandSyntaxException {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        a.on(h);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_" + what), false);
        return 1;
    }

    private static int all(CommandContext<CommandSourceStack> c, Act a, String what) throws CommandSyntaxException {
        List<HollowbellEntity> l = allOf(c.getSource());
        if (l.isEmpty()) return none(c);
        for (HollowbellEntity h : l) a.on(h);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_" + what), true);
        return l.size();
    }

    private static int set(CommandContext<CommandSourceStack> c, Runnable r, String what, Object v) {
        r.run();
        HollowbellConfig.save();
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.set", what, String.valueOf(v)), true);
        return 1;
    }

    private static int none(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("command.hollowbell.none"));
        return 0;
    }

    public static List<HollowbellEntity> allOf(CommandSourceStack src) {
        List<HollowbellEntity> out = new ArrayList<>();
        for (ServerLevel l : src.getServer().getAllLevels()) out.addAll(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved()));
        return out;
    }

    public static @Nullable HollowbellEntity nearest(CommandSourceStack src) {
        Vec3 p = src.getPosition();
        return src.getLevel().getEntities(ModEntities.HOLLOWBELL, e -> !e.isDeadOrDying()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(p))).orElse(null);
    }

    // ------------------------------------------------------------------ the new world commands

    /** /hollowbell natural: does the world keep one of him out there, and where things stand */
    private static int naturalSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        String msg;
        if (!HollowbellConfig.V.oneInTheWorld) msg = "He doesn't rise on his own. /hollowbell natural on and the world keeps one.";
        else if (w.aliveNow() && w.where() != null)
            msg = "The world keeps one of him. He's out there near " + w.where().getX() + ", " + w.where().getZ() + ".";
        else if (w.where() != null) {
            int days = w.daysLeft(over);
            msg = days > 0 ? "The world keeps one of him. The next comes down in the Bell Hollows near " + w.where().getX() + ", " + w.where().getZ()
                    + " in about " + days + (days == 1 ? " day." : " days.")
                    : "The world keeps one of him. The next comes down in the Bell Hollows near " + w.where().getX() + ", " + w.where().getZ() + " any moment now.";
        } else msg = "The world keeps one of him. It's still picking his spot.";
        final String out = msg;
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int setNatural(CommandContext<CommandSourceStack> c, boolean on) {
        HollowbellConfig.V.oneInTheWorld = on;
        HollowbellConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "The world keeps one of him from here on. He'll rise on pale ground of his own, the Bell Hollows, somewhere far off."
                : "He only comes when summoned now. One already out there stays."), true);
        return 1;
    }

    /** everyone standing, in the world and out of it */
    private static int countAll(CommandSourceStack src) {
        return allOf(src).size() + Away.get(src.getServer()).count();
    }

    private static int limitSay(CommandContext<CommandSourceStack> c) {
        int max = HollowbellConfig.V.maxHollowbells;
        int now = countAll(c.getSource());
        c.getSource().sendSuccess(() -> Component.literal(max <= 0
                ? "There is no limit on how many of him there can be. " + now + " standing."
                : "The world holds " + max + " of him at once. " + now + " standing. Summon another past that and the oldest makes way."), false);
        return 1;
    }

    private static int setLimit(CommandContext<CommandSourceStack> c) {
        int v = IntegerArgumentType.getInteger(c, "how many");
        HollowbellConfig.V.maxHollowbells = v;
        HollowbellConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(v == 0
                ? "As many of him as you like now. Nothing is turned away and nothing is pushed out."
                : "The world holds " + v + " of him at once now. Summon another past that and the oldest makes way."), true);
        if (v > 0) WorldOne.limitNow(null, c.getSource().getServer().overworld());
        return 1;
    }

    private static int wardSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        var at = w.wardSpot();
        String msg;
        if (w.warding(over) && at != null)
            msg = "His crown is awake at " + at.getX() + ", " + at.getZ() + ". He is held off "
                    + (int) WorldOne.wardRange() + " blocks for another " + (w.wardLeft(over) / 20) + " seconds.";
        else if (w.wardRestLeft(over) > 0)
            msg = "The crown is dark. It gathers itself for another " + (w.wardRestLeft(over) / 20) + " seconds.";
        else msg = "No crown is awake. Set one down, stand by it and /hollowbell ward on: he is held off "
                    + (int) WorldOne.wardRange() + " blocks for " + Math.max(1, HollowbellConfig.V.wardSeconds / 60)
                    + " minutes, then it sits dark for " + Math.max(0, HollowbellConfig.V.wardRestSeconds / 60) + ".";
        final String out = msg;
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static int wardOn(CommandContext<CommandSourceStack> c) {
        ServerLevel lvl = c.getSource().getLevel();
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
        net.minecraft.core.BlockPos crown = null;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16)))
            if (lvl.getBlockState(p).is(net.jj.hollowbell.ModBlocks.CROWN)) { crown = p.immutable(); break; }
        if (crown == null) { c.getSource().sendFailure(Component.literal("No crown set down within 16 blocks of you.")); return 0; }
        WorldOne w = WorldOne.get(c.getSource().getServer());
        ServerLevel over = c.getSource().getServer().overworld();
        if (w.warding(over)) {
            var at = w.wardSpot();
            c.getSource().sendFailure(Component.literal("A crown is already awake at " + at.getX() + ", " + at.getZ() + ", for another "
                    + (w.wardLeft(over) / 20) + " seconds. /hollowbell ward off stops it."));
            return 0;
        }
        if (w.wardRestLeft(over) > 0) {
            c.getSource().sendFailure(Component.literal("The crown is dark. It gathers itself for another " + (w.wardRestLeft(over) / 20)
                    + " seconds. /hollowbell ward off lets it off."));
            return 0;
        }
        w.startWard(lvl, crown, Math.max(20, HollowbellConfig.V.wardSeconds * 20), Math.max(0, HollowbellConfig.V.wardRestSeconds * 20));
        for (HollowbellEntity e : lvl.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved()))
            if (e.warded(e.getX(), e.getZ())) e.pushedBackByWard();
        final int bx = crown.getX(), bz = crown.getZ();
        c.getSource().sendSuccess(() -> Component.literal("The crown wakes at " + bx + ", " + bz
                + ". He is held off " + (int) WorldOne.wardRange() + " blocks."), true);
        return 1;
    }

    private static int areaSay(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        c.getSource().sendSuccess(() -> Component.literal(h.bound()
                ? "He keeps within " + h.boundRadius() + " blocks of " + (int) h.boundCentre().x + ", " + (int) h.boundCentre().z + "."
                : "He roams free."), false);
        return 1;
    }

    private static int areaBind(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        double x = FloatArgumentType.getFloat(c, "x"), z = FloatArgumentType.getFloat(c, "z");
        int r = IntegerArgumentType.getInteger(c, "radius");
        h.bindTo(x, z, r);
        c.getSource().sendSuccess(() -> Component.literal("He keeps within " + r + " blocks of " + (int) x + ", " + (int) z + " now."), true);
        return 1;
    }

    private static int areaFree(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        h.unbind();
        c.getSource().sendSuccess(() -> Component.literal("He roams free again."), true);
        return 1;
    }
}
