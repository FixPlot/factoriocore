package com.fixplot.factoriocore.content.crafting;

import com.fixplot.factoriocore.content.research.ResearchManager;
import com.fixplot.factoriocore.network.RequestCraftPayload;
import com.fixplot.factoriocore.network.SyncCraftQueuePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

import java.util.*;

public class ServerCraftingQueueManager {

    private static final Map<UUID, List<FactorioRecipes.QueueStep>> QUEUES = new HashMap<>();
    private static final Map<UUID, Integer> ACTIVE_PROGRESS = new HashMap<>();

    public static void handleCraftRequest(ServerPlayerEntity player, RequestCraftPayload payload) {
        FactorioRecipes.init();
        UUID uuid = player.getUuid();
        List<FactorioRecipes.QueueStep> queue = QUEUES.computeIfAbsent(uuid, k -> new ArrayList<>());

        // Отмена элемента очереди по клику в левом нижнем углу
        if (payload.action() < 0) {
            if (!queue.isEmpty()) {
                int targetIndex = findLastMatchingStepIndex(queue, payload.recipeId());
                if (targetIndex >= 0) {
                    cancelStepWithCascade(queue, targetIndex, player);
                }
                if (queue.isEmpty()) {
                    ACTIVE_PROGRESS.put(uuid, 0);
                }
                player.currentScreenHandler.sendContentUpdates();
                player.playerScreenHandler.sendContentUpdates();
                syncQueueToClient(player);
            }
            return;
        }

        FactorioRecipes.Recipe recipe = FactorioRecipes.getById(payload.recipeId());
        if (recipe == null) return;

        int requested = payload.action();
        if (requested >= 999) {
            int maxPossible = FactorioRecipes.getCraftableCount(player.getInventory(), recipe);
            requested = Math.max(1, maxPossible / Math.max(1, recipe.output().getCount()));
        }

        if (player.isCreative()) {
            int countToQueue = Math.min(requested, 20);
            for (int i = 0; i < countToQueue; i++) {
                queue.add(new FactorioRecipes.QueueStep(recipe, true));
            }
            syncQueueToClient(player);
            return;
        }

        List<FactorioRecipes.QueueStep> planned = FactorioRecipes.planAndConsume(player.getInventory(), recipe, requested);
        if (!planned.isEmpty()) {
            queue.addAll(planned);
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
            syncQueueToClient(player);
        } else {
            player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.5f, 0.7f);
        }
    }

    private static int findLastMatchingStepIndex(List<FactorioRecipes.QueueStep> queue, String recipeId) {
        if (recipeId != null && !recipeId.isEmpty()) {
            for (int i = queue.size() - 1; i >= 0; i--) {
                if (queue.get(i).recipe().id().equals(recipeId)) {
                    return i;
                }
            }
        }
        return queue.size() - 1;
    }

    /**
     * Каскадно отменяет выбранный шаг крафта:
     * - Если отменяется финальный предмет, для которого в очереди ещё лежат невыполненные промежуточные шаги (deliverToPlayer == false),
     *   эти промежуточные шаги тоже снимаются с очереди, а игроку возвращается базовое сырьё.
     * - Если игрок отменяет напрямую промежуточный шаг (deliverToPlayer == false),
     *   автоматически отменяется зависимый от него финальный предмет, чтобы избежать бесплатного крафта.
     */
    private static void cancelStepWithCascade(List<FactorioRecipes.QueueStep> queue, int index, ServerPlayerEntity player) {
        if (index < 0 || index >= queue.size()) return;

        FactorioRecipes.QueueStep step = queue.get(index);

        // Если игрок кликнул по промежуточному шагу (deliver == false), ищем зависящий от него родительский шаг правее в очереди
        if (!step.deliverToPlayer()) {
            Item intermediateOut = step.recipe().output().getItem();
            for (int i = queue.size() - 1; i > index; i--) {
                FactorioRecipes.QueueStep later = queue.get(i);
                for (FactorioRecipes.Ingredient ing : later.recipe().ingredients()) {
                    if (ing.item() == intermediateOut) {
                        cancelStepWithCascade(queue, i, player);
                        return;
                    }
                }
            }
        }

        boolean wasFirst = (index == 0);
        queue.remove(index);
        if (wasFirst) {
            ACTIVE_PROGRESS.put(player.getUuid(), 0);
        }

        if (player.isCreative()) {
            return;
        }

        refundIngredientsOrCancelSubsteps(queue, step.recipe(), player);
    }

    private static void refundIngredientsOrCancelSubsteps(List<FactorioRecipes.QueueStep> queue,
                                                          FactorioRecipes.Recipe cancelledRecipe,
                                                          ServerPlayerEntity player) {
        for (FactorioRecipes.Ingredient ing : cancelledRecipe.ingredients()) {
            int remainingToRefund = ing.count();

            // Проверяем с конца очереди, есть ли ожидающие промежуточные шаги (deliverToPlayer == false), производящие этот ингредиент
            for (int i = queue.size() - 1; i >= 0 && remainingToRefund > 0; i--) {
                FactorioRecipes.QueueStep candidate = queue.get(i);
                if (!candidate.deliverToPlayer() && candidate.recipe().output().isOf(ing.item())) {
                    int outCount = candidate.recipe().output().getCount();
                    if (outCount <= remainingToRefund) {
                        boolean removedFirst = (i == 0);
                        queue.remove(i);
                        if (removedFirst) {
                            ACTIVE_PROGRESS.put(player.getUuid(), 0);
                        }
                        remainingToRefund -= outCount;
                        refundIngredientsOrCancelSubsteps(queue, candidate.recipe(), player);
                    }
                }
            }

            if (remainingToRefund > 0) {
                player.getInventory().offerOrDrop(new ItemStack(ing.item(), remainingToRefund));
            }
        }
    }

    public static void tickServer(MinecraftServer server) {
        ResearchManager.tickServer(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();
            List<FactorioRecipes.QueueStep> queue = QUEUES.get(uuid);
            if (queue == null || queue.isEmpty()) {
                continue;
            }

            FactorioRecipes.QueueStep current = queue.get(0);
            int progress = ACTIVE_PROGRESS.getOrDefault(uuid, 0) + 1;
            int maxTicks = Math.max(1, current.recipe().timeTicks());

            if (progress >= maxTicks) {
                queue.remove(0);
                ACTIVE_PROGRESS.put(uuid, 0);

                if (current.deliverToPlayer()) {
                    ItemStack result = current.recipe().output().copy();
                    player.getInventory().offerOrDrop(result);
                    player.currentScreenHandler.sendContentUpdates();
                    player.playerScreenHandler.sendContentUpdates();
                    player.playSoundToPlayer(SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.4f, 1.3f);
                } else {
                    player.playSoundToPlayer(SoundEvents.BLOCK_SMITHING_TABLE_USE, SoundCategory.PLAYERS, 0.2f, 1.6f);
                }
                syncQueueToClient(player);
            } else {
                ACTIVE_PROGRESS.put(uuid, progress);
                if (progress % 2 == 0) {
                    syncQueueToClient(player);
                }
            }
        }
    }

    public static void syncQueueToClient(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        List<FactorioRecipes.QueueStep> queue = QUEUES.get(uuid);
        if (queue == null || queue.isEmpty()) {
            ServerPlayNetworking.send(player, new SyncCraftQueuePayload(List.of(), List.of(), 0, 1));
            return;
        }

        List<String> ids = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();

        for (FactorioRecipes.QueueStep step : queue) {
            String rId = step.recipe().id();
            int outCount = step.recipe().output().getCount();
            if (!ids.isEmpty() && ids.get(ids.size() - 1).equals(rId)) {
                int lastIdx = counts.size() - 1;
                counts.set(lastIdx, counts.get(lastIdx) + outCount);
            } else if (ids.size() < 64) {
                ids.add(rId);
                counts.add(outCount);
            }
        }

        int prog = ACTIVE_PROGRESS.getOrDefault(uuid, 0);
        int max = Math.max(1, queue.get(0).recipe().timeTicks());
        ServerPlayNetworking.send(player, new SyncCraftQueuePayload(ids, counts, prog, max));
    }
}