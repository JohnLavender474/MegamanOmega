package com.game.blocks;

import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.math.Vector2;
import com.game.Entity;
import com.game.behaviors.Behavior;
import com.game.utils.Trajectory;
import com.game.utils.UtilMethods;
import com.game.world.BodyComponent;
import com.game.world.BodyType;
import com.game.world.Fixture;
import com.game.world.FixtureType;

import javax.print.attribute.standard.JobImpressionsCompleted;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Factory for instantiating new block entities.
 */
public class BlockFactory {

    /**
     * Create list of block entities.
     *
     * @param rectangleMapObjects the rectangle map objects
     * @return the list
     */
    public final List<Entity> create(List<RectangleMapObject> rectangleMapObjects) {
        return rectangleMapObjects.stream().map(this::create).collect(Collectors.toList());
    }

    /**
     * Create block entity.
     *
     * @param rectangleMapObject the rectangle map object
     * @return the entity
     */
    public final Entity create(RectangleMapObject rectangleMapObject) {
        Entity entity = new Entity();
        define(entity, rectangleMapObject);
        return entity;
    }

    /**
     * Define the {@link Entity} using the provided {@link RectangleMapObject}.
     *
     * @param entity             the entity
     * @param rectangleMapObject the rectangle map object
     */
    protected void define(Entity entity, RectangleMapObject rectangleMapObject) {
        BodyComponent bodyComponent = new BodyComponent(BodyType.STATIC);
        defineBodyComponent(bodyComponent, rectangleMapObject);
        entity.addComponent(bodyComponent);
    }

    /**
     * Define body component.
     *
     * @param bodyComponent      the body component
     * @param rectangleMapObject the rectangle map object
     */
    protected void defineBodyComponent(
            BodyComponent bodyComponent, RectangleMapObject rectangleMapObject) {
        bodyComponent.getCollisionBox().set(rectangleMapObject.getRectangle());
        bodyComponent.setGravity(gravity(rectangleMapObject));
        bodyComponent.setFriction(friction(rectangleMapObject));
        defineWallSlideSensors(bodyComponent, rectangleMapObject);
    }

    /**
     * Gravity vector 2.
     *
     * @param rectangleMapObject the rectangle map object
     * @return the vector 2
     */
    protected float gravity(RectangleMapObject rectangleMapObject) {
        Float gravity = rectangleMapObject.getProperties().get("Gravity", Float.class);
        return gravity != null ? gravity : 0f;
    }

    /**
     * Friction vector 2.
     *
     * @param rectangleMapObject the rectangle map object
     * @return the vector 2
     */
    protected Vector2 friction(RectangleMapObject rectangleMapObject) {
        Vector2 friction = new Vector2();
        Float frictionX = rectangleMapObject.getProperties().get("FrictionX", Float.class);
        friction.x = frictionX == null ? 1f : frictionX;
        Float frictionY = rectangleMapObject.getProperties().get("FrictionY", Float.class);
        friction.y = frictionY == null ? 1f : frictionY;
        return friction;
    }

    protected void defineWallSlideSensors(BodyComponent bodyComponent, RectangleMapObject rectangleMapObject) {
        Boolean canWallSlideLeft = rectangleMapObject.getProperties().get(
                "CanWallSlideLeft", Boolean.class);
        if (canWallSlideLeft != null && canWallSlideLeft) {
            Fixture wallSlideLeft = new Fixture(bodyComponent, FixtureType.WALL_SLIDE_SENSOR);
            wallSlideLeft.setSize(3f, bodyComponent.getSize().y - 10f);
            wallSlideLeft.setOffset(-bodyComponent.getSize().x / 2f, 0f);
            bodyComponent.getFixtures().add(wallSlideLeft);
        }
        Boolean canWallSlideRight = rectangleMapObject.getProperties().get(
                "CanWallSlideRight", Boolean.class);
        if (canWallSlideRight != null && canWallSlideRight) {
            Fixture wallSlideRight = new Fixture(bodyComponent, FixtureType.WALL_SLIDE_SENSOR);
            wallSlideRight.setSize(3f, bodyComponent.getSize().y - 10f);
            wallSlideRight.setOffset(bodyComponent.getSize().x / 2f, 0f);
            bodyComponent.getFixtures().add(wallSlideRight);
        }
    }

    protected void defineMovement(BodyComponent bodyComponent, RectangleMapObject rectangleMapObject) {
        String movements = rectangleMapObject.getProperties().get("Movements", String.class);
        if (movements == null) {
            return;
        }
        List<Trajectory> trajectories = decipherMovementDefs(movements);

    }

    protected List<Trajectory> decipherMovementDefs(String movements) {
        List<Trajectory> trajectories = new ArrayList<>();
        String[] tokens = movements.split(",");
        for (String token : tokens) {
            String[] def = token.split("\\s+");
            if (def.length != 3) {
                throw new IllegalStateException("Movement def must have 3 tokens, instead has " + def.length);
            }
            float x = Float.parseFloat(def[0]);
            float y = Float.parseFloat(def[1]);
            float duration = Float.parseFloat(def[2]);
            trajectories.add(new Trajectory(x, y, duration));
        }
        return trajectories;
    }

}
