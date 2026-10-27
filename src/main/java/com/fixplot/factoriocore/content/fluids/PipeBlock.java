package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class PipeBlock extends BlockWithEntity {
    public static final MapCodec<PipeBlock> CODEC = createCodec(s -> new PipeBlock(s, false));
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty NORTH = Properties.NORTH;
    public static final BooleanProperty EAST = Properties.EAST;
    public static final BooleanProperty SOUTH = Properties.SOUTH;
    public static final BooleanProperty WEST = Properties.WEST;

    private static final VoxelShape SHAPE = Block.createCuboidShape(3.0, 0.0, 3.0, 13.0, 13.0, 13.0);
    private final boolean underground;

    public PipeBlock(Settings settings, boolean underground) {
        super(settings);
        this.underground = underground;
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(NORTH, false)
                .with(EAST, false)
                .with(SOUTH, false)
                .with(WEST, false));
    }

    public boolean isUndergroundPipe() {
        return this.underground;
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, NORTH, EAST, SOUTH, WEST);
    }

    /**
     * Проверяет, есть ли у соседнего механизма жидкостный порт, смотрящий в сторону трубы.
     * dirFromPipeToMachine — направление от трубы к блоку машины.
     * Значит сам порт на машине должен смотреть в сторону portOutDir = dirFromPipeToMachine.getOpposite().
     */
    public static boolean canConnectFluidTo(BlockState neighbor, Direction dirFromPipeToMachine) {
        Block b = neighbor.getBlock();
        if (b instanceof PipeBlock) {
            return true;
        }

        Direction portOutDir = dirFromPipeToMachine.getOpposite();

        // 1. Прибрежная помпа: выход позади береговой стойки (part=0)
        if (b instanceof OffshorePumpBlock) {
            if (neighbor.get(OffshorePumpBlock.UPPER) || neighbor.get(OffshorePumpBlock.PART) != 0) return false;
            Direction f = neighbor.get(OffshorePumpBlock.FACING);
            return portOutDir == f.getOpposite();
        }

        // 2. Бойлер (3x2): вода по бокам (part 0 и 2, или 3 и 5), пар спереди по центру (part 4) и сзади (part 1)
        if (b instanceof BoilerBlock) {
            if (neighbor.get(BoilerBlock.UPPER)) return false;
            Direction f = neighbor.get(BoilerBlock.FACING);
            int part = neighbor.get(BoilerBlock.PART);
            Direction left = f.rotateYCounterclockwise();
            Direction right = f.rotateYClockwise();
            if ((part == 0 || part == 3) && portOutDir == left) return true;
            if ((part == 2 || part == 5) && portOutDir == right) return true;
            if (part == 4 && portOutDir == f) return true;
            if (part == 1 && portOutDir == f.getOpposite()) return true;
            return false;
        }

        // 3. Паровой двигатель (3x5): вход пара спереди по центру (part 1) и сквозной выход сзади по центру (part 13)
        if (b instanceof SteamEngineBlock) {
            if (neighbor.get(SteamEngineBlock.LAYER) != 0) return false;
            Direction f = neighbor.get(SteamEngineBlock.FACING);
            int part = neighbor.get(SteamEngineBlock.PART);
            if (part == 1 && portOutDir == f.getOpposite()) return true;
            if (part == 13 && portOutDir == f) return true;
            return false;
        }

        // 4. Нефтяная вышка (3x3): 1 выходной порт на переднем правом углу (part = 8, смотрит вперёд по facing)
        if (b instanceof OilMachines.PumpjackBlock) {
            if (neighbor.get(OilMachines.Base3x3MachineBlock.UPPER)) return false;
            Direction f = neighbor.get(OilMachines.Base3x3MachineBlock.FACING);
            int part = neighbor.get(OilMachines.Base3x3MachineBlock.PART);
            return part == 8 && portOutDir == f;
        }

        // 5. Химический завод (3x3): 2 входа сзади (part 0 и 2) и 2 выхода спереди (part 6 и 8)
        if (b instanceof OilMachines.ChemicalPlantBlock) {
            if (neighbor.get(OilMachines.Base3x3MachineBlock.UPPER)) return false;
            Direction f = neighbor.get(OilMachines.Base3x3MachineBlock.FACING);
            int part = neighbor.get(OilMachines.Base3x3MachineBlock.PART);
            if ((part == 0 || part == 2) && portOutDir == f.getOpposite()) return true;
            if ((part == 6 || part == 8) && portOutDir == f) return true;
            return false;
        }

        // 6. Нефтеперерабатывающий завод (5x5): 2 входа сзади (part 1 и 3) и 3 выхода спереди (part 20, 22, 24)
        if (b instanceof OilMachines.OilRefineryBlock) {
            if (neighbor.get(OilMachines.OilRefineryBlock.UPPER)) return false;
            Direction f = neighbor.get(OilMachines.OilRefineryBlock.FACING);
            int part = neighbor.get(OilMachines.OilRefineryBlock.PART);
            if ((part == 1 || part == 3) && portOutDir == f.getOpposite()) return true;
            if ((part == 20 || part == 22 || part == 24) && portOutDir == f) return true;
            return false;
        }

        // 7. Резервуар для хранения жидкостей (3x3): подключается по углам (part 0, 2, 6, 8)
        if (b instanceof StorageTankBlock) {
            if (neighbor.get(StorageTankBlock.UPPER)) return false;
            int part = neighbor.get(StorageTankBlock.PART);
            return part == 0 || part == 2 || part == 6 || part == 8;
        }

        // 8. Огнемётная турель (2x2): принимает нефть/мазут/дизель с любой нижней грани
        if (b instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlock tb && tb.getTurretKind() == 2) {
            return !neighbor.get(com.fixplot.factoriocore.content.combat.turret.TurretBlock.UPPER);
        }

        return false;
    }

    public static boolean canConnectFluid(BlockState neighbor) {
        Block b = neighbor.getBlock();
        return b instanceof PipeBlock
                || b instanceof StorageTankBlock
                || b instanceof OffshorePumpBlock
                || b instanceof BoilerBlock
                || b instanceof SteamEngineBlock
                || b instanceof OilMachines.Base3x3MachineBlock
                || b instanceof OilMachines.OilRefineryBlock
                || (b instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlock tb && tb.getTurretKind() == 2);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockView w = ctx.getWorld();
        BlockPos p = ctx.getBlockPos();
        Direction dir = ctx.getHorizontalPlayerFacing();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            dir = dir.getOpposite();
        }
        return this.getDefaultState()
                .with(FACING, dir)
                .with(NORTH, canConnectFluidTo(w.getBlockState(p.north()), Direction.NORTH))
                .with(EAST, canConnectFluidTo(w.getBlockState(p.east()), Direction.EAST))
                .with(SOUTH, canConnectFluidTo(w.getBlockState(p.south()), Direction.SOUTH))
                .with(WEST, canConnectFluidTo(w.getBlockState(p.west()), Direction.WEST));
    }

    @Override
    protected BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                   WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        return switch (direction) {
            case NORTH -> state.with(NORTH, canConnectFluidTo(neighborState, Direction.NORTH));
            case EAST -> state.with(EAST, canConnectFluidTo(neighborState, Direction.EAST));
            case SOUTH -> state.with(SOUTH, canConnectFluidTo(neighborState, Direction.SOUTH));
            case WEST -> state.with(WEST, canConnectFluidTo(neighborState, Direction.WEST));
            default -> state;
        };
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 0.125f;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, ModBlocks.PIPE_BLOCK_ENTITY, PipeBlockEntity::tick);
    }
}