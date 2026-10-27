package com.fixplot.factoriocore.content.fluids;

import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public interface FactorioFluidContainer {

    enum FluidKind {
        NONE("Empty", "fluid.factoriocore.none", 0xFF555555),
        WATER("Water", "fluid.factoriocore.water", 0xFF2980B9),
        STEAM("Steam", "fluid.factoriocore.steam", 0xFFD5DBDB),
        CRUDE_OIL("Crude Oil", "fluid.factoriocore.crude_oil", 0xFF1C1C22),
        HEAVY_OIL("Heavy Oil", "fluid.factoriocore.heavy_oil", 0xFFA93226),
        LIGHT_OIL("Light Oil", "fluid.factoriocore.light_oil", 0xFFD68910),
        PETROLEUM_GAS("Petroleum Gas", "fluid.factoriocore.petroleum_gas", 0xFF8E44AD),
        LUBRICANT("Lubricant", "fluid.factoriocore.lubricant", 0xFF27AE60),
        SULFURIC_ACID("Sulfuric Acid", "fluid.factoriocore.sulfuric_acid", 0xFFD4AC0D);

        public final String displayName;
        public final String translationKey;
        public final int color;

        FluidKind(String displayName, String translationKey, int color) {
            this.displayName = displayName;
            this.translationKey = translationKey;
            this.color = color;
        }

        public String getLocalizedName() {
            return Text.translatableWithFallback(this.translationKey, this.displayName).getString();
        }
    }

    record HudFluidLine(String label, int amountMb, int capacityMb, int color) {
        public String formatText() {
            return label + ": " + amountMb + " / " + capacityMb + " "
                    + Text.translatableWithFallback("unit.factoriocore.mb", "mB").getString();
        }
    }

    FluidKind getFluidKind();
    int getFluidAmount();
    int getFluidCapacity();
    int getTemperatureC();
    boolean canAcceptFluid(FluidKind kind, Direction fromSide);
    int insertFluid(FluidKind kind, int maxAmount, int tempC, Direction fromSide);

    static int pushFluidToNeighbor(World world, BlockPos targetPos, Direction fromSide,
                                   FluidKind kind, int maxOffer, int tempC) {
        if (world == null || maxOffer <= 0 || kind == FluidKind.NONE) return 0;
        var state = world.getBlockState(targetPos);
        BlockPos canonical = BlockHealthManager.getCanonicalPos(targetPos, state);
        if (world.getBlockEntity(canonical) instanceof FactorioFluidContainer container) {
            if (container.canAcceptFluid(kind, fromSide)) {
                return container.insertFluid(kind, maxOffer, tempC, fromSide);
            }
        }
        return 0;
    }
}