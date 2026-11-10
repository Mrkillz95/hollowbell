package net.jj.giants.fun;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.jj.giants.Giant;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.admin.AdminLive;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 7, ADMIN_BRIDGE.md 23.3) What an admin gives one giant: a name and title on his boss bar (his custom name),
 * his own loot, and giant tags (a named set of his own stats and looks: health, damage, speed, size, title, colour,
 * glowing, loot). His own stats are entity tags his mod reads (jj_giants.health=2 ...): kept with him wherever he goes.
 */
public final class Marks {
    private Marks() {}

    public static final String TAG = "jj_giants.", NO_DROPS = "jj_giants.nodrops", WEARS = "jj_giants.tag=";
    public static final List<String> COLOURS = List.of("white", "gold", "red", "purple", "aqua", "green", "yellow");
    public static final int MOST_NAME = 32;

    static ChatFormatting colour(String c) {
        return switch (c) {
            case "gold" -> ChatFormatting.GOLD;
            case "red" -> ChatFormatting.RED;
            case "purple" -> ChatFormatting.LIGHT_PURPLE;
            case "aqua" -> ChatFormatting.AQUA;
            case "green" -> ChatFormatting.GREEN;
            case "yellow" -> ChatFormatting.YELLOW;
            default -> ChatFormatting.WHITE;
        };
    }

    static String nameOf(LivingEntity e) {
        Giant g = AdminServer.giant(FunGiants.kindOf(e));
        return g == null ? "giant" : AdminServer.nameOf(g);
    }

    /**
     * his name as it shows: "Grukk, Terror of the Bay"; with no name his own ("Wreckback, Terror of the Bay"), or the
     * title alone when his own name is left off ("Terror of the Bay")
     */
    static String shownName(LivingEntity e, FunWorld.Mark m) {
        String base = !m.name.isEmpty() ? m.name : ownName(e, m) ? nameOf(e) : "";
        return m.title.isEmpty() ? base : base.isEmpty() ? m.title : base + ", " + m.title;
    }

    /** (tags) whether his own name goes before his title: his own choice, else his tag's, else yes */
    public static boolean ownName(LivingEntity e, FunWorld.Mark m) {
        if (m.ownName != null) return m.ownName;
        if (m.tag.isEmpty() || e.getServer() == null) return true;
        FunWorld.Tag t = FunWorld.get(e.getServer()).tag(m.tag);
        return t == null || t.ownName;
    }

    /** his custom name made from his marks (none left: back to what he had before) */
    static void applyName(LivingEntity e, FunWorld.Mark m) {
        if (m.name.isEmpty() && m.title.isEmpty()) {
            e.setCustomName(m.oldName.isEmpty() ? null : Component.literal(m.oldName));
            e.setCustomNameVisible(m.shown && !m.oldName.isEmpty());
            return;
        }
        e.setCustomName(Component.literal(shownName(e, m)).withStyle(colour(m.colour)));
        e.setCustomNameVisible(m.shown);
    }

    static String clean(String s) {
        s = s == null ? "" : s.strip().replaceAll("[\\p{Cntrl}§]", "");
        return s.length() > MOST_NAME ? s.substring(0, MOST_NAME) : s;
    }

    /** his name, title, colour and whether it shows over his head; null leaves that part as it is */
    public static AdminServer.Result name(MinecraftServer server, String who, LivingEntity e, @Nullable String name,
            @Nullable String title, @Nullable String colour, @Nullable Boolean shown) {
        return name(server, who, e, name, title, colour, shown, null, false);
    }

    /**
     * (tags) the same, and whether his own name goes before his title (ownName null leaves it; asTag: back to what his
     * tag says)
     */
    public static AdminServer.Result name(MinecraftServer server, String who, LivingEntity e, @Nullable String name,
            @Nullable String title, @Nullable String colour, @Nullable Boolean shown, @Nullable Boolean ownName,
            boolean asTag) {
        FunWorld w = FunWorld.get(server);
        String id = e.getStringUUID();
        boolean fresh = w.markOf(id) == null;
        FunWorld.Mark m = w.mark(id, FunGiants.kindOf(e));
        if (fresh && e.hasCustomName() && e.getCustomName() != null) m.oldName = e.getCustomName().getString();
        if (colour != null) {
            String c = colour.toLowerCase(Locale.ROOT).strip();
            if (!COLOURS.contains(c)) return AdminServer.Result.fail("The colour has to be one of: "
                    + String.join(", ", COLOURS) + ".");
            m.colour = c;
        }
        if (name != null) m.name = clean(name);
        if (title != null) m.title = clean(title);
        if (shown != null) m.shown = shown;
        if (asTag) m.ownName = null;
        else if (ownName != null) m.ownName = ownName;
        applyName(e, m);
        w.tidy(id);
        String now = m.name.isEmpty() && m.title.isEmpty() ? "" : shownName(e, m);
        String what = now.isEmpty() ? nameOf(e) + ": his own name again." : nameOf(e) + ": now \"" + now + "\".";
        if (ownName != null || asTag) what += ownName(e, m) ? " His own name shows before a title."
                : " His own name is left off: a title shows alone.";
        Fun.log(server, who, FunGiants.kindOf(e), "name", "name", now.isEmpty() ? "cleared" : now);
        Fun.changed();
        return new AdminServer.Result(true, List.of(what), List.of(who + (now.isEmpty() ? " cleared a " + nameOf(e)
                + "'s name." : " named a " + nameOf(e) + " \"" + now + "\".")));
    }

    /**
     * (Part 11) a script's title for him (the boss bar shows it): set quietly, without a change-log line, the way his
     * name and title are kept for the Fun page ("" takes it off)
     */
    public static void scriptTitle(MinecraftServer server, LivingEntity e, String title) {
        FunWorld w = FunWorld.get(server);
        String id = e.getStringUUID();
        boolean fresh = w.markOf(id) == null;
        FunWorld.Mark m = w.mark(id, FunGiants.kindOf(e));
        if (fresh && e.hasCustomName() && e.getCustomName() != null) m.oldName = e.getCustomName().getString();
        m.title = clean(title);
        applyName(e, m);
        w.tidy(id);
    }

    // loot

    /** what a container holds as saved text (at most 54 stacks), or null when there's no container there */
    static @Nullable List<String> copyChest(ServerPlayer p) {
        BlockPos at = Spots.looked(p, 8);
        if (at == null || !(p.level().getBlockEntity(at) instanceof Container c)) return null;
        List<String> out = new ArrayList<>();
        for (int i = 0; i < c.getContainerSize() && out.size() < 54; i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty()) out.add(s.save(p.registryAccess()).toString());
        }
        return out;
    }

    static boolean tableExists(MinecraftServer server, String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return false;
        LootTable t = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, rl));
        return t != LootTable.EMPTY;
    }

    /**
     * his loot: mode add/replace/clear; from "chest" (the container the player looks at) or "table" (a loot table id)
     */
    public static AdminServer.Result loot(MinecraftServer server, String who, @Nullable ServerPlayer p, LivingEntity e,
            String mode, String from, String table) {
        FunWorld w = FunWorld.get(server);
        String id = e.getStringUUID();
        mode = mode.toLowerCase(Locale.ROOT);
        if (mode.equals("clear")) {
            FunWorld.Mark m = w.markOf(id);
            if (m != null) m.loot = null;
            e.removeTag(NO_DROPS);
            w.tidy(id);
            Fun.log(server, who, FunGiants.kindOf(e), "loot", "loot", "cleared");
            Fun.changed();
            return new AdminServer.Result(true, List.of(nameOf(e) + ": his own loot again."),
                    List.of(who + " cleared a " + nameOf(e) + "'s loot."));
        }
        if (!mode.equals("add") && !mode.equals("replace"))
            return AdminServer.Result.fail("Loot is add, replace or clear.");
        FunWorld.Loot l = new FunWorld.Loot();
        l.mode = mode;
        if (from.equals("table")) {
            if (!tableExists(server, table)) return AdminServer.Result.fail("There's no loot table called " + table + ".");
            l.table = table;
        } else {
            List<String> items = p == null ? null : copyChest(p);
            if (items == null) return AdminServer.Result.fail("Look at a chest, barrel or shulker box (8 blocks at most).");
            if (items.isEmpty()) return AdminServer.Result.fail("That container is empty.");
            l.items = items;
        }
        FunWorld.Mark m = w.mark(id, FunGiants.kindOf(e));
        m.loot = l;
        if (mode.equals("replace")) e.addTag(NO_DROPS);
        else e.removeTag(NO_DROPS);
        w.setDirty();
        String said = describe(l);
        Fun.log(server, who, FunGiants.kindOf(e), "loot", "loot", said);
        Fun.changed();
        return new AdminServer.Result(true, List.of(nameOf(e) + ": " + said + "."),
                List.of(who + " gave a " + nameOf(e) + " his own loot (" + said + ")."));
    }

    static String describe(@Nullable FunWorld.Loot l) {
        if (l == null || l.empty()) return "his own loot";
        String what = !l.items.isEmpty() ? l.items.size() + (l.items.size() == 1 ? " stack" : " stacks")
                + (l.table.isEmpty() ? "" : " and the table " + l.table) : "the table " + l.table;
        return (l.mode.equals("replace") ? "drops only " : "also drops ") + what;
    }

    /** he fell: his loot drops where he fell (once) */
    private static final java.util.Set<java.util.UUID> fallen = java.util.Collections.newSetFromMap(
            new java.util.LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<java.util.UUID, Boolean> eldest) {
                    return size() > 256;
                }
            });

    static void fell(LivingEntity e) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        // (once: some giants leave the world more than once on their way out)
        if (!fallen.add(e.getUUID())) return;
        FunWorld w = FunWorld.get(sl.getServer());
        FunWorld.Mark m = w.s.marks.remove(e.getStringUUID());
        if (m == null) return;
        w.setDirty();
        Fun.changed();
        if (m.loot == null || m.loot.empty()) return;
        Vec3 at = e.position();
        List<ItemStack> drop = new ArrayList<>();
        for (String s : m.loot.items)
            try {
                CompoundTag t = TagParser.parseTag(s);
                ItemStack.parse(sl.registryAccess(), t).ifPresent(drop::add);
            } catch (Exception bad) {
                GiantsGuideMod.LOG.warn("A giant's loot had a stack that can't be read: {}", s);
            }
        if (!m.loot.table.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(m.loot.table);
            if (rl != null)
                try {
                    LootTable t = sl.getServer().reloadableRegistries()
                            .getLootTable(ResourceKey.create(Registries.LOOT_TABLE, rl));
                    LootParams lp = new LootParams.Builder(sl)
                            .withParameter(LootContextParams.ORIGIN, at)
                            .withOptionalParameter(LootContextParams.THIS_ENTITY, e)
                            .create(LootContextParamSets.CHEST);
                    drop.addAll(t.getRandomItems(lp));
                } catch (RuntimeException bad) {
                    GiantsGuideMod.LOG.warn("A giant's loot table {} couldn't be rolled: {}", m.loot.table, bad.toString());
                }
        }
        for (ItemStack s : drop) {
            ItemEntity it = new ItemEntity(sl, at.x, at.y + 1, at.z, s);
            it.setDeltaMovement((sl.random.nextDouble() - 0.5) * 0.3, 0.3, (sl.random.nextDouble() - 0.5) * 0.3);
            it.setDefaultPickUpDelay();
            sl.addFreshEntity(it);
        }
        GiantsGuideMod.LOG.info("A {} fell at {}: dropped his admin loot ({} stacks)", nameOf(e),
                e.blockPosition().toShortString(), drop.size());
    }

    // giant tags

    /** his stats tags: one per stat, none when x1 */
    static void setStat(LivingEntity e, String what, double v) {
        // (Part 8) times the systems' own factor on him (1 when there's none: as before)
        double sys = net.jj.giants.systems.SystemsTags.sys(e, what);
        if (Math.abs(sys - 1) > 1e-9) v = Math.round(v * sys * 1000) / 1000.0;
        for (String t : List.copyOf(e.getTags())) if (t.startsWith(TAG + what + "=")) e.removeTag(t);
        if (Math.abs(v - 1) > 1e-9) e.addTag(TAG + what + "=" + AdminSetting.plain(v));
    }

    static double stat(LivingEntity e, String what) {
        for (String t : e.getTags())
            if (t.startsWith(TAG + what + "="))
                try {
                    return Double.parseDouble(t.substring((TAG + what + "=").length()));
                } catch (NumberFormatException bad) {
                    return 1;
                }
        return 1;
    }

    public static @Nullable String wears(LivingEntity e) {
        for (String t : e.getTags()) if (t.startsWith(WEARS)) return t.substring(WEARS.length());
        return null;
    }

    public static boolean validName(String n) {
        return n != null && n.matches("[A-Za-z0-9 '\\-]{1,24}") && !n.isBlank();
    }

    /** makes or changes a tag (old: the name it had, "" for a new one); checked, then every wearer changed */
    public static AdminServer.Result saveTag(MinecraftServer server, String who, String old, FunWorld.Tag t) {
        FunWorld w = FunWorld.get(server);
        t.name = t.name == null ? "" : t.name.strip();
        if (!validName(t.name))
            return AdminServer.Result.fail("A tag's name is 1 to 24 letters, numbers, spaces, ' or -.");
        if (!(t.health >= 0.1 && t.health <= 20) || !(t.damage >= 0.1 && t.damage <= 20))
            return AdminServer.Result.fail("Health and damage have to be numbers from 0.1 to 20.");
        if (!(t.speed >= 0.25 && t.speed <= 4)) return AdminServer.Result.fail("Speed has to be a number from 0.25 to 4.");
        if (!(t.size == 0 || t.size >= 0.01 && t.size <= 4))
            return AdminServer.Result.fail("Size has to be 0 (leave him as he is) or a number from 0.01 to 4.");
        t.title = clean(t.title);
        if (t.colour == null || !COLOURS.contains(t.colour)) t.colour = "white";
        FunWorld.Tag was = old == null || old.isBlank() ? null : w.tag(old);
        FunWorld.Tag clash = w.tag(t.name);
        if (clash != null && clash != was) return AdminServer.Result.fail("There's already a tag called " + clash.name + ".");
        if (was != null) w.s.tags.remove(was.name);
        w.s.tags.put(t.name, t);
        w.setDirty();
        // every wearer standing in the world now (the rest when they next come into it)
        int n = 0;
        for (LivingEntity e : FunGiants.standing(server, null)) {
            String wt = wears(e);
            if (wt == null || !(wt.equalsIgnoreCase(t.name) || was != null && wt.equalsIgnoreCase(was.name))) continue;
            give(server, who, e, t, false);
            n++;
        }
        Fun.log(server, who, "all", "tag", "tag " + t.name, was == null ? "made" : "changed");
        Fun.changed();
        return new AdminServer.Result(true, List.of((was == null ? "Made the tag " : "Changed the tag ") + t.name
                + (n > 0 ? " (" + n + (n == 1 ? " giant wears it)." : " giants wear it).") : ".")),
                List.of(who + (was == null ? " made the giant tag " : " changed the giant tag ") + t.name + "."));
    }

    public static AdminServer.Result deleteTag(MinecraftServer server, String who, String name) {
        FunWorld w = FunWorld.get(server);
        FunWorld.Tag t = w.tag(name);
        if (t == null) return AdminServer.Result.fail("There's no tag called " + name + ".");
        int n = 0;
        for (LivingEntity e : FunGiants.standing(server, null))
            if (t.name.equalsIgnoreCase(wears(e))) {
                take(server, who, e, false);
                n++;
            }
        w.s.tags.remove(t.name);
        w.setDirty();
        Fun.log(server, who, "all", "tag", "tag " + t.name, "deleted");
        Fun.changed();
        return new AdminServer.Result(true, List.of("Deleted the tag " + t.name + (n > 0 ? " and took it off " + n
                + (n == 1 ? " giant." : " giants.") : ".")), List.of(who + " deleted the giant tag " + t.name + "."));
    }

    /** gives a tag to a giant standing in the world */
    public static AdminServer.Result give(MinecraftServer server, String who, LivingEntity e, FunWorld.Tag t,
            boolean say) {
        String kind = FunGiants.kindOf(e);
        Giant g = kind == null ? null : AdminServer.giant(kind);
        if (g == null) return AdminServer.Result.fail("That isn't a giant.");
        boolean stats = FunGiants.does(server, g).contains("stats");
        String before = wears(e);
        if (before != null && !before.equalsIgnoreCase(t.name)) take(server, who, e, false);
        for (String x : List.copyOf(e.getTags())) if (x.startsWith(WEARS)) e.removeTag(x);
        e.addTag(WEARS + t.name);
        setStat(e, "health", t.health);
        setStat(e, "damage", t.damage);
        setStat(e, "speed", t.speed);
        List<String> said = new ArrayList<>();
        if (stats) {
            AdminServer.Result r = FunGiants.act(server, g, e.getStringUUID(), "stats", new JsonObject());
            if (!r.ok()) said.addAll(r.lines());
        } else said.add(AdminServer.nameOf(g) + ": this version doesn't read a tag's stats. Update the "
                + AdminServer.nameOf(g) + " mod.");
        if (t.size > 0) {
            JsonObject o = new JsonObject();
            o.addProperty("live", AdminLive.VERSION);
            o.addProperty("id", e.getStringUUID());
            o.addProperty("do", "size");
            o.addProperty("value", AdminSetting.plain(t.size));
            o.addProperty("by", "-");
            List<String> r = AdminServer.call(server, g, AdminLive.ACT, o.toString());
            if (r == null || r.isEmpty() || !r.get(0).equals("ok")) said.add(r != null && r.size() > 1 ? r.get(1)
                    : AdminServer.nameOf(g) + ": his size couldn't be changed.");
            else net.jj.giants.systems.Systems.resized(server, e.getStringUUID(), AdminSetting.plain(t.size));
        }
        FunWorld w = FunWorld.get(server);
        String id = e.getStringUUID();
        boolean fresh = w.markOf(id) == null;
        FunWorld.Mark m = w.mark(id, kind);
        if (fresh && e.hasCustomName() && e.getCustomName() != null) m.oldName = e.getCustomName().getString();
        m.tag = t.name;
        if (!t.title.isEmpty()) {
            m.title = t.title;
            m.colour = t.colour;
        }
        applyName(e, m);
        if (t.glow) {
            if (!e.hasGlowingTag()) m.tagGlow = true;
            e.setGlowingTag(true);
        } else if (m.tagGlow) {
            e.setGlowingTag(false);
            m.tagGlow = false;
        }
        if (t.loot != null && !t.loot.empty()) {
            m.loot = t.loot;
            if (t.loot.mode.equals("replace")) e.addTag(NO_DROPS);
            else e.removeTag(NO_DROPS);
        }
        w.setDirty();
        Fun.changed();
        if (!say) return AdminServer.Result.said(said);
        Fun.log(server, who, kind, "tag", "tag", t.name);
        List<String> lines = new ArrayList<>();
        // "The Wreckback 2 now wears Event Boss: health x3, damage x2. His bar reads "Event Boss"."
        lines.add("The " + FunGiants.label(server, e) + " now wears " + t.name + statWords(t) + "."
                + (e.hasCustomName() && e.getCustomName() != null && !m.title.isEmpty() ? " His bar reads \""
                        + e.getCustomName().getString() + "\"." : ""));
        lines.addAll(said);
        return new AdminServer.Result(true, lines, List.of(who + " gave a " + AdminServer.nameOf(g) + " the tag "
                + t.name + "."));
    }

    /** takes his tag off: x1 again, his title, glow and loot from it gone */
    public static AdminServer.Result take(MinecraftServer server, String who, LivingEntity e, boolean say) {
        String kind = FunGiants.kindOf(e);
        Giant g = kind == null ? null : AdminServer.giant(kind);
        String had = wears(e);
        if (g == null) return AdminServer.Result.fail("That isn't a giant.");
        if (had == null) return AdminServer.Result.fail(AdminServer.nameOf(g) + " wears no tag.");
        FunWorld w = FunWorld.get(server);
        FunWorld.Tag t = w.tag(had);
        for (String x : List.copyOf(e.getTags()))
            if (x.startsWith(WEARS) || x.startsWith(TAG + "health=") || x.startsWith(TAG + "damage=")
                    || x.startsWith(TAG + "speed=")) e.removeTag(x);
        // (Part 8) the systems' own factors stay on him
        net.jj.giants.systems.SystemsTags.rewrite(server, e);
        if (FunGiants.does(server, g).contains("stats"))
            FunGiants.act(server, g, e.getStringUUID(), "stats", new JsonObject());
        FunWorld.Mark m = w.markOf(e.getStringUUID());
        if (m != null) {
            m.tag = "";
            if (t != null && !t.title.isEmpty() && t.title.equals(m.title)) {
                m.title = "";
                m.colour = "white";
            }
            if (m.tagGlow) {
                e.setGlowingTag(false);
                m.tagGlow = false;
            }
            if (t != null && t.loot != null && m.loot != null && sameLoot(t.loot, m.loot)) {
                m.loot = null;
                e.removeTag(NO_DROPS);
            }
            applyName(e, m);
            w.tidy(e.getStringUUID());
        }
        w.setDirty();
        Fun.changed();
        if (!say) return AdminServer.Result.said(List.of());
        Fun.log(server, who, kind, "tag", "tag", "taken off (" + had + ")");
        return new AdminServer.Result(true, List.of(AdminServer.nameOf(g) + ": the tag " + had + " is off him."),
                List.of(who + " took the tag " + had + " off a " + AdminServer.nameOf(g) + "."));
    }

    /** ": health x3, damage x2" (only what it changes; "" when it changes none of his stats) */
    static String statWords(FunWorld.Tag t) {
        List<String> w = new ArrayList<>();
        if (Math.abs(t.health - 1) > 1e-9) w.add("health x" + AdminSetting.plain(t.health));
        if (Math.abs(t.damage - 1) > 1e-9) w.add("damage x" + AdminSetting.plain(t.damage));
        if (Math.abs(t.speed - 1) > 1e-9) w.add("speed x" + AdminSetting.plain(t.speed));
        if (t.size > 0) w.add("size " + AdminSetting.plain(t.size));
        return w.isEmpty() ? "" : ": " + String.join(", ", w);
    }

    // (tags) whom to give a tag to: the giant you look at, the nearest, every one of a kind

    /** a giant in a minigame's own world (never given tags from outside it) */
    private static boolean inGame(LivingEntity e) {
        return net.jj.giants.minigames.Minigames.inMinigame(e);
    }

    /**
     * the giant a player looks at, up to so far: the nearest one his look runs into (not through a wall), else the
     * one his look passes closest to (within 5 degrees of his middle)
     */
    public static @Nullable LivingEntity lookedAt(ServerPlayer p, double far) {
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1f), to = eye.add(dir.scale(far));
        BlockHitResult wallHit = p.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        double wall = wallHit.getType() == HitResult.Type.MISS ? far : wallHit.getLocation().distanceTo(eye);
        LivingEntity hit = null, close = null;
        double hd = Double.MAX_VALUE, best = Math.toRadians(5);
        MinecraftServer server = p.getServer();
        if (server == null) return null;
        for (LivingEntity e : FunGiants.standing(server, null)) {
            if (e.level() != p.level() || inGame(e)) continue;
            AABB box = e.getBoundingBox().inflate(0.5 + 0.1 * e.getBbWidth());
            java.util.Optional<Vec3> in = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, to);
            if (in.isPresent()) {
                double d = in.get().distanceTo(eye);
                if (d <= wall + 1 && d < hd) {
                    hd = d;
                    hit = e;
                }
                continue;
            }
            Vec3 v = e.getBoundingBox().getCenter().subtract(eye);
            double len = v.length();
            if (len < 1e-6 || len > far) continue;
            // (not through a wall: the wall has to be past his near side)
            if (wall < len - Math.max(e.getBbWidth(), e.getBbHeight())) continue;
            double a = Math.acos(Math.max(-1, Math.min(1, v.dot(dir) / len)));
            if (a < best) {
                best = a;
                close = e;
            }
        }
        return hit != null ? hit : close;
    }

    /** the giant standing nearest to there in that world (not one in a minigame) */
    public static @Nullable LivingEntity nearest(MinecraftServer server, ServerLevel l, Vec3 at) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : FunGiants.standing(server, null)) {
            if (e.level() != l || inGame(e)) continue;
            double d = e.position().distanceToSqr(at);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** gives a tag (null: takes his off) to the giant a player looks at, or the one nearest to him */
    public static AdminServer.Result giveTo(MinecraftServer server, String who, ServerPlayer p, String to,
            @Nullable FunWorld.Tag t) {
        boolean look = !to.equals("nearest");
        LivingEntity e = look ? lookedAt(p, Spots.LOOK) : nearest(server, p.serverLevel(), p.position());
        if (e == null) return AdminServer.Result.fail(look ? "Look at a giant first (256 blocks at most)."
                : "There's no giant standing in this world.");
        return t == null ? take(server, who, e, true) : give(server, who, e, t, true);
    }

    /** gives a tag to every giant of that kind standing in the world now (the ones out of it aren't changed) */
    public static AdminServer.Result giveEvery(MinecraftServer server, String who, String kind, FunWorld.Tag t) {
        Giant g = AdminServer.giant(kind.toLowerCase(Locale.ROOT).strip());
        if (g == null) return AdminServer.Result.fail("No giant called " + kind + ".");
        String name = AdminServer.nameOf(g);
        List<LivingEntity> all = new ArrayList<>();
        for (LivingEntity e : FunGiants.standing(server, g.key())) if (!inGame(e)) all.add(e);
        if (all.isEmpty()) return AdminServer.Result.fail("There's no " + name + " standing in the world now.");
        List<String> more = new ArrayList<>();
        for (LivingEntity e : all) {
            AdminServer.Result r = give(server, who, e, t, false);
            for (String l : r.lines()) if (!more.contains(l)) more.add(l);
        }
        int out = Math.max(0, FunGiants.liveIds(server, g).size() - all.size());
        Fun.log(server, who, g.key(), "tag", "tag", t.name + " (every " + name + ", " + all.size() + ")");
        List<String> lines = new ArrayList<>();
        lines.add((all.size() == 1 ? "The " + name + " now wears " : "All " + all.size() + " " + name + (name.endsWith("s") ? "es" : "s")
                + " now wear ")
                + t.name + statWords(t) + "." + (out > 0 ? " (" + out + " out of the world " + (out == 1 ? "isn't"
                        : "aren't") + " changed: give it to them when they're back.)" : ""));
        lines.addAll(more);
        return new AdminServer.Result(true, lines, List.of(who + " gave every " + name + " the tag " + t.name + "."));
    }

    private static boolean sameLoot(FunWorld.Loot a, FunWorld.Loot b) {
        return a.mode.equals(b.mode) && a.table.equals(b.table) && a.items.equals(b.items);
    }

    /** a giant came into the world: wearing a tag that changed (or went) since, he's put right */
    static void loaded(MinecraftServer server, LivingEntity e) {
        String had = wears(e);
        if (had == null) return;
        FunWorld.Tag t = FunWorld.get(server).tag(had);
        if (t == null) {
            take(server, "Server", e, false);
            return;
        }
        // (Part 8: his tag's stats times the systems' own factors)
        if (Math.abs(stat(e, "health") - t.health * net.jj.giants.systems.SystemsTags.sys(e, "health")) > 1e-3
                || Math.abs(stat(e, "damage") - t.damage * net.jj.giants.systems.SystemsTags.sys(e, "damage")) > 1e-3
                || Math.abs(stat(e, "speed") - t.speed * net.jj.giants.systems.SystemsTags.sys(e, "speed")) > 1e-3)
            give(server, "Server", e, t, false);
    }

    /** how many standing giants wear each tag now */
    static Map<String, Integer> wearing(MinecraftServer server) {
        Map<String, Integer> out = new java.util.HashMap<>();
        for (LivingEntity e : FunGiants.standing(server, null)) {
            String t = wears(e);
            if (t != null) out.merge(t.toLowerCase(Locale.ROOT), 1, Integer::sum);
        }
        return out;
    }
}
