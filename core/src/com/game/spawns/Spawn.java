package com.game.spawns;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Rectangle;
import com.game.Entity;
import com.game.GameContext2d;
import com.game.core.IEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.game.utils.UtilMethods.rectToBBox;

@Getter
@RequiredArgsConstructor
public class Spawn {

    private final GameContext2d gameContext;
    private final Supplier<IEntity> spawnSupplier;
    private final Rectangle spawnBounds;

    private IEntity entity;
    private boolean inCamBounds;
    private boolean wasInCamBounds;

    @Setter
    private Supplier<Boolean> doSpawn = () -> true;

    public void update(Camera camera) {
        if (entity != null && entity.isDead()) {
            entity = null;
        }
        wasInCamBounds = inCamBounds;
        inCamBounds = camera.frustum.boundsInFrustum(rectToBBox(spawnBounds));
        if (entity == null && !wasInCamBounds && inCamBounds && doSpawn.get()) {
            entity = spawnSupplier.get();
            gameContext.addEntity(entity);
        }
    }

    public void cull() {
        if (entity == null) {
            return;
        }
        entity.setDead(true);
        entity = null;
    }

    public void resetCamBounds() {
        inCamBounds = wasInCamBounds = false;
    }

}