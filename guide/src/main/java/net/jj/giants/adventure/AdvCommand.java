package net.jj.giants.adventure;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.minigames.AdvMode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (Part 11, ADMIN_BRIDGE.md §28.7) The adventure commands, every one usable from a command block or a datapack
 * function (permission level 2): /giants admin script|path|area|wand|adventure, /giants hook, and /giants adventure
 * win|lose (what a map's hooks run to end its round).
 */
public final class AdvCommand {
    private AdvCommand() {}

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName()
                : c.getSource().getTextName();
    }

    /** the permission level of whoever runs it (a command block: 2, the console: 4) */
    static int level(CommandSourceStack s) {
        for (int i = 4; i > 0; i--) if (s.hasPermission(i)) return i;
        return 0;
    }

    private static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String l : r.lines()) {
            Component line = Component.literal(l);
            if (r.ok()) c.getSource().sendSuccess(() -> line, false);
            else c.getSource().sendFailure(line);
        }
        if (!r.announce().isEmpty()) AdminServer.announce(server(c),
                c.getSource().getEntity() instanceof ServerPlayer p ? p : null, r.announce());
        return r.ok() ? 1 : 0;
    }

    private static final SuggestionProvider<CommandSourceStack> GIANTS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) out.add(g.key());
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> SCRIPTS = (c, b) -> SharedSuggestionProvider.suggest(
            AdvData.get(c.getSource().getServer()).s.scripts.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> PATHS = (c, b) -> SharedSuggestionProvider.suggest(
            AdvData.get(c.getSource().getServer()).s.paths.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> AREAS = (c, b) -> SharedSuggestionProvider.suggest(
            AdvData.get(c.getSource().getServer()).s.areas.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> MAPS = (c, b) -> {
        List<String> out = new ArrayList<>(AdvData.get(c.getSource().getServer()).s.maps.keySet());
        out.add(0, AdvMaps.BUILT_IN);
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> EVENTS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (String[] e : Hooks.EVENTS) out.add(e[0]);
        for (String a : AdvData.get(c.getSource().getServer()).s.areas.keySet()) {
            out.add("enters." + a);
            out.add("leaves." + a);
        }
        return SharedSuggestionProvider.suggest(out, b);
    };
    private static final SuggestionProvider<CommandSourceStack> STEPS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (String[] v : Scripts.VERBS) out.add(v[0]);
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static LivingEntity find(CommandContext<CommandSourceStack> c) {
        String w = StringArgumentType.getString(c, "giant");
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        ServerLevel l = c.getSource().getLevel();
        Vec3 at = c.getSource().getPosition();
        FunGiants.Found f = FunGiants.which(server(c), w.replace('.', ' '), l, at);
        if (f.giant() == null) c.getSource().sendFailure(Component.literal(f.why()));
        return f.giant();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(s -> s.hasPermission(
                AdminCommand.OPS));
        // scripts
        admin.then(Commands.literal("script")
                .executes(c -> say(c, scriptList(server(c))))
                .then(Commands.literal("steps").executes(c -> say(c, steps())))
                .then(Commands.literal("show").then(Commands.argument("name", StringArgumentType.word()).suggests(SCRIPTS)
                        .executes(c -> say(c, show(server(c), StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("add").then(Commands.argument("name", StringArgumentType.word()).suggests(SCRIPTS)
                        .then(Commands.argument("step", StringArgumentType.greedyString()).suggests(STEPS)
                                .executes(c -> say(c, Adventure.scriptStep(server(c), who(c),
                                        StringArgumentType.getString(c, "name"), 0,
                                        StringArgumentType.getString(c, "step")))))))
                .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests(SCRIPTS)
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, AdvData.MOST_STEPS))
                                .then(Commands.argument("step", StringArgumentType.greedyString()).suggests(STEPS)
                                        .executes(c -> say(c, Adventure.scriptStep(server(c), who(c),
                                                StringArgumentType.getString(c, "name"),
                                                IntegerArgumentType.getInteger(c, "n"),
                                                StringArgumentType.getString(c, "step"))))))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(SCRIPTS)
                        .then(Commands.argument("n", IntegerArgumentType.integer(1, AdvData.MOST_STEPS))
                                .executes(c -> say(c, Adventure.scriptRemoveStep(server(c), who(c),
                                        StringArgumentType.getString(c, "name"),
                                        IntegerArgumentType.getInteger(c, "n")))))))
                .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word())
                        .suggests(SCRIPTS).executes(c -> say(c, Adventure.scriptDelete(server(c), who(c),
                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("give").then(Commands.argument("giant", StringArgumentType.word()).suggests(GIANTS)
                        .then(Commands.argument("name", StringArgumentType.word()).suggests(SCRIPTS).executes(c -> {
                            LivingEntity e = find(c);
                            return e == null ? 0 : say(c, Adventure.give(server(c), who(c), level(c.getSource()), e,
                                    StringArgumentType.getString(c, "name")));
                        }))))
                .then(Commands.literal("take").then(Commands.argument("giant", StringArgumentType.word()).suggests(GIANTS)
                        .executes(c -> {
                            LivingEntity e = find(c);
                            return e == null ? 0 : say(c, Adventure.take(server(c), who(c), e));
                        })))
                .then(Commands.literal("restart").then(Commands.argument("giant", StringArgumentType.word())
                        .suggests(GIANTS).executes(c -> {
                            LivingEntity e = find(c);
                            return e == null ? 0 : say(c, Adventure.restart(server(c), who(c), e));
                        }))));
        // paths and areas
        admin.then(Commands.literal("path")
                .executes(c -> say(c, pathList(server(c))))
                .then(Commands.literal("point").then(Commands.argument("name", StringArgumentType.word()).suggests(PATHS)
                        .executes(c -> {
                            Vec3 at = c.getSource().getPosition();
                            return say(c, Adventure.pathAdd(server(c), who(c), StringArgumentType.getString(c, "name"),
                                    c.getSource().getLevel().dimension().location().toString(), at.x, at.y, at.z));
                        })))
                .then(Commands.literal("undo").then(Commands.argument("name", StringArgumentType.word()).suggests(PATHS)
                        .executes(c -> say(c, Adventure.pathUndo(server(c), who(c),
                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(PATHS)
                        .executes(c -> say(c, Adventure.pathRemove(server(c), who(c),
                                StringArgumentType.getString(c, "name")))))));
        admin.then(Commands.literal("area")
                .executes(c -> say(c, areaList(server(c))))
                .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests(AREAS)
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> {
                                    BlockPos a = BlockPosArgument.getBlockPos(c, "from"),
                                            b = BlockPosArgument.getBlockPos(c, "to");
                                    return say(c, Adventure.areaSet(server(c), who(c),
                                            StringArgumentType.getString(c, "name"),
                                            c.getSource().getLevel().dimension().location().toString(), a, b));
                                })))))
                .then(Commands.literal("remove").then(Commands.argument("name", StringArgumentType.word()).suggests(AREAS)
                        .executes(c -> say(c, Adventure.areaRemove(server(c), who(c),
                                StringArgumentType.getString(c, "name")))))));
        admin.then(Commands.literal("wand")
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("path", "area", "map"), b))
                        .then(Commands.argument("name", StringArgumentType.word()).executes(c -> say(c, Wand.set(
                                c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "mode"),
                                StringArgumentType.getString(c, "name")))))));
        // adventure maps
        admin.then(Commands.literal("adventure")
                .executes(c -> say(c, mapList(server(c))))
                .then(Commands.literal("save").then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos()).executes(c -> say(c,
                                        AdvMaps.save(server(c), c.getSource().getPlayer(),
                                                StringArgumentType.getString(c, "name"),
                                                BlockPosArgument.getBlockPos(c, "from"),
                                                BlockPosArgument.getBlockPos(c, "to"))))))))
                .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.word()).suggests(MAPS)
                        .executes(c -> say(c, AdvMaps.delete(server(c), who(c),
                                StringArgumentType.getString(c, "name"))))))
                .then(Commands.literal("play").then(Commands.argument("name", StringArgumentType.word()).suggests(MAPS)
                        .executes(c -> say(c, Adventure.play(server(c), who(c), StringArgumentType.getString(c, "name"),
                                false, 0)))
                        .then(Commands.argument("who", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("open", "everyone"), b))
                                .executes(c -> play(c, 0))
                                .then(Commands.argument("rounds", IntegerArgumentType.integer(1, 5))
                                        .executes(c -> play(c, IntegerArgumentType.getInteger(c, "rounds")))))))
                .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.word()).suggests(MAPS)
                        .then(Commands.argument("what", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("goal", "timeup",
                                        "winarea"), b))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(c -> say(c, Adventure.mapSet(server(c), who(c),
                                                StringArgumentType.getString(c, "name"),
                                                StringArgumentType.getString(c, "what"),
                                                StringArgumentType.getString(c, "value")))))))));
        // hooks (operators and command blocks)
        LiteralArgumentBuilder<CommandSourceStack> hook = Commands.literal("hook").requires(s -> s.hasPermission(
                AdminCommand.OPS));
        hook.executes(c -> say(c, hookList(server(c))))
                .then(Commands.literal("add").then(Commands.argument("target", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> out = new ArrayList<>(List.of("all"));
                            for (Giant g : AdminServer.giants()) out.add(g.key());
                            return SharedSuggestionProvider.suggest(out, b);
                        })
                        .then(Commands.argument("event", StringArgumentType.word()).suggests(EVENTS)
                                .then(Commands.argument("command", StringArgumentType.greedyString())
                                        .executes(c -> say(c, Adventure.hookAdd(server(c), who(c), level(c.getSource()),
                                                StringArgumentType.getString(c, "target"),
                                                StringArgumentType.getString(c, "event"),
                                                StringArgumentType.getString(c, "command"))))))))
                .then(Commands.literal("remove").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(c -> say(c, Adventure.hookRemove(server(c), who(c),
                                IntegerArgumentType.getInteger(c, "id"))))))
                .then(Commands.literal("test").then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(c -> say(c, Adventure.hookTest(server(c), c.getSource().getLevel(),
                                c.getSource().getPosition(), IntegerArgumentType.getInteger(c, "id"))))))
                .then(Commands.literal("events").executes(c -> say(c, events())))
                .then(Commands.literal("scores")
                        .then(Commands.literal("on").executes(c -> say(c, Adventure.scores(server(c), who(c), true))))
                        .then(Commands.literal("off").executes(c -> say(c, Adventure.scores(server(c), who(c), false)))));
        // what a map's hooks run to end its round
        LiteralArgumentBuilder<CommandSourceStack> adv = Commands.literal("adventure").requires(s -> s.hasPermission(
                AdminCommand.OPS));
        adv.then(Commands.literal("win").executes(c -> say(c, AdvMode.end(true, "")))
                        .then(Commands.argument("words", StringArgumentType.greedyString())
                                .executes(c -> say(c, AdvMode.end(true, StringArgumentType.getString(c, "words"))))))
                .then(Commands.literal("lose").executes(c -> say(c, AdvMode.end(false, "")))
                        .then(Commands.argument("words", StringArgumentType.greedyString())
                                .executes(c -> say(c, AdvMode.end(false, StringArgumentType.getString(c, "words"))))));
        d.register(Commands.literal("giants").then(admin).then(hook).then(adv));
    }

    private static int play(CommandContext<CommandSourceStack> c, int rounds) {
        String w = StringArgumentType.getString(c, "who");
        if (!w.equals("open") && !w.equals("everyone"))
            return say(c, AdminServer.Result.fail("Say open (players choose) or everyone (everyone online is taken)."));
        return say(c, Adventure.play(server(c), who(c), StringArgumentType.getString(c, "name"), w.equals("everyone"),
                rounds));
    }

    static AdminServer.Result scriptList(MinecraftServer server) {
        AdvData d = AdvData.get(server);
        List<String> l = new ArrayList<>();
        l.add(d.s.scripts.isEmpty() ? "No scripts yet: /giants admin script add <name> <step> (steps: /giants admin"
                + " script steps)." : "Scripts:");
        for (var e : d.s.scripts.entrySet()) l.add("  " + e.getKey() + ": " + e.getValue().steps.size() + " steps");
        for (var e : d.s.runs.entrySet()) {
            LivingEntity g = FunGiants.find(server, e.getKey());
            l.add("  " + (g == null ? e.getValue().kind + " (not loaded)" : FunGiants.label(server, g)) + " runs "
                    + e.getValue().script + ": " + Scripts.where(d.s, e.getValue()));
        }
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result show(MinecraftServer server, String name) {
        AdvData.Script sc = AdvData.get(server).s.scripts.get(name.toLowerCase(java.util.Locale.ROOT));
        if (sc == null) return AdminServer.Result.fail("There's no script called " + name + ".");
        List<String> l = new ArrayList<>();
        l.add("Script " + name + ":");
        for (int i = 0; i < sc.steps.size(); i++) l.add("  " + (i + 1) + ". " + sc.steps.get(i));
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result steps() {
        List<String> l = new ArrayList<>();
        l.add("Script steps (one a line, done in order):");
        for (String[] v : Scripts.VERBS) l.add("  " + v[0] + (v[1].isEmpty() ? "" : " " + v[1]) + ": " + v[2]);
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result events() {
        List<String> l = new ArrayList<>();
        l.add("Hook events (the command runs once each time; @s = the giant):");
        for (String[] v : Hooks.EVENTS) l.add("  " + v[0] + ": " + v[1]);
        l.add("In the command: {giant} {name} {id} {x} {y} {z} {health} {player} {area}.");
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result pathList(MinecraftServer server) {
        AdvData d = AdvData.get(server);
        List<String> l = new ArrayList<>();
        l.add(d.s.paths.isEmpty() ? "No paths yet: /giants admin wand path <name>, or /giants admin path point <name>."
                : "Paths:");
        for (var e : d.s.paths.entrySet()) l.add("  " + e.getKey() + ": " + e.getValue().pts.size() + " points ("
                + e.getValue().dim + ")");
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result areaList(MinecraftServer server) {
        AdvData d = AdvData.get(server);
        List<String> l = new ArrayList<>();
        l.add(d.s.areas.isEmpty() ? "No areas yet: /giants admin wand area <name>, or /giants admin area set." : "Areas:");
        for (var e : d.s.areas.entrySet()) l.add("  " + e.getKey() + ": " + e.getValue().sx() + " x "
                + e.getValue().sy() + " x " + e.getValue().sz() + " at " + Math.min(e.getValue().x1, e.getValue().x2)
                + " " + Math.min(e.getValue().y1, e.getValue().y2) + " " + Math.min(e.getValue().z1, e.getValue().z2));
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result hookList(MinecraftServer server) {
        AdvData d = AdvData.get(server);
        List<String> l = new ArrayList<>();
        l.add(d.s.hooks.isEmpty() ? "No hooks yet: /giants hook add <giant|tag> <event> <command> (events: /giants hook"
                + " events)." : "Hooks:");
        for (AdvData.Hook h : d.s.hooks) l.add("  " + h.id + ". " + h.target + " " + h.event + ": " + h.command
                + " (by " + h.by + ", level " + h.level + ", ran " + h.fired + "x)");
        if (d.s.scores) l.add("Scoreboard on: giants_health, giants_alive, giants_events.");
        return AdminServer.Result.said(l);
    }

    static AdminServer.Result mapList(MinecraftServer server) {
        AdvData d = AdvData.get(server);
        List<String> l = new ArrayList<>();
        l.add("Adventure maps (play: /giants admin adventure play <name> [open|everyone]):");
        l.add("  " + AdvMaps.BUILT_IN + ": " + AdvMaps.title(AdvMaps.BUILT_IN) + " (built in)");
        for (AdvData.MapInfo m : d.s.maps.values()) l.add("  " + m.name + ": " + m.sx + " x " + m.sy + " x " + m.sz
                + ", " + m.giants + " giants, by " + m.by + ". Goal: " + m.goal);
        if (AdvMaps.saving()) l.add("A map is being saved now.");
        return AdminServer.Result.said(l);
    }
}
