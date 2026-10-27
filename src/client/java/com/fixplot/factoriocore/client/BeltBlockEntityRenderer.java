package com.fixplot.factoriocore.client;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class BeltBlockEntityRenderer extends GeoBlockRenderer<BeltBlockEntity> {

    private static final double SLOT_STEP = 0.25;
    private static final double[] BASE_Z = { 0.125, 0.375, 0.625, 0.875 };
    private final ItemRenderer itemRenderer;

    public BeltBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new BeltGeoModel());
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public int getRenderDistance() {
        return 40;
    }

    @Override
    public void render(BeltBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (!entity.isSplitter() && !entity.isUnderground()) {
            super.render(entity, tickDelta, matrices, vertexConsumers, light, overlay);
        }

        BeltLane left = entity.getLeftLane();
        BeltLane right = entity.getRightLane();
        // Если на конвейере нет предметов — вообще не тратим CPU на матрицы и циклы слотов!
        if (left.isEmpty() && right.isEmpty()) {
            return;
        }

        Direction facing = entity.getCachedState().get(BeltBlock.FACING);
        int delay = entity.getMoveDelay();
        float rawTicks = Math.min((float) delay, entity.getRenderTicks() + tickDelta);
        float progress = MathHelper.clamp(rawTicks / (float) delay, 0.0f, 1.0f);

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        BeltBlockEntity.UndergroundMode mode = entity.getUndergroundMode();

        if (!left.isEmpty()) {
            renderLane(left, 0.72, progress, mode, matrices, vertexConsumers, light, overlay);
        }
        if (!right.isEmpty()) {
            renderLane(right, 0.28, progress, mode, matrices, vertexConsumers, light, overlay);
        }

        matrices.pop();
    }

    private void renderLane(BeltLane lane, double laneX, float progress, BeltBlockEntity.UndergroundMode mode,
                            MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        ItemRenderer ir = (this.itemRenderer != null) ? this.itemRenderer : MinecraftClient.getInstance().getItemRenderer();

        for (int i = 0; i < BeltLane.CAPACITY; i++) {
            ItemStack stack = lane.getStack(i);
            if (!stack.isEmpty()) {
                double z = BASE_Z[i];
                if (lane.isMoving(i)) {
                    z = MathHelper.lerp(progress, z - SLOT_STEP, z);
                }

                if (mode == BeltBlockEntity.UndergroundMode.INPUT && z > 0.52) continue;
                if (mode == BeltBlockEntity.UndergroundMode.OUTPUT && z < 0.48) continue;

                matrices.push();
                matrices.translate(laneX, 0.38, z);
                matrices.scale(0.35f, 0.35f, 0.35f);
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));

                ir.renderItem(
                        stack,
                        ModelTransformationMode.FIXED,
                        light,
                        overlay,
                        matrices,
                        vertexConsumers,
                        null,
                        0
                );

                matrices.pop();
            }
        }
    }
}