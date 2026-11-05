package net.jj.giants.fights;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.fights.FightData.Fighter;
import net.jj.giants.fights.FightData.Setup;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.Spots;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B) Changing a fight script, the same for the book and the commands: each option by name, the giants on each
 * side, saving by name, loading, starting and stopping. Each admin edits a script of their own (kept with the world).
 */
public final class FightEdit {
    private FightEdit() {}

    /** the options and what each may be (for the commands' suggestions and the book) */
    public static final List<String> OPTIONS = List.of("winner", "ending", "break", "length", "arena", "healblocks",
            "rounds", "rest", "roundheal", "spectators", "join", "bets", "music", "countdown", "announcer", "slowmo",
            "bars", "after", "remains", "grow");

    static List<String> values(String option) {
        return switch (option) {
            case "winner" -> List.of("a", "b", "fight");
            case "ending" -> List.of("death", "escape", "caught", "bow");
            case "length" -> List.of("quick", "normal", "epic");
            case "arena" -> List.of("usual", "off", "some", "little");
            case "rounds" -> List.of("1", "3", "5");
            case "spectators" -> List.of("safe", "danger");
            case "after" -> List.of("home", "stay", "turn");
            case "remains" -> List.of("rule", "corpse", "skeleton", "drops");
            case "break" -> List.of("0.1", "0.25", "0.4");
            case "rest" -> List.of("10", "20", "40");
            case "roundheal" -> List.of("0", "0.25", "0.5");
            default -> List.of("on", "off");
        };
    }

    private static boolean onOff(String v) {
        return v.equals("on") || v.equals("true") || v.equals("yes");
    }

    /** one option of this script to this value: what it says, or why not */
    public static AdminServer.Result set(Setup s, String option, String value) {
        String o = option.toLowerCase(Locale.ROOT).trim(), v = value.toLowerCase(Locale.ROOT).trim();
        try {
            switch (o) {
                case "winner", "ending", "length", "arena", "spectators", "after", "remains" -> {
                    if (!values(o).contains(v))
                        return AdminServer.Result.fail(o + " is one of: " + String.join(", ", values(o)) + ".");
                    switch (o) {
                        case "winner" -> s.winner = v;
                        case "ending" -> s.ending = v;
                        case "length" -> s.length = v;
                        case "arena" -> s.arena = v;
                        case "spectators" -> s.spectators = v;
                        case "after" -> s.after = v;
                        default -> s.remains = v;
                    }
                }
                case "rounds" -> {
                    int n = Integer.parseInt(v);
                    if (n != 1 && n != 3 && n != 5) return AdminServer.Result.fail("Rounds are 1, 3 (best of 3) or 5.");
                    s.rounds = n;
                }
                case "break" -> {
                    double d = Double.parseDouble(v.replace("%", ""));
                    if (d > 1) d /= 100;
                    if (d < 0.05 || d > 0.9) return AdminServer.Result.fail("The break point is from 5% to 90%.");
                    s.breakAt = Math.round(d * 100) / 100.0;
                }
                case "rest" -> {
                    int n = Integer.parseInt(v);
                    if (n < 3 || n > 300) return AdminServer.Result.fail("The rest between rounds is 3 to 300 seconds.");
                    s.rest = n;
                }
                case "roundheal" -> {
                    double d = Double.parseDouble(v.replace("%", ""));
                    if (d > 1) d /= 100;
                    if (d < 0 || d > 1) return AdminServer.Result.fail("Healing between rounds is 0% to 100%.");
                    s.roundHeal = Math.round(d * 100) / 100.0;
                }
                case "healblocks", "join", "bets", "music", "countdown", "announcer", "slowmo", "bars", "grow" -> {
                    if (!values(o).contains(v) && !v.equals("true") && !v.equals("false"))
                        return AdminServer.Result.fail(o + " is on or off.");
                    boolean b = onOff(v);
                    switch (o) {
                        case "healblocks" -> s.healBlocks = b;
                        case "join" -> s.join = b;
                        case "bets" -> s.bets = b;
                        case "music" -> s.music = b;
                        case "countdown" -> s.countdown = b;
                        case "announcer" -> s.announcer = b;
                        case "slowmo" -> s.slowmo = b;
                        case "bars" -> s.bars = b;
                        default -> s.grow = b;
                    }
                }
                default -> {
                    return AdminServer.Result.fail("A fight script has: " + String.join(", ", OPTIONS) + ".");
                }
            }
        } catch (NumberFormatException e) {
            return AdminServer.Result.fail(o + " has to be a number.");
        }
        return AdminServer.Result.said(List.of("Fight script: " + o + " " + words(s, o) + "."));
    }

    /** an option as the lines say it */
    static String words(Setup s, String o) {
        return switch (o) {
            case "winner" -> s.winner.equals("fight") ? "let them fight" : "side " + s.winner.toUpperCase(Locale.ROOT);
            case "ending" -> switch (s.ending) {
                case "escape" -> "flees and escapes";
                case "caught" -> "flees and is caught and killed";
                case "bow" -> "surrenders and bows out";
                default -> "to the death";
            };
            case "break" -> Math.round(s.breakAt * 100) + "%";
            case "length" -> s.length;
            case "arena" -> switch (s.arena) {
                case "off" -> "no blocks broken";
                case "some" -> "half the blocks broken";
                case "little" -> "a quarter of the blocks broken";
                default -> "as each giant's own griefing";
            };
            case "rounds" -> s.rounds == 1 ? "one fight" : "best of " + s.rounds;
            case "rest" -> s.rest + " seconds";
            case "roundheal" -> Math.round(s.roundHeal * 100) + "%";
            case "spectators" -> s.spectators.equals("safe") ? "safe" : "in the danger zone";
            case "after" -> switch (s.after) {
                case "stay" -> "the winner stays";
                case "turn" -> "the winner turns on the players near";
                default -> "the winner roars and goes home";
            };
            case "remains" -> s.remains.equals("rule") ? "as the world's rule" : s.remains;
            case "healblocks" -> s.healBlocks ? "on" : "off";
            case "join" -> s.join ? "on" : "off";
            case "bets" -> s.bets ? "on" : "off";
            case "music" -> s.music ? "on" : "off";
            case "countdown" -> s.countdown ? "on" : "off";
            case "announcer" -> s.announcer ? "on" : "off";
            case "slowmo" -> s.slowmo ? "on" : "off";
            case "bars" -> s.bars ? "on" : "off";
            default -> s.grow ? "on" : "off";
        };
    }

    /** the script in a few lines */
    public static List<String> describe(MinecraftServer server, Setup s) {
        List<String> out = new ArrayList<>();
        out.add("Fight script" + (s.name.isEmpty() ? "" : " \"" + s.name + "\"") + ": " + (s.fighters.isEmpty()
                ? "no giants yet (/giants admin fight giant a 1 <kind> [size])" : FightScript.sideNames(s)) + ".");
        for (Fighter f : s.fighters) {
            Giant g = AdminServer.giant(f.kind);
            out.add("  " + f.side.toUpperCase(Locale.ROOT) + ": " + (g == null ? f.kind : AdminServer.nameOf(g))
                    + (f.id.isEmpty() ? " (fresh, size " + AdminSetting.plain(f.size) + ")" : " (one out there)")
                    + (f.health < 0.999 ? ", starts at " + Math.round(f.health * 100) + "%" : "")
                    + (f.moves.isEmpty() ? "" : ", moves: " + String.join(" ", f.moves))
                    + (f.finisher.isEmpty() ? "" : ", finisher: " + f.finisher.replace('_', ' ')));
        }
        out.add("  Winner " + words(s, "winner") + "; loser: " + words(s, "ending") + " at " + words(s, "break")
                + "; " + words(s, "length") + "; " + words(s, "rounds") + (s.rounds > 1 ? " (" + words(s, "rest")
                + " rest, " + words(s, "roundheal") + " healed)" : "") + ".");
        out.add("  Arena: " + words(s, "arena") + (s.healBlocks ? ", grows back after" : "") + ". Players: "
                + words(s, "spectators") + (s.join ? ", may join a side" : "") + (s.bets ? ", bets" : "") + ".");
        List<String> show = new ArrayList<>();
        if (s.music) show.add("music");
        if (s.countdown) show.add("countdown");
        if (s.announcer) show.add("announcer");
        if (s.slowmo) show.add("slow-motion end");
        if (s.bars) show.add("both bars");
        out.add("  Show: " + (show.isEmpty() ? "none" : String.join(", ", show)) + ". After: " + words(s, "after")
                + "; remains " + words(s, "remains") + (s.grow ? "; the winner grows (with Growth on)" : "") + ".");
        return out;
    }

    /** a giant on a side: slot 1 or 2; "none" takes him off; a kind (fresh, at a size) or one out there */
    public static AdminServer.Result giant(MinecraftServer server, Setup s, String side, int slot, String what,
            double size, @Nullable ServerPlayer p) {
        side = side.toLowerCase(Locale.ROOT);
        if (!side.equals("a") && !side.equals("b")) return AdminServer.Result.fail("The sides are a and b.");
        if (slot < 1 || slot > 2) return AdminServer.Result.fail("Each side has slots 1 and 2 (2: tag team).");
        List<Fighter> on = s.side(side);
        Fighter f = slot <= on.size() ? on.get(slot - 1) : null;
        if (what.equalsIgnoreCase("none")) {
            if (f == null) return AdminServer.Result.said(List.of("There was nobody there."));
            s.fighters.remove(f);
            return AdminServer.Result.said(List.of("Side " + side.toUpperCase(Locale.ROOT) + " slot " + slot
                    + ": nobody."));
        }
        Fighter n = new Fighter();
        n.side = side;
        Giant g = AdminServer.giant(what.toLowerCase(Locale.ROOT));
        if (g != null) {
            n.kind = g.key();
            n.size = size > 0 ? size : 0.5;
        } else {
            FunGiants.Found fd = FunGiants.which(server, what.replace('.', ' '), p == null ? null : p.serverLevel(),
                    p == null ? null : p.position());
            if (fd.giant() == null) return AdminServer.Result.fail(fd.why());
            n.kind = fd.kind().key();
            n.id = fd.giant().getStringUUID();
            n.size = FunGiants.sizeOf(fd.giant());
        }
        if (f != null) {
            // (keeps his options when he is the same kind)
            if (f.kind.equals(n.kind)) {
                n.moves = f.moves;
                n.finisher = f.finisher;
            }
            n.health = f.health;
            s.fighters.set(s.fighters.indexOf(f), n);
        } else if (slot == 2 && on.isEmpty()) return AdminServer.Result.fail("Fill slot 1 first.");
        else s.fighters.add(n);
        return AdminServer.Result.said(List.of("Side " + side.toUpperCase(Locale.ROOT) + " slot " + slot + ": "
                + AdminServer.nameOf(AdminServer.giant(n.kind)) + (n.id.isEmpty() ? " (fresh, size "
                + AdminSetting.plain(n.size) + ")" : " (one out there)") + "."));
    }

    static @Nullable Fighter fighter(Setup s, String side, int slot) {
        List<Fighter> on = s.side(side.toLowerCase(Locale.ROOT));
        return slot >= 1 && slot <= on.size() ? on.get(slot - 1) : null;
    }

    /** his moves ("all", or a list of his move ids) */
    public static AdminServer.Result moves(MinecraftServer server, Setup s, String side, int slot, String list) {
        Fighter f = fighter(s, side, slot);
        if (f == null) return AdminServer.Result.fail("There's no giant in that slot.");
        Giant g = AdminServer.giant(f.kind);
        List<String> his = g == null ? List.of() : FightGiants.moves(server, g);
        if (list.equalsIgnoreCase("all")) {
            f.moves = new ArrayList<>();
            return AdminServer.Result.said(List.of(AdminServer.nameOf(g) + " may use all his moves."));
        }
        List<String> want = new ArrayList<>();
        for (String m : list.split("[,\\s]+")) {
            if (m.isBlank()) continue;
            if (!his.contains(m)) return AdminServer.Result.fail(AdminServer.nameOf(g) + " has no move " + m
                    + ". His: " + String.join(", ", his) + ".");
            if (!want.contains(m)) want.add(m);
        }
        if (want.isEmpty()) return AdminServer.Result.fail("Name at least one move (or all).");
        f.moves = want;
        return AdminServer.Result.said(List.of(AdminServer.nameOf(g) + " may use: " + String.join(", ", want)
                .replace('_', ' ') + "."));
    }

    public static AdminServer.Result finisher(MinecraftServer server, Setup s, String side, int slot, String move) {
        Fighter f = fighter(s, side, slot);
        if (f == null) return AdminServer.Result.fail("There's no giant in that slot.");
        Giant g = AdminServer.giant(f.kind);
        if (move.equalsIgnoreCase("own") || move.isEmpty()) {
            f.finisher = "";
            return AdminServer.Result.said(List.of(AdminServer.nameOf(g) + " finishes with his own: "
                    + FightGiants.finisher(server, g).replace('_', ' ') + "."));
        }
        if (g == null || !FightGiants.moves(server, g).contains(move))
            return AdminServer.Result.fail(AdminServer.nameOf(g) + " has no move " + move + ".");
        f.finisher = move;
        return AdminServer.Result.said(List.of(AdminServer.nameOf(g) + " finishes with " + move.replace('_', ' ')
                + "."));
    }

    public static AdminServer.Result health(Setup s, String side, int slot, double share) {
        Fighter f = fighter(s, side, slot);
        if (f == null) return AdminServer.Result.fail("There's no giant in that slot.");
        if (share > 1) share /= 100;
        if (share < 0.05 || share > 1) return AdminServer.Result.fail("Starting health is from 5% to 100%.");
        f.health = Math.round(share * 100) / 100.0;
        return AdminServer.Result.said(List.of(AdminServer.nameOf(AdminServer.giant(f.kind)) + " starts at "
                + Math.round(f.health * 100) + "% health."));
    }

    /** this admin's script saved under a name, at the spot where they stand (facing as they look) */
    public static AdminServer.Result save(MinecraftServer server, ServerPlayer p, String name) {
        String n = name.strip();
        if (n.isEmpty() || n.length() > 32 || !n.matches("[A-Za-z0-9 '\\-]+"))
            return AdminServer.Result.fail("A name is 1-32 letters, numbers, spaces, ' and -.");
        FightData d = FightData.get(server);
        Setup s = d.draft(p.getStringUUID()).copy();
        if (s.fighters.isEmpty()) return AdminServer.Result.fail("Put a giant on each side first.");
        s.name = n;
        s.dim = p.level().dimension().location().toString();
        BlockPos at = p.blockPosition();
        s.x = at.getX() + 0.5;
        s.y = at.getY();
        s.z = at.getZ() + 0.5;
        s.yaw = p.getYRot();
        boolean had = d.s.saved.containsKey(n.toLowerCase(Locale.ROOT));
        d.s.saved.put(n.toLowerCase(Locale.ROOT), s);
        d.draft(p.getStringUUID()).name = n;
        d.setDirty();
        Fights.changed();
        return new AdminServer.Result(true, List.of((had ? "Saved over " : "Saved ") + "\"" + n + "\": "
                + FightScript.sideNames(s) + ", fought here (" + (int) s.x + " " + (int) s.y + " " + (int) s.z
                + "). Replay it from the book, /giants admin fight replay " + n + ", or a schedule (fight:" + n
                + ")."), List.of(p.getGameProfile().getName() + " saved the fight setup \"" + n + "\"."));
    }

    public static AdminServer.Result load(MinecraftServer server, ServerPlayer p, String name) {
        FightData d = FightData.get(server);
        Setup s = d.saved(name);
        if (s == null) return AdminServer.Result.fail("No fight setup called " + name + ".");
        d.s.drafts.put(p.getStringUUID(), s);
        d.setDirty();
        Fights.changed();
        return AdminServer.Result.said(List.of("Loaded \"" + s.name + "\" into your fight script."));
    }

    public static AdminServer.Result delete(MinecraftServer server, String who, String name) {
        FightData d = FightData.get(server);
        Setup s = d.s.saved.remove(name.trim().toLowerCase(Locale.ROOT));
        if (s == null) return AdminServer.Result.fail("No fight setup called " + name + ".");
        d.setDirty();
        Fights.changed();
        return new AdminServer.Result(true, List.of("Deleted \"" + s.name + "\"."), List.of(who
                + " deleted the fight setup \"" + s.name + "\"."));
    }

    /** this admin's script started where they stand or where they look */
    public static AdminServer.Result start(MinecraftServer server, ServerPlayer p, boolean look) {
        Setup s = FightData.get(server).draft(p.getStringUUID());
        BlockPos at = look ? Spots.looked(p, Spots.LOOK) : p.blockPosition();
        if (at == null) return AdminServer.Result.fail("Look at the ground where they should fight (256 blocks at"
                + " most).");
        return FightScript.start(server, p.getGameProfile().getName(), p.getUUID(), s, p.serverLevel(), at,
                p.getViewVector(1f));
    }

    /** the book's request to change the script: {key, value} or {side, slot, giant|kind+size|moves|finisher|health} */
    static AdminServer.Result request(MinecraftServer server, ServerPlayer p, JsonObject o) {
        FightData d = FightData.get(server);
        Setup s = d.draft(p.getStringUUID());
        AdminServer.Result r;
        String side = AdminJson.str(o, "side", 2);
        int slot = (int) AdminJson.num(o, "slot", 1);
        if (o.has("key")) r = set(s, AdminJson.str(o, "key", 16), AdminJson.str(o, "value", 16));
        else if (o.has("giant")) r = giant(server, s, side, slot, AdminJson.str(o, "giant", 64), AdminJson.num(o,
                "size", 0.5), p);
        else if (o.has("size")) {
            Fighter f = fighter(s, side, slot);
            if (f == null || !f.id.isEmpty()) r = AdminServer.Result.fail("That slot has no fresh giant.");
            else {
                f.size = Math.max(0.01, Math.min(4, AdminJson.num(o, "size", 0.5)));
                r = AdminServer.Result.said(List.of());
            }
        } else if (o.has("moves")) r = moves(server, s, side, slot, AdminJson.str(o, "moves", 2000));
        else if (o.has("finisher")) r = finisher(server, s, side, slot, AdminJson.str(o, "finisher", 64));
        else if (o.has("health")) r = health(s, side, slot, AdminJson.num(o, "health", 1));
        else if (o.has("reset")) {
            d.s.drafts.put(p.getStringUUID(), new Setup());
            r = AdminServer.Result.said(List.of("A fresh fight script."));
        } else r = AdminServer.Result.fail("The book sent a change the server can't read.");
        d.setDirty();
        Fights.changed();
        return r;
    }
}
