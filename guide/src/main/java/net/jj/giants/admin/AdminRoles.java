package net.jj.giants.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Roles for the admin book (Part 2B): an Admin is an operator (permission level 2) and may do everything; a Moderator
 * is a player an Admin made one (kept with the world) and may only use the Live control page. The server checks the
 * role on every request (AdminBook.role).
 */
public final class AdminRoles {
    private AdminRoles() {}

    /** a player online by their UUID or name */
    static @Nullable ServerPlayer online(MinecraftServer server, String who) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p.getStringUUID().equals(who)) return p;
        return server.getPlayerList().getPlayerByName(who);
    }

    /** makes a player a moderator (on) or not (off), by whoever asked (an Admin, already checked) */
    public static AdminServer.Result set(MinecraftServer server, String who, String player, boolean on) {
        AdminWorld w = AdminWorld.get(server);
        ServerPlayer p = online(server, player);
        if (on) {
            if (p == null) return AdminServer.Result.fail("Pick a player who is online.");
            String name = p.getGameProfile().getName();
            if (p.hasPermissions(AdminCommand.OPS))
                return AdminServer.Result.said(List.of(name + " is an admin already (an operator): they have every"
                        + " page."));
            if (!w.addMod(p.getUUID(), name))
                return AdminServer.Result.said(List.of(name + " is a moderator already."));
            w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "roles", "mod:add",
                    who + " made " + name + " a moderator", "", "", ""));
            boolean has = p.getInventory().contains(new net.minecraft.world.item.ItemStack(AdminBookItem.BOOK));
            if (!has) AdminBookItem.give(p);
            p.sendSystemMessage(Component.literal(who + " made you a moderator: the Giants Admin Book opens Live"
                    + " control for you" + (has ? "." : " (it's in your pack).")).withStyle(ChatFormatting.GRAY));
            return new AdminServer.Result(true,
                    List.of(name + " is a moderator now: Live control only" + (has ? "." : ", and they got the book.")),
                    List.of(who + " made " + name + " a moderator."));
        }
        UUID id = p != null ? p.getUUID() : null;
        String name = p != null ? p.getGameProfile().getName() : player;
        if (id == null)
            for (AdminWorld.Mod m : w.mods())
                if (m.id().toString().equals(player) || m.name().equalsIgnoreCase(player)) {
                    id = m.id();
                    name = m.name();
                }
        if (id == null || !w.removeMod(id)) return AdminServer.Result.said(List.of(name + " isn't a moderator."));
        w.log(new AdminWorld.Change(System.currentTimeMillis(), who, "roles", "mod:remove",
                who + " made " + name + " a player again (no longer a moderator)", "", "", ""));
        if (p != null)
            p.sendSystemMessage(Component.literal(who + " took away your moderator role.")
                    .withStyle(ChatFormatting.GRAY));
        return new AdminServer.Result(true, List.of(name + " isn't a moderator any more."),
                List.of(who + " took away " + name + "'s moderator role."));
    }

    /** /giants admin moderators */
    public static AdminServer.Result list(MinecraftServer server) {
        List<AdminWorld.Mod> mods = AdminWorld.get(server).mods();
        if (mods.isEmpty())
            return AdminServer.Result.said(List.of("No moderators. /giants admin moderator add <player> makes one."));
        List<String> names = new ArrayList<>();
        for (AdminWorld.Mod m : mods)
            names.add(m.name() + (server.getPlayerList().getPlayer(m.id()) != null ? " (online)" : ""));
        return AdminServer.Result.said(List.of("Moderators (Live control only): " + String.join(", ", names) + "."));
    }

    /** the moderators, for Home */
    static JsonArray json(MinecraftServer server) {
        JsonArray a = new JsonArray();
        for (AdminWorld.Mod m : AdminWorld.get(server).mods()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", m.id().toString());
            j.addProperty("name", m.name());
            j.addProperty("online", server.getPlayerList().getPlayer(m.id()) != null);
            a.add(j);
        }
        return a;
    }
}
