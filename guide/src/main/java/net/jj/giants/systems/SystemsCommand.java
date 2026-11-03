package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminSetting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * (Part 8, ADMIN_BRIDGE.md §24) /giants admin systems: what the new systems are doing now (the Systems page's "Right
 * now"), and /giants admin systems giants: each giant they follow. The settings themselves are ordinary ones:
 * /giants admin set all growth on.
 */
public final class SystemsCommand {
    private SystemsCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("giants")
                .then(Commands.literal("admin")
                        .requires(s -> s.hasPermission(AdminCommand.OPS))
                        .then(Commands.literal("systems")
                                .executes(SystemsCommand::now)
                                .then(Commands.literal("giants").executes(SystemsCommand::giants))
                                .then(Commands.literal("titan")
                                        .then(Commands.argument("giant", StringArgumentType.greedyString())
                                                .executes(SystemsCommand::titan))))));
    }

    /** the systems that are on, and what they do now */
    public static List<String> nowLines(net.minecraft.server.MinecraftServer server) {
        JsonObject o = SystemsAdmin.json(server);
        JsonObject n = o.getAsJsonObject("now");
        List<String> on = new ArrayList<>();
        for (String[] grp : SystemsCatalog.GROUPS) {
            AdminSetting first = net.jj.giants.admin.AdminCatalog.shared(grp[1]);
            if (first == null) continue;
            JsonElement v = Systems.value(server, grp[1]);
            if (!AdminSetting.same(v, first.jar()))
                on.add(first.name() + " " + AdminSetting.text(v));
        }
        List<String> lines = new ArrayList<>();
        lines.add(on.isEmpty() ? "No new systems are on in this world (each is a setting: /giants admin list all"
                + " systems)." : "Systems on: " + String.join(", ", on) + ".");
        String season = AdminJson.str(n, "season", 16);
        if (!season.isEmpty())
            lines.add("  Season: " + season + ", day " + AdminJson.num(n, "seasonDay", 1) + " of "
                    + AdminJson.num(n, "seasonDays", 7) + ".");
        if (n.get("fullMoon").getAsBoolean()) lines.add("  The full moon is up: the giants are in a frenzy.");
        double scale = AdminJson.num(n, "scale", 1);
        if (scale != 1)
            lines.add("  Scaling: x" + AdminSetting.plain(scale) + " (" + (int) AdminJson.num(n, "players", 0)
                    + " players on, day " + (int) AdminJson.num(n, "days", 0) + ", best armour "
                    + Math.round(AdminJson.num(n, "armour", 0) * 100) + "%).");
        lines.add("  Sent to the giants: health x" + AdminSetting.plain(AdminJson.num(n, "health", 1)) + ", damage x"
                + AdminSetting.plain(AdminJson.num(n, "damage", 1)) + ", speed x"
                + AdminSetting.plain(AdminJson.num(n, "speed", 1)) + ", healing x"
                + AdminSetting.plain(AdminJson.num(n, "healing", 1)) + " (on top of the world's own).");
        int grief = (int) AdminJson.num(n, "grief", 0), rot = (int) AdminJson.num(n, "rotting", 0);
        if (grief > 0) lines.add("  " + grief + " broken blocks wait to grow back.");
        if (rot > 0) lines.add("  " + rot + (rot == 1 ? " corpse is" : " corpses are") + " rotting.");
        // (Part 9) new systems B
        partB(server, n, lines);
        return lines;
    }

    private static String nameOf(String kind) {
        net.jj.giants.Giant g = net.jj.giants.admin.AdminServer.giant(kind);
        return g == null ? kind : net.jj.giants.admin.AdminServer.nameOf(g);
    }

    /** (Part 9) what the weather, followers, the underworld, sacred ground, Mirror and Random chaos do now */
    static void partB(net.minecraft.server.MinecraftServer server, JsonObject n, List<String> lines) {
        if (Systems.on(server, "weather"))
            lines.add("  Weather Gods: " + (int) AdminJson.num(n, "weatherPlayers", 0) + " players in a giant's"
                    + " weather.");
        if (Systems.on(server, "followers"))
            lines.add("  Followers: " + (int) AdminJson.num(n, "followers", 0) + " with their giants.");
        if (n.get("underworld") instanceof JsonArray u && !u.isEmpty()) {
            List<String> parts = new ArrayList<>();
            for (JsonElement e : u) {
                JsonObject j = e.getAsJsonObject();
                String where = AdminJson.str(j, "dim", 64).endsWith("end") ? "the End" : "the Nether";
                String state = AdminJson.str(j, "state", 16);
                parts.add(nameOf(AdminJson.str(j, "kind", 32)) + " in " + where + " (" + switch (state) {
                    case "standing" -> "there";
                    case "never" -> "fell, never back";
                    case "waits" -> "back in " + (int) AdminJson.num(j, "minutes", 0) + " min";
                    default -> "comes when a player is there";
                } + ")");
            }
            lines.add("  Underworld: " + String.join(", ", parts) + ".");
        }
        if (n.get("sacred") instanceof JsonArray sa) {
            List<String> names = new ArrayList<>();
            for (JsonElement e : sa) names.add(nameOf(e.getAsString()));
            lines.add("  Sacred ground: " + (names.isEmpty() ? "no giant lives now, so nothing is held sacred"
                    : String.join(", ", names) + " (no building or breaking in their grounds)") + ".");
        }
        if (n.get("mirror") instanceof JsonObject m) {
            List<String> parts = new ArrayList<>();
            for (var e : m.entrySet()) {
                JsonObject k = e.getValue().getAsJsonObject();
                for (String t : Mirror.TACTICS) {
                    double f = AdminJson.num(k, t, 1);
                    if (f < 1) parts.add(nameOf(e.getKey()) + " counters " + Mirror.WORDS.get(Mirror.TACTICS.indexOf(t))
                            + " (x" + AdminSetting.plain(f) + ")");
                }
            }
            if (Systems.on(server, "mirror"))
                lines.add("  Mirror: " + (parts.isEmpty() ? "nothing learned yet" : String.join(", ", parts)) + ".");
        }
        if (n.get("chaos") instanceof JsonObject c && c.has("day")) {
            long day = (long) AdminJson.num(c, "day", -1);
            StringBuilder b = new StringBuilder("  Random chaos: ");
            b.append(day < 0 ? "not shuffled yet" : "shuffled on day " + day + ", next on day "
                    + (long) AdminJson.num(c, "next", 0));
            if (c.get("rests") instanceof JsonObject r && !r.entrySet().isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (var e : r.entrySet()) parts.add(nameOf(e.getKey()) + " rests " + e.getValue().getAsJsonArray()
                        .size());
                b.append("; moves resting today: ").append(String.join(", ", parts));
            }
            lines.add(b.append('.').toString());
        }
    }

    private static int now(CommandContext<CommandSourceStack> c) {
        for (String s : nowLines(c.getSource().getServer()))
            c.getSource().sendSuccess(() -> Component.literal(s), false);
        return 1;
    }

    /** /giants admin systems titan <giant>: that one becomes a Titan now (Growth must be on) */
    private static int titan(CommandContext<CommandSourceStack> c) {
        net.minecraft.server.MinecraftServer server = c.getSource().getServer();
        if (!Systems.on(server, "growth")) {
            c.getSource().sendFailure(Component.literal("Growth is off in this world: /giants admin set all growth on"
                    + " first."));
            return 0;
        }
        net.jj.giants.fun.FunGiants.Found f = net.jj.giants.fun.FunGiants.which(server,
                StringArgumentType.getString(c, "giant"), c.getSource().getLevel(), c.getSource().getPosition());
        if (f.giant() == null) {
            c.getSource().sendFailure(Component.literal(f.why()));
            return 0;
        }
        String line = Look.titanNow(server, f.giant());
        c.getSource().sendSuccess(() -> Component.literal(line), true);
        return 1;
    }

    private static int giants(CommandContext<CommandSourceStack> c) {
        JsonArray a = SystemsAdmin.json(c.getSource().getServer()).getAsJsonArray("giants");
        if (a.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.literal("The systems follow no giants yet (Growth, Hunger or"
                    + " Loyalty follow each one they see)."), false);
            return 1;
        }
        c.getSource().sendSuccess(() -> Component.literal("Giants the systems follow:"), false);
        for (JsonElement e : a) {
            JsonObject j = e.getAsJsonObject();
            StringBuilder s = new StringBuilder("  " + AdminJson.str(j, "name", 32));
            // (one out of the world, or gone, has no size to read now: the book says the same)
            if (j.has("here") && j.get("here").getAsBoolean())
                s.append(": size ").append(AdminSetting.plain(AdminJson.num(j, "size", 0))).append(" (started ")
                        .append(AdminSetting.plain(AdminJson.num(j, "start", 0))).append("), ");
            else s.append(": out of the world now, ");
            s.append(AdminSetting.plain(AdminJson.num(j, "days", 0))).append(" days, ")
                    .append((int) AdminJson.num(j, "wins", 0)).append(" wins, food ")
                    .append((int) AdminJson.num(j, "food", 100)).append("%");
            int loyal = (int) AdminJson.num(j, "loyalty", -1);
            if (loyal >= 0) s.append(", loyalty ").append(loyal).append("% (").append(AdminJson.str(j, "owner", 32))
                    .append(")");
            if (j.get("titan").getAsBoolean()) s.append(", a Titan");
            int belly = (int) AdminJson.num(j, "belly", 0);
            if (belly > 0) s.append(", ").append(belly).append(" stacks swallowed");
            String line = s.append('.').toString();
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
