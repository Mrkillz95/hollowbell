package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.giants.FirstJoin;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminCatalog;
import net.jj.giants.admin.AdminInsight;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.admin.AdminWorld;
import net.jj.giants.net.PlayersAskPayload;
import net.jj.giants.net.PlayersStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Part 5, "Players" (ADMIN_BRIDGE.md 20): the Guide's server side for players. The player settings (in the
 * all-giants group), per-player rules pushed to the giants, My Giant's pick, the starting kit, the "This world" pages
 * of the Guide, fight music sent to players' games, and the clock for leaderboards, votes, signs and challenges.
 */
public final class Players {
    private Players() {}

    /** the giants' own entity (the one that falls), by the Guide's key */
    public static final Map<String, String> MAIN = Map.of(
            "pitchgut", "mountain_breathes:mountain", "furrowmaw", "furrowmaw:furrowmaw",
            "cerberus", "fire_ice_cerberus:cerberus", "hollowbell", "hollowbell:hollowbell",
            "lanternwillow", "lanternwillow:lanternwillow", "wreckback", "wreckback:wreckback",
            "swarmforge", "swarmforge:swarmforge", "gloamwick", "gloamwick:gloamwick",
            "hearthstride", "hearthstride:hearthstride");

    /** each giant's finder and book (for the crafting switches) */
    public static final Map<String, String> FINDERS = Map.of(
            "pitchgut", "mountain_breathes:mountain_compass", "furrowmaw", "furrowmaw:furrowmaw_finder",
            "cerberus", "fire_ice_cerberus:cerberus_finder", "hollowbell", "hollowbell:hollowbell_finder",
            "lanternwillow", "lanternwillow:lanternwillow_finder", "wreckback", "wreckback:wreckback_finder",
            "swarmforge", "swarmforge:swarmforge_finder", "gloamwick", "gloamwick:gloamwick_finder",
            "hearthstride", "hearthstride:hearth_lantern");
    public static final Map<String, String> BOOKS = Map.of(
            "pitchgut", "mountain_breathes:mountain_codex", "furrowmaw", "furrowmaw:furrowmaw_codex",
            "cerberus", "fire_ice_cerberus:cerberus_codex", "hollowbell", "hollowbell:hollowbell_codex",
            "lanternwillow", "lanternwillow:lanternwillow_codex", "wreckback", "wreckback:wreckback_codex",
            "swarmforge", "swarmforge:swarmforge_codex", "gloamwick", "gloamwick:gloamwick_codex",
            "hearthstride", "hearthstride:hearth_book");

    /**
     * how hard each one is to beat as shipped (the pick screen's danger, before the world's settings). JJ (Nov 3): the
     * same for all of them, Normal, because the giants were balanced against each other (handoff DUELS.md); only the
     * world's settings (sizes, health, damage, presets) move a giant's rating.
     */
    public static final Map<String, Integer> BASE_DANGER = Map.of(
            "pitchgut", 3, "furrowmaw", 3, "cerberus", 3, "hollowbell", 3, "lanternwillow", 3, "wreckback", 3,
            "swarmforge", 3, "gloamwick", 3, "hearthstride", 3);

    /** how a page goes to a player: over the network, or (in the tests) to a catcher */
    public static BiConsumer<ServerPlayer, PlayersStatePayload> sender = (p, s) -> {
        if (ServerPlayNetworking.canSend(p, PlayersStatePayload.TYPE)) ServerPlayNetworking.send(p, s);
    };

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PlayersStatePayload.TYPE, PlayersStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PlayersAskPayload.TYPE, PlayersAskPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(PlayersAskPayload.TYPE,
                (p, ctx) -> ctx.server().execute(() -> ask(ctx.server(), ctx.player(), p.json())));
        ServerPlayConnectionEvents.JOIN.register((h, s, server) -> join(h.getPlayer()));
        ServerLivingEntityEvents.AFTER_DEATH.register(Players::died);
        // (several giants die their own way, with a death of their own and then gone, never through the game's
        // own death: caught when the one who was dying leaves the world)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((e, level) -> {
            if (!(e instanceof LivingEntity le) || kindOf(le) == null) return;
            LOADED.remove(le);
            if (e.getRemovalReason() != null && e.getRemovalReason().shouldDestroy()) {
                if (dying(le)) fell(le);
                else CREDIT.remove(le.getUUID());
            }
        });
        // (the giants in loaded land, to remember who last hurt each: a death of his own can outlast the game's
        // own memory of the player behind the blow)
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e instanceof LivingEntity le && kindOf(le) != null) LOADED.add(le);
        });
        ServerTickEvents.END_SERVER_TICK.register(Players::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> running = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            running = null;
            forget();
        });
        PlayersCommand.registerAll();
    }

    /** the server running in this game (for the crafting grid, which has no server at hand) */
    private static volatile @Nullable MinecraftServer running;

    /**
     * (the crafting switches, ADMIN_BRIDGE.md 20.13) may this be crafted now: a giant's finder only while finders
     * are craftable, a giant's book only while books are on
     */
    public static boolean craftable(ItemStack result) {
        MinecraftServer server = running;
        if (server == null || result.isEmpty()) return true;
        String id = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
        if (FINDERS.containsValue(id)) return on(server, "findersCraftable");
        if (BOOKS.containsValue(id)) return on(server, "books");
        return true;
    }

    /** (the tests) the server the crafting switches read */
    public static void runningForTests(@Nullable MinecraftServer s) {
        running = s;
    }

    public static void forget() {
        lastMusic = null;
        LOADED.clear();
        CREDIT.clear();
        Challenges.forget();
    }

    // the settings

    /** a player setting's value in this world (the world's own, else its default) */
    public static JsonElement value(MinecraftServer server, String id) {
        String full = id.startsWith("all.") ? id : "all." + id;
        JsonElement v = AdminWorld.get(server).values(AdminCatalog.ALL).get(full);
        if (v != null) return v;
        AdminSetting s = AdminCatalog.shared(full);
        return s == null ? JsonParser.parseString("null") : s.def();
    }

    public static boolean on(MinecraftServer server, String id) {
        JsonElement v = value(server, id);
        try {
            return v.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String option(MinecraftServer server, String id) {
        JsonElement v = value(server, id);
        return v != null && v.isJsonPrimitive() ? v.getAsString() : "";
    }

    // per-player rules (ADMIN_BRIDGE.md 20.4)

    /** at the overworld's early loading, right after the giants get their settings */
    public static void atLoad(MinecraftServer server, ServerLevel overworld) {
        pushRules(server, PlayerData.get(overworld));
    }

    public static String rulesArg(PlayerData d) {
        JsonObject rules = new JsonObject();
        for (var e : d.s.rules.entrySet()) {
            PlayerData.Rule r = e.getValue();
            if (r.own && !r.ignored && r.max < 0) continue;
            JsonObject o = new JsonObject();
            o.addProperty("own", r.own);
            o.addProperty("ignored", r.ignored);
            // (Max giants per player, 20.18) only an override; absent: the world's all.maxGiants
            if (r.max >= 0) o.addProperty("max", Math.min(9, r.max));
            rules.add(e.getKey(), o);
        }
        // (Part 6) players standing in a safe area are ignored there
        for (String id : net.jj.giants.tools.Watch.safeNow()) {
            JsonObject o = rules.has(id) ? rules.getAsJsonObject(id) : new JsonObject();
            if (!o.has("own")) o.addProperty("own", d.rule(id).own);
            o.addProperty("ignored", true);
            rules.add(id, o);
        }
        JsonObject arg = new JsonObject();
        arg.addProperty("players", 1);
        arg.add("rules", rules);
        return arg.toString();
    }

    /** every giant gets the per-player rules (the list replaces the last one); how many took them */
    public static int pushRules(MinecraftServer server, PlayerData d) {
        String arg = rulesArg(d);
        int n = 0;
        for (Giant g : AdminServer.giants()) if (AdminServer.call(server, g, "players", arg) != null) n++;
        return n;
    }

    /** the rules, as /giants players and the book's Players page set them */
    public static AdminServer.Result setRule(MinecraftServer server, String who, String player, String rule,
            boolean on) {
        Who w = who(server, player);
        if (w == null) return AdminServer.Result.fail("No player called " + player + " has been on this server.");
        PlayerData d = PlayerData.get(server);
        PlayerData.Rule r = d.s.rules.computeIfAbsent(w.id(), k -> new PlayerData.Rule());
        r.name = w.name();
        String words;
        switch (rule) {
            case "own" -> {
                r.own = on;
                words = on ? "may own giants again" : "may not own giants";
            }
            case "ignored" -> {
                r.ignored = on;
                words = on ? "is ignored by giants" : "isn't ignored by giants any more";
            }
            case "nokit" -> {
                r.noKit = on;
                words = on ? "gets no starting kit" : "gets the starting kit";
            }
            default -> {
                return AdminServer.Result.fail("No rule called " + rule + ". Try one of: own, ignored, nokit.");
            }
        }
        if (!r.any()) d.s.rules.remove(w.id());
        d.setDirty();
        pushRules(server, d);
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, AdminCatalog.ALL,
                "players:" + rule, w.name() + " " + words, "", on ? "on" : "off", ""));
        AdminBook.changed("players");
        String line = w.name() + " " + words + " in this world.";
        return new AdminServer.Result(true, List.of(line), List.of(who + ": " + line));
    }

    /**
     * (Max giants per player, ADMIN_BRIDGE.md 20.18) a player's own limit, as /giants players <player> max and the
     * book's Players page set it: "default" (the world's), "none" (no limit for them) or 1 to 9. Nothing they own is
     * taken away, whatever it is lowered to.
     */
    public static AdminServer.Result setMax(MinecraftServer server, String who, String player, String value) {
        String v = value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
        int max;
        switch (v) {
            case "default", "world" -> max = -1;
            case "none" -> max = 0;
            default -> {
                try {
                    max = Integer.parseInt(v);
                } catch (NumberFormatException e) {
                    max = -2;
                }
                if (max < 1 || max > 9) return AdminServer.Result.fail("Pick 1 to 9, none or default.");
            }
        }
        Who w = who(server, player);
        if (w == null) return AdminServer.Result.fail("No player called " + player + " has been on this server.");
        PlayerData d = PlayerData.get(server);
        PlayerData.Rule r = d.s.rules.computeIfAbsent(w.id(), k -> new PlayerData.Rule());
        r.name = w.name();
        r.max = max;
        if (!r.any()) d.s.rules.remove(w.id());
        d.setDirty();
        pushRules(server, d);
        int world = Owns.worldCap(server);
        String words = max > 0 ? "may own at most " + max + (max == 1 ? " giant" : " giants")
                : max == 0 ? "may own any number of giants (no limit)"
                : "follows the world's limit on giants (" + (world > 0 ? String.valueOf(world) : "no limit") + ")";
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, AdminCatalog.ALL,
                "players:max", w.name() + " " + words, "", max > 0 ? String.valueOf(max) : max == 0 ? "none"
                        : "default", ""));
        AdminBook.changed("players");
        String line = w.name() + " " + words + " in this world.";
        return new AdminServer.Result(true, List.of(line), List.of(who + ": " + line));
    }

    /** /giants players <player> max: what they own and their limit */
    public static AdminServer.Result showMax(MinecraftServer server, String player) {
        Who w = who(server, player);
        if (w == null) return AdminServer.Result.fail("No player called " + player + " has been on this server.");
        Owns.All a = Owns.all(server);
        PlayerData.Rule r = PlayerData.get(server).rule(w.id());
        int cap = Owns.cap(server, w.id());
        String rule = r.max > 0 ? "Their own limit." : r.max == 0 ? "No limit for them." : "The world's limit.";
        return AdminServer.Result.said(List.of(Owns.summary(w.name(), a.of(w.id()), cap), rule));
    }

    /** a player by name or UUID: online, known to this world's data, or the server's name cache */
    public record Who(String id, String name) {}

    public static @Nullable Who who(MinecraftServer server, String nameOrId) {
        for (ServerPlayer p : server.getPlayerList().getPlayers())
            if (p.getGameProfile().getName().equalsIgnoreCase(nameOrId) || p.getStringUUID().equals(nameOrId))
                return new Who(p.getStringUUID(), p.getGameProfile().getName());
        PlayerData d = PlayerData.get(server);
        for (var e : d.s.rules.entrySet())
            if (e.getValue().name.equalsIgnoreCase(nameOrId) || e.getKey().equals(nameOrId))
                return new Who(e.getKey(), e.getValue().name);
        for (var e : d.s.stats.entrySet())
            if (e.getValue().name.equalsIgnoreCase(nameOrId) || e.getKey().equals(nameOrId))
                return new Who(e.getKey(), e.getValue().name);
        try {
            var cache = server.getProfileCache();
            if (cache != null) {
                var gp = cache.get(nameOrId);
                if (gp.isPresent()) return new Who(gp.get().getId().toString(), gp.get().getName());
            }
        } catch (RuntimeException e) {
            // (no cache in some test servers)
        }
        return null;
    }

    // joining

    public static void join(ServerPlayer p) {
        MinecraftServer server = p.server;
        PlayerData d = PlayerData.get(server);
        PlayerData.Stats st = d.stats(p.getStringUUID(), p.getGameProfile().getName());
        if (st.first < 0) st.first = server.overworld().getGameTime();
        PlayerData.Rule r = d.s.rules.get(p.getStringUUID());
        if (r != null) r.name = p.getGameProfile().getName();
        kit(p);
        sendSync(p);
        Challenges.joined(p);
        if (on(server, "myGiant") && !d.s.picks.containsKey(p.getStringUUID())) offerPick(p);
    }

    /** the starting kit, the first time they join this world (ADMIN_BRIDGE.md 20.13); true if it was given now */
    public static boolean kit(ServerPlayer p) {
        MinecraftServer server = p.server;
        FirstJoin f = FirstJoin.get(server);
        if (f.had(p.getUUID())) return false;
        f.mark(p.getUUID());
        if (PlayerData.get(server).rule(p.getStringUUID()).noKit) return false;
        if (on(server, "kitGuide")) GiantsGuideMod.give(p);
        List<Giant> giants = AdminServer.giants();
        switch (option(server, "kitFinders")) {
            case "all" -> {
                for (Giant g : giants) give(server, p, g, "finder");
            }
            case "one" -> {
                String which = option(server, "kitFinder");
                Giant g = which.equals("random") || AdminServer.giant(which) == null
                        ? giants.isEmpty() ? null : giants.get(p.getRandom().nextInt(giants.size()))
                        : AdminServer.giant(which);
                if (g != null) give(server, p, g, "finder");
            }
            default -> {}
        }
        String armour = option(server, "kitArmour");
        switch (armour) {
            case "none", "" -> {}
            case "leather" -> wear(p, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS,
                    Items.LEATHER_BOOTS);
            case "iron" -> wear(p, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case "diamond" -> wear(p, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS,
                    Items.DIAMOND_BOOTS);
            default -> {
                Giant g = AdminServer.giant(armour);
                if (g != null) give(server, p, g, "armour");
            }
        }
        String book = option(server, "kitBook");
        if (!book.equals("none") && !book.isEmpty() && on(server, "books")) {
            Giant g = AdminServer.giant(book);
            // (Max giants per player, 20.18) at their limit the kit's book is left out, the same way
            Owns.Verdict v = g == null ? null : Owns.check(server, p.getStringUUID());
            List<String> r = g == null ? null : !v.ok() ? List.of("no", v.line()) : give(server, p, g, "book");
            if (r != null && r.size() > 1 && r.get(0).equals("no"))
                p.sendSystemMessage(Component.literal("Your starting kit has no " + AdminServer.nameOf(g) + " book: "
                        + r.get(1)).withStyle(ChatFormatting.GRAY));
        }
        return true;
    }

    private static @Nullable List<String> give(MinecraftServer server, ServerPlayer p, Giant g, String what) {
        JsonObject o = new JsonObject();
        o.addProperty("players", 1);
        o.addProperty("player", p.getStringUUID());
        o.addProperty("what", what);
        List<String> r = AdminServer.call(server, g, "give", o.toString());
        boolean answered = r != null && !r.isEmpty() && (r.get(0).equals("ok") || r.get(0).equals("no"));
        if (!answered && what.equals("finder")) {
            // (an older giant, or one who hands nothing out himself: his finder by its item id)
            Item it = item(FINDERS.get(g.key()));
            if (it != null) put(p, new ItemStack(it));
        }
        return r;
    }

    static @Nullable Item item(@Nullable String id) {
        if (id == null) return null;
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) return null;
        return BuiltInRegistries.ITEM.get(rl);
    }

    private static void put(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.drop(s, false);
    }

    private static void wear(ServerPlayer p, Item head, Item chest, Item legs, Item feet) {
        Item[] items = {head, chest, legs, feet};
        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        for (int i = 0; i < 4; i++)
            if (p.getItemBySlot(slots[i]).isEmpty()) p.setItemSlot(slots[i], new ItemStack(items[i]));
            else put(p, new ItemStack(items[i]));
    }

    // fight music on players' games (ADMIN_BRIDGE.md 20.6)

    private static @Nullable String lastMusic;

    public static void sendSync(ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.addProperty("fightMusic", option(p.server, "fightMusic"));
        sender.accept(p, new PlayersStatePayload(PlayersStatePayload.SYNC, o.toString()));
    }

    // My Giant (ADMIN_BRIDGE.md 20.5, 20.7)

    /** the giants that answer My Giant: their mod lists the player switches */
    public static List<Giant> pickable(MinecraftServer server) {
        List<Giant> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            if (AdminCatalog.EARNED.contains(g.key())) continue;
            AdminJson.Report r = AdminServer.report(server, g);
            if (r != null && r.uses().stream().anyMatch(u -> u.equals("all.sit") || u.equals("all.books")))
                out.add(g);
        }
        return out;
    }

    /** the pick screen's list: each giant's name, role, line, danger and what picking him would mean now */
    public static JsonObject pickJson(MinecraftServer server, ServerPlayer viewer) {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        Map<String, Double> all = allValues(server);
        PlayerData d = PlayerData.get(server);
        boolean mayOwn = d.rule(viewer.getStringUUID()).own && on(server, "books");
        Owns.All[] all0 = {null};
        for (Giant g : pickable(server)) {
            JsonObject j = new JsonObject();
            j.addProperty("key", g.key());
            j.addProperty("name", AdminServer.nameOf(g));
            AdminJson.Report r = AdminServer.report(server, g);
            int level = BASE_DANGER.getOrDefault(g.key(), 3);
            if (r != null) {
                AdminInsight.Danger dg = AdminInsight.danger(List.of(AdminInsight.view(r)), all);
                level = (int) Math.max(1, Math.min(5, Math.floor(level + dg.score() - 3 + 0.5)));
            }
            j.addProperty("danger", level);
            j.addProperty("label", AdminInsight.LABELS[level - 1]);
            Live live = live(server, g);
            String note = "";
            AdminSetting cap = r == null ? null : r.setting("maxInWorld");
            int most = cap == null ? 0 : (int) cap.value().getAsDouble();
            boolean full = most > 0 && live.count >= most;
            // (the main book's holder: with My Giant's own books a live line's owner may be an own book's)
            String holder = mainHolder(server, g, live);
            boolean other = holder != null && !holder.equals(viewer.getGameProfile().getName());
            // (Max giants per player, 20.18) at their limit: his ground without his book
            String capped = mayOwn ? cappedFor(server, viewer.getStringUUID(), g, all0) : null;
            // (1.11.0, BOOK_RULE.md's My Giant exception) with room, a picker gets their own book and their own one
            if (!mayOwn) note = "You'd go to his ground; you may not own giants in this world.";
            else if (capped != null) note = capped.substring(0, capped.length() - 1)
                    + ": you'd go to his ground without his book.";
            else if (other && full) note = "As many as the world allows: you'd share " + holder + "'s book.";
            else if (other) note = holder + " has his book: you'd get your own book and your own one of him.";
            else if (full) note = "As many as the world allows: you'd take charge of them.";
            j.addProperty("note", note);
            a.add(j);
        }
        o.add("giants", a);
        o.addProperty("picked", d.s.picks.containsKey(viewer.getStringUUID()));
        return o;
    }

    /**
     * (Max giants per player, 20.18) the line for a player who may not gain this giant now (at their limit and not
     * already holding one of his books), else null. pass: one count shared by a whole list (filled on first use).
     */
    static @Nullable String cappedFor(MinecraftServer server, String id, Giant g, Owns.All[] pass) {
        int cap = Owns.cap(server, id);
        if (cap <= 0) return null;
        if (pass[0] == null) pass[0] = Owns.all(server);
        List<Owns.Claim> mine = pass[0].of(id);
        for (Owns.Claim c : mine) if (c.giant().equals(g.key())) return null;
        return mine.size() < cap ? null : Owns.line(mine.size(), cap);
    }

    static Map<String, Double> allValues(MinecraftServer server) {
        // (the same numbers the book's rating reads: the multipliers, the switches as 1/0, the systems' numbers)
        return net.jj.giants.admin.AdminBook.allValuesForTests(AdminWorld.get(server));
    }

    /** what a giant's live list says now: how many of him, and who holds his book */
    record Live(int count, @Nullable String owner, @Nullable String ownerId, List<String> ids) {}

    /** who holds his book now, by his live list (null: nobody, or none of him standing) */
    public static @Nullable String liveOwner(MinecraftServer server, Giant g) {
        return live(server, g).owner();
    }

    /** how many of him the world holds now (his live list) */
    public static int liveCount(MinecraftServer server, Giant g) {
        return live(server, g).count();
    }

    private static final java.util.regex.Pattern OWNED_BY = java.util.regex.Pattern.compile(", owned by (.+?), health x");

    /** who holds his (main) book: from his /giants info line, else his live list's owner */
    static @Nullable String mainHolder(MinecraftServer server, Giant g, Live live) {
        List<String> info = AdminServer.call(server, g, "info", "");
        if (info != null)
            for (String l : info) {
                var m = OWNED_BY.matcher(l);
                if (m.find()) return m.group(1);
                if (l.contains(", health x")) return null;
            }
        return live.owner;
    }

    static Live live(MinecraftServer server, Giant g) {
        List<String> l = AdminServer.call(server, g, "live", "1");
        int n = 0;
        String owner = null, ownerId = null;
        List<String> ids = new ArrayList<>();
        if (l != null)
            for (int i = 1; i < l.size(); i++) {
                JsonObject o = AdminJson.object(l.get(i));
                if (o == null) continue;
                n++;
                if (o.has("id")) ids.add(o.get("id").getAsString());
                if (o.has("owner")) owner = o.get("owner").getAsString();
                if (o.has("ownerId")) ownerId = o.get("ownerId").getAsString();
            }
        return new Live(n, owner, ownerId, ids);
    }

    /** the pick screen goes to them (or, on a game without the Guide, the list in chat) */
    public static void offerPick(ServerPlayer p) {
        JsonObject list = pickJson(p.server, p);
        if (ServerPlayNetworking.canSend(p, PlayersStatePayload.TYPE) || sender != DEFAULT_SENDER) {
            sender.accept(p, new PlayersStatePayload(PlayersStatePayload.PICK, list.toString()));
            return;
        }
        MutableComponent m = Component.literal("My Giant: pick your giant (click one): ").withStyle(ChatFormatting.GOLD);
        for (JsonElement e : list.getAsJsonArray("giants")) {
            JsonObject j = e.getAsJsonObject();
            String key = j.get("key").getAsString();
            m.append(Component.literal("[" + j.get("name").getAsString() + "] ").withStyle(s -> s
                    .withColor(ChatFormatting.YELLOW)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/giants pick " + key))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(
                            "Danger " + j.get("danger").getAsInt() + " " + j.get("label").getAsString()
                                    + (j.get("note").getAsString().isEmpty() ? "" : ". " + j.get("note")
                                            .getAsString()))))));
        }
        p.sendSystemMessage(m);
    }

    private static final BiConsumer<ServerPlayer, PlayersStatePayload> DEFAULT_SENDER = sender;

    /** a player picks a giant (the pick screen, or /giants pick) */
    public static AdminServer.Result pick(MinecraftServer server, ServerPlayer p, String which) {
        if (!on(server, "myGiant")) return AdminServer.Result.fail("My Giant isn't on in this world.");
        PlayerData d = PlayerData.get(server);
        PlayerData.Pick had = d.s.picks.get(p.getStringUUID());
        if (had != null) {
            Giant g0 = AdminServer.giant(had.giant);
            return AdminServer.Result.fail("You picked the " + (g0 == null ? had.giant : AdminServer.nameOf(g0))
                    + ". An admin can let you pick again.");
        }
        Giant g = AdminServer.giant(which);
        if (g == null || !pickable(server).contains(g))
            return AdminServer.Result.fail("There's no giant called " + which + " to pick here.");
        PlayerData.Pick pk = new PlayerData.Pick();
        pk.giant = g.key();
        pk.time = System.currentTimeMillis();
        d.s.picks.put(p.getStringUUID(), pk);
        d.setDirty();
        JsonObject o = new JsonObject();
        o.addProperty("players", 1);
        o.addProperty("player", p.getStringUUID());
        String capped = on(server, "books") && d.rule(p.getStringUUID()).own
                ? cappedFor(server, p.getStringUUID(), g, new Owns.All[1]) : null;
        o.addProperty("book", on(server, "books") && d.rule(p.getStringUUID()).own && capped == null);
        o.addProperty("finder", true);
        o.addProperty("giant", true);
        List<String> r = AdminServer.call(server, g, "mygiant", o.toString());
        if (r == null || r.isEmpty() || !r.get(0).equals("ok")) {
            d.s.picks.remove(p.getStringUUID());
            d.setDirty();
            return AdminServer.Result.fail(r != null && r.size() > 1 ? r.get(1)
                    : AdminServer.nameOf(g) + " can't be picked right now.");
        }
        AdminBook.changed("players");
        if (capped == null) return AdminServer.Result.said(r.subList(1, r.size()));
        List<String> said = new ArrayList<>(r.subList(1, r.size()));
        // (a giant that knows the limit says it himself)
        if (said.stream().noneMatch(l -> l.contains(capped))) said.add(capped);
        return AdminServer.Result.said(said);
    }

    /** GuideBridge "mygiantdone": what came of a pick */
    public static @Nullable List<String> pickDone(MinecraftServer server, String arg) {
        JsonObject o = AdminJson.object(arg);
        if (o == null) return null;
        try {
            if (o.get("players").getAsInt() > 1) return null;
            String player = o.get("player").getAsString(), outcome = o.get("outcome").getAsString();
            PlayerData d = PlayerData.get(server);
            PlayerData.Pick pk = d.s.picks.get(player);
            if (pk == null) return List.of("ok");
            if (outcome.equals("failed")) d.s.picks.remove(player);
            else {
                pk.outcome = outcome;
                pk.spawned = o.has("spawned") && o.get("spawned").getAsBoolean();
            }
            d.setDirty();
            AdminBook.changed("players");
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(player));
            if (p != null && (outcome.equals("own") || outcome.equals("own_cap")))
                Challenges.award(p, "yours_to_keep", "my_giant");
            return List.of("ok");
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** an admin lets a player pick again (their book and giant stay theirs) */
    public static AdminServer.Result repick(MinecraftServer server, String who, String player) {
        Who w = who(server, player);
        if (w == null) return AdminServer.Result.fail("No player called " + player + " has been on this server.");
        PlayerData d = PlayerData.get(server);
        if (d.s.picks.remove(w.id()) == null)
            return AdminServer.Result.said(List.of(w.name() + " hasn't picked a giant yet."));
        d.setDirty();
        AdminWorld.get(server).log(new AdminWorld.Change(System.currentTimeMillis(), who, AdminCatalog.ALL,
                "players:repick", w.name() + " may pick a giant again", "", "", ""));
        AdminBook.changed("players");
        ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(w.id()));
        if (p != null && on(server, "myGiant")) offerPick(p);
        String line = w.name() + " may pick a giant again (their book and giant stay theirs).";
        return new AdminServer.Result(true, List.of(line), List.of(who + ": " + line));
    }

    // the Guide's "This world" pages

    /** what the player's game asks: {"do":"world"} (the pages), "pick" (the screen), "choose" a giant, "vote" n */
    public static void ask(MinecraftServer server, ServerPlayer p, String json) {
        JsonObject o = AdminJson.object(json);
        if (o == null) return;
        String what = AdminJson.str(o, "do", 16);
        switch (what) {
            case "world" -> sendWorld(p);
            case "pick" -> {
                if (on(server, "myGiant")) offerPick(p);
            }
            case "choose" -> {
                AdminServer.Result r = pick(server, p, AdminJson.str(o, "giant", 32));
                tell(p, r);
                sendWorld(p);
            }
            case "vote" -> {
                AdminServer.Result r = Votes.vote(server, p, (int) AdminJson.num(o, "n", -1));
                tell(p, r);
                sendWorld(p);
            }
            // (Part 10A) This world â†’ Minigames: Join, Watch, Leave
            case "mgjoin", "mgwatch", "mgleave" -> {
                AdminServer.Result r = net.jj.giants.minigames.MgAdmin.ask(p, what);
                if (r != null) tell(p, r);
                sendWorld(p);
            }
            default -> {}
        }
    }

    static void tell(ServerPlayer p, AdminServer.Result r) {
        for (String l : r.lines())
            p.sendSystemMessage(Component.literal(l).withStyle(r.ok() ? ChatFormatting.WHITE : ChatFormatting.RED));
        JsonObject o = new JsonObject();
        o.addProperty("ok", r.ok());
        JsonArray a = new JsonArray();
        for (String l : r.lines()) a.add(l);
        o.add("lines", a);
        sender.accept(p, new PlayersStatePayload(PlayersStatePayload.SAID, o.toString()));
    }

    public static void sendWorld(ServerPlayer p) {
        sender.accept(p, new PlayersStatePayload(PlayersStatePayload.WORLD, worldJson(p.server, p).toString()));
    }

    /** the Rules, Votes and Leaderboards pages for this player, and whether they can still pick */
    public static JsonObject worldJson(MinecraftServer server, ServerPlayer p) {
        JsonObject o = new JsonObject();
        o.add("rules", RulesPage.json(server));
        o.add("vote", Votes.json(server, p));
        o.add("boards", Boards.json(server, p.getStringUUID(), 10));
        boolean pick = on(server, "myGiant") && !PlayerData.get(server).s.picks.containsKey(p.getStringUUID());
        o.addProperty("canPick", pick);
        // (Part 10A) This world â†’ Minigames
        o.add("minigame", net.jj.giants.minigames.MgAdmin.guideJson(server, p));
        return o;
    }

    // the clock

    private static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (t % 20 == 0) {
            Votes.tick(server);
            String music = option(server, "fightMusic");
            if (lastMusic != null && !lastMusic.equals(music))
                for (ServerPlayer p : server.getPlayerList().getPlayers()) sendSync(p);
            lastMusic = music;
        }
        if (t % 100 == 0) Challenges.tick(server);
        if (t % 10 == 0) rememberCredit();
        if (t % 600 == 0) {
            Boards.lookAtOwners(server);
            Boards.updateSigns(server);
        }
    }

    // a giant falls

    /** the giants counted lately (the game's death and leaving the world after it are the same fall) */
    private static final java.util.Set<UUID> counted = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
                    return size() > 256;
                }
            });

    /** the giants standing in loaded land now */
    private static final java.util.Set<LivingEntity> LOADED = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<>());
    /** each giant's last player behind a blow (giant id to player id) */
    private static final Map<UUID, UUID> CREDIT = new HashMap<>();

    /** twice a second: whoever the game says last hurt each giant */
    private static void rememberCredit() {
        for (LivingEntity le : List.copyOf(LOADED)) {
            Player p = le.getKillCredit() instanceof Player x ? x : le.getLastHurtByMob() instanceof Player y ? y : null;
            if (p != null) CREDIT.put(le.getUUID(), p.getUUID());
        }
    }

    private static @Nullable String kindOf(LivingEntity e) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        for (var m : MAIN.entrySet()) if (m.getValue().equals(type)) return m.getKey();
        return null;
    }

    /**
     * he is dying: the game's own word, or his mod's (every giant's solid kit has sitDying(): true while his own
     * death plays, for the giants whose health is their own)
     */
    static boolean dying(LivingEntity e) {
        if (e.isDeadOrDying()) return true;
        try {
            java.lang.reflect.Method m = e.getClass().getMethod("sitDying");
            return m.getReturnType() == boolean.class && (boolean) m.invoke(e);
        } catch (ReflectiveOperationException | RuntimeException x) {
            return false;
        }
    }

    /** one who was dying left the world (his own death done): counted once, with whoever last hurt him */
    private static void fell(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl) || e instanceof Player) return;
        // (Part 10A) a minigame's giants count only in the minigame
        if (net.jj.giants.minigames.MgLevels.temporary(sl)) return;
        String kind = kindOf(e);
        if (kind == null || !counted.add(e.getUUID())) return;
        ServerPlayer killer = e.getKillCredit() instanceof ServerPlayer sp ? sp
                : e.getLastHurtByMob() instanceof ServerPlayer sp2 ? sp2 : null;
        UUID was = CREDIT.remove(e.getUUID());
        if (killer == null && was != null) killer = sl.getServer().getPlayerList().getPlayer(was);
        // (Part 8) his remains, what he swallowed, the win for whoever brought him down
        net.jj.giants.systems.Systems.fell(e);
        slain(sl.getServer(), kind, killer);
    }

    private static void died(LivingEntity e, net.minecraft.world.damagesource.DamageSource src) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        // (Part 10A) nothing in a minigame's world counts on the boards, the challenges or the systems
        if (net.jj.giants.minigames.MgLevels.temporary(sl)) return;
        if (e instanceof ServerPlayer dead) {
            Challenges.playerDied(dead);
            return;
        }
        if (kindOf(e) != null && !counted.add(e.getUUID())) return;
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString();
        String kind = null;
        for (var m : MAIN.entrySet()) if (m.getValue().equals(type)) kind = m.getKey();
        if (kind == null) return;
        ServerPlayer killer = src.getEntity() instanceof ServerPlayer sp ? sp
                : e.getKillCredit() instanceof ServerPlayer sp2 ? sp2 : null;
        // (Part 8) his remains, what he swallowed, the win for whoever brought him down
        net.jj.giants.systems.Systems.fell(e);
        slain(sl.getServer(), kind, killer);
    }

    /** a giant of this kind fell, by this player's hand (or nobody's) */
    public static void slain(MinecraftServer server, String kind, @Nullable ServerPlayer killer) {
        Challenges.fell(server, kind, killer);
        // (Part 6) the stats page
        net.jj.giants.tools.Stats.fell(server, kind, killer != null);
        if (killer == null) return;
        PlayerData d = PlayerData.get(server);
        PlayerData.Stats st = d.stats(killer.getStringUUID(), killer.getGameProfile().getName());
        st.kills.merge(kind, 1, Integer::sum);
        d.setDirty();
        AdminBook.changed("players");
    }
}
