package net.jj.giants.minigames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.jj.giants.GiantsGuideMod;
import net.jj.giants.fun.FunGiants;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * (Part 10A, ADMIN_BRIDGE.md §26.1) One minigame, from opening to results: the lobby (players join, or everyone is
 * taken; the map builds meanwhile), each round in a fresh world of its own (everyone's main-world things kept safe on
 * the way in and put back exactly on the way out), the countdown, the round, who won, the next round's fresh world,
 * and the results. One at a time on a server.
 */
public final class MgGame {
    public enum Phase { LOBBY, READY, PLAY, ROUND_END, NEXT, RESULTS, DONE }

    /** one player in it: playing or watching */
    public static final class P {
        public final UUID id;
        public String name;
        public boolean watcher;
        /** in the round's world now (their things kept) */
        public boolean in;
        /** still in the round (not knocked out for good, not left) */
        public boolean alive;
        /** down for a moment (back at respawnAt) */
        public boolean down;
        public boolean left;
        public int score, roundWins, kos, lives, team = -1, place;
        public double dealt;
        public int respawnAt = -1, outAt = -1;
        @Nullable CompoundTag snap;
        Vec3 spot = Vec3.ZERO;
        /** the mode's own numbers for them */
        public final Map<String, Double> num = new HashMap<>();

        P(UUID id, String name, boolean watcher) {
            this.id = id;
            this.name = name;
            this.watcher = watcher;
        }
    }

    /** a round's end: who won it and the line that says so */
    public record End(List<UUID> winners, String line) {}

    final MinecraftServer server;
    public final MgMode mode;
    public final boolean force;
    public final String by, id;
    Phase phase = Phase.LOBBY;
    int phaseTicks, lobbyLeft, extended;
    public final Map<UUID, P> people = new LinkedHashMap<>();
    public int round = 1;
    public final int rounds;
    public int roundTicks;
    final int roundMax;
    public @Nullable ServerLevel level;
    @Nullable ServerLevel next;
    public @Nullable MgMap map;
    @Nullable MgMap nextMap;
    @Nullable MgBuild build;
    public MgGiants giants;
    final ServerBossEvent bar =
            new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);
    // the world's settings when it opened
    public final int sizeIndex, need, most;
    public final double giantSize;
    final boolean pvpOff, tell, watching;
    @Nullable End last;
    /** the mode's own state for this round (cleared each round) */
    public final Map<String, Object> data = new HashMap<>();
    /** game time it opened (ms) */
    final long opened = System.currentTimeMillis();

    MgGame(MinecraftServer server, MgMode mode, boolean force, String by, int rounds) {
        this.server = server;
        this.mode = mode;
        this.force = force;
        this.by = by;
        this.id = Long.toString(System.currentTimeMillis(), 36);
        this.rounds = rounds;
        this.roundMax = Math.max(2, MgCatalog.num(server, "mgMinutes")) * 1200;
        this.sizeIndex = switch (MgCatalog.option(server, "mgSize")) {
            case "small" -> 0;
            case "large" -> 2;
            default -> 1;
        };
        this.giantSize = Math.max(0.05, Math.min(0.5, MgCatalog.real(server, "mgGiantSize")));
        this.need = Math.max(mode.fewest(), Math.max(1, MgCatalog.num(server, "mgMin")));
        this.most = Math.max(need, MgCatalog.num(server, "mgMax"));
        this.pvpOff = MgCatalog.option(server, "mgPvp").equals("off");
        this.tell = MgCatalog.on(server, "mgTell");
        this.watching = MgCatalog.on(server, "mgSpectate");
        this.lobbyLeft = Math.max(10, MgCatalog.num(server, "mgLobby")) * 20;
        this.giants = new MgGiants(this);
    }

    public MinecraftServer server() {
        return server;
    }

    public Phase phase() {
        return phase;
    }

    // who is in it

    public List<P> players() {
        List<P> out = new ArrayList<>();
        for (P p : people.values()) if (!p.watcher && !p.left) out.add(p);
        return out;
    }

    /** the players still in the round */
    public List<P> alive() {
        List<P> out = new ArrayList<>();
        for (P p : people.values()) if (!p.watcher && !p.left && p.alive) out.add(p);
        return out;
    }

    public @Nullable ServerPlayer online(P p) {
        return server.getPlayerList().getPlayer(p.id);
    }

    public @Nullable P of(UUID id) {
        return people.get(id);
    }

    int playing() {
        int n = 0;
        for (P p : people.values()) if (!p.watcher && !p.left) n++;
        return n;
    }

    /** players can hurt each other now */
    boolean pvpNow() {
        return phase == Phase.PLAY && !pvpOff && mode.pvp();
    }

    // the clock

    public String clock() {
        int left = Math.max(0, roundMax - roundTicks) / 20;
        return left / 60 + ":" + String.format("%02d", left % 60);
    }

    public float timeLeftShare() {
        return Math.max(0f, Math.min(1f, (roundMax - roundTicks) / (float) roundMax));
    }

    public int roundMax() {
        return roundMax;
    }

    // telling

    static void title(ServerPlayer sp, Component title, Component sub, int in, int stay, int out) {
        sp.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
        sp.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        sp.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    /** a line to everyone in the round (playing and watching) */
    public void tellRound(Component line) {
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp != null && (p.in || phase == Phase.LOBBY)) sp.sendSystemMessage(line);
        }
    }

    public void tellRound(String line) {
        tellRound(Component.literal(line).withStyle(ChatFormatting.GOLD));
    }

    /** the round's world border, as this player's game should draw it (the game only sends the Overworld's) */
    void border(ServerPlayer sp) {
        if (level != null && sp.level() == level)
            sp.connection.send(new ClientboundInitializeBorderPacket(level.getWorldBorder()));
    }

    /** every player in the round's world gets the border again (after it changed) */
    public void borderChanged() {
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp != null && p.in) border(sp);
        }
    }

    static Component click(String words, String command, String hover, ChatFormatting colour) {
        return Component.literal("[" + words + "]").withStyle(s -> s.withColor(colour).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** the invitation: the mode, when it starts, Join and Watch to click */
    Component invite(String lead) {
        MutableComponent c = Component.literal("[Giants] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal(lead).withStyle(ChatFormatting.YELLOW));
        if (!force) {
            c.append(" ").append(click("Join", "/giants join", "Play " + mode.name(), ChatFormatting.GREEN));
            if (watching) c.append(" ").append(click("Watch", "/giants spectate", "Watch " + mode.name(),
                    ChatFormatting.AQUA));
        }
        return c;
    }

    // opening

    /** the lobby opens: everyone told, the first round's world made and its map started */
    void open() {
        makeWorld(false);
        if (force) for (ServerPlayer sp : server.getPlayerList().getPlayers()) addForced(sp);
        String lead = mode.name() + (force ? " starts in " + lobbyLeft / 20 + " s for everyone online: " + mode.about()
                : " opens! Starts in " + lobbyLeft / 20 + " s. " + mode.about());
        Component line = invite(lead);
        GiantsGuideMod.LOG.info("Giants minigames: {} opened by {} ({}, {} round(s))", mode.name(), by,
                force ? "everyone" : "open", rounds);
        for (ServerPlayer sp : server.getPlayerList().getPlayers())
            if (force || (tell && !Minigames.quiet(server)) || people.containsKey(sp.getUUID())
                    || Minigames.staff(sp)) sp.sendSystemMessage(line);
    }

    void addForced(ServerPlayer sp) {
        if (people.containsKey(sp.getUUID())) return;
        boolean full = playing() >= most;
        P p = new P(sp.getUUID(), sp.getGameProfile().getName(), full);
        people.put(sp.getUUID(), p);
    }

    /** a fresh world for the next round, and its map started */
    private void makeWorld(boolean forNext) {
        ServerLevel l = MgLevels.create(server, id + "_" + round + (forNext ? "n" : ""), mode.dayTime());
        MgMap m = mode.map(this);
        var border = l.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(2 * m.half + 1);
        border.setDamagePerBlock(1.0);
        border.setDamageSafeZone(0);
        border.setWarningBlocks(4);
        int dist = Math.min(32, (int) Math.ceil(m.half / 16.0) + 3);
        l.getChunkSource().addRegionTicket(MgLevels.TICKET, new ChunkPos(0, 0), dist, new ChunkPos(0, 0));
        MgBuild b = new MgBuild(l, MgLevels.GROUND);
        for (MgMap.Piece pc : m.pieces) b.add(pc.shape(), pc.x(), pc.z());
        if (forNext) {
            next = l;
            nextMap = m;
        } else {
            level = l;
            map = m;
        }
        build = b;
    }

    // joining and leaving

    private static net.jj.giants.admin.AdminServer.Result yes(String line) {
        return net.jj.giants.admin.AdminServer.Result.said(List.of(line));
    }

    private static net.jj.giants.admin.AdminServer.Result no(String line) {
        return net.jj.giants.admin.AdminServer.Result.fail(line);
    }

    net.jj.giants.admin.AdminServer.Result join(ServerPlayer sp, boolean watch) {
        P p = people.get(sp.getUUID());
        if (watch && !watching) return no("Watching minigames is turned off in this world.");
        if (phase == Phase.LOBBY) {
            if (p != null && !p.left) {
                if (p.watcher == watch) return no(watch ? "You're down to watch " + mode.name() + " already."
                        : "You're in " + mode.name() + " already. It starts in " + lobbyLeft / 20 + " s.");
                if (!watch && playing() >= most)
                    return no("It's full (" + most + " players). You can watch: /giants spectate");
                p.watcher = watch;
                Minigames.changed();
                return yes(watch ? "You'll watch " + mode.name() + " instead." : "You'll play " + mode.name()
                        + " instead.");
            }
            if (!watch && playing() >= most)
                return no("It's full (" + most + " players)." + (watching ? " You can watch: /giants spectate" : ""));
            people.put(sp.getUUID(), new P(sp.getUUID(), sp.getGameProfile().getName(), watch));
            Minigames.changed();
            return yes(watch ? "You'll watch " + mode.name() + ". It starts in " + lobbyLeft / 20 + " s."
                    : "You're in " + mode.name() + "! It starts in " + lobbyLeft / 20 + " s. /giants leave to drop out.");
        }
        if (phase == Phase.RESULTS || phase == Phase.DONE) return no(mode.name() + " is just ending.");
        if (!watch) return no(mode.name() + " has started." + (watching ? " You can watch: /giants spectate" : ""));
        if (p != null && p.in) return no("You're in it already.");
        if (level == null || map == null) return no("It isn't ready yet.");
        if (p == null) {
            p = new P(sp.getUUID(), sp.getGameProfile().getName(), true);
            people.put(sp.getUUID(), p);
        }
        p.watcher = true;
        p.left = false;
        if (!takeIn(sp, p, map.watch)) return no("Your things couldn't be kept safe, so you can't come in.");
        Minigames.changed();
        return yes("You're watching " + mode.name() + ". /giants leave to go back.");
    }

    net.jj.giants.admin.AdminServer.Result leave(ServerPlayer sp) {
        P p = people.get(sp.getUUID());
        if (p == null || p.left) return no("You aren't in a minigame.");
        if (phase == Phase.LOBBY) {
            if (force) return no("This one is for everyone online: you're in it. You can leave once it has started.");
            people.remove(sp.getUUID());
            Minigames.changed();
            return yes("You've dropped out of " + mode.name() + ".");
        }
        boolean was = !p.watcher && p.alive;
        p.left = true;
        p.alive = false;
        if (p.in) sendHome(sp, p);
        if (was) {
            mode.left(this, p);
            tellRound(p.name + " left (" + alive().size() + " still in).");
        }
        Minigames.changed();
        return yes("You're back where you were.");
    }

    /** gone from the game (logged out): home now, before their file is saved */
    void gone(ServerPlayer sp) {
        P p = people.get(sp.getUUID());
        if (p == null) return;
        if (phase == Phase.LOBBY) {
            if (!force) people.remove(sp.getUUID());
            return;
        }
        boolean was = !p.watcher && p.alive && !p.left;
        p.left = true;
        p.alive = false;
        if (p.in) sendHome(sp, p);
        if (was) {
            mode.left(this, p);
            tellRound(p.name + " left the game (" + alive().size() + " still in).");
        }
        Minigames.changed();
    }

    // taking in and sending home

    /** into the round's world at that spot: their things kept first (on the way into the first round) */
    boolean takeIn(ServerPlayer sp, P p, Vec3 spot) {
        if (p.snap == null) {
            CompoundTag t = MgSnap.take(sp);
            if (!MgSnap.write(server, sp.getUUID(), t)) {
                p.left = true;
                sp.sendSystemMessage(Component.literal("Your things couldn't be kept safe, so you can't come into "
                        + mode.name() + ".").withStyle(ChatFormatting.RED));
                return false;
            }
            p.snap = t;
        }
        MgSnap.strip(sp);
        sp.setGameMode(p.watcher ? GameType.SPECTATOR : GameType.ADVENTURE);
        place(sp, p, spot);
        p.in = true;
        p.alive = !p.watcher;
        p.down = false;
        p.respawnAt = -1;
        bar.addPlayer(sp);
        return true;
    }

    /** to a spot on the map, standing on the ground there (watchers: in the air over it) */
    void place(ServerPlayer sp, P p, Vec3 spot) {
        ServerLevel l = level;
        if (l == null) return;
        double y = p.watcher ? MgGiants.ground(l, spot.x, spot.z) + Math.max(12, spot.y)
                : MgGiants.ground(l, spot.x, spot.z);
        float yaw = (float) (Math.atan2(-spot.x, spot.z) * 180 / Math.PI);
        if (Math.abs(spot.x) < 1 && Math.abs(spot.z) < 1) yaw = sp.getYRot();
        Minigames.moving.add(sp.getUUID());
        try {
            sp.fallDistance = 0;
            sp.teleportTo(l, spot.x, y, spot.z, yaw, p.watcher ? 40 : 0);
        } finally {
            Minigames.moving.remove(sp.getUUID());
        }
        p.spot = new Vec3(spot.x, y, spot.z);
        border(sp);
    }

    /** back where they were, everything of theirs as it was; their player file saved, the kept file gone */
    void sendHome(ServerPlayer sp, P p) {
        CompoundTag t = p.snap != null ? p.snap : MgSnap.read(server, p.id);
        Minigames.moving.add(sp.getUUID());
        try {
            if (t != null) MgSnap.apply(sp, t);
        } finally {
            Minigames.moving.remove(sp.getUUID());
        }
        bar.removePlayer(sp);
        p.in = false;
        p.snap = null;
        MgSnap.savePlayer(sp);
        MgSnap.delete(server, p.id);
    }

    // the round

    /** a player out of the round for good (they watch from where they are, or go home with /giants leave) */
    public void out(P p, ServerPlayer sp, String title) {
        if (!p.alive) return;
        p.alive = false;
        p.down = false;
        p.outAt = roundTicks;
        p.place = alive().size() + 1;
        sp.setGameMode(GameType.SPECTATOR);
        sp.setHealth(sp.getMaxHealth());
        title(sp, Component.literal(title).withStyle(ChatFormatting.RED),
                Component.literal("Watch the rest, or /giants leave to go back."), 5, 50, 15);
        int n = alive().size();
        tellRound(p.name + " is out" + (n > 0 ? " (" + n + " left)." : "."));
        Minigames.changed();
    }

    /** down for a few seconds, then back in at a start (a mode with lives) */
    public void down(P p, ServerPlayer sp, int seconds, String title) {
        p.down = true;
        p.respawnAt = roundTicks + seconds * 20;
        sp.setGameMode(GameType.SPECTATOR);
        sp.setHealth(sp.getMaxHealth());
        sp.removeAllEffects();
        sp.clearFire();
        title(sp, Component.literal(title).withStyle(ChatFormatting.RED),
                Component.literal(seconds <= 60 ? "Back in " + seconds + " s" : "Watch for now"), 5,
                Math.min(60, seconds * 20 - 10), 5);
    }

    private void respawns() {
        for (P p : people.values()) {
            if (!p.down || p.respawnAt < 0 || roundTicks < p.respawnAt || !p.alive) continue;
            ServerPlayer sp = online(p);
            if (sp == null || map == null) continue;
            p.down = false;
            p.respawnAt = -1;
            sp.setGameMode(GameType.ADVENTURE);
            sp.setHealth(sp.getMaxHealth());
            sp.getFoodData().setFoodLevel(20);
            place(sp, p, map.start(level == null ? 0 : level.random.nextInt(Math.max(1, map.starts.size()))));
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 60, 4, false, false));
        }
    }

    /** the giants of the round kept inside the map (one wandered off, or backed down toward his far-off home) */
    private void keepGiantsIn() {
        if (level == null || map == null) return;
        double half = map.half;
        for (MgGiants.G g : giants.standing()) {
            LivingEntity e = giants.entity(g);
            if (e == null) continue;
            double out = Math.max(Math.abs(e.getX()), Math.abs(e.getZ())) - (half - 6);
            if (out <= 0) continue;
            net.jj.giants.Giant k = net.jj.giants.admin.AdminServer.giant(g.kind);
            if (k == null) continue;
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            double f = Math.max(0, half * 0.5) / Math.max(1, e.position().horizontalDistance());
            o.addProperty("dim", level.dimension().location().toString());
            o.addProperty("x", e.getX() * f);
            o.addProperty("y", MgGiants.ground(level, e.getX() * f, e.getZ() * f));
            o.addProperty("z", e.getZ() * f);
            o.addProperty("where", "land");
            o.addProperty("stay", false);
            FunGiants.act(server, k, g.id.toString(), "move", o);
        }
    }

    // the tick

    void tick() {
        phaseTicks++;
        giants.tick();
        switch (phase) {
            case LOBBY -> lobby();
            case READY -> ready();
            case PLAY -> play();
            case ROUND_END -> {
                if (phaseTicks >= 120) afterRound();
            }
            case NEXT -> nextRound();
            case RESULTS -> {
                if (phaseTicks >= 160) finish(null);
            }
            default -> {}
        }
        if (phaseTicks % 10 == 0) barNow();
    }

    private void lobby() {
        if (build != null && !build.done()) build.tick(System.nanoTime() + 6_000_000L);
        if (force && phaseTicks % 20 == 0) for (ServerPlayer sp : server.getPlayerList().getPlayers()) addForced(sp);
        // the joined who logged out meanwhile are dropped (forced ones are kept for when they come back)
        if (phaseTicks % 20 == 0) people.values().removeIf(p -> !force && online(p) == null);
        if (lobbyLeft > 0) {
            lobbyLeft--;
            if ((lobbyLeft == 600 || lobbyLeft == 200) && !force && tell && !Minigames.quiet(server)) {
                Component line = invite(mode.name() + " starts in " + lobbyLeft / 20 + " s (" + playing()
                        + " in).");
                for (ServerPlayer sp : server.getPlayerList().getPlayers())
                    if (!people.containsKey(sp.getUUID())) sp.sendSystemMessage(line);
            }
            return;
        }
        int n = 0;
        for (P p : people.values()) if (!p.watcher && online(p) != null) n++;
        if (n < need) {
            if (extended < 2) {
                extended++;
                lobbyLeft = Math.max(200, Math.min(600, MgCatalog.num(server, "mgLobby") * 20));
                tellRound("Waiting for more players for " + mode.name() + " (" + n + " of " + need + ")…");
                if (!force && tell && !Minigames.quiet(server)) {
                    Component line = invite(mode.name() + " needs " + (need - n) + " more player"
                            + (need - n == 1 ? "" : "s") + ".");
                    for (ServerPlayer sp : server.getPlayerList().getPlayers())
                        if (!people.containsKey(sp.getUUID())) sp.sendSystemMessage(line);
                }
                return;
            }
            stop(null, "Not enough players joined " + mode.name() + " (" + n + " of " + need + ").");
            return;
        }
        if (build != null && !build.done()) return;
        enter();
    }

    /** the lobby's time is up: everyone in */
    void enter() {
        if (level == null || map == null) return;
        int i = 0;
        for (P p : List.copyOf(people.values())) {
            ServerPlayer sp = online(p);
            if (sp == null) {
                people.remove(p.id);
                continue;
            }
            if (p.watcher) takeIn(sp, p, map.watch);
            else takeIn(sp, p, map.start(i++));
        }
        startRound();
    }

    private void startRound() {
        phase = Phase.READY;
        phaseTicks = 0;
        roundTicks = 0;
        data.clear();
        for (P p : people.values())
            if (!p.watcher && !p.left) {
                p.alive = p.in;
                p.down = false;
                p.respawnAt = -1;
                p.outAt = -1;
                p.place = 0;
            }
        mode.begin(this);
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp == null || !p.in) continue;
            title(sp, Component.literal(mode.name()).withStyle(ChatFormatting.GOLD),
                    Component.literal(rounds > 1 ? "Round " + round + " of " + rounds + ": " + mode.goal()
                            : mode.goal()), 10, 50, 10);
        }
        Minigames.changed();
    }

    private void ready() {
        // held on their spots while the countdown runs
        for (P p : people.values()) {
            if (p.watcher || !p.in || !p.alive) continue;
            ServerPlayer sp = online(p);
            if (sp == null) continue;
            if (sp.position().distanceToSqr(p.spot) > 0.5)
                sp.connection.teleport(p.spot.x, p.spot.y, p.spot.z, sp.getYRot(), sp.getXRot());
        }
        if (phaseTicks == 40 || phaseTicks == 60 || phaseTicks == 80) {
            String n = String.valueOf((100 - phaseTicks) / 20);
            for (P p : people.values()) {
                ServerPlayer sp = online(p);
                if (sp == null || !p.in) continue;
                title(sp, Component.literal(n).withStyle(ChatFormatting.YELLOW), Component.empty(), 0, 20, 2);
                sp.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.MASTER, 1f, 1f);
            }
        }
        if (phaseTicks < 100) return;
        phase = Phase.PLAY;
        phaseTicks = 0;
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp == null || !p.in) continue;
            title(sp, Component.literal("Go!").withStyle(ChatFormatting.GREEN), Component.empty(), 0, 20, 10);
            // (a few seconds' grace)
            if (!p.watcher && p.alive) sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 100, 4, false, false));
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.MASTER, 1f, 1.4f);
        }
        mode.go(this);
        Minigames.changed();
    }

    private void play() {
        roundTicks++;
        respawns();
        mode.tick(this);
        if (roundTicks % 40 == 0) keepGiantsIn();
        if (roundTicks % 20 == 0)
            for (P p : people.values()) {
                if (!p.in || p.watcher || !p.alive) continue;
                ServerPlayer sp = online(p);
                Component c = sp == null ? null : mode.status(this, p);
                if (c != null) sp.displayClientMessage(c, true);
            }
        if (phase != Phase.PLAY) return;
        End e = mode.check(this);
        if (e == null && roundTicks >= roundMax) e = mode.timeUp(this);
        if (e != null) endRound(e);
    }

    /** the round is over: who won it said, a pause before the next */
    public void endRound(End e) {
        if (phase != Phase.PLAY && phase != Phase.READY) return;
        last = e;
        phase = Phase.ROUND_END;
        phaseTicks = 0;
        for (UUID w : e.winners()) {
            P p = people.get(w);
            if (p != null) p.roundWins++;
        }
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp == null || !p.in) continue;
            boolean won = e.winners().contains(p.id);
            title(sp, Component.literal(won ? "You win!" : e.winners().isEmpty() ? "Round over" : "Round over")
                    .withStyle(won ? ChatFormatting.GOLD : ChatFormatting.RED), Component.literal(e.line()), 5, 80, 20);
            sp.playNotifySound(won ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.MASTER, 1f, 1f);
        }
        tellRound(e.line());
        GiantsGuideMod.LOG.info("Giants minigames: {} round {}: {}", mode.name(), round, e.line());
        Minigames.changed();
    }

    private void afterRound() {
        if (round < rounds && playing() > 0) {
            phase = Phase.NEXT;
            phaseTicks = 0;
            makeWorld(true);
            tellRound("Round " + (round + 1) + " of " + rounds + ": getting the next map ready…");
            return;
        }
        results();
    }

    private void nextRound() {
        if (build != null && !build.done()) {
            build.tick(System.nanoTime() + 8_000_000L);
            return;
        }
        ServerLevel old = level;
        MgGiants oldGiants = giants;
        level = next;
        map = nextMap;
        next = null;
        nextMap = null;
        giants = new MgGiants(this);
        round++;
        int i = 0;
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp == null || !p.in) continue;
            if (!p.watcher) {
                sp.setGameMode(GameType.ADVENTURE);
                MgSnap.strip(sp);
                place(sp, p, map.start(i++));
            } else place(sp, p, map.watch);
        }
        oldGiants.removeAll(old);
        if (old != null) MgLevels.retire(old);
        startRound();
    }

    /** every round played: the game's winners (most round wins), told, kept, and challenges given */
    private void results() {
        phase = Phase.RESULTS;
        phaseTicks = 0;
        int best = 0;
        for (P p : players()) best = Math.max(best, p.roundWins);
        List<P> winners = new ArrayList<>();
        if (best > 0) for (P p : players()) if (p.roundWins == best) winners.add(p);
        List<String> names = new ArrayList<>();
        for (P p : winners) names.add(p.name);
        String line = rounds == 1 && last != null ? mode.name() + ": " + last.line()
                : winners.isEmpty() ? mode.name() + ": nobody won."
                : mode.name() + ": " + String.join(", ", names) + " won (" + best + " of " + round + " rounds).";
        MgData d = MgData.get(server);
        MgData.Result r = new MgData.Result();
        r.mode = mode.id();
        r.name = mode.name();
        r.by = by;
        r.line = line;
        r.time = System.currentTimeMillis();
        r.players = players().size();
        r.rounds = round;
        r.winners.addAll(names);
        d.result(r);
        for (P p : people.values()) {
            if (p.watcher) continue;
            MgData.Player rec = d.player(p.id.toString(), p.name);
            rec.games++;
            if (winners.contains(p)) {
                rec.wins++;
                rec.modeWins.merge(mode.id(), 1, Integer::sum);
                ServerPlayer sp = online(p);
                if (sp != null && mode.challenge() != null) Minigames.challenge(sp, mode.challenge());
            }
        }
        d.setDirty();
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp == null || !p.in) continue;
            boolean won = winners.contains(p);
            title(sp, Component.literal(won ? "Victory!" : "Game over").withStyle(won ? ChatFormatting.GOLD
                    : ChatFormatting.GRAY), Component.literal(line), 5, 120, 20);
        }
        Minigames.tellResult(server, this, line);
        net.jj.giants.admin.AdminBook.changed("players");
        Minigames.changed();
    }

    /** everyone home, the worlds thrown away, the game over (why: said to everyone in it, or null) */
    void finish(@Nullable String why) {
        phase = Phase.DONE;
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp != null && p.in) {
                sendHome(sp, p);
                if (why != null) sp.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GOLD));
            }
        }
        giants.removeAll(level);
        if (level != null) MgLevels.retire(level);
        if (next != null) MgLevels.retire(next);
        level = next = null;
        bar.removeAllPlayers();
        Minigames.ended(this);
    }

    /** stopped (by someone, or not enough players): everyone home at once */
    void stop(@Nullable String who, String why) {
        GiantsGuideMod.LOG.info("Giants minigames: {} stopped{}: {}", mode.name(), who == null ? "" : " by " + who,
                why);
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp != null && !p.in && phase == Phase.LOBBY)
                sp.sendSystemMessage(Component.literal(why).withStyle(ChatFormatting.GOLD));
        }
        finish(why);
    }

    /** the server stopping: everyone home now (their files are saved right after), the worlds closed */
    void stopNow() {
        phase = Phase.DONE;
        for (P p : people.values()) {
            ServerPlayer sp = online(p);
            if (sp != null && p.in) sendHome(sp, p);
        }
        giants.removeAll(level);
        bar.removeAllPlayers();
        if (level != null) MgLevels.close(server, level);
        if (next != null) MgLevels.close(server, next);
        level = next = null;
    }

    // the boss bar

    private void barNow() {
        String words;
        float prog;
        BossEvent.BossBarColor colour;
        switch (phase) {
            case LOBBY -> {
                int s = lobbyLeft / 20;
                boolean building = lobbyLeft <= 0 && build != null && !build.done();
                words = mode.name() + (building ? " · getting the map ready (" + Math.round(build.progress() * 100)
                        + "%)" : " · starts in " + s / 60 + ":" + String.format("%02d", s % 60)) + " · " + playing()
                        + " in" + (force ? "" : " · /giants join");
                prog = building ? (float) build.progress()
                        : Math.max(0f, Math.min(1f, lobbyLeft / (float) Math.max(1, MgCatalog.num(server, "mgLobby") * 20)));
                colour = BossEvent.BossBarColor.YELLOW;
            }
            case READY -> {
                words = mode.name() + (rounds > 1 ? " · round " + round + " of " + rounds : "") + " · get ready";
                prog = 1f;
                colour = BossEvent.BossBarColor.GREEN;
            }
            case PLAY -> {
                words = mode.bar(this);
                prog = mode.barProgress(this);
                colour = BossEvent.BossBarColor.RED;
            }
            case NEXT -> {
                words = mode.name() + " · next round: getting the map ready ("
                        + Math.round((build == null ? 1 : build.progress()) * 100) + "%)";
                prog = build == null ? 1f : (float) build.progress();
                colour = BossEvent.BossBarColor.BLUE;
            }
            default -> {
                words = mode.name() + " · " + (last != null ? last.line() : "over");
                prog = 1f;
                colour = BossEvent.BossBarColor.PURPLE;
            }
        }
        bar.setName(Component.literal(words));
        bar.setProgress(Math.max(0f, Math.min(1f, prog)));
        bar.setColor(colour);
        // who sees it: everyone in it; in the lobby also everyone online it was offered to
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            P p = people.get(sp.getUUID());
            boolean show = p != null && !p.left && (p.in || phase == Phase.LOBBY)
                    || phase == Phase.LOBBY && (force || tell && !Minigames.quiet(server));
            if (show) bar.addPlayer(sp);
            else bar.removePlayer(sp);
        }
    }
}
