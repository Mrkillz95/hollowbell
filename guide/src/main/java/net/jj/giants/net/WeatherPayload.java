package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (Part 9, ADMIN_BRIDGE.md 25.5) The server to a player's Guide: the giant weather round them now ("" = none) and how
 * strong (0-1). Their game eases into it and out of it; the rain and thunder come as the game's own weather packets.
 */
public record WeatherPayload(String kind, float strength) implements CustomPacketPayload {
    public static final Type<WeatherPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "weather"));
    public static final StreamCodec<FriendlyByteBuf, WeatherPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            WeatherPayload::kind,
            ByteBufCodecs.FLOAT,
            WeatherPayload::strength,
            WeatherPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
