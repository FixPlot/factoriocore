package com.fixplot.factoriocore.client.content.crafting;

import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.network.RequestCraftPayload;
import com.fixplot.factoriocore.network.SyncCraftQueuePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class ClientCraftingQueueHud {

    private static List<String> recipeIds = new ArrayList<>();
    private static List<Integer> counts = new ArrayList<>();
    private static int currentProgress = 0;
    private static int currentMax = 1;
    private static boolean expandedInInventory = false;

    public static void updateFromPacket(SyncCraftQueuePayload payload) {
        recipeIds = payload.recipeIds();
        counts = payload.counts();
        currentProgress = payload.currentProgressTicks();
        currentMax = Math.max(1, payload.currentMaxTicks());
        if (recipeIds.size() <= 1) {
            expandedInInventory = false;
        }
    }

    /** Очистка при дисконнекте/смене мира — очередь из старого мира не должна рендериться. */
    public static void clear() {
        recipeIds.clear();
        counts.clear();
        currentProgress = 0;
        currentMax = 1;
        expandedInInventory = false;
    }

    /**
     * Считает, сколько слотов очереди помещается слева от хотбара Майнкрафта,
     * чтобы очередь НИКОГДА не залезала под панель быстрого доступа!
     */
    private static int getMaxColsLeftOfHotbar(int screenW) {
        int hotbarLeft = (screenW / 2) - 95;
        int availPx = Math.max(48, hotbarLeft - 8);
        return MathHelper.clamp(availPx / 24, 2, 7);
    }

    public static void renderBottomLeftQueue(DrawContext context, boolean isInventoryScreen) {
        if (recipeIds.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        FactorioRecipes.init();
        TextRenderer tr = client.textRenderer;
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();

        int boxSize = 22;
        int step = boxSize + 2; // 24px
        int startX = 6;
        int baseY = screenH - boxSize - 6;

        int maxCols = getMaxColsLeftOfHotbar(screenW);
        int totalItems = recipeIds.size();

        // В инвентаре оставляем место под кнопку-стрелочку [>>], если элементов больше, чем помещается в 1 ряд
        boolean canExpand = totalItems > (maxCols - 1);
        boolean showExpanded = isInventoryScreen && expandedInInventory && canExpand;

        if (!showExpanded) {
            // Свёрнутый однострочный режим (строго слева от хотбара!)
            int visibleSlots = canExpand ? (maxCols - 1) : Math.min(totalItems, maxCols);

            for (int i = 0; i < visibleSlots && i < totalItems; i++) {
                int bx = startX + i * step;
                drawQueueCell(context, tr, i, bx, baseY, boxSize);
            }

            if (canExpand) {
                int btnX = startX + visibleSlots * step;
                if (isInventoryScreen) {
                    // В инвентаре рисуем интерактивную кнопку со стрелочкой [>>] как в Factorio
                    drawExpandArrowButton(context, tr, btnX, baseY, boxSize, ">>", totalItems - visibleSlots);
                } else {
                    // В обычном HUD рисуем компактный индикатор "+N" остальных элементов очереди
                    int hiddenCount = totalItems - visibleSlots;
                    drawHiddenCountBadge(context, tr, btnX, baseY, boxSize, "+" + hiddenCount);
                }
            }
        } else {
            // Раскрытый многострочный режим в инвентаре: ряды поднимаются снизу вверх!
            for (int i = 0; i < totalItems; i++) {
                int col = i % maxCols;
                int rowFromBottom = i / maxCols;
                int bx = startX + col * step;
                int by = baseY - rowFromBottom * step;
                drawQueueCell(context, tr, i, bx, by, boxSize);
            }

            // Кнопка сворачивания [<<] в конце верхнего ряда
            int collapseCol = totalItems % maxCols;
            int collapseRow = totalItems / maxCols;
            int btnX = startX + collapseCol * step;
            int btnY = baseY - collapseRow * step;
            drawExpandArrowButton(context, tr, btnX, btnY, boxSize, "<<", 0);
        }
    }

    private static void drawQueueCell(DrawContext context, TextRenderer tr, int index, int bx, int by, int boxSize) {
        FactorioRecipes.Recipe r = FactorioRecipes.getById(recipeIds.get(index));
        if (r == null) return;

        context.fill(bx, by, bx + boxSize, by + boxSize, 0xF0141414);
        context.fill(bx + 1, by + 1, bx + boxSize - 1, by + boxSize - 1, (index == 0) ? 0xF04A3B22 : 0xF02C2D30);

        if (index == 0) {
            float ratio = MathHelper.clamp(currentProgress / (float) currentMax, 0.0f, 1.0f);
            int fillH = Math.round(ratio * (boxSize - 2));
            if (fillH > 0) {
                context.fill(bx + 1, by + boxSize - 1 - fillH, bx + boxSize - 1, by + boxSize - 1, 0xCCDF8B1E);
            }
        }

        context.drawItem(r.output(), bx + 3, by + 3);

        int cnt = (index < counts.size()) ? counts.get(index) : 1;
        String cntStr = String.valueOf(cnt);
        int tw = tr.getWidth(cntStr);
        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 200.0f);
        context.drawText(tr, cntStr, bx + boxSize - tw - 1, by + boxSize - 9, 0xFFFFFFFF, true);
        context.getMatrices().pop();
    }

    private static void drawExpandArrowButton(DrawContext context, TextRenderer tr, int bx, int by,
                                              int boxSize, String arrow, int extraCount) {
        context.fill(bx, by, bx + boxSize, by + boxSize, 0xF0141414);
        context.fill(bx + 1, by + 1, bx + boxSize - 1, by + boxSize - 1, 0xF03B3D42);
        context.fill(bx + 1, by + 1, bx + boxSize - 1, by + 3, 0xFFE59524);

        int aw = tr.getWidth(arrow);
        context.drawText(tr, arrow, bx + (boxSize - aw) / 2, by + (extraCount > 0 ? 4 : 7), 0xFFFFCC66, true);

        if (extraCount > 0) {
            String plusStr = "+" + extraCount;
            int pw = tr.getWidth(plusStr);
            context.drawText(tr, plusStr, bx + (boxSize - pw) / 2, by + 13, 0xFFCCCCCC, false);
        }
    }

    private static void drawHiddenCountBadge(DrawContext context, TextRenderer tr, int bx, int by,
                                             int boxSize, String label) {
        context.fill(bx, by, bx + boxSize, by + boxSize, 0xD0141414);
        context.fill(bx + 1, by + 1, bx + boxSize - 1, by + boxSize - 1, 0xD0252629);
        int lw = tr.getWidth(label);
        context.drawText(tr, label, bx + (boxSize - lw) / 2, by + 7, 0xFFAAAAAA, true);
    }

    public static boolean handleMouseClick(double mouseX, double mouseY, int button, int screenW, int screenH) {
        if (recipeIds.isEmpty()) return false;

        int boxSize = 22;
        int step = boxSize + 2;
        int startX = 6;
        int baseY = screenH - boxSize - 6;

        int maxCols = getMaxColsLeftOfHotbar(screenW);
        int totalItems = recipeIds.size();
        boolean canExpand = totalItems > (maxCols - 1);
        boolean showExpanded = expandedInInventory && canExpand;

        if (!showExpanded) {
            int visibleSlots = canExpand ? (maxCols - 1) : Math.min(totalItems, maxCols);

            // Клик по кнопке раскрытия очереди [>>]
            if (canExpand) {
                int btnX = startX + visibleSlots * step;
                if (mouseX >= btnX && mouseX <= btnX + boxSize && mouseY >= baseY && mouseY <= baseY + boxSize) {
                    expandedInInventory = true;
                    return true;
                }
            }

            // Клик по видимой ячейке для отмены
            for (int i = 0; i < visibleSlots && i < totalItems; i++) {
                int bx = startX + i * step;
                if (mouseX >= bx && mouseX <= bx + boxSize && mouseY >= baseY && mouseY <= baseY + boxSize) {
                    ClientPlayNetworking.send(new RequestCraftPayload(recipeIds.get(i), -1));
                    return true;
                }
            }
        } else {
            // Клик по кнопке сворачивания [<<]
            int collapseCol = totalItems % maxCols;
            int collapseRow = totalItems / maxCols;
            int btnX = startX + collapseCol * step;
            int btnY = baseY - collapseRow * step;
            if (mouseX >= btnX && mouseX <= btnX + boxSize && mouseY >= btnY && mouseY <= btnY + boxSize) {
                expandedInInventory = false;
                return true;
            }

            // Клик по любой ячейке в раскрытой сетке
            for (int i = 0; i < totalItems; i++) {
                int col = i % maxCols;
                int rowFromBottom = i / maxCols;
                int bx = startX + col * step;
                int by = baseY - rowFromBottom * step;
                if (mouseX >= bx && mouseX <= bx + boxSize && mouseY >= by && mouseY <= by + boxSize) {
                    ClientPlayNetworking.send(new RequestCraftPayload(recipeIds.get(i), -1));
                    return true;
                }
            }
        }
        return false;
    }
}