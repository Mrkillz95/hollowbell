package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The admin book's screen asks the server for its pages ("" = every page; it then gets fresh ones whenever anything
 * changes) or says it was closed ("close"). Only a request: the server checks the player is an operator first.
 */
public record AdminBookAskPayload(String what) implements CustomPacketPayload {
    public static final String ALL = "", CLOSE = "close", LIVE = "live", LIVE_OFF = "live_off";
    public static final Type<AdminBookAskPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "admin_ask"));
    public static final StreamCodec<FriendlyByteBuf, AdminBookAskPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(64), AdminBookAskPayload::what, AdminBookAskPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
