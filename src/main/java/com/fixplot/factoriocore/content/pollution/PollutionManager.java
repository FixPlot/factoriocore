package com.fixplot.factoriocore.content.pollution;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.fluids.BoilerBlock;
import com.fixplot.factoriocore.content.fluids.BoilerBlockEntity;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlock;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.network.SyncPollutionPayload;
import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import com.fixplot.factoriocore.worldgen.NauvisConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.*;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PollutionManager {

    public static final float MAX_CHUNK_POLLUTION = 500.0f;

    // Машин рядом с игроком: их эмиссию считаем из живых BE (текущие чанки).
    private static final Map<RegistryKey<World>, Set<BlockPos>> ACTIVE_MACHINES = new ConcurrentHashMap<>();

    private static final Map<Long, Float> CLIENT_POLLUTION_CACHE = new ConcurrentHashMap<>();
    private static MinecraftServer loadedServer = null;

    public static Set<BlockPos> getActiveMachines(World world) {
        return ACTIVE_MACHINES.computeIfAbsent(world.getRegistryKey(), k -> ConcurrentHashMap.newKeySet());
    }

    /**
     * Доступ к глобальной виртуальной сетке загрязнения (источник истины
     * для серверной симуляции). Ключи — ChunkPos.toLong.
     */
    public static com.fixplot.factoriocore.content.simulation.VirtualPollutionGrid grid(ServerWorld world) {
        return com.fixplot.factoriocore.content.simulation.SimulationState.get(world).pollution;
    }

    /**
     * Регистрирует постройку завода в глобальной симуляции: кластер
     * GlobalFactoryManager получает машину, чанк берёт LOADED-тикет (level 33,
     * без entity-ticking), а entity-ticking выдаётся ротационно с бюджетом.
     */
    public static void registerFactoryBlock(ServerWorld world, BlockPos rawPos, BlockState state) {
        if (world == null || rawPos == null) return;
        BlockPos masterPos = BlockHealthManager.getCanonicalPos(rawPos, state);
        Block block = state.getBlock();

        if (isPollutingMachine(block)) {
            getActiveMachines(world).add(masterPos.toImmutable());
        }

        if (BlockHealthManager.getMaxHealth(block) > 0) {
            com.fixplot.factoriocore.content.simulation.GlobalFactoryManager
                    .onFactoryBlockAdded(world, masterPos);
        }
    }

    /**
     * Вызывается при сносе постройки: кластер снимает тикеты, если в чанке
     * больше не осталось построек завода.
     */
    public static void unregisterFactoryBlock(ServerWorld world, BlockPos masterPos) {
        if (world == null || masterPos == null) return;
        getActiveMachines(world).remove(masterPos);
        com.fixplot.factoriocore.content.simulation.GlobalFactoryManager
                .onFactoryBlockRemoved(world, masterPos);
    }

    public static boolean isPollutingMachine(Block block) {
        return block instanceof BurnerDrillBlock
                || block instanceof ElectricDrillBlock
                || block instanceof FactorioFurnaceBlock
                || block instanceof BoilerBlock
                || block instanceof AssemblerBlock
                || block instanceof OilMachines.PumpjackBlock
                || block instanceof OilMachines.OilRefineryBlock
                || block instanceof OilMachines.ChemicalPlantBlock;
    }

    public static void emitPollution(World world, BlockPos pos, float amount) {
        if (world == null || world.isClient() || amount <= 0.0f || !NauvisConfig.ACTIVE.pollutionEnabled) return;
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        // Источник истины — VirtualPollutionGrid в SimulationState.
        if (world instanceof ServerWorld sw) {
            com.fixplot.factoriocore.content.simulation.SimulationState.get(sw)
                    .pollution.emit(chunkKey, amount);
        }
    }

    /** Доступ к глобальной сетке для серверных вызовов. */
    public static float getGridPollution(ServerWorld world, long chunkKey) {
        return com.fixplot.factoriocore.content.simulation.SimulationState.get(world)
                .pollution.get(chunkKey);
    }

    public static float getPollutionAt(World world, BlockPos pos) {
        long key = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        if (world.isClient()) {
            return CLIENT_POLLUTION_CACHE.getOrDefault(key, 0.0f);
        }
        if (world instanceof ServerWorld sw) {
            return com.fixplot.factoriocore.content.simulation.SimulationState.get(sw)
                    .pollution.get(key);
        }
        return 0.0f;
    }

    public static float getClientChunkPollution(int chunkX, int chunkZ) {
        return CLIENT_POLLUTION_CACHE.getOrDefault(ChunkPos.toLong(chunkX, chunkZ), 0.0f);
    }

    public static float getClientWaterTintFactor(int blockX, int blockZ) {
        float fx = (blockX / 16.0f) - 0.5f;
        float fz = (blockZ / 16.0f) - 0.5f;
        int cx0 = MathHelper.floor(fx);
        int cz0 = MathHelper.floor(fz);
        float tx = fx - cx0;
        float tz = fz - cz0;

        float p00 = getEffectiveWaterPollution(cx0, cz0);
        float p10 = getEffectiveWaterPollution(cx0 + 1, cz0);
        float p01 = getEffectiveWaterPollution(cx0, cz0 + 1);
        float p11 = getEffectiveWaterPollution(cx0 + 1, cz0 + 1);

        float px0 = MathHelper.lerp(tx, p00, p10);
        float px1 = MathHelper.lerp(tx, p01, p11);
        float poll = MathHelper.lerp(tz, px0, px1);

        if (poll < 0.8f) return 0.0f;
        return MathHelper.clamp((poll - 0.8f) / 18.0f, 0.0f, 1.0f);
    }

    private static float getEffectiveWaterPollution(int cx, int cz) {
        float direct = getClientChunkPollution(cx, cz);
        float maxNeighbor = 0.0f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                float np = getClientChunkPollution(cx + dx, cz + dz);
                float weight = (dx == 0 || dz == 0) ? 0.55f : 0.35f;
                maxNeighbor = Math.max(maxNeighbor, np * weight);
            }
        }
        return Math.max(direct, maxNeighbor);
    }

    public static Map<Long, Float> getClientCache() {
        return CLIENT_POLLUTION_CACHE;
    }

    public static void updateClientCache(Map<Long, Float> syncedMap) {
        CLIENT_POLLUTION_CACHE.clear();
        CLIENT_POLLUTION_CACHE.putAll(syncedMap);
    }

    public static void clearClientCache() {
        CLIENT_POLLUTION_CACHE.clear();
    }

    public static void tickServerWorld(ServerWorld world) {
        MinecraftServer server = world.getServer();
        if (loadedServer != server && world.getRegistryKey() == World.OVERWORLD) {
            loadedServer = server;
            // Миграция: один раз после перехода на SimulationState переносим
            // старый факториокор-файл, если PersistentState пуст.
            migrateLegacySimulationFile(world);
        }

        long time = world.getTime();

        // 1. Раз в 10 секунд автоматически подхватываем станки рядом с игроком
        //    (на случай построек, размещённых до включения глобальной симуляции).
        if (time % 200 == 0) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                discoverExistingMachinesNearPlayer(world, player.getBlockPos(), 24);
            }
        }

        // 2. Эмиссия машин рядом с игроком (в тикающих чанках) — раз в 20 тиков.
        //    Дальние машины эмиссирует GlobalFactoryManager через VirtualPollutionGrid.
        if (time % 20 == 0 && NauvisConfig.ACTIVE.pollutionEnabled) {
            tickGlobalRegisteredMachines(world);
        }

        // 3. Диффузия, поглощение, гнёзда, кусаки, эволюция, клиентская синхронизация
        //    — всё в GlobalFactoryManager.tickServer (END_SERVER_TICK). Здесь не дублируем.
    }

    private static void discoverExistingMachinesNearPlayer(ServerWorld world, BlockPos center, int radius) {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx += 2) {
            for (int dz = -radius; dz <= radius; dz += 2) {
                for (int dy = -3; dy <= 3; dy++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = world.getBlockState(pos);
                    if (!state.isAir() && BlockHealthManager.getMaxHealth(state.getBlock()) > 0) {
                        registerFactoryBlock(world, pos, state);
                    }
                }
            }
        }
    }

    private static void tickGlobalRegisteredMachines(ServerWorld world) {
        Set<BlockPos> machines = getActiveMachines(world);
        if (machines.isEmpty()) return;

        var simState = com.fixplot.factoriocore.content.simulation.SimulationState.get(world);
        Iterator<BlockPos> it = machines.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (!world.isChunkLoaded(cx, cz)) {
                // Дальний чанк: НЕ форсим (это делала старая версия, кладя TPS).
                // Эмиссию даёт VirtualPollutionGrid из VirtualEmissionTable
                // в GlobalFactoryManager.tickVirtualMachineEmissions.
                continue;
            }

            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (!isPollutingMachine(block)) {
                it.remove();
                continue;
            }

            if (block instanceof BurnerDrillBlock && world.getBlockEntity(pos) instanceof BurnerDrillBlockEntity drill) {
                if (drill.isWorking()) {
                    emitPollution(world, pos, 0.70f);
                    spawnChimneySmoke(world, pos.getX() + 0.5, pos.getY() + 2.55, pos.getZ() + 0.5, 1);
                }
            } else if (block instanceof ElectricDrillBlock && world.getBlockEntity(pos) instanceof ElectricDrillBlockEntity edrill) {
                if (edrill.isActivelyMining()) {
                    emitPollution(world, pos, 0.55f);
                }
            } else if (block instanceof FactorioFurnaceBlock furnaceBlock && world.getBlockEntity(pos) instanceof FactorioFurnaceBlockEntity furnace) {
                if (furnace.isBurning()) {
                    float emission = (furnaceBlock.getTier() == 1) ? 0.40f : 0.75f;
                    emitPollution(world, pos, emission);
                    spawnChimneySmoke(world, pos.getX() + 0.5, pos.getY() + 2.6, pos.getZ() + 0.5, 1);
                }
            } else if (block instanceof BoilerBlock && world.getBlockEntity(pos) instanceof BoilerBlockEntity boiler) {
                if (boiler.isBurning()) {
                    emitPollution(world, pos, 1.10f * boiler.getLoadRatio());
                    spawnChimneySmoke(world, pos.getX() + 0.5, pos.getY() + 2.6, pos.getZ() + 0.5, 2);
                }
            } else if (block instanceof AssemblerBlock && world.getBlockEntity(pos) instanceof AssemblerBlockEntity asm) {
                if (asm.isWorking()) {
                    emitPollution(world, pos, 0.30f);
                }
            } else if (block instanceof OilMachines.PumpjackBlock && world.getBlockEntity(pos) instanceof OilMachines.PumpjackBlockEntity pj) {
                if (pj.isWorking()) {
                    emitPollution(world, pos, 0.65f);
                }
            } else if (block instanceof OilMachines.OilRefineryBlock && world.getBlockEntity(pos) instanceof OilMachines.OilRefineryBlockEntity ref) {
                if (ref.isWorking()) {
                    emitPollution(world, pos, 0.90f);
                    spawnChimneySmoke(world, pos.getX() + 0.5, pos.getY() + 2.75, pos.getZ() + 0.5, 2);
                }
            } else if (block instanceof OilMachines.ChemicalPlantBlock && world.getBlockEntity(pos) instanceof OilMachines.ChemicalPlantBlockEntity cp) {
                if (cp.isWorking()) {
                    emitPollution(world, pos, 0.60f);
                    spawnChimneySmoke(world, pos.getX() + 0.5, pos.getY() + 2.25, pos.getZ() + 0.5, 1);
                }
            }
        }
    }

    private static void spawnChimneySmoke(ServerWorld world, double x, double y, double z, int count) {
        world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, count, 0.08, 0.10, 0.08, 0.012);
    }

    /**
     * Одноразовая миграция legacy-файла factoriocore_simulation.nbt в
     * SimulationState (PersistentState). Выполняется на первом тике нового
     * сервера, если PersistentState ещё пуст. После переноса файл переименовывается
     * в .migrated (не удаляется — безопасность отката).
     */
    private static void migrateLegacySimulationFile(ServerWorld world) {
        try {
            var simState = com.fixplot.factoriocore.content.simulation.SimulationState.get(world);
            if (!simState.pollution.raw().isEmpty() || !simState.clusters.isEmpty()) {
                return; // PersistentState уже наполнен (не первая загрузка)
            }

            Path dataDir = world.getServer().getSavePath(WorldSavePath.ROOT).resolve("data");
            File file = dataDir.resolve("factoriocore_simulation.nbt").toFile();
            if (!file.exists()) return;

            NbtCompound root = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());

            // Загрязнение -> сетка.
            if (root.contains("Pollution", NbtElement.LIST_TYPE)) {
                NbtList list = root.getList("Pollution", NbtElement.COMPOUND_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound c = list.getCompound(i);
                    simState.pollution.emit(c.getLong("K"), c.getFloat("V"));
                }
            }

            // Активные машины -> кластеры (чанк каждой машины получает LOADED-тикет).
            Set<BlockPos> machines = getActiveMachines(world);
            machines.clear();
            if (root.contains("ActiveMachines", NbtElement.LIST_TYPE)) {
                NbtList list = root.getList("ActiveMachines", NbtElement.LONG_TYPE);
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) instanceof NbtLong nl) {
                        BlockPos p = BlockPos.fromLong(nl.longValue());
                        machines.add(p);
                        com.fixplot.factoriocore.content.simulation.GlobalFactoryManager
                                .onFactoryBlockAdded(world, p);
                    }
                }
            }

            simState.markDirty();
            file.renameTo(dataDir.resolve("factoriocore_simulation.nbt.migrated").toFile());
        } catch (Exception ignored) {
        }
    }
}