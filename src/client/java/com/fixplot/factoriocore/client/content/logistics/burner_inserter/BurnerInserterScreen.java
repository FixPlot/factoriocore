package com.fixplot.factoriocore.client.content.logistics.burner_inserter;

import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterScreenHandler;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
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

public class BurnerInserterScreen extends HandledScreen<BurnerInserterScreenHandler> {

    private float previewYaw = -35.0f;
    private float previewPitch = 28.0f;
    private float previewZoom = 24.0f;
    private boolean draggingPreview = false;

    public BurnerInserterScreen(BurnerInserterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 214;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // Заголовок сверху слева
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);

        // Подпись "Топливо / Fuel" рядом со слотом топлива
        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.fuel"), 12, 107, 0xFFD8D8D8, false);
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

        // Индикатор состояния справа сверху:
        // Красный = Нет топлива, Жёлтый = Спит, Зелёный = Работает
        boolean hasFuel = handler.hasFuelEnergy();
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
        int viewBottom = y + 70;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderInserter3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 5, delta);
        context.disableScissor();

        // 3. Ряд содержимого лапки под 3D-окном
        drawSlotFrame(context, x + 12, y + 74);
        if (handler.blockEntity == null || handler.blockEntity.getHeldStack().isEmpty()) {
            drawClawSilhouette(context, x + 13, y + 75);
        }
        for (int i = 0; i < 10; i++) {
            drawGhostSlotFrame(context, x + 36 + i * 18, y + 74);
        }

        drawDivider(context, x + 8, y + 96, backgroundWidth - 16);

        // 4. Секция ТОПЛИВА: Слот топлива (X: 68, Y: 102) + индикатор сгорания в стиле Factorio
        drawSlotFrame(context, x + 68, y + 102);
        if (handler.blockEntity == null || handler.blockEntity.getFuelStack().isEmpty()) {
            drawFlameSilhouette(context, x + 69, y + 103);
        }

        // Горизонтальная шкала энергии топлива справа от слота топлива
        int barX = x + 92;
        int barY = y + 106;
        int barWidth = 132;
        context.fill(barX, barY, barX + barWidth, barY + 10, 0xFF141414);
        context.fill(barX + 1, barY + 1, barX + barWidth - 1, barY + 9, 0xFF222325);

        int burnTime = handler.getBurnTime();
        int maxBurnTime = Math.max(1, handler.getMaxBurnTime());
        if (burnTime > 0) {
            int fillW = Math.min(barWidth - 2, (int) ((burnTime / (float) maxBurnTime) * (barWidth - 2)));
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + 9, 0xFFE56B1F);
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + 4, 0xFFFF9E3D);
        }

        drawDivider(context, x + 8, y + 125, backgroundWidth - 16);

        // 5. Слоты инвентаря игрока
        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(context, invFrameX + col * 18, y + 131 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(context, invFrameX + col * 18, y + 189);
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

    private void drawGhostSlotFrame(DrawContext context, int slotX, int slotY) {
        context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF28292B);
        context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, 0xFF313234);
    }

    private void drawClawSilhouette(DrawContext context, int sx, int sy) {
        int c = 0xFF626466;
        context.fill(sx + 3, sy + 11, sx + 7, sy + 13, c);
        context.fill(sx + 5, sy + 6, sx + 9, sy + 11, c);
        context.fill(sx + 9, sy + 4, sx + 13, sy + 6, c);
        context.fill(sx + 9, sy + 9, sx + 13, sy + 11, c);
        context.fill(sx + 8, sy + 5, sx + 10, sy + 10, c);
    }

    /**
     * Силуэт пламени внутри пустого слота топлива.
     */
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

    private void renderInserter3DPreview(DrawContext context, int centerX, int centerY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(centerX, centerY + 6, 150.0f);
        matrices.scale(this.previewZoom, -this.previewZoom, this.previewZoom);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));

        boolean isBurner = handler.blockEntity != null && handler.blockEntity.isBurner();
        BlockState previewState = handler.blockEntity != null
                ? handler.blockEntity.getCachedState()
                : (isBurner ? ModBlocks.BURNER_INSERTER.getDefaultState() : ModBlocks.INSERTER.getDefaultState());

        matrices.translate(-0.5f, -0.4f, -0.5f);
        // 1. Рисуем станину манипулятора
        client.getBlockRenderManager().renderBlockAsEntity(
                previewState, matrices, context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);

        // 2. Рисуем шарнирную 3D-руку с клешнёй и предметом в реальном времени
        Direction facing = previewState.contains(InserterBlock.FACING)
                ? previewState.get(InserterBlock.FACING) : Direction.NORTH;
        float progress = handler.blockEntity != null ? handler.blockEntity.getArmProgress(delta) : 0.0f;
        ItemStack held = handler.blockEntity != null ? handler.blockEntity.getHeldStack() : ItemStack.EMPTY;

        com.fixplot.factoriocore.client.content.logistics.inserter.InserterRenderer.renderArticulatedArm(
                handler.blockEntity,
                isBurner,
                facing,
                progress,
                delta,
                held,
                matrices,
                context.getVertexConsumers(),
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV
        );

        matrices.pop();
        context.draw();
    }

    private boolean isInsideViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        return mouseX >= x + 10 && mouseX <= x + backgroundWidth - 10 && mouseY >= y + 18 && mouseY <= y + 70;
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
