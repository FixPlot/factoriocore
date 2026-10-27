package com.fixplot.factoriocore.content.combat.biters;

import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.simulation.SimulationState;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity гнезда кусак.
 *
 * Тик (каждые 20 тиков, только сервер):
 *  1. «Пожирает» загрязнение из VirtualPollutionGrid своего чанка: уменьшает
 *     Float-значение сетки на drainRate, НЕ загружая соседние чанки.
 *  2. Конвертирует съеденное в spawn_charge (внутренний буфер).
 *  3. При накоплении заряда до порога — НЕ спавнит моба физически, а
 *     регистрирует штурмовую единицу в пуле ближайшего отряда
 *     BiterAttackDirector (FeedFactor в виртуальную группу).
 */
public class BiterSpawnerBlockEntity extends BlockEntity {

    /** Заряд на одну штурмовую единицу (в единицах съеденного загрязнения). */
    public static final float CHARGE_PER_UNIT = 12.0f;

    /** Внутренний буфер заряда спавна. */
    private float spawnCharge = 0.0f;
    /** Сколько единиц заспавнено этим гнездом (статистика). */
    private long unitsEmitted = 0L;

    public BiterSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BITER_SPAWNER_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, BiterSpawnerBlockEntity entity) {
        if (world.isClient || !(world instanceof ServerWorld serverWorld)) return;

        // Основной цикл гнезда — раз в секунду.
        if ((world.getTime() % 20L) != 0L) return;

        entity.tickSpawner(serverWorld);
    }

    private void tickSpawner(ServerWorld world) {
        var cfg = FactorioConfig.INSTANCE;

        // 1. Пожираем загрязнение из сетки (свой чанк, без загрузки мира).
        SimulationState simState = SimulationState.get(world);
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        float drained = simState.pollution.drainForNest(chunkKey, cfg.biterSpawnerDrainPerSecond);

        if (drained > 0f) {
            // 2. Конвертируем в заряд спавна (эффективность растёт с эволюцией:
            //    старые гнёзда конвертируют агрессивнее).
            float evolution = BiterEvolutionManager.get(world).getEvolutionFactor();
            float conversion = 0.5f + 0.5f * evolution;
            spawnCharge += drained * conversion;
            markDirty();
        }

        // 3. Заряд накопился — регистрируем штурмовую единицу в отряд
        //    (физического спавна НЕТ, макро-ИИ разберётся сам).
        while (spawnCharge >= CHARGE_PER_UNIT) {
            spawnCharge -= CHARGE_PER_UNIT;
            unitsEmitted++;
            BiterAttackDirector.feedNestUnit(world, pos, chunkKey);

            // Визуальный отклик для игроков рядом (только в тикающем чанке).
            if (world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
                world.spawnParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                        3, 0.3, 0.2, 0.3, 0.01);
            }
            markDirty();
        }
    }

    public float getSpawnCharge() {
        return spawnCharge;
    }

    public long getUnitsEmitted() {
        return unitsEmitted;
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        nbt.putFloat("SpawnCharge", spawnCharge);
        nbt.putLong("UnitsEmitted", unitsEmitted);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        this.spawnCharge = Math.max(0f, nbt.getFloat("SpawnCharge"));
        this.unitsEmitted = Math.max(0L, nbt.getLong("UnitsEmitted"));
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }
}
