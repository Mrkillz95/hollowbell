package net.jj.giants.client.dev;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.client.GuideScreen;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Development only (-Djj_giants.autotest=<script>), the same idea as the giants' own: makes a creative flat world,
 * runs a list of lines with waits between them, takes screenshots, then quits. Never runs in a normal game.
 * Lines: join HOST:PORT (a real server instead), wait N, cmd ... (as an operator, own world only), chat /..., guiscale N,
 * gui on|off, guide KEY TAB [SCROLL], close, shot NAME, quit.
 */
public final class AutoTest {
    private static List<String> script;
    private static int line, wait, joined;
    private static boolean asked;

    public static void init() {
        String f = System.getProperty("jj_giants.autotest");
        if (System.getenv("GUIDE_SCRIPT") != null && !System.getenv("GUIDE_SCRIPT").isEmpty()) f = System.getenv("GUIDE_SCRIPT");
        try { script = new ArrayList<>(Files.readAllLines(Path.of(f))); }
        catch (Exception e) { GiantsGuideMod.LOG.error("autotest: cannot read {}", f, e); return; }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (mc.level == null) {
            if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) { mc.setScreen(new TitleScreen()); return; }
            if (!asked && mc.screen instanceof TitleScreen) {
                asked = true;
                // "join host:port": play on a real server instead of a new world of its own
                String join = script.stream().filter(l -> l.startsWith("join ")).map(l -> l.substring(5).trim()).findFirst().orElse(null);
                if (join != null) {
                    GiantsGuideMod.LOG.info("autotest: joining {}", join);
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(mc.screen, mc,
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(join),
                            new net.minecraft.client.multiplayer.ServerData("guide test", join, net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                    return;
                }
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings ls = new LevelSettings("guidetest", GameType.CREATIVE, false, Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("guidetest" + System.currentTimeMillis() % 100000, ls, new WorldOptions(20261001L, false, false),
                        ra -> ra.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
            }
            return;
        }
        if (mc.player == null) return;
        if (++joined < 40) {
            // no "move with W, A, S and D" in the pictures
            mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            mc.getToasts().clear();
            return;
        }
        if (wait > 0) { wait--; return; }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("join ")) continue;
            // chat /...: typed the way a player types it (on a real server: as that player, with their own rights)
            if (s.startsWith("chat /")) { GiantsGuideMod.LOG.info("autotest chat: {}", s.substring(5)); mc.player.connection.sendCommand(s.substring(6).trim()); continue; }
            if (s.startsWith("wait ")) { wait = Integer.parseInt(s.substring(5).trim()); return; }
            if (s.startsWith("cmd ")) { run(mc, s.substring(4).trim()); continue; }
            if (s.startsWith("guiscale ")) { mc.options.guiScale().set(Integer.parseInt(s.substring(9).trim())); mc.resizeDisplay(); continue; }
            if (s.startsWith("gui ")) { mc.options.hideGui = !s.endsWith("on"); continue; }
            if (s.startsWith("guide ")) {
                String[] a = s.substring(6).trim().split("\\s+");
                GuideScreen.showPage(a[0], Integer.parseInt(a[1]), a.length > 2 ? Double.parseDouble(a[2]) : 0);
                mc.setScreen(new GuideScreen());
                continue;
            }
            if (s.equals("close")) { mc.setScreen(null); continue; }
            // log guides: how many Giants Guides the player has (the first-join book on a real server)
            if (s.equals("log guides")) {
                int n = 0;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++)
                    if (mc.player.getInventory().getItem(i).is(GiantsGuideMod.GUIDE)) n += mc.player.getInventory().getItem(i).getCount();
                GiantsGuideMod.LOG.info("autotest guides: {} as {}", n, mc.player.getGameProfile().getName());
                continue;
            }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> GiantsGuideMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.equals("quit")) { GiantsGuideMod.LOG.info("autotest done"); mc.stop(); return; }
        }
    }

    private static void run(Minecraft mc, String cmd) {
        MinecraftServer srv = mc.getSingleplayerServer();
        if (srv == null) return;
        String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        srv.execute(() -> {
            var player = srv.getPlayerList().getPlayers().isEmpty() ? null : srv.getPlayerList().getPlayers().get(0);
            var src = player != null ? player.createCommandSourceStack().withPermission(4) : srv.createCommandSourceStack();
            GiantsGuideMod.LOG.info("autotest cmd: {}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }
}
