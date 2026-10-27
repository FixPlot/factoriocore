package com.fixplot.factoriocore.client.content.production.electric_drill;

import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillScreenHandler;
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

public class ElectricDrillScreen extends HandledScreen<ElectricDrillScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 13.5f;
    private boolean draggingPreview = false;

    public ElectricDrillScreen(ElectricDrillScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 224;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF161616);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF313234);

        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 16, 0xFF242527);
        int titleEnd = x + 14 + textRenderer.getWidth(this.title);
        for (int gx = titleEnd; gx < x + backgroundWidth - 24; gx += 3) {
            context.fill(gx, y + 5, gx + 1, y + 14, 0xFF1A1B1C);
        }

        boolean isWorking = handler.getMiningProgress() > 0;
        int ledOuter = !isWorking ? 0xFF661111 : 0xFF115511;
        int ledInner = !isWorking ? 0xFFFF3333 : 0xFF44FF44;
        int ledX = x + backgroundWidth - 19;
        context.fill(ledX, y + 5, ledX + 10, y + 14, 0xFF111111);
        context.fill(ledX + 1, y + 6, ledX + 9, y + 13, ledOuter);
        context.fill(ledX + 2, y + 7, ledX + 8, y + 12, ledInner);

        int viewLeft = x + 10;
        int viewTop = y + 18;
        int viewRight = x + backgroundWidth - 10;
        int viewBottom = y + 78;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderDrill3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 12, delta);
        context.disableScissor();

        int greenBarX = x + 10;
        int greenBarY = y + 83;
        int greenBarW = 192;
        int greenBarH = 18;
        context.fill(greenBarX, greenBarY, greenBarX + greenBarW, greenBarY + greenBarH, 0xFF141414);
        context.fill(greenBarX + 1, greenBarY + 1, greenBarX + greenBarW - 1, greenBarY + greenBarH - 1, 0xFF222325);

        int progress = handler.getMiningProgress();
        int maxProgress = Math.max(1, handler.getMaxMiningTime());
        int percent = MathHelper.clamp(Math.round((progress / (float) maxProgress) * 100.0f), 0, 100);
        if (progress > 0) {
            int fillW = Math.min(greenBarW - 2, Math.round((progress / (float) maxProgress) * (greenBarW - 2)));
            context.fill(greenBarX + 1, greenBarY + 1, greenBarX + 1 + fillW, greenBarY + greenBarH - 1, 0xFF38B828);
            context.fill(greenBarX + 1, greenBarY + 1, greenBarX + 1 + fillW, greenBarY + 5, 0xFF5CE64C);
        }
        String pctStr = percent + "%";
        int pctW = textRenderer.getWidth(pctStr);
        context.drawText(textRenderer, pctStr, greenBarX + greenBarW - pctW - 5, greenBarY + 5, 0xFFE6E6E6, true);

        drawSlotFrame(context, x + 206, y + 83);

        context.fill(x + 4, y + 108, x + backgroundWidth - 4, y + 109, 0xFF1A1B1C);
        context.fill(x + 4, y + 109, x + backgroundWidth - 4, y + 110, 0xFF3A3C3F);

        // Декоративные слоты под модули
        for (int m = 0; m < 4; m++) {
            drawGhostSlot(context, x + 10 + m * 19, y + 114);
        }

        context.fill(x + 4, y + 138, x + backgroundWidth - 4, y + 139, 0xFF202123);

        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(context, invFrameX + col * 18, y + 143 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(context, invFrameX + col * 18, y + 201);
        }
    }

    private void drawSlotFrame(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF18181A);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3C3E);
    }

    private void drawGhostSlot(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1B1C);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF222325);
    }

    private void drawCheckerboard(DrawContext context, int left, int top, int right, int bottom, int cellSize) {
        for (int py = top; py < bottom; py += cellSize) {
            for (int px = left; px < right; px += cellSize) {
                int gx = (px - left) / cellSize;
                int gy = (py - top) / cellSize;
                context.fill(px, py, Math.min(px + cellSize, right), Math.min(py + cellSize, bottom),
                        ((gx + gy) % 2 == 0) ? 0xFF262729 : 0xFF2E3032);
            }
        }
    }

    private void renderDrill3DPreview(DrawContext context, int centerX, int centerY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();
        float drillAngle = (handler.getMiningProgress() > 0 && client.world != null)
                ? (client.world.getTime() + delta) * 15.0f : 0.0f;

        matrices.push();
        matrices.translate(centerX, centerY, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));
        // Центрируем модель 3x3 вокруг центральной штанги (0.5, 1.1, 0.5)
        matrices.translate(-0.5f, -1.1f, -0.5f);

        ElectricDrillRenderer.renderDrillModel(
                matrices,
                context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                drillAngle
        );

        matrices.pop();
        context.draw();
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