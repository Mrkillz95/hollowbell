package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** "He's going after that one": the creature glows for a moment, for the one who gave the order only. */
public record LookMarkPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LookMarkPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "look_mark"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LookMarkPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.entityId()), buf -> new LookMarkPayload(buf.readVarInt()));

    @Override public CustomPacketPayload.Type<LookMarkPayload> type() { return TYPE; }
}
