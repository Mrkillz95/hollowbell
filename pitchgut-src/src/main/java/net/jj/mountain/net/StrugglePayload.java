package net.jj.mountain.net;

import net.jj.mountain.MountainMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** "I hit the key this many times": how the fight to get out of his hand gets to the server. */
public record StrugglePayload(int presses) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<StrugglePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "struggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrugglePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, StrugglePayload::presses, StrugglePayload::new);

    @Override public CustomPacketPayload.Type<StrugglePayload> type() { return TYPE; }
}
