package net.jj.hollowbell.client.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.client.render.BellMeshes;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.joml.Vector3f;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development only (-Dhollowbell.autotest=<script>), the same idea as the Mountain's: makes a creative world, runs
 * a list of lines with waits between them, takes screenshots, then quits. Never runs in a normal game.
 * Lines: world flat|normal, gui on|off, render N, cmd ..., wait N, waitmodel, shot name,
 * view mx my mz tx ty tz (camera in his own model space), book, page N, quit.
 */
public final class AutoTest {
    private static List<String> script;
    private static int line, wait, joined;
    private static boolean asked;

    public static void init() {
        String f = System.getProperty("hollowbell.autotest");
        if (f == null) return;
        try { script = new ArrayList<>(Files.readAllLines(Path.of(f))); }
        catch (Exception e) { HollowbellMod.LOG.error("autotest: cannot read {}", f, e); return; }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) { mc.setScreen(new TitleScreen()); return; }
            if (!asked && mc.screen instanceof TitleScreen) {
                asked = true;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings ls = new LevelSettings("hbtest", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                String type = script.stream().filter(l -> l.startsWith("world ")).map(l -> l.substring(6).trim()).findFirst().orElse("flat");
                mc.createWorldOpenFlows().createFreshLevel("hbtest" + System.currentTimeMillis() % 100000, ls, new WorldOptions(20260924L, false, false),
                        ra -> type.equals("flat") ? ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                                : WorldPresets.createNormalWorldDimensions(ra), mc.screen);
            }
            return;
        }
        if (mc.player == null) return;
        if (++joined < 40) return;
        if (wait > 0) { wait--; return; }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("world ")) continue;
            if (s.startsWith("wait ")) { wait = Integer.parseInt(s.substring(5).trim()); return; }
            if (s.equals("waitmodel")) { if (!BellMeshes.INSTANCE.ensureReady()) { line--; wait = 10; } return; }
            if (s.startsWith("cmd ")) { run(mc, s.substring(4).trim()); continue; }
            if (s.startsWith("view ")) { view(mc, s.substring(5).trim()); continue; }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> HollowbellMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.startsWith("gui ")) { mc.options.hideGui = !s.endsWith("on"); continue; }
            if (s.startsWith("render ")) { mc.options.renderDistance().set(Integer.parseInt(s.substring(7).trim())); continue; }
            if (s.equals("book")) { mc.setScreen(new net.jj.hollowbell.client.CodexScreen()); continue; }
            if (s.equals("close")) { mc.setScreen(null); continue; }
            if (s.equals("quit")) { HollowbellMod.LOG.info("autotest done"); mc.stop(); return; }
        }
    }

    private static void view(Minecraft mc, String args) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String[] a = args.split("\\s+");
        float[] v = new float[6];
        for (int i = 0; i < 6; i++) v[i] = Float.parseFloat(a[i]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel().getEntitiesOfClass(HollowbellEntity.class, player.getBoundingBox().inflate(3000));
            if (l.isEmpty()) { HollowbellMod.LOG.info("autotest view: none near"); return; }
            var m = l.get(0);
            var cam = m.toWorld(new Vector3f(v[0], v[1], v[2]));
            var tgt = m.toWorld(new Vector3f(v[3], v[4], v[5]));
            var d = tgt.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
        });
    }

    private static void run(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().get(0);
            var src = player != null ? player.createCommandSourceStack().withPermission(4) : srv.createCommandSourceStack();
            HollowbellMod.LOG.info("autotest cmd: {}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }
}
