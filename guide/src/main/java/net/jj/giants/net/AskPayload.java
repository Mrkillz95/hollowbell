package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** The guide asks the server for live news about one giant (where he is, his settings). */
public record AskPayload(String giant) implements CustomPacketPayload {
    public static final Type<AskPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "ask"));
    public static final StreamCodec<FriendlyByteBuf, AskPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(64), AskPayload::giant, AskPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
