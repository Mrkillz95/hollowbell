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

    private static final String[] MOODS = {"calm", "hunting", "guardian"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hollowbell").requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon").executes(c -> summon(c, HollowbellEntity.HUNTER, 1f))
                        .then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                                .executes(c -> summon(c, mood(c), 1f))
                                .then(Commands.argument("size", FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                                        .executes(c -> summon(c, mood(c), FloatArgumentType.getFloat(c, "size"))))))
                .then(Commands.literal("do").then(Commands.argument("move", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.copyOfRange(Moves.NAMES, 1, Moves.NAMES.length), b))
                        .executes(HollowbellCommand::doMove)))
                .then(Commands.literal("list").executes(HollowbellCommand::list))
                .then(Commands.literal("mood").then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                        .executes(c -> near(c, h -> { h.setVariant(mood(c)); if (h.isGuardian()) h.setHome(h.position()); }, "mood"))))
                .then(Commands.literal("size").then(Commands.argument("size", FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                        .executes(c -> near(c, h -> h.setBellScale(FloatArgumentType.getFloat(c, "size")), "size"))))
                .then(Commands.literal("hurt").then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                        .executes(c -> near(c, h -> h.hurtBy(FloatArgumentType.getFloat(c, "amount")), "hurt"))))
                .then(Commands.literal("sethealth").then(Commands.argument("n", FloatArgumentType.floatArg(1f))
                        .executes(c -> near(c, h -> h.setHealthTo(FloatArgumentType.getFloat(c, "n")), "sethealth"))))
                .then(Commands.literal("heal").executes(c -> near(c, h -> { h.heal(); h.mendThreads(); }, "heal")))
                .then(Commands.literal("popped").then(Commands.argument("n", IntegerArgumentType.integer(0, 64))
                        .executes(c -> near(c, h -> h.popPods(IntegerArgumentType.getInteger(c, "n")), "popped"))))
                .then(Commands.literal("cut").then(Commands.argument("n", IntegerArgumentType.integer(0, 128))
                        .executes(c -> near(c, h -> h.cutThreads(IntegerArgumentType.getInteger(c, "n")), "cut"))))
                .then(Commands.literal("mend").executes(c -> near(c, HollowbellEntity::mendThreads, "mend")))
                .then(Commands.literal("ride").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    HollowbellEntity h = nearest(c.getSource());
                    if (h == null) return none(c);
                    if (h.carrying()) h.dropRider(); else h.possess(p);
                    return 1;
                }))
                .then(Commands.literal("kill").executes(c -> all(c, h -> h.hurt(h.damageSources().genericKill(), Float.MAX_VALUE), "kill")))
                .then(Commands.literal("remove").executes(c -> all(c, h -> h.discard(), "remove")))
                .then(Commands.literal("health").then(Commands.argument("n", FloatArgumentType.floatArg(10f))
                        .executes(c -> set(c, () -> HollowbellConfig.V.health = FloatArgumentType.getFloat(c, "n"), "health", FloatArgumentType.getFloat(c, "n")))))
                .then(Commands.literal("damage").then(Commands.argument("x", FloatArgumentType.floatArg(0f, 100f))
                        .executes(c -> set(c, () -> HollowbellConfig.V.damageMultiplier = FloatArgumentType.getFloat(c, "x"), "damage", FloatArgumentType.getFloat(c, "x")))))
                .then(Commands.literal("griefing").then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> set(c, () -> HollowbellConfig.V.griefing = BoolArgumentType.getBool(c, "on"), "griefing", BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("shake").then(Commands.argument("on", BoolArgumentType.bool())
                        .executes(c -> set(c, () -> HollowbellConfig.V.screenShake = BoolArgumentType.getBool(c, "on"), "shake", BoolArgumentType.getBool(c, "on")))))
                .then(Commands.literal("reload").executes(c -> { HollowbellConfig.load(); c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.reloaded"), true); return 1; })));
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
        if (all.isEmpty()) return none(c);
        for (HollowbellEntity h : all) {
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.list_line", (int) h.getX(), (int) h.getY(), (int) h.getZ(),
                    String.format("%.2f", h.bellScale()), Component.translatable("mode.hollowbell." + h.variant()),
                    (int) h.healthNow(), (int) h.healthMax(), h.podsLeft(), h.rig.pods.length, h.threadsHolding(), h.rig.threads.length), false);
        }
        return all.size();
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

    /** the world only holds so many: the oldest go */
    public static void keepToTheLimit(HollowbellEntity h, ServerLevel l) {
        int max = HollowbellConfig.V.maxHollowbells;
        if (max <= 0 || h.tickCount > 0) return;
        List<HollowbellEntity> all = new ArrayList<>(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved() && e != h));
        all.sort(Comparator.comparingLong(HollowbellEntity::bornAt));
        while (all.size() >= max) all.remove(0).discard();
    }
}
