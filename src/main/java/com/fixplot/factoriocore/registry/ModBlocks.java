package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.combat.FactorioGateBlock;
import com.fixplot.factoriocore.content.combat.FactorioGateBlockEntity;
import com.fixplot.factoriocore.content.combat.FactorioWallBlock;
import com.fixplot.factoriocore.content.combat.LandMineBlock;
import com.fixplot.factoriocore.content.combat.LandMineBlockEntity;
import com.fixplot.factoriocore.content.electricity.CreativeEnergySourceBlock;
import com.fixplot.factoriocore.content.electricity.ElectricPoleBlock;
import com.fixplot.factoriocore.content.fluids.*;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlockEntity;
import com.fixplot.factoriocore.content.logistics.express_belt.ExpressBeltBlock;
import com.fixplot.factoriocore.content.logistics.fast_belt.FastBeltBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.pavement.LandfillItem;
import com.fixplot.factoriocore.content.logistics.pavement.PavementBlock;
import com.fixplot.factoriocore.content.logistics.pavement.PavementItem;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterBlock;
import com.fixplot.factoriocore.content.logistics.underground.UndergroundBeltBlock;
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
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block BELT = registerBlock("belt",
            new BeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 1, false));

    public static final Block FAST_BELT = registerBlock("fast_belt",
            new FastBeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block EXPRESS_BELT = registerBlock("express_belt",
            new ExpressBeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block UNDERGROUND_BELT = registerBlock("underground_belt",
            new UndergroundBeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 1));

    public static final Block FAST_UNDERGROUND_BELT = registerBlock("fast_underground_belt",
            new UndergroundBeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 2));

    public static final Block EXPRESS_UNDERGROUND_BELT = registerBlock("express_underground_belt",
            new UndergroundBeltBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 3));

    public static final Block SPLITTER = registerBlock("splitter",
            new SplitterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 1));

    public static final Block FAST_SPLITTER = registerBlock("fast_splitter",
            new SplitterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 2));

    public static final Block EXPRESS_SPLITTER = registerBlock("express_splitter",
            new SplitterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 3));

    public static final Block BURNER_INSERTER = registerBlock("burner_inserter",
            new BurnerInserterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block LONG_HANDED_INSERTER = registerBlock("long_handed_inserter",
            new InserterBlock(AbstractBlock.Settings.create().strength(1.5f).nonOpaque()));

    public static final Block FAST_INSERTER = registerBlock("fast_inserter",
            new InserterBlock(AbstractBlock.Settings.create().strength(1.5f).nonOpaque()));

    public static final Block INSERTER = registerBlock("inserter",
            new InserterBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block PIPE = registerBlock("pipe",
            new PipeBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), false));

    public static final Block PIPE_TO_GROUND = registerBlock("pipe_to_ground",
            new PipeBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), true));

    public static final Block OFFSHORE_PUMP = registerBlock("offshore_pump",
            new OffshorePumpBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block BOILER = registerBlock("boiler",
            new BoilerBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block STEAM_ENGINE = registerBlock("steam_engine",
            new SteamEngineBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block ELECTRIC_MINING_DRILL = registerBlock("electric_mining_drill",
            new ElectricDrillBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final BlockEntityType<PipeBlockEntity> PIPE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "pipe"),
            FabricBlockEntityTypeBuilder.create(PipeBlockEntity::new, PIPE, PIPE_TO_GROUND).build()
    );

    public static final BlockEntityType<OffshorePumpBlockEntity> OFFSHORE_PUMP_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "offshore_pump"),
            FabricBlockEntityTypeBuilder.create(OffshorePumpBlockEntity::new, OFFSHORE_PUMP).build()
    );

    public static final BlockEntityType<BoilerBlockEntity> BOILER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "boiler"),
            FabricBlockEntityTypeBuilder.create(BoilerBlockEntity::new, BOILER).build()
    );

    public static final BlockEntityType<SteamEngineBlockEntity> STEAM_ENGINE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "steam_engine"),
            FabricBlockEntityTypeBuilder.create(SteamEngineBlockEntity::new, STEAM_ENGINE).build()
    );

    public static final BlockEntityType<ElectricDrillBlockEntity> ELECTRIC_DRILL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "electric_mining_drill"),
            FabricBlockEntityTypeBuilder.create(ElectricDrillBlockEntity::new, ELECTRIC_MINING_DRILL).build()
    );

    public static final Block BURNER_MINING_DRILL = registerBlock("burner_mining_drill",
            new BurnerDrillBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block STONE_FURNACE = registerBlock("stone_furnace",
            new FactorioFurnaceBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS).nonOpaque(), 1));

    public static final Block STEEL_FURNACE = registerBlock("steel_furnace",
            new FactorioFurnaceBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 2));

    public static final Block WOODEN_CHEST = registerBlock("wooden_chest",
            new FactorioChestBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).nonOpaque(), 1));

    public static final Block IRON_CHEST = registerBlock("iron_chest",
            new FactorioChestBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque(), 2));

    public static final Block STEEL_CHEST = registerBlock("steel_chest",
            new FactorioChestBlock(AbstractBlock.Settings.copy(Blocks.NETHERITE_BLOCK).strength(4.0f, 12.0f).nonOpaque(), 3));

    public static final Block LANDFILL = registerLandfillBlock("landfill",
            new Block(AbstractBlock.Settings.copy(Blocks.COARSE_DIRT).strength(0.8f)));

    public static final Block STONE_PATH = registerPavementBlock("stone_path",
            new PavementBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS), 130));

    public static final Block CONCRETE = registerPavementBlock("concrete",
            new PavementBlock(AbstractBlock.Settings.copy(Blocks.SMOOTH_STONE), 140));

    public static final Block HAZARD_CONCRETE = registerPavementBlock("hazard_concrete",
            new PavementBlock(AbstractBlock.Settings.copy(Blocks.SMOOTH_STONE), 140));

    public static final Block REFINED_CONCRETE = registerPavementBlock("refined_concrete",
            new PavementBlock(AbstractBlock.Settings.copy(Blocks.POLISHED_DEEPSLATE), 150));

    public static final Block REFINED_HAZARD_CONCRETE = registerPavementBlock("refined_hazard_concrete",
            new PavementBlock(AbstractBlock.Settings.copy(Blocks.POLISHED_DEEPSLATE), 150));

    public static final Block STONE_WALL = registerBlock("stone_wall",
            new FactorioWallBlock(AbstractBlock.Settings.copy(Blocks.STONE_BRICKS).strength(3.0f, 12.0f).nonOpaque()));

    public static final Block GATE = registerBlock("gate",
            new FactorioGateBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.0f, 12.0f).nonOpaque()));

    public static final Block LAND_MINE = registerBlock("land_mine",
            new LandMineBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(0.4f, 1.0f).nonOpaque().noCollision()));

    public static final Block BITER_SPAWNER = registerBlock("biter_spawner",
            new com.fixplot.factoriocore.content.combat.biters.BiterSpawnerBlock(
                    AbstractBlock.Settings.copy(Blocks.SPAWNER).strength(5.0f, 10.0f).nonOpaque()));

    public static final BlockEntityType<com.fixplot.factoriocore.content.combat.biters.BiterSpawnerBlockEntity> BITER_SPAWNER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "biter_spawner"),
            FabricBlockEntityTypeBuilder.create(
                    com.fixplot.factoriocore.content.combat.biters.BiterSpawnerBlockEntity::new,
                    BITER_SPAWNER).build()
    );

    public static final BlockEntityType<FactorioGateBlockEntity> FACTORIO_GATE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "gate"),
            FabricBlockEntityTypeBuilder.create(FactorioGateBlockEntity::new, GATE).build()
    );

    public static final BlockEntityType<LandMineBlockEntity> LAND_MINE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "land_mine"),
            FabricBlockEntityTypeBuilder.create(LandMineBlockEntity::new, LAND_MINE).build()
    );

    public static final Block SMALL_ELECTRIC_POLE = registerBlock("small_electric_pole",
            new ElectricPoleBlock(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).strength(1.0f).nonOpaque(), 2, 7.5, false, 1));

    public static final Block MEDIUM_ELECTRIC_POLE = registerBlock("medium_electric_pole",
            new ElectricPoleBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(1.5f).nonOpaque(), 3, 9.0, false, 2));

    public static final Block BIG_ELECTRIC_POLE = registerBlock("big_electric_pole",
            new ElectricPoleBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.0f).nonOpaque(), 2, 30.0, true, 3));

    public static final Block SUBSTATION = registerBlock("substation",
            new ElectricPoleBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.5f).nonOpaque(), 9, 18.0, true, 4));

    public static final BlockEntityType<ElectricPoleBlock.LargePoleBlockEntity> LARGE_POLE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "large_electric_pole"),
            FabricBlockEntityTypeBuilder.create(ElectricPoleBlock.LargePoleBlockEntity::new, BIG_ELECTRIC_POLE, SUBSTATION).build()
    );

    public static final Block ELECTRIC_ENERGY_INTERFACE = registerBlock("electric_energy_interface",
            new CreativeEnergySourceBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(1.5f).nonOpaque()));

    public static final Block IRON_ORE_DEPOSIT = registerBlock("iron_ore_deposit",
            new ResourceOreBlock(AbstractBlock.Settings.copy(Blocks.STONE).strength(0.7f, 1200.0f).nonOpaque(), 0));

    public static final Block COPPER_ORE_DEPOSIT = registerBlock("copper_ore_deposit",
            new ResourceOreBlock(AbstractBlock.Settings.copy(Blocks.STONE).strength(0.7f, 1200.0f).nonOpaque(), 1));

    public static final Block COAL_DEPOSIT = registerBlock("coal_deposit",
            new ResourceOreBlock(AbstractBlock.Settings.copy(Blocks.STONE).strength(0.7f, 1200.0f).nonOpaque(), 2));

    public static final Block STONE_DEPOSIT = registerBlock("stone_deposit",
            new ResourceOreBlock(AbstractBlock.Settings.copy(Blocks.STONE).strength(0.7f, 1200.0f).nonOpaque(), 3));

    public static final BlockEntityType<BeltBlockEntity> BELT_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "belt"),
            FabricBlockEntityTypeBuilder.create(
                    BeltBlockEntity::new,
                    BELT,
                    FAST_BELT,
                    EXPRESS_BELT,
                    UNDERGROUND_BELT,
                    FAST_UNDERGROUND_BELT,
                    EXPRESS_UNDERGROUND_BELT,
                    SPLITTER,
                    FAST_SPLITTER,
                    EXPRESS_SPLITTER
            ).build()
    );

    public static final BlockEntityType<InserterBlockEntity> INSERTER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "inserter"),
            FabricBlockEntityTypeBuilder.create(
                    InserterBlockEntity::new,
                    INSERTER,
                    BURNER_INSERTER,
                    LONG_HANDED_INSERTER,
                    FAST_INSERTER
            ).build()
    );

    public static final BlockEntityType<BurnerDrillBlockEntity> BURNER_DRILL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "burner_mining_drill"),
            FabricBlockEntityTypeBuilder.create(
                    BurnerDrillBlockEntity::new,
                    BURNER_MINING_DRILL
            ).build()
    );

    public static final BlockEntityType<FactorioFurnaceBlockEntity> FACTORIO_FURNACE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "factorio_furnace"),
            FabricBlockEntityTypeBuilder.create(
                    FactorioFurnaceBlockEntity::new,
                    STONE_FURNACE,
                    STEEL_FURNACE
            ).build()
    );

    public static final BlockEntityType<FactorioChestBlockEntity> FACTORIO_CHEST_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "factorio_chest"),
            FabricBlockEntityTypeBuilder.create(
                    FactorioChestBlockEntity::new,
                    WOODEN_CHEST,
                    IRON_CHEST,
                    STEEL_CHEST
            ).build()
    );

    public static final BlockEntityType<ResourceOreBlockEntity> RESOURCE_ORE_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "resource_ore_deposit"),
            FabricBlockEntityTypeBuilder.create(
                    ResourceOreBlockEntity::new,
                    IRON_ORE_DEPOSIT,
                    COPPER_ORE_DEPOSIT,
                    COAL_DEPOSIT,
                    STONE_DEPOSIT
            ).build()
    );

    private static Block registerBlock(String name, Block block) {
        registerBlockItem(name, block);
        return Registry.register(Registries.BLOCK, Identifier.of(FactorioCore.MOD_ID, name), block);
    }

    private static void registerBlockItem(String name, Block block) {
        Registry.register(Registries.ITEM, Identifier.of(FactorioCore.MOD_ID, name),
                new BlockItem(block, new Item.Settings()));
    }

    private static Block registerLandfillBlock(String name, Block block) {
        Registry.register(Registries.ITEM, Identifier.of(FactorioCore.MOD_ID, name),
                new LandfillItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, Identifier.of(FactorioCore.MOD_ID, name), block);
    }

    private static Block registerPavementBlock(String name, Block block) {
        Registry.register(Registries.ITEM, Identifier.of(FactorioCore.MOD_ID, name),
                new PavementItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, Identifier.of(FactorioCore.MOD_ID, name), block);
    }

    public static final Block ASSEMBLER = registerBlock("assembler",
            new AssemblerBlock(FabricBlockSettings.create().mapColor(MapColor.IRON_GRAY).strength(3.5f).requiresTool().nonOpaque()));

    public static final BlockEntityType<AssemblerBlockEntity> ASSEMBLER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            FactorioCore.id("assembler_block_entity"),
            FabricBlockEntityTypeBuilder.create(AssemblerBlockEntity::new, ASSEMBLER).build(null)
    );

    public static final Block LAB = registerBlock("lab",
            new LabBlock(AbstractBlock.Settings.create().strength(2.5f).nonOpaque()));

    public static final BlockEntityType<LabBlockEntity> LAB_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "lab"),
            FabricBlockEntityTypeBuilder.create(LabBlockEntity::new, LAB).build()
    );

    public static final Block CRUDE_OIL_DEPOSIT = registerBlock("crude_oil_deposit",
            new OilMachines.CrudeOilDepositBlock(
                    AbstractBlock.Settings.copy(Blocks.DEEPSLATE).strength(-1.0f, 3600000.0f).nonOpaque()));

    public static final Block PUMPJACK = registerBlock("pumpjack",
            new OilMachines.PumpjackBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block OIL_REFINERY = registerBlock("oil_refinery",
            new OilMachines.OilRefineryBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final Block CHEMICAL_PLANT = registerBlock("chemical_plant",
            new OilMachines.ChemicalPlantBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).nonOpaque()));

    public static final BlockEntityType<OilMachines.CrudeOilDepositBlockEntity> CRUDE_OIL_DEPOSIT_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "crude_oil_deposit"),
            FabricBlockEntityTypeBuilder.create(OilMachines.CrudeOilDepositBlockEntity::new, CRUDE_OIL_DEPOSIT).build()
    );

    public static final BlockEntityType<OilMachines.PumpjackBlockEntity> PUMPJACK_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "pumpjack"),
            FabricBlockEntityTypeBuilder.create(OilMachines.PumpjackBlockEntity::new, PUMPJACK).build()
    );

    public static final BlockEntityType<OilMachines.OilRefineryBlockEntity> OIL_REFINERY_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "oil_refinery"),
            FabricBlockEntityTypeBuilder.create(OilMachines.OilRefineryBlockEntity::new, OIL_REFINERY).build()
    );

    public static final BlockEntityType<OilMachines.ChemicalPlantBlockEntity> CHEMICAL_PLANT_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "chemical_plant"),
            FabricBlockEntityTypeBuilder.create(OilMachines.ChemicalPlantBlockEntity::new, CHEMICAL_PLANT).build()
    );

    public static final Block STORAGE_TANK = registerBlock("storage_tank",
            new StorageTankBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.5f).nonOpaque()));

    public static final Block SOLAR_PANEL = registerBlock("solar_panel",
            new com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.SolarPanelBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.0f).nonOpaque()));

    public static final Block ACCUMULATOR = registerBlock("accumulator",
            new com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.AccumulatorBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(2.0f).nonOpaque()));

    public static final BlockEntityType<StorageTankBlock.StorageTankBlockEntity> STORAGE_TANK_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "storage_tank"),
            FabricBlockEntityTypeBuilder.create(StorageTankBlock.StorageTankBlockEntity::new, STORAGE_TANK).build()
    );

    public static final BlockEntityType<com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.SolarPanelBlockEntity> SOLAR_PANEL_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "solar_panel"),
            FabricBlockEntityTypeBuilder.create(com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.SolarPanelBlockEntity::new, SOLAR_PANEL).build()
    );

    public static final BlockEntityType<com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.AccumulatorBlockEntity> ACCUMULATOR_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "accumulator"),
            FabricBlockEntityTypeBuilder.create(com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines.AccumulatorBlockEntity::new, ACCUMULATOR).build()
    );

    public static final Block GUN_TURRET = registerBlock("gun_turret",
            new com.fixplot.factoriocore.content.combat.turret.TurretBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.0f, 12.0f).nonOpaque(), 0));

    public static final Block LASER_TURRET = registerBlock("laser_turret",
            new com.fixplot.factoriocore.content.combat.turret.TurretBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f, 12.0f).nonOpaque(), 1));

    public static final Block FLAMETHROWER_TURRET = registerBlock("flamethrower_turret",
            new com.fixplot.factoriocore.content.combat.turret.TurretBlock(
                    AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).strength(3.5f, 12.0f).nonOpaque(), 2));

    public static final BlockEntityType<com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity> TURRET_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(FactorioCore.MOD_ID, "turret"),
            FabricBlockEntityTypeBuilder.create(
                    com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity::new,
                    GUN_TURRET, LASER_TURRET, FLAMETHROWER_TURRET
            ).build()
    );

    public static void registerModBlocks() {
        FactorioCore.LOGGER.info("Registering Mod Blocks for " + FactorioCore.MOD_ID);
    }
}