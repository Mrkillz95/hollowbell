package net.jj.giants.client.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.client.GuideScreen;
import net.jj.giants.client.admin.AdminBookClient;
import net.jj.giants.client.admin.AdminBookScreen;
import net.jj.giants.client.admin.AdminPrefs;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import net.jj.giants.client.perf.ControlsClient;
import net.jj.giants.client.perf.ControlsScreen;
import net.jj.giants.controls.ControlsJson;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen;
import net.jj.giants.client.perf.PerfClient;
import net.jj.giants.client.perf.PerfScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development only (-Djj_giants.autotest=<script>), the same idea as the giants' own: makes a creative flat world, runs
 * a list of lines with waits between them, takes screenshots, then quits. Never runs in a normal game. Lines: join
 * HOST:PORT (a real server instead), wait N, cmd ... (as an operator, own world only), chat /..., guiscale N, gui
 * on|off, guide KEY TAB [SCROLL], perf [SCROLL] [SEARCH], controls [SCROLL] [SEARCH], bind KEYNAME KEY, vanillacontrols, gesturedemo GIANT, mouse X Y, close, shot NAME, quit,
 * adminbook [PAGE] [SCROLL] [SEARCH], adminmode simple|advanced, adminscope world|all, adminintro N|seen|unseen,
 * adminconfirm GIANT ID, render N (chunks), fov N, noclouds, deepflat (a flat world with deep ground, read when it is made).
 */
public final class AutoTest implements ClientModInitializer {
    private static List<String> script;
    private static int line, wait, joined;
    private static boolean asked;
    /** (Part 3) "menu": the script starts at the title screen (no world made for it) until "inworld" */
    private static boolean menu, toWorld;

    @Override
    public void onInitializeClient() {
        if (System.getProperty("jj_giants.autotest") == null) return;
        String f = System.getProperty("jj_giants.autotest");
        if (System.getenv("GUIDE_SCRIPT") != null
                && !System.getenv("GUIDE_SCRIPT").isEmpty()) f = System.getenv("GUIDE_SCRIPT");
        try {
            script = new ArrayList<>(Files.readAllLines(Path.of(f)));
        } catch (Exception e) {
            GiantsGuideMod.LOG.error("autotest: cannot read {}", f, e);
            return;
        }
        menu = script.stream().anyMatch(l -> l.trim().equals("menu"));
        asked = menu;
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        if (menu && !toWorld) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
                return;
            }
            if (mc.screen == null) return;
            if (wait > 0) {
                wait--;
                return;
            }
            runLines(mc);
            return;
        }
        if (mc.level == null) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!asked && mc.screen instanceof TitleScreen) {
                asked = true;
                // "join host:port": play on a real server instead of a new world of its own
                String join = script.stream()
                        .filter(l -> l.startsWith("join "))
                        .map(l -> l.substring(5).trim())
                        .findFirst()
                        .orElse(null);
                if (join != null) {
                    GiantsGuideMod.LOG.info("autotest: joining {}", join);
                    ConnectScreen.startConnecting(
                            mc.screen,
                            mc,
                            ServerAddress.parseString(join),
                            new ServerData("guide test", join, ServerData.Type.OTHER),
                            false,
                            null);
                    return;
                }
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings ls = new LevelSettings(
                        "guidetest",
                        GameType.CREATIVE,
                        false,
                        Difficulty.NORMAL,
                        true,
                        rules,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows()
                        .createFreshLevel(
                                "guidetest" + System.currentTimeMillis() % 100000,
                                ls,
                                new WorldOptions(20261001L, false, false),
                                ra -> {
                                    // "normalworld": an ordinary world (land made as in a real game), for lag hunts
                                    boolean normal = script.stream().anyMatch(l -> l.trim().equals("normalworld"));
                                    var dims = ra.registryOrThrow(Registries.WORLD_PRESET)
                                            .getHolderOrThrow(normal ? WorldPresets.NORMAL : WorldPresets.FLAT)
                                            .value()
                                            .createWorldDimensions();
                                    // "deepflat": a flat world with deep ground (grass at y 36) for the dens
                                    if (normal || !script.stream().anyMatch(l -> l.trim().equals("deepflat")))
                                        return dims;
                                    var flat = (net.minecraft.world.level.levelgen.FlatLevelSource) dims.overworld();
                                    var layers = List.of(
                                            new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                                    1, net.minecraft.world.level.block.Blocks.BEDROCK),
                                            new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                                    96, net.minecraft.world.level.block.Blocks.STONE),
                                            new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                                    3, net.minecraft.world.level.block.Blocks.DIRT),
                                            new net.minecraft.world.level.levelgen.flat.FlatLayerInfo(
                                                    1, net.minecraft.world.level.block.Blocks.GRASS_BLOCK));
                                    var set = flat.settings().withBiomeAndLayers(
                                            layers, flat.settings().structureOverrides(), flat.settings().getBiome());
                                    return dims.replaceOverworldGenerator(
                                            ra, new net.minecraft.world.level.levelgen.FlatLevelSource(set));
                                },
                                mc.screen);
            }
            return;
        }
        if (mc.player == null) return;
        if (++joined < 40) {
            // no "move with W, A, S and D" in the pictures
            mc.getTutorial().setStep(TutorialSteps.NONE);
            mc.getToasts().clear();
            return;
        }
        // (no "goal reached" toasts over the pictures)
        mc.getToasts().clear();
        if (wait > 0) {
            wait--;
            return;
        }
        // (Part 10B) fightphase PHASE MOST: until the staged fight (on this game's own server) is at that phase
        if (fightPhase != null) {
            String now = net.jj.giants.fights.FightScript.phaseForTests();
            if (!fightPhase.equals(now) && --fightMost > 0) return;
            fightPhase = null;
        }
        runLines(mc);
    }

    private static int layoutProblems;

    private static void layoutFound(String where, java.util.Set<String> found) {
        layoutProblems += found.size();
        for (String f : found) GiantsGuideMod.LOG.warn("layout: {}", f);
        GiantsGuideMod.LOG.info("layout: {} looked at, {} overlap(s); {} so far", where, found.size(), layoutProblems);
    }

    private static String fightPhase;
    private static int fightMost;

    /** the first button on the open screen whose words start so */
    private static boolean press(Minecraft mc, String text) {
        if (mc.screen == null) return false;
        for (var c : mc.screen.children())
            if (c instanceof net.minecraft.client.gui.components.AbstractButton b && b.visible
                    && b.getMessage().getString().startsWith(text)) {
                b.onPress();
                return true;
            }
        return false;
    }

    private static void runLines(Minecraft mc) {
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#") || s.startsWith("join ") || s.equals("menu")) continue;
            // (Part 3, menu scripts) the title screen's way into a new world
            if (s.equals("inworld")) {
                toWorld = true;
                return;
            }
            if (s.equals("createworld")) {
                net.minecraft.client.gui.screens.worldselection.CreateWorldScreen.openFresh(mc, mc.screen);
                return;
            }
            if (s.startsWith("cwtab ")) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cw) {
                    try {
                        java.lang.reflect.Field f = cw.getClass().getDeclaredField("tabNavigationBar");
                        f.setAccessible(true);
                        ((net.minecraft.client.gui.components.tabs.TabNavigationBar) f.get(cw))
                                .selectTab(Integer.parseInt(s.substring(6).trim()), false);
                    } catch (ReflectiveOperationException e) {
                        GiantsGuideMod.LOG.warn("autotest: no tab bar: {}", e.toString());
                    }
                } else GiantsGuideMod.LOG.warn("autotest: not on Create World for {}", s);
                continue;
            }
            // cwcommands: the new world allows commands (so the player is an operator in it, as "Allow Commands")
            if (s.equals("cwcommands")) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cw)
                    cw.getUiState().setAllowCommands(true);
                continue;
            }
            if (s.startsWith("cwname ")) {

                if (mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen cw)
                    cw.getUiState().setName(s.substring(7).trim());
                continue;
            }
            if (s.startsWith("press ")) {
                if (!press(mc, s.substring(6).trim()))
                    GiantsGuideMod.LOG.warn("autotest: no button {} on {}", s.substring(6).trim(), mc.screen);
                return;
            }
            if (s.startsWith("pick ")) {
                if (!net.jj.giants.client.admin.CreationScreen.testPick(s.substring(5).trim()))
                    GiantsGuideMod.LOG.warn("autotest: can't pick {}", s);
                continue;
            }
            if (s.equals("edit")) {
                if (!net.jj.giants.client.admin.CreationScreen.testEdit()) GiantsGuideMod.LOG.warn("autotest: no picker");
                continue;
            }
            // confirmyes: an "are you sure" screen open (the experimental world settings warning): its first button
            if (s.equals("confirmyes")) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen cs) {
                    GiantsGuideMod.LOG.info("autotest: confirming {}", cs.getTitle().getString());
                    for (var c : cs.children())
                        if (c instanceof net.minecraft.client.gui.components.AbstractButton b) {
                            b.onPress();
                            break;
                        }
                } else GiantsGuideMod.LOG.info("autotest: nothing to confirm ({})", mc.screen);
                continue;
            }
            if (s.equals("back")) {

                if (mc.screen != null) mc.screen.onClose();
                continue;
            }
            // showpage PAGE [SCROLL] [SEARCH]: the open book (a draft's too) turned to that page
            if (s.startsWith("showpage ")) {
                String[] a = s.substring(9).trim().split(" +", 3);
                AdminBookScreen.showAt(a[0], a.length > 1 ? Double.parseDouble(a[1]) : 0, a.length > 2 ? a[2] : "");
                if (mc.screen != null) mc.screen.init(mc, mc.getWindow().getGuiScaledWidth(),
                        mc.getWindow().getGuiScaledHeight());
                continue;
            }

            // chat /...: typed the way a player types it (on a real server: as that player, with their own rights)
            if (s.startsWith("chat /")) {
                GiantsGuideMod.LOG.info("autotest chat: {}", s.substring(5));
                mc.player.connection.sendCommand(s.substring(6).trim());
                continue;
            }
            if (s.startsWith("fightphase ")) {
                String[] a = s.substring(11).trim().split(" +");
                fightPhase = a[0];
                fightMost = a.length > 1 ? Integer.parseInt(a[1]) : 2400;
                return;
            }
            if (s.startsWith("wait ")) {
                wait = Integer.parseInt(s.substring(5).trim());
                return;
            }
            if (s.startsWith("cmd ")) {
                run(mc, s.substring(4).trim());
                continue;
            }
            // coveplay [cove]: the Wreck Cove Rescue walked as a player walks it (CovePlay); coveplay stop
            if (s.equals("coveplay") || s.startsWith("coveplay ")) {
                String m = s.length() > 8 ? s.substring(9).trim() : "";
                if (m.equals("stop")) CovePlay.phase = -1;
                else CovePlay.start(m);
                continue;
            }
            if (s.startsWith("guiscale ")) {
                mc.options.guiScale().set(Integer.parseInt(s.substring(9).trim()));
                mc.resizeDisplay();
                continue;
            }
            if (s.startsWith("render ")) {
                mc.options.renderDistance().set(Integer.parseInt(s.substring(7).trim()));
                continue;
            }
            // (lagfix) window W H: the game's window that size (a moment later)
            if (s.startsWith("window ")) {
                String[] a = s.substring(7).trim().split(" +");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), Integer.parseInt(a[0]),
                        Integer.parseInt(a[1]));
                continue;
            }
            // (lagfix) layoutbook TAG: every page of the open admin book, each half and section, scrolled through,
            // looked at for overlaps (layout: lines in the log)
            if (s.startsWith("layoutbook ")) {
                String[] a = s.substring(11).trim().split(" +");
                String tag = a[0];
                boolean shots = a.length > 1 && a[1].equals("shots");
                List<String> more = new ArrayList<>();
                for (String k : AdminBookScreen.testPages()) {
                    more.add("layoutpage " + k);
                    more.add("wait 40");
                    more.add("layoutviews " + tag + " " + k);
                    // (shots: one picture of each page, from its top, to look at)
                    if (shots) more.addAll(List.of("adminview general/ 0", "wait 10", "mouse 2 2", "wait 5",
                            "shot layout_" + tag + "_" + k));
                }
                script.addAll(line, more);
                continue;
            }
            // adminscrollto WORDS: the open book scrolled to the heading that starts so
            if (s.startsWith("adminscrollto ")) {
                AdminBookScreen.testScrollTo(s.substring(14).trim());
                continue;
            }
            // adminview HALF/SECTION [SCROLL]: the open book's page on that half and section, scrolled
            if (s.startsWith("adminview ")) {
                String[] a = s.substring(10).trim().split(" +");
                AdminBookScreen.testView(a[0]);
                if (a.length > 1) AdminBookScreen.testScroll(Double.parseDouble(a[1]));
                continue;
            }
            if (s.startsWith("layoutpage ")) {
                AdminBookScreen.testGo(s.substring(11).trim());
                continue;
            }
            if (s.startsWith("layoutviews ")) {
                String where = s.substring(12).trim() + " (" + mc.getWindow().getWidth() + "x"
                        + mc.getWindow().getHeight() + " scale " + mc.getWindow().getGuiScale() + ")";
                java.util.Set<String> found = new java.util.LinkedHashSet<>();
                for (String v : AdminBookScreen.testViews()) {
                    int most = AdminBookScreen.testView(v);
                    for (int at = 0; ; at += Math.max(20, AdminBookScreen.testScroll(at) / 2)) {
                        AdminBookScreen.testScroll(Math.min(at, most));
                        found.addAll(LayoutCheck.check(mc, where + " " + v + " @" + Math.min(at, most)));
                        if (at >= most) break;
                    }
                }
                layoutFound(where, found);
                continue;
            }
            // (lagfix) layoutmodal NAME: the open book's question: every button inside it, none over another
            if (s.startsWith("layoutmodal ")) {
                String where = s.substring(12).trim() + " (" + mc.getWindow().getWidth() + "x"
                        + mc.getWindow().getHeight() + " scale " + mc.getWindow().getGuiScale() + ")";
                List<int[]> m = AdminBookScreen.testModal();
                java.util.Set<String> found = new java.util.LinkedHashSet<>();
                if (m.isEmpty()) found.add(where + ": no question open");
                for (int i = 1; i < m.size(); i++) {
                    int[] b = m.get(i), box = m.get(0);
                    if (b[0] < box[0] || b[2] > box[2] || b[1] < box[1] || b[3] > box[3])
                        found.add(where + ": button " + i + " sticks out of its question");
                    for (int j = i + 1; j < m.size(); j++) {
                        int[] o = m.get(j);
                        if (b[0] < o[2] && o[0] < b[2] && b[1] < o[3] && o[1] < b[3])
                            found.add(where + ": buttons " + i + " and " + j + " overlap");
                    }
                }
                layoutFound(where, found);
                continue;
            }
            // (lagfix) layoutcheck NAME: the open screen (the Guide's pages) looked at for overlaps
            if (s.startsWith("layoutcheck ")) {
                String where = s.substring(12).trim() + " (" + mc.getWindow().getWidth() + "x"
                        + mc.getWindow().getHeight() + " scale " + mc.getWindow().getGuiScale() + ")";
                layoutFound(where, new java.util.LinkedHashSet<>(LayoutCheck.check(mc, where)));
                continue;
            }
            // leakscan: what still holds a thrown-away minigame world (the log)
            if (s.equals("leakscan")) {
                MinecraftServer srv = mc.getSingleplayerServer();
                if (srv != null) srv.execute(() -> {
                    System.gc();
                    var held = net.jj.giants.minigames.MgLevels.stillHeld();
                    GiantsGuideMod.LOG.info("leakscan: {} thrown-away world(s) still held", held.size());
                    for (var l : held)
                        for (String p : net.jj.giants.test.LeakScan.paths(srv, l, 6, 20_000_000))
                            GiantsGuideMod.LOG.info("leakscan path: {}", p);
                });
                continue;
            }
            // simdist N: the simulation distance (the integrated server picks it up next tick)
            if (s.startsWith("simdist ")) {
                mc.options.simulationDistance().set(Integer.parseInt(s.substring(8).trim()));
                continue;
            }
            if (s.startsWith("fov ")) {
                mc.options.fov().set(Integer.parseInt(s.substring(4).trim()));
                continue;
            }
            if (s.equals("deepflat") || s.equals("normalworld")) continue;
            if (s.equals("noclouds")) {
                mc.options.cloudStatus().set(net.minecraft.client.CloudStatus.OFF);
                continue;
            }
            if (s.startsWith("gui ")) {
                mc.options.hideGui = !s.endsWith("on");
                continue;
            }
            if (s.startsWith("guide ")) {
                String[] a = s.substring(6).trim().split("\\s+");
                GuideScreen.showPage(a[0], Integer.parseInt(a[1]), a.length > 2 ? Double.parseDouble(a[2]) : 0);
                mc.setScreen(new GuideScreen());
                continue;
            }
            // perf [SCROLL] [SEARCH...]: the Giants Performance screen, scrolled (and searched)
            if (s.equals("perf") || s.startsWith("perf ")) {
                String[] a = s.substring(4).trim().split(" +", 2);
                PerfScreen.showAt(
                        a.length > 0 && !a[0].isEmpty() ? Double.parseDouble(a[0]) : 0, a.length > 1 ? a[1] : "");
                PerfClient.open(null);
                continue;
            }
            // controls [SCROLL] [SEARCH...]: the same screen's Controls tab
            if (s.equals("controls") || s.startsWith("controls ")) {
                String[] a = s.substring(8).trim().split(" +", 2);
                ControlsScreen.showAt(
                        a.length > 0 && !a[0].isEmpty() ? Double.parseDouble(a[0]) : 0, a.length > 1 ? a[1] : "");
                mc.setScreen(new ControlsScreen(null));
                continue;
            }
            // bind KEYNAME KEY: sets a key the way the Controls tab does (KEY as in options.txt, key.keyboard.space)
            if (s.startsWith("bind ")) {
                String[] a = s.substring(5).trim().split("\\s+");
                KeyMapping km = ControlsClient.mapping(a[0]);
                if (km != null) ControlsClient.bind(km, InputConstants.getKey(a[1]));
                else GiantsGuideMod.LOG.warn("autotest: no key {}", a[0]);
                continue;
            }
            // vanillacontrols: Minecraft's own Controls screen (to see the same keys there)
            if (s.equals("vanillacontrols")) {
                mc.setScreen(new KeyBindsScreen(null, mc.options));
                continue;
            }
            // gesturedemo GIANT: a gesture row for that giant, as if his jar had a giants_controls.json
            if (s.startsWith("gesturedemo ")) {
                String who = s.substring(12).trim();
                ControlsClient.addFile(new ControlsJson.Controls(who, Map.of(), List.of(), List.of(new ControlsJson.Gesture(
                        "triple_crouch_sit", "Triple crouch to sit",
                        "Crouch three times within two seconds while standing on him (on his safe list) to sit down.",
                        "tripleCrouchSit", "key." + who + ".sit_on_giant", true))));
                continue;
            }
            // mouse X Y: puts the pointer there (window pixels), so no tooltip covers the picture
            if (s.startsWith("mouse ")) {
                String[] a = s.substring(6).trim().split("\\s+");
                try {
                    for (String f : new String[] {"xpos", "ypos"}) {
                        java.lang.reflect.Field fd = MouseHandler.class.getDeclaredField(f);
                        fd.setAccessible(true);
                        fd.setDouble(mc.mouseHandler, Double.parseDouble(a[f.equals("xpos") ? 0 : 1]));
                    }
                } catch (ReflectiveOperationException e) {
                    GiantsGuideMod.LOG.warn("autotest: can't move the mouse: {}", e.toString());
                }
                continue;
            }
            // adminbook [PAGE] [SCROLL] [SEARCH...]: the Giants Admin Book (home, all, or a giant's key), asked of the
            // server like the book itself does (so an operator only)
            if (s.equals("adminbook") || s.startsWith("adminbook ")) {
                String[] a = s.substring(9).trim().split(" +", 3);
                AdminBookScreen.showAt(
                        a.length > 0 && !a[0].isEmpty() ? a[0] : "home",
                        a.length > 1 ? Double.parseDouble(a[1]) : 0,
                        a.length > 2 ? a[2] : "");
                AdminBookClient.open();
                continue;
            }
            // adminmode simple|advanced, adminscope world|all, adminintro N|seen|unseen, adminconfirm GIANT ID
            if (s.startsWith("adminmode ")) {
                AdminPrefs.advanced(s.endsWith("advanced"));
                continue;
            }
            if (s.startsWith("adminscope ")) {
                AdminBookScreen.scopeAt(s.endsWith("all"));
                continue;
            }
            if (s.startsWith("adminintro ")) {
                String a = s.substring(11).trim();
                if (a.equals("seen") || a.equals("unseen")) AdminPrefs.seenIntro(a.equals("seen"));
                else AdminBookScreen.introAt(Integer.parseInt(a));
                continue;
            }
            // adminset GIANT ID VALUE: a change as if made with that control on the open book; adminpress: the open
            // question's yes (or the walkthrough's next)
            if (s.startsWith("adminset ")) {
                String[] a = s.substring(9).trim().split(" +", 3);
                if (!AdminBookScreen.testChange(a[0], a[1], a[2])) GiantsGuideMod.LOG.warn("autotest: can't set {}", s);
                continue;
            }
            // adminreq {JSON}: a request as the open book sends it (a preset preview, an undo...)
            if (s.startsWith("adminreq ")) {
                if (!AdminBookScreen.testRequest(s.substring(9).trim()))
                    GiantsGuideMod.LOG.warn("autotest: can't send {}", s);
                continue;
            }
            // adminpressword WORDS: the open question's button whose words start so (Part 2C: "See all changes")
            if (s.startsWith("adminpressword ")) {
                if (!AdminBookScreen.testPressWord(s.substring(15).trim()))
                    GiantsGuideMod.LOG.warn("autotest: no button {}", s);
                continue;
            }
            // (Part 7) funask name|loot|tagedit: a Fun page question open on the book
            if (s.startsWith("funask ")) {
                if (!AdminBookScreen.testFunAsk(s.substring(7).trim())) GiantsGuideMod.LOG.warn("autotest: no {}", s);
                continue;
            }
            // (JJ answers 2) mapview DX DY TIMES: the open book's map dragged by DX, DY gui pixels and zoomed TIMES
            if (s.startsWith("mapview ")) {
                String[] a = s.substring(8).trim().split(" +");
                AdminBookScreen.testMap(Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                continue;
            }
            // (Part 12) editorpick KIND SET: the Editor page's giant and set; importlast: the last code copied, pasted
            // into Import code (its preview opens)
            if (s.startsWith("editorpick ")) {
                String[] a = s.substring(11).trim().split(" +", 2);
                AdminBookScreen.editorPick(a[0], a.length > 1 ? a[1] : "frost");
                continue;
            }
            if (s.equals("importlast")) {
                com.google.gson.JsonObject o = new com.google.gson.JsonObject();
                o.addProperty("do", "import");
                o.addProperty("text", AdminBookClient.lastCopied());
                if (!AdminBookScreen.testRequest(o.toString())) GiantsGuideMod.LOG.warn("autotest: can't import");
                continue;
            }
            if (s.equals("adminpress")) {
                if (!AdminBookScreen.testPress()) GiantsGuideMod.LOG.warn("autotest: nothing to press");
                continue;
            }
            if (s.startsWith("adminconfirm ")) {
                String[] a = s.substring(13).trim().split(" +");
                AdminBookScreen.confirmAt(a[0], a[1]);
                continue;
            }
            // hotbar N: hold what is in hotbar slot N (0-8)
            if (s.startsWith("hotbar ")) {
                if (mc.player != null) mc.player.getInventory().selected = Integer.parseInt(s.substring(7).trim());
                continue;
            }
            if (s.equals("close")) {
                mc.setScreen(null);
                continue;
            }
            // (Part 5) pickchosen N: My Giant's pick screen opens with giant N chosen (-1: none)
            if (s.startsWith("pickchosen ")) {
                net.jj.giants.client.players.PickScreen.startChosen = Integer.parseInt(s.substring(11).trim());
                continue;
            }
            // log guides: how many Giants Guides the player has (the first-join book on a real server)
            if (s.equals("log guides")) {
                int n = 0;
                for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++)
                    if (mc.player.getInventory().getItem(i).is(GiantsGuideMod.GUIDE))
                        n += mc.player.getInventory().getItem(i).getCount();
                GiantsGuideMod.LOG.info(
                        "autotest guides: {} as {}",
                        n,
                        mc.player.getGameProfile().getName());
                continue;
            }
            if (s.startsWith("shot ")) {
                String name = s.substring(5).trim() + ".png";
                Screenshot.grab(
                        mc.gameDirectory,
                        name,
                        mc.getMainRenderTarget(),
                        c -> GiantsGuideMod.LOG.info("autotest shot {}", name));
                return;
            }
            if (s.equals("quit")) {
                GiantsGuideMod.LOG.info("autotest done");
                mc.stop();
                return;
            }
        }
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
            GiantsGuideMod.LOG.info("autotest cmd: {}", c);
            srv.getCommands().performPrefixedCommand(src, c);
        });
    }
}
