package com.fixplot.factoriocore.content.simulation;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.network.SyncPollutionPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Центральный оркестратор глобальной симуляции фабрики:
 *  - регистрация машин в кластеры (вместо setChunkForced у PollutionManager);
 *  - duty-cycle ротация FACTORY_TICKING с бюджетом;
 *  - снапшот виртуальных нод при выгрузке, catch-up при материализации;
 *  - глобальный тик симуляции (загрязнение, кусаки, эволюция) — без загрузки вокселей.
 *
 * Точки расширения существующего кода:
 *  - PollutionManager.registerFactoryBlock -> GlobalFactoryManager.onFactoryBlockAdded
 *  - PollutionManager.tickGlobalRegisteredMachines -> эмиссия из виртуальных нод
 *  - PollutionManager.saveSimulationState/loadSimulationState -> SimulationState (PersistentState)
 */
public final class GlobalFactoryManager {

    // ======== Бюджеты (вынести в FactorioConfig при следующем проходе) ========
    /** Максимум чанков фабрики на entity-ticking одновременно. */
    private static int maxTickingChunks = 32;
    /** Тиков (10 с) entity-ticking на чанк в ротации. */
    private static int dutyTicks = 200;
    /** Максимум суммарно LOADED (level 33) чанков фабрики — RAM guard. */
    private static int maxLoadedChunks = 2048;
    /** Интервал шага загрязнения (2 с). */
    private static final int POLLUTION_STEP_TICKS = 40;

    private static MinecraftServer server = null;
    private static int loadedTicketCount = 0;

    private GlobalFactoryManager() {}

    // =====================================================================
    // Регистрация машин
    // =====================================================================

    public static void onFactoryBlockAdded(ServerWorld world, BlockPos masterPos) {
        if (world == null || masterPos == null) return;
        SimulationState state = SimulationState.get(world);

        FactoryCluster cluster = state.getOrCreateCluster(world, masterPos);
        cluster.addMachine(world, masterPos);
        loadedTicketCount++;
        state.markDirty();

        // Персистентный чанк-менеджер: уровень 32 (BE-тики, без мобов),
        // списке чанков выживает рестарт сервера; тикет — сразу.
        FactorioChunkManager chunkManager = FactorioChunkManager.get(world);
        chunkManager.addFactoryChunk(world, masterPos.getX() >> 4, masterPos.getZ() >> 4, true);

        // Виртуальная нода для уже существующей машины гасится: чанк теперь
        // материализован, BE живой, catch-up выполнит он сам при загрузке.
        cluster.consumeVirtualNode(masterPos);
    }

    public static void onFactoryBlockRemoved(ServerWorld world, BlockPos masterPos) {
        if (world == null || masterPos == null) return;
        SimulationState state = SimulationState.get(world);

        FactoryCluster cluster = state.clusters.get(SimulationState.FactoryClusterKey.of(masterPos));
        if (cluster == null) return;
        boolean chunkReleased = cluster.removeMachine(world, masterPos);
        if (chunkReleased) {
            loadedTicketCount--;
            FactorioChunkManager.get(world).removeFactoryChunk(
                    world, masterPos.getX() >> 4, masterPos.getZ() >> 4);
        }
        if (cluster.isEmpty()) {
            cluster.releaseAllTickets(world);
            state.clusters.remove(cluster.superChunkKey);
        }
        state.markDirty();
    }

    /** BE нашей фабрики? (для проверки остатков при снятии тикета). */
    public static boolean isFactoryBlockEntity(BlockEntity be) {
        return be instanceof VirtualSimulatable
                || be instanceof TurretBlockEntity
                || be instanceof FactorioChestBlockEntity
                || be instanceof InserterBlockEntity
                || be instanceof BurnerDrillBlockEntity
                || be instanceof ElectricDrillBlockEntity
                || be instanceof FactorioFurnaceBlockEntity
                || be instanceof AssemblerBlockEntity;
    }

    // =====================================================================
    // Главный тик (END_SERVER_TICK)
    // =====================================================================

    public static void tickServer(MinecraftServer mcServer) {
        server = mcServer;
        for (ServerWorld world : mcServer.getWorlds()) {
            if (world.getRegistryKey() != World.OVERWORLD && world.getPlayers().isEmpty()) {
                continue; // пустые миры не симулируем
            }

            SimulationState state = SimulationState.get(world);

            // 1. Duty-ротация чанков фабрик (каждый тик, O(кластеров) — дёшево).
            for (FactoryCluster cluster : state.clusters.values()) {
                cluster.rotateDuty(world, maxTickingChunks, dutyTicks);
            }

            // 2. Загрязнение: диффузия + поглощение + пулы гнёзд (раз в 40 тиков).
            if (world.getTime() % POLLUTION_STEP_TICKS == 0) {
                state.pollution.step(world);
            }

            // 3. Эмиссия машин в выгруженных чанках — виртуальные ноды раз в 20 тиков.
            if (world.getTime() % 20 == 0) {
                tickVirtualMachineEmissions(world, state);
            }

            // 4. Кусаки: макро-движение, материализация (внутри шаг раз в 20 тиков).
            state.biters.step(world, state);

            // 5. Эволюция (внутри шаг раз в 200 тиков).
            state.stepEvolution(world);

            // 6. Клиентская синхронизация: только dirty-чанки (раз в 20 тиков).
            if (world.getTime() % 20 == 0) {
                syncPollutionToClients(world, state);
            }
        }
    }

    /**
     * Эмиссия загрязнения от машин в ВИРТУАЛЬНОМ состоянии. Машины рядом с игроком
     * (в тикающих чанках) эмиссируют через обычные BE-тикеры (существующий путь);
     * здесь обрабатываем только дальние, у которых нет тикающего BE.
     */
    private static void tickVirtualMachineEmissions(ServerWorld world, SimulationState state) {
        for (FactoryCluster cluster : state.clusters.values()) {
            for (VirtualMachineNode node : cluster.nodes.values()) {
                long chunkKey = ChunkPos.toLong(node.pos.getX() >> 4, node.pos.getZ() >> 4);
                if (world.isChunkLoaded(ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey))) {
                    // Чанк материализован: BE-тикер сам эмиссирует — не дублируем.
                    // Но catch-up ещё не обязательно выполнен: BE при CHUNK_LOAD
                    // получает elapsed и доматывает работу, эмиссия посчитается там.
                    cluster.consumeVirtualNode(node.pos);
                    continue;
                }
                // Дормант-эмиссия: статистическая, по типу машины.
                float rate = VirtualEmissionTable.rateFor(node.machineType);
                if (rate > 0f) {
                    state.pollution.emit(chunkKey, rate);
                }
                node.lastSimTick = world.getTime();
            }
        }
    }

    /**
     * Синхронизация карты загрязнения клиентам: дельта только изменившихся чанков
     * в радиусе видимости игрока (24 чанка) — вместо полной карты каждый раз.
     */
    private static void syncPollutionToClients(ServerWorld world, SimulationState state) {
        var dirty = state.pollution.drainDirtyForSync();
        boolean dirtyEmpty = (dirty == null || dirty.isEmpty());

        for (ServerPlayerEntity player : world.getPlayers()) {
            int pcx = player.getChunkPos().x;
            int pcz = player.getChunkPos().z;

            if (dirtyEmpty) {
                // Нечего слать — пропускаем игрока (было: полная карта каждый раз).
                continue;
            }

            java.util.Map<Long, Float> nearby = new java.util.HashMap<>();
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    long k = ChunkPos.toLong(pcx + dx, pcz + dz);
                    if (dirty.containsKey(k) && dirty.get(k) >= 0.25f) {
                        nearby.put(k, dirty.get(k));
                    }
                }
            }
            if (!nearby.isEmpty()) {
                ServerPlayNetworking.send(player, new SyncPollutionPayload(nearby));
            }
        }
    }

    // =====================================================================
    // События чанков
    // =====================================================================

    /** CHUNK_UNLOAD: BE снапшотятся в виртуальные ноды кластера. */
    public static void onChunkUnload(ServerWorld world, WorldChunk chunk) {
        SimulationState state = SimulationState.get(world);
        FactoryCluster cluster = state.clusters.get(
                SimulationState.FactoryClusterKey.of(chunk.getPos().getStartX(), chunk.getPos().getStartZ()));
        if (cluster != null) {
            cluster.captureVirtualNodes(world, chunk);
        }
    }

    /**
     * CHUNK_LOAD: материализованный BE с VirtualSimulatable делает catch-up
     * виртуально прожитых тиков.
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        SimulationState state = SimulationState.get(world);
        FactoryCluster cluster = state.clusters.get(
                SimulationState.FactoryClusterKey.of(chunk.getPos().getStartX(), chunk.getPos().getStartZ()));
        if (cluster == null) return;

        long now = world.getTime();
        for (var entry : chunk.getBlockEntities().entrySet()) {
            BlockEntity be = entry.getValue();
            if (!(be instanceof VirtualSimulatable sim)) continue;

            VirtualMachineNode node = cluster.nodes.get(entry.getKey().asLong());
            if (node == null) continue;

            long elapsed = Math.max(0, now - node.lastSimTick);
            if (elapsed > 1) {
                sim.catchUp(elapsed);
                sim.markVirtualTicked(now);
            }
            cluster.consumeVirtualNode(entry.getKey());
        }
    }

    // =====================================================================
    // Запуск/остановка
    // =====================================================================

    /** ServerWorldEvents.LOAD: тикеты не пережили рестарт — перевыдаём. */
    public static void onWorldLoad(MinecraftServer mcServer, ServerWorld world) {
        server = mcServer;
        SimulationState state = SimulationState.get(world);

        int reissued = 0;
        for (FactoryCluster cluster : state.clusters.values()) {
            if (!cluster.dimension.equals(world.getRegistryKey())) continue;
            // RAM-guard: не загружаем сверх бюджета, остальное остаётся виртуальным.
            for (long chunkKey : cluster.factoryChunks) {
                if (reissued++ >= maxLoadedChunks) break;
                FactoryTickets.setLoaded(world,
                        ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey), true);
            }
            cluster.reissueLoadedTickets(world);
        }
        loadedTicketCount = reissued;

        // Персистентные чанк-тикеты фабрики (список выжил рестарт).
        FactorioChunkManager.get(world).reissueAllTickets(world);
    }

    /** ServerWorldEvents.UNLOAD: снимаем все наши тикеты. */
    public static void onWorldUnload(MinecraftServer mcServer, ServerWorld world) {
        SimulationState state = SimulationState.get(world);
        for (FactoryCluster cluster : state.clusters.values()) {
            if (cluster.dimension.equals(world.getRegistryKey())) {
                cluster.releaseAllTickets(world);
            }
        }
        FactorioChunkManager.get(world).removeAllTickets(world);
    }

    /** Быстрая проверка для BiterAttackDirector: чанк сейчас на FACTORY_TICKING? */
    public static boolean isChunkTickingByFactory(ServerWorld world, long chunkKey) {
        SimulationState state = SimulationState.get(world);
        FactoryCluster cluster = state.clusters.get(
                SimulationState.FactoryClusterKey.of(
                        ChunkPos.getPackedX(chunkKey) << 4,
                        ChunkPos.getPackedZ(chunkKey) << 4));
        if (cluster == null) return false;
        // Кластер не хранит публичный доступ к tickingSince — упрощённая эвристика:
        // чанк фабрики загружен (level 33+) и рядом игрок или бой. Для точного
        // ответа кластер отдаёт факт наличия чанка + чанк реально тикает.
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);
        if (!cluster.factoryChunks.contains(chunkKey)) return false;
        if (!world.isChunkLoaded(cx, cz)) return false;
        // Тикает ли чанк по-настоящему (не нашими руками, а у игрока) —
        // проверяем через отсутствие заморозки: упрощённо считаем, что
        // загруженный чанк фабрики с активным игроком рядом — тикающий.
        return isPlayerNear(world, cx, cz);
    }

    private static boolean isPlayerNear(ServerWorld world, int cx, int cz) {
        int sim = Math.max(2, world.getServer().getPlayerManager().getSimulationDistance());
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (Math.abs(player.getChunkPos().x - cx) <= sim
                    && Math.abs(player.getChunkPos().z - cz) <= sim) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public static MinecraftServer server() {
        return server;
    }

    // =====================================================================
    // Таблица виртуальной эмиссии (дормант-состояние L3)
    // =====================================================================

    /**
     * Статистическая эмиссия на тип машины, когда даже виртуальная нода
     * не тикает (L3): усреднённые значения из PollutionManager.
     */
    static final class VirtualEmissionTable {
        static float rateFor(String machineType) {
            if (machineType.endsWith("BurnerDrillBlockEntity")) return 0.70f;
            if (machineType.endsWith("ElectricDrillBlockEntity")) return 0.55f;
            if (machineType.endsWith("FactorioFurnaceBlockEntity")) return 0.55f;
            if (machineType.endsWith("BoilerBlockEntity")) return 1.10f;
            if (machineType.endsWith("AssemblerBlockEntity")) return 0.30f;
            if (machineType.contains("Pumpjack")) return 0.65f;
            if (machineType.contains("OilRefinery")) return 0.90f;
            if (machineType.contains("ChemicalPlant")) return 0.60f;
            return 0f;
        }
        private VirtualEmissionTable() {}
    }
}
