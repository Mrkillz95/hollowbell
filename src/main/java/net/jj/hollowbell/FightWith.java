package net.jj.hollowbell;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.hollowbell.entity.HollowbellEntity;
import net.jj.hollowbell.entity.Giants;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

/**
 * Who each Hollowbell is in a fight with, for his fight music (FightMusic): his theme plays on a player's game only while
 * he is fighting them, and then for the whole fight, pauses and all.
 *
 * <p>A fight with a player starts when he targets them, when they hurt him or he hurts them (their own hits, their
 * arrows and other shots, their tamed creatures; a hit counts for 20 seconds), or when they ride or sit on a giant
 * that is fighting him. It goes on as long as he has them as his target or holds a grudge against them (a grudge lasts
 * until they stay well away for ten minutes), or the giant under them is still fighting him. It ends when he dies,
 * when they die or respawn, when he no longer has them as his target and holds no grudge, when he backs down or
 * flees, when they leave his music range, or when he's calmed or put to sleep. Then the music's hold and fade-out
 * run as before.
 *
 * <p>A calm or wandering one nearby, an owned one that isn't fighting you, or a fight between giants you're not part
 * of: no music. The server works it out and sends each player the ids of the ones in a fight with them (a small
 * payload), twice a second when it changes and every two seconds anyway; the client keeps the last list it got.
 */
public final class FightWith {
    private FightWith() {}

    /** how long a hit, either way, keeps the fight going: 20 seconds */
    public static final long HOLD_TICKS = 400;

    /** for each of him: everything he fought (players and creatures alike) and when, in game time */
    private static final Map<Entity, Map<UUID, Long>> FOUGHT = new WeakHashMap<>();
    /** for each of him: the players he's in a fight with now */
    private static final Map<Entity, Set<UUID>> ENGAGED = new WeakHashMap<>();
    /** what each player was last sent */
    private static final Map<UUID, int[]> SENT = new HashMap<>();

    /** what this game was told: the ids of the ones fighting you (the client's side) */
    private static volatile int[] mine = new int[0];

    /** the ids of the ones of him fighting this player, sent by the server */
    public record Payload(int[] ids) implements CustomPacketPayload {
        public static final Type<Payload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "fight_with"));
        public static final StreamCodec<ByteBuf, Payload> CODEC = StreamCodec.of(
                (b, p) -> {
                    ByteBufCodecs.VAR_INT.encode(b, p.ids.length);
                    for (int id : p.ids) ByteBufCodecs.VAR_INT.encode(b, id);
                },
                b -> {
                    int n = ByteBufCodecs.VAR_INT.decode(b);
                    if (n < 0 || n > 256) throw new IllegalArgumentException("too many: " + n);
                    int[] ids = new int[n];
                    for (int i = 0; i < n; i++) ids[i] = ByteBufCodecs.VAR_INT.decode(b);
                    return new Payload(ids);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** once, in the mod's init: the payload, the hits both ways, and telling each player */
    public static void init() {
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((victim, src, amount) -> {
            hurt(victim, src);
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(FightWith::tell);
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> SENT.remove(h.getPlayer().getUUID()));
        // dying ends every fight you were in: a fresh start after you respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((was, now, alive) -> endAll(now.getUUID()));
    }

    // the client's side

    /** the server's list arrived */
    public static void received(int[] ids) {
        mine = ids.clone();
    }

    /** leaving a world: nobody is fighting you there any more */
    public static void forget() {
        mine = new int[0];
    }

    /** is the one with this entity id in a fight with you (as the server last said) */
    public static boolean withMe(int id) {
        for (int i : mine) if (i == id) return true;
        return false;
    }

    // the server's side

    /** a hit, either way: noted for the one of him in it (called for every hurt, and by his own hurt) */
    public static void hurt(LivingEntity victim, DamageSource src) {
        if (victim.level().isClientSide) return;
        Entity by = Giants.behind(src);
        if (by == null) return;
        long now = victim.level().getGameTime();
        Entity hurtGiant = Giants.ownerOf(victim);
        if (hurtGiant instanceof HollowbellEntity g) {
            Entity who = Giants.ownerOf(by) != null ? Giants.ownerOf(by) : by;
            if (who != g) {
                note(g, who, now);
                // a tamed creature's owner is in the fight too
                if (who instanceof OwnableEntity o && o.getOwner() != null) note(g, o.getOwner(), now);
            }
        }
        if (Giants.ownerOf(by) instanceof HollowbellEntity g && g != hurtGiant) note(g, victim, now);
    }

    /** he and this one fought just now */
    public static void note(Entity giant, Entity other, long now) {
        Map<UUID, Long> m = FOUGHT.computeIfAbsent(giant, k -> new HashMap<>());
        m.put(other.getUUID(), now);
        if (m.size() > 64) m.values().removeIf(t -> now - t > HOLD_TICKS);
    }

    /** did he fight this one in the last 20 seconds (as at game time now) */
    public static boolean recent(Entity giant, Entity other, long now) {
        Map<UUID, Long> m = FOUGHT.get(giant);
        Long t = m == null ? null : m.get(other.getUUID());
        return t != null && now - t >= 0 && now - t <= HOLD_TICKS;
    }

    /** what starts a fight with this player: his target is them, a hit either way in the last 20 s, or their giant's */
    public static boolean starts(HollowbellEntity g, Player p, long now) {
        return targetOf(g) == p || recent(g, p, now) || underFighting(g, p, now);
    }

    /** what keeps it going, however long the pause: his target is still them, his grudge, or their giant's fight */
    public static boolean keeps(HollowbellEntity g, Player p, long now) {
        return targetOf(g) == p || g.holdsGrudge(p) || underFighting(g, p, now);
    }

    /** what ends it at once: he's dead, asleep or calmed, backing down or fleeing; they're dead or out of range */
    public static boolean ends(HollowbellEntity g, Player p) {
        if (g.isRemoved() || g.isDeadOrDying() || (!g.awake() || g.isGhost() || g.flee().running()) || g.getTags().contains("jj_giant_yield")) return true;
        if (p.isRemoved() || p.isDeadOrDying() || p.isSpectator()) return true;
        double dx = g.getX() - p.getX(), dz = g.getZ() - p.getZ(), r = range(g);
        return dx * dx + dz * dz > r * r;
    }

    /** how far off (flat) a fight with him still counts: his music range and its slack, and some */
    public static double range(HollowbellEntity g) {
        return net.jj.hollowbell.ThemeHold.range(g.bellScale()) + 16.0 + 32.0;
    }

    /** the giant this player rides or sits on is in a fight with him */
    private static boolean underFighting(HollowbellEntity g, Player p, long now) {
        Entity on = giantUnder(p);
        return on != null && on != g && (targetOf(g) == on || targetOf(on) == g || recent(g, on, now));
    }

    /**
     * Works out whether he's in a fight with this player now (game time now), and remembers it: started by
     * {@link #starts}, kept by {@link #keeps}, ended by {@link #ends}.
     */
    public static boolean update(HollowbellEntity g, Player p, long now) {
        Set<UUID> with = ENGAGED.computeIfAbsent(g, k -> new java.util.HashSet<>());
        UUID id = p.getUUID();
        if (ends(g, p)) {
            with.remove(id);
            if (p.isDeadOrDying()) forgetHits(id);
            return false;
        }
        if (with.contains(id) && !keeps(g, p, now)) {
            // over: only a new hit, either way, starts another
            with.remove(id);
            Map<UUID, Long> hits = FOUGHT.get(g);
            if (hits != null) hits.remove(id);
            return false;
        }
        if (!with.contains(id) && starts(g, p, now)) with.add(id);
        return with.contains(id);
    }

    /** is he in a fight with this player, as last worked out */
    public static boolean engaged(Entity g, Player p) {
        Set<UUID> with = ENGAGED.get(g);
        return with != null && with.contains(p.getUUID());
    }

    /** this player died: every fight they were in is over, and the hits from before don't start a new one */
    public static void endAll(UUID id) {
        for (Set<UUID> with : ENGAGED.values()) with.remove(id);
        forgetHits(id);
    }

    private static void forgetHits(UUID id) {
        for (Map<UUID, Long> m : FOUGHT.values()) m.remove(id);
    }

    private static void tell(MinecraftServer s) {
        if (s.getTickCount() % 10 != 0) return;
        Map<ServerLevel, List<? extends HollowbellEntity>> all = new HashMap<>();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!ServerPlayNetworking.canSend(p, Payload.TYPE)) continue;
            ServerLevel l = p.serverLevel();
            List<? extends HollowbellEntity> here = all.computeIfAbsent(
                    l, k -> k.getEntities(EntityTypeTest.forClass(HollowbellEntity.class), e -> e.isAlive()));
            long now = l.getGameTime();
            List<Integer> ids = new ArrayList<>();
            for (HollowbellEntity g : here) {
                if (update(g, p, now)) ids.add(g.getId());
            }
            int[] a = ids.stream().mapToInt(Integer::intValue).toArray();
            int[] was = SENT.get(p.getUUID());
            if (!Arrays.equals(a, was) || s.getTickCount() % 40 == 0) {
                ServerPlayNetworking.send(p, new Payload(a));
                SENT.put(p.getUUID(), a);
            }
        }
    }

    // what's what

    /** whom this one (one of him, another giant, any creature) is going after, if anyone */
    public static @Nullable LivingEntity targetOf(@Nullable Entity e) {
        if (e instanceof HollowbellEntity g) return g.getTarget();
        if (e instanceof Mob m && m.getTarget() != null) return m.getTarget();
        if (e == null) return null;
        // (the giants that keep their own prey instead of the game's target)
        var prey = method(e.getClass(), "prey");
        if (prey.isEmpty()) return null;
        try {
            return prey.get().invoke(e) instanceof LivingEntity le ? le : null;
        } catch (Exception x) {
            return null;
        }
    }

    /** the giant this player rides or sits on, if any (any of the giants: his seats and theirs) */
    public static @Nullable Entity giantUnder(Player p) {
        Entity v = p.getVehicle();
        for (int i = 0; i < 4 && v != null; i++) {
            if (Giants.isGiant(v)) return Giants.ownerOf(v) != null ? Giants.ownerOf(v) : v;
            // a seat on a giant: every giant's seats know the one they're on by its id
            var id = method(v.getClass(), "ownerId");
            if (id.isPresent()) {
                try {
                    Entity o = id.get().invoke(v) instanceof Integer n ? v.level().getEntity(n) : null;
                    if (o != null && Giants.isGiant(o)) return Giants.ownerOf(o) != null ? Giants.ownerOf(o) : o;
                } catch (Exception x) {
                    // not a seat after all
                }
            }
            v = v.getVehicle();
        }
        return null;
    }

    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    private static Optional<Method> method(Class<?> c, String name) {
        return METHODS.computeIfAbsent(c.getName() + "#" + name, k -> {
            try {
                Method m = c.getMethod(name);
                m.setAccessible(true);
                return m.getParameterCount() == 0 ? Optional.of(m) : Optional.empty();
            } catch (Exception x) {
                return Optional.empty();
            }
        });
    }
}
