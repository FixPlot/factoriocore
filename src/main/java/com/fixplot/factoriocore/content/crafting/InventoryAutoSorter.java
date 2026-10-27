package com.fixplot.factoriocore.content.crafting;

import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InventoryAutoSorter {

    public static void tickPlayerInventory(ServerPlayerEntity player) {
        // Не сортируем, пока игрок держит предмет на курсоре мыши или использует предмет
        if (!player.currentScreenHandler.getCursorStack().isEmpty() || player.isUsingItem()) {
            return;
        }

        PlayerInventory inv = player.getInventory();
        boolean changed = false;

        // Шаг 1. Объединяем все неполные стаки одинаковых предметов по всему инвентарю (0..35)
        for (int i = 0; i < 36; i++) {
            ItemStack target = inv.main.get(i);
            if (target.isEmpty() || target.getCount() >= target.getMaxCount()) {
                continue;
            }
            for (int j = i + 1; j < 36; j++) {
                ItemStack source = inv.main.get(j);
                if (!source.isEmpty() && ItemStack.areItemsAndComponentsEqual(target, source)) {
                    int space = target.getMaxCount() - target.getCount();
                    int move = Math.min(space, source.getCount());
                    if (move > 0) {
                        target.increment(move);
                        source.decrement(move);
                        if (source.isEmpty()) {
                            inv.main.set(j, ItemStack.EMPTY);
                        }
                        changed = true;
                        if (target.getCount() >= target.getMaxCount()) {
                            break;
                        }
                    }
                }
            }
        }

        // Шаг 2. Сортируем и уплотняем слоты инвентаря (9..35), а если в хотбаре (0..8) нет предметов или открыто окно — уплотняем без дыр
        if (sortMainRange(inv, 9, 36)) {
            changed = true;
        }

        if (changed) {
            inv.markDirty();
            player.currentScreenHandler.sendContentUpdates();
            player.playerScreenHandler.sendContentUpdates();
        }
    }

    private static boolean sortMainRange(PlayerInventory inv, int startInclusive, int endExclusive) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            ItemStack s = inv.main.get(i);
            if (!s.isEmpty()) {
                nonEmpty.add(s.copy());
            }
        }

        if (nonEmpty.isEmpty()) {
            return false;
        }

        nonEmpty.sort(Comparator
                .comparingInt(InventoryAutoSorter::getCategoryOrder)
                .thenComparingInt((ItemStack s) -> getFactorioRecipeOrder(s.getItem())).thenComparing(s -> Registries.ITEM.getId(s.getItem()).toString())
                .thenComparing((ItemStack s) -> -s.getCount()));

        // Проверяем, отличается ли новый порядок от текущего (чтобы не слать лишние пакеты, если всё уже отсортировано)
        boolean anyDiff = false;
        for (int i = startInclusive; i < endExclusive; i++) {
            int listIdx = i - startInclusive;
            ItemStack expected = (listIdx < nonEmpty.size()) ? nonEmpty.get(listIdx) : ItemStack.EMPTY;
            ItemStack current = inv.main.get(i);
            if (!ItemStack.areEqual(current, expected)) {
                anyDiff = true;
                break;
            }
        }

        if (!anyDiff) {
            return false;
        }

        for (int i = startInclusive; i < endExclusive; i++) {
            int listIdx = i - startInclusive;
            inv.main.set(i, (listIdx < nonEmpty.size()) ? nonEmpty.get(listIdx) : ItemStack.EMPTY);
        }
        return true;
    }

        public static int getFactorioRecipeOrder(Item item) {
        FactorioRecipes.init();
        for (int i = 0; i < FactorioRecipes.ALL.size(); i++) {
            if (FactorioRecipes.ALL.get(i).output().isOf(item)) {
                return i;
            }
        }
        return 1000;
    }

    public static int getCategoryOrder(ItemStack stack) {
        Item item = stack.getItem();
        FactorioRecipes.Recipe r = FactorioRecipes.getByOutput(item);
        if (r != null) {
            return switch (r.category()) {
                case LOGISTICS -> 1;
                case PRODUCTION -> 2;
                case INTERMEDIATE -> 3;
                case COMBAT -> 4;
            };
        }
        if (item == ModItems.STEEL_PLATE || item == ModItems.IRON_GEAR_WHEEL
                || item == ModItems.COPPER_CABLE || item == ModItems.ELECTRONIC_CIRCUIT || item == ModItems.IRON_STICK) {
            return 3;
        }
        if (item instanceof BlockItem) {
            return 5;
        }
        return 6;
    }
}