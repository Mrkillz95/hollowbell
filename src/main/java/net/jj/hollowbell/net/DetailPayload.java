package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * /hollowbell detail: the setting lives on the player's own computer, so the server only passes the word on.
 * ASK says which it is, ON and OFF switch it.
 */
public record DetailPayload(int what) implements CustomPacketPayload {
    public static final int ASK = 0, ON = 1, OFF = 2;

    public static final CustomPacketPayload.Type<DetailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "detail"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DetailPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.what()), buf -> new DetailPayload(buf.readVarInt()));

    @Override public CustomPacketPayload.Type<DetailPayload> type() { return TYPE; }
}
