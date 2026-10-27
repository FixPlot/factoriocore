package com.fixplot.factoriocore.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public record SetAssemblerRecipePayload(BlockPos pos, String recipeId) implements CustomPayload {
    public static final CustomPayload.Id<SetAssemblerRecipePayload> ID = new CustomPayload.Id<>(Identifier.of("factoriocore", "set_assembler_recipe"));
    public static final PacketCodec<RegistryByteBuf, SetAssemblerRecipePayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SetAssemblerRecipePayload::pos,
            PacketCodecs.STRING, SetAssemblerRecipePayload::recipeId,
            SetAssemblerRecipePayload::new
    );
    @Override public Id<? extends CustomPayload> getId() { return ID; }
}