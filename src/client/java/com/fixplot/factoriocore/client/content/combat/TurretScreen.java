package com.fixplot.factoriocore.client.content.combat;

import com.fixplot.factoriocore.content.combat.turret.TurretScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class TurretScreen extends HandledScreen<TurretScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 14.5f;
    private boolean draggingPreview = false;

    public TurretScreen(TurretScreenHandler handler, PlayerInventory inventory, Text title) {
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
        int kind = handler.getTurretKind();

        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF161616);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF313234);

        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 16, 0xFF242527);
        int titleEnd = x + 14 + textRenderer.getWidth(this.title);
        for (int gx = titleEnd; gx < x + backgroundWidth - 24; gx += 3) {
            context.fill(gx, y + 5, gx + 1, y + 14, 0xFF1A1B1C);
        }

        boolean ready = switch (kind) {
            case 0 -> handler.getCurrentRounds() > 0 || !handler.getSlot(0).getStack().isEmpty();
            case 1 -> handler.isPowered();
            default -> handler.getFuelFluidMb() >= 10;
        };
        int ledOuter = ready ? 0xFF115511 : 0xFF661111;
        int ledInner = ready ? 0xFF44FF44 : 0xFFFF3333;
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
        renderTurret3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 10, kind);
        context.disableScissor();

        if (kind == 0) {
            // Слот магазинов + шкала оставшихся патронов в текущем магазине
            drawSlotFrame(context, x + 10, y + 87);
            int barX = x + 32;
            int barY = y + 91;
            int barW = 192;
            int barH = 10;
            context.fill(barX, barY, barX + barW, barY + barH, 0xFF141414);
            context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF222325);

            float ratio = MathHelper.clamp(handler.getCurrentRounds() / (float) handler.getMaxRounds(), 0.0f, 1.0f);
            int fillW = Math.round(ratio * (barW - 2));
            if (fillW > 0) {
                context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFFE59524);
            }
        } else if (kind == 1) {
            String pwrStr = handler.isPowered() ? "§aПитание подключено (Готова к бою)" : "§cНет питания от электросети!";
            context.drawText(this.textRenderer, Text.literal(pwrStr), x + 12, y + 92, 0xFFFFFFFF, true);
        } else {
            int barX = x + 10;
            int barY = y + 90;
            int barW = 214;
            int barH = 12;
            context.fill(barX, barY, barX + barW, barY + barH, 0xFF141414);
            context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF222325);
            float ratio = MathHelper.clamp(handler.getFuelFluidMb() / 2000.0f, 0.0f, 1.0f);
            int fillW = Math.round(ratio * (barW - 2));
            if (fillW > 0) {
                context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFFD68910);
            }
            context.drawText(this.textRenderer, "Топливо: " + handler.getFuelFluidMb() + " / 2000 мБ", barX + 6, barY + 2, 0xFFFFFFFF, true);
        }

        // Боевая статистика турели (Убийства и Нанесённый урон, как в Factorio!)
        String statsStr = "Убито врагов: " + handler.getKillsCount() + "  |  Нанесено урона: " + handler.getTotalDamage();
        context.drawText(this.textRenderer, statsStr, x + 12, y + 114, 0xFFFFCC66, true);

        context.fill(x + 8, y + 131, x + backgroundWidth - 8, y + 132, 0xFF202122);
        context.fill(x + 8, y + 132, x + backgroundWidth - 8, y + 133, 0xFF3E4042);

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

    private void renderTurret3DPreview(DrawContext context, int centerX, int centerY, int kind) {
        MatrixStack matrices = context.getMatrices();
        float yaw = (handler.turretEntity != null) ? handler.turretEntity.getTurretYaw() : 0.0f;
        float pitch = (handler.turretEntity != null) ? handler.turretEntity.getTurretPitch() : 0.0f;

        matrices.push();
        matrices.translate(centerX, centerY, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));
        matrices.translate(-1.0f, -0.9f, -1.0f);

        TurretRenderer.renderTurretModel(
                kind, yaw, pitch, false, matrices,
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
                context.fill(px, py, Math.min(px + cellSize, right), Math.min(py + cellSize, bottom),
                        ((gx + gy) % 2 == 0) ? 0xFF262729 : 0xFF2E3032);
            }
        }
    }

    private void drawSlotFrame(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF18181A);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3C3E);
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        return mouseX >= x + 10 && mouseX <= x + backgroundWidth - 10 && mouseY >= y + 18 && mouseY <= y + 78;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 1.8f, 8.0f, 32.0f);
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
        this.draggingPreview = false;
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