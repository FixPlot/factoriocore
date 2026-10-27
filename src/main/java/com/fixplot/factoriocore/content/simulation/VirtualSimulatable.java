package com.fixplot.factoriocore.content.simulation;

import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

/**
 * Контракт машины, способной симулироваться ВНЕ тикающего чанка.
 *
 * Жизненный цикл:
 *  1. Машина стоит в тикающем чанке — работает ванильный BE-тикер, ничего не делаем.
 *  2. Чанк выгружается (или с него снимается FACTORY_TICKING) — на CHUNK_UNLOAD
 *     вызывается {@link #captureVirtualState()}: машина отдаёт снапшот в
 *     виртуальный граф GlobalFactoryManager.
 *  3. Чанк снова материализуется (ротация duty-cycle, приход игрока, рейд кусак) —
 *     вызывается {@link #catchUp(long)}: машина доматывает elapsedTicks арифметически,
 *     с гарантией отсутствия переполнения инвентарей и дюпа.
 *
 * Контракт catchUp:
 *  - вызывается РОВНО ОДИН РАЗ на период виртуальности (elapsed монотонно растёт);
 *  - машины с вещественными побочными эффектами (частицы, звук, sync-пакеты)
 *    НЕ воспроизводят их при catchUp;
 *  - все добавления предметов идут через offer-семантику с клампом на maxCount.
 */
public interface VirtualSimulatable {

    /** Снапшот для автономной симуляции. null = машина неактивна, виртуальная нода не нужна. */
    @Nullable
    NbtCompound captureVirtualState();

    /**
     * Доматывание виртуально прожитых тиков. Машина обязана:
     *  - проклампить всё, что может переполниться (инвентари, буферы);
     *  - не превышать 1 цикл работы за вызов сверх точной арифметики;
     *  - записать новое виртуальное время через {@code markVirtualTicked(world.getTime())}.
     */
    void catchUp(long elapsedTicks);

    /** Отметка "машина была симулирована до этого игрового времени". */
    default void markVirtualTicked(long gameTime) {}

    /** Последняя отметка виртуального тика (для расчёта elapsed). */
    default long getLastVirtualTicked() { return 0L; }
}
