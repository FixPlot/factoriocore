package com.fixplot.factoriocore.client.content.research;

import com.fixplot.factoriocore.content.research.LabBlock;
import com.fixplot.factoriocore.content.research.LabBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;

public class LabRenderer implements BlockEntityRenderer<LabBlockEntity> {

    public LabRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public int getRenderDistance() {
        return 48;
    }

    @Override
    public void render(LabBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof LabBlock)) return;
        if (state.get(LabBlock.PART) != 4 || state.get(LabBlock.UPPER)) return;

        Direction facing = state.contains(LabBlock.FACING) ? state.get(LabBlock.FACING) : Direction.NORTH;
        float angle = (entity.isWorking() && entity.getWorld() != null)
                ? (entity.getWorld().getTime() + tickDelta) * 8.0f : 0.0f;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        renderLabModel(matrices, vertexConsumers, light, overlay, angle, entity.isWorking());

        matrices.pop();
    }

    public static void renderLabModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                      int light, int overlay, float workingAngle, boolean working) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState baseFrame = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState whiteHull = Blocks.IRON_BLOCK.getDefaultState();
        BlockState darkRibs = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState glassDome = Blocks.CYAN_STAINED_GLASS.getDefaultState();
        BlockState coreBlock = working ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.PRISMARINE_BRICKS.getDefaultState();
        int coreLight = working ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

        // 1. Тяжёлое основание 3x3 (X: -0.95..1.95, Z: -0.95..1.95)
        drawBox(brm, baseFrame, matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.95f, 2.90f, 0.35f, 2.90f);

        // 2. Белый научно-промышленный корпус и угловые дуги купола
        drawBox(brm, whiteHull, matrices, vertexConsumers, light, overlay, -0.80f, 0.35f, -0.80f, 2.60f, 0.65f, 2.60f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, light, overlay, -0.86f, 0.35f, -0.86f, 0.38f, 1.35f, 0.38f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, light, overlay,  1.48f, 0.35f, -0.86f, 0.38f, 1.35f, 0.38f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, light, overlay, -0.86f, 0.35f,  1.48f, 0.38f, 1.35f, 0.38f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, light, overlay,  1.48f, 0.35f,  1.48f, 0.38f, 1.35f, 0.38f);

        // 3. Вращающееся энергетическое ядро внутри купола
        matrices.push();
        matrices.translate(0.5, 1.05, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(workingAngle));
        drawBox(brm, coreBlock, matrices, vertexConsumers, coreLight, overlay, -0.45f, 0.0f, -0.45f, 0.90f, 0.70f, 0.90f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, coreLight, overlay, -0.60f, 0.25f, -0.12f, 1.20f, 0.20f, 0.24f);
        drawBox(brm, darkRibs,  matrices, vertexConsumers, coreLight, overlay, -0.12f, 0.25f, -0.60f, 0.24f, 0.20f, 1.20f);
        matrices.pop();

        // 4. Стеклянный геодезический купол Лаборатории (до высоты 2.15 блока)
        drawBox(brm, glassDome, matrices, vertexConsumers, coreLight, overlay, -0.62f, 1.00f, -0.62f, 2.24f, 0.85f, 2.24f);
        drawBox(brm, glassDome, matrices, vertexConsumers, coreLight, overlay, -0.35f, 1.85f, -0.35f, 1.70f, 0.30f, 1.70f);
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