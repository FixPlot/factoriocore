package com.fixplot.factoriocore.content.simulation;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Map;

/**
 * Кластер фабрики: все наши машины в области одного супер-чанка (4x4 чанков).
 * Удерживает:
 *  - список чанков с нашими постройками (для тикетинга);
 *  - виртуальные ноды машин, симулируемые вне тикающих чанков;
 *  - ротацию duty-cycle: одновременно entity-ticking держим не больше
 *    {@code maxTickingChunks} чанков, по {@code dutyTicks} на чанк.
 */
public class FactoryCluster {

    public final long superChunkKey;
    public final RegistryKey<World> dimension;

    /** Чанки, в которых есть наши постройки (ключ ChunkPos.toLong). */
    public final LongSet factoryChunks = new LongOpenHashSet();
    /** Виртуальные ноды машин (ключ BlockPos.asLong). */
    public final Long2ObjectMap<VirtualMachineNode> nodes = new Long2ObjectOpenHashMap<>();
    /** Чанки, сейчас находящиеся на FACTORY_TICKING по ротации, с моментом выдачи. */
    private final Long2ObjectMap<Long> tickingSince = new Long2ObjectOpenHashMap<>();
    /** Кольцевой курсор ротации по чанкам. */
    private long rotationCursor = 0L;
    /** Последнее игровое время любой активности — для L3-дорманта. */
    public long lastActiveGameTime = 0L;

    public FactoryCluster(long superChunkKey, RegistryKey<World> dimension) {
        this.superChunkKey = superChunkKey;
        this.dimension = dimension;
    }

    public boolean isEmpty() {
        return factoryChunks.isEmpty() && nodes.isEmpty();
    }

    public void addMachine(ServerWorld world, BlockPos masterPos) {
        long chunkKey = ChunkPos.toLong(masterPos.getX() >> 4, masterPos.getZ() >> 4);
        if (factoryChunks.add(chunkKey)) {
            // Новый чанк фабрики — сразу уровень LOADED (без тика), тик выдаст ротация.
            FactoryTickets.setLoaded(world, masterPos.getX() >> 4, masterPos.getZ() >> 4, true);
        }
        lastActiveGameTime = world.getTime();
    }

    public boolean removeMachine(ServerWorld world, BlockPos masterPos) {
        long chunkKey = ChunkPos.toLong(masterPos.getX() >> 4, masterPos.getZ() >> 4);
        nodes.remove(masterPos.asLong());
        lastActiveGameTime = world.getTime();

        if (factoryChunks.contains(chunkKey) && !hasAnyMachinesInChunk(world, chunkKey)) {
            factoryChunks.remove(chunkKey);
            releaseChunkTickets(world, chunkKey);
            return true;
        }
        return false;
    }

    /**
     * Проверка остатка наших построек в чанке через скан по реальным блокам.
     * Дорогой (только при сносе!), вызывается из removeMachine.
     */
    private boolean hasAnyMachinesInChunk(ServerWorld world, long chunkKey) {
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);
        if (!world.isChunkLoaded(cx, cz)) {
            return !nodes.isEmpty(); // консервативно: не уверены — считаем, что есть
        }
        // Дешёвая проверка: BE чанка с нашим интерфейсом виртуальной симуляции
        // или любой BE не-ванильного типа.
        WorldChunk chunk = world.getChunk(cx, cz);
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            BlockEntity be = e.getValue();
            if (be instanceof VirtualSimulatable || GlobalFactoryManager.isFactoryBlockEntity(be)) {
                return true;
            }
        }
        return false;
    }

    private void releaseChunkTickets(ServerWorld world, long chunkKey) {
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);
        FactoryTickets.setTicking(world, cx, cz, false);
        FactoryTickets.setLoaded(world, cx, cz, false);
        tickingSince.remove(chunkKey);
    }

    public void releaseAllTickets(ServerWorld world) {
        for (long chunkKey : factoryChunks) {
            releaseChunkTickets(world, chunkKey);
        }
        tickingSince.clear();
    }

    public void reissueLoadedTickets(ServerWorld world) {
        // После рестарта сервера тикеты пропали — перевыдаём только LOADED.
        // Ticking выдаст ротация в первый же tickRotation.
        for (long chunkKey : factoryChunks) {
            FactoryTickets.setLoaded(world,
                    ChunkPos.getPackedX(chunkKey), ChunkPos.getPackedZ(chunkKey), true);
        }
    }

    /** Событие выгрузки чанка: снапшотим наши BE в виртуальные ноды. */
    public void captureVirtualNodes(ServerWorld world, WorldChunk chunk) {
        long now = world.getTime();
        for (Map.Entry<BlockPos, BlockEntity> e : chunk.getBlockEntities().entrySet()) {
            if (e.getValue() instanceof VirtualSimulatable) {
                // Нода создаётся даже без снапшота BE: catchUp при материализации
                // посчитает elapsed по lastSimTick.
                nodes.putIfAbsent(e.getKey().asLong(),
                        new VirtualMachineNode(e.getKey(), e.getValue().getClass().getName(), now));
                lastActiveGameTime = now;
            }
        }
    }

    /** Нода поглощена материализованным BE — удаляем из графа. */
    public void consumeVirtualNode(BlockPos pos) {
        nodes.remove(pos.asLong());
    }

    /**
     * Ротационный duty-cycle: выдаёт FACTORY_TICKING не более maxTickingChunks
     * чанкам на dutyTicks каждый. Вызывается из END_SERVER_TICK (каждый тик — дёшево).
     */
    public void rotateDuty(ServerWorld world, int maxTickingChunks, int dutyTicks) {
        long now = world.getTime();
        if (factoryChunks.isEmpty()) return;

        // 1. Снимаем истёкшие тикеты.
        LongOpenHashSet expired = null;
        for (Long2ObjectMap.Entry<Long> entry : tickingSince.long2ObjectEntrySet()) {
            if (now - entry.getValue() >= dutyTicks) {
                FactoryTickets.setTicking(world,
                        ChunkPos.getPackedX(entry.getLongKey()),
                        ChunkPos.getPackedZ(entry.getLongKey()), false);
                if (expired == null) expired = new LongOpenHashSet();
                expired.add(entry.getLongKey());
            }
        }
        if (expired != null) {
            for (long k : expired) tickingSince.remove(k);
        }

        // 2. Додаём тикеты до бюджета.
        if (tickingSince.size() >= maxTickingChunks || factoryChunks.isEmpty()) return;

        long[] chunks = factoryChunks.toLongArray();
        for (int i = 0; i < chunks.length && tickingSince.size() < maxTickingChunks; i++) {
            long chunkKey = chunks[(int) ((rotationCursor + i) % chunks.length)];
            if (tickingSince.containsKey(chunkKey)) continue;

            // Пропускаем чанки, которые и так тикают у игрока — не тратим бюджет.
            int cx = ChunkPos.getPackedX(chunkKey);
            int cz = ChunkPos.getPackedZ(chunkKey);
            if (world.isChunkLoaded(cx, cz) && isTickingByPlayer(world, chunkKey)) {
                continue;
            }

            FactoryTickets.setTicking(world, cx, cz, true);
            tickingSince.put((long) chunkKey, (Long) now);
        }
        rotationCursor += Math.max(1, Math.min(chunks.length, maxTickingChunks));
    }

    private boolean isTickingByPlayer(ServerWorld world, long chunkKey) {
        // Чанк в simulation distance игрока уже тикает — грубая проверка по расстоянию.
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);
        for (var player : world.getPlayers()) {
            int sim = Math.max(2, player.getServerWorld().getServer().getPlayerManager().getSimulationDistance());
            if (Math.abs(player.getChunkPos().x - cx) <= sim && Math.abs(player.getChunkPos().z - cz) <= sim) {
                return true;
            }
        }
        return false;
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("SuperChunk", superChunkKey);
        nbt.putString("Dim", dimension.getValue().toString());
        long[] chunks = factoryChunks.toLongArray();
        nbt.putLongArray("Chunks", chunks);
        NbtList nodeList = new NbtList();
        for (VirtualMachineNode node : nodes.values()) {
            nodeList.add(node.writeNbt(new NbtCompound()));
        }
        nbt.put("Nodes", nodeList);
        nbt.putLong("LastActive", lastActiveGameTime);
        return nbt;
    }

    public static FactoryCluster fromNbt(NbtCompound nbt, RegistryKey<World> fallbackDim) {
        long key = nbt.getLong("SuperChunk");
        FactoryCluster c = new FactoryCluster(key, fallbackDim);
        for (long ck : nbt.getLongArray("Chunks")) {
            c.factoryChunks.add(ck);
        }
        if (nbt.contains("Nodes", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Nodes", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                VirtualMachineNode node = VirtualMachineNode.readNbt(list.getCompound(i));
                c.nodes.put(node.pos.asLong(), node);
            }
        }
        c.lastActiveGameTime = nbt.getLong("LastActive");
        return c;
    }
}
