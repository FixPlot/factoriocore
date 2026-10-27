package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

public record RequestCraftPayload(String recipeId, int action) implements CustomPayload {
    // action: 1 = скрафтить 1 шт, 5 = скрафтить 5 шт, 999 = скрафтить максимум, -1 = отменить слот очереди (recipeId = индекс)
    public static final CustomPayload.Id<RequestCraftPayload> ID =
            new CustomPayload.Id<>(FactorioCore.id("request_craft"));

    public static final PacketCodec<RegistryByteBuf, RequestCraftPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, RequestCraftPayload::recipeId,
                    PacketCodecs.INTEGER, RequestCraftPayload::action,
                    RequestCraftPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}