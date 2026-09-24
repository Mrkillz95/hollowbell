package net.jj.mountain.client.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.mountain.MountainMod;
import net.jj.mountain.client.render.VoxelModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development only (-Dmountain.autotest=<script file>): makes a flat creative world, runs a list of commands with
 * waits between them, takes screenshots, then quits. Lines: "wait N", "cmd /something", "shot name", "quit".
 */
public final class AutoTest {
    private static List<String> script;
    private static int line, wait, joinedTicks;
    private static boolean worldRequested;

    public static void init() {
        String f = System.getProperty("mountain.autotest");
        if (f == null) return;
        try { script = new ArrayList<>(Files.readAllLines(Path.of(f))); }
        catch (Exception e) { MountainMod.LOG.error("autotest: cannot read {}", f, e); return; }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings ls = new LevelSettings("mtntest", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                String type = script.stream().filter(l -> l.startsWith("world ")).map(l -> l.substring(6).trim()).findFirst().orElse("flat");
                mc.createWorldOpenFlows().createFreshLevel("mtntest" + System.currentTimeMillis() % 100000, ls, new WorldOptions(20260920L, false, false),
                        ra -> type.equals("flat") ? ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()
                                : WorldPresets.createNormalWorldDimensions(ra), mc.screen);
            }
            return;
        }
        if (mc.player == null) return;
        joinedTicks++;
        if (joinedTicks < 40) return;
        if (wait > 0) { wait--; return; }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("world ")) continue;
            if (s.startsWith("wait ")) { wait = Integer.parseInt(s.substring(5).trim()); return; }
            if (s.startsWith("waitmodel")) { if (!VoxelModel.MOUNTAIN.ensureReady()) { line--; wait = 10; } return; }
            if (s.startsWith("cmd ")) { run(mc, s.substring(4).trim()); continue; }
            if (s.startsWith("view ")) { view(mc, s.substring(5).trim()); continue; }
            if (s.startsWith("count")) {
                int n = 0; String where = "";
                if (mc.level != null) for (var en : mc.level.entitiesForRendering()) {
                    if (en instanceof net.jj.mountain.entity.MountainEntity m) {
                        n++;
                        where += " [" + m.getId() + " at " + m.position() + " scale " + m.mountainScale()
                                + " " + (mc.player == null ? "?" : String.format("%.0f", mc.player.distanceTo(m))) + " away]";
                    }
                }
                MountainMod.LOG.info("autotest count: client knows {} mountain(s){} | inside={} him={}", n, where,
                        net.jj.mountain.client.InsideHim.inside(), net.jj.mountain.client.InsideHim.him());
                continue;
            }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> MountainMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.startsWith("gui ")) { mc.options.hideGui = !s.endsWith("on"); continue; }
            if (s.startsWith("sit")) {
                MinecraftServer srv2 = mc.getSingleplayerServer();
                if (srv2 == null) continue;
                final float yaw = s.length() > 4 ? Float.parseFloat(s.substring(4).trim()) : 0f;
                srv2.execute(() -> {
                    if (srv2.getPlayerList().getPlayers().isEmpty()) return;
                    var pl = srv2.getPlayerList().getPlayers().get(0);
                    var l = pl.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, pl.getBoundingBox().inflate(2000));
                    if (l.isEmpty()) return;
                    var at = l.get(0).saddleWorld();
                    pl.teleportTo(pl.serverLevel(), at.x, at.y + 1.2, at.z, yaw, 8f);
                    MountainMod.LOG.info("autotest sit at {}", at);
                });
                continue;
            }
            if (s.equals("fetchme")) {
                MinecraftServer srv5 = mc.getSingleplayerServer();
                if (srv5 == null) continue;
                srv5.execute(() -> {
                    if (srv5.getPlayerList().getPlayers().isEmpty()) return;
                    var pl = srv5.getPlayerList().getPlayers().get(0);
                    var l = pl.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, pl.getBoundingBox().inflate(2000));
                    if (l.isEmpty()) return;
                    MountainMod.LOG.info("autotest fetchme: {}", l.get(0).comeAndGetMe(pl));
                });
                continue;
            }
            if (s.equals("inside") || s.equals("outside")) {
                MinecraftServer srv3 = mc.getSingleplayerServer();
                if (srv3 == null) continue;
                final boolean in = s.equals("inside");
                srv3.execute(() -> {
                    if (srv3.getPlayerList().getPlayers().isEmpty()) return;
                    var pl = srv3.getPlayerList().getPlayers().get(0);
                    var l = pl.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, pl.getBoundingBox().inflate(2000));
                    if (l.isEmpty()) return;
                    if (in) MountainMod.LOG.info("autotest inside: {}", l.get(0).possess(pl));
                    else { MountainMod.LOG.info("autotest outside: {}", l.get(0).setMeDown()); }
                });
                continue;
            }
            if (s.startsWith("drive ")) {
                String[] dv = s.substring(6).trim().split("\\s+");
                net.jj.mountain.client.InsideHim.autoDrive(Float.parseFloat(dv[0]), Float.parseFloat(dv[1]), Float.parseFloat(dv[2]));
                continue;
            }
            if (s.startsWith("attack ")) {
                MinecraftServer srv6 = mc.getSingleplayerServer();
                if (srv6 == null) continue;
                final int which = Integer.parseInt(s.substring(7).trim());
                srv6.execute(() -> {
                    if (srv6.getPlayerList().getPlayers().isEmpty()) return;
                    var pl = srv6.getPlayerList().getPlayers().get(0);
                    var l = pl.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, pl.getBoundingBox().inflate(2000));
                    if (!l.isEmpty()) MountainMod.LOG.info("autotest attack {}: {}", which, l.get(0).forceAttack(which));
                });
                continue;
            }
            if (s.equals("unmake")) {
                MinecraftServer srv8 = mc.getSingleplayerServer();
                if (srv8 == null) continue;
                srv8.execute(() -> {
                    if (srv8.getPlayerList().getPlayers().isEmpty()) return;
                    var pl = srv8.getPlayerList().getPlayers().get(0);
                    var l = pl.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, pl.getBoundingBox().inflate(2000));
                    if (!l.isEmpty()) MountainMod.LOG.info("autotest unmake: {} (state {})", l.get(0).unmakeIt(pl), l.get(0).unmakeState());
                });
                continue;
            }
            if (s.equals("end")) { mc.setScreen(new net.jj.mountain.client.DoomScreen(null)); continue; }
            if (s.equals("end2")) { mc.setScreen(new net.jj.mountain.client.DoomScreen.Word(null)); continue; }
            if (s.startsWith("zoom ")) { net.jj.mountain.client.InsideHim.zoomBy(Double.parseDouble(s.substring(5).trim())); continue; }
            if (s.equals("book")) { mc.setScreen(new net.jj.mountain.client.CodexScreen()); continue; }
            if (s.startsWith("page ")) {
                if (mc.screen instanceof net.jj.mountain.client.CodexScreen) mc.screen.onClose();
                net.jj.mountain.client.CodexScreen.showPage(Integer.parseInt(s.substring(5).trim()));
                mc.setScreen(new net.jj.mountain.client.CodexScreen());
                continue;
            }
            if (s.startsWith("render ")) { mc.options.renderDistance().set(Integer.parseInt(s.substring(7).trim())); continue; }
            if (s.equals("quit")) { MountainMod.LOG.info("autotest done"); mc.stop(); return; }
        }
    }

    /** "view mx my mz tx ty tz": camera at a point in the nearest mountain's own model space, looking at another one */
    private static void view(Minecraft mc, String args) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String[] a = args.split("\\s+");
        float[] v = new float[6];
        for (int i = 0; i < 6; i++) v[i] = Float.parseFloat(a[i]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel().getEntitiesOfClass(net.jj.mountain.entity.MountainEntity.class, player.getBoundingBox().inflate(2000));
            if (l.isEmpty()) {
                var all = player.serverLevel().getEntities(net.jj.mountain.ModEntities.MOUNTAIN, x -> true);
                MountainMod.LOG.info("autotest view {}: no mountain near the player at {} ({} in the level: {})", args, player.position(), all.size(),
                        all.isEmpty() ? "-" : all.get(0).position() + " scale " + all.get(0).mountainScale() + " alive " + all.get(0).isAlive());
                return;
            }
            var m = l.get(0);
            var cam = m.toWorld(new org.joml.Vector3f(v[0], v[1], v[2]), new org.joml.Matrix4f());
            var tgt = m.toWorld(new org.joml.Vector3f(v[3], v[4], v[5]), new org.joml.Matrix4f());
            var d = tgt.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            MountainMod.LOG.info("autotest view {} -> cam {}", args, cam);
        });
    }

    private static void run(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().get(0);
            var src = player != null ? player.createCommandSourceStack().withPermission(4) : srv.createCommandSourceStack();
            MountainMod.LOG.info("autotest cmd: {}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }
}
