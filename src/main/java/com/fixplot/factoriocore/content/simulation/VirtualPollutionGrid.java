package com.fixplot.factoriocore.content.simulation;

import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import com.fixplot.factoriocore.worldgen.NauvisConfig;
import it.unimi.dsi.fastutil.longs.Long2FloatMap;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.util.Map;

/**
 * Глобальная сетка загрязнения на чанковом уровне — БЕЗ обращения к вокселям
 * мира для выгруженных чанков. Поглощение лесом в тумане войны считается
 * аналитически по детерминированному шуму NauvisChunkGenerator (мир не нужен).
 *
 * Перенос и усиление PollutionManager:
 *  - пул атак гнёзд живёт рядом с сеткой (одна точка истины);
 *  - дальнее распространение ускоряется иерархической диффузией (шаг
 *    супер-чанка = 4 шага обычной свёртки по расстоянию).
 */
public class VirtualPollutionGrid {

    public static final float MAX_CHUNK_POLLUTION = 500.0f;
    private static final float BASE_DISSIPATION = 0.05f;
    private static final float DIRTY_THRESHOLD = 0.15f;

    /** Значение загрязнения чанка (ключ ChunkPos.toLong). */
    private final Long2FloatMap pollution = new Long2FloatOpenHashMap();
    /** Пул атаки гнезда в чанке: копится из поглощённого гнездом загрязнения. */
    private final Long2FloatMap nestPools = new Long2FloatOpenHashMap();
    /** Загрязнение, изменившееся с последней клиентской синхронизации. */
    private final Long2FloatOpenHashMap dirtyForSync = new Long2FloatOpenHashMap();

    public float get(long chunkKey) {
        return pollution.get(chunkKey);
    }

    public void emit(long chunkKey, float amount) {
        if (amount <= 0f) return;
        float current = pollution.get(chunkKey);
        float next = Math.min(MAX_CHUNK_POLLUTION, current + amount);
        pollution.put(chunkKey, next);
        dirtyForSync.put(chunkKey, next);
    }

    /**
     * Отбор загрязнения гнездом-спавнером (BiterSpawnerBlockEntity): уменьшает
     * значение чанка на amount (но не в минус), возвращает фактически съеденное.
     * Сетку кормит физический спавнер в тикающем чанке — работает и с
     * чистой сеткой (никаких обращений к миру).
     */
    public float drainForNest(long chunkKey, float amount) {
        if (amount <= 0f) return 0f;
        float current = pollution.get(chunkKey);
        if (current <= 0f) return 0f;
        float drained = Math.min(current, amount);
        float next = current - drained;
        if (next < DIRTY_THRESHOLD) {
            pollution.remove(chunkKey);
            dirtyForSync.put(chunkKey, 0f);
        } else {
            pollution.put(chunkKey, next);
            dirtyForSync.put(chunkKey, next);
        }
        return drained;
    }

    public Map<Long, Float> getNestPools() {
        return nestPools;
    }

    public Long2FloatMap raw() {
        return pollution;
    }

    /**
     * Шаг симуляции: вызывается раз в 40 тиков (2 с) из SimulationTicker.
     * Диффузия + поглощение + питание пулов гнёзд. Тот же алгоритм, что был
     * в PollutionManager.stepDiffusionAndAbsorption, с иерархическим усилением.
     */
    public void step(ServerWorld world) {
        if (pollution.isEmpty()) return;
        if (!NauvisConfig.ACTIVE.pollutionEnabled) {
            pollution.clear();
            nestPools.clear();
            dirtyForSync.clear();
            return;
        }

        float absorbMod = Math.max(0.1f, NauvisConfig.ACTIVE.pollutionAbsorptionMod);
        float diffusionRate = MathHelper.clamp(NauvisConfig.ACTIVE.pollutionDiffusionPercent / 100.0f, 0.0f, 0.20f) * 3.0f;
        Long2FloatMap next = new Long2FloatOpenHashMap(pollution.size());

        for (Long2FloatMap.Entry entry : pollution.long2FloatEntrySet()) {
            long key = entry.getLongKey();
            float val = entry.getFloatValue();
            if (val < DIRTY_THRESHOLD) continue;

            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);

            float absorbed = BASE_DISSIPATION * absorbMod;
            if (world.isChunkLoaded(cx, cz)) {
                absorbed += sampleLoadedChunkAbsorption(world, cx, cz, val) * absorbMod;
            } else {
                // Аналитика по шуму: мир НЕ загружается.
                int midX = (cx << 4) + 8;
                int midZ = (cz << 4) + 8;
                double elev = NauvisChunkGenerator.evaluateElevation(midX, midZ);
                if (NauvisChunkGenerator.hasForestAt(midX, midZ, elev)) {
                    absorbed += 0.18f * absorbMod;
                }
            }

            // Гнёзда кусак поглощают и копят пул рейда (даже в выгруженных чанках).
            int centerX = (cx << 4) + 8;
            int centerZ = (cz << 4) + 8;
            double centerElev = NauvisChunkGenerator.evaluateElevation(centerX, centerZ);
            if (!NauvisConfig.ACTIVE.peacefulMode
                    && NauvisConfig.hasEnemyBasePreviewAt(centerX, centerZ, centerElev)) {
                float nestAbsorb = Math.min(val * 0.35f, 4.5f);
                absorbed += nestAbsorb;
                nestPools.merge(key, nestAbsorb, Float::sum);
            }

            float remaining = Math.max(0.0f, val - absorbed);
            if (remaining <= 0.1f) {
                dirtyForSync.put(key, 0f);
                continue;
            }

            if (remaining > 3.0f && diffusionRate > 0.001f) {
                float spreadEach = remaining * diffusionRate;
                float stayed = remaining - (spreadEach * 4.0f);
                next.merge(key, stayed, Float::sum);

                next.merge(ChunkPos.toLong(cx + 1, cz), spreadEach, (a, b) -> Math.min(MAX_CHUNK_POLLUTION, a + b));
                next.merge(ChunkPos.toLong(cx - 1, cz), spreadEach, (a, b) -> Math.min(MAX_CHUNK_POLLUTION, a + b));
                next.merge(ChunkPos.toLong(cx, cz + 1), spreadEach, (a, b) -> Math.min(MAX_CHUNK_POLLUTION, a + b));
                next.merge(ChunkPos.toLong(cx, cz - 1), spreadEach, (a, b) -> Math.min(MAX_CHUNK_POLLUTION, a + b));
            } else {
                next.merge(key, remaining, Float::sum);
            }
        }

        pollution.clear();
        for (Long2FloatMap.Entry e : next.long2FloatEntrySet()) {
            if (e.getFloatValue() >= DIRTY_THRESHOLD) {
                float clamped = MathHelper.clamp(e.getFloatValue(), 0.0f, MAX_CHUNK_POLLUTION);
                pollution.put(e.getLongKey(), clamped);
                dirtyForSync.put(e.getLongKey(), clamped);
            }
        }

        // Иерархическое ускорение дальнего хвоста: раз в 4-й шаг — «прыжок»
        // супер-чанков (дальний фронт двигается с 4x скоростью, локально — точно).
        if ((world.getTime() / 40) % 4 == 0) {
            stepSuperChunkDiffusion();
        }
    }

    /** Супер-чанковая диффузия: агрегаты 4x4 обмениваются загрязнением через границы. */
    private void stepSuperChunkDiffusion() {
        Long2FloatMap superMap = new Long2FloatOpenHashMap();
        for (Long2FloatMap.Entry e : pollution.long2FloatEntrySet()) {
            long superKey = superKey(e.getLongKey());
            superMap.merge(superKey, e.getFloatValue(), Float::sum);
        }
        if (superMap.isEmpty() || superMap.size() < 2) return;

        Long2FloatMap superNext = new Long2FloatOpenHashMap(superMap.size());
        float rate = 0.08f;
        for (Long2FloatMap.Entry e : superMap.long2FloatEntrySet()) {
            long sk = e.getLongKey();
            float v = e.getFloatValue();
            int scx = ChunkPos.getPackedX(sk);
            int scz = ChunkPos.getPackedZ(sk);
            // Двигаем только агрегаты-«фронты»: у соседей меньше — туда и течёт.
            long right = ChunkPos.toLong(scx + 1, scz);
            long left = ChunkPos.toLong(scx - 1, scz);
            long up = ChunkPos.toLong(scx, scz + 1);
            long down = ChunkPos.toLong(scx, scz - 1);
            float spread = v * rate;
            if (spread > 0.05f) {
                superNext.merge(sk, v - spread * 2f, Float::sum);
                superNext.merge(right, spread * 0.5f, Float::sum);
                superNext.merge(left, spread * 0.5f, Float::sum);
                superNext.merge(up, spread * 0.5f, Float::sum);
                superNext.merge(down, spread * 0.5f, Float::sum);
            } else {
                superNext.merge(sk, v, Float::sum);
            }
        }

        // Раскладываем дельту супер-чанков обратно в чанки (равномерно по 16).
        Long2FloatMap delta = new Long2FloatOpenHashMap();
        for (Long2FloatMap.Entry e : superNext.long2FloatEntrySet()) {
            long sk = e.getLongKey();
            float newVal = e.getFloatValue();
            float oldVal = superMap.get(sk);
            float diff = newVal - oldVal;
            if (Math.abs(diff) < 0.01f) continue;
            int scx = ChunkPos.getPackedX(sk);
            int scz = ChunkPos.getPackedZ(sk);
            float perChunk = diff / 16.0f;
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    long ck = ChunkPos.toLong(scx * 4 + dx, scz * 4 + dz);
                    if (pollution.containsKey(ck) || perChunk > 0f) {
                        delta.merge(ck, perChunk, Float::sum);
                    }
                }
            }
        }
        for (Long2FloatMap.Entry e : delta.long2FloatEntrySet()) {
            long ck = e.getLongKey();
            float v = pollution.get(ck) + e.getFloatValue();
            v = MathHelper.clamp(v, 0f, MAX_CHUNK_POLLUTION);
            if (v >= DIRTY_THRESHOLD) {
                pollution.put(ck, v);
                dirtyForSync.put(ck, v);
            } else if (pollution.containsKey(ck)) {
                pollution.remove(ck);
                dirtyForSync.put(ck, 0f);
            }
        }
    }

    private static long superKey(long chunkKey) {
        return ChunkPos.toLong(ChunkPos.getPackedX(chunkKey) >> 2, ChunkPos.getPackedZ(chunkKey) >> 2);
    }

    /**
     * Поглощение в ЗАГРУЖЕННОМ чанке — сэмпл листвы/травы (перенос из
     * PollutionManager.sampleChunkNatureAbsorption, включая урон деревьям).
     */
    private float sampleLoadedChunkAbsorption(ServerWorld world, int cx, int cz, float chunkPollution) {
        float bonusAbsorption = 0.0f;
        int startX = cx << 4;
        int startZ = cz << 4;
        int minTreeDmg = NauvisConfig.ACTIVE.pollutionMinTreeDamage;

        for (int i = 0; i < 2; i++) {
            int rx = startX + world.random.nextInt(16);
            int rz = startZ + world.random.nextInt(16);
            int topY = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, rx, rz) - 1;
            if (topY <= world.getBottomY()) continue;

            var surfacePos = new net.minecraft.util.math.BlockPos(rx, topY, rz);
            var state = world.getBlockState(surfacePos);

            if (state.isIn(net.minecraft.registry.tag.BlockTags.LEAVES)) {
                bonusAbsorption += 0.12f;
                if (chunkPollution > minTreeDmg && world.random.nextFloat() < 0.02f) {
                    world.breakBlock(surfacePos, false);
                    bonusAbsorption += NauvisConfig.ACTIVE.pollutionAbsorbedPerTree * 0.25f;
                }
            } else if (state.isOf(net.minecraft.block.Blocks.GRASS_BLOCK)) {
                bonusAbsorption += 0.03f;
                if (chunkPollution > (minTreeDmg * 2.5f) && world.random.nextFloat() < 0.015f) {
                    var withered = world.random.nextBoolean()
                            ? net.minecraft.block.Blocks.COARSE_DIRT.getDefaultState()
                            : net.minecraft.block.Blocks.PODZOL.getDefaultState();
                    world.setBlockState(surfacePos, withered, net.minecraft.block.Block.NOTIFY_LISTENERS);
                }
            }
        }
        return bonusAbsorption;
    }

    /** Дельта для клиентской синхронизации (только изменившиеся чанки). */
    public Long2FloatOpenHashMap drainDirtyForSync() {
        if (dirtyForSync.isEmpty()) return null;
        Long2FloatOpenHashMap copy = new Long2FloatOpenHashMap(dirtyForSync);
        dirtyForSync.clear();
        return copy;
    }

    public void clear() {
        pollution.clear();
        nestPools.clear();
        dirtyForSync.clear();
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList pollList = new NbtList();
        for (Long2FloatMap.Entry e : pollution.long2FloatEntrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("K", e.getLongKey());
            c.putFloat("V", e.getFloatValue());
            pollList.add(c);
        }
        nbt.put("Pollution", pollList);

        NbtList nestList = new NbtList();
        for (Long2FloatMap.Entry e : nestPools.long2FloatEntrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("K", e.getLongKey());
            c.putFloat("V", e.getFloatValue());
            nestList.add(c);
        }
        nbt.put("NestPools", nestList);
        return nbt;
    }

    public void readNbt(NbtCompound nbt) {
        pollution.clear();
        nestPools.clear();
        dirtyForSync.clear();
        if (nbt.contains("Pollution", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Pollution", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                pollution.put(c.getLong("K"), c.getFloat("V"));
            }
        }
        if (nbt.contains("NestPools", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("NestPools", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                nestPools.put(c.getLong("K"), c.getFloat("V"));
            }
        }
    }
}
