package com.game.core;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.game.utils.enums.Position;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static com.game.core.ConstVals.ViewVals.*;
import static com.game.core.ConstVals.ViewVals.PPM;
import static com.game.utils.enums.Position.*;
import static lombok.AccessLevel.PRIVATE;

public class ConstVals {

    public static Vector3 getCamInitPos() {
        return new Vector3(VIEW_WIDTH * PPM / 2f, VIEW_HEIGHT * PPM / 2f, 0f);
    }

    public enum GameScreen {
        TEST_LEVEL_1, TEST_LEVEL_2, MAIN_MENU, PASSWORD, SETTINGS, EXTRAS, BOSS_SELECT, PAUSE_MENU, TIMBER_WOMAN
    }

    public enum RenderingGround {
        BACKGROUND, PLAYGROUND, UI
    }

    public enum Events {
        PLAYER_DEAD, LEVEL_PAUSED, LEVEL_UNPAUSED, LEVEL_FINISHED, SOUND_VOLUME_CHANGE
    }

    public enum LevelStatus {
        PAUSED, UNPAUSED, NONE
    }

    /**
     * DESCRIPTIONS:
     * -Timber Woman:
     *
     * Timber Woman beats Histrionic Man,
     * Histrionic Man beats Sales Man,
     * Sales Man beats Maniac Man,
     * Maniac Man beats Lighter Man,
     * Lighter Man beats Weed Man,
     * Weed Man beats Beacon Man,
     * Beacon Man beats Tsunami Man,
     * Tsunami Man beats Timber Woman
     */
    @Getter
    @RequiredArgsConstructor(access = PRIVATE)
    public enum Boss {

        TIMBER_WOMAN("Timber Woman", BOTTOM_LEFT, GameScreen.TIMBER_WOMAN) {
            @Override
            public String getBio() {
                return "Originally designed to be 'Timbre Woman', she was \n" +
                        "planned to be the finest a cappella singer in the world. \n" +
                        "But one of the programmers screwed up and typed \n" +
                        "'Timber' instead of 'Timbre' into her firmware, and \n" +
                        "as a result, she became the world's strongest \n" +
                        "lumberjack. She is an advocate for responsible \n" +
                        "forestation practices and dreams of building a \n" +
                        "'City Among the Trees' should she ever hold \n" +
                        "a position in government.";
            }
        },
        /*
        MANIAC_MAN("Maniac Man", BOTTOM_CENTER, null),
        TSUNAMI_MAN("Tsunami Man", BOTTOM_RIGHT, null),
        SALES_MAN("Sales Man", CENTER_LEFT, null),
        HISTRIONIC_MAN("Histrionic Man", CENTER_RIGHT, null),
        BEACON_MAN("Beacon Man", TOP_LEFT, null),
        WEED_MAN("Weed Man", TOP_CENTER, null),
        LIGHTER_MAN("Lighter Man", TOP_RIGHT, null)
         */
        ;

        private final String bossName;
        private final Position position;
        private final GameScreen gameScreen;

        public abstract String getBio();

        public static Boss findByName(String name) {
            /*
            for (Boss boss : values()) {
                if (name.equals(boss.getBossName())) {
                    return boss;
                }
            }
            return null;
             */
            return TIMBER_WOMAN;
        }

        public static Boss findByPos(Position position) {
            /*
            for (Boss boss : values()) {
                if (boss.getPosition().equals(position)) {
                    return boss;
                }
            }
            return null;
             */
            return TIMBER_WOMAN;
        }

        public static Boss findByPos(int x, int y) {
            return findByPos(getByGridIndex(x, y));
        }

    }

    public static class MegamanVals {
        public static final String MEGAMAN_GAME_INFO = "MegamanGameInfo";
        public static final Integer MEGAMAN_MAX_HEALTH = 30;
    }

    public static class WorldVals {
        public static final Vector2 AIR_RESISTANCE = new Vector2(1.035f, 1.025f);
        public static final float FIXED_TIME_STEP = 1f / 150f;
    }

    public static class ViewVals {
        public static final float VIEW_HEIGHT = 14f;
        public static final float VIEW_WIDTH = 16f;
        public static final float PPM = 32f;
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public enum TextureAsset {

        MET_TEXTURE_ATLAS("Met.txt"),
        SAW_BEAM_ATLAS("SawBeam.txt"),
        FIRE_TEXTURE_ATLAS("Fire.txt"),
        ITEMS_TEXTURE_ATLAS("Items.txt"),
        COLORS_TEXTURE_ATLAS("Colors.txt"),
        ENEMIES_TEXTURE_ATLAS("Enemies.txt"),
        OBJECTS_TEXTURE_ATLAS("Objects.txt"),
        MEGAMAN_TEXTURE_ATLAS("Megaman.txt"),
        BITS_ATLAS("HealthAndWeaponBits.txt"),
        HAZARDS_1_TEXTURE_ATLAS("Hazards1.txt"),
        BOSS_FACES_TEXTURE_ATLAS("BossFaces.txt"),
        CHARGE_ORBS_TEXTURE_ATLAS("ChargeOrbs.txt"),
        DECORATIONS_TEXTURE_ATLAS("Decorations.txt"),
        STAGE_SELECT_TEXTURE_ATLAS("StageSelect.txt"),
        MEGAMAN_FIRE_TEXTURE_ATLAS("MegamanFire.txt"),
        CUSTOM_TILES_TEXTURE_ATLAS("CustomTiles.txt"),
        MEGAMAN_MAIN_MENU_ATLAS("MegamanMainMenu.txt"),
        MEGAMAN_FACES_TEXTURE_ATLAS("MegamanFaces.txt"),
        BACKGROUNDS_1_TEXTURE_ATLAS("Backgrounds1.txt"),
        BACKGROUNDS_2_TEXTURE_ATLAS("Backgrounds2.txt"),
        ELECTRIC_BALL_TEXTURE_ATLAS("ElectricBall.txt"),
        MEGAMAN_CHARGED_SHOT_TEXTURE_ATLAS("MegamanChargedShot.txt");

        private static final String prefix = "sprites/SpriteSheets/";

        private final String src;

        public String getSrc() {
            return prefix + src;
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public enum MusicAsset {

        MMZ_NEO_ARCADIA_MUSIC("MMZ_NeoArcadia.mp3"),
        STAGE_SELECT_MM3_MUSIC("StageSelectMM3.mp3"),
        MMX3_INTRO_STAGE_MUSIC("MMX3_IntroStage.ogg"),
        MM11_MAIN_MENU_MUSIC("MM11_Main_Menu.mp3"),
        MM11_WILY_STAGE_MUSIC("MM11_Wily_Stage.mp3"),
        XENOBLADE_GAUR_PLAINS_MUSIC("Xenoblade_GaurPlains.ogg"),
        MMX_LEVEL_SELECT_SCREEN_MUSIC("MMX_LevelSelectScreen.ogg");

        private static final String prefix = "music/";

        private final String src;

        public String getSrc() {
            return prefix + src;
        }

    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    public enum SoundAsset {

        DINK_SOUND("Dink.mp3"),
        THUMP_SOUND("Thump.mp3"),
        WHOOSH_SOUND("Whoosh.mp3"),
        PAUSE_SOUND("PauseMenu.mp3"),
        EXPLOSION_SOUND("Explosion.mp3"),
        BEAM_OUT_SOUND("TeleportOut.mp3"),
        ENERGY_FILL_SOUND("EnergyFill.mp3"),
        SELECT_PING_SOUND("SelectPing.mp3"),
        ENEMY_BULLET_SOUND("EnemyShoot.mp3"),
        ENEMY_DAMAGE_SOUND("EnemyDamage.mp3"),
        MEGAMAN_LAND_SOUND("MegamanLand.mp3"),
        ACID_SOUND("Megaman_2_Sounds/acid.wav"),
        MEGAMAN_DAMAGE_SOUND("MegamanDamage.mp3"),
        MEGAMAN_DEFEAT_SOUND("MegamanDefeat.mp3"),
        CURSOR_MOVE_BLOOP_SOUND("CursorMoveBloop.mp3"),
        MEGA_BUSTER_CHARGING_SOUND("MegaBusterCharging.mp3"),
        AIR_SHOOTER_SOUND("Megaman_2_Sounds/air_shooter.wav"),
        ATOMIC_FIRE_SOUND("Megaman_2_Sounds/atomic_fire.wav"),
        CRASH_BOMBER_SOUND("Megaman_2_Sounds/crash_bomber.wav"),
        MEGA_BUSTER_BULLET_SHOT_SOUND("MegaBusterBulletShot.mp3"),
        MEGA_BUSTER_CHARGED_SHOT_SOUND("MegaBusterChargedShot.mp3");

        private static final String prefix = "sounds/";

        private final String src;

        public String getSrc() {
            return prefix + src;
        }

    }

}