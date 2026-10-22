package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: a Glass Bell stands here, this big, for this many more ticks (0: it's gone). */
public record GlassBellPayload(double x, double y, double z, float radius, int ticks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlassBellPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "glass_bell"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GlassBellPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeDouble(p.x());
                buf.writeDouble(p.y());
                buf.writeDouble(p.z());
                buf.writeFloat(p.radius());
                buf.writeVarInt(p.ticks());
            },
            buf -> new GlassBellPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readVarInt()));

    @Override
    public CustomPacketPayload.Type<GlassBellPayload> type() {
        return TYPE;
    }
}
