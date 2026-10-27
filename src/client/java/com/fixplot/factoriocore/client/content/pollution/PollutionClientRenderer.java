package com.fixplot.factoriocore.client.content.pollution;

import com.fixplot.factoriocore.client.hud.AltModeRenderer;
import com.fixplot.factoriocore.content.pollution.PollutionManager;
import com.fixplot.factoriocore.network.SyncPollutionPayload;
import com.fixplot.factoriocore.worldgen.NauvisChunkGenerator;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class PollutionClientRenderer {

    private static float smoothedLocalPollution = 0.0f;
    private static final Map<Long, Integer> LAST_RENDERED_WATER_TIER = new HashMap<>();

    public static void register() {
        ColorProviderRegistry.BLOCK.register((state, world, pos, tintIndex) -> {
            int baseColor = (world != null && pos != null)
                    ? BiomeColors.getWaterColor(world, pos)
                    : 0x3F76E4;
            if (pos == null) return baseColor;

            float factor = PollutionManager.getClientWaterTintFactor(pos.getX(), pos.getZ());
            if (factor <= 0.01f) return baseColor;

            int r = MathHelper.lerp(factor, (baseColor >> 16) & 0xFF, 0x4E);
            int g = MathHelper.lerp(factor, (baseColor >> 8) & 0xFF, 0x7A);
            int b = MathHelper.lerp(factor, baseColor & 0xFF, 0x27);
            return (r << 16) | (g << 8) | b;
        }, Blocks.WATER, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS, Blocks.KELP, Blocks.KELP_PLANT);

        ClientPlayNetworking.registerGlobalReceiver(
                SyncPollutionPayload.ID,
                (payload, context) -> context.client().execute(() -> {
                    PollutionManager.updateClientCache(payload.chunkPollution());
                    refreshWaterChunksIfNeeded(context.client(), payload.chunkPollution());
                })
        );

        ClientTickEvents.END_CLIENT_TICK.register(PollutionClientRenderer::tickClientSmogParticles);
        WorldRenderEvents.LAST.register(PollutionClientRenderer::renderWorldPollutionGrid);
        HudRenderCallback.EVENT.register(PollutionClientRenderer::renderSmogVignetteAndBadge);
    }

    private static void refreshWaterChunksIfNeeded(MinecraftClient client, Map<Long, Float> syncedMap) {
        if (client.world == null || client.worldRenderer == null) return;
        int seaY = NauvisChunkGenerator.SEA_LEVEL;
        boolean anyChanged = false;

        for (Map.Entry<Long, Float> entry : syncedMap.entrySet()) {
            long key = entry.getKey();
            int tier = (int) (entry.getValue() / 1.5f);
            int prevTier = LAST_RENDERED_WATER_TIER.getOrDefault(key, -1);

            if (tier != prevTier) {
                LAST_RENDERED_WATER_TIER.put(key, tier);
                anyChanged = true;
            }
        }

        if (anyChanged) {
            // Сбрасываем BiomeColorCache клиента, чтобы вода пересчитала свой цвет через наш миксин!
            client.world.reloadColor();
            for (Long key : syncedMap.keySet()) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        int minX = (cx + dx) << 4;
                        int minZ = (cz + dz) << 4;
                        client.worldRenderer.scheduleBlockRenders(
                                minX, seaY - 6, minZ,
                                minX + 15, seaY + 2, minZ + 15
                        );
                    }
                }
            }
        }
    }

    private static void tickClientSmogParticles(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            PollutionManager.clearClientCache();
            LAST_RENDERED_WATER_TIER.clear();
            smoothedLocalPollution = 0.0f;
            return;
        }
        if (client.isPaused()) return;

        BlockPos playerPos = client.player.getBlockPos();
        float targetPollution = PollutionManager.getPollutionAt(client.world, playerPos);
        smoothedLocalPollution = MathHelper.lerp(0.05f, smoothedLocalPollution, targetPollution);

        if (smoothedLocalPollution < 35.0f) return;
        if (client.world.random.nextFloat() > 0.35f) return;

        int particleCount = MathHelper.clamp((int) (smoothedLocalPollution / 90.0f), 1, 3);
        for (int i = 0; i < particleCount; i++) {
            double px = client.player.getX() + (client.world.random.nextDouble() - 0.5) * 28.0;
            double py = client.player.getY() + (client.world.random.nextDouble() - 0.2) * 10.0;
            double pz = client.player.getZ() + (client.world.random.nextDouble() - 0.5) * 28.0;
            client.world.addParticle(ParticleTypes.ASH, px, py, pz, 0.005, -0.005, 0.005);
        }
    }

    private static void renderWorldPollutionGrid(WorldRenderContext context) {
        if (!AltModeRenderer.isAltModeActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null || context.consumers() == null || context.matrixStack() == null) {
            return;
        }

        Map<Long, Float> cache = PollutionManager.getClientCache();
        if (cache.isEmpty()) return;

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        int pcx = client.player.getChunkPos().x;
        int pcz = client.player.getChunkPos().z;

        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                float poll = PollutionManager.getClientChunkPollution(cx, cz);
                if (poll < 2.0f) continue;

                float intensity = MathHelper.clamp(poll / 120.0f, 0.15f, 0.85f);
                int minX = cx << 4;
                int minZ = cz << 4;
                int surfaceY = client.world.getTopY(Heightmap.Type.WORLD_SURFACE, minX + 8, minZ + 8);

                Box chunkBox = new Box(
                        minX + 0.1 - cam.x, surfaceY + 0.08 - cam.y, minZ + 0.1 - cam.z,
                        minX + 15.9 - cam.x, surfaceY + 0.35 - cam.y, minZ + 15.9 - cam.z
                );
                WorldRenderer.drawBox(matrices, lines, chunkBox, 1.0f, 0.18f, 0.15f, intensity);
            }
        }
    }

    private static void renderSmogVignetteAndBadge(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden) return;

        int sw = context.getScaledWindowWidth();
        int sh = context.getScaledWindowHeight();

        if (smoothedLocalPollution > 65.0f) {
            float ratio = MathHelper.clamp((smoothedLocalPollution - 65.0f) / 380.0f, 0.0f, 0.10f);
            int alpha = (int) (ratio * 255.0f);
            if (alpha > 0) {
                int smogColor = (alpha << 24) | 0x3D2618;
                context.fill(0, 0, sw, sh, smogColor);
            }
        }

        if (AltModeRenderer.isAltModeActive() && smoothedLocalPollution >= 0.5f) {
            String pollText = String.format(Locale.ROOT, "Загрязнение: %.1f PU", smoothedLocalPollution);
            int tw = client.textRenderer.getWidth(pollText);
            int bx = sw - tw - 14;
            int by = sh - 22;

            context.fill(bx - 4, by - 3, bx + tw + 4, by + 11, 0xCC141414);
            int barCol = smoothedLocalPollution > 80.0f ? 0xFFFF4444 : (smoothedLocalPollution > 25.0f ? 0xFFFF9933 : 0xFFFFCC66);
            context.drawText(client.textRenderer, Text.literal(pollText), bx, by, barCol, true);
        }
    }

    public static void renderMinimapPollutionOverlay(DrawContext context, int mapCenterX, int mapCenterY,
                                                     int mapHalfSizePx, float blocksPerPx) {
        if (!AltModeRenderer.isAltModeActive()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        int pcx = client.player.getChunkPos().x;
        int pcz = client.player.getChunkPos().z;

        int chunkRadius = Math.min(10, Math.max(3, Math.round((mapHalfSizePx * blocksPerPx) / 16.0f) + 1));

        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                float poll = PollutionManager.getClientChunkPollution(cx, cz);
                if (poll < 2.0f) continue;

                double chunkMinWorldX = (cx << 4);
                double chunkMinWorldZ = (cz << 4);
                double chunkMaxWorldX = chunkMinWorldX + 16.0;
                double chunkMaxWorldZ = chunkMinWorldZ + 16.0;

                int x1 = mapCenterX + Math.round((float) ((chunkMinWorldX - playerX) / blocksPerPx));
                int y1 = mapCenterY + Math.round((float) ((chunkMinWorldZ - playerZ) / blocksPerPx));
                int x2 = mapCenterX + Math.round((float) ((chunkMaxWorldX - playerX) / blocksPerPx));
                int y2 = mapCenterY + Math.round((float) ((chunkMaxWorldZ - playerZ) / blocksPerPx));

                x1 = MathHelper.clamp(x1, mapCenterX - mapHalfSizePx, mapCenterX + mapHalfSizePx);
                y1 = MathHelper.clamp(y1, mapCenterY - mapHalfSizePx, mapCenterY + mapHalfSizePx);
                x2 = MathHelper.clamp(x2, mapCenterX - mapHalfSizePx, mapCenterX + mapHalfSizePx);
                y2 = MathHelper.clamp(y2, mapCenterY - mapHalfSizePx, mapCenterY + mapHalfSizePx);

                if (x2 > x1 && y2 > y1) {
                    int alpha = MathHelper.clamp((int) ((poll / 140.0f) * 145.0f), 24, 150);
                    int redOverlay = (alpha << 24) | 0xE61919;
                    context.fill(x1, y1, x2, y2, redOverlay);
                }
            }
        }
    }
}