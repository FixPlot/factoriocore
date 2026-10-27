package com.fixplot.factoriocore;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.content.crafting.ServerCraftingQueueManager;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.pavement.PavementBlock;
import com.fixplot.factoriocore.network.ChangeStackSizePayload;
import com.fixplot.factoriocore.network.RequestCraftPayload;
import com.fixplot.factoriocore.network.SyncBlockHealthPayload;
import com.fixplot.factoriocore.network.SyncOreAmountPayload;
import com.fixplot.factoriocore.network.SyncCraftQueuePayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItemGroups;
import com.fixplot.factoriocore.registry.ModItems;
import com.fixplot.factoriocore.registry.ModScreenHandlers;
import com.fixplot.factoriocore.worldgen.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FactorioCore implements ModInitializer {
public static final String MOD_ID = "factoriocore";
public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

@Override
public void onInitialize() {
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
            com.fixplot.factoriocore.network.SyncPollutionPayload.ID,
            com.fixplot.factoriocore.network.SyncPollutionPayload.CODEC
    );
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(
            com.fixplot.factoriocore.network.ResearchActionPayload.ID,
            com.fixplot.factoriocore.network.ResearchActionPayload.CODEC
    );
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(
            com.fixplot.factoriocore.network.SyncResearchStatePayload.ID,
            com.fixplot.factoriocore.network.SyncResearchStatePayload.CODEC
    );
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
            com.fixplot.factoriocore.network.ResearchActionPayload.ID,
            com.fixplot.factoriocore.network.ResearchActionPayload::handleServer
    );
    net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            server.execute(() -> {
                com.fixplot.factoriocore.content.research.ResearchManager.init();
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                        handler.player,
                        new com.fixplot.factoriocore.network.SyncResearchStatePayload(
                                com.fixplot.factoriocore.content.research.ResearchManager.toNbt()));
            })
    );
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(
            com.fixplot.factoriocore.content.pollution.PollutionManager::tickServerWorld
    );

    // === Глобальная симуляция фабрики (вне simulation distance) ===
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(
            com.fixplot.factoriocore.content.simulation.GlobalFactoryManager::tickServer
    );
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register(
            com.fixplot.factoriocore.content.simulation.GlobalFactoryManager::onWorldLoad
    );
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.UNLOAD.register(
            com.fixplot.factoriocore.content.simulation.GlobalFactoryManager::onWorldUnload
    );
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
        com.fixplot.factoriocore.content.simulation.GlobalFactoryManager.onChunkUnload(world, chunk);
    });
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
        com.fixplot.factoriocore.content.simulation.GlobalFactoryManager.onChunkLoad(world, chunk);
    });

    // === FactorioChunkManager: персистентные чанк-тикеты фабрики ===
    // Инициализация при старте сервера: перевыдача тикетов из PersistentState,
    // чтобы заводы не засыпали после перезапуска. Уровень 32 (BLOCK_TICKING)
    // держит BE-тики фабрики без тика ванильных мобов.
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register(
            (server, world) -> {
                // Инициализация состояния на первый тик мира: список чанков уже
                // загружен из NBT; тикеты выдаются вне транзакции чанков.
                com.fixplot.factoriocore.content.simulation.FactorioChunkManager.get(world);
            }
    );

    // === Кусаки (Biters): эволюция + штурмовые группы + сущности ===
    // Регистрация типов сущностей (4 тира) с атрибутами.
    com.fixplot.factoriocore.registry.BiterEntityTypes.registerBiterEntityTypes();

    // Инициализация менеджера эволюции при загрузке мира (PersistentState).
    net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register(
            (server, world) ->
                    com.fixplot.factoriocore.content.combat.biters.BiterEvolutionManager.get(world)
    );

    // destroy_factor эволюции: снос игроком гнезда-спавнера ускоряет эволюцию.
    net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
        if (!world.isClient && world instanceof net.minecraft.server.world.ServerWorld serverWorld
                && state.getBlock() == com.fixplot.factoriocore.registry.ModBlocks.BITER_SPAWNER) {
            com.fixplot.factoriocore.content.combat.biters.BiterEvolutionManager.get(serverWorld)
                    .onSpawnerDestroyed(serverWorld);
        }
    });

    ModItems.registerModItems();
    ModBlocks.registerModBlocks();
    ModItemGroups.registerItemGroups();
    ModScreenHandlers.registerScreenHandlers();
    ModWorldGen.registerWorldGen();
    BlockHealthManager.registerServer();
    com.fixplot.factoriocore.content.combat.weapons.FactorioWeapons.registerServerTick();
    com.fixplot.factoriocore.registry.ModSounds.registerSounds();
    com.fixplot.factoriocore.config.FactorioConfig.load();
		FactorioRecipes.init();

PayloadTypeRegistry.playC2S().register(ChangeStackSizePayload.ID, ChangeStackSizePayload.CODEC);
PayloadTypeRegistry.playC2S().register(RequestCraftPayload.ID, RequestCraftPayload.CODEC);
PayloadTypeRegistry.playS2C().register(SyncBlockHealthPayload.ID, SyncBlockHealthPayload.CODEC);
PayloadTypeRegistry.playS2C().register(SyncOreAmountPayload.ID, SyncOreAmountPayload.CODEC);
PayloadTypeRegistry.playS2C().register(SyncCraftQueuePayload.ID, SyncCraftQueuePayload.CODEC);
PayloadTypeRegistry.playC2S().register(com.fixplot.factoriocore.network.SetAssemblerRecipePayload.ID, com.fixplot.factoriocore.network.SetAssemblerRecipePayload.CODEC);

ServerPlayNetworking.registerGlobalReceiver(ChangeStackSizePayload.ID, (payload, context) -> {
context.server().execute(() -> {
BlockEntity be = context.player().getWorld().getBlockEntity(payload.pos());
if (be instanceof InserterBlockEntity inserter) {
inserter.changeMaxPickupCount(payload.delta());
} else if (be instanceof BeltBlockEntity belt && belt.isSplitter()) {
belt.handleSplitterGuiAction(payload.delta());
} else if (be instanceof FactorioChestBlockEntity chest) {
chest.setUnlockedSlots(payload.delta());
}
});
});

ServerPlayNetworking.registerGlobalReceiver(com.fixplot.factoriocore.network.SetAssemblerRecipePayload.ID, (payload, context) -> {
    context.server().execute(() -> {
        if (context.player().currentScreenHandler instanceof com.fixplot.factoriocore.content.production.assembler.AssemblerScreenHandler ash && ash.blockEntity != null) {
            ash.blockEntity.setRecipeId(payload.recipeId(), context.player());
            return;
        }
        net.minecraft.block.entity.BlockEntity be = context.player().getWorld().getBlockEntity(payload.pos());
        if (be instanceof com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity asm) {
            asm.setRecipeId(payload.recipeId(), context.player());
        }
    });
});

ServerPlayNetworking.registerGlobalReceiver(RequestCraftPayload.ID, (payload, context) -> {
context.server().execute(() -> ServerCraftingQueueManager.handleCraftRequest(context.player(), payload));
});

ServerTickEvents.END_SERVER_TICK.register(server -> {
for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
if (com.fixplot.factoriocore.config.FactorioConfig.INSTANCE.enablePavementSpeedBoost) { PavementBlock.updatePlayerPavementSpeed(player); }
if ((server.getTicks() & 3) == 0) {
if (com.fixplot.factoriocore.config.FactorioConfig.INSTANCE.enableInventoryAutoSort) { com.fixplot.factoriocore.content.crafting.InventoryAutoSorter.tickPlayerInventory(player); }
}
}
ServerCraftingQueueManager.tickServer(server);
});

LOGGER.info("FactorioCore initialized!");
}

public static Identifier id(String path) {
return Identifier.of(MOD_ID, path);
}
}