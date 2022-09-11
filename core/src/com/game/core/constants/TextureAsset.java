package com.game.core.constants;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public enum TextureAsset {

    MET("Met.txt"),
    FIRE("Fire.txt"),
    SAW("SawBeam.txt"),
    ITEMS("Items.txt"),
    COLORS("Colors.txt"),
    ENEMIES("Enemies.txt"),
    OBJECTS("Objects.txt"),
    MEGAMAN("Megaman.txt"),
    ELECTRIC("Electric.txt"),
    HAZARDS_1("Hazards1.txt"),
    BOSS_FACES("BossFaces.txt"),
    CHARGE_ORBS("ChargeOrbs.txt"),
    DECORATIONS("Decorations.txt"),
    BITS("HealthAndWeaponBits.txt"),
    STAGE_SELECT("StageSelect.txt"),
    MEGAMAN_FIRE("MegamanFire.txt"),
    CUSTOM_TILES("CustomTiles.txt"),
    TIMBER_WOMAN("TimberWoman.txt"),
    MEGAMAN_FACES("MegamanFaces.txt"),
    BACKGROUNDS_1("Backgrounds1.txt"),
    BACKGROUNDS_2("Backgrounds2.txt"),
    MEGAMAN_MAIN_MENU("MegamanMainMenu.txt"),
    MEGAMAN_CHARGED_SHOT("MegamanChargedShot.txt");

    private static final String prefix = "sprites/SpriteSheets/";

    private final String src;

    public String getSrc() {
        return prefix + src;
    }

}
