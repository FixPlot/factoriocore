package com.fixplot.factoriocore.client.hud;

import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.crafting.FactorioRecipes;
import com.fixplot.factoriocore.content.fluids.BoilerBlock;
import com.fixplot.factoriocore.content.fluids.OffshorePumpBlock;
import com.fixplot.factoriocore.content.fluids.PipeBlock;
import com.fixplot.factoriocore.content.fluids.SteamEngineBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlock;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AltModeRenderer {

    private static boolean altModeActive = false;
    private static KeyBinding altModeKey;

    private record AltBadge(double wx, double wy, double wz, double pullTowardsCam, ItemStack icon, float scale) {}

    public static KeyBinding getAltModeKey() {
        return altModeKey;
    }

    public static boolean isAltModeActive() {
        return altModeActive;
    }

    public static void register() {
        altModeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.factoriocore.alt_mode",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_LEFT_ALT,
                "category.factoriocore"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (altModeKey.wasPressed()) {
                altModeActive = !altModeActive;
                if (client.player != null) {
                    client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, altModeActive ? 1.25f : 0.9f);
                }
            }
        });

        WorldRenderEvents.LAST.register(AltModeRenderer::renderAltModeWorld);
    }

    private static boolean isHoldingFluidBlock(MinecraftClient client) {
        if (client.player == null) return false;
        ItemStack main = client.player.getMainHandStack();
        ItemStack off = client.player.getOffHandStack();
        return isFluidItem(main) || isFluidItem(off);
    }

    private static boolean isFluidItem(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        return PipeBlock.canConnectFluid(bi.getBlock().getDefaultState());
    }

    private static void renderAltModeWorld(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean showFluidPorts = altModeActive || isHoldingFluidBlock(client);
        if (!altModeActive && !showFluidPorts) return;

        World world = client.world;
        if (world == null || client.player == null || context.consumers() == null || context.matrixStack() == null) {
            return;
        }

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        BlockPos playerPos = client.player.getBlockPos();
        int radius = 22;

        List<AltBadge> badgesToRender = new ArrayList<>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();

                    // --- 1. ПОРТЫ ВХОДА/ВЫХОДА ЖИДКОСТЕЙ (голубые треугольные стрелки как в Factorio!) ---
                    if (showFluidPorts) {
                        if (block instanceof OffshorePumpBlock && state.get(OffshorePumpBlock.PART) == 0 && !state.get(OffshorePumpBlock.UPPER)) {
                            Direction facing = state.get(OffshorePumpBlock.FACING);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(facing.getOpposite()), facing.getOpposite(), false);
                        } else if (block instanceof BoilerBlock && state.get(BoilerBlock.PART) == 1 && !state.get(BoilerBlock.UPPER)) {
                            Direction facing = state.get(BoilerBlock.FACING);
                            Direction left = facing.rotateYCounterclockwise();
                            Direction right = facing.rotateYClockwise();
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(left, 2), right, true);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, 2), left, true);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(facing, 2), facing, false);
                        } else if (block instanceof SteamEngineBlock && state.get(SteamEngineBlock.PART) == 1 && state.get(SteamEngineBlock.LAYER) == 0) {
                            Direction facing = state.get(SteamEngineBlock.FACING);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(facing.getOpposite()), facing, true);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(facing, 5), facing, false);
                        } else if (block instanceof OilMachines.PumpjackBlock && state.get(OilMachines.Base3x3MachineBlock.PART) == 4 && !state.get(OilMachines.Base3x3MachineBlock.UPPER)) {
                            Direction facing = state.get(OilMachines.Base3x3MachineBlock.FACING);
                            Direction right = facing.rotateYClockwise();
                            // Выходной порт Нефтяной вышки спереди-справа (part=8)
                            BlockPos outPortTile = pos.offset(right, 1).offset(facing, 1);
                            drawFactorioFluidPortChevron(matrices, lines, cam, outPortTile, facing, false);
                        } else if (block instanceof OilMachines.ChemicalPlantBlock && state.get(OilMachines.Base3x3MachineBlock.PART) == 4 && !state.get(OilMachines.Base3x3MachineBlock.UPPER)) {
                            Direction facing = state.get(OilMachines.Base3x3MachineBlock.FACING);
                            Direction right = facing.rotateYClockwise();
                            // 2 входных порта сзади (стрелки смотрят ВНУТРЬ химзавода по направлению facing)
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, -1).offset(facing, -1), facing, true);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, 1).offset(facing, -1), facing, true);
                            // 2 выходных порта спереди (стрелки смотрят НАРУЖУ из химзавода по направлению facing)
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, -1).offset(facing, 1), facing, false);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, 1).offset(facing, 1), facing, false);

                            if (altModeActive && world.getBlockEntity(pos) instanceof OilMachines.ChemicalPlantBlockEntity chem) {
                                badgesToRender.add(new AltBadge(
                                        pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5,
                                        1.75, chem.getRecipeIcon(), 0.65f
                                ));
                            }
                        } else if (block instanceof OilMachines.OilRefineryBlock && state.get(OilMachines.OilRefineryBlock.PART) == 12 && !state.get(OilMachines.OilRefineryBlock.UPPER)) {
                            Direction facing = state.get(OilMachines.OilRefineryBlock.FACING);
                            Direction right = facing.rotateYClockwise();
                            // 2 входных порта сзади 5x5 (wOff = -1, +1; dOff = -2)
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, -1).offset(facing, -2), facing, true);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, 1).offset(facing, -2), facing, true);
                            // 3 выходных порта спереди 5x5 (wOff = -2, 0, +2; dOff = +2)
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, -2).offset(facing, 2), facing, false);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(facing, 2), facing, false);
                            drawFactorioFluidPortChevron(matrices, lines, cam, pos.offset(right, 2).offset(facing, 2), facing, false);
                        }
                    }

                    if (!altModeActive) continue;

                    // --- 2. ОБЫЧНЫЕ ALT-MODE СТРЕЛКИ И БЕЙДЖИ ---
                    if (block instanceof InserterBlock) {
                        Direction facing = state.get(InserterBlock.FACING);
                        int reach = (block == ModBlocks.LONG_HANDED_INSERTER) ? 2 : 1;
                        drawInserterOverlay(matrices, lines, cam, pos, facing, reach);
                    } else if (block instanceof AssemblerBlock && state.get(AssemblerBlock.PART) == 4 && !state.get(AssemblerBlock.UPPER)) {
                        if (world.getBlockEntity(pos) instanceof AssemblerBlockEntity asm) {
                            String rid = asm.getRecipeId();
                            if (!rid.isEmpty()) {
                                FactorioRecipes.Recipe rec = FactorioRecipes.getById(rid);
                                if (rec != null) {
                                    badgesToRender.add(new AltBadge(
                                            pos.getX() + 0.5, pos.getY() + 1.25, pos.getZ() + 0.5,
                                            1.75, rec.output(), 0.65f
                                    ));
                                }
                            }
                        }
                    } else if (block instanceof FactorioChestBlock && world.getBlockEntity(pos) instanceof FactorioChestBlockEntity chest) {
                        List<ItemStack> top = chest.getSortedContentSummary(1);
                        if (!top.isEmpty()) {
                            badgesToRender.add(new AltBadge(
                                    pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5,
                                    0.62, top.get(0), 0.48f
                            ));
                        }
                    } else if (block instanceof FactorioFurnaceBlock && state.get(FactorioFurnaceBlock.PART) == 0 && !state.get(FactorioFurnaceBlock.UPPER)) {
                        if (world.getBlockEntity(pos) instanceof FactorioFurnaceBlockEntity furnace) {
                            ItemStack icon = furnace.getStack(2);
                            if (icon.isEmpty() && !furnace.getStack(0).isEmpty()) {
                                icon = FactorioFurnaceBlockEntity.getSmeltingResult(world, furnace.getStack(0));
                            }
                            if (!icon.isEmpty()) {
                                Direction f = state.get(FactorioFurnaceBlock.FACING);
                                Direction r = f.rotateYClockwise();
                                double cx = pos.getX() + 0.5 + (f.getOffsetX() + r.getOffsetX()) * 0.5;
                                double cz = pos.getZ() + 0.5 + (f.getOffsetZ() + r.getOffsetZ()) * 0.5;
                                badgesToRender.add(new AltBadge(cx, pos.getY() + 1.15, cz, 1.25, icon, 0.62f));
                            }
                        }
                    } else if (block instanceof BurnerDrillBlock && state.get(BurnerDrillBlock.PART) == 0 && !state.get(BurnerDrillBlock.UPPER)) {
                        Direction f = state.get(BurnerDrillBlock.FACING);
                        BlockPos outPos = BurnerDrillBlock.getPartPos(pos, f, 3, false).offset(f);
                        drawYellowOutputArrow(matrices, lines, cam, outPos, f);

                        if (world.getBlockEntity(pos) instanceof BurnerDrillBlockEntity drill) {
                            ItemStack ore = drill.findResourceUnderneath(world, pos, f);
                            if (!ore.isEmpty()) {
                                Direction r = f.rotateYClockwise();
                                double cx = pos.getX() + 0.5 + (f.getOffsetX() + r.getOffsetX()) * 0.5;
                                double cz = pos.getZ() + 0.5 + (f.getOffsetZ() + r.getOffsetZ()) * 0.5;
                                badgesToRender.add(new AltBadge(cx, pos.getY() + 1.20, cz, 1.35, ore, 0.60f));
                            }
                        }
                    } else if (block instanceof ElectricDrillBlock && state.get(ElectricDrillBlock.PART) == 4 && !state.get(ElectricDrillBlock.UPPER)) {
                        Direction f = state.get(ElectricDrillBlock.FACING);
                        BlockPos outPos = pos.offset(f, 2);
                        drawYellowOutputArrow(matrices, lines, cam, outPos, f);

                        ItemStack ore = findOreUnderElectricDrill(world, pos);
                        if (!ore.isEmpty()) {
                            badgesToRender.add(new AltBadge(
                                    pos.getX() + 0.5, pos.getY() + 1.35, pos.getZ() + 0.5,
                                    1.70, ore, 0.65f
                            ));
                        }
                    }
                }
            }
        }

        for (AltBadge badge : badgesToRender) {
            renderFactorioAltBadge(
                    client, context, matrices, consumers, cam,
                    badge.wx(), badge.wy(), badge.wz(),
                    badge.pullTowardsCam(), badge.icon(), badge.scale()
            );
        }
    }

    /**
     * Рисует фирменный голубой треугольник порта жидкости (как на скриншоте 205524.jpg) на кромке тайла порта!
     */
    private static void drawFactorioFluidPortChevron(MatrixStack matrices, VertexConsumer lines, Vec3d cam,
                                                     BlockPos portTilePos, Direction flowDir, boolean isInput) {
        // Голубой цвет жидкостных стрелок Factorio (#29B6F6)
        float r = 0.16f;
        float g = 0.72f;
        float b = 0.98f;

        Direction edgeSide = isInput ? flowDir.getOpposite() : flowDir;
        double cx = portTilePos.getX() + 0.5 + edgeSide.getOffsetX() * 0.42 - cam.x;
        double y = portTilePos.getY() + 0.48 - cam.y;
        double cz = portTilePos.getZ() + 0.5 + edgeSide.getOffsetZ() * 0.42 - cam.z;

        double fx = flowDir.getOffsetX();
        double fz = flowDir.getOffsetZ();
        double px = -fz;
        double pz = fx;

        // Заполненный концентрическими рёбрами широкий голубой треугольник
        for (int layer = 0; layer < 3; layer++) {
            double scale = 1.0 - layer * 0.25;
            double halfW = 0.28 * scale;
            double len = 0.26 * scale;

            double baseCenterX = cx - fx * (len * 0.45);
            double baseCenterZ = cz - fz * (len * 0.45);
            double tipX = cx + fx * (len * 0.55);
            double tipZ = cz + fz * (len * 0.55);

            double leftX = baseCenterX - px * halfW;
            double leftZ = baseCenterZ - pz * halfW;
            double rightX = baseCenterX + px * halfW;
            double rightZ = baseCenterZ + pz * halfW;

            drawLine(matrices, lines, leftX, y, leftZ, rightX, y, rightZ, r, g, b, 0.98f);
            drawLine(matrices, lines, leftX, y, leftZ, tipX, y, tipZ, r, g, b, 0.98f);
            drawLine(matrices, lines, rightX, y, rightZ, tipX, y, tipZ, r, g, b, 0.98f);
        }
    }

    private static ItemStack findOreUnderElectricDrill(World world, BlockPos centerPos) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Block b = world.getBlockState(centerPos.add(dx, -1, dz)).getBlock();
                if (b instanceof ResourceOreBlock) {
                    return new ItemStack(b.asItem());
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private static void drawInserterOverlay(MatrixStack matrices, VertexConsumer lines, Vec3d cam,
                                            BlockPos pos, Direction facing, int reach) {
        int col = FactorioConfig.INSTANCE.altArrowColor;
        float r = FactorioConfig.getRed(col);
        float g = FactorioConfig.getGreen(col);
        float b = FactorioConfig.getBlue(col);

        double y = pos.getY() + 0.22 - cam.y;
        double cx = pos.getX() + 0.5 - cam.x;
        double cz = pos.getZ() + 0.5 - cam.z;

        double fx = facing.getOffsetX();
        double fz = facing.getOffsetZ();
        double px = -fz;
        double pz = fx;

        double backX = cx - fx * (reach - 0.15);
        double backZ = cz - fz * (reach - 0.15);
        drawLine(matrices, lines, backX - px * 0.25, y, backZ - pz * 0.25, backX + px * 0.25, y, backZ + pz * 0.25, r, g, b, 0.95f);

        double frontX = cx + fx * (reach - 0.10);
        double frontZ = cz + fz * (reach - 0.10);
        drawLine(matrices, lines, backX, y, backZ, frontX, y, frontZ, r, g, b, 0.90f);
        drawLine(matrices, lines, frontX, y, frontZ, frontX - fx * 0.22 + px * 0.18, y, frontZ - fz * 0.22 + pz * 0.18, r, g, b, 0.95f);
        drawLine(matrices, lines, frontX, y, frontZ, frontX - fx * 0.22 - px * 0.18, y, frontZ - fz * 0.22 - pz * 0.18, r, g, b, 0.95f);
    }

    private static void drawYellowOutputArrow(MatrixStack matrices, VertexConsumer lines, Vec3d cam,
                                              BlockPos outPos, Direction facing) {
        int col = FactorioConfig.INSTANCE.altArrowColor;
        float r = FactorioConfig.getRed(col);
        float g = FactorioConfig.getGreen(col);
        float b = FactorioConfig.getBlue(col);

        double cx = outPos.getX() + 0.5 - cam.x;
        double y = outPos.getY() + 0.25 - cam.y;
        double cz = outPos.getZ() + 0.5 - cam.z;

        double fx = facing.getOffsetX();
        double fz = facing.getOffsetZ();
        double px = -fz;
        double pz = fx;

        double tailX = cx - fx * 0.30;
        double tailZ = cz - fz * 0.30;
        double tipX = cx + fx * 0.25;
        double tipZ = cz + fz * 0.25;

        drawLine(matrices, lines, tailX, y, tailZ, tipX, y, tipZ, r, g, b, 0.95f);
        drawLine(matrices, lines, tipX, y, tipZ, tipX - fx * 0.24 + px * 0.20, y, tipZ - fz * 0.24 + pz * 0.20, r, g, b, 0.95f);
        drawLine(matrices, lines, tipX, y, tipZ, tipX - fx * 0.24 - px * 0.20, y, tipZ - fz * 0.24 - pz * 0.20, r, g, b, 0.95f);
    }

    private static void drawLine(MatrixStack matrices, VertexConsumer lines,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        MatrixStack.Entry entry = matrices.peek();
        float dx = (float) (x2 - x1);
        float dy = (float) (y2 - y1);
        float dz = (float) (z2 - z1);
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len > 1.0e-4f) {
            dx /= len;
            dy /= len;
            dz /= len;
        } else {
            dx = 0.0f;
            dy = 1.0f;
            dz = 0.0f;
        }
        lines.vertex(entry, (float) x1, (float) y1, (float) z1).color(r, g, b, a).normal(entry, dx, dy, dz);
        lines.vertex(entry, (float) x2, (float) y2, (float) z2).color(r, g, b, a).normal(entry, dx, dy, dz);
    }

    private static void renderFactorioAltBadge(MinecraftClient client, WorldRenderContext context,
                                               MatrixStack matrices, VertexConsumerProvider consumers,
                                               Vec3d cam, double wx, double wy, double wz,
                                               double pullTowardsCam, ItemStack icon, float scale) {
        if (icon.isEmpty()) return;

        Vec3d toCam = cam.subtract(wx, wy, wz);
        double dist = toCam.length();
        if (dist > 1.0e-3) {
            double shift = Math.min(pullTowardsCam, dist * 0.65);
            Vec3d dir = toCam.normalize().multiply(shift);
            wx += dir.x;
            wy += dir.y;
            wz += dir.z;
        }

        matrices.push();
        matrices.translate(wx - cam.x, wy - cam.y, wz - cam.z);
        matrices.multiply(context.camera().getRotation());
        matrices.scale(scale, scale, 0.02f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f));

        client.getItemRenderer().renderItem(
                icon,
                ModelTransformationMode.GUI,
                LightmapTextureManager.MAX_LIGHT_COORDINATE,
                OverlayTexture.DEFAULT_UV,
                matrices,
                consumers,
                client.world,
                0
        );

        matrices.pop();
    }
}