package com.game.spawns;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.math.Rectangle;
import com.game.Entity;
import com.game.GameContext2d;
import com.game.core.IEntity;
import com.game.updatables.UpdatableComponent;
import com.game.utils.objects.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.game.utils.UtilMethods.rectToBBox;

public class SpawnLocation extends Entity {

    private final List<IEntity> entities = new ArrayList<>();
    private final Supplier<IEntity> entitySupplier;
    private final GameContext2d gameContext;
    private final Rectangle bounds;
    private final Camera camera;
    private final Timer timer;
    private final int max;

    public SpawnLocation(GameContext2d gameContext, Camera camera, Rectangle bounds,
                         int max, float duration, Supplier<IEntity> entitySupplier) {
        this.entitySupplier = entitySupplier;
        this.timer = new Timer(duration);
        this.gameContext = gameContext;
        this.bounds = bounds;
        this.camera = camera;
        this.max = max;
        addComponent(defineUpdatableComponent());
    }

    private UpdatableComponent defineUpdatableComponent() {
        return new UpdatableComponent(delta -> {
            entities.removeIf(IEntity::isDead);
            boolean isInCamBounds = camera.frustum.boundsInFrustum(rectToBBox(bounds));
            if (!isInCamBounds || entities.size() >= max) {
                timer.reset();
                return;
            }
            timer.update(delta);
            if (timer.isFinished()) {
                IEntity entity = entitySupplier.get();
                gameContext.addEntity(entity);
                entities.add(entity);
                timer.reset();
            }
        });
    }

}