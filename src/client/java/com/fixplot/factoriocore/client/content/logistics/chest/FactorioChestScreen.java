package com.fixplot.factoriocore.client.content.logistics.chest;

import com.fixplot.factoriocore.content.logistics.chest.FactorioChestScreenHandler;
import com.fixplot.factoriocore.network.ChangeStackSizePayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class FactorioChestScreen extends HandledScreen<FactorioChestScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 28.0f;
    private float previewZoom = 22.0f;
    private boolean draggingPreview = false;

    // Режим установки красного крестика ограничения слотов (как в Factorio)
    private boolean limitSelectionMode = false;

    public FactorioChestScreen(FactorioChestScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 220;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);

        // Счётчик открытых слотов под кнопкой [X]
        int unlocked = handler.getUnlockedSlots();
        String limitStr = unlocked + " / " + handler.totalSlots;
        int strW = this.textRenderer.getWidth(limitStr);
        int color = (unlocked < handler.totalSlots) ? 0xFFFF6666 : 0xFFAAAAAA;
        context.drawText(this.textRenderer, limitStr, 43 - strW / 2, 114, color, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // 1. Внешний корпус окна в стиле Factorio
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF161616);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF313234);

        // Шапка с ребристыми полосками
        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 16, 0xFF242527);
        int titleEnd = x + 14 + textRenderer.getWidth(this.title);
        for (int gx = titleEnd; gx < x + backgroundWidth - 24; gx += 3) {
            context.fill(gx, y + 5, gx + 1, y + 14, 0xFF1A1B1C);
        }

        // Зелёный диод состояния
        int ledX = x + backgroundWidth - 19;
        context.fill(ledX, y + 5, ledX + 10, y + 14, 0xFF111111);
        context.fill(ledX + 1, y + 6, ledX + 9, y + 13, 0xFF115511);
        context.fill(ledX + 2, y + 7, ledX + 8, y + 12, 0xFF44FF44);

        // 2. Слева сверху: 3D-окно предпросмотра сундука (X: 10..76, Y: 20..86)
        int viewLeft = x + 10;
        int viewTop = y + 20;
        int viewRight = x + 76;
        int viewBottom = y + 86;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderChest3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 6);
        context.disableScissor();

        // 3. Под 3D-окном: Фирменная кнопка красного крестика [X] из Factorio (X: 34, Y: 91)
        int redBtnX = x + 34;
        int redBtnY = y + 91;
        drawRedXButton(context, redBtnX, redBtnY, mouseX, mouseY);

        // 4. Справа: Сетка слотов сундука (8 колонок x 2/4/6 рядов)
        int unlocked = handler.getUnlockedSlots();
        int hoveredSlotIndex = getHoveredChestSlotIndex(mouseX, mouseY);
        int previewLimit = (this.limitSelectionMode && hoveredSlotIndex >= 0) ? hoveredSlotIndex : unlocked;

        int gridX = x + FactorioChestScreenHandler.GRID_START_X - 1;
        int gridY = y + handler.gridStartY - 1;

        // Тёмная подложка под блок слотов сундука
        context.fill(gridX - 2, gridY - 2,
                gridX + FactorioChestScreenHandler.COLUMNS * 18 + 2,
                gridY + handler.rows * 18 + 2, 0xFF18181A);

        for (int r = 0; r < handler.rows; r++) {
            for (int c = 0; c < FactorioChestScreenHandler.COLUMNS; c++) {
                int slotIdx = c + r * FactorioChestScreenHandler.COLUMNS;
                int sx = gridX + c * 18;
                int sy = gridY + r * 18;

                boolean isLocked = (slotIdx >= previewLimit);
                drawChestSlot(context, sx, sy, isLocked);
            }
        }

        drawDivider(context, x + 8, y + 131, backgroundWidth - 16);

        // 5. Слоты инвентаря игрока
        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawChestSlot(context, invFrameX + col * 18, y + 137 + row * 18, false);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawChestSlot(context, invFrameX + col * 18, y + 195, false);
        }
    }

    private void drawChestSlot(DrawContext context, int slotX, int slotY, boolean lockedByRedX) {
        context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF18181A);
        if (lockedByRedX) {
            // Красный заблокированный слот в стиле Factorio
            context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF7A2222);
            context.fill(slotX + 2, slotY + 2, slotX + 16, slotY + 16, 0xFF5E1919);
            drawSmallX(context, slotX + 5, slotY + 5, 0xFFCC4444);
        } else {
            context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF3A3C3E);
        }
    }

    private void drawRedXButton(DrawContext context, int bx, int by, int mouseX, int mouseY) {
        boolean hovered = mouseX >= bx && mouseX <= bx + 18 && mouseY >= by && mouseY <= by + 18;
        boolean hasLimit = handler.getUnlockedSlots() < handler.totalSlots;

        int borderCol = this.limitSelectionMode ? 0xFFFFCC44 : 0xFF141414;
        int bgCol = this.limitSelectionMode ? 0xFFB83227 : (hovered ? 0xFF8F2820 : (hasLimit ? 0xFF752019 : 0xFF3A3C3E));

        context.fill(bx, by, bx + 18, by + 18, borderCol);
        context.fill(bx + 1, by + 1, bx + 17, by + 17, bgCol);
        drawSmallX(context, bx + 5, by + 5, 0xFFFF5555);
    }

    private void drawSmallX(DrawContext context, int ox, int oy, int color) {
        for (int i = 0; i < 8; i++) {
            context.fill(ox + i, oy + i, ox + i + 2, oy + i + 1, color);
            context.fill(ox + 6 - i, oy + i, ox + 8 - i, oy + i + 1, color);
        }
    }

    private int getHoveredChestSlotIndex(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int gridX = x + FactorioChestScreenHandler.GRID_START_X - 1;
        int gridY = y + handler.gridStartY - 1;

        if (mouseX < gridX || mouseX >= gridX + FactorioChestScreenHandler.COLUMNS * 18
                || mouseY < gridY || mouseY >= gridY + handler.rows * 18) {
            return -1;
        }
        int col = (int) ((mouseX - gridX) / 18);
        int row = (int) ((mouseY - gridY) / 18);
        return col + row * FactorioChestScreenHandler.COLUMNS;
    }

    private void renderChest3DPreview(DrawContext context, int centerX, int centerY) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(centerX, centerY, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));

        BlockState previewState = handler.chestEntity != null
                ? handler.chestEntity.getCachedState()
                : ModBlocks.WOODEN_CHEST.getDefaultState();

        matrices.translate(-0.5f, -0.45f, -0.5f);
        client.getBlockRenderManager().renderBlockAsEntity(
                previewState,
                matrices,
                context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
        context.draw();
    }

    private void drawCheckerboard(DrawContext context, int left, int top, int right, int bottom, int cellSize) {
        for (int py = top; py < bottom; py += cellSize) {
            for (int px = left; px < right; px += cellSize) {
                int gx = (px - left) / cellSize;
                int gy = (py - top) / cellSize;
                int color = ((gx + gy) % 2 == 0) ? 0xFF262729 : 0xFF2E3032;
                context.fill(px, py, Math.min(px + cellSize, right), Math.min(py + cellSize, bottom), color);
            }
        }
    }

    private void drawDivider(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0xFF202122);
        context.fill(x, y + 1, x + width, y + 2, 0xFF3E4042);
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        return mouseX >= x + 10 && mouseX <= x + 76 && mouseY >= y + 20 && mouseY <= y + 86;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 2.5f, 12.0f, 42.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        if ((button == 0 || button == 1) && isInsideViewport(mouseX, mouseY)) {
            this.draggingPreview = true;
            return true;
        }

        // Клик по красной кнопке [X] (X: 34..52, Y: 91..109)
        int redBtnX = x + 34;
        int redBtnY = y + 91;
        if (mouseX >= redBtnX && mouseX <= redBtnX + 18 && mouseY >= redBtnY && mouseY <= redBtnY + 18) {
            if (button == 1 || (this.limitSelectionMode && button == 0)) {
                // Повторный клик или ПКМ по кнопке [X] полностью разблокирует все слоты сундука!
                this.limitSelectionMode = false;
                ClientPlayNetworking.send(new ChangeStackSizePayload(handler.targetPos, handler.totalSlots));
            } else if (button == 0) {
                this.limitSelectionMode = true;
            }
            return true;
        }

        // Если активен режим красного крестика [X] ИЛИ зажат Shift + ПКМ по слоту сундука
        int hoveredSlot = getHoveredChestSlotIndex(mouseX, mouseY);
        if (hoveredSlot >= 0) {
            if (this.limitSelectionMode && button == 0) {
                this.limitSelectionMode = false;
                ClientPlayNetworking.send(new ChangeStackSizePayload(handler.targetPos, hoveredSlot));
                return true;
            }
            if (hasShiftDown() && button == 1) {
                int nextLimit = (handler.getUnlockedSlots() == hoveredSlot) ? handler.totalSlots : hoveredSlot;
                ClientPlayNetworking.send(new ChangeStackSizePayload(handler.targetPos, nextLimit));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingPreview && (button == 0 || button == 1)) {
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // Если включён режим выбора лимита [X] — рисуем красный крестик рядом с курсором мыши!
        if (this.limitSelectionMode) {
            drawSmallX(context, mouseX + 6, mouseY + 6, 0xFFFF4444);
        }

        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        if (mouseX >= x + 34 && mouseX <= x + 52 && mouseY >= y + 91 && mouseY <= y + 109) {
            context.drawTooltip(this.textRenderer,
                    Text.translatable("gui.factoriocore.chest_limit_tooltip"), mouseX, mouseY);
        } else {
            drawMouseoverTooltip(context, mouseX, mouseY);
        }
    }
}
