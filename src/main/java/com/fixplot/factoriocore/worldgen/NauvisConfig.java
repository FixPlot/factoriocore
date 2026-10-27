package com.fixplot.factoriocore.worldgen;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.File;
import java.nio.file.Path;
import java.util.Random;

public class NauvisConfig {

    public static NauvisConfig ACTIVE = new NauvisConfig();

    public long worldSeed = new Random().nextLong();
    // 0 = По умолчанию, 1 = Богатые ресурсы, 2 = Мир смерти, 3 = Железнодорожный мир, 4 = Остров
    public int presetIndex = 0;

    // === ВКЛАДКА 0: РЕСУРСЫ (Частота, Размер, Богатство + чекбоксы включения) ===
    public boolean ironEnabled = true;
    public float ironFreq = 1.0f, ironSize = 1.0f, ironRichness = 1.0f;

    public boolean copperEnabled = true;
    public float copperFreq = 1.0f, copperSize = 1.0f, copperRichness = 1.0f;

    public boolean stoneEnabled = true;
    public float stoneFreq = 1.0f, stoneSize = 1.0f, stoneRichness = 1.0f;

    public boolean coalEnabled = true;
    public float coalFreq = 1.0f, coalSize = 1.0f, coalRichness = 1.0f;

    public boolean oilEnabled = true;
    public float oilFreq = 1.0f, oilSize = 1.0f, oilRichness = 1.0f;

    // === ВКЛАДКА 1: ЛАНДШАФТ ===
    public int mapType = 0; // 0 = Уровень моря как на Наувисе, 1 = Остров
    public boolean waterEnabled = true;
    public float waterScale = 1.0f, waterCoverage = 1.0f;

    public boolean treesEnabled = true;
    public float treeScale = 1.0f, treeCoverage = 1.0f;

    public boolean rocksEnabled = true;
    public float boulderFreq = 1.0f, boulderSize = 1.0f;

    public boolean startingMoistureEnabled = true;
    public float startingMoisture = 1.0f;

    public boolean cliffsEnabled = true;
    public float cliffFreq = 1.0f, cliffContinuity = 1.0f;

    public float moistureScale = 1.0f, moistureBias = 0.0f;
    public float auxScale = 1.0f, auxBias = 0.0f;

    // === ВКЛАДКА 2: ВРАГ (КУСАКИ, БАЗЫ, ЭКСПАНСИЯ, ЭВОЛЮЦИЯ) ===
    public boolean enemyBasesEnabled = true;
    public float enemyBaseFreq = 1.0f, enemyBaseSize = 1.0f;
    public boolean noEnemies = false;
    public boolean peacefulMode = false;
    public float startingAreaSize = 1.0f; // 1.0 = 100% (радиус ~120 блоков)

    public boolean enemyExpansionEnabled = true;
    public int expansionMinDist = 3;
    public int expansionMaxDist = 5;
    public int expansionGroupSize = 8;
    public int expansionMinCooldownMin = 10;
    public int expansionMaxCooldownMin = 60;

    public boolean evolutionEnabled = true;
    public int evolutionTimeFactor = 40;
    public int evolutionDestroyFactor = 200;
    public int evolutionPollutionFactor = 9;

    // === ВКЛАДКА 3: ПРОДВИНУТЫЕ (КАРТА, ТЕХНОЛОГИИ, ЗАГРЯЗНЕНИЕ) ===
    public int mapWidthLimit = 0;  // 0 = бесконечная
    public int mapHeightLimit = 0;
    public int techPriceMultiplier = 1;

    public boolean pollutionEnabled = true;
    public float pollutionAbsorptionMod = 1.0f;    // 100%
    public float pollutionAttackCostMod = 1.0f;    // 100%
    public int pollutionMinTreeDamage = 60;        // 60 PU
    public int pollutionAbsorbedPerTree = 10;      // 10 PU
    public int pollutionDiffusionPercent = 2;      // 2%

    public static long seed(long salt) {
        long s = ACTIVE.worldSeed ^ salt;
        s = (s ^ (s >>> 30)) * 0xbf58476d1ce4e5b9L;
        s = (s ^ (s >>> 27)) * 0x94d049bb133111ebL;
        return s ^ (s >>> 31);
    }

    public void applyPreset(int index) {
        resetDefaults();
        this.presetIndex = index;
        switch (index) {
            case 1 -> { // Богатые ресурсы (Rich resources)
                ironRichness = 2.0f; copperRichness = 2.0f; coalRichness = 2.0f; stoneRichness = 2.0f; oilRichness = 2.0f;
                ironSize = 1.5f; copperSize = 1.5f;
            }
            case 2 -> { // Мир смерти (Death world)
                enemyBaseFreq = 2.0f; enemyBaseSize = 2.0f;
                startingAreaSize = 0.75f;
                evolutionTimeFactor = 200; evolutionPollutionFactor = 12;
                pollutionAbsorptionMod = 0.5f;
            }
            case 3 -> { // Железнодорожный мир (Rail world)
                ironFreq = 0.35f; copperFreq = 0.35f; coalFreq = 0.35f; stoneFreq = 0.35f; oilFreq = 0.5f;
                ironSize = 2.0f; copperSize = 2.0f; coalSize = 2.0f; stoneSize = 2.0f;
                ironRichness = 1.5f; copperRichness = 1.5f;
                enemyExpansionEnabled = false;
            }
            case 4 -> { // Остров (Island)
                mapType = 1;
                waterCoverage = 1.5f;
            }
        }
    }

    public void resetDefaults() {
        presetIndex = 0;
        ironEnabled = true; ironFreq = 1.0f; ironSize = 1.0f; ironRichness = 1.0f;
        copperEnabled = true; copperFreq = 1.0f; copperSize = 1.0f; copperRichness = 1.0f;
        stoneEnabled = true; stoneFreq = 1.0f; stoneSize = 1.0f; stoneRichness = 1.0f;
        coalEnabled = true; coalFreq = 1.0f; coalSize = 1.0f; coalRichness = 1.0f;
        oilEnabled = true; oilFreq = 1.0f; oilSize = 1.0f; oilRichness = 1.0f;

        mapType = 0;
        waterEnabled = true; waterScale = 1.0f; waterCoverage = 1.0f;
        treesEnabled = true; treeScale = 1.0f; treeCoverage = 1.0f;
        rocksEnabled = true; boulderFreq = 1.0f; boulderSize = 1.0f;
        startingMoistureEnabled = true; startingMoisture = 1.0f;
        cliffsEnabled = true; cliffFreq = 1.0f; cliffContinuity = 1.0f;
        moistureScale = 1.0f; moistureBias = 0.0f;
        auxScale = 1.0f; auxBias = 0.0f;

        enemyBasesEnabled = true; enemyBaseFreq = 1.0f; enemyBaseSize = 1.0f;
        noEnemies = false; peacefulMode = false;
        startingAreaSize = 1.0f;

        enemyExpansionEnabled = true;
        expansionMinDist = 3; expansionMaxDist = 5; expansionGroupSize = 8;
        expansionMinCooldownMin = 10; expansionMaxCooldownMin = 60;

        evolutionEnabled = true;
        evolutionTimeFactor = 40; evolutionDestroyFactor = 200; evolutionPollutionFactor = 9;

        mapWidthLimit = 0; mapHeightLimit = 0;
        techPriceMultiplier = 1;

        pollutionEnabled = true;
        pollutionAbsorptionMod = 1.0f; pollutionAttackCostMod = 1.0f;
        pollutionMinTreeDamage = 60; pollutionAbsorbedPerTree = 10; pollutionDiffusionPercent = 2;
    }

    public void randomizeSeed() {
        this.worldSeed = Math.abs(new Random().nextInt());
    }

    /**
     * Проверяет, есть ли в данной точке мира гнездо кусак (для карты предпросмотра и будущего спавна гнёзд!).
     */
    public static boolean hasEnemyBasePreviewAt(int x, int z, double elevation) {
        NauvisConfig cfg = ACTIVE;
        if (!cfg.enemyBasesEnabled || cfg.noEnemies || cfg.enemyBaseFreq <= 0.02f || cfg.enemyBaseSize <= 0.02f || elevation < 0.04) {
            return false;
        }
        double startRadius = 128.0 * Math.max(0.4, cfg.startingAreaSize);
        if (x * x + z * z < startRadius * startRadius) {
            return false;
        }
        long enemySeed = seed(9911773L);
        int cellX = Math.floorDiv(x, 128);
        int cellZ = Math.floorDiv(z, 128);
        double chance = Math.min(0.92, 0.45 * cfg.enemyBaseFreq);
        if (NauvisNoise.rand01(cellX, cellZ, enemySeed) > chance) {
            return false;
        }
        int cx = cellX * 128 + 32 + (int) (NauvisNoise.rand01(cellX, cellZ, enemySeed + 1L) * 64.0);
        int cz = cellZ * 128 + 32 + (int) (NauvisNoise.rand01(cellX, cellZ, enemySeed + 2L) * 64.0);
        if (cx * cx + cz * cz < startRadius * startRadius) {
            return false;
        }
        double baseRadius = 10.0 * Math.sqrt(cfg.enemyBaseSize);
        double cone = NauvisNoise.spotCone(x, z, cx, cz, baseRadius, 4.0, enemySeed + 3L);
        return cone > 0.35;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("WorldSeed", worldSeed);
        nbt.putInt("PresetIndex", presetIndex);

        nbt.putBoolean("IronEn", ironEnabled); nbt.putFloat("IronFreq", ironFreq); nbt.putFloat("IronSize", ironSize); nbt.putFloat("IronRich", ironRichness);
        nbt.putBoolean("CopperEn", copperEnabled); nbt.putFloat("CopperFreq", copperFreq); nbt.putFloat("CopperSize", copperSize); nbt.putFloat("CopperRich", copperRichness);
        nbt.putBoolean("CoalEn", coalEnabled); nbt.putFloat("CoalFreq", coalFreq); nbt.putFloat("CoalSize", coalSize); nbt.putFloat("CoalRich", coalRichness);
        nbt.putBoolean("StoneEn", stoneEnabled); nbt.putFloat("StoneFreq", stoneFreq); nbt.putFloat("StoneSize", stoneSize); nbt.putFloat("StoneRich", stoneRichness);
        nbt.putBoolean("OilEn", oilEnabled); nbt.putFloat("OilFreq", oilFreq); nbt.putFloat("OilSize", oilSize); nbt.putFloat("OilRich", oilRichness);

        nbt.putInt("MapType", mapType);
        nbt.putBoolean("WaterEn", waterEnabled); nbt.putFloat("WaterCoverage", waterCoverage); nbt.putFloat("WaterScale", waterScale);
        nbt.putBoolean("TreesEn", treesEnabled); nbt.putFloat("TreeScale", treeScale); nbt.putFloat("TreeCoverage", treeCoverage);
        nbt.putBoolean("RocksEn", rocksEnabled); nbt.putFloat("BoulderFreq", boulderFreq); nbt.putFloat("BoulderSize", boulderSize);
        nbt.putBoolean("CliffsEn", cliffsEnabled); nbt.putFloat("CliffFreq", cliffFreq); nbt.putFloat("CliffContinuity", cliffContinuity);
        nbt.putFloat("MoistureScale", moistureScale); nbt.putFloat("MoistureBias", moistureBias);
        nbt.putFloat("AuxScale", auxScale); nbt.putFloat("AuxBias", auxBias);

        nbt.putBoolean("EnemyBasesEn", enemyBasesEnabled);
        nbt.putFloat("EnemyBaseFreq", enemyBaseFreq);
        nbt.putFloat("EnemyBaseSize", enemyBaseSize);
        nbt.putBoolean("NoEnemies", noEnemies);
        nbt.putBoolean("PeacefulMode", peacefulMode);
        nbt.putFloat("StartingAreaSize", startingAreaSize);
        nbt.putBoolean("EnemyExpansionEn", enemyExpansionEnabled);
        nbt.putInt("ExpMinDist", expansionMinDist);
        nbt.putInt("ExpMaxDist", expansionMaxDist);
        nbt.putInt("ExpGroupSize", expansionGroupSize);
        nbt.putInt("ExpMinCd", expansionMinCooldownMin);
        nbt.putInt("ExpMaxCd", expansionMaxCooldownMin);
        nbt.putBoolean("EvolutionEn", evolutionEnabled);
        nbt.putInt("EvoTime", evolutionTimeFactor);
        nbt.putInt("EvoDestroy", evolutionDestroyFactor);
        nbt.putInt("EvoPoll", evolutionPollutionFactor);

        nbt.putInt("TechMultiplier", techPriceMultiplier);
        nbt.putBoolean("PollutionEn", pollutionEnabled);
        nbt.putFloat("PollAbsorbMod", pollutionAbsorptionMod);
        nbt.putFloat("PollAttackMod", pollutionAttackCostMod);
        nbt.putInt("PollMinTree", pollutionMinTreeDamage);
        nbt.putInt("PollPerTree", pollutionAbsorbedPerTree);
        nbt.putInt("PollDiffPct", pollutionDiffusionPercent);
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("WorldSeed")) worldSeed = nbt.getLong("WorldSeed");
        if (nbt.contains("PresetIndex")) presetIndex = nbt.getInt("PresetIndex");

        if (nbt.contains("IronEn")) ironEnabled = nbt.getBoolean("IronEn");
        if (nbt.contains("IronFreq")) ironFreq = nbt.getFloat("IronFreq");
        if (nbt.contains("IronSize")) ironSize = nbt.getFloat("IronSize");
        if (nbt.contains("IronRich")) ironRichness = nbt.getFloat("IronRich");

        if (nbt.contains("CopperEn")) copperEnabled = nbt.getBoolean("CopperEn");
        if (nbt.contains("CopperFreq")) copperFreq = nbt.getFloat("CopperFreq");
        if (nbt.contains("CopperSize")) copperSize = nbt.getFloat("CopperSize");
        if (nbt.contains("CopperRich")) copperRichness = nbt.getFloat("CopperRich");

        if (nbt.contains("CoalEn")) coalEnabled = nbt.getBoolean("CoalEn");
        if (nbt.contains("CoalFreq")) coalFreq = nbt.getFloat("CoalFreq");
        if (nbt.contains("CoalSize")) coalSize = nbt.getFloat("CoalSize");
        if (nbt.contains("CoalRich")) coalRichness = nbt.getFloat("CoalRich");

        if (nbt.contains("StoneEn")) stoneEnabled = nbt.getBoolean("StoneEn");
        if (nbt.contains("StoneFreq")) stoneFreq = nbt.getFloat("StoneFreq");
        if (nbt.contains("StoneSize")) stoneSize = nbt.getFloat("StoneSize");
        if (nbt.contains("StoneRich")) stoneRichness = nbt.getFloat("StoneRich");

        if (nbt.contains("OilEn")) oilEnabled = nbt.getBoolean("OilEn");
        if (nbt.contains("OilFreq")) oilFreq = nbt.getFloat("OilFreq");
        if (nbt.contains("OilSize")) oilSize = nbt.getFloat("OilSize");
        if (nbt.contains("OilRich")) oilRichness = nbt.getFloat("OilRich");

        if (nbt.contains("MapType")) mapType = nbt.getInt("MapType");
        if (nbt.contains("WaterEn")) waterEnabled = nbt.getBoolean("WaterEn");
        if (nbt.contains("WaterCoverage")) waterCoverage = nbt.getFloat("WaterCoverage");
        if (nbt.contains("WaterScale")) waterScale = nbt.getFloat("WaterScale");
        if (nbt.contains("TreesEn")) treesEnabled = nbt.getBoolean("TreesEn");
        if (nbt.contains("TreeScale")) treeScale = nbt.getFloat("TreeScale");
        if (nbt.contains("TreeCoverage")) treeCoverage = nbt.getFloat("TreeCoverage");
        if (nbt.contains("RocksEn")) rocksEnabled = nbt.getBoolean("RocksEn");
        if (nbt.contains("BoulderFreq")) boulderFreq = nbt.getFloat("BoulderFreq");
        if (nbt.contains("BoulderSize")) boulderSize = nbt.getFloat("BoulderSize");
        if (nbt.contains("CliffsEn")) cliffsEnabled = nbt.getBoolean("CliffsEn");
        if (nbt.contains("CliffFreq")) cliffFreq = nbt.getFloat("CliffFreq");
        if (nbt.contains("CliffContinuity")) cliffContinuity = nbt.getFloat("CliffContinuity");
        if (nbt.contains("MoistureScale")) moistureScale = nbt.getFloat("MoistureScale");
        if (nbt.contains("MoistureBias")) moistureBias = nbt.getFloat("MoistureBias");
        if (nbt.contains("AuxScale")) auxScale = nbt.getFloat("AuxScale");
        if (nbt.contains("AuxBias")) auxBias = nbt.getFloat("AuxBias");

        if (nbt.contains("EnemyBasesEn")) enemyBasesEnabled = nbt.getBoolean("EnemyBasesEn");
        if (nbt.contains("EnemyBaseFreq")) enemyBaseFreq = nbt.getFloat("EnemyBaseFreq");
        if (nbt.contains("EnemyBaseSize")) enemyBaseSize = nbt.getFloat("EnemyBaseSize");
        if (nbt.contains("NoEnemies")) noEnemies = nbt.getBoolean("NoEnemies");
        if (nbt.contains("PeacefulMode")) peacefulMode = nbt.getBoolean("PeacefulMode");
        if (nbt.contains("StartingAreaSize")) startingAreaSize = nbt.getFloat("StartingAreaSize");
        if (nbt.contains("EnemyExpansionEn")) enemyExpansionEnabled = nbt.getBoolean("EnemyExpansionEn");
        if (nbt.contains("ExpMinDist")) expansionMinDist = nbt.getInt("ExpMinDist");
        if (nbt.contains("ExpMaxDist")) expansionMaxDist = nbt.getInt("ExpMaxDist");
        if (nbt.contains("ExpGroupSize")) expansionGroupSize = nbt.getInt("ExpGroupSize");
        if (nbt.contains("ExpMinCd")) expansionMinCooldownMin = nbt.getInt("ExpMinCd");
        if (nbt.contains("ExpMaxCd")) expansionMaxCooldownMin = nbt.getInt("ExpMaxCd");
        if (nbt.contains("EvolutionEn")) evolutionEnabled = nbt.getBoolean("EvolutionEn");
        if (nbt.contains("EvoTime")) evolutionTimeFactor = nbt.getInt("EvoTime");
        if (nbt.contains("EvoDestroy")) evolutionDestroyFactor = nbt.getInt("EvoDestroy");
        if (nbt.contains("EvoPoll")) evolutionPollutionFactor = nbt.getInt("EvoPoll");

        if (nbt.contains("TechMultiplier")) techPriceMultiplier = Math.max(1, nbt.getInt("TechMultiplier"));
        if (nbt.contains("PollutionEn")) pollutionEnabled = nbt.getBoolean("PollutionEn");
        if (nbt.contains("PollAbsorbMod")) pollutionAbsorptionMod = nbt.getFloat("PollAbsorbMod");
        if (nbt.contains("PollAttackMod")) pollutionAttackCostMod = nbt.getFloat("PollAttackMod");
        if (nbt.contains("PollMinTree")) pollutionMinTreeDamage = nbt.getInt("PollMinTree");
        if (nbt.contains("PollPerTree")) pollutionAbsorbedPerTree = nbt.getInt("PollPerTree");
        if (nbt.contains("PollDiffPct")) pollutionDiffusionPercent = nbt.getInt("PollDiffPct");
    }

    public static void loadOrSaveForServer(MinecraftServer server, boolean newlyCreatedConfig, long fallbackMcSeed) {
        try {
            Path dataDir = server.getSavePath(WorldSavePath.ROOT).resolve("data");
            File file = dataDir.resolve("factoriocore_nauvis.nbt").toFile();
            if (file.exists() && !newlyCreatedConfig) {
                NbtCompound nbt = NbtIo.readCompressed(file.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                ACTIVE.fromNbt(nbt);
            } else {
                if (!newlyCreatedConfig) {
                    ACTIVE.worldSeed = fallbackMcSeed;
                }
                dataDir.toFile().mkdirs();
                NbtIo.writeCompressed(ACTIVE.toNbt(), file.toPath());
            }
        } catch (Exception ignored) {
            ACTIVE.worldSeed = fallbackMcSeed;
        }
    }
}