package com.fixplot.factoriocore.content.electricity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.List;

public class CreativeEnergySourceBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);
    public static final long OUTPUT_WATTS = 500_000_000L; // 500 MW бесконечной энергии

    public CreativeEnergySourceBlock(Settings settings) {
        super(settings);
    }

    public int getSupplyRadius() {
        return 4; // Питает зону 9x9 вокруг себя + все подключённые опоры ЛЭП!
    }

    public int getSupplyAreaSize() {
        return 9;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (player.isSneaking()) {
            return ActionResult.PASS;
        }
        if (world.isClient && PowerNetworkManager.clientGuiOpener != null) {
            PowerNetworkManager.clientGuiOpener.accept(pos.toImmutable());
        }
        return ActionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return 0.125f;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        tooltip.add(Text.translatable("hud.factoriocore.power_output", "500 MW").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("hud.factoriocore.supply_area", "9x9").formatted(Formatting.GRAY));
    }
}