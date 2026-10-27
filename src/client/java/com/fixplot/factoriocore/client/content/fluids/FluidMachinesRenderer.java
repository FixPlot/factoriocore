package com.fixplot.factoriocore.client.content.fluids;

import com.fixplot.factoriocore.content.fluids.*;
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

public class FluidMachinesRenderer {

    private static void drawBox(BlockRenderManager brm, BlockState state, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay,
                                float x, float y, float z, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(sx, sy, sz);
        brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    // === 1. РЕНДЕР "Г"-ОБРАЗНОЙ ПРИБРЕЖНОЙ ПОМПЫ (стоит на берегу и опускает трубу вниз в воду озера!) ===
    public static class PumpRenderer implements BlockEntityRenderer<OffshorePumpBlockEntity> {
        public PumpRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public int getRenderDistance() { return 48; }

        @Override
        public void render(OffshorePumpBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof OffshorePumpBlock)) return;
            Direction facing = state.get(OffshorePumpBlock.FACING);

            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState copper = Blocks.COPPER_BLOCK.getDefaultState();
            BlockState bluePipe = Blocks.CYAN_TERRACOTTA.getDefaultState();

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            // Наземный насосный блок на кромке берега (Z: 0.05..0.95, Y: 0.0..0.95 — ровно в уровень с трубами!)
            drawBox(brm, dark,     matrices, vertexConsumers, light, overlay, 0.15f, 0.0f,  0.02f, 0.70f, 0.25f, 0.90f);
            drawBox(brm, bluePipe, matrices, vertexConsumers, light, overlay, 0.22f, 0.25f, 0.05f, 0.56f, 0.55f, 0.80f);
            drawBox(brm, copper,   matrices, vertexConsumers, light, overlay, 0.18f, 0.55f, 0.20f, 0.64f, 0.35f, 0.55f);

            // Горизонтальная консоль буквы "Г" вперёд над водой (Z: 0.75..1.75, Y: 0.45..0.82)
            drawBox(brm, steel,    matrices, vertexConsumers, light, overlay, 0.25f, 0.45f, 0.75f, 0.50f, 0.36f, 1.00f);

            // Вертикальная водозаборная колонна, уходящая вниз на -1.25 блока прямо в воду озера (Y: -1.25..0.55)!
            drawBox(brm, dark,     matrices, vertexConsumers, light, overlay, 0.28f, -1.25f, 1.28f, 0.44f, 1.80f, 0.44f);
            drawBox(brm, copper,   matrices, vertexConsumers, light, overlay, 0.22f, -1.30f, 1.22f, 0.56f, 0.25f, 0.56f);

            matrices.pop();
        }
    }

    // === 2. РЕНДЕР БОЙЛЕРА (3x2x2.5, строго отцентрированный по мастер-блоку PART = 1) ===
    public static class BoilerRenderer implements BlockEntityRenderer<BoilerBlockEntity> {
        public BoilerRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public int getRenderDistance() { return 48; }

        @Override
        public void render(BoilerBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof BoilerBlock)) return;
            Direction facing = state.get(BoilerBlock.FACING);
            boolean burning = entity.isBurning();

            renderBoilerModel(facing, burning, matrices, vertexConsumers, light, overlay);
        }


        public static void renderBoilerModel(Direction facing, boolean burning, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {

            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState darkBase = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState boilerDrum = Blocks.COPPER_BLOCK.getDefaultState();
            BlockState steelBand = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState pipeNozzle = Blocks.IRON_BLOCK.getDefaultState();
            BlockState fireCore = burning ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.COAL_BLOCK.getDefaultState();
            int fireLight = burning ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            // Платформа 3x2 (X: -0.95..1.95, Z: 0.05..1.95)
            drawBox(brm, darkBase,   matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, 0.05f, 2.90f, 0.45f, 1.90f);
            // Горизонтальный медный барабан котла
            drawBox(brm, boilerDrum, matrices, vertexConsumers, light, overlay, -0.88f, 0.45f, 0.20f, 2.76f, 1.65f, 1.55f);
            drawBox(brm, steelBand,  matrices, vertexConsumers, light, overlay, -0.92f, 0.42f, 0.16f, 0.25f, 1.72f, 1.63f);
            drawBox(brm, steelBand,  matrices, vertexConsumers, light, overlay,  1.67f, 0.42f, 0.16f, 0.25f, 1.72f, 1.63f);

            // Боковые водяные патрубки для соединения бойлеров в ряд (слева X=-1.0 и справа X=+2.0)
            drawBox(brm, pipeNozzle, matrices, vertexConsumers, light, overlay, -1.00f, 0.25f, 0.65f, 0.12f, 0.65f, 0.65f);
            drawBox(brm, pipeNozzle, matrices, vertexConsumers, light, overlay,  1.88f, 0.25f, 0.65f, 0.12f, 0.65f, 0.65f);

            // Паровой выходной коллектор спереди по центру (Z = 1.75..2.00)
            drawBox(brm, pipeNozzle, matrices, vertexConsumers, light, overlay,  0.20f, 0.50f, 1.75f, 0.60f, 0.70f, 0.25f);

            // Верхний дымоход (до высоты 2.50 блока)
            drawBox(brm, steelBand,  matrices, vertexConsumers, light, overlay,  0.15f, 2.10f, 0.65f, 0.70f, 0.40f, 0.70f);
            // Топка
            drawBox(brm, fireCore,   matrices, vertexConsumers, fireLight, overlay, 0.10f, 0.15f, 0.02f, 0.80f, 0.55f, 0.20f);

            matrices.pop();
        }
    }

    // === 3. РЕНДЕР ПАРОВОГО ДВИГАТЕЛЯ (3x5x3.5) ===
    public static class SteamEngineRenderer implements BlockEntityRenderer<SteamEngineBlockEntity> {
        public SteamEngineRenderer(BlockEntityRendererFactory.Context ctx) {}

        @Override
        public int getRenderDistance() { return 56; }

        @Override
        public void render(SteamEngineBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof SteamEngineBlock)) return;
            Direction facing = state.get(SteamEngineBlock.FACING);
            float wheelAngle = entity.getClientFlywheelAngle(tickDelta);

            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState heavyBase = Blocks.DEEPSLATE_TILES.getDefaultState();
            BlockState steelBody = Blocks.IRON_BLOCK.getDefaultState();
            BlockState darkFrame = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState copperCoil = Blocks.COPPER_BLOCK.getDefaultState();

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            drawBox(brm, heavyBase, matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, 0.05f, 2.90f, 0.55f, 4.90f);
            drawBox(brm, steelBody, matrices, vertexConsumers, light, overlay, -0.75f, 0.55f, 0.30f, 2.50f, 2.25f, 2.75f);
            drawBox(brm, darkFrame, matrices, vertexConsumers, light, overlay, -0.82f, 0.55f, 0.45f, 2.64f, 2.35f, 0.30f);
            drawBox(brm, darkFrame, matrices, vertexConsumers, light, overlay, -0.82f, 0.55f, 1.85f, 2.64f, 2.35f, 0.30f);

            drawBox(brm, darkFrame, matrices, vertexConsumers, light, overlay,  0.10f, 2.80f, 0.80f, 0.80f, 0.70f, 0.80f);
            drawBox(brm, darkFrame, matrices, vertexConsumers, light, overlay,  0.10f, 2.80f, 2.10f, 0.80f, 0.70f, 0.80f);

            drawBox(brm, copperCoil, matrices, vertexConsumers, light, overlay, -0.60f, 0.55f, 3.20f, 2.20f, 1.95f, 1.45f);

            matrices.push();
            matrices.translate(0.5, 1.65, 3.95);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(wheelAngle));
            drawBox(brm, steelBody, matrices, vertexConsumers, light, overlay, -0.90f, -0.22f, -0.25f, 1.80f, 0.44f, 0.50f);
            drawBox(brm, darkFrame, matrices, vertexConsumers, light, overlay, -0.22f, -0.90f, -0.25f, 0.44f, 1.80f, 0.50f);
            matrices.pop();

            matrices.pop();
        }
    }
}