package net.jj.mountain.net;

import net.jj.mountain.MountainMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What the book shows about how he is taking it: how much wind he has left, how much he holds against the
 * person reading, which stage of that he is at, and whether breaking the world is even on the table.
 */
public record MoodPayload(float wind, float sour, int stage, int huntDays, int endState) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MoodPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "mood"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoodPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeFloat(p.wind()); buf.writeFloat(p.sour());
                buf.writeVarInt(p.stage()); buf.writeVarInt(p.huntDays()); buf.writeVarInt(p.endState());
            },
            buf -> new MoodPayload(buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    @Override public CustomPacketPayload.Type<MoodPayload> type() { return TYPE; }
}
