package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Driving him while you are in him: which way you are pushing, where you are looking, and anything you pressed. */
public record DrivePayload(int what, int arg, float forward, float strafe, float yaw) implements CustomPacketPayload {
    public static final int DRIVE = 0, LEAVE = 1, ATTACK = 2;

    public static final CustomPacketPayload.Type<DrivePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "drive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DrivePayload> CODEC = StreamCodec.of(
            (buf, p) -> { buf.writeVarInt(p.what()); buf.writeVarInt(p.arg()); buf.writeFloat(p.forward()); buf.writeFloat(p.strafe()); buf.writeFloat(p.yaw()); },
            buf -> new DrivePayload(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    @Override public CustomPacketPayload.Type<DrivePayload> type() { return TYPE; }
}
