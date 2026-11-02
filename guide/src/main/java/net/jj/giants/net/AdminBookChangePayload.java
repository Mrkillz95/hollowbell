package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A request from the admin book, as JSON: {"do":"set"|"reset","giant":..,"id":..,"value":..,
 * "scope":"world"|"allworlds"}, and (Part 2B) live, summon, lock, unlock, undo, undoto, restore, preset, import, apply,
 * savepreset, deletepreset, export, mod (ADMIN_BRIDGE.md, "Book payloads"). Only a request: the server checks the
 * player's role every time, then does exactly what the matching /giants admin command does.
 */
public record AdminBookChangePayload(String json) implements CustomPacketPayload {
    /** longest request (an import's pasted settings string goes in one) */
    public static final int MOST = 30000;
    public static final Type<AdminBookChangePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "admin_change"));
    public static final StreamCodec<FriendlyByteBuf, AdminBookChangePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MOST + 2000), AdminBookChangePayload::json, AdminBookChangePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
