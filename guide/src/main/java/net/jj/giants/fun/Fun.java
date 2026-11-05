package net.jj.giants.fun;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCommand;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.net.AdminBookStatePayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Part 7 of the admin book (ADMIN_BRIDGE.md 23): admin fun and world tools. Watching a giant, playing as one, staging
 * a fight, names and titles, loot, giant tags (Fun page); the biome brush, arenas and dens, giant mixing (World tools
 * page). Admins only (never moderators), each change logged and told to the other operators.
 */
public final class Fun {
    private Fun() {}

    /** the book's requests this part answers */
    public static final Set<String> DOS = Set.of("spectate", "spectatestop", "playas", "playasstop", "fight", "name",
            "tag", "tagsave", "tagdelete", "loot", "brush", "brushget", "paintat", "eraseat", "build", "buildstop",
            "takedown", "buildgo", "settle",
            // (Part 10B) the fight script and giant fights (net.jj.giants.fights)
            "fightscript", "fightstart", "fightstop", "fightsave", "fightload", "fightdelete", "fightreplay",
            "fightstanddown");
    /** the book's page kind */
    public static final String PAGE = "fun";

    private static final Gson GSON = new Gson();
    private static final Set<UUID> loadedSince = new LinkedHashSet<>();

    public static void register() {
        Brush.register();
        FunCommand.registerAll();
        ServerTickEvents.END_SERVER_TICK.register(Fun::tick);
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> Watch.joined(server, h.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((h, server) -> {
            Watch.left(h.getPlayer());
            PlayAs.left(h.getPlayer());
        });
        // a giant gone for good: his loot drops where he fell (the game's own death, or his own death ending)
        ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (!(e instanceof LivingEntity le) || FunGiants.kindOf(le) == null) return;
            if (e.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.KILLED) Marks.fell(le);
        });
        // a giant coming into the world wearing a tag that changed since: put right next tick
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e instanceof LivingEntity le && FunGiants.kindOf(le) != null && Marks.wears(le) != null)
                loadedSince.add(le.getUUID());
        });
        ServerLifecycleEvents.SERVER_STARTED.register(Builds::stoppedOnStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> forget());
    }

    public static void forget() {
        Watch.forget();
        PlayAs.forget();
        Fights.forget();
        Brush.forget();
        Builds.forget();
        Spots.forget();
        loadedSince.clear();
        dirty = false;
    }

    static boolean dirty;

    /** the Fun and World tools pages changed: the open books get them fresh */
    static void changed() {
        dirty = true;
        AdminBook.changed(PAGE);
    }

    static void tick(MinecraftServer server) {
        Spots.tick(server);
        Watch.tick(server);
        PlayAs.tick(server);
        Fights.tick(server);
        Brush.tick(server);
        Brush.showCircles(server);
        Builds.tick(server);
        if (!loadedSince.isEmpty()) {
            for (UUID id : List.copyOf(loadedSince)) {
                LivingEntity e = FunGiants.find(server, id);
                if (e != null) Marks.loaded(server, e);
            }
            loadedSince.clear();
        }
        // (a build's progress on the page, now and then)
        if (Builds.busy() && server.getTickCount() % 40 == 0) changed();
    }

    /** an operator (the admin book's Admin role) */
    public static boolean admin(ServerPlayer p) {
        return p.hasPermissions(AdminCommand.OPS);
    }

    /** one line in the admin change log (no undo: these aren't settings) */
    static void log(MinecraftServer server, String who, String giant, String what, String name, String now) {
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, giant == null ? "all" : giant,
                "fun:" + what, name, "", now, ""));
    }

    /** told to the other operators (grey italic) and the server log */
    static void tell(MinecraftServer server, @Nullable ServerPlayer by, String line) {
        AdminServer.announce(server, by, List.of(line));
    }

    // the book

    /** the Fun and World tools pages' own state for this admin */
    public static AdminBookStatePayload page(MinecraftServer server, @Nullable ServerPlayer p) {
        return new AdminBookStatePayload(PAGE, state(server, p).toString());
    }

    public static JsonObject state(MinecraftServer server, @Nullable ServerPlayer p) {
        FunWorld w = FunWorld.get(server);
        JsonObject o = new JsonObject();
        Map<String, Integer> wearing = Marks.wearing(server);
        JsonArray tags = new JsonArray();
        for (FunWorld.Tag t : w.s.tags.values()) {
            JsonObject j = GSON.toJsonTree(t).getAsJsonObject();
            j.addProperty("wearing", wearing.getOrDefault(t.name.toLowerCase(Locale.ROOT), 0));
            j.addProperty("lootWords", Marks.describe(t.loot));
            tags.add(j);
        }
        o.add("tags", tags);
        JsonObject marks = new JsonObject();
        for (var m : w.s.marks.entrySet()) {
            JsonObject j = GSON.toJsonTree(m.getValue()).getAsJsonObject();
            j.addProperty("lootWords", Marks.describe(m.getValue().loot));
            if (m.getValue().loot != null) j.addProperty("lootItems", m.getValue().loot.items.size());
            j.remove("oldName");
            marks.add(m.getKey(), j);
        }
        o.add("marks", marks);
        JsonObject me = new JsonObject();
        if (p != null) {
            Watch.Follow f = Watch.of(p);
            if (f != null) {
                JsonObject j = new JsonObject();
                j.addProperty("giant", f.kind);
                j.addProperty("id", f.giant.toString());
                j.addProperty("label", f.label);
                j.addProperty("mode", f.mode);
                me.add("watching", j);
            }
            PlayAs.Playing pl = PlayAs.of(p);
            if (pl != null) {
                JsonObject j = new JsonObject();
                j.addProperty("giant", pl.kind());
                j.addProperty("id", pl.giant().toString());
                j.addProperty("label", pl.label());
                me.add("playing", j);
            }
            Brush.Setting b = Brush.of(p);
            JsonObject bj = new JsonObject();
            bj.addProperty("giant", b.giant);
            bj.addProperty("radius", b.radius);
            bj.addProperty("mode", b.mode);
            o.add("brush", bj);
        }
        o.add("me", me);
        JsonArray builds = new JsonArray();
        for (FunWorld.Build b : w.s.builds) if (!b.state.equals("gone")) builds.add(GSON.toJsonTree(b));
        o.add("builds", builds);
        JsonObject places = new JsonObject();
        JsonArray kinds = new JsonArray();
        for (Giant g : AdminServer.giants()) {
            JsonObject head = FunGiants.head(server, g);
            JsonObject k = new JsonObject();
            k.addProperty("key", g.key());
            k.addProperty("name", AdminServer.nameOf(g));
            k.addProperty("fun", head != null);
            JsonArray pl = new JsonArray();
            for (String s : FunGiants.list(head, "places")) pl.add(s);
            places.add(g.key(), pl);
            JsonArray does = new JsonArray();
            for (String s : FunGiants.list(head, "does")) does.add(s);
            k.add("does", does);
            kinds.add(k);
        }
        o.add("places", places);
        o.add("kinds", kinds);
        o.addProperty("erasing", Brush.erasing());
        // (Part 10B) the fight script, the saved setups, the staged fight now, the fights going on
        o.add("fights", net.jj.giants.fights.Fights.state(server, p));
        return o;
    }

    /** whom this player watches now ({giant, id, label, mode}), or null */
    public static @Nullable JsonObject watchingJson(ServerPlayer p) {
        Watch.Follow f = Watch.of(p);
        if (f == null) return null;
        JsonObject j = new JsonObject();
        j.addProperty("giant", f.kind);
        j.addProperty("id", f.giant.toString());
        j.addProperty("label", f.label);
        j.addProperty("mode", f.mode);
        return j;
    }

    // the book's requests (Admins only: AdminBook checked the role; a moderator's Watch too, JJ answers 2)

    public static @Nullable AdminServer.Result request(MinecraftServer server, ServerPlayer p, String who, String what,
            JsonObject o) {
        return switch (what) {
            case "spectate" -> {
                LivingEntity e = giant(server, o);
                yield e == null ? notThere(server, o) : Watch.start(server, p, e, AdminJson.str(o, "mode", 8)
                        .equals("eyes") ? "eyes" : "free");
            }
            case "spectatestop" -> Watch.stop(server, p);
            case "playas" -> {
                LivingEntity e = giant(server, o);
                yield e == null ? notThere(server, o) : PlayAs.start(server, p, e);
            }
            case "playasstop" -> PlayAs.stop(server, p);
            case "fight" -> fight(server, p, o);
            case "name" -> {
                LivingEntity e = giant(server, o);
                if (e == null) yield notThere(server, o);
                yield Marks.name(server, who, e, AdminJson.str(o, "name", 64), AdminJson.str(o, "title", 64),
                        AdminJson.str(o, "colour", 16).isEmpty() ? null : AdminJson.str(o, "colour", 16),
                        o.has("shown") ? !AdminJson.str(o, "shown", 8).equals("false") : null);
            }
            case "tag" -> {
                LivingEntity e = giant(server, o);
                if (e == null) yield notThere(server, o);
                String t = AdminJson.str(o, "tag", 32);
                if (t.isEmpty()) yield Marks.take(server, who, e, true);
                FunWorld.Tag tag = FunWorld.get(server).tag(t);
                yield tag == null ? AdminServer.Result.fail("There's no tag called " + t + ".")
                        : Marks.give(server, who, e, tag, true);
            }
            case "tagsave" -> {
                FunWorld.Tag t;
                try {
                    t = GSON.fromJson(o.get("tag"), FunWorld.Tag.class);
                } catch (RuntimeException bad) {
                    t = null;
                }
                if (t == null) yield AdminServer.Result.fail("The book sent a tag the server can't read.");
                // the tag's loot: kept as it was, none, or a copy of the chest you look at (added or in place of his)
                String from = AdminJson.str(o, "lootFrom", 8), mode = AdminJson.str(o, "lootMode", 8);
                FunWorld.Tag old = FunWorld.get(server).tag(AdminJson.str(o, "old", 32));
                if (from.equals("chest")) {
                    List<String> items = Marks.copyChest(p);
                    if (items == null || items.isEmpty())
                        yield AdminServer.Result.fail("Look at a chest with the tag's loot in it (8 blocks at most).");
                    FunWorld.Loot l = new FunWorld.Loot();
                    l.mode = mode.equals("replace") ? "replace" : "add";
                    l.items = items;
                    t.loot = l;
                } else if (from.equals("none")) t.loot = null;
                else {
                    t.loot = old == null || old.loot == null ? null : old.loot;
                    if (t.loot != null && (mode.equals("add") || mode.equals("replace"))) t.loot.mode = mode;
                }
                yield Marks.saveTag(server, who, AdminJson.str(o, "old", 32), t);
            }
            case "tagdelete" -> Marks.deleteTag(server, who, AdminJson.str(o, "name", 32));
            case "loot" -> {
                LivingEntity e = giant(server, o);
                if (e == null) yield notThere(server, o);
                yield Marks.loot(server, who, p, e, AdminJson.str(o, "mode", 8), AdminJson.str(o, "from", 8),
                        AdminJson.str(o, "table", 128));
            }
            case "brush" -> Brush.set(p, AdminJson.str(o, "giant", 32), (int) AdminJson.num(o, "radius", 32),
                    AdminJson.str(o, "mode", 8));
            case "brushget" -> {
                Brush.give(p);
                yield AdminServer.Result.said(List.of("The Biome Brush is in your pack: right-click to paint, sneak +"
                        + " right-click to erase."));
            }
            case "paintat", "eraseat" -> {
                Brush.Setting s = Brush.of(p);
                net.minecraft.core.BlockPos at = AdminJson.str(o, "spot", 8).equals("look")
                        ? Spots.looked(p, Spots.LOOK) : p.blockPosition();
                if (at == null) yield AdminServer.Result.fail("Look at the ground (256 blocks at most).");
                yield what.equals("eraseat") || s.giant.equals("erase")
                        ? Brush.erase(server, who, p.serverLevel(), at.getX(), at.getZ(), s.radius, p)
                        : Brush.paint(server, who, p, s.giant, s.radius, s.mode, at.getX(), at.getZ());
            }
            case "build" -> AdminJson.str(o, "kind", 8).equals("den")
                    ? Builds.den(server, who, p, AdminJson.str(o, "style", 32), AdminJson.num(o, "size", 1))
                    : Builds.arena(server, who, p, AdminJson.str(o, "style", 16));
            case "buildstop" -> Builds.stop(server, who);
            case "takedown" -> Builds.takeDown(server, who, (int) AdminJson.num(o, "n", -1));
            case "buildgo" -> {
                FunWorld.Build b = FunWorld.get(server).build((int) AdminJson.num(o, "n", -1));
                if (b == null) yield AdminServer.Result.fail("There's no such build.");
                var l = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        net.minecraft.resources.ResourceLocation.parse(b.dim)));
                if (l == null) yield AdminServer.Result.fail("That world isn't there.");
                p.teleportTo(l, b.x + 0.5, b.y + 1.5 + (b.kind.equals("den") && b.giant.equals("hollowbell") ? 0 : 0),
                        b.z + b.r + 4.5, 180f, 20f);
                yield AdminServer.Result.said(List.of("Took you to build " + b.n + "."));
            }
            case "settle" -> {
                LivingEntity e = giant(server, o);
                if (e == null) yield notThere(server, o);
                yield Settle.settle(server, who, p, e, AdminJson.str(o, "where", 16), AdminJson.str(o, "ground", 32));
            }
            default -> net.jj.giants.fights.Fights.request(server, p, who, what, o);
        };
    }

    /** the giant a request names by kind and id */
    private static @Nullable LivingEntity giant(MinecraftServer server, JsonObject o) {
        return FunGiants.find(server, AdminJson.str(o, "id", 40));
    }

    private static AdminServer.Result notThere(MinecraftServer server, JsonObject o) {
        try {
            return AdminServer.Result.fail(FunGiants.outOrGone(server, UUID.fromString(AdminJson.str(o, "id", 40))));
        } catch (IllegalArgumentException e) {
            return AdminServer.Result.fail("The book didn't say which giant.");
        }
    }

    /** a fight from the book: a and b each {kind, size} (fresh) or {giant, id} (one out there) */
    private static AdminServer.Result fight(MinecraftServer server, ServerPlayer p, JsonObject o) {
        Fights.Side a = side(server, o.get("a")), b = side(server, o.get("b"));
        if (a == null || b == null) return AdminServer.Result.fail("Pick both giants: a kind and a size, or one out"
                + " there.");
        return Fights.stage(server, p, a, b, AdminJson.str(o, "spot", 8).equals("look"));
    }

    static @Nullable Fights.Side side(MinecraftServer server, @Nullable JsonElement j) {
        if (!(j instanceof JsonObject s)) return null;
        String id = AdminJson.str(s, "id", 40);
        if (!id.isEmpty()) {
            LivingEntity e = FunGiants.find(server, id);
            Giant g = e == null ? null : AdminServer.giant(FunGiants.kindOf(e));
            return g == null ? null : new Fights.Side(g, FunGiants.sizeOf(e), e);
        }
        Giant g = AdminServer.giant(AdminJson.str(s, "kind", 32));
        if (g == null) return null;
        double size = AdminJson.num(s, "size", 0.5);
        return new Fights.Side(g, size > 0 ? size : 0.5, null);
    }

    /** every name a giant answers to here, for the commands' suggestions */
    static List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) out.add(g.key());
        return out;
    }
}
