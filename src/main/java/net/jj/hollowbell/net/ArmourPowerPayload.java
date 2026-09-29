package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: the "Armour power" key was pressed. The server checks the set and the wait itself. */
public record ArmourPowerPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ArmourPowerPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "armour_power"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmourPowerPayload> CODEC = StreamCodec.unit(new ArmourPowerPayload());
    @Override public CustomPacketPayload.Type<ArmourPowerPayload> type() { return TYPE; }
}
