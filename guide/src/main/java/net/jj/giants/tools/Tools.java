package net.jj.giants.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 6, ADMIN_BRIDGE.md §22) The admin book's server tools, the server's half: pause all giants, events, preset
 * schedules, areas and dimension rules, the map, the stats, the alerts, the respawn timers, quiet mode, per-world
 * profiles and pack mode. The Guide does all of it with the giants' own bridges (freeze, goto, wake, live, liveact,
 * grounds) and one new one (respawn, respawnact); every setting is an ordinary all-giants setting (category "server"),
 * so the change log, undo, locks, export and presets treat it as any other.
 */
public final class Tools {
    private Tools() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Tools::tick);
        // (after the settings sync: the profile and the modpack's preset are this world's own values)
        ServerLifecycleEvents.SERVER_STARTING.register(s -> PackMode.load());
        // (JJ, Nov 3) the alerts start off in a single player game, on for a dedicated server (the game tests' server
        // counts as a dedicated one)
        ServerLifecycleEvents.SERVER_STARTING.register(s -> net.jj.giants.admin.AdminCatalog.singlePlayer =
                !s.isDedicatedServer() && !(s instanceof net.minecraft.gametest.framework.GameTestServer));
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            PackMode.onStart(s);
            Profiles.onStart(s);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
        ServerLivingEntityEvents.AFTER_DEATH.register(Stats::died);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(Stats::damaged);
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> ToolsCommand.register(d));
    }

    /** nothing left from a world that stopped (and the tests) */
    public static void forget() {
        Watch.forget();
        Stats.forget();
        Events.forget();
        ToolsAdmin.forget();
    }

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 20 == 0) {
            Events.tick(server);
            Schedules.tick(server);
        }
        if (t % 40 == 0) Watch.look(server);
        Stats.tick(server);
        ToolsAdmin.tick(server);
    }

    // the settings (the all-giants ones of category "server", ADMIN_BRIDGE.md §22.1)

    /** a server tools setting's value in this world (the world's own, else its default) */
    public static JsonElement value(MinecraftServer server, String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get(full);
        if (v != null) return v;
        AdminSetting s = AdminCatalog.shared(full);
        return s == null ? new JsonPrimitive(0) : s.def();
    }

    public static boolean on(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static int num(MinecraftServer server, String id) {
        try {
            return value(server, id).getAsInt();
        } catch (RuntimeException e) {
            return 0;
        }
    }

    public static String option(MinecraftServer server, String id) {
        JsonElement v = value(server, id);
        return v.isJsonPrimitive() ? v.getAsString() : "";
    }

    /**
     * (JJ answers 2, #11) the preset as Home shows it. An event or a schedule changes settings only for a while, so
     * while one runs the world still reads as the preset underneath it ("Classic", "Titans"): the values they hold, and
     * the player and server settings, don't count as the world's own. Only a real change by an admin reads "Custom" or
     * ", changed".
     */
    public static String presetName(MinecraftServer server) {
        AdminWorld w = AdminWorld.get(server);
        return net.jj.giants.admin.AdminInsight.presetName(w.log(), ownValues(server, w));
    }

    /** whether the world keeps a giant value of its own: not a player/server setting, nor one an event holds now */
    public static boolean ownValues(MinecraftServer server, AdminWorld w) {
        ToolsData d = ToolsData.get(server);
        java.util.List<ToolsData.Put> held = new java.util.ArrayList<>();
        if (d.s.event != null) held.addAll(d.s.event.puts);
        if (d.s.schedule != null) held.addAll(d.s.schedule.puts);
        for (String g : w.giantsWithValues())
            for (var e : w.values(g).entrySet()) {
                if (g.equals(AdminCatalog.ALL) && AdminCatalog.player(e.getKey())) continue;
                boolean byTools = false;
                for (ToolsData.Put p : held)
                    if (p.giant.equals(g) && p.id.equals(e.getKey()) && p.now != null
                            && AdminSetting.same(e.getValue(), com.google.gson.JsonParser.parseString(p.now))) {
                        byTools = true;
                        break;
                    }
                if (!byTools) return true;
            }
        return false;
    }

    /** quiet mode: no chat about the giants for players, no chat lines for admins (the books and logs keep them) */
    public static boolean quiet(@Nullable MinecraftServer server) {
        return server != null && on(server, "quiet");
    }

    /** the switches the giants get: with quiet mode on, their chat lines are off whatever the world set */
    public static Map<String, JsonElement> forGiants(Map<String, JsonElement> all) {
        JsonElement q = all.get("all.quiet");
        boolean quiet;
        try {
            quiet = q != null && q.getAsBoolean();
        } catch (RuntimeException e) {
            quiet = false;
        }
        if (!quiet) return all;
        Map<String, JsonElement> out = new LinkedHashMap<>(all);
        for (String id : QUIETED) out.put(id, new JsonPrimitive(false));
        return out;
    }

    /** the giants' chat switches quiet mode turns off */
    public static final java.util.List<String> QUIETED = java.util.List.of("all.chatMeetings", "all.chatMoves",
            "all.chatBackDown");

    // telling people

    /** a line to every player (an event or a schedule starting): not in quiet mode */
    public static void tellEveryone(MinecraftServer server, Component line) {
        net.jj.giants.GiantsGuideMod.LOG.info("Giants: {}", line.getString());
        if (quiet(server)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) p.sendSystemMessage(line);
    }

    /** a line to every admin online but these (an alert): not in quiet mode */
    public static void tellAdmins(MinecraftServer server, Component line, Set<UUID> notThem) {
        if (quiet(server)) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p.hasPermissions(net.jj.giants.admin.AdminCommand.OPS) && !notThem.contains(p.getUUID()))
                p.sendSystemMessage(line);
    }

    static Component gold(String s) {
        return Component.literal(s).withStyle(ChatFormatting.GOLD);
    }

    // which giant an entity belongs to

    /** each giant's mod namespace (his entities' ids) to the Guide's key */
    private static final Map<String, String> SPACES = new HashMap<>();

    static {
        for (net.jj.giants.Giant g : net.jj.giants.Giants.ALL) SPACES.put(g.modId(), g.key());
    }

    private static final Map<EntityType<?>, String> KIND_OF = new java.util.IdentityHashMap<>();

    /** the giant this entity belongs to (him, his parts, his robots, his creatures), or null */
    public static @Nullable String kindOf(@Nullable Entity e) {
        if (e == null) return null;
        EntityType<?> t = e.getType();
        String k = KIND_OF.get(t);
        if (k == null && !KIND_OF.containsKey(t)) {
            k = SPACES.get(BuiltInRegistries.ENTITY_TYPE.getKey(t).getNamespace());
            KIND_OF.put(t, k);
        }
        return k;
    }
}
