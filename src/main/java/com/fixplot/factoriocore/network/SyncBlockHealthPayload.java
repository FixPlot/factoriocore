package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record SyncBlockHealthPayload(BlockPos pos, int damageTaken) implements CustomPayload {
    public static final CustomPayload.Id<SyncBlockHealthPayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("sync_block_health"));

    public static final PacketCodec<RegistryByteBuf, SyncBlockHealthPayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, SyncBlockHealthPayload::pos,
                    PacketCodecs.INTEGER, SyncBlockHealthPayload::damageTaken,
                    SyncBlockHealthPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
