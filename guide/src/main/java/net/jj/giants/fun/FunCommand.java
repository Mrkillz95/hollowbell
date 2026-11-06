package net.jj.giants.fun;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) The command twins of the Fun and World tools pages, under /giants admin (operators).
 * A giant is named as "wreckback" (the nearest one to you), "wreckback.2" (the second as Live control numbers them)
 * or his id.
 */
public final class FunCommand {
    private FunCommand() {}

    public static void registerAll() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
    }

    private static final SuggestionProvider<CommandSourceStack> GIANTS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            out.add(g.key());
            int n = FunGiants.liveIds(c.getSource().getServer(), g).size();
            for (int i = 1; i <= Math.min(n, 9) && n > 1; i++) out.add(g.key() + "." + i);
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static final SuggestionProvider<CommandSourceStack> KINDS =
            (c, b) -> SharedSuggestionProvider.suggest(Fun.kinds(), b);

    private static final SuggestionProvider<CommandSourceStack> TAGS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (FunWorld.Tag t : FunWorld.get(c.getSource().getServer()).s.tags.values())
            out.add(t.name.contains(" ") ? "\"" + t.name + "\"" : t.name);
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static RequiredArgumentBuilder<CommandSourceStack, String> giant() {
        return Commands.argument("giant", StringArgumentType.word()).suggests(GIANTS);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> admin = Commands.literal("admin").requires(s -> s.hasPermission(
                AdminCommand.OPS));
        // watching and playing
        admin.then(Commands.literal("spectate")
                .then(Commands.literal("stop").executes(c -> say(c, Watch.stop(server(c), player(c)))))
                .then(giant().executes(c -> spectate(c, "free"))
                        .then(Commands.literal("eyes").executes(c -> spectate(c, "eyes")))));
        admin.then(Commands.literal("playas")
                .then(Commands.literal("stop").executes(c -> say(c, PlayAs.stop(server(c), player(c)))))
                .then(giant().executes(c -> {
                    LivingEntity e = find(c);
                    return e == null ? 0 : say(c, PlayAs.start(server(c), player(c), e));
                })));
        // a fight
        admin.then(Commands.literal("fight")
                .then(Commands.argument("a", StringArgumentType.word()).suggests(GIANTS)
                        .then(Commands.argument("b", StringArgumentType.word()).suggests(GIANTS)
                                .executes(c -> fight(c, false, 0.5))
                                .then(Commands.literal("here")
                                        .executes(c -> fight(c, false, 0.5))
                                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.01, 4))
                                                .executes(c -> fight(c, false, DoubleArgumentType.getDouble(c, "size")))))
                                .then(Commands.literal("look")
                                        .executes(c -> fight(c, true, 0.5))
                                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.01, 4))
                                                .executes(c -> fight(c, true, DoubleArgumentType.getDouble(c, "size"))))))));
        // names and titles
        admin.then(Commands.literal("name").then(giant()
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                    LivingEntity e = find(c);
                    String t = StringArgumentType.getString(c, "text");
                    return e == null ? 0 : say(c, Marks.name(server(c), who(c), e, t.equalsIgnoreCase("clear") ? "" : t,
                            t.equalsIgnoreCase("clear") ? "" : null, null, null));
                }))));
        admin.then(Commands.literal("title").then(giant()
                .then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
                    LivingEntity e = find(c);
                    String t = StringArgumentType.getString(c, "text");
                    return e == null ? 0 : say(c, Marks.name(server(c), who(c), e, null,
                            t.equalsIgnoreCase("clear") ? "" : t, null, null));
                }))));
        admin.then(Commands.literal("namecolour").then(giant()
                .then(Commands.argument("colour", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Marks.COLOURS, b))
                        .executes(c -> {
                            LivingEntity e = find(c);
                            return e == null ? 0 : say(c, Marks.name(server(c), who(c), e, null, null,
                                    StringArgumentType.getString(c, "colour"), null));
                        }))));
        admin.then(Commands.literal("nameshown").then(giant()
                .then(Commands.literal("on").executes(c -> shown(c, true)))
                .then(Commands.literal("off").executes(c -> shown(c, false)))));
        // (tags) his own name before his title on his bar ("Wreckback, Event Boss"), or the title alone ("Event Boss");
        // "tag": as his tag says
        admin.then(Commands.literal("showname").then(giant()
                .then(Commands.literal("on").executes(c -> ownName(c, true)))
                .then(Commands.literal("off").executes(c -> ownName(c, false)))
                .then(Commands.literal("tag").executes(c -> ownName(c, null)))));
        // loot
        LiteralArgumentBuilder<CommandSourceStack> loot = Commands.literal("loot");
        RequiredArgumentBuilder<CommandSourceStack, String> lg = giant().executes(FunCommand::lootSay)
                .then(Commands.literal("clear").executes(c -> loot(c, "clear", "", "")));
        for (String mode : List.of("add", "replace"))
            lg.then(Commands.literal(mode)
                    .then(Commands.literal("chest").executes(c -> loot(c, mode, "chest", "")))
                    .then(Commands.literal("table").then(Commands.argument("table", StringArgumentType.greedyString())
                            .executes(c -> loot(c, mode, "table", StringArgumentType.getString(c, "table"))))));
        admin.then(loot.then(lg));
        // giant tags
        admin.then(Commands.literal("tag")
                .then(Commands.literal("list").executes(FunCommand::tagList))
                .then(Commands.literal("make").then(Commands.argument("name", StringArgumentType.string())
                        .executes(c -> {
                            FunWorld.Tag t = new FunWorld.Tag();
                            t.name = StringArgumentType.getString(c, "name");
                            if (FunWorld.get(server(c)).tag(t.name) != null)
                                return fail(c, "There's already a tag called " + t.name + ".");
                            return say(c, Marks.saveTag(server(c), who(c), "", t));
                        })))
                .then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.string())
                        .suggests(TAGS)
                        .executes(c -> say(c, Marks.deleteTag(server(c), who(c), StringArgumentType.getString(c,
                                "name"))))))
                .then(Commands.literal("set").then(Commands.argument("name", StringArgumentType.string())
                        .suggests(TAGS)
                        .then(Commands.argument("field", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("health", "damage",
                                        "speed", "size", "title", "colour", "glow", "loot", "showname"), b))
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(FunCommand::tagSet)))))
                .then(Commands.literal("give").then(Commands.argument("name", StringArgumentType.string())
                        .suggests(TAGS)
                        // (tags) no giant named: the one you look at; "nearest"; "every <kind>"
                        .executes(c -> giveTo(c, "look"))
                        .then(Commands.literal("nearest").executes(c -> giveTo(c, "nearest")))
                        .then(Commands.literal("every").then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(KINDS).executes(c -> {
                                    FunWorld.Tag t = tagNamed(c);
                                    return t == null ? 0 : say(c, Marks.giveEvery(server(c), who(c),
                                            StringArgumentType.getString(c, "kind"), t));
                                })))
                        .then(giant().executes(c -> {
                            LivingEntity e = find(c);
                            if (e == null) return 0;
                            FunWorld.Tag t = FunWorld.get(server(c)).tag(StringArgumentType.getString(c, "name"));
                            if (t == null) return fail(c, "There's no tag called " + StringArgumentType.getString(c,
                                    "name") + ".");
                            return say(c, Marks.give(server(c), who(c), e, t, true));
                        }))))
                .then(Commands.literal("take").then(giant().executes(c -> {
                    LivingEntity e = find(c);
                    return e == null ? 0 : say(c, Marks.take(server(c), who(c), e, true));
                }))));
        // the biome brush, paint and erase
        admin.then(Commands.literal("brush")
                .executes(c -> brush(c, null, -1, null))
                .then(Commands.argument("giant", StringArgumentType.word())
                        .suggests((c, b) -> {
                            List<String> o = new ArrayList<>(Fun.kinds());
                            o.add("erase");
                            return SharedSuggestionProvider.suggest(o, b);
                        })
                        .executes(c -> brush(c, StringArgumentType.getString(c, "giant"), -1, null))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(Brush.MIN_R, Brush.MAX_R))
                                .executes(c -> brush(c, StringArgumentType.getString(c, "giant"),
                                        IntegerArgumentType.getInteger(c, "radius"), null))
                                .then(Commands.literal("biome").executes(c -> brush(c, StringArgumentType.getString(c,
                                        "giant"), IntegerArgumentType.getInteger(c, "radius"), "biome")))
                                .then(Commands.literal("full").executes(c -> brush(c, StringArgumentType.getString(c,
                                        "giant"), IntegerArgumentType.getInteger(c, "radius"), "full"))))));
        admin.then(Commands.literal("paint").then(Commands.argument("giant", StringArgumentType.word()).suggests(KINDS)
                .executes(c -> paint(c, 32, "biome", null))
                .then(Commands.argument("radius", IntegerArgumentType.integer(Brush.MIN_R, Brush.MAX_R))
                        .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "biome", null))
                        .then(Commands.literal("biome")
                                .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "biome", null))
                                .then(xz(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "biome", xz(c)))))
                        .then(Commands.literal("full")
                                .executes(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "full", null))
                                .then(xz(c -> paint(c, IntegerArgumentType.getInteger(c, "radius"), "full", xz(c))))))));
        admin.then(Commands.literal("erase")
                .then(Commands.literal("stop").executes(c -> Brush.stopErasing() ? say(c, AdminServer.Result.said(
                        List.of("Stopped erasing."))) : fail(c, "Nothing is erasing.")))
                .executes(c -> erase(c, 32, null))
                .then(Commands.argument("radius", IntegerArgumentType.integer(Brush.MIN_R, Brush.MAX_R))
                        .executes(c -> erase(c, IntegerArgumentType.getInteger(c, "radius"), null))
                        .then(xz(c -> erase(c, IntegerArgumentType.getInteger(c, "radius"), xz(c))))));
        // arenas and dens
        LiteralArgumentBuilder<CommandSourceStack> arena = Commands.literal("arena");
        for (String sz : List.of("small", "medium", "large"))
            arena.then(Commands.literal(sz).executes(c -> say(c, Builds.arena(server(c), who(c), player(c), sz))));
        admin.then(Commands.literal("build")
                .then(arena)
                .then(Commands.literal("den").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                        .executes(c -> say(c, Builds.den(server(c), who(c), player(c), StringArgumentType.getString(c,
                                "kind"), 0.5)))
                        .then(Commands.argument("size", DoubleArgumentType.doubleArg(0.01, 4))
                                .executes(c -> say(c, Builds.den(server(c), who(c), player(c),
                                        StringArgumentType.getString(c, "kind"), DoubleArgumentType.getDouble(c,
                                                "size")))))))
                .then(Commands.literal("list").executes(FunCommand::buildList))
                .then(Commands.literal("stop").executes(c -> say(c, Builds.stop(server(c), who(c)))))
                .then(Commands.literal("takedown").then(Commands.argument("n", IntegerArgumentType.integer(1))
                        .executes(c -> say(c, Builds.takeDown(server(c), who(c), IntegerArgumentType.getInteger(c,
                                "n")))))));
        // giant mixing
        RequiredArgumentBuilder<CommandSourceStack, String> sg = giant();
        for (String w : List.of("here", "look", "sea", "cave", "sky"))
            sg.then(Commands.literal(w).executes(c -> settle(c, w, null)));
        sg.then(Commands.literal("ground").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                .executes(c -> settle(c, "ground", StringArgumentType.getString(c, "kind")))));
        admin.then(Commands.literal("settle").then(sg));
        d.register(Commands.literal("giants").then(admin));
    }

    // helpers

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Server";
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    /** the giant named: "wreckback", "wreckback.2" or his id (null: said why) */
    private static LivingEntity find(CommandContext<CommandSourceStack> c) {
        return find(c, StringArgumentType.getString(c, "giant"));
    }

    private static LivingEntity find(CommandContext<CommandSourceStack> c, String word) {
        String w = word.replace('.', ' ');
        FunGiants.Found f = FunGiants.which(server(c), w, c.getSource().getLevel(), c.getSource().getPosition());
        if (f.giant() == null) fail(c, f.why());
        return f.giant();
    }

    private static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String s : r.lines())
            if (r.ok()) c.getSource().sendSuccess(() -> Component.literal(s), false);
            else c.getSource().sendFailure(Component.literal(s));
        if (!r.announce().isEmpty())
            Fun.tell(server(c), c.getSource().getEntity() instanceof ServerPlayer p ? p : null, r.announce().get(0));
        return r.ok() ? 1 : 0;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String why) {
        c.getSource().sendFailure(Component.literal(why));
        return 0;
    }

    private static int spectate(CommandContext<CommandSourceStack> c, String mode)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LivingEntity e = find(c);
        return e == null ? 0 : say(c, Watch.start(server(c), player(c), e, mode));
    }

    private static int fight(CommandContext<CommandSourceStack> c, boolean look, double size)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Fights.Side a = side(c, StringArgumentType.getString(c, "a"), size),
                b = side(c, StringArgumentType.getString(c, "b"), size);
        if (a == null || b == null) return 0;
        return say(c, Fights.stage(server(c), player(c), a, b, look));
    }

    /** a kind alone: a fresh one; kind.N or an id: one out there */
    private static Fights.Side side(CommandContext<CommandSourceStack> c, String word, double size) {
        Giant g = AdminServer.giant(word.toLowerCase(Locale.ROOT));
        if (g != null) return new Fights.Side(g, size, null);
        LivingEntity e = find(c, word);
        if (e == null) return null;
        return new Fights.Side(AdminServer.giant(FunGiants.kindOf(e)), FunGiants.sizeOf(e), e);
    }

    private static int shown(CommandContext<CommandSourceStack> c, boolean on) {
        LivingEntity e = find(c);
        return e == null ? 0 : say(c, Marks.name(server(c), who(c), e, null, null, null, on));
    }

    private static int ownName(CommandContext<CommandSourceStack> c, Boolean on) {
        LivingEntity e = find(c);
        return e == null ? 0 : say(c, Marks.name(server(c), who(c), e, null, null, null, null, on, on == null));
    }

    /** the tag the command names (null: said there's none) */
    private static FunWorld.Tag tagNamed(CommandContext<CommandSourceStack> c) {
        String n = StringArgumentType.getString(c, "name");
        FunWorld.Tag t = FunWorld.get(server(c)).tag(n);
        if (t == null) fail(c, "There's no tag called " + n + ". /giants admin tag list");
        return t;
    }

    /** (tags) a tag to the giant you look at (256 blocks at most) or the one nearest to you */
    private static int giveTo(CommandContext<CommandSourceStack> c, String to)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        FunWorld.Tag t = tagNamed(c);
        return t == null ? 0 : say(c, Marks.giveTo(server(c), who(c), player(c), to, t));
    }

    private static int loot(CommandContext<CommandSourceStack> c, String mode, String from, String table) {
        LivingEntity e = find(c);
        if (e == null) return 0;
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        return say(c, Marks.loot(server(c), who(c), p, e, mode, from, table));
    }

    private static int lootSay(CommandContext<CommandSourceStack> c) {
        LivingEntity e = find(c);
        if (e == null) return 0;
        FunWorld.Mark m = FunWorld.get(server(c)).markOf(e.getStringUUID());
        return say(c, AdminServer.Result.said(List.of(Marks.nameOf(e) + ": " + Marks.describe(m == null ? null
                : m.loot) + ".")));
    }

    private static int tagList(CommandContext<CommandSourceStack> c) {
        var tags = FunWorld.get(server(c)).s.tags.values();
        if (tags.isEmpty()) return say(c, AdminServer.Result.said(List.of("No giant tags. /giants admin tag make "
                + "<name>")));
        var wearing = Marks.wearing(server(c));
        List<String> out = new ArrayList<>();
        out.add("Giant tags:");
        for (FunWorld.Tag t : tags)
            out.add(t.name + ": health x" + plain(t.health) + ", damage x" + plain(t.damage) + ", speed x"
                    + plain(t.speed) + (t.size > 0 ? ", size " + plain(t.size) : "")
                    + (t.title.isEmpty() ? "" : ", title \"" + t.title + "\"" + (t.ownName ? "" : " alone"))
                    + (t.glow ? ", glowing" : "")
                    + (t.loot == null || t.loot.empty() ? "" : ", " + Marks.describe(t.loot)) + " ("
                    + wearing.getOrDefault(t.name.toLowerCase(Locale.ROOT), 0) + " wear it)");
        return say(c, AdminServer.Result.said(out));
    }

    private static String plain(double v) {
        return net.jj.giants.admin.AdminSetting.plain(v);
    }

    private static int tagSet(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = server(c);
        FunWorld.Tag was = FunWorld.get(s).tag(StringArgumentType.getString(c, "name"));
        if (was == null) return fail(c, "There's no tag called " + StringArgumentType.getString(c, "name") + ".");
        FunWorld.Tag t = was.copy();
        String field = StringArgumentType.getString(c, "field").toLowerCase(Locale.ROOT),
                value = StringArgumentType.getString(c, "value").strip();
        try {
            switch (field) {
                case "health" -> t.health = Double.parseDouble(value);
                case "damage" -> t.damage = Double.parseDouble(value);
                case "speed" -> t.speed = Double.parseDouble(value);
                case "size" -> t.size = Double.parseDouble(value);
                case "title" -> t.title = value.equalsIgnoreCase("clear") ? "" : value;
                case "colour", "color" -> t.colour = value.toLowerCase(Locale.ROOT);
                case "glow" -> t.glow = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
                // (tags) his own name before the title on his bar, or the title alone
                case "showname", "ownname" -> {
                    if (!value.matches("(?i)on|off|true|false"))
                        return fail(c, "showname is on (\"Wreckback, Event Boss\") or off (\"Event Boss\").");
                    t.ownName = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
                }
                case "loot" -> {
                    String[] w = value.split("\\s+");
                    if (w[0].equalsIgnoreCase("none")) t.loot = null;
                    else if (w.length >= 2 && (w[0].equals("add") || w[0].equals("replace"))) {
                        FunWorld.Loot l = new FunWorld.Loot();
                        l.mode = w[0];
                        if (w[1].equals("chest")) {
                            if (!(c.getSource().getEntity() instanceof ServerPlayer p))
                                return fail(c, "Only a player can point at a chest.");
                            List<String> items = Marks.copyChest(p);
                            if (items == null || items.isEmpty())
                                return fail(c, "Look at a chest with the tag's loot in it (8 blocks at most).");
                            l.items = items;
                        } else if (w[1].equals("table") && w.length >= 3) {
                            if (!Marks.tableExists(s, w[2])) return fail(c, "There's no loot table called " + w[2]
                                    + ".");
                            l.table = w[2];
                        } else return fail(c, "Loot is none, add chest, replace chest, add table <id> or replace "
                                + "table <id>.");
                        t.loot = l;
                    } else return fail(c, "Loot is none, add chest, replace chest, add table <id> or replace table "
                            + "<id>.");
                }
                default -> {
                    return fail(c, "A tag has health, damage, speed, size, title, colour, glow, loot and showname.");
                }
            }
        } catch (NumberFormatException e) {
            return fail(c, field + " has to be a number.");
        }
        return say(c, Marks.saveTag(s, who(c), was.name, t));
    }

    private static int brush(CommandContext<CommandSourceStack> c, String giant, int radius, String mode)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(c);
        Brush.Setting now = Brush.of(p);
        if (giant != null) {
            AdminServer.Result r = Brush.set(p, giant, radius > 0 ? radius : now.radius, mode != null ? mode
                    : now.mode);
            if (!r.ok()) return say(c, r);
        }
        Brush.give(p);
        return say(c, AdminServer.Result.said(List.of("The Biome Brush: " + Brush.words(Brush.of(p))
                + ". Right-click to paint, sneak + right-click to erase.")));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Integer> xz(
            com.mojang.brigadier.Command<CommandSourceStack> run) {
        return Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer()).executes(run));
    }

    private static int[] xz(CommandContext<CommandSourceStack> c) {
        return new int[] {IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "z")};
    }

    private static int paint(CommandContext<CommandSourceStack> c, int radius, String mode, int[] at)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(c);
        int x = at == null ? p.getBlockX() : at[0], z = at == null ? p.getBlockZ() : at[1];
        return say(c, Brush.paint(server(c), who(c), p, StringArgumentType.getString(c, "giant"), radius, mode, x, z));
    }

    private static int erase(CommandContext<CommandSourceStack> c, int radius, int[] at) {
        var pos = c.getSource().getPosition();
        int x = at == null ? (int) Math.floor(pos.x) : at[0], z = at == null ? (int) Math.floor(pos.z) : at[1];
        ServerPlayer p = c.getSource().getEntity() instanceof ServerPlayer sp ? sp : null;
        return say(c, Brush.erase(server(c), who(c), c.getSource().getLevel(), x, z, radius, p));
    }

    private static int buildList(CommandContext<CommandSourceStack> c) {
        List<String> out = new ArrayList<>();
        for (FunWorld.Build b : FunWorld.get(server(c)).s.builds) {
            if (b.state.equals("gone")) continue;
            out.add(b.n + ": " + (b.kind.equals("arena") ? b.style + " arena" : "den for the "
                    + AdminServer.nameOf(AdminServer.giant(b.giant))) + " at " + b.x + " " + b.y + " " + b.z + " ("
                    + b.state + (b.undo ? "" : ", can't be taken down") + ", by " + b.who + ")");
        }
        if (out.isEmpty()) out.add("Nothing built. /giants admin build arena small|medium|large, or build den <giant>");
        else out.add(0, "Builds:");
        return say(c, AdminServer.Result.said(out));
    }

    private static int settle(CommandContext<CommandSourceStack> c, String where, String kind)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        LivingEntity e = find(c);
        return e == null ? 0 : say(c, Settle.settle(server(c), who(c), player(c), e, where, kind));
    }

    /** grey for the help */
    static Component grey(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GRAY);
    }
}
