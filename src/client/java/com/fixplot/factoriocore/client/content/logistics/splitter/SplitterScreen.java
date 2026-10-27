package com.fixplot.factoriocore.client.content.logistics.splitter;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterBlock;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterScreenHandler;
import com.fixplot.factoriocore.network.ChangeStackSizePayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class SplitterScreen extends HandledScreen<SplitterScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 28.0f;
    private float previewZoom = 20.0f;
    private boolean draggingPreview = false;

    public SplitterScreen(SplitterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 240;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);

        Text leftTxt = Text.translatable("gui.factoriocore.left");
        Text rightTxt = Text.translatable("gui.factoriocore.right");
        int leftW = this.textRenderer.getWidth(leftTxt);

        // Тумблер сдвинут на X = 148..166, чтобы между "Приор. выхода" и "Слева" было много свободного места!
        int switchX = 148;
        int leftTextX = switchX - 6 - leftW;
        int rightTextX = switchX + 24;

        // 1. Строка приоритета входа
        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.input_priority"), 24, 88, 0xFFD8D8D8, false);
        boolean inRight = handler.isInputRight();
        boolean inEnabled = handler.isInputPriority();
        context.drawText(this.textRenderer, leftTxt, leftTextX, 88,
                (inEnabled && !inRight) ? 0xFFFFCC66 : 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, rightTxt, rightTextX, 88,
                (inEnabled && inRight) ? 0xFFFFCC66 : 0xFFAAAAAA, false);

        // 2. Строка приоритета выхода (без надписи "Фильтр" у ячейки)
        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.output_priority"), 24, 122, 0xFFD8D8D8, false);
        boolean outRight = handler.isOutputRight();
        boolean outEnabled = handler.isOutputPriority();
        context.drawText(this.textRenderer, leftTxt, leftTextX, 122,
                (outEnabled && !outRight) ? 0xFFFFCC66 : 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, rightTxt, rightTextX, 122,
                (outEnabled && outRight) ? 0xFFFFCC66 : 0xFFAAAAAA, false);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        // Корпус окна
        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF161616);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF313234);

        // Шапка
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

        // 3D-окно с шахматной сеткой
        int viewLeft = x + 10;
        int viewTop = y + 18;
        int viewRight = x + backgroundWidth - 10;
        int viewBottom = y + 78;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderSplitter3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 5);
        context.disableScissor();

        // Приоритет входа
        drawFactorioCheckbox(context, x + 10, y + 87, handler.isInputPriority());
        drawPillSwitch(context, x + 148, y + 88, handler.isInputRight());

        drawDivider(context, x + 10, y + 106, backgroundWidth - 20);

        // Приоритет выхода + слот фильтра справа (без текста "Фильтр")
        drawFactorioCheckbox(context, x + 10, y + 121, handler.isOutputPriority());
        drawPillSwitch(context, x + 148, y + 122, handler.isOutputRight());
        drawSlotFrame(context, x + 206, y + 117);

        drawDivider(context, x + 8, y + 151, backgroundWidth - 16);

        // Инвентарь игрока
        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(context, invFrameX + col * 18, y + 157 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(context, invFrameX + col * 18, y + 215);
        }
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

    private void drawDivider(DrawContext context, int x, int y, int width) {
        context.fill(x, y, x + width, y + 1, 0xFF202122);
        context.fill(x, y + 1, x + width, y + 2, 0xFF3E4042);
    }

    private void drawFactorioCheckbox(DrawContext context, int cx, int cy, boolean checked) {
        context.fill(cx, cy, cx + 10, cy + 10, 0xFF151515);
        if (checked) {
            context.fill(cx + 1, cy + 1, cx + 9, cy + 9, 0xFFE59524);
            context.fill(cx + 3, cy + 5, cx + 5, cy + 7, 0xFF1E1E1E);
            context.fill(cx + 5, cy + 3, cx + 7, cy + 6, 0xFF1E1E1E);
        } else {
            context.fill(cx + 1, cy + 1, cx + 9, cy + 9, 0xFF424447);
        }
    }

    private void drawPillSwitch(DrawContext context, int sx, int sy, boolean rightActive) {
        context.fill(sx, sy, sx + 18, sy + 9, 0xFF18181A);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 8, 0xFF2A2B2D);
        int knobX = rightActive ? sx + 10 : sx + 2;
        context.fill(knobX, sy + 1, knobX + 6, sy + 8, 0xFF9A9C9E);
    }

    private void renderSplitter3DPreview(DrawContext context, int centerX, int centerY) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(centerX, centerY, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));

        net.minecraft.block.Block splitterBlock = (handler.splitterEntity != null)
                ? handler.splitterEntity.getCachedState().getBlock()
                : ModBlocks.SPLITTER;
        BlockState leftState = splitterBlock.getDefaultState()
                .with(BeltBlock.FACING, Direction.NORTH)
                .with(SplitterBlock.PART_RIGHT, false);
        BlockState rightState = leftState.with(SplitterBlock.PART_RIGHT, true);

        matrices.push();
        matrices.translate(-1.0f, -0.4f, -0.5f);
        client.getBlockRenderManager().renderBlockAsEntity(
                leftState, matrices, context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        matrices.translate(1.0f, 0.0f, 0.0f);
        client.getBlockRenderManager().renderBlockAsEntity(
                rightState, matrices, context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        matrices.pop();

        if (handler.splitterEntity != null) {
            renderPreviewBeltItems(client, handler.splitterEntity, -1.0f, matrices, context);
            BeltBlockEntity rightPartner = handler.splitterEntity.getOtherSplitterHalf();
            if (rightPartner != null) {
                renderPreviewBeltItems(client, rightPartner, 0.0f, matrices, context);
            }
        }

        matrices.pop();
        context.draw();
    }

    private void renderPreviewBeltItems(MinecraftClient client, BeltBlockEntity belt, float offsetX,
                                        MatrixStack matrices, DrawContext context) {
        double[] baseZ = { 0.375, 0.125, -0.125, -0.375 };
        BeltLane[] lanes = { belt.getLeftLane(), belt.getRightLane() };
        double[] laneX = { offsetX + 0.28, offsetX + 0.72 };

        for (int l = 0; l < 2; l++) {
            for (int s = 0; s < 4; s++) {
                ItemStack stack = lanes[l].getStack(s);
                if (!stack.isEmpty()) {
                    matrices.push();
                    matrices.translate(laneX[l], -0.02, baseZ[s]);
                    matrices.scale(0.32f, 0.32f, 0.32f);
                    matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
                    client.getItemRenderer().renderItem(
                            stack, ModelTransformationMode.FIXED,
                            LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV,
                            matrices, context.getVertexConsumers(), belt.getWorld(), 0);
                    matrices.pop();
                }
            }
        }
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        return mouseX >= x + 10 && mouseX <= x + backgroundWidth - 10 && mouseY >= y + 18 && mouseY <= y + 78;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isInsideViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 2.5f, 10.0f, 52.0f);
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

        if (button == 0) {
            if (mouseX >= x + 8 && mouseX <= x + 105 && mouseY >= y + 84 && mouseY <= y + 99) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, BeltBlockEntity.ACTION_SPLITTER_TOGGLE_IN_PRIORITY));
                return true;
            }
            if (mouseX >= x + 108 && mouseX <= x + 202 && mouseY >= y + 84 && mouseY <= y + 99) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, BeltBlockEntity.ACTION_SPLITTER_TOGGLE_IN_RIGHT));
                return true;
            }
            if (mouseX >= x + 8 && mouseX <= x + 105 && mouseY >= y + 118 && mouseY <= y + 134) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, BeltBlockEntity.ACTION_SPLITTER_TOGGLE_OUT_PRIORITY));
                return true;
            }
            if (mouseX >= x + 108 && mouseX <= x + 202 && mouseY >= y + 118 && mouseY <= y + 134) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, BeltBlockEntity.ACTION_SPLITTER_TOGGLE_OUT_RIGHT));
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
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
