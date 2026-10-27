package com.fixplot.factoriocore.client.hud;

import com.fixplot.factoriocore.client.content.crafting.ClientCraftingQueueHud;
import com.fixplot.factoriocore.client.content.pollution.PollutionClientRenderer;
import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.combat.turret.TurretBlock;
import com.fixplot.factoriocore.content.combat.turret.TurretBlockEntity;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines;
import com.fixplot.factoriocore.content.fluids.*;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlock;
import com.fixplot.factoriocore.content.logistics.burner_inserter.BurnerInserterBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlock;
import com.fixplot.factoriocore.content.logistics.chest.FactorioChestBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlock;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.pavement.PavementBlock;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.assembler.AssemblerBlockEntity;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlockEntity;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlockEntity;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlock;
import com.fixplot.factoriocore.content.production.furnace.FactorioFurnaceBlockEntity;
import com.fixplot.factoriocore.content.research.ResearchManager;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlock;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlockEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FactorioHudOverlay {

    private static final int MAP_CELLS = 44;
    private static final int PIXEL_SCALE = 2;
    private static final int MAP_SIZE_PX = MAP_CELLS * PIXEL_SCALE;

    private static final int[] colorBuffer = new int[MAP_CELLS * MAP_CELLS];
    private static final int UNLOADED_CELL_COLOR = 0xFF18191B;
    private static boolean minimapVisible = true;
    private static int blocksPerCell = 1;
    private static int nextSliceRow = 0;

    private static float prevMiningProgress = 0.0f;
    private static float miningBarProgress = 0.0f;
    private static BlockPos lastMiningPos = null;
    private static int lastSeenOreAmount = -1;

    private record RadarBlip(int offsetX, int offsetZ, int color) {}
    private static final List<RadarBlip> cachedRadarBlips = new ArrayList<>();

    // --- Кэш результатов тяжёлых power-пробов (BFS по столбам): один расчёт на тик, а не на кадр ---
    private static final class PowerProbe {
        BlockPos pos;
        long worldTime = -1;
        boolean powered;
        boolean poleEnergized;
    }
    private static final PowerProbe lastPowerProbe = new PowerProbe();

    // --- Кэш модели инфо-панели: строится раз в тик, рендерится каждый кадр без аллокаций ---
    private static final List<InfoSection> panelSections = new ArrayList<>();
    private static final List<Text> panelHeaderLines = new ArrayList<>();
    private static List<ItemStack> panelChestItems = Collections.emptyList();
    private static ItemStack panelIcon = ItemStack.EMPTY;
    private static Text panelTitle = Text.empty();
    private static int panelMaxHp = 0;
    private static int panelCurHp = 0;
    private static BlockPos panelPos = null;
    private static BlockPos panelCachePos = null;
    private static long panelCacheTime = -1;

    // --- Кэш строк research-виджета ---
    private static String researchIdleText = null;
    private static String researchActiveTechId = "";
    private static String researchTrimmedTitle = "";
    private static String researchPctText = "";
    private static int researchFillW = 0;
    private static int researchBarW = 0;

    private static KeyBinding toggleMapKey;
    private static KeyBinding zoomInKey;
    private static KeyBinding zoomOutKey;

    public static KeyBinding getToggleMapKey() { return toggleMapKey; }
    public static KeyBinding getZoomInKey() { return zoomInKey; }
    public static KeyBinding getZoomOutKey() { return zoomOutKey; }

    public static void register() {
        toggleMapKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.factoriocore.toggle_minimap", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.factoriocore"));
        zoomInKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.factoriocore.minimap_zoom_in", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, "category.factoriocore"));
        zoomOutKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.factoriocore.minimap_zoom_out", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, "category.factoriocore"));

        // Очистка static-состояния при смене мира/дисконнекте: иначе минимапа, радар,
        // прогресс-бар и панель показывают данные из предыдущего мира.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetClientState());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> resetClientState());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleMapKey.wasPressed()) minimapVisible = !minimapVisible;
            // При смене масштаба весь буфер устарел (цвета считались для другого zoom) — сбрасываем.
            while (zoomInKey.wasPressed()) if (blocksPerCell > 1) { blocksPerCell /= 2; Arrays.fill(colorBuffer, UNLOADED_CELL_COLOR); }
            while (zoomOutKey.wasPressed()) if (blocksPerCell < 4) { blocksPerCell *= 2; Arrays.fill(colorBuffer, UNLOADED_CELL_COLOR); }

            if (client.world != null && client.player != null && !client.isPaused()) {
                if (minimapVisible && FactorioConfig.INSTANCE.enableMinimap) {
                    updateMapSlice(client.world, client.player, nextSliceRow, nextSliceRow + 11);
                    nextSliceRow = (nextSliceRow + 11) % MAP_CELLS;
                    if ((client.world.getTime() & 7L) == 0L) updateRadarEntities(client.world, client.player);
                }
                tickMiningProgressBar(client);
                tickInfoPanelCache(client);
            }
        });

        HudRenderCallback.EVENT.register(FactorioHudOverlay::renderHud);
    }

    private static void resetClientState() {
        cachedRadarBlips.clear();
        Arrays.fill(colorBuffer, UNLOADED_CELL_COLOR);
        lastMiningPos = null;
        lastSeenOreAmount = -1;
        prevMiningProgress = 0.0f;
        miningBarProgress = 0.0f;
        nextSliceRow = 0;
        lastPowerProbe.pos = null;
        lastPowerProbe.worldTime = -1;
        clearPanelCache();
        researchIdleText = null;
        researchActiveTechId = "";
    }

    private static boolean isMachinePoweredCached(World world, BlockPos pos) {
        long now = world.getTime();
        if (lastPowerProbe.pos == null || !lastPowerProbe.pos.equals(pos) || lastPowerProbe.worldTime != now) {
            lastPowerProbe.pos = pos.toImmutable();
            lastPowerProbe.worldTime = now;
            lastPowerProbe.powered = PowerNetworkManager.isMachinePowered(world, pos);
        }
        return lastPowerProbe.powered;
    }

    private static boolean isPoleEnergizedCached(World world, BlockPos pos) {
        long now = world.getTime();
        if (lastPowerProbe.pos == null || !lastPowerProbe.pos.equals(pos) || lastPowerProbe.worldTime != now) {
            lastPowerProbe.pos = pos.toImmutable();
            lastPowerProbe.worldTime = now;
            lastPowerProbe.poleEnergized = PowerNetworkManager.isPoleEnergized(world, pos);
        }
        return lastPowerProbe.poleEnergized;
    }

    private static void tickMiningProgressBar(MinecraftClient client) {
        if (client.player == null || client.world == null || client.player.isCreative() || client.interactionManager == null) {
            prevMiningProgress = 0.0f;
            miningBarProgress = 0.0f;
            lastMiningPos = null;
            return;
        }

        if (client.options.attackKey.isPressed() && client.interactionManager.isBreakingBlock()
                && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            BlockState state = client.world.getBlockState(pos);
            Block block = state.getBlock();

            if (BlockHealthManager.getMaxHealth(block) > 0 || block instanceof ResourceOreBlock || block instanceof PavementBlock) {
                int currentOre = -1;
                if (block instanceof ResourceOreBlock && client.world.getBlockEntity(pos) instanceof ResourceOreBlockEntity oreBe) {
                    currentOre = oreBe.getOrInitAmount();
                }

                if (!pos.equals(lastMiningPos) || (currentOre != -1 && lastSeenOreAmount != -1 && currentOre != lastSeenOreAmount)) {
                    lastMiningPos = pos.toImmutable();
                    lastSeenOreAmount = currentOre;
                    prevMiningProgress = 0.0f;
                    miningBarProgress = 0.0f;
                }
                lastSeenOreAmount = currentOre;

                prevMiningProgress = miningBarProgress;
                float delta = state.calcBlockBreakingDelta(client.player, client.world, pos);
                miningBarProgress += delta;
                if (miningBarProgress >= 1.0f) {
                    miningBarProgress = (block instanceof ResourceOreBlock) ? 0.0f : 1.0f;
                    prevMiningProgress = miningBarProgress;
                }
                return;
            }
        }

        prevMiningProgress = 0.0f;
        miningBarProgress = 0.0f;
        lastMiningPos = null;
        lastSeenOreAmount = -1;
    }

    private static void updateRadarEntities(World world, PlayerEntity player) {
        cachedRadarBlips.clear();
        int radiusBlocks = (MAP_CELLS / 2) * blocksPerCell;
        Box radarBox = player.getBoundingBox().expand(radiusBlocks, 24.0, radiusBlocks);
        List<Entity> entities = world.getOtherEntities(player, radarBox);

        for (Entity ent : entities) {
            int color = 0;
            if (ent instanceof Monster) color = 0xFFFF2222;
            else if (ent instanceof PlayerEntity) color = 0xFF44FF44;

            if (color != 0) {
                double dx = (ent.getX() - player.getX()) / blocksPerCell;
                double dz = (ent.getZ() - player.getZ()) / blocksPerCell;
                int ox = MathHelper.floor(dx * PIXEL_SCALE);
                int oz = MathHelper.floor(dz * PIXEL_SCALE);
                cachedRadarBlips.add(new RadarBlip(ox, oz, color));
            }
        }
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) {
            return;
        }

        int screenW = context.getScaledWindowWidth();
        int nextPanelY = 6;

        float mapScale = MathHelper.clamp(FactorioConfig.INSTANCE.minimapScale, 0.5f, 2.0f);
        context.getMatrices().push();
        context.getMatrices().translate(screenW, 0, 0);
        context.getMatrices().scale(mapScale, mapScale, 1.0f);
        context.getMatrices().translate(-screenW, 0, 0);

        int frameSize = MAP_SIZE_PX + 6;
        int x = screenW - frameSize - 6;
        int curTopY = 6;

        int researchBarH = 22;
        renderTopRightResearchWidget(context, client.textRenderer, x, curTopY, frameSize, researchBarH);
        curTopY += researchBarH + 2;

        if (minimapVisible && FactorioConfig.INSTANCE.enableMinimap) {
            int y = curTopY;

            context.fill(x, y, x + frameSize, y + frameSize, 0xFF141414);
            context.fill(x + 1, y + 1, x + frameSize - 1, y + frameSize - 1, 0xFF313234);

            int mapX = x + 3;
            int mapY = y + 3;
            context.fill(mapX - 1, mapY - 1, mapX + MAP_SIZE_PX + 1, mapY + MAP_SIZE_PX + 1, 0xFF141414);

            // Склейка горизонтальных прогонов одного цвета: вместо 44*44=1936 fill-вызовов
            // на типичном рельефе выходит 100-300.
            for (int gz = 0; gz < MAP_CELLS; gz++) {
                int drawY = mapY + gz * PIXEL_SCALE;
                int rowOffset = gz * MAP_CELLS;
                int gx = 0;
                while (gx < MAP_CELLS) {
                    int color = colorBuffer[rowOffset + gx];
                    int runStart = gx;
                    while (gx < MAP_CELLS && colorBuffer[rowOffset + gx] == color) gx++;
                    int drawX = mapX + runStart * PIXEL_SCALE;
                    context.fill(drawX, drawY, drawX + (gx - runStart) * PIXEL_SCALE, drawY + PIXEL_SCALE, color);
                }
            }

            int centerPxX = mapX + MAP_SIZE_PX / 2;
            int centerPxY = mapY + MAP_SIZE_PX / 2;

            PollutionClientRenderer.renderMinimapPollutionOverlay(context, centerPxX, centerPxY, MAP_SIZE_PX / 2, (float) blocksPerCell / PIXEL_SCALE);

            for (RadarBlip blip : cachedRadarBlips) {
                int ex = centerPxX + blip.offsetX();
                int ez = centerPxY + blip.offsetZ();
                if (ex >= mapX + 2 && ex < mapX + MAP_SIZE_PX - 2 && ez >= mapY + 2 && ez < mapY + MAP_SIZE_PX - 2) {
                    context.fill(ex - 1, ez - 1, ex + 2, ez + 2, blip.color());
                }
            }

            drawPlayerMarker(context, centerPxX, centerPxY, client.player.getYaw());
            if (AltModeRenderer.isAltModeActive()) {
                context.fill(x - 28, y, x - 2, y + 14, 0xEE141414);
                context.fill(x - 27, y + 1, x - 3, y + 13, 0xEEAC6E14);
                context.drawText(client.textRenderer, "ALT", x - 23, y + 3, 0xFFFFFFFF, true);
            }
            curTopY += frameSize + 4;
        }

        context.getMatrices().pop();
        nextPanelY = (int) (6 + (curTopY - 6) * mapScale);

        if (FactorioConfig.INSTANCE.enableTargetInfoPanel) {
            float infoScale = MathHelper.clamp(FactorioConfig.INSTANCE.infoPanelScale, 0.5f, 2.0f);
            context.getMatrices().push();
            context.getMatrices().translate(screenW, nextPanelY, 0);
            context.getMatrices().scale(infoScale, infoScale, 1.0f);
            context.getMatrices().translate(-screenW, -nextPanelY, 0);

            renderTargetInfoPanel(context, client, screenW, nextPanelY);

            context.getMatrices().pop();
        }

        if (FactorioConfig.INSTANCE.enableCraftingQueueHud) {
            ClientCraftingQueueHud.renderBottomLeftQueue(context, false);
        }
        renderMiningProgressBar(context, tickCounter.getTickDelta(false));
    }

    private static void renderTopRightResearchWidget(DrawContext context, TextRenderer tr, int x, int y, int width, int height) {
        ResearchManager.TechNode activeTech = ResearchManager.getActiveTech();

        context.fill(x, y, x + width, y + height, 0xFF141414);
        context.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF28292C);
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, 0xFF1F2023);

        if (activeTech == null) {
            if (researchIdleText == null) {
                researchIdleText = Text.translatable("hud.factoriocore.press_t_research").getString();
            }
            context.getMatrices().push();
            context.getMatrices().translate(x + 5, y + 8, 100.0f);
            context.getMatrices().scale(0.62f, 0.62f, 1.0f);
            context.drawText(tr, researchIdleText, 0, 0, 0xFF999B9F, true);
            context.getMatrices().pop();
            return;
        }

        // Строки и размеры пересчитываются только при смене активной технологии
        // или процента — TranslatableText-резолв и trimToWidth не гоняются каждый кадр.
        float ratio = ResearchManager.getOverallProgressRatio();
        int pct = MathHelper.clamp(Math.round(ratio * 100.0f), 0, 100);
        String pctText = pct + "%";

        if (!activeTech.id.equals(researchActiveTechId)) {
            researchActiveTechId = activeTech.id;
            float titleScale = 0.65f;
            int maxTitlePx = Math.round((width - 24) / titleScale);
            researchTrimmedTitle = tr.trimToWidth(activeTech.getLocalizedName(), maxTitlePx);
        }

        context.getMatrices().push();
        context.getMatrices().translate(x + 3.0f, y + 3.0f, 100.0f);
        context.drawItem(activeTech.icon, 0, 0);
        context.getMatrices().pop();

        context.getMatrices().push();
        context.getMatrices().translate(x + 21.0f, y + 4.0f, 100.0f);
        context.getMatrices().scale(0.65f, 0.65f, 1.0f);
        context.drawText(tr, researchTrimmedTitle, 0, 0, 0xFFFFFFFF, true);
        context.getMatrices().pop();

        if (!pctText.equals(researchPctText)) {
            researchPctText = pctText;
            researchFillW = -1; // заставить пересчитать ширину заливки
        }

        float pctScale = 0.62f;
        int pctW = Math.round(tr.getWidth(pctText) * pctScale);
        int pctX = x + width - pctW - 3;

        int barX = x + 21;
        int barY = y + 13;
        int barW = Math.max(16, (pctX - 3) - barX);
        int barH = 5;

        if (barW != researchBarW || researchFillW < 0) {
            researchBarW = barW;
            researchFillW = Math.round(ratio * (barW - 2));
        }

        context.fill(barX, barY, barX + barW, barY + barH, 0xFF111213);
        context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF2D2E32);

        if (researchFillW > 0) {
            context.fill(barX + 1, barY + 1, barX + 1 + researchFillW, barY + barH - 1, 0xFF43A047);
            context.fill(barX + 1, barY + 1, barX + 1 + researchFillW, barY + 2, 0xFF66BB6A);
        }

        context.getMatrices().push();
        context.getMatrices().translate(pctX, y + 12.0f, 100.0f);
        context.getMatrices().scale(pctScale, pctScale, 1.0f);
        context.drawText(tr, pctText, 0, 0, 0xFFE6E6E6, true);
        context.getMatrices().pop();
    }

    private static class InfoSection {
        String title;
        int iconColor;
        List<Text> lines = new ArrayList<>();
        InfoSection(String t, int c) { title = t; iconColor = c; }
    }

    private static Formatting formattingForFluid(FactorioFluidContainer.FluidKind kind) {
        return switch (kind) {
            case WATER -> Formatting.AQUA;
            case STEAM -> Formatting.WHITE;
            case CRUDE_OIL -> Formatting.GRAY;
            case HEAVY_OIL -> Formatting.RED;
            case LIGHT_OIL -> Formatting.GOLD;
            case PETROLEUM_GAS -> Formatting.LIGHT_PURPLE;
            case LUBRICANT -> Formatting.GREEN;
            case SULFURIC_ACID -> Formatting.YELLOW;
            default -> Formatting.GRAY;
        };
    }

    private static String mbUnit() {
        return Text.translatableWithFallback("unit.factoriocore.mb", "mB").getString();
    }

    /**
     * Раз в клиентский тик собирает модель инфо-панели (переводы, секции, ItemStack'и,
     * power-пробы) в кэш. Рендер каждый кадр только читает кэш — никаких аллокаций
     * и тяжёлых вызовов World в render().
     */
    private static void tickInfoPanelCache(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            if (panelCachePos != null) clearPanelCache();
            return;
        }
        World world = client.world;
        if (world == null) {
            clearPanelCache();
            return;
        }

        long worldTime = world.getTime();
        BlockPos rawPos = hit.getBlockPos();
        // Пересобираем каждый тик (состояния машин меняются), но только для валидного прицела.
        if (panelCachePos != null && !rawPos.equals(panelCachePos)) {
            // Смена цели — мгновенная пересборка нужна и ниже всё равно выполнится.
        }

        BlockState rawState = world.getBlockState(rawPos);
        if (rawState.isAir()) {
            clearPanelCache();
            return;
        }

        BlockPos pos = BlockHealthManager.getCanonicalPos(rawPos, rawState);
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        int maxHp = BlockHealthManager.getMaxHealth(block);
        boolean isFactorioOre = block instanceof ResourceOreBlock || block instanceof OilMachines.CrudeOilDepositBlock;

        if (maxHp <= 0 && !isFactorioOre) {
            clearPanelCache();
            return;
        }

        panelCachePos = rawPos.toImmutable();
        panelCacheTime = worldTime;

        TextRenderer tr = client.textRenderer;
        FactorioConfig cfg = FactorioConfig.INSTANCE;

        panelSections.clear();
        panelHeaderLines.clear();
        panelPos = pos.toImmutable();
        panelMaxHp = maxHp;
        panelCurHp = (cfg.showInfoHealth && maxHp > 0)
                ? BlockHealthManager.getCurrentHealth(world, pos, state) : 0;
        panelTitle = block.getName();
        panelIcon = new ItemStack(block.asItem());
        BlockEntity be = world.getBlockEntity(pos);

        // --- 1. Описание ---
        if (cfg.showInfoDescription) {
            String descKey = block.getTranslationKey() + ".desc";
            String desc = Text.translatable(descKey).getString();
            if (!desc.equals(descKey)) {
                String[] words = desc.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String word : words) {
                    if (tr.getWidth(sb.toString() + word) > 180) {
                        panelHeaderLines.add(Text.literal(sb.toString()).formatted(Formatting.GRAY));
                        sb = new StringBuilder();
                    }
                    sb.append(word).append(" ");
                }
                if (sb.length() > 0) {
                    panelHeaderLines.add(Text.literal(sb.toString()).formatted(Formatting.GRAY));
                }
            }
        }

        // --- 2. Статус ---
        if (cfg.showInfoStatus) {
            if (block instanceof ResourceOreBlock oreBlock) {
                int amount = ResourceOreBlockEntity.getClientDisplayAmount(world, pos, oreBlock.getOreType());
                panelHeaderLines.add(Text.translatable("gui.factoriocore.info.expected_resources",
                                Text.literal(String.valueOf(amount)).formatted(Formatting.YELLOW))
                        .formatted(Formatting.WHITE));
            } else if (block instanceof OilMachines.CrudeOilDepositBlock) {
                int yieldPct = com.fixplot.factoriocore.worldgen.NauvisChunkGenerator.evaluateOilYieldPercentAt(pos.getX(), pos.getZ());
                panelHeaderLines.add(Text.translatable("hud.factoriocore.oil_yield").formatted(Formatting.WHITE)
                        .append(Text.literal(yieldPct + "%").formatted(Formatting.LIGHT_PURPLE)));
            } else if (be != null && !(be instanceof PipeBlockEntity) && !(be instanceof StorageTankBlock.StorageTankBlockEntity)) {
                boolean isWorking = false;
                boolean noFuel = false;
                boolean noPower = false;

                if (be instanceof ElectricDrillBlockEntity eDrill) {
                    isWorking = eDrill.isActivelyMining();
                    noPower = !eDrill.isPowered();
                } else if (be instanceof BoilerBlockEntity boiler) {
                    isWorking = boiler.isBurning();
                    noFuel = !isWorking && boiler.getStack(0).isEmpty();
                } else if (be instanceof BurnerDrillBlockEntity drill) {
                    isWorking = drill.getClientDrillRotation(0.0f) > 0.0f || !drill.getFuelStack().isEmpty();
                    noFuel = !isWorking;
                } else if (be instanceof FactorioFurnaceBlockEntity furnace) {
                    isWorking = furnace.isBurning();
                    noFuel = !isWorking && furnace.getStack(1).isEmpty();
                } else if (be instanceof InserterBlockEntity ins) {
                    boolean isBurner = block instanceof BurnerInserterBlock;
                    isWorking = !ins.getHeldStack().isEmpty() || ins.getArmProgress(0.0f) > 0.01f;
                    // Тяжёлый BFS по столбам — через per-tick кэш, НЕ напрямую в рендере.
                    boolean powered = isBurner || isMachinePoweredCached(world, pos);
                    noPower = !isBurner && !powered;
                    noFuel = isBurner && ins.getFuelStack().isEmpty();
                } else if (be instanceof com.fixplot.factoriocore.content.research.LabBlockEntity lab) {
                    isWorking = lab.isWorking();
                    noPower = !lab.isPowered();
                } else if (be instanceof AssemblerBlockEntity asm) {
                    isWorking = asm.isWorking();
                    noPower = !asm.isPowered();
                } else if (be instanceof OilMachines.PumpjackBlockEntity pj) {
                    isWorking = pj.isWorking();
                    noPower = !pj.isPowered();
                } else if (be instanceof OilMachines.OilRefineryBlockEntity ref) {
                    isWorking = ref.isWorking();
                    noPower = !ref.isPowered();
                } else if (be instanceof OilMachines.ChemicalPlantBlockEntity cp) {
                    isWorking = cp.isWorking();
                    noPower = !cp.isPowered();
                } else if (be instanceof SolarAndAccumulatorMachines.SolarPanelBlockEntity) {
                    isWorking = SolarAndAccumulatorMachines.SolarPanelBlockEntity.getCurrentOutputWatts(world) > 0;
                } else if (be instanceof SolarAndAccumulatorMachines.AccumulatorBlockEntity acc) {
                    isWorking = acc.getChargeState() != 0 || acc.getStoredJoules() > 0;
                } else if (be instanceof TurretBlockEntity turret) {
                    isWorking = turret.isFiring() || (turret.getTurretKind() == 1 && turret.isPowered())
                            || (turret.getTurretKind() == 0 && !turret.isEmpty())
                            || (turret.getTurretKind() == 2 && turret.getFluidAmount() > 0);
                    noPower = (turret.getTurretKind() == 1 && !turret.isPowered());
                } else if (block instanceof com.fixplot.factoriocore.content.electricity.ElectricPoleBlock) {
                    // Тяжёлый BFS по столбам — через per-tick кэш.
                    isWorking = isPoleEnergizedCached(world, pos);
                    noPower = !isWorking;
                }

                if (noPower) {
                    panelHeaderLines.add(Text.translatable("gui.factoriocore.info.status_no_power").formatted(Formatting.RED));
                } else if (noFuel) {
                    panelHeaderLines.add(Text.translatable("gui.factoriocore.info.status_no_fuel").formatted(Formatting.RED));
                } else if (isWorking) {
                    panelHeaderLines.add(Text.translatable("gui.factoriocore.info.status_working").formatted(Formatting.GREEN));
                } else {
                    panelHeaderLines.add(Text.translatable("gui.factoriocore.info.status_waiting").formatted(Formatting.YELLOW));
                }
            }
        }

        // --- 3. СОДЕРЖИМОЕ ТРУБ, РЕЗЕРВУАРОВ И ЖИДКОСТНЫХ МАШИН ---
        if (cfg.showInfoFluids) {
            String inTag = Text.translatable("gui.factoriocore.info.input_tag").getString();
            String outTag = Text.translatable("gui.factoriocore.info.output_tag").getString();
            String mb = mbUnit();

            if (be instanceof PipeBlockEntity pipe) {
                FactorioFluidContainer.FluidKind kind = pipe.getFluidKind();
                int amt = pipe.getFluidAmount();
                int cap = pipe.getFluidCapacity();
                InfoSection pipeSec = new InfoSection(Text.translatable("gui.factoriocore.info.pipe_contents").getString(), kind.color);
                if (amt <= 0 || kind == FactorioFluidContainer.FluidKind.NONE) {
                    pipeSec.lines.add(Text.translatable("gui.factoriocore.info.fluid_empty", cap).formatted(Formatting.GRAY));
                } else {
                    pipeSec.lines.add(Text.literal(kind.getLocalizedName() + ": ").formatted(Formatting.WHITE)
                            .append(Text.literal(amt + " / " + cap + " " + mb).formatted(formattingForFluid(kind))));
                    pipeSec.lines.add(Text.translatable("gui.factoriocore.info.temperature", pipe.getTemperatureC() + " °C").formatted(Formatting.WHITE));
                }
                panelSections.add(pipeSec);
            } else if (be instanceof StorageTankBlock.StorageTankBlockEntity tank) {
                FactorioFluidContainer.FluidKind kind = tank.getFluidKind();
                int amt = tank.getFluidAmount();
                int cap = tank.getFluidCapacity();
                InfoSection tankSec = new InfoSection(Text.translatable("gui.factoriocore.info.tank_contents").getString(), kind.color);
                if (amt <= 0 || kind == FactorioFluidContainer.FluidKind.NONE) {
                    tankSec.lines.add(Text.translatable("gui.factoriocore.info.fluid_empty", cap).formatted(Formatting.GRAY));
                } else {
                    tankSec.lines.add(Text.literal(kind.getLocalizedName() + ": ").formatted(Formatting.WHITE)
                            .append(Text.literal(amt + " / " + cap + " " + mb).formatted(formattingForFluid(kind))));
                }
                panelSections.add(tankSec);
            } else if (be instanceof OffshorePumpBlockEntity) {
                InfoSection pumpSec = new InfoSection(Text.translatable("gui.factoriocore.info.fluid_output").getString(), 0x2980B9);
                pumpSec.lines.add(Text.translatable("gui.factoriocore.info.water_rate").formatted(Formatting.AQUA));
                panelSections.add(pumpSec);
            } else if (be instanceof BoilerBlockEntity boiler) {
                InfoSection waterSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_water").getString(), 0x33CCFF);
                waterSec.lines.add(Text.translatable("gui.factoriocore.info.water_line", boiler.getWaterMb()).formatted(Formatting.WHITE));
                waterSec.lines.add(Text.translatable("gui.factoriocore.info.temperature", "15 °C").formatted(Formatting.WHITE));
                panelSections.add(waterSec);

                InfoSection steamSec = new InfoSection(Text.translatable("gui.factoriocore.info.produces_steam").getString(), 0xDDDDDD);
                steamSec.lines.add(Text.translatable("gui.factoriocore.info.steam_boiler_line", boiler.getSteamMb()).formatted(Formatting.WHITE));
                panelSections.add(steamSec);
            } else if (be instanceof SteamEngineBlockEntity engine) {
                InfoSection steamSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_steam").getString(), 0xDDDDDD);
                steamSec.lines.add(Text.translatable("gui.factoriocore.info.steam_line", engine.getFluidAmount()).formatted(Formatting.WHITE));
                steamSec.lines.add(Text.translatable("gui.factoriocore.info.max_temperature", "165 °C").formatted(Formatting.WHITE));
                panelSections.add(steamSec);
            } else if (be instanceof OilMachines.PumpjackBlockEntity pj) {
                InfoSection oilSec = new InfoSection(Text.translatable("gui.factoriocore.info.oil_extraction").getString(), 0x33333A);
                oilSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.CRUDE_OIL.getLocalizedName() + ": "
                        + pj.getFluidAmount() + " / 2000 " + mb).formatted(Formatting.WHITE));
                panelSections.add(oilSec);
            } else if (be instanceof OilMachines.OilRefineryBlockEntity ref) {
                int mode = ref.getRecipeMode();
                InfoSection refSec = new InfoSection(Text.translatable("gui.factoriocore.info.refinery_fluids").getString(), 0xF57F17);
                if (mode == 2) {
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.HEAVY_OIL.getLocalizedName() + inTag + ": " + ref.getCrudeOilMb() + " / 4000 " + mb).formatted(Formatting.RED));
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.STEAM.getLocalizedName() + inTag + ": " + ref.getWaterMb() + " / 4000 " + mb).formatted(Formatting.AQUA));
                } else {
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.CRUDE_OIL.getLocalizedName() + inTag + ": " + ref.getCrudeOilMb() + " / 4000 " + mb).formatted(Formatting.WHITE));
                    if (mode == 1) {
                        refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.WATER.getLocalizedName() + inTag + ": " + ref.getWaterMb() + " / 4000 " + mb).formatted(Formatting.AQUA));
                    }
                }
                if (mode == 0) {
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.PETROLEUM_GAS.getLocalizedName() + outTag + ": " + ref.getPetroleumGasMb() + " / 4000 " + mb).formatted(Formatting.LIGHT_PURPLE));
                } else {
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.HEAVY_OIL.getLocalizedName() + outTag + ": " + ref.getHeavyOilMb() + " / 4000 " + mb).formatted(Formatting.RED));
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.LIGHT_OIL.getLocalizedName() + outTag + ": " + ref.getLightOilMb() + " / 4000 " + mb).formatted(Formatting.GOLD));
                    refSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.PETROLEUM_GAS.getLocalizedName() + outTag + ": " + ref.getPetroleumGasMb() + " / 4000 " + mb).formatted(Formatting.LIGHT_PURPLE));
                }
                panelSections.add(refSec);
            } else if (be instanceof OilMachines.ChemicalPlantBlockEntity cp) {
                FactorioFluidContainer.FluidKind reqMain = cp.getRequiredMainFluid();
                FactorioFluidContainer.FluidKind prodFluid = cp.getProducedFluidKind();
                int secColor = (prodFluid != FactorioFluidContainer.FluidKind.NONE) ? prodFluid.color
                        : (reqMain != FactorioFluidContainer.FluidKind.NONE ? reqMain.color : 0x2980B9);

                InfoSection cpSec = new InfoSection(Text.translatable("gui.factoriocore.info.chem_fluids").getString(), secColor);
                if (reqMain != FactorioFluidContainer.FluidKind.NONE) {
                    cpSec.lines.add(Text.literal(reqMain.getLocalizedName() + inTag + ": " + cp.getMainFluidMb() + " / 4000 " + mb)
                            .formatted(formattingForFluid(reqMain)));
                }
                if (cp.requiresWater()) {
                    cpSec.lines.add(Text.literal(FactorioFluidContainer.FluidKind.WATER.getLocalizedName() + inTag + ": " + cp.getWaterMb() + " / 4000 " + mb)
                            .formatted(Formatting.AQUA));
                }
                if (prodFluid != FactorioFluidContainer.FluidKind.NONE) {
                    cpSec.lines.add(Text.literal(prodFluid.getLocalizedName() + outTag + ": " + cp.getOutFluidMb() + " / 4000 " + mb)
                            .formatted(formattingForFluid(prodFluid)));
                }
                panelSections.add(cpSec);
            } else if (be instanceof TurretBlockEntity turret && turret.getTurretKind() == 2) {
                FactorioFluidContainer.FluidKind fk = turret.getFluidKind();
                InfoSection ftSec = new InfoSection(Text.translatable("gui.factoriocore.info.turret_defense").getString(), 0xD68910);
                String fname = (fk == FactorioFluidContainer.FluidKind.NONE)
                        ? FactorioFluidContainer.FluidKind.LIGHT_OIL.getLocalizedName()
                        : fk.getLocalizedName();
                ftSec.lines.add(Text.literal(fname + ": " + turret.getFluidAmount() + " / 2000 " + mb).formatted(Formatting.GOLD));
                panelSections.add(ftSec);
            }
        }

        // --- 4. Электричество / Топливо / Турели ---
        if (cfg.showInfoElectricity) {
            if (block instanceof ElectricDrillBlock || be instanceof OilMachines.PumpjackBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(90_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof OilMachines.OilRefineryBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(420_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof OilMachines.ChemicalPlantBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(210_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof com.fixplot.factoriocore.content.research.LabBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(60_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof AssemblerBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(75_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof SteamEngineBlockEntity engine) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.produces_electricity").getString(), 0xFFD700);
                String kw = PowerNetworkManager.formatWatts(engine.getCurrentOutputWatts());
                String maxKw = PowerNetworkManager.formatWatts(900_000L);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.energy_production", kw + " / " + maxKw).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof SolarAndAccumulatorMachines.SolarPanelBlockEntity) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.produces_electricity").getString(), 0xFFD700);
                String kw = PowerNetworkManager.formatWatts(SolarAndAccumulatorMachines.SolarPanelBlockEntity.getCurrentOutputWatts(world));
                String maxKw = PowerNetworkManager.formatWatts(60_000L);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.energy_production", kw + " / " + maxKw).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof SolarAndAccumulatorMachines.AccumulatorBlockEntity acc) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.accumulator_section").getString(), 0x00E5FF);
                String stored = PowerNetworkManager.formatJoules(acc.getStoredJoules());
                String cap = PowerNetworkManager.formatJoules(acc.getCapacityJoules());
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.stored_charge", stored + " / " + cap).formatted(Formatting.AQUA));
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(300_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (be instanceof TurretBlockEntity turret && block instanceof TurretBlock tb) {
                InfoSection tSec = new InfoSection(Text.translatable("gui.factoriocore.info.turret_defense").getString(), 0xE53935);
                tSec.lines.add(Text.translatable("gui.factoriocore.info.turret_range", tb.getAttackRangeBlocks(), turret.getKillsCount()).formatted(Formatting.YELLOW));
                if (tb.getTurretKind() == 1) {
                    tSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(800_000L)).formatted(Formatting.WHITE));
                }
                panelSections.add(tSec);
            } else if (be instanceof BoilerBlockEntity) {
                InfoSection fuelSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_fuel").getString(), 0xFF8C00);
                fuelSec.lines.add(Text.translatable("gui.factoriocore.info.max_consumption", PowerNetworkManager.formatWatts(1_800_000L)).formatted(Formatting.WHITE));
                panelSections.add(fuelSec);
            } else if (be instanceof InserterBlockEntity ins && !(block instanceof BurnerInserterBlock)) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.consumes_electricity").getString(), 0xFFD700);
                boolean active = !ins.getHeldStack().isEmpty() || ins.getArmProgress(0.0f) > 0.01f;
                String usage = PowerNetworkManager.formatWatts(active ? ins.getActiveConsumptionWatts() : 400L);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.consumption", usage).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            } else if (block instanceof com.fixplot.factoriocore.content.electricity.CreativeEnergySourceBlock) {
                InfoSection elecSec = new InfoSection(Text.translatable("gui.factoriocore.info.produces_electricity").getString(), 0xFFD700);
                elecSec.lines.add(Text.translatable("gui.factoriocore.info.energy_production", PowerNetworkManager.formatWatts(500_000_000L)).formatted(Formatting.WHITE));
                panelSections.add(elecSec);
            }
        }

        // --- 5. Сундуки ---
        panelChestItems = Collections.emptyList();
        if (block instanceof FactorioChestBlock && be instanceof FactorioChestBlockEntity chest) {
            panelChestItems = chest.getSortedContentSummary(4);
            if (chest.getUnlockedSlots() < chest.getTotalSlots()) {
                panelHeaderLines.add(Text.translatable("gui.factoriocore.info.slots_available",
                                Text.literal(chest.getUnlockedSlots() + " / " + chest.getTotalSlots()).formatted(Formatting.RED))
                        .formatted(Formatting.WHITE));
            }
        }
    }

    private static void clearPanelCache() {
        panelCachePos = null;
        panelCacheTime = -1;
        panelSections.clear();
        panelHeaderLines.clear();
        panelChestItems = Collections.emptyList();
        panelIcon = ItemStack.EMPTY;
        panelTitle = Text.empty();
        panelPos = null;
        panelMaxHp = 0;
        panelCurHp = 0;
    }

    /** Рендер инфо-панели: читает готовую модель из кэша, собранную в tickInfoPanelCache. */
    private static void renderTargetInfoPanel(DrawContext context, MinecraftClient client, int screenW, int topY) {
        if (panelCachePos == null || panelPos == null) return;

        TextRenderer tr = client.textRenderer;
        FactorioConfig cfg = FactorioConfig.INSTANCE;

        List<InfoSection> sections = panelSections;
        List<Text> headerLines = panelHeaderLines;
        List<ItemStack> chestTopItems = panelChestItems;
        Text title = panelTitle;
        int maxHp = panelMaxHp;
        int curHp = panelCurHp;

        int panelW = Math.max(160, tr.getWidth(title) + 40);
        for (Text line : headerLines) panelW = Math.max(panelW, tr.getWidth(line) + 16);
        for (InfoSection sec : sections) {
            panelW = Math.max(panelW, tr.getWidth(sec.title) + 24);
            for (Text line : sec.lines) panelW = Math.max(panelW, tr.getWidth(line) + 16);
        }
        if (!chestTopItems.isEmpty()) panelW = Math.max(panelW, chestTopItems.size() * 20 + 16);

        int hpBarH = (cfg.showInfoHealth && maxHp > 0) ? 14 : 0;
        int chestIconsH = chestTopItems.isEmpty() ? 0 : 22;
        int panelH = 26 + headerLines.size() * 11 + hpBarH + chestIconsH + 4;
        for (InfoSection sec : sections) {
            panelH += 18 + sec.lines.size() * 11 + 4;
        }

        int x = screenW - panelW - 6;
        int y = topY;

        context.fill(x, y, x + panelW, y + panelH, 0xFA1E1E1E);
        context.fill(x + 1, y + 1, x + panelW - 1, y + panelH - 1, 0xFA2C2C2C);
        context.fill(x + 2, y + 2, x + panelW - 2, y + 22, 0xFFE2C899);
        context.fill(x + 2, y + 22, x + panelW - 2, y + 24, 0xFF000000);

        if (!panelIcon.isEmpty()) {
            context.drawItem(panelIcon, x + 4, y + 4);
        }
        context.drawText(tr, title, x + 24, y + 8, 0xFF000000, false);

        int cursorY = y + 28;
        for (Text line : headerLines) {
            context.drawText(tr, line, x + 8, cursorY, 0xFFFFFFFF, false);
            cursorY += 11;
        }

        if (cfg.showInfoHealth && maxHp > 0) {
            context.drawText(tr, Text.translatable("gui.factoriocore.info.health", curHp + "/" + maxHp).formatted(Formatting.WHITE), x + 8, cursorY, 0xFFFFFFFF, false); // Text.translatable в рендере — единственный дешёвый мутирующий элемент, HP меняется каждый тик
            cursorY += 10;
            int barX = x + 8;
            int barW = panelW - 16;
            int barH = 4;
            context.fill(barX, cursorY, barX + barW, cursorY + barH, 0xFF141414);
            float ratio = MathHelper.clamp(curHp / (float) maxHp, 0.0f, 1.0f);
            int fillW = Math.round(ratio * barW);
            int barColor = (ratio > 0.6f) ? 0xFF38B828 : (ratio > 0.3f ? 0xFFE59524 : 0xFFD93025);
            if (fillW > 0) context.fill(barX, cursorY, barX + fillW, cursorY + barH, barColor);
            cursorY += 8;
        }

        for (InfoSection sec : sections) {
            context.fill(x + 4, cursorY, x + panelW - 4, cursorY + 1, 0xFF444444);
            cursorY += 4;
            context.fill(x + 8, cursorY + 2, x + 12, cursorY + 6, 0xFF000000 | sec.iconColor);
            context.drawText(tr, Text.literal(sec.title).formatted(Formatting.YELLOW), x + 16, cursorY, 0xFFFFFFFF, false);
            cursorY += 12;
            context.fill(x + 4, cursorY, x + panelW - 4, cursorY + 1, 0xFF333333);
            cursorY += 4;
            for (Text line : sec.lines) {
                context.drawText(tr, line, x + 8, cursorY, 0xFFFFFFFF, false);
                cursorY += 11;
            }
        }

        if (!chestTopItems.isEmpty()) {
            cursorY += 2;
            int slotStartX = x + 8;
            for (int i = 0; i < chestTopItems.size(); i++) {
                ItemStack st = chestTopItems.get(i);
                int sx = slotStartX + i * 20;
                int sy = cursorY;
                context.fill(sx, sy, sx + 18, sy + 18, 0xFF141414);
                context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3C3E);
                context.drawItem(st, sx + 1, sy + 1);

                String cntStr = formatCompactCount(st.getCount());
                context.getMatrices().push();
                context.getMatrices().translate(0.0f, 0.0f, 200.0f);
                context.getMatrices().scale(0.65f, 0.65f, 1.0f);
                int tw = tr.getWidth(cntStr);
                int tx = (int) ((sx + 17) / 0.65f) - tw;
                int ty = (int) ((sy + 12) / 0.65f);
                context.drawText(tr, cntStr, tx, ty, 0xFFFFFFFF, true);
                context.getMatrices().pop();
            }
        }
    }

    private static String formatCompactCount(int count) {
        if (count >= 1000) {
            int t = (count * 10 + 500) / 1000;
            return (t / 10) + "." + (t % 10) + "k";
        }
        return Integer.toString(count);
    }

    private static void renderMiningProgressBar(DrawContext context, float tickDelta) {
        if (miningBarProgress <= 0.0f && prevMiningProgress <= 0.0f) return;

        float smoothProgress = MathHelper.clamp(MathHelper.lerp(tickDelta, prevMiningProgress, miningBarProgress), 0.0f, 1.0f);
        int screenW = context.getScaledWindowWidth();
        int screenH = context.getScaledWindowHeight();
        int barW = 182;
        int barH = 5;
        int bx = (screenW - barW) / 2;
        int by = screenH - 54;

        context.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, 0xEE141414);
        context.fill(bx, by, bx + barW, by + barH, 0xEE26272A);
        int fillW = Math.round(smoothProgress * barW);
        if (fillW > 0) {
            context.fill(bx, by, bx + fillW, by + barH, 0xFFE59524);
            context.fill(bx, by, bx + fillW, by + 2, 0xFFFFCC66);
        }
    }

    private static void drawPlayerMarker(DrawContext context, int cx, int cy, float yaw) {
        context.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFF111111);
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFFFFCC00);

        double rad = Math.toRadians(yaw + 90.0f);
        int tipX = cx + (int) Math.round(Math.cos(rad) * 3.0);
        int tipY = cy + (int) Math.round(Math.sin(rad) * 3.0);
        context.fill(tipX - 1, tipY - 1, tipX + 1, tipY + 1, 0xFFFFFFFF);
    }

    private static void updateMapSlice(World world, PlayerEntity player, int startRow, int endRow) {
        int centerX = MathHelper.floor(player.getX());
        int centerZ = MathHelper.floor(player.getZ());
        int half = MAP_CELLS / 2;
        BlockPos.Mutable pos = new BlockPos.Mutable();

        int maxRow = Math.min(MAP_CELLS, endRow);
        for (int gz = startRow; gz < maxRow; gz++) {
            int wz = centerZ + (gz - half) * blocksPerCell;
            int rowOffset = gz * MAP_CELLS;

            for (int gx = 0; gx < MAP_CELLS; gx++) {
                int wx = centerX + (gx - half) * blocksPerCell;

                if (!world.isChunkLoaded(wx >> 4, wz >> 4)) {
                    colorBuffer[rowOffset + gx] = 0xFF18191B;
                    continue;
                }

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, wx, wz) - 1;
                if (topY < world.getBottomY()) {
                    colorBuffer[rowOffset + gx] = 0xFF18191B;
                    continue;
                }

                pos.set(wx, topY, wz);
                BlockState state = world.getBlockState(pos);
                if (state.isAir() && topY > world.getBottomY()) {
                    pos.set(wx, topY - 1, wz);
                    state = world.getBlockState(pos);
                }

                colorBuffer[rowOffset + gx] = resolveFactorioMapColor(world, pos, state);
            }
        }
    }

    private static int resolveFactorioMapColor(World world, BlockPos pos, BlockState state) {
        Block block = state.getBlock();

        if (block instanceof OilMachines.CrudeOilDepositBlock
                || com.fixplot.factoriocore.worldgen.NauvisChunkGenerator.hasCrudeOilSpotAt(pos.getX(), pos.getZ(), 0.25)) {
            return 0xFFD81B60;
        }

        if (state.isOf(Blocks.WATER) || state.isOf(Blocks.SEAGRASS) || state.isOf(Blocks.LILY_PAD)) {
            float factor = com.fixplot.factoriocore.content.pollution.PollutionManager.getClientWaterTintFactor(pos.getX(), pos.getZ());
            if (factor <= 0.01f) return 0xFF2471A3;
            // Clamp: при factor > 1 lerp выходит за 255 и биты протекают в соседние каналы.
            int r = MathHelper.clamp(MathHelper.lerp(factor, 0x24, 0x46), 0, 255);
            int g = MathHelper.clamp(MathHelper.lerp(factor, 0x71, 0x6E), 0, 255);
            int b = MathHelper.clamp(MathHelper.lerp(factor, 0xA3, 0x24), 0, 255);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        }

        if (block instanceof BeltBlock belt) {
            return switch (belt.getTier()) {
                case 2 -> 0xFFE53935;
                case 3 -> 0xFF29B6F6;
                default -> 0xFFFDD835;
            };
        }
        if (block instanceof com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock || block instanceof FactorioFurnaceBlock) return 0xFF42A5F5;
        if (block instanceof InserterBlock || block instanceof FactorioChestBlock || block instanceof TurretBlock) return 0xFFFFB300;
        if (block instanceof OilMachines.CrudeOilDepositBlock) return 0xFFD81B60;

        if (block instanceof ResourceOreBlock ore) {
            return switch (ore.getOreType()) {
                case 0 -> 0xFF5B9BD5;
                case 1 -> 0xFFED7D31;
                case 2 -> 0xFF1A1A1D;
                default -> 0xFFD2C290;
            };
        }

        Block below = world.getBlockState(pos.down()).getBlock();
        if (below instanceof ResourceOreBlock oreBelow) {
            return switch (oreBelow.getOreType()) {
                case 0 -> 0xFF5B9BD5;
                case 1 -> 0xFFED7D31;
                case 2 -> 0xFF1A1A1D;
                default -> 0xFFD2C290;
            };
        }

        if (block instanceof PavementBlock) return 0xFF70757A;
        if (pos.getY() >= 66 && (state.isOf(Blocks.DRIPSTONE_BLOCK) || state.isOf(Blocks.GRANITE) || state.isOf(Blocks.TUFF))) return 0xFF424949;

        MapColor mapColor = state.getMapColor(world, pos);
        if (mapColor == MapColor.CLEAR) {
            BlockState groundState = world.getBlockState(pos.down());
            mapColor = groundState.getMapColor(world, pos.down());
        }
        int rgb = mapColor.color;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}