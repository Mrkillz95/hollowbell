package net.jj.hollowbell.client.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.hollowbell.Detail;
import net.jj.hollowbell.HollowbellMod;
import net.jj.hollowbell.ModEntities;
import net.jj.hollowbell.client.BellPick;
import net.jj.hollowbell.client.CodexScreen;
import net.jj.hollowbell.client.render.BellMeshes;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Moves;
import net.jj.hollowbell.fx.BigFx;
import net.jj.hollowbell.rig.BellModel;
import net.jj.hollowbell.rig.BellPieces;
import net.jj.hollowbell.solid.Solid;
import net.jj.hollowbell.solid.SolidCarry;
import net.jj.hollowbell.solid.client.SolidClient;
import net.jj.hollowbell.test.DevView;
import net.jj.hollowbell.world.BellGen;
import net.jj.hollowbell.world.HomeGround;
import net.jj.hollowbell.world.WorldOne;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Development only (-Dhollowbell.autotest=<script>): makes a creative world, runs
 * a list of lines with waits between them, takes screenshots, then quits. Never runs in a normal game.
 * Lines: world flat|normal, gui on|off, render N, cmd ..., wait N, waitmodel, shot name,
 * view mx my mz tx ty tz (camera in his own model space), book, page N, quit.
 */
public final class AutoTest implements ClientModInitializer {
    private static List<String> script;
    private static int line, wait, joined, atWaited;
    private static boolean asked;
    private static String follow;
    /** landtime: chunks out from the player to wait for, and when the wait began (real time) */
    private static int landR = -1;

    private static long landFrom;

    @Override
    public void onInitializeClient() {
        String f = System.getProperty("hollowbell.autotest");
        if (f != null
                && System.getenv("HB_SCRIPT") != null
                && !System.getenv("HB_SCRIPT").isEmpty()) f = System.getenv("HB_SCRIPT");
        if (f == null) return;
        try {
            script = new ArrayList<>(Files.readAllLines(Path.of(f)));
        } catch (Exception e) {
            HollowbellMod.LOG.error("autotest: cannot read {}", f, e);
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!asked && mc.screen instanceof TitleScreen) newWorld(mc);
            return;
        }
        if (mc.player == null) return;
        if (++joined < 40) return;
        if (follow != null) view(mc, follow);
        if (landR >= 0 && !landDone(mc)) return;
        if (wait > 0) {
            wait--;
            return;
        }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("world ")) continue;
            if (s.startsWith("wait ")) {
                wait = Integer.parseInt(s.substring(5).trim());
                return;
            }
            if (s.equals("waitmodel")) {
                if (!BellMeshes.INSTANCE.ensureReady()) {
                    line--;
                    wait = 10;
                }
                return;
            }
            if (s.startsWith("cmd ")) {
                run(mc, s.substring(4).trim());
                continue;
            }
            // atmove name tick: wait until the nearest one is that far into that move (gives up after 40 s)
            if (s.startsWith("atmove ")) {
                if (atMove(mc, s)) return;
                continue;
            }
            // fx kind x y z size [dx dy dz extra]: one of the big-moment effects, sent by the server as a move sends it
            if (s.startsWith("fx ")) {
                fx(mc, s);
                continue;
            }
            // chat /...: typed the way a player types it, through the client (so client-side command handling counts)
            if (s.startsWith("chat /")) {
                String c = s.substring(6).trim();
                HollowbellMod.LOG.info("autotest chat: /{}", c);
                mc.player.connection.sendCommand(c);
                continue;
            }
            if (s.startsWith("guiscale ")) {
                mc.options.guiScale().set(Integer.parseInt(s.substring(9).trim()));
                mc.resizeDisplay();
                continue;
            }
            // view: the camera at a spot in his own model space, looking at another, and kept there as he moves
            if (s.startsWith("view ")) {
                follow = s.substring(5).trim();
                view(mc, follow);
                continue;
            }
            if (s.equals("fixed")) {
                follow = null;
                continue;
            }
            // cowcam dx dy dz: the camera that far from the cow (in blocks), looking at it, kept there as it moves
            if (s.startsWith("cowcam ")) {
                follow = "cow " + s.substring(7).trim();
                view(mc, follow);
                continue;
            }
            // carrycam dist height [out]: side on to the strand carrying somebody up onto his crown, dist model blocks
            // off the upright plane it goes up in, looking at height (model), out from his middle, in that plane
            if (s.startsWith("carrycam ")) {
                follow = "carry " + s.substring(9).trim();
                view(mc, follow);
                continue;
            }
            if (s.startsWith("detail ")) {
                Detail.set(s.endsWith("on"));
                continue;
            }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(
                        mc.gameDirectory,
                        name,
                        mc.getMainRenderTarget(),
                        c -> HollowbellMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.startsWith("gui ")) {
                mc.options.hideGui = !s.endsWith("on");
                continue;
            }
            if (s.startsWith("render ")) {
                mc.options.renderDistance().set(Integer.parseInt(s.substring(7).trim()));
                continue;
            }
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
                CodexScreen.showPage(Integer.parseInt(s.substring(5).trim()));
                mc.setScreen(new CodexScreen());
                continue;
            }
            if (s.equals("pick")) {
                logPick(mc);
                continue;
            }
            if (s.equals("swing")) {
                if (mc.hitResult instanceof EntityHitResult eh && mc.gameMode != null) {
                    mc.gameMode.attack(mc.player, eh.getEntity());
                    HollowbellMod.LOG.info("autotest swing at {}", eh.getEntity());
                } else HollowbellMod.LOG.info("autotest swing: nothing under the crosshair ({})", mc.hitResult);
                continue;
            }
            if (s.equals("status")) {
                logStatus(mc);
                continue;
            }
            if (s.equals("book")) {
                mc.setScreen(new CodexScreen());
                continue;
            }
            if (s.equals("close")) {
                mc.setScreen(null);
                continue;
            }
            // f3 on|off: the debug screen (it shows the biome)
            if (s.startsWith("f3 ")) {
                if (mc.getDebugOverlay().showDebugScreen() != s.endsWith("on"))
                    mc.getDebugOverlay().toggleOverlay();
                continue;
            }
            // ground dx dz up lookdx lookdz: stand over his ground, measured from its middle, looking at another spot
            // of it
            if (s.startsWith("ground ")) {
                groundView(mc, s.substring(7).trim().split("\\s+"));
                continue;
            }
            // edge angle out up: just outside the edge of his ground at that angle (degrees), looking back in across it
            if (s.startsWith("edge ")) {
                edgeView(mc, s.substring(5).trim().split("\\s+"));
                continue;
            }
            // cmdlog ...: a command, and everything it answers written to the log
            if (s.startsWith("cmdlog ")) {
                cmdLog(mc, s.substring(7).trim());
                continue;
            }
            // groundcheck: the biome where you stand, and what the land round you is made of, to the log
            if (s.equals("groundcheck")) {
                groundCheck(mc);
                continue;
            }
            // standon <crown|dome|side|spot|pod|arm>: the player (walking, not flying) set down on the top of that part
            // of him
            if (s.startsWith("standon ")) {
                standOn(mc, s.substring(8).trim());
                follow = null;
                continue;
            }
            // third on|off: the camera behind the player (to see them standing on him)
            if (s.startsWith("third ")) {
                mc.options.setCameraType(s.endsWith("on") ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON);
                continue;
            }
            // ridelog: is the player being carried on him, how far a frame set them from where the tick had them, how
            // often they were moved out of him
            if (s.equals("ridelog")) {
                logRide(mc);
                continue;
            }
            // the arms-only view and the land timings (see measure)
            int m = measure(mc, s);
            if (m == 1) continue;
            if (m == 2) return;
            if (s.equals("quit")) {
                HollowbellMod.LOG.info("autotest done");
                mc.stop();
                return;
            }
        }
    }

    private static void fx(Minecraft mc, String s) {
        String[] a = s.substring(3).trim().split("\\s+");
        int kind = Arrays.asList(BigFx.NAMES).indexOf(a[0]);
        double fx = Double.parseDouble(a[1]), fy = Double.parseDouble(a[2]), fz = Double.parseDouble(a[3]);
        float size = Float.parseFloat(a[4]);
        float dx = a.length > 5 ? Float.parseFloat(a[5]) : 0,
                dy = a.length > 6 ? Float.parseFloat(a[6]) : 0,
                dz = a.length > 7 ? Float.parseFloat(a[7]) : 0;
        int extra = a.length > 8 ? (int) Long.parseLong(a[8].replace("0x", ""), a[8].startsWith("0x") ? 16 : 10) : 0;
        MinecraftServer sv = mc.getSingleplayerServer();
        if (sv != null) sv.execute(() -> BigFx.send(sv.overworld(), kind, fx, fy, fz, size, dx, dy, dz, extra));
    }

    /** true while still waiting for the move */
    private static boolean atMove(Minecraft mc, String s) {
        String[] a = s.substring(7).trim().split("\\s+");
        int want = a[0].equals("none") ? 0 : Moves.byName(a[0]);
        float at = Float.parseFloat(a[1]);
        HollowbellEntity near = null;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof HollowbellEntity w
                    && (near == null || w.distanceToSqr(mc.player) < near.distanceToSqr(mc.player))) near = w;
        boolean there = near != null && near.moveNow() == want && near.moveT(0f) >= at;
        if (!there && ++atWaited < 800) {
            line--;
            return true;
        }
        if (!there) HollowbellMod.LOG.info("autotest {}: gave up waiting", s);
        atWaited = 0;
        return false;
    }

    /** a fresh creative world, flat unless the script says "world normal" */
    private static void newWorld(Minecraft mc) {
        asked = true;
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        LevelSettings ls = new LevelSettings(
                "hbtest", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
        String type = script.stream()
                .filter(l -> l.startsWith("world "))
                .map(l -> l.substring(6).trim())
                .findFirst()
                .orElse("flat");
        mc.createWorldOpenFlows()
                .createFreshLevel(
                        "hbtest" + System.currentTimeMillis() % 100000,
                        ls,
                        new WorldOptions(20260924L, false, false),
                        ra -> type.equals("flat")
                                ? ra.registryOrThrow(Registries.WORLD_PRESET)
                                        .getHolderOrThrow(WorldPresets.FLAT)
                                        .value()
                                        .createWorldDimensions()
                                : WorldPresets.createNormalWorldDimensions(ra),
                        mc.screen);
    }

    /** what the crosshair is on, to the log */
    private static void logPick(Minecraft mc) {
        BellPick.afterPick(1f);
        for (var en : mc.level.entitiesForRendering())
            if (en instanceof HollowbellEntity hh) {
                var cam = mc.getCameraEntity();
                HollowbellMod.LOG.info(
                        "autotest pick: eye {} look {} in box {} ready {} ray {}",
                        cam.getEyePosition(),
                        cam.getViewVector(1f),
                        hh.bodyBox().contains(cam.getEyePosition()),
                        hh.clientPoseReady(),
                        hh.raycast(cam.getEyePosition(), cam.getViewVector(1f), 100));
            }
        var hb = BellPick.looking();
        HollowbellMod.LOG.info(
                "autotest pick: {} bone {} {}",
                hb != null,
                BellPick.bone,
                hb != null && BellPick.bone >= 0 ? hb.rig.boneNames[BellPick.bone] : "-");
    }

    /** where each one is and how he is, on both sides, to the log */
    private static void logStatus(Minecraft mc) {
        for (var en : mc.level.entitiesForRendering())
            if (en instanceof HollowbellEntity hh)
                HollowbellMod.LOG.info(
                        "autotest client sees him at {} (camera {}) fps {} hurtTime {} hp {}",
                        hh.position(),
                        mc.gameRenderer.getMainCamera().getPosition(),
                        mc.getFps(),
                        hh.hurtTime,
                        hh.getHealth());
        MinecraftServer sv = mc.getSingleplayerServer();
        if (sv != null)
            sv.execute(() -> {
                for (var l : sv.getAllLevels())
                    for (var hb : l.getEntities(ModEntities.HOLLOWBELL, x -> true))
                        HollowbellMod.LOG.info(
                                "autotest status: hp {}/{} pods {} move {} inside {} at {}",
                                hb.healthNow(),
                                hb.healthMax(),
                                hb.podsLeft(),
                                hb.moveNow(),
                                hb.moves().insideCount(),
                                hb.position());
            });
    }

    /** how riding on him is going, to the log */
    private static void logRide(Minecraft mc) {
        SolidCarry c = SolidClient.CARRY;
        String on = c.on == null
                ? "-"
                : ((HollowbellEntity) c.on).rig.boneNames[BellPieces.get().bone[c.on.solidShape().bone[c.frame]]];
        HollowbellMod.LOG.info(
                "autotest ridelog: riding {} frame {} air {} at {} frame fix at most {}"
                        + " unstuck {} by server {} soft pushes {} onGround {}",
                c.riding(),
                on,
                c.air,
                mc.player.position(),
                String.format("%.3f", SolidClient.frameFix),
                SolidClient.unstuck,
                Solid.unstuckServer,
                SolidClient.softPushed,
                mc.player.onGround());
        SolidClient.frameFix = 0;
    }

    private static void standOn(Minecraft mc, String part) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null || mc.level == null) return;
        // the spot is found as this game draws him: his pods and arms swing a little differently on the server
        var l = mc.level.getEntitiesOfClass(
                HollowbellEntity.class, mc.player.getBoundingBox().inflate(3000));
        if (l.isEmpty()) return;
        HollowbellEntity m = l.get(0);
        var rig = m.rig;
        var pc = BellPieces.get();
        int[] slices = switch (part) {
            case "crown" -> new int[] {pc.first[rig.crownBone]};
            case "side" -> new int[] {pc.first[rig.index.get("bell_6")], pc.first[rig.index.get("bell_7")]};
            case "spot" -> new int[] {pc.first[rig.index.get("spot_0")], pc.first[rig.index.get("spot_1")]};
            case "pod" -> new int[] {pc.first[rig.pods[11].bone()], pc.first[rig.pods[0].bone()]};
            case "arm" ->
                new int[] {
                    pc.first[rig.arms[2].bones()[0]] + 2, pc.first[rig.arms[2].bones()[0]] + 4
                };
            default -> new int[] {pc.first[rig.index.get("bell_2")], pc.first[rig.index.get("bell_3")]};
        };
        Vec3 at = null;
        for (int b : slices) {
            at = m.topOfSlice(b);
            if (at != null) break;
        }
        if (at == null) {
            HollowbellMod.LOG.warn("autotest standon {}: no spot", part);
            return;
        }
        Vec3 spot = at;
        Vec3 look = m.position().add(0, 150 * m.bellScale(), 0).subtract(at);
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z)) + 150f;
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var pl = srv.getPlayerList().getPlayers().get(0);
            pl.getAbilities().flying = false;
            pl.onUpdateAbilities();
            pl.teleportTo(pl.serverLevel(), spot.x, spot.y + 0.05, spot.z, yaw, 35f);
            pl.setDeltaMovement(Vec3.ZERO);
        });
        HollowbellMod.LOG.info("autotest standon {}: at {}", part, at);
    }

    private static void view(Minecraft mc, String args) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        if (args.startsWith("cow ")) {
            cowView(srv, args.substring(4).trim().split("\\s+"));
            return;
        }
        if (args.startsWith("carry ")) {
            carryView(srv, args.substring(6).trim().split("\\s+"));
            return;
        }
        // arm <a> side|across|end <blocks>: close on the end of one of his arms, as it is posed now
        if (args.startsWith("arm ")) {
            armView(srv, args.substring(4).trim().split("\\s+"));
            return;
        }
        String[] a = args.split("\\s+");
        float[] v = new float[6];
        for (int i = 0; i < 6; i++) v[i] = Float.parseFloat(a[i]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel()
                    .getEntitiesOfClass(
                            HollowbellEntity.class, player.getBoundingBox().inflate(3000));
            if (l.isEmpty()) {
                HollowbellMod.LOG.info("autotest view: none near");
                return;
            }
            var m = l.get(0);
            var cam = m.toWorld(new Vector3f(v[0], v[1], v[2]));
            var tgt = m.toWorld(new Vector3f(v[3], v[4], v[5]));
            var d = tgt.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            // the camera hangs where it's put, even when it stops following him
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static void armView(MinecraftServer srv, String[] a) {
        int arm = Integer.parseInt(a[0]);
        String mode = a[1];
        float dist = Float.parseFloat(a[2]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel()
                    .getEntitiesOfClass(
                            HollowbellEntity.class, player.getBoundingBox().inflate(3000));
            if (l.isEmpty()) return;
            var m = l.get(0);
            var def = m.rig.arms[arm];
            int b = def.bones()[def.bones().length - 1];
            Vector3f piv = def.joints()[def.joints().length - 1];
            // the arm's end: its block furthest from the last joint
            BellModel model = BellModel.get();
            Vector3f end = new Vector3f(piv);
            float best = -1;
            for (int i = 0; i < model.count(b); i++) {
                Vector3f v = new Vector3f(model.x[b][i] + 0.5f, model.y[b][i] + 0.5f, model.z[b][i] + 0.5f);
                if (v.distanceSquared(piv) > best) {
                    best = v.distanceSquared(piv);
                    end = v;
                }
            }
            float s = m.bellScale();
            Vec3 p = m.boneWorld(b, end),
                    q = m.boneWorld(b, piv),
                    u = p.subtract(q).normalize();
            Vec3 across = u.cross(new Vec3(0, 1, 0));
            if (across.lengthSqr() < 1e-4) across = new Vec3(1, 0, 0);
            across = across.normalize();
            Vec3 other = u.cross(across).normalize();
            Vec3 tgt = p.subtract(u.scale(3 * s));
            Vec3 cam = switch (mode) {
                case "across" -> tgt.add(other.scale(dist * s));
                case "end" -> p.add(u.scale(dist * s));
                default -> tgt.add(across.scale(dist * s));
            };
            var d = tgt.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static float carryTheta = 0f;

    private static void carryView(MinecraftServer srv, String[] a) {
        float dist = Float.parseFloat(a[0]),
                height = Float.parseFloat(a[1]),
                out = a.length > 2 ? Float.parseFloat(a[2]) : 50f;
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel()
                    .getEntitiesOfClass(
                            HollowbellEntity.class, player.getBoundingBox().inflate(3000));
            if (l.isEmpty()) return;
            var m = l.get(0);
            if (m.moveNow() == Moves.GRAB && m.moveArg() >= 0) {
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
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static void cowView(MinecraftServer srv, String[] a) {
        double dx = Double.parseDouble(a[0]), dy = Double.parseDouble(a[1]), dz = Double.parseDouble(a[2]);
        srv.execute(() -> {
            if (srv.getPlayerList().getPlayers().isEmpty()) return;
            var player = srv.getPlayerList().getPlayers().get(0);
            var l = player.serverLevel()
                    .getEntitiesOfClass(Cow.class, player.getBoundingBox().inflate(500));
            if (l.isEmpty()) return;
            var c = l.get(0).position().add(0, 0.7, 0);
            var cam = c.add(dx, dy, dz);
            var d = c.subtract(cam).normalize();
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) -Math.toDegrees(Math.asin(d.y));
            player.teleportTo(player.serverLevel(), cam.x, cam.y, cam.z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
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
            var w = WorldOne.get(srv);
            var l = srv.overworld();
            double x = w.homeX() + dx, z = w.homeZ() + dz, tx = w.homeX() + lx, tz = w.homeZ() + lz;
            l.getChunk(Mth.floor(x) >> 4, Mth.floor(z) >> 4);
            l.getChunk(Mth.floor(tx) >> 4, Mth.floor(tz) >> 4);
            int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            int ty = l.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(tx), Mth.floor(tz));
            double y = gy + up;
            double ddx = tx - x, ddz = tz - z, ddy = ty - y, len = Math.max(1e-3, Math.sqrt(ddx * ddx + ddz * ddz));
            float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz)),
                    pitch = (float) -Math.toDegrees(Math.atan2(ddy, len));
            player.teleportTo(l, x, y, z, yaw, pitch);
            player.setNoGravity(true);
            player.setDeltaMovement(Vec3.ZERO);
            HollowbellMod.LOG.info(
                    "autotest ground: at {} {} {} looking at {} {} {} (the ground's middle is {} {}, radius {})",
                    (int) x,
                    (int) y,
                    (int) z,
                    (int) tx,
                    ty,
                    (int) tz,
                    w.homeX(),
                    w.homeZ(),
                    w.homeRadius());
        });
    }

    private static void edgeView(Minecraft mc, String[] a) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        double ang = Math.toRadians(Double.parseDouble(a[0])), out = Double.parseDouble(a[1]);
        String up = a[2];
        srv.execute(() -> {
            var snap = BellGen.SNAP;
            if (snap == null || !snap.claimed) return;
            double c = Math.cos(ang), sn = Math.sin(ang);
            double edge = 0;
            for (double d = 0; d < snap.radius; d += 4)
                if (snap.outline.dn((int) (snap.cx + c * d), (int) (snap.cz + sn * d)) < 1) edge = d;
            // edge <angle> <out> <up> [in] [side]: the camera <out> blocks past the edge (and <side> along it), looking
            // at a spot <in> blocks inside it
            double in = a.length > 3 ? Double.parseDouble(a[3]) : 90,
                    side = a.length > 4 ? Double.parseDouble(a[4]) : 0;
            double cam = edge + out, look = edge - in;
            double camX = c * cam - sn * side, camZ = sn * cam + c * side;
            HollowbellMod.LOG.info("autotest edge: at {} degrees the ground reaches {} blocks", a[0], (int) edge);
            mc.execute(() -> groundView(mc, new String[] {
                String.valueOf(camX), String.valueOf(camZ), up, String.valueOf(c * look), String.valueOf(sn * look)
            }));
        });
    }

    private static void cmdLog(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty()
                    ? null
                    : srv.getPlayerList().getPlayers().get(0);
            CommandSource logger = new CommandSource() {
                @Override
                public void sendSystemMessage(Component m) {
                    HollowbellMod.LOG.info("autotest said: {}", m.getString());
                    if (player != null) player.sendSystemMessage(m);
                }

                @Override
                public boolean acceptsSuccess() {
                    return true;
                }

                @Override
                public boolean acceptsFailure() {
                    return true;
                }

                @Override
                public boolean shouldInformAdmins() {
                    return false;
                }
            };
            var src = player != null
                    ? new CommandSourceStack(
                            logger,
                            player.position(),
                            player.getRotationVector(),
                            player.serverLevel(),
                            4,
                            player.getName().getString(),
                            player.getDisplayName(),
                            srv,
                            player)
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
            var biome =
                    l.getBiome(at).unwrapKey().map(k -> k.location().toString()).orElse("?");
            int ores = 0, ours = 0, cols = 0;
            Map<String, Integer> tops = new TreeMap<>();
            for (int x = at.getX() - 24; x <= at.getX() + 24; x++)
                for (int z = at.getZ() - 24; z <= at.getZ() + 24; z++) {
                    int top = l.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    var b = l.getBlockState(new BlockPos(x, top, z));
                    tops.merge(BuiltInRegistries.BLOCK.getKey(b.getBlock()).getPath(), 1, Integer::sum);
                    if (HomeGround.isOurs(b)) ours++;
                    cols++;
                    for (int y = 0; y < top - 4; y++) {
                        var s = l.getBlockState(new BlockPos(x, y, z));
                        if (s.is(BlockTags.COAL_ORES)
                                || s.is(BlockTags.IRON_ORES)
                                || s.is(BlockTags.COPPER_ORES)
                                || s.is(BlockTags.GOLD_ORES)
                                || s.is(BlockTags.REDSTONE_ORES)
                                || s.is(BlockTags.LAPIS_ORES)
                                || s.is(BlockTags.DIAMOND_ORES)) ores++;
                    }
                }
            HollowbellMod.LOG.info(
                    "autotest groundcheck at {}: biome {}, {} of {} tops are his, {} ore blocks from y 0 up; tops {}",
                    at,
                    biome,
                    ours,
                    cols,
                    ores,
                    tops);
        });
    }

    /** armsonly, landtime and landhash: 0 not one of them, 1 done (go on to the next line), 2 wait from here */
    private static int measure(Minecraft mc, String s) {
        // armsonly on|off: his strands, pods and eggs left out of the picture
        if (s.startsWith("armsonly ")) {
            DevView.armsOnly = s.endsWith("on");
            return 1;
        }
        // landtime r: from now, how long until the land r chunks round the player is all loaded, to the log
        if (s.startsWith("landtime ")) {
            landR = Integer.parseInt(s.substring(9).trim());
            landFrom = System.nanoTime();
            return 2;
        }
        // landhash r name: a hash of every block of each chunk r chunks round the player, to the log
        if (s.startsWith("landhash ")) {
            String[] a = s.substring(9).trim().split("\s+");
            landHash(mc, Integer.parseInt(a[0]), a.length > 1 ? a[1] : "");
            return 1;
        }
        return 0;
    }

    /** all the land round the player is in on the server and in this game: the time it took, and the worst tick */
    private static boolean landDone(Minecraft mc) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return true;
        // (what this game has been sent: the server sends a chunk once it's made)
        int px = mc.player.getBlockX() >> 4, pz = mc.player.getBlockZ() >> 4;
        boolean all = true;
        for (int cx = px - landR; cx <= px + landR && all; cx++)
            for (int cz = pz - landR; cz <= pz + landR && all; cz++)
                if (mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) == null) all = false;
        long ms = (System.nanoTime() - landFrom) / 1_000_000L;
        if (!all && ms < 180_000L) return false;
        long worst = 0;
        for (long t : srv.getTickTimesNanos()) worst = Math.max(worst, t);
        HollowbellMod.LOG.info(
                "autotest landtime: {} chunks out, {} ms ({}), worst of the last 100 ticks {} ms",
                landR,
                ms,
                all ? "all in" : "gave up",
                worst / 1_000_000L);
        landR = -1;
        return true;
    }

    private static void landHash(Minecraft mc, int r, String name) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null || mc.player == null) return;
        int px = mc.player.getBlockX() >> 4, pz = mc.player.getBlockZ() >> 4;
        srv.execute(() -> {
            var sl = srv.getPlayerList().getPlayers().get(0).serverLevel();
            long total = 0;
            int n = 0;
            for (int cx = px - r; cx <= px + r; cx++)
                for (int cz = pz - r; cz <= pz + r; cz++) {
                    var c = sl.getChunkSource().getChunkNow(cx, cz);
                    if (c == null) continue;
                    long h = 1469598103934665603L;
                    BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
                    for (int y = sl.getMinBuildHeight(); y < sl.getMaxBuildHeight(); y++)
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++) {
                                BlockState st = c.getBlockState(m.set(x, y, z));
                                // (what changes on its own after the land is made is left out: water and lava
                                // running, grass and snow spreading, plants and leaves)
                                if (st.isAir()
                                        || !st.getFluidState().isEmpty()
                                        || st.isRandomlyTicking()
                                        || st.canBeReplaced()
                                        || st.is(Blocks.DIRT)) continue;
                                h ^= Block.getId(st) * 31L + y * 7919L + x * 131L + z;
                                h *= 1099511628211L;
                            }
                    HollowbellMod.LOG.info("autotest landhash {} chunk {} {}: {}", name, cx, cz, Long.toHexString(h));
                    total = total * 31 + h;
                    n++;
                }
            HollowbellMod.LOG.info("autotest landhash {}: {} chunks, all {}", name, n, Long.toHexString(total));
        });
    }

    private static void run(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty()
                    ? null
                    : srv.getPlayerList().getPlayers().get(0);
            var src = player != null
                    ? player.createCommandSourceStack().withPermission(4)
                    : srv.createCommandSourceStack();
            HollowbellMod.LOG.info("autotest cmd: {}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }
}
