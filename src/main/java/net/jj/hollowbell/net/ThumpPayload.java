package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: something of his came down hard here, shake the screen. */
public record ThumpPayload(double x, double y, double z, float power) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ThumpPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "thump"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ThumpPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeDouble(p.x()); buf.writeDouble(p.y()); buf.writeDouble(p.z()); buf.writeFloat(p.power()); },
            buf -> new ThumpPayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
    @Override public CustomPacketPayload.Type<ThumpPayload> type() { return TYPE; }
}
