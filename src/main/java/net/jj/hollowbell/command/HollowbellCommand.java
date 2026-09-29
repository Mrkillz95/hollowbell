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

    /** /hollowbell detail far: how far off he can be seen coming */
    private static int farSay(CommandContext<CommandSourceStack> c) {
        int b = HollowbellConfig.V.farSightBlocks;
        c.getSource().sendSuccess(() -> b > 0 ? Component.translatable("command.hollowbell.far_is", b)
                : Component.translatable("command.hollowbell.far_off"), false);
        return 1;
    }

    /** /hollowbell detail far <blocks>: set here, and passed on to the player's own game too */
    private static int farSet(CommandContext<CommandSourceStack> c, int blocks) {
        HollowbellConfig.V.farSightBlocks = blocks;
        HollowbellConfig.save();
        if (c.getSource().getPlayer() instanceof ServerPlayer p)
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, new net.jj.hollowbell.net.DetailPayload(net.jj.hollowbell.net.DetailPayload.FAR, blocks));
        c.getSource().sendSuccess(() -> blocks > 0 ? Component.translatable("command.hollowbell.far_set", blocks)
                : Component.translatable("command.hollowbell.far_off"), true);
        return 1;
    }

    private static final String[] MOODS = {"calm", "hunting", "guardian"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hollowbell")
                // detail: anyone, cheats or not (it only changes how their own game draws him)
                .then(Commands.literal("detail").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.ASK))
                        .then(Commands.literal("on").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.ON)))
                        .then(Commands.literal("off").executes(c -> detail(c, net.jj.hollowbell.net.DetailPayload.OFF)))
                        .then(Commands.literal("far").executes(HollowbellCommand::farSay)
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 4096)).requires(OP)
                                        .executes(c -> farSet(c, IntegerArgumentType.getInteger(c, "blocks"))))))
                .then(Commands.literal("summon").requires(OP).executes(c -> summon(c, HollowbellEntity.HUNTER, eggSize()))
                        .then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                                .executes(c -> summon(c, mood(c), eggSize()))
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
                .then(Commands.literal("bossbar").requires(OP).executes(HollowbellCommand::bossbarSay)
                        .then(Commands.literal("on").executes(c -> set(c, () -> HollowbellConfig.V.bossBar = true, "bossBar", true)))
                        .then(Commands.literal("off").executes(c -> set(c, () -> HollowbellConfig.V.bossBar = false, "bossBar", false)))
                        .then(Commands.argument("blocks", IntegerArgumentType.integer(0, 100000))
                                .executes(c -> set(c, () -> HollowbellConfig.V.bossBarRange = IntegerArgumentType.getInteger(c, "blocks"), "bossBarRange", IntegerArgumentType.getInteger(c, "blocks")))))
                .then(Commands.literal("mood").requires(OP).then(Commands.argument("mood", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(MOODS, b))
                        .executes(c -> near(c, h -> { h.setVariant(mood(c)); if (h.isGuardian()) h.setHome(h.position()); }, "mood"))))
                .then(Commands.literal("size").requires(OP).then(Commands.argument("size", FloatArgumentType.floatArg(HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE))
                        .executes(c -> near(c, h -> h.setBellScale(FloatArgumentType.getFloat(c, "size")), "size"))))
                .then(Commands.literal("hurt").requires(OP).then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                        .executes(c -> near(c, h -> h.hurtBy(FloatArgumentType.getFloat(c, "amount")), "hurt"))))
                .then(Commands.literal("sethealth").requires(OP).then(Commands.argument("health", FloatArgumentType.floatArg(1f))
                        .executes(c -> near(c, h -> h.setHealthTo(FloatArgumentType.getFloat(c, "health")), "sethealth"))))
                .then(Commands.literal("heal").requires(OP).executes(c -> near(c, h -> { h.heal(); h.mendPods(); }, "heal")))
                .then(Commands.literal("popped").requires(OP).then(Commands.argument("n", IntegerArgumentType.integer(0, 64))
                        .executes(c -> near(c, h -> h.popPods(IntegerArgumentType.getInteger(c, "n")), "popped"))))
                .then(Commands.literal("goto").requires(OP).then(Commands.argument("x", FloatArgumentType.floatArg()).then(Commands.argument("z", FloatArgumentType.floatArg())
                        .executes(c -> farGo(c, new Vec3(FloatArgumentType.getFloat(c, "x"), 0, FloatArgumentType.getFloat(c, "z")), null)))))
                // come: the book's "Come to me", at any distance (to you, or to the player named)
                .then(Commands.literal("come").requires(OP).executes(c -> farGo(c, null, c.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(c -> farGo(c, null, net.minecraft.commands.arguments.EntityArgument.getPlayer(c, "player")))))
                // paint: the land round you becomes the Bell Hollows (admins; the same as /giants paint hollowbell)
                .then(Commands.literal("paint").requires(OP).executes(c -> paint(c, 64, true))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(16, 512)).executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), true))
                                .then(Commands.literal("full").executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), true)))
                                .then(Commands.literal("biome").executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), false)))))
                // tp: take me to him (never in the book)
                .then(Commands.literal("tp").requires(OP).executes(c -> tp(c, 0))
                        .then(Commands.argument("which", IntegerArgumentType.integer(1, 999)).executes(c -> tp(c, IntegerArgumentType.getInteger(c, "which")))))
                .then(Commands.literal("ground").requires(OP).executes(HollowbellCommand::groundSay)
                        .then(Commands.literal("new").executes(HollowbellCommand::groundNew)))
                .then(Commands.literal("stay").requires(OP).executes(c -> near(c, h -> h.setStay(!h.staying()), "stay"))
                        .then(Commands.literal("on").executes(c -> near(c, h -> h.setStay(true), "stay")))
                        .then(Commands.literal("off").executes(c -> near(c, h -> h.setStay(false), "stay")))
                        .then(Commands.argument("value", BoolArgumentType.bool())
                                .executes(c -> near(c, h -> h.setStay(BoolArgumentType.getBool(c, "value")), "stay"))))
                // sleep: he drifts down and sleeps, or wakes up (a toggle)
                .then(Commands.literal("sleep").requires(OP).executes(HollowbellCommand::sleepToggle))
                // the safe list of whoever runs it: players, single creatures, or whole kinds
                .then(Commands.literal("spare").requires(OP).executes(HollowbellCommand::spareList)
                        .then(Commands.literal("add")
                                .then(Commands.literal("kind").then(Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet(), b))
                                        .executes(c -> spareKind(c, true))))
                                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities()).executes(c -> spareWho(c, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.literal("kind").then(Commands.argument("type", net.minecraft.commands.arguments.ResourceLocationArgument.id())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggestResource(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.keySet(), b))
                                        .executes(c -> spareKind(c, false))))
                                .then(Commands.argument("targets", net.minecraft.commands.arguments.EntityArgument.entities()).executes(c -> spareWho(c, false)))))
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
                            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.ward_off"), true);
                            return 1;
                        }))
                        .then(Commands.literal("minutes").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            HollowbellConfig.V.wardSeconds = v * 60;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.ward_minutes", v), true);
                            return 1;
                        })))
                        .then(Commands.literal("rest").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            HollowbellConfig.V.wardRestSeconds = v * 60;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> v == 0 ? Component.translatable("command.hollowbell.ward_rest_none")
                                    : Component.translatable("command.hollowbell.ward_rest", v), true);
                            return 1;
                        })))
                        .then(Commands.literal("blocks").then(Commands.argument("blocks", IntegerArgumentType.integer(0, 20000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            HollowbellConfig.V.wardBlocks = v;
                            HollowbellConfig.save();
                            c.getSource().sendSuccess(() -> v == 0 ? Component.translatable("command.hollowbell.ward_blocks_none")
                                    : Component.translatable("command.hollowbell.ward_blocks", v), true);
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
                .then(Commands.literal("volume").requires(OP)
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.volume_is", String.valueOf(HollowbellConfig.V.soundVolume)), false); return 1; })
                        .then(Commands.literal("off").executes(c -> set(c, () -> HollowbellConfig.V.soundVolume = 0f, "soundVolume", 0f)))
                        .then(Commands.argument("volume", FloatArgumentType.floatArg(0f, 2f))
                                .executes(c -> set(c, () -> HollowbellConfig.V.soundVolume = FloatArgumentType.getFloat(c, "volume"), "soundVolume", FloatArgumentType.getFloat(c, "volume")))))
                .then(Commands.literal("kill").requires(OP).executes(c -> {
                    int n = net.jj.hollowbell.world.FarOrders.killAll(c.getSource().getServer());
                    if (n == 0) return none(c);
                    c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_kill"), true);
                    return n;
                }))
                .then(Commands.literal("remove").requires(OP).executes(c -> {
                    var server = c.getSource().getServer();
                    WorldOne w = WorldOne.get(server);
                    int out = Away.get(server).count();
                    for (Away.Rec r : Away.get(server).all()) w.removed(server.overworld(), r.id);
                    Away.get(server).forgetAll();
                    if (allOf(c.getSource()).isEmpty() && out > 0) { c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.done_remove"), true); return out; }
                    return all(c, h -> { h.discard(); w.removed(server.overworld(), h.getUUID()); }, "remove");
                }))
                .then(Commands.literal("health").requires(OP)
                        .executes(c -> say(c, "health", HollowbellConfig.V.health))
                        .then(Commands.argument("health", FloatArgumentType.floatArg(10f, 1_000_000f))
                                .executes(c -> set(c, () -> HollowbellConfig.V.health = FloatArgumentType.getFloat(c, "health"), "health", FloatArgumentType.getFloat(c, "health")))))
                .then(Commands.literal("damage").requires(OP)
                        .executes(c -> say(c, "damage", HollowbellConfig.V.damageMultiplier))
                        .then(Commands.literal("mobs").executes(c -> say(c, "mobDamage", HollowbellConfig.V.mobDamage))
                                .then(Commands.argument("times", FloatArgumentType.floatArg(0f, 100f))
                                        .executes(c -> set(c, () -> HollowbellConfig.V.mobDamage = FloatArgumentType.getFloat(c, "times"), "mobDamage", FloatArgumentType.getFloat(c, "times")))))
                        .then(Commands.argument("multiplier", FloatArgumentType.floatArg(0f, 100f))
                                .executes(c -> set(c, () -> HollowbellConfig.V.damageMultiplier = FloatArgumentType.getFloat(c, "multiplier"), "damage", FloatArgumentType.getFloat(c, "multiplier")))))
                .then(toggle("griefing", "griefing", () -> HollowbellConfig.V.griefing, v -> HollowbellConfig.V.griefing = v))
                .then(toggle("shake", "screenShake", () -> HollowbellConfig.V.screenShake, v -> HollowbellConfig.V.screenShake = v))
                .then(Commands.literal("reload").requires(OP).executes(c -> {
                    boolean ok = HollowbellConfig.load();
                    c.getSource().sendSuccess(() -> Component.translatable(ok ? "command.hollowbell.reloaded" : "command.hollowbell.reload_bad"), true);
                    return ok ? 1 : 0;
                })));
    }

    /** "/hollowbell griefing [on|off]": bare says what it is; the old true/false form still works */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> toggle(String name, String key,
            java.util.function.BooleanSupplier get, java.util.function.Consumer<Boolean> put) {
        return Commands.literal(name).requires(OP)
                .executes(c -> say(c, key, get.getAsBoolean() ? "on" : "off"))
                .then(Commands.literal("on").executes(c -> set(c, () -> put.accept(true), key, "on")))
                .then(Commands.literal("off").executes(c -> set(c, () -> put.accept(false), key, "off")))
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(c -> { boolean v = BoolArgumentType.getBool(c, "value"); return set(c, () -> put.accept(v), key, v ? "on" : "off"); }));
    }

    private static int say(CommandContext<CommandSourceStack> c, String key, Object v) {
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.is", key, String.valueOf(v)), false);
        return 1;
    }

    private static float eggSize() {
        return net.minecraft.util.Mth.clamp(HollowbellConfig.V.spawnEggScale, HollowbellEntity.MIN_SCALE, HollowbellEntity.MAX_SCALE);
    }

    private static int bossbarSay(CommandContext<CommandSourceStack> c) {
        int r = HollowbellConfig.V.bossBarRange;
        c.getSource().sendSuccess(() -> Component.translatable(HollowbellConfig.V.bossBar ? "command.hollowbell.bossbar_on" : "command.hollowbell.bossbar_off",
                r > 0 ? String.valueOf(r) : (int) HollowbellEntity.barRange(1f) + " (auto)"), false);
        return 1;
    }

    /** /hollowbell sleep: the nearest one goes to sleep, or wakes up */
    private static int sleepToggle(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        if (h.asleep()) h.wakeUp(); else h.goToSleep();
        boolean now = h.asleep();
        c.getSource().sendSuccess(() -> Component.translatable(now ? "command.hollowbell.asleep" : "command.hollowbell.awake"), false);
        return 1;
    }

    /** /hollowbell spare: what's on your list */
    private static int spareList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var w = net.jj.hollowbell.world.BellWorld.get(p.server);
        var who = w.listOf(p.getUUID());
        var kinds = w.kindsOf(p.getUUID());
        if (who.isEmpty() && kinds.isEmpty()) { c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.spare_empty"), false); return 0; }
        List<String> names = new ArrayList<>();
        for (var u : who) names.add(w.isMob(u) ? Component.translatable("codex.hollowbell.list_one", w.nameOf(u)).getString() : w.nameOf(u));
        for (var k : kinds) names.add(Component.translatable("codex.hollowbell.list_kind", net.jj.hollowbell.world.BellWorld.kindsName(k)).getString());
        String all = String.join(", ", names);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.spare_list", all), false);
        return names.size();
    }

    private static int spareWho(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var w = net.jj.hollowbell.world.BellWorld.get(p.server);
        int n = 0;
        for (var e : net.minecraft.commands.arguments.EntityArgument.getEntities(c, "targets")) {
            if (e instanceof net.minecraft.world.entity.player.Player pl) {
                w.rememberName(pl.getUUID(), pl.getGameProfile().getName());
                if (on) w.addFriend(p.getUUID(), pl.getUUID()); else w.dropFriend(p.getUUID(), pl.getUUID());
                n++;
            } else if (e instanceof LivingEntity le && !(e instanceof HollowbellEntity)) {
                w.rememberMob(le.getUUID(), le.getName().getString());
                if (on) w.addFriend(p.getUUID(), le.getUUID()); else w.dropFriend(p.getUUID(), le.getUUID());
                n++;
            }
        }
        net.jj.hollowbell.net.CodexOrders.sendSafeList(p);
        final int f = n;
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.hollowbell.spare_added" : "command.hollowbell.spare_removed", f), false);
        return n;
    }

    private static int spareKind(CommandContext<CommandSourceStack> c, boolean on) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        var id = net.minecraft.commands.arguments.ResourceLocationArgument.getId(c, "type");
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(id);
        if (type.isEmpty()) { c.getSource().sendFailure(Component.translatable("command.hollowbell.spare_no_kind", id.toString())); return 0; }
        net.jj.hollowbell.net.CodexOrders.spareKind(p, type.get(), on);
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.hollowbell.spare_kind_added" : "command.hollowbell.spare_kind_removed",
                net.jj.hollowbell.world.BellWorld.kindsName(id.toString())), false);
        return 1;
    }

    /** /hollowbell goto and come: the nearest one, wherever he is (in the world, out of it, or in unloaded land) */
    private static int farGo(CommandContext<CommandSourceStack> c, @Nullable Vec3 to, @Nullable ServerPlayer follow) {
        ServerLevel l = follow != null ? follow.serverLevel() : c.getSource().getLevel();
        Vec3 from = follow != null ? follow.position() : c.getSource().getPosition();
        var t = net.jj.hollowbell.world.FarOrders.nearest(l, from);
        if (t == null) return none(c);
        Component said = net.jj.hollowbell.world.FarOrders.order(l, t, to != null ? to : follow.position(), follow, 0f);
        if (said == null) return none(c);
        c.getSource().sendSuccess(() -> said, false);
        return 1;
    }

    private static int paint(CommandContext<CommandSourceStack> c, int radius, boolean full) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        int n = net.jj.hollowbell.world.Painter.start(p, radius, full);
        c.getSource().sendSuccess(() -> Component.translatable(full ? "command.hollowbell.paint_full" : "command.hollowbell.paint_biome", radius, n), true);
        return n;
    }

    private static int tp(CommandContext<CommandSourceStack> c, int which) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Component said = net.jj.hollowbell.world.TakeMe.tp(p, which);
        c.getSource().sendSuccess(() -> said, false);
        return 1;
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
        int[] k = {0};
        for (HollowbellEntity h : all) {
            int num = ++k[0];
            c.getSource().sendSuccess(() -> Component.literal(num + ". ").append(Component.translatable("command.hollowbell.list_line", (int) h.getX(), (int) h.getY(), (int) h.getZ(),
                    String.format("%.2f", h.bellScale()), Component.translatable("mode.hollowbell." + h.variant()),
                    (int) h.healthNow(), (int) h.healthMax(), h.podsLeft(), h.rig.pods.length)), false);
        }
        long now = c.getSource().getLevel().getGameTime();
        for (Away.Rec r : out) awayLine(c, r, now, ++k[0]);
        return all.size() + out.size();
    }

    private static void awayLine(CommandContext<CommandSourceStack> c, Away.Rec r, long now) { awayLine(c, r, now, 0); }

    private static void awayLine(CommandContext<CommandSourceStack> c, Away.Rec r, long now, int num) {
        Vec3 s = r.spot(now);
        Component line = r.going
                ? Component.translatable("command.hollowbell.away_going", (int) s.x, (int) s.z, r.dim, (int) r.toX, (int) r.toZ, Math.max(1, r.minutesLeft(now)),
                        String.format("%.2f", r.scale), (int) r.hp, (int) r.hpMax)
                : Component.translatable("command.hollowbell.away_still", (int) s.x, (int) s.z, r.dim, String.format("%.2f", r.scale), (int) r.hp, (int) r.hpMax);
        c.getSource().sendSuccess(() -> num > 0 ? Component.literal(num + ". ").append(line) : line, false);
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

    /** /hollowbell natural: does the world keep one of him out there, and where things stand (the finder's words) */
    private static int naturalSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        Component msg = HollowbellConfig.V.oneInTheWorld
                ? Component.translatable("command.hollowbell.natural_is_on", net.jj.hollowbell.item.FinderItem.tell(over, c.getSource().getPosition()))
                : Component.translatable("command.hollowbell.natural_is_off");
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /** /hollowbell ground: where his ground is and how big */
    private static int groundSay(CommandContext<CommandSourceStack> c) {
        WorldOne w = WorldOne.get(c.getSource().getServer());
        Component msg = !w.homeClaimed() ? Component.translatable("command.hollowbell.ground_none")
                : Component.translatable("command.hollowbell.ground_is", w.homeX(), w.homeZ(), w.homeRadius());
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    /** /hollowbell ground new: a fresh ground in land the world hasn't made yet (the old one stays as it is) */
    private static int groundNew(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        net.minecraft.core.BlockPos at = w.newGround(over);
        boolean alive = w.aliveNow();
        c.getSource().sendSuccess(() -> Component.translatable(alive ? "command.hollowbell.ground_new" : "command.hollowbell.ground_new_next", at.getX(), at.getZ()), true);
        return 1;
    }

    private static int setNatural(CommandContext<CommandSourceStack> c, boolean on) {
        HollowbellConfig.V.oneInTheWorld = on;
        HollowbellConfig.save();
        c.getSource().sendSuccess(() -> Component.translatable(on ? "command.hollowbell.natural_on" : "command.hollowbell.natural_off"), true);
        return 1;
    }

    /** everyone standing, in the world and out of it */
    private static int countAll(CommandSourceStack src) {
        return allOf(src).size() + Away.get(src.getServer()).count();
    }

    private static int limitSay(CommandContext<CommandSourceStack> c) {
        int max = HollowbellConfig.V.maxInWorld;
        int now = countAll(c.getSource());
        c.getSource().sendSuccess(() -> max <= 0 ? Component.translatable("command.hollowbell.limit_none", now)
                : Component.translatable("command.hollowbell.limit_is", max, now), false);
        return 1;
    }

    private static int setLimit(CommandContext<CommandSourceStack> c) {
        int v = IntegerArgumentType.getInteger(c, "how many");
        HollowbellConfig.V.maxInWorld = v;
        HollowbellConfig.save();
        c.getSource().sendSuccess(() -> v == 0 ? Component.translatable("command.hollowbell.limit_set_none")
                : Component.translatable("command.hollowbell.limit_set", v), true);
        if (v > 0) WorldOne.limitNow(null, c.getSource().getServer().overworld());
        return 1;
    }

    private static int wardSay(CommandContext<CommandSourceStack> c) {
        ServerLevel over = c.getSource().getServer().overworld();
        WorldOne w = WorldOne.get(c.getSource().getServer());
        var at = w.wardSpot();
        Component msg;
        if (w.warding(over) && at != null)
            msg = Component.translatable("command.hollowbell.ward_awake", at.getX(), at.getZ(), (int) WorldOne.wardRange(), w.wardLeft(over) / 20);
        else if (w.wardRestLeft(over) > 0)
            msg = Component.translatable("command.hollowbell.ward_dark", w.wardRestLeft(over) / 20);
        else msg = Component.translatable("command.hollowbell.ward_none", (int) WorldOne.wardRange(), Math.max(1, HollowbellConfig.V.wardSeconds / 60),
                    Math.max(0, HollowbellConfig.V.wardRestSeconds / 60));
        c.getSource().sendSuccess(() -> msg, false);
        return 1;
    }

    private static int wardOn(CommandContext<CommandSourceStack> c) {
        ServerLevel lvl = c.getSource().getLevel();
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
        net.minecraft.core.BlockPos crown = null;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16)))
            if (lvl.getBlockState(p).is(net.jj.hollowbell.ModBlocks.CROWN)) { crown = p.immutable(); break; }
        if (crown == null) { c.getSource().sendFailure(Component.translatable("command.hollowbell.ward_no_crown")); return 0; }
        if (HollowbellConfig.V.wardBlocks <= 0) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.ward_no_blocks"));
            return 0;
        }
        WorldOne w = WorldOne.get(c.getSource().getServer());
        ServerLevel over = c.getSource().getServer().overworld();
        if (w.warding(over)) {
            var awake = w.wardSpot();
            c.getSource().sendFailure(Component.translatable("command.hollowbell.ward_already", awake.getX(), awake.getZ(), w.wardLeft(over) / 20));
            return 0;
        }
        if (w.wardRestLeft(over) > 0) {
            c.getSource().sendFailure(Component.translatable("command.hollowbell.ward_resting", w.wardRestLeft(over) / 20));
            return 0;
        }
        w.startWard(lvl, crown, Math.max(20, HollowbellConfig.V.wardSeconds * 20), Math.max(0, HollowbellConfig.V.wardRestSeconds * 20));
        for (HollowbellEntity e : lvl.getEntities(ModEntities.HOLLOWBELL, x -> !x.isRemoved()))
            if (e.warded(e.getX(), e.getZ())) e.pushedBackByWard();
        final int bx = crown.getX(), bz = crown.getZ();
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.ward_wakes", bx, bz, (int) WorldOne.wardRange()), true);
        return 1;
    }

    private static int areaSay(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        c.getSource().sendSuccess(() -> (h.bound() ? Component.translatable("command.hollowbell.area_is", h.boundRadius(), net.minecraft.util.Mth.floor(h.boundCentre().x), net.minecraft.util.Mth.floor(h.boundCentre().z))
                : Component.translatable("command.hollowbell.area_free")), false);
        return 1;
    }

    private static int areaBind(CommandContext<CommandSourceStack> c) {
        HollowbellEntity h = nearest(c.getSource());
        if (h == null) return none(c);
        double x = FloatArgumentType.getFloat(c, "x"), z = FloatArgumentType.getFloat(c, "z");
        int r = IntegerArgumentType.getInteger(c, "radius");
        h.bindTo(x, z, r);
        c.getSource().sendSuccess(() -> Component.translatable("command.hollowbell.area_set", r, net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z)), true);
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
