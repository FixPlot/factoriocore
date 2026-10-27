package com.fixplot.factoriocore.client.content.production.electric_drill;

import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class ElectricDrillRenderer implements BlockEntityRenderer<ElectricDrillBlockEntity> {

    public ElectricDrillRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public int getRenderDistance() {
        return 48;
    }

    @Override
    public void render(ElectricDrillBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof ElectricDrillBlock)) return;
        if (state.get(ElectricDrillBlock.PART) != 4 || state.get(ElectricDrillBlock.UPPER)) return;

        Direction facing = state.get(ElectricDrillBlock.FACING);
        float angle = (entity.getWorld() != null && entity.isPowered()) ? entity.getClientDrillRotation(tickDelta) : 0.0f;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        renderDrillModel(matrices, vertexConsumers, light, overlay, angle);

        matrices.pop();
    }

    public static void renderDrillModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        int light, int overlay, float angle) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState baseFrame = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState darkPillars = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState tealMotor = Blocks.CYAN_TERRACOTTA.getDefaultState();
        BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
        BlockState yellowChute = Blocks.YELLOW_CONCRETE.getDefaultState();

        // Нижняя рама 3x3
        drawBox(brm, baseFrame,   matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.95f, 2.90f, 0.35f, 0.35f);
        drawBox(brm, baseFrame,   matrices, vertexConsumers, light, overlay, -0.95f, 0.0f,  1.60f, 2.90f, 0.35f, 0.35f);
        drawBox(brm, baseFrame,   matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.60f, 0.35f, 0.35f, 2.20f);
        drawBox(brm, baseFrame,   matrices, vertexConsumers, light, overlay,  1.60f, 0.0f, -0.60f, 0.35f, 0.35f, 2.20f);

        // 4 угловые опоры до высоты 2.2 блока
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay, -0.88f, 0.35f, -0.88f, 0.28f, 1.85f, 0.28f);
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay,  1.60f, 0.35f, -0.88f, 0.28f, 1.85f, 0.28f);
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay, -0.88f, 0.35f,  1.60f, 0.28f, 1.85f, 0.28f);
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay,  1.60f, 0.35f,  1.60f, 0.28f, 1.85f, 0.28f);

        // Верхняя мостовая рама и электродвигатель
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay, -0.90f, 2.00f, -0.90f, 2.80f, 0.28f, 2.80f);
        drawBox(brm, tealMotor,   matrices, vertexConsumers, light, overlay, -0.35f, 1.35f, -0.35f, 1.70f, 1.15f, 1.70f);

        // Жёлтый выходной жёлоб спереди по центру
        drawBox(brm, yellowChute, matrices, vertexConsumers, light, overlay,  0.20f, 0.30f,  1.60f, 0.60f, 0.25f, 0.42f);

        // Вращающаяся буровая штанга в центре 3x3
        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        drawBox(brm, steel,       matrices, vertexConsumers, light, overlay, -0.20f, 0.05f, -0.20f, 0.40f, 1.40f, 0.40f);
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay, -0.65f, 0.12f, -0.14f, 1.30f, 0.45f, 0.28f);
        drawBox(brm, darkPillars, matrices, vertexConsumers, light, overlay, -0.14f, 0.12f, -0.65f, 0.28f, 0.45f, 1.30f);
        matrices.pop();
    }

    private static void drawBox(BlockRenderManager brm, BlockState state, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay,
                                float x, float y, float z, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(sx, sy, sz);
        brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }
}