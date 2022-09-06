package com.game.entities.specials;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.game.animations.AnimationComponent;
import com.game.animations.TimedAnimation;
import com.game.core.ConstVals;
import com.game.core.Entity;
import com.game.core.GameContext2d;
import com.game.sounds.SoundComponent;
import com.game.sprites.SpriteAdapter;
import com.game.sprites.SpriteComponent;
import com.game.updatables.UpdatableComponent;
import com.game.utils.enums.Position;
import com.game.utils.objects.Timer;
import com.game.utils.objects.Wrapper;
import com.game.world.BodyComponent;
import com.game.world.Fixture;

import java.util.Map;
import java.util.function.Supplier;

import static com.game.core.ConstVals.SoundAsset.*;
import static com.game.core.ConstVals.TextureAsset.*;
import static com.game.core.ConstVals.ViewVals.PPM;
import static com.game.utils.enums.Position.*;
import static com.game.world.BodyType.*;
import static com.game.world.FixtureType.*;

public class SpringyBouncer extends Entity {

    private final Timer bounceTimer = new Timer(.5f);

    public SpringyBouncer(GameContext2d gameContext, RectangleMapObject bouncerObj) {
        super(gameContext);
        bounceTimer.setToEnd();
        addComponent(new SoundComponent());
        addComponent(defineUpdatableComponent());
        addComponent(defineAnimationComponent());
        addComponent(defineBodyComponent(bouncerObj));
        addComponent(defineSpriteComponent(bouncerObj.getRectangle()));
    }

    private UpdatableComponent defineUpdatableComponent() {
        return new UpdatableComponent(bounceTimer::update);
    }

    private BodyComponent defineBodyComponent(RectangleMapObject bouncerObj) {
        BodyComponent bodyComponent = new BodyComponent(ABSTRACT, bouncerObj.getRectangle());
        Float x = bouncerObj.getProperties().get("x", Float.class);
        Float y = bouncerObj.getProperties().get("y", Float.class);
        Fixture bouncer = new Fixture(this, BOUNCER);
        if (x != null) {
            bouncer.putUserData("x", x);
        }
        if (y != null) {
            bouncer.putUserData("y", y);
        }
        bouncer.putUserData("onBounce", (Runnable) () -> {
            bounceTimer.reset();
            getComponent(SoundComponent.class).requestSound(DINK_SOUND);
        });
        bouncer.setBounds(bouncerObj.getRectangle());
        bodyComponent.addFixture(bouncer);
        return bodyComponent;
    }

    private SpriteComponent defineSpriteComponent(Rectangle boundsRect) {
        Sprite sprite = new Sprite();
        sprite.setSize(1.5f * PPM, 1.5f * PPM);
        return new SpriteComponent(sprite, new SpriteAdapter() {
            @Override
            public boolean setPositioning(Wrapper<Rectangle> bounds, Wrapper<Position> position) {
                bounds.setData(boundsRect);
                position.setData(BOTTOM_CENTER);
                return true;
            }
        });
    }

    private AnimationComponent defineAnimationComponent() {
        Supplier<String> keySupplier = () -> bounceTimer.isFinished() ? "still" : "bounce";
        TextureAtlas textureAtlas = gameContext.getAsset(OBJECTS_TEXTURE_ATLAS.getSrc(), TextureAtlas.class);
        Map<String, TimedAnimation> animationMap = Map.of(
                "still", new TimedAnimation(textureAtlas.findRegion("SpringyBouncerStill")),
                "bounce", new TimedAnimation(textureAtlas.findRegion("SpringyBouncer"), 5, .05f));
        return new AnimationComponent(keySupplier, animationMap::get);
    }

}