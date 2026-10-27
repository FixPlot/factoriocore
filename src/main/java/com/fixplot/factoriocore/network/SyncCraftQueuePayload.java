package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

public record SyncCraftQueuePayload(List<String> recipeIds, List<Integer> counts, int currentProgressTicks, int currentMaxTicks) implements CustomPayload {
    public static final CustomPayload.Id<SyncCraftQueuePayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("sync_craft_queue"));

    public static final PacketCodec<RegistryByteBuf, SyncCraftQueuePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.collection(ArrayList::new, PacketCodecs.STRING), SyncCraftQueuePayload::recipeIds,
                    PacketCodecs.collection(ArrayList::new, PacketCodecs.INTEGER), SyncCraftQueuePayload::counts,
                    PacketCodecs.INTEGER, SyncCraftQueuePayload::currentProgressTicks,
                    PacketCodecs.INTEGER, SyncCraftQueuePayload::currentMaxTicks,
                    SyncCraftQueuePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}