package net.jj.giants.players;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.Giant;
import net.jj.giants.admin.AdminBook;
import net.jj.giants.admin.AdminServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The leaderboards (ADMIN_BRIDGE.md 20.12): giants slain, the longest anyone owned a giant without a break, and how
 * many giants each player has owned; worked out from the world's data and the giants' live lists, shown in the Guide,
 * the admin book and on signs at spawn.
 */
public final class Boards {
    private Boards() {}

    public static final List<String> KINDS = List.of("slayers", "owners", "friends");
    public static final Map<String, String> TITLES = Map.of("slayers", "Giant slayers", "owners", "Longest owners",
            "friends", "Most befriended");
    /** each board's own switch */
    static final Map<String, String> SWITCH = Map.of("slayers", "boardSlayers", "owners", "boardOwners", "friends",
            "boardFriends");

    /** how often the owners are looked at (ticks) */
    public static final int LOOK_EVERY = 600;

    public static boolean shown(MinecraftServer server, String kind) {
        return Players.on(server, "boards") && Players.on(server, SWITCH.get(kind));
    }

    /** one line of a board */
    public record Row(String id, String name, long score, String text) {}

    public static List<Row> rows(MinecraftServer server, String kind) {
        PlayerData d = PlayerData.get(server);
        List<Row> out = new ArrayList<>();
        for (var e : d.s.stats.entrySet()) {
            PlayerData.Stats st = e.getValue();
            long score = switch (kind) {
                case "slayers" -> st.killsAll();
                case "owners" -> Math.max(st.longest, st.streak);
                default -> st.owned.size();
            };
            if (score <= 0) continue;
            String text = kind.equals("owners") ? time(score) : String.valueOf(score);
            out.add(new Row(e.getKey(), st.name, score, text));
        }
        out.sort(Comparator.comparingLong(Row::score).reversed().thenComparing(Row::name));
        return out;
    }

    /** ticks as people say it: "2 h 5 min", "12 min", "under a minute" */
    public static String time(long ticks) {
        long min = ticks / 1200;
        if (min < 1) return "under a minute";
        long h = min / 60;
        return h > 0 ? h + " h " + (min % 60) + " min" : min + " min";
    }

    /** every board shown: its top rows, and where this player stands */
    public static JsonObject json(MinecraftServer server, @Nullable String viewer, int top) {
        JsonObject o = new JsonObject();
        o.addProperty("on", Players.on(server, "boards"));
        JsonArray boards = new JsonArray();
        for (String k : KINDS) {
            if (!shown(server, k)) continue;
            JsonObject b = new JsonObject();
            b.addProperty("kind", k);
            b.addProperty("title", TITLES.get(k));
            List<Row> rows = rows(server, k);
            JsonArray a = new JsonArray();
            for (int i = 0; i < rows.size() && i < top; i++) {
                JsonObject r = new JsonObject();
                r.addProperty("place", i + 1);
                r.addProperty("name", rows.get(i).name());
                r.addProperty("text", rows.get(i).text());
                r.addProperty("me", rows.get(i).id().equals(viewer));
                a.add(r);
            }
            b.add("rows", a);
            for (int i = 0; i < rows.size(); i++)
                if (rows.get(i).id().equals(viewer)) {
                    b.addProperty("myPlace", i + 1);
                    b.addProperty("myText", rows.get(i).text());
                }
            if (k.equals("slayers") && viewer != null) {
                PlayerData.Stats st = PlayerData.get(server).s.stats.get(viewer);
                if (st != null && !st.kills.isEmpty()) {
                    JsonObject per = new JsonObject();
                    for (var e : st.kills.entrySet()) per.addProperty(e.getKey(), e.getValue());
                    b.add("myKinds", per);
                }
            }
            boards.add(b);
        }
        o.add("boards", boards);
        return o;
    }

    // owners (every 30 seconds)

    /**
     * Each giant's live list: whoever holds a book while one of him stands owns a giant now. Two looks in a row add
     * the time between to their streak; a look without ends it. Every giant (by id) they own counts as befriended.
     */
    public static void lookAtOwners(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        long now = server.overworld().getGameTime();
        Map<String, List<String>> owns = new HashMap<>();
        for (Giant g : AdminServer.giants()) {
            Players.Live l = Players.live(server, g);
            if (l.ownerId() == null || l.count() == 0) continue;
            owns.computeIfAbsent(l.ownerId(), k -> new ArrayList<>()).addAll(l.ids());
        }
        boolean changed = false;
        for (var e : owns.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(UUID.fromString(e.getKey()));
            PlayerData.Stats st = d.stats(e.getKey(), p == null ? null : p.getGameProfile().getName());
            boolean newOwner = st.last == -1 || now - st.last > LOOK_EVERY * 2L;
            if (!newOwner) st.streak += now - st.last;
            else st.streak = 0;
            st.longest = Math.max(st.longest, st.streak);
            st.last = now;
            int before = st.owned.size();
            st.owned.addAll(e.getValue());
            if (st.owned.size() != before || !newOwner) changed = true;
            if (p != null) Challenges.award(p, "kind_hearted", "gentle_giants");
        }
        // (a look without: the streak ends)
        for (var e : d.s.stats.entrySet())
            if (!owns.containsKey(e.getKey()) && e.getValue().streak > 0) {
                e.getValue().streak = 0;
                e.getValue().last = -1;
                changed = true;
            }
        if (changed) {
            d.setDirty();
            AdminBook.changed("players");
        }
    }

    // signs at spawn

    /** the sign this player looks at (6 blocks), or null */
    public static @Nullable BlockPos lookedAtSign(ServerPlayer p) {
        HitResult h = p.pick(6, 1f, false);
        if (!(h instanceof BlockHitResult bh) || h.getType() != HitResult.Type.BLOCK) return null;
        return p.level().getBlockEntity(bh.getBlockPos()) instanceof SignBlockEntity ? bh.getBlockPos() : null;
    }

    public static AdminServer.Result setSign(MinecraftServer server, ServerLevel level, BlockPos pos, String kind) {
        if (!KINDS.contains(kind))
            return AdminServer.Result.fail("No board called " + kind + ". Try one of: " + String.join(", ", KINDS) + ".");
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity))
            return AdminServer.Result.fail("Look at a sign (6 blocks) to make it a leaderboard.");
        PlayerData d = PlayerData.get(server);
        String dim = level.dimension().location().toString();
        d.s.signs.removeIf(s -> s.dim.equals(dim) && s.x == pos.getX() && s.y == pos.getY() && s.z == pos.getZ());
        PlayerData.Sign s = new PlayerData.Sign();
        s.dim = dim;
        s.x = pos.getX();
        s.y = pos.getY();
        s.z = pos.getZ();
        s.kind = kind;
        d.s.signs.add(s);
        d.setDirty();
        write(server, s);
        AdminBook.changed("players");
        return AdminServer.Result.said(List.of("That sign shows the " + TITLES.get(kind).toLowerCase(
                java.util.Locale.ROOT) + " board now (it updates every 30 seconds)."));
    }

    public static AdminServer.Result removeSign(MinecraftServer server, String dim, BlockPos pos) {
        PlayerData d = PlayerData.get(server);
        boolean gone = d.s.signs.removeIf(s -> s.dim.equals(dim) && s.x == pos.getX() && s.y == pos.getY()
                && s.z == pos.getZ());
        if (!gone) return AdminServer.Result.fail("That sign isn't a leaderboard.");
        d.setDirty();
        AdminBook.changed("players");
        return AdminServer.Result.said(List.of("That sign isn't a leaderboard any more (its words stay)."));
    }

    public static AdminServer.Result removeSign(MinecraftServer server, int i) {
        PlayerData d = PlayerData.get(server);
        if (i < 0 || i >= d.s.signs.size()) return AdminServer.Result.fail("There's no such board.");
        PlayerData.Sign s = d.s.signs.get(i);
        return removeSign(server, s.dim, new BlockPos(s.x, s.y, s.z));
    }

    public static AdminServer.Result list(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        if (d.s.signs.isEmpty()) return AdminServer.Result.said(List.of("No leaderboard signs yet. Look at a sign and"
                + " use /giants players board set slayers|owners|friends."));
        List<String> lines = new ArrayList<>(List.of("Leaderboard signs:"));
        for (PlayerData.Sign s : d.s.signs)
            lines.add("  " + TITLES.get(s.kind) + " at " + s.x + " " + s.y + " " + s.z + " (" + s.dim + ")");
        return AdminServer.Result.said(lines);
    }

    /** every sign's words again (a sign that isn't there any more is dropped; one in land not loaded waits) */
    public static void updateSigns(MinecraftServer server) {
        PlayerData d = PlayerData.get(server);
        if (d.s.signs.isEmpty()) return;
        boolean dropped = d.s.signs.removeIf(s -> {
            ServerLevel l = level(server, s.dim);
            BlockPos pos = new BlockPos(s.x, s.y, s.z);
            if (l == null) return true;
            if (!l.isLoaded(pos)) return false;
            return !write(server, s);
        });
        if (dropped) d.setDirty();
    }

    static @Nullable ServerLevel level(MinecraftServer server, String dim) {
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        return rl == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }

    /** writes the board on the sign's front; false if there's no sign there now */
    static boolean write(MinecraftServer server, PlayerData.Sign s) {
        ServerLevel l = level(server, s.dim);
        BlockPos pos = new BlockPos(s.x, s.y, s.z);
        if (l == null || !(l.getBlockEntity(pos) instanceof SignBlockEntity sign)) return false;
        Component[] lines = new Component[4];
        lines[0] = Component.literal(TITLES.get(s.kind)).withStyle(net.minecraft.ChatFormatting.BOLD);
        if (!shown(server, s.kind)) {
            lines[1] = Component.literal("Turned off");
            lines[2] = Component.empty();
            lines[3] = Component.empty();
        } else {
            List<Row> rows = rows(server, s.kind);
            for (int i = 0; i < 3; i++)
                lines[i + 1] = i < rows.size()
                        ? Component.literal((i + 1) + " " + rows.get(i).name() + " " + rows.get(i).text())
                        : Component.empty();
        }
        SignText front = sign.getFrontText();
        boolean same = true;
        for (int i = 0; i < 4; i++) same &= front.getMessage(i, false).getString().equals(lines[i].getString());
        if (same) return true;
        SignText t = new SignText(lines, lines, front.getColor(), front.hasGlowingText());
        sign.setText(t, true);
        l.sendBlockUpdated(pos, l.getBlockState(pos), l.getBlockState(pos), 3);
        return true;
    }
}
