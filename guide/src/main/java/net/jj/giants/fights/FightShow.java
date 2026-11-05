package net.jj.giants.fights;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.jj.giants.net.PlayersStatePayload;
import net.jj.giants.players.Players;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10B) What players near a giant fight see and hear: both giants' bars (their names and titles), the countdown,
 * the announcer's lines, fight music forced on or off for them, and the slow-motion final blow (the server's tick rate
 * slowed for a moment, so every player's game slows with it).
 */
public final class FightShow {
    private FightShow() {}

    /** one fight's show: its bars (one per giant), who sees them, who has music forced */
    public static final class Show {
        final Map<UUID, ServerBossEvent> bars = new HashMap<>();
        final Set<UUID> music = new HashSet<>();
        /** the music forced: "on", "off", or "" (each player's own) */
        String musicMode = "";
        /** health shares already announced for each giant (75, 50, 25) */
        final Map<UUID, Integer> told = new HashMap<>();
    }

    static String pct(double share) {
        return Math.round(share * 100) + "%";
    }

    /** the players who are near: within this of the spot, in the same world */
    static List<ServerPlayer> near(ServerLevel l, Vec3 at, double r) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer p : l.players()) if (p.position().distanceToSqr(at) <= r * r) out.add(p);
        return out;
    }

    /** each giant's bar brought up to date and shown to the players near (side a red, b blue) */
    static void bars(Show s, ServerLevel l, Vec3 at, double r, List<LivingEntity> giants, Map<UUID, Double> share,
            Map<UUID, String> side) {
        List<ServerPlayer> who = near(l, at, r);
        Set<UUID> live = new HashSet<>();
        for (LivingEntity g : giants) {
            live.add(g.getUUID());
            ServerBossEvent b = s.bars.computeIfAbsent(g.getUUID(), k -> new ServerBossEvent(Component.empty(),
                    "b".equals(side.get(g.getUUID())) ? BossEvent.BossBarColor.BLUE : BossEvent.BossBarColor.RED,
                    BossEvent.BossBarOverlay.NOTCHED_10));
            double sh = share.getOrDefault(g.getUUID(), 1.0);
            b.setName(Component.literal(FightGiants.shown(g) + "  " + pct(sh)).withStyle(
                    "b".equals(side.get(g.getUUID())) ? ChatFormatting.AQUA : ChatFormatting.RED));
            b.setProgress((float) Math.max(0, Math.min(1, sh)));
            for (ServerPlayer p : List.copyOf(b.getPlayers())) if (!who.contains(p)) b.removePlayer(p);
            for (ServerPlayer p : who) b.addPlayer(p);
        }
        for (UUID id : List.copyOf(s.bars.keySet()))
            if (!live.contains(id)) s.bars.remove(id).removeAllPlayers();
    }

    /** fight music forced on or off for the players near ("" leaves each their own) */
    static void music(Show s, MinecraftServer server, ServerLevel l, Vec3 at, double r, String mode) {
        if (mode.isEmpty() && s.music.isEmpty()) return;
        Set<UUID> now = new HashSet<>();
        if (!mode.isEmpty()) for (ServerPlayer p : near(l, at, r)) now.add(p.getUUID());
        for (UUID id : now)
            if (!s.music.contains(id) || !mode.equals(s.musicMode)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) sendMusic(p, mode);
            }
        for (UUID id : s.music)
            if (!now.contains(id)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p != null) Players.sendSync(p);
            }
        s.music.clear();
        s.music.addAll(now);
        s.musicMode = mode;
    }

    private static void sendMusic(ServerPlayer p, String mode) {
        JsonObject o = new JsonObject();
        o.addProperty("fightMusic", mode);
        Players.sender.accept(p, new PlayersStatePayload(PlayersStatePayload.SYNC, o.toString()));
    }

    /** the show is over: bars gone, every player's own music back */
    static void end(Show s, MinecraftServer server) {
        for (ServerBossEvent b : s.bars.values()) b.removeAllPlayers();
        s.bars.clear();
        for (UUID id : s.music) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) Players.sendSync(p);
        }
        s.music.clear();
    }

    /** a big line in the middle of the screen (and a small one under it) for the players near */
    static void title(List<ServerPlayer> who, Component big, @Nullable Component small, int stay) {
        for (ServerPlayer p : who) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(2, stay, 6));
            p.connection.send(new ClientboundSetSubtitleTextPacket(small == null ? Component.empty() : small));
            p.connection.send(new ClientboundSetTitleTextPacket(big));
        }
    }

    static void sound(List<ServerPlayer> who, SoundEvent ev, float vol, float pitch) {
        for (ServerPlayer p : who) p.playNotifySound(ev, SoundSource.MASTER, vol, pitch);
    }

    /** the countdown's beat: 3, 2, 1 (a click), then Fight! (a horn) */
    static void count(List<ServerPlayer> who, int n, String sub) {
        if (n > 0) {
            title(who, Component.literal(String.valueOf(n)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                    Component.literal(sub).withStyle(ChatFormatting.GRAY), 16);
            sound(who, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, n == 1 ? 1.2f : 0.9f);
        } else {
            title(who, Component.literal("Fight!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                    Component.literal(sub).withStyle(ChatFormatting.GRAY), 24);
            sound(who, SoundEvents.RAID_HORN.value(), 0.8f, 1f);
        }
    }

    /** the announcer: one gold line to the players near (not in quiet mode) */
    static void say(MinecraftServer server, List<ServerPlayer> who, String line) {
        if (net.jj.giants.tools.Tools.quiet(server)) return;
        Component c = Component.literal("[Fight] ").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
                .append(Component.literal(line).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : who) p.sendSystemMessage(c);
    }

    /** the announcer's line for a giant's health falling past a mark (75, 50, 25), or null */
    static @Nullable String hurtLine(Show s, LivingEntity g, double share, String called) {
        int mark = share <= 0.25 ? 25 : share <= 0.5 ? 50 : share <= 0.75 ? 75 : 100;
        int was = s.told.getOrDefault(g.getUUID(), 100);
        if (mark >= was) return null;
        s.told.put(g.getUUID(), mark);
        return switch (mark) {
            case 75 -> called + " is hurt.";
            case 50 -> called + " is badly hurt!";
            default -> called + " is on his last legs!";
        };
    }

    // the slow-motion final blow: the whole server slowed for a moment (every player's game follows its tick rate)

    private static float before = -1f;
    private static long slowUntil = -1L;

    /** slow everything down for about this many real milliseconds */
    static void slowMo(MinecraftServer server, long millis) {
        var t = server.tickRateManager();
        if (before < 0f) before = t.tickrate();
        t.setTickRate(Math.max(1f, before / 4f));
        slowUntil = System.currentTimeMillis() + millis;
    }

    static boolean slow() {
        return before >= 0f;
    }

    /** every tick: back to the old speed once the moment has passed */
    static void tick(MinecraftServer server) {
        if (before >= 0f && System.currentTimeMillis() >= slowUntil) normal(server);
    }

    static void normal(MinecraftServer server) {
        if (before < 0f) return;
        server.tickRateManager().setTickRate(before);
        before = -1f;
        slowUntil = -1L;
    }
}
