package com.game.tests.screens;

import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.game.ConstVals.TextureAssets;
import com.game.ConstVals.VolumeVals;
import com.game.ConstVals.WorldVals;
import com.game.Entity;
import com.game.Message;
import com.game.MessageListener;
import com.game.System;
import com.game.animations.AnimationComponent;
import com.game.animations.AnimationSystem;
import com.game.animations.Animator;
import com.game.animations.TimedAnimation;
import com.game.behaviors.Behavior;
import com.game.behaviors.BehaviorComponent;
import com.game.behaviors.BehaviorSystem;
import com.game.behaviors.BehaviorType;
import com.game.contracts.Damageable;
import com.game.contracts.Damager;
import com.game.contracts.Faceable;
import com.game.contracts.Facing;
import com.game.controllers.*;
import com.game.core.IAssetLoader;
import com.game.core.IController;
import com.game.core.IEntitiesAndSystemsManager;
import com.game.core.IMessageDispatcher;
import com.game.debugging.DebugComponent;
import com.game.debugging.DebugSystem;
import com.game.megaman.behaviors.MegamanRun;
import com.game.screens.levels.CullOnLevelCamTrans;
import com.game.screens.levels.CullOnOutOfGameCamBounds;
import com.game.screens.levels.LevelCameraFocusable;
import com.game.sound.SoundComponent;
import com.game.sound.SoundRequest;
import com.game.sound.SoundSystem;
import com.game.sprites.SpriteComponent;
import com.game.sprites.SpriteSystem;
import com.game.tests.screens.TestDamageScreen.TestPlayer.AButtonTask;
import com.game.updatables.UpdatableComponent;
import com.game.updatables.UpdatableSystem;
import com.game.utils.*;
import com.game.utils.Timer;
import com.game.world.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.function.Supplier;

import static com.game.ConstVals.MusicAssets.MMZ_NEO_ARCADIA_MUSIC;
import static com.game.ConstVals.SoundAssets.*;
import static com.game.ConstVals.SoundAssets.THUMP_SOUND;
import static com.game.ConstVals.TextureAssets.*;
import static com.game.ConstVals.ViewVals.*;
import static com.game.ConstVals.ViewVals.PPM;
import static com.game.behaviors.BehaviorType.*;
import static com.game.controllers.ControllerButtonStatus.*;
import static com.game.controllers.ControllerButtonStatus.IS_JUST_RELEASED;
import static com.game.controllers.ControllerUtils.*;

public class TestDamageScreen extends ScreenAdapter {


    static class AssetLoader implements IAssetLoader, Disposable {

        private final AssetManager assetManager = new AssetManager();

        public AssetLoader() {
            loadAssets(Music.class,
                       MMZ_NEO_ARCADIA_MUSIC);
            loadAssets(Sound.class,
                       ENEMY_BULLET_SOUND,
                       ENEMY_DAMAGE_SOUND,
                       MEGA_BUSTER_BULLET_SHOT_SOUND,
                       MEGAMAN_LAND_SOUND,
                       MEGAMAN_DEFEAT_SOUND,
                       WHOOSH_SOUND,
                       THUMP_SOUND);
            loadAssets(TextureAtlas.class,
                       OBJECTS_TEXTURE_ATLAS,
                       MEGAMAN_TEXTURE_ATLAS,
                       DECORATIONS_TEXTURE_ATLAS);
            assetManager.finishLoading();
        }

        private <S> void loadAssets(Class<S> sClass, String... sources) {
            for (String source : sources) {
                assetManager.load(source, sClass);
            }
        }

        @Override
        public <T> T getAsset(String key, Class<T> tClass) {
            return assetManager.get(key, tClass);
        }

        @Override
        public void dispose() {
            assetManager.dispose();
        }

    }

    static class MessageDispatcher implements IMessageDispatcher {

        private final Set<MessageListener> messageListeners = new HashSet<>();
        private final Queue<Message> messageQueue = new ArrayDeque<>();

        @Override
        public void addListener(MessageListener messageListener) {
            messageListeners.add(messageListener);
        }

        @Override
        public void removeListener(MessageListener messageListener) {
            messageListeners.remove(messageListener);
        }

        @Override
        public void addMessage(Message message) {
            messageQueue.add(message);
        }

        @Override
        public void updateMessageDispatcher(float delta) {
            while (!messageQueue.isEmpty()) {
                Message message = messageQueue.poll();
                messageListeners.forEach(listener -> {
                    if (listener.isListeningForMessageFrom(message.getOwner())) {
                        listener.listenToMessage(message.getOwner(), message.getContents(), delta);
                    }
                });
            }
        }

    }

    static class EntitiesAndSystemsManager implements IEntitiesAndSystemsManager {

        private final Set<Entity> entities = new HashSet<>();
        private final Map<Class<? extends System>, System> systems = new LinkedHashMap<>();

        @Override
        public void addEntity(Entity entity) {
            entities.add(entity);
        }

        @Override
        public Collection<Entity> viewOfEntities() {
            return entities;
        }

        @Override
        public void purgeAllEntities() {
            entities.clear();
            systems.values().forEach(System::purgeAllEntities);
        }

        @Override
        public void addSystem(System system) {
            systems.put(system.getClass(), system);
        }

        @Override
        public <S extends System> S getSystem(Class<S> sClass) {
            return sClass.cast(systems.get(sClass));
        }

        @Override
        public void updateSystems(float delta) {
            Iterator<Entity> entityIterator = entities.iterator();
            while (entityIterator.hasNext()) {
                Entity entity = entityIterator.next();
                if (entity.isDead()) {
                    systems.values().forEach(system -> {
                        if (system.entityIsMember(entity)) {
                            system.removeEntity(entity);
                        }
                    });
                    entityIterator.remove();
                } else {
                    systems.values().forEach(system -> {
                        if (!system.entityIsMember(entity) && system.qualifiesMembership(entity)) {
                            system.addEntity(entity);
                        } else if (system.entityIsMember(entity) && !system.qualifiesMembership(entity)) {
                            system.removeEntity(entity);
                        }
                    });
                }
            }
            systems.values().forEach(system -> system.update(delta));
        }

    }

    @Getter
    @Setter
    static class TestDamager extends Entity implements Damager {

        private final Timer timer = new Timer(1f);

        public TestDamager(Rectangle bounds) {
            timer.setToEnd();
            addComponent(defineBodyComponent(bounds));
            addComponent(defineDebugComponent());
            addComponent(defineUpdatableComponent());
        }

        @Override
        public void onDamageInflictedTo(Class<? extends Damageable> damageableClass) {
            timer.reset();
        }

        private BodyComponent defineBodyComponent(Rectangle bounds) {
            BodyComponent bodyComponent = new BodyComponent(BodyType.ABSTRACT);
            bodyComponent.set(bounds);
            bodyComponent.setGravityOn(false);
            bodyComponent.setFriction(0f, 0f);
            bodyComponent.setAffectedByResistance(false);
            Fixture damageBox = new Fixture(this, FixtureType.DAMAGE_BOX);
            damageBox.set(UtilMethods.getScaledRect(bounds, 1.05f));
            bodyComponent.addFixture(damageBox);
            return bodyComponent;
        }

        private DebugComponent defineDebugComponent() {
            DebugComponent debugComponent = new DebugComponent();
            debugComponent.addDebugHandle(() -> getComponent(BodyComponent.class).getCollisionBox(), () -> timer.isFinished() ? Color.PURPLE : Color.RED);
            return debugComponent;
        }

        private UpdatableComponent defineUpdatableComponent() {
            UpdatableComponent updatableComponent = new UpdatableComponent();
            updatableComponent.setUpdatable(delta -> {
                if (!timer.isFinished()) {
                    timer.update(delta);
                }
            });
            return updatableComponent;
        }

    }

    @Getter
    @Setter
    static class TestPlayer extends Entity implements Damageable, Faceable, LevelCameraFocusable {

        enum AButtonTask {
            JUMP,
            AIR_DASH
        }

        private final IController controller;
        private final IAssetLoader assetLoader;
        private final IEntitiesAndSystemsManager entitiesAndSystemsManager;

        private boolean isCharging;
        private Facing facing = Facing.RIGHT;
        private AButtonTask aButtonTask = AButtonTask.JUMP;
        
        private final Timer airDashTimer = new Timer(0.25f);
        private final Timer groundSlideTimer = new Timer(0.35f);
        private final Timer wallJumpImpetusTimer = new Timer(0.2f);
        private final Timer megaBusterChargingTimer = new Timer(0.5f);
        private final Timer shootCoolDownTimer = new Timer(0.1f);
        private final Timer shootAnimationTimer = new Timer(0.5f);

        private final Timer damageTimer = new Timer(0.75f);
        private final Timer damageRecoveryTimer = new Timer(1.5f);
        private final Timer damageRecoveryBlinkTimer = new Timer(0.05f);
        private boolean recoveryBlink;

        public TestPlayer(IController controller, IAssetLoader assetLoader, IEntitiesAndSystemsManager entitiesAndSystemsManager) {
            this.controller = controller;
            this.assetLoader = assetLoader;
            this.entitiesAndSystemsManager = entitiesAndSystemsManager;
            addComponent(defineUpdatableComponent());
            addComponent(defineControllerComponent());
            addComponent(defineBehaviorComponent());
            addComponent(defineBodyComponent());
            addComponent(defineDebugComponent());
            addComponent(defineSpriteComponent());
            addComponent(defineAnimationComponent(assetLoader.getAsset(TextureAssets.MEGAMAN_TEXTURE_ATLAS, TextureAtlas.class)));
            shootCoolDownTimer.setToEnd();
            shootAnimationTimer.setToEnd();
            wallJumpImpetusTimer.setToEnd();
            damageTimer.setToEnd();
            damageRecoveryTimer.setToEnd();
        }

        @Override
        public Set<Class<? extends Damager>> getDamagerMaskSet() {
            return Set.of(TestDamager.class);
        }

        @Override
        public void takeDamageFrom(Class<? extends Damager> damagerClass) {
            if (damagerClass.equals(TestDamager.class)) {
                damageTimer.reset();
            }
        }

        @Override
        public boolean isInvincible() {
            return !damageTimer.isFinished() || !damageRecoveryTimer.isFinished();
        }

        public boolean isDamaged() {
            return !damageTimer.isFinished();
        }

        public boolean isShooting() {
            return !shootAnimationTimer.isFinished();
        }

        public void shoot() {
            if (isDamaged()) {
                return;
            }
            Vector2 trajectory = new Vector2(15f * (facing == Facing.LEFT ? -PPM : PPM), 0f);
            Vector2 spawn = getComponent(BodyComponent.class).getCenter().add(facing == Facing.LEFT ? -15f : 15f, 1f);
            if (getComponent(BehaviorComponent.class).is(WALL_SLIDING)) {
                spawn.y += 3.5f;
            } else if (!getComponent(BodyComponent.class).is(BodySense.FEET_ON_GROUND)) {
                spawn.y += 4.5f;
            }
            TextureRegion yellowBullet = assetLoader.getAsset(TextureAssets.OBJECTS_TEXTURE_ATLAS, TextureAtlas.class).findRegion("YellowBullet");
            TestBullet bullet = new TestBullet(this, trajectory, spawn, yellowBullet, assetLoader, entitiesAndSystemsManager);
            entitiesAndSystemsManager.addEntity(bullet);
            shootCoolDownTimer.reset();
            shootAnimationTimer.reset();
        }

        @Override
        public Rectangle getCurrentBoundingBox() {
            return getComponent(BodyComponent.class).getCollisionBox();
        }

        @Override
        public Rectangle getPriorBoundingBox() {
            return getComponent(BodyComponent.class).getPriorCollisionBox();
        }

        private UpdatableComponent defineUpdatableComponent() {
            UpdatableComponent updatableComponent = new UpdatableComponent();
            updatableComponent.setUpdatable(delta -> {
                damageTimer.update(delta);
                if (damageTimer.isJustFinished()) {
                    damageRecoveryTimer.reset();
                }
                wallJumpImpetusTimer.update(delta);
                shootCoolDownTimer.update(delta);
                shootAnimationTimer.update(delta);
                setCharging(megaBusterChargingTimer.isFinished());
            });
            return updatableComponent;
        }

        private ControllerComponent defineControllerComponent() {
            ControllerComponent controllerComponent = new ControllerComponent();
            controllerComponent.addControllerAdapter(ControllerButton.LEFT, new ControllerAdapter() {

                @Override
                public void onPressContinued(float delta) {
                    if (isDamaged()) {
                        return;
                    }
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                    if (wallJumpImpetusTimer.isFinished()) {
                        setFacing(behaviorComponent.is(WALL_SLIDING) ? Facing.RIGHT : Facing.LEFT);
                    }
                    behaviorComponent.set(RUNNING, !behaviorComponent.is(WALL_SLIDING));
                    if (bodyComponent.getVelocity().x > -MegamanRun.RUN_SPEED * PPM) {
                        bodyComponent.applyImpulse(-PPM * 50f * delta, 0f);
                    }
                }

                @Override
                public void onJustReleased(float delta) {
                    getComponent(BehaviorComponent.class).setIsNot(RUNNING);
                }

            });
            controllerComponent.addControllerAdapter(ControllerButton.RIGHT, new ControllerAdapter() {

                @Override
                public void onPressContinued(float delta) {
                    if (isDamaged()) {
                        return;
                    }
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                    if (wallJumpImpetusTimer.isFinished()) {
                        setFacing(behaviorComponent.is(WALL_SLIDING) ? Facing.LEFT : Facing.RIGHT);
                    }
                    behaviorComponent.set(RUNNING, !behaviorComponent.is(WALL_SLIDING));
                    if (bodyComponent.getVelocity().x < MegamanRun.RUN_SPEED * PPM) {
                        bodyComponent.applyImpulse(PPM * 50f * delta, 0f);
                    }
                }

                @Override
                public void onJustReleased(float delta) {
                    getComponent(BehaviorComponent.class).setIsNot(RUNNING);
                }

            });
            controllerComponent.addControllerAdapter(ControllerButton.X, new ControllerAdapter() {

                @Override
                public void onPressContinued(float delta) {
                    if (isDamaged()) {
                        megaBusterChargingTimer.reset();
                        return;
                    }
                    megaBusterChargingTimer.update(delta);
                }

                @Override
                public void onJustReleased(float delta) {
                    BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                    if (shootCoolDownTimer.isFinished() && !behaviorComponent.is(GROUND_SLIDING) && !behaviorComponent.is(AIR_DASHING)) {
                        shoot();
                    }
                    megaBusterChargingTimer.reset();
                }

            });
            return controllerComponent;
        }

        private BehaviorComponent defineBehaviorComponent() {
            BehaviorComponent behaviorComponent = new BehaviorComponent();
            Behavior wallSlide = new Behavior() {

                @Override
                protected boolean evaluate(float delta) {
                    if (isDamaged()) {
                        return false;
                    }
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    return wallJumpImpetusTimer.isFinished() && !bodyComponent.is(BodySense.FEET_ON_GROUND) &&
                            ((bodyComponent.is(BodySense.TOUCHING_WALL_SLIDE_LEFT) && controller.isPressed(ControllerButton.LEFT)) ||
                                    (bodyComponent.is(BodySense.TOUCHING_WALL_SLIDE_RIGHT) && controller.isPressed(ControllerButton.RIGHT)));
                }

                @Override
                protected void init() {
                    behaviorComponent.setIs(WALL_SLIDING);
                    setAButtonTask(TestPlayer.AButtonTask.JUMP);
                }

                @Override
                protected void act(float delta) {
                    getComponent(BodyComponent.class).applyResistanceY(1.25f);
                }

                @Override
                protected void end() {
                    behaviorComponent.setIsNot(WALL_SLIDING);
                    setAButtonTask(TestPlayer.AButtonTask.AIR_DASH);
                }

            };
            behaviorComponent.addBehavior(wallSlide);
            // Jump
            Behavior jump = new Behavior() {

                @Override
                protected boolean evaluate(float delta) {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    if (isDamaged() || controller.isPressed(ControllerButton.DOWN) || bodyComponent.is(BodySense.HEAD_TOUCHING_BLOCK)) {
                        return false;
                    }
                    return behaviorComponent.is(JUMPING) ?
                            // case 1
                            bodyComponent.getVelocity().y >= 0f && controller.isPressed(ControllerButton.A) :
                            // case 2
                            aButtonTask == TestPlayer.AButtonTask.JUMP && controller.isJustPressed(ControllerButton.A) &&
                                    (bodyComponent.is(BodySense.FEET_ON_GROUND) || behaviorComponent.is(WALL_SLIDING));
                }

                @Override
                protected void init() {
                    behaviorComponent.setIs(JUMPING);
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    if (behaviorComponent.is(WALL_SLIDING)) {
                        bodyComponent.applyImpulse((isFacing(Facing.LEFT) ? -1f : 1f) * 15f * PPM, 32f * PPM);
                        wallJumpImpetusTimer.reset();
                    } else {
                        bodyComponent.applyImpulse(0f, 18f * PPM);
                    }
                }

                @Override
                protected void act(float delta) {}

                @Override
                protected void end() {
                    behaviorComponent.setIsNot(JUMPING);
                    getComponent(BodyComponent.class).getVelocity().y = 0f;
                }

            };
            behaviorComponent.addBehavior(jump);
            // Air dash
            Behavior airDash = new Behavior() {

                @Override
                protected boolean evaluate(float delta) {
                    if (isDamaged() || behaviorComponent.is(WALL_SLIDING) || getComponent(BodyComponent.class).is(BodySense.FEET_ON_GROUND) || airDashTimer.isFinished()) {
                        return false;
                    }
                    return behaviorComponent.is(AIR_DASHING) ? controller.isPressed(ControllerButton.A) :
                            controller.isJustPressed(ControllerButton.A) && getAButtonTask() == TestPlayer.AButtonTask.AIR_DASH;
                }

                @Override
                protected void init() {
                    getComponent(BodyComponent.class).setGravityOn(false);
                    behaviorComponent.setIs(BehaviorType.AIR_DASHING);
                    setAButtonTask(TestPlayer.AButtonTask.JUMP);
                }

                @Override
                protected void act(float delta) {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    airDashTimer.update(delta);
                    bodyComponent.setVelocityY(0f);
                    if ((isFacing(Facing.LEFT) && bodyComponent.is(BodySense.TOUCHING_BLOCK_LEFT)) ||
                            (isFacing(Facing.RIGHT) && bodyComponent.is(BodySense.TOUCHING_BLOCK_RIGHT))) {
                        return;
                    }
                    float x = 12f * PPM;
                    if (isFacing(Facing.LEFT)) {
                        x *= -1f;
                    }
                    bodyComponent.setVelocityX(x);
                }

                @Override
                protected void end() {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    airDashTimer.reset();
                    bodyComponent.setGravityOn(true);
                    behaviorComponent.setIsNot(BehaviorType.AIR_DASHING);
                    if (isFacing(Facing.LEFT)) {
                        bodyComponent.applyImpulse(-5f * PPM, 0f);
                    } else {
                        bodyComponent.applyImpulse(5f * PPM, 0f);
                    }
                }
            };
            behaviorComponent.addBehavior(airDash);
            // Ground slide
            Behavior groundSlide = new Behavior() {

                @Override
                protected boolean evaluate(float delta) {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    if (behaviorComponent.is(BehaviorType.GROUND_SLIDING) && bodyComponent.is(BodySense.HEAD_TOUCHING_BLOCK)) {
                        return true;
                    }
                    if (isDamaged() || !bodyComponent.is(BodySense.FEET_ON_GROUND) || groundSlideTimer.isFinished()) {
                        return false;
                    }
                    if (!behaviorComponent.is(BehaviorType.GROUND_SLIDING)) {
                        return controller.isPressed(ControllerButton.DOWN) && controller.isJustPressed(ControllerButton.A);
                    } else {
                        return controller.isPressed(ControllerButton.DOWN) && controller.isPressed(ControllerButton.A);
                    }
                }

                @Override
                protected void init() {
                    behaviorComponent.setIs(BehaviorType.GROUND_SLIDING);
                }

                @Override
                protected void act(float delta) {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    groundSlideTimer.update(delta);
                    if (isDamaged() ||
                            (isFacing(Facing.LEFT) && bodyComponent.is(BodySense.TOUCHING_BLOCK_LEFT)) ||
                            (isFacing(Facing.RIGHT) && bodyComponent.is(BodySense.TOUCHING_BLOCK_RIGHT))) {
                        return;
                    }
                    float x = 12f * PPM;
                    if (isFacing(Facing.LEFT)) {
                        x *= -1f;
                    }
                    bodyComponent.setVelocityX(x);
                }

                @Override
                protected void end() {
                    BodyComponent bodyComponent = getComponent(BodyComponent.class);
                    groundSlideTimer.reset();
                    behaviorComponent.setIsNot(BehaviorType.GROUND_SLIDING);
                    if (isFacing(Facing.LEFT)) {
                        bodyComponent.applyImpulse(-5f * PPM, 0f);
                    } else {
                        bodyComponent.applyImpulse(5f * PPM, 0f);
                    }
                }
            };
            behaviorComponent.addBehavior(groundSlide);
            return behaviorComponent;
        }

        private BodyComponent defineBodyComponent() {
            BodyComponent bodyComponent = new BodyComponent(BodyType.DYNAMIC);
            bodyComponent.setPosition(3f * PPM, 3f * PPM);
            bodyComponent.setWidth(0.8f * PPM);
            bodyComponent.setPreProcess(delta -> {
                BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                if (behaviorComponent.is(GROUND_SLIDING)) {
                    bodyComponent.setHeight(0.45f * PPM);
                } else {
                    bodyComponent.setHeight(0.95f * PPM);
                }
                if (bodyComponent.getVelocity().y < 0f && !bodyComponent.is(BodySense.FEET_ON_GROUND)) {
                    bodyComponent.setGravity(-60f * PPM);
                } else {
                    bodyComponent.setGravity(-20f * PPM);
                }
            });
            Fixture feet = new Fixture(this, FixtureType.FEET);
            feet.setSize(10f, 1f);
            feet.setOffset(0f, -PPM / 2f);
            bodyComponent.addFixture(feet);
            Fixture head = new Fixture(this, FixtureType.HEAD);
            head.setSize(7f, 2f);
            head.setOffset(0f, PPM / 2f);
            bodyComponent.addFixture(head);
            Fixture left = new Fixture(this, FixtureType.LEFT);
            left.setSize(1f, 0.25f * PPM);
            left.setOffset(-0.45f * PPM, 0f);
            bodyComponent.addFixture(left);
            Fixture right = new Fixture(this, FixtureType.RIGHT);
            right.setSize(1f, 0.25f * PPM);
            right.setOffset(0.45f * PPM, 0f);
            bodyComponent.addFixture(right);
            Fixture hitBox = new Fixture(this, FixtureType.HIT_BOX);
            hitBox.setSize(0.8f * PPM, 0.5f * PPM);
            bodyComponent.addFixture(hitBox);
            return bodyComponent;
        }

        private DebugComponent defineDebugComponent() {
            BodyComponent bodyComponent = getComponent(BodyComponent.class);
            DebugComponent debugComponent = new DebugComponent();
            debugComponent.addDebugHandle(bodyComponent::getCollisionBox, () -> Color.GREEN);
            bodyComponent.getFixtures().forEach(fixture -> debugComponent.addDebugHandle(fixture::getFixtureBox, () -> Color.GOLD));
            return debugComponent;
        }

        private SpriteComponent defineSpriteComponent() {
            SpriteComponent spriteComponent = new SpriteComponent();
            spriteComponent.getSprite().setSize(1.65f * PPM, 1.35f * PPM);
            spriteComponent.setSpriteUpdater(delta -> {
                BodyComponent bodyComponent = getComponent(BodyComponent.class);
                BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                Sprite sprite = spriteComponent.getSprite();
                if (damageTimer.isFinished() && !damageRecoveryTimer.isFinished()) {
                    damageRecoveryTimer.update(delta);
                    damageRecoveryBlinkTimer.update(delta);
                    sprite.setAlpha(recoveryBlink ? 0f : 1f);
                    if (damageRecoveryBlinkTimer.isFinished()) {
                        recoveryBlink = !recoveryBlink;
                        damageRecoveryBlinkTimer.reset();
                    }
                } 
                if (damageRecoveryTimer.isJustFinished()) {
                    sprite.setAlpha(1f);
                }
                if (behaviorComponent.is(WALL_SLIDING)) {
                    sprite.setFlip(isFacing(Facing.RIGHT), false);
                } else {
                    sprite.setFlip(isFacing(Facing.LEFT), false);
                }
                Vector2 bottomCenter = UtilMethods.bottomCenterPoint(bodyComponent.getCollisionBox());
                sprite.setCenterX(bottomCenter.x);
                sprite.setY(bottomCenter.y);
                if (behaviorComponent.is(GROUND_SLIDING)) {
                    sprite.translateY(-.035f * PPM);
                }
            });
            return spriteComponent;
        }

        private AnimationComponent defineAnimationComponent(TextureAtlas textureAtlas) {
            Supplier<String> keySupplier = () -> {
                BodyComponent bodyComponent = getComponent(BodyComponent.class);
                BehaviorComponent behaviorComponent = getComponent(BehaviorComponent.class);
                if (isDamaged()) {
                    return "Damaged";
                } else if (behaviorComponent.is(AIR_DASHING)) {
                    return "AirDash";
                } else if (behaviorComponent.is(GROUND_SLIDING)) {
                    return "GroundSlide";
                } else if (behaviorComponent.is(WALL_SLIDING)) {
                    return isShooting() ? "WallSlideShoot" : "WallSlide";
                } else if (behaviorComponent.is(JUMPING) || !bodyComponent.is(BodySense.FEET_ON_GROUND)) {
                    return isShooting() ? "JumpShoot" : "Jump";
                } else if (bodyComponent.is(BodySense.FEET_ON_GROUND) && behaviorComponent.is(RUNNING)) {
                    return isShooting() ? "RunShoot" : "Run";
                } else if (behaviorComponent.is(CLIMBING)) {
                    return isShooting() ? "ClimbShoot" : "Climb";
                } else if (bodyComponent.is(BodySense.FEET_ON_GROUND) && Math.abs(bodyComponent.getVelocity().x) > 3f) {
                    return isShooting() ? "SlipSlideShoot" : "SlipSlide";
                } else {
                    return isShooting() ? "StandShoot" : "Stand";
                }
            };
            Map<String, TimedAnimation> animations = new HashMap<>();
            animations.put("Climb", new TimedAnimation(textureAtlas.findRegion("Climb"), 2, 0.125f));
            animations.put("ClimbShoot", new TimedAnimation(textureAtlas.findRegion("ClimbShoot")));
            animations.put("Stand", new TimedAnimation(textureAtlas.findRegion("Stand"), new float[]{1.5f, 0.15f}));
            animations.put("StandShoot", new TimedAnimation(textureAtlas.findRegion("StandShoot")));
            animations.put("Damaged", new TimedAnimation(textureAtlas.findRegion("Damaged"), 3, 0.05f));
            animations.put("Run", new TimedAnimation(textureAtlas.findRegion("Run"), 4, 0.125f));
            animations.put("RunShoot", new TimedAnimation(textureAtlas.findRegion("RunShoot"), 4, 0.125f));
            animations.put("Jump", new TimedAnimation(textureAtlas.findRegion("Jump")));
            animations.put("JumpShoot", new TimedAnimation(textureAtlas.findRegion("JumpShoot")));
            animations.put("WallSlide", new TimedAnimation(textureAtlas.findRegion("WallSlide")));
            animations.put("WallSlideShoot", new TimedAnimation(textureAtlas.findRegion("WallSlideShoot")));
            animations.put("GroundSlide", new TimedAnimation(textureAtlas.findRegion("GroundSlide")));
            animations.put("AirDash", new TimedAnimation(textureAtlas.findRegion("AirDash")));
            animations.put("SlipSlide", new TimedAnimation(textureAtlas.findRegion("SlipSlide")));
            animations.put("SlipSlideShoot", new TimedAnimation(textureAtlas.findRegion("SlipSlideShoot")));
            Animator animator = new Animator(keySupplier, animations);
            AnimationComponent animationComponent = new AnimationComponent(animator);
            return animationComponent;
        }

    }

    @Getter
    @Setter
    static class TestBullet extends Entity implements CullOnOutOfGameCamBounds, CullOnLevelCamTrans {

        private int damage;
        private Entity owner;
        private final Vector2 trajectory = new Vector2();
        private final Timer cullTimer = new Timer(0.15f);

        private final IAssetLoader assetLoader;
        private final IEntitiesAndSystemsManager entitiesAndSystemsManager;

        public TestBullet(Entity owner, Vector2 trajectory, Vector2 spawn, TextureRegion textureRegion,
                          IAssetLoader assetLoader, IEntitiesAndSystemsManager entitiesAndSystemsManager) {
            this.owner = owner;
            this.trajectory.set(trajectory);
            this.assetLoader = assetLoader;
            this.entitiesAndSystemsManager = entitiesAndSystemsManager;
            addComponent(defineSpriteComponent(textureRegion));
            addComponent(defineBodyComponent(spawn));
            addComponent(new SoundComponent());
        }

        @Override
        public Rectangle getBoundingBox() {
            return getComponent(BodyComponent.class).getCollisionBox();
        }

        @Override
        public void die() {
            super.die();
            SoundComponent soundComponent = getComponent(SoundComponent.class);
            soundComponent.request(new SoundRequest(THUMP_SOUND, false, Percentage.of(VolumeVals.HIGH_VOLUME)));
            TestDisintegration disintegration = new TestDisintegration(assetLoader, getComponent(BodyComponent.class).getCenter());
            entitiesAndSystemsManager.addEntity(disintegration);
        }

        private SpriteComponent defineSpriteComponent(TextureRegion textureRegion) {
            SpriteComponent spriteComponent = new SpriteComponent();
            Sprite sprite = spriteComponent.getSprite();
            sprite.setRegion(textureRegion);
            sprite.setSize(PPM * 1.25f, PPM * 1.25f);
            spriteComponent.setSpriteUpdater(delta -> {
                BodyComponent bodyComponent = getComponent(BodyComponent.class);
                sprite.setCenter(bodyComponent.getCenter().x, bodyComponent.getCenter().y);
            });
            return spriteComponent;
        }

        private BodyComponent defineBodyComponent(Vector2 spawn) {
            BodyComponent bodyComponent = new BodyComponent(BodyType.DYNAMIC);
            bodyComponent.setPreProcess(delta -> bodyComponent.setVelocity(trajectory));
            bodyComponent.setSize(0.1f * PPM, 0.1f * PPM);
            bodyComponent.setCenter(spawn.x, spawn.y);
            Fixture projectile = new Fixture(this, FixtureType.PROJECTILE);
            projectile.setSize(0.1f * PPM, 0.1f * PPM);
            projectile.setCenter(spawn.x, spawn.y);
            bodyComponent.addFixture(projectile);
            Fixture damageBox = new Fixture(this, FixtureType.DAMAGE_BOX);
            damageBox.setSize(0.1f * PPM, 0.1f * PPM);
            damageBox.setCenter(spawn.x, spawn.y);
            bodyComponent.addFixture(damageBox);
            return bodyComponent;
        }

    }

    static class TestDisintegration extends Entity {

        public static final float DISINTEGRATION_DURATION = 0.15f;

        private final Timer timer = new Timer(DISINTEGRATION_DURATION);

        public TestDisintegration(IAssetLoader assetLoader, Vector2 center) {
            addComponent(defineBodyComponent(center));
            addComponent(defineAnimationComponent(assetLoader));
            addComponent(defineSpriteComponent(center));
            addComponent(defineUpdatableComponent());
        }

        private BodyComponent defineBodyComponent(Vector2 center) {
            BodyComponent bodyComponent = new BodyComponent(BodyType.ABSTRACT);
            bodyComponent.setSize(PPM, PPM);
            bodyComponent.setCenter(center);
            bodyComponent.setGravityOn(false);
            bodyComponent.setFriction(0f, 0f);
            return bodyComponent;
        }

        private SpriteComponent defineSpriteComponent(Vector2 center) {
            SpriteComponent spriteComponent = new SpriteComponent();
            spriteComponent.getSprite().setSize(PPM, PPM);
            spriteComponent.getSprite().setCenter(center.x, center.y);
            return spriteComponent;
        }

        private AnimationComponent defineAnimationComponent(IAssetLoader assetLoader) {
            Map<String, TimedAnimation> animations = Map.of("Disintegration", new TimedAnimation(
                    assetLoader.getAsset(TextureAssets.DECORATIONS_TEXTURE_ATLAS, TextureAtlas.class).findRegion("Disintegration"), 3, 0.1f));
            Animator animator = new Animator(() -> "Disintegration", animations);
            return new AnimationComponent(animator);
        }

        private UpdatableComponent defineUpdatableComponent() {
            return new UpdatableComponent(delta -> {
                timer.update(delta);
                if (timer.isFinished()) {
                    die();
                }
            });
        }

    }

    static class TestWorldContactListener implements WorldContactListener {

        @Override
        public void beginContact(Contact contact, float delta) {
            if (contact.acceptMask(FixtureType.LEFT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_BLOCK_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_BLOCK_RIGHT);
            } else if (contact.acceptMask(FixtureType.LEFT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_WALL_SLIDE_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_WALL_SLIDE_RIGHT);
            } else if (contact.acceptMask(FixtureType.FEET, FixtureType.BLOCK)) {
                Entity entity = contact.maskFirstEntity();
                entity.getComponent(BodyComponent.class).setIs(BodySense.FEET_ON_GROUND);
                if (entity instanceof TestPlayer testPlayer) {
                    testPlayer.setAButtonTask(AButtonTask.JUMP);
                }
            } else if (contact.acceptMask(FixtureType.HEAD, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIs(BodySense.HEAD_TOUCHING_BLOCK);
            } else if (contact.acceptMask(FixtureType.DAMAGE_BOX, FixtureType.HIT_BOX) &&
                    contact.maskFirstEntity() instanceof Damager damager &&
                    contact.maskSecondEntity() instanceof Damageable damageable &&
                    damageable.canBeDamagedBy(damager)) {
                damageable.takeDamageFrom(damager.getClass());
                damager.onDamageInflictedTo(damageable.getClass());
            } else if (contact.acceptMask(FixtureType.PROJECTILE, FixtureType.BLOCK) &&
                    contact.maskFirstEntity() instanceof TestBullet testBullet) {
                testBullet.die();
            }
        }

        @Override
        public void continueContact(Contact contact, float delta) {
            if (contact.acceptMask(FixtureType.LEFT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_BLOCK_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_BLOCK_RIGHT);
            } else if (contact.acceptMask(FixtureType.LEFT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_WALL_SLIDE_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIs(BodySense.TOUCHING_WALL_SLIDE_RIGHT);
            } else if (contact.acceptMask(FixtureType.FEET, FixtureType.BLOCK)) {
                Entity entity = contact.maskFirstEntity();
                entity.getComponent(BodyComponent.class).setIs(BodySense.FEET_ON_GROUND);
                if (entity instanceof TestPlayer testPlayer) {
                    testPlayer.setAButtonTask(AButtonTask.JUMP);
                }
            } else if (contact.acceptMask(FixtureType.HEAD, FixtureType.BLOCK)) {
                contact.maskFirstEntity().getComponent(BodyComponent.class).setIs(BodySense.HEAD_TOUCHING_BLOCK);
            } else if (contact.acceptMask(FixtureType.DAMAGE_BOX, FixtureType.HIT_BOX) &&
                    contact.maskFirstEntity() instanceof Damager damager &&
                    contact.maskSecondEntity() instanceof Damageable damageable &&
                    damageable.canBeDamagedBy(damager)) {
                damageable.takeDamageFrom(damager.getClass());
                damager.onDamageInflictedTo(damageable.getClass());
            }
        }

        @Override
        public void endContact(Contact contact, float delta) {
            if (contact.acceptMask(FixtureType.LEFT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIsNot(BodySense.TOUCHING_BLOCK_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIsNot(BodySense.TOUCHING_BLOCK_RIGHT);
            } else if (contact.acceptMask(FixtureType.LEFT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIsNot(BodySense.TOUCHING_WALL_SLIDE_LEFT);
            } else if (contact.acceptMask(FixtureType.RIGHT, FixtureType.WALL_SLIDE_SENSOR)) {
                contact.maskFirstBody().setIsNot(BodySense.TOUCHING_WALL_SLIDE_RIGHT);
            } else if (contact.acceptMask(FixtureType.FEET, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIsNot(BodySense.FEET_ON_GROUND);
                if (contact.maskFirstEntity() instanceof TestPlayer testPlayer) {
                    testPlayer.setAButtonTask(AButtonTask.AIR_DASH);
                }
            } else if (contact.acceptMask(FixtureType.HEAD, FixtureType.BLOCK)) {
                contact.maskFirstBody().setIsNot(BodySense.HEAD_TOUCHING_BLOCK);
            }
        }

    }

    static class TestController implements IController {

        private final Map<ControllerButton, ControllerButtonStatus> controllerButtons = new HashMap<>() {{
            for (ControllerButton controllerButton : ControllerButton.values()) {
                put(controllerButton, IS_RELEASED);
            }
        }};

        @Override
        public boolean isJustPressed(ControllerButton controllerButton) {
            return controllerButtons.get(controllerButton) == IS_JUST_PRESSED;
        }

        @Override
        public boolean isPressed(ControllerButton controllerButton) {
            return controllerButtons.get(controllerButton) == IS_JUST_PRESSED || controllerButtons.get(controllerButton) == IS_PRESSED;
        }

        @Override
        public boolean isJustReleased(ControllerButton controllerButton) {
            return controllerButtons.get(controllerButton) == IS_JUST_RELEASED;
        }

        @Override
        public void updateController() {
            for (ControllerButton controllerButton : ControllerButton.values()) {
                ControllerButtonStatus status = controllerButtons.get(controllerButton);
                boolean isControllerButtonPressed = isControllerConnected() ?
                        isControllerButtonPressed(controllerButton.getControllerBindingCode()) :
                        isKeyboardButtonPressed(controllerButton.getKeyboardBindingCode());
                if (isControllerButtonPressed) {
                    if (status == IS_RELEASED || status == IS_JUST_RELEASED) {
                        controllerButtons.replace(controllerButton, IS_JUST_PRESSED);
                    } else {
                        controllerButtons.replace(controllerButton, IS_PRESSED);
                    }
                } else if (status == IS_RELEASED || status == IS_JUST_RELEASED) {
                    controllerButtons.replace(controllerButton, IS_RELEASED);
                } else {
                    controllerButtons.replace(controllerButton, IS_JUST_RELEASED);
                }
            }
        }

    }

    private TestPlayer player;
    private TestDamager testDamager;

    private AssetLoader assetLoader;
    private TestController testController;
    private MessageDispatcher messageDispatcher;
    private EntitiesAndSystemsManager entitiesAndSystemsManager;

    private Viewport uiViewport;
    private Viewport playgroundViewport;
    private ShapeRenderer shapeRenderer;
    private SpriteBatch spriteBatch;
    private FontHandle message;

    @Override
    public void show() {
        testController = new TestController();
        messageDispatcher = new MessageDispatcher();
        entitiesAndSystemsManager = new EntitiesAndSystemsManager();
        spriteBatch = new SpriteBatch();
        message = new FontHandle("Megaman10Font.ttf", 6);
        message.getFont().setColor(Color.WHITE);
        message.setText("NULL");
        message.getPosition().set(-VIEW_WIDTH * PPM / 3f, -VIEW_HEIGHT * PPM / 4f);
        shapeRenderer = new ShapeRenderer();
        uiViewport = new FitViewport(VIEW_WIDTH * PPM, VIEW_HEIGHT * PPM);
        uiViewport.getCamera().position.x = 0f;
        uiViewport.getCamera().position.y = 0f;
        playgroundViewport = new FitViewport(VIEW_WIDTH * PPM, VIEW_HEIGHT * PPM);
        assetLoader = new AssetLoader();
        entitiesAndSystemsManager.addSystem(new WorldSystem(new TestWorldContactListener(), WorldVals.AIR_RESISTANCE, WorldVals.FIXED_TIME_STEP));
        entitiesAndSystemsManager.addSystem(new UpdatableSystem());
        entitiesAndSystemsManager.addSystem(new ControllerSystem(testController));
        entitiesAndSystemsManager.addSystem(new BehaviorSystem());
        entitiesAndSystemsManager.addSystem(new SpriteSystem((OrthographicCamera) playgroundViewport.getCamera(), spriteBatch));
        entitiesAndSystemsManager.addSystem(new AnimationSystem());
        entitiesAndSystemsManager.addSystem(new SoundSystem(assetLoader));
        entitiesAndSystemsManager.addSystem(new DebugSystem(shapeRenderer, (OrthographicCamera) playgroundViewport.getCamera()));
        player = new TestPlayer(testController, assetLoader, entitiesAndSystemsManager);
        entitiesAndSystemsManager.addEntity(player);
        testDamager = new TestDamager(new Rectangle(3f * PPM, 3f * PPM, 3f * PPM, PPM));
        entitiesAndSystemsManager.addEntity(testDamager);
        defineBlocks();
    }

    private void defineBlocks() {
        // first
        Entity entity1 = new Entity();
        BodyComponent bodyComponent1 = new BodyComponent(BodyType.STATIC);
        bodyComponent1.setGravityOn(false);
        bodyComponent1.set(0f, 0f, 20f * PPM, PPM);
        bodyComponent1.setFriction(.035f, .0f);
        Fixture block1 = new Fixture(entity1, FixtureType.BLOCK);
        block1.set(0f, 0f, 20f * PPM, PPM);
        bodyComponent1.addFixture(block1);
        entity1.addComponent(bodyComponent1);
        Fixture wallSlideLeft1 = new Fixture(entity1, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideLeft1.setSize(1f, 0.8f * PPM);
        wallSlideLeft1.setOffset(-10f * PPM, 0f);
        bodyComponent1.addFixture(wallSlideLeft1);
        Fixture wallSlideRight1 = new Fixture(entity1, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideRight1.setSize(1f, 0.8f * PPM);
        wallSlideRight1.setOffset(10f * PPM, 0f);
        bodyComponent1.addFixture(wallSlideRight1);
        DebugComponent debugComponent1 = new DebugComponent();
        debugComponent1.addDebugHandle(block1::getFixtureBox, () -> Color.GREEN);
        debugComponent1.addDebugHandle(wallSlideLeft1::getFixtureBox, () -> Color.GOLD);
        debugComponent1.addDebugHandle(wallSlideRight1::getFixtureBox, () -> Color.GOLD);
        entity1.addComponent(debugComponent1);
        entitiesAndSystemsManager.addEntity(entity1);
        // second
        Entity entity2 = new Entity();
        BodyComponent bodyComponent2 = new BodyComponent(BodyType.STATIC);
        bodyComponent2.setGravityOn(false);
        bodyComponent2.setFriction(.035f, 0f);
        bodyComponent2.set(23f * PPM, 0f, 20f * PPM, PPM);
        Fixture block2 = new Fixture(entity2, FixtureType.BLOCK);
        block2.set(23f * PPM, 0f, 20f * PPM, PPM);
        bodyComponent2.addFixture(block2);
        entity2.addComponent(bodyComponent2);
        Fixture wallSlideLeft2 = new Fixture(entity2, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideLeft2.setSize(1f, 0.8f * PPM);
        wallSlideLeft2.setOffset(-10f * PPM, 0f);
        bodyComponent2.addFixture(wallSlideLeft2);
        Fixture wallSlideRight2 = new Fixture(entity2, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideRight2.setSize(1f, 0.8f * PPM);
        wallSlideRight2.setOffset(10f * PPM, 0f);
        bodyComponent2.addFixture(wallSlideRight2);
        DebugComponent debugComponent2 = new DebugComponent();
        debugComponent2.addDebugHandle(block2::getFixtureBox, () -> Color.YELLOW);
        debugComponent2.addDebugHandle(wallSlideLeft2::getFixtureBox, () -> Color.GOLD);
        debugComponent2.addDebugHandle(wallSlideRight2::getFixtureBox, () -> Color.GOLD);
        entity2.addComponent(debugComponent2);
        entitiesAndSystemsManager.addEntity(entity2);
        // third
        Entity entity3 = new Entity();
        BodyComponent bodyComponent3 = new BodyComponent(BodyType.STATIC);
        bodyComponent3.setGravityOn(false);
        bodyComponent3.setFriction(.05f, .015f);
        bodyComponent3.set(10f * PPM, 2f * PPM, PPM, 30f * PPM);
        Fixture block3 = new Fixture(entity3, FixtureType.BLOCK);
        block3.set(10f * PPM, 2f * PPM, PPM, 30f * PPM);
        bodyComponent3.addFixture(block3);
        entity3.addComponent(bodyComponent3);
        Fixture wallSlideLeft3 = new Fixture(entity3, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideLeft3.setSize(3f, 30f * PPM * .9f);
        wallSlideLeft3.setOffset((-.5f * PPM) + 1.5f, 0f);
        bodyComponent3.addFixture(wallSlideLeft3);
        Fixture wallSlideRight3 = new Fixture(entity3, FixtureType.WALL_SLIDE_SENSOR);
        wallSlideRight3.setSize(3f, 30f * PPM * .9f);
        wallSlideRight3.setOffset((.5f * PPM) - 1.5f, 0f);
        bodyComponent3.addFixture(wallSlideRight3);
        DebugComponent debugComponent3 = new DebugComponent();
        debugComponent3.addDebugHandle(block3::getFixtureBox, () -> Color.RED);
        debugComponent3.addDebugHandle(wallSlideLeft3::getFixtureBox, () -> Color.GOLD);
        debugComponent3.addDebugHandle(wallSlideRight3::getFixtureBox, () -> Color.GOLD);
        entity3.addComponent(debugComponent3);
        entitiesAndSystemsManager.addEntity(entity3);
        // fourth
        Entity entity4 = new Entity();
        BodyComponent bodyComponent4 = new BodyComponent(BodyType.STATIC);
        bodyComponent4.setGravityOn(false);
        bodyComponent4.setFriction(.05f, 0f);
        bodyComponent4.set(4f * PPM, 1.65f * PPM, 10f * PPM, PPM);
        Fixture block4 = new Fixture(entity4, FixtureType.BLOCK);
        block4.set(4f * PPM, 1.5f * PPM, 10f * PPM, PPM);
        bodyComponent4.addFixture(block4);
        entity4.addComponent(bodyComponent4);
        DebugComponent debugComponent4 = new DebugComponent();
        debugComponent4.addDebugHandle(block4::getFixtureBox, () -> Color.RED);
        entity4.addComponent(debugComponent4);
        entitiesAndSystemsManager.addEntity(entity4);
    }

    @Override
    public void render(float delta) {
        testController.updateController();
        entitiesAndSystemsManager.updateSystems(delta);
        messageDispatcher.updateMessageDispatcher(delta);
        BodyComponent bodyComponent = player.getComponent(BodyComponent.class);
        Vector2 priorCenter = UtilMethods.bottomCenterPoint(bodyComponent.getPriorCollisionBox());
        Vector2 currentCenter = UtilMethods.bottomCenterPoint(bodyComponent.getCollisionBox());
        Vector2 interpolatedCenter = UtilMethods.interpolate(priorCenter, currentCenter, delta);
        playgroundViewport.getCamera().position.x = interpolatedCenter.x;
        playgroundViewport.getCamera().position.y = interpolatedCenter.y;
        playgroundViewport.apply();
        message.setText("Behaviors: " + player.getComponent(BehaviorComponent.class).getActiveBehaviors());
        spriteBatch.setProjectionMatrix(uiViewport.getCamera().combined);
        spriteBatch.begin();
        message.draw(spriteBatch);
        spriteBatch.end();
        uiViewport.apply();
    }

    @Override
    public void resize(int width, int height) {
        uiViewport.update(width, height);
        playgroundViewport.update(width, height);
    }
    
}