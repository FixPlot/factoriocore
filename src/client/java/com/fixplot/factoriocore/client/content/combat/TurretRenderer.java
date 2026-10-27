package com.fixplot.factoriocore.client.content.combat;

import com.fixplot.factoriocore.content.combat.turret.TurretBlock;
import com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public class TurretRenderer implements BlockEntityRenderer<TurretBlockEntity> {

    public TurretRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public int getRenderDistance() {
        return 64;
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

    @Override
    public void render(TurretBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!(state.getBlock() instanceof TurretBlock tb)) return;
        if (state.get(TurretBlock.PART) != 0 || state.get(TurretBlock.UPPER)) return;

        int kind = tb.getTurretKind();
        renderTurretModel(kind, entity.getTurretYaw(), entity.getTurretPitch(), entity.isFiring(),
                matrices, vertexConsumers, light, overlay);

        // Отрисовка 3D-луча Лазерной турели при выстреле
        if (kind == 1 && entity.isFiring()) {
            Vec3d target = entity.getTargetVec();
            if (target.lengthSquared() > 1.0) {
                drawLaserBeam(entity, target, matrices, vertexConsumers);
            }
        }
    }

    private static void drawLaserBeam(TurretBlockEntity entity, Vec3d targetWorld,
                                      MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        float sx = 1.0f;
        float sy = 1.68f;
        float sz = 1.0f;
        float ex = (float) (targetWorld.x - entity.getPos().getX());
        float ey = (float) (targetWorld.y - entity.getPos().getY());
        float ez = (float) (targetWorld.z - entity.getPos().getZ());

        VertexConsumer lines = vertexConsumers.getBuffer(RenderLayer.getLines());
        MatrixStack.Entry entry = matrices.peek();

        float dx = ex - sx;
        float dy = ey - sy;
        float dz = ez - sz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-4f) {
            dx /= len; dy /= len; dz /= len;
        }

        // Тройной ярко-красный лазерный луч
        for (int i = -1; i <= 1; i++) {
            float off = i * 0.03f;
            lines.vertex(entry, sx + off, sy, sz).color(1.0f, 0.15f, 0.15f, 1.0f).normal(entry, dx, dy, dz);
            lines.vertex(entry, ex + off, ey, ez).color(1.0f, 0.35f, 0.25f, 1.0f).normal(entry, dx, dy, dz);
        }
    }

    public static void renderTurretModel(int kind, float yaw, float pitch, boolean firing,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, int overlay) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState base = Blocks.DEEPSLATE_TILES.getDefaultState();
        BlockState dark = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState steel = Blocks.IRON_BLOCK.getDefaultState();
        BlockState brass = Blocks.YELLOW_TERRACOTTA.getDefaultState();
        BlockState redLaser = firing ? Blocks.SHROOMLIGHT.getDefaultState() : Blocks.REDSTONE_BLOCK.getDefaultState();
        BlockState orangeTank = Blocks.ORANGE_TERRACOTTA.getDefaultState();

        // 1. Тяжёлый бронированный пьедестал 2x2 (X: 0..2, Z: 0..2)
        drawBox(brm, base, matrices, vertexConsumers, light, overlay, 0.08f, 0.0f, 0.08f, 1.84f, 0.38f, 1.84f);
        drawBox(brm, dark, matrices, vertexConsumers, light, overlay, 0.32f, 0.38f, 0.32f, 1.36f, 0.52f, 1.36f);

        // 2. Поворотная голова турели в центре 2x2 (X = 1.0, Y = 1.15, Z = 1.0)
        matrices.push();
        matrices.translate(1.0, 1.15, 1.0);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));

        if (kind == 0) {
            // === ПУЛЕМЁТНАЯ ТУРЕЛЬ (GUN TURRET) ===
            drawBox(brm, brass, matrices, vertexConsumers, light, overlay, -0.46f, -0.25f, -0.52f, 0.92f, 0.56f, 1.04f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, -0.54f, -0.15f, -0.35f, 0.16f, 0.42f, 0.55f); // Короб с патронной лентой
            // Спаренный двуствольный пулемёт Гатлинга
            drawBox(brm, steel, matrices, vertexConsumers, light, overlay, -0.24f, -0.05f,  0.45f, 0.16f, 0.16f, 0.85f);
            drawBox(brm, steel, matrices, vertexConsumers, light, overlay,  0.08f, -0.05f,  0.45f, 0.16f, 0.16f, 0.85f);
            drawBox(brm, dark,  matrices, vertexConsumers, light, overlay, -0.28f, -0.08f,  1.05f, 0.56f, 0.22f, 0.14f);
        } else if (kind == 1) {
            // === ЛАЗЕРНАЯ ТУРЕЛЬ (LASER TURRET) ===
            int beamLight = firing ? LightmapTextureManager.MAX_LIGHT_COORDINATE : light;
            drawBox(brm, steel,    matrices, vertexConsumers, light, overlay, -0.44f, -0.25f, -0.48f, 0.88f, 0.62f, 0.96f);
            drawBox(brm, dark,     matrices, vertexConsumers, light, overlay, -0.30f,  0.37f, -0.30f, 0.60f, 0.20f, 0.80f);
            drawBox(brm, redLaser, matrices, vertexConsumers, beamLight, overlay, -0.22f,  0.02f,  0.42f, 0.44f, 0.44f, 0.55f);
        } else {
            // === ОГНЕМЁТНАЯ ТУРЕЛЬ (FLAMETHROWER TURRET) ===
            drawBox(brm, dark,       matrices, vertexConsumers, light, overlay, -0.45f, -0.25f, -0.55f, 0.90f, 0.55f, 1.05f);
            // Два боковых оранжевых топливных баллона под давлением
            drawBox(brm, orangeTank, matrices, vertexConsumers, light, overlay, -0.68f, -0.15f, -0.45f, 0.26f, 0.45f, 0.85f);
            drawBox(brm, orangeTank, matrices, vertexConsumers, light, overlay,  0.42f, -0.15f, -0.45f, 0.26f, 0.45f, 0.85f);
            // Длинный брандспойт с горелкой
            drawBox(brm, steel,      matrices, vertexConsumers, light, overlay, -0.14f, -0.05f,  0.45f, 0.28f, 0.26f, 0.95f);
        }

        matrices.pop();
    }
}