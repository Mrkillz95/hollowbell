package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A player's Giants Performance screen asks the server for the world's settings (and whether they may change them). */
public record PerfAskPayload() implements CustomPacketPayload {
    public static final Type<PerfAskPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "perf_ask"));
    public static final StreamCodec<FriendlyByteBuf, PerfAskPayload> CODEC = StreamCodec.unit(new PerfAskPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
