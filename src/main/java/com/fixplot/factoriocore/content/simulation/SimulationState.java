package com.fixplot.factoriocore.content.simulation;

import com.fixplot.factoriocore.content.combat.biters.BiterAttackDirector;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Единое персистентное состояние глобальной симуляции (фабрики, загрязнение,
 * кусаки, эволюция). Заменяет ручной файл factoriocore_simulation.nbt:
 * атомарная запись движком вместе с чанками, автосохранение, один источник истины.
 */
public class SimulationState extends PersistentState {

    public static final String KEY = "factoriocore_simulation";

    private static final Type<SimulationState> TYPE =
            new Type<>(SimulationState::new, SimulationState::fromNbt, null);

    /** Кластеры фабрик по супер-чанкам (4x4 чанков). */
    public final Long2ObjectMap<FactoryCluster> clusters = new Long2ObjectOpenHashMap<>();
    /** Глобальная сетка загрязнения + пулы гнёзд. */
    public final VirtualPollutionGrid pollution = new VirtualPollutionGrid();
    /** Директор штурмовых групп кусак (макро-ИИ). */
    public final BiterAttackDirector biters;
    /** Фактор эволюции кусак [0..1] — устаревшее поле, источник истины теперь
     *  BiterEvolutionManager (PersistentState); оставлено для миграции сейвов. */
    public float evolutionFactor = 0.0f;

    private SimulationState() {
        this.biters = new BiterAttackDirector(this);
    }

    public static SimulationState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public static SimulationState fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        SimulationState s = new SimulationState();
        if (nbt.contains("Pollution")) s.pollution.readNbt(nbt.getCompound("Pollution"));
        if (nbt.contains("Biters")) s.biters.readNbt(nbt.getCompound("Biters"));
        s.evolutionFactor = nbt.getFloat("Evolution");
        if (nbt.contains("Clusters", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Clusters", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                FactoryCluster c = FactoryCluster.fromNbt(list.getCompound(i), World.OVERWORLD);
                s.clusters.put(c.superChunkKey, c);
            }
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.put("Pollution", pollution.writeNbt(new NbtCompound()));
        nbt.put("Biters", biters.writeNbt(new NbtCompound()));
        nbt.putFloat("Evolution", evolutionFactor);
        NbtList list = new NbtList();
        for (FactoryCluster c : clusters.values()) {
            list.add(c.writeNbt(new NbtCompound()));
        }
        nbt.put("Clusters", list);
        return nbt;
    }

    /**
     * Эволюция кусак — теперь в BiterEvolutionManager (PersistentState).
     * Здесь: миграция устаревшего поля evolutionFactor при первой загрузке
     * старого сейва и единый шаг роста (pollution + time слагаемые).
     */
    public void stepEvolution(ServerWorld world) {
        var evolution = com.fixplot.factoriocore.content.combat.biters.BiterEvolutionManager.get(world);

        // Миграция: если менеджер ещё чист, а старое поле было заполнено — переносим.
        if (evolution.getEvolutionFactor() <= 0f && this.evolutionFactor > 0f) {
            evolution.setEvolutionFactor(this.evolutionFactor);
        }

        if (world.getTime() % 200 != 0) return; // раз в 10 с

        float totalPollution = 0f;
        for (var e : pollution.raw().long2FloatEntrySet()) {
            totalPollution += e.getFloatValue();
        }
        // destroy_factor менеджер получает событийно (onNestDestroyed).
        evolution.step(world, totalPollution);

        // Держим устаревшее поле синхронным для сейв-совместимости.
        this.evolutionFactor = evolution.getEvolutionFactor();
    }

    public FactoryCluster getOrCreateCluster(World world, BlockPos superPos) {
        long key = FactoryClusterKey.of(superPos);
        return clusters.computeIfAbsent(key,
                k -> new FactoryCluster(k, world.getRegistryKey()));
    }

    /** Утилита ключа супер-чанка. */
    public static final class FactoryClusterKey {
        public static long of(int blockX, int blockZ) {
            return net.minecraft.util.math.ChunkPos.toLong(blockX >> 6, blockZ >> 6);
        }
        public static long of(net.minecraft.util.math.BlockPos pos) {
            return of(pos.getX(), pos.getZ());
        }
        private FactoryClusterKey() {}
    }
}
