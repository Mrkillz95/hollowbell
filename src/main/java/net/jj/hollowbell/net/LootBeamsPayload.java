package net.jj.hollowbell.net;

import net.jj.hollowbell.HollowbellMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Every two seconds (and at once when one goes on or off): the loot caches with their beam on within 1024 blocks of
 * you in your dimension. Your game draws the beams of the ones whose ground isn't loaded for you. Empty clears them.
 */
public record LootBeamsPayload(List<BlockPos> all) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LootBeamsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HollowbellMod.MOD_ID, "loot_beams"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LootBeamsPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.all().size());
                for (BlockPos b : p.all()) buf.writeBlockPos(b);
            },
            buf -> {
                int n = buf.readVarInt();
                List<BlockPos> all = new ArrayList<>(Math.min(n, 256));
                for (int i = 0; i < n; i++) { BlockPos b = buf.readBlockPos(); if (i < 256) all.add(b); }
                return new LootBeamsPayload(all);
            });

    @Override public CustomPacketPayload.Type<LootBeamsPayload> type() { return TYPE; }
}
