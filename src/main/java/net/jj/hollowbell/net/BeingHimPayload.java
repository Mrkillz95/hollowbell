package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: you are on his crown and working him now (or not any more). */
public record BeingHimPayload(int bellId, boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BeingHimPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "being_him"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BeingHimPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.bellId()); buf.writeBoolean(p.on()); },
            buf -> new BeingHimPayload(buf.readVarInt(), buf.readBoolean()));
    @Override public CustomPacketPayload.Type<BeingHimPayload> type() { return TYPE; }
}
