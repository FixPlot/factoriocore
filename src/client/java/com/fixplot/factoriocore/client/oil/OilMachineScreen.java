package com.fixplot.factoriocore.client.oil;

import com.fixplot.factoriocore.content.fluids.FactorioFluidContainer;
import com.fixplot.factoriocore.content.oil.OilMachines.OilMachineScreenHandler;
import com.fixplot.factoriocore.registry.ModItems;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

import java.util.List;

public class OilMachineScreen extends HandledScreen<OilMachineScreenHandler> {

    private boolean recipeSelectionMode = false;
    private float previewYaw = -35.0f;
    private float previewPitch = 25.0f;
    private float previewZoom = 11.0f;
    private boolean draggingPreview = false;

    public OilMachineScreen(OilMachineScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 430;
        this.backgroundHeight = 224;
    }

    private void setRecipeSelectionMode(boolean mode) {
        this.recipeSelectionMode = mode;
        this.handler.setSlotsVisible(!mode);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int machineType = handler.getMachineType();
        int recipeMode = handler.getRecipeMode();

        context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xFF141414);
        context.fill(x + 1, y + 1, x + backgroundWidth - 1, y + backgroundHeight - 1, 0xFF2D2E30);

        context.fill(x + 3, y + 3, x + backgroundWidth - 3, y + 17, 0xFF222325);
        Text headerTitle = recipeSelectionMode ? Text.translatable("gui.factoriocore.select_recipe") : this.title;
        context.drawText(this.textRenderer, headerTitle, x + 8, y + 6, 0xFFFFCC66, true);

        int startRibs = x + 14 + this.textRenderer.getWidth(headerTitle);
        int endRibs = x + backgroundWidth - 24;
        for (int rx = startRibs; rx < endRibs; rx += 3) {
            context.fill(rx, y + 5, rx + 1, y + 14, 0xFF17181A);
        }

        int closeX = x + backgroundWidth - 17;
        boolean hovClose = mouseX >= closeX && mouseX <= closeX + 13 && mouseY >= y + 4 && mouseY <= y + 16;
        context.fill(closeX, y + 4, closeX + 13, y + 16, 0xFF141414);
        context.fill(closeX + 1, y + 5, closeX + 12, y + 15, hovClose ? 0xFFB83227 : 0xFF3A3C3F);
        context.drawText(this.textRenderer, "x", closeX + 4, y + 6, 0xFFFFFFFF, false);

        if (recipeSelectionMode) {
            int gridX = x + 14;
            int gridY = y + 26;
            int cols = 10;
            int rows = 5;
            int slotSize = 26;

            context.fill(gridX - 3, gridY - 3, gridX + cols * slotSize + 3, gridY + rows * slotSize + 3, 0xFF18181A);
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int sx = gridX + c * slotSize;
                    int sy = gridY + r * slotSize;
                    context.fill(sx, sy, sx + slotSize - 2, sy + slotSize - 2, 0xFF1A1B1D);
                    context.fill(sx + 1, sy + 1, sx + slotSize - 3, sy + slotSize - 3, 0xFF242528);
                }
            }

            int totalRecipes = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) ? 11 : 3;
            for (int m = 0; m < totalRecipes; m++) {
                int col = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? (m - 7) : m;
                int row = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? 1 : 0;
                int sx = gridX + col * slotSize;
                int sy = gridY + row * slotSize;

                boolean selected = (m == recipeMode);
                boolean hov = mouseX >= sx && mouseX < sx + slotSize - 2 && mouseY >= sy && mouseY < sy + slotSize - 2;
                int border = hov ? 0xFFE59524 : (selected ? 0xFFFFCC66 : 0xFF141414);
                int bg = hov ? 0xFF525458 : (selected ? 0xFF44464A : 0xFF35373A);

                context.fill(sx, sy, sx + slotSize - 2, sy + slotSize - 2, border);
                context.fill(sx + 1, sy + 1, sx + slotSize - 3, sy + slotSize - 3, bg);
                drawRecipeBadgeIcon(context, machineType, m, sx + 4, sy + 4);
            }
            return;
        }

        int leftX = x + 6;
        int leftW = 180;
        int panelH = backgroundHeight - 26;
        context.fill(leftX, y + 20, leftX + leftW, y + 20 + panelH, 0xFF141414);
        context.fill(leftX + 1, y + 21, leftX + leftW - 1, y + 19 + panelH, 0xFF242527);

        context.fill(leftX + 2, y + 22, leftX + leftW - 2, y + 34, 0xFF1C1D1E);
        context.drawText(this.textRenderer, Text.translatable("gui.factoriocore.character"), leftX + 6, y + 24, 0xFFE6E6E6, false);

        int invStartX = x + 12;
        int invStartY = y + 36;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                drawSlotFrame(context, invStartX + c * 18, invStartY + r * 18);
            }
        }

        context.fill(leftX + 4, y + 92, leftX + leftW - 4, y + 93, 0xFF1A1B1C);
        context.fill(leftX + 4, y + 93, leftX + leftW - 4, y + 94, 0xFF3A3C3F);

        for (int c = 0; c < 9; c++) {
            drawSlotFrame(context, invStartX + c * 18, y + 96);
        }

        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 9; c++) {
                drawGhostSlot(context, invStartX + c * 18, y + 118 + r * 18);
            }
        }

        int rightX = x + 192;
        int rightW = 232;
        context.fill(rightX, y + 20, rightX + rightW, y + 20 + panelH, 0xFF141414);
        context.fill(rightX + 1, y + 21, rightX + rightW - 1, y + 19 + panelH, 0xFF28292C);

        Text statusText;
        int ledOuter, ledInner;
        if (!handler.isPowered()) {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.no_power", "No power");
            ledOuter = 0xFF661111;
            ledInner = 0xFFFF3333;
        } else if (handler.isWorking()) {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.working", "Working");
            ledOuter = 0xFF115511;
            ledInner = 0xFF44FF44;
        } else {
            statusText = Text.translatableWithFallback("gui.factoriocore.status.missing_ingredients", "Item ingredient shortage");
            ledOuter = 0xFF664400;
            ledInner = 0xFFFFAA00;
        }

        int ledX = rightX + 6;
        int ledY = y + 25;
        context.fill(ledX, ledY, ledX + 6, ledY + 6, 0xFF111111);
        context.fill(ledX + 1, ledY + 1, ledX + 5, ledY + 5, ledOuter);
        context.fill(ledX + 2, ledY + 2, ledX + 4, ledY + 4, ledInner);
        context.drawText(this.textRenderer, statusText, rightX + 16, y + 24, 0xFFE6E6E6, false);

        int viewLeft = rightX + 6;
        int viewTop = y + 34;
        int viewRight = rightX + rightW - 6;
        int viewBottom = y + 110;
        context.fill(viewLeft - 1, viewTop - 1, viewRight + 1, viewBottom + 1, 0xFF141414);
        drawCheckerboard(context, viewLeft, viewTop, viewRight, viewBottom, 8);

        context.enableScissor(viewLeft, viewTop, viewRight, viewBottom);
        renderMachine3DPreview(context, (viewLeft + viewRight) / 2, (viewTop + viewBottom) / 2 + 10, delta, machineType);
        context.disableScissor();

        int recBtnX = rightX + 6;
        int recBtnY = y + 116;
        context.fill(recBtnX, recBtnY, recBtnX + 22, recBtnY + 22, 0xFF141414);
        context.fill(recBtnX + 1, recBtnY + 1, recBtnX + 21, recBtnY + 21, 0xFF35373A);
        drawRecipeBadgeIcon(context, machineType, recipeMode, recBtnX + 3, recBtnY + 3);

        String recipeTitle = this.textRenderer.trimToWidth(getRecipeDisplayName(machineType, recipeMode), 160);
        context.drawText(this.textRenderer, recipeTitle, rightX + 34, y + 123, 0xFFE6E6E6, false);

        if (machineType != OilMachineScreenHandler.TYPE_PUMPJACK) {
            int gearX = rightX + rightW - 28;
            int gearY = y + 116;
            boolean hovGear = mouseX >= gearX && mouseX <= gearX + 22 && mouseY >= gearY && mouseY <= gearY + 22;
            context.fill(gearX, gearY, gearX + 22, gearY + 22, 0xFF141414);
            context.fill(gearX + 1, gearY + 1, gearX + 21, gearY + 21, hovGear ? 0xFFE59524 : 0xFF383A3D);
            drawGearIcon(context, gearX + 3, gearY + 3, hovGear ? 0xFF141414 : 0xFFE6E6E6);
        }

        int slotRowY = y + 143;

        if (machineType == OilMachineScreenHandler.TYPE_REFINERY) {
            drawSlotFrame(context, rightX + 6, slotRowY);
            drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFF18181E, 0xFF484852);
            if (recipeMode >= 1) {
                drawSlotFrame(context, rightX + 25, slotRowY);
                drawFluidDropIcon(context, rightX + 26, slotRowY + 1, 0xFF1E88E5, 0xFF80D8FF);
            }
            if (recipeMode == 2) {
                drawSlotFrame(context, x + 216, slotRowY);
                if (handler.getSlot(0).getStack().isEmpty()) {
                    context.drawItem(new ItemStack(Items.COAL), x + 217, slotRowY + 1);
                    context.fill(x + 217, slotRowY + 1, x + 233, slotRowY + 17, 0x883A1818);
                }
            }
        } else if (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) {
            drawSlotFrame(context, rightX + 6, slotRowY);
            if (recipeMode == 0 || recipeMode == 4 || recipeMode == 5) {
                drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFFB03A2E, 0xFFF1948A);
            } else if (recipeMode == 1 || recipeMode == 3) {
                drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFFD68910, 0xFFF8C471);
            } else if (recipeMode == 2 || recipeMode == 7 || recipeMode == 8) {
                drawGasMoleculeIcon(context, rightX + 7, slotRowY + 1);
            } else if (recipeMode == 9) {
                drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFFD4AC0D, 0xFFFFF176);
            } else {
                drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFF1E88E5, 0xFF80D8FF);
            }

            if (recipeMode == 0 || recipeMode == 1 || recipeMode == 8) {
                drawSlotFrame(context, rightX + 25, slotRowY);
                drawFluidDropIcon(context, rightX + 26, slotRowY + 1, 0xFF1E88E5, 0xFF80D8FF);
            }

            if (recipeMode == 6 || recipeMode == 7 || recipeMode == 9 || recipeMode == 10) {
                drawSlotFrame(context, x + 216, slotRowY);
                if (handler.getSlot(0).getStack().isEmpty()) {
                    ItemStack g0 = switch (recipeMode) {
                        case 6, 10 -> new ItemStack(ModItems.SULFUR);
                        case 7 -> new ItemStack(Items.COAL);
                        default -> new ItemStack(Items.IRON_INGOT);
                    };
                    context.drawItem(g0, x + 217, slotRowY + 1);
                    context.fill(x + 217, slotRowY + 1, x + 233, slotRowY + 17, 0x883A1818);
                }
            }
            if (recipeMode == 6 || recipeMode == 9 || recipeMode == 10) {
                drawSlotFrame(context, x + 235, slotRowY);
                if (handler.getSlot(1).getStack().isEmpty()) {
                    ItemStack g1 = switch (recipeMode) {
                        case 6 -> new ItemStack(Items.IRON_INGOT);
                        case 9 -> new ItemStack(Items.COPPER_INGOT);
                        default -> new ItemStack(Items.COAL);
                    };
                    context.drawItem(g1, x + 236, slotRowY + 1);
                    context.fill(x + 236, slotRowY + 1, x + 252, slotRowY + 17, 0x883A1818);
                }
            }
        } else {
            drawSlotFrame(context, rightX + 6, slotRowY);
            drawFluidDropIcon(context, rightX + 7, slotRowY + 1, 0xFF18181E, 0xFF484852);
        }

        int barX = rightX + 66;
        int barY = y + 146;
        int barW = (machineType == OilMachineScreenHandler.TYPE_REFINERY && recipeMode >= 1) ? 94 : 132;
        int barH = 12;
        context.fill(barX, barY, barX + barW, barY + barH, 0xFF141414);
        context.fill(barX + 1, barY + 1, barX + barW - 1, barY + barH - 1, 0xFF222325);

        float ratio = MathHelper.clamp(handler.getProgress() / (float) handler.getMaxProgress(), 0.0f, 1.0f);
        int fillW = Math.round(ratio * (barW - 2));
        if (fillW > 0) {
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + barH - 1, 0xFF38B828);
            context.fill(barX + 1, barY + 1, barX + 1 + fillW, barY + 4, 0xFF5CE64C);
        }
        String pctStr = Math.round(ratio * 100.0f) + "%";
        int pctW = this.textRenderer.getWidth(pctStr);
        context.drawText(this.textRenderer, pctStr, barX + barW - pctW - 4, barY + 2, 0xFFFFFFFF, true);

        int outX = rightX + rightW - 24;
        if (machineType == OilMachineScreenHandler.TYPE_REFINERY && recipeMode >= 1) {
            drawSlotFrame(context, outX - 38, slotRowY);
            drawFluidDropIcon(context, outX - 37, slotRowY + 1, 0xFFB03A2E, 0xFFF1948A);
            drawSlotFrame(context, outX - 19, slotRowY);
            drawFluidDropIcon(context, outX - 18, slotRowY + 1, 0xFFD68910, 0xFFF8C471);
            drawSlotFrame(context, outX, slotRowY);
            drawGasMoleculeIcon(context, outX + 1, slotRowY + 1);
        } else {
            drawSlotFrame(context, outX, slotRowY);
            if (machineType == OilMachineScreenHandler.TYPE_REFINERY) {
                drawGasMoleculeIcon(context, outX + 1, slotRowY + 1);
            } else if (machineType == OilMachineScreenHandler.TYPE_PUMPJACK) {
                drawFluidDropIcon(context, outX + 1, slotRowY + 1, 0xFF18181E, 0xFF484852);
            } else if (recipeMode == 0) {
                drawFluidDropIcon(context, outX + 1, slotRowY + 1, 0xFFD68910, 0xFFF8C471);
            } else if (recipeMode == 1) {
                drawGasMoleculeIcon(context, outX + 1, slotRowY + 1);
            } else if (recipeMode == 5) {
                drawFluidDropIcon(context, outX + 1, slotRowY + 1, 0xFF27AE60, 0xFF82E0AA);
            } else if (recipeMode == 6) {
                drawFluidDropIcon(context, outX + 1, slotRowY + 1, 0xFFD4AC0D, 0xFFFFF176);
            } else if (handler.getSlot(2).getStack().isEmpty()) {
                ItemStack ghostOut = switch (recipeMode) {
                    case 2, 3, 4 -> new ItemStack(ModItems.SOLID_FUEL);
                    case 7 -> new ItemStack(ModItems.PLASTIC_BAR);
                    case 8 -> new ItemStack(ModItems.SULFUR);
                    case 9 -> new ItemStack(ModItems.BATTERY);
                    default -> new ItemStack(ModItems.EXPLOSIVES);
                };
                context.drawItem(ghostOut, outX + 1, slotRowY + 1);
                context.fill(outX + 1, slotRowY + 1, outX + 17, slotRowY + 17, 0x8828292C);
            }
        }

        context.fill(rightX + 4, y + 168, rightX + rightW - 4, y + 169, 0xFF1A1B1C);
        context.fill(rightX + 4, y + 169, rightX + rightW - 4, y + 170, 0xFF3A3C3F);
        for (int m = 0; m < 3; m++) {
            drawGhostSlot(context, rightX + 6 + m * 19, y + 174);
        }
        for (int gy = y + 198; gy <= y + 210; gy += 4) {
            context.fill(rightX + 6, gy, rightX + rightW - 6, gy + 1, 0xFF202123);
        }
    }

    private String getRecipeDisplayName(int machineType, int recipeMode) {
        if (machineType == OilMachineScreenHandler.TYPE_REFINERY) {
            return Text.translatable("recipe.factoriocore.refinery." + recipeMode).getString();
        } else if (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) {
            return Text.translatable("recipe.factoriocore.chem." + recipeMode).getString();
        }
        return Text.translatable("recipe.factoriocore.pumpjack").getString();
    }

    private void drawRecipeBadgeIcon(DrawContext context, int machineType, int mode, int ix, int iy) {
        if (machineType == OilMachineScreenHandler.TYPE_REFINERY) {
            if (mode == 0) {
                drawFluidDropIcon(context, ix, iy - 2, 0xFF141418, 0xFF555560);
                drawSmallMolecule(context, ix + 8, iy + 11);
            } else if (mode == 1) {
                drawMiniDrop(context, ix + 3, iy + 1, 0xFF29B6F6);
                drawMiniDrop(context, ix + 9, iy + 1, 0xFF222228);
                drawMiniDrop(context, ix + 1, iy + 9, 0xFFB03A2E);
                drawMiniDrop(context, ix + 6, iy + 9, 0xFF8E44AD);
                drawMiniDrop(context, ix + 11, iy + 9, 0xFFD68910);
            } else {
                context.drawItem(new ItemStack(Items.COAL), ix, iy - 1);
                drawMiniDrop(context, ix + 6, iy + 8, 0xFFB03A2E);
            }
        } else if (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) {
            switch (mode) {
                case 0 -> {
                    drawMiniDrop(context, ix + 6, iy + 1, 0xFFB03A2E);
                    drawMiniDrop(context, ix + 2, iy + 9, 0xFFD68910);
                    drawMiniDrop(context, ix + 10, iy + 9, 0xFFD68910);
                }
                case 1 -> {
                    drawMiniDrop(context, ix + 6, iy + 1, 0xFFD68910);
                    drawSmallMolecule(context, ix + 1, iy + 10);
                    drawSmallMolecule(context, ix + 8, iy + 10);
                }
                case 2 -> {
                    context.drawItem(new ItemStack(ModItems.SOLID_FUEL), ix, iy);
                    drawSmallMolecule(context, ix + 1, iy + 1);
                }
                case 3 -> {
                    context.drawItem(new ItemStack(ModItems.SOLID_FUEL), ix, iy);
                    drawMiniDrop(context, ix + 1, iy + 1, 0xFFD68910);
                }
                case 4 -> {
                    context.drawItem(new ItemStack(ModItems.SOLID_FUEL), ix, iy);
                    drawMiniDrop(context, ix + 1, iy + 1, 0xFFB03A2E);
                }
                case 5 -> drawFluidDropIcon(context, ix, iy, 0xFF27AE60, 0xFF82E0AA);
                case 6 -> drawFluidDropIcon(context, ix, iy, 0xFFD4AC0D, 0xFFFFF176);
                case 7 -> context.drawItem(new ItemStack(ModItems.PLASTIC_BAR), ix, iy);
                case 8 -> context.drawItem(new ItemStack(ModItems.SULFUR), ix, iy);
                case 9 -> context.drawItem(new ItemStack(ModItems.BATTERY), ix, iy);
                default -> context.drawItem(new ItemStack(ModItems.EXPLOSIVES), ix, iy);
            }
        } else {
            drawFluidDropIcon(context, ix, iy, 0xFF141418, 0xFF555560);
        }
    }

    private void drawFluidDropIcon(DrawContext context, int x, int y, int bodyCol, int highlightCol) {
        context.fill(x + 7, y + 2, x + 9, y + 4, bodyCol);
        context.fill(x + 6, y + 4, x + 10, y + 6, bodyCol);
        context.fill(x + 5, y + 6, x + 11, y + 8, bodyCol);
        context.fill(x + 4, y + 8, x + 12, y + 12, bodyCol);
        context.fill(x + 5, y + 12, x + 11, y + 14, bodyCol);
        context.fill(x + 6, y + 7, x + 7, y + 11, highlightCol);
    }

    private void drawMiniDrop(DrawContext context, int x, int y, int col) {
        context.fill(x + 1, y, x + 3, y + 2, col);
        context.fill(x, y + 2, x + 4, y + 5, col);
        context.fill(x + 1, y + 5, x + 3, y + 6, col);
    }

    private void drawGasMoleculeIcon(DrawContext context, int x, int y) {
        context.fill(x + 5, y + 3, x + 11, y + 8, 0xFF424248);
        context.fill(x + 2, y + 7, x + 7, y + 13, 0xFF8E24AA);
        context.fill(x + 9, y + 7, x + 14, y + 13, 0xFF8E24AA);
        context.fill(x + 3, y + 8, x + 5, y + 10, 0xFFE1BEE7);
        context.fill(x + 10, y + 8, x + 12, y + 10, 0xFFE1BEE7);
    }

    private void drawSmallMolecule(DrawContext context, int x, int y) {
        context.fill(x, y, x + 3, y + 3, 0xFFE0E0E0);
        context.fill(x + 3, y - 1, x + 6, y + 2, 0xFF555555);
        context.fill(x + 5, y + 1, x + 8, y + 4, 0xFFE0E0E0);
    }

    private void renderMachine3DPreview(DrawContext context, int centerX, int centerY, float delta, int machineType) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.getMatrices();

        matrices.push();
        matrices.translate(centerX, centerY + 8, 150.0f);
        float scale = (machineType == OilMachineScreenHandler.TYPE_REFINERY) ? (this.previewZoom * 0.65f) : this.previewZoom;
        matrices.scale(scale, -scale, scale);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(this.previewPitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(this.previewYaw));
        matrices.translate(-0.5f, -1.1f, -0.5f);

        if (machineType == OilMachineScreenHandler.TYPE_REFINERY) {
            OilMachinesRenderer.RefineryRenderer.renderRefineryModel(
                    matrices, context.getVertexConsumers(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, handler.isWorking());
        } else if (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) {
            OilMachinesRenderer.ChemicalPlantRenderer.renderChemicalPlantModel(
                    matrices, context.getVertexConsumers(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        } else {
            float angle = (handler.isWorking() && client.world != null)
                    ? (float) Math.sin((client.world.getTime() + delta) * 0.2f) * 22.0f : 0.0f;
            OilMachinesRenderer.PumpjackRenderer.renderPumpjackModel(
                    matrices, context.getVertexConsumers(),
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV, angle);
        }

        matrices.pop();
        context.draw();
    }

    private void drawSlotFrame(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF141414);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3C3E);
    }

    private void drawGhostSlot(DrawContext context, int sx, int sy) {
        context.fill(sx, sy, sx + 18, sy + 18, 0xFF1A1B1C);
        context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF222325);
    }

    private void drawCheckerboard(DrawContext context, int left, int top, int right, int bottom, int cellSize) {
        for (int py = top; py < bottom; py += cellSize) {
            for (int px = left; px < right; px += cellSize) {
                int gx = (px - left) / cellSize;
                int gy = (py - top) / cellSize;
                int color = ((gx + gy) % 2 == 0) ? 0xFF262729 : 0xFF2E3032;
                context.fill(px, py, Math.min(px + cellSize, right), Math.min(py + cellSize, bottom), color);
            }
        }
    }

    private void drawGearIcon(DrawContext context, int gx, int gy, int col) {
        context.fill(gx + 4, gy + 2, gx + 12, gy + 14, col);
        context.fill(gx + 2, gy + 4, gx + 14, gy + 12, col);
        context.fill(gx + 6, gy + 6, gx + 10, gy + 10, 0xFF141414);
    }

    private boolean isInsidePreviewViewport(double mouseX, double mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int rightX = x + 192;
        return mouseX >= rightX + 6 && mouseX <= rightX + 226 && mouseY >= y + 34 && mouseY <= y + 110;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!recipeSelectionMode && isInsidePreviewViewport(mouseX, mouseY)) {
            this.previewZoom = MathHelper.clamp(this.previewZoom + (float) verticalAmount * 1.5f, 6.0f, 26.0f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;

        if (mouseX >= x + backgroundWidth - 17 && mouseX <= x + backgroundWidth - 4 && mouseY >= y + 4 && mouseY <= y + 16) {
            if (recipeSelectionMode) {
                setRecipeSelectionMode(false);
            } else {
                this.close();
            }
            return true;
        }

        if (recipeSelectionMode) {
            int gridX = x + 14;
            int gridY = y + 26;
            int slotSize = 26;
            int machineType = handler.getMachineType();
            int totalRecipes = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) ? 11 : 3;

            for (int m = 0; m < totalRecipes; m++) {
                int col = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? (m - 7) : m;
                int row = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? 1 : 0;
                int sx = gridX + col * slotSize;
                int sy = gridY + row * slotSize;

                if (mouseX >= sx && mouseX < sx + slotSize - 2 && mouseY >= sy && mouseY < sy + slotSize - 2) {
                    if (this.client != null && this.client.interactionManager != null) {
                        this.client.interactionManager.clickButton(this.handler.syncId, m);
                    }
                    if (this.client != null && this.client.player != null) {
                        this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                    }
                    setRecipeSelectionMode(false);
                    return true;
                }
            }
            return true;
        }

        if ((button == 0 || button == 1) && isInsidePreviewViewport(mouseX, mouseY)) {
            this.draggingPreview = true;
            return true;
        }

        if (handler.getMachineType() != OilMachineScreenHandler.TYPE_PUMPJACK) {
            int rightX = x + 192;
            int rightW = 232;
            int recBtnX = rightX + 6;
            int gearX = rightX + rightW - 28;
            if ((mouseX >= recBtnX && mouseX <= recBtnX + 22 && mouseY >= y + 116 && mouseY <= y + 138)
                    || (mouseX >= gearX && mouseX <= gearX + 22 && mouseY >= y + 116 && mouseY <= y + 138)) {
                setRecipeSelectionMode(true);
                if (this.client != null && this.client.player != null) {
                    this.client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.15f);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingPreview = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (this.draggingPreview) {
            this.previewYaw += (float) deltaX * 1.5f;
            this.previewPitch = MathHelper.clamp(this.previewPitch + (float) deltaY * 1.5f, -80.0f, 80.0f);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.handler.setSlotsVisible(!this.recipeSelectionMode);
        super.render(context, mouseX, mouseY, delta);

        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        int machineType = handler.getMachineType();
        int recipeMode = handler.getRecipeMode();

        if (recipeSelectionMode) {
            int gridX = x + 14;
            int gridY = y + 26;
            int slotSize = 26;
            int totalRecipes = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT) ? 11 : 3;

            for (int m = 0; m < totalRecipes; m++) {
                int col = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? (m - 7) : m;
                int row = (machineType == OilMachineScreenHandler.TYPE_CHEM_PLANT && m >= 7) ? 1 : 0;
                int sx = gridX + col * slotSize;
                int sy = gridY + row * slotSize;

                if (mouseX >= sx && mouseX < sx + slotSize - 2 && mouseY >= sy && mouseY < sy + slotSize - 2) {
                    context.drawTooltip(this.textRenderer, List.of(
                            Text.literal(getRecipeDisplayName(machineType, m)).formatted(Formatting.GOLD),
                            Text.translatable("gui.factoriocore.click_to_select_recipe").formatted(Formatting.GRAY)
                    ), mouseX, mouseY);
                }
            }
            return;
        }

        int rightX = x + 192;
        int slotRowY = y + 143;
        int outX = rightX + 232 - 24;
        String mb = Text.translatableWithFallback("unit.factoriocore.mb", "mB").getString();
        String outTag = Text.translatable("gui.factoriocore.info.output_tag").getString();

        if (machineType == OilMachineScreenHandler.TYPE_REFINERY && recipeMode >= 1) {
            if (mouseX >= outX - 38 && mouseX < outX - 20 && mouseY >= slotRowY && mouseY < slotRowY + 18) {
                context.drawTooltip(this.textRenderer, List.of(
                        Text.literal(FactorioFluidContainer.FluidKind.HEAVY_OIL.getLocalizedName() + outTag).formatted(Formatting.RED),
                        Text.literal(handler.getFluid3Mb() + " / 4000 " + mb).formatted(Formatting.WHITE)
                ), mouseX, mouseY);
                return;
            }
            if (mouseX >= outX - 19 && mouseX < outX - 1 && mouseY >= slotRowY && mouseY < slotRowY + 18) {
                context.drawTooltip(this.textRenderer, List.of(
                        Text.literal(FactorioFluidContainer.FluidKind.LIGHT_OIL.getLocalizedName() + outTag).formatted(Formatting.GOLD),
                        Text.literal(handler.getFluid4Mb() + " / 4000 " + mb).formatted(Formatting.WHITE)
                ), mouseX, mouseY);
                return;
            }
            if (mouseX >= outX && mouseX < outX + 18 && mouseY >= slotRowY && mouseY < slotRowY + 18) {
                context.drawTooltip(this.textRenderer, List.of(
                        Text.literal(FactorioFluidContainer.FluidKind.PETROLEUM_GAS.getLocalizedName() + outTag).formatted(Formatting.LIGHT_PURPLE),
                        Text.literal(handler.getFluid5Mb() + " / 4000 " + mb).formatted(Formatting.WHITE)
                ), mouseX, mouseY);
                return;
            }
        }

        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}