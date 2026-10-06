package net.jj.hollowbell.fx;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The "big moment" kit, server half: one small message per event (where, what kind, how big, which way), sent to
 * every player within about 400 blocks. Their game draws it (fx/client/BigFxClient): flying chunks of the real
 * ground, dust, a ground shockwave with cracks, water spray and waves, sparks, embers, a flash, camera shake and
 * late-arriving sound. Nothing here touches the world.
 *
 * To copy it into another mod: this file and fx/client/*, change the package lines and NS below, call
 * {@link #register()} once in the mod's main init and BigFxClient.init(...) in its client init (see the spec).
 */
public final class BigFx {
    private BigFx() {}

    /** the mod's id: the message's name and the textures' folder (assets/NS/textures/fx) */
    public static final String NS = "hollowbell";

    /**
     * The kinds. size is the effect's radius in blocks (already scaled for the giant's size); dir is a direction
     * (0,0,0 = all round); extra depends on the kind (see each).
     */
    public static final int
            /** the big one: chunks of the ground flying, a dust plume and a rolling dust ring, a shockwave with cracks, heavy shake, a boom. On water it becomes SPLASH (and a wave when big). */
            SLAM = 0,
            /** a lighter footfall/landing: a few chunks, a dust ring, a small shockwave, a thud. On water, a small splash. */
            STOMP = 1,
            /** chunks only (extra: what they're made of, Block.getId(state) — ship timber, scrap iron; 0 = the ground there) */
            DEBRIS = 2,
            /** a dust plume going up */
            DUST = 3,
            /** a ring of dust rolling out along the ground */
            DUST_RING = 4,
            /** a ring running out along the ground with cracks (extra: ticks to run its whole size at an even pace, to match a ring the server runs; 0 = by size, fast then slowing) */
            SHOCKWAVE = 5,
            /** a column of water spray (dir: which way it leans) */
            SPRAY = 6,
            /** something big hits water: a crown of spray, a splash ring, foam */
            SPLASH = 7,
            /** a wave wall that rolls out and breaks (dir: which way; 0 = all round. extra: the arc in degrees (0 = 120 when it has a dir) + 1000 x the ticks it takes to roll out (0 = by size)) */
            WAVE = 8,
            /** a burst of sparks (dir: the main way they fly. extra: their colour 0xRRGGBB, 0 = hot sparks off metal) */
            SPARKS = 9,
            /** embers rising and drifting (extra: ticks it keeps giving them, 0 = a short puff) */
            EMBERS = 10,
            /** a flash of light (and a white flash on the screen of those near who look at it) (extra: its colour 0xRRGGBB, 0 = warm white) */
            FLASH = 11,
            /** a blast: flash, sparks, embers, black smoke, chunks, a shockwave, shake, a boom */
            EXPLOSION = 12,
            /** dark smoke going up (cannon smoke, exhaust) (dir: blown that way) */
            SMOKE = 13,
            /** the dust kicked up behind something charging along the ground (dir: the way it's going) */
            TRAIL = 14,
            /** the ground shakes (and rumbles), nothing to see */
            SHAKE = 15,
            /** a whirl: water spiralling in to a hole (or dust on land) (extra: ticks it lasts) */
            WHIRL = 16,
            /** a lingering coloured cloud (poison, spores, seeds) (extra: colour 0xRRGGBB) */
            CLOUD = 17;
    public static final String[] NAMES = {"slam", "stomp", "debris", "dust", "dust_ring", "shockwave", "spray", "splash", "wave",
            "sparks", "embers", "flash", "explosion", "smoke", "trail", "shake", "whirl", "cloud"};

    /** who gets it: everyone this near (plus the effect's own size) */
    public static final double RANGE = 400;

    public record Payload(byte kind, double x, double y, double z, float size, float dx, float dy, float dz, int seed, int extra)
            implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<Payload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NS, "big_fx"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> CODEC = StreamCodec.of(
                (b, p) -> { b.writeByte(p.kind); b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z); b.writeFloat(p.size);
                    b.writeFloat(p.dx); b.writeFloat(p.dy); b.writeFloat(p.dz); b.writeInt(p.seed); b.writeInt(p.extra); },
                b -> new Payload(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(), b.readFloat(), b.readInt(), b.readInt()));
        @Override public CustomPacketPayload.Type<Payload> type() { return TYPE; }
    }

    /** once, in the mod's main init */
    public static void register() {
        PayloadTypeRegistry.playS2C().register(Payload.TYPE, Payload.CODEC);
    }

    public static void send(Level level, int kind, Vec3 at, float size) { send(level, kind, at.x, at.y, at.z, size, 0, 0, 0, 0); }

    public static void send(Level level, int kind, Vec3 at, float size, Vec3 dir) { send(level, kind, at.x, at.y, at.z, size, (float) dir.x, (float) dir.y, (float) dir.z, 0); }

    public static void send(Level level, int kind, Vec3 at, float size, Vec3 dir, int extra) { send(level, kind, at.x, at.y, at.z, size, (float) dir.x, (float) dir.y, (float) dir.z, extra); }

    public static void send(Level level, int kind, double x, double y, double z, float size, float dx, float dy, float dz, int extra) {
        if (!(level instanceof ServerLevel sl) || !(size > 0f) || Double.isNaN(x + y + z)) return;
        remember(sl, kind, x, y, z, size);
        Payload p = new Payload((byte) kind, x, y, z, size, dx, dy, dz, sl.random.nextInt(), extra);
        double r = RANGE + size * 2;
        for (ServerPlayer pl : sl.players())
            if (pl.distanceToSqr(x, y, z) < r * r && ServerPlayNetworking.canSend(pl, Payload.TYPE)) ServerPlayNetworking.send(pl, p);
    }

    // ------------------------------------------------------------------ for the tests: what was sent lately

    public record Sent(long time, int kind, double x, double y, double z, float size) {}
    private static final java.util.ArrayDeque<Sent> recent = new java.util.ArrayDeque<>();

    private static synchronized void remember(ServerLevel sl, int kind, double x, double y, double z, float size) {
        recent.addLast(new Sent(sl.getGameTime(), kind, x, y, z, size));
        while (recent.size() > 512) recent.removeFirst();
    }

    /** what was sent within r blocks (sideways) of at, since that game time */
    public static synchronized java.util.List<Sent> sentNear(Vec3 at, double r, long since) {
        java.util.List<Sent> out = new java.util.ArrayList<>();
        for (Sent s : recent) if (s.time >= since && (s.x - at.x) * (s.x - at.x) + (s.z - at.z) * (s.z - at.z) < r * r) out.add(s);
        return out;
    }
}
