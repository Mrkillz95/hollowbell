package net.jj.hollowbell.solid;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: sit down on him where I stand (the triple crouch or the "Sit on giant" key; giant = the one my game
 * sees me standing on), or get up (sit false). The server checks everything.
 */
public record SitPayload(int giant, boolean sit) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hollowbell", "sit"));
    public static final StreamCodec<FriendlyByteBuf, SitPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.giant());
                buf.writeBoolean(p.sit());
            },
            buf -> new SitPayload(buf.readVarInt(), buf.readBoolean()));

    @Override
    public CustomPacketPayload.Type<SitPayload> type() {
        return TYPE;
    }
}
