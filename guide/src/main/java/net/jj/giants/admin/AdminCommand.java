package net.jj.giants.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * /giants admin (operators, permission level 2): get, set and reset any giant's admin settings for this world, list
 * them, the change log, presets, export and import (ADMIN_BRIDGE.md). It joins the giants' own /giants the same way
 * /giants performance does. Also /giants adminbook.
 */
public final class AdminCommand {
    private AdminCommand() {}

    public static final int OPS = 2;

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("giants")
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(OPS))
                        .executes(c -> say(c, AdminServer.Result.said(HELP)))
                        .then(Commands.literal("get")
                                .then(giant("giant", false)
                                        .executes(c -> say(c, AdminServer.get(server(c), word(c, "giant"), null)))
                                        .then(setting()
                                                .executes(c -> say(c, AdminServer.get(
                                                        server(c), word(c, "giant"), word(c, "setting")))))))
                        .then(Commands.literal("set")
                                .then(giant("giant", false)
                                        .then(setting()
                                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                                        .suggests(VALUES)
                                                        .executes(AdminCommand::set)))))
                        .then(Commands.literal("reset")
                                .then(giant("giant", true)
                                        .executes(c -> say(c, AdminServer.reset(
                                                server(c), who(c), word(c, "giant"), null)))
                                        .then(setting()
                                                .executes(c -> say(c, AdminServer.reset(
                                                        server(c), who(c), word(c, "giant"), word(c, "setting")))))))
                        .then(Commands.literal("list")
                                .then(giant("giant", false)
                                        .executes(c -> say(c, AdminServer.list(server(c), word(c, "giant"), null)))
                                        .then(Commands.argument("category", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        AdminSetting.CATEGORIES, b))
                                                .executes(c -> say(c, AdminServer.list(
                                                        server(c), word(c, "giant"), word(c, "category")))))))
                        .then(Commands.literal("log")
                                .executes(c -> say(c, AdminServer.log(server(c), 10)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, AdminWorld.MOST_LOG))
                                        .executes(c -> say(c, AdminServer.log(
                                                server(c), IntegerArgumentType.getInteger(c, "count"))))))
                        .then(Commands.literal("preset")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(presetNames(), b))
                                        .executes(AdminCommand::preset)))
                        .then(Commands.literal("savepreset")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(c -> say(c, AdminPresets.save(server(c), who(c), whoId(c),
                                                StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("deletepreset")
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests((c, b) -> {
                                            List<String> out = new ArrayList<>();
                                            for (AdminPresets.Saved s : AdminPresets.list()) out.add(s.name());
                                            return SharedSuggestionProvider.suggest(out, b);
                                        })
                                        .executes(c -> say(c, AdminPresets.delete(who(c), whoId(c),
                                                StringArgumentType.getString(c, "name"))))))
                        .then(Commands.literal("export").executes(AdminCommand::export))
                        .then(Commands.literal("import")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(AdminCommand::importText)))
                        .then(Commands.literal("confirm").executes(c -> say(c,
                                confirm(server(c), holder(c), who(c)))))
                        // Part 2B: locks, undo, snapshots, moderators, settings sync
                        .then(Commands.literal("lock")
                                .then(giant("giant", false)
                                        .then(setting().executes(c -> say(c, AdminServer.lock(server(c), who(c),
                                                whoId(c), word(c, "giant"), word(c, "setting")))))))
                        .then(Commands.literal("unlock")
                                .then(giant("giant", false)
                                        .then(setting().executes(c -> say(c, AdminServer.unlock(server(c), who(c),
                                                word(c, "giant"), word(c, "setting")))))))
                        .then(Commands.literal("locks").executes(c -> say(c, AdminServer.locks(server(c)))))
                        .then(Commands.literal("undo")
                                .executes(c -> undo(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, AdminWorld.MOST_LOG))
                                        .executes(c -> undo(c, IntegerArgumentType.getInteger(c, "count")))))
                        .then(Commands.literal("snapshot")
                                .executes(AdminCommand::snapshots)
                                .then(Commands.literal("restore")
                                        .then(Commands.argument("number", IntegerArgumentType.integer(1,
                                                        AdminWorld.MOST_SNAPSHOTS))
                                                .executes(AdminCommand::restore))))
                        .then(Commands.literal("moderator")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        c.getSource().getOnlinePlayerNames(), b))
                                                .executes(c -> say(c, AdminRoles.set(server(c), who(c),
                                                        word(c, "player"), true)))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", StringArgumentType.word())
                                                .suggests((c, b) -> {
                                                    List<String> out = new ArrayList<>();
                                                    for (AdminWorld.Mod m : AdminWorld.get(server(c)).mods())
                                                        out.add(m.name());
                                                    return SharedSuggestionProvider.suggest(out, b);
                                                })
                                                .executes(c -> say(c, AdminRoles.set(server(c), who(c),
                                                        word(c, "player"), false))))))
                        .then(Commands.literal("moderators").executes(c -> say(c, AdminRoles.list(server(c)))))
                        .then(Commands.literal("sync")
                                .executes(c -> say(c, AdminServer.Result.said(AdminSync.status())))
                                .then(Commands.literal("status")
                                        .executes(c -> say(c, AdminServer.Result.said(AdminSync.status()))))
                                .then(Commands.literal("now").executes(c -> {
                                    AdminSync.Outcome o = AdminSync.run(server(c), true);
                                    return say(c, new AdminServer.Result(o.ran(), o.lines(), o.ran()
                                            ? List.of(who(c) + " applied the settings sync file ("
                                                    + o.applied() + " server defaults).")
                                            : List.of()));
                                }))))
                .then(Commands.literal("adminbook")
                        .requires(s -> s.hasPermission(OPS))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            AdminBookItem.give(p);
                            c.getSource().sendSuccess(() -> Component.literal("Here is the Giants Admin Book."), false);
                            return 1;
                        })
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        c.getSource().getOnlinePlayerNames(), b))
                                .executes(c -> {
                                    ServerPlayer p = AdminRoles.online(server(c), word(c, "player"));
                                    if (p == null)
                                        return say(c, AdminServer.Result.fail("Pick a player who is online."));
                                    AdminBookItem.give(p);
                                    String n = p.getGameProfile().getName();
                                    boolean opens = AdminBook.allowed(p);
                                    return say(c, AdminServer.Result.said(List.of("Gave " + n
                                            + " a Giants Admin Book." + (opens ? "" : " It only opens for admins and"
                                                    + " moderators: /giants admin moderator add " + n + "."))));
                                }))));
    }

    private static List<String> presetNames() {
        List<String> out = new ArrayList<>(AdminTransfer.presets());
        for (AdminPresets.Saved s : AdminPresets.list()) out.add(s.name());
        return out;
    }

    /** the player's UUID ("" for the console) */
    private static String whoId(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() != null ? c.getSource().getPlayer().getStringUUID() : "";
    }

    /** /giants admin undo [n]: the last change, or the last n as one */
    private static int undo(CommandContext<CommandSourceStack> c, int n) {
        List<AdminWorld.Change> log = AdminWorld.get(server(c)).log();
        if (log.isEmpty()) return say(c, AdminServer.Result.fail("No changes yet in this world."));
        int i = Math.max(0, log.size() - n);
        return say(c, n == 1
                ? AdminUndo.undo(server(c), who(c), i, log.get(i).time())
                : AdminUndo.undoBackTo(server(c), who(c), i, log.get(i).time()));
    }

    /** /giants admin snapshot: the snapshots kept, newest first, numbered for restore */
    private static int snapshots(CommandContext<CommandSourceStack> c) {
        List<AdminWorld.Snapshot> all = AdminWorld.get(server(c)).snapshots();
        if (all.isEmpty())
            return say(c, AdminServer.Result.said(List.of("No snapshots yet. One is kept before every preset, import"
                    + " and \"everything back to the defaults\".")));
        List<String> lines = new ArrayList<>(List.of("Snapshots (newest first; /giants admin snapshot restore <n>):"));
        java.text.SimpleDateFormat when = new java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.ROOT);
        for (int k = all.size() - 1, n = 1; k >= 0; k--, n++) {
            AdminWorld.Snapshot s = all.get(k);
            lines.add("  " + n + ". " + when.format(new java.util.Date(s.time())) + " " + s.why() + " (" + s.who()
                    + ", " + s.count() + " set)");
        }
        return say(c, AdminServer.Result.said(lines));
    }

    private static int restore(CommandContext<CommandSourceStack> c) {
        List<AdminWorld.Snapshot> all = AdminWorld.get(server(c)).snapshots();
        int n = IntegerArgumentType.getInteger(c, "number"), k = all.size() - n;
        if (k < 0) return say(c, AdminServer.Result.fail("There's no snapshot " + n + ". /giants admin snapshot"
                + " lists them."));
        AdminTransfer.Plan p = AdminUndo.restorePlan(server(c), k, all.get(k).time());
        if (p == null) return say(c, AdminServer.Result.fail("That snapshot isn't kept any more."));
        if (!p.changes().isEmpty()) AdminTransfer.hold(holder(c), p);
        return say(c, AdminServer.Result.said(p.lines()));
    }

    private static final List<String> HELP = List.of(
            "/giants admin get <giant|all> [setting]: what this world has set, or one setting.",
            "/giants admin set <giant|all> <setting> <value> [world|allworlds]: a setting for this world (or the"
                    + " server default, for all worlds).",
            "/giants admin reset <giant|all|everything> [setting]: back to the server defaults.",
            "/giants admin list <giant|all> [category]: every setting there is.",
            "/giants admin log [count]: the recent changes.",
            "/giants admin preset <name>, export, import <text>, then confirm; savepreset <name>, deletepreset <name>.",
            "/giants admin lock|unlock <giant|all> <setting>, locks: keep a setting as it is.",
            "/giants admin undo [count]; snapshot [restore <n>]: put changes back.",
            "/giants admin moderator add|remove <player>, moderators: Live control only.",
            "/giants admin sync [now|status]: config/jj_giants_admin/sync.txt as the server defaults.");

    /**
     * /giants admin set ... [world|allworlds] (ADMIN_BRIDGE.md, "Command scope"): the last word of the value says where
     * it goes; nothing said is this world.
     */
    private static int set(CommandContext<CommandSourceStack> c) {
        String text = StringArgumentType.getString(c, "value").trim();
        int sp = text.lastIndexOf(' ');
        String last = sp < 0 ? "" : text.substring(sp + 1).toLowerCase(java.util.Locale.ROOT);
        boolean all = last.equals("allworlds");
        if (all || last.equals("world")) text = text.substring(0, sp).trim();
        String giant = word(c, "giant"), id = word(c, "setting");
        return say(c, all
                ? AdminServer.setDefault(server(c), who(c), giant, id, text)
                : AdminServer.set(server(c), who(c), giant, id, text));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String word(CommandContext<CommandSourceStack> c, String name) {
        return StringArgumentType.getString(c, name);
    }

    /** who made a change, as the log and the other operators see it */
    static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() != null ? c.getSource().getPlayer().getGameProfile().getName() : "Server";
    }

    /** what a pending preset or import is kept under: the player, or the server's console */
    private static String holder(CommandContext<CommandSourceStack> c) {
        return c.getSource().getPlayer() != null ? c.getSource().getPlayer().getStringUUID() : "server";
    }

    static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        CommandSourceStack src = c.getSource();
        for (String s : r.lines()) {
            if (r.ok()) src.sendSuccess(() -> Component.literal(s), false);
            else src.sendFailure(Component.literal(s));
        }
        if (!r.announce().isEmpty()) AdminServer.announce(src.getServer(), src.getPlayer(), r.announce());
        return r.ok() ? 1 : 0;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> giant(String name, boolean everything) {
        return Commands.argument(name, StringArgumentType.word()).suggests((c, b) -> {
            List<String> out = new ArrayList<>();
            for (Giant g : AdminServer.giants()) out.add(g.key());
            out.add(AdminCatalog.ALL);
            if (everything) out.add("everything");
            return SharedSuggestionProvider.suggest(out, b);
        });
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> setting() {
        return Commands.argument("setting", StringArgumentType.word()).suggests((c, b) -> {
            List<String> out = new ArrayList<>();
            for (AdminSetting s : settingsOf(c)) out.add(s.giant().equals(AdminCatalog.ALL) ? s.id().substring(4)
                    : s.id());
            return SharedSuggestionProvider.suggest(out, b);
        });
    }

    /** the settings of the giant typed so far (for suggestions); empty if there's none */
    private static List<AdminSetting> settingsOf(CommandContext<CommandSourceStack> c) {
        String which;
        try {
            which = word(c, "giant");
        } catch (IllegalArgumentException e) {
            return List.of();
        }
        if (which.equalsIgnoreCase(AdminCatalog.ALL)) return AdminServer.shared(AdminWorld.get(server(c)));
        Giant g = AdminServer.giant(which);
        AdminJson.Report r = g == null ? null : AdminServer.report(server(c), g);
        return r == null ? List.of() : r.settings();
    }

    /** a setting's values to offer: on/off, its options, or its default, current, lowest and highest */
    private static final SuggestionProvider<CommandSourceStack> VALUES = (c, b) -> {
        String id;
        try {
            id = word(c, "setting");
        } catch (IllegalArgumentException e) {
            return b.buildFuture();
        }
        for (AdminSetting s : settingsOf(c)) {
            if (!s.id().equals(id) && !s.id().equals("all." + id)) continue;
            List<String> out = new ArrayList<>();
            switch (s.type()) {
                case BOOL -> out.addAll(List.of("on", "off"));
                case CHOICE -> out.addAll(s.options());
                default -> {
                    for (var v : List.of(s.value(), s.def())) out.add(AdminSetting.text(v));
                    if (s.type() == AdminSetting.Type.RANGE) out.add(AdminSetting.plain(s.min()) + "-"
                            + AdminSetting.plain(s.max()));
                    else {
                        out.add(AdminSetting.plain(s.min()));
                        out.add(AdminSetting.plain(s.max()));
                    }
                }
            }
            return SharedSuggestionProvider.suggest(out.stream().distinct().toList(), b);
        }
        return b.buildFuture();
    };

    private static int preset(CommandContext<CommandSourceStack> c) {
        String name = word(c, "name");
        AdminTransfer.Bundle b = AdminTransfer.preset(name);
        if (b == null)
            return say(c, AdminServer.Result.fail("No preset called " + name + ". Try one of: "
                    + String.join(", ", AdminTransfer.presets()) + "."));
        return preview(c, b);
    }

    private static int importText(CommandContext<CommandSourceStack> c) {
        AdminTransfer.Bundle b = AdminTransfer.decode(StringArgumentType.getString(c, "text"));
        if (b == null)
            return say(c, AdminServer.Result.fail(
                    "That isn't a settings string: it starts with " + AdminTransfer.PREFIX + " (from /giants admin"
                            + " export)."));
        return preview(c, b);
    }

    private static int preview(CommandContext<CommandSourceStack> c, AdminTransfer.Bundle b) {
        AdminTransfer.Plan p = AdminTransfer.plan(server(c), b);
        if (!p.changes().isEmpty()) AdminTransfer.hold(holder(c), p);
        return say(c, AdminServer.Result.said(p.lines()));
    }

    /** /giants admin confirm, and the book's Apply: the preset, import or snapshot this player previewed last */
    static AdminServer.Result confirm(MinecraftServer server, String holder, String who) {
        AdminTransfer.Plan p = AdminTransfer.take(holder);
        if (p == null)
            return AdminServer.Result.fail("Nothing to confirm. Pick a preset, an import or a snapshot first (a"
                    + " preview is kept five minutes).");
        int n = AdminTransfer.apply(server, who, p);
        boolean snap = p.bundle().id().equals("snapshot");
        String title = snap ? "The " + p.bundle().name() : p.bundle().name().isEmpty() ? "The settings"
                : p.bundle().name();
        List<String> lines = new ArrayList<>(List.of(title + ": " + n + (n == 1 ? " setting" : " settings")
                + " changed for this world." + (snap || n == 0 ? "" : " A snapshot of how it was is on the Changes"
                        + " page.")));
        if (!p.creationOnly().isEmpty())
            lines.add("World creation: " + String.join(", ", p.creationOnly())
                    + " only take full effect in a new world or on land not made yet.");
        String what = snap ? "put back the " + p.bundle().name()
                : p.bundle().name().isEmpty() ? "applied imported settings" : "applied the preset " + p.bundle().name();
        return new AdminServer.Result(true, lines, n == 0 ? List.of() : List.of(who + " " + what + " (" + n
                + " changes)."));
    }

    private static int export(CommandContext<CommandSourceStack> c) {
        String text = AdminTransfer.encode(AdminTransfer.current(server(c)));
        Component line = Component.literal(text)
                .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
        c.getSource().sendSuccess(() -> Component.literal(
                "This world's giant settings (click to copy; /giants admin import <text> in another world):"), false);
        c.getSource().sendSuccess(() -> line, false);
        if (text.length() > 240)
            c.getSource().sendSuccess(() -> Component.literal(
                    "It is longer than chat lets you type: import it from a command block or the server console."),
                    false);
        GiantsGuideMod.LOG.info("Giants admin export: {}", text);
        return 1;
    }
}
