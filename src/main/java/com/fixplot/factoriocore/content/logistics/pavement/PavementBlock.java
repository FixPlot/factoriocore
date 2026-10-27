package com.fixplot.factoriocore.content.logistics.pavement;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

public class PavementBlock extends Block {

    public static final Identifier PAVEMENT_SPEED_ID = Identifier.of(FactorioCore.MOD_ID, "pavement_speed_bonus");

    private final int speedBonusPercent;

    public PavementBlock(Settings settings, int speedBonusPercent) {
        super(settings);
        this.speedBonusPercent = speedBonusPercent;
    }

    public int getSpeedBonusPercent() {
        return this.speedBonusPercent;
    }

    public double getAttributeMultiplier() {
        return (this.speedBonusPercent - 100) / 100.0; // +0.30, +0.40 или +0.50
    }

    /**
     * Обновляет модификатор скорости игрока каждый тик:
     * Даёт +30% / +40% / +50% к скорости бега при 100% стандартном сцеплении с землёй (никакого скольжения!).
     */
    public static void updatePlayerPavementSpeed(PlayerEntity player) {
        EntityAttributeInstance attr = player.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (attr == null) return;

        BlockPos underPos = player.getSteppingPos();
        BlockState underState = player.getWorld().getBlockState(underPos);
        if (!(underState.getBlock() instanceof PavementBlock)) {
            underState = player.getWorld().getBlockState(player.getBlockPos().down());
        }

        if (underState.getBlock() instanceof PavementBlock pavement) {
            double targetBonus = pavement.getAttributeMultiplier();
            EntityAttributeModifier existing = attr.getModifier(PAVEMENT_SPEED_ID);
            if (existing == null || Math.abs(existing.value() - targetBonus) > 0.001) {
                attr.removeModifier(PAVEMENT_SPEED_ID);
                attr.addTemporaryModifier(new EntityAttributeModifier(
                        PAVEMENT_SPEED_ID,
                        targetBonus,
                        EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE
                ));
            }
        } else if (attr.hasModifier(PAVEMENT_SPEED_ID)) {
            attr.removeModifier(PAVEMENT_SPEED_ID);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        tooltip.add(Text.translatable("tooltip.factoriocore.pavement_speed", speedBonusPercent));
    }

    @Override
    protected float calcBlockBreakingDelta(net.minecraft.block.BlockState state, net.minecraft.entity.player.PlayerEntity player, net.minecraft.world.BlockView world, net.minecraft.util.math.BlockPos pos) {
        return 0.15f;
    }
}