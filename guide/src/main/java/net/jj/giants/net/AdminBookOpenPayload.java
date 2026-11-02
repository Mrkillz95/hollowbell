package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** An operator used the Giants Admin Book: their game opens its screen (which then asks for the pages). */
public record AdminBookOpenPayload() implements CustomPacketPayload {
    public static final Type<AdminBookOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "admin_open"));
    public static final StreamCodec<FriendlyByteBuf, AdminBookOpenPayload> CODEC =
            StreamCodec.unit(new AdminBookOpenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
