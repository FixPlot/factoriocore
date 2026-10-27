package com.fixplot.factoriocore.content.electricity;

import com.fixplot.factoriocore.content.fluids.SteamEngineBlock;
import com.fixplot.factoriocore.content.fluids.SteamEngineBlockEntity;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlock;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import com.fixplot.factoriocore.content.research.LabBlock;
import com.fixplot.factoriocore.content.research.LabBlockEntity;
import com.fixplot.factoriocore.registry.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

import java.util.*;
import java.util.function.Consumer;

public class PowerNetworkManager {

    public static Consumer<BlockPos> clientGuiOpener = null;

    public record MachineGroupStat(Block block, int count, long watts, int color) {}

    public record NetworkSnapshot(
            long totalDemandWatts,
            long totalProductionWatts,
            long maxProductionCapacityWatts,
            long accumulatorStoredJoules,
            long accumulatorCapacityJoules,
            List<MachineGroupStat> consumers,
            List<MachineGroupStat> producers
    ) {
        public int getAccumulatorCount() {
            for (MachineGroupStat stat : producers) {
                if (stat.block() == ModBlocks.ACCUMULATOR) {
                    return stat.count();
                }
            }
            if (accumulatorCapacityJoules > 0L) {
                return (int) (accumulatorCapacityJoules / SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_CAPACITY_JOULES);
            }
            return 0;
        }
    }

    private static String unitOrFallback(String translationKey, String fallback) {
        String translated = Text.translatable(translationKey).getString();
        return translated.equals(translationKey) ? fallback : translated;
    }

    public static String formatWatts(long watts) {
        if (watts >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1f %s", watts / 1_000_000_000.0, unitOrFallback("unit.factoriocore.gw", "GW"));
        }
        if (watts >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1f %s", watts / 1_000_000.0, unitOrFallback("unit.factoriocore.mw", "MW"));
        }
        if (watts >= 1_000L) {
            return String.format(Locale.ROOT, "%.1f %s", watts / 1_000.0, unitOrFallback("unit.factoriocore.kw", "kW"));
        }
        return watts + " " + unitOrFallback("unit.factoriocore.w", "W");
    }

    public static String formatAxisWatts(long watts) {
        if (watts >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.0f %s", watts / 1_000_000_000.0, unitOrFallback("unit.factoriocore.gw", "GW"));
        }
        if (watts >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.0f %s", watts / 1_000_000.0, unitOrFallback("unit.factoriocore.mw", "MW"));
        }
        if (watts >= 1_000L) {
            return String.format(Locale.ROOT, "%.0f %s", watts / 1_000.0, unitOrFallback("unit.factoriocore.kw", "kW"));
        }
        return watts + " " + unitOrFallback("unit.factoriocore.w", "W");
    }

    public static String formatJoules(long joules) {
        if (joules >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.1f %s", joules / 1_000_000_000.0, unitOrFallback("unit.factoriocore.gj", "GJ"));
        }
        if (joules >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.1f %s", joules / 1_000_000.0, unitOrFallback("unit.factoriocore.mj", "MJ"));
        }
        if (joules >= 1_000L) {
            return String.format(Locale.ROOT, "%.1f %s", joules / 1_000.0, unitOrFallback("unit.factoriocore.kj", "kJ"));
        }
        return joules + " " + unitOrFallback("unit.factoriocore.j", "J");
    }

    public static String formatAxisJoules(long joules) {
        if (joules >= 1_000_000_000L) {
            return String.format(Locale.ROOT, "%.0f %s", joules / 1_000_000_000.0, unitOrFallback("unit.factoriocore.gj", "GJ"));
        }
        if (joules >= 1_000_000L) {
            return String.format(Locale.ROOT, "%.0f %s", joules / 1_000_000.0, unitOrFallback("unit.factoriocore.mj", "MJ"));
        }
        if (joules >= 1_000L) {
            return String.format(Locale.ROOT, "%.0f %s", joules / 1_000.0, unitOrFallback("unit.factoriocore.kj", "kJ"));
        }
        return joules + " " + unitOrFallback("unit.factoriocore.j", "J");
    }

    public static BlockPos findNearestConnectedPoleForMachine(World world, BlockPos machinePos, int machineFootprintRadius) {
        if (world == null) return null;
        int maxRadius = 9 + machineFootprintRadius;
        BlockPos.Mutable checkPos = new BlockPos.Mutable();

        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    checkPos.set(machinePos.getX() + dx, machinePos.getY() + dy, machinePos.getZ() + dz);
                    BlockState st = world.getBlockState(checkPos);
                    Block b = st.getBlock();

                    if (b instanceof CreativeEnergySourceBlock source) {
                        int r = source.getSupplyRadius() + machineFootprintRadius;
                        if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                            return checkPos.toImmutable();
                        }
                    } else if (b instanceof ElectricPoleBlock pole) {
                        if (!ElectricPoleBlock.isMasterPoleNode(st)) continue;
                        int r = pole.getSupplyRadius() + machineFootprintRadius;
                        if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                            return checkPos.toImmutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    public static float getSteamEngineLoadRatio(World world, BlockPos engineMasterPos) {
        if (world == null) return 0.0f;
        BlockPos polePos = findNearestConnectedPoleForMachine(world, engineMasterPos, 2);
        if (polePos == null) {
            return 0.0f;
        }

        NetworkBalance bal = evaluateNetworkBalance(world, polePos);
        if (bal.steamAvailableMaxWatts <= 0L) {
            return 0.0f;
        }

        long totalNeededFromGenerators = bal.machineDemandWatts + bal.accChargeDemandWatts;
        long primaryCovered = bal.creativeMaxWatts + bal.solarMaxWatts;
        long neededFromSteam = Math.max(0L, totalNeededFromGenerators - primaryCovered);

        return MathHelper.clamp((float) neededFromSteam / (float) bal.steamAvailableMaxWatts, 0.0f, 1.0f);
    }

    public static long getAccumulatorChargeOrDischargeWatts(World world, BlockPos accMasterPos,
                                                            SolarAndAccumulatorMachines.AccumulatorBlockEntity acc) {
        if (world == null || acc == null) return 0L;
        BlockPos polePos = findNearestConnectedPoleForMachine(world, accMasterPos, 1);
        if (polePos == null) return 0L;

        NetworkBalance bal = evaluateNetworkBalance(world, polePos);
        long totalPrimaryAndSecondary = bal.creativeMaxWatts + bal.solarMaxWatts + bal.steamAvailableMaxWatts;

        if (totalPrimaryAndSecondary > bal.machineDemandWatts) {
            if (acc.getStoredJoules() >= SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_CAPACITY_JOULES
                    || bal.accChargeDemandWatts <= 0L) {
                return 0L;
            }
            long surplusWatts = totalPrimaryAndSecondary - bal.machineDemandWatts;
            float ratio = MathHelper.clamp((float) surplusWatts / (float) bal.accChargeDemandWatts, 0.0f, 1.0f);
            return Math.round(SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS * ratio);
        } else if (bal.machineDemandWatts > totalPrimaryAndSecondary) {
            if (!acc.canProvidePower() || bal.accDischargeMaxWatts <= 0L) {
                return 0L;
            }
            long deficitWatts = bal.machineDemandWatts - totalPrimaryAndSecondary;
            float ratio = MathHelper.clamp((float) deficitWatts / (float) bal.accDischargeMaxWatts, 0.0f, 1.0f);
            return -Math.round(SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS * ratio);
        }
        return 0L;
    }

    private record NetworkBalance(
            long machineDemandWatts,
            long accChargeDemandWatts,
            long creativeMaxWatts,
            long solarMaxWatts,
            long steamAvailableMaxWatts,
            long accDischargeMaxWatts
    ) {}

    private static NetworkBalance evaluateNetworkBalance(World world, BlockPos startPolePos) {
        BlockState rawStartState = world.getBlockState(startPolePos);
        BlockPos masterStartPos = (rawStartState.getBlock() instanceof ElectricPoleBlock)
                ? ElectricPoleBlock.getMasterPos(startPolePos, rawStartState)
                : startPolePos.toImmutable();

        Set<BlockPos> poles = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        Block startBlock = world.getBlockState(masterStartPos).getBlock();
        if (startBlock instanceof ElectricPoleBlock || startBlock instanceof CreativeEnergySourceBlock) {
            poles.add(masterStartPos);
            queue.add(masterStartPos);
        }

        while (!queue.isEmpty() && poles.size() < 256) {
            BlockPos current = queue.pollFirst();
            Block currentBlock = world.getBlockState(current).getBlock();
            for (BlockPos neighbor : findConnectedNodes(world, current, currentBlock)) {
                if (poles.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        Set<BlockPos> visited = new HashSet<>();
        long machineDemand = 0L;
        long accChargeDemand = 0L;
        long creativeMax = 0L;
        long solarMax = 0L;
        long steamMax = 0L;
        long accDischargeMax = 0L;

        long singleSolarOut = SolarAndAccumulatorMachines.SolarPanelBlockEntity.getCurrentOutputWatts(world);
        BlockPos.Mutable check = new BlockPos.Mutable();

        for (BlockPos polePos : poles) {
            Block poleBlock = world.getBlockState(polePos).getBlock();
            if (poleBlock instanceof CreativeEnergySourceBlock && visited.add(polePos)) {
                creativeMax += CreativeEnergySourceBlock.OUTPUT_WATTS;
            }

            int r = getSupplyRadius(poleBlock);
            if (r <= 0) continue;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        check.set(polePos.getX() + dx, polePos.getY() + dy, polePos.getZ() + dz);
                        BlockState st = world.getBlockState(check);
                        if (st.isAir()) continue;
                        Block b = st.getBlock();

                        if (b instanceof ElectricDrillBlock) {
                            BlockPos master = ElectricDrillBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof ElectricDrillBlockEntity drill && drill.isActivelyMining()) {
                                machineDemand += ElectricDrillBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof AssemblerBlock) {
                            BlockPos master = AssemblerBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof AssemblerBlockEntity asm && asm.isWorking()) {
                                machineDemand += AssemblerBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof LabBlock) {
                            BlockPos master = LabBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof LabBlockEntity lab && lab.isWorking()) {
                                machineDemand += LabBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof OilMachines.PumpjackBlock) {
                            BlockPos master = OilMachines.Base3x3MachineBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof OilMachines.PumpjackBlockEntity pj && pj.isWorking()) {
                                machineDemand += OilMachines.PumpjackBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof OilMachines.OilRefineryBlock) {
                            BlockPos master = OilMachines.OilRefineryBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof OilMachines.OilRefineryBlockEntity ref && ref.isWorking()) {
                                machineDemand += OilMachines.OilRefineryBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof OilMachines.ChemicalPlantBlock) {
                            BlockPos master = OilMachines.Base3x3MachineBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof OilMachines.ChemicalPlantBlockEntity cp && cp.isWorking()) {
                                machineDemand += OilMachines.ChemicalPlantBlockEntity.POWER_CONSUMPTION_WATTS;
                            }
                        } else if (b instanceof InserterBlock && !(b instanceof BurnerInserterBlock)) {
                            BlockPos master = check.toImmutable();
                            if (visited.add(master)) {
                                long usage = InserterBlockEntity.ELECTRIC_IDLE_WATTS;
                                if (world.getBlockEntity(master) instanceof InserterBlockEntity ins) {
                                    boolean active = !ins.getHeldStack().isEmpty() || ins.getArmProgress(0.0f) > 0.01f;
                                    usage = active ? ins.getActiveConsumptionWatts() : InserterBlockEntity.ELECTRIC_IDLE_WATTS;
                                }
                                machineDemand += usage;
                            }
                        } else if (b instanceof SteamEngineBlock) {
                            BlockPos master = SteamEngineBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof SteamEngineBlockEntity engine && engine.hasSteamAvailable()) {
                                steamMax += SteamEngineBlockEntity.MAX_OUTPUT_WATTS;
                            }
                        } else if (b instanceof SolarAndAccumulatorMachines.SolarPanelBlock) {
                            BlockPos master = SolarAndAccumulatorMachines.SolarPanelBlock.getMasterPos(check, st);
                            if (visited.add(master)) {
                                solarMax += singleSolarOut;
                            }
                        } else if (b instanceof SolarAndAccumulatorMachines.AccumulatorBlock) {
                            BlockPos master = SolarAndAccumulatorMachines.AccumulatorBlock.getMasterPos(check, st);
                            if (visited.add(master) && world.getBlockEntity(master) instanceof SolarAndAccumulatorMachines.AccumulatorBlockEntity acc) {
                                if (acc.getStoredJoules() < SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_CAPACITY_JOULES) {
                                    accChargeDemand += SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS;
                                }
                                if (acc.canProvidePower()) {
                                    accDischargeMax += SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS;
                                }
                            }
                        } else if (b instanceof CreativeEnergySourceBlock) {
                            BlockPos master = check.toImmutable();
                            if (visited.add(master)) {
                                creativeMax += CreativeEnergySourceBlock.OUTPUT_WATTS;
                            }
                        }
                    }
                }
            }
        }

        return new NetworkBalance(machineDemand, accChargeDemand, creativeMax, solarMax, steamMax, accDischargeMax);
    }

    public static NetworkSnapshot scanNetwork(World world, BlockPos startPolePos) {
        if (world == null) {
            return new NetworkSnapshot(0, 0, 0, 0, 0, List.of(), List.of());
        }

        BlockState rawStartState = world.getBlockState(startPolePos);
        BlockPos masterStartPos = (rawStartState.getBlock() instanceof ElectricPoleBlock)
                ? ElectricPoleBlock.getMasterPos(startPolePos, rawStartState)
                : startPolePos.toImmutable();

        Set<BlockPos> poles = new LinkedHashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();

        Block startBlock = world.getBlockState(masterStartPos).getBlock();
        if (startBlock instanceof ElectricPoleBlock || startBlock instanceof CreativeEnergySourceBlock) {
            poles.add(masterStartPos);
            queue.add(masterStartPos);
        }

        while (!queue.isEmpty() && poles.size() < 256) {
            BlockPos current = queue.pollFirst();
            Block currentBlock = world.getBlockState(current).getBlock();
            for (BlockPos neighbor : findConnectedNodes(world, current, currentBlock)) {
                if (poles.add(neighbor)) {
                    queue.addLast(neighbor);
                }
            }
        }

        Set<BlockPos> visitedMachines = new HashSet<>();
        int drillCount = 0; long drillDemandWatts = 0L;
        int asmCount = 0; long asmDemandWatts = 0L;
        int labCount = 0; long labDemandWatts = 0L;
        int pumpjackCount = 0; long pumpjackDemandWatts = 0L;
        int refineryCount = 0; long refineryDemandWatts = 0L;
        int chemPlantCount = 0; long chemPlantDemandWatts = 0L;
        int basicInserterCount = 0; long basicInserterWatts = 0L;
        int longInserterCount = 0; long longInserterWatts = 0L;
        int fastInserterCount = 0; long fastInserterWatts = 0L;

        int steamEngineCount = 0; long steamEngineMaxWatts = 0L;
        int solarPanelCount = 0; long solarPanelMaxWatts = 0L;
        int accumulatorCount = 0; long accumulatorMaxWatts = 0L;
        int chargingAccCount = 0; long accChargeDemandWatts = 0L;
        long totalAccStoredJoules = 0L; long totalAccCapacityJoules = 0L;
        int creativeSourceCount = 0; long creativeSourceMaxWatts = 0L;

        long singleSolarOutput = SolarAndAccumulatorMachines.SolarPanelBlockEntity.getCurrentOutputWatts(world);

        BlockPos.Mutable check = new BlockPos.Mutable();
        for (BlockPos polePos : poles) {
            Block poleBlock = world.getBlockState(polePos).getBlock();
            if (poleBlock instanceof CreativeEnergySourceBlock) {
                if (visitedMachines.add(polePos)) {
                    creativeSourceCount++;
                    creativeSourceMaxWatts += CreativeEnergySourceBlock.OUTPUT_WATTS;
                }
            }

            int r = getSupplyRadius(poleBlock);
            if (r <= 0) continue;

            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    for (int dy = -2; dy <= 2; dy++) {
                        check.set(polePos.getX() + dx, polePos.getY() + dy, polePos.getZ() + dz);
                        BlockState st = world.getBlockState(check);
                        if (st.isAir()) continue;
                        Block b = st.getBlock();

                        if (b instanceof ElectricDrillBlock) {
                            BlockPos master = ElectricDrillBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                drillCount++;
                                if (world.getBlockEntity(master) instanceof ElectricDrillBlockEntity drill && drill.isActivelyMining()) {
                                    drillDemandWatts += ElectricDrillBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof AssemblerBlock) {
                            BlockPos master = AssemblerBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                asmCount++;
                                if (world.getBlockEntity(master) instanceof AssemblerBlockEntity asm && asm.isWorking()) {
                                    asmDemandWatts += AssemblerBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof LabBlock) {
                            BlockPos master = LabBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                labCount++;
                                if (world.getBlockEntity(master) instanceof LabBlockEntity lab && lab.isWorking()) {
                                    labDemandWatts += LabBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof OilMachines.PumpjackBlock) {
                            BlockPos master = OilMachines.Base3x3MachineBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                pumpjackCount++;
                                if (world.getBlockEntity(master) instanceof OilMachines.PumpjackBlockEntity pj && pj.isWorking()) {
                                    pumpjackDemandWatts += OilMachines.PumpjackBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof OilMachines.OilRefineryBlock) {
                            BlockPos master = OilMachines.OilRefineryBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                refineryCount++;
                                if (world.getBlockEntity(master) instanceof OilMachines.OilRefineryBlockEntity ref && ref.isWorking()) {
                                    refineryDemandWatts += OilMachines.OilRefineryBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof OilMachines.ChemicalPlantBlock) {
                            BlockPos master = OilMachines.Base3x3MachineBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                chemPlantCount++;
                                if (world.getBlockEntity(master) instanceof OilMachines.ChemicalPlantBlockEntity cp && cp.isWorking()) {
                                    chemPlantDemandWatts += OilMachines.ChemicalPlantBlockEntity.POWER_CONSUMPTION_WATTS;
                                }
                            }
                        } else if (b instanceof InserterBlock && !(b instanceof BurnerInserterBlock)) {
                            BlockPos master = check.toImmutable();
                            if (visitedMachines.add(master)) {
                                BlockEntity be = world.getBlockEntity(master);
                                long usage = InserterBlockEntity.ELECTRIC_IDLE_WATTS;
                                if (be instanceof InserterBlockEntity ins) {
                                    boolean active = !ins.getHeldStack().isEmpty() || ins.getArmProgress(0.0f) > 0.01f;
                                    usage = active ? ins.getActiveConsumptionWatts() : InserterBlockEntity.ELECTRIC_IDLE_WATTS;
                                }
                                if (b == ModBlocks.LONG_HANDED_INSERTER) {
                                    longInserterCount++;
                                    longInserterWatts += usage;
                                } else if (b == ModBlocks.FAST_INSERTER) {
                                    fastInserterCount++;
                                    fastInserterWatts += usage;
                                } else {
                                    basicInserterCount++;
                                    basicInserterWatts += usage;
                                }
                            }
                        } else if (b instanceof SteamEngineBlock) {
                            BlockPos master = SteamEngineBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                steamEngineCount++;
                                if (world.getBlockEntity(master) instanceof SteamEngineBlockEntity engine && engine.hasSteamAvailable()) {
                                    steamEngineMaxWatts += SteamEngineBlockEntity.MAX_OUTPUT_WATTS;
                                }
                            }
                        } else if (b instanceof SolarAndAccumulatorMachines.SolarPanelBlock) {
                            BlockPos master = SolarAndAccumulatorMachines.SolarPanelBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                solarPanelCount++;
                                solarPanelMaxWatts += singleSolarOutput;
                            }
                        } else if (b instanceof SolarAndAccumulatorMachines.AccumulatorBlock) {
                            BlockPos master = SolarAndAccumulatorMachines.AccumulatorBlock.getMasterPos(check, st);
                            if (visitedMachines.add(master)) {
                                accumulatorCount++;
                                totalAccCapacityJoules += SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_CAPACITY_JOULES;
                                if (world.getBlockEntity(master) instanceof SolarAndAccumulatorMachines.AccumulatorBlockEntity acc) {
                                    totalAccStoredJoules += acc.getStoredJoules();
                                    if (acc.canProvidePower()) {
                                        accumulatorMaxWatts += SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS;
                                    }
                                    if (acc.getStoredJoules() < SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_CAPACITY_JOULES) {
                                        chargingAccCount++;
                                        accChargeDemandWatts += SolarAndAccumulatorMachines.AccumulatorBlockEntity.MAX_TRANSFER_WATTS;
                                    }
                                }
                            }
                        } else if (b instanceof CreativeEnergySourceBlock) {
                            BlockPos master = check.toImmutable();
                            if (visitedMachines.add(master)) {
                                creativeSourceCount++;
                                creativeSourceMaxWatts += CreativeEnergySourceBlock.OUTPUT_WATTS;
                            }
                        }
                    }
                }
            }
        }

        long machineDemand = drillDemandWatts + asmDemandWatts + labDemandWatts
                + pumpjackDemandWatts + refineryDemandWatts + chemPlantDemandWatts
                + basicInserterWatts + longInserterWatts + fastInserterWatts;

        long primaryAndSecondaryMax = creativeSourceMaxWatts + solarPanelMaxWatts + steamEngineMaxWatts;
        long actualAccChargeWatts = (primaryAndSecondaryMax > machineDemand)
                ? Math.min(accChargeDemandWatts, primaryAndSecondaryMax - machineDemand)
                : 0L;

        long totalDemand = machineDemand + actualAccChargeWatts;
        long maxProduction = primaryAndSecondaryMax + (actualAccChargeWatts > 0 ? 0L : accumulatorMaxWatts);
        long actualProduction = Math.min(totalDemand, maxProduction);
        float satisfactionRatio = (machineDemand > 0) ? Math.min(1.0f, maxProduction / (float) machineDemand) : 1.0f;

        List<MachineGroupStat> consumerList = new ArrayList<>();
        if (asmCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.ASSEMBLER, asmCount, Math.round(asmDemandWatts * satisfactionRatio), 0xFFD4AC0D));
        if (labCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.LAB, labCount, Math.round(labDemandWatts * satisfactionRatio), 0xFFAB47BC));
        if (drillCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.ELECTRIC_MINING_DRILL, drillCount, Math.round(drillDemandWatts * satisfactionRatio), 0xFF1E88E5));
        if (pumpjackCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.PUMPJACK, pumpjackCount, Math.round(pumpjackDemandWatts * satisfactionRatio), 0xFF2E7D32));
        if (refineryCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.OIL_REFINERY, refineryCount, Math.round(refineryDemandWatts * satisfactionRatio), 0xFFF57F17));
        if (chemPlantCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.CHEMICAL_PLANT, chemPlantCount, Math.round(chemPlantDemandWatts * satisfactionRatio), 0xFF00ACC1));
        if (basicInserterCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.INSERTER, basicInserterCount, Math.round(basicInserterWatts * satisfactionRatio), 0xFFE67E22));
        if (longInserterCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.LONG_HANDED_INSERTER, longInserterCount, Math.round(longInserterWatts * satisfactionRatio), 0xFFE53935));
        if (fastInserterCount > 0) consumerList.add(new MachineGroupStat(ModBlocks.FAST_INSERTER, fastInserterCount, Math.round(fastInserterWatts * satisfactionRatio), 0xFF29B6F6));
        if (chargingAccCount > 0 && actualAccChargeWatts > 0) {
            consumerList.add(new MachineGroupStat(ModBlocks.ACCUMULATOR, chargingAccCount, actualAccChargeWatts, 0xFF00E5FF));
        }
        consumerList.sort(Comparator.comparingLong(MachineGroupStat::watts).reversed());

        long remDemand = actualProduction;
        long creativeActual = Math.min(remDemand, creativeSourceMaxWatts);
        remDemand -= creativeActual;

        long solarActual = Math.min(remDemand, solarPanelMaxWatts);
        remDemand -= solarActual;

        long steamActual = Math.min(remDemand, steamEngineMaxWatts);
        remDemand -= steamActual;

        long accActual = Math.min(remDemand, accumulatorMaxWatts);

        List<MachineGroupStat> producerList = new ArrayList<>();
        if (steamEngineCount > 0) {
            producerList.add(new MachineGroupStat(ModBlocks.STEAM_ENGINE, steamEngineCount, steamActual, 0xFF1E88E5));
        }
        if (solarPanelCount > 0) {
            producerList.add(new MachineGroupStat(ModBlocks.SOLAR_PANEL, solarPanelCount, solarActual, 0xFFFDD835));
        }
        if (accumulatorCount > 0) {
            producerList.add(new MachineGroupStat(ModBlocks.ACCUMULATOR, accumulatorCount, accActual, 0xFF00E5FF));
        }
        if (creativeSourceCount > 0) {
            producerList.add(new MachineGroupStat(ModBlocks.ELECTRIC_ENERGY_INTERFACE, creativeSourceCount, creativeActual, 0xFF26C6DA));
        }
        // Сортируем производителей от большего к меньшему по текущей выработке (как в оригинальной Factorio!)
        producerList.sort(Comparator.comparingLong(MachineGroupStat::watts).reversed());

        return new NetworkSnapshot(totalDemand, actualProduction, maxProduction, totalAccStoredJoules, totalAccCapacityJoules, consumerList, producerList);
    }

    public static boolean hasAnyConnectedPole(World world, BlockPos machinePos) {
        return findNearestConnectedPoleForMachine(world, machinePos, 1) != null;
    }

    public static boolean isMachinePoweredByPrimaryGenerators(World world, BlockPos machinePos) {
        return isMachinePoweredInternal(world, machinePos, false);
    }

    public static boolean isMachinePowered(World world, BlockPos machinePos) {
        return isMachinePoweredInternal(world, machinePos, true);
    }

    private static boolean isMachinePoweredInternal(World world, BlockPos machinePos, boolean allowAccumulators) {
        if (world == null) return false;

        Block machineBlock = world.getBlockState(machinePos).getBlock();
        int machineFootprintRadius = (machineBlock instanceof OilMachines.OilRefineryBlock) ? 2
                : ((machineBlock instanceof ElectricDrillBlock
                || machineBlock instanceof AssemblerBlock
                || machineBlock instanceof LabBlock
                || machineBlock instanceof OilMachines.Base3x3MachineBlock
                || machineBlock instanceof SolarAndAccumulatorMachines.SolarPanelBlock
                || machineBlock instanceof SolarAndAccumulatorMachines.AccumulatorBlock) ? 1 : 0);

        int maxRadius = 9 + machineFootprintRadius;
        BlockPos.Mutable checkPos = new BlockPos.Mutable();

        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                for (int dy = -2; dy <= 3; dy++) {
                    checkPos.set(machinePos.getX() + dx, machinePos.getY() + dy, machinePos.getZ() + dz);
                    BlockState st = world.getBlockState(checkPos);
                    Block b = st.getBlock();

                    if (b instanceof CreativeEnergySourceBlock source) {
                        int r = source.getSupplyRadius() + machineFootprintRadius;
                        if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                            return true;
                        }
                    } else if (b instanceof ElectricPoleBlock pole) {
                        if (!ElectricPoleBlock.isMasterPoleNode(st)) continue;
                        int r = pole.getSupplyRadius() + machineFootprintRadius;
                        if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                            if (isPoleEnergizedInternal(world, checkPos.toImmutable(), allowAccumulators)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean isPoleEnergized(World world, BlockPos startPolePos) {
        return isPoleEnergizedInternal(world, startPolePos, true);
    }

    private static boolean isPoleEnergizedInternal(World world, BlockPos startPolePos, boolean allowAccumulators) {
        if (world == null) return false;

        BlockState startState = world.getBlockState(startPolePos);
        if (startState.getBlock() instanceof ElectricPoleBlock) {
            startPolePos = ElectricPoleBlock.getMasterPos(startPolePos, startState);
        }

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(startPolePos);
        visited.add(startPolePos);

        int steps = 0;
        while (!queue.isEmpty() && steps < 64) {
            steps++;
            BlockPos current = queue.pollFirst();
            Block currentBlock = world.getBlockState(current).getBlock();

            if (currentBlock instanceof CreativeEnergySourceBlock) {
                return true;
            }
            if (hasActiveGeneratorNearPole(world, current, getSupplyRadius(currentBlock), allowAccumulators)) {
                return true;
            }

            double reach = getWireReach(currentBlock);
            int searchR = (int) Math.ceil(reach);

            for (int dx = -searchR; dx <= searchR; dx++) {
                for (int dz = -searchR; dz <= searchR; dz++) {
                    for (int dy = -4; dy <= 4; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos nextPos = current.add(dx, dy, dz);
                        if (visited.contains(nextPos)) continue;

                        BlockState nextState = world.getBlockState(nextPos);
                        Block nextBlock = nextState.getBlock();
                        if (nextBlock instanceof ElectricPoleBlock && !ElectricPoleBlock.isMasterPoleNode(nextState)) {
                            continue;
                        }
                        if (nextBlock instanceof ElectricPoleBlock || nextBlock instanceof CreativeEnergySourceBlock) {
                            double maxWire = Math.min(reach, getWireReach(nextBlock));
                            if (current.getSquaredDistance(nextPos) <= maxWire * maxWire) {
                                if (nextBlock instanceof CreativeEnergySourceBlock) {
                                    return true;
                                }
                                visited.add(nextPos.toImmutable());
                                queue.addLast(nextPos.toImmutable());
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public static boolean hasActiveGeneratorNearPole(World world, BlockPos polePos, int radius, boolean allowAccumulators) {
        int r = Math.max(2, radius);
        boolean solarActiveNow = SolarAndAccumulatorMachines.SolarPanelBlockEntity.getCurrentOutputWatts(world) > 0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = polePos.add(dx, dy, dz);
                    BlockState st = world.getBlockState(p);
                    Block b = st.getBlock();

                    if (b instanceof SteamEngineBlock) {
                        BlockPos master = SteamEngineBlock.getMasterPos(p, st);
                        if (world.getBlockEntity(master) instanceof SteamEngineBlockEntity engine && engine.hasSteamAvailable()) {
                            return true;
                        }
                    } else if (solarActiveNow && b instanceof SolarAndAccumulatorMachines.SolarPanelBlock) {
                        return true;
                    } else if (allowAccumulators && b instanceof SolarAndAccumulatorMachines.AccumulatorBlock) {
                        BlockPos master = SolarAndAccumulatorMachines.AccumulatorBlock.getMasterPos(p, st);
                        if (world.getBlockEntity(master) instanceof SolarAndAccumulatorMachines.AccumulatorBlockEntity acc && acc.canProvidePower()) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static double getWireReach(Block block) {
        if (block instanceof ElectricPoleBlock pole) return pole.getWireReach();
        if (block instanceof CreativeEnergySourceBlock) return 10.5;
        return 0.0;
    }

    public static int getSupplyRadius(Block block) {
        if (block instanceof ElectricPoleBlock pole) return pole.getSupplyRadius();
        if (block instanceof CreativeEnergySourceBlock source) return source.getSupplyRadius();
        return 0;
    }

    public static List<BlockPos> findConnectedNodes(World world, BlockPos origin, Block originBlock) {
        List<BlockPos> result = new ArrayList<>();
        double reach = getWireReach(originBlock);
        if (reach <= 0.0) return result;

        int r = (int) Math.ceil(reach);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 4; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos check = origin.add(dx, dy, dz);
                    BlockState st = world.getBlockState(check);
                    Block b = st.getBlock();
                    if (b instanceof ElectricPoleBlock && !ElectricPoleBlock.isMasterPoleNode(st)) {
                        continue;
                    }
                    if (b instanceof ElectricPoleBlock || b instanceof CreativeEnergySourceBlock) {
                        double maxDist = Math.min(reach, getWireReach(b));
                        if (origin.getSquaredDistance(check) <= maxDist * maxDist) {
                            result.add(check.toImmutable());
                        }
                    }
                }
            }
        }
        return result;
    }
}