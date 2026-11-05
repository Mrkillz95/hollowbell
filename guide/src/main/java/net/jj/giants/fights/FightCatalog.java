package net.jj.giants.fights;

import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminSetting.Type;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27.3) The giant-fight settings: all-giants settings of the category "systems" (so they
 * have a server default in config/jj_giants_admin/systems.json, presets name them, the book shows them on the Systems
 * page under "Giant fights"), each split for wild fights (no player started them) and owners' fights (a player sent
 * his giant), plus a few for every fight. Every jar value is today's behaviour.
 */
public final class FightCatalog {
    private FightCatalog() {}

    static final String CAT = "systems";

    private static AdminSetting flag(String id, boolean on, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(on);
        return new AdminSetting("all", id, CAT, Type.BOOL, 0, 1, 1, List.of(), v, v, v, false, AdminSetting.LIVE,
                name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting num(String id, Type t, double def, double min, double max, double step, String name,
            String what) {
        JsonPrimitive v = t == Type.INT ? new JsonPrimitive((long) def) : new JsonPrimitive(def);
        return new AdminSetting("all", id, CAT, t, min, max, step, List.of(), v, v, v, false, AdminSetting.LIVE, name,
                what, "admin.jj_giants." + id, "");
    }

    /** the first option is the jar's */
    private static AdminSetting choice(String id, List<String> options, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(options.get(0));
        return new AdminSetting("all", id, CAT, Type.CHOICE, 0, options.size() - 1, 1, options, v, v, v, false,
                AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    /** the two kinds of fight: wild (no player started it) and an owner's (a player sent his giant) */
    public static final List<String> SIDES = List.of("wild", "owned");

    /** one kind of fight's settings, in the book's order */
    private static List<AdminSetting> side(String s) {
        boolean wild = s.equals("wild");
        String p = "all." + s, of = wild ? "a wild fight" : "an owner's fight";
        List<AdminSetting> out = new ArrayList<>();
        out.add(choice(p + "FightTo", wild ? List.of("back_down", "death", "flee") : List.of("death", "back_down",
                "flee"), "Fight to",
                (wild ? "back_down: the loser backs down at Backs down at, as always. " : "death: on to the end, as "
                        + "always (orders aren't backed down from). back_down: the loser backs down at Backs down at. ")
                        + (wild ? "death: no backing down; it ends when one falls. " : "")
                        + "flee: the first to fall under half his health runs."));
        out.add(choice(p + "Escape", List.of("gets_away", "chased"), "Loser's escape",
                "gets_away: the one who backed down always gets away. chased: the winner chases him down (Chased "
                        + "for); caught, he is spared or killed (Caught)."));
        out.add(num(p + "ChaseBlocks", Type.INT, 96, 16, 512, 16, "Chased for",
                "How far the winner chases him, in blocks. Past it, he got away."));
        out.add(choice(p + "Caught", List.of("spared", "killed"), "Caught",
                "spared: the winner lets him go once he has him. killed: the winner finishes him."));
        out.add(choice(p + "Spared", List.of("home", "follow"), "Spared loser",
                "home: he goes home (an owned one to his owner), as always. follow: a wild one follows the winner "
                        + "from then on, one of his herd."));
        out.add(num(p + "Damage", Type.FLOAT, 1, 0.1, 5, 0.05, "Damage between giants",
                "Giants' blows on each other in " + of + ", times this."));
        out.add(choice(p + "Blocks", List.of("usual", "off", "heal"), "Blocks broken",
                "usual: as each giant's own griefing says. off: nothing breaks while giants fight. heal: what they "
                        + "break grows back after the fight."));
        out.add(choice(p + "Heal", List.of("none", "partial", "full"), "Healing after",
                "none: as always (he heals on his own later). partial: both get a third of their health back when "
                        + "it ends. full: both are healed."));
        out.add(num(p + "Rest", Type.INT, 0, 0, 60, 1, "Rest before the next",
                "Minutes before either may fight another giant again (0: as always)."));
        out.add(num(p + "Drift", Type.INT, 0, 0, 1024, 16, "Fight area",
                "The fight may not drift further than this from where it began: they break off and walk back (0: "
                        + "no limit)."));
        out.add(flag(p + "SafeAreas", false, "Keep out of villages",
                "They break off before a fight reaches a village or a safe area (Server tools), and walk back."));
        out.add(choice(p + "Watchers", List.of("usual", "safe"), "Players near",
                "usual: stray blows hit players near. safe: giants fighting each other never hurt a player who isn't "
                        + "in the fight."));
        out.add(flag(p + "Bars", false, "Both boss bars",
                "Players near see both giants' health on one pair of bars, with their names and titles."));
        out.add(choice(p + "Music", List.of("usual", "on", "off"), "Fight music",
                "usual: each player's own choice. on: plays for players near the fight. off: never during it."));
        out.add(flag(p + "News", false, "Fight news",
                "Players near and the owners are told (\"Your Cerberus is fighting the Wreckback\"), owners with a "
                        + "Stand down button."));
        out.add(flag(p + "Grow", false, "Winner grows",
                "With Growth on, a win where the loser backs down counts too (a fall always counts)."));
        out.add(flag(p + "Loyalty", false, "Loyalty up",
                "With Loyalty on, an owned winner's loyalty goes up 10."));
        out.add(flag(p + "Loot", true, "Loser's loot",
                "Off: a giant brought down by another giant drops nothing."));
        return out;
    }

    /** for every fight */
    private static List<AdminSetting> every() {
        return List.of(
                choice("all.ownedMayFight", List.of("always", "both_ok", "pvp", "never"), "Owned giants fight",
                        "May owned giants fight each other: always (as always); both_ok (both owners said yes: "
                                + "/giants fight allow); pvp (only where players may hurt each other); never."),
                flag("all.ownedMayKill", true, "Owned may kill owned",
                        "Off: an owned giant never kills another owned one: the loser backs down at Backs down at."),
                num("all.fightsMost", Type.INT, 0, 0, 32, 1, "Most fights at once",
                        "How many giant fights may go on at once in a world (0: no limit)."),
                choice("all.fightsWhen", List.of("any", "day", "night"), "Fights when",
                        "any: as always. day or night: giants only start fights then."),
                flag("all.fightsSleepers", true, "Sleepers can be fought",
                        "Off: no fight is started with a sleeping giant."));
    }

    /** every giant-fight setting, in the book's order */
    public static final List<AdminSetting> SETTINGS;

    /** the book's groups (a name key and the settings in it) */
    public static final String[][] GROUPS;

    static {
        List<AdminSetting> all = new ArrayList<>();
        List<String[]> groups = new ArrayList<>();
        for (String s : SIDES) {
            List<AdminSetting> one = side(s);
            all.addAll(one);
            String[] g = new String[one.size() + 1];
            g[0] = s + "fights";
            for (int i = 0; i < one.size(); i++) g[i + 1] = one.get(i).id();
            groups.add(g);
        }
        List<AdminSetting> e = every();
        all.addAll(e);
        String[] g = new String[e.size() + 1];
        g[0] = "everyfight";
        for (int i = 0; i < e.size(); i++) g[i + 1] = e.get(i).id();
        groups.add(g);
        SETTINGS = List.copyOf(all);
        GROUPS = groups.toArray(new String[0][]);
    }

    /** the first group of the giant fights (the book starts them under their own heading) */
    public static final String FIRST = "wildfights";

    /** the ones the giants apply themselves (sent with their server default filled in) */
    public static final List<String> GIANTS_APPLY = List.of("all.wildFightTo", "all.ownedFightTo", "all.wildDamage",
            "all.ownedDamage", "all.ownedMayKill", "all.wildLoot", "all.ownedLoot");

    /** with a list in front: the systems' settings then these */
    public static List<AdminSetting> after(List<AdminSetting> before) {
        List<AdminSetting> out = new ArrayList<>(before);
        out.addAll(SETTINGS);
        return List.copyOf(out);
    }

    public static String[][] after(String[][] before) {
        String[][] out = new String[before.length + GROUPS.length][];
        System.arraycopy(before, 0, out, 0, before.length);
        System.arraycopy(GROUPS, 0, out, before.length, GROUPS.length);
        return out;
    }
}
