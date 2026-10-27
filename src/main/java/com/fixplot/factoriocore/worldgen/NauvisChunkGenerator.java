package com.fixplot.factoriocore.worldgen;

import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.PillarBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class NauvisChunkGenerator extends ChunkGenerator {

    public static final MapCodec<NauvisChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, instance.stable(NauvisChunkGenerator::new))
    );

    public static final int SEA_LEVEL = 63;
    public static final int MAIN_PLAIN_Y = 64;

    private static final long ELEV_MACRO_SALT = 104729L;
    private static final long ELEV_RIDGE_SALT = 218357L;
    private static final long CLIFF_ELEV_SALT = 334919L;
    private static final long CLIFF_CONTINUITY_SALT = 449173L;
    private static final long MOISTURE_SALT = 563209L;
    private static final long AUX_SALT = 679423L;
    private static final long FOREST_SALT = 791843L;
    private static final long DETAIL_SALT = 905117L;
    private static final long ORE_SALT = 1234577L;
    private static final long OIL_SALT = 1333799L;

    public enum NauvisBiome {
        LUSH_GRASS("Густые луга (Grass-1)"),
        DRY_GRASS("Степь (Grass-3)"),
        DRY_DIRT("Сухая земля (Dry Dirt)"),
        SAND_DESERT("Песчаная пустыня (Sand)"),
        RED_DESERT("Красная пустыня (Red Desert)");

        public final String displayName;

        NauvisBiome(String displayName) {
            this.displayName = displayName;
        }
    }

    public record OreSample(int oreType, int amount) {
    }

    public NauvisChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    public static double evaluateElevation(int x, int z) {
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        if (!cfg.waterEnabled || cfg.waterCoverage <= 0.02f) {
            return 0.35;
        }
        if (cfg.mapType == 1) { // Режим "Остров" (Island)
            double dist = Math.sqrt((double) x * x + (double) z * z);
            double islandRadius = 260.0 * Math.max(0.5, cfg.waterScale);
            if (dist > islandRadius * 1.35) return -0.45;
            double edgeFalloff = Math.max(0.0, (dist - islandRadius * 0.65) / (islandRadius * 0.55));
            double noise = NauvisNoise.warpedFbm(x, z, 3, 140.0, 35.0, NauvisConfig.seed(ELEV_MACRO_SALT)) * 0.25;
            return (0.32 + noise) - edgeFalloff * 0.85;
        }

        double scaleMul = Math.max(0.35, cfg.waterScale);
        long macroSeed = NauvisConfig.seed(ELEV_MACRO_SALT);
        long ridgeSeed = NauvisConfig.seed(ELEV_RIDGE_SALT);

        double macro = NauvisNoise.warpedFbm(x, z, 4, 320.0 * scaleMul, 65.0 * scaleMul, macroSeed);
        double detail = NauvisNoise.fbm(x, z, 2, 75.0 * scaleMul, macroSeed + 55L) * 0.06;
        double ridgePaths = NauvisNoise.ridgeNoise(x, z, 2, 260.0 * scaleMul, 45.0 * scaleMul, ridgeSeed);

        double waterBias = (1.0 - cfg.waterCoverage) * 0.38;
        double elevation = macro * 0.82 + detail + (ridgePaths * 0.18) + 0.09 + waterBias;

        double spawnLandCone = NauvisNoise.spotCone(x, z, 0.0, 0.0, 64.0, 14.0, macroSeed + 11L);
        if (spawnLandCone > 0.0) {
            elevation = MathHelper.lerp(spawnLandCone, elevation, 0.28);
        }

        double lakeAngle = NauvisNoise.rand01(0, 0, macroSeed + 99L) * Math.PI * 2.0;
        double lakeX = Math.cos(lakeAngle) * 54.0;
        double lakeZ = Math.sin(lakeAngle) * 54.0;

        double startLakeCone = NauvisNoise.spotCone(x, z, lakeX, lakeZ, 24.0, 6.0, macroSeed + 29L);
        if (startLakeCone > 0.0) {
            elevation -= startLakeCone * 0.65;
        }

        return elevation;
    }

    public static int evaluateCliffHeight(int x, int z, double waterElevation) {
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        if (cfg.cliffFreq <= 0.02f || (x * x + z * z) < 56 * 56 || waterElevation < 0.10) {
            return 0;
        }
        if (isNearAnyOilFieldCenter(x, z)) {
            return 0;
        }

        double waveScale = 290.0 / Math.max(0.35, cfg.cliffFreq);
        double cliffWave = NauvisNoise.warpedFbm(x, z, 3, waveScale, 44.0, NauvisConfig.seed(CLIFF_ELEV_SALT));
        double distFromIsoline = Math.abs(cliffWave - 0.18);

        double bandHalfWidth = 0.014 * Math.max(0.6, Math.min(1.3, cfg.cliffFreq));
        if (distFromIsoline > bandHalfWidth) {
            return 0;
        }

        double continuity = NauvisNoise.fbm(x, z, 2, 100.0, NauvisConfig.seed(CLIFF_CONTINUITY_SALT));
        double threshold = 0.42 - cfg.cliffContinuity * 0.30;
        if (continuity < threshold) {
            return 0;
        }

        double endTaper = MathHelper.clamp((continuity - threshold) * 4.2, 0.0, 1.0);
        double profile = 1.0 - (distFromIsoline / bandHalfWidth);

        if (endTaper < 0.24 || profile < 0.22) {
            return 2;
        }
        if (profile < 0.45) {
            return 4;
        }
        return (endTaper > 0.62 && profile > 0.68) ? 6 : 5;
    }

    public static double evaluateMoisture(int x, int z) {
        long mSeed = NauvisConfig.seed(MOISTURE_SALT);
        double macro = NauvisNoise.warpedFbm(x, z, 4, 420.0, 75.0, mSeed);
        double detail = NauvisNoise.fbm(x, z, 2, 45.0, mSeed + 77L) * 0.08;
        return MathHelper.clamp((macro + 1.0) * 0.5 + detail + NauvisConfig.ACTIVE.moistureBias, 0.0, 1.0);
    }

    public static double evaluateAux(int x, int z) {
        long aSeed = NauvisConfig.seed(AUX_SALT);
        double macro = NauvisNoise.warpedFbm(x, z, 4, 380.0, 65.0, aSeed);
        double detail = NauvisNoise.fbm(x, z, 2, 45.0, aSeed + 99L) * 0.08;
        return MathHelper.clamp((macro + 1.0) * 0.5 + detail + NauvisConfig.ACTIVE.auxBias, 0.0, 1.0);
    }

    public static NauvisBiome getBiomeAt(int x, int z) {
        long dSeed = NauvisConfig.seed(DETAIL_SALT);
        double jitter = (NauvisNoise.rand01(x, z, dSeed + 123L) - 0.5) * 0.035;
        double m = evaluateMoisture(x, z) + jitter;
        double a = evaluateAux(x, z) + jitter;

        if (m >= 0.36) {
            return (m >= 0.62) ? NauvisBiome.LUSH_GRASS : NauvisBiome.DRY_GRASS;
        } else if (m >= 0.22) {
            return NauvisBiome.DRY_DIRT;
        } else {
            return (a >= 0.50) ? NauvisBiome.RED_DESERT : NauvisBiome.SAND_DESERT;
        }
    }

    public static boolean hasForestAt(int x, int z, double elev) {
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        if (cfg.treeCoverage <= 0.02f || (x * x + z * z) < 16 * 16 || elev < 0.05) {
            return false;
        }
        if (isNearAnyOilFieldCenter(x, z)) {
            return false;
        }
        double moisture = evaluateMoisture(x, z);
        double forestNoise = NauvisNoise.warpedFbm(x, z, 3, 80.0, 18.0, NauvisConfig.seed(FOREST_SALT));
        double threshold = (0.58 - moisture * 0.62) - (cfg.treeCoverage - 1.0) * 0.28;
        return forestNoise > threshold;
    }

    // =================================================================================
    // ГЕНЕРАЦИЯ МЕСТОРОЖДЕНИЙ СЫРОЙ НЕФТИ (КАНОН FACTORIO — БЕЗ СТАРТОВОГО ПОЛЯ У СПАВНА)
    // =================================================================================

    /**
     * Проверяет близость к нефтяному полю только для защиты скважин от зарослей деревьев и скал.
     * Рудные жилы НЕ режет!
     */
    public static boolean isNearAnyOilFieldCenter(int x, int z) {
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        if (cfg.oilFreq <= 0.02f || cfg.oilSize <= 0.02f) return false;
        if (x * x + z * z < 130 * 130) return false;

        long oilSeed = NauvisConfig.seed(OIL_SALT);
        int cellX = Math.floorDiv(x, 144);
        int cellZ = Math.floorDiv(z, 144);
        double chance = Math.min(0.85, 0.40 * cfg.oilFreq);
        if (NauvisNoise.rand01(cellX, cellZ, oilSeed) > chance) return false;

        int centerX = cellX * 144 + 36 + (int) (NauvisNoise.rand01(cellX, cellZ, oilSeed + 1L) * 72.0);
        int centerZ = cellZ * 144 + 36 + (int) (NauvisNoise.rand01(cellX, cellZ, oilSeed + 2L) * 72.0);
        if (centerX * centerX + centerZ * centerZ < 140 * 140) return false;

        return Math.abs(x - centerX) <= 16 && Math.abs(z - centerZ) <= 16;
    }

    public static boolean hasCrudeOilSpotAt(int x, int z, double elevation) {
        return hasCrudeOilPreviewAt(x, z, elevation, 0);
    }

    /**
     * В Factorio сырая нефть никогда не спавнится в стартовой зоне —
     * первые нефтяные поля появляются на дистанции >= 140 блоков от (0, 0).
     */
    public static boolean hasCrudeOilPreviewAt(int x, int z, double elevation, int toleranceRadius) {
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        if (!cfg.oilEnabled || cfg.oilFreq <= 0.02f || cfg.oilSize <= 0.02f || elevation < 0.05 ) {
            return false;
        }

        if (x * x + z * z < 125 * 125) {
            return false;
        }

        long oilSeed = NauvisConfig.seed(OIL_SALT);
        int cellX = Math.floorDiv(x, 144);
        int cellZ = Math.floorDiv(z, 144);
        double chance = Math.min(0.88, 0.40 * cfg.oilFreq);
        if (NauvisNoise.rand01(cellX, cellZ, oilSeed) > chance) {
            return false;
        }

        int centerX = cellX * 144 + 36 + (int) (NauvisNoise.rand01(cellX, cellZ, oilSeed + 1L) * 72.0);
        int centerZ = cellZ * 144 + 36 + (int) (NauvisNoise.rand01(cellX, cellZ, oilSeed + 2L) * 72.0);
        if (centerX * centerX + centerZ * centerZ < 140 * 140) {
            return false;
        }

        if (Math.abs(x - centerX) > 16 + toleranceRadius || Math.abs(z - centerZ) > 16 + toleranceRadius) {
            return false;
        }

        int baseWells = 3 + (int) Math.round(NauvisNoise.rand01(cellX, cellZ, oilSeed + 3L) * 4.5 * cfg.oilSize);
        int wellCount = MathHelper.clamp(baseWells, 2, 9);
        long fieldSalt = NauvisNoise.hash2D(cellX, cellZ, oilSeed + 55L);

        return matchesWellInField(x, z, centerX, centerZ, wellCount, fieldSalt, toleranceRadius);
    }

    private static boolean matchesWellInField(int x, int z, int centerX, int centerZ, int wellCount, long fieldSalt, int toleranceRadius) {
        int[] gridOrder = { 4, 0, 2, 6, 8, 1, 3, 5, 7 };
        for (int i = 0; i < Math.min(9, wellCount); i++) {
            int slot = gridOrder[i];
            int gx = (slot % 3) - 1;
            int gz = (slot / 3) - 1;

            int jitterX = (int) Math.round((NauvisNoise.rand01(slot, i, fieldSalt + 10L + i) - 0.5) * 2.0);
            int jitterZ = (int) Math.round((NauvisNoise.rand01(i, slot, fieldSalt + 30L + i) - 0.5) * 2.0);

            int wx = centerX + gx * 7 + jitterX;
            int wz = centerZ + gz * 7 + jitterZ;
            if (Math.abs(x - wx) <= toleranceRadius && Math.abs(z - wz) <= toleranceRadius) {
                return true;
            }
        }
        return false;
    }

    public static int evaluateOilYieldPercentAt(int x, int z) {
        long oilSeed = NauvisConfig.seed(OIL_SALT);
        double distFromOrigin = Math.sqrt((double) x * x + (double) z * z);
        double distanceMultiplier = 1.0 + Math.pow(distFromOrigin / 400.0, 1.15);
        double randFactor = 0.80 + NauvisNoise.rand01(x, z, oilSeed + 999L) * 0.65;
        double yield = 165.0 * NauvisConfig.ACTIVE.oilRichness * distanceMultiplier * randFactor;
        return Math.max(50, (int) Math.round(yield));
    }

    public static BlockState evaluateOrePatch(int x, int z, double elevation) {
        // Сначала проверяем рудные жилы, чтобы нефть никогда не разрезала и не перезаписывала руду!
        OreSample sample = sampleOreAt(x, z, elevation);
        if (sample != null) {
            return getOreBlockForType(sample.oreType());
        }
        if (hasCrudeOilSpotAt(x, z, elevation)) {
            return ModBlocks.CRUDE_OIL_DEPOSIT.getDefaultState();
        }
        return null;
    }

    public static int evaluateOreAmountAt(int x, int z, int oreType) {
        double elev = evaluateElevation(x, z);
        OreSample sample = sampleOreAt(x, z, elev);
        if (sample != null) {
            return sample.amount();
        }
        return 250;
    }

    /**
     * Вычисляет рудные пятна без жёстких поблочных границ отсечения —
     * ни одно пятно больше никогда не будет разрезано пополам!
     */
    public static OreSample sampleOreAt(int x, int z, double elevation) {
        if (elevation < 0.03) {
            return null;
        }
        NauvisConfig cfg = NauvisConfig.ACTIVE;
        long baseOreSeed = NauvisConfig.seed(ORE_SALT);

        float[] freqs = {
                cfg.ironEnabled ? cfg.ironFreq : 0.0f,
                cfg.copperEnabled ? cfg.copperFreq : 0.0f,
                cfg.coalEnabled ? cfg.coalFreq : 0.0f,
                cfg.stoneEnabled ? cfg.stoneFreq : 0.0f
        };
        float[] sizes = { cfg.ironSize, cfg.copperSize, cfg.coalSize, cfg.stoneSize };
        float[] rich  = { cfg.ironRichness, cfg.copperRichness, cfg.coalRichness, cfg.stoneRichness };
        double[] baseRadiusWeights = { 1.08, 1.04, 0.96, 0.82 };

        int distSq = x * x + z * z;

        // 1. Стартовые пятна руды вокруг спавна (проверяем с большим запасом до 105 блоков, чтобы края конусов не обрезались!)
        if (distSq > 9 * 9 && distSq < 105 * 105) {
            double startRot = NauvisNoise.rand01(0, 0, baseOreSeed + 77L) * Math.PI * 2.0;
            double spread = 0.78 + NauvisNoise.rand01(1, 1, baseOreSeed + 88L) * 0.35;

            int bestType = -1;
            double bestSignal = 0.0;

            for (int type = 0; type < 4; type++) {
                if (freqs[type] <= 0.02f || sizes[type] <= 0.02f) continue;

                double angleJitter = (NauvisNoise.rand01(type * 13, 2, baseOreSeed + 99L) - 0.5) * 1.15;
                double angle = startRot + type * (Math.PI * 0.5) + angleJitter;
                double dist = (22.0 + NauvisNoise.rand01(type * 29, 3, baseOreSeed + 111L) * 16.0) * spread;

                double cx = Math.cos(angle) * dist;
                double cz = Math.sin(angle) * dist;
                double radius = 10.5 * baseRadiusWeights[type] * Math.sqrt(sizes[type]);

                double signal = evaluateSpotNoiseCone(x, z, cx, cz, radius, baseOreSeed + type * 53L);
                if (signal > bestSignal) {
                    bestSignal = signal;
                    bestType = type;
                }
            }

            if (bestType >= 0) {
                int amount = computeSpotRichness(bestSignal, rich[bestType], 0.0, true);
                return new OreSample(bestType, amount);
            }
        }

        // 2. Региональные рудные месторождения (ограничиваем ТОЛЬКО центры пятен >= 96 блоков от спавна, не обрезая сами края!)
        final int regionSize = 192;
        int regX = Math.floorDiv(x, regionSize);
        int regZ = Math.floorDiv(z, regionSize);

        int bestType = -1;
        double bestSignal = 0.0;
        double bestDistFromOrigin = 0.0;

        for (int rx = regX - 1; rx <= regX + 1; rx++) {
            for (int rz = regZ - 1; rz <= regZ + 1; rz++) {
                long regionSeed = NauvisNoise.hash2D(rx, rz, baseOreSeed + 40409L);

                double[] placedX = new double[8];
                double[] placedZ = new double[8];
                double[] placedR = new double[8];
                int placedCount = 0;

                for (int type = 0; type < 4; type++) {
                    if (freqs[type] <= 0.02f || sizes[type] <= 0.02f) continue;

                    double expectedSpots = 0.65 * freqs[type];
                    int maxSpots = (expectedSpots >= 1.0) ? 2 : 1;

                    for (int s = 0; s < maxSpots; s++) {
                        long spotSalt = regionSeed + type * 7919L + s * 1301L;
                        double roll = NauvisNoise.rand01(rx + s * 17, rz - type * 23, spotSalt);
                        double prob = (s == 0) ? Math.min(0.92, expectedSpots) : Math.max(0.0, expectedSpots - 0.85);
                        if (roll > prob) {
                            continue;
                        }

                        double radius = MathHelper.clamp(13.5 * baseRadiusWeights[type] * Math.sqrt(sizes[type]), 5.0, 32.0);
                        double margin = radius + 6.0;
                        double span = Math.max(16.0, regionSize - 2.0 * margin);

                        double cx = rx * regionSize + margin + NauvisNoise.rand01(rx, rz, spotSalt + 1L) * span;
                        double cz = rz * regionSize + margin + NauvisNoise.rand01(rx, rz, spotSalt + 2L) * span;

                        if (cx * cx + cz * cz < 98 * 98) {
                            continue;
                        }

                        boolean overlapsOther = false;
                        for (int p = 0; p < placedCount; p++) {
                            double dx = cx - placedX[p];
                            double dz = cz - placedZ[p];
                            double minSep = radius + placedR[p] + 8.0;
                            if (dx * dx + dz * dz < minSep * minSep) {
                                overlapsOther = true;
                                break;
                            }
                        }
                        if (overlapsOther) {
                            continue;
                        }

                        if (placedCount < placedX.length) {
                            placedX[placedCount] = cx;
                            placedZ[placedCount] = cz;
                            placedR[placedCount] = radius;
                            placedCount++;
                        }

                        double signal = evaluateSpotNoiseCone(x, z, cx, cz, radius, spotSalt + 3L);
                        if (signal > bestSignal) {
                            bestSignal = signal;
                            bestType = type;
                            bestDistFromOrigin = Math.sqrt(cx * cx + cz * cz);
                        }
                    }
                }
            }
        }

        if (bestType >= 0) {
            int amount = computeSpotRichness(bestSignal, rich[bestType], bestDistFromOrigin, false);
            return new OreSample(bestType, amount);
        }

        return null;
    }

    private static double evaluateSpotNoiseCone(int x, int z, double cx, double cz, double radius, long salt) {
        double maxBound = radius * 1.45;
        if (Math.abs(x - cx) > maxBound || Math.abs(z - cz) > maxBound) {
            return 0.0;
        }

        double warpScale = Math.max(10.0, radius * 0.75);
        double wx = x + NauvisNoise.fbm(x, z, 2, warpScale, salt + 11L) * (radius * 0.24);
        double wz = z + NauvisNoise.fbm(x + 193.0, z - 157.0, 2, warpScale, salt + 23L) * (radius * 0.24);

        double angle = NauvisNoise.rand01((int) cx, (int) cz, salt + 37L) * Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dx = wx - cx;
        double dz = wz - cz;

        double rotX = (dx * cos - dz * sin) * 0.86;
        double rotZ = (dx * sin + dz * cos) * 1.14;

        double dist = Math.sqrt(rotX * rotX + rotZ * rotZ);
        double cone = 1.0 - (dist / radius);
        if (cone <= -0.25) {
            return 0.0;
        }

        double basisNoise = NauvisNoise.fbm(wx, wz, 2, Math.max(7.0, radius * 0.50), salt + 59L) * 0.22;
        double rawHeight = cone + basisNoise;

        double cutoff = 0.24;
        if (rawHeight <= cutoff) {
            return 0.0;
        }

        return MathHelper.clamp((rawHeight - cutoff) / (1.0 - cutoff), 0.0, 1.25);
    }

    private static int computeSpotRichness(double signal, float richnessSlider, double distFromOrigin, boolean isStarter) {
        double centerGradient = 0.15 + Math.pow(signal, 1.35) * 1.85;
        double distanceBonus = isStarter ? 0.85 : (1.0 + Math.pow(distFromOrigin / 360.0, 1.18));
        double baseAmount = 520.0 * centerGradient * richnessSlider * distanceBonus;
        return Math.max(35, (int) Math.round(baseAmount));
    }

    private static BlockState getOreBlockForType(int type) {
        return switch (type) {
            case 0 -> ModBlocks.IRON_ORE_DEPOSIT.getDefaultState();
            case 1 -> ModBlocks.COPPER_ORE_DEPOSIT.getDefaultState();
            case 2 -> ModBlocks.COAL_DEPOSIT.getDefaultState();
            default -> ModBlocks.STONE_DEPOSIT.getDefaultState();
        };
    }

    public static int getSurfaceY(int x, int z) {
        double elev = evaluateElevation(x, z);

        if (elev < 0.0) {
            if (elev > -0.09) {
                return SEA_LEVEL - 1;
            }
            int depth = MathHelper.clamp(2 + (int) ((-0.09 - elev) * 10.0), 2, 5);
            return SEA_LEVEL - depth;
        }

        int cliffH = evaluateCliffHeight(x, z, elev);
        if (cliffH > 0) {
            return MAIN_PLAIN_Y + cliffH;
        }

        return MAIN_PLAIN_Y;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig,
                                                  StructureAccessor structureAccessor, Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int wx = startX + localX;
                int wz = startZ + localZ;

                double elev = evaluateElevation(wx, wz);
                int surfaceY = getSurfaceY(wx, wz);
                int cliffH = evaluateCliffHeight(wx, wz, elev);
                NauvisBiome biome = getBiomeAt(wx, wz);

                chunk.setBlockState(pos.set(localX, -64, localZ), Blocks.BEDROCK.getDefaultState(), false);
                for (int y = -63; y <= 0; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ), Blocks.DEEPSLATE.getDefaultState(), false);
                }

                for (int y = 1; y <= MAIN_PLAIN_Y - 4; y++) {
                    chunk.setBlockState(pos.set(localX, y, localZ), Blocks.STONE.getDefaultState(), false);
                }

                if (surfaceY < SEA_LEVEL) {
                    BlockState lakeBed = selectLakeBedBlock(biome);
                    for (int y = surfaceY - 3; y <= surfaceY; y++) {
                        chunk.setBlockState(pos.set(localX, y, localZ), lakeBed, false);
                    }
                    for (int y = surfaceY + 1; y <= SEA_LEVEL; y++) {
                        chunk.setBlockState(pos.set(localX, y, localZ), Blocks.WATER.getDefaultState(), false);
                    }
                } else if (cliffH > 0) {
                    for (int y = MAIN_PLAIN_Y - 3; y <= surfaceY; y++) {
                        chunk.setBlockState(pos.set(localX, y, localZ), chooseRockBlock(biome, wx, y, wz), false);
                    }
                } else {
                    boolean isWaterEdge = (elev < 0.025);
                    BlockState oreState = evaluateOrePatch(wx, wz, elev);
                    BlockState topState = (oreState != null)
                            ? oreState
                            : selectSurfaceBlock(biome, isWaterEdge, wx, wz);
                    BlockState underState = selectUnderBlock(biome);

                    for (int y = MAIN_PLAIN_Y - 3; y < MAIN_PLAIN_Y; y++) {
                        chunk.setBlockState(pos.set(localX, y, localZ), underState, false);
                    }
                    BlockPos surfaceBlockPos = new BlockPos(wx, MAIN_PLAIN_Y, wz);
                    chunk.setBlockState(pos.set(localX, MAIN_PLAIN_Y, localZ), topState, false);
                    if (topState.isOf(ModBlocks.CRUDE_OIL_DEPOSIT)) {
                        chunk.setBlockEntity(new OilMachines.CrudeOilDepositBlockEntity(surfaceBlockPos, topState));
                    }
                }
            }
        }

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
    }

    private static BlockState chooseRockBlock(NauvisBiome biome, int x, int y, int z) {
        double r = NauvisNoise.rand01(x + y * 5, z - y * 11, NauvisConfig.seed(DETAIL_SALT));

        if (y <= MAIN_PLAIN_Y + 1) {
            return (r < 0.55) ? Blocks.TUFF.getDefaultState() : Blocks.DEEPSLATE.getDefaultState();
        }

        if (r < 0.55) return Blocks.DRIPSTONE_BLOCK.getDefaultState();
        if (r < 0.82) return Blocks.GRANITE.getDefaultState();
        return Blocks.TUFF.getDefaultState();
    }

    private static BlockState selectLakeBedBlock(NauvisBiome biome) {
        return switch (biome) {
            case LUSH_GRASS, DRY_GRASS -> Blocks.CLAY.getDefaultState();
            case DRY_DIRT -> Blocks.MUD.getDefaultState();
            case SAND_DESERT -> Blocks.SAND.getDefaultState();
            case RED_DESERT -> Blocks.RED_SAND.getDefaultState();
        };
    }

    private static BlockState selectSurfaceBlock(NauvisBiome biome, boolean isWaterEdge, int x, int z) {
        long dSeed = NauvisConfig.seed(DETAIL_SALT);
        double subZone = NauvisNoise.warpedFbm(x, z, 2, 95.0, 20.0, dSeed + 505L)
                + (NauvisNoise.rand01(x, z, dSeed + 506L) - 0.5) * 0.12;
        double dripRoll = NauvisNoise.rand01(x, z, dSeed + 808L);

        BlockState mudBrickMix = (dripRoll < 0.18)
                ? Blocks.DRIPSTONE_BLOCK.getDefaultState()
                : Blocks.MUD_BRICKS.getDefaultState();

        return switch (biome) {
            case LUSH_GRASS -> (subZone > 0.25) ? Blocks.MOSS_BLOCK.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState();
            case DRY_GRASS -> Blocks.GRASS_BLOCK.getDefaultState();
            case DRY_DIRT -> (subZone > 0.25) ? mudBrickMix : Blocks.PACKED_MUD.getDefaultState();
            case SAND_DESERT -> (subZone > 0.35) ? Blocks.SAND.getDefaultState() : Blocks.PACKED_MUD.getDefaultState();
            case RED_DESERT -> (subZone > 0.15) ? mudBrickMix : Blocks.PACKED_MUD.getDefaultState();
        };
    }

    private static BlockState selectUnderBlock(NauvisBiome biome) {
        return switch (biome) {
            case LUSH_GRASS, DRY_GRASS, DRY_DIRT -> Blocks.DIRT.getDefaultState();
            case SAND_DESERT -> Blocks.SANDSTONE.getDefaultState();
            case RED_DESERT -> Blocks.RED_SANDSTONE.getDefaultState();
        };
    }

    @Override
    public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;
        int startX = chunkPos.getStartX();
        int startZ = chunkPos.getStartZ();
        long dSeed = NauvisConfig.seed(DETAIL_SALT);

        // 1. Валуны Наувиса
        double boulderRoll = NauvisNoise.rand01(chunkX, chunkZ, dSeed + 777L);
        double boulderThreshold = 0.045 * NauvisConfig.ACTIVE.boulderFreq;
        if (boulderRoll < boulderThreshold) {
            int bx = startX + 4 + (int) (NauvisNoise.rand01(chunkX, chunkZ, dSeed + 1L) * 8.0);
            int bz = startZ + 4 + (int) (NauvisNoise.rand01(chunkX, chunkZ, dSeed + 2L) * 8.0);
            double elev = evaluateElevation(bx, bz);

            if (elev > 0.06 && evaluateCliffHeight(bx, bz, elev) == 0
                    && evaluateOrePatch(bx, bz, elev) == null
                    && !isNearAnyOilFieldCenter(bx, bz)) {
                int sizeTier = (boulderRoll < boulderThreshold * 0.33) ? 2 : 1;
                generateNauvisBoulder(region, new BlockPos(bx, MAIN_PLAIN_Y + 1, bz), sizeTier, dSeed);
            }
        }

        // 2. Лесные массивы и сухие коряги
        for (int cellX = 0; cellX < 4; cellX++) {
            for (int cellZ = 0; cellZ < 4; cellZ++) {
                int tx = startX + cellX * 4 + 1 + (int) (NauvisNoise.rand01(startX + cellX, startZ + cellZ, dSeed + 19L) * 2.0);
                int tz = startZ + cellZ * 4 + 1 + (int) (NauvisNoise.rand01(startX + cellX, startZ + cellZ, dSeed + 23L) * 2.0);

                double elev = evaluateElevation(tx, tz);
                if (elev < 0.05 || evaluateCliffHeight(tx, tz, elev) > 0 || evaluateOrePatch(tx, tz, elev) != null) {
                    continue;
                }

                if (hasForestAt(tx, tz, elev)) {
                    double moisture = evaluateMoisture(tx, tz);
                    double treeRoll = NauvisNoise.rand01(tx, tz, dSeed + 333L);
                    NauvisBiome biome = getBiomeAt(tx, tz);

                    if (moisture >= 0.45 && treeRoll < 0.48) {
                        generateLivingNauvisTree(region, new BlockPos(tx, MAIN_PLAIN_Y + 1, tz), tx, tz, dSeed);
                    } else if (moisture < 0.45 && treeRoll < 0.09) {
                        generateDeadTreeTrunk(region, new BlockPos(tx, MAIN_PLAIN_Y + 1, tz), biome, tx, tz, dSeed);
                    }
                }
            }
        }

        // 3. Растительность на суше и в озёрах
        BlockPos.Mutable decoPos = new BlockPos.Mutable();
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int wx = startX + lx;
                int wz = startZ + lz;
                double elev = evaluateElevation(wx, wz);
                int surfaceY = getSurfaceY(wx, wz);
                double r = NauvisNoise.rand01(wx, wz, dSeed + 999L);
                NauvisBiome biome = getBiomeAt(wx, wz);

                if (surfaceY < SEA_LEVEL) {
                    if (r < 0.22) {
                        decoPos.set(wx, surfaceY + 1, wz);
                        if (region.getBlockState(decoPos).isOf(Blocks.WATER)) {
                            region.setBlockState(decoPos, Blocks.SEAGRASS.getDefaultState(), 2);
                        }
                    }
                    if ((biome == NauvisBiome.LUSH_GRASS || biome == NauvisBiome.DRY_GRASS)
                            && surfaceY == SEA_LEVEL - 1 && r > 0.94) {
                        decoPos.set(wx, SEA_LEVEL + 1, wz);
                        if (region.getBlockState(decoPos).isAir()) {
                            region.setBlockState(decoPos, Blocks.LILY_PAD.getDefaultState(), 2);
                        }
                    }
                    continue;
                }

                if (elev < 0.03 || evaluateCliffHeight(wx, wz, elev) > 0
                        || evaluateOrePatch(wx, wz, elev) != null) {
                    continue;
                }

                decoPos.set(wx, MAIN_PLAIN_Y + 1, wz);
                if (!region.getBlockState(decoPos).isAir()) {
                    continue;
                }

                BlockState ground = region.getBlockState(decoPos.down());
                if ((biome == NauvisBiome.LUSH_GRASS || biome == NauvisBiome.DRY_GRASS) && ground.isOf(Blocks.GRASS_BLOCK)) {
                    if (r < 0.09) {
                        region.setBlockState(decoPos, Blocks.SHORT_GRASS.getDefaultState(), 2);
                    } else if (biome == NauvisBiome.LUSH_GRASS && r < 0.115) {
                        region.setBlockState(decoPos, Blocks.FERN.getDefaultState(), 2);
                    }
                } else if ((biome == NauvisBiome.SAND_DESERT || biome == NauvisBiome.RED_DESERT || biome == NauvisBiome.DRY_DIRT) && r < 0.018) {
                    if (ground.isOf(Blocks.SAND) || ground.isOf(Blocks.PACKED_MUD) || ground.isOf(Blocks.MUD_BRICKS)) {
                        region.setBlockState(decoPos, Blocks.DEAD_BUSH.getDefaultState(), 2);
                    }
                }
            }
        }
    }

    private void generateNauvisBoulder(ChunkRegion region, BlockPos center, int sizeTier, long dSeed) {
        int radiusXZ = sizeTier;
        int height = sizeTier + 1;

        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                for (int dy = 0; dy < height; dy++) {
                    double normDist = (dx * dx + dz * dz) / (radiusXZ * radiusXZ + 0.5)
                            + (dy * dy) / (height * height + 0.5);
                    double jitter = NauvisNoise.rand01(center.getX() + dx * 3, center.getZ() + dz * 5 + dy, dSeed) * 0.30;

                    if (normDist + jitter <= 0.92) {
                        p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                        double r = NauvisNoise.rand01(p.getX(), p.getZ(), dSeed + 77L);
                        BlockState boulderBlock = (r < 0.50) ? Blocks.ANDESITE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState();
                        region.setBlockState(p, boulderBlock, 2);
                    }
                }
            }
        }
    }

    private void generateLivingNauvisTree(ChunkRegion region, BlockPos base, int wx, int wz, long dSeed) {
        boolean spruceStyle = NauvisNoise.rand01(wx, wz, dSeed + 12L) < 0.45;
        BlockState log = (spruceStyle ? Blocks.SPRUCE_LOG : Blocks.OAK_LOG).getDefaultState();
        BlockState leaves = (spruceStyle ? Blocks.SPRUCE_LEAVES : Blocks.OAK_LEAVES).getDefaultState()
                .with(LeavesBlock.PERSISTENT, true);

        int height = 5 + (int) (NauvisNoise.rand01(wx, wz, dSeed + 15L) * 3.0);

        for (int y = 0; y < height; y++) {
            region.setBlockState(base.up(y), log, 2);
        }

        for (int dy = height - 3; dy <= height + 1; dy++) {
            int radius = (dy >= height) ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && dy >= height - 1) {
                        continue;
                    }
                    BlockPos leafPos = base.add(dx, dy, dz);
                    if (region.getBlockState(leafPos).isAir()) {
                        region.setBlockState(leafPos, leaves, 2);
                    }
                }
            }
        }
    }

    private void generateDeadTreeTrunk(ChunkRegion region, BlockPos base, NauvisBiome biome, int wx, int wz, long dSeed) {
        BlockState trunk = (biome == NauvisBiome.RED_DESERT
                ? Blocks.STRIPPED_DARK_OAK_LOG
                : Blocks.STRIPPED_OAK_LOG).getDefaultState();

        int height = 3 + (int) (NauvisNoise.rand01(wx, wz, dSeed + 44L) * 3.0);
        for (int y = 0; y < height; y++) {
            region.setBlockState(base.up(y), trunk, 2);
        }

        Direction branchDir = Direction.Type.HORIZONTAL.random(
                net.minecraft.util.math.random.Random.create(NauvisNoise.hash2D(wx, wz, dSeed + 88L)));
        BlockPos branchPos = base.up(height - 1).offset(branchDir);
        region.setBlockState(branchPos, trunk.with(PillarBlock.AXIS, branchDir.getAxis()), 2);
    }

    @Override
    public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess,
                      StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep) {
    }

    @Override
    public void populateEntities(ChunkRegion region) {
    }

    @Override
    public int getWorldHeight() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return SEA_LEVEL;
    }

    @Override
    public int getMinimumY() {
        return -64;
    }

    @Override
    public int getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
        int surfaceY = getSurfaceY(x, z);
        if (heightmap == Heightmap.Type.OCEAN_FLOOR_WG || heightmap == Heightmap.Type.OCEAN_FLOOR) {
            return surfaceY + 1;
        }
        return Math.max(surfaceY, SEA_LEVEL) + 1;
    }

    @Override
    public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
        int minY = getMinimumY();
        int height = getWorldHeight();
        BlockState[] states = new BlockState[height];
        int surfaceY = getSurfaceY(x, z);

        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y == minY) {
                states[i] = Blocks.BEDROCK.getDefaultState();
            } else if (y <= surfaceY) {
                states[i] = Blocks.STONE.getDefaultState();
            } else if (y <= SEA_LEVEL) {
                states[i] = Blocks.WATER.getDefaultState();
            } else {
                states[i] = Blocks.AIR.getDefaultState();
            }
        }
        return new VerticalBlockSample(minY, states);
    }

    @Override
    public void getDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
        NauvisBiome biome = getBiomeAt(pos.getX(), pos.getZ());
        double m = evaluateMoisture(pos.getX(), pos.getZ());
        double a = evaluateAux(pos.getX(), pos.getZ());
        text.add(String.format("[FactorioCore] Seed: %d | Biome: %s (M: %.2f, Aux: %.2f)",
                NauvisConfig.ACTIVE.worldSeed, biome.displayName, m, a));
    }
}