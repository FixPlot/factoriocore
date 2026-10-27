package com.fixplot.factoriocore.content.simulation;

import net.minecraft.nbt.NbtCompound;

/**
 * Виртуальная рейдовая группа кусак на макро-карте. Живёт в чанковых координатах,
 * не занимает ни сущностей, ни чанков. Физические мобы материализуются только
 * при входе группы в зону материализации (чанки игрока или периметр фабрики).
 */
public class VirtualRaidGroup {

    public long id;
    /** Текущая позиция: ChunkPos.toLong. */
    public long posChunk;
    /** Позиция гнезда-источника: ChunkPos.toLong. */
    public long nestChunk;
    /** Состав (эволюция определяет пропорции при запуске): small, medium, big, behemoth. */
    public int smallBiters, mediumBiters, bigBiters, behemothBiters;
    /** Шаги без положительного градиента — «разочарование» группы. */
    public int lostSteps;
    /** Физические мобы заматериализованы (сущности живут ванильной жизнью). */
    public boolean materialized;
    /** Группа добила цель / рассеяна — к удалению. */
    public boolean finished;
    /** Игровое время последнего макро-шага. */
    public long lastStepTime;

    public VirtualRaidGroup(long id, long nestChunk, long posChunk,
                            int small, int medium, int big) {
        this(id, nestChunk, posChunk, small, medium, big, 0);
    }

    public VirtualRaidGroup(long id, long nestChunk, long posChunk,
                            int small, int medium, int big, int behemoth) {
        this.id = id;
        this.nestChunk = nestChunk;
        this.posChunk = posChunk;
        this.smallBiters = small;
        this.mediumBiters = medium;
        this.bigBiters = big;
        this.behemothBiters = behemoth;
    }

    public int totalMembers() {
        return smallBiters + mediumBiters + bigBiters + behemothBiters;
    }

    /** Индекс тира по позиции в составе (клампы в порядке старшинства). */
    public int getMembersAtTier(int tier) {
        return switch (tier) {
            case 3 -> behemothBiters;
            case 2 -> bigBiters;
            case 1 -> mediumBiters;
            default -> smallBiters;
        };
    }

    public void setMembersAtTier(int tier, int count) {
        switch (tier) {
            case 3 -> behemothBiters = count;
            case 2 -> bigBiters = count;
            case 1 -> mediumBiters = count;
            default -> smallBiters = count;
        }
    }

    /** Добавить одну единицу тира (питание от спавнера). */
    public void addMemberAtTier(int tier) {
        setMembersAtTier(tier, getMembersAtTier(tier) + 1);
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLong("Id", id);
        nbt.putLong("Pos", posChunk);
        nbt.putLong("Nest", nestChunk);
        nbt.putInt("Small", smallBiters);
        nbt.putInt("Medium", mediumBiters);
        nbt.putInt("Big", bigBiters);
        nbt.putInt("Behemoth", behemothBiters);
        nbt.putInt("Lost", lostSteps);
        nbt.putBoolean("Mat", materialized);
        nbt.putBoolean("Fin", finished);
        nbt.putLong("Step", lastStepTime);
        return nbt;
    }

    public static VirtualRaidGroup fromNbt(NbtCompound nbt) {
        VirtualRaidGroup g = new VirtualRaidGroup(
                nbt.getLong("Id"), nbt.getLong("Nest"), nbt.getLong("Pos"),
                nbt.getInt("Small"), nbt.getInt("Medium"), nbt.getInt("Big"),
                nbt.getInt("Behemoth"));
        g.lostSteps = nbt.getInt("Lost");
        g.materialized = nbt.getBoolean("Mat");
        g.finished = nbt.getBoolean("Fin");
        g.lastStepTime = nbt.getLong("Step");
        return g;
    }
}
