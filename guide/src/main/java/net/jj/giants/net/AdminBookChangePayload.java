package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A change asked for from the admin book, as JSON: {"do":"set"|"reset","giant":..,"id":..,"value":..,
 * "scope":"world"|"allworlds"} (ADMIN_BRIDGE.md, "Book payloads"). Only a request: the server checks the player is
 * an operator, then does exactly what /giants admin set or reset does.
 */
public record AdminBookChangePayload(String json) implements CustomPacketPayload {
    public static final Type<AdminBookChangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "admin_change"));
    public static final StreamCodec<FriendlyByteBuf, AdminBookChangePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(4096), AdminBookChangePayload::json, AdminBookChangePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
