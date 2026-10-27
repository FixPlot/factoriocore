package com.fixplot.factoriocore.client.mixin;

import com.fixplot.factoriocore.content.pollution.PollutionManager;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class ExampleClientMixin {

	@Inject(method = "getWaterColor", at = @At("RETURN"), cancellable = true)
	private static void factoriocore$tintPollutedWater(BlockRenderView world, BlockPos pos, CallbackInfoReturnable<Integer> cir) {
		if (pos == null) return;
		float factor = PollutionManager.getClientWaterTintFactor(pos.getX(), pos.getZ());
		if (factor <= 0.01f) return;

		int baseColor = cir.getReturnValueI();
		int r1 = (baseColor >> 16) & 0xFF;
		int g1 = (baseColor >> 8) & 0xFF;
		int b1 = baseColor & 0xFF;

		// Каноничный болотно-зелёный цвет загрязнённой воды из Factorio (#4E7A27)
		int r = MathHelper.lerp(factor, r1, 0x4E);
		int g = MathHelper.lerp(factor, g1, 0x7A);
		int b = MathHelper.lerp(factor, b1, 0x27);

		cir.setReturnValue((r << 16) | (g << 8) | b);
	}
}