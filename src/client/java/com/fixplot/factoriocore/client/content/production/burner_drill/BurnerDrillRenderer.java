package com.fixplot.factoriocore.client.content.production.burner_drill;

import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
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

public class BurnerDrillRenderer implements BlockEntityRenderer<BurnerDrillBlockEntity> {

    public BurnerDrillRenderer(BlockEntityRendererFactory.Context ctx) {} @Override public int getRenderDistance() { return 48; }

    @Override
    public void render(BurnerDrillBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof BurnerDrillBlock)) return;
        // Рисуем всю 3D-машину 2x2x2.5 один раз от имени главного блока (part=0, upper=false)
        if (state.get(BurnerDrillBlock.PART) != 0 || state.get(BurnerDrillBlock.UPPER)) {
            return;
        }

        Direction facing = state.get(BurnerDrillBlock.FACING);
        float drillAngle = (entity.getWorld() != null && entity.isWorking()) ? entity.getClientDrillRotation(tickDelta) : 0.0f;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        // В локальных координатах (когда facing = SOUTH, +Z):
        // Главный блок (part=0) занимает X: [0..1], Z: [0..1]
        // Правый блок (part=1) занимает X: [-1..0], Z: [0..1]
        // Передний-Левый (part=2) занимает X: [0..1], Z: [1..2]
        // Передний-Правый (part=3) занимает X: [-1..0], Z: [1..2]
        // Значит весь бур 2x2 занимает X: [-1.0 .. 1.0], Z: [0.0 .. 2.0], Y: [0.0 .. 2.5]!
        renderDrillModel(matrices, vertexConsumers, light, overlay, drillAngle);

        matrices.pop();
    }

    public static void renderDrillModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                        int light, int overlay, float drillAngle) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState darkIron = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
        BlockState stoneBase = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState firebox = Blocks.BLAST_FURNACE.getDefaultState();
        BlockState yellowChute = Blocks.YELLOW_CONCRETE.getDefaultState();

        // 1. Нижняя рама по периметру 2x2 (X: -0.95..0.95, Z: 0.05..1.95)
        drawBox(brm, stoneBase, matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, 0.05f, 0.35f, 0.35f, 1.90f);
        drawBox(brm, stoneBase, matrices, vertexConsumers, light, overlay,  0.60f, 0.0f, 0.05f, 0.35f, 0.35f, 1.90f);
        drawBox(brm, stoneBase, matrices, vertexConsumers, light, overlay, -0.60f, 0.0f, 0.05f, 1.20f, 0.35f, 0.35f);
        drawBox(brm, stoneBase, matrices, vertexConsumers, light, overlay, -0.60f, 0.0f, 1.60f, 1.20f, 0.35f, 0.35f);

        // 2. Четыре мощные опорные стойки до высоты 2.2 блока
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay, -0.88f, 0.35f, 0.12f, 0.24f, 1.85f, 0.24f);
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay,  0.64f, 0.35f, 0.12f, 0.24f, 1.85f, 0.24f);
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay, -0.88f, 0.35f, 1.64f, 0.24f, 1.85f, 0.24f);
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay,  0.64f, 0.35f, 1.64f, 0.24f, 1.85f, 0.24f);

        // 3. Верхняя балка и редуктор (высота до 2.5 блоков!)
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay, -0.90f, 2.05f, 0.10f, 1.80f, 0.25f, 1.80f);
        drawBox(brm, firebox,  matrices, vertexConsumers, light, overlay, -0.55f, 1.20f, 0.15f, 1.10f, 0.85f, 0.75f);
        drawBox(brm, steel,    matrices, vertexConsumers, light, overlay, -0.35f, 2.30f, 0.65f, 0.70f, 0.20f, 0.70f);

        // 4. Жёлтый выходной жёлоб со стрелкой на переднем-правом тайле (X: -0.75..-0.25, Z: 1.65..2.02)
        drawBox(brm, yellowChute, matrices, vertexConsumers, light, overlay, -0.75f, 0.30f, 1.60f, 0.50f, 0.22f, 0.42f);

        // 5. Вращающийся центральный бур и шестерня в центре 2x2 (X = 0.0, Z = 1.0)
        matrices.push();
        matrices.translate(0.0, 0.0, 1.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(drillAngle));

        // Центральный вал
        drawBox(brm, steel, matrices, vertexConsumers, light, overlay, -0.15f, 0.10f, -0.15f, 0.30f, 2.00f, 0.30f);
        // Лопасти буровой коронки внизу
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay, -0.45f, 0.15f, -0.10f, 0.90f, 0.45f, 0.20f);
        drawBox(brm, darkIron, matrices, vertexConsumers, light, overlay, -0.10f, 0.15f, -0.45f, 0.20f, 0.45f, 0.90f);

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
