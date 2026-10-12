package net.jj.hollowbell.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.jj.hollowbell.ModItems;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.item.BellPower;
import net.jj.hollowbell.net.CodexOrders;
import net.jj.hollowbell.net.CodexPayload;
import net.jj.hollowbell.net.LookTrace;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The nearest one's state: who he goes for, his wind, grudges, home, cooldowns, freezing him, his speed, whether he
 * can be hurt, a glow, a name, forgiving, waking, and the players' own powers. The "/hollowbell set ..." forms work too.
 */
public final class OpsCommand {
    private OpsCommand() {}

    private static Component t(String key, Object... args) {
        return Component.translatable("command.hollowbell." + key, args);
    }

    private interface Act { int run(CommandContext<CommandSourceStack> c, HollowbellEntity h) throws CommandSyntaxException; }

    private static Command<CommandSourceStack> on(Act a) {
        return c -> {
            HollowbellEntity h = HollowbellCommand.nearest(c.getSource());
            if (h == null) { c.getSource().sendFailure(t("none")); return 0; }
            return a.run(c, h);
        };
    }

    private static int say(CommandContext<CommandSourceStack> c, Component what) {
        c.getSource().sendSuccess(() -> what, true);
        return 1;
    }

    /** adds every state command to /hollowbell */
    public static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTo(T root) {
        // (bare, or "help": the main commands, so nobody is left with "Unknown or incomplete command")
        root.executes(OpsCommand::help);
        root.then(Commands.literal("help").executes(OpsCommand::help));
        root.then(Commands.literal("target").requires(s -> s.hasPermission(2))
                .then(Commands.literal("none").executes(on((c, h) -> { h.clearHitList(); return say(c, t("target_none")); })))
                // what the player giving it is looking at, by the book's rules
                .then(Commands.literal("look").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    String said = CodexOrders.attackLooked(p, new CodexPayload(CodexPayload.ATTACK_THAT, 0), true);
                    return said.startsWith("look_going") ? 1 : 0;
                }))
                .then(Commands.argument("who", EntityArgument.entity()).executes(on((c, h) -> {
                    Entity e = EntityArgument.getEntity(c, "who");
                    // (a part of another giant stands for its giant)
                    if (!(LookTrace.ownerOf(e) instanceof LivingEntity le) || le == h) {
                        c.getSource().sendFailure(t("target_cant", e.getName()));
                        return 0;
                    }
                    h.sendAfter(le);
                    return say(c, t("target", e.getName()));
                }))));
        root.then(Commands.literal("windnow").requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> {
                    c.getSource().sendSuccess(() -> t("windnow_is", Math.round(h.mood().wind() * 100)), false);
                    return 1;
                }))
                .then(Commands.argument("share", FloatArgumentType.floatArg(0, 1)).executes(on((c, h) -> {
                    h.mood().setWind(FloatArgumentType.getFloat(c, "share"));
                    return say(c, t("windnow_is", Math.round(h.mood().wind() * 100)));
                }))));
        root.then(Commands.literal("grudge").requires(s -> s.hasPermission(2))
                .then(Commands.literal("clear").executes(on((c, h) -> { h.mood().settle(null); return say(c, t("grudge_clear")); })))
                .then(Commands.argument("player", EntityArgument.player())
                        .executes(on((c, h) -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            long now = Math.round(h.mood().sourOf(p.getUUID()) * 100);
                            c.getSource().sendSuccess(() -> t("grudge_is", p.getName(), now), false);
                            return 1;
                        }))
                        .then(Commands.argument("share", FloatArgumentType.floatArg(0, 1)).executes(on((c, h) -> {
                            ServerPlayer p = EntityArgument.getPlayer(c, "player");
                            h.mood().setSour(p.getUUID(), FloatArgumentType.getFloat(c, "share"));
                            return say(c, t("grudge_is", p.getName(), Math.round(h.mood().sourOf(p.getUUID()) * 100)));
                        })))));
        root.then(Commands.literal("forgive").requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> { h.forgiveAll(); return say(c, t("forgiven")); })));
        root.then(Commands.literal("home").requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> {
                    Vec3 at = h.home();
                    c.getSource().sendSuccess(() -> at == null ? t("home_none") : t("home_is", (int) at.x, (int) at.y, (int) at.z), false);
                    return 1;
                }))
                .then(Commands.literal("here").executes(on((c, h) -> {
                    Vec3 at = c.getSource().getPosition();
                    h.setHome(at);
                    return say(c, t("home_is", (int) at.x, (int) at.y, (int) at.z));
                }))));
        root.then(Commands.literal("cooldowns").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset").executes(on((c, h) -> { h.moves().clearCooldowns(); return say(c, t("cooldowns_reset")); }))));
        // the glass set's and the Stinger's waits, for players (not him)
        root.then(Commands.literal("powers").requires(s -> s.hasPermission(2))
                .then(Commands.literal("reset")
                        .executes(c -> powersReset(c, List.of(c.getSource().getPlayerOrException())))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(c -> powersReset(c, EntityArgument.getPlayers(c, "players"))))));
        root.then(onOff("freeze", HollowbellEntity::frozen, HollowbellEntity::setFrozen));
        root.then(onOff("invulnerable", Entity::isInvulnerable, Entity::setInvulnerable));
        root.then(onOff("glow", Entity::hasGlowingTag, Entity::setGlowingTag));
        root.then(Commands.literal("speed").requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> {
                    c.getSource().sendSuccess(() -> t("speed_is", String.format(Locale.ROOT, "%.2f", h.speedMul())), false);
                    return 1;
                }))
                .then(Commands.argument("times", FloatArgumentType.floatArg(0.1f, 5f)).executes(on((c, h) -> {
                    h.setSpeedMul(FloatArgumentType.getFloat(c, "times"));
                    return say(c, t("speed_is", String.format(Locale.ROOT, "%.2f", h.speedMul())));
                }))));
        root.then(Commands.literal("name").requires(s -> s.hasPermission(2))
                .then(Commands.literal("clear").executes(on((c, h) -> { h.setCustomName(null); return say(c, t("name_clear")); })))
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(on((c, h) -> {
                    String n = StringArgumentType.getString(c, "name");
                    h.setCustomName(Component.literal(n));
                    return say(c, t("name_set", n));
                }))));
        root.then(Commands.literal("wake").requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> { h.wakeUp(); return say(c, t("awake")); })));
        return root;
    }

    private static int help(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> t("help"), false);
        return 1;
    }

    private static int powersReset(CommandContext<CommandSourceStack> c, Collection<ServerPlayer> who) {
        for (ServerPlayer p : who) {
            p.getCooldowns().removeCooldown(ModItems.BELL_CHESTPLATE);
            p.getCooldowns().removeCooldown(ModItems.STINGER);
            BellPower.stop(p);
        }
        Component name = who.size() == 1 ? who.iterator().next().getName() : Component.literal(String.valueOf(who.size()));
        return say(c, t("powers_reset", name));
    }

    private interface Get { boolean get(HollowbellEntity h); }
    private interface Set { void set(HollowbellEntity h, boolean v); }

    private static LiteralArgumentBuilder<CommandSourceStack> onOff(String name, Get get, Set set) {
        return Commands.literal(name).requires(s -> s.hasPermission(2))
                .executes(on((c, h) -> { c.getSource().sendSuccess(() -> t(name + (get.get(h) ? "_is_on" : "_is_off")), false); return 1; }))
                .then(Commands.literal("on").executes(on((c, h) -> { set.set(h, true); return say(c, t(name + "_is_on")); })))
                .then(Commands.literal("off").executes(on((c, h) -> { set.set(h, false); return say(c, t(name + "_is_off")); })));
    }
}
