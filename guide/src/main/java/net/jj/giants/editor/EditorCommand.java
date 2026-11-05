package net.jj.giants.editor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The giant editor's command twins, under /giants admin look (operators):
 * {@code look} (what everyone wears) · {@code look kind <giant> <set|none>} · {@code look giant <giant> <set|none>}
 * (wreckback, wreckback.2 or his id) · {@code look tag <tag> <set|none>} · {@code look custom <name> base <set|none>}
 * · {@code look custom <name> block <from> <to|same>} · {@code look delete <name>}.
 */
public final class EditorCommand {
    private EditorCommand() {}

    public static void registerAll() {
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> register(d));
    }

    private static final SuggestionProvider<CommandSourceStack> SETS = (c, b) -> {
        List<String> out = new ArrayList<>(LookSets.BUILT_IN);
        out.add("none");
        for (EditorWorld.Custom x : EditorWorld.get(c.getSource().getServer()).s.custom.values())
            out.add(x.name.contains(" ") ? "\"" + x.name + "\"" : x.name);
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static final SuggestionProvider<CommandSourceStack> KINDS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) out.add(g.key());
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static final SuggestionProvider<CommandSourceStack> GIANTS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            out.add(g.key());
            int n = FunGiants.liveIds(c.getSource().getServer(), g).size();
            for (int i = 1; i <= Math.min(n, 9) && n > 1; i++) out.add(g.key() + "." + i);
        }
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static final SuggestionProvider<CommandSourceStack> TAGS = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (FunWorld.Tag t : FunWorld.get(c.getSource().getServer()).s.tags.values())
            out.add(t.name.contains(" ") ? "\"" + t.name + "\"" : t.name);
        return SharedSuggestionProvider.suggest(out, b);
    };

    private static final SuggestionProvider<CommandSourceStack> CUSTOM = (c, b) -> {
        List<String> out = new ArrayList<>();
        for (EditorWorld.Custom x : EditorWorld.get(c.getSource().getServer()).s.custom.values())
            out.add(x.name.contains(" ") ? "\"" + x.name + "\"" : x.name);
        return SharedSuggestionProvider.suggest(out, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        var look = Commands.literal("look").executes(EditorCommand::list);
        look.then(Commands.literal("kind").then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS)
                .then(Commands.argument("set", StringArgumentType.string()).suggests(SETS)
                        .executes(c -> say(c, Editor.setKind(server(c), who(c), StringArgumentType.getString(c,
                                "kind"), StringArgumentType.getString(c, "set")))))));
        look.then(Commands.literal("giant").then(Commands.argument("giant", StringArgumentType.word()).suggests(GIANTS)
                .then(Commands.argument("set", StringArgumentType.string()).suggests(SETS).executes(c -> {
                    String w = StringArgumentType.getString(c, "giant").replace('.', ' ');
                    FunGiants.Found f = FunGiants.which(server(c), w, c.getSource().getLevel(),
                            c.getSource().getPosition());
                    if (f.giant() == null) return say(c, AdminServer.Result.fail(f.why()));
                    return say(c, Editor.setGiant(server(c), who(c), f.giant(), StringArgumentType.getString(c,
                            "set")));
                }))));
        look.then(Commands.literal("tag").then(Commands.argument("tag", StringArgumentType.string()).suggests(TAGS)
                .then(Commands.argument("set", StringArgumentType.string()).suggests(SETS)
                        .executes(c -> say(c, Editor.setTag(server(c), who(c), StringArgumentType.getString(c, "tag"),
                                StringArgumentType.getString(c, "set")))))));
        look.then(Commands.literal("custom").then(Commands.argument("name", StringArgumentType.string())
                .suggests(CUSTOM)
                .then(Commands.literal("base").then(Commands.argument("set", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(withNone(), b))
                        .executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            EditorWorld.Custom old = EditorWorld.get(server(c)).custom(name);
                            return say(c, Editor.saveCustom(server(c), who(c), name, StringArgumentType.getString(c,
                                    "set"), old == null ? Map.of() : old.blocks));
                        })))
                .then(Commands.literal("block").then(Commands.argument("from", StringArgumentType.string())
                        .then(Commands.argument("to", StringArgumentType.string()).executes(c -> {
                            String name = StringArgumentType.getString(c, "name");
                            EditorWorld.Custom old = EditorWorld.get(server(c)).custom(name);
                            Map<String, String> b = new LinkedHashMap<>(old == null ? Map.of() : old.blocks);
                            String from = LookSets.valid(StringArgumentType.getString(c, "from"));
                            String to = StringArgumentType.getString(c, "to");
                            if (from == null)
                                return say(c, AdminServer.Result.fail("There's no block called "
                                        + StringArgumentType.getString(c, "from") + "."));
                            if (to.equalsIgnoreCase("same")) b.remove(LookSets.blockId(from));
                            else b.put(LookSets.blockId(from), to);
                            return say(c, Editor.saveCustom(server(c), who(c), name, old == null ? "" : old.base, b));
                        }))))));
        look.then(Commands.literal("delete").then(Commands.argument("name", StringArgumentType.string())
                .suggests(CUSTOM).executes(c -> say(c, Editor.deleteCustom(server(c), who(c),
                        StringArgumentType.getString(c, "name"))))));
        var admin = Commands.literal("admin").requires(s -> s.hasPermission(AdminCommand.OPS)).then(look);
        d.register(Commands.literal("giants").then(admin));
    }

    private static List<String> withNone() {
        List<String> l = new ArrayList<>(LookSets.BUILT_IN);
        l.add("none");
        return l;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer s = server(c);
        EditorWorld w = EditorWorld.get(s);
        List<String> lines = new ArrayList<>();
        lines.add("Looks (sets: " + String.join(", ", LookSets.BUILT_IN) + (w.s.custom.isEmpty() ? "" : ", "
                + String.join(", ", w.s.custom.keySet())) + "):");
        for (var e : w.s.kinds.entrySet()) {
            Giant g = AdminServer.giant(e.getKey());
            lines.add("  Every " + (g == null ? e.getKey() : AdminServer.nameOf(g)) + ": " + Editor.lookName(e.getValue()));
        }
        for (var e : w.s.tags.entrySet()) lines.add("  Tag " + e.getKey() + ": " + Editor.lookName(e.getValue()));
        for (var e : w.s.giants.entrySet()) {
            LivingEntity g = FunGiants.find(s, e.getKey());
            lines.add("  " + (g == null ? e.getValue().kind + " " + e.getKey() : FunGiants.label(s, g)) + ": "
                    + Editor.lookName(e.getValue().look));
        }
        if (lines.size() == 1) lines.add("  Every giant wears his own look.");
        return say(c, AdminServer.Result.said(lines));
    }

    private static MinecraftServer server(CommandContext<CommandSourceStack> c) {
        return c.getSource().getServer();
    }

    private static String who(CommandContext<CommandSourceStack> c) {
        return c.getSource().getEntity() instanceof ServerPlayer p ? p.getGameProfile().getName() : "Server";
    }

    private static int say(CommandContext<CommandSourceStack> c, AdminServer.Result r) {
        for (String s : r.lines())
            if (r.ok()) c.getSource().sendSuccess(() -> Component.literal(s), false);
            else c.getSource().sendFailure(Component.literal(s));
        if (!r.announce().isEmpty())
            AdminServer.announce(server(c), c.getSource().getEntity() instanceof ServerPlayer p ? p : null,
                    r.announce());
        return r.ok() ? 1 : 0;
    }
}
