package com.fixplot.factoriocore.content.simulation;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;

import java.util.Comparator;

/**
 * Менеджер фоновых чанков фабрики (PersistentState).
 *
 * Задача: фабрика продолжает симулироваться, когда игрок ушёл, но БЕЗ
 * ванильной нагрузки entity-ticking (спавн мобов, AI, random ticks,
 * распространение травы/огня) — иначе сотни форснутых чанков кладут TPS.
 *
 * Механика уровней тикетов в 1.21.1 (меньше = сильнее):
 *   31 — ChunkLevelType.ENTITY_TICKING  (мобы + random ticks) — НЕ используется;
 *   32 — ChunkLevelType.BLOCK_TICKING   (BE + random ticks, БЕЗ тика сущностей)
 *         — базовый уровень для чанков фабрики: печи/бойлеры/инсертеры работают,
 *           ванильные мобы не тикают и не спавнятся;
 *   33 — ChunkLevelType.FULL (полный чанк в памяти, без каких-либо тиков)
 *         — эконом-уровень для «холодных» чанков фабрики (склады, дальние зоны).
 *
 * Уровень выдаётся в addTicket как «расстояние»: для уровня загрузки L
 * тикет добавляется с level = L (меньше значение — выше приоритет тикания;
 * фактический тип чанка определяется итоговым минимальным уровнем).
 *
 * Персистентность: список чанков хранится в NBT через PersistentState —
 * после рестарта сервера тикеты перевыдаются в onWorldLoad (тикаеты сами по
 * себе НЕ переживают рестарт, переживает только наш список).
 */
public class FactorioChunkManager extends PersistentState {

    public static final String KEY = "factoriocore_factory_chunks";

    /** BLOCK_TICKING: фабрика тикает, ванильные мобы — нет. */
    public static final int LEVEL_FACTORY_TICKING = 32;
    /** FULL: чанк в памяти без тиков — эконом-режим. */
    public static final int LEVEL_FACTORY_LOADED = 33;

    /** Кастомный тип тикета: expiry 0 = никогда не истекает сам по себе. */
    public static final ChunkTicketType<ChunkPos> FACTORY_TICKING_NO_ENTITIES =
            ChunkTicketType.create("factoriocore:factory_ticking_no_entities",
                    Comparator.comparingLong(ChunkPos::toLong));

    /** Тикеты уровня FULL (холодные чанки). */
    public static final ChunkTicketType<ChunkPos> FACTORY_COLD_LOADED =
            ChunkTicketType.create("factoriocore:factory_cold_loaded",
                    Comparator.comparingLong(ChunkPos::toLong));

    /** Зарегистрированные чанки фабрики: ключ ChunkPos.toLong -> уровень (32 или 33). */
    private final Long2IntMap managedChunks = new Long2IntOpenHashMap();
    /** Чанки, которым тикет уже выдан в этом запуске сервера. */
    private final LongSet issuedThisSession = new LongOpenHashSet();

    public FactorioChunkManager() {
    }

    public static FactorioChunkManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    private static final Type<FactorioChunkManager> TYPE =
            new Type<>(FactorioChunkManager::new, FactorioChunkManager::fromNbt, null);

    public static FactorioChunkManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        FactorioChunkManager m = new FactorioChunkManager();
        if (nbt.contains("Chunks", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Chunks", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                m.managedChunks.put(c.getLong("K"), c.getInt("L"));
            }
        }
        return m;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        NbtList list = new NbtList();
        for (Long2IntMap.Entry e : managedChunks.long2IntEntrySet()) {
            NbtCompound c = new NbtCompound();
            c.putLong("K", e.getLongKey());
            c.putInt("L", e.getIntValue());
            list.add(c);
        }
        nbt.put("Chunks", list);
        return nbt;
    }

    // =====================================================================
    // Публичный API
    // =====================================================================

    /**
     * Регистрирует чанк фабрики и сразу выдаёт тикет. active = true -> уровень 32
     * (BE-тики), false -> уровень 33 (просто держим в памяти).
     */
    public void addFactoryChunk(ServerWorld world, int cx, int cz, boolean active) {
        long key = ChunkPos.toLong(cx, cz);
        int level = active ? LEVEL_FACTORY_TICKING : LEVEL_FACTORY_LOADED;
        int prev = managedChunks.getOrDefault(key, Integer.MAX_VALUE);

        if (prev != level) {
            // Уровень меняется: снимаем старый тикет, если он был выдан.
            if (prev != Integer.MAX_VALUE && issuedThisSession.contains(key)) {
                removeTicket(world, key, prev);
            }
            managedChunks.put(key, level);
            issueTicket(world, key, level);
            markDirty();
        } else if (!issuedThisSession.contains(key)) {
            // Уровень тот же, но тикет ещё не выдан (например, после рестарта).
            issueTicket(world, key, level);
            markDirty();
        }
    }

    /** Снимает регистрацию чанка и его тикет. */
    public void removeFactoryChunk(ServerWorld world, int cx, int cz) {
        long key = ChunkPos.toLong(cx, cz);
        if (!managedChunks.containsKey(key)) return;
        int level = managedChunks.get(key);
        managedChunks.remove(key);
        if (issuedThisSession.contains(key)) {
            removeTicket(world, key, level);
        }
        markDirty();
    }

    /** Чанк сейчас зарегистрирован? */
    public boolean isManaged(int cx, int cz) {
        return managedChunks.containsKey(ChunkPos.toLong(cx, cz));
    }

    public int getManagedChunkCount() {
        return managedChunks.size();
    }

    /** Уровень чанка (32/33) или -1, если не зарегистрирован. */
    public int getLevel(int cx, int cz) {
        return managedChunks.getOrDefault(ChunkPos.toLong(cx, cz), -1);
    }

    /**
     * Выдача всех сохранённых тикетов — вызывается при загрузке мира
     * (ServerWorldEvents.LOAD): тикеты не переживают рестарт, список — да.
     */
    public void reissueAllTickets(ServerWorld world) {
        for (Long2IntMap.Entry e : managedChunks.long2IntEntrySet()) {
            issueTicket(world, e.getLongKey(), e.getIntValue());
        }
    }

    /** Снятие всех тикетов сессии — вызывается при выгрузке мира. */
    public void removeAllTickets(ServerWorld world) {
        for (Long2IntMap.Entry e : managedChunks.long2IntEntrySet()) {
            if (issuedThisSession.contains(e.getLongKey())) {
                removeTicket(world, e.getLongKey(), e.getIntValue());
            }
        }
        issuedThisSession.clear();
    }

    // =====================================================================
    // Внутреннее
    // =====================================================================

    private void issueTicket(ServerWorld world, long key, int level) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos pos = new ChunkPos(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        ChunkTicketType<ChunkPos> type = (level <= LEVEL_FACTORY_TICKING)
                ? FACTORY_TICKING_NO_ENTITIES : FACTORY_COLD_LOADED;
        cm.addTicket(type, pos, level, pos);
        issuedThisSession.add(key);
    }

    private void removeTicket(ServerWorld world, long key, int level) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos pos = new ChunkPos(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
        ChunkTicketType<ChunkPos> type = (level <= LEVEL_FACTORY_TICKING)
                ? FACTORY_TICKING_NO_ENTITIES : FACTORY_COLD_LOADED;
        cm.removeTicket(type, pos, level, pos);
        issuedThisSession.remove(key);
    }
}
