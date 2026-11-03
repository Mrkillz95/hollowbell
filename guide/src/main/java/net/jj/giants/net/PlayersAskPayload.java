package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (Part 5) a player's Guide asks: {"do":"world"} the This world pages, "pick" the pick screen, "choose" a giant
 * ({"giant"}), "vote" ({"n"}). Only a request: the server checks everything.
 */
public record PlayersAskPayload(String json) implements CustomPacketPayload {
    public static final Type<PlayersAskPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "players_ask"));
    public static final StreamCodec<FriendlyByteBuf, PlayersAskPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(512), PlayersAskPayload::json, PlayersAskPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
