package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: I swung at him and it landed on this bone. He is far too big for the game's own reach check
 * (that measures to the one small box at his middle), so swings at him come this way instead and the server checks
 * them against the real shape of him.
 */
public record HitPayload(int bellId, int bone) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "hit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HitPayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.bellId()); buf.writeVarInt(p.bone()); },
            buf -> new HitPayload(buf.readVarInt(), buf.readVarInt()));
    @Override public CustomPacketPayload.Type<HitPayload> type() { return TYPE; }
}
