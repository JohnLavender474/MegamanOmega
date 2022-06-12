package com.game.world;

import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.game.Component;
import com.game.System;
import com.game.Entity;
import com.game.utils.Direction;
import com.game.utils.Updatable;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * {@link System} implementation that handles the logic of the "game world physics", i.e. gravity, collision handling,
 * and contact-event-handling.
 */
@RequiredArgsConstructor
public class WorldSystem extends System {

    private final Set<Contact> priorContacts = new HashSet<>();
    private final Set<Contact> currentContacts = new HashSet<>();
    private final List<BodyComponent> bodies = new ArrayList<>();
    private final List<Updatable> postProcess = new ArrayList<>();
    private final WorldContactListener worldContactListener;
    private final Vector2 airResistance;
    private final float fixedTimeStep;
    private float accumulator;

    @Override
    public Set<Class<? extends Component>> getComponentMask() {
        return Set.of(BodyComponent.class);
    }

    @Override
    protected void preProcess(float delta) {
        postProcess.clear();
        bodies.clear();
    }

    @Override
    protected void processEntity(Entity entity, float delta) {
        BodyComponent bodyComponent = entity.getComponent(BodyComponent.class);
        bodyComponent.clearCollisionFlags();
        bodies.add(bodyComponent);
        if (bodyComponent.getPreProcess() != null) {
            bodyComponent.getPreProcess().update(delta);
        }
        if (bodyComponent.getPostProcess() != null) {
            postProcess.add(bodyComponent.getPostProcess());
        }
    }

    @Override
    protected void postProcess(float delta) {
        // ImpulseMovement and collision handling is time-stepped
        accumulator += delta;
        while (accumulator >= fixedTimeStep) {
            accumulator -= fixedTimeStep;
            // Apply forces
            bodies.forEach(bodyComponent -> {
                // If below 0.5 speed, then set to 0
                if (Math.abs(bodyComponent.getVelocity().x) < 0.5f) {
                    bodyComponent.setVelocityX(0f);
                }
                if (Math.abs(bodyComponent.getVelocity().y) < 0.5f) {
                    bodyComponent.setVelocityY(0f);
                }
                // Prevent clipping through obstacles left and right
                if (bodyComponent.isColliding(Direction.LEFT)) {
                    bodyComponent.getVelocity().x = Math.max(0f, bodyComponent.getVelocity().x);
                } else if (bodyComponent.isColliding(Direction.RIGHT)) {
                    bodyComponent.getVelocity().x = Math.min(0f, bodyComponent.getVelocity().x);
                }
                // Apply resistance
                bodyComponent.getVelocity().x *= 1f / Math.max(1f, bodyComponent.getResistance().x);
                bodyComponent.getVelocity().y *= 1f / Math.max(1f, bodyComponent.getResistance().y);
                // Reset resistance
                bodyComponent.setResistance(airResistance);
                // If gravity on: if colliding down, minimum gravity is -0.5f, otherwise apply gravity
                // If gravity off: set to zero
                if (bodyComponent.isGravityOn()) {
                    if (bodyComponent.isColliding(Direction.DOWN)) {
                        bodyComponent.setVelocityY(-0.5f);
                    } else {
                        bodyComponent.applyImpulse(0f, bodyComponent.getGravity() * fixedTimeStep);
                    }
                }
                // Round to 2 decimal places
                bodyComponent.setVelocity(Math.round(bodyComponent.getVelocity().x * 100f) / 100f,
                                          Math.round(bodyComponent.getVelocity().y * 100f) / 100f);
                // Translate
                bodyComponent.translate(bodyComponent.getVelocity().x * fixedTimeStep,
                                        bodyComponent.getVelocity().y * fixedTimeStep);
                // Each Fixture is moved to conform to its position center from the center of the Body Component
                bodyComponent.getFixtures().forEach(fixture -> {
                    Vector2 center = bodyComponent.getCenter();
                    center.add(fixture.getOffset());
                    fixture.setCenter(center);
                });
            });
            for (int i = 0; i < bodies.size(); i++) {
                BodyComponent bc1 = bodies.get(i);
                // Handle contacts
                for (int j = i + 1; j < bodies.size(); j++) {
                    BodyComponent bc2 = bodies.get(j);
                    for (Fixture f1 : bc1.getFixtures()) {
                        for (Fixture f2 : bc2.getFixtures()) {
                            if (Intersector.overlaps(f1.getFixtureBox(), f2.getFixtureBox())) {
                                currentContacts.add(new Contact(f1, f2));
                            }
                        }
                    }
                }
            }
            // Handle collisions
            for (int i = 0; i < bodies.size(); i++) {
                for (int j = i + 1; j < bodies.size(); j++) {
                    BodyComponent bc1 = bodies.get(i);
                    BodyComponent bc2 = bodies.get(j);
                    Rectangle overlap = new Rectangle();
                    if (Intersector.intersectRectangles(
                            bc1.getCollisionBox(), bc2.getCollisionBox(), overlap)) {
                        handleCollision(bc1, bc2, overlap);
                    }
                }
            }
        }
        // Handle contacts in the current contacts set
        currentContacts.forEach(contact -> {
            if (priorContacts.contains(contact)) {
                worldContactListener.continueContact(contact, delta);
            } else {
                worldContactListener.beginContact(contact, delta);
            }
        });
        // Handle contacts in the prior contacts set
        priorContacts.forEach(contact -> {
            if (!currentContacts.contains(contact)) {
                worldContactListener.endContact(contact, delta);
            }
        });
        // Move current contacts to prior contacts set, then clear the current contacts set
        priorContacts.clear();
        priorContacts.addAll(currentContacts);
        currentContacts.clear();
        postProcess.forEach(postProcessable -> postProcessable.update(delta));
    }

    /**
     * Handles collision between {@link BodyType#DYNAMIC} and {@link BodyType#STATIC} {@link BodyComponent} instances
     * where parameter bc1 should be dynamic and bc2 static. Dynamic body is adjusted out of collision and has static
     * body's friction applied.
     *
     * @param bc1 the first body
     * @param bc2 the second body
     * @param overlap the overlap between both bodies
     */
    private void handleCollision(BodyComponent bc1, BodyComponent bc2, Rectangle overlap) {
        if (overlap.getWidth() > overlap.getHeight()) {
            if (bc1.getCollisionBox().getY() > bc2.getCollisionBox().getY()) {
                // Apply resistance
                if (bc1.getVelocity().y <= 0f) {
                    bc1.applyResistanceX(bc2.getFriction().x);
                }
                if (bc2.getVelocity().y >= 0f) {
                    bc2.applyResistanceX(bc1.getFriction().x);
                }
                // Set collision flags
                bc1.setColliding(Direction.DOWN);
                bc2.setColliding(Direction.UP);
                // If one is dynamic and the other static, handle collision
                if (bc1.getBodyType() == BodyType.DYNAMIC && bc2.getBodyType() == BodyType.STATIC) {
                    bc1.getCollisionBox().y += overlap.getHeight();
                } else if (bc2.getBodyType() == BodyType.DYNAMIC && bc1.getBodyType() == BodyType.STATIC) {
                    bc2.getCollisionBox().y -= overlap.getHeight();
                }
            } else {
                // Apply resistance
                if (bc1.getVelocity().y > 0f) {
                    bc1.applyResistanceX(bc2.getFriction().x);
                }
                if (bc2.getVelocity().y < 0f) {
                    bc2.applyResistanceX(bc1.getFriction().x);
                }
                // Set collision flags
                bc1.setColliding(Direction.UP);
                bc2.setColliding(Direction.DOWN);
                // If one is dynamic and the other static, handle collision
                if (bc1.getBodyType() == BodyType.DYNAMIC && bc2.getBodyType() == BodyType.STATIC) {
                    bc1.getCollisionBox().y -= overlap.getHeight();
                } else if (bc2.getBodyType() == BodyType.DYNAMIC && bc1.getBodyType() == BodyType.STATIC) {
                    bc2.getCollisionBox().y += overlap.getHeight();
                }
            }
        } else {
            if (bc1.getCollisionBox().getX() > bc2.getCollisionBox().getX()) {
                // Apply resistance
                if (bc1.getVelocity().x < 0f) {
                    bc1.applyResistanceY(bc2.getFriction().y);
                }
                if (bc2.getVelocity().x > 0f) {
                    bc2.applyResistanceY(bc1.getFriction().y);
                }
                // Set collision flags
                bc1.setColliding(Direction.LEFT);
                bc2.setColliding(Direction.RIGHT);
                // If one is dynamic and the other static, handle collision
                if (bc1.getBodyType() == BodyType.DYNAMIC && bc2.getBodyType() == BodyType.STATIC) {
                    bc1.getCollisionBox().x += overlap.getWidth();
                } else if (bc2.getBodyType() == BodyType.DYNAMIC && bc1.getBodyType() == BodyType.STATIC) {
                    bc2.getCollisionBox().x -= overlap.getWidth();
                }
            } else {
                // Apply resistance
                if (bc1.getVelocity().x > 0f) {
                    bc1.applyResistanceY(bc2.getFriction().y);
                }
                if (bc2.getVelocity().x < 0f) {
                    bc2.applyResistanceY(bc1.getFriction().y);
                }
                // Set collision flags
                bc1.setColliding(Direction.RIGHT);
                bc2.setColliding(Direction.LEFT);
                // If one is dynamic and the other static, handle collision
                if (bc1.getBodyType() == BodyType.DYNAMIC && bc2.getBodyType() == BodyType.STATIC) {
                    bc1.getCollisionBox().x -= overlap.getWidth();
                } else if (bc2.getBodyType() == BodyType.DYNAMIC && bc1.getBodyType() == BodyType.STATIC) {
                    bc2.getCollisionBox().x += overlap.getWidth();
                }
            }
        }
    }

}
