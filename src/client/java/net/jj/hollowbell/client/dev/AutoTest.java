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
    private static String follow;

    public static void init() {
        String f = System.getProperty("hollowbell.autotest");
        if (f != null && System.getenv("HB_SCRIPT") != null && !System.getenv("HB_SCRIPT").isEmpty()) f = System.getenv("HB_SCRIPT");
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
        if (follow != null) view(mc, follow);
        if (wait > 0) { wait--; return; }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("world ")) continue;
            if (s.startsWith("wait ")) { wait = Integer.parseInt(s.substring(5).trim()); return; }
            if (s.equals("waitmodel")) { if (!BellMeshes.INSTANCE.ensureReady()) { line--; wait = 10; } return; }
            if (s.startsWith("cmd ")) { run(mc, s.substring(4).trim()); continue; }
            // chat /...: typed the way a player types it, through the client (so client-side command handling counts)
            if (s.startsWith("chat /")) { String c = s.substring(6).trim(); HollowbellMod.LOG.info("autotest chat: /{}", c); mc.player.connection.sendCommand(c); continue; }
            if (s.startsWith("guiscale ")) { mc.options.guiScale().set(Integer.parseInt(s.substring(9).trim())); mc.resizeDisplay(); continue; }
            // view: the camera at a spot in his own model space, looking at another, and kept there as he moves
            if (s.startsWith("view ")) { follow = s.substring(5).trim(); view(mc, follow); continue; }
            if (s.equals("fixed")) { follow = null; continue; }
            // cowcam dx dy dz: the camera that far from the cow (in blocks), looking at it, kept there as it moves
            if (s.startsWith("cowcam ")) { follow = "cow " + s.substring(7).trim(); view(mc, follow); continue; }
            // carrycam dist height [out]: side on to the strand carrying somebody up onto his crown, dist model blocks
            // off the upright plane it goes up in, looking at height (model), out from his middle, in that plane
            if (s.startsWith("carrycam ")) { follow = "carry " + s.substring(9).trim(); view(mc, follow); continue; }
            if (s.startsWith("detail ")) { net.jj.hollowbell.Detail.set(s.endsWith("on")); continue; }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> HollowbellMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.startsWith("gui ")) { mc.options.hideGui = !s.endsWith("on"); continue; }
            if (s.startsWith("render ")) { mc.options.renderDistance().set(Integer.parseInt(s.substring(7).trim())); continue; }
            // how far from the camera things keep moving (he's big: the camera often sits a long way off)
            if (s.startsWith("sim ")) {
                int n = Integer.parseInt(s.substring(4).trim());
                mc.options.simulationDistance().set(n);
                MinecraftServer sv = mc.getSingleplayerServer();
                if (sv != null) sv.execute(() -> sv.getPlayerList().setSimulationDistance(n));
                continue;
            }
            if (s.startsWith("page ")) {
                if (mc.screen != null) mc.screen.onClose();
                net.jj.hollowbell.client.CodexScreen.showPage(Integer.parseInt(s.substring(5).trim()));
                mc.setScreen(new net.jj.hollowbell.client.CodexScreen());
                continue;
            }
            if (s.equals("pick")) {
                net.jj.hollowbell.client.BellPick.afterPick(1f);
                for (var en : mc.level.entitiesForRendering()) if (en instanceof HollowbellEntity hh) {
                    var cam = mc.getCameraEntity();
                    HollowbellMod.LOG.info("autotest pick: eye {} look {} in box {} ready {} ray {}", cam.getEyePosition(), cam.getViewVector(1f),
                            hh.bodyBox().contains(cam.getEyePosition()), hh.clientPoseReady(), hh.raycast(cam.getEyePosition(), cam.getViewVector(1f), 100));
                }
                var hb = net.jj.hollowbell.client.BellPick.looking();
                HollowbellMod.LOG.info("autotest pick: {} bone {} {}", hb != null, net.jj.hollowbell.client.BellPick.bone,
                        hb != null && net.jj.hollowbell.client.BellPick.bone >= 0 ? hb.rig.boneNames[net.jj.hollowbell.client.BellPick.bone] : "-");
                continue;
            }
            if (s.equals("swing")) {
                if (mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult eh && mc.gameMode != null) {
                    mc.gameMode.attack(mc.player, eh.getEntity());
                    HollowbellMod.LOG.info("autotest swing at {}", eh.getEntity());
                } else HollowbellMod.LOG.info("autotest swing: nothing under the crosshair ({})", mc.hitResult);
                continue;
            }
            if (s.equals("status")) {
                for (var en : mc.level.entitiesForRendering()) if (en instanceof HollowbellEntity hh)
                    HollowbellMod.LOG.info("autotest client sees him at {} (camera {}) fps {} hurtTime {} hp {}", hh.position(), mc.gameRenderer.getMainCamera().getPosition(), mc.getFps(), hh.hurtTime, hh.getHealth());
                MinecraftServer sv = mc.getSingleplayerServer();
                if (sv != null) sv.execute(() -> {
                    for (var l : sv.getAllLevels()) for (var hb : l.getEntities(net.jj.hollowbell.ModEntities.HOLLOWBELL, x -> true))
                        HollowbellMod.LOG.info("autotest status: hp {}/{} pods {} move {} inside {} at {}", hb.healthNow(), hb.healthMax(),
                                hb.podsLeft(), hb.moveNow(), hb.moves().insideCount(), hb.position());
                });
                continue;
            }
            if (s.equals("book")) { mc.setScreen(new net.jj.hollowbell.client.CodexScreen()); continue; }
            if (s.equals("close")) { mc.setScreen(null); continue; }
            // f3 on|off: the debug screen (it shows the biome)
            if (s.startsWith("f3 ")) { if (mc.getDebugOverlay().showDebugScreen() != s.endsWith("on")) mc.getDebugOverlay().toggleOverlay(); continue; }
            // ground dx dz up lookdx lookdz: stand over his ground, measured from its middle, looking at another spot of it
            if (s.startsWith("ground ")) { groundView(mc, s.substring(7).trim().split("\\s+")); continue; }
            // edge angle out up: just outside the edge of his ground at that angle (degrees), looking back in across it
            if (s.startsWith("edge ")) { edgeView(mc, s.substring(5).trim().split("\\s+")); continue; }
            // cmdlog ...: a command, and everything it answers written to the log
            if (s.startsWith("cmdlog ")) { cmdLog(mc, s.substring(7).trim()); continue; }
            // groundcheck: the biome where you stand, and what the land round you is made of, to the log
            if (s.equals("groundcheck")) { groundCheck(mc); continue; }
            if (s.equals("quit")) { HollowbellMod.LOG.info("autotest done"); mc.stop(); return; }
        }
    }

    private static void view(Minecraft mc, String args) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        if (args.startsWith("cow ")) { cowView(srv, args.substring(4).trim().split("\\s+")); return; }
        if (args.startsWith("carry ")) { carryView(srv, args.substring(6).trim().split("\\s+")); return; }
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
            // the camera hangs where it's put, even when it stops following him
            player.setNoGravity(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        });
    }

    private static float carryTheta = 0f;

    private static void carryView(MinecraftServer srv, String[] a) {
        float dist = Float.parseFloat(a[0]), height = Float.parseFloat(a[1]), out = a.length > 2 ? Float.parseFloat(a[2]) : 50f;
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel().getEntitiesOfClass(HollowbellEntity.class, player.getBoundingBox().inflate(3000));
            if (l.isEmpty()) return;
            var m = l.get(0);
            if (m.moveNow() == net.jj.hollowbell.entity.Moves.GRAB && m.moveArg() >= 0) {
                var j0 = m.rig.strands[m.moveArg()].joints()[0];
                carryTheta = (float) Math.atan2(j0.z, j0.x);
            }
            float c = (float) Math.cos(carryTheta), sn = (float) Math.sin(carryTheta);
            var tgt = m.toWorld(new Vector3f(c * out, height, sn * out));
            var cam = m.toWorld(new Vector3f(c * out - sn * dist, height, sn * out + c * dist));
            var d = tgt.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        });
    }

    private static void cowView(MinecraftServer srv, String[] a) {
        double dx = Double.parseDouble(a[0]), dy = Double.parseDouble(a[1]), dz = Double.parseDouble(a[2]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.animal.Cow.class, player.getBoundingBox().inflate(500));
            if (l.isEmpty()) return;
            var c = l.get(0).position().add(0, 0.7, 0);
            var cam = c.add(dx, dy, dz);
            var d = c.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        });
    }

    private static void groundView(Minecraft mc, String[] a) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        double dx = Double.parseDouble(a[0]), dz = Double.parseDouble(a[1]), up = Double.parseDouble(a[2]);
        double lx = a.length > 3 ? Double.parseDouble(a[3]) : 0, lz = a.length > 4 ? Double.parseDouble(a[4]) : 0;
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var w = net.jj.hollowbell.world.WorldOne.get(srv);
            var l = srv.overworld();
            double x = w.homeX() + dx, z = w.homeZ() + dz, tx = w.homeX() + lx, tz = w.homeZ() + lz;
            l.getChunk(net.minecraft.util.Mth.floor(x) >> 4, net.minecraft.util.Mth.floor(z) >> 4);
            l.getChunk(net.minecraft.util.Mth.floor(tx) >> 4, net.minecraft.util.Mth.floor(tz) >> 4);
            int gy = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(x), net.minecraft.util.Mth.floor(z));
            int ty = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.util.Mth.floor(tx), net.minecraft.util.Mth.floor(tz));
            double y = gy + up;
            double ddx = tx - x, ddz = tz - z, ddy = ty - y, len = Math.max(1e-3, Math.sqrt(ddx * ddx + ddz * ddz));
            float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz)), pitch = (float) -Math.toDegrees(Math.atan2(ddy, len));
            player.teleportTo(l, x, y, z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            HollowbellMod.LOG.info("autotest ground: at {} {} {} looking at {} {} {} (the ground's middle is {} {}, radius {})",
                    (int) x, (int) y, (int) z, (int) tx, ty, (int) tz, w.homeX(), w.homeZ(), w.homeRadius());
        });
    }

    private static void edgeView(Minecraft mc, String[] a) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        double ang = Math.toRadians(Double.parseDouble(a[0])), out = Double.parseDouble(a[1]);
        String up = a[2];
        srv.execute(() -> {
            var snap = net.jj.hollowbell.world.BellGen.SNAP;
            if (snap == null || !snap.claimed) return;
            double c = Math.cos(ang), sn = Math.sin(ang);
            double edge = 0;
            for (double d = 0; d < snap.radius; d += 4) if (snap.outline.dn((int) (snap.cx + c * d), (int) (snap.cz + sn * d)) < 1) edge = d;
            double cam = edge + out, look = edge - 90;
            HollowbellMod.LOG.info("autotest edge: at {} degrees the ground reaches {} blocks", a[0], (int) edge);
            mc.execute(() -> groundView(mc, new String[]{String.valueOf(c * cam), String.valueOf(sn * cam), up, String.valueOf(c * look), String.valueOf(sn * look)}));
        });
    }

    private static void cmdLog(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().get(0);
            net.minecraft.commands.CommandSource logger = new net.minecraft.commands.CommandSource() {
                @Override public void sendSystemMessage(net.minecraft.network.chat.Component m) {
                    HollowbellMod.LOG.info("autotest said: {}", m.getString());
                    if (player != null) player.sendSystemMessage(m);
                }
                @Override public boolean acceptsSuccess() { return true; }
                @Override public boolean acceptsFailure() { return true; }
                @Override public boolean shouldInformAdmins() { return false; }
            };
            var src = player != null
                    ? new net.minecraft.commands.CommandSourceStack(logger, player.position(), player.getRotationVector(), player.serverLevel(), 4,
                            player.getName().getString(), player.getDisplayName(), srv, player)
                    : srv.createCommandSourceStack();
            HollowbellMod.LOG.info("autotest cmdlog: /{}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }

    private static void groundCheck(Minecraft mc) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var p = srv.getPlayerList().getPlayers().get(0);
            var l = p.serverLevel();
            var at = p.blockPosition();
            var biome = l.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("?");
            int ores = 0, ours = 0, cols = 0;
            java.util.Map<String, Integer> tops = new java.util.TreeMap<>();
            for (int x = at.getX() - 24; x <= at.getX() + 24; x++) for (int z = at.getZ() - 24; z <= at.getZ() + 24; z++) {
                int top = l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                var b = l.getBlockState(new net.minecraft.core.BlockPos(x, top, z));
                tops.merge(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath(), 1, Integer::sum);
                if (net.jj.hollowbell.world.HomeGround.isOurs(b)) ours++;
                cols++;
                for (int y = 0; y < top - 4; y++) {
                    var s = l.getBlockState(new net.minecraft.core.BlockPos(x, y, z));
                    if (s.is(net.minecraft.tags.BlockTags.COAL_ORES) || s.is(net.minecraft.tags.BlockTags.IRON_ORES) || s.is(net.minecraft.tags.BlockTags.COPPER_ORES)
                            || s.is(net.minecraft.tags.BlockTags.GOLD_ORES) || s.is(net.minecraft.tags.BlockTags.REDSTONE_ORES) || s.is(net.minecraft.tags.BlockTags.LAPIS_ORES)
                            || s.is(net.minecraft.tags.BlockTags.DIAMOND_ORES)) ores++;
                }
            }
            HollowbellMod.LOG.info("autotest groundcheck at {}: biome {}, {} of {} tops are his, {} ore blocks from y 0 up; tops {}", at, biome, ours, cols, ores, tops);
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
