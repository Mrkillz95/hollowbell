package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * (Part 5) the server to a player's Guide: "world" (the This world pages: rules, vote, leaderboards), "pick" (open
 * My Giant's pick screen with this list), "sync" (the fight music choice for the giants on their game), "said" (the
 * answer to a pick or a vote). ADMIN_BRIDGE.md 20.6, 20.7, 20.14.
 */
public record PlayersStatePayload(String kind, String json) implements CustomPacketPayload {
    public static final String WORLD = "world", PICK = "pick", SYNC = "sync", SAID = "said";
    public static final Type<PlayersStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "players_state"));
    public static final StreamCodec<FriendlyByteBuf, PlayersStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            PlayersStatePayload::kind,
            ByteBufCodecs.stringUtf8(1 << 18),
            PlayersStatePayload::json,
            PlayersStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
