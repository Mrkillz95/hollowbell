package net.jj.mountain.net;

import net.jj.mountain.MountainMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything his eye storm has hold of this moment. There is no room in what the game syncs about him for more
 * than a handful of look points, and the storm can have fifty things in it at once, so the list goes over on its
 * own: one number per creature, and the drawing works out a beam for each.
 */
public record StormPayload(int mountain, int[] targets) implements CustomPacketPayload {
    /** past this many the drawing would cost more than it is worth */
    public static final int MOST = 64;

    public static final CustomPacketPayload.Type<StormPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MountainMod.MODID, "storm"));

    public static final StreamCodec<RegistryFriendlyByteBuf, StormPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mountain());
                int n = Math.min(MOST, p.targets().length);
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) buf.writeVarInt(p.targets()[i]);
            },
            buf -> {
                int who = buf.readVarInt();
                int n = Math.min(MOST, Math.max(0, buf.readVarInt()));
                int[] t = new int[n];
                for (int i = 0; i < n; i++) t[i] = buf.readVarInt();
                return new StormPayload(who, t);
            });

    @Override public CustomPacketPayload.Type<StormPayload> type() { return TYPE; }
}
