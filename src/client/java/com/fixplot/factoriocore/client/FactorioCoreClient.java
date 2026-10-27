package com.fixplot.factoriocore.client;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.client.config.FactorioConfigScreen;
import com.fixplot.factoriocore.client.content.crafting.ClientCraftingQueueHud;
import com.fixplot.factoriocore.client.content.crafting.FactorioCharacterScreen;
import com.fixplot.factoriocore.client.content.electricity.ElectricNetworkInfoScreen;
import com.fixplot.factoriocore.client.content.electricity.LargePoleRenderer;
import com.fixplot.factoriocore.client.content.fluids.BoilerScreen;
import com.fixplot.factoriocore.client.content.fluids.FluidMachinesRenderer;
import com.fixplot.factoriocore.client.content.logistics.burner_inserter.BurnerInserterScreen;
import com.fixplot.factoriocore.client.content.logistics.chest.FactorioChestScreen;
import com.fixplot.factoriocore.client.content.logistics.inserter.InserterRenderer;
import com.fixplot.factoriocore.client.content.logistics.inserter.InserterScreen;
import com.fixplot.factoriocore.client.content.logistics.splitter.SplitterScreen;
import com.fixplot.factoriocore.client.content.pollution.PollutionClientRenderer;
import com.fixplot.factoriocore.client.content.production.assembler.AssemblerRenderer;
import com.fixplot.factoriocore.client.content.production.assembler.AssemblerScreen;
import com.fixplot.factoriocore.client.content.production.burner_drill.BurnerDrillRenderer;
import com.fixplot.factoriocore.client.content.production.burner_drill.BurnerDrillScreen;
import com.fixplot.factoriocore.client.content.production.electric_drill.ElectricDrillRenderer;
import com.fixplot.factoriocore.client.content.production.electric_drill.ElectricDrillScreen;
import com.fixplot.factoriocore.client.content.production.furnace.FactorioFurnaceRenderer;
import com.fixplot.factoriocore.client.content.production.furnace.FactorioFurnaceScreen;
import com.fixplot.factoriocore.client.content.research.LabRenderer;
import com.fixplot.factoriocore.client.content.research.LabScreen;
import com.fixplot.factoriocore.client.content.research.TechTreeScreen;
import com.fixplot.factoriocore.client.hud.AltModeRenderer;
import com.fixplot.factoriocore.client.hud.FactorioHudOverlay;
import com.fixplot.factoriocore.client.oil.OilMachineScreen;
import com.fixplot.factoriocore.client.oil.OilMachinesRenderer;
import com.fixplot.factoriocore.client.sound.FactorioSoundController;
import com.fixplot.factoriocore.client.worldgen.NauvisCustomizeScreen;
import com.fixplot.factoriocore.config.FactorioConfig;
import com.fixplot.factoriocore.content.combat.BlockHealthManager;
import com.fixplot.factoriocore.content.electricity.CreativeEnergySourceBlock;
import com.fixplot.factoriocore.content.electricity.ElectricPoleBlock;
import com.fixplot.factoriocore.content.electricity.PowerNetworkManager;
import com.fixplot.factoriocore.content.electricity.SolarAndAccumulatorMachines;
import com.fixplot.factoriocore.content.fluids.BoilerBlock;
import com.fixplot.factoriocore.content.fluids.OffshorePumpBlock;
import com.fixplot.factoriocore.content.fluids.SteamEngineBlock;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import com.fixplot.factoriocore.content.logistics.inserter.InserterBlockEntity;
import com.fixplot.factoriocore.content.logistics.splitter.SplitterBlock;
import com.fixplot.factoriocore.content.oil.OilMachines;
import com.fixplot.factoriocore.content.production.burner_drill.BurnerDrillBlock;
import com.fixplot.factoriocore.content.production.electric_drill.ElectricDrillBlock;
import com.fixplot.factoriocore.content.research.LabBlock;
import com.fixplot.factoriocore.content.world.ore.ResourceOreBlockEntity;
import com.fixplot.factoriocore.network.SyncBlockHealthPayload;
import com.fixplot.factoriocore.network.SyncCraftQueuePayload;
import com.fixplot.factoriocore.network.SyncOreAmountPayload;
import com.fixplot.factoriocore.registry.ModBlocks;
import com.fixplot.factoriocore.registry.ModScreenHandlers;
import com.fixplot.factoriocore.worldgen.ModWorldGen;
import com.fixplot.factoriocore.worldgen.NauvisConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FactorioCoreClient implements ClientModInitializer {

    private static final DustParticleEffect YELLOW_PARTICLE =
            new DustParticleEffect(new Vector3f(1.0f, 0.85f, 0.10f), 0.65f);

    private static int cachedParticleRgb = -1;
    private static DustParticleEffect cachedPlacementParticle = YELLOW_PARTICLE;

    private static KeyBinding openFactorioInvKeyRef;
    private static KeyBinding openSettingsKeyRef;
    private static KeyBinding openTechTreeKeyRef;

    public static KeyBinding getOpenCraftingKey() { return openFactorioInvKeyRef; }
    public static KeyBinding getOpenSettingsKey() { return openSettingsKeyRef; }

    private static BeltBlockEntity previewBeltT1;
    private static BeltBlockEntity previewBeltT2;
    private static BeltBlockEntity previewBeltT3;
    private static InserterBlockEntity previewLongInserter;
    private static InserterBlockEntity previewFastInserter;

    private record WireLink(BlockPos a, BlockPos b) {}
    private record PoleSupplyNode(BlockPos pos, int radius) {}

    private static final List<WireLink> CACHED_WIRE_LINKS = new ArrayList<>();
    private static final List<PoleSupplyNode> CACHED_POLE_NODES = new ArrayList<>();
    private static long lastWireCacheTick = -100L;
    private static BlockPos lastWireCacheCenter = BlockPos.ORIGIN;

    @Override
    public void onInitializeClient() {
        ensureBlockModelAssetsExist();

        BlockEntityRendererFactories.register(ModBlocks.ASSEMBLER_BLOCK_ENTITY, AssemblerRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.LAB_BLOCK_ENTITY, LabRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.BELT_BLOCK_ENTITY, BeltBlockEntityRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.INSERTER_BLOCK_ENTITY, InserterRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.BURNER_DRILL_BLOCK_ENTITY, BurnerDrillRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.FACTORIO_FURNACE_BLOCK_ENTITY, FactorioFurnaceRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.OFFSHORE_PUMP_BLOCK_ENTITY, FluidMachinesRenderer.PumpRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.BOILER_BLOCK_ENTITY, FluidMachinesRenderer.BoilerRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.STEAM_ENGINE_BLOCK_ENTITY, FluidMachinesRenderer.SteamEngineRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.ELECTRIC_DRILL_BLOCK_ENTITY, ElectricDrillRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.CRUDE_OIL_DEPOSIT_BLOCK_ENTITY, OilMachinesRenderer.OilDepositRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.PUMPJACK_BLOCK_ENTITY, OilMachinesRenderer.PumpjackRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.OIL_REFINERY_BLOCK_ENTITY, OilMachinesRenderer.RefineryRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.CHEMICAL_PLANT_BLOCK_ENTITY, OilMachinesRenderer.ChemicalPlantRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.LARGE_POLE_BLOCK_ENTITY, LargePoleRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.STORAGE_TANK_BLOCK_ENTITY, com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.StorageTankRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.SOLAR_PANEL_BLOCK_ENTITY, com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.SolarPanelRenderer::new);
        BlockEntityRendererFactories.register(ModBlocks.ACCUMULATOR_BLOCK_ENTITY, com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.AccumulatorRenderer::new);

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.STORAGE_TANK, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.12, 0.5);
            matrices.scale(0.34f, 0.34f, 0.34f);
            matrices.translate(-0.5, 0.0, -0.5);
            com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.StorageTankRenderer.renderStorageTankModel(
                    matrices, vertexConsumers, light, overlay, com.fixplot.factoriocore.content.fluids.FactorioFluidContainer.FluidKind.WATER, 0.65f);
            matrices.pop();
        });

        BlockEntityRendererFactories.register(ModBlocks.TURRET_BLOCK_ENTITY, com.fixplot.factoriocore.client.content.combat.TurretRenderer::new);
        HandledScreens.register(ModScreenHandlers.TURRET_SCREEN_HANDLER, com.fixplot.factoriocore.client.content.combat.TurretScreen::new);

        BuiltinItemRendererRegistry.DynamicItemRenderer turretItemRenderer = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            int kind = stack.isOf(ModBlocks.LASER_TURRET.asItem()) ? 1 : (stack.isOf(ModBlocks.FLAMETHROWER_TURRET.asItem()) ? 2 : 0);
            matrices.push();
            matrices.translate(0.18, 0.12, 0.18);
            matrices.scale(0.36f, 0.36f, 0.36f);
            com.fixplot.factoriocore.client.content.combat.TurretRenderer.renderTurretModel(kind, 0.0f, 0.0f, false, matrices, vertexConsumers, light, overlay);
            matrices.pop();
        };
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.GUN_TURRET, turretItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.LASER_TURRET, turretItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.FLAMETHROWER_TURRET, turretItemRenderer);

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.SOLAR_PANEL, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.22, 0.5);
            matrices.scale(0.35f, 0.35f, 0.35f);
            matrices.translate(-0.5, 0.0, -0.5);
            com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.SolarPanelRenderer.renderSolarPanelModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.ACCUMULATOR, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.18, 0.12, 0.18);
            matrices.scale(0.38f, 0.38f, 0.38f);
            com.fixplot.factoriocore.client.content.electricity.SolarAndTankRenderer.AccumulatorRenderer.renderAccumulatorModel(matrices, vertexConsumers, light, overlay, 1.0f, 1);
            matrices.pop();
        });

        LabBlock.clientTechTreeOpener = p -> MinecraftClient.getInstance().execute(() ->
                MinecraftClient.getInstance().setScreen(new TechTreeScreen()));

        PowerNetworkManager.clientGuiOpener = polePos -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> mc.setScreen(new ElectricNetworkInfoScreen(polePos)));
        };

        BuiltinItemRendererRegistry.DynamicItemRenderer beltItemRenderer = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            if (previewBeltT1 == null) {
                previewBeltT1 = new BeltBlockEntity(BlockPos.ORIGIN, ModBlocks.BELT.getDefaultState());
                previewBeltT2 = new BeltBlockEntity(BlockPos.ORIGIN, ModBlocks.FAST_BELT.getDefaultState());
                previewBeltT3 = new BeltBlockEntity(BlockPos.ORIGIN, ModBlocks.EXPRESS_BELT.getDefaultState());
            }

            BeltBlockEntity target = previewBeltT1;
            if (stack.isOf(ModBlocks.FAST_BELT.asItem())) {
                target = previewBeltT2;
            } else if (stack.isOf(ModBlocks.EXPRESS_BELT.asItem())) {
                target = previewBeltT3;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.world != null) {
                target.setWorld(mc.world);
            }
            mc.getBlockEntityRenderDispatcher().renderEntity(target, matrices, vertexConsumers, light, overlay);
        };

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.BELT, beltItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.FAST_BELT, beltItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.EXPRESS_BELT, beltItemRenderer);

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.LAB, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.12, 0.5);
            matrices.scale(0.34f, 0.34f, 0.34f);
            matrices.translate(-0.5, 0.0, -0.5);
            MinecraftClient mc = MinecraftClient.getInstance();
            float angle = (mc.world != null) ? (mc.world.getTime() * 6.0f) : 0.0f;
            LabRenderer.renderLabModel(matrices, vertexConsumers, light, overlay, angle, true);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.ASSEMBLER, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.12, 0.5);
            matrices.scale(0.34f, 0.34f, 0.34f);
            matrices.translate(-0.5, 0.0, -0.5);
            AssemblerRenderer.renderAssemblerModel(matrices, vertexConsumers, light, overlay, 0.0f);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.CRUDE_OIL_DEPOSIT, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.1, 0.15, 0.1);
            matrices.scale(0.8f, 0.8f, 0.8f);
            OilMachinesRenderer.OilDepositRenderer.renderOilDepositModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.PUMPJACK, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.12, 0.5);
            matrices.scale(0.34f, 0.34f, 0.34f);
            matrices.translate(-0.5, 0.0, -0.5);
            OilMachinesRenderer.PumpjackRenderer.renderPumpjackModel(matrices, vertexConsumers, light, overlay, 0.0f);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.OIL_REFINERY, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.10, 0.5);
            matrices.scale(0.20f, 0.20f, 0.20f);
            matrices.translate(-0.5, 0.0, -0.5);
            OilMachinesRenderer.RefineryRenderer.renderRefineryModel(matrices, vertexConsumers, light, overlay, true);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.CHEMICAL_PLANT, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5, 0.12, 0.5);
            matrices.scale(0.34f, 0.34f, 0.34f);
            matrices.translate(-0.5, 0.0, -0.5);
            OilMachinesRenderer.ChemicalPlantRenderer.renderChemicalPlantModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.BIG_ELECTRIC_POLE, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.18, 0.08, 0.18);
            matrices.scale(0.32f, 0.32f, 0.32f);
            LargePoleRenderer.renderBigElectricPoleModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.SUBSTATION, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.18, 0.12, 0.18);
            matrices.scale(0.36f, 0.36f, 0.36f);
            LargePoleRenderer.renderSubstationModel(matrices, vertexConsumers, light, overlay);
            matrices.pop();
        });

        BuiltinItemRendererRegistry.DynamicItemRenderer customInserterItemRenderer = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            Block block = ((BlockItem) stack.getItem()).getBlock();
            if (previewLongInserter == null) {
                previewLongInserter = new InserterBlockEntity(BlockPos.ORIGIN, ModBlocks.LONG_HANDED_INSERTER.getDefaultState());
                previewFastInserter = new InserterBlockEntity(BlockPos.ORIGIN, ModBlocks.FAST_INSERTER.getDefaultState());
            }
            InserterBlockEntity dummy = (block == ModBlocks.LONG_HANDED_INSERTER) ? previewLongInserter : previewFastInserter;
            matrices.push();
            mc.getBlockRenderManager().renderBlockAsEntity(block.getDefaultState(), matrices, vertexConsumers, light, overlay);
            InserterRenderer.renderArticulatedArm(dummy, false, Direction.NORTH, 0.35f, 0.0f, ItemStack.EMPTY, matrices, vertexConsumers, light, overlay);
            matrices.pop();
        };

        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.LONG_HANDED_INSERTER, customInserterItemRenderer);
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.FAST_INSERTER, customInserterItemRenderer);

        HandledScreens.register(ModScreenHandlers.INSERTER_SCREEN_HANDLER, InserterScreen::new);
        HandledScreens.register(ModScreenHandlers.BURNER_INSERTER_SCREEN_HANDLER, BurnerInserterScreen::new);
        HandledScreens.register(ModScreenHandlers.SPLITTER_SCREEN_HANDLER, SplitterScreen::new);
        HandledScreens.register(ModScreenHandlers.BURNER_DRILL_SCREEN_HANDLER, BurnerDrillScreen::new);
        HandledScreens.register(ModScreenHandlers.FACTORIO_FURNACE_SCREEN_HANDLER, FactorioFurnaceScreen::new);
        HandledScreens.register(ModScreenHandlers.FACTORIO_CHEST_SCREEN_HANDLER, FactorioChestScreen::new);
        HandledScreens.register(ModScreenHandlers.ASSEMBLER_SCREEN_HANDLER, AssemblerScreen::new);
        HandledScreens.register(ModScreenHandlers.ELECTRIC_DRILL_SCREEN_HANDLER, ElectricDrillScreen::new);
        HandledScreens.register(ModScreenHandlers.BOILER_SCREEN_HANDLER, BoilerScreen::new);
        HandledScreens.register(ModScreenHandlers.LAB_SCREEN_HANDLER, LabScreen::new);
        HandledScreens.register(ModScreenHandlers.OIL_MACHINE_SCREEN_HANDLER, OilMachineScreen::new);

        FactorioHudOverlay.register();
        AltModeRenderer.register();
        PollutionClientRenderer.register();

        ClientPlayNetworking.registerGlobalReceiver(
                SyncCraftQueuePayload.ID,
                (payload, context) -> context.client().execute(() -> ClientCraftingQueueHud.updateFromPacket(payload))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SyncOreAmountPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        ResourceOreBlockEntity.setClientCachedAmount(payload.pos(), payload.amount()))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                SyncBlockHealthPayload.ID,
                (payload, context) -> context.client().execute(() ->
                        BlockHealthManager.setClientDamage(payload.pos(), payload.damageTaken()))
        );

        ClientPlayNetworking.registerGlobalReceiver(
                com.fixplot.factoriocore.network.SyncResearchStatePayload.ID,
                (payload, context) -> context.client().execute(() ->
                        com.fixplot.factoriocore.content.research.ResearchManager.applyClientSync(payload.researchNbt()))
        );

        // Очистка клиентских static-кэшей: иначе утечка памяти за сессию
        // и ложные данные при смене мира/измерения.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            BlockHealthManager.clearClientState();
            ResourceOreBlockEntity.clearClientCache();
            com.fixplot.factoriocore.content.pollution.PollutionManager.clearClientCache();
            ClientCraftingQueueHud.clear();
            com.fixplot.factoriocore.content.research.ResearchManager.resetToDefaults();
        });

        FactorioConfig.load();
        openFactorioInvKeyRef = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.factoriocore.open_crafting", GLFW.GLFW_KEY_C, "category.factoriocore")
        );
        openTechTreeKeyRef = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.factoriocore.open_tech_tree", GLFW.GLFW_KEY_T, "category.factoriocore")
        );
        openSettingsKeyRef = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.factoriocore.open_settings", GLFW.GLFW_KEY_F8, "category.factoriocore")
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openFactorioInvKeyRef.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new FactorioCharacterScreen());
                }
            }
            while (openTechTreeKeyRef != null && openTechTreeKeyRef.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new TechTreeScreen());
                }
            }
            while (openSettingsKeyRef != null && openSettingsKeyRef.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new FactorioConfigScreen(client.currentScreen));
                }
            }
            if (FactorioConfig.INSTANCE.replaceVanillaInventory
                    && client.currentScreen instanceof InventoryScreen
                    && client.player != null
                    && !client.player.isCreative()) {
                client.setScreen(new FactorioCharacterScreen());
            }

            updateElectricityRenderCache(client);
            spawnMultiblockPlacementParticles(client);
            ElectricNetworkInfoScreen.tickClientHistory(client);
            FactorioSoundController.tickClient(client);
            if (!client.isPaused()) {
                // Состояние исследований приходит с сервера через SyncResearchStatePayload;
                // локальная инициализация statics не нужна и вредна (затирает серверные данные).
            }
        });

        WorldRenderEvents.LAST.register(FactorioCoreClient::renderMultiblockPlacementOutline);
        WorldRenderEvents.LAST.register(FactorioCoreClient::renderElectricityOverlayAndWires);

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof CreateWorldScreen createWorldScreen) {
                if (!NauvisCustomizeScreen.customConfigTouched) {
                    NauvisConfig.ACTIVE.resetDefaults();
                    NauvisConfig.ACTIVE.randomizeSeed();
                }

                ScreenEvents.beforeRender(screen).register((scr, drawContext, mouseX, mouseY, tickDelta) -> {
                    if (isNauvisPresetSelected(createWorldScreen)) {
                        ButtonWidget customizeBtn = findCustomizeButton(createWorldScreen);
                        if (customizeBtn != null) {
                            customizeBtn.active = true;
                        }
                    }
                });

                ScreenMouseEvents.allowMouseClick(screen).register((scr, mouseX, mouseY, button) -> {
                    if (button == 0 && isNauvisPresetSelected(createWorldScreen)) {
                        ButtonWidget customizeBtn = findCustomizeButton(createWorldScreen);
                        if (customizeBtn != null && customizeBtn.visible && customizeBtn.active
                                && customizeBtn.isMouseOver(mouseX, mouseY)) {
                            ModWorldGen.newlyCustomizedWorld = true;
                            client.setScreen(new NauvisCustomizeScreen(createWorldScreen));
                            return false;
                        }
                    }
                    return true;
                });
            }
        });
    }

    private static void ensureBlockModelAssetsExist() {
        String[] builtin3dBlocks = {
                "pumpjack", "oil_refinery", "chemical_plant", "crude_oil_deposit",
                "lab", "assembler", "big_electric_pole", "substation",
                "gun_turret", "laser_turret", "flamethrower_turret",
                "storage_tank", "solar_panel", "accumulator"
        };
        String itemModelJson = "{\n" +
                "  \"parent\": \"minecraft:builtin/entity\",\n" +
                "  \"gui_light\": \"side\",\n" +
                "  \"display\": {\n" +
                "    \"gui\": { \"rotation\": [ 30, 225, 0 ], \"translation\": [ 0, 0, 0 ], \"scale\": [ 0.625, 0.625, 0.625 ] },\n" +
                "    \"ground\": { \"rotation\": [ 0, 0, 0 ], \"translation\": [ 0, 3, 0 ], \"scale\": [ 0.25, 0.25, 0.25 ] },\n" +
                "    \"fixed\": { \"rotation\": [ 0, 0, 0 ], \"translation\": [ 0, 0, 0 ], \"scale\": [ 0.5, 0.5, 0.5 ] },\n" +
                "    \"thirdperson_righthand\": { \"rotation\": [ 75, 45, 0 ], \"translation\": [ 0, 2.5, 0 ], \"scale\": [ 0.375, 0.375, 0.375 ] },\n" +
                "    \"firstperson_righthand\": { \"rotation\": [ 0, 45, 0 ], \"translation\": [ 0, 0, 0 ], \"scale\": [ 0.40, 0.40, 0.40 ] },\n" +
                "    \"firstperson_lefthand\": { \"rotation\": [ 0, 225, 0 ], \"translation\": [ 0, 0, 0 ], \"scale\": [ 0.40, 0.40, 0.40 ] }\n" +
                "  }\n" +
                "}\n";

        Path gameDir = FabricLoader.getInstance().getGameDir();
        Path projectRoot = gameDir.getParent();
        if (projectRoot == null) return;

        Path[] targetAssetRoots = {
                projectRoot.resolve("src/main/resources/assets/" + FactorioCore.MOD_ID),
                projectRoot.resolve("build/resources/main/assets/" + FactorioCore.MOD_ID),
                gameDir.resolve("resourcepacks/FactorioCore_Sounds/assets/" + FactorioCore.MOD_ID)
        };

        for (Path assetRoot : targetAssetRoots) {
            try {
                Path itemModelsDir = assetRoot.resolve("models/item");
                Path blockstatesDir = assetRoot.resolve("blockstates");
                Files.createDirectories(itemModelsDir);
                Files.createDirectories(blockstatesDir);

                for (String blockName : builtin3dBlocks) {
                    Path itemFile = itemModelsDir.resolve(blockName + ".json");
                    if (!Files.exists(itemFile)) {
                        Files.writeString(itemFile, itemModelJson, StandardCharsets.UTF_8);
                    }
                    Path bsFile = blockstatesDir.resolve(blockName + ".json");
                    String targetModel = blockName.equals("crude_oil_deposit")
                            ? "minecraft:block/obsidian"
                            : "minecraft:block/stone";
                    String customBsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + targetModel + "\" }\n  }\n}\n";
                    Files.writeString(bsFile, customBsJson, StandardCharsets.UTF_8);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void updateElectricityRenderCache(MinecraftClient client) {
        if (client.world == null || client.player == null) {
            CACHED_WIRE_LINKS.clear();
            CACHED_POLE_NODES.clear();
            return;
        }

        long worldTime = client.world.getTime();
        BlockPos playerPos = client.player.getBlockPos();
        if (worldTime - lastWireCacheTick < 10 && playerPos.getSquaredDistance(lastWireCacheCenter) < 4.0) {
            return;
        }
        lastWireCacheTick = worldTime;
        lastWireCacheCenter = playerPos;

        CACHED_WIRE_LINKS.clear();
        CACHED_POLE_NODES.clear();

        for (int dx = -32; dx <= 32; dx++) {
            for (int dz = -32; dz <= 32; dz++) {
                for (int dy = -6; dy <= 6; dy++) {
                    BlockPos p = playerPos.add(dx, dy, dz);
                    BlockState st = client.world.getBlockState(p);
                    Block b = st.getBlock();

                    if (b instanceof ElectricPoleBlock && !ElectricPoleBlock.isMasterPoleNode(st)) {
                        continue;
                    }

                    if (b instanceof ElectricPoleBlock || b instanceof CreativeEnergySourceBlock) {
                        int supplyRadius = PowerNetworkManager.getSupplyRadius(b);
                        if (supplyRadius > 0 && Math.abs(dy) <= 5) {
                            CACHED_POLE_NODES.add(new PoleSupplyNode(p.toImmutable(), supplyRadius));
                        }

                        List<BlockPos> neighbors = PowerNetworkManager.findConnectedNodes(client.world, p, b);
                        for (BlockPos n : neighbors) {
                            if (p.asLong() < n.asLong()) {
                                CACHED_WIRE_LINKS.add(new WireLink(p.toImmutable(), n.toImmutable()));
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isNauvisPresetSelected(CreateWorldScreen screen) {
        var presetEntry = screen.getWorldCreator().getWorldType().preset();
        if (presetEntry != null && presetEntry.getKey().isPresent()) {
            return presetEntry.getKey().get().getValue().getPath().contains("nauvis");
        }
        return false;
    }

    @Nullable
    private static ButtonWidget findCustomizeButton(ParentElement root) {
        String expected = Text.translatable("selectWorld.customizeType").getString();
        for (Element child : root.children()) {
            if (child instanceof ButtonWidget btn) {
                String msg = btn.getMessage().getString();
                if (msg.equals(expected) || msg.contains("Customize") || msg.contains("Настроить")) {
                    return btn;
                }
            }
            if (child instanceof ParentElement parent) {
                ButtonWidget nested = findCustomizeButton(parent);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static boolean isMultiblockPreviewTarget(@Nullable Block block) {
        return block instanceof SplitterBlock
                || block == ModBlocks.BURNER_MINING_DRILL
                || block == ModBlocks.ELECTRIC_MINING_DRILL
                || block == ModBlocks.STONE_FURNACE
                || block == ModBlocks.STEEL_FURNACE
                || block == ModBlocks.OFFSHORE_PUMP
                || block == ModBlocks.BOILER
                || block == ModBlocks.STEAM_ENGINE
                || block == ModBlocks.LAB
                || block == ModBlocks.ASSEMBLER
                || block == ModBlocks.PUMPJACK
                || block == ModBlocks.OIL_REFINERY
                || block == ModBlocks.CHEMICAL_PLANT
                || block == ModBlocks.BIG_ELECTRIC_POLE
                || block == ModBlocks.SUBSTATION
                || block == ModBlocks.STORAGE_TANK
                || block == ModBlocks.SOLAR_PANEL
                || block == ModBlocks.ACCUMULATOR
                || block instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlock;
    }

    @Nullable
    private static PlacementPreview getMultiblockPlacementPreview(MinecraftClient client) {
        PlayerEntity player = client.player;
        World world = client.world;
        if (player == null || world == null || !(client.crosshairTarget instanceof BlockHitResult hit)) {
            return null;
        }
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        Hand activeHand = Hand.MAIN_HAND;
        ItemStack held = player.getMainHandStack();
        Block targetBlock = (held.getItem() instanceof BlockItem bi) ? bi.getBlock() : null;
        if (!isMultiblockPreviewTarget(targetBlock)) {
            held = player.getOffHandStack();
            activeHand = Hand.OFF_HAND;
            targetBlock = (held.getItem() instanceof BlockItem bi2) ? bi2.getBlock() : null;
            if (!isMultiblockPreviewTarget(targetBlock)) {
                return null;
            }
        }

        ItemPlacementContext ctx = new ItemPlacementContext(player, activeHand, held, hit);
        BlockPos originPos = ctx.getBlockPos();

        Direction facing = (targetBlock instanceof SplitterBlock)
                ? ctx.getHorizontalPlayerFacing()
                : ctx.getHorizontalPlayerFacing().getOpposite();
        if (player.isSneaking()) {
            facing = facing.getOpposite();
        }

        // 1. Солнечная панель 3x3x1 (высота ровно 1 блок, без верхнего яруса UPPER!)
        if (targetBlock == ModBlocks.SOLAR_PANEL) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(9);
            for (int part = 0; part < 9; part++) {
                BlockPos tile = SolarAndAccumulatorMachines.SolarPanelBlock.getPartPos(originPos, part);
                Box col = new Box(tile).withMaxY(tile.getY() + 0.75);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                if (!world.getWorldBorder().contains(tile) || !world.getBlockState(tile).canReplace(ctx)) {
                    valid = false;
                }
            }
            return new PlacementPreview(combined, cells, null, valid);
        }

        // 2. Постройки 2x2 (Большая опора ЛЭП, Подстанция, Аккумулятор)
        if (targetBlock == ModBlocks.BIG_ELECTRIC_POLE || targetBlock == ModBlocks.SUBSTATION || targetBlock == ModBlocks.ACCUMULATOR || targetBlock instanceof com.fixplot.factoriocore.content.combat.turret.TurretBlock) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(4);
            double maxH = (targetBlock == ModBlocks.BIG_ELECTRIC_POLE) ? 3.2 : (targetBlock == ModBlocks.ACCUMULATOR ? 2.125 : 2.5);
            for (int part = 0; part < 4; part++) {
                BlockPos tile = ElectricPoleBlock.getPartPos(originPos, part, false);
                Box col = new Box(tile).withMaxY(tile.getY() + maxH);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 1; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }
            return new PlacementPreview(combined, cells, null, valid);
        }

        if (targetBlock instanceof SplitterBlock) {
            BlockPos rightPos = originPos.offset(facing.rotateYClockwise());
            boolean canLeft = world.getBlockState(originPos).canReplace(ctx) && world.getWorldBorder().contains(originPos);
            boolean canRight = world.getBlockState(rightPos).canReplace(ctx) && world.getWorldBorder().contains(rightPos);
            Box combined = new Box(originPos).union(new Box(rightPos)).withMaxY(originPos.getY() + 0.6875);
            List<Box> cells = List.of(
                    new Box(originPos).withMaxY(originPos.getY() + 0.6875),
                    new Box(rightPos).withMaxY(rightPos.getY() + 0.6875)
            );
            return new PlacementPreview(combined, cells, null, canLeft && canRight);
        }

        if (targetBlock == ModBlocks.OFFSHORE_PUMP) {
            Direction pumpFacing = player.getHorizontalFacing();
            if (player.isSneaking()) pumpFacing = pumpFacing.getOpposite();
            BlockPos snappedPos = OffshorePumpBlock.resolveShoreMasterPos(world, originPos, pumpFacing);
            boolean validShore = OffshorePumpBlock.isValidShorelinePlacement(world, snappedPos, pumpFacing, ctx);
            Box shoreCol = new Box(snappedPos).withMaxY(snappedPos.getY() + 1.0);
            Box waterCol = new Box(snappedPos.offset(pumpFacing)).withMinY(snappedPos.getY() - 1.25).withMaxY(snappedPos.getY() + 1.0);
            Box outPipe = new Box(snappedPos.offset(pumpFacing.getOpposite())).withMaxY(snappedPos.getY() + 0.35);
            return new PlacementPreview(shoreCol.union(waterCol), List.of(shoreCol, waterCol), outPipe, validShore);
        }

        if (targetBlock == ModBlocks.BOILER) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(6);
            for (int part = 0; part < 6; part++) {
                BlockPos tile = BoilerBlock.getPartPos(originPos, facing, part, false);
                Box col = new Box(tile).withMaxY(tile.getY() + 2.5);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 2; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }
            BlockPos steamOut = BoilerBlock.getPartPos(originPos, facing, 4, false).offset(facing);
            return new PlacementPreview(combined, cells, new Box(steamOut).withMaxY(steamOut.getY() + 0.35), valid);
        }

        if (targetBlock == ModBlocks.STEAM_ENGINE) {
            Direction engFacing = player.getHorizontalFacing();
            if (player.isSneaking()) engFacing = engFacing.getOpposite();
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(15);
            for (int part = 0; part < 15; part++) {
                BlockPos tile = SteamEngineBlock.getPartPos(originPos, engFacing, part, 0);
                Box col = new Box(tile).withMaxY(tile.getY() + 3.5);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 3; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }
            return new PlacementPreview(combined, cells, null, valid);
        }

        if (targetBlock == ModBlocks.OIL_REFINERY) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(25);
            for (int part = 0; part < 25; part++) {
                BlockPos tile = OilMachines.OilRefineryBlock.getPartPos(originPos, facing, part, false);
                Box col = new Box(tile).withMaxY(tile.getY() + 3.2);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 2; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }
            return new PlacementPreview(combined, cells, null, valid);
        }

        // Превью 3x3x2 для Лаборатории, Сборщика, Вышки, Химзавода и Резервуара
        if (targetBlock == ModBlocks.LAB || targetBlock == ModBlocks.ASSEMBLER
                || targetBlock == ModBlocks.PUMPJACK
                || targetBlock == ModBlocks.CHEMICAL_PLANT
                || targetBlock == ModBlocks.STORAGE_TANK) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(9);
            for (int part = 0; part < 9; part++) {
                BlockPos tile = LabBlock.getPartPos(originPos, facing, part, false);
                Box col = new Box(tile).withMaxY(tile.getY() + 2.2);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 1; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }

            Box outIndicator = null;
            if (targetBlock == ModBlocks.PUMPJACK) {
                if (!OilMachines.PumpjackBlock.hasOilInFootprint(world, originPos)) {
                    valid = false;
                }
                Direction right = facing.rotateYClockwise();
                BlockPos outPipePos = originPos.offset(right, 1).offset(facing, 2);
                outIndicator = new Box(outPipePos).withMaxY(outPipePos.getY() + 0.35);
            }
            return new PlacementPreview(combined, cells, outIndicator, valid);
        }

        if (targetBlock == ModBlocks.ELECTRIC_MINING_DRILL) {
            boolean valid = true;
            Box combined = null;
            List<Box> cells = new ArrayList<>(9);
            for (int part = 0; part < 9; part++) {
                BlockPos tile = ElectricDrillBlock.getPartPos(originPos, facing, part, false);
                Box col = new Box(tile).withMaxY(tile.getY() + 2.5);
                cells.add(col);
                combined = (combined == null) ? col : combined.union(col);
                for (int yOff = 0; yOff <= 2; yOff++) {
                    BlockPos cp = tile.up(yOff);
                    if (!world.getWorldBorder().contains(cp) || !world.getBlockState(cp).canReplace(ctx)) valid = false;
                }
            }
            if (!ElectricDrillBlock.hasOreInFootprint(world, originPos, facing)) valid = false;
            BlockPos outPos = originPos.offset(facing, 2);
            return new PlacementPreview(combined, cells, new Box(outPos).withMaxY(outPos.getY() + 0.35), valid);
        }

        boolean allValid = true;
        Box combined = null;
        List<Box> cells = new ArrayList<>(4);

        for (int part = 0; part < 4; part++) {
            BlockPos groundTile = BurnerDrillBlock.getPartPos(originPos, facing, part, false);
            Box tileColumn = new Box(groundTile).withMaxY(groundTile.getY() + 2.5);
            cells.add(tileColumn);
            combined = (combined == null) ? tileColumn : combined.union(tileColumn);

            for (int yOff = 0; yOff <= 2; yOff++) {
                BlockPos checkPos = groundTile.up(yOff);
                if (!world.getWorldBorder().contains(checkPos) || !world.getBlockState(checkPos).canReplace(ctx)) {
                    allValid = false;
                }
            }
        }

        Box outputBox = null;
        if (targetBlock == ModBlocks.BURNER_MINING_DRILL) {
            if (!BurnerDrillBlock.hasOreInFootprint(world, originPos, facing)) allValid = false;
            BlockPos outputPos = BurnerDrillBlock.getPartPos(originPos, facing, 3, false).offset(facing);
            outputBox = new Box(outputPos).withMaxY(outputPos.getY() + 0.35);
        }

        return new PlacementPreview(combined, cells, outputBox, allValid);
    }

    private static void renderMultiblockPlacementOutline(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || client.player == null || consumers == null || matrices == null) {
            return;
        }

        Vec3d cam = context.camera().getPos();
        VertexConsumer lines = consumers.getBuffer(RenderLayer.getLines());

        if (client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hit.getBlockPos();
            var hitState = client.world.getBlockState(hitPos);
            if (hitState.getBlock() instanceof ElectricDrillBlock) {
                BlockPos master = ElectricDrillBlock.getMasterPos(hitPos, hitState);
                if (FactorioConfig.INSTANCE.showDrillMiningArea) {
                    int dc = FactorioConfig.INSTANCE.drillAreaColor;
                    drawPoleSupplyZone(matrices, lines, cam, master, 2,
                            FactorioConfig.getRed(dc), FactorioConfig.getGreen(dc), FactorioConfig.getBlue(dc), 0.80f);
                }
            }
        }

        PlacementPreview preview = getMultiblockPlacementPreview(client);
        if (preview == null) {
            return;
        }

        ItemStack heldMain = client.player.getMainHandStack();
        ItemStack heldOff = client.player.getOffHandStack();
        boolean mainIsDrill = heldMain.isOf(ModBlocks.ELECTRIC_MINING_DRILL.asItem());
        boolean offIsDrill = !mainIsDrill && heldOff.isOf(ModBlocks.ELECTRIC_MINING_DRILL.asItem());

        if ((mainIsDrill || offIsDrill)
                && client.crosshairTarget instanceof BlockHitResult hit2
                && hit2.getType() == HitResult.Type.BLOCK) {
            Hand drillHand = mainIsDrill ? Hand.MAIN_HAND : Hand.OFF_HAND;
            ItemStack drillStack = mainIsDrill ? heldMain : heldOff;
            ItemPlacementContext ctx = new ItemPlacementContext(client.player, drillHand, drillStack, hit2);
            if (FactorioConfig.INSTANCE.showDrillMiningArea) {
                int dc = FactorioConfig.INSTANCE.drillAreaColor;
                drawPoleSupplyZone(matrices, lines, cam, ctx.getBlockPos(), 2,
                        FactorioConfig.getRed(dc), FactorioConfig.getGreen(dc), FactorioConfig.getBlue(dc), 0.85f);
            }
        }

        int outlineRgb = preview.valid
                ? FactorioConfig.INSTANCE.validPlacementColor
                : FactorioConfig.INSTANCE.invalidPlacementColor;
        float r = FactorioConfig.getRed(outlineRgb);
        float g = FactorioConfig.getGreen(outlineRgb);
        float b = FactorioConfig.getBlue(outlineRgb);

        Box outer = preview.combinedBox.offset(-cam.x, -cam.y, -cam.z).expand(0.004);
        WorldRenderer.drawBox(matrices, lines, outer, r, g, b, 0.95f);

        for (Box cell : preview.cellBoxes) {
            WorldRenderer.drawBox(matrices, lines, cell.offset(-cam.x, -cam.y, -cam.z), r, g, b, 0.45f);
        }

        if (preview.outputIndicatorBox != null) {
            Box outBox = preview.outputIndicatorBox.offset(-cam.x, -cam.y, -cam.z).expand(0.003);
            WorldRenderer.drawBox(matrices, lines, outBox, 0.16f, 0.85f, 1.0f, 0.98f);

            Direction outDir = client.player.getHorizontalFacing().getOpposite();
            if (client.player.isSneaking()) outDir = outDir.getOpposite();
            double cx = (outBox.minX + outBox.maxX) * 0.5;
            double cy = outBox.maxY + 0.04;
            double cz = (outBox.minZ + outBox.maxZ) * 0.5;
            double fx = outDir.getOffsetX();
            double fz = outDir.getOffsetZ();
            double px = -fz;
            double pz = fx;

            MatrixStack.Entry entry = matrices.peek();
            float tx = (float) (cx + fx * 0.28);
            float tz = (float) (cz + fz * 0.28);
            float bx1 = (float) (cx - fx * 0.22 - px * 0.24);
            float bz1 = (float) (cz - fz * 0.22 - pz * 0.24);
            float bx2 = (float) (cx - fx * 0.22 + px * 0.24);
            float bz2 = (float) (cz - fz * 0.22 + pz * 0.24);
            float fy = (float) cy;

            lines.vertex(entry, bx1, fy, bz1).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, tx, fy, tz).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, bx2, fy, bz2).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, tx, fy, tz).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, bx1, fy, bz1).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, bx2, fy, bz2).color(1.0f, 0.88f, 0.15f, 1.0f).normal(entry, 0.0f, 1.0f, 0.0f);
        }
    }

    private static void spawnMultiblockPlacementParticles(MinecraftClient client) {
        if (!FactorioConfig.INSTANCE.enablePlacementParticles
                || client.isPaused()
                || client.world == null
                || client.world.getTime() % 3 != 0) {
            return;
        }
        PlacementPreview preview = getMultiblockPlacementPreview(client);
        if (preview == null) {
            return;
        }

        int pCol = preview.valid
                ? FactorioConfig.INSTANCE.validPlacementColor
                : FactorioConfig.INSTANCE.invalidPlacementColor;
        if (pCol != cachedParticleRgb) {
            cachedParticleRgb = pCol;
            cachedPlacementParticle = new DustParticleEffect(
                    new Vector3f(FactorioConfig.getRed(pCol), FactorioConfig.getGreen(pCol), FactorioConfig.getBlue(pCol)),
                    0.65f
            );
        }

        Box box = preview.combinedBox;
        for (int i = 0; i <= 4; i++) {
            double t = i / 4.0;
            double px = MathHelper.lerp(t, box.minX, box.maxX);
            double pz = MathHelper.lerp(t, box.minZ, box.maxZ);

            client.world.addParticle(cachedPlacementParticle, px, box.minY + 0.05, box.minZ, 0.0, 0.0, 0.0);
            client.world.addParticle(cachedPlacementParticle, px, box.minY + 0.05, box.maxZ, 0.0, 0.0, 0.0);
            client.world.addParticle(cachedPlacementParticle, box.minX, box.minY + 0.05, pz, 0.0, 0.0, 0.0);
            client.world.addParticle(cachedPlacementParticle, box.maxX, box.minY + 0.05, pz, 0.0, 0.0, 0.0);

            client.world.addParticle(cachedPlacementParticle, px, box.maxY, box.minZ, 0.0, 0.0, 0.0);
            client.world.addParticle(cachedPlacementParticle, px, box.maxY, box.maxZ, 0.0, 0.0, 0.0);
        }

        if (preview.outputIndicatorBox != null) {
            Box out = preview.outputIndicatorBox;
            client.world.addParticle(YELLOW_PARTICLE, out.getCenter().x, out.maxY, out.getCenter().z, 0.0, 0.0, 0.0);
        }
    }

    private static void renderElectricityOverlayAndWires(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || context.consumers() == null || context.matrixStack() == null) {
            return;
        }

        Vec3d cam = context.camera().getPos();
        MatrixStack matrices = context.matrixStack();
        VertexConsumer lines = context.consumers().getBuffer(RenderLayer.getLines());

        if (FactorioConfig.INSTANCE.enableCopperWiresRender && !CACHED_WIRE_LINKS.isEmpty()) {
            int wc = FactorioConfig.INSTANCE.wireColor;
            float wr = FactorioConfig.getRed(wc);
            float wg = FactorioConfig.getGreen(wc);
            float wb = FactorioConfig.getBlue(wc);
            for (WireLink link : CACHED_WIRE_LINKS) {
                drawCopperWire(client.world, matrices, lines, cam, link.a(), link.b(), wr, wg, wb, 0.9f);
            }
        }

        Hand electricHand = Hand.MAIN_HAND;
        ItemStack held = client.player.getMainHandStack();
        Block heldBlock = (held.getItem() instanceof BlockItem bi) ? bi.getBlock() : null;
        if (!isElectricRelatedBlock(heldBlock)) {
            held = client.player.getOffHandStack();
            electricHand = Hand.OFF_HAND;
            heldBlock = (held.getItem() instanceof BlockItem bi2) ? bi2.getBlock() : null;
        }

        if (FactorioConfig.INSTANCE.showPoleSupplyWhenHoldingElectric && isElectricRelatedBlock(heldBlock)) {
            int pc = FactorioConfig.INSTANCE.poleSupplyColor;
            float pr = FactorioConfig.getRed(pc);
            float pg = FactorioConfig.getGreen(pc);
            float pb = FactorioConfig.getBlue(pc);
            for (PoleSupplyNode node : CACHED_POLE_NODES) {
                drawPoleSupplyZone(matrices, lines, cam, node.pos(), node.radius(), pr, pg, pb, 0.65f);
            }
        }

        int supplyRadius = PowerNetworkManager.getSupplyRadius(heldBlock);
        if (supplyRadius > 0 && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            ItemPlacementContext ctx = new ItemPlacementContext(client.player, electricHand, held, hit);
            BlockPos targetPos = ctx.getBlockPos();
            int pc2 = FactorioConfig.INSTANCE.poleSupplyColor;
            drawPoleSupplyZone(matrices, lines, cam, targetPos, supplyRadius,
                    FactorioConfig.getRed(pc2), FactorioConfig.getGreen(pc2), FactorioConfig.getBlue(pc2), 0.90f);

            List<BlockPos> previewLinks = PowerNetworkManager.findConnectedNodes(client.world, targetPos, heldBlock);
            for (BlockPos link : previewLinks) {
                drawCopperWire(client.world, matrices, lines, cam, targetPos, link, 0.2f, 0.9f, 1.0f, 0.95f);
            }
        }
    }

    private static boolean isElectricRelatedBlock(@Nullable Block block) {
        if (block == null) return false;
        return block == ModBlocks.SMALL_ELECTRIC_POLE
                || block == ModBlocks.MEDIUM_ELECTRIC_POLE
                || block == ModBlocks.BIG_ELECTRIC_POLE
                || block == ModBlocks.SUBSTATION
                || block == ModBlocks.ELECTRIC_ENERGY_INTERFACE
                || block == ModBlocks.INSERTER
                || block == ModBlocks.LONG_HANDED_INSERTER
                || block == ModBlocks.FAST_INSERTER
                || block == ModBlocks.ASSEMBLER
                || block == ModBlocks.LAB
                || block == ModBlocks.ELECTRIC_MINING_DRILL
                || block == ModBlocks.STEAM_ENGINE
                || block == ModBlocks.PUMPJACK
                || block == ModBlocks.OIL_REFINERY
                || block == ModBlocks.CHEMICAL_PLANT
                || block == ModBlocks.SOLAR_PANEL
                || block == ModBlocks.ACCUMULATOR;
    }

    private static void drawPoleSupplyZone(MatrixStack matrices, VertexConsumer lines, Vec3d cam,
                                           BlockPos centerPos, int radius, float r, float g, float b, float alpha) {
        double minX = centerPos.getX() - radius - cam.x;
        double minZ = centerPos.getZ() - radius - cam.z;
        double maxX = centerPos.getX() + radius + 1 - cam.x;
        double maxZ = centerPos.getZ() + radius + 1 - cam.z;
        double y = centerPos.getY() + 0.04 - cam.y;

        Box supplyBox = new Box(minX, y, minZ, maxX, y + 0.06, maxZ);
        WorldRenderer.drawBox(matrices, lines, supplyBox, r, g, b, alpha);

        MatrixStack.Entry entry = matrices.peek();
        for (int step = 1; step < (radius * 2 + 1); step++) {
            float gx = (float) (minX + step);
            float gz = (float) (minZ + step);
            float fy = (float) (y + 0.02);
            lines.vertex(entry, gx, fy, (float) minZ).color(r, g, b, alpha * 0.45f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, gx, fy, (float) maxZ).color(r, g, b, alpha * 0.45f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, (float) minX, fy, gz).color(r, g, b, alpha * 0.45f).normal(entry, 0.0f, 1.0f, 0.0f);
            lines.vertex(entry, (float) maxX, fy, gz).color(r, g, b, alpha * 0.45f).normal(entry, 0.0f, 1.0f, 0.0f);
        }
    }

    private static Vec3d getWireAttachmentPoint(World world, BlockPos pos) {
        Block b = world.getBlockState(pos).getBlock();
        if (b == ModBlocks.BIG_ELECTRIC_POLE) {
            return new Vec3d(pos.getX() + 1.0, pos.getY() + 3.15, pos.getZ() + 1.0);
        }
        if (b == ModBlocks.SUBSTATION) {
            return new Vec3d(pos.getX() + 1.0, pos.getY() + 2.45, pos.getZ() + 1.0);
        }
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.92, pos.getZ() + 0.5);
    }

    private static void drawCopperWire(World world, MatrixStack matrices, VertexConsumer lines, Vec3d cam,
                                       BlockPos a, BlockPos b, float r, float g, float bl, float alpha) {
        Vec3d pa = getWireAttachmentPoint(world, a);
        Vec3d pb = getWireAttachmentPoint(world, b);

        float x1 = (float) (pa.x - cam.x);
        float y1 = (float) (pa.y - cam.y);
        float z1 = (float) (pa.z - cam.z);
        float x2 = (float) (pb.x - cam.x);
        float y2 = (float) (pb.y - cam.y);
        float z2 = (float) (pb.z - cam.z);

        MatrixStack.Entry entry = matrices.peek();
        lines.vertex(entry, x1, y1, z1).color(r, g, bl, alpha).normal(entry, 0.0f, 1.0f, 0.0f);
        lines.vertex(entry, x2, y2, z2).color(r, g, bl, alpha).normal(entry, 0.0f, 1.0f, 0.0f);
    }

    private record PlacementPreview(Box combinedBox, List<Box> cellBoxes, @Nullable Box outputIndicatorBox, boolean valid) {}
}