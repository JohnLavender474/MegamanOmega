package com.game.entities.enemies;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.game.GameContext2d;
import com.game.animations.AnimationComponent;
import com.game.animations.TimedAnimation;
import com.game.damage.DamageNegotiation;
import com.game.damage.Damager;
import com.game.debugging.DebugMessageComponent;
import com.game.debugging.DebugRectComponent;
import com.game.entities.contracts.Faceable;
import com.game.entities.contracts.Facing;
import com.game.entities.megaman.Megaman;
import com.game.entities.projectiles.Bullet;
import com.game.entities.projectiles.ChargedShot;
import com.game.entities.projectiles.ChargedShotDisintegration;
import com.game.entities.projectiles.Fireball;
import com.game.sprites.SpriteComponent;
import com.game.updatables.UpdatableComponent;
import com.game.utils.enums.Position;
import com.game.utils.objects.Timer;
import com.game.utils.objects.Wrapper;
import com.game.world.BodyComponent;
import com.game.world.Fixture;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.function.Supplier;

import static com.badlogic.gdx.graphics.Color.*;
import static com.game.ConstVals.RenderingGround.*;
import static com.game.ConstVals.TextureAsset.*;
import static com.game.ConstVals.ViewVals.PPM;
import static com.game.entities.contracts.Facing.F_LEFT;
import static com.game.entities.contracts.Facing.F_RIGHT;
import static com.game.entities.enemies.Dragonfly.DragonFlyBehavior.*;
import static com.game.utils.UtilMethods.*;
import static com.game.utils.enums.Position.*;
import static com.game.world.BodyType.*;
import static com.game.world.FixtureType.*;

public class Dragonfly extends AbstractEnemy implements Faceable {

    public enum DragonFlyBehavior {
        MOVE_UP, MOVE_DOWN, MOVE_HORIZONTAL
    }

    private static final float VERT_SPEED = 18f;
    private static final float HORIZ_SPEED = 14f;
    private static final float VERT_SCANNER_OFFSET = 2f;
    private static final float HORIZ_SCANNER_OFFSET = 3f;

    private final Camera camera;
    private final Timer changeBehaviorLapse = new Timer(.35f);

    private boolean toLeftBounds;
    private DragonFlyBehavior currentBehavior;
    private DragonFlyBehavior previousBehavior;

    @Getter
    @Setter
    private Facing facing;

    public Dragonfly(GameContext2d gameContext, Supplier<Megaman> megamanSupplier, Vector2 spawn) {
        super(gameContext, megamanSupplier, .1f, 3f);
        addComponent(defineSpriteComponent());
        addComponent(defineAnimationComponent());
        addComponent(defineBodyComponent(spawn));
        addComponent(defineDebugRectComponent());
        addComponent(defineUpdatableComponent());
        addComponent(new DebugMessageComponent());
        currentBehavior = previousBehavior = MOVE_UP;
        camera = gameContext.getViewport(PLAYGROUND).getCamera();
    }

    @Override
    protected Map<Class<? extends Damager>, DamageNegotiation> defineDamageNegotiations() {
        return Map.of(
                Bullet.class, new DamageNegotiation(5),
                Fireball.class, new DamageNegotiation(30),
                ChargedShot.class, new DamageNegotiation(30),
                ChargedShotDisintegration.class, new DamageNegotiation(15));
    }

    @Override
    public void onDeath() {
        System.out.println("Culled dragonfly");
    }

    private UpdatableComponent defineUpdatableComponent() {
        return new UpdatableComponent(new StandardEnemyUpdater() {
            @Override
            public void update(float delta) {
                super.update(delta);
                getComponent(DebugMessageComponent.class).debugMessage(1, "Current: " + currentBehavior);
                getComponent(DebugMessageComponent.class).debugMessage(2, "Previous: " + previousBehavior);
            }
        });
    }

    private BodyComponent defineBodyComponent(Vector2 spawn) {
        BodyComponent bodyComponent = new BodyComponent(ABSTRACT);
        bodyComponent.setSize(.75f * PPM, .75f * PPM);
        bodyComponent.setCenter(spawn);
        // damageable box
        Fixture damageableBox = new Fixture(this, DAMAGEABLE_BOX);
        damageableBox.setSize(.75f * PPM, .75f * PPM);
        bodyComponent.addFixture(damageableBox);
        // damager box
        Fixture damagerBox = new Fixture(this, DAMAGER_BOX);
        damagerBox.setSize(.75f * PPM, .75f * PPM);
        bodyComponent.addFixture(damagerBox);
        // out-of-bounds scanner
        Fixture oobScanner = new Fixture(this, CUSTOM);
        oobScanner.setSize(1f, 1f);
        bodyComponent.addFixture(oobScanner);
        // Megaman scanner
        Fixture megamanScanner = new Fixture(this, CUSTOM);
        megamanScanner.setSize(32f * PPM, PPM);
        bodyComponent.addFixture(megamanScanner);
        // pre-process
        bodyComponent.setPreProcess(delta -> {
            changeBehaviorLapse.update(delta);
            if (!changeBehaviorLapse.isFinished()) {
                bodyComponent.setVelocity(Vector2.Zero);
                return;
            }
            switch (currentBehavior) {
                case MOVE_UP -> {
                    bodyComponent.setVelocity(0f, VERT_SPEED * PPM);
                    oobScanner.setOffset(0f, VERT_SCANNER_OFFSET * PPM);
                }
                case MOVE_HORIZONTAL -> {
                    bodyComponent.setVelocity((toLeftBounds ? -HORIZ_SPEED : HORIZ_SPEED) * PPM, 0f);
                    oobScanner.setOffset((toLeftBounds ? -HORIZ_SCANNER_OFFSET : HORIZ_SCANNER_OFFSET) * PPM, 0f);
                }
                case MOVE_DOWN -> {
                    bodyComponent.setVelocity(0f, -VERT_SPEED * PPM);
                    oobScanner.setOffset(0f, -VERT_SCANNER_OFFSET * PPM);
                }
            }
        });
        // post-process
        bodyComponent.setPostProcess(delta -> {
            if (!changeBehaviorLapse.isFinished()) {
                bodyComponent.setVelocity(Vector2.Zero);
                return;
            }
            switch (currentBehavior) {
                case MOVE_UP -> {
                    if (!isInCamBounds(camera, oobScanner.getFixtureBox())) {
                        changeBehavior(MOVE_HORIZONTAL);
                        toLeftBounds = isMegamanLeft();
                    }
                }
                case MOVE_HORIZONTAL -> {
                    boolean doChange = (toLeftBounds && !isMegamanLeft()) || (!toLeftBounds && isMegamanLeft());
                    if (doChange && !isInCamBounds(camera, oobScanner.getFixtureBox())) {
                        changeBehavior(previousBehavior == MOVE_UP ? MOVE_DOWN : MOVE_UP);
                    }
                }
                case MOVE_DOWN -> {
                    if (megamanScanner.getFixtureBox().contains(getMegaman().getFocus()) ||
                            (!isMegamanBelow() && !isInCamBounds(camera, oobScanner.getFixtureBox()))) {
                        changeBehavior(MOVE_HORIZONTAL);
                        toLeftBounds = isMegamanLeft();
                    }
                }
            }
        });
        return bodyComponent;
    }

    private SpriteComponent defineSpriteComponent() {
        Sprite sprite = new Sprite();
        sprite.setSize(1.5f * PPM, 1.5f * PPM);
        return new SpriteComponent(sprite, new StandardEnemySpriteAdapter() {

            @Override
            public boolean setPositioning(Wrapper<Rectangle> bounds, Wrapper<Position> position) {
                bounds.setData(getComponent(BodyComponent.class).getCollisionBox());
                position.setData(CENTER);
                return true;
            }

            @Override
            public void update(float delta) {
                if (equalsAny(currentBehavior, MOVE_UP, MOVE_DOWN)) {
                    setFacing(isMegamanLeft() ? F_LEFT : F_RIGHT);
                }
            }

            @Override
            public boolean isFlipX() {
                return isFacing(F_RIGHT);
            }

        });
    }

    private AnimationComponent defineAnimationComponent() {
        TextureAtlas textureAtlas = gameContext.getAsset(ENEMIES_TEXTURE_ATLAS.getSrc(), TextureAtlas.class);
        return new AnimationComponent(new TimedAnimation(textureAtlas.findRegion("Dragonfly"), 2, .1f));
    }

    private DebugRectComponent defineDebugRectComponent() {
        DebugRectComponent debugRectComponent = new DebugRectComponent();
        BodyComponent bodyComponent = getComponent(BodyComponent.class);
        debugRectComponent.addDebugHandle(bodyComponent::getCollisionBox, () -> RED);
        bodyComponent.getFixtures().forEach(fixture ->
                debugRectComponent.addDebugHandle(fixture::getFixtureBox, () -> BLUE));
        return debugRectComponent;
    }

    private void changeBehavior(DragonFlyBehavior behavior) {
        changeBehaviorLapse.reset();
        previousBehavior = currentBehavior;
        currentBehavior = behavior;
    }

    private boolean isMegamanLeft() {
        float thisX = getComponent(BodyComponent.class).getCenter().x;
        float megamanX = getMegaman().getComponent(BodyComponent.class).getCenter().x;
        return thisX > megamanX;
    }

    private boolean isMegamanBelow() {
        float thisY = getComponent(BodyComponent.class).getCenter().y;
        float megamanY = getMegaman().getComponent(BodyComponent.class).getCenter().y;
        return thisY > megamanY;
    }

}