package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C: полная синхронизация состояния исследований (Server -> Client).
 * Переиспользует toNbt()/fromNbt() ResearchManager, поэтому формат един для
 * сохранения мира и сети.
 */
public record SyncResearchStatePayload(NbtCompound researchNbt) implements CustomPayload {

    public static final CustomPayload.Id<SyncResearchStatePayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("sync_research_state"));

    public static final PacketCodec<RegistryByteBuf, SyncResearchStatePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.NBT_COMPOUND, SyncResearchStatePayload::researchNbt,
                    SyncResearchStatePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
