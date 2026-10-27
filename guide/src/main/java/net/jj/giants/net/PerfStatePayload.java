package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's world settings for the Giants Performance screen.
 *
 * @param op       whether this player may change them
 * @param settings the world's settings (the guide's Features and the giants' own world settings), JSON
 * @param values   their values now, JSON (ids left out are at their default)
 * @param message  a word back about the player's last request ("" for none)
 */
public record PerfStatePayload(boolean op, String settings, String values, String message)
        implements CustomPacketPayload {
    public static final Type<PerfStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "perf_state"));
    public static final StreamCodec<FriendlyByteBuf, PerfStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            PerfStatePayload::op,
            ByteBufCodecs.stringUtf8(262144),
            PerfStatePayload::settings,
            ByteBufCodecs.stringUtf8(32768),
            PerfStatePayload::values,
            ByteBufCodecs.stringUtf8(512),
            PerfStatePayload::message,
            PerfStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
