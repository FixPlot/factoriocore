package com.fixplot.factoriocore.content.simulation;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;

/**
 * Виртуальная нода машины в бестелесном графе фабрики. Живёт в FactoryCluster,
 * тикается не каждый серверный тик, а батчами (шаг симуляции раз в N тиков),
 * что и даёт факториовскую пропускную способность при копеечном TPS.
 */
public class VirtualMachineNode {

    public final BlockPos pos;
    /** Тип машины: полное имя класса BE, для валидации при материализации. */
    public final String machineType;
    /** Виртуальное игровое время последнего шага симуляции ноды. */
    public long lastSimTick;

    public VirtualMachineNode(BlockPos pos, String machineType, long lastSimTick) {
        this.pos = pos.toImmutable();
        this.machineType = machineType;
        this.lastSimTick = lastSimTick;
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("Pos", pos.asLong());
        nbt.putString("Type", machineType);
        nbt.putLong("LastSim", lastSimTick);
        return nbt;
    }

    public static VirtualMachineNode readNbt(NbtCompound nbt) {
        return new VirtualMachineNode(
                BlockPos.fromLong(nbt.getLong("Pos")),
                nbt.getString("Type"),
                nbt.getLong("LastSim"));
    }
}
