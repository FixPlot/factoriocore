package com.fixplot.factoriocore.client.content.logistics.inserter;

import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltLane;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class InserterRenderer implements BlockEntityRenderer<InserterBlockEntity> {

    private static final double[] BELT_SLOT_Z = { 0.125, 0.375, 0.625, 0.875 };

    public InserterRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public int getRenderDistance() {
        return 36;
    }

    private static BlockState resolveMainColor(InserterBlockEntity entity, boolean isBurner) {
        if (isBurner) return Blocks.POLISHED_DEEPSLATE.getDefaultState();
        if (entity != null) {
            Block b = entity.getCachedState().getBlock();
            if (b == ModBlocks.LONG_HANDED_INSERTER) return Blocks.RED_CONCRETE.getDefaultState();
            if (b == ModBlocks.FAST_INSERTER) return Blocks.BLUE_CONCRETE.getDefaultState();
        }
        return Blocks.YELLOW_CONCRETE.getDefaultState();
    }

    private static BlockState resolveJointColor(InserterBlockEntity entity, boolean isBurner) {
        if (isBurner) return Blocks.COPPER_BLOCK.getDefaultState();
        if (entity != null) {
            Block b = entity.getCachedState().getBlock();
            if (b == ModBlocks.LONG_HANDED_INSERTER) return Blocks.SMOOTH_QUARTZ.getDefaultState();
            if (b == ModBlocks.FAST_INSERTER) return Blocks.LIGHT_BLUE_CONCRETE.getDefaultState();
        }
        return Blocks.GOLD_BLOCK.getDefaultState();
    }

    @Override
    public void render(InserterBlockEntity entity, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = entity.getPos();

        double distSq = 0.0;
        if (client.gameRenderer != null && client.gameRenderer.getCamera() != null) {
            Vec3d cam = client.gameRenderer.getCamera().getPos();
            double dx = (pos.getX() + 0.5) - cam.x;
            double dy = (pos.getY() + 0.5) - cam.y;
            double dz = (pos.getZ() + 0.5) - cam.z;
            distSq = dx * dx + dy * dy + dz * dz;
        }

        if (distSq > 36.0 * 36.0) return;

        World world = entity.getWorld();
        if (distSq > 5.0 * 5.0 && entity.isClientOccluded(world)) return;

        BlockState state = entity.getCachedState();
        Direction facing = state.contains(InserterBlock.FACING) ? state.get(InserterBlock.FACING) : Direction.NORTH;
        float progress = entity.getArmProgress(tickDelta);

        int brightLight = light;
        if (brightLight == 0 && world != null) {
            brightLight = (distSq < 14.0 * 14.0)
                    ? WorldRenderer.getLightmapCoordinates(world, pos.up())
                    : LightmapTextureManager.MAX_LIGHT_COORDINATE;
        }

        if (distSq > 12.0 * 12.0) {
            renderFastLodArm(entity, entity.isBurner(), facing, progress, matrices, vertexConsumers, brightLight, overlay);
            return;
        }

        renderArticulatedArm(
                entity,
                entity.isBurner(),
                facing,
                progress,
                tickDelta,
                entity.getHeldStack(),
                matrices,
                vertexConsumers,
                brightLight,
                overlay
        );
    }

    private static void renderFastLodArm(InserterBlockEntity entity, boolean isBurner, Direction facing, float progress,
                                         MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, int overlay) {
        BlockRenderManager brm = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState mainColor = resolveMainColor(entity, isBurner);
        boolean isLong = entity != null && entity.isLongHanded();
        float yawDegrees = (1.0f - progress) * 180.0f;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));

        drawCenteredBox(brm, mainColor, matrices, vertexConsumers, light, overlay,
                0.0f, 0.25f, isLong ? 0.35f : 0.20f, 0.22f, 0.42f, isLong ? 1.15f : 0.65f);
        matrices.pop();
    }

    public static void renderArticulatedArm(InserterBlockEntity entity, boolean isBurner, Direction facing,
                                            float progress, float tickDelta, ItemStack held, MatrixStack matrices,
                                            VertexConsumerProvider vertexConsumers, int light, int overlay) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockRenderManager brm = client.getBlockRenderManager();

        BlockState mainColor = resolveMainColor(entity, isBurner);
        BlockState jointColor = resolveJointColor(entity, isBurner);
        BlockState darkMetal = Blocks.NETHERITE_BLOCK.getDefaultState();
        BlockState steelClaw = Blocks.IRON_BLOCK.getDefaultState();
        BlockState firebox = Blocks.BLAST_FURNACE.getDefaultState();

        boolean isLong = entity != null && entity.isLongHanded();
        float armScaleZ = isLong ? 1.55f : 1.0f;

        ArmTarget pickupTarget = computePickupTarget(entity, facing, tickDelta);
        ArmTarget dropoffTarget = computeDropoffTarget(entity, facing);

        float midArc = MathHelper.sin(progress * (float) Math.PI);
        float trackingYaw = MathHelper.lerp(progress, pickupTarget.yawOffset, dropoffTarget.yawOffset);
        float yawDegrees = (1.0f - progress) * 180.0f + trackingYaw;

        float reachFactor = MathHelper.lerp(progress, pickupTarget.reachFactor, dropoffTarget.reachFactor);
        float dipFactor = MathHelper.lerp(progress, pickupTarget.dipFactor, 1.0f);

        float endShoulderPitch = (isLong ? 36.0f : 28.0f) + reachFactor * 20.0f + dipFactor * 4.0f;
        float endElbowPitch = (isLong ? 54.0f : 62.0f) - reachFactor * 26.0f + dipFactor * 6.0f;

        float shoulderPitch = endShoulderPitch - midArc * 26.0f;
        float elbowPitch = endElbowPitch - midArc * 24.0f;

        matrices.push();
        matrices.translate(0.5, 0.0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yawDegrees));

        // 1. Поворотная башня
        drawCenteredBox(brm, mainColor, matrices, vertexConsumers, light, overlay,
                0.0f, 0.22f, 0.0f, 0.40f, 0.20f, 0.42f);

        if (isBurner) {
            drawCenteredBox(brm, firebox, matrices, vertexConsumers, light, overlay,
                    0.0f, 0.20f, -0.24f, 0.42f, 0.34f, 0.28f);
            drawCenteredBox(brm, darkMetal, matrices, vertexConsumers, light, overlay,
                    0.12f, 0.54f, -0.26f, 0.11f, 0.22f, 0.11f);
        } else {
            drawCenteredBox(brm, darkMetal, matrices, vertexConsumers, light, overlay,
                    0.0f, 0.24f, -0.22f, 0.32f, 0.20f, 0.24f);
        }

        // 2. Плечевой узел и нижняя штанга (удлинённая для Красного манипулятора)
        float lowerLen = 0.58f * armScaleZ;
        matrices.translate(0.0, 0.40, 0.06);
        drawCenteredBox(brm, jointColor, matrices, vertexConsumers, light, overlay,
                0.0f, -0.06f, 0.0f, 0.44f, 0.14f, 0.14f);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(shoulderPitch));
        drawCenteredBox(brm, mainColor, matrices, vertexConsumers, light, overlay,
                0.0f, 0.0f, -0.04f, 0.16f, lowerLen, 0.16f);
        drawCenteredBox(brm, steelClaw, matrices, vertexConsumers, light, overlay,
                0.0f, 0.05f, 0.06f, 0.07f, lowerLen - 0.12f, 0.07f);

        // 3. Локтевой шарнир и предплечье
        float upperLen = 0.56f * armScaleZ;
        matrices.translate(0.0, lowerLen - 0.03f, 0.0);
        drawCenteredBox(brm, jointColor, matrices, vertexConsumers, light, overlay,
                0.0f, -0.06f, 0.0f, 0.26f, 0.12f, 0.12f);

        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(elbowPitch));
        drawCenteredBox(brm, darkMetal, matrices, vertexConsumers, light, overlay,
                0.0f, 0.0f, -0.02f, 0.12f, upperLen, 0.12f);
        drawCenteredBox(brm, mainColor, matrices, vertexConsumers, light, overlay,
                0.0f, 0.08f, 0.0f, 0.15f, upperLen * 0.55f, 0.15f);

        // 4. Кисть и двухпалая клешня
        matrices.translate(0.0, upperLen - 0.02f, 0.0);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(shoulderPitch + elbowPitch)));

        drawCenteredBox(brm, steelClaw, matrices, vertexConsumers, light, overlay,
                0.0f, -0.04f, 0.02f, 0.36f, 0.09f, 0.09f);

        float fingerSpread = held.isEmpty() ? 0.13f : 0.09f;
        drawCenteredBox(brm, steelClaw, matrices, vertexConsumers, light, overlay,
                -fingerSpread, -0.04f, 0.14f, 0.07f, 0.09f, 0.24f);
        drawCenteredBox(brm, steelClaw, matrices, vertexConsumers, light, overlay,
                 fingerSpread, -0.04f, 0.14f, 0.07f, 0.09f, 0.24f);

        // 5. Предмет в захвате клешни
        if (!held.isEmpty()) {
            matrices.push();
            matrices.translate(0.0, -0.02, 0.20);
            matrices.scale(0.40f, 0.40f, 0.40f);
            client.getItemRenderer().renderItem(
                    held,
                    ModelTransformationMode.FIXED,
                    light,
                    overlay,
                    matrices,
                    vertexConsumers,
                    entity != null ? entity.getWorld() : null,
                    0
            );
            matrices.pop();
        }

        matrices.pop();
    }

    private static ArmTarget computePickupTarget(InserterBlockEntity entity, Direction inserterFacing, float tickDelta) {
        if (entity == null || entity.getWorld() == null) {
            return new ArmTarget(0.0f, 0.45f, 0.5f);
        }
        int reach = entity.getReachDistance();
        BlockPos backPos = entity.getPos().offset(inserterFacing.getOpposite(), reach);
        if (!(entity.getWorld().getBlockState(backPos).getBlock() instanceof BeltBlock)) {
            return new ArmTarget(0.0f, 0.45f, 0.7f);
        }
        if (!(entity.getWorld().getBlockEntity(backPos) instanceof BeltBlockEntity belt)) {
            return new ArmTarget(0.0f, 0.45f, 0.7f);
        }

        Direction beltFacing = belt.getCachedState().get(BeltBlock.FACING);
        float beltProgress = MathHelper.clamp(
                (belt.getRenderTicks() + tickDelta) / (float) belt.getMoveDelay(), 0.0f, 1.0f);

        boolean rightFirst = (beltFacing == inserterFacing.rotateYClockwise());
        BeltLane firstLane = rightFirst ? belt.getRightLane() : belt.getLeftLane();
        BeltLane secondLane = rightFirst ? belt.getLeftLane() : belt.getRightLane();
        double firstLaneLocalX = rightFirst ? 0.28 : 0.72;
        double secondLaneLocalX = rightFirst ? 0.72 : 0.28;

        int[] slotOrder = (beltFacing == inserterFacing)
                ? new int[]{ 3, 2, 1, 0 }
                : new int[]{ 2, 1, 3, 0 };

        ArmTarget tracked = findItemTargetOnLane(firstLane, firstLaneLocalX, beltFacing, inserterFacing, beltProgress, slotOrder);
        if (tracked != null) return tracked;
        tracked = findItemTargetOnLane(secondLane, secondLaneLocalX, beltFacing, inserterFacing, beltProgress, slotOrder);
        if (tracked != null) return tracked;

        return new ArmTarget(0.0f, 0.25f, 0.2f);
    }

    private static ArmTarget findItemTargetOnLane(BeltLane lane, double laneLocalX, Direction beltFacing,
                                                  Direction inserterFacing, float beltProgress, int[] slotOrder) {
        for (int slot : slotOrder) {
            if (lane.hasItemAt(slot)) {
                double localZ = BELT_SLOT_Z[slot];
                if (lane.isMoving(slot)) {
                    localZ -= (1.0f - beltProgress) * 0.25;
                }

                double dx = laneLocalX - 0.5;
                double dz = localZ - 0.5;
                double rotRad = Math.toRadians(beltFacing.asRotation() - inserterFacing.asRotation());
                double relX = dx * Math.cos(rotRad) - dz * Math.sin(rotRad);
                double relZ = dx * Math.sin(rotRad) + dz * Math.cos(rotRad);

                double distFromInserter = 1.0 - relZ;
                float reachFactor = MathHelper.clamp((float) ((distFromInserter - 0.75) / 0.48), 0.0f, 1.0f);
                float yawOffset = MathHelper.clamp((float) Math.toDegrees(Math.atan2(-relX, distFromInserter)) * 0.85f, -24.0f, 24.0f);

                return new ArmTarget(yawOffset, reachFactor, 1.0f);
            }
        }
        return null;
    }

    private static ArmTarget computeDropoffTarget(InserterBlockEntity entity, Direction inserterFacing) {
        if (entity == null || entity.getWorld() == null) {
            return new ArmTarget(0.0f, 0.75f, 1.0f);
        }
        int reach = entity.getReachDistance();
        BlockPos frontPos = entity.getPos().offset(inserterFacing, reach);
        BlockState frontState = entity.getWorld().getBlockState(frontPos);
        if (frontState.getBlock() instanceof BeltBlock) {
            Direction beltFacing = frontState.get(BeltBlock.FACING);
            if (beltFacing == inserterFacing.rotateYClockwise() || beltFacing == inserterFacing.rotateYCounterclockwise()) {
                return new ArmTarget(0.0f, 1.0f, 1.0f);
            } else if (beltFacing == inserterFacing) {
                return new ArmTarget(-11.0f, 0.35f, 1.0f);
            } else {
                return new ArmTarget(11.0f, 0.35f, 1.0f);
            }
        }
        return new ArmTarget(0.0f, 0.55f, 0.8f);
    }

    private record ArmTarget(float yawOffset, float reachFactor, float dipFactor) {}

    private static void drawCenteredBox(BlockRenderManager brm, BlockState state, MatrixStack matrices,
                                        VertexConsumerProvider vertexConsumers, int light, int overlay,
                                        float cx, float minY, float cz, float sx, float sy, float sz) {
        matrices.push();
        matrices.translate(cx - sx * 0.5f, minY, cz - sz * 0.5f);
        matrices.scale(sx, sy, sz);
        brm.renderBlockAsEntity(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
    }
}