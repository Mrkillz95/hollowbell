package net.jj.hollowbell;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.jj.hollowbell.entity.Belling;
import net.jj.hollowbell.entity.Giants;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Meeting;
import net.jj.hollowbell.entity.Moves;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B, ADMIN_BRIDGE.md §27) Giant fights, his side: the world's giant-fight settings (how far a wild fight and an
 * owner's fight go, how hard giants' blows land on him, whether an owned giant may kill another, whether a loser
 * killed by a giant drops his loot), the Guide's staged-fight script on him (entity tags: no backing down on his own,
 * blows paced, a health floor, his team, the moves he may use, the one he chases), and a bridge pair for what only he
 * can do ("fights", "fightact"). His Bellings' blows count as his (the jj_giant_of tag), and when he stands down or
 * backs down for the Guide they're called off the giant they're on. His backing down is his own: he climbs up out of
 * reach and flies off (or, bowing out, settles where he is). With every setting at the jar's and no tag on him (as
 * shipped), each hook gives back the very same thing and nothing is asked or told.
 */
public final class GiantsFight {
    private GiantsFight() {}

    public static final int VERSION = 1;
    /** the all-giants settings he applies (listed in his admin header's "uses") */
    public static final String[] USES = {"all.wildFightTo", "all.ownedFightTo", "all.wildDamage", "all.ownedDamage",
        "all.ownedMayKill", "all.wildLoot", "all.ownedLoot", "all.fightAsk", "all.fightReport"};
    static final List<String> DOES = List.of("hp", "health", "fight", "yield", "chase", "stop", "do", "roar");
    private static final String WHO = "Hollowbell";
    /**
     * the move he finishes a staged fight with unless the admin picks another: his sky dive (he climbs, turns over and
     * comes down crown first; the ring that runs out from where he lands is his hardest single blow, and lands on the
     * giant he's fighting wherever its body is under the ring)
     */
    public static final String FINISHER = "sky_dive";

    /** the tags the Guide puts on him for a staged fight (and the owner it mirrors from Live control) */
    public static final String TAG = "jj_giants.fight.", SCRIPT = TAG + "script", TAKEN = TAG + "taken=",
            FLOOR = TAG + "floor=", TEAM = TAG + "team=", MOVES = TAG + "moves=", CHASE = TAG + "chase=",
            OWNER = "jj_giants.owner=", NO_DROPS = GiantsFun.NO_DROPS;

    /** the jar's values: an owner's fight goes on to the end (no backing down from a player's fight), as ever */
    public static final String WILD_TO = "back_down", OWNED_TO = "death";

    private static volatile String wildTo = WILD_TO, ownedTo = OWNED_TO;
    private static volatile float wildDamage = 1f, ownedDamage = 1f;
    private static volatile boolean mayKill = true, wildLoot = true, ownedLoot = true, ask, report;
    /** any setting off the jar's: the hooks have something to do for every one of him */
    private static volatile boolean any;

    /** the switches out of adminset's "all" (absent: as shipped) */
    static void receiveAll(JsonObject all) {
        wildTo = option(all.get("all.wildFightTo"), WILD_TO);
        ownedTo = option(all.get("all.ownedFightTo"), OWNED_TO);
        wildDamage = num(all.get("all.wildDamage"), 1f, 0f, 10f);
        ownedDamage = num(all.get("all.ownedDamage"), 1f, 0f, 10f);
        mayKill = bool(all.get("all.ownedMayKill"), true);
        wildLoot = bool(all.get("all.wildLoot"), true);
        ownedLoot = bool(all.get("all.ownedLoot"), true);
        ask = bool(all.get("all.fightAsk"), false);
        report = bool(all.get("all.fightReport"), false);
        any = !wildTo.equals(WILD_TO) || !ownedTo.equals(OWNED_TO) || wildDamage != 1f || ownedDamage != 1f || !mayKill
                || !wildLoot || !ownedLoot;
    }

    public static void clear() {
        wildTo = WILD_TO;
        ownedTo = OWNED_TO;
        wildDamage = 1f;
        ownedDamage = 1f;
        mayKill = true;
        wildLoot = true;
        ownedLoot = true;
        ask = false;
        report = false;
        any = false;
    }

    private static String option(@Nullable JsonElement v, String or) {
        try {
            String s = v == null || !v.isJsonPrimitive() ? or : v.getAsString().trim().toLowerCase(Locale.ROOT);
            return List.of("back_down", "death", "flee").contains(s) ? s : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    private static boolean bool(@Nullable JsonElement v, boolean or) {
        try {
            return v == null || !v.isJsonPrimitive() ? or : v.getAsBoolean();
        } catch (RuntimeException e) {
            return or;
        }
    }

    private static float num(@Nullable JsonElement v, float or, float lo, float hi) {
        try {
            if (v == null || !v.isJsonPrimitive()) return or;
            float f = v.getAsFloat();
            return Float.isFinite(f) ? Math.max(lo, Math.min(hi, f)) : or;
        } catch (RuntimeException e) {
            return or;
        }
    }

    // his tags

    /** a staged fight's tag on him (any of them) */
    public static boolean tagged(@Nullable Entity e) {
        if (e == null) return false;
        for (String t : e.getTags()) if (t.startsWith(TAG)) return true;
        return false;
    }

    /** anything for the hooks to do for this one: a setting off the jar's, or the Guide's fight tags on him */
    public static boolean active(@Nullable Entity e) {
        return any || tagged(e);
    }

    private static @Nullable String tag(@Nullable Entity e, String head) {
        if (e == null) return null;
        for (String t : e.getTags()) if (t.startsWith(head)) return t.substring(head.length());
        return null;
    }

    private static float tagNum(Entity e, String head, float or, float lo, float hi) {
        String s = tag(e, head);
        if (s == null) return or;
        try {
            float f = Float.parseFloat(s);
            return Float.isFinite(f) ? Math.max(lo, Math.min(hi, f)) : or;
        } catch (NumberFormatException bad) {
            return or;
        }
    }

    /** whom Live control says owns him (the Guide mirrors it onto him while an owned-giant rule needs it) */
    public static @Nullable String owner(@Nullable Entity e) {
        return tag(e, OWNER);
    }

    /** on the same side of a staged fight */
    public static boolean sameTeam(@Nullable Entity a, @Nullable Entity b) {
        String ta = tag(a, TEAM), tb = tag(b, TEAM);
        return ta != null && ta.equals(tb);
    }

    /** he is chasing this one down (a loser who backed down): he may go for it all the same */
    public static boolean chasing(@Nullable Entity self, @Nullable Entity other) {
        String c = tag(self, CHASE);
        return c != null && other != null && c.equals(other.getStringUUID());
    }

    /** a move a staged fight lets him use (no list on him: every move) */
    public static boolean moveOk(@Nullable Entity self, String move) {
        String m = tag(self, MOVES);
        if (m == null) return true;
        for (String s : m.split(",")) if (s.trim().equals(move)) return true;
        return false;
    }

    /** where an owned giant who may not kill another owned one stops: his Backs-down-at (a tenth at least) */
    public static float keep(float yieldAt) {
        return yieldAt > 0.01f ? yieldAt : 0.1f;
    }

    // the hooks

    /**
     * Backing down from a giant's fight: null = the old rule decides (as shipped); else true (he backs down now) or
     * false (not). A staged fight's giant never backs down on his own (the Guide does it). Between two owned giants
     * when owned giants may not kill each other, he backs down at his Backs-down-at. Then the world's "Fight to" for
     * this kind of fight (wild, or an owner's).
     */
    public static @Nullable Boolean yieldNow(LivingEntity self, @Nullable Entity by, boolean ordered, float hp,
            float max, float yieldAt) {
        if (self.getTags().contains(SCRIPT)) return false;
        if (!any || max <= 0f) return null;
        float f = hp / max;
        if (!mayKill && by != null && owner(self) != null && owner(by) != null && f <= keep(yieldAt) + 1e-4f)
            return true;
        // (meetGiants run_away decides a wild fight itself, as before)
        if (!ordered && "run_away".equals(GiantsBehave.meets())) return null;
        String to = ordered ? ownedTo : wildTo;
        return switch (to) {
            case "death" -> ordered ? null : Boolean.FALSE;
            case "flee" -> f < 0.5f;
            default -> ordered ? (yieldAt > 0f && f < yieldAt) : null;
        };
    }

    /**
     * A blow he takes, last of all before it comes off his health: a giant's blow (another giant's helpers' count as
     * their giant's) times the world's giant-fight damage (wild or an owner's fight) and a staged
     * fight's pace; nothing from his own side of a staged fight; never past a staged fight's floor (or, between two
     * owned giants who may not kill, his Backs-down-at); and a loser a giant kills drops nothing when the world says
     * so. Off: the very same number.
     */
    public static float taken(LivingEntity self, DamageSource src, @Nullable LivingEntity byGiant, float dealt,
            float hp, float max, boolean ordered, float yieldAt) {
        if (src.is(DamageTypes.GENERIC_KILL) || !active(self)) return dealt;
        float d = dealt;
        boolean giant = byGiant != null && byGiant != self;
        if (giant) {
            if (sameTeam(self, byGiant)) return 0f;
            float m = ordered ? ownedDamage : wildDamage;
            if (m != 1f) d *= m;
            float pace = tagNum(self, TAKEN, 1f, 0f, 20f);
            if (pace != 1f) d *= pace;
        }
        float floor = tagNum(self, FLOOR, 0f, 0f, 1f);
        if (giant && !mayKill && owner(self) != null && owner(byGiant) != null) floor = Math.max(floor, keep(yieldAt));
        if (floor > 0f && max > 0f) d = Math.min(d, Math.max(0f, hp - floor * max));
        if (giant && d >= hp && !(ordered ? ownedLoot : wildLoot)) self.addTag(NO_DROPS);
        return d;
    }

    /** may he start a fight with this giant now: the Guide is asked while one of its rules needs it */
    public static boolean mayStart(LivingEntity self, LivingEntity other, boolean ordered) {
        return refusal(self, other, ordered, null) == null;
    }

    /**
     * Why an owner's book can't send him at this giant now (the world's rules on owned giants, how many fight at
     * once, day or night...), or null when it may
     */
    public static @Nullable String orderRefusal(LivingEntity self, @Nullable Entity target, ServerPlayer by) {
        LivingEntity g = Giants.ownerOf(target);
        if (g == null || !Giants.isGiant(g)) return null;
        return refusal(self, g, true, by);
    }

    private static @Nullable String refusal(LivingEntity self, LivingEntity other, boolean ordered,
            @Nullable ServerPlayer by) {
        if (!ask || !(self.level() instanceof ServerLevel sl)) return null;
        JsonObject o = head(self, "fight");
        o.addProperty("other", other.getStringUUID());
        o.addProperty("ordered", ordered);
        o.addProperty("by", by == null ? "-" : by.getStringUUID());
        List<String> r = guide(sl.getServer(), "fightok", o);
        if (r == null || r.isEmpty() || r.get(0).equals("ok")) return null;
        return r.size() > 1 ? r.get(1) : "Not now.";
    }

    /** a fight of his began: the Guide is told while it follows fights */
    public static void started(LivingEntity self, LivingEntity other, boolean ordered) {
        news(self, "start", other, ordered, "");
    }

    /** he backed down (how: run, bow): the Guide is told while it follows fights */
    public static void yielded(LivingEntity self, @Nullable Entity winner, boolean ordered, String how) {
        news(self, "yield", winner, ordered, how);
    }

    private static void news(LivingEntity self, String what, @Nullable Entity other, boolean ordered, String how) {
        if (!report || !(self.level() instanceof ServerLevel sl)) return;
        JsonObject o = head(self, "fight");
        o.addProperty("event", what);
        o.addProperty("other", other == null ? "-" : other.getStringUUID());
        o.addProperty("ordered", ordered);
        if (!how.isEmpty()) o.addProperty("how", how);
        guide(sl.getServer(), "fightnews", o);
    }

    private static JsonObject head(LivingEntity self, String key) {
        JsonObject o = new JsonObject();
        o.addProperty(key, VERSION);
        o.addProperty("giant", HollowbellMod.MOD_ID);
        o.addProperty("id", self.getStringUUID());
        return o;
    }

    private static @Nullable List<String> guide(MinecraftServer server, String action, JsonObject o) {
        try {
            Class<?> c = Class.forName("net.jj.giants.GuideBridge", true, GiantsFight.class.getClassLoader());
            Method m = c.getMethod("guide", MinecraftServer.class, String.class, String.class);
            Object r = m.invoke(null, server, action, o.toString());
            if (!(r instanceof List<?> l)) return null;
            return l.stream().map(String::valueOf).toList();
        } catch (ClassNotFoundException e) {
            ask = false; // (no Guide: nobody to ask or tell)
            report = false;
            return null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            HollowbellMod.LOG.debug("Couldn't reach the Guide about a fight: {}", e.toString());
            return null;
        }
    }

    /**
     * His Bellings let go of whatever giant they're on (a stand-down, or backing down for the Guide): a Belling
     * otherwise keeps after a giant it was set on even once he has let go of it. Never anything else they're on.
     */
    public static int callOff(HollowbellEntity h) {
        if (!(h.level() instanceof ServerLevel sl)) return 0;
        String mine = Giants.OF + h.getStringUUID();
        int n = 0;
        for (Belling b : sl.getEntitiesOfClass(Belling.class, h.getBoundingBox().inflate(160),
                b -> b.getTags().contains(mine))) {
            LivingEntity t = b.getTarget();
            if (t == null || !Giants.isGiant(t) && !Giants.isGiant(Giants.ownerOf(t))) continue;
            b.setTarget(null);
            n++;
        }
        return n;
    }

    // the bridge

    /** "fights": the one header line (his moves, his finisher, what fightact does) */
    public static @Nullable List<String> describe(@Nullable MinecraftServer server, String arg) {
        if (server == null) return null;
        try {
            if (arg != null && !arg.isBlank() && Integer.parseInt(arg.trim()) < 1) return null;
        } catch (NumberFormatException e) {
            return null;
        }
        JsonObject h = new JsonObject();
        h.addProperty("fight", VERSION);
        h.addProperty("giant", HollowbellMod.MOD_ID);
        h.addProperty("name", WHO);
        // (his fight moves, by the ids of his move.<id>.on settings: not his harvest, which is no fight move)
        JsonArray moves = new JsonArray();
        for (int m : Moves.ORDER) if (m != Moves.HARVEST) moves.add(Moves.NAMES[m]);
        h.add("moves", moves);
        h.addProperty("finisher", FINISHER);
        JsonArray does = new JsonArray();
        for (String d : DOES) does.add(d);
        h.add("does", does);
        return List.of(h.toString());
    }

    /** "fightact": one thing on one of him; ["ok", line] or ["no", why]; null for what it can't read */
    public static @Nullable List<String> act(@Nullable MinecraftServer server, String arg) {
        if (server == null || arg == null) return null;
        JsonObject o;
        UUID id;
        String what;
        try {
            o = JsonParser.parseString(arg).getAsJsonObject();
            if (o.get("fight").getAsInt() > VERSION) return null;
            id = UUID.fromString(o.get("id").getAsString());
            what = o.get("do").getAsString().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
        if (!DOES.contains(what)) return null;
        HollowbellEntity h = GiantsFun.find(server, id);
        if (h == null) {
            for (Object x : net.jj.hollowbell.world.TakeMe.listed(server))
                if (x instanceof net.jj.hollowbell.world.Away.Rec r && id.equals(r.id)
                        || x instanceof net.jj.hollowbell.world.TakeMe.Lying k && id.equals(k.id()))
                    return no("He's out of the world right now.");
            return no("He isn't there any more.");
        }
        ServerLevel sl = (ServerLevel) h.level();
        LivingEntity foe = foe(sl, o);
        switch (what) {
            case "hp" -> {
                JsonObject j = new JsonObject();
                j.addProperty("hp", h.healthNow());
                j.addProperty("max", h.healthMax());
                j.addProperty("yielding", Meeting.yielding(h));
                j.addProperty("running", h.flee().running());
                LivingEntity t = h.getTarget();
                j.addProperty("target", t == null ? "-" : t.getStringUUID());
                return List.of("ok", j.toString());
            }
            case "health" -> {
                // his health to this share of his most (set), or this share of it more (heal)
                float share = num(o.get("share"), -1f, 0f, 1f);
                if (share < 0f) return null;
                float max = h.healthMax();
                boolean set = bool(o.get("set"), false);
                float to = set ? share * max : Math.min(max, h.healthNow() + share * max);
                h.setHealthTo(Math.max(1f, to));
                return ok(WHO + ": health " + Math.round(h.healthNow() / Math.max(1f, max) * 100) + "%.");
            }
            case "fight" -> {
                if (foe == null) return no("The other giant isn't there.");
                // ready for it again (a staged fight's next round): no longer backed down, running or resting
                h.freshFor(foe.getUUID());
                h.setStay(false);
                // (wild: a wild fight taken up again after it was pulled back; else an owner's fight, as /giants meet)
                boolean wild = bool(o.get("wild"), false);
                return h.startMeeting(foe, !wild) ? ok(WHO + ": he goes for "
                        + Meeting.name(Meeting.kindOf(foe), false) + ".") : no(WHO + ": he can't fight that one now.");
            }
            case "yield" -> {
                // his own backing down, as when he loses a wild fight: he climbs out of reach and flies off (to his
                // owner or the Bell Hollows), or bows out and settles where he is; his Bellings let go of the giant
                if (foe == null) return no("The other giant isn't there.");
                boolean bow = "bow".equals(str(o.get("how")));
                h.backDown(foe);
                if (bow) h.bowOut();
                callOff(h);
                return ok(WHO + (bow ? " bows out." : " backs down."));
            }
            case "chase" -> {
                if (foe == null) return no("The other giant isn't there.");
                for (String t : List.copyOf(h.getTags())) if (t.startsWith(CHASE)) h.removeTag(t);
                h.addTag(CHASE + foe.getStringUUID());
                h.freshFor(foe.getUUID());
                h.setStay(false);
                h.sendAfter(foe);
                return ok(WHO + ": he goes after " + Meeting.name(Meeting.kindOf(foe), false) + ".");
            }
            case "stop" -> {
                for (String t : List.copyOf(h.getTags())) if (t.startsWith(CHASE)) h.removeTag(t);
                h.dropMeeting();
                h.clearHitList();
                h.forgetGiantGrudges(sl);
                // (and his Bellings: they'd keep after the giant they were on)
                callOff(h);
                return ok(WHO + " stands down.");
            }
            case "do" -> {
                int m = Moves.byName(str(o.get("move")));
                if (m < 0) return no(WHO + " has no such move.");
                if (!h.forceMove(m, foe != null ? foe : h.getTarget())) return no(WHO + " can't do that now.");
                return ok(WHO + ": " + Moves.NAMES[m].replace('_', ' ') + ".");
            }
            case "roar" -> {
                // his own show of a win over a giant (as when a meeting fight is won): a big toll and a flare of glow
                h.showWin();
                return ok(WHO + " roars.");
            }
            default -> {
                return null;
            }
        }
    }

    private static @Nullable LivingEntity foe(ServerLevel sl, JsonObject o) {
        try {
            String f = str(o.get("foe"));
            if (f.isEmpty()) f = str(o.get("by"));
            if (f.isEmpty() || f.equals("-")) return null;
            Entity e = sl.getEntity(UUID.fromString(f));
            LivingEntity g = Giants.ownerOf(e);
            return g != null && g.isAlive() ? g : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(@Nullable JsonElement v) {
        try {
            return v == null || !v.isJsonPrimitive() ? "" : v.getAsString();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static List<String> ok(String line) {
        return List.of("ok", line);
    }

    private static List<String> no(String why) {
        return List.of("no", why);
    }

    /** (the tests) the settings as he holds them now */
    public static String stateForTests() {
        return wildTo + "," + ownedTo + "," + wildDamage + "," + ownedDamage + "," + mayKill + "," + wildLoot + ","
                + ownedLoot + "," + ask + "," + report + "," + any;
    }
}
