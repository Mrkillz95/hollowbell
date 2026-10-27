package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A request to change the world's Features: the setting ids and their new values, as one JSON object. Only a
 * request: the server checks the player is an operator before it changes anything.
 */
public record PerfSetPayload(String values) implements CustomPacketPayload {
    public static final Type<PerfSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "perf_set"));
    public static final StreamCodec<FriendlyByteBuf, PerfSetPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(32768), PerfSetPayload::values, PerfSetPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
