package com.fixplot.factoriocore.client.content.production.assembler;

import com.fixplot.factoriocore.client.content.crafting.FactorioCharacterScreen;
import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.content.production.assembler.AssemblerScreenHandler;
import com.fixplot.factoriocore.network.SetAssemblerRecipePayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModItems;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class AssemblerScreen extends HandledScreen<AssemblerScreenHandler> {

    private boolean recipeSelectionMode = false;
    private boolean initialSyncChecked = false;
    private FactorioRecipes.Category activeCategory = FactorioRecipes.Category.LOGISTICS;

    private float previewYaw = -35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 12.0f;
    private boolean draggingPreview = false;

    public AssemblerScreen(AssemblerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 430;
        this.backgroundHeight = 224;
    }

    @Override
    protected void init() {
        super.init();
        if (handler.isSynced()) {
            this.initialSyncChecked = true;
            setRecipeSelectionMode(handler.getRecipeId().isEmpty());
        } else {
            setRecipeSelectionMode(true);
        }
    }

    private void setRecipeSelectionMode(boolean selectionMode) {
        this.recipeSelectionMode = selectionMode;
        this.handler.setSlotsVisible(!selectionMode);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Внешний корпус окна
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF141414);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF2D2E30);

        // Верхняя шапка окна
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 17, 0xFF222325);
        Text titleText = recipeSelectionMode ? Text.translatable("gui.factoriocore.select_recipe") : this.title;
        context.drawText(this.textRenderer, titleText, x + 8, y + 6, 0xFFFFCC66, true);

        // Ребра в шапке
        int startRibs = x + 14 + this.textRenderer.getWidth(titleText);
        int endRibs = x + backgroundWidth - 24;
        for (int rx = startRibs; rx < endRibs; rx += 3) {
            context.fill(rx, y + 5, rx + 1, y + 14, 0xFF17181A);
        }

        // Кнопка закрытия [X]
        int closeX = x + backgroundWidth - 17;
        boolean hovClose = mouseX >= closeX && mouseX <= closeX + 13 && mouseY >= y + 4 && mouseY <= y + 16;
        context.fill(closeX, y + 4, closeX + 13, y + 16, 0xFF141414);
        context.fill(closeX + 1, y + 5, closeX + 12, y + 15, hovClose ? 0xFFB83227 : 0xFF3A3C3F);
        context.drawText(this.textRenderer, "x", closeX + 4, y + 6, 0xFFFFFFFF, false);

        // ================= РЕЖИМ ВЫБОРА РЕЦЕПТА =================
        if (recipeSelectionMode) {
            int tabY = y + 20;
            int tabW = (backgroundWidth - 16) / 4;
            int tabH = 24;

            FactorioRecipes.Category[] cats = FactorioRecipes.Category.values();
            ItemStack[] tabIcons = {
                    new ItemStack(ModBlocks.BELT),
                    new ItemStack(ModBlocks.STONE_FURNACE),
                    new ItemStack(ModItems.ELECTRONIC_CIRCUIT),
                    new ItemStack(ModItems.REPAIR_PACK)
            };

            for (int i = 0; i < 4; i++) {
                int tx = x + 8 + i * tabW;
                boolean selected = (cats[i] == this.activeCategory);
                boolean hovered = mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + tabH;

                int tabBg = selected ? 0xFF6B6D70 : (hovered ? 0xFF4A4C50 : 0xFF383A3D);
                context.fill(tx, tabY, tx + tabW - 1, tabY + tabH, 0xFF18181A);
                context.fill(tx + 1, tabY + 1, tx + tabW - 2, tabY + tabH - (selected ? 0 : 1), tabBg);

                context.getMatrices().push();
                context.getMatrices().translate(tx + tabW / 2.0f - 8.0f, tabY + 4.0f, 100.0f);
                context.drawItem(tabIcons[i], 0, 0);
                context.getMatrices().pop();
            }

            int startX = x + 12;
            int startY = y + 48;
            int rows = 9;
            int cols = 22;

            context.fill(startX - 2, startY - 2, startX + cols * 18 + 2, startY + rows * 18 + 2, 0xFF18181A);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int sx = startX + c * 18;
                    int sy = startY + r * 18;
                    context.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1B1D);
                    context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF242528);
                }
            }

            List<List<FactorioRecipes.Recipe>> compactedRows = FactorioRecipes.getCompactedRows(this.activeCategory, "", cols);
            for (int r = 0; r < Math.min(rows, compactedRows.size()); r++) {
                List<FactorioRecipes.Recipe> rowRecipes = compactedRows.get(r);
                for (int c = 0; c < Math.min(cols, rowRecipes.size()); c++) {
                    FactorioRecipes.Recipe recipe = rowRecipes.get(c);
                    int sx = startX + c * 18;
                    int sy = startY + r * 18;
                    boolean hovered = mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18;
                    context.fill(sx, sy, sx + 18, sy + 18, hovered ? 0xFFE59524 : 0xFF141414);
                    context.fill(sx + 1, sy + 1, sx + 17, sy + 17, hovered ? 0xFF525458 : 0xFF3A3C3F);
                    context.drawItem(recipe.output(), sx + 1, sy + 1);
                }
            }
            return;
        }

        // ================= ОСНОВНОЙ ДВУХПАНЕЛЬНЫЙ ЭКРАН FACTORIO =================
        // ЛЕВАЯ ПАНЕЛЬ: "Персонаж"
        int leftX = x + 6;
        int leftW = 180;
        int panelH = backgroundHeight - 26;
        context.fill(leftX, y + 20, leftX + leftW, y + 20 + panelH, 0xFF141414);
        context.fill(leftX + 1, y + 21, leftX + leftW - 1, y + 19 + panelH, 0xFF242527);

        // Шапка "Персонаж"
        context.fill(leftX + 2, y + 22, leftX + leftW - 2, y + 34, 0xFF1C1D1E);
        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.character"), leftX + 6, y + 24, 0xFFE6E6E6, false);

        // Слоты инвентаря игрока (3 ряда по 9)
        int invStartX = x + 12;
        int invStartY = y + 36;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlotFrame(context, invStartX + c * 18, invStartY + r * 18);
            }
        }

        // Разделительная черта перед хотбаром
        context.fill(leftX + 4, y + 92, leftX + leftW - 4, y + 93, 0xFF1A1B1C);
        context.fill(leftX + 4, y + 93, leftX + leftW - 4, y + 94, 0xFF3A3C3F);

        // Хотбар (1 ряд по 9)
        for (int c = 0; c < 9; c++) {
            drawSlotFrame(context, invStartX + c * 18, y + 96);
        }

        // Декоративные заблокированные слоты под хотбаром
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 9; c++) {
                drawGhostSlot(context, invStartX + c * 18, y + 118 + r * 18);
            }
        }

        // ПРАВАЯ ПАНЕЛЬ: Сборочный автомат
        int rightX = x + 192;
        int rightW = 232;
        context.fill(rightX, y + 20, rightX + rightW, y + 20 + panelH, 0xFF141414);
        context.fill(rightX + 1, y + 21, rightX + rightW - 1, y + 19 + panelH, 0xFF28292C);

        // Статус автомата вверху (LED + локализованный текст)
        Text statusText;
        int ledOuter, ledInner;
        if (!handler.isPowered()) {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.no_power", "Нет питания");
            ledOuter = 0xFF661111;
            ledInner = 0xFFFF3333;
        } else if (handler.getRecipeId().isEmpty()) {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.no_recipe", "Рецепт не выбран");
            ledOuter = 0xFF664400;
            ledInner = 0xFFFFAA00;
        } else if (handler.isWorking()) {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.working", "Работает");
            ledOuter = 0xFF115511;
            ledInner = 0xFF44FF44;
        } else {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.missing_ingredients", "Не хватает ингредиентов");
            ledOuter = 0xFF661111;
            ledInner = 0xFFFF3333;
        }

        int ledX = rightX + 6;
        int ledY = y + 25;
        context.fill(ledX, ledY, ledX + 6, ledY + 6, 0xFF111111);
        context.fill(ledX + 1, ledY + 1, ledX + 5, ledY + 5, ledOuter);
        context.fill(ledX + 2, ledY + 2, ledX + 4, ledY + 4, ledInner);
        context.drawText(this.textRenderer, statusText, rightX + 16, y + 24, 0xFFE6E6E6, false);

        // 3D-окно предпросмотра сборщика с шахматным фоном
        int viewLeft = rightX + 6;
        int viewTop = y + 34;
        int viewRight = rightX + rightW - 6;
        int viewBottom = y + 110;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderAssembler3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 8, delta);
        context.disableScissor();

        // СТРОКА РЕЦЕПТА: [Иконка рецепта] + Название рецепта + Кнопка [⚙]
        int recBtnX = rightX + 6;
        int recBtnY = y + 116;
        boolean hovRecBtn = mouseX >= recBtnX && mouseX <= recBtnX + 22 && mouseY >= recBtnY && mouseY <= recBtnY + 22;
        context.fill(recBtnX, recBtnY, recBtnX + 22, recBtnY + 22, 0xFF141414);
        context.fill(recBtnX + 1, recBtnY + 1, recBtnX + 21, recBtnY + 21, hovRecBtn ? 0xFF4A4C50 : 0xFF35373A);

        FactorioRecipes.Recipe activeRec = FactorioRecipes.getById(handler.getRecipeId());
        if (activeRec != null) {
            context.drawItem(activeRec.output(), recBtnX + 3, recBtnY + 3);
            context.drawText(this.textRenderer, activeRec.output().getName(), rightX + 34, y + 123, 0xFFE6E6E6, false);
        } else {
            context.drawText(this.textRenderer,
                    Text.translatableWithFallback("gui.factoriocore.choose_recipe_prompt", "Выберите рецепт..."),
                    rightX + 34, y + 123, 0xFF888A8E, false);
        }

        // Шестеренка смены рецепта справа
        int gearX = rightX + rightW - 28;
        int gearY = y + 116;
        boolean hovGear = mouseX >= gearX && mouseX <= gearX + 22 && mouseY >= gearY && mouseY <= gearY + 22;
        context.fill(gearX, gearY, gearX + 22, gearY + 22, 0xFF141414);
        context.fill(gearX + 1, gearY + 1, gearX + 21, gearY + 21, hovGear ? 0xFFE59524 : 0xFF383A3D);
        drawGearIcon(context, gearX + 3, gearY + 3, hovGear ? 0xFF141414 : 0xFFE6E6E6);

        // СТРОКА КРАФТА: [Слоты сырья] -> [Зеленая полоса прогресса] -> [Слот выхода]
        int slotRowY = y + 144;
        int ingCount = activeRec != null ? Math.min(4, activeRec.ingredients().size()) : 0;
        for (int i = 0; i < ingCount; i++) {
            int sx = rightX + 6 + i * 19;
            FactorioRecipes.Ingredient ing = activeRec.ingredients().get(i);
            ItemStack currentStack = handler.getSlot(i).getStack();
            boolean enough = !currentStack.isEmpty() && currentStack.getCount() >= ing.count();

            context.fill(sx, slotRowY, sx + 18, slotRowY + 18, 0xFF141414);
            context.fill(sx + 1, slotRowY + 1, sx + 17, slotRowY + 17, enough ? 0xFF3A3C3E : 0xFF5A2424);

            if (currentStack.isEmpty()) {
                ItemStack ghost = new ItemStack(ing.item());
                context.drawItem(ghost, sx + 1, slotRowY + 1);
                context.getMatrices().push();
                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                context.fill(sx + 1, slotRowY + 1, sx + 17, slotRowY + 17, 0x96481C1C);
                context.getMatrices().pop();
            }
        }

        // Зеленая шкала прогресса
        int barX = rightX + 85;
        int barY = y + 147;
        int barW = 96;
        int barH = 12;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF141414);
        context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF222325);

        float ratio = (handler.getMaxProgress() > 0 && handler.getProgress() > 0)
                ? MathHelper.clamp(handler.getProgress() / (float) handler.getMaxProgress(), 0.0f, 1.0f) : 0.0f;
        int fillW = Math.round(ratio * (barW - 2));
        if (fillW > 0) {
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFF38B828);
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + 4, 0xFF5CE64C);
        }

        String pctStr = Math.round(ratio * 100.0f) + "%";
        int pctW = this.textRenderer.getWidth(pctStr);
        context.drawText(this.textRenderer, pctStr, barX + (barW - pctW) / 2, barY + 2, 0xFFFFFFFF, true);

        // Слот готовой продукции
        int outX = rightX + rightW - 24;
        drawSlotFrame(context, outX, slotRowY);
        if (activeRec != null && handler.getSlot(4).getStack().isEmpty()) {
            context.drawItem(activeRec.output(), outX + 1, slotRowY + 1);
            context.getMatrices().push();
            context.getMatrices().translate(0.0f, 0.0f, 200.0f);
            context.fill(outX + 1, slotRowY + 1, outX + 17, slotRowY + 17, 0x9A28292C);
            context.getMatrices().pop();
        }

        // Нижняя декоративная панель для модулей
        context.fill(rightX + 4, y + 168, rightX + rightW - 4, y + 169, 0xFF1A1B1C);
        context.fill(rightX + 4, y + 169, rightX + rightW - 4, y + 170, 0xFF3A3C3F);

        for (int m = 0; m < 4; m++) {
            drawGhostSlot(context, rightX + 6 + m * 19, y + 174);
        }
        for (int gy = y + 198; gy <= y + 210; gy += 4) {
            context.fill(rightX + 6, gy, rightX + rightW - 6, gy + 1, 0xFF202123);
        }
    }

    private void drawSlotFrame(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF141414);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3C3E);
    }

    private void drawGhostSlot(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1B1C);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF222325);
    }

    private void drawCheckerboard(DrawContext context, int left, int top, int right, int bottom) {
        int cellSize = 8;
        for (int py = top; py < bottom; py += cellSize) {
            for (int px = left; px < right; px += cellSize) {
                int gx = (px - left) / cellSize;
                int gy = (py - top) / cellSize;
                int color = ((gx + gy) % 2 == 0) ? 0xFF262729 : 0xFF2E3032;
                context.fill(px, py, Math.min(px + cellSize, right), Math.min(py + cellSize, bottom), color);
            }
        }
    }

    private void drawGearIcon(DrawContext context, int gx, int gy, int col) {
        context.fill(gx + 4, gy + 2, gx + 12, gy + 14, col);
        context.fill(gx + 2, gy + 4, gx + 14, gy + 12, col);
        context.fill(gx + 6, gy + 6, gx + 10, gy + 10, 0xFF141414);
    }

    private void renderAssembler3DPreview(DrawContext context, int centerX, int centerY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(centerX, centerY + 8, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));

        matrices.translate(-0.5f, -1.0f, -0.5f);

        float angle = (handler.isWorking() && client.world != null)
                ? (client.world.getTime() + delta) * 10.0f : 0.0f;
        AssemblerRenderer.renderAssemblerModel(
                matrices,
                context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                angle
        );

        matrices.pop();
        context.draw();
    }

    private boolean isInsidePreviewViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int rightX = x + 192;
        return mouseX >= rightX + 6 && mouseX <= rightX + 226 && mouseY >= y + 34 && mouseY <= y + 110;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!recipeSelectionMode && isInsidePreviewViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 1.5f, 6.0f, 28.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        if (recipeSelectionMode) {
            // Клик по кнопке [X]
            if (mouseX >= x + backgroundWidth - 17 && mouseX <= x + backgroundWidth - 4 && mouseY >= y + 4 && mouseY <= y + 16) {
                if (!handler.getRecipeId().isEmpty()) {
                    setRecipeSelectionMode(false);
                } else {
                    this.close();
                }
                return true;
            }

            // Клик по 4 вкладкам категорий
            int tabY = y + 20;
            int tabW = (backgroundWidth - 16) / 4;
            int tabH = 24;
            FactorioRecipes.Category[] cats = FactorioRecipes.Category.values();
            for (int i = 0; i < 4; i++) {
                int tx = x + 8 + i * tabW;
                if (mouseX >= tx && mouseX < tx + tabW && mouseY >= tabY && mouseY < tabY + tabH) {
                    this.activeCategory = cats[i];
                    return true;
                }
            }

            // Клик по рецепту
            int startX = x + 12;
            int startY = y + 48;
            int rows = 9;
            int cols = 22;

            List<List<FactorioRecipes.Recipe>> compactedRows = FactorioRecipes.getCompactedRows(this.activeCategory, "", cols);
            for (int r = 0; r < Math.min(rows, compactedRows.size()); r++) {
                List<FactorioRecipes.Recipe> rowRecipes = compactedRows.get(r);
                for (int c = 0; c < Math.min(cols, rowRecipes.size()); c++) {
                    FactorioRecipes.Recipe recipe = rowRecipes.get(c);
                    int sx = startX + c * 18;
                    int sy = startY + r * 18;
                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        handler.setClientRecipeId(recipe.id());
                        ClientPlayNetworking.send(new SetAssemblerRecipePayload(handler.getTargetPos(), recipe.id()));
                        setRecipeSelectionMode(false);
                        return true;
                    }
                }
            }
            return true;
        }

        // Закрыть окно
        if (mouseX >= x + backgroundWidth - 17 && mouseX <= x + backgroundWidth - 4 && mouseY >= y + 4 && mouseY <= y + 16) {
            this.close();
            return true;
        }

        // Перетаскивание 3D-модели
        if ((button == 0 || button == 1) && isInsidePreviewViewport(mouseX, mouseY)) {
            this.draggingPreview = true;
            return true;
        }

        // Клик по рецепту или шестеренке для смены рецепта
        int rightX = x + 192;
        int rightW = 232;
        int recBtnX = rightX + 6;
        int gearX = rightX + rightW - 28;
        if ((mouseX >= recBtnX && mouseX <= recBtnX + 22 && mouseY >= y + 116 && mouseY <= y + 138)
                || (mouseX >= gearX && mouseX <= gearX + 22 && mouseY >= y + 116 && mouseY <= y + 138)) {
            setRecipeSelectionMode(true);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingPreview) {
            this.draggingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingPreview) {
            this.previewYaw += (float) deltaX * 1.5f;
            this.previewPitch = MathHelper.clamp(this.previewPitch + (float) deltaY * 1.5f, -80.0f, 80.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.recipeSelectionMode && (keyCode == GLFW.GLFW_KEY_ESCAPE
                || (this.client != null && this.client.options.inventoryKey.matchesKey(keyCode, scanCode)))) {
            if (!handler.getRecipeId().isEmpty()) {
                setRecipeSelectionMode(false);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (!this.initialSyncChecked && handler.isSynced()) {
            this.initialSyncChecked = true;
            setRecipeSelectionMode(handler.getRecipeId().isEmpty());
        }
        this.handler.setSlotsVisible(!this.recipeSelectionMode);
        super.render(context, mouseX, mouseY, delta);

        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        FactorioRecipes.Recipe hoveredRecipe = null;

        if (recipeSelectionMode) {
            int startX = x + 12;
            int startY = y + 48;
            int rows = 9;
            int cols = 22;

            List<List<FactorioRecipes.Recipe>> compactedRows = FactorioRecipes.getCompactedRows(this.activeCategory, "", cols);
            outer:
            for (int r = 0; r < Math.min(rows, compactedRows.size()); r++) {
                List<FactorioRecipes.Recipe> rowRecipes = compactedRows.get(r);
                for (int c = 0; c < Math.min(cols, rowRecipes.size()); c++) {
                    int sx = startX + c * 18;
                    int sy = startY + r * 18;
                    if (mouseX >= sx && mouseX < sx + 18 && mouseY >= sy && mouseY < sy + 18) {
                        hoveredRecipe = rowRecipes.get(c);
                        break outer;
                    }
                }
            }
        } else {
            int rightX = x + 192;
            int recBtnX = rightX + 6;
            if (mouseX >= recBtnX && mouseX <= recBtnX + 22 && mouseY >= y + 116 && mouseY <= y + 138) {
                String rid = handler.getRecipeId();
                if (!rid.isEmpty()) {
                    hoveredRecipe = FactorioRecipes.getById(rid);
                }
            } else {
                FactorioRecipes.Recipe activeRec = FactorioRecipes.getById(handler.getRecipeId());
                boolean drewGhostTooltip = false;
                if (activeRec != null && this.handler.getCursorStack().isEmpty()) {
                    int slotRowY = y + 144;
                    int ingCount = Math.min(4, activeRec.ingredients().size());
                    for (int i = 0; i < ingCount; i++) {
                        int sx = rightX + 6 + i * 19;
                        if (mouseX >= sx && mouseX < sx + 18 && mouseY >= slotRowY && mouseY < slotRowY + 18 && handler.getSlot(i).getStack().isEmpty()) {
                            FactorioRecipes.Ingredient ing = activeRec.ingredients().get(i);
                            context.drawTooltip(this.textRenderer, List.of(
                                    Text.translatableWithFallback("gui.factoriocore.ghost_slot.filter", "Фантомный слот (фильтр рецепта)").formatted(Formatting.GRAY),
                                    Text.translatableWithFallback("gui.factoriocore.ghost_slot.required", "Требуется: ").formatted(Formatting.RED)
                                            .append(Text.literal(ing.count() + " \u00d7 " + ing.item().getName().getString()).formatted(Formatting.WHITE))
                            ), mouseX, mouseY);
                            drewGhostTooltip = true;
                            break;
                        }
                    }
                    int outX = rightX + 232 - 24;
                    if (!drewGhostTooltip && mouseX >= outX && mouseX < outX + 18 && mouseY >= slotRowY && mouseY < slotRowY + 18 && handler.getSlot(4).getStack().isEmpty()) {
                        context.drawTooltip(this.textRenderer, List.of(
                                Text.translatableWithFallback("gui.factoriocore.ghost_slot.output", "Готовая продукция").formatted(Formatting.GRAY),
                                activeRec.output().getName().copy().formatted(Formatting.YELLOW)
                        ), mouseX, mouseY);
                        drewGhostTooltip = true;
                    }
                }
                if (!drewGhostTooltip) {
                    drawMouseoverTooltip(context, mouseX, mouseY);
                }
            }
        }

        if (hoveredRecipe != null && this.client != null && this.client.player != null) {
            FactorioCharacterScreen.renderRecipeCard(
                    context, this.textRenderer, this.width, this.height,
                    hoveredRecipe, this.client.player.getInventory(), mouseX, mouseY, false
            );
        }
    }
}