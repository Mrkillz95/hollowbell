package net.jj.giants.systems;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminJson;
import net.jj.giants.admin.AdminServer;
import net.jj.giants.admin.AdminSetting;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 8, ADMIN_BRIDGE.md §24.3) The ten-second look: every giant's live rows, and what Herds, Growth, Hunger and
 * Loyalty do with them. Only giants standing in loaded land are touched; nothing is loaded.
 */
public final class Look {
    private Look() {}

    /** one kind's live list: his header (smallest and biggest size, moods, what he does) and his rows */
    record Kind(Giant g, String key, String name, double min, double max, List<String> moods, List<String> does,
            List<JsonObject> rows) {}

    private static int looks;
    /** the giants the systems put a tag on (to take them off when the systems go off) */
    private static boolean tagged;

    static void forget() {
        looks = 0;
        tagged = false;
        sysDoes.clear();
    }

    static List<Kind> read(MinecraftServer server) {
        List<Kind> out = new ArrayList<>();
        for (Giant g : AdminServer.giants()) {
            List<String> lines = AdminServer.call(server, g, "live", "1");
            if (lines == null || lines.isEmpty()) continue;
            JsonObject head = AdminJson.object(lines.get(0));
            if (head == null || !head.has("live")) continue;
            double min = 0.01, max = 4;
            if (head.get("size") instanceof JsonArray sz && sz.size() == 2) {
                min = sz.get(0).getAsDouble();
                max = sz.get(1).getAsDouble();
            }
            List<JsonObject> rows = new ArrayList<>();
            for (String l : lines.subList(1, lines.size())) {
                JsonObject r = AdminJson.object(l);
                if (r != null && r.has("id") && r.has("state")) rows.add(r);
            }
            out.add(new Kind(g, g.key(), AdminServer.nameOf(g), min, max,
                    Systems.names(head.getAsJsonArray("moods")), Systems.names(head.getAsJsonArray("does")), rows));
        }
        return out;
    }

    static boolean here(JsonObject r) {
        return "here".equals(AdminJson.str(r, "state", 16));
    }

    static boolean flag(JsonObject r, String f) {
        return r.has(f) && r.get(f).isJsonPrimitive() && r.get(f).getAsBoolean();
    }

    /** the look, every ten seconds */
    static void tick(MinecraftServer server) {
        boolean herd = Systems.on(server, "herd"), grow = Systems.on(server, "growth"),
                hunger = Systems.on(server, "hunger"), loyal = Systems.on(server, "loyalty");
        if (!herd && !grow && !hunger && !loyal) {
            if (tagged) untag(server);
            return;
        }
        if (server.getPlayerList().getPlayers().isEmpty() && !forced) return;
        looks++;
        SystemsData d = SystemsData.get(server);
        long now = server.overworld().getGameTime();
        List<Kind> kinds = read(server);
        boolean follow = grow || hunger || loyal;
        for (Kind k : kinds) {
            for (JsonObject r : k.rows()) {
                if (!here(r)) continue;
                String id = AdminJson.str(r, "id", 40);
                LivingEntity e = FunGiants.find(server, id);
                if (e == null || !follow) continue;
                SystemsData.G g = d.giant(id, k.key());
                if (g.born == 0) {
                    g.born = now;
                    g.start = AdminJson.num(r, "size", FunGiants.sizeOf(e));
                }
                g.seen = now;
                if (flag(r, "fighting")) g.lastFight = now;
                if (hunger) hunger(server, k, r, e, g, now);
                else g.food = 100;
                if (loyal) loyalty(server, k, r, e, g, now);
                else g.loyalty = -1;
                if (grow && looks % 3 == 0) growth(server, k, r, e, g, now);
                if (!grow) g.titan = false;
                tags(server, k, e, g, loyal && devotedNear(server, e, g));
                d.setDirty();
            }
        }
        if (herd) herds(server, kinds);
        // giants not seen for a game day are let go of (one gone for good, or far off for long)
        d.s.giants.values().removeIf(g -> g.seen > 0 && now - g.seen > 24000 && g.belly.isEmpty());
        SystemsAdmin.changed();
    }

    // Growth

    private static void growth(MinecraftServer server, Kind k, JsonObject r, LivingEntity e, SystemsData.G g,
            long now) {
        double most = Math.max(1, Systems.num(server, "growthMost"));
        double days = (now - g.born) / 24000.0;
        double grown = Math.min(most, 1 + Systems.num(server, "growthPerDay") * days
                + Systems.num(server, "growthPerWin") * g.wins);
        int titanDays = (int) Systems.num(server, "growthTitan");
        if (!g.titan && titanDays > 0 && now - Math.max(g.lastFight, g.born) >= titanDays * 24000L) {
            g.titan = true;
            tellNear(e, Component.literal("The " + k.name() + " has grown into a Titan.")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 256);
            Systems.log("a {} became a Titan at {}", k.name(), e.blockPosition().toShortString());
        }
        if (g.titan) grown = most;
        double want = Math.max(k.min(), Math.min(k.max(), g.start * grown));
        double size = AdminJson.num(r, "size", FunGiants.sizeOf(e));
        if (size > 0 && Math.abs(want - size) / size >= 0.02 && k.does().contains("size"))
            Systems.live(server, k.g(), e.getStringUUID(), "size", AdminSetting.plain(Math.round(want * 1000) / 1000.0));
    }

    /** (an admin's command) this one becomes a Titan now: his biggest growth, twice the health and damage */
    static String titanNow(MinecraftServer server, LivingEntity e) {
        String kind = FunGiants.kindOf(e);
        SystemsData d = SystemsData.get(server);
        SystemsData.G g = d.giant(e.getStringUUID(), kind);
        long now = server.overworld().getGameTime();
        if (g.born == 0) {
            g.born = now;
            g.start = FunGiants.sizeOf(e);
        }
        g.seen = now;
        g.titan = true;
        d.setDirty();
        for (Kind k : read(server)) {
            if (!k.key().equals(kind)) continue;
            for (JsonObject r : k.rows())
                if (AdminJson.str(r, "id", 40).equals(e.getStringUUID())) {
                    growth(server, k, r, e, g, now);
                    tags(server, k, e, g, false);
                }
        }
        SystemsAdmin.changed();
        Giant gk = AdminServer.giant(kind);
        return (gk == null ? kind : AdminServer.nameOf(gk)) + ": a Titan now (size " + AdminSetting.plain(
                Math.round(FunGiants.sizeOf(e) * 100) / 100.0) + ").";
    }

    /** a giant (or a player) fell to this one: a win, and a fight */
    static void win(MinecraftServer server, String id) {
        if (!Systems.on(server, "growth")) return;
        SystemsData d = SystemsData.get(server);
        SystemsData.G g = d.s.giants.get(id);
        if (g == null) {
            LivingEntity e = FunGiants.find(server, id);
            if (e == null) return;
            g = d.giant(id, FunGiants.kindOf(e));
            g.born = server.overworld().getGameTime();
            g.start = FunGiants.sizeOf(e);
            g.seen = g.born;
        }
        g.wins++;
        g.lastFight = server.overworld().getGameTime();
        d.setDirty();
    }

    /** a giant told the Guide he was hurt (by whoever): his fight, the win's credit, his owner's hand */
    static void hurt(MinecraftServer server, String id, String by) {
        SystemsData d = SystemsData.get(server);
        long now = server.overworld().getGameTime();
        SystemsData.G g = d.s.giants.get(id);
        if (g == null) return;
        g.lastFight = now;
        if (!by.isEmpty()) {
            g.lastBy = by;
            g.lastByAt = now;
            SystemsData.G other = d.s.giants.get(by);
            if (other != null) other.lastFight = now;
            if (Systems.on(server, "loyalty") && by.equals(g.owner) && g.loyalty >= 0)
                g.loyalty = Math.max(0, g.loyalty - 5);
        }
        d.setDirty();
    }

    // Hunger

    private static void hunger(MinecraftServer server, Kind k, JsonObject r, LivingEntity e, SystemsData.G g,
            long now) {
        double days = Math.max(0.5, Systems.num(server, "hungerDays"));
        double loss = 100.0 / (days * 24000) * 200 * (flag(r, "asleep") ? 0.5 : 1);
        double was = g.food;
        g.food = Math.max(0, g.food - loss);
        // food lying within his reach
        if (e.level() instanceof ServerLevel sl) {
            double reach = Math.min(64, Math.max(4, FunGiants.reach(k.key(), FunGiants.sizeOf(e))));
            int stacks = 0;
            for (ItemEntity it : sl.getEntitiesOfClass(ItemEntity.class, e.getBoundingBox().inflate(reach),
                    x -> x.isAlive() && x.getItem().has(DataComponents.FOOD))) {
                if (g.food >= 100 || stacks++ >= 8) break;
                ItemStack st = it.getItem();
                FoodProperties fp = st.get(DataComponents.FOOD);
                int n = st.getCount();
                double add = Math.max(1, fp == null ? 1 : fp.nutrition()) * 4.0 * n;
                Entity by = it.getOwner();
                sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, st.copyWithCount(1)), it.getX(),
                        it.getY() + 0.3, it.getZ(), 12, 0.25, 0.25, 0.25, 0.08);
                sl.playSound(null, it.getX(), it.getY(), it.getZ(), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE,
                        1.5f, 0.6f);
                it.discard();
                ate(server, e, add, by, n);
            }
        }
        boolean wild = AdminJson.str(r, "ownerId", 40).isEmpty();
        String mood = AdminJson.str(r, "mood", 16);
        // a hungry wild one goes looking for food
        if (wild && g.food < 25 && was >= 25 && "calm".equals(mood) && k.moods().contains("hunting")) {
            if (mood(server, k, e, "hunting")) g.moodSet = "hunting";
        }
    }

    /** he ate: food (and, when his owner threw it, loyalty); a full wild one not fighting settles down */
    static void ate(MinecraftServer server, LivingEntity e, double amount, @Nullable Entity thrower, int items) {
        SystemsData d = SystemsData.get(server);
        SystemsData.G g = d.s.giants.get(e.getStringUUID());
        if (g == null) {
            String kind = FunGiants.kindOf(e);
            if (kind == null) return;
            g = d.giant(e.getStringUUID(), kind);
            g.born = server.overworld().getGameTime();
            g.start = FunGiants.sizeOf(e);
            g.seen = g.born;
        }
        double was = g.food;
        g.food = Math.min(100, g.food + amount);
        if (thrower != null && thrower.getStringUUID().equals(g.owner) && g.loyalty >= 0
                && Systems.on(server, "loyalty"))
            g.loyalty = Math.min(100, g.loyalty + 2 * Math.min(10, items));
        d.setDirty();
        if (g.food > 80 && was <= 80 && g.owner.isEmpty() && !(e instanceof net.minecraft.world.entity.Mob m
                && m.getTarget() != null)) {
            Giant kind = AdminServer.giant(g.kind);
            if (kind != null) {
                Kind k = new Kind(kind, g.kind, AdminServer.nameOf(kind), 0, 4, List.of("calm", "hunting"),
                        List.of(), List.of());
                if (mood(server, k, e, "calm")) g.moodSet = "calm";
            }
        }
        SystemsAdmin.changed();
    }

    // Loyalty

    private static void loyalty(MinecraftServer server, Kind k, JsonObject r, LivingEntity e, SystemsData.G g,
            long now) {
        String owner = AdminJson.str(r, "ownerId", 40);
        if (owner.isEmpty()) {
            g.loyalty = -1;
            g.owner = "";
            g.sulking = false;
            return;
        }
        if (!owner.equals(g.owner) || g.loyalty < 0) {
            g.owner = owner;
            g.loyalty = 60;
            g.sulking = false;
            g.turned = false;
        }
        if (g.turned) {
            if (now - g.turnedAt >= 24000) {
                g.turned = false;
                g.loyalty = 30;
            }
            return;
        }
        ServerPlayer p = player(server, owner);
        // none left: he turns on them (before anything this look could give back)
        if (g.loyalty <= 0 && Systems.on(server, "loyaltyTurns")) {
            g.turned = true;
            g.turnedAt = now;
            JsonObject more = new JsonObject();
            more.addProperty("player", owner);
            List<String> said = Systems.act(server, k.g(), e.getStringUUID(), "turn", more);
            Systems.log("a {} turned on its owner {}: {}", k.name(), owner, said);
            if (p != null)
                p.sendSystemMessage(Component.literal("The " + k.name() + " has turned on you!")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            return;
        }
        if (p != null) {
            boolean near = p.level() == e.level() && p.distanceTo(e) <= 64;
            boolean far = p.level() != e.level() || p.distanceTo(e) > 300;
            if (near) g.loyalty = Math.min(100, g.loyalty + 1 / 6.0);
            else if (far) g.loyalty = Math.max(0, g.loyalty - 1 / 30.0);
        }
        if (g.loyalty < 30 && !g.sulking) {
            g.sulking = true;
            if (k.moods().contains("hunting") && mood(server, k, e, "hunting")) g.moodSet = "hunting";
            if (p != null)
                p.sendSystemMessage(Component.literal("The " + k.name() + " sulks: he'd like more of your time.")
                        .withStyle(ChatFormatting.GOLD));
        } else if (g.sulking && g.loyalty >= 50) {
            g.sulking = false;
            if ("hunting".equals(g.moodSet) && mood(server, k, e, "calm")) g.moodSet = "calm";
        }
    }

    private static boolean devotedNear(MinecraftServer server, LivingEntity e, SystemsData.G g) {
        if (g.loyalty < 80 || g.owner.isEmpty() || g.turned) return false;
        ServerPlayer p = player(server, g.owner);
        return p != null && p.level() == e.level() && p.distanceTo(e) <= 64;
    }

    // the tags (health, damage, speed): Titan, hunger, a devoted one beside his owner

    private static void tags(MinecraftServer server, Kind k, LivingEntity e, SystemsData.G g, boolean devoted) {
        double hungerF = 1;
        if (Systems.on(server, "hunger") && g.food < 25) {
            double weak = Systems.num(server, "hungerWeak");
            hungerF = weak + (1 - weak) * (g.food / 25.0);
        }
        double health = g.titan ? 2 : 1, damage = (g.titan ? 2 : 1) * hungerF * (devoted ? 1.15 : 1), speed = hungerF;
        health = Math.round(health * 100) / 100.0;
        damage = Math.round(damage * 100) / 100.0;
        speed = Math.round(speed * 100) / 100.0;
        boolean healthChanged = Math.abs(health - SystemsTags.sys(e, "health")) > 1e-6;
        if (healthChanged) SystemsTags.set(server, e, "health", health);
        if (Math.abs(damage - SystemsTags.sys(e, "damage")) > 1e-6) SystemsTags.set(server, e, "damage", damage);
        if (Math.abs(speed - SystemsTags.sys(e, "speed")) > 1e-6) SystemsTags.set(server, e, "speed", speed);
        g.tagHealth = health;
        g.tagDamage = damage;
        g.tagSpeed = speed;
        if (health != 1 || damage != 1 || speed != 1) tagged = true;
        if (healthChanged && FunGiants.does(server, k.g()).contains("stats"))
            FunGiants.act(server, k.g(), e.getStringUUID(), "stats", new JsonObject());
    }

    /** the systems went off: every giant they tagged back to x1 */
    private static void untag(MinecraftServer server) {
        tagged = false;
        for (LivingEntity e : FunGiants.standing(server, null)) {
            boolean health = Math.abs(SystemsTags.sys(e, "health") - 1) > 1e-6;
            boolean any = false;
            for (String w : SystemsTags.STATS)
                if (Math.abs(SystemsTags.sys(e, w) - 1) > 1e-6) {
                    SystemsTags.set(server, e, w, 1);
                    any = true;
                }
            if (any && health) {
                Giant g = AdminServer.giant(FunGiants.kindOf(e));
                if (g != null && FunGiants.does(server, g).contains("stats"))
                    FunGiants.act(server, g, e.getStringUUID(), "stats", new JsonObject());
            }
        }
    }

    // Herds

    private static void herds(MinecraftServer server, List<Kind> kinds) {
        int range = (int) Systems.num(server, "herdRange");
        for (Kind k : kinds) {
            List<JsonObject> here = new ArrayList<>();
            for (JsonObject r : k.rows()) if (here(r)) here.add(r);
            if (here.size() < 2 || !sys(server, k.g()).contains("go")) continue;
            JsonObject lead = null;
            for (JsonObject r : here) if (flag(r, "own")) lead = r;
            if (lead == null)
                for (JsonObject r : here)
                    if (lead == null || AdminJson.num(r, "size", 0) > AdminJson.num(lead, "size", 0)) lead = r;
            double lx = AdminJson.num(lead, "x", 0), lz = AdminJson.num(lead, "z", 0);
            String dim = AdminJson.str(lead, "dim", 64);
            double spread = Math.max(range, FunGiants.reach(k.key(), AdminJson.num(lead, "size", 1)));
            for (JsonObject r : here) {
                if (r == lead || !dim.equals(AdminJson.str(r, "dim", 64))) continue;
                if (!AdminJson.str(r, "ownerId", 40).isEmpty() || flag(r, "fighting") || flag(r, "asleep")) continue;
                double dx = AdminJson.num(r, "x", 0) - lx, dz = AdminJson.num(r, "z", 0) - lz;
                if (Math.hypot(dx, dz) <= spread) continue;
                double a = Math.random() * Math.PI * 2, d = Math.random() * spread * 0.5;
                JsonObject more = new JsonObject();
                more.addProperty("x", Math.round(lx + Math.cos(a) * d));
                more.addProperty("z", Math.round(lz + Math.sin(a) * d));
                Systems.act(server, k.g(), AdminJson.str(r, "id", 40), "go", more);
            }
        }
    }

    // helpers

    private static final Map<String, List<String>> sysDoes = new HashMap<>();

    /** what his "sys" bridge does (cached while the server runs; empty for an older mod) */
    static List<String> sys(MinecraftServer server, Giant g) {
        return sysDoes.computeIfAbsent(g.key(), k -> {
            JsonObject h = Systems.header(server, g);
            return h == null ? List.of() : Systems.names(h.getAsJsonArray("does"));
        });
    }

    /** sets his mood through his sys bridge; true when he took it */
    static boolean mood(MinecraftServer server, Kind k, LivingEntity e, String mood) {
        if (!sys(server, k.g()).contains("mood")) return false;
        JsonObject more = new JsonObject();
        more.addProperty("value", mood);
        List<String> r = Systems.act(server, k.g(), e.getStringUUID(), "mood", more);
        return r != null && !r.isEmpty() && r.get(0).equals("ok");
    }

    static @Nullable ServerPlayer player(MinecraftServer server, String id) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void tellNear(LivingEntity e, Component line, double r) {
        if (net.jj.giants.tools.Tools.quiet(e.getServer())) return;
        if (!(e.level() instanceof ServerLevel sl)) return;
        for (ServerPlayer p : sl.players()) if (p.distanceTo(e) <= r) p.sendSystemMessage(line);
    }

    /** (the tests) look even with nobody on */
    static boolean forced;

    public static void lookForTests(MinecraftServer server) {
        forced = true;
        try {
            tick(server);
        } finally {
            forced = false;
        }
    }

    /** (the tests) a giant's sys bridge asked afresh */
    public static void forgetSysForTests() {
        sysDoes.clear();
    }
}
