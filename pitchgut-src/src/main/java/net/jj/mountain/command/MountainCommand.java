package net.jj.mountain.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jj.mountain.ModEntities;
import net.jj.mountain.entity.MountainEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * /mountain summon [calm|hunter|guardian] [scale] [asleep] - he appears in front of you, facing you (1.0 = full size)
 * /mountain sleep                         - the nearest one lies down to sleep (again: wakes him up)
 * /mountain goo on|off                    - goo on or off for every Mountain (saved)
 * /mountain breakblocks on|off            - digging, crushing and trampling on or off (saved)
 * /mountain goto <pos>                    - walk the nearest one to a spot (he keeps fighting on the way)
 * /mountain attack <targets>              - send him after those players or creatures (nobody = forget them)
 * /mountain bossbar on|off                - his health and eye bars at the top of the screen
 * /mountain burrow <pos>                  - he goes under the ground and comes up there
 * /mountain book [lost]                   - whether the one book exists; 'lost' lets another be written
 * /mountain forgive                       - he forgets everyone who has ever hurt him
 * /mountain volume [0-200|off]            - how loud he is
 * /mountain digcooldown [days]            - how many days between orders to go under
 * /mountain where                         - whether he is loaded, and what the sum says while he is not
 * /mountain away on|off|blocks|now        - taking him out of the world while nobody is near him
 * /mountain unmake on|off|unmark <player>  - the last thing he does, and lifting the mark it leaves
 * /mountain wind on|off|seconds|grudge     - whether the book tires him, and how fast he sours
 * /mountain settle [player]                - full wind, and he holds nothing against them
 * /mountain shake on|off                  - the ground shaking under his feet
 * /mountain stay                          - make him stand still (again: let him wander)
 * /mountain breathe                       - make him take a full, hard breath now (in, hold, blast)
 * /mountain popeye [count]                - pop some of his eyes
 * /mountain swallow <player>              - swallow a player right now (straight in to fight his heart)
 * /mountain cough                         - make him cough up everyone inside him
 * /mountain remove                        - take every Mountain in this dimension away (no loot)
 */
public final class MountainCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("mountain").requires(s -> s.hasPermission(2))
                .then(Commands.literal("summon")
                        .executes(c -> summon(c, MountainEntity.CALM, 1.0f, false))
                        .then(Commands.literal("asleep").executes(c -> summon(c, MountainEntity.CALM, 1.0f, true)))
                        .then(Commands.argument("scale", FloatArgumentType.floatArg(0.02f, 2.0f)).executes(c -> summon(c, MountainEntity.CALM, FloatArgumentType.getFloat(c, "scale"), false)))
                        .then(kind("calm", MountainEntity.CALM))
                        .then(kind("hunter", MountainEntity.HUNTER))
                        .then(kind("guardian", MountainEntity.GUARDIAN)))
                .then(Commands.literal("sleep").executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    if (e.sleeping()) {
                        e.wakeUp(c.getSource().getEntity() instanceof ServerPlayer p ? p : null);
                        c.getSource().sendSuccess(() -> Component.literal("He's waking up."), false);
                    } else {
                        e.goToSleep();
                        c.getSource().sendSuccess(() -> Component.literal("He lies down to sleep. He'll lie down again whenever he's left alone for a while (type it again to wake him and keep him up)."), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("goo")
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("Goo is " + (net.jj.mountain.MountainConfig.V.gooTrail ? "on." : "off.")), false); return 1; })
                        .then(Commands.literal("on").executes(c -> setGoo(c, true)))
                        .then(Commands.literal("off").executes(c -> setGoo(c, false))))
                .then(Commands.literal("breakblocks")
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("Breaking blocks is " + (net.jj.mountain.MountainConfig.V.griefing ? "on." : "off.")), false); return 1; })
                        .then(Commands.literal("on").executes(c -> setGrief(c, true)))
                        .then(Commands.literal("off").executes(c -> setGrief(c, false))))
                .then(Commands.literal("volume")
                        .executes(c -> {
                            int pct = Math.round(net.jj.mountain.MountainConfig.V.soundVolume * 100);
                            c.getSource().sendSuccess(() -> Component.literal(pct <= 0 ? "He makes no sound." : "He's at " + pct + "% volume."), false);
                            return 1;
                        })
                        .then(Commands.literal("off").executes(c -> setVolume(c, 0f)))
                        .then(Commands.argument("percent", IntegerArgumentType.integer(0, 200)).executes(c ->
                                setVolume(c, IntegerArgumentType.getInteger(c, "percent") / 100f))))
                .then(Commands.literal("digcooldown")
                        .executes(c -> {
                            int days = net.jj.mountain.MountainConfig.V.burrowGapDays;
                            c.getSource().sendSuccess(() -> Component.literal(days <= 0
                                    ? "He'll go under whenever you ask."
                                    : "He'll only be sent under once every " + days + " Minecraft day" + (days == 1 ? "." : "s.")), false);
                            return 1;
                        })
                        .then(Commands.argument("days", IntegerArgumentType.integer(0, 1000)).executes(c -> {
                            int days = IntegerArgumentType.getInteger(c, "days");
                            net.jj.mountain.MountainConfig.V.burrowGapDays = days;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(days <= 0
                                    ? "He'll go under whenever you ask now."
                                    : "He'll only be sent under once every " + days + " Minecraft day" + (days == 1 ? "." : "s.")), true);
                            return 1;
                        })))
                .then(Commands.literal("unmake")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            var w = net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld());
                            c.getSource().sendSuccess(() -> Component.literal((V.unmake
                                    ? "The last thing he does is in the book. The hole is " + (V.unmakeRadius * 2)
                                      + " blocks across and the wave carries " + V.unmakeWave + "."
                                    : "The last thing he does has been taken out of the book.")
                                    + " " + w.markedCount() + " marked."), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> setUnmake(c, true)))
                        .then(Commands.literal("off").executes(c -> setUnmake(c, false)))
                        .then(Commands.literal("radius").then(Commands.argument("blocks", IntegerArgumentType.integer(24, 1200)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            net.jj.mountain.MountainConfig.V.unmakeRadius = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("The hole is " + (v * 2) + " blocks across."
                                    + (v > 400 ? " That is a lot of ground to make and then take away; it will take a few minutes to open." : "")), true);
                            return 1;
                        })))
                        .then(Commands.literal("wave").then(Commands.argument("blocks", IntegerArgumentType.integer(0, 8000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            net.jj.mountain.MountainConfig.V.unmakeWave = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("The wave behind it carries " + v + " blocks."), true);
                            return 1;
                        })))
                        .then(Commands.literal("unmark").then(Commands.argument("who", EntityArgument.player()).executes(c -> {
                            ServerPlayer who = EntityArgument.getPlayer(c, "who");
                            boolean was = net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld()).unmark(who.getUUID());
                            c.getSource().sendSuccess(() -> Component.literal(was
                                    ? "They are forgotten. A book will stay in their hands again."
                                    : "They were never marked."), true);
                            return was ? 1 : 0;
                        }))))
                .then(Commands.literal("limit")
                        .executes(c -> {
                            int max = net.jj.mountain.MountainConfig.V.maxMountains;
                            int now = 0;
                            for (ServerLevel l : c.getSource().getServer().getAllLevels())
                                for (MountainEntity ignored : l.getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved() && !x.isDeadOrDying())) now++;
                            final int out = now;
                            c.getSource().sendSuccess(() -> Component.literal(max <= 0
                                    ? "There is no limit on how many of him there can be. " + out + " standing."
                                    : "The world holds " + max + " of him at once. " + out + " standing. "
                                      + "Summon another and the oldest makes way."), false);
                            return 1;
                        })
                        .then(Commands.argument("how many", IntegerArgumentType.integer(0, 20)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "how many");
                            net.jj.mountain.MountainConfig.V.maxMountains = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v == 0
                                    ? "As many of him as you like now. Nothing is turned away and nothing is pushed out."
                                    : "The world holds " + v + " of him at once now. Summon another and the oldest makes way."), true);
                            return 1;
                        })))
                .then(Commands.literal("scars")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            MountainEntity m = nearest(c.getSource());
                            String his = m == null ? "" : " He is carrying " + m.scarredLegs() + " scarred legs and "
                                    + m.ruinedLegs() + " ruined ones, of " + m.legCount() + "; "
                                    + m.brokenLegs() + " are broken right now and " + m.crippleAt() + " puts him down for good.";
                            c.getSource().sendSuccess(() -> Component.literal((V.scars
                                    ? "What is broken and mended never comes back the same."
                                    : "He knits back whole every time.") + his), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> { net.jj.mountain.MountainConfig.V.scars = true; net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("He keeps what every fight does to him now."), true); return 1; }))
                        .then(Commands.literal("off").executes(c -> { net.jj.mountain.MountainConfig.V.scars = false; net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("He knits back whole from here on."), true); return 1; }))
                        .then(Commands.literal("clear").executes(c -> {
                            ServerLevel lvl = c.getSource().getLevel();
                            int n = 0;
                            for (MountainEntity e : lvl.getEntities(ModEntities.MOUNTAIN, x -> true)) { e.clearScars(); n++; }
                            final int done = n;
                            c.getSource().sendSuccess(() -> Component.literal(done == 0 ? "No mountain nearby." : "Every mark taken off him."), true);
                            return 1;
                        })))
                .then(Commands.literal("ward")
                        .executes(c -> {
                            ServerLevel over = c.getSource().getServer().overworld();
                            var w = net.jj.mountain.world.MountainWorld.get(over);
                            var at = w.wardSpot();
                            String msg;
                            if (w.warding(over) && at != null)
                                msg = "His heart is beating at " + at.getX() + ", " + at.getZ() + ". He is held off "
                                        + (int) net.jj.mountain.world.MountainWorld.wardRange() + " blocks for another "
                                        + (w.wardLeft(over) / 20) + " seconds.";
                            else if (w.wardRestLeft(over) > 0)
                                msg = "The heart is dark. It gathers itself for another " + (w.wardRestLeft(over) / 20) + " seconds.";
                            else msg = "No heart is beating. Knock on one and he is held off "
                                        + (int) net.jj.mountain.world.MountainWorld.wardRange() + " blocks for "
                                        + Math.max(1, net.jj.mountain.MountainConfig.V.wardSeconds / 60) + " minutes, then it sits dark for "
                                        + Math.max(0, net.jj.mountain.MountainConfig.V.wardRestSeconds / 60) + ".";
                            final String out = msg;
                            c.getSource().sendSuccess(() -> Component.literal(out), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> {
                            ServerLevel lvl = c.getSource().getLevel();
                            net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(c.getSource().getPosition());
                            net.minecraft.core.BlockPos heart = null;
                            for (net.minecraft.core.BlockPos p2 : net.minecraft.core.BlockPos.betweenClosed(at.offset(-16, -8, -16), at.offset(16, 8, 16)))
                                if (lvl.getBlockState(p2).is(net.jj.mountain.ModBlocks.HEART)) { heart = p2.immutable(); break; }
                            net.minecraft.core.BlockPos spot = heart == null ? at : heart;
                            var w = net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld());
                            w.startWard(lvl, spot, net.jj.mountain.block.HeartTrophyBlock.wardTicks(),
                                    net.jj.mountain.block.HeartTrophyBlock.restTicks());
                            if (heart != null) {
                                lvl.setBlock(heart, lvl.getBlockState(heart).setValue(net.jj.mountain.block.HeartTrophyBlock.MODE,
                                        net.jj.mountain.block.HeartTrophyBlock.WARDING), net.minecraft.world.level.block.Block.UPDATE_ALL);
                                lvl.scheduleTick(heart, net.jj.mountain.ModBlocks.HEART, net.jj.mountain.block.HeartTrophyBlock.wardTicks());
                            }
                            final int bx = spot.getX(), bz = spot.getZ();
                            for (MountainEntity e : lvl.getEntities(ModEntities.MOUNTAIN, x -> !x.isRemoved()))
                                if (e.warded(e.getX(), e.getZ())) e.pushedBackByHeart();
                            c.getSource().sendSuccess(() -> Component.literal("The heart beats at " + bx + ", " + bz
                                    + ". He is held off " + (int) net.jj.mountain.world.MountainWorld.wardRange() + " blocks."), true);
                            return 1;
                        }))
                        .then(Commands.literal("off").executes(c -> {
                            net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld()).forgetWard();
                            c.getSource().sendSuccess(() -> Component.literal("The heart is quiet. He can come back."), true);
                            return 1;
                        }))
                        .then(Commands.literal("minutes").then(Commands.argument("minutes", IntegerArgumentType.integer(1, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            net.jj.mountain.MountainConfig.V.wardSeconds = v * 60;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("His heart holds him off for " + v + " minutes once it is woken."), true);
                            return 1;
                        })))
                        .then(Commands.literal("rest").then(Commands.argument("minutes", IntegerArgumentType.integer(0, 1440)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "minutes");
                            net.jj.mountain.MountainConfig.V.wardRestSeconds = v * 60;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v == 0
                                    ? "The heart is ready again the moment it stops."
                                    : "The heart sits dark for " + v + " minutes afterwards."), true);
                            return 1;
                        })))
                        .then(Commands.literal("blocks").then(Commands.argument("blocks", IntegerArgumentType.integer(0, 20000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            net.jj.mountain.MountainConfig.V.wardBlocks = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v == 0 ? "His heart holds him off nowhere now." : "His heart holds him off " + v + " blocks."), true);
                            return 1;
                        }))))
                .then(Commands.literal("wind")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            MountainEntity m = nearest(c.getSource());
                            String his = m == null ? "" : " He is at " + Math.round(m.mood().wind() * 100) + "% of his wind.";
                            c.getSource().sendSuccess(() -> Component.literal((V.bookCosts
                                    ? "The book tires him: he gets his wind back over " + V.windSeconds + " seconds, and he sours at " + V.grudgeRate + " times the usual rate."
                                    : "The book costs him nothing.") + his), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> setCosts(c, true)))
                        .then(Commands.literal("off").executes(c -> setCosts(c, false)))
                        .then(Commands.literal("seconds").then(Commands.argument("seconds", IntegerArgumentType.integer(5, 3600)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "seconds");
                            net.jj.mountain.MountainConfig.V.windSeconds = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("He gets all his wind back in " + v + " seconds."), true);
                            return 1;
                        })))
                        .then(Commands.literal("grudge").then(Commands.argument("rate", FloatArgumentType.floatArg(0f, 10f)).executes(c -> {
                            float v = FloatArgumentType.getFloat(c, "rate");
                            net.jj.mountain.MountainConfig.V.grudgeRate = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal(v <= 0f ? "He will never sour on anyone." : "He sours at " + v + " times the usual rate."), true);
                            return 1;
                        }))))
                .then(Commands.literal("settle")
                        .executes(c -> settle(c, null))
                        .then(Commands.argument("who", EntityArgument.player()).executes(c -> settle(c, EntityArgument.getPlayer(c, "who")))))
                .then(Commands.literal("away")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            c.getSource().sendSuccess(() -> Component.literal(V.offscreenTravel
                                    ? "With nobody within " + net.jj.mountain.world.MountainWorld.awayRange(c.getSource().getServer())
                                      + " blocks he is taken out of the world and kept as a sum; he is put back within "
                                      + net.jj.mountain.world.MountainWorld.backRange(c.getSource().getServer()) + "."
                                    : "He always stays in the world."), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> setAway(c, true)))
                        .then(Commands.literal("off").executes(c -> setAway(c, false)))
                        .then(Commands.literal("blocks").then(Commands.argument("blocks", IntegerArgumentType.integer(0, 8000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "blocks");
                            net.jj.mountain.MountainConfig.V.awayBlocks = v;
                            net.jj.mountain.MountainConfig.save();
                            int draw = net.jj.mountain.MountainConfig.V.renderDistance;
                            c.getSource().sendSuccess(() -> Component.literal("He steps out of the world once nobody is within "
                                    + (int) net.jj.mountain.world.MountainWorld.awayRange(c.getSource().getServer()) + " blocks, and is put back within "
                                    + (int) net.jj.mountain.world.MountainWorld.backRange(c.getSource().getServer()) + "."
                                    + (v == 0 ? " That follows the game's own simulation distance."
                                        : v < draw ? " That is closer than the " + draw + " blocks he is drawn from, so you may see him wink out."
                                        : "")), true);
                            return 1;
                        })))
                        .then(Commands.literal("now").executes(c -> {
                            MountainEntity e = nearest(c.getSource());
                            if (e == null) return fail(c, "No mountain nearby.");
                            if (!e.stepAside()) return fail(c, "He can't step out right now.");
                            c.getSource().sendSuccess(() -> Component.literal("He is out of the world and kept as a sum."), true);
                            return 1;
                        })))
                .then(Commands.literal("where").executes(c -> {
                    var src = c.getSource();
                    var sl = src.getLevel();
                    var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
                    StringBuilder b = new StringBuilder();
                    java.util.List<MountainEntity> loaded = new java.util.ArrayList<>();
                    for (MountainEntity m : sl.getEntities(ModEntities.MOUNTAIN, m -> true)) loaded.add(m);
                    if (loaded.isEmpty()) b.append("None loaded in this world. ");
                    for (MountainEntity m : loaded)
                        b.append("loaded at ").append(Mth.floor(m.getX())).append(",").append(Mth.floor(m.getZ()))
                         .append(" tick=").append(m.tickCount).append(" doing=").append(m.doingNow())
                         .append(" hp=").append(Math.round(m.healthNow())).append("/").append(Math.round(m.healthMax()))
                         .append(" eyes=").append(m.eyesOpen()).append(" legs_broken=").append(m.brokenLegs()).append(". ");
                    for (MountainEntity m : loaded) {
                        double bd = Double.MAX_VALUE;
                        for (var pl : sl.players()) bd = Math.min(bd, pl.distanceToSqr(m));
                        b.append("Nearest player ").append(bd == Double.MAX_VALUE ? "none" : (int) Math.sqrt(bd) + " blocks")
                         .append(" (he steps out past ").append((int) net.jj.mountain.world.MountainWorld.awayRange(c.getSource().getServer())).append("). ");
                    }
                    var spot = w.where(sl);
                    b.append("World says ").append(spot == null ? "nowhere" : spot.getX() + "," + spot.getZ());
                    if (w.isAway()) b.append(" | out of the world, kept as a sum");
                    if (w.tripping()) b.append(" | walking to ").append(Mth.floor(w.tripEnd().x)).append(",").append(Mth.floor(w.tripEnd().z))
                            .append(", ").append(w.tripLeftMinutes(sl)).append(" min left, done=").append(w.tripDone(sl));
                    else b.append(" | no journey running");
                    final String out = b.toString();
                    src.sendSuccess(() -> Component.literal(out), false);
                    return 1;
                }))
                .then(Commands.literal("shake")
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("The ground shaking is " + (net.jj.mountain.MountainConfig.V.screenShake ? "on." : "off.")), false); return 1; })
                        .then(Commands.literal("on").executes(c -> setShake(c, true)))
                        .then(Commands.literal("off").executes(c -> setShake(c, false))))
                .then(Commands.literal("bossbar")
                        .executes(c -> { c.getSource().sendSuccess(() -> Component.literal("His bars at the top of the screen are " + (net.jj.mountain.MountainConfig.V.bossBar ? "on." : "off.")), false); return 1; })
                        .then(Commands.literal("on").executes(c -> setBar(c, true)))
                        .then(Commands.literal("off").executes(c -> setBar(c, false))))
                .then(Commands.literal("damage")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            c.getSource().sendSuccess(() -> Component.literal("His attack is " + V.attackDamage + " (10 is normal), and he hits anything that isn't a player " + V.mobDamage + " times as hard."), false);
                            return 1;
                        })
                        .then(Commands.argument("attack", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f, 1000f)).executes(c -> {
                            float v = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "attack");
                            net.jj.mountain.MountainConfig.V.attackDamage = v;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("His attack is " + v + " now (10 is normal)."), true);
                            return 1;
                        }))
                        .then(Commands.literal("mobs")
                                .then(Commands.argument("times", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(0f, 100f)).executes(c -> {
                                    float v = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "times");
                                    net.jj.mountain.MountainConfig.V.mobDamage = v;
                                    net.jj.mountain.MountainConfig.save();
                                    c.getSource().sendSuccess(() -> Component.literal("He now hits anything that isn't a player " + v + " times as hard."), true);
                                    return 1;
                                }))))
                .then(Commands.literal("health")
                        .executes(c -> {
                            MountainEntity e = nearest(c.getSource());
                            String now = e == null ? "" : " The one nearby has " + Math.round(e.healthNow()) + " of " + Math.round(e.healthMax()) + ".";
                            c.getSource().sendSuccess(() -> Component.literal("His health at full size is " + net.jj.mountain.MountainConfig.V.health + "." + now), false);
                            return 1;
                        })
                        .then(Commands.argument("health", com.mojang.brigadier.arguments.FloatArgumentType.floatArg(10f, 1000000f)).executes(c -> {
                            float v = com.mojang.brigadier.arguments.FloatArgumentType.getFloat(c, "health");
                            net.jj.mountain.MountainConfig.V.health = v;
                            net.jj.mountain.MountainConfig.save();
                            int n = 0;
                            for (MountainEntity e : c.getSource().getLevel().getEntities(ModEntities.MOUNTAIN, e -> !e.isDeadOrDying())) { e.refreshHealthFromConfig(); n++; }
                            int cnt = n;
                            c.getSource().sendSuccess(() -> Component.literal("His health at full size is " + v + " now" + (cnt > 0 ? " (" + cnt + " already out there updated)." : ".")), true);
                            return 1;
                        })))
                .then(Commands.literal("detail")
                        .executes(c -> {
                            var V = net.jj.mountain.MountainConfig.V;
                            c.getSource().sendSuccess(() -> Component.literal(V.simpleFarAway
                                    ? "Simpler drawing far away is on: past " + V.simpleFarAwayAt + " blocks he is drawn with bigger blocks so the game runs faster."
                                    : "Simpler drawing far away is off: he is always drawn in full."), false);
                            return 1;
                        })
                        .then(Commands.literal("on").executes(c -> setDetail(c, true)))
                        .then(Commands.literal("off").executes(c -> setDetail(c, false)))
                        .then(Commands.argument("distance", IntegerArgumentType.integer(20, 4000)).executes(c -> {
                            int v = IntegerArgumentType.getInteger(c, "distance");
                            net.jj.mountain.MountainConfig.V.simpleFarAwayAt = v;
                            net.jj.mountain.MountainConfig.V.simpleFarAway = true;
                            net.jj.mountain.MountainConfig.save();
                            c.getSource().sendSuccess(() -> Component.literal("Past " + v + " blocks he is drawn with bigger blocks now."), true);
                            return 1;
                        })))
                .then(Commands.literal("breakleg").executes(c -> breakLeg(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 39)).executes(c -> breakLeg(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("mendlimbs")
                        .then(Commands.literal("one").executes(c -> {
                            MountainEntity e = nearest(c.getSource());
                            if (e == null) return fail(c, "No mountain nearby.");
                            int k = e.mendOneLeg();
                            c.getSource().sendSuccess(() -> Component.literal(k < 0 ? "Nothing of his is waiting to knit." : "One leg knits back."), true);
                            return 1;
                        }))
                        .executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    e.mendAllLimbs();
                    c.getSource().sendSuccess(() -> Component.literal("Every leg and arm is whole again."), true);
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.literal("calm").executes(c -> mode(c, MountainEntity.CALM)))
                        .then(Commands.literal("hunter").executes(c -> mode(c, MountainEntity.HUNTER)))
                        .then(Commands.literal("guardian").executes(c -> mode(c, MountainEntity.GUARDIAN))))
                .then(Commands.literal("goto").then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) {                       // out of the world and kept as a sum: turn the sum instead
                        var sl = c.getSource().getLevel();
                        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
                        Vec3 to = Vec3Argument.getVec3(c, "pos");
                        if (!w.sendAway(sl, to, false)) return fail(c, "No mountain nearby.");
                        c.getSource().sendSuccess(() -> Component.literal("He's a long way out and on his way there — about "
                                + w.tripLeftMinutes(sl) + " minutes."), false);
                        return 1;
                    }
                    e.setGoal(Vec3Argument.getVec3(c, "pos"));   // he keeps whatever he is fighting and fights on the way
                    c.getSource().sendSuccess(() -> Component.literal(e.getTarget() != null ? "He's on his way, still fighting." : "He's on his way."), false);
                    return 1;
                })))
                .then(Commands.literal("book")
                        .executes(c -> {
                            boolean made = net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld()).bookExists();
                            c.getSource().sendSuccess(() -> Component.literal(made ? "The book has been written. There will not be another." : "The book has not been written yet."), false);
                            return 1;
                        })
                        .then(Commands.literal("lost").executes(c -> {
                            net.jj.mountain.world.MountainWorld.get(c.getSource().getServer().overworld()).forgetBook();
                            c.getSource().sendSuccess(() -> Component.literal("The book is counted lost. One more can be written."), true);
                            return 1;
                        })))
                .then(Commands.literal("forgive").executes(c -> {
                    ServerLevel lvl = c.getSource().getLevel();
                    int n = 0;
                    for (MountainEntity e : lvl.getEntities(ModEntities.MOUNTAIN, x -> true)) { n += e.grudgeCount(); e.forgiveAll(); }
                    final int total = n;
                    c.getSource().sendSuccess(() -> Component.literal(total == 0 ? "He wasn't holding anything against anyone." : "He forgets " + total + " of you."), true);
                    return 1;
                }))
                .then(Commands.literal("burrow").then(Commands.argument("pos", Vec3Argument.vec3()).executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    long left = e.forcedUnderLeft();
                    if (left > 0) return fail(c, "He won't dig in again for about " + (int) Math.ceil(left / 24000.0) + " days.");
                    if (!e.goUnderOnCommand(Vec3Argument.getVec3(c, "pos"))) return fail(c, "He can't go under right now.");
                    if (c.getSource().getEntity() instanceof net.minecraft.server.level.ServerPlayer sp) e.sentUnderBy(sp.getUUID());
                    c.getSource().sendSuccess(() -> Component.literal("He folds down into the ground."), true);
                    return 1;
                })))
                .then(Commands.literal("stay").executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) {
                        var sl = c.getSource().getLevel();
                        var w = net.jj.mountain.world.MountainWorld.get(sl.getServer().overworld());
                        if (!w.haltAway(sl)) return fail(c, "No mountain nearby.");
                        c.getSource().sendSuccess(() -> Component.literal("He stops where he is, a long way out."), false);
                        return 1;
                    }
                    e.setStay(!e.staying());
                    c.getSource().sendSuccess(() -> Component.literal(e.staying() ? "He stays where he is." : "He is free to wander again."), false);
                    return 1;
                }))
                .then(Commands.literal("breathe").executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    e.forceBreath();
                    return 1;
                }))
                .then(attackCommand())
                .then(Commands.literal("cleangoo").executes(c -> cleanGoo(c, 300))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(8, 3000)).executes(c -> cleanGoo(c, IntegerArgumentType.getInteger(c, "radius")))))
                .then(Commands.literal("popeye").executes(c -> pop(c, 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 300)).executes(c -> pop(c, IntegerArgumentType.getInteger(c, "count")))))
                .then(Commands.literal("swallow").then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    ServerPlayer p = EntityArgument.getPlayer(c, "player");
                    e.swallow(p);
                    return 1;
                })))
                .then(Commands.literal("cough").executes(c -> {
                    MountainEntity e = nearest(c.getSource());
                    if (e == null) return fail(c, "No mountain nearby.");
                    e.coughUp();
                    return 1;
                }))
                .then(Commands.literal("remove").executes(c -> {
                    ServerLevel lvl = c.getSource().getLevel();
                    List<? extends MountainEntity> all = lvl.getEntities(ModEntities.MOUNTAIN, e -> true);
                    all.forEach(Entity::discard);
                    int n = all.size();
                    // and the one being kept as a sum while he is out of the world
                    var w = net.jj.mountain.world.MountainWorld.get(lvl.getServer().overworld());
                    boolean sum = w.isAway();
                    w.forgetAway();
                    w.endTrip();
                    c.getSource().sendSuccess(() -> Component.literal("Removed " + n + " mountain" + (n == 1 ? "" : "s") + "."
                            + (sum ? " The one being kept as a sum is gone too." : "")), true);
                    return n;
                })));
    }

    private static int pop(CommandContext<CommandSourceStack> c, int n) {
        MountainEntity e = nearest(c.getSource());
        if (e == null) return fail(c, "No mountain nearby.");
        e.popEyes(n, c.getSource().getEntity());
        c.getSource().sendSuccess(() -> Component.literal(e.eyesOpen() + " eyes still open."), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    /**
     * /mountain cleangoo [radius]: removes every block of his goo in the loaded world around you (300 blocks by
     * default). Where the goo had eaten down into the ground the hole is filled back in with what's under it.
     */
    private static int cleanGoo(CommandContext<CommandSourceStack> c, int radius) {
        ServerLevel lvl = c.getSource().getLevel();
        if (lvl.dimension() == net.jj.mountain.innards.Innards.KEY) return fail(c, "Not in here: the goo is part of him.");
        Vec3 p = c.getSource().getPosition();
        int px = Mth.floor(p.x), pz = Mth.floor(p.z);
        java.util.List<net.minecraft.core.BlockPos> found = new java.util.ArrayList<>();
        for (int cx = (px - radius) >> 4; cx <= (px + radius) >> 4; cx++) {
            for (int cz = (pz - radius) >> 4; cz <= (pz + radius) >> 4; cz++) {
                double dx = Math.max(0, Math.abs((cx << 4) + 8 - px) - 8), dz = Math.max(0, Math.abs((cz << 4) + 8 - pz) - 8);
                if (dx * dx + dz * dz > (double) radius * radius || !lvl.hasChunk(cx, cz)) continue;
                net.minecraft.world.level.chunk.LevelChunk ch = lvl.getChunk(cx, cz);
                var secs = ch.getSections();
                for (int i = 0; i < secs.length; i++) {
                    var sec = secs[i];
                    if (sec.hasOnlyAir() || !sec.maybeHas(st -> st.is(net.jj.mountain.ModBlocks.GOO))) continue;
                    int by = net.minecraft.core.SectionPos.sectionToBlockCoord(ch.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
                        if (sec.getBlockState(x, y, z).is(net.jj.mountain.ModBlocks.GOO)) found.add(new net.minecraft.core.BlockPos((cx << 4) + x, by + y, (cz << 4) + z));
                }
            }
        }
        for (net.minecraft.core.BlockPos bp : found) {
            var st = lvl.getBlockState(bp);
            net.minecraft.world.level.block.state.BlockState put = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            if (st.getValue(net.jj.mountain.block.GooBlock.EATEN)) {
                var below = lvl.getBlockState(bp.below());
                put = below.isCollisionShapeFullBlock(lvl, bp.below()) && !below.hasBlockEntity() ? below : net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
                if (put.is(net.minecraft.world.level.block.Blocks.DIRT) && lvl.getBlockState(bp.above()).isAir()) put = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
            }
            lvl.setBlock(bp, put, 2);
        }
        int n = found.size();
        c.getSource().sendSuccess(() -> Component.literal(n == 0 ? "No goo within " + radius + " blocks." : "Cleaned up " + n + " blocks of goo."), true);
        return Math.max(1, n);
    }

    private static final String[] ATTACKS = net.jj.mountain.entity.MountainAttacks.NAMES;

    /**
     * /mountain attack <name>: makes the nearest one do that attack on the nearest player now
     * /mountain attack <targets>: sends him after those players or creatures until they are dead
     * /mountain attack nobody: he forgets the list and goes back to what he would do anyway
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> attackCommand() {
        var root = Commands.literal("attack");
        for (int i = 1; i < ATTACKS.length; i++) {
            final int id = i;
            root.then(Commands.literal(ATTACKS[i]).executes(c -> {
                MountainEntity e = nearest(c.getSource());
                if (e == null) return fail(c, "No mountain nearby.");
                if (e.sleeping()) return fail(c, "He's asleep. Use /mountain sleep to wake him first.");
                if (!e.forceAttack(id)) return fail(c, "He can't do that right now.");
                return 1;
            }));
        }
        root.then(Commands.literal("nobody").executes(c -> {
            MountainEntity e = nearest(c.getSource());
            if (e == null) return fail(c, "No mountain nearby.");
            e.clearHitList();
            c.getSource().sendSuccess(() -> Component.literal("He lets them be."), true);
            return 1;
        }));
        root.then(Commands.argument("targets", EntityArgument.entities()).executes(c -> {
            MountainEntity e = nearest(c.getSource());
            if (e == null) return fail(c, "No mountain nearby.");
            List<? extends Entity> list = List.copyOf(EntityArgument.getEntities(c, "targets"));
            e.sendAfter(list);
            int n = e.hitListSize();
            if (n == 0) return fail(c, "Nothing there he can go after.");
            String what = n == 1 ? list.get(0).getDisplayName().getString() : n + " of them";
            c.getSource().sendSuccess(() -> Component.literal("He's going after " + what + "."), true);
            return n;
        }));
        return root;
    }

    /** every Mountain within reach of whoever typed it */
    private static List<MountainEntity> all(CommandSourceStack src) {
        Vec3 p = src.getPosition();
        return src.getLevel().getEntitiesOfClass(MountainEntity.class, new AABB(p, p).inflate(1200));
    }

    private static MountainEntity nearest(CommandSourceStack src) {
        if (src.getEntity() instanceof ServerPlayer sp && net.jj.mountain.innards.Innards.isInside(sp)) {
            MountainEntity m = net.jj.mountain.innards.Innards.mountainOf(sp);      // typed from inside him
            if (m != null) return m;
        }
        Vec3 p = src.getPosition();
        List<MountainEntity> l = src.getLevel().getEntitiesOfClass(MountainEntity.class, new AABB(p, p).inflate(1200));
        MountainEntity best = null; double bd = Double.MAX_VALUE;
        for (MountainEntity e : l) { double d = e.distanceToSqr(p); if (d < bd) { bd = d; best = e; } }
        return best;
    }

    /** /mountain mode <calm|hunter|guardian>: switches the nearest one */
    private static int mode(CommandContext<CommandSourceStack> c, int variant) {
        MountainEntity e = nearest(c.getSource());
        if (e == null) return fail(c, "No mountain nearby.");
        e.setVariant(variant);
        String msg = switch (variant) {
            case MountainEntity.HUNTER -> "He's hunting players now.";
            case MountainEntity.GUARDIAN -> "He's a guardian now: he goes after monsters and leaves players alone, unless a player hurts him.";
            default -> "He's calm now: he wanders and only fights back.";
        };
        c.getSource().sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }

    /** summon calm|hunter|guardian [scale] [asleep] */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> kind(String name, int v) {
        return Commands.literal(name).executes(c -> summon(c, v, 1.0f, false))
                .then(Commands.literal("asleep").executes(c -> summon(c, v, 1.0f, true)))
                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.02f, 2.0f)).executes(c -> summon(c, v, FloatArgumentType.getFloat(c, "scale"), false))
                        .then(Commands.literal("asleep").executes(c -> summon(c, v, FloatArgumentType.getFloat(c, "scale"), true))));
    }

    private static int setGoo(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.gooTrail = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on ? "Goo is on: he pours and trails his goo again." : "Goo is off: no more goo from any Mountain (use /mountain cleangoo to clear what's already there)."), true);
        return 1;
    }

    private static int breakLeg(CommandContext<CommandSourceStack> c, int count) {
        MountainEntity e = nearest(c.getSource());
        if (e == null) return fail(c, "No mountain nearby.");
        int n = e.breakLegsNear(c.getSource().getPosition(), count);
        c.getSource().sendSuccess(() -> Component.literal(n + " of his legs give way (" + e.brokenLegs() + " broken now)"
                + (e.knockedDown() ? " and he comes down on his belly." : ".")), true);
        return 1;
    }

    private static int setDetail(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.simpleFarAway = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "Simpler drawing far away is on: he is drawn with bigger blocks in the distance so the game runs faster."
                : "Simpler drawing far away is off: he is always drawn in full."), true);
        return 1;
    }

    private static int setVolume(CommandContext<CommandSourceStack> c, float v) {
        net.jj.mountain.MountainConfig.V.soundVolume = v;
        net.jj.mountain.MountainConfig.save();
        int pct = Math.round(v * 100);
        c.getSource().sendSuccess(() -> Component.literal(pct <= 0 ? "He goes quiet." : "He's at " + pct + "% volume now."), true);
        return 1;
    }

    private static int setAway(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.offscreenTravel = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "He steps out of the world when nobody is near him, and is put back when somebody comes to where he should be."
                : "He always stays in the world now."), true);
        return 1;
    }

    private static int setUnmake(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.unmake = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on
                ? "The last thing he does is back in the book."
                : "The last thing he does has been taken out of the book."), true);
        return 1;
    }

    private static int setCosts(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.bookCosts = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on ? "The book tires him again." : "The book costs him nothing now."), true);
        return 1;
    }

    /** puts him right with somebody: full wind, and nothing held against them */
    private static int settle(CommandContext<CommandSourceStack> c, ServerPlayer who) {
        int n = 0;
        for (MountainEntity m : all(c.getSource())) { m.mood().settle(who == null ? null : who.getUUID()); n++; }
        final int done = n;
        c.getSource().sendSuccess(() -> Component.literal(done == 0 ? "No mountain nearby."
                : who == null ? "He has his wind back and holds nothing against anyone."
                : "He holds nothing against " + who.getGameProfile().getName() + " any more."), true);
        return done;
    }

    private static int setShake(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.screenShake = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on ? "The ground shakes under his feet again." : "The ground stays still."), true);
        return 1;
    }

    private static int setBar(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.bossBar = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on ? "His bars are back at the top of the screen." : "His bars are off."), true);
        return 1;
    }

    private static int setGrief(CommandContext<CommandSourceStack> c, boolean on) {
        net.jj.mountain.MountainConfig.V.griefing = on;
        net.jj.mountain.MountainConfig.save();
        c.getSource().sendSuccess(() -> Component.literal(on ? "Breaking blocks is on: his goo eats into the ground and his feet crush plants." : "Breaking blocks is off: he won't dig, crush or break anything."), true);
        return 1;
    }

    private static int summon(CommandContext<CommandSourceStack> c, int variant, float scale) { return summon(c, variant, scale, false); }

    private static int summon(CommandContext<CommandSourceStack> c, int variant, float scale, boolean asleep) {
        CommandSourceStack src = c.getSource();
        ServerLevel lvl = src.getLevel();
        MountainEntity e = ModEntities.MOUNTAIN.create(lvl);
        if (e == null) return fail(c, "Could not make him.");
        float yaw = src.getRotation().y;
        Vec3 look = Vec3.directionFromRotation(0, yaw);
        Vec3 p = src.getPosition().add(look.scale(150 * scale + 14));
        e.setVariant(variant);
        e.setMountainScale(scale);
        // He lands far out in front of you, and the bigger he is the further: without a ticket that chunk is not
        // running, so he would sit there never taking a tick and never being sent to anybody.
        net.minecraft.core.BlockPos at = net.minecraft.core.BlockPos.containing(p);
        MountainEntity.holdChunkAt(lvl, at, e.getId());
        lvl.getChunk(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4);          // load it first, or the ground reads as the bottom of the world
        int gy = e.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        e.moveTo(p.x, Math.max(gy, lvl.getMinBuildHeight() + 1), p.z, Mth.wrapDegrees(yaw + 180f), 0f);
        e.setYBodyRot(e.getYRot()); e.setYHeadRot(e.getYRot());
        if (asleep) e.startAsleep();
        lvl.addFreshEntityWithPassengers(e);
        String kind = variant == MountainEntity.HUNTER ? "hunting" : variant == MountainEntity.GUARDIAN ? "guardian" : "calm";
        c.getSource().sendSuccess(() -> Component.literal("The Mountain That Breathes has come (" + kind + ", size " + scale + (asleep ? ", asleep" : "") + ")."), true);
        return 1;
    }
}
