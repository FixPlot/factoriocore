package com.fixplot.factoriocore.network;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

public record SyncPollutionPayload(Map<Long, Float> chunkPollution) implements CustomPayload {

    public static final CustomPayload.Id<SyncPollutionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(FactorioCore.MOD_ID, "sync_pollution"));

    public static final PacketCodec<RegistryByteBuf, SyncPollutionPayload> CODEC = PacketCodec.of(
            (value, buf) -> {
                Map<Long, Float> map = value.chunkPollution();
                buf.writeVarInt(map.size());
                for (Map.Entry<Long, Float> entry : map.entrySet()) {
                    buf.writeLong(entry.getKey());
                    buf.writeFloat(entry.getValue());
                }
            },
            buf -> {
                int size = buf.readVarInt();
                Map<Long, Float> map = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    long key = buf.readLong();
                    float val = buf.readFloat();
                    map.put(key, val);
                }
                return new SyncPollutionPayload(map);
            }
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}