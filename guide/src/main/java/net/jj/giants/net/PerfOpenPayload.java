package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** /giants performance (or /giants effects) was typed: the player's game opens the Giants Performance screen. */
public record PerfOpenPayload() implements CustomPacketPayload {
    public static final Type<PerfOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "perf_open"));
    public static final StreamCodec<FriendlyByteBuf, PerfOpenPayload> CODEC = StreamCodec.unit(new PerfOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
