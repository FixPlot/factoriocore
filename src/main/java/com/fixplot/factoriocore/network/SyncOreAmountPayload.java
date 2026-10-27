package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record SyncOreAmountPayload(BlockPos pos, int amount) implements CustomPayload {
    public static final CustomPayload.Id<SyncOreAmountPayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("sync_ore_amount"));

    public static final PacketCodec<RegistryByteBuf, SyncOreAmountPayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, SyncOreAmountPayload::pos,
                    PacketCodecs.INTEGER, SyncOreAmountPayload::amount,
                    SyncOreAmountPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}