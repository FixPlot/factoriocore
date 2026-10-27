package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

public record ChangeStackSizePayload(BlockPos pos, int delta) implements CustomPayload {
    public static final CustomPayload.Id<ChangeStackSizePayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("change_stack_size"));

    public static final PacketCodec<RegistryByteBuf, ChangeStackSizePayload> CODEC =
            PacketCodec.tuple(
                    BlockPos.PACKET_CODEC, ChangeStackSizePayload::pos,
                    PacketCodecs.INTEGER, ChangeStackSizePayload::delta,
                    ChangeStackSizePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}