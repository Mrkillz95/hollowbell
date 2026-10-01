package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** The server's answer: the lines his own mod gave (the same as /giants where and /giants). */
public record ToldPayload(String giant, List<String> lines) implements CustomPacketPayload {
    public static final Type<ToldPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "told"));
    public static final StreamCodec<FriendlyByteBuf, ToldPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), ToldPayload::giant,
            ByteBufCodecs.stringUtf8(512).apply(ByteBufCodecs.list(24)), ToldPayload::lines,
            ToldPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
