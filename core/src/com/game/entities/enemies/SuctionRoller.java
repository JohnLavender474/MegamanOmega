package com.game.entities.enemies;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.game.GameContext2d;
import com.game.animations.AnimationComponent;
import com.game.animations.TimedAnimation;
import com.game.entities.blocks.Block;
import com.game.entities.contracts.Faceable;
import com.game.entities.contracts.Facing;
import com.game.entities.megaman.Megaman;
import com.game.entities.projectiles.Bullet;
import com.game.pathfinding.PathfindingComponent;
import com.game.sprites.SpriteAdapter;
import com.game.sprites.SpriteComponent;
import com.game.updatables.UpdatableComponent;
import com.game.utils.enums.Position;
import com.game.utils.objects.Timer;
import com.game.utils.objects.Wrapper;
import com.game.world.BodyComponent;
import com.game.world.Fixture;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

import static com.game.ConstVals.TextureAssets.*;
import static com.game.ConstVals.ViewVals.PPM;
import static com.game.entities.contracts.Facing.F_LEFT;
import static com.game.entities.contracts.Facing.F_RIGHT;
import static com.game.utils.UtilMethods.*;
import static com.game.utils.enums.Position.*;
import static com.game.world.BodySense.*;
import static com.game.world.BodyType.DYNAMIC;
import static com.game.world.FixtureType.*;

@Getter
@Setter
public class SuctionRoller extends AbstractEnemy implements Faceable {

    private final Rectangle nextTarget = new Rectangle();
    private final Timer offWallGrace = new Timer(.15f);

    private Facing facing;
    private boolean isOnWall;
    private boolean wasOnWall;

    public SuctionRoller(GameContext2d gameContext, Supplier<Megaman> megamanSupplier, Vector2 spawn) {
        super(gameContext, megamanSupplier);
        damageTimer.setToEnd();
        offWallGrace.setToEnd();
        damageNegotiation.put(Bullet.class, 5);
        addComponent(definePathfindingComponent());
        addComponent(defineAnimationComponent());
        addComponent(defineUpdatableComponent());
        addComponent(defineBodyComponent(spawn));
        addComponent(defineSpriteComponent());
    }

    private UpdatableComponent defineUpdatableComponent() {
        return new UpdatableComponent(delta -> {
            if (getMegaman().isDead()) {
                return;
            }
            damageTimer.update(delta);
            BodyComponent thisBody = getComponent(BodyComponent.class);
            wasOnWall = isOnWall;
            isOnWall = (isFacing(F_LEFT) && thisBody.is(TOUCHING_BLOCK_LEFT)) ||
                    (isFacing(F_RIGHT) && thisBody.is(TOUCHING_BLOCK_RIGHT));
            offWallGrace.update(delta);
            if (wasOnWall && !isOnWall) {
                offWallGrace.reset();
            }
            BodyComponent playerBody = getMegaman().getComponent(BodyComponent.class);
            if (thisBody.is(FEET_ON_GROUND)) {
                if (bottomRightPoint(playerBody.getCollisionBox()).x < thisBody.getPosition().x) {
                    setFacing(F_LEFT);
                } else if (playerBody.getPosition().x > bottomRightPoint(thisBody.getCollisionBox()).x) {
                    setFacing(F_RIGHT);
                }
            }
        });
    }

    private PathfindingComponent definePathfindingComponent() {
        PathfindingComponent pathfindingComponent = new PathfindingComponent(
                () -> getComponent(BodyComponent.class).getCenter(), () -> getMegaman().getFocus(),
                nextTarget::set,
                target -> getComponent(BodyComponent.class).getCollisionBox().contains(centerPoint(target)));
        pathfindingComponent.setDoAcceptPredicate(node ->
                node.getObjects().stream().noneMatch(o -> o instanceof Block) &&
                        (node.getObjects().contains("Ground") || node.getObjects().contains("LeftWall") ||
                                node.getObjects().contains("RightWall")));
        pathfindingComponent.setDoAllowDiagonal(() -> false);
        Timer timer = new Timer(.25f);
        pathfindingComponent.setDoUpdatePredicate(delta -> {
            timer.update(delta);
            boolean isFinished = timer.isFinished();
            if (isFinished) {
                timer.reset();
            }
            return isFinished;
        });
        return pathfindingComponent;
    }

    private SpriteComponent defineSpriteComponent() {
        Sprite sprite = new Sprite();
        sprite.setSize(1.5f * PPM, 1.5f * PPM);
        return new SpriteComponent(sprite, new SpriteAdapter() {

            @Override
            public boolean setPositioning(Wrapper<Rectangle> bounds, Wrapper<Position> position) {
                bounds.setData(getComponent(BodyComponent.class).getCollisionBox());
                position.setData(isOnWall ? (isFacing(F_LEFT) ? CENTER_LEFT : CENTER_RIGHT) : BOTTOM_CENTER);
                return true;
            }

            @Override
            public float getRotation() {
                return isOnWall ? (isFacing(F_LEFT) ? -90f : 90f) : 0f;
            }

            @Override
            public boolean isFlipX() {
                if (isOnWall || !offWallGrace.isFinished()) {
                    return isFacing(F_RIGHT) ? nextTarget.y >= getComponent(BodyComponent.class).getCenter().y :
                            nextTarget.y < getComponent(BodyComponent.class).getCenter().y;
                }
                return isFacing(F_RIGHT);
            }

        });
    }

    private AnimationComponent defineAnimationComponent() {
        TextureAtlas textureAtlas = gameContext.getAsset(ENEMIES_TEXTURE_ATLAS, TextureAtlas.class);
        return new AnimationComponent(new TimedAnimation(textureAtlas.findRegion("SuctionRoller"), 5, .1f));
    }

    private BodyComponent defineBodyComponent(Vector2 spawn) {
        BodyComponent bodyComponent = new BodyComponent(DYNAMIC);
        bodyComponent.setGravity(-35f * PPM);
        bodyComponent.setSize(.75f * PPM, PPM);
        setBottomCenterToPoint(bodyComponent.getCollisionBox(), spawn);
        bodyComponent.setPreProcess(delta -> {
            BodyComponent thisBody = getComponent(BodyComponent.class);
            BodyComponent testPlayerBody = getMegaman().getComponent(BodyComponent.class);
            if (isOnWall) {
                if (!wasOnWall) {
                    bodyComponent.setVelocityX(0f);
                }
                boolean moveUp = centerPoint(nextTarget).y > thisBody.getCenter().y;
                bodyComponent.setVelocityY((moveUp ? 2.5f : -2.5f) * PPM);
                if (!moveUp && testPlayerBody.is(FEET_ON_GROUND)) {
                    isOnWall = false;
                }
            }
            if (!isOnWall) {
                if (wasOnWall) {
                    bodyComponent.setVelocityY(0f);
                }
                bodyComponent.setVelocityX((isFacing(F_RIGHT) ? 2.5f : -2.5f) * PPM);
            }
        });
        // hit box
        Fixture hitbox = new Fixture(this, DAMAGEABLE_BOX);
        hitbox.setSize(.75f * PPM, PPM);
        bodyComponent.addFixture(hitbox);
        // damager box
        Fixture damagerbox = new Fixture(this, DAMAGER_BOX);
        damagerbox.setSize(.75f * PPM, PPM);
        bodyComponent.addFixture(damagerbox);
        // feet
        Fixture feet = new Fixture(this, FEET);
        feet.setSize(8f, .75f);
        feet.setOffset(0f, -PPM / 2f);
        bodyComponent.addFixture(feet);
        // left
        Fixture left = new Fixture(this, LEFT);
        left.setSize(1f, PPM - 1f);
        left.setOffset(-.375f * PPM, 0f);
        bodyComponent.addFixture(left);
        // right
        Fixture right = new Fixture(this, RIGHT);
        right.setSize(1f, PPM - 1f);
        right.setOffset(.375f * PPM, 0f);
        bodyComponent.addFixture(right);
        return bodyComponent;
    }

}