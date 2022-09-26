package com.game.levels;

import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.game.utils.enums.Direction;
import com.game.utils.enums.ProcessState;
import com.game.utils.interfaces.Updatable;
import com.game.utils.objects.KeyValuePair;
import com.game.utils.objects.Timer;
import lombok.Getter;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

import static com.game.ViewVals.PPM;
import static com.game.utils.UtilMethods.*;
import static com.game.utils.enums.ProcessState.*;
import static java.lang.Math.min;

/**
 * Manager for {@link OrthographicCamera} that handles transitions between "level rooms".
 */
public class LevelCameraManager implements Updatable {

    private final Camera camera;
    private final Timer transitionTimer;

    private final Map<Rectangle, String> gameRooms;
    private final Map<String, Rectangle> gameRoomNameMap = new HashMap<>();

    private final Vector2 transStartPos = new Vector2();
    private final Vector2 transTargetPos = new Vector2();
    @Getter
    private final Vector2 focusableStartPos = new Vector2();
    @Getter
    private final Vector2 focusableTargetPos = new Vector2();

    private final Queue<Runnable> actionQ = new LinkedList<>();

    private final float focusableDistFromEdge;

    private Rectangle currentGameRoom;

    private CameraFocusable focusable;
    private CameraFocusable queuedFocusable;

    @Getter
    private ProcessState transState;
    @Getter
    private Direction transDirection;

    private boolean reset;
    @Getter
    private boolean updating;

    /**
     * Sets the camera, transition timer, game rooms, and focusable. Also, each game room that has a non-null name
     * will be stored in a name-to-room map that can be queried via {@link #getGameRoomByName(String)}. Only one
     * game room will be mapped to each unique game room name string.
     *
     * @param camera                the camera
     * @param transitionTimer       the transition timer
     * @param gameRooms             the game rooms
     * @param focusable             the focusable
     * @param focusableDistFromEdge the distance from the edge of the last game room after a transition
     */
    public LevelCameraManager(Camera camera, Timer transitionTimer, Map<Rectangle, String> gameRooms,
                              CameraFocusable focusable, float focusableDistFromEdge) {
        this.camera = camera;
        this.gameRooms = gameRooms;
        this.focusable = focusable;
        this.transitionTimer = transitionTimer;
        this.focusableDistFromEdge = focusableDistFromEdge;
        gameRooms.forEach((room, name) -> {
            if (name != null) {
                gameRoomNameMap.put(name, room);
            }
        });
    }

    /**
     * Set the focusable. If updating, then action is queued for next update cycle.
     *
     * @param focusable the focusable
     */
    public void setFocusable(CameraFocusable focusable) {
        if (updating) {
            queuedFocusable = focusable;
        } else {
            this.focusable = focusable;
            reset = true;
        }
        Vector2 pos = focusable.getFocus();
        camera.position.x = pos.x;
        camera.position.y = pos.y;
    }

    /**
     * Return the string associated with {@link #currentGameRoom}.
     *
     * @return the string associated with the current game room
     */
    public String getCurrentGameRoomName() {
        return gameRooms.get(currentGameRoom);
    }

    /**
     * Return a copy of the current game room along with its name.
     *
     * @return copy of the current game room along with its name
     */
    public KeyValuePair<Rectangle, String> getCurrentGameRoom() {
        return KeyValuePair.of(new Rectangle(currentGameRoom), gameRooms.get(currentGameRoom));
    }

    /**
     * Return a copy of the game room mapped to the name.
     *
     * @param name the name of the game room
     * @return copy of the game room mapped to the name
     */
    public Rectangle getGameRoomByName(String name) {
        Rectangle gameRoom = gameRoomNameMap.get(name);
        if (gameRoom == null) {
            throw new IllegalArgumentException("No game room mapped to " + name);
        }
        return new Rectangle(gameRoom);
    }

    /**
     * Sets the camera to transition to the game room mapped to the name. If manager is currently updating, then
     * action is queued for next update cycle.
     *
     * @param name the name of the game room to transition to
     */
    public void transToGameRoomWithName(String name) {
        Rectangle nextGameRoom = gameRoomNameMap.get(name);
        if (nextGameRoom == null) {
            throw new IllegalStateException("No game room mapped to " + name);
        }
        Runnable runnable = () -> {
            transDirection = getSingleMostDirectionFromStartToTarget(currentGameRoom, nextGameRoom);
            setTransVals(nextGameRoom);
        };
        if (updating) {
            actionQ.add(runnable);
        } else {
            runnable.run();
        }
    }

    /**
     * Get transition timer ratio.
     *
     * @return the transition time ticker ratio
     */
    public float getTransTimerRatio() {
        return transitionTimer.getRatio();
    }

    /**
     * Returns the interpolated position of the focusable during transition based on the start position and the target
     * position as determined at trans init.
     *
     * @return the interpolated position of the focusable during transition
     */
    public Vector2 getFocusableTransInterpolation() {
        Vector2 startCopy = focusableStartPos.cpy();
        Vector2 targetCopy = focusableTargetPos.cpy();
        return interpolate(startCopy, targetCopy, getTransTimerRatio());
    }

    @Override
    public void update(float delta) {
        updating = true;
        while (!actionQ.isEmpty()) {
            actionQ.poll().run();
        }
        if (queuedFocusable != null) {
            focusable = queuedFocusable;
            queuedFocusable = null;
            reset = true;
        }
        if (reset) {
            setCamToFocusable();
            currentGameRoom = nextGameRoom();
            reset = false;
        } else if (transState == null) {
            onNullTrans();
        } else {
            onTrans(delta);
        }
        updating = false;
    }

    private void setTransVals(Rectangle nextGameRoom) {
        transState = BEGIN;
        transStartPos.set(toVec2(camera.position));
        transTargetPos.set(transStartPos);
        focusableStartPos.set(focusable.getFocus());
        focusableTargetPos.set(focusableStartPos);
        switch (transDirection) {
            case DIR_LEFT -> {
                transTargetPos.x = (nextGameRoom.x + nextGameRoom.width) - min(nextGameRoom.width / 2.0f,
                        camera.viewportWidth / 2.0f);
                focusableTargetPos.x = (nextGameRoom.x + nextGameRoom.width) - focusableDistFromEdge;
            }
            case DIR_RIGHT -> {
                transTargetPos.x = nextGameRoom.x + min(nextGameRoom.width / 2.0f,
                        camera.viewportWidth / 2.0f);
                focusableTargetPos.x = nextGameRoom.x + focusableDistFromEdge;
            }
            case DIR_UP -> {
                transTargetPos.y = nextGameRoom.y + min(nextGameRoom.height / 2.0f,
                        camera.viewportHeight / 2.0f);
                focusableTargetPos.y = nextGameRoom.y + focusableDistFromEdge;
            }
            case DIR_DOWN -> {
                transTargetPos.y = (nextGameRoom.y + nextGameRoom.height) - min(nextGameRoom.height / 2.0f,
                        camera.viewportHeight / 2.0f);
                focusableTargetPos.y = (nextGameRoom.y + nextGameRoom.height) - focusableDistFromEdge;
            }
        }
    }

    private void onNullTrans() {
        /*
        case 1: if current game room is null, try to find next game room and assign it to current game room,
        wait until next update cycle to attempt another action

        case 2: if current game room contains focusable, then set camera position to current focus and
        correct bounds if necessary

        case 3: if current game room is not null and doesn't contain focusable, then setBounds next game room,
        and if next game room is a neighbour, then init transition process, otherwise jump directly to
        focusable on next update cycle
        */
        if (currentGameRoom == null) {
            currentGameRoom = nextGameRoom();
        } else if (currentGameRoom.contains(focusable.getFocus())) {
            setCamToFocusable();
            if (camera.position.y > (currentGameRoom.y + currentGameRoom.height) - camera.viewportHeight / 2.0f) {
                camera.position.y = (currentGameRoom.y + currentGameRoom.height) - camera.viewportHeight / 2.0f;
            }
            if (camera.position.y < currentGameRoom.y + camera.viewportHeight / 2.0f) {
                camera.position.y = currentGameRoom.y + camera.viewportHeight / 2.0f;
            }
            if (camera.position.x > (currentGameRoom.x + currentGameRoom.width) - camera.viewportWidth / 2.0f) {
                camera.position.x = (currentGameRoom.x + currentGameRoom.width) - camera.viewportWidth / 2.0f;
            }
            if (camera.position.x < currentGameRoom.x + camera.viewportWidth / 2.0f) {
                camera.position.x = currentGameRoom.x + camera.viewportWidth / 2.0f;
            }
        } else {
            Rectangle nextGameRoom = nextGameRoom();
            // if next game room is null, do nothing and return
            if (nextGameRoom == null) {
                return;
            }
            // generic 5 * PPM by 5 * PPM square is used to determine push direction
            Rectangle overlap = new Rectangle();
            Rectangle boundingBox = new Rectangle(0f, 0f, 5f * PPM, 5f * PPM).setCenter(focusable.getFocus());
            transDirection = getOverlapPushDirection(boundingBox, currentGameRoom, overlap);
            // go ahead and set current game room to next room, which needs to be done even if
            // transition direction is null
            currentGameRoom = nextGameRoom;
            if (transDirection == null) {
                return;
            }
            // set trans vals
            setTransVals(nextGameRoom);
        }
    }

    private void onTrans(float delta) {
        switch (transState) {
            case END:
                transDirection = null;
                transState = null;
                transitionTimer.reset();
                transStartPos.setZero();
                transTargetPos.setZero();
                break;
            case BEGIN:
                transState = CONTINUE;
            case CONTINUE:
                transitionTimer.update(delta);
                Vector2 pos = interpolate(transStartPos, transTargetPos, getTransTimerRatio());
                camera.position.x = pos.x;
                camera.position.y = pos.y;
                transState = transitionTimer.isFinished() ? END : CONTINUE;
        }
    }

    private Rectangle nextGameRoom() {
        return gameRooms.keySet().stream().filter(gameRoom -> gameRoom.contains(
                focusable.getFocus())).findFirst().orElse(null);
    }

    private void setCamToFocusable() {
        Vector2 pos = focusable.getFocus();
        camera.position.x = pos.x;
        camera.position.y = pos.y;
    }

}