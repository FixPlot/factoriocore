package com.fixplot.factoriocore.client.content.production.furnace;

import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class FactorioFurnaceScreen extends HandledScreen<FactorioFurnaceScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 13.5f;
    private boolean draggingPreview = false;

    public FactorioFurnaceScreen(FactorioFurnaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 220;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);
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

        // Индикатор состояния справа сверху
        boolean hasFuel = handler.hasFuel();
        boolean isSleeping = handler.isSleeping();
        int ledOuter = !hasFuel ? 0xFF661111 : (isSleeping ? 0xFF664400 : 0xFF115511);
        int ledInner = !hasFuel ? 0xFFFF3333 : (isSleeping ? 0xFFFFAA00 : 0xFF44FF44);
        int ledX = x + backgroundWidth - 19;
        context.fill(ledX, y + 5, ledX + 10, y + 14, 0xFF111111);
        context.fill(ledX + 1, y + 6, ledX + 9, y + 13, ledOuter);
        context.fill(ledX + 2, y + 7, ledX + 8, y + 12, ledInner);

        // 2. Окно 3D-предпросмотра с шахматной сеткой
        int viewLeft = x + 10;
        int viewTop = y + 18;
        int viewRight = x + backgroundWidth - 10;
        int viewBottom = y + 78;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderFurnace3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 12);
        context.disableScissor();

        // 3. Строка плавки: [Слот сырья] -> [Зелёная шкала прогресса с %] -> [Слот готового слитка]
        drawSlotFrame(context, x + 10, y + 83);

        int greenBarX = x + 32;
        int greenBarY = y + 83;
        int greenBarW = 170;
        int greenBarH = 18;
        context.fill(greenBarX, greenBarY, greenBarX + greenBarW, greenBarY + greenBarH, 0xFF141414);
        context.fill(greenBarX + 1, greenBarY + 1, greenBarX + greenBarW - 1, greenBarY + greenBarH - 1, 0xFF222325);

        int cookTime = handler.getCookTime();
        int maxCookTime = handler.getRequiredCookTime();
        int percent = MathHelper.clamp(Math.round((cookTime / (float) maxCookTime) * 100.0f), 0, 100);
        if (cookTime > 0) {
            int fillW = Math.min(greenBarW - 2, Math.round((cookTime / (float) maxCookTime) * (greenBarW - 2)));
            context.fill(greenBarX + 1, greenBarY + 1, greenBarX + 1 + fillW, greenBarY + greenBarH - 1, 0xFF38B828);
            context.fill(greenBarX + 1, greenBarY + 1, greenBarX + 1 + fillW, greenBarY + 5, 0xFF5CE64C);
        }
        String pctStr = percent + "%";
        int pctW = textRenderer.getWidth(pctStr);
        context.drawText(textRenderer, pctStr, greenBarX + greenBarW - pctW - 5, greenBarY + 5, 0xFFE6E6E6, true);

        drawSlotFrame(context, x + 206, y + 83);

        // 4. Строка топлива: [Слот топлива] -> [Красная шкала сгорания]
        drawSlotFrame(context, x + 10, y + 107);
        if (handler.furnaceEntity == null || handler.furnaceEntity.getStack(1).isEmpty()) {
            drawFlameSilhouette(context, x + 11, y + 108);
        }

        int redBarX = x + 32;
        int redBarY = y + 111;
        int redBarW = 192;
        int redBarH = 10;
        context.fill(redBarX, redBarY, redBarX + redBarW, redBarY + redBarH, 0xFF141414);
        context.fill(redBarX + 1, redBarY + 1, redBarX + redBarW - 1, redBarY + redBarH - 1, 0xFF222325);

        int burnTime = handler.getBurnTime();
        int maxBurnTime = handler.getMaxBurnTime();
        if (burnTime > 0) {
            int fillW = Math.min(redBarW - 2, Math.round((burnTime / (float) maxBurnTime) * (redBarW - 2)));
            context.fill(redBarX + 1, redBarY + 1, redBarX + 1 + fillW, redBarY + redBarH - 1, 0xFFC82828);
            context.fill(redBarX + 1, redBarY + 1, redBarX + 1 + fillW, redBarY + 4, 0xFFEA4C3B);
        }

        drawDivider(context, x + 8, y + 131, backgroundWidth - 16);

        // 5. Слоты инвентаря игрока
        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(context, invFrameX + col * 18, y + 137 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(context, invFrameX + col * 18, y + 195);
        }
    }

    private void renderFurnace3DPreview(DrawContext context, int centerX, int centerY) {
        MatrixStack matrices = context.getMatrices();
        int tier = handler.furnaceEntity != null ? handler.furnaceEntity.getTier() : 1;
        boolean burning = handler.furnaceEntity != null && handler.furnaceEntity.isBurning();

        matrices.push();
        matrices.translate(centerX, centerY, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));

        matrices.translate(0.0f, -1.1f, -1.0f);
        FactorioFurnaceRenderer.renderFurnaceModel(
                tier,
                burning,
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

    private void drawSlotFrame(DrawContext context, int slotX, int slotY) {
        context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF18181A);
        context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF3A3C3E);
    }

    private void drawFlameSilhouette(DrawContext context, int sx, int sy) {
        int c = 0xFF626466;
        context.fill(sx + 7, sy + 3, sx + 9, sy + 6, c);
        context.fill(sx + 5, sy + 6, sx + 11, sy + 10, c);
        context.fill(sx + 4, sy + 9, sx + 12, sy + 13, c);
        context.fill(sx + 6, sy + 13, sx + 10, sy + 14, c);
    }

    private void drawDivider(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0xFF202122);
        context.fill(x, y + 1, x + width, y + 2, 0xFF3E4042);
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        return mouseX >= x + 10 && mouseX <= x + backgroundWidth - 10 && mouseY >= y + 18 && mouseY <= y + 78;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 1.8f, 7.0f, 32.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if ((button == 0 || button == 1) && isInsideViewport(mouseX, mouseY)) {
            this.draggingPreview = true;
            return true;
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
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
