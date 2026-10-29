package net.jj.hollowbell.solid;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Client to server: sit down on him where I stand (the triple crouch or the "Sit on giant" key; giant = the one my game
 * sees me standing on, frame and rest = the spot of him under my feet as my game has it, frame -1 if none), or get up
 * (sit false). The server checks everything.
 */
public record SitPayload(int giant, boolean sit, int frame, Vector3f rest) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("hollowbell", "sit"));
    public static final StreamCodec<FriendlyByteBuf, SitPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.giant());
                buf.writeBoolean(p.sit());
                buf.writeVarInt(p.frame() + 1);
                if (p.frame() >= 0) buf.writeVector3f(p.rest());
            },
            buf -> {
                int giant = buf.readVarInt();
                boolean sit = buf.readBoolean();
                // (an older game sends only the first two)
                int frame = buf.isReadable() ? buf.readVarInt() - 1 : -1;
                Vector3f rest = frame >= 0 ? buf.readVector3f() : new Vector3f();
                return new SitPayload(giant, sit, frame, rest);
            });

    public SitPayload(int giant, boolean sit) {
        this(giant, sit, -1, new Vector3f());
    }

    public SitPayload(int giant, @Nullable Solid.Anchor spot) {
        this(giant, true, spot == null ? -1 : spot.frame(), spot == null ? new Vector3f() : spot.rest());
    }

    /** the spot of him my game has under my feet, or null */
    public @Nullable Solid.Anchor spot() {
        return frame >= 0 ? new Solid.Anchor(frame, new Vector3f(rest)) : null;
    }

    @Override
    public CustomPacketPayload.Type<SitPayload> type() {
        return TYPE;
    }
}
