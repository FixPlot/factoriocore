package com.fixplot.factoriocore.worldgen;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

public class ModWorldGen {

    public static boolean newlyCustomizedWorld = false;

    public static final RegistryKey<World> NAUVIS_WORLD_KEY = RegistryKey.of(
            RegistryKeys.WORLD,
            Identifier.of(FactorioCore.MOD_ID, "nauvis")
    );

    public static void registerWorldGen() {
        Registry.register(
                Registries.CHUNK_GENERATOR,
                Identifier.of(FactorioCore.MOD_ID, "nauvis_generator"),
                NauvisChunkGenerator.CODEC
        );

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            long mcSeed = server.getSaveProperties().getGeneratorOptions().getSeed();
            NauvisConfig.loadOrSaveForServer(server, newlyCustomizedWorld, mcSeed);
            newlyCustomizedWorld = false;
            FactorioCore.LOGGER.info("Loaded Nauvis World Seed: " + NauvisConfig.ACTIVE.worldSeed);
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (state.getBlock() instanceof ResourceOreBlock oreBlock) {
                if (player.isCreative() && player.isSneaking()) {
                    return true;
                }
                if (!world.isClient) {
                    ItemStack mined = oreBlock.createProductStack(1);
                    // ВАЖНО: сохраняем имя ресурса ДО offerOrDrop, так как offerOrDrop обнуляет стек до 0 (Воздух)!
                    String resourceName = mined.getName().getString();
                    player.getInventory().offerOrDrop(mined);

                    int left = ResourceOreBlock.consumeOreAt(world, pos, 1);
                    world.playSound(null, pos, SoundEvents.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.7f, 1.15f);

                    if (left > 0) {
                        player.sendMessage(Text.translatable("hud.factoriocore.mined_ore_feedback", resourceName, left)//
                                , true);
                    } else {
                        
                    }
                }
                return false;
            }
            return true;
        });

        FactorioCore.LOGGER.info("Registered Nauvis World Generation for " + FactorioCore.MOD_ID);
    }
}