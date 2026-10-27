package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.fluids.BoilerScreenHandler;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterScreenHandler;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestScreenHandler;
import com.fixplot.factoriocore.content.logistics.inserter.InserterScreenHandler;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterScreenHandler;
import com.fixplot.factoriocore.content.production.assembler.AssemblerScreenHandler;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillScreenHandler;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillScreenHandler;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceScreenHandler;
import com.fixplot.factoriocore.content.research.LabScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModScreenHandlers {

    public static final ScreenHandlerType<InserterScreenHandler> INSERTER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "inserter"),
            new ExtendedScreenHandlerType<>(InserterScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<BurnerInserterScreenHandler> BURNER_INSERTER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "burner_inserter"),
            new ExtendedScreenHandlerType<>(BurnerInserterScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<SplitterScreenHandler> SPLITTER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "splitter"),
            new ExtendedScreenHandlerType<>(SplitterScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<BurnerDrillScreenHandler> BURNER_DRILL_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "burner_drill"),
            new ExtendedScreenHandlerType<>(BurnerDrillScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<FactorioFurnaceScreenHandler> FACTORIO_FURNACE_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "factorio_furnace"),
            new ExtendedScreenHandlerType<>(FactorioFurnaceScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<FactorioChestScreenHandler> FACTORIO_CHEST_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "factorio_chest"),
            new ExtendedScreenHandlerType<>(FactorioChestScreenHandler::new, BlockPos.PACKET_CODEC));

    public static final ScreenHandlerType<AssemblerScreenHandler> ASSEMBLER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "assembler"),
            new ScreenHandlerType<>(AssemblerScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<BoilerScreenHandler> BOILER_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "boiler"),
            new ScreenHandlerType<>(BoilerScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<ElectricDrillScreenHandler> ELECTRIC_DRILL_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "electric_drill"),
            new ScreenHandlerType<>(ElectricDrillScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<LabScreenHandler> LAB_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "lab"),
            new ScreenHandlerType<>(LabScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static final ScreenHandlerType<com.fixplot.factoriocore.content.oil.OilMachines.OilMachineScreenHandler> OIL_MACHINE_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "oil_machine"),
            new ScreenHandlerType<>(com.fixplot.factoriocore.content.oil.OilMachines.OilMachineScreenHandler::new, FeatureFlags.VANILLA_FEATURES));

    public static void registerScreenHandlers() {
        FactorioCore.LOGGER.info("Registering Screen Handlers for " + FactorioCore.MOD_ID);
    }

    public static final ScreenHandlerType<com.fixplot.factoriocore.content.combat.turret.TurretScreenHandler> TURRET_SCREEN_HANDLER = Registry.register(
            Registries.SCREEN_HANDLER, Identifier.of(FactorioCore.MOD_ID, "turret"),
            new ExtendedScreenHandlerType<>(com.fixplot.factoriocore.content.combat.turret.TurretScreenHandler::new, BlockPos.PACKET_CODEC));

    public static void registerAll() {
        registerScreenHandlers();
    }
}
