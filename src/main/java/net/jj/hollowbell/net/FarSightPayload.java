package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Once a second, every Hollowbell a player could see on the horizon but isn't being sent as a real creature
 * (too far off, or stepped out of the world): where he is, which way he faces, how big, his mood, and whether he's asleep. An empty
 * list clears them. The client draws each one simply, far off, until the real one arrives.
 */
public record FarSightPayload(List<Far> all) implements CustomPacketPayload {
    public record Far(UUID id, double x, double y, double z, float yaw, float scale, int variant, boolean asleep) {}

    public static final CustomPacketPayload.Type<FarSightPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "far_sight"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FarSightPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.all().size());
                for (Far f : p.all()) {
                    buf.writeUUID(f.id());
                    buf.writeDouble(f.x()); buf.writeDouble(f.y()); buf.writeDouble(f.z());
                    buf.writeFloat(f.yaw()); buf.writeFloat(f.scale());
                    buf.writeVarInt(f.variant());
                    buf.writeBoolean(f.asleep());
                }
            },
            buf -> {
                int n = Math.min(64, buf.readVarInt());
                List<Far> all = new ArrayList<>(n);
                for (int i = 0; i < n; i++)
                    all.add(new Far(buf.readUUID(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readBoolean()));
                return new FarSightPayload(all);
            });

    @Override public CustomPacketPayload.Type<FarSightPayload> type() { return TYPE; }
}
