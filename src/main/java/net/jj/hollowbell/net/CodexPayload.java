package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A line pressed in the book: what was asked for, and anything it needed to know. */
public record CodexPayload(int action, int arg, double x, double z) implements CustomPacketPayload {
    public static final int COME = 0, GO_THERE = 1, ATTACK_THAT = 2, STAY = 3, CALM = 4, HUNTER = 5, GUARDIAN = 6, RIDE = 7,
            CALL_OFF = 10, ATTACK_MOVE = 11, BREAK_BLOCKS = 13, GO_TO_XZ = 14, FORGIVE = 18, WHERE = 21, SPARE_LOOK = 22,
            SPARE_ME = 23, SAFE_GET = 24, SPARE_NEAR = 26, LET_GO = 28, HARVEST = 29;

    public CodexPayload(int action) { this(action, 0, 0, 0); }
    public CodexPayload(int action, int arg) { this(action, arg, 0, 0); }

    public static final CustomPacketPayload.Type<CodexPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "codex"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.action()); buf.writeVarInt(p.arg()); buf.writeDouble(p.x()); buf.writeDouble(p.z()); },
            buf -> new CodexPayload(buf.readVarInt(), buf.readVarInt(), buf.readDouble(), buf.readDouble()));

    @Override public CustomPacketPayload.Type<CodexPayload> type() { return TYPE; }
}
