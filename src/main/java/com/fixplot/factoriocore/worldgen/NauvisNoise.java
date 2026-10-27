package com.fixplot.factoriocore.worldgen;

import net.minecraft.util.math.MathHelper;

/**
 * Реализация математических примитивов из Factorio FFF-390 (Noise expressions 2.0):
 * - Когерентный градиентный шум (Coherent Gradient Noise)
 * - Многооктавный фрактальный шум (Multioctave Fractal Noise)
 * - Инвертированный гребневой шум (Inverted Ridge Noise: -|n| + offset)
 * - Конусный точечный шум с возмущением (Perturbed Spot Noise)
 * - Искажение входных координат (Domain Warping)
 */
public final class NauvisNoise {

    private NauvisNoise() {
    }

    public static long hash2D(int x, int z, long seedSalt) {
        long h = seedSalt;
        h ^= (long) x * 0x45d9f3b3335b369L;
        h ^= (long) z * 0x119de1f3456789abL;
        h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
        h = (h ^ (h >>> 27)) * 0x94d049bb133111ebL;
        return h ^ (h >>> 31);
    }

    public static double rand01(int x, int z, long salt) {
        return ((hash2D(x, z, salt) >>> 11) * 0x1.0p-53);
    }

    /**
     * 2D градиентный шум Перлина в диапазоне [-1.0, 1.0] (без квадратных артефактов сетки).
     */
    public static double gradientNoise2D(double x, double z, long salt) {
        int x0 = MathHelper.floor(x);
        int z0 = MathHelper.floor(z);
        double fx = x - x0;
        double fz = z - z0;

        double u = fx * fx * fx * (fx * (fx * 6.0 - 15.0) + 10.0);
        double v = fz * fz * fz * (fz * (fz * 6.0 - 15.0) + 10.0);

        double n00 = gradDot(x0, z0, fx, fz, salt);
        double n10 = gradDot(x0 + 1, z0, fx - 1.0, fz, salt);
        double n01 = gradDot(x0, z0 + 1, fx, fz - 1.0, salt);
        double n11 = gradDot(x0 + 1, z0 + 1, fx - 1.0, fz - 1.0, salt);

        double nx0 = MathHelper.lerp(u, n00, n10);
        double nx1 = MathHelper.lerp(u, n01, n11);
        return MathHelper.clamp(MathHelper.lerp(v, nx0, nx1) * 1.4142, -1.0, 1.0);
    }

    private static double gradDot(int ix, int iz, double dx, double dz, long salt) {
        int h = (int) (hash2D(ix, iz, salt) & 7L);
        return switch (h) {
            case 0 ->  dx + dz;
            case 1 -> -dx + dz;
            case 2 ->  dx - dz;
            case 3 -> -dx - dz;
            case 4 ->  dx;
            case 5 -> -dx;
            case 6 ->  dz;
            default -> -dz;
        };
    }

    /**
     * Фрактальный шум (Fractal Basis Noise из FFF-390): складывает несколько октав разного масштаба.
     */
    public static double fbm(double x, double z, int octaves, double baseScale, long salt) {
        double sum = 0.0;
        double amp = 1.0;
        double freq = 1.0 / baseScale;
        double maxAmp = 0.0;

        for (int i = 0; i < octaves; i++) {
            sum += gradientNoise2D(x * freq, z * freq, salt + i * 1619L) * amp;
            maxAmp += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / maxAmp;
    }

    /**
     * Фрактальный шум с искажением координат (Domain Warping), создающий извилистые берега и границы биомов.
     */
    public static double warpedFbm(double x, double z, int octaves, double baseScale, double warpStrength, long salt) {
        double wx = x + fbm(x, z, 2, baseScale * 0.65, salt + 7001L) * warpStrength;
        double wz = z + fbm(x + 317.0, z - 193.0, 2, baseScale * 0.65, salt + 9007L) * warpStrength;
        return fbm(wx, wz, octaves, baseScale, salt);
    }

    /**
     * Инвертированный гребневой шум (Ridge Noise из FFF-390):
     * Формула: -|noise| + offset.
     * Создаёт непрерывную сеть связанных сухопутных мостов и перешейков, разделяющих котловины озёр.
     */
    public static double ridgeNoise(double x, double z, int octaves, double baseScale, double warpStrength, long salt) {
        double n = warpedFbm(x, z, octaves, baseScale, warpStrength, salt);
        return 1.0 - 2.0 * Math.abs(n); // Возвращает +1.0 на хребтах/перешейках и -1.0 в центрах впадин
    }

    /**
     * Точечный конус с возмущением базовым шумом (Perturbed Spot Cone из FFF-390):
     * Возвращает 1.0 в центре конуса и плавно спадает к 0.0 на радиусе radius, искажая края шумом.
     */
    public static double spotCone(double x, double z, double centerX, double centerZ,
                                  double radius, double perturbationAmp, long salt) {
        double dx = x - centerX;
        double dz = z - centerZ;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > radius * 1.45) {
            return 0.0;
        }
        double perturb = fbm(x, z, 2, radius * 0.45, salt) * perturbationAmp;
        double normalized = (dist + perturb) / radius;
        return MathHelper.clamp(1.0 - normalized, 0.0, 1.0);
    }
}