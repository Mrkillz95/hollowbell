package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the book shows about how he is taking it: how much wind he has left, how much he holds against the
 * person reading, which stage of that he is at, and the server's own switches (flags: IN_REACH, GRIEF, HARVEST, ASLEEP).
 */
public record MoodPayload(float wind, float sour, int stage, int huntDays, int flags) implements CustomPacketPayload {

    public static final int IN_REACH = 1, GRIEF = 2, HARVEST = 4, ASLEEP = 8;
    public boolean has(int f) { return (flags & f) != 0; }

    public static final CustomPacketPayload.Type<MoodPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "mood"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoodPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.wind()); buf.writeFloat(p.sour());
                buf.writeVarInt(p.stage()); buf.writeVarInt(p.huntDays()); buf.writeVarInt(p.flags());
            },
            buf -> new MoodPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override public CustomPacketPayload.Type<MoodPayload> type() { return TYPE; }
}
