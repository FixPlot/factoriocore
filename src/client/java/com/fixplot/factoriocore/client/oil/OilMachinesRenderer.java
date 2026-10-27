package com.fixplot.factoriocore.client.oil;

import com.fixplot.factoriocore.content.oil.OilMachines;
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

public class OilMachinesRenderer {

    private static void drawBox(BlockRenderManager brm, BlockState state, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay,
                                float x, float y, float z, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(sx, sy, sz);
        brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    // === 0. МЕСТОРОЖДЕНИЕ СЫРОЙ НЕФТИ (CRUDE OIL DEPOSIT) ===
    public static class OilDepositRenderer implements BlockEntityRenderer<OilMachines.CrudeOilDepositBlockEntity> {
        public OilDepositRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 64; }

        @Override
        public void render(OilMachines.CrudeOilDepositBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            renderOilDepositModel(matrices, vertexConsumers, light, overlay);
        }

        public static void renderOilDepositModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, int overlay) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState rockGround = Blocks.DEEPSLATE.getDefaultState();
            BlockState blackOil = Blocks.BLACK_CONCRETE.getDefaultState();
            BlockState glossyOil = Blocks.OBSIDIAN.getDefaultState();

            drawBox(brm, rockGround, matrices, vertexConsumers, light, overlay, 0.0f, 0.0f, 0.0f, 1.0f, 0.96f, 1.0f);
            drawBox(brm, blackOil,   matrices, vertexConsumers, light, overlay, 0.08f, 0.96f, 0.08f, 0.84f, 0.07f, 0.84f);
            drawBox(brm, glossyOil,  matrices, vertexConsumers, light, overlay, 0.22f, 0.98f, 0.22f, 0.56f, 0.07f, 0.56f);
        }
    }

    // === 1. НЕФТЯНАЯ ВЫШКА (PUMPJACK — 3x3x2.5 + выходной патрубок на переднем-правом углу) ===
    public static class PumpjackRenderer implements BlockEntityRenderer<OilMachines.PumpjackBlockEntity> {
        public PumpjackRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 48; }

        @Override
        public void render(OilMachines.PumpjackBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof OilMachines.PumpjackBlock)) return;
            if (state.get(OilMachines.PumpjackBlock.PART) != 4 || state.get(OilMachines.PumpjackBlock.UPPER)) return;

            Direction facing = state.get(OilMachines.PumpjackBlock.FACING);
            float beamAngle = entity.getClientBeamAngle(tickDelta);

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            renderPumpjackModel(matrices, vertexConsumers, light, overlay, beamAngle);
            matrices.pop();
        }

        public static void renderPumpjackModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                               int light, int overlay, float beamAngle) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState greenFrame = Blocks.GREEN_TERRACOTTA.getDefaultState();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();

            drawBox(brm, base,       matrices, vertexConsumers, light, overlay, -0.90f, 0.0f, -0.90f, 2.80f, 0.35f, 2.80f);
            drawBox(brm, greenFrame, matrices, vertexConsumers, light, overlay,  0.15f, 0.35f,  0.15f, 0.70f, 1.50f, 0.70f);
            drawBox(brm, dark,       matrices, vertexConsumers, light, overlay, -0.45f, 0.35f, -0.75f, 1.90f, 0.85f, 0.70f);

            // Выходной трубный фланец на переднем-правом тайле (X: -0.5, Z: +1.85 -> смотрит вперёд +Z)
            drawBox(brm, steel,      matrices, vertexConsumers, light, overlay, -0.80f, 0.22f,  1.65f, 0.60f, 0.55f, 0.35f);

            matrices.push();
            matrices.translate(0.5, 1.85, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(beamAngle));
            drawBox(brm, greenFrame, matrices, vertexConsumers, light, overlay, -0.22f, -0.15f, -1.10f, 0.44f, 0.32f, 2.20f);
            drawBox(brm, steel,      matrices, vertexConsumers, light, overlay, -0.28f, -0.35f,  0.90f, 0.56f, 0.75f, 0.35f);
            matrices.pop();
        }
    }

    // === 2. НЕФТЕПЕРЕРАБАТЫВАЮЩИЙ ЗАВОД (OIL REFINERY — 5x5x3.4 + 2 входных и 3 выходных патрубка) ===
    public static class RefineryRenderer implements BlockEntityRenderer<OilMachines.OilRefineryBlockEntity> {
        public RefineryRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 64; }

        @Override
        public void render(OilMachines.OilRefineryBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof OilMachines.OilRefineryBlock)) return;
            if (state.get(OilMachines.OilRefineryBlock.PART) != 12 || state.get(OilMachines.OilRefineryBlock.UPPER)) return;

            Direction facing = state.get(OilMachines.OilRefineryBlock.FACING);

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            renderRefineryModel(matrices, vertexConsumers, light, overlay, entity.isWorking());
            matrices.pop();
        }

        public static void renderRefineryModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                               int light, int overlay, boolean working) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.DEEPSLATE_TILES.getDefaultState();
            BlockState rustTower = Blocks.COPPER_BLOCK.getDefaultState();
            BlockState yellowTank = Blocks.YELLOW_TERRACOTTA.getDefaultState();
            BlockState greenTank = Blocks.GREEN_TERRACOTTA.getDefaultState();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState flare = working ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.COAL_BLOCK.getDefaultState();
            int flareLight = working ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

            // Фундамент 5x5
            drawBox(brm, base,        matrices, vertexConsumers, light, overlay, -1.95f, 0.0f, -1.95f, 4.90f, 0.42f, 4.90f);

            // Центральная ректификационная колонна
            drawBox(brm, rustTower,   matrices, vertexConsumers, light, overlay, -0.35f, 0.42f, -1.35f, 1.70f, 2.65f, 1.70f);
            drawBox(brm, dark,        matrices, vertexConsumers, light, overlay, -0.42f, 1.45f, -1.42f, 1.84f, 0.25f, 1.84f);
            drawBox(brm, dark,        matrices, vertexConsumers, light, overlay, -0.42f, 2.35f, -1.42f, 1.84f, 0.25f, 1.84f);

            // Левая башня крекинга и факельная труба
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay, -1.75f, 0.42f, -1.65f, 1.20f, 2.20f, 1.20f);
            drawBox(brm, dark,        matrices, vertexConsumers, light, overlay, -1.45f, 2.62f, -1.35f, 0.60f, 0.75f, 0.60f);
            drawBox(brm, flare,       matrices, vertexConsumers, flareLight, overlay, -1.40f, 3.37f, -1.30f, 0.50f, 0.14f, 0.50f);

            // Правый сепаратор
            drawBox(brm, yellowTank,  matrices, vertexConsumers, light, overlay,  1.50f, 0.42f, -1.55f, 1.25f, 2.15f, 1.35f);
            drawBox(brm, dark,        matrices, vertexConsumers, light, overlay, -1.10f, 2.75f, -0.85f, 1.30f, 0.35f, 0.45f);

            // Передние резервуары
            drawBox(brm, dark,        matrices, vertexConsumers, light, overlay, -1.75f, 0.42f, -0.20f, 1.30f, 1.75f, 1.35f);
            drawBox(brm, rustTower,   matrices, vertexConsumers, light, overlay, -0.25f, 0.42f,  0.55f, 1.40f, 1.55f, 1.40f);
            drawBox(brm, greenTank,   matrices, vertexConsumers, light, overlay,  1.35f, 0.42f,  0.45f, 1.35f, 1.50f, 1.45f);
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay, -1.50f, 1.25f,  0.10f, 4.00f, 0.45f, 0.55f);

            // 2 ВХОДНЫХ ПАТРУБКА СЗАДИ (Z = -2.0, X = -0.5 и X = +1.5)
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay, -0.80f, 0.22f, -2.02f, 0.60f, 0.55f, 0.25f);
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay,  1.20f, 0.22f, -2.02f, 0.60f, 0.55f, 0.25f);

            // 3 ВЫХОДНЫХ ПАТРУБКА СПЕРЕДИ (Z = +3.0, X = -1.5, +0.5, +2.5)
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay, -1.80f, 0.22f,  2.77f, 0.60f, 0.55f, 0.25f);
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay,  0.20f, 0.22f,  2.77f, 0.60f, 0.55f, 0.25f);
            drawBox(brm, steel,       matrices, vertexConsumers, light, overlay,  2.20f, 0.22f,  2.77f, 0.60f, 0.55f, 0.25f);
        }
    }

    // === 3. ХИМИЧЕСКИЙ ЗАВОД (CHEMICAL PLANT — 3x3x2.0 + 2 входных и 2 выходных патрубка) ===
    public static class ChemicalPlantRenderer implements BlockEntityRenderer<OilMachines.ChemicalPlantBlockEntity> {
        public ChemicalPlantRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 48; }

        @Override
        public void render(OilMachines.ChemicalPlantBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof OilMachines.ChemicalPlantBlock)) return;
            if (state.get(OilMachines.ChemicalPlantBlock.PART) != 4 || state.get(OilMachines.ChemicalPlantBlock.UPPER)) return;

            Direction facing = state.get(OilMachines.ChemicalPlantBlock.FACING);

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            renderChemicalPlantModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        }

        public static void renderChemicalPlantModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                    int light, int overlay) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState yellowVat = Blocks.YELLOW_TERRACOTTA.getDefaultState();
            BlockState tealVat = Blocks.CYAN_TERRACOTTA.getDefaultState();
            BlockState glass = Blocks.PURPLE_STAINED_GLASS.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();

            drawBox(brm, base,      matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.95f, 2.90f, 0.35f, 2.90f);
            drawBox(brm, yellowVat, matrices, vertexConsumers, light, overlay, -0.80f, 0.35f, -0.80f, 1.15f, 1.55f, 1.15f);
            drawBox(brm, tealVat,   matrices, vertexConsumers, light, overlay,  0.55f, 0.35f, -0.80f, 1.25f, 1.35f, 1.25f);
            drawBox(brm, glass,     matrices, vertexConsumers, light, overlay, -0.65f, 0.35f,  0.55f, 2.30f, 1.10f, 1.15f);
            drawBox(brm, dark,      matrices, vertexConsumers, light, overlay, -0.70f, 1.45f,  0.50f, 2.40f, 0.25f, 1.25f);

            // 2 ВХОДНЫХ ПАТРУБКА СЗАДИ (part 0 и 2)
            drawBox(brm, steel,     matrices, vertexConsumers, light, overlay, -0.80f, 0.22f, -1.02f, 0.60f, 0.55f, 0.25f);
            drawBox(brm, steel,     matrices, vertexConsumers, light, overlay,  1.20f, 0.22f, -1.02f, 0.60f, 0.55f, 0.25f);

            // 2 ВЫХОДНЫХ ПАТРУБКА СПЕРЕДИ (part 6 и 8)
            drawBox(brm, steel,     matrices, vertexConsumers, light, overlay, -0.80f, 0.22f,  1.77f, 0.60f, 0.55f, 0.25f);
            drawBox(brm, steel,     matrices, vertexConsumers, light, overlay,  1.20f, 0.22f,  1.77f, 0.60f, 0.55f, 0.25f);
        }
    }
}