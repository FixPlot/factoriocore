package com.fixplot.factoriocore.client.content.production.assembler;

import com.fixplot.factoriocore.content.production.assembler.AssemblerBlock;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
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

public class AssemblerRenderer implements BlockEntityRenderer<AssemblerBlockEntity> {
    public AssemblerRenderer(BlockEntityRendererFactory.Context ctx) {}
    @Override public int getRenderDistance() { return 48; }

    @Override
    public void render(AssemblerBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof AssemblerBlock)) return;
        if (state.get(AssemblerBlock.PART) != 4 || state.get(AssemblerBlock.UPPER)) return;
        Direction facing = state.get(AssemblerBlock.FACING);
        float angle = entity.isWorking() && entity.getWorld() != null ? (entity.getWorld().getTime() + tickDelta) * 10f : 0;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        renderAssemblerModel(matrices, vertexConsumers, light, overlay, angle);

        matrices.pop();
    }

    public static void renderAssemblerModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            int light, int overlay, float workingAngle) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState baseFrame = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState darkMetal = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState glass = Blocks.TINTED_GLASS.getDefaultState();
        BlockState gear = Blocks.COPPER_BLOCK.getDefaultState();

        drawBox(brm, baseFrame, matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.95f, 2.90f, 0.40f, 2.90f);
        drawBox(brm, darkMetal, matrices, vertexConsumers, light, overlay, -0.85f, 0.40f, -0.85f, 0.35f, 1.40f, 0.35f);
        drawBox(brm, darkMetal, matrices, vertexConsumers, light, overlay,  1.50f, 0.40f, -0.85f, 0.35f, 1.40f, 0.35f);
        drawBox(brm, darkMetal, matrices, vertexConsumers, light, overlay, -0.85f, 0.40f,  1.50f, 0.35f, 1.40f, 0.35f);
        drawBox(brm, darkMetal, matrices, vertexConsumers, light, overlay,  1.50f, 0.40f,  1.50f, 0.35f, 1.40f, 0.35f);

        drawBox(brm, glass, matrices, vertexConsumers, light, overlay, -0.50f, 0.40f, -0.50f, 2.0f, 1.00f, 2.0f);
        drawBox(brm, darkMetal, matrices, vertexConsumers, light, overlay, -0.65f, 1.40f, -0.65f, 2.30f, 0.35f, 2.30f);

        matrices.push();
        matrices.translate(0.5, 1.85, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(workingAngle));
        drawBox(brm, gear, matrices, vertexConsumers, light, overlay, -0.6f, 0.0f, -0.1f, 1.2f, 0.15f, 0.2f);
        drawBox(brm, gear, matrices, vertexConsumers, light, overlay, -0.1f, 0.0f, -0.6f, 0.2f, 0.15f, 1.2f);
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