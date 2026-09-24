package net.jj.mountain.net;

import net.jj.mountain.MountainMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server to client: you are in him now (or you are back in yourself), and this is which one. */
public record InsideHimPayload(int mountainId, boolean on) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InsideHimPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "inside_him"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InsideHimPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.mountainId()); buf.writeBoolean(p.on()); },
            buf -> new InsideHimPayload(buf.readVarInt(), buf.readBoolean()));

    @Override public CustomPacketPayload.Type<InsideHimPayload> type() { return TYPE; }
}
