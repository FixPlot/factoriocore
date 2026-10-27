package com.fixplot.factoriocore.client;

import com.fixplot.factoriocore.FactorioCore;
import com.fixplot.factoriocore.content.logistics.belt.BeltBlockEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class BeltGeoModel extends GeoModel<BeltBlockEntity> {

    @Override
    public Identifier getModelResource(BeltBlockEntity animatable) {
        // Подгружаем разные 3D-файлы моделей из папки geo в зависимости от тира конвейера
        return switch (animatable.getBeltTier()) {
            case 2 -> Identifier.of(FactorioCore.MOD_ID, "geo/fast_belt.geo.json");
            case 3 -> Identifier.of(FactorioCore.MOD_ID, "geo/express_belt.geo.json");
            default -> Identifier.of(FactorioCore.MOD_ID, "geo/belt.geo.json");
        };
    }

    @Override
    public Identifier getTextureResource(BeltBlockEntity animatable) {
        // Подгружаем соответствующие текстуры стрелочек (жёлтые, красные, синие)
        return switch (animatable.getBeltTier()) {
            case 2 -> Identifier.of(FactorioCore.MOD_ID, "textures/block/fast_belt_texture.png");
            case 3 -> Identifier.of(FactorioCore.MOD_ID, "textures/block/express_belt_texture.png");
            default -> Identifier.of(FactorioCore.MOD_ID, "textures/block/belt_texture.png");
        };
    }

    @Override
    public Identifier getAnimationResource(BeltBlockEntity animatable) {
        return Identifier.of(FactorioCore.MOD_ID, "animations/belt.animation.json");
    }
}
