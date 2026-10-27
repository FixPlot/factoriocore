package com.fixplot.factoriocore.content.simulation;

import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;

/**
 * Кастомные chunk-тикеты фабрики. Уровень тикета задаёт глубину симуляции:
 *  - LEVEL_LOADED (33): чанк в памяти, блоки/BE читаются и пишутся, но НИКАКИХ
 *    entity-ticking, random ticks и освещения. Дешёвый "склад состояния".
 *  - LEVEL_TICKING (31): полный entity-ticking. Выдаётся ТОЛЬКО ротационно
 *    с бюджетом (см. GlobalFactoryManager) либо на периметре боя с кусаками.
 *
 * Тикеты не переживают рестарт сервера — источник истины SimulationState,
 * тикеты перевыдаются в ServerLevelEvents.LOAD.
 */
public final class FactoryTickets {

    /** ChunkLevelType.FULL: loaded, но не тикает. */
    public static final int LEVEL_LOADED = 33;
    /** ChunkLevelType.ENTITY_TICKING: полный тик чанка. */
    public static final int LEVEL_TICKING = 31;

    public static final ChunkTicketType<ChunkPos> FACTORY_LOADED =
            ChunkTicketType.create("factoriocore:factory_loaded",
                    Comparator.comparingLong(ChunkPos::toLong));

    public static final ChunkTicketType<ChunkPos> FACTORY_TICKING =
            ChunkTicketType.create("factoriocore:factory_ticking",
                    Comparator.comparingLong(ChunkPos::toLong));

    private FactoryTickets() {}

    public static void setLoaded(ServerWorld world, int cx, int cz, boolean on) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos pos = new ChunkPos(cx, cz);
        if (on) cm.addTicket(FACTORY_LOADED, pos, LEVEL_LOADED, pos);
        else    cm.removeTicket(FACTORY_LOADED, pos, LEVEL_LOADED, pos);
    }

    public static void setTicking(ServerWorld world, int cx, int cz, boolean on) {
        ServerChunkManager cm = world.getChunkManager();
        ChunkPos pos = new ChunkPos(cx, cz);
        if (on) cm.addTicket(FACTORY_TICKING, pos, LEVEL_TICKING, pos);
        else    cm.removeTicket(FACTORY_TICKING, pos, LEVEL_TICKING, pos);
    }

    /** Промоушен чанка на время боя с кусаками (турели должны тикать). */
    public static void requestCombatTicking(ServerWorld world, int cx, int cz) {
        setTicking(world, cx, cz, true);
    }
}
