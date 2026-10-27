package com.fixplot.factoriocore.client.content.electricity;

import com.fixplot.factoriocore.content.electricity.ElectricPoleBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;

public class LargePoleRenderer implements BlockEntityRenderer<ElectricPoleBlock.LargePoleBlockEntity> {

    public LargePoleRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public int getRenderDistance() {
        return 64;
    }

    @Override
    public void render(ElectricPoleBlock.LargePoleBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof ElectricPoleBlock pole)) return;
        if (state.get(ElectricPoleBlock.PART) != 0 || state.get(ElectricPoleBlock.UPPER)) return;

        boolean isSubstation = (state.getBlock() == ModBlocks.SUBSTATION || pole.getPoleTier() == 4);
        if (isSubstation) {
            renderSubstationModel(matrices, vertexConsumers, light, overlay);
        } else {
            renderBigElectricPoleModel(matrices, vertexConsumers, light, overlay);
        }
    }

    public static void renderBigElectricPoleModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  int light, int overlay) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState base = Blocks.POLISHED_DEEPSLATE.getDefaultState();
        BlockState darkSteel = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState iron = Blocks.IRON_BLOCK.getDefaultState();
        BlockState copper = Blocks.COPPER_BLOCK.getDefaultState();
        BlockState insulator = Blocks.CYAN_TERRACOTTA.getDefaultState();

        // 4 бетонных башмака фундамента 2x2 (X: 0..2, Z: 0..2)
        drawBox(brm, base,      matrices, vertexConsumers, light, overlay, 0.08f, 0.0f, 0.08f, 0.48f, 0.35f, 0.48f);
        drawBox(brm, base,      matrices, vertexConsumers, light, overlay, 1.44f, 0.0f, 0.08f, 0.48f, 0.35f, 0.48f);
        drawBox(brm, base,      matrices, vertexConsumers, light, overlay, 0.08f, 0.0f, 1.44f, 0.48f, 0.35f, 0.48f);
        drawBox(brm, base,      matrices, vertexConsumers, light, overlay, 1.44f, 0.0f, 1.44f, 0.48f, 0.35f, 0.48f);

        // Нижний ярус решётчатой вышки ЛЭП
        drawBox(brm, darkSteel, matrices, vertexConsumers, light, overlay, 0.22f, 0.35f, 0.22f, 0.24f, 1.25f, 0.24f);
        drawBox(brm, darkSteel, matrices, vertexConsumers, light, overlay, 1.54f, 0.35f, 0.22f, 0.24f, 1.25f, 0.24f);
        drawBox(brm, darkSteel, matrices, vertexConsumers, light, overlay, 0.22f, 0.35f, 1.54f, 0.24f, 1.25f, 0.24f);
        drawBox(brm, darkSteel, matrices, vertexConsumers, light, overlay, 1.54f, 0.35f, 1.54f, 0.24f, 1.25f, 0.24f);
        drawBox(brm, iron,      matrices, vertexConsumers, light, overlay, 0.20f, 1.40f, 0.20f, 1.60f, 0.18f, 1.60f);

        // Верхний сужающийся ярус мачты (высота до 3.35 блока!)
        drawBox(brm, darkSteel, matrices, vertexConsumers, light, overlay, 0.48f, 1.58f, 0.48f, 1.04f, 1.45f, 1.04f);

        // Широкие высоковольтные траверсы с изоляторами и медными шинами
        drawBox(brm, iron,      matrices, vertexConsumers, light, overlay, -0.15f, 2.75f, 0.78f, 2.30f, 0.22f, 0.44f);
        drawBox(brm, insulator, matrices, vertexConsumers, light, overlay, -0.10f, 2.97f, 0.85f, 0.30f, 0.32f, 0.30f);
        drawBox(brm, insulator, matrices, vertexConsumers, light, overlay,  1.80f, 2.97f, 0.85f, 0.30f, 0.32f, 0.30f);
        drawBox(brm, copper,    matrices, vertexConsumers, light, overlay,  0.75f, 3.03f, 0.75f, 0.50f, 0.35f, 0.50f);
    }

    public static void renderSubstationModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                             int light, int overlay) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState base = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState body = Blocks.IRON_BLOCK.getDefaultState();
        BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState blueCoil = Blocks.CYAN_TERRACOTTA.getDefaultState();
        BlockState copper = Blocks.COPPER_BLOCK.getDefaultState();

        // Платформа 2x2
        drawBox(brm, base,     matrices, vertexConsumers, light, overlay, 0.05f, 0.0f, 0.05f, 1.90f, 0.35f, 1.90f);

        // Главный трансформаторный шкаф подстанции
        drawBox(brm, body,     matrices, vertexConsumers, light, overlay, 0.20f, 0.35f, 0.20f, 1.60f, 1.35f, 1.60f);
        drawBox(brm, dark,     matrices, vertexConsumers, light, overlay, 0.14f, 0.50f, 0.35f, 1.72f, 0.95f, 0.28f);
        drawBox(brm, dark,     matrices, vertexConsumers, light, overlay, 0.14f, 0.50f, 1.37f, 1.72f, 0.95f, 0.28f);

        // 4 высоковольтных керамических изолятора сверху и медный терминал
        drawBox(brm, blueCoil, matrices, vertexConsumers, light, overlay, 0.32f, 1.70f, 0.32f, 0.36f, 0.65f, 0.36f);
        drawBox(brm, blueCoil, matrices, vertexConsumers, light, overlay, 1.32f, 1.70f, 0.32f, 0.36f, 0.65f, 0.36f);
        drawBox(brm, blueCoil, matrices, vertexConsumers, light, overlay, 0.32f, 1.70f, 1.32f, 0.36f, 0.65f, 0.36f);
        drawBox(brm, blueCoil, matrices, vertexConsumers, light, overlay, 1.32f, 1.70f, 1.32f, 0.36f, 0.65f, 0.36f);
        drawBox(brm, copper,   matrices, vertexConsumers, light, overlay, 0.30f, 2.35f, 0.30f, 1.40f, 0.18f, 1.40f);
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