package net.jj.hollowbell.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.BellConfig;
import net.jj.hollowbell.ModBlocks;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.block.CrownBlock;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.item.FinderItem;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.DetailPayload;
import net.jj.hollowbell.net.LookTrace;
import net.jj.hollowbell.world.Away;
import net.jj.hollowbell.world.BellWorld;
import net.jj.hollowbell.world.FarOrders;
import net.jj.hollowbell.world.GroundCheck;
import net.jj.hollowbell.world.NoWait;
import net.jj.hollowbell.world.Painter;
import net.jj.hollowbell.world.TakeMe;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** /hollowbell ... (cheats on) */
public final class BellCommand {
    private BellCommand() {}

    /** everything but detail needs cheats on */
    private static final Predicate<CommandSourceStack> OP = s -> s.hasPermission(2);

    private static int detail(CommandContext<CommandSourceStack> c, int what) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerPlayNetworking.send(p, new DetailPayload(what));
        return 1;
    }

    /** /hollowbell detail far: how far off he can be seen coming */
    private static int farSay(CommandContext<CommandSourceStack> c) {
        int b = BellConfig.V.farSightBlocks;
        c.getSource()
                .sendSuccess(
                        () -> b > 0
                                ? Component.translatable("command.hollowbell.far_is", b)
                                : Component.translatable("command.hollowbell.far_off"),
                        false);
        return 1;
    }

    /** /hollowbell detail far <blocks>: set here, and passed on to the player's own game too */
    private static int farSet(CommandContext<CommandSourceStack> c, int blocks) {
        BellConfig.V.farSightBlocks = blocks;
        BellConfig.save();
        if (c.getSource().getPlayer() instanceof ServerPlayer p)
            ServerPlayNetworking.send(p, new DetailPayload(DetailPayload.FAR, blocks));
        c.getSource()
                .sendSuccess(
                        () -> blocks > 0
                                ? Component.translatable("command.hollowbell.far_set", blocks)
                                : Component.translatable("command.hollowbell.far_off"),
                        true);
        return 1;
    }

    private static final String[] MOODS = {"calm", "hunting", "guardian"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(OpsCommand.addTo(Commands.literal("hollowbell")).then(windCommand()));
        d.register(Commands.literal("hollowbell")
                .then(detailCommand())
                .then(summonCommand())
                .then(doCommand())
                .then(listCommand())
                .then(whereCommand())
                .then(awayCommand())
                .then(bossbarCommand())
                .then(moodCommand())
                .then(sizeCommand())
                .then(hurtCommand())
                .then(sethealthCommand())
                .then(healCommand())
                .then(poppedCommand())
                .then(gotoCommand())
                .then(comeCommand())
                .then(configCommand())
                .then(setCommand())
                .then(podsCommand())
                .then(paintCommand())
                .then(tpCommand())
                .then(groundCommand())
                .then(stayCommand())
                .then(sleepCommand())
                .then(spareCommand())
                .then(heightCommand())
                .then(rideCommand())
                .then(carryCommand())
                .then(naturalCommand())
                .then(limitCommand())
                .then(wardCommand())
                .then(areaCommand())
                .then(giantsCommand())
                .then(meetingsCommand())
                .then(volumeCommand())
                .then(killCommand())
                .then(removeCommand())
                .then(healthCommand())
                .then(damageCommand())
                .then(toggle("griefing", "griefing", () -> BellConfig.V.griefing, v -> BellConfig.V.griefing = v))
                .then(heavydelayCommand())
                .then(toggle("shake", "screenShake", () -> BellConfig.V.screenShake, v -> BellConfig.V.screenShake = v))
                .then(reloadCommand()));
    }

    /** no cheats needed: it only changes how their own game draws him */
    private static LiteralArgumentBuilder<CommandSourceStack> detailCommand() {
        return Commands.literal("detail")
                .executes(c -> detail(c, DetailPayload.ASK))
                .then(Commands.literal("on").executes(c -> detail(c, DetailPayload.ON)))
                .then(Commands.literal("off").executes(c -> detail(c, DetailPayload.OFF)))
                .then(Commands.literal("far")
                        .executes(BellCommand::farSay)
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 4096))
                                .requires(OP)
                                .executes(c -> farSet(c, IntegerArgumentType.getInteger(c, "blocks")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> summonCommand() {
        return Commands.literal("summon")
                .requires(OP)
                .executes(c -> summon(c, HollowbellEntity.HUNTER, eggSize()))
                .then(Commands.argument("mood", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                        .executes(BellCommand::summonMoodOrSize)
                        .then(Commands.argument(
                                        "size",
                                        FloatArgumentType.floatArg(
                                                HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                                .executes(c -> summon(c, mood(c), FloatArgumentType.getFloat(c, "size")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> doCommand() {
        return Commands.literal("do")
                .requires(OP)
                .then(Commands.argument("move", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                Arrays.copyOfRange(Moves.NAMES, 1, Moves.NAMES.length), b))
                        .executes(c -> doMove(c, null, null))
                        .then(Commands.literal("at")
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(c -> doMove(c, null, Vec3Argument.getVec3(c, "pos")))))
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> doMove(c, EntityArgument.getEntity(c, "target"), null))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> listCommand() {
        return Commands.literal("list").requires(OP).executes(BellCommand::list);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> whereCommand() {
        return Commands.literal("where").executes(BellCommand::where);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> awayCommand() {
        return Commands.literal("away")
                .requires(OP)
                .executes(c -> awaySay(c))
                .then(Commands.literal("on")
                        .executes(c -> set(c, () -> BellConfig.V.offscreenTravel = true, "offscreenTravel", true)))
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.offscreenTravel = false, "offscreenTravel", false)))
                .then(Commands.literal("blocks")
                        .then(Commands.argument("n", IntegerArgumentType.integer(0, 100000))
                                .executes(c -> set(
                                        c,
                                        () -> BellConfig.V.awayBlocks = IntegerArgumentType.getInteger(c, "n"),
                                        "awayBlocks",
                                        IntegerArgumentType.getInteger(c, "n")))))
                .then(Commands.literal("now").executes(BellCommand::awayNow));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> bossbarCommand() {
        return Commands.literal("bossbar")
                .requires(OP)
                .executes(BellCommand::bossbarSay)
                .then(Commands.literal("on").executes(c -> set(c, () -> BellConfig.V.bossBar = true, "bossBar", true)))
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.bossBar = false, "bossBar", false)))
                .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                        .executes(c -> set(
                                c,
                                () -> BellConfig.V.bossBarRange = IntegerArgumentType.getInteger(c, "blocks"),
                                "bossBarRange",
                                IntegerArgumentType.getInteger(c, "blocks"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> moodCommand() {
        return Commands.literal("mood")
                .requires(OP)
                .then(Commands.argument("mood", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                        .executes(c -> near(
                                c,
                                h -> {
                                    h.setVariant(mood(c));
                                    if (h.isGuardian()) h.setHome(h.position());
                                },
                                "mood")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sizeCommand() {
        return Commands.literal("size")
                .requires(OP)
                .then(Commands.argument(
                                "size",
                                FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                        .executes(c -> near(c, h -> h.setBellScale(FloatArgumentType.getFloat(c, "size")), "size")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> hurtCommand() {
        return Commands.literal("hurt")
                .requires(OP)
                .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                        .executes(c -> near(c, h -> h.hurtBy(FloatArgumentType.getFloat(c, "amount")), "hurt")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> sethealthCommand() {
        return Commands.literal("sethealth")
                .requires(OP)
                .then(Commands.argument("health", FloatArgumentType.floatArg(1f))
                        .executes(c ->
                                near(c, h -> h.setHealthTo(FloatArgumentType.getFloat(c, "health")), "sethealth")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> healCommand() {
        return Commands.literal("heal")
                .requires(OP)
                .executes(c -> near(
                        c,
                        h -> {
                            h.heal();
                            h.mendPods();
                        },
                        "heal"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> poppedCommand() {
        return Commands.literal("popped")
                .requires(OP)
                .then(Commands.argument("n", IntegerArgumentType.integer(0, 64))
                        .executes(c -> near(c, h -> h.popPods(IntegerArgumentType.getInteger(c, "n")), "popped")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> gotoCommand() {
        return Commands.literal("goto")
                .requires(OP)
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                .executes(c -> farGo(
                                        c,
                                        new Vec3(
                                                FloatArgumentType.getFloat(c, "x"),
                                                0,
                                                FloatArgumentType.getFloat(c, "z")),
                                        null))));
    }

    /** come: the book's "Come to me", at any distance (to you, or to the player named) */
    private static LiteralArgumentBuilder<CommandSourceStack> comeCommand() {
        return Commands.literal("come")
                .requires(OP)
                .executes(c -> farGo(c, null, c.getSource().getPlayerOrException()))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(c -> farGo(c, null, EntityArgument.getPlayer(c, "player"))));
    }

    /** config: any setting from the file, by name */
    private static LiteralArgumentBuilder<CommandSourceStack> configCommand() {
        return Commands.literal("config")
                .requires(OP)
                .executes(BellCommand::configList)
                .then(Commands.argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(OpsCommand.keys(), b))
                        .executes(c -> configGet(c, StringArgumentType.getString(c, "key")))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .executes(c -> configSet(
                                        c,
                                        StringArgumentType.getString(c, "key"),
                                        StringArgumentType.getString(c, "value")))));
    }

    /** set: everything about the nearest one */
    private static LiteralArgumentBuilder<CommandSourceStack> setCommand() {
        return Commands.literal("set")
                .requires(OP)
                .then(Commands.literal("freeze")
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(c -> near(c, h -> h.setFrozen(BoolArgumentType.getBool(c, "value")), "set")))
                        .then(Commands.literal("on").executes(c -> near(c, h -> h.setFrozen(true), "set")))
                        .then(Commands.literal("off").executes(c -> near(c, h -> h.setFrozen(false), "set"))))
                .then(Commands.literal("speed")
                        .then(Commands.argument("times", FloatArgumentType.floatArg(0.1f, 5f))
                                .executes(c ->
                                        near(c, h -> h.setSpeedMul(FloatArgumentType.getFloat(c, "times")), "set"))))
                .then(Commands.literal("invulnerable")
                        .then(Commands.literal("on").executes(c -> near(c, h -> h.setInvulnerable(true), "set")))
                        .then(Commands.literal("off").executes(c -> near(c, h -> h.setInvulnerable(false), "set"))))
                .then(Commands.literal("glow")
                        .then(Commands.literal("on").executes(c -> near(c, h -> h.setGlowingTag(true), "set")))
                        .then(Commands.literal("off").executes(c -> near(c, h -> h.setGlowingTag(false), "set"))))
                .then(Commands.literal("name")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(c -> near(
                                        c,
                                        h -> h.setCustomName(
                                                Component.literal(StringArgumentType.getString(c, "name"))),
                                        "set"))))
                .then(Commands.literal("target")
                        .then(Commands.argument("target", EntityArgument.entity())
                                .executes(c -> {
                                    var t = EntityArgument.getEntity(c, "target");
                                    // (a part of another giant stands for its giant)
                                    if (!(LookTrace.ownerOf(t) instanceof LivingEntity le)) {
                                        c.getSource()
                                                .sendFailure(Component.translatable("command.hollowbell.not_alive"));
                                        return 0;
                                    }
                                    return near(c, h -> h.sendAfter(le), "set");
                                }))
                        // what the player giving it is looking at, by the book's rules
                        .then(Commands.literal("look").executes(c -> {
                            String said = CodexOrders.attackLooked(
                                    c.getSource().getPlayerOrException(),
                                    new CodexPayload(CodexPayload.ATTACK_THAT, 0),
                                    true);
                            return said.startsWith("look_going") ? 1 : 0;
                        }))
                        .then(Commands.literal("none").executes(c -> near(c, HollowbellEntity::clearHitList, "set"))))
                .then(Commands.literal("wind")
                        .then(Commands.argument("wind", FloatArgumentType.floatArg(0f, 1f))
                                .executes(c ->
                                        near(c, h -> h.mood().setWind(FloatArgumentType.getFloat(c, "wind")), "set"))))
                .then(Commands.literal("grudge")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("grudge", FloatArgumentType.floatArg(0f, 1f))
                                        .executes(c -> {
                                            var pl = EntityArgument.getPlayer(c, "player");
                                            return near(
                                                    c,
                                                    h -> h.mood()
                                                            .setSour(
                                                                    pl.getUUID(),
                                                                    FloatArgumentType.getFloat(c, "grudge")),
                                                    "set");
                                        }))))
                .then(Commands.literal("home")
                        .executes(c -> near(c, h -> h.setHome(c.getSource().getPosition()), "set")))
                .then(Commands.literal("cooldowns")
                        .then(Commands.literal("clear")
                                .executes(c -> near(c, h -> h.moves().clearCooldowns(), "set"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> podsCommand() {
        return Commands.literal("pods")
                .requires(OP)
                .then(Commands.literal("mend").executes(c -> near(c, HollowbellEntity::mendPods, "set")))
                .then(Commands.literal("pop")
                        .then(Commands.argument("how many", IntegerArgumentType.integer(0, 64))
                                .executes(c -> near(
                                        c, h -> h.popPods(IntegerArgumentType.getInteger(c, "how many")), "popped"))));
    }

    /** paint: the land round you becomes the Bell Hollows (admins; the same as /giants paint hollowbell) */
    private static LiteralArgumentBuilder<CommandSourceStack> paintCommand() {
        return Commands.literal("paint")
                .requires(OP)
                .executes(c -> paint(c, 64, true))
                .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512))
                        .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), true))
                        .then(Commands.literal("full")
                                .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), true))
                                .then(Commands.literal("depth")
                                        .then(Commands.argument("depth", IntegerArgumentType.integer(1, 64))
                                                .executes(c -> paint(
                                                        c,
                                                        IntegerArgumentType.getInteger(c, "radius"),
                                                        true,
                                                        IntegerArgumentType.getInteger(c, "depth"))))))
                        .then(Commands.literal("biome")
                                .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), false))));
    }

    /** tp: take me to him (never in the book) */
    private static LiteralArgumentBuilder<CommandSourceStack> tpCommand() {
        return Commands.literal("tp")
                .requires(OP)
                .executes(c -> tp(c, 0))
                .then(Commands.argument("which", IntegerArgumentType.integer(1, 999))
                        .executes(c -> tp(c, IntegerArgumentType.getInteger(c, "which"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> groundCommand() {
        return Commands.literal("ground")
                .requires(OP)
                .executes(BellCommand::groundSay)
                .then(Commands.literal("new").executes(BellCommand::groundNew))
                // check: goes over his whole ground on the real world and says what came out wrong (a map in
                // the world folder)
                .then(Commands.literal("check")
                        .executes(c -> groundCheck(c, 0))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 4000))
                                .executes(c -> groundCheck(c, IntegerArgumentType.getInteger(c, "radius")))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> stayCommand() {
        return Commands.literal("stay")
                .requires(OP)
                .executes(c -> near(c, h -> h.setStay(!h.staying()), "stay"))
                .then(Commands.literal("on").executes(c -> near(c, h -> h.setStay(true), "stay")))
                .then(Commands.literal("off").executes(c -> near(c, h -> h.setStay(false), "stay")))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(c -> near(c, h -> h.setStay(BoolArgumentType.getBool(c, "value")), "stay")));
    }

    /** sleep: he drifts down and sleeps, or wakes up (a toggle) */
    private static LiteralArgumentBuilder<CommandSourceStack> sleepCommand() {
        return Commands.literal("sleep")
                .requires(OP)
                .executes(BellCommand::sleepToggle)
                .then(Commands.literal("on").executes(c -> near(c, HollowbellEntity::goToSleep, "sleep")))
                .then(Commands.literal("off").executes(c -> near(c, HollowbellEntity::wakeUp, "sleep")));
    }

    /** the safe list of whoever runs it: players, single creatures, or whole kinds */
    private static LiteralArgumentBuilder<CommandSourceStack> spareCommand() {
        return Commands.literal("spare")
                .requires(OP)
                .executes(BellCommand::spareList)
                .then(Commands.literal("add")
                        .then(Commands.literal("kind")
                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.ENTITY_TYPE.keySet(), b))
                                        .executes(c -> spareKind(c, true))))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> spareWho(c, true))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("kind")
                                .then(Commands.argument("type", ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                                BuiltInRegistries.ENTITY_TYPE.keySet(), b))
                                        .executes(c -> spareKind(c, false))))
                        .then(Commands.argument("targets", EntityArgument.entities())
                                .executes(c -> spareWho(c, false))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heightCommand() {
        return Commands.literal("height")
                .requires(OP)
                .then(Commands.argument("blocks", FloatArgumentType.floatArg(0f))
                        .executes(c -> near(c, h -> h.setCruise(FloatArgumentType.getFloat(c, "blocks")), "height")));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> rideCommand() {
        return Commands.literal("ride").requires(OP).executes(c -> {
            ServerPlayer p = c.getSource().getPlayerOrException();
            HollowbellEntity h = nearest(c.getSource());
            if (h == null) return none(c);
            if (h.carrying()) h.dropRider();
            else h.possess(p);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> carryCommand() {
        return Commands.literal("carry").requires(OP).executes(c -> {
            // he comes to you and a strand carries you up onto his crown, like "ride him" in the book
            // (with /execute as, anything alive can be fetched: handy for watching it from the side)
            if (!(c.getSource().getEntity() instanceof LivingEntity p)) {
                c.getSource().getPlayerOrException();
                return 0;
            }
            HollowbellEntity h = nearest(c.getSource());
            if (h == null) return none(c);
            // already up there: he carries you back down the same way
            if (h.rider() == p) {
                h.setMeDown(p);
                c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_set_down"), false);
                return 1;
            }
            if (h.carrying() || !h.comeAndGetMe(p)) {
                c.getSource().sendFailure(Component.translatable("command.hollowbell.cannot_carry"));
                return 0;
            }
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_carry"), false);
            return 1;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> naturalCommand() {
        return Commands.literal("natural")
                .requires(OP)
                .executes(BellCommand::naturalSay)
                .then(Commands.literal("on").executes(c -> setNatural(c, true)))
                .then(Commands.literal("off").executes(c -> setNatural(c, false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> limitCommand() {
        return Commands.literal("limit")
                .requires(OP)
                .executes(BellCommand::limitSay)
                .then(Commands.argument("how many", IntegerArgumentType.integer(0, 20))
                        .executes(BellCommand::setLimit));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> wardCommand() {
        return Commands.literal("ward")
                .requires(OP)
                .executes(BellCommand::wardSay)
                .then(Commands.literal("on").executes(BellCommand::wardOn))
                .then(Commands.literal("off").executes(c -> {
                    WorldOne.get(c.getSource().getServer()).forgetWard();
                    c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.ward_off"), true);
                    return 1;
                }))
                .then(Commands.literal("minutes")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440))
                                .executes(c -> {
                                    int v = IntegerArgumentType.getInteger(c, "minutes");
                                    BellConfig.V.wardSeconds = v * 60;
                                    BellConfig.save();
                                    c.getSource()
                                            .sendSuccess(
                                                    () -> Component.translatable("command.hollowbell.ward_minutes", v),
                                                    true);
                                    return 1;
                                })))
                .then(Commands.literal("rest")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440))
                                .executes(c -> {
                                    int v = IntegerArgumentType.getInteger(c, "minutes");
                                    BellConfig.V.wardRestSeconds = v * 60;
                                    BellConfig.save();
                                    c.getSource()
                                            .sendSuccess(
                                                    () -> v == 0
                                                            ? Component.translatable(
                                                                    "command.hollowbell.ward_rest_none")
                                                            : Component.translatable("command.hollowbell.ward_rest", v),
                                                    true);
                                    return 1;
                                })))
                .then(Commands.literal("blocks")
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 20000))
                                .executes(c -> {
                                    int v = IntegerArgumentType.getInteger(c, "blocks");
                                    BellConfig.V.wardBlocks = v;
                                    BellConfig.save();
                                    c.getSource()
                                            .sendSuccess(
                                                    () -> v == 0
                                                            ? Component.translatable(
                                                                    "command.hollowbell.ward_blocks_none")
                                                            : Component.translatable(
                                                                    "command.hollowbell.ward_blocks", v),
                                                    true);
                                    return 1;
                                })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> areaCommand() {
        return Commands.literal("area")
                .requires(OP)
                .executes(BellCommand::areaSay)
                .then(Commands.literal("off").executes(BellCommand::areaFree))
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                                .then(Commands.argument("radius", IntegerArgumentType.integer(32, 100000))
                                        .executes(BellCommand::areaBind))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> giantsCommand() {
        return Commands.literal("giants")
                .requires(OP)
                .executes(c -> {
                    c.getSource()
                            .sendSuccess(
                                    () -> Component.translatable(
                                            BellConfig.V.fightGiants
                                                    ? "command.hollowbell.giants_is_on"
                                                    : "command.hollowbell.giants_is_off"),
                                    false);
                    return 1;
                })
                .then(Commands.literal("on")
                        .executes(c -> set(c, () -> BellConfig.V.fightGiants = true, "fightGiants", true)))
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.fightGiants = false, "fightGiants", false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> meetingsCommand() {
        return Commands.literal("meetings")
                .requires(OP)
                .executes(c -> {
                    c.getSource()
                            .sendSuccess(
                                    () -> Component.translatable(
                                            BellConfig.V.meetings
                                                    ? "command.hollowbell.meetings_is_on"
                                                    : "command.hollowbell.meetings_is_off"),
                                    false);
                    return 1;
                })
                .then(Commands.literal("on")
                        .executes(c -> set(c, () -> BellConfig.V.meetings = true, "meetings", true)))
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.meetings = false, "meetings", false)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> volumeCommand() {
        return Commands.literal("volume")
                .requires(OP)
                .executes(c -> {
                    c.getSource()
                            .sendSuccess(
                                    () -> Component.translatable(
                                            "command.hollowbell.volume_is", String.valueOf(BellConfig.V.soundVolume)),
                                    false);
                    return 1;
                })
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.soundVolume = 0f, "soundVolume", 0f)))
                .then(Commands.argument("volume", FloatArgumentType.floatArg(0f, 2f))
                        .executes(c -> set(
                                c,
                                () -> BellConfig.V.soundVolume = FloatArgumentType.getFloat(c, "volume"),
                                "soundVolume",
                                FloatArgumentType.getFloat(c, "volume"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> killCommand() {
        return Commands.literal("kill").requires(OP).executes(c -> {
            int n = FarOrders.killAll(c.getSource().getServer());
            if (n == 0) return none(c);
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_kill"), true);
            return n;
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> removeCommand() {
        return Commands.literal("remove").requires(OP).executes(c -> {
            var server = c.getSource().getServer();
            WorldOne w = WorldOne.get(server);
            int out = Away.get(server).count() + FarOrders.removeLying(server);
            for (Away.Rec r : Away.get(server).all()) w.removed(server.overworld(), r.id);
            Away.get(server).forgetAll();
            if (allOf(c.getSource()).isEmpty() && out > 0) {
                c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_remove"), true);
                return out;
            }
            return all(
                    c,
                    h -> {
                        h.discard();
                        w.removed(server.overworld(), h.getUUID());
                    },
                    "remove");
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> healthCommand() {
        return Commands.literal("health")
                .requires(OP)
                .executes(c -> say(c, "health", BellConfig.V.health))
                .then(Commands.argument("health", FloatArgumentType.floatArg(10f, 1_000_000f))
                        .executes(c -> set(
                                c,
                                () -> BellConfig.V.health = FloatArgumentType.getFloat(c, "health"),
                                "health",
                                FloatArgumentType.getFloat(c, "health"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> damageCommand() {
        return Commands.literal("damage")
                .requires(OP)
                .executes(c -> say(c, "damage", BellConfig.V.damageMultiplier))
                .then(Commands.literal("mobs")
                        .executes(c -> say(c, "mobDamage", BellConfig.V.mobDamage))
                        .then(Commands.argument("times", FloatArgumentType.floatArg(0f, 100f))
                                .executes(c -> set(
                                        c,
                                        () -> BellConfig.V.mobDamage = FloatArgumentType.getFloat(c, "times"),
                                        "mobDamage",
                                        FloatArgumentType.getFloat(c, "times")))))
                .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0f, 100f))
                        .executes(c -> set(
                                c,
                                () -> BellConfig.V.damageMultiplier = FloatArgumentType.getFloat(c, "multiplier"),
                                "damage",
                                FloatArgumentType.getFloat(c, "multiplier"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> heavydelayCommand() {
        return Commands.literal("heavydelay")
                .requires(OP)
                .executes(c -> heavyDelay(c, -1))
                .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 600))
                        .executes(c -> heavyDelay(c, IntegerArgumentType.getInteger(c, "seconds"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> reloadCommand() {
        return Commands.literal("reload").requires(OP).executes(c -> {
            boolean ok = BellConfig.load();
            c.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    ok ? "command.hollowbell.reloaded" : "command.hollowbell.reload_bad"),
                            true);
            return ok ? 1 : 0;
        });
    }

    /** how the book tires him: on or off, how long to get all his wind back, how fast pushing him sours him */
    private static LiteralArgumentBuilder<CommandSourceStack> windCommand() {
        return Commands.literal("wind")
                .requires(OP)
                .executes(BellCommand::windSay)
                .then(Commands.literal("on")
                        .executes(c -> set(c, () -> BellConfig.V.bookCosts = true, "bookCosts", "on")))
                .then(Commands.literal("off")
                        .executes(c -> set(c, () -> BellConfig.V.bookCosts = false, "bookCosts", "off")))
                .then(Commands.literal("seconds")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600))
                                .executes(c -> set(
                                        c,
                                        () -> BellConfig.V.windSeconds = IntegerArgumentType.getInteger(c, "seconds"),
                                        "windSeconds",
                                        IntegerArgumentType.getInteger(c, "seconds")))))
                .then(Commands.literal("grudge")
                        .then(Commands.argument("rate", FloatArgumentType.floatArg(0f, 10f))
                                .executes(c -> set(
                                        c,
                                        () -> BellConfig.V.grudgeRate = FloatArgumentType.getFloat(c, "rate"),
                                        "grudgeRate",
                                        FloatArgumentType.getFloat(c, "rate")))))
                .then(Commands.argument("share", FloatArgumentType.floatArg(0f, 1f))
                        .executes(c -> {
                            HollowbellEntity h = nearest(c.getSource());
                            if (h == null) return none(c);
                            h.mood().setWind(FloatArgumentType.getFloat(c, "share"));
                            int now = Math.round(h.mood().wind() * 100);
                            c.getSource()
                                    .sendSuccess(
                                            () -> Component.translatable("command.hollowbell.windnow_is", now), true);
                            return 1;
                        }));
    }

    private static int windSay(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        int now = h == null ? 100 : Math.round(h.mood().wind() * 100);
        String key = BellConfig.V.bookCosts ? "command.hollowbell.wind_on" : "command.hollowbell.wind_off";
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                key, now, BellConfig.V.windSeconds, String.valueOf(BellConfig.V.grudgeRate)),
                        false);
        return 1;
    }

    /** how long into a fight before his first heavy move: said, or set (seconds >= 0) */
    private static int heavyDelay(CommandContext<CommandSourceStack> c, int seconds) {
        if (seconds >= 0) {
            BellConfig.V.heavyDelaySeconds = seconds;
            BellConfig.save();
        }
        int now = BellConfig.V.heavyDelaySeconds;
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.heavydelay", now), seconds >= 0);
        return 1;
    }

    /** "/hollowbell griefing [on|off]": bare says what it is; the old true/false form still works */
    private static LiteralArgumentBuilder<CommandSourceStack> toggle(
            String name, String key, BooleanSupplier get, Consumer<Boolean> put) {
        return Commands.literal(name)
                .requires(OP)
                .executes(c -> say(c, key, get.getAsBoolean() ? "on" : "off"))
                .then(Commands.literal("on").executes(c -> set(c, () -> put.accept(true), key, "on")))
                .then(Commands.literal("off").executes(c -> set(c, () -> put.accept(false), key, "off")))
                .then(Commands.argument("value", BoolArgumentType.bool()).executes(c -> {
                    boolean v = BoolArgumentType.getBool(c, "value");
                    return set(c, () -> put.accept(v), key, v ? "on" : "off");
                }));
    }

    private static int say(CommandContext<CommandSourceStack> c, String key, Object v) {
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable("command.hollowbell.is", plain(key), String.valueOf(v)), false);
        return 1;
    }

    /** what a setting is called in chat (its name in the file means little to a player) */
    private static final Map<String, String> PLAIN = Map.ofEntries(
            Map.entry("health", "His full-size health"),
            Map.entry("damage", "His hits"),
            Map.entry("mobDamage", "His hits on creatures"),
            Map.entry("griefing", "Breaking blocks"),
            Map.entry("screenShake", "Screen shake"),
            Map.entry("soundVolume", "His volume"),
            Map.entry("meetings", "Meeting other giants"),
            Map.entry("fightGiants", "Fighting other giants"),
            Map.entry("bossBar", "His boss bars"),
            Map.entry("bossBarRange", "How far off his boss bars show"),
            Map.entry("bookCosts", "The book tiring him"),
            Map.entry("windSeconds", "Seconds to get his wind back"),
            Map.entry("grudgeRate", "How fast he holds a grudge"),
            Map.entry("offscreenTravel", "Travelling while nobody is near"),
            Map.entry("awayBlocks", "How far off nobody counts as near"));

    private static String plain(String key) {
        return PLAIN.getOrDefault(key, key);
    }

    private static float eggSize() {
        return Mth.clamp(BellConfig.V.spawnEggScale, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE);
    }

    private static int bossbarSay(CommandContext<CommandSourceStack> c) {
        int r = BellConfig.V.bossBarRange;
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                BellConfig.V.bossBar
                                        ? "command.hollowbell.bossbar_on"
                                        : "command.hollowbell.bossbar_off",
                                r > 0 ? String.valueOf(r) : (int) HollowbellEntity.barRange(1f) + " (auto)"),
                        false);
        return 1;
    }

    /** /hollowbell sleep: the nearest one goes to sleep, or wakes up */
    private static int sleepToggle(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        if (h.asleep()) h.wakeUp();
        else h.goToSleep();
        boolean now = h.asleep();
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(now ? "command.hollowbell.asleep" : "command.hollowbell.awake"),
                        false);
        return 1;
    }

    /** /hollowbell spare: what's on your list */
    private static int spareList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var w = BellWorld.get(p.server);
        var who = w.listOf(p.getUUID());
        var kinds = w.kindsOf(p.getUUID());
        if (who.isEmpty() && kinds.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.spare_empty"), false);
            return 0;
        }
        List<String> names = new ArrayList<>();
        for (var u : who)
            names.add(
                    w.isMob(u)
                            ? Component.translatable("codex.hollowbell.list_one", w.nameOf(u))
                                    .getString()
                            : w.nameOf(u));
        for (var k : kinds)
            names.add(Component.translatable("codex.hollowbell.list_kind", BellWorld.kindsName(k))
                    .getString());
        String all = String.join(", ", names);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.spare_list", all), false);
        return names.size();
    }

    private static int spareWho(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var w = BellWorld.get(p.server);
        int n = 0;
        for (var e : EntityArgument.getEntities(c, "targets")) {
            if (e instanceof Player pl) {
                w.rememberName(pl.getUUID(), pl.getGameProfile().getName());
                if (on) w.addFriend(p.getUUID(), pl.getUUID());
                else w.dropFriend(p.getUUID(), pl.getUUID());
                n++;
            } else if (e instanceof LivingEntity le && !(e instanceof HollowbellEntity)) {
                w.rememberMob(le.getUUID(), le.getName().getString());
                if (on) w.addFriend(p.getUUID(), le.getUUID());
                else w.dropFriend(p.getUUID(), le.getUUID());
                n++;
            }
        }
        CodexOrders.sendSafeList(p);
        final int f = n;
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                on ? "command.hollowbell.spare_added" : "command.hollowbell.spare_removed", f),
                        false);
        return n;
    }

    private static int spareKind(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var id = ResourceLocationArgument.getId(c, "type");
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.spare_no_kind", id.toString()));
            return 0;
        }
        CodexOrders.spareKind(p, type.get(), on);
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                on ? "command.hollowbell.spare_kind_added" : "command.hollowbell.spare_kind_removed",
                                BellWorld.kindsName(id.toString())),
                        false);
        return 1;
    }

    /** /hollowbell goto and come: the nearest one, wherever he is (in the world, out of it, or in unloaded land) */
    private static int farGo(CommandContext<CommandSourceStack> c, @Nullable Vec3 to, @Nullable ServerPlayer follow) {
        ServerLevel l = follow != null ? follow.serverLevel() : c.getSource().getLevel();
        Vec3 from = follow != null ? follow.position() : c.getSource().getPosition();
        var t = FarOrders.nearest(l, from);
        if (t == null) return none(c);
        Component said = FarOrders.order(l, t, to != null ? to : follow.position(), follow, 0f);
        if (said == null) return none(c);
        c.getSource().sendSuccess(() -> said, false);
        return 1;
    }

    private static int configList(CommandContext<CommandSourceStack> c) {
        StringBuilder b = new StringBuilder();
        for (String k : OpsCommand.keys()) {
            if (b.length() > 0) b.append(", ");
            b.append(k).append(" ").append(OpsCommand.get(k));
        }
        String all = b.toString();
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.config_all", all), false);
        return 1;
    }

    private static int configGet(CommandContext<CommandSourceStack> c, String key) {
        String v = OpsCommand.get(key);
        if (v == null) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.config_none", key));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.is", key, v), false);
        return 1;
    }

    private static int configSet(CommandContext<CommandSourceStack> c, String key, String value) {
        String bad = OpsCommand.set(key, value);
        if (bad != null) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.config_bad", bad));
            return 0;
        }
        String now = OpsCommand.get(key);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.set", key, now), true);
        return 1;
    }

    private static int paint(CommandContext<CommandSourceStack> c, int radius, boolean full)
            throws CommandSyntaxException {
        return paint(c, radius, full, Painter.DEPTH);
    }

    private static int paint(CommandContext<CommandSourceStack> c, int radius, boolean full, int depth)
            throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = Painter.start(p, radius, full, depth);
        c.getSource()
                .sendSuccess(
                        () -> full
                                ? Component.translatable("command.hollowbell.paint_full", radius, depth, n)
                                : Component.translatable("command.hollowbell.paint_biome", radius, n),
                        true);
        return n;
    }

    private static int tp(CommandContext<CommandSourceStack> c, int which) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Component said = TakeMe.tp(p, which);
        c.getSource().sendSuccess(() -> said, false);
        return 1;
    }

    /** /hollowbell summon hunting, or /hollowbell summon 0.3 (a hunting one that size) */
    private static int summonMoodOrSize(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String m = StringArgumentType.getString(c, "mood");
        try {
            float size = Float.parseFloat(m);
            if (Float.isFinite(size))
                return summon(
                        c,
                        HollowbellEntity.HUNTER,
                        Mth.clamp(size, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE));
        } catch (NumberFormatException notASize) {
            // a mood, then
        }
        return summon(c, mood(c), eggSize());
    }

    private static int mood(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String m = StringArgumentType.getString(c, "mood");
        if (m.equals("hunter")) return HollowbellEntity.HUNTER;
        for (int i = 0; i < MOODS.length; i++) if (MOODS[i].equals(m)) return i;
        throw new SimpleCommandExceptionType(Component.translatable("command.hollowbell.bad_mood")).create();
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
        h.moveTo(
                at.x,
                NoWait.standOrGuess(l, Mth.floor(at.x), Mth.floor(at.z), Mth.floor(at.y)),
                at.z,
                l.getRandom().nextFloat() * 360f,
                0f);
        h.setBellScale(size);
        h.setVariant(mood);
        h.setHome(h.position());
        l.addFreshEntity(h);
        // the land may not be made yet that far out: held until it is, then kept ticking a few seconds so he takes
        // his first ticks there (a ticket that only loads the land never ticks him)
        NoWait.hold(l, (int) Math.floor(at.x), (int) Math.floor(at.z), 1200);
        NoWait.settle(l, (int) Math.floor(at.x), (int) Math.floor(at.z), 200);
        src.sendSuccess(
                () -> Component.translatable(
                        "command.hollowbell.summoned",
                        Component.translatable("mode.hollowbell." + mood),
                        String.format("%.2f", size)),
                true);
        return 1;
    }

    /** do <move> [target | at x y z]: at whoever runs it (or his own target) unless told otherwise */
    private static int doMove(CommandContext<CommandSourceStack> c, @Nullable Entity who, @Nullable Vec3 spot) {
        String name = StringArgumentType.getString(c, "move");
        int which = Moves.byName(name);
        if (which < 0) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.bad_move", name));
            return 0;
        }
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        if (who != null && !(who instanceof LivingEntity)) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.not_alive"));
            return 0;
        }
        LivingEntity at = who instanceof LivingEntity w
                ? w
                : c.getSource().getEntity() instanceof LivingEntity le ? le : h.getTarget();
        boolean ok = spot != null ? h.forceMoveAt(which, spot) : h.forceMove(which, at);
        if (!ok) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.cannot", name));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.doing", name), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        List<HollowbellEntity> all = allOf(c.getSource());
        List<Away.Rec> out = Away.get(c.getSource().getServer()).all();
        var lying = Away.get(c.getSource().getServer()).lying(c.getSource().getServer());
        if (all.isEmpty() && out.isEmpty() && lying.isEmpty()) return none(c);
        int[] k = {0};
        for (HollowbellEntity h : all) {
            int num = ++k[0];
            c.getSource()
                    .sendSuccess(
                            () -> Component.literal(num + ". ")
                                    .append(Component.translatable(
                                            "command.hollowbell.list_line",
                                            (int) h.getX(),
                                            (int) h.getY(),
                                            (int) h.getZ(),
                                            String.format("%.2f", h.bellScale()),
                                            Component.translatable("mode.hollowbell." + h.variant()),
                                            (int) h.healthNow(),
                                            (int) h.healthMax(),
                                            h.podsLeft(),
                                            h.rig.pods.length)),
                            false);
        }
        long now = c.getSource().getLevel().getGameTime();
        for (Away.Rec r : out) awayLine(c, r, now, ++k[0]);
        for (Away.Parked pk : lying.values()) lyingLine(c, pk, ++k[0]);
        return all.size() + out.size() + lying.size();
    }

    /** one saved with his unloaded chunk */
    private static void lyingLine(CommandContext<CommandSourceStack> c, Away.Parked k, int num) {
        Component line = Component.translatable(
                "command.hollowbell.where_lying", (int) Math.floor(k.x()), (int) Math.floor(k.z()), k.dim());
        c.getSource().sendSuccess(() -> num > 0 ? Component.literal(num + ". ").append(line) : line, false);
    }

    private static void awayLine(CommandContext<CommandSourceStack> c, Away.Rec r, long now) {
        awayLine(c, r, now, 0);
    }

    private static void awayLine(CommandContext<CommandSourceStack> c, Away.Rec r, long now, int num) {
        Vec3 s = r.spot(now);
        Component line = r.going
                ? Component.translatable(
                        "command.hollowbell.away_going",
                        (int) s.x,
                        (int) s.z,
                        r.dim,
                        (int) r.toX,
                        (int) r.toZ,
                        Math.max(1, r.minutesLeft(now)),
                        String.format("%.2f", r.scale),
                        (int) r.hp,
                        (int) r.hpMax)
                : Component.translatable(
                        "command.hollowbell.away_still",
                        (int) s.x,
                        (int) s.z,
                        r.dim,
                        String.format("%.2f", r.scale),
                        (int) r.hp,
                        (int) r.hpMax);
        c.getSource().sendSuccess(() -> num > 0 ? Component.literal(num + ". ").append(line) : line, false);
    }

    /** where each one is, loaded or away */
    private static int where(CommandContext<CommandSourceStack> c) {
        List<HollowbellEntity> all = allOf(c.getSource());
        List<Away.Rec> out = Away.get(c.getSource().getServer()).all();
        var lying = Away.get(c.getSource().getServer()).lying(c.getSource().getServer());
        if (all.isEmpty() && out.isEmpty() && lying.isEmpty()) return none(c);
        for (HollowbellEntity h : all)
            c.getSource()
                    .sendSuccess(
                            () -> Component.translatable(
                                    "command.hollowbell.where_in",
                                    (int) h.getX(),
                                    (int) h.getY(),
                                    (int) h.getZ(),
                                    h.level().dimension().location().toString(),
                                    (int) h.healthNow(),
                                    (int) h.healthMax()),
                            false);
        long now = c.getSource().getLevel().getGameTime();
        for (Away.Rec r : out) awayLine(c, r, now);
        for (Away.Parked pk : lying.values()) lyingLine(c, pk, 0);
        return all.size() + out.size() + lying.size();
    }

    private static int awaySay(CommandContext<CommandSourceStack> c) {
        int b = BellConfig.V.awayBlocks;
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                BellConfig.V.offscreenTravel
                                        ? "command.hollowbell.away_is_on"
                                        : "command.hollowbell.away_is_off",
                                b > 0
                                        ? String.valueOf(b)
                                        : (int) Away.awayRange(c.getSource().getServer(), 1f) + " (auto)",
                                Away.get(c.getSource().getServer()).count()),
                        false);
        return 1;
    }

    /** every one with nobody near steps out now */
    private static int awayNow(CommandContext<CommandSourceStack> c) {
        int n = 0;
        for (HollowbellEntity h : allOf(c.getSource())) {
            boolean near = false;
            double r = Away.awayRange(c.getSource().getServer(), h.bellScale());
            for (ServerPlayer p : ((ServerLevel) h.level()).players())
                if (p.distanceToSqr(h.getX(), p.getY(), h.getZ()) < r * r) {
                    near = true;
                    break;
                }
            if (!near && h.stepAside()) n++;
        }
        final int f = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.away_now", f), true);
        return n;
    }

    private interface Act {
        void on(HollowbellEntity h) throws CommandSyntaxException;
    }

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
        BellConfig.save();
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable("command.hollowbell.set", plain(what), String.valueOf(v)), true);
        return 1;
    }

    private static int none(CommandContext<CommandSourceStack> c) {
        c.getSource().sendFailure(Component.translatable("command.hollowbell.none"));
        return 0;
    }

    public static List<HollowbellEntity> allOf(CommandSourceStack src) {
        List<HollowbellEntity> out = new ArrayList<>();
        for (ServerLevel l : src.getServer().getAllLevels())
            out.addAll(l.getEntities(ModEntities.HOLLOWBELL, e -> !e.isRemoved()));
        return out;
    }

    public static @Nullable HollowbellEntity nearest(CommandSourceStack src) {
        Vec3 p = src.getPosition();
        return src.getLevel().getEntities(ModEntities.HOLLOWBELL, e -> !e.isDeadOrDying()).stream()
                .min(Comparator.comparingDouble(e -> e.distanceToSqr(p)))
                .orElse(null);
    }

    // the new world commands

    /** /hollowbell natural: does the world keep one of him out there, and where things stand (the finder's words) */
    private static int naturalSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        Component msg = BellConfig.V.oneInTheWorld
                ? Component.translatable(
                        "command.hollowbell.natural_is_on",
                        FinderItem.tell(over, c.getSource().getPosition()))
                : Component.translatable("command.hollowbell.natural_is_off");
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /** /hollowbell ground: where his ground is and how big */
    private static int groundSay(CommandContext<CommandSourceStack> c) {
        WorldOne w = WorldOne.get(c.getSource().getServer());
        Component msg = !w.homeClaimed()
                ? Component.translatable("command.hollowbell.ground_none")
                : Component.translatable("command.hollowbell.ground_is", w.homeX(), w.homeZ(), w.homeRadius());
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /** /hollowbell ground new: a fresh ground in land the world hasn't made yet (the old one stays as it is) */
    private static int groundNew(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        BlockPos at = w.newGround(over);
        boolean alive = w.aliveNow();
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                alive ? "command.hollowbell.ground_new" : "command.hollowbell.ground_new_next",
                                at.getX(),
                                at.getZ()),
                        true);
        return 1;
    }

    /**
     * /hollowbell ground check [radius]: every column of his ground looked at, the answer in chat and the world folder
     */
    private static int groundCheck(CommandContext<CommandSourceStack> c, int radius) {
        Component msg = GroundCheck.start(c.getSource(), radius);
        c.getSource().sendSuccess(() -> msg, true);
        return 1;
    }

    private static int setNatural(CommandContext<CommandSourceStack> c, boolean on) {
        BellConfig.V.oneInTheWorld = on;
        BellConfig.save();
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable(
                                on ? "command.hollowbell.natural_on" : "command.hollowbell.natural_off"),
                        true);
        return 1;
    }

    /** everyone standing, in the world and out of it */
    private static int countAll(CommandSourceStack src) {
        return allOf(src).size() + Away.get(src.getServer()).count();
    }

    private static int limitSay(CommandContext<CommandSourceStack> c) {
        int max = BellConfig.V.maxInWorld;
        int now = countAll(c.getSource());
        c.getSource()
                .sendSuccess(
                        () -> max <= 0
                                ? Component.translatable("command.hollowbell.limit_none", now)
                                : Component.translatable("command.hollowbell.limit_is", max, now),
                        false);
        return 1;
    }

    private static int setLimit(CommandContext<CommandSourceStack> c) {
        int v = IntegerArgumentType.getInteger(c, "how many");
        BellConfig.V.maxInWorld = v;
        BellConfig.save();
        c.getSource()
                .sendSuccess(
                        () -> v == 0
                                ? Component.translatable("command.hollowbell.limit_set_none")
                                : Component.translatable("command.hollowbell.limit_set", v),
                        true);
        if (v > 0) WorldOne.limitNow(null, c.getSource().getServer().overworld());
        return 1;
    }

    private static int wardSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        var at = w.wardSpot();
        Component msg;
        if (w.warding(over) && at != null)
            msg = Component.translatable(
                    "command.hollowbell.ward_awake",
                    at.getX(),
                    at.getZ(),
                    (int) WorldOne.wardRange(),
                    w.wardLeft(over) / 20);
        else if (w.wardRestLeft(over) > 0)
            msg = Component.translatable("command.hollowbell.ward_dark", w.wardRestLeft(over) / 20);
        else
            msg = Component.translatable(
                    "command.hollowbell.ward_none",
                    (int) WorldOne.wardRange(),
                    Math.max(1, BellConfig.V.wardSeconds / 60),
                    Math.max(0, BellConfig.V.wardRestSeconds / 60));
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int wardOn(CommandContext<CommandSourceStack> c) {
        ServerLevel lvl = c.getSource().getLevel();
        BlockPos at = BlockPos.containing(c.getSource().getPosition());
        BlockPos crown = null;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16)))
            if (lvl.getBlockState(p).is(ModBlocks.CROWN)) {
                crown = p.immutable();
                break;
            }
        if (crown == null) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.ward_no_crown"));
            return 0;
        }
        CrownBlock.Woken w = CrownBlock.wake(lvl, crown);
        if (!w.ok()) {
            c.getSource().sendFailure(w.said());
            return 0;
        }
        c.getSource().sendSuccess(w::said, true);
        return 1;
    }

    private static int areaSay(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        c.getSource()
                .sendSuccess(
                        () -> (h.bound()
                                ? Component.translatable(
                                        "command.hollowbell.area_is",
                                        h.boundRadius(),
                                        Mth.floor(h.boundCentre().x),
                                        Mth.floor(h.boundCentre().z))
                                : Component.translatable("command.hollowbell.area_free")),
                        false);
        return 1;
    }

    private static int areaBind(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        double x = FloatArgumentType.getFloat(c, "x"), z = FloatArgumentType.getFloat(c, "z");
        int r = IntegerArgumentType.getInteger(c, "radius");
        h.bindTo(x, z, r);
        c.getSource()
                .sendSuccess(
                        () -> Component.translatable("command.hollowbell.area_set", r, Mth.floor(x), Mth.floor(z)),
                        true);
        return 1;
    }

    private static int areaFree(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        h.unbind();
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.area_off"), true);
        return 1;
    }
}
