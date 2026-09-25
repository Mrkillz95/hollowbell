package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** Server to client: your own safe list, by name, and whether it is doing anything right now. */
public record SafeListPayload(List<String> names, List<String> ids, boolean inForce, boolean hasBook) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SafeListPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "safe_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SafeListPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                int n = Math.min(64, Math.min(p.names().size(), p.ids().size()));
                buf.writeVarInt(n);
                for (int i = 0; i < n; i++) {
                    buf.writeUtf(cut(p.names().get(i), 64), 64);
                    buf.writeUtf(cut(p.ids().get(i), 200), 200);
                }
                buf.writeBoolean(p.inForce());
                buf.writeBoolean(p.hasBook());
            },
            buf -> {
                int n = Math.min(64, Math.max(0, buf.readVarInt()));
                List<String> names = new ArrayList<>(n), ids = new ArrayList<>(n);
                for (int i = 0; i < n; i++) { names.add(buf.readUtf(64)); ids.add(buf.readUtf(200)); }
                return new SafeListPayload(names, ids, buf.readBoolean(), buf.readBoolean());
            });

    /** a name from somewhere else can be any length; the packet has a limit and breaking it drops the player */
    private static String cut(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }

    @Override public CustomPacketPayload.Type<SafeListPayload> type() { return TYPE; }
}
