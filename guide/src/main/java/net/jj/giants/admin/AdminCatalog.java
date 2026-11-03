package net.jj.giants.admin;

import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.jj.giants.admin.AdminSetting.Type;
import org.jetbrains.annotations.Nullable;

/**
 * The guide's own admin settings (giant "all"): the all-giants multipliers, which each giant applies (the ones it
 * lists in its "uses") on top of its own settings, 1 changing nothing; and (Part 5) the player settings, category
 * "players" (ADMIN_BRIDGE.md 20.2), some kept by the Guide and some applied by the giants.
 */
public final class AdminCatalog {
    private AdminCatalog() {}

    public static final String ALL = "all";

    private static AdminSetting mult(String id, String cat, double min, double max, String when, String name,
            String what) {
        JsonPrimitive one = new JsonPrimitive(1.0);
        return new AdminSetting(ALL, id, cat, Type.FLOAT, min, max, 0.05, List.of(), one, one, one, false, when, name,
                what, "admin.jj_giants." + id, "");
    }

    private static final List<AdminSetting> SHARED = List.of(
            mult("all.health", "stats", 0.1, 10, AdminSetting.LIVE, "Health",
                    "Every giant's health times this, on top of his own multiplier."),
            mult("all.damage", "stats", 0.1, 10, AdminSetting.LIVE, "Damage",
                    "Every hit a giant lands times this, on top of his own multiplier."),
            mult("all.speed", "stats", 0.25, 4, AdminSetting.LIVE, "Speed",
                    "How fast every giant walks, runs and swims, times this."),
            mult("all.size", "spawning", 0.1, 4, AdminSetting.NEW_SPAWNS, "Spawn size",
                    "The size each giant comes at (the world's own, eggs, a plain summon), times this. Each giant"
                            + " keeps to his own smallest and biggest."),
            mult("all.cooldowns", "moves", 0.25, 4, AdminSetting.LIVE, "Move waits",
                    "The wait after each move, and the book's wait for it, times this."),
            mult("all.healing", "healing", 0, 10, AdminSetting.LIVE, "Healing",
                    "How fast every giant heals on his own, times this. 0 = no healing on their own."),
            mult("all.windCost", "taming", 0, 4, AdminSetting.LIVE, "Wind cost",
                    "What every order from a giant's book costs his wind, times this."));

    // (Part 5) the player settings

    public static final String PLAYERS = "players";

    /** every giant the Guide knows, as option ids (the kit's choices) */
    public static final List<String> KEYS = List.of("pitchgut", "furrowmaw", "cerberus", "hollowbell",
            "lanternwillow", "wreckback", "swarmforge", "gloamwick");

    private static List<String> with(List<String> first) {
        List<String> l = new ArrayList<>(first);
        l.addAll(KEYS);
        return List.copyOf(l);
    }

    private static AdminSetting flag(String id, boolean on, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(on);
        return new AdminSetting(ALL, id, PLAYERS, Type.BOOL, 0, 1, 1, List.of(), v, v, v, false, AdminSetting.LIVE,
                name, what, "admin.jj_giants." + id, "");
    }

    private static AdminSetting choice(String id, List<String> options, String name, String what) {
        JsonPrimitive v = new JsonPrimitive(options.get(0));
        return new AdminSetting(ALL, id, PLAYERS, Type.CHOICE, 0, options.size() - 1, 1, options, v, v, v, false,
                AdminSetting.LIVE, name, what, "admin.jj_giants." + id, "");
    }

    private static final List<AdminSetting> PLAYER_SETTINGS = List.of(
            flag("all.myGiant", false, "My Giant",
                    "Each player picks a giant the first time they join: they are taken to his ground with their own"
                            + " book of him and his finder, and one of him comes to them as their own."),
            choice("all.rulesPage", List.of("full", "basic", "hidden"), "Rules page",
                    "What the Guide's Rules page shows players: the preset, how dangerous it is and the rules that"
                            + " differ from Classic (full), only the preset and what it means (basic), or nothing."),
            flag("all.votes", true, "Votes",
                    "Admins may open a vote on the next preset; players vote from the Guide or chat."),
            flag("all.boards", true, "Leaderboards", "Leaderboards in the Guide and on signs placed at spawn."),
            flag("all.boardSlayers", true, "Slayers board", "Giants slain, by kind and in all."),
            flag("all.boardOwners", true, "Owners board",
                    "The longest anyone has owned a giant without a break."),
            flag("all.boardFriends", true, "Befriended board", "How many giants each player has owned."),
            flag("all.achievements", true, "Challenges",
                    "The Giants: Challenges advancements, one or two for each preset with a clear goal."),
            flag("all.kitGuide", true, "The Guide on first join",
                    "Every player gets a Giants Guide the first time they join this world."),
            choice("all.kitFinders", List.of("none", "all", "one"), "Finders in the kit",
                    "New players get every installed giant's finder, one (Which finder), or none."),
            choice("all.kitFinder", with(List.of("random")), "Which finder", "The finder \"one\" gives."),
            choice("all.kitArmour", with(List.of("none", "leather", "iron", "diamond")), "Armour in the kit",
                    "A full set put on new players (a giant's name: his armour set)."),
            choice("all.kitBook", with(List.of("none")), "Book in the kit",
                    "That giant's book for a new player, when nobody has it; else left out and they are told."),
            flag("all.sit", true, "Sitting on giants",
                    "Players on a giant's safe list may sit on him (triple crouch or the key)."),
            flag("all.ride", true, "Riding giants",
                    "Players may ride giants (the book's ride order, the helm, a seat)."),
            flag("all.stand", true, "Standing on giants",
                    "Players may stand on a giant's body. Off: anyone standing on him (not riding or sitting) is"
                            + " set down beside him after a second."),
            flag("all.armourPowers", true, "Armour powers",
                    "The giants' armour sets' powers (R and Sneak + R). Off: the armour still protects."),
            choice("all.bossBars", List.of("usual", "on", "off"), "Boss bars",
                    "usual: as each giant's own Boss bars setting. on: every giant shows his. off: none does."),
            choice("all.fightMusic", List.of("usual", "on", "off"), "Fight music",
                    "usual: each player's own choice. on: plays even if a player turned it off (their volume"
                            + " still counts). off: never plays."),
            flag("all.chatMeetings", true, "Meeting news",
                    "The chat lines telling nearby players that two giants met."),
            flag("all.chatMoves", true, "Move notices",
                    "The lines a giant's moves send players: heavy-move warnings and what a move did to you."),
            flag("all.chatBackDown", true, "Backing-down news",
                    "The lines saying a giant had enough of your hits or lets them go."),
            flag("all.books", true, "Giants' books",
                    "Players may make and use the giants' own books. Off: books can't be crafted, aren't given,"
                            + " and give no orders."),
            choice("all.bookOrders", List.of("all", "noattacks"), "Book orders",
                    "noattacks: a book's attack orders (every move, go after, the hunting mood) and attack moves used"
                            + " while riding are refused; following, going, riding, sleeping and the rest still work."),
            flag("all.findersCraftable", true, "Finders craftable",
                    "Off: no giant's finder can be crafted (kits, loot and commands still give them)."));

    /** a player setting (not a multiplier) */
    public static boolean player(AdminSetting s) {
        return s.cat().equals(PLAYERS);
    }

    public static boolean player(String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        for (AdminSetting s : PLAYER_SETTINGS) if (s.id().equals(full)) return true;
        return false;
    }

    /** the player settings (kitGuide's server default is the Guide's config, giveOnFirstJoin) */
    public static List<AdminSetting> players() {
        List<AdminSetting> out = new ArrayList<>();
        for (AdminSetting s : PLAYER_SETTINGS) {
            if (s.id().equals("all.kitGuide")) {
                JsonPrimitive d = new JsonPrimitive(net.jj.giants.GuideConfig.V.giveOnFirstJoin);
                s = new AdminSetting(s.giant(), s.id(), s.cat(), s.type(), s.min(), s.max(), s.step(), s.options(),
                        d, d, s.jar(), false, s.when(), s.name(), s.what(), s.key(), s.group());
            }
            out.add(s);
        }
        return out;
    }

    /** every all-giants setting (multipliers, then the player settings), at its default */
    public static List<AdminSetting> shared() {
        List<AdminSetting> out = new ArrayList<>(SHARED);
        out.addAll(players());
        return out;
    }

    /** the multipliers only (what the giants multiply; the danger rating's numbers) */
    public static List<AdminSetting> multipliers() {
        return SHARED;
    }

    public static @Nullable AdminSetting shared(String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        for (AdminSetting s : shared()) if (s.id().equals(full)) return s;
        return null;
    }
}
