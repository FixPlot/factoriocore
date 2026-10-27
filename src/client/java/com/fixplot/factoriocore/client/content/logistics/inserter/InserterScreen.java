package com.fixplot.factoriocore.client.content.logistics.inserter;

import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterScreenHandler;
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

public class InserterScreen extends HandledScreen<InserterScreenHandler> {

    private static final int MAX_STACK_OVERRIDE = 4;

    private float previewYaw = -35.0f;
    private float previewPitch = 28.0f;
    private float previewZoom = 24.0f;
    private boolean draggingPreview = false;
    private boolean draggingSlider = false;

    public InserterScreen(InserterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 240;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, 8, 6, 0xFFE6E6E6, false);

        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.use_filters"), 25, 102, 0xFFD8D8D8, false);

        boolean blacklist = handler.isBlacklistMode();
        boolean filterOn = handler.isFilterEnabled();
        Text whiteTxt = Text.translatable("gui.factoriocore.whitelist");
        Text blackTxt = Text.translatable("gui.factoriocore.blacklist");

        int whiteW = this.textRenderer.getWidth(whiteTxt);
        int switchX = 18 + whiteW;

        context.drawText(this.textRenderer, whiteTxt, 10, 117,
                (filterOn && !blacklist) ? 0xFFFFCC66 : 0xFFAAAAAA, false);
        context.drawText(this.textRenderer, blackTxt, switchX + 24, 117,
                (filterOn && blacklist) ? 0xFFFFCC66 : 0xFFAAAAAA, false);

        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.override_stack"), 25, 137, 0xFFD8D8D8, false);
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

        boolean isSleeping = handler.isSleeping();
        int ledOuter = isSleeping ? 0xFF664400 : 0xFF115511;
        int ledInner = isSleeping ? 0xFFFFAA00 : 0xFF44FF44;
        int ledX = x + backgroundWidth - 19;
        context.fill(ledX, y + 5, ledX + 10, y + 14, 0xFF111111);
        context.fill(ledX + 1, y + 6, ledX + 9, y + 13, ledOuter);
        context.fill(ledX + 2, y + 7, ledX + 8, y + 12, ledInner);

        int viewLeft = x + 10;
        int viewTop = y + 18;
        int viewRight = x + backgroundWidth - 10;
        int viewBottom = y + 70;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderInserter3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 5, delta);
        context.disableScissor();

        drawSlotFrame(context, x + 12, y + 74, true);
        if (handler.blockEntity == null || handler.blockEntity.getHeldStack().isEmpty()) {
            drawClawSilhouette(context, x + 13, y + 75);
        }
        for (int i = 0; i < 10; i++) {
            drawGhostSlotFrame(context, x + 36 + i * 18, y + 74);
        }

        drawDivider(context, x + 8, y + 96, backgroundWidth - 16);

        int whiteW = this.textRenderer.getWidth(Text.translatable("gui.factoriocore.whitelist"));
        drawFactorioCheckbox(context, x + 10, y + 101, handler.isFilterEnabled());
        drawPillSwitch(context, x + 18 + whiteW, y + 116, handler.isBlacklistMode());

        context.fill(x + 133, y + 104, x + 225, y + 124, 0xFF18181A);
        for (int i = 0; i < InserterBlockEntity.FILTER_SLOTS_COUNT; i++) {
            drawSlotFrame(context, x + 134 + i * 18, y + 105, handler.isFilterEnabled());
        }

        drawDivider(context, x + 8, y + 130, backgroundWidth - 16);

        boolean overrideStack = handler.isOverrideStackSize();
        int stackSize = MathHelper.clamp(handler.getMaxPickupCount(), 1, MAX_STACK_OVERRIDE);
        drawFactorioCheckbox(context, x + 10, y + 136, overrideStack);
        drawFactorioSlider(context, x + 126, y + 136, 66, stackSize, overrideStack);

        context.fill(x + 198, y + 133, x + 224, y + 149, 0xFF18181A);
        context.fill(x + 199, y + 134, x + 223, y + 148, 0xFF232426);
        String stackStr = String.valueOf(stackSize);
        int strW = textRenderer.getWidth(stackStr);
        context.drawText(textRenderer, stackStr, x + 211 - strW / 2, y + 137,
                overrideStack ? 0xFFFFFFFF : 0xFF777777, false);

        drawDivider(context, x + 8, y + 153, backgroundWidth - 16);

        int invFrameX = x + 37;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlotFrame(context, invFrameX + col * 18, y + 157 + row * 18, true);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlotFrame(context, invFrameX + col * 18, y + 215, true);
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

    private void drawSlotFrame(DrawContext context, int slotX, int slotY, boolean active) {
        context.fill(slotX, slotY, slotX + 18, slotY + 18, 0xFF18181A);
        context.fill(slotX + 1, slotY + 1, slotX + 17, slotY + 17, active ? 0xFF3A3C3E : 0xFF28292B);
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

    private void drawFactorioSlider(DrawContext context, int sx, int sy, int width, int value, boolean active) {
        // Деления шкалы на 4 позиции (1, 2, 3, 4), как на скриншоте из Factorio
        for (int step = 0; step < MAX_STACK_OVERRIDE; step++) {
            int tickX = sx + 2 + Math.round((step / (float) (MAX_STACK_OVERRIDE - 1)) * (width - 5));
            context.fill(tickX, sy + 1, tickX + 1, sy + 4, 0xFF18181A);
        }
        context.fill(sx, sy + 4, sx + width, sy + 7, 0xFF18181A);

        float ratio = (MathHelper.clamp(value, 1, MAX_STACK_OVERRIDE) - 1) / (float) (MAX_STACK_OVERRIDE - 1);
        int knobX = sx + Math.round(ratio * (width - 6));
        int knobColor = active ? 0xFFB0B2B5 : 0xFF555759;
        context.fill(knobX, sy + 1, knobX + 6, sy + 10, 0xFF141414);
        context.fill(knobX + 1, sy + 2, knobX + 5, sy + 9, knobColor);
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
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        if ((button == 0 || button == 1) && isInsideViewport(mouseX, mouseY)) {
            this.draggingPreview = true;
            return true;
        }

        if (button == 0 && handler.blockEntity != null) {
            if (mouseX >= x + 10 && mouseX <= x + 115 && mouseY >= y + 100 && mouseY <= y + 112) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, InserterBlockEntity.ACTION_TOGGLE_FILTER));
                return true;
            }
            if (mouseX >= x + 8 && mouseX <= x + 128 && mouseY >= y + 115 && mouseY <= y + 128) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, InserterBlockEntity.ACTION_TOGGLE_BLACKLIST));
                return true;
            }
            if (mouseX >= x + 10 && mouseX <= x + 122 && mouseY >= y + 135 && mouseY <= y + 147) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(
                        handler.targetPos, InserterBlockEntity.ACTION_TOGGLE_OVERRIDE_STACK));
                return true;
            }
            if (mouseX >= x + 126 && mouseX <= x + 192 && mouseY >= y + 134 && mouseY <= y + 148) {
                this.draggingSlider = true;
                updateSliderFromMouse(mouseX, x + 126, 66);
                return true;
            }
            if (mouseX >= x + 198 && mouseX <= x + 224 && mouseY >= y + 133 && mouseY <= y + 149) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(handler.targetPos, 1));
                return true;
            }
        } else if (button == 1 && handler.blockEntity != null) {
            if (mouseX >= x + 198 && mouseX <= x + 224 && mouseY >= y + 133 && mouseY <= y + 149) {
                ClientPlayNetworking.send(new ChangeStackSizePayload(handler.targetPos, -1));
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateSliderFromMouse(double mouseX, int sliderX, int sliderWidth) {
        if (handler.blockEntity == null) return;
        float ratio = MathHelper.clamp((float) (mouseX - sliderX) / (float) sliderWidth, 0.0f, 1.0f);
        int newStack = Math.max(1, Math.min(MAX_STACK_OVERRIDE, Math.round(1 + ratio * (MAX_STACK_OVERRIDE - 1))));
        ClientPlayNetworking.send(new ChangeStackSizePayload(
                handler.targetPos, InserterBlockEntity.ACTION_SET_STACK_BASE + newStack));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingPreview && (button == 0 || button == 1)) {
            this.draggingPreview = false;
            return true;
        }
        if (this.draggingSlider && button == 0) {
            this.draggingSlider = false;
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
        if (this.draggingSlider && button == 0) {
            int x = (width - backgroundWidth) / 2;
            updateSliderFromMouse(mouseX, x + 126, 66);
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