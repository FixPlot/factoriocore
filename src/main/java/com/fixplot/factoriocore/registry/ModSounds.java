package com.fixplot.factoriocore.registry;

import com.fixplot.factoriocore.FactorioCore;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {

    public static final SoundEvent ASSEMBLER = register("machine_assembler");
    public static final SoundEvent BURNER_DRILL = register("machine_burner_drill");
    public static final SoundEvent ELECTRIC_DRILL = register("machine_electric_drill");
    public static final SoundEvent FURNACE = register("machine_furnace");
    public static final SoundEvent BOILER = register("machine_boiler");
    public static final SoundEvent STEAM_ENGINE = register("machine_steam_engine");
    public static final SoundEvent PUMPJACK = register("machine_pumpjack");
    public static final SoundEvent OIL_REFINERY = register("machine_oil_refinery");
    public static final SoundEvent CHEMICAL_PLANT = register("machine_chemical_plant");
    public static final SoundEvent LAB = register("machine_lab");
    public static final SoundEvent BELT = register("machine_belt");
    public static final SoundEvent INSERTER = register("machine_inserter");
    public static final SoundEvent GUI_CLICK = register("gui_click");
    public static final SoundEvent CRAFT_FINISHED = register("craft_finished");
    public static final SoundEvent RESEARCH_COMPLETED = register("research_completed");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.of(FactorioCore.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerSounds() {
        FactorioCore.LOGGER.info("Registered FactorioCore SoundEvents");
    }
}