package com.fixplot.factoriocore.content.combat.biters;

import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.simulation.FactoryTickets;
import com.fixplot.factoriocore.content.simulation.GlobalFactoryManager;
import com.fixplot.factoriocore.content.simulation.SimulationState;
import com.fixplot.factoriocore.content.simulation.VirtualRaidGroup;
import com.fixplot.factoriocore.registry.BiterEntityTypes;
import com.fixplot.factoriocore.worldgen.NauvisConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Макро-ИИ штурмовых групп кусак (уровень чанковых координат, без сущностей).
 *
 * Жизненный цикл группы (BiterAttackGroup = VirtualRaidGroup):
 *  1. Рост: гнёзда (BiterSpawnerBlockEntity.feedNestUnit и аналитические
 *     гнёзда из VirtualPollutionGrid.nestPools) добавляют единицы в отряд.
 *  2. Критическая масса: от 5 до 30 кусак (растёт с эволюцией) — группа
 *     отправляется в поход.
 *  3. Вектор движения: по градиенту максимального загрязнения — отряд идёт
 *     к источнику «дыма» (заводам/бойлерам). Градиент монотонен, pathfinding
 *     не нужен: это диффузия наоборот.
 *  4. Материализация: физические BiterEntity спавнятся ТОЛЬКО когда группа
 *     пересекает границу загруженных чанков игрока или работающей фабрики;
 *     материализованные кусаки бегут ломать машины (AttackMachinesGoal).
 *  5. Дематериализация: при уходе из зоны выжившие пересчитываются обратно
 *     в виртуальный состав.
 */
public class BiterAttackDirector {

    private final SimulationState state;
    private final List<VirtualRaidGroup> groups = new ArrayList<>();
    private long nextGroupId = 1L;

    public BiterAttackDirector(SimulationState state) {
        this.state = state;
    }

    // =====================================================================
    // Точка входа: шаг макро-ИИ (раз в 20 тиков)
    // =====================================================================

    public void step(ServerWorld world, SimulationState s) {
        if (world.getTime() % 20 != 0) return;
        if (NauvisConfig.ACTIVE.peacefulMode || NauvisConfig.ACTIVE.noEnemies) return;

        var cfg = FactorioConfig.INSTANCE;
        if (!cfg.enableBiters) return;

        BiterEvolutionManager evolution = BiterEvolutionManager.get(world);

        // 1. Питание аналитических гнёзд из пула сетки (выгруженные чанки).
        feedFromNestPools(world, s, evolution);

        // 2. Движение / материализация существующих групп.
        Iterator<VirtualRaidGroup> it = groups.iterator();
        while (it.hasNext()) {
            VirtualRaidGroup g = it.next();
            if (g.finished || g.totalMembers() <= 0) {
                it.remove();
                continue;
            }
            stepGroup(world, s, g, evolution);
        }
    }

    // =====================================================================
    // Питание отрядов
    // =====================================================================

    /**
     * Гнёзда в тумане войны (аналитические, из VirtualPollutionGrid.nestPools)
     * копят «штормовые единицы» напрямую в отряды своего гнезда.
     */
    private void feedFromNestPools(ServerWorld world, SimulationState s,
                                   BiterEvolutionManager evolution) {
        var nestPools = s.pollution.getNestPools();
        if (nestPools.isEmpty()) return;

        var cfg = FactorioConfig.INSTANCE;
        float unitsPerCharge = cfg.biterNestUnitsPerPoolCharge;
        int maxGroups = cfg.biterMaxAttackGroups;

        int checked = 0;
        for (var entry : nestPools.entrySet()) {
            if (checked++ >= 64) break; // бюджет: не более 64 гнёзд за шаг

            long nestChunk = entry.getKey();
            float pool = entry.getValue();

            // Заряд -> единицы. Каждая единица добавляется в отряд этого гнезда
            // с тиром по текущей эволюции.
            while (pool >= BiterSpawnerBlockEntity.CHARGE_PER_UNIT && groups.size() <= maxGroups) {
                pool -= BiterSpawnerBlockEntity.CHARGE_PER_UNIT;
                nestPools.put(nestChunk, pool);
                feedUnitToGroup(world, nestChunk, evolution.getEvolutionFactor());
            }
            nestPools.put(nestChunk, pool);
        }
    }

    /**
     * Питание от ФИЗИЧЕСКОГО спавнера (BiterSpawnerBlockEntity): единица
     * регистрируется в отряд ближайшего (или нового) гнезда.
     */
    public static void feedNestUnit(ServerWorld world, BlockPos spawnerPos, long spawnerChunkKey) {
        SimulationState s = SimulationState.get(world);
        BiterAttackDirector director = s.biters;
        if (director == null) return;

        BiterEvolutionManager evolution = BiterEvolutionManager.get(world);
        director.feedUnitToGroup(world, spawnerChunkKey, evolution.getEvolutionFactor());
    }

    /**
     * Регистрирует одну штурмовую единицу в отряде гнезда: находит стоящую
     * у гнезда группу или создаёт новую; тир единицы — по эволюции.
     */
    private void feedUnitToGroup(ServerWorld world, long nestChunk, float evolution) {
        var cfg = FactorioConfig.INSTANCE;

        // Ищем группу этого гнезда, которая ещё не в походе.
        VirtualRaidGroup group = null;
        for (VirtualRaidGroup g : groups) {
            if (g.nestChunk == nestChunk && !g.materialized && !g.finished) {
                group = g;
                break;
            }
        }

        // Критическая масса: слишком разросшийся гарнизон раскалывается на два отряда.
        int currentSize = group != null ? group.totalMembers() : 0;
        int maxGarrison = getMaxGroupSize(evolution);
        if (group == null || currentSize >= maxGarrison) {
            if (groups.size() >= cfg.biterMaxAttackGroups) {
                return; // guard: не плодим сверх лимита
            }
            group = new VirtualRaidGroup(nextGroupId++, nestChunk, nestChunk, 0, 0, 0, 0);
            group.lastStepTime = world.getTime();
            groups.add(group);
        }

        // Тир новой единицы: взвешенный выбор по эволюции (как Factorio).
        int tier = pickTierForNewUnit(evolution);
        group.addMemberAtTier(tier);
        group.lastStepTime = world.getTime();
    }

    /** Взвешенный выбор тира новой единицы по фактору эволюции. */
    private static int pickTierForNewUnit(float evolution) {
        // Вес тира — «перекрытие» фактора с диапазоном тира, Small — всегда.
        float wSmall = 1.0f;
        float wMedium = evolution >= 0.20f ? (evolution - 0.20f) / 0.80f : 0f;
        float wBig = evolution >= 0.50f ? (evolution - 0.50f) / 0.50f : 0f;
        float wBehemoth = evolution >= 0.90f ? (evolution - 0.90f) / 0.10f : 0f;

        float total = wSmall + wMedium + wBig + wBehemoth;
        float roll = ThreadLocalRandom.current().nextFloat() * total;
        if (roll < wBehemoth) return 3;
        roll -= wBehemoth;
        if (roll < wBig) return 2;
        roll -= wBig;
        if (roll < wMedium) return 1;
        return 0;
    }

    // =====================================================================
    // Движение и материализация
    // =====================================================================

    private void stepGroup(ServerWorld world, SimulationState s, VirtualRaidGroup g,
                           BiterEvolutionManager evolution) {
        // Материализованная группа живёт ванильной жизнью, пока мобы в зоне.
        if (g.materialized) {
            if (!isInMaterializationZone(world, g.posChunk)) {
                dematerialize(world, g);
            }
            return;
        }

        float evo = evolution.getEvolutionFactor();
        int criticalMass = getCriticalMass(evo);

        // Гарнизон ещё не набрал критическую массу — копим у гнезда.
        if (g.totalMembers() < criticalMass && g.posChunk == g.nestChunk) {
            return;
        }

        // Движение по градиенту загрязнения (вектор к источнику дыма).
        advanceAlongGradient(s, g);

        // Потеряли след надолго — рассеиваемся.
        if (g.lostSteps >= FactorioConfig.INSTANCE.biterMaxLostSteps) {
            g.finished = true;
            return;
        }

        if (isInMaterializationZone(world, g.posChunk)) {
            materialize(world, s, g);
        }
    }

    /** Критическая масса отряда для выхода в поход: 5..30 по эволюции. */
    public static int getCriticalMass(float evolution) {
        return Math.round(MathHelper.lerp(MathHelper.clamp(evolution, 0f, 1f), 5.0f, 30.0f));
    }

    /** Максимальный размер отряда (раскол гарнизона). */
    public static int getMaxGroupSize(float evolution) {
        return getCriticalMass(evolution) * 2;
    }

    /**
     * Движение по градиенту: сосед-чанк с максимальным загрязнением.
     * Монотонный рост значения ведёт к источнику без pathfinding.
     */
    private void advanceAlongGradient(SimulationState s, VirtualRaidGroup g) {
        int cx = ChunkPos.getPackedX(g.posChunk);
        int cz = ChunkPos.getPackedZ(g.posChunk);
        float here = s.pollution.get(g.posChunk);

        long best = g.posChunk;
        float bestVal = here;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                long k = ChunkPos.toLong(cx + dx, cz + dz);
                float v = s.pollution.get(k);
                if (v > bestVal) { bestVal = v; best = k; }
            }
        }

        float sniff = FactorioConfig.INSTANCE.biterGradientSniffThreshold;
        if (bestVal <= sniff || best == g.posChunk) {
            g.lostSteps++;
            return;
        }
        g.posChunk = best;
        g.lostSteps = 0;
    }

    /**
     * Зона материализации: чанки игрока (simulation distance) или чанк
     * работающей фабрики (наш тикет). Кусаки НЕ материализуются в level-33
     * чанках — там мобы не тикают, вышли бы «статуи».
     */
    private boolean isInMaterializationZone(ServerWorld world, long chunkKey) {
        int cx = ChunkPos.getPackedX(chunkKey);
        int cz = ChunkPos.getPackedZ(chunkKey);

        int simDist = Math.max(2, world.getServer().getPlayerManager().getSimulationDistance());
        for (var player : world.getPlayers()) {
            if (Math.abs(player.getChunkPos().x - cx) <= simDist
                    && Math.abs(player.getChunkPos().z - cz) <= simDist) {
                return true;
            }
        }
        return GlobalFactoryManager.isChunkTickingByFactory(world, chunkKey);
    }

    // =====================================================================
    // Материализация / дематериализация
    // =====================================================================

    /**
     * Спавн физических BiterEntity: только при входе группы в загруженные чанки.
     * Целевой чанк (и соседи) промоутится в FACTORY_TICKING — турели должны
     * честно отработать, а кусаки — бежать ломать машины.
     */
    private void materialize(ServerWorld world, SimulationState s, VirtualRaidGroup g) {
        int cx = ChunkPos.getPackedX(g.posChunk);
        int cz = ChunkPos.getPackedZ(g.posChunk);

        // Промоушен целевых чанков для честного боя.
        FactoryTickets.requestCombatTicking(world, cx, cz);
        FactoryTickets.requestCombatTicking(world, cx + 1, cz);
        FactoryTickets.requestCombatTicking(world, cx, cz + 1);

        var cfg = FactorioConfig.INSTANCE;
        int total = g.totalMembers();
        int maxSpawn = Math.min(total, cfg.biterMaxMaterializedPerGroup);

        long targetChunk = findMaxPollutionNearby(s, g.posChunk, 4);
        int tcx = ChunkPos.getPackedX(targetChunk);
        int tcz = ChunkPos.getPackedZ(targetChunk);

        // Порядок спавна: старшие тиры вперёд (танки принимают турели на себя).
        int[] spawnedPerTier = new int[4];
        int spawned = 0;
        for (int tier = 3; tier >= 0 && spawned < maxSpawn; tier--) {
            int remaining = g.getMembersAtTier(tier);
            int toSpawn = Math.min(remaining, maxSpawn - spawned);
            for (int i = 0; i < toSpawn; i++) {
                if (spawnBiter(world, g, tier, cx, cz, tcx, tcz)) {
                    spawnedPerTier[tier]++;
                    spawned++;
                }
            }
        }

        // Нематериализованный остаток (сверх клампа) рассеивается в тумане.
        for (int tier = 0; tier < 4; tier++) {
            g.setMembersAtTier(tier, spawnedPerTier[tier]);
        }

        g.materialized = true;
        g.lastStepTime = world.getTime();
    }

    private boolean spawnBiter(ServerWorld world, VirtualRaidGroup g, int tier,
                               int cx, int cz, int tcx, int tcz) {
        EntityType<BiterEntity> type = BiterEntityTypes.forTier(tier);
        BiterEntity biter = type.create(world);
        if (biter == null) return false;

        biter.setTier(tier);
        double x = (cx << 4) + 8 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 12;
        double z = (cz << 4) + 8 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 12;
        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING,
                MathHelper.floor(x), MathHelper.floor(z)) - 1;
        biter.setPosition(x, topY + 1.0, z);

        // Тег группы: дематериализация собирает выживших по нему.
        biter.getCommandTags().add(BiterEntity.RAID_GROUP_TAG_PREFIX + g.id);
        biter.getCommandTags().add("factoriocore:raider");

        if (world.spawnEntity(biter)) {
            // Цель: точка максимального загрязнения (фактически — фабрика).
            // AttackMachinesGoal возьмёт машины в радиусе сам; сюда — общий вектор.
            biter.getNavigation().startMovingTo((tcx << 4) + 8, topY + 1, (tcz << 4) + 8, 1.2);
            return true;
        }
        return false;
    }

    /** Пересчёт выживших физических кусак обратно в виртуальный состав. */
    private void dematerialize(ServerWorld world, VirtualRaidGroup g) {
        int baseX = ChunkPos.getPackedX(g.posChunk) << 4;
        int baseZ = ChunkPos.getPackedZ(g.posChunk) << 4;
        String groupTag = BiterEntity.RAID_GROUP_TAG_PREFIX + g.id;

        // Широкий бокс: материализованные кусаки могли убежать к фабрике.
        int survivors = 0;
        int[] perTier = new int[4];
        for (var entity : world.getEntitiesByClass(BiterEntity.class,
                new Box(baseX - 96.0, world.getBottomY() - 16, baseZ - 96.0,
                        baseX + 112.0, world.getBottomY() + 384, baseZ + 112.0),
                e -> e.isAlive() && e.getCommandTags().contains(groupTag))) {
            survivors++;
            perTier[entity.getTier()]++;
        }

        // Убиваем физических (они заморожены/выгрузятся), состав — в виртуал.
        for (var entity : world.getEntitiesByClass(BiterEntity.class,
                new Box(baseX - 96.0, world.getBottomY() - 16, baseZ - 96.0,
                        baseX + 112.0, world.getBottomY() + 384, baseZ + 112.0),
                e -> e.getCommandTags().contains(groupTag))) {
            entity.discard();
        }

        if (survivors > 0) {
            for (int tier = 0; tier < 4; tier++) {
                g.setMembersAtTier(tier, perTier[tier]);
            }
            // Группа возвращается в марш по градиенту.
            g.lostSteps = 0;
        } else {
            // Всех положили (турели/мины/игрок) — отряд добит.
            g.finished = true;
        }
        g.materialized = false;
    }

    private long findMaxPollutionNearby(SimulationState s, long fromChunk, int radius) {
        int cx = ChunkPos.getPackedX(fromChunk);
        int cz = ChunkPos.getPackedZ(fromChunk);
        long best = fromChunk;
        float bestVal = -1f;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                long k = ChunkPos.toLong(cx + dx, cz + dz);
                float v = s.pollution.get(k);
                if (v > bestVal) { bestVal = v; best = k; }
            }
        }
        return best;
    }

    public List<VirtualRaidGroup> getGroups() {
        return groups;
    }

    // =====================================================================
    // Персистентность
    // =====================================================================

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("NextId", nextGroupId);
        NbtList list = new NbtList();
        for (VirtualRaidGroup g : groups) {
            list.add(g.writeNbt(new NbtCompound()));
        }
        nbt.put("Groups", list);
        return nbt;
    }

    public void readNbt(NbtCompound nbt) {
        groups.clear();
        nextGroupId = nbt.getLong("NextId");
        if (nbt.contains("Groups", NbtElement.LIST_TYPE)) {
            NbtList list = nbt.getList("Groups", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                groups.add(VirtualRaidGroup.fromNbt(list.getCompound(i)));
            }
        }
    }
}
