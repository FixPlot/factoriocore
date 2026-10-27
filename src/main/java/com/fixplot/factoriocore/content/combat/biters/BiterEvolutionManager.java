package com.fixplot.factoriocore.content.combat.biters;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.PersistentState;

/**
 * Глобальный менеджер эволюции кусак (PersistentState, один на мир).
 *
 * Фактор эволюции [0..1] растёт от трёх слагаемых (формула в духе Factorio):
 *  - time_factor:     пассивный рост со временем игры (медленно, неумолимо);
 *  - pollution_factor: от суммарного объёма загрязнения в VirtualPollutionGrid
 *                      (главный двигатель: грязная фабрика = быстрая эволюция);
 *  - destroy_factor:  от уничтожения игроками гнёзд кусак (триггерное слагаемое).
 *
 * Тир кусак (Small / Medium / Big / Behemoth) выбирается из фактора:
 *  [0.00, 0.20) -> SMALL,  [0.20, 0.50) -> MEDIUM,
 *  [0.50, 0.90) -> BIG,    [0.90, 1.00] -> BEHEMOTH.
 */
public class BiterEvolutionManager extends PersistentState {

    public static final String KEY = "factoriocore_biter_evolution";

    private static final Type<BiterEvolutionManager> TYPE =
            new Type<>(BiterEvolutionManager::new, BiterEvolutionManager::fromNbt, null);

    // ==== Балансные константы (см. также FactorioConfig.biter*) ====
    /** Шаг пересчёта: раз в 10 секунд. */
    public static final int STEP_TICKS = 200;
    /** Пассивный рост за шаг (time_factor): ~0.0005 → полная эволюция за ~55 часов. */
    private static final float TIME_TERM_PER_STEP = 0.0005f;
    /** Загрязнение, дающее канонический темп: 10000 суммарного -> +0.02 за шаг. */
    private static final float POLLUTION_TERM_SCALE = 0.000002f;
    /** Кламп слагаемого загрязнения за шаг. */
    private static final float POLLUTION_TERM_MAX = 0.02f;
    /** Бонус за уничтоженное гнездо (destroy_factor). */
    private static final float DESTROY_NEST_BONUS = 0.004f;
    /** Бонус за уничтоженного спавнера-блока. */
    private static final float DESTROY_SPAWNER_BONUS = 0.006f;

    /** Текущий фактор эволюции [0..1]. */
    private float evolutionFactor = 0.0f;
    /** Счётчики для статистики/ HUD. */
    private long nestsDestroyed = 0L;
    private long spawnersDestroyed = 0L;

    public BiterEvolutionManager() {
    }

    public static BiterEvolutionManager get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE, KEY);
    }

    public static BiterEvolutionManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        BiterEvolutionManager m = new BiterEvolutionManager();
        m.evolutionFactor = MathHelper.clamp(nbt.getFloat("Evolution"), 0.0f, 1.0f);
        m.nestsDestroyed = nbt.getLong("NestsDestroyed");
        m.spawnersDestroyed = nbt.getLong("SpawnersDestroyed");
        return m;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup lookup) {
        nbt.putFloat("Evolution", evolutionFactor);
        nbt.putLong("NestsDestroyed", nestsDestroyed);
        nbt.putLong("SpawnersDestroyed", spawnersDestroyed);
        return nbt;
    }

    // =====================================================================
    // Рост эволюции
    // =====================================================================

    /**
     * Шаг роста: вызывается из GlobalFactoryManager.tickServer (каждый тик,
     * сам фильтрует по STEP_TICKS). totalPollution — суммарное загрязнение
     * VirtualPollutionGrid (считается вызывающим, чтобы не дублировать обход).
     */
    public void step(ServerWorld world, float totalPollution) {
        if (world.getTime() % STEP_TICKS != 0) return;

        float pollutionTerm = Math.min(POLLUTION_TERM_MAX, POLLUTION_TERM_SCALE * Math.max(0f, totalPollution));
        float timeTerm = TIME_TERM_PER_STEP;
        float next = evolutionFactor + pollutionTerm + timeTerm;
        if (next != evolutionFactor) {
            evolutionFactor = MathHelper.clamp(next, 0.0f, 1.0f);
            markDirty();
        }
    }

    /** destroy_factor: игрок снёс вражеское гнездо (виртуальное, из аналитики). */
    public void onNestDestroyed(ServerWorld world) {
        nestsDestroyed++;
        evolutionFactor = MathHelper.clamp(evolutionFactor + DESTROY_NEST_BONUS, 0.0f, 1.0f);
        markDirty();
    }

    /** destroy_factor: игрок снёс физический спавнер-блок кусак. */
    public void onSpawnerDestroyed(ServerWorld world) {
        spawnersDestroyed++;
        evolutionFactor = MathHelper.clamp(evolutionFactor + DESTROY_SPAWNER_BONUS, 0.0f, 1.0f);
        markDirty();
    }

    // =====================================================================
    // Запросы
    // =====================================================================

    public float getEvolutionFactor() {
        return evolutionFactor;
    }

    public void setEvolutionFactor(float value) {
        this.evolutionFactor = MathHelper.clamp(value, 0.0f, 1.0f);
        markDirty();
    }

    /** Тир кусак на основе фактора эволюции. */
    public BiterTier getCurrentTier() {
        return tierFor(evolutionFactor);
    }

    public long getNestsDestroyed() { return nestsDestroyed; }
    public long getSpawnersDestroyed() { return spawnersDestroyed; }

    // =====================================================================
    // Тиры
    // =====================================================================

    public enum BiterTier {
        SMALL("small", 0.0f),
        MEDIUM("medium", 0.20f),
        BIG("big", 0.50f),
        BEHEMOTH("behemoth", 0.90f);

        public final String id;
        /** Порог фактора, с которого тир доступен. */
        public final float threshold;

        BiterTier(String id, float threshold) {
            this.id = id;
            this.threshold = threshold;
        }

        /** Название локализации сущности этого тира. */
        public String getEntityTranslationKey() {
            return "entity.factoriocore.biter_" + id;
        }
    }

    /** Тир для произвольного фактора. */
    public static BiterTier tierFor(float evolutionFactor) {
        if (evolutionFactor >= BiterTier.BEHEMOTH.threshold) return BiterTier.BEHEMOTH;
        if (evolutionFactor >= BiterTier.BIG.threshold) return BiterTier.BIG;
        if (evolutionFactor >= BiterTier.MEDIUM.threshold) return BiterTier.MEDIUM;
        return BiterTier.SMALL;
    }

    /**
     * Состав штурма из фактора эволюции: тиры распределяются пропорционально
     * «перекрытию» фактора с диапазоном тира (как у Factorio: при 0.6 в волне
     * уже есть Big, но Small ещё доминирует по числу).
     */
    public static int[] composeWaveSizes(float evolutionFactor, int totalSize) {
        int small = 0, medium = 0, big = 0, behemoth = 0;
        if (evolutionFactor >= BiterTier.BEHEMOTH.threshold) {
            float span = 1.0f - BiterTier.BEHEMOTH.threshold;
            float f = MathHelper.clamp((evolutionFactor - BiterTier.BEHEMOTH.threshold) / span, 0f, 1f);
            behemoth = Math.round(totalSize * f * 0.35f);
        }
        if (evolutionFactor >= BiterTier.BIG.threshold) {
            float span = 1.0f - BiterTier.BIG.threshold;
            float f = MathHelper.clamp((evolutionFactor - BiterTier.BIG.threshold) / span, 0f, 1f);
            big = Math.round(totalSize * (0.3f + 0.25f * f));
        }
        if (evolutionFactor >= BiterTier.MEDIUM.threshold) {
            float span = 1.0f - BiterTier.MEDIUM.threshold;
            float f = MathHelper.clamp((evolutionFactor - BiterTier.MEDIUM.threshold) / span, 0f, 1f);
            medium = Math.round(totalSize * (0.4f + 0.2f * f));
        }
        // Остаток — Small: всегда есть, доля падает с эволюцией.
        small = Math.max(1, totalSize - medium - big - behemoth);
        int sum = small + medium + big + behemoth;
        if (sum > totalSize) {
            small -= (sum - totalSize); // кламп: переполнения состава не допускаем
        }
        return new int[]{small, medium, big, behemoth};
    }
}
