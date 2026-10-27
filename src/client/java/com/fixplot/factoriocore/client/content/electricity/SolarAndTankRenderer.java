package com.fixplot.factoriocore.client.content.electricity;

import com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines;
import com.fixplot.factoriocore.content.fluids.FactorioFluidContainer;
import com.fixplot.factoriocore.content.fluids.StorageTankBlock;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class SolarAndTankRenderer {

    private static void drawBox(BlockRenderManager brm, BlockState state, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay,
                                float x, float y, float z, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(x, y, z);
        matrices.scale(sx, sy, sz);
        brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }

    // === 1. РЕЗЕРВУАР ДЛЯ ХРАНЕНИЯ ЖИДКОСТЕЙ (STORAGE TANK 3x3x2) ===
    public static class StorageTankRenderer implements BlockEntityRenderer<StorageTankBlock.StorageTankBlockEntity> {
        public StorageTankRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 56; }

        @Override
        public void render(StorageTankBlock.StorageTankBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof StorageTankBlock)) return;
            if (state.get(StorageTankBlock.PART) != 4 || state.get(StorageTankBlock.UPPER)) return;

            Direction facing = state.get(StorageTankBlock.FACING);
            float fillRatio = MathHelper.clamp(
                    entity.getFluidAmount() / (float) StorageTankBlock.StorageTankBlockEntity.CAPACITY_MB, 0.0f, 1.0f);

            matrices.push();
            matrices.translate(0.5, 0.0, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, 0.0, -0.5);

            renderStorageTankModel(matrices, vertexConsumers, light, overlay, entity.getFluidKind(), fillRatio);
            matrices.pop();
        }

        public static void renderStorageTankModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  int light, int overlay, FactorioFluidContainer.FluidKind kind, float fillRatio) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState glass = Blocks.GLASS.getDefaultState();

            // Опорная плита 3x3 и угловые стойки
            drawBox(brm, base,  matrices, vertexConsumers, light, overlay, -0.92f, 0.0f, -0.92f, 2.84f, 0.30f, 2.84f);

            // Массивный цилиндрический бак (перекрещенные восьмигранные секции)
            drawBox(brm, steel, matrices, vertexConsumers, light, overlay, -0.70f, 0.30f, -0.45f, 2.40f, 1.65f, 1.90f);
            drawBox(brm, steel, matrices, vertexConsumers, light, overlay, -0.45f, 0.30f, -0.70f, 1.90f, 1.65f, 2.40f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, -0.20f, 1.95f, -0.20f, 1.40f, 0.16f, 1.40f);

            // Смотровое окно уровня жидкости сверху и сбоку
            drawBox(brm, glass, matrices, vertexConsumers, light, overlay,  0.10f, 0.45f, -0.76f, 0.80f, 1.35f, 2.52f);

            if (fillRatio > 0.01f && kind != FactorioFluidContainer.FluidKind.NONE) {
                BlockState liquidBlock = switch (kind) {
                    case WATER -> Blocks.LIGHT_BLUE_CONCRETE.getDefaultState();
                    case STEAM -> Blocks.WHITE_CONCRETE.getDefaultState();
                    case CRUDE_OIL -> Blocks.BLACK_CONCRETE.getDefaultState();
                    case HEAVY_OIL -> Blocks.RED_TERRACOTTA.getDefaultState();
                    case LIGHT_OIL -> Blocks.YELLOW_CONCRETE.getDefaultState();
                    case PETROLEUM_GAS -> Blocks.PURPLE_CONCRETE.getDefaultState();
                    case LUBRICANT -> Blocks.LIME_TERRACOTTA.getDefaultState();
                    case SULFURIC_ACID -> Blocks.YELLOW_GLAZED_TERRACOTTA.getDefaultState();
                    default -> Blocks.BLUE_CONCRETE.getDefaultState();
                };
                float h = Math.max(0.08f, 1.30f * fillRatio);
                drawBox(brm, liquidBlock, matrices, vertexConsumers, LightmapTextureManager.MAX_LIGHT_COORDINATE, overlay,
                        0.14f, 0.46f, -0.78f, 0.72f, h, 2.56f);
            }

            // Угловые трубные патрубки (как в Factorio)
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, -0.80f, 0.22f, -1.02f, 0.60f, 0.55f, 0.32f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay,  1.20f, 0.22f, -1.02f, 0.60f, 0.55f, 0.32f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, -0.80f, 0.22f,  1.70f, 0.60f, 0.55f, 0.32f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay,  1.20f, 0.22f,  1.70f, 0.60f, 0.55f, 0.32f);
        }
    }

    // === 2. СОЛНЕЧНАЯ ПАНЕЛЬ (SOLAR PANEL 3x3x1) ===
    public static class SolarPanelRenderer implements BlockEntityRenderer<SolarAndAccumulatorMachines.SolarPanelBlockEntity> {
        public SolarPanelRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 56; }

        @Override
        public void render(SolarAndAccumulatorMachines.SolarPanelBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof SolarAndAccumulatorMachines.SolarPanelBlock)) return;
            if (state.get(SolarAndAccumulatorMachines.SolarPanelBlock.PART) != 4) return;

            renderSolarPanelModel(matrices, vertexConsumers, light, overlay);
        }

        public static void renderSolarPanelModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                 int light, int overlay) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState frame = Blocks.IRON_BLOCK.getDefaultState();
            BlockState blueCell = Blocks.LAPIS_BLOCK.getDefaultState();
            BlockState cyanBus = Blocks.CYAN_TERRACOTTA.getDefaultState();

            // Стальная рама 3x3
            drawBox(brm, base,  matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, -0.95f, 2.90f, 0.22f, 2.90f);
            drawBox(brm, frame, matrices, vertexConsumers, light, overlay, -0.90f, 0.22f, -0.90f, 2.80f, 0.26f, 2.80f);

            // Сетка 3x3 синих фотоэлементов с серебристыми токоведущими дорожками
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float cx = -0.82f + col * 0.90f;
                    float cz = -0.82f + row * 0.90f;
                    drawBox(brm, blueCell, matrices, vertexConsumers, light, overlay, cx, 0.48f, cz, 0.84f, 0.14f, 0.84f);
                    drawBox(brm, cyanBus,  matrices, vertexConsumers, light, overlay, cx + 0.38f, 0.50f, cz + 0.04f, 0.08f, 0.14f, 0.76f);
                }
            }
        }
    }

    // === 3. АККУМУЛЯТОР (ACCUMULATOR 2x2x2) ===
    public static class AccumulatorRenderer implements BlockEntityRenderer<SolarAndAccumulatorMachines.AccumulatorBlockEntity> {
        public AccumulatorRenderer(BlockEntityRendererFactory.Context ctx) {}
        @Override public int getRenderDistance() { return 56; }

        @Override
        public void render(SolarAndAccumulatorMachines.AccumulatorBlockEntity entity, float tickDelta, MatrixStack matrices,
                           VertexConsumerProvider vertexConsumers, int light, int overlay) {
            BlockState state = entity.getCachedState();
            if (!(state.getBlock() instanceof SolarAndAccumulatorMachines.AccumulatorBlock)) return;
            if (state.get(SolarAndAccumulatorMachines.AccumulatorBlock.PART) != 0
                    || state.get(SolarAndAccumulatorMachines.AccumulatorBlock.UPPER)) return;

            renderAccumulatorModel(matrices, vertexConsumers, light, overlay, entity.getChargeRatio(), entity.getChargeState());
        }

        public static void renderAccumulatorModel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                  int light, int overlay, float chargeRatio, int chargeState) {
            BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
            BlockState base = Blocks.DEEPSLATE_TILES.getDefaultState();
            BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
            BlockState copper = Blocks.COPPER_BLOCK.getDefaultState();
            BlockState glow = (chargeState != 0 || chargeRatio > 0.15f)
                    ? Blocks.SEA_LANTERN.getDefaultState()
                    : Blocks.CYAN_TERRACOTTA.getDefaultState();
            int glowLight = (chargeState != 0 || chargeRatio > 0.15f) ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

            // Корпус 2x2 (X: 0..2, Z: 0..2)
            drawBox(brm, base,  matrices, vertexConsumers, light, overlay, 0.06f, 0.0f, 0.06f, 1.88f, 0.35f, 1.88f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, 0.18f, 0.35f, 0.18f, 1.64f, 0.65f, 1.64f);

            // 4 высоковольтные конденсаторные башни по углам 2x2
            float[] offsets = { 0.22f, 1.18f };
            for (float ox : offsets) {
                for (float oz : offsets) {
                    drawBox(brm, steel,  matrices, vertexConsumers, light, overlay, ox, 1.00f, oz, 0.60f, 0.90f, 0.60f);
                    drawBox(brm, glow,   matrices, vertexConsumers, glowLight, overlay, ox + 0.08f, 1.25f, oz - 0.02f, 0.44f, 0.45f, 0.64f);
                    drawBox(brm, copper, matrices, vertexConsumers, light, overlay, ox + 0.15f, 1.90f, oz + 0.15f, 0.30f, 0.22f, 0.30f);
                }
            }

            // Центральная разрядная шина сверху
            drawBox(brm, glow, matrices, vertexConsumers, glowLight, overlay, 0.72f, 1.00f, 0.72f, 0.56f, 1.15f, 0.56f);
        }
    }
}