package net.jj.giants.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.fun.FunGiants;
import net.jj.giants.fun.FunWorld;
import net.jj.giants.fun.Marks;
import net.jj.giants.net.AdminBookStatePayload;
import net.jj.giants.net.PlayersStatePayload;
import net.jj.giants.players.Players;
import net.jj.giants.systems.remains.GiantModel;
import net.jj.giants.systems.remains.GiantModels;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 12, ADMIN_BRIDGE.md §29) The giant editor and the sound picker on the server: which look each giant wears
 * (his own, else his tag's, else his kind's), the custom sets, the book's Editor page and its requests, and what every
 * player's game is sent (the looks, worked out for each giant's palette, and the sound settings) whenever they change.
 */
public final class Editor {
    private Editor() {}

    public static final String PAGE = "editor";
    /** the PlayersStatePayload kinds: the looks, the sound */
    public static final String LOOKS = "looks", SOUND = "sound";
    public static final Set<String> DOS = Set.of("look", "lookset", "lookdel");
    public static final int MOST_CUSTOM = 40, MOST_BLOCKS = 64;

    private static @Nullable String sentLooks, sentSound;
    /** worked-out maps: kind|set|custom check -> palette entry -> block */
    private static final Map<String, Map<String, String>> MAPS = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Editor::tick);
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> sendTo(server, h.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
        EditorCommand.registerAll();
    }

    public static void forget() {
        sentLooks = sentSound = null;
        MAPS.clear();
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) return;
        flush(server);
    }

    /** sends what changed to every player (each second; the tests call it at once) */
    public static void flush(MinecraftServer server) {
        String looks = looksJson(server).toString(), sound = SoundCatalog.sync(server).toString();
        boolean l = !looks.equals(sentLooks), s = !sound.equals(sentSound);
        if (!l && !s) return;
        sentLooks = looks;
        sentSound = sound;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (l) Players.sender.accept(p, new PlayersStatePayload(LOOKS, looks));
            if (s) Players.sender.accept(p, new PlayersStatePayload(SOUND, sound));
        }
    }

    static void sendTo(MinecraftServer server, ServerPlayer p) {
        Players.sender.accept(p, new PlayersStatePayload(LOOKS, looksJson(server).toString()));
        Players.sender.accept(p, new PlayersStatePayload(SOUND, SoundCatalog.sync(server).toString()));
    }

    // which look

    /** the look a giant wears: his own, else his tag's, else his kind's ("" none) */
    public static String lookOf(MinecraftServer server, LivingEntity e) {
        EditorWorld w = EditorWorld.get(server);
        EditorWorld.One one = w.s.giants.get(e.getStringUUID());
        if (one != null && !one.look.isEmpty()) return one.look;
        String tag = Marks.wears(e);
        if (tag != null) {
            String t = tagLook(w, tag);
            if (t != null) return t;
        }
        String kind = FunGiants.kindOf(e);
        return kind == null ? "" : w.s.kinds.getOrDefault(kind, "");
    }

    private static @Nullable String tagLook(EditorWorld w, String tag) {
        for (var e : w.s.tags.entrySet()) if (e.getKey().equalsIgnoreCase(tag)) return e.getValue();
        return null;
    }

    /** a look's map for this kind ("" or unknown: null) */
    public static @Nullable Map<String, String> mapFor(MinecraftServer server, String kind, String look) {
        return mapFor(EditorWorld.get(server), kind, look);
    }

    static @Nullable Map<String, String> mapFor(EditorWorld w, String kind, String look) {
        if (look == null || look.isEmpty()) return null;
        EditorWorld.Custom c = null;
        if (look.startsWith(LookSets.CUSTOM)) {
            c = w.custom(look.substring(LookSets.CUSTOM.length()));
            if (c == null) return null;
        } else if (!LookSets.builtIn(look)) return null;
        String cacheKey = kind + "|" + look + "|" + (c == null ? "" : c.base + c.blocks.hashCode());
        Map<String, String> m = MAPS.get(cacheKey);
        if (m != null) return m;
        GiantModel model = GiantModels.get(kind);
        if (model == null) return null;
        double[] share = LookSets.share(model.palette, model.pal);
        m = c == null ? LookSets.map(look, model.palette, share)
                : LookSets.custom(c.base, c.blocks, model.palette, share);
        m = Map.copyOf(m);
        MAPS.put(cacheKey, m);
        return m;
    }

    /**
     * Every player's game: for each kind with a look anywhere,
     * {"kinds":{"wreckback":{"look":1,"maps":{key:{entry:block}},"kind":key,"giants":{uuid:key}}}}.
     */
    public static JsonObject looksJson(MinecraftServer server) {
        EditorWorld w = EditorWorld.get(server);
        JsonObject kinds = new JsonObject();
        Map<String, JsonObject> per = new LinkedHashMap<>();
        // his kind's
        for (var e : w.s.kinds.entrySet()) put(w, per, e.getKey(), e.getValue(), null);
        // (a tag's: every one standing in the world wearing it, unless he has his own)
        if (!w.s.tags.isEmpty())
            for (LivingEntity g : FunGiants.standing(server, null)) {
                if (w.s.giants.containsKey(g.getStringUUID())) continue;
                String tag = Marks.wears(g);
                String t = tag == null ? null : tagLook(w, tag);
                String kind = FunGiants.kindOf(g);
                if (t != null && kind != null) put(w, per, kind, t, g.getStringUUID());
            }
        // each one's own
        for (var e : w.s.giants.entrySet()) put(w, per, e.getValue().kind, e.getValue().look, e.getKey());
        for (var e : per.entrySet()) kinds.add(e.getKey(), e.getValue());
        JsonObject o = new JsonObject();
        o.add("kinds", kinds);
        // (1.16) his followers (Systems: Mob followers) wear his look: tinted in its colour
        if (!per.isEmpty() && !net.jj.giants.systems.Followers.BANDS.isEmpty()) {
            JsonObject f = new JsonObject();
            Map<String, Integer> tints = new java.util.HashMap<>();
            for (var e : net.jj.giants.systems.Followers.BANDS.entrySet()) {
                Integer t = tints.computeIfAbsent(e.getValue(), id -> {
                    LivingEntity g = FunGiants.find(server, id);
                    return g == null ? -1 : LookSets.tint(w, lookOf(server, g));
                });
                if (t >= 0) f.addProperty(e.getKey(), t);
            }
            if (!f.isEmpty()) o.add("followers", f);
        }
        return o;
    }

    private static void put(EditorWorld w, Map<String, JsonObject> per, String kind, String look,
            @Nullable String giant) {
        Map<String, String> m = mapFor(w, kind, look);
        if (m == null) return;
        JsonObject k = per.computeIfAbsent(kind, x -> {
            JsonObject o = new JsonObject();
            o.addProperty("look", 1);
            o.add("maps", new JsonObject());
            o.add("giants", new JsonObject());
            return o;
        });
        String key = LookSets.key(look, m);
        JsonObject maps = k.getAsJsonObject("maps");
        if (!maps.has(key)) {
            JsonObject mm = new JsonObject();
            for (var e : m.entrySet()) mm.addProperty(e.getKey(), e.getValue());
            maps.add(key, mm);
        }
        if (giant == null) k.addProperty("kind", key);
        else k.getAsJsonObject("giants").addProperty(giant, key);
    }

    // changes (the book and the commands)

    /** a look's words: "Frost", "Ice palace (custom)", "his own look" */
    public static String lookName(String look) {
        if (look == null || look.isEmpty()) return "his own look";
        if (look.startsWith(LookSets.CUSTOM)) return look.substring(LookSets.CUSTOM.length()) + " (custom)";
        return Character.toUpperCase(look.charAt(0)) + look.substring(1);
    }

    /** a set's id from what someone typed ("frost", "Ice palace", "none"); null: there's no such set */
    public static @Nullable String setId(EditorWorld w, String words) {
        String t = words == null ? "" : words.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("none") || t.equalsIgnoreCase("own")) return "";
        String low = t.toLowerCase(Locale.ROOT).replace("ancient stone", "ancient").replace(' ', '_');
        if (LookSets.builtIn(low)) return low;
        String name = t.startsWith(LookSets.CUSTOM) ? t.substring(LookSets.CUSTOM.length()) : t;
        EditorWorld.Custom c = w.custom(name);
        return c == null ? null : LookSets.CUSTOM + c.name;
    }

    private static AdminServer.Result noSet(String words) {
        return AdminServer.Result.fail("There's no set called " + words + ". Try one of: "
                + String.join(", ", LookSets.BUILT_IN) + ", none, or a custom set's name.");
    }

    /** a kind's look ("" back to his own) */
    public static AdminServer.Result setKind(MinecraftServer server, String who, String kindWords, String set) {
        Giant g = AdminServer.giant(kindWords);
        if (g == null) return AdminServer.Result.fail("No giant called " + kindWords + ".");
        EditorWorld w = EditorWorld.get(server);
        String id = setId(w, set);
        if (id == null) return noSet(set);
        String name = AdminServer.nameOf(g);
        if (!id.isEmpty() && !takesLooks(server, g))
            return AdminServer.Result.fail(name + ": this version can't wear another look.");
        if (id.isEmpty()) w.s.kinds.remove(g.key());
        else w.s.kinds.put(g.key(), id);
        w.setDirty();
        String line = id.isEmpty() ? "Every " + name + " is back to his own look."
                : "Every " + name + " now wears " + lookName(id) + ".";
        return done(server, who, g.key(), "kind " + name, id, line);
    }

    /** a giant tag's look */
    public static AdminServer.Result setTag(MinecraftServer server, String who, String tag, String set) {
        FunWorld.Tag t = FunWorld.get(server).tag(tag);
        if (t == null) return AdminServer.Result.fail("There's no giant tag called " + tag + ".");
        EditorWorld w = EditorWorld.get(server);
        String id = setId(w, set);
        if (id == null) return noSet(set);
        w.s.tags.keySet().removeIf(k -> k.equalsIgnoreCase(t.name));
        if (!id.isEmpty()) w.s.tags.put(t.name, id);
        w.setDirty();
        String line = id.isEmpty() ? "Giants wearing the tag " + t.name + " are back to their own looks."
                : "Giants wearing the tag " + t.name + " now wear " + lookName(id) + ".";
        return done(server, who, "all", "tag " + t.name, id, line);
    }

    /** one giant's own look */
    public static AdminServer.Result setGiant(MinecraftServer server, String who, LivingEntity e, String set) {
        String kind = FunGiants.kindOf(e);
        if (kind == null) return AdminServer.Result.fail("That isn't a giant.");
        EditorWorld w = EditorWorld.get(server);
        String id = setId(w, set);
        if (id == null) return noSet(set);
        Giant g = AdminServer.giant(kind);
        String label = FunGiants.label(server, e);
        if (!id.isEmpty() && g != null && !takesLooks(server, g))
            return AdminServer.Result.fail(label + ": this version can't wear another look.");
        if (id.isEmpty()) w.s.giants.remove(e.getStringUUID());
        else {
            EditorWorld.One one = new EditorWorld.One();
            one.kind = kind;
            one.look = id;
            w.s.giants.put(e.getStringUUID(), one);
        }
        w.setDirty();
        String line = id.isEmpty() ? label + " is back to " + (w.s.kinds.containsKey(kind)
                ? "his kind's look (" + lookName(w.s.kinds.get(kind)) + ")." : "his own look.")
                : label + " now wears " + lookName(id) + ".";
        return done(server, who, kind, label, id, line);
    }

    /** a custom set saved (made or changed): its base and its blocks, each "from" block to a block */
    public static AdminServer.Result saveCustom(MinecraftServer server, String who, String name, String base,
            Map<String, String> blocks) {
        String n = name == null ? "" : name.trim();
        if (!Marks.validName(n)) return AdminServer.Result.fail("A set's name is 1-24 letters, numbers, spaces, ' or -.");
        if (LookSets.builtIn(n.toLowerCase(Locale.ROOT)) || n.equalsIgnoreCase("none"))
            return AdminServer.Result.fail(n + " is a built-in set's name. Pick another.");
        EditorWorld w = EditorWorld.get(server);
        EditorWorld.Custom old = w.custom(n);
        if (old == null && w.s.custom.size() >= MOST_CUSTOM)
            return AdminServer.Result.fail("This world has " + MOST_CUSTOM + " custom sets. Delete one first.");
        String b = base == null ? "" : base.trim().toLowerCase(Locale.ROOT);
        if (b.equals("none")) b = "";
        if (!b.isEmpty() && !LookSets.builtIn(b)) return noSet(base);
        if (blocks.size() > MOST_BLOCKS) return AdminServer.Result.fail("At most " + MOST_BLOCKS + " blocks in a set.");
        Map<String, String> clean = new LinkedHashMap<>();
        for (var e : blocks.entrySet()) {
            String from = LookSets.valid(e.getKey()), to = LookSets.valid(e.getValue());
            if (from == null) return AdminServer.Result.fail("There's no block called " + e.getKey() + ".");
            if (to == null) return AdminServer.Result.fail("There's no block called " + e.getValue() + ".");
            clean.put(LookSets.blockId(from), to);
        }
        EditorWorld.Custom c = old != null ? old : new EditorWorld.Custom();
        if (old != null) w.s.custom.values().remove(old);
        c.name = n;
        c.base = b;
        c.blocks = clean;
        c.by = who;
        c.time = System.currentTimeMillis();
        w.s.custom.put(n, c);
        w.setDirty();
        String line = (old == null ? "Made" : "Changed") + " the custom set " + n + " (" + (b.isEmpty() ? "" : "from "
                + lookName(b) + ", ") + clean.size() + (clean.size() == 1 ? " block" : " blocks") + ").";
        return done(server, who, "all", "set " + n, old == null ? "made" : "changed", line);
    }

    public static AdminServer.Result deleteCustom(MinecraftServer server, String who, String name) {
        EditorWorld w = EditorWorld.get(server);
        EditorWorld.Custom c = w.custom(name == null ? "" : name);
        if (c == null) return AdminServer.Result.fail("There's no custom set called " + name + ".");
        w.s.custom.values().remove(c);
        String id = LookSets.CUSTOM + c.name;
        // (whoever wore it goes back to his own look)
        w.s.kinds.values().removeIf(id::equals);
        w.s.tags.values().removeIf(id::equals);
        w.s.giants.values().removeIf(o -> o.look.equals(id));
        w.setDirty();
        return done(server, who, "all", "set " + c.name, "deleted", "Deleted the custom set " + c.name
                + "; whoever wore it is back to his own look.");
    }

    private static AdminServer.Result done(MinecraftServer server, String who, String giant, String what, String now,
            String line) {
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, giant, "fun:look",
                "look " + what, "", now.isEmpty() ? "own" : now, ""));
        AdminBook.changed(PAGE);
        flush(server);
        return new AdminServer.Result(true, List.of(line), List.of(who + ": " + line));
    }

    /** his mod's version takes looks (answers "look") */
    public static boolean takesLooks(@Nullable MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, "look", "1");
        return l != null && !l.isEmpty() && l.get(0).contains("\"look\"");
    }

    // the book

    public static @Nullable AdminServer.Result request(MinecraftServer server, ServerPlayer p, String who, String what,
            JsonObject o) {
        return switch (what) {
            case "look" -> {
                String scope = str(o, "scope"), target = str(o, "target"), set = str(o, "set");
                yield switch (scope) {
                    case "kind" -> setKind(server, who, target, set);
                    case "tag" -> setTag(server, who, target, set);
                    case "giant" -> {
                        LivingEntity e = FunGiants.find(server, target);
                        yield e == null ? AdminServer.Result.fail("He isn't standing in the world now.")
                                : setGiant(server, who, e, set);
                    }
                    default -> AdminServer.Result.fail("The book didn't say whose look.");
                };
            }
            case "lookset" -> {
                Map<String, String> blocks = new LinkedHashMap<>();
                if (o.get("blocks") instanceof JsonObject b)
                    for (var e : b.entrySet())
                        if (e.getValue().isJsonPrimitive()) blocks.put(e.getKey(), e.getValue().getAsString());
                yield saveCustom(server, who, str(o, "name"), str(o, "base"), blocks);
            }
            case "lookdel" -> deleteCustom(server, who, str(o, "name"));
            default -> null;
        };
    }

    private static String str(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsString() : "";
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static AdminBookStatePayload page(MinecraftServer server) {
        return new AdminBookStatePayload(PAGE, state(server).toString());
    }

    /** the Editor page: the sets, each kind's look, the tags' and each giant's, the custom sets */
    public static JsonObject state(MinecraftServer server) {
        EditorWorld w = EditorWorld.get(server);
        JsonObject o = new JsonObject();
        JsonArray sets = new JsonArray();
        for (String s : LookSets.BUILT_IN) sets.add(s);
        o.add("sets", sets);
        JsonObject kinds = new JsonObject(), takes = new JsonObject();
        for (Giant g : AdminServer.giants()) {
            kinds.addProperty(g.key(), w.s.kinds.getOrDefault(g.key(), ""));
            takes.addProperty(g.key(), takesLooks(server, g));
        }
        o.add("kinds", kinds);
        o.add("takes", takes);
        JsonObject tags = new JsonObject();
        for (FunWorld.Tag t : FunWorld.get(server).s.tags.values()) {
            String l = tagLook(w, t.name);
            tags.addProperty(t.name, l == null ? "" : l);
        }
        o.add("tags", tags);
        JsonArray giants = new JsonArray();
        List<LivingEntity> standing = new ArrayList<>(FunGiants.standing(server, null));
        int n = 0;
        for (LivingEntity e : standing) {
            if (n++ >= 40) break;
            JsonObject j = new JsonObject();
            j.addProperty("id", e.getStringUUID());
            j.addProperty("kind", FunGiants.kindOf(e));
            j.addProperty("label", FunGiants.label(server, e));
            EditorWorld.One one = w.s.giants.get(e.getStringUUID());
            j.addProperty("own", one == null ? "" : one.look);
            j.addProperty("wears", lookOf(server, e));
            giants.add(j);
        }
        // (ones with their own look that aren't standing now: kept, listed)
        for (var e : w.s.giants.entrySet()) {
            boolean shown = false;
            for (LivingEntity s : standing) if (s.getStringUUID().equals(e.getKey())) shown = true;
            if (shown) continue;
            JsonObject j = new JsonObject();
            j.addProperty("id", e.getKey());
            j.addProperty("kind", e.getValue().kind);
            j.addProperty("label", "");
            j.addProperty("own", e.getValue().look);
            j.addProperty("wears", e.getValue().look);
            j.addProperty("away", true);
            giants.add(j);
        }
        o.add("giants", giants);
        JsonArray custom = new JsonArray();
        for (EditorWorld.Custom c : w.s.custom.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("name", c.name);
            j.addProperty("base", c.base);
            JsonObject b = new JsonObject();
            for (var e : c.blocks.entrySet()) b.addProperty(e.getKey(), e.getValue());
            j.add("blocks", b);
            j.addProperty("by", c.by);
            j.addProperty("time", c.time);
            custom.add(j);
        }
        o.add("custom", custom);
        return o;
    }

    /** (the tests) a giant's id, for a look by id */
    public static UUID id(LivingEntity e) {
        return e.getUUID();
    }
}
