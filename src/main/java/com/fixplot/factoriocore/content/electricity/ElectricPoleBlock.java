package com.fixplot.factoriocore.content.electricity;

import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ElectricPoleBlock extends Block implements BlockEntityProvider {

    public static final IntProperty PART = IntProperty.of("part", 0, 3);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape POLE_1X1_SHAPE = Block.createCuboidShape(5.0, 0.0, 5.0, 11.0, 16.0, 11.0);
    private static final VoxelShape POLE_2X2_BOTTOM = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape POLE_2X2_TOP = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 24.0, 16.0);

    private final int supplyRadius;
    private final double wireReach;
    private final boolean footprint2x2;
    private final int poleTier; // 1 = Small, 2 = Medium, 3 = Big (2x2), 4 = Substation (2x2)

    public ElectricPoleBlock(Settings settings, int supplyRadius, double wireReach) {
        this(settings, supplyRadius, wireReach, false, 1);
    }

    public ElectricPoleBlock(Settings settings, int supplyRadius, double wireReach, boolean footprint2x2, int poleTier) {
        super(settings);
        this.supplyRadius = supplyRadius;
        this.wireReach = wireReach;
        this.footprint2x2 = footprint2x2;
        this.poleTier = poleTier;
        this.setDefaultState(this.stateManager.getDefaultState().with(PART, 0).with(UPPER, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(PART, UPPER);
    }

    public boolean isFootprint2x2() {
        return this.footprint2x2;
    }

    public int getPoleTier() {
        return this.poleTier;
    }

    public int getSupplyRadius() {
        return this.supplyRadius;
    }

    public int getSupplyAreaSize() {
        return this.footprint2x2 ? (this.supplyRadius * 2) : (this.supplyRadius * 2 + 1);
    }

    public double getWireReach() {
        return this.wireReach;
    }

    public static boolean isMasterPoleNode(BlockState state) {
        if (!(state.getBlock() instanceof ElectricPoleBlock pole)) return false;
        if (!pole.footprint2x2) return true;
        return state.get(PART) == 0 && !state.get(UPPER);
    }

    public static BlockPos getPartPos(BlockPos masterPos, int part, boolean upper) {
        int dx = part % 2;
        int dz = part / 2;
        return masterPos.add(dx, upper ? 1 : 0, dz);
    }

    public static BlockPos getMasterPos(BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof ElectricPoleBlock pole) || !pole.footprint2x2) {
            return pos;
        }
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        return pos.add(-(part % 2), upper ? -1 : 0, -(part / 2));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        if (!this.footprint2x2) {
            return this.getDefaultState();
        }
        World world = ctx.getWorld();
        BlockPos masterPos = ctx.getBlockPos();
        for (int part = 0; part < 4; part++) {
            for (int u = 0; u <= 1; u++) {
                BlockPos check = getPartPos(masterPos, part, u == 1);
                if (!world.getWorldBorder().contains(check) || !world.getBlockState(check).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return this.getDefaultState().with(PART, 0).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && this.footprint2x2 && state.get(PART) == 0 && !state.get(UPPER)) {
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    if (part == 0 && u == 0) continue;
                    BlockPos p = getPartPos(pos, part, u == 1);
                    world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && this.footprint2x2) {
            BlockPos masterPos = getMasterPos(pos, state);
            for (int part = 0; part < 4; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos p = getPartPos(masterPos, part, u == 1);
                    if (!p.equals(pos) && world.getBlockState(p).isOf(this)) {
                        world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
                    }
                }
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) {
            return ActionResult.PASS;
        }
        BlockPos masterPos = getMasterPos(pos, state);
        if (world.isClient && PowerNetworkManager.clientGuiOpener != null) {
            PowerNetworkManager.clientGuiOpener.accept(masterPos.toImmutable());
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return this.footprint2x2 ? BlockRenderType.INVISIBLE : BlockRenderType.MODEL;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!this.footprint2x2) return POLE_1X1_SHAPE;
        return state.get(UPPER) ? POLE_2X2_TOP : POLE_2X2_BOTTOM;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        if (!this.footprint2x2) return POLE_1X1_SHAPE;
        return state.get(UPPER) ? POLE_2X2_TOP : POLE_2X2_BOTTOM;
    }

    @Override
    protected float getAmbientOcclusionLightLevel(BlockState state, BlockView world, BlockPos pos) {
        return 1.0f;
    }

    @Override
    protected boolean isTransparent(BlockState state, BlockView world, BlockPos pos) {
        return true;
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 0.125f;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        int area = getSupplyAreaSize();
        tooltip.add(Text.translatable("hud.factoriocore.supply_area", area + "×" + area).formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Дальность провода: " + this.wireReach + " бл.").formatted(Formatting.AQUA));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        if (!this.footprint2x2 || state.get(PART) != 0 || state.get(UPPER)) return null;
        return new LargePoleBlockEntity(pos, state);
    }

    public static class LargePoleBlockEntity extends BlockEntity {
        public LargePoleBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.LARGE_POLE_BLOCK_ENTITY, pos, state);
        }
    }
}