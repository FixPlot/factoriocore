package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class StorageTankBlock extends BlockWithEntity {
    public static final MapCodec<StorageTankBlock> CODEC = createCodec(StorageTankBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty PART = IntProperty.of("part", 0, 8);
    public static final BooleanProperty UPPER = BooleanProperty.of("upper");

    private static final VoxelShape BOTTOM_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape TOP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 18.0, 16.0);

    public StorageTankBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(PART, 4)
                .with(UPPER, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, UPPER);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(UPPER) ? TOP_SHAPE : BOTTOM_SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
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
        return 0.12f;
    }

    public static BlockPos getPartPos(BlockPos masterPos, Direction facing, int part, boolean upper) {
        int wOff = (part % 3) - 1;
        int dOff = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = masterPos.offset(right, wOff).offset(facing, dOff);
        return upper ? base.up() : base;
    }

    public static BlockPos getMasterPos(BlockPos currentPos, BlockState state) {
        if (!(state.getBlock() instanceof StorageTankBlock)) return currentPos;
        Direction facing = state.get(FACING);
        int part = state.get(PART);
        boolean upper = state.get(UPPER);
        int wOff = (part % 3) - 1;
        int dOff = (part / 3) - 1;
        Direction right = facing.rotateYClockwise();
        BlockPos base = upper ? currentPos.down() : currentPos;
        return base.offset(right, -wOff).offset(facing, -dOff);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (ctx.getPlayer() != null && ctx.getPlayer().isSneaking()) {
            facing = facing.getOpposite();
        }
        BlockPos masterPos = ctx.getBlockPos();
        World world = ctx.getWorld();

        for (int part = 0; part < 9; part++) {
            for (int u = 0; u <= 1; u++) {
                BlockPos checkPos = getPartPos(masterPos, facing, part, u == 1);
                if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                    return null;
                }
            }
        }
        return this.getDefaultState().with(FACING, facing).with(PART, 4).with(UPPER, false);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);
        if (!world.isClient && state.get(PART) == 4 && !state.get(UPPER)) {
            Direction facing = state.get(FACING);
            for (int part = 0; part < 9; part++) {
                for (int u = 0; u <= 1; u++) {
                    if (part == 4 && u == 0) continue;
                    BlockPos p = getPartPos(pos, facing, part, u == 1);
                    world.setBlockState(p, state.with(PART, part).with(UPPER, u == 1), Block.NOTIFY_ALL);
                }
            }
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            Direction facing = state.get(FACING);
            for (int part = 0; part < 9; part++) {
                for (int u = 0; u <= 1; u++) {
                    BlockPos p = getPartPos(masterPos, facing, part, u == 1);
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
        if (!world.isClient) {
            BlockPos masterPos = getMasterPos(pos, state);
            if (world.getBlockEntity(masterPos) instanceof StorageTankBlockEntity tank) {
                if (player.isSneaking() && player.getMainHandStack().isEmpty() && tank.getFluidAmount() > 0) {
                    tank.flushFluid();
                    world.playSound(null, masterPos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.7f, 1.1f);
                    player.sendMessage(Text.literal("§e[Резервуар] Жидкость полностью слита."), true);
                } else {
                    float pct = (tank.getFluidAmount() * 100.0f) / StorageTankBlockEntity.CAPACITY_MB;
                    player.sendMessage(Text.literal(String.format(
                            "§6[Резервуар] §f%s: §b%d / %d мБ §7(%.1f%%) §8[Shift+ПКМ пустой рукой — слить]",
                            tank.getFluidKind().displayName, tank.getFluidAmount(), StorageTankBlockEntity.CAPACITY_MB, pct)), true);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return (state.get(PART) == 4 && !state.get(UPPER)) ? new StorageTankBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (state.get(PART) != 4 || state.get(UPPER)) return null;
        return world.isClient ? null : validateTicker(type, ModBlocks.STORAGE_TANK_BLOCK_ENTITY, StorageTankBlockEntity::tick);
    }

    public static class StorageTankBlockEntity extends BlockEntity implements FactorioFluidContainer {
        public static final int CAPACITY_MB = 25000;

        private FluidKind fluidKind = FluidKind.NONE;
        private int amountMb = 0;
        private int temperatureC = 25;

        public StorageTankBlockEntity(BlockPos pos, BlockState state) {
            super(ModBlocks.STORAGE_TANK_BLOCK_ENTITY, pos, state);
        }

        public void flushFluid() {
            this.amountMb = 0;
            this.fluidKind = FluidKind.NONE;
            sync();
        }

        public static void tick(World world, BlockPos pos, BlockState state, StorageTankBlockEntity tank) {
            if (world == null || world.isClient || tank.amountMb <= 0 || tank.fluidKind == FluidKind.NONE) {
                return;
            }

            boolean changed = false;

            // Обмениваемся жидкостью через угловые и боковые порты 3x3
            for (Direction dir : Direction.Type.HORIZONTAL) {
                if (tank.amountMb <= 0) break;
                Direction right = dir.rotateYClockwise();
                int[] offsets = { -1, 1 };
                for (int offset : offsets) {
                    if (tank.amountMb <= 0) break;
                    BlockPos neighborPos = pos.offset(dir, 2).offset(right, offset);
                    BlockState neighborState = world.getBlockState(neighborPos);
                    BlockPos canonical = BlockHealthManager.getCanonicalPos(neighborPos, neighborState);
                    BlockEntity be = world.getBlockEntity(canonical);

                    if (be instanceof StorageTankBlockEntity otherTank && otherTank != tank) {
                        if (otherTank.fluidKind == FluidKind.NONE || otherTank.fluidKind == tank.fluidKind) {
                            int diff = tank.amountMb - otherTank.amountMb;
                            if (diff > 20) {
                                int move = Math.min(diff / 2, 250);
                                otherTank.fluidKind = tank.fluidKind;
                                otherTank.temperatureC = tank.temperatureC;
                                otherTank.amountMb += move;
                                tank.amountMb -= move;
                                otherTank.markDirty();
                                if ((world.getTime() & 3L) == 0L) otherTank.sync();
                                changed = true;
                            }
                        }
                    } else if (be instanceof PipeBlockEntity pipe) {
                        // Поддерживаем в выходных трубах рабочее давление (до 300 мБ), не устраивая пинг-понг
                        if (pipe.canAcceptFluid(tank.fluidKind, dir.getOpposite()) && pipe.getFluidAmount() < 300) {
                            int needed = Math.min(300 - pipe.getFluidAmount(), Math.min(tank.amountMb, 150));
                            int accepted = pipe.insertFluid(tank.fluidKind, needed, tank.temperatureC, dir.getOpposite());
                            if (accepted > 0) {
                                tank.amountMb -= accepted;
                                changed = true;
                            }
                        }
                    } else if (be instanceof FactorioFluidContainer machine && !(be instanceof OffshorePumpBlockEntity)) {
                        if (machine.canAcceptFluid(tank.fluidKind, dir.getOpposite())) {
                            int pushed = machine.insertFluid(tank.fluidKind, Math.min(tank.amountMb, 150), tank.temperatureC, dir.getOpposite());
                            if (pushed > 0) {
                                tank.amountMb -= pushed;
                                changed = true;
                            }
                        }
                    }
                }
            }

            if (tank.amountMb <= 0) {
                tank.amountMb = 0;
                tank.fluidKind = FluidKind.NONE;
                changed = true;
            }

            if (changed) {
                tank.markDirty();
                if ((world.getTime() & 3L) == 0L || tank.amountMb == 0) {
                    tank.sync();
                }
            }
        }

        public void sync() {
            markDirty();
            if (this.world != null && !this.world.isClient) {
                this.world.updateListeners(this.pos, getCachedState(), getCachedState(), Block.NOTIFY_LISTENERS);
            }
        }

        @Override public FluidKind getFluidKind() { return this.amountMb > 0 ? this.fluidKind : FluidKind.NONE; }
        @Override public int getFluidAmount() { return this.amountMb; }
        @Override public int getFluidCapacity() { return CAPACITY_MB; }
        @Override public int getTemperatureC() { return this.temperatureC; }

        @Override
        public boolean canAcceptFluid(FluidKind kind, Direction fromSide) {
            return this.amountMb == 0 || (this.fluidKind == kind && this.amountMb < CAPACITY_MB);
        }

        @Override
        public int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide) {
            if (!canAcceptFluid(kind, fromSide) || maxAmount <= 0) return 0;
            int space = CAPACITY_MB - this.amountMb;
            int add = Math.min(space, maxAmount);
            if (add > 0) {
                boolean wasEmpty = (this.amountMb == 0);
                this.fluidKind = kind;
                this.temperatureC = tempC;
                this.amountMb += add;
                markDirty();
                if (wasEmpty || (this.world != null && (this.world.getTime() & 3L) == 0L)) {
                    sync();
                }
            }
            return add;
        }

        @Override
        protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.writeNbt(nbt, registries);
            nbt.putString("FluidKind", this.fluidKind.name());
            nbt.putInt("AmountMb", this.amountMb);
            nbt.putInt("TempC", this.temperatureC);
        }

        @Override
        protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            super.readNbt(nbt, registries);
            try {
                this.fluidKind = FluidKind.valueOf(nbt.getString("FluidKind"));
            } catch (Exception e) {
                this.fluidKind = FluidKind.NONE;
            }
            this.amountMb = nbt.getInt("AmountMb");
            this.temperatureC = nbt.getInt("TempC");
        }

        @Nullable @Override public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }
        @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) { return createNbt(registries); }
    }
}