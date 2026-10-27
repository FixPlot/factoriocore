package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.research.ResearchManager;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record ResearchActionPayload(int actionType, String techId, int queueIndex) implements CustomPayload {

    public static final int ACTION_QUEUE = 0;
    public static final int ACTION_REMOVE_AT = 1;
    public static final int ACTION_COMPLETE_NOW = 2;

    public static final CustomPayload.Id<ResearchActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(FactorioCore.MOD_ID, "research_action"));

    public static final PacketCodec<RegistryByteBuf, ResearchActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, ResearchActionPayload::actionType,
            PacketCodecs.STRING, ResearchActionPayload::techId,
            PacketCodecs.VAR_INT, ResearchActionPayload::queueIndex,
            ResearchActionPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void handleServer(ResearchActionPayload payload, ServerPlayNetworking.Context context) {
        context.server().execute(() -> {
            ResearchManager.init();
            switch (payload.actionType()) {
                case ACTION_QUEUE -> ResearchManager.queueTech(payload.techId());
                case ACTION_REMOVE_AT -> ResearchManager.removeQueueAt(payload.queueIndex());
                case ACTION_COMPLETE_NOW -> {
                    if (context.player().hasPermissionLevel(2) || context.player().isCreative()) {
                        ResearchManager.completeImmediately(payload.techId());
                    }
                }
            }
            ResearchManager.saveForServer(context.server());
            ResearchManager.broadcastResearchState(context.server());
        });
    }
}