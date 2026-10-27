package com.fixplot.factoriocore.client.sound;

import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.fluids.BoilerBlock;
import com.fixplot.factoriocore.content.fluids.BoilerBlockEntity;
import com.fixplot.factoriocore.content.fluids.SteamEngineBlock;
import com.fixplot.factoriocore.content.fluids.SteamEngineBlockEntity;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlock;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.content.research.LabBlock;
import com.fixplot.factoriocore.content.research.LabBlockEntity;
import com.fixplot.factoriocore.registry.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class FactorioSoundController {

    private static final Map<Long, Long> LAST_PLAYED_TICK = new HashMap<>();

    public static boolean hasCustomSoundLoaded(MinecraftClient client, SoundEvent customEvent) {
        return client.getSoundManager().get(customEvent.getId()) != null;
    }

    public static void playUiClick(MinecraftClient client) {
        if (client.player == null) return;
        if (hasCustomSoundLoaded(client, ModSounds.GUI_CLICK)) {
            client.player.playSound(ModSounds.GUI_CLICK, 0.5f, 1.0f);
        } else {
            client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.15f);
        }
    }

    public static void tickClient(MinecraftClient client) {
        if (client.world == null || client.player == null || client.isPaused()) return;
        if (!FactorioConfig.INSTANCE.enableMachineSounds || FactorioConfig.INSTANCE.machineSoundVolume <= 0.01f) {
            return;
        }

        long now = client.world.getTime();
        if ((now & 3L) != 0L) return; // Сканируем раз в 4 тика (0.2 сек) для идеального FPS

        BlockPos playerPos = client.player.getBlockPos();
        float masterVol = FactorioConfig.INSTANCE.machineSoundVolume;
        int radius = 14;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos pos = playerPos.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;
                    Block block = state.getBlock();

                    if (block instanceof AssemblerBlock && state.get(AssemblerBlock.PART) == 4 && !state.get(AssemblerBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof AssemblerBlockEntity asm && asm.isWorking()) {
                            tryPlayLoop(client, pos, now, 32, ModSounds.ASSEMBLER, SoundEvents.BLOCK_PISTON_EXTEND, 0.35f * masterVol, 1.25f);
                        }
                    } else if (block instanceof BurnerDrillBlock && state.get(BurnerDrillBlock.PART) == 0 && !state.get(BurnerDrillBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof BurnerDrillBlockEntity drill && drill.isWorking()) {
                            tryPlayLoop(client, pos, now, 28, ModSounds.BURNER_DRILL, SoundEvents.BLOCK_GRINDSTONE_USE, 0.40f * masterVol, 0.75f);
                        }
                    } else if (block instanceof ElectricDrillBlock && state.get(ElectricDrillBlock.PART) == 4 && !state.get(ElectricDrillBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof ElectricDrillBlockEntity edrill && edrill.isActivelyMining()) {
                            tryPlayLoop(client, pos, now, 28, ModSounds.ELECTRIC_DRILL, SoundEvents.BLOCK_GRINDSTONE_USE, 0.35f * masterVol, 1.15f);
                        }
                    } else if (block instanceof FactorioFurnaceBlock && state.get(FactorioFurnaceBlock.PART) == 0 && !state.get(FactorioFurnaceBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof FactorioFurnaceBlockEntity furnace && furnace.isBurning()) {
                            tryPlayLoop(client, pos, now, 36, ModSounds.FURNACE, SoundEvents.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.65f * masterVol, 0.95f);
                        }
                    } else if (block instanceof BoilerBlock && state.get(BoilerBlock.PART) == 1 && !state.get(BoilerBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof BoilerBlockEntity boiler && boiler.isBurning()) {
                            tryPlayLoop(client, pos, now, 36, ModSounds.BOILER, SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE, 0.60f * masterVol, 0.85f);
                        }
                    } else if (block instanceof SteamEngineBlock && state.get(SteamEngineBlock.PART) == 1 && state.get(SteamEngineBlock.LAYER) == 0) {
                        if (client.world.getBlockEntity(pos) instanceof SteamEngineBlockEntity eng && eng.isGeneratingPower()) {
                            tryPlayLoop(client, pos, now, 24, ModSounds.STEAM_ENGINE, SoundEvents.BLOCK_CAMPFIRE_CRACKLE, 0.45f * masterVol, 1.35f);
                        }
                    } else if (block instanceof OilMachines.PumpjackBlock && state.get(OilMachines.PumpjackBlock.PART) == 4 && !state.get(OilMachines.PumpjackBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof OilMachines.PumpjackBlockEntity pj && pj.isWorking()) {
                            tryPlayLoop(client, pos, now, 32, ModSounds.PUMPJACK, SoundEvents.BLOCK_PISTON_CONTRACT, 0.40f * masterVol, 0.70f);
                        }
                    } else if (block instanceof OilMachines.OilRefineryBlock && state.get(OilMachines.OilRefineryBlock.PART) == 4 && !state.get(OilMachines.OilRefineryBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof OilMachines.OilRefineryBlockEntity ref && ref.isWorking()) {
                            tryPlayLoop(client, pos, now, 36, ModSounds.OIL_REFINERY, SoundEvents.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.55f * masterVol, 0.80f);
                        }
                    } else if (block instanceof OilMachines.ChemicalPlantBlock && state.get(OilMachines.ChemicalPlantBlock.PART) == 4 && !state.get(OilMachines.ChemicalPlantBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof OilMachines.ChemicalPlantBlockEntity cp && cp.isWorking()) {
                            tryPlayLoop(client, pos, now, 32, ModSounds.CHEMICAL_PLANT, SoundEvents.BLOCK_BREWING_STAND_BREW, 0.45f * masterVol, 1.05f);
                        }
                    } else if (block instanceof LabBlock && state.get(LabBlock.PART) == 4 && !state.get(LabBlock.UPPER)) {
                        if (client.world.getBlockEntity(pos) instanceof LabBlockEntity lab && lab.isWorking()) {
                            tryPlayLoop(client, pos, now, 36, ModSounds.LAB, SoundEvents.BLOCK_BEACON_AMBIENT, 0.45f * masterVol, 1.40f);
                        }
                    } else if (block instanceof InserterBlock) {
                        if (client.world.getBlockEntity(pos) instanceof InserterBlockEntity ins && !ins.getHeldStack().isEmpty()) {
                            tryPlayLoop(client, pos, now, 16, ModSounds.INSERTER, SoundEvents.BLOCK_DISPENSER_DISPENSE, 0.22f * masterVol, 1.45f);
                        }
                    } else if (block instanceof BeltBlock) {
                        if (client.world.getBlockEntity(pos) instanceof BeltBlockEntity belt
                                && (!belt.getLeftLane().isEmpty() || !belt.getRightLane().isEmpty())) {
                            tryPlayLoop(client, pos, now, 40, ModSounds.BELT, SoundEvents.BLOCK_WOOL_STEP, 0.14f * masterVol, 0.65f);
                        }
                    }
                }
            }
        }

        if (LAST_PLAYED_TICK.size() > 512) {
            LAST_PLAYED_TICK.entrySet().removeIf(e -> now - e.getValue() > 100L);
        }
    }

    private static void tryPlayLoop(MinecraftClient client, BlockPos pos, long now, int cooldownTicks,
                                    SoundEvent factorioSound, SoundEvent vanillaFallback,
                                    float volume, float fallbackPitch) {
        long key = pos.asLong();
        long last = LAST_PLAYED_TICK.getOrDefault(key, -1000L);
        if (now - last < cooldownTicks) return;

        LAST_PLAYED_TICK.put(key, now);
        boolean useCustom = hasCustomSoundLoaded(client, factorioSound);
        SoundEvent toPlay = useCustom ? factorioSound : vanillaFallback;
        float pitch = useCustom ? 1.0f : fallbackPitch;

        client.world.playSound(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                toPlay, SoundCategory.BLOCKS, volume, pitch, false
        );
    }
}