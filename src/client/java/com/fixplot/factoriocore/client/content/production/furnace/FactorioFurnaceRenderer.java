package com.fixplot.factoriocore.client.content.production.furnace;

import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.World;

public class FactorioFurnaceRenderer implements BlockEntityRenderer<FactorioFurnaceBlockEntity> {

    public FactorioFurnaceRenderer(BlockEntityRendererFactory.Context ctx) {} @Override public int getRenderDistance() { return 48; }

    @Override
    public void render(FactorioFurnaceBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof FactorioFurnaceBlock furnaceBlock)) return;
        if (state.get(FactorioFurnaceBlock.PART) != 0 || state.get(FactorioFurnaceBlock.UPPER)) {
            return;
        }

        int brightLight = light;
        World world = entity.getWorld();
        if (world != null) {
            int upLight = WorldRenderer.getLightmapCoordinates(world, entity.getPos().up(2));
            brightLight = Math.max(light, upLight);
            if (brightLight == 0) {
                brightLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
            }
        }

        Direction facing = state.get(FactorioFurnaceBlock.FACING);

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.translate(-0.5, 0.0, -0.5);

        renderFurnaceModel(furnaceBlock.getTier(), entity.isBurning(), matrices, vertexConsumers, brightLight, overlay);

        matrices.pop();
    }

    public static void renderFurnaceModel(int tier, boolean burning, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();

        BlockState fireCore = burning ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.COAL_BLOCK.getDefaultState();
        int fireLight = burning ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;

        if (tier == 1) {
            // === КАМЕННАЯ ПЕЧЬ (2x2 в ширину, 2.5 блока в высоту — сужающаяся каменная домна как на иконке Factorio) ===
            BlockState baseStone = Blocks.DEEPSLATE_TILES.getDefaultState();
            BlockState brickBody = Blocks.STONE_BRICKS.getDefaultState();
            BlockState mudAccent = Blocks.MUD_BRICKS.getDefaultState();
            BlockState rimDark = Blocks.POLISHED_BLACKSTONE.getDefaultState();

            // 1. Цоколь 2x2 (высота 0.45)
            drawBox(brm, baseStone, matrices, vertexConsumers, light, overlay, -0.95f, 0.0f, 0.05f, 1.90f, 0.45f, 1.90f);

            // 2. Нижний ярус каменной кладки (высота 0.45..1.35)
            drawBox(brm, brickBody, matrices, vertexConsumers, light, overlay, -0.82f, 0.45f, 0.18f, 1.64f, 0.90f, 1.64f);
            // Угловые контрфорсы
            drawBox(brm, mudAccent, matrices, vertexConsumers, light, overlay, -0.90f, 0.45f, 0.10f, 0.32f, 1.05f, 0.32f);
            drawBox(brm, mudAccent, matrices, vertexConsumers, light, overlay,  0.58f, 0.45f, 0.10f, 0.32f, 1.05f, 0.32f);
            drawBox(brm, mudAccent, matrices, vertexConsumers, light, overlay, -0.90f, 0.45f, 1.58f, 0.32f, 1.05f, 0.32f);
            drawBox(brm, mudAccent, matrices, vertexConsumers, light, overlay,  0.58f, 0.45f, 1.58f, 0.32f, 1.05f, 0.32f);

            // 3. Средний сужающийся ярус шахты (высота 1.35..2.10)
            drawBox(brm, mudAccent, matrices, vertexConsumers, light, overlay, -0.66f, 1.35f, 0.34f, 1.32f, 0.75f, 1.32f);

            // 4. Верхнее горнило / дымоход (до высоты 2.50 блока!)
            drawBox(brm, brickBody, matrices, vertexConsumers, light, overlay, -0.50f, 2.10f, 0.50f, 1.00f, 0.40f, 1.00f);
            drawBox(brm, rimDark,   matrices, vertexConsumers, fireLight, overlay, -0.34f, 2.42f, 0.66f, 0.68f, 0.10f, 0.68f);

            // 5. Передняя топка с раскалёнными углями (на лицевой стороне Z = 1.84)
            drawBox(brm, rimDark,  matrices, vertexConsumers, light, overlay, -0.45f, 0.15f, 1.76f, 0.90f, 0.75f, 0.16f);
            drawBox(brm, fireCore, matrices, vertexConsumers, fireLight, overlay, -0.32f, 0.22f, 1.80f, 0.64f, 0.52f, 0.14f);
        } else {
            // === СТАЛЬНАЯ ПЕЧЬ (2x2 в ширину, 2.5 блока в высоту — массивная стальная домна с трубами) ===
            BlockState darkChassis = Blocks.NETHERITE_BLOCK.getDefaultState();
            BlockState steelPlate = Blocks.IRON_BLOCK.getDefaultState();
            BlockState pipeMetal = Blocks.POLISHED_DEEPSLATE.getDefaultState();
            BlockState goldTrim = Blocks.COPPER_BLOCK.getDefaultState();

            // 1. Тяжёлая стальная платформа 2x2
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay, -0.96f, 0.0f, 0.04f, 1.92f, 0.40f, 1.92f);

            // 2. Основной бронированный корпус домны
            drawBox(brm, steelPlate,  matrices, vertexConsumers, light, overlay, -0.80f, 0.40f, 0.20f, 1.60f, 1.25f, 1.60f);
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay, -0.86f, 0.40f, 0.14f, 0.28f, 1.65f, 0.28f);
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay,  0.58f, 0.40f, 0.14f, 0.28f, 1.65f, 0.28f);
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay, -0.86f, 0.40f, 1.58f, 0.28f, 1.65f, 0.28f);
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay,  0.58f, 0.40f, 1.58f, 0.28f, 1.65f, 0.28f);

            // 3. Боковые воздуховоды и компрессорные трубы (слева и справа)
            drawBox(brm, pipeMetal, matrices, vertexConsumers, light, overlay, -0.94f, 0.65f, 0.55f, 0.20f, 1.25f, 0.45f);
            drawBox(brm, goldTrim,  matrices, vertexConsumers, light, overlay, -0.96f, 1.15f, 0.52f, 0.24f, 0.22f, 0.51f);

            // 4. Верхний купол и двойная выхлопная труба (высота до 2.50 блока!)
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay, -0.68f, 1.65f, 0.32f, 1.36f, 0.45f, 1.36f);
            drawBox(brm, steelPlate,  matrices, vertexConsumers, light, overlay, -0.45f, 2.10f, 0.45f, 0.90f, 0.40f, 0.90f);
            drawBox(brm, pipeMetal,   matrices, vertexConsumers, light, overlay,  0.15f, 2.00f, 1.15f, 0.38f, 0.50f, 0.38f);

            // 5. Широкое окно топки спереди
            drawBox(brm, darkChassis, matrices, vertexConsumers, light, overlay, -0.52f, 0.20f, 1.74f, 1.04f, 0.80f, 0.16f);
            drawBox(brm, fireCore,    matrices, vertexConsumers, fireLight, overlay, -0.38f, 0.28f, 1.78f, 0.76f, 0.56f, 0.14f);
        }
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
