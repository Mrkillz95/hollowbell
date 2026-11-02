package net.jj.giants.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One of the admin book's pages from the server, or its word on a request.
 *
 * @param kind "home", "page" (the all-giants settings or one giant's), "said" (the answer to a change), "denied",
 *             and (Part 2B) "role", "live", "changes", "presets", "preview" (a preset, import or snapshot's
 *             changes before Apply) and "export" (the settings string to copy)
 * @param json what it holds (ADMIN_BRIDGE.md, "Book payloads")
 */
public record AdminBookStatePayload(String kind, String json) implements CustomPacketPayload {
    public static final String HOME = "home", PAGE = "page", SAID = "said", DENIED = "denied", ROLE = "role",
            LIVE = "live", CHANGES = "changes", PRESETS = "presets", PREVIEW = "preview", EXPORT = "export";
    public static final Type<AdminBookStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("jj_giants", "admin_state"));
    public static final StreamCodec<FriendlyByteBuf, AdminBookStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(16),
            AdminBookStatePayload::kind,
            ByteBufCodecs.stringUtf8(1 << 19),
            AdminBookStatePayload::json,
            AdminBookStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
