package net.jj.giants;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Every giant the guide knows. Adding one is one entry here plus his lines in the language file
 * (guide.jj_giants.KEY.name, .role, .size, .about.N, .where.N, .fight.N, .drops.N, .commands.N and a copy of each
 * move's name and what it does) and a 64x64 picture at textures/gui/giant/KEY.png.
 */
public final class Giants {
    public static final int LIGHT = 0, MEDIUM = 1, HEAVY = 2;
    public static final String[] TIER_NAMES = {"light", "medium", "heavy", "sea"};
    /** Wreckback's fourth kind: the sea moves he only does in deep water */
    public static final int SEA = 3;
    /** the pages every giant has, in the order of the tabs (moves is made from the list above, not from lines) */
    public static final String[] PAGES = {"about", "where", "moves", "fight", "drops", "commands"};
    /** the Swarmforge's pages: an Army tab after his moves, for his robots and units */
    private static final String[] SWARMFORGE_PAGES = {"about", "where", "moves", "army", "fight", "drops", "commands"};

    /** the tabs this giant has (the Swarmforge has one more, his army) */
    public static String[] pages(Giant g) { return g != null && g.key().equals("swarmforge") ? SWARMFORGE_PAGES : PAGES; }
    /** most numbered lines one page can have */
    public static final int MAX_LINES = 80;

    public static final List<Giant> ALL = List.of(
            new Giant("pitchgut", "mountain_breathes", "pitchgut", "net.jj.mountain.GiantsBridge",
                    moves("attack.mountain_breathes.", "codex.mountain_breathes.move_tip.",
                            new String[]{"goo_lob", "leg_sweep", "hand_slam", "darts", "leg_stomp"},
                            new String[]{"gaze", "body_slam", "vomit", "tentacle_eruption", "loose_eyes", "scream", "tongue", "belly_crush"},
                            new String[]{"goo_storm", "eye_storm", "draw_in", "tear_off", "quake", "tentacle_field"}),
                    Set.of("furrowmaw", "cerberus", "wreckback", "swarmforge")),
            new Giant("furrowmaw", "furrowmaw", "furrowmaw", "net.jj.furrowmaw.GiantsBridge",
                    moves("attack.furrowmaw.", "codex.furrowmaw.move_tip.",
                            new String[]{"bite", "spit", "tail_flick", "dust"},
                            new String[]{"slam", "swallow", "charge", "shriek", "vent", "tail_whip", "tremor", "thrash", "coil", "breach=codex.furrowmaw.breach_short"},
                            new String[]{"roll", "erupt", "sinkhole", "great_slam", "magma_storm", "rupture", "inhale"}),
                    Set.of("pitchgut", "cerberus", "lanternwillow", "wreckback", "swarmforge")),
            new Giant("cerberus", "fire_ice_cerberus", "cerberus", "net.jj.cerberus.GiantsBridge",
                    moves("attack.fire_ice_cerberus.", "codex.fire_ice_cerberus.move_tip.",
                            new String[]{"bite", "swipe", "tail_sweep", "fireballs", "shake"},
                            new String[]{"flame_breath", "frost_breath", "ice_spikes", "boulder", "stomp", "roar", "cinder_rain", "hail", "howl"},
                            new String[]{"pounce", "charge", "twin_breath", "firestorm", "frost_nova", "frenzy", "quake_slam"}),
                    Set.of("pitchgut", "furrowmaw", "hollowbell", "lanternwillow", "wreckback", "swarmforge")),
            new Giant("hollowbell", "hollowbell", "hollowbell", "net.jj.hollowbell.GiantsBridge",
                    moves("move.hollowbell.", "codex.hollowbell.move_tip.",
                            new String[]{"grab", "harvest", "sting_volley", "strand_lash", "glow_flash"},
                            new String[]{"curtain", "sweep", "arm_slam", "arm_wrap", "pulse_wave", "shed", "spore_cloud", "pod_burst", "egg_rain"},
                            new String[]{"drop", "whirlpool", "sky_dive", "deep_toll", "arm_storm", "stinger_storm", "sun_lances", "undertow"}),
                    Set.of("cerberus")),
            new Giant("lanternwillow", "lanternwillow", "lanternwillow", "net.jj.lanternwillow.GiantsBridge",
                    moves("move.lanternwillow.", "codex.lanternwillow.move_tip.",
                            new String[]{"strand_lash", "root_jab", "mud_spit", "lure_call", "eye_flash", "bite"},
                            new String[]{"swallow", "strand_sweep", "root_stamp", "root_snare", "lantern_burst", "call_the_small", "bog", "seed_cloud"},
                            new String[]{"weeping_storm", "uproot_slam", "devour", "root_web", "lantern_nova", "limb_crash", "root_quake", "drink_the_land", "pull_under"}),
                    Set.of("furrowmaw", "cerberus", "swarmforge")),
            new Giant("wreckback", "wreckback", "wreckback", "net.jj.wreckback.GiantsBridge",
                    moves("move.wreckback.", "codex.wreckback.move_tip.",
                            new String[]{"claw_snip", "feeler_lash", "barnacle_spit", "claw_sweep", "leg_stab", "grapeshot"},
                            new String[]{"claw_clamp", "feeler_grab", "cannon_broadside", "burrow", "gaze", "claw_hammer", "leg_flurry", "scuttle_charge",
                                    "hull_ram", "barnacle_mortar", "kelp_snare", "silt_cloud", "drowned_crew", "ships_bell"},
                            new String[]{"anchor_swing", "mast_sweep", "tide_pull", "shell_slam", "pincer_crush", "cannon_barrage", "feeler_storm", "whirlpool"},
                            new String[]{"tidal_wave", "deeps_grasp", "full_broadside", "maelstrom", "depth_charge", "storm_call", "riptide_charge"}),
                    Set.of("pitchgut", "furrowmaw", "cerberus")),
            new Giant("swarmforge", "swarmforge", "swarmforge", "net.jj.swarmforge.GiantsBridge",
                    moves("move.swarmforge.", "codex.swarmforge.move_tip.",
                            new String[]{"scrap_cannon", "smoke_screen", "spotlight_lock", "rally", "mine_layer", "repair_bot", "barricade"},
                            new String[]{"tread_crush", "crane_swing", "crane_grab", "alarm", "paratroop_pod", "drone_swarm", "deploy_turret",
                                    "shield_drone", "drill_bots", "sniper", "mortar_crawler"},
                            new String[]{"crane_slam", "furnace_blast", "ram_charge", "overclock"}),
                    Set.of("pitchgut", "furrowmaw", "cerberus", "lanternwillow")));

    private Giants() {}

    /** "id" uses the usual keys; "id=key" gives the name its own key (a move his book names differently) */
    private static List<Giant.Move> moves(String name, String what, String[]... tiers) {
        List<Giant.Move> out = new ArrayList<>();
        for (int t = 0; t < tiers.length; t++)
            for (String s : tiers[t]) {
                int eq = s.indexOf('=');
                String id = eq < 0 ? s : s.substring(0, eq);
                out.add(new Giant.Move(id, t, eq < 0 ? name + id : s.substring(eq + 1), what + id));
            }
        return List.copyOf(out);
    }

    public static Giant byKey(String key) {
        for (Giant g : ALL) if (g.key().equals(key)) return g;
        return null;
    }

    public static boolean loaded(Giant g) {
        try { return FabricLoader.getInstance().isModLoaded(g.modId()); } catch (Throwable t) { return false; }
    }

    /** the giants whose mods are loaded here, in the guide's order */
    public static List<Giant> installed() {
        List<Giant> out = new ArrayList<>();
        for (Giant g : ALL) if (loaded(g)) out.add(g);
        return out;
    }

    /** whether the two fight when they meet (either one listing the other is enough) */
    public static boolean fight(Giant a, Giant b) {
        return a != b && (a.fights().contains(b.key()) || b.fights().contains(a.key()));
    }
}
