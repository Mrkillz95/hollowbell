package net.jj.giants.client.perf;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.jj.giants.Giant;
import net.jj.giants.Giants;
import net.jj.giants.net.PerfAskPayload;
import net.jj.giants.net.PerfOpenPayload;
import net.jj.giants.net.PerfSetPayload;
import net.jj.giants.net.PerfStatePayload;
import net.jj.giants.perf.PerfBridges;
import net.jj.giants.perf.PerfJson;
import net.jj.giants.perf.PerfProfile;
import net.jj.giants.perf.PerfSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The player's half of Giants Performance: their own settings (kept in config/jj_giants_performance.json and handed
 * to each giant's mod on this game), what the server last said about the world's Features, and the ways in: the
 * server's /giants performance (and /giants effects), a key (unbound at first: set it in Controls) and the Guide's
 * button.
 */
public final class PerfClient {
    private PerfClient() {}

    private static PerfProfile profile;
    private static List<Giant> giants = List.of();
    private static List<PerfJson.Report> reports = List.of();
    /** every setting this game knows of (the guide's and its giants'), for the player's own side */
    private static List<PerfSetting> local = List.of();
    private static boolean asked;
    private static boolean openNextTick;
    private static KeyMapping key;

    /** the server's word: whether it has the guide at all, who may change what, and the world's values */
    public record World(boolean known, boolean op, List<PerfSetting> settings, Map<String, Double> values,
            String message, long at) {}

    private static World world = noWorld();

    private static World noWorld() {
        return new World(false, false, List.of(), Map.of(), "", 0);
    }

    public static void init() {
        key = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.jj_giants.performance", InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(),
                "key.categories.jj_giants"));
        // (the command is the server's: /giants is the giants' own command, and a client command of the same name
        // would stop every other /giants word from reaching the server)
        ClientPlayNetworking.registerGlobalReceiver(PerfOpenPayload.TYPE, (p, ctx) -> openNextTick = true);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) if (mc.screen == null) openNextTick = true;
            if (openNextTick) {
                openNextTick = false;
                open(mc.screen);
            }
        });
        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> start());
        ClientPlayNetworking.registerGlobalReceiver(PerfStatePayload.TYPE, (p, ctx) -> told(p));
        ClientPlayConnectionEvents.JOIN.register((h, s, mc) -> {
            world = noWorld();
            ask();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> world = noWorld());
    }

    /** opens the screen (from the Guide it goes back to the Guide when closed) */
    public static void open(@Nullable Screen back) {
        start();
        ask();
        Minecraft.getInstance().setScreen(new PerfScreen(back));
    }

    /** reads the file and asks each giant's mod on this game what it has; then hands them the player's values */
    private static void start() {
        if (profile != null) return;
        profile = PerfProfile.load(file());
        giants = Giants.installed();
        reports = PerfBridges.askAll(null, giants);
        local = PerfBridges.settings(reports, giants);
        push();
    }

    private static java.nio.file.Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("jj_giants_performance.json");
    }

    public static PerfProfile profile() {
        start();
        return profile;
    }

    public static List<PerfJson.Report> reports() {
        return reports;
    }

    public static List<Giant> giants() {
        return giants;
    }

    /** the installed giants whose mods are too old to answer (they're left out of the list) */
    public static List<Giant> leftOut() {
        List<Giant> out = new ArrayList<>();
        for (Giant g : giants) if (reports.stream().noneMatch(r -> r.giant().equals(g.key()))) out.add(g);
        return out;
    }

    /**
     * Every setting for the screen: the player's own (from this game's giants), then the world's: the server's list
     * if it sent one, else this game's (shown greyed out).
     */
    public static List<PerfSetting> settings() {
        start();
        List<PerfSetting> out = new ArrayList<>();
        for (PerfSetting s : local) if (!s.server()) out.add(s);
        List<PerfSetting> w = world.known() ? world.settings() : local.stream().filter(PerfSetting::server).toList();
        for (PerfSetting s : w) if (out.stream().noneMatch(o -> o.id().equals(s.id()))) out.add(s);
        return out;
    }

    public static World world() {
        return world;
    }

    /** the player's own values go to every giant's mod on this game, and into the file */
    public static void push() {
        if (profile == null) return;
        PerfBridges.push(null, reports, giants, false, local, profile.values());
        profile.save(file());
    }

    /** asks the server for the world's settings (if it has the guide) */
    public static void ask() {
        if (Minecraft.getInstance().getConnection() == null) return;
        if (ClientPlayNetworking.canSend(PerfAskPayload.TYPE)) ClientPlayNetworking.send(new PerfAskPayload());
    }

    /** asks the server to change some of the world's settings; it checks the player may. False if it can't be sent */
    public static boolean request(Map<String, Double> values) {
        if (!world.known() || !ClientPlayNetworking.canSend(PerfSetPayload.TYPE) || values.isEmpty()) return false;
        List<PerfSetting> ws = world.settings();
        String json = PerfJson.values(values, ws).toString();
        ClientPlayNetworking.send(new PerfSetPayload(json));
        return true;
    }

    private static void told(PerfStatePayload p) {
        List<PerfSetting> s = PerfJson.readAll(p.settings());
        world = new World(true, p.op(), s, PerfJson.values(p.values()), p.message(), net.minecraft.Util.getMillis());
        if (Minecraft.getInstance().screen instanceof PerfScreen ps) ps.worldChanged();
    }
}
