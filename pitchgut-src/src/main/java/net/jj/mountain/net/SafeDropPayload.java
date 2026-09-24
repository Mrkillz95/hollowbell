package net.jj.mountain.net;

import net.jj.mountain.MountainMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: take this one off my safe list. It names the entry rather than counting to it, so a row
 * pressed twice in a hurry can't take somebody else off instead.
 */
public record SafeDropPayload(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SafeDropPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "safe_drop"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafeDropPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeUtf(p.id(), 200),
            buf -> new SafeDropPayload(buf.readUtf(200)));

    @Override public CustomPacketPayload.Type<SafeDropPayload> type() { return TYPE; }
}
