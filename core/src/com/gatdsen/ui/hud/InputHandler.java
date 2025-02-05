package com.gatdsen.ui.hud;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.math.Vector2;
import com.gatdsen.manager.player.HumanPlayer;
import com.gatdsen.simulation.Tower;
import com.gatdsen.ui.menu.Hud;
import com.gatdsen.ui.menu.InGameScreen;

import java.util.HashMap;
import java.util.Map;

public class InputHandler implements InputProcessor, com.gatdsen.manager.InputProcessor {
    InGameScreen ingameScreen;
    private final int KEY_CAMERA_UP = Input.Keys.UP;
    private final int KEY_CAMERA_DOWN = Input.Keys.DOWN;
    private final int KEY_CAMERA_LEFT = Input.Keys.LEFT;
    private final int KEY_CAMERA_RIGHT = Input.Keys.RIGHT;
    private final int KEY_CAMERA_ZOOM_IN = Input.Keys.I;
    private final int KEY_CAMERA_ZOOM_OUT = Input.Keys.O;
    private final int KEY_CAMERA_ZOOM_RESET = Input.Keys.R;
    private final int KEY_TOGGLE_SCORES = Input.Keys.P;
    private final int KEY_CAMERA_TOGGLE_PLAYER_FOCUS = Input.Keys.F;
    private final int KEY_EXIT_TO_MENU = Input.Keys.ESCAPE;
    private final int KEY_TOGGLE_DEBUG = Input.Keys.F3;

    /**
     * Eine Liste aller HumanPlayer, die gerade am Zug sind, indexiert nach ihrer PlayerId
     */
    private final Map<Integer, HumanPlayer> currentPlayers = new HashMap<>();
    private Vector2 lastMousePosition;
    private Vector2 deltaMouseMove;
    private boolean leftMousePressed;
    private boolean rightMousePressed;
    private boolean turnInProgress = false;
    private UiMessenger uiMessenger;

    //Time between turns
    private int defaultTurnWait = 2 * 1000;

    private int turnWaitTime = defaultTurnWait;

    //used for storing arrow key input -> for now used for camera
    //first index for x Axis, second for y
    //length 3 because the cameraposition is 3d
    float[] ingameCameraDirection = new float[3];

    float cameraZoomPressed = 0;

    private Hud hud;


    public InputHandler(InGameScreen ingameScreen, Hud h) {
        this.ingameScreen = ingameScreen;
        this.hud = h;
    }


    public void activateTurn(HumanPlayer player, int playerIndex) {
        boolean wasEmpty;
        // activateTurn() wird bei min. zwei HumanPlayern in einem Spiel von mehreren Threads aufgerufen, sodass der
        // Zugriff auf currentPlayers synchronisiert werden muss
        synchronized(currentPlayers) {
            wasEmpty = currentPlayers.isEmpty();
            currentPlayers.put(playerIndex, player);
        }
        if (wasEmpty && uiMessenger != null) {
            uiMessenger.startTurnTimer(player.getTurnDuration(), true);
        }
        turnInProgress = true;
    }

    public void endTurn() {
        // endTurn() kann einmal von der UI, durch den Klick auf den "Nächste Runde"-Button und einmal vom Manager beim
        // regulären Ende einer Runde aufgerufen werden, sodass der Zugriff auf currentPlayers synchronisiert werden muss
        synchronized (currentPlayers) {
            if (turnInProgress) {
                for (HumanPlayer player : currentPlayers.values()) {
                    player.endCurrentTurn();
                }
                currentPlayers.clear();
                if (uiMessenger != null) {
                    uiMessenger.stopTurnTimer();
                }
                turnInProgress = false;
            }
        }
    }

    public void tick(float delta) {
        if (!turnInProgress) {
            return;
        }
        for (HumanPlayer player : currentPlayers.values()) {
            player.tick(delta);
        }
    }

    /**
     * Converts the mouse screen-coordinates to worldPosition and calls {@link HumanPlayer} to aim the Indicator
     *
     * @param screenX
     * @param screenY
     */
    private void processMouseAim(int screenX, int screenY) {
        Vector2 worldCursorPos = ingameScreen.toWorldCoordinates(new Vector2(screenX, screenY));
        //ToDo do stuff
        /*if (currentPlayer != null) {
        }*/
    }

    /**
     * Wird aufgerufen, wenn ein Spieler auf das Spielfeld links klickt, um einen Turm zu platzieren
     *
     * @param playerId Die ID des Spielers, der den Linksklick ausgeführt hat
     * @param x        Die x-Koordinate des Spielfelds
     * @param y        Die y-Koordinate des Spielfelds
     */
    public void playerFieldLeftClicked(int playerId, int x, int y) {
        HumanPlayer currentPlayer = currentPlayers.get(playerId);
        if (currentPlayer == null) {
            return;
        }
        currentPlayer.placeTower(x, y, Tower.TowerType.BASIC_TOWER);
    }

    /**
     * Wird aufgerufen, wenn ein Spieler auf das Spielfeld rechts klickt, um einen Turm zu verbessern
     *
     * @param playerId Die ID des Spielers, der den Rechtsklick ausgeführt hat
     * @param x        Die x-Koordinate des Spielfelds
     * @param y        Die y-Koordinate des Spielfelds
     */
    public void playerFieldRightClicked(int playerId, int x, int y) {
        HumanPlayer currentPlayer = currentPlayers.get(playerId);
        if (currentPlayer == null) {
            return;
        }
        currentPlayer.upgradeTower(x, y);
    }

    /**
     * Allows the camera to be moved with the mouse by using the position of the new and old mouse positions, to calculate the distance
     * to move.
     *
     * @param screenX
     * @param screenY
     */
    private void processMouseCameraMove(int screenX, int screenY) {
        Vector2 worldCursorPos = ingameScreen.toWorldCoordinates(new Vector2(screenX, screenY));
        deltaMouseMove = worldCursorPos.sub(ingameScreen.toWorldCoordinates(lastMousePosition));
        lastMousePosition = new Vector2(screenX, screenY);

        ingameScreen.moveCameraByOffset(deltaMouseMove);
    }

    /**
     * Called whenever a button is just pressed.
     * For now it handles input from the Arrow Keys, used for the camera Movement.
     * This is done by storing the direction in {@link InputHandler#ingameCameraDirection}.
     * <p>
     * Also calls {@link HumanPlayer#processKeyDown(int keycode)} for user input.
     * Currently is the default case.
     *
     * @param keycode one of the constants in {@link com.badlogic.gdx.Input.Keys}
     * @return was the input handled
     */

    //todo, pass input to hud, instead of ingameScreen
    @Override
    public boolean keyDown(int keycode) {


        //input handling for the camera and ui
        switch (keycode) {
            case KEY_CAMERA_UP:
                ingameCameraDirection[1] += 1;
                break;
            case KEY_CAMERA_DOWN:
                ingameCameraDirection[1] -= 1;
                break;
            case KEY_CAMERA_LEFT:
                ingameCameraDirection[0] -= 1;
                break;
            case KEY_CAMERA_RIGHT:
                ingameCameraDirection[0] += 1;
                break;
            case KEY_EXIT_TO_MENU:
                ingameScreen.dispose();
                break;
            case KEY_CAMERA_ZOOM_IN:
                cameraZoomPressed -= 1;
                break;
            case KEY_CAMERA_ZOOM_OUT:
                cameraZoomPressed += 1;

                break;
            case KEY_CAMERA_ZOOM_RESET:
                ingameScreen.resetCamera();
                //Todo: Zoom Reset
                break;
            case KEY_CAMERA_TOGGLE_PLAYER_FOCUS:
                ingameScreen.toggleCameraMove();
                break;
            case KEY_TOGGLE_DEBUG:
                ingameScreen.toggleDebugView();
                break;
            case KEY_TOGGLE_SCORES:
                hud.toggleScores();
            default:
                if (!turnInProgress) {
                    break;
                }
                ingameScreen.skipTurnStart();
                for (HumanPlayer player : currentPlayers.values()) {
                    player.processKeyDown(keycode);
                }
                break;
        }
        ingameScreen.processInputs(ingameCameraDirection, cameraZoomPressed);


        return true;

    }


    /**
     * Called whenever a button stops getting pressed/is lifted up.
     * Resets the values to some Keypresses.
     *
     * @param keycode one of the constants in {@link com.badlogic.gdx.Input.Keys}
     * @return
     */
    @Override
    public boolean keyUp(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
                ingameCameraDirection[1] -= 1;
                break;
            case Input.Keys.DOWN:
                ingameCameraDirection[1] += 1;
                break;
            case Input.Keys.LEFT:
                ingameCameraDirection[0] += 1;
                break;
            case Input.Keys.RIGHT:
                ingameCameraDirection[0] -= 1;
                break;
            case KEY_CAMERA_ZOOM_IN:
                cameraZoomPressed += 1;
                break;
            case KEY_CAMERA_ZOOM_OUT:
                cameraZoomPressed -= 1;
                break;
            default:
                if (!turnInProgress) {
                    break;
                }
                for (HumanPlayer player : currentPlayers.values()) {
                    player.processKeyUp(keycode);
                }
                break;
        }
        ingameScreen.processInputs(ingameCameraDirection, cameraZoomPressed);

        return true;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            lastMousePosition = new Vector2(screenX, screenY);
            rightMousePressed = true;
        }
        if (button == Input.Buttons.LEFT) {
            leftMousePressed = true;
            processMouseAim(screenX, screenY);
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.RIGHT) {
            rightMousePressed = false;
        }
        if (button == Input.Buttons.LEFT) {
            leftMousePressed = false;
        }

        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {

        //wenn gezogen wird, alte position nehmen, distanz zur neuen position ermitteln und die Kamera nun um diesen wert verschieben
        if (rightMousePressed) {
            processMouseCameraMove(screenX, screenY);
            return true;
        }

        //only change the aim values, when leftmouse is pressed
        if (leftMousePressed) {
            processMouseAim(screenX, screenY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {


        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {

        if (amountY != 0) {
            ingameScreen.zoomCamera(amountY / 10);
        }
        return false;
    }

    //decreases the turn wait time by multiplying with 1/speedupval
    public void turnChangeSpeedup(float speedupVal) {

        turnWaitTime = (defaultTurnWait / (int) speedupVal);

    }

    public void setUiMessenger(UiMessenger uiMessenger) {
        this.uiMessenger = uiMessenger;
    }

    public UiMessenger getUiMessenger() {
        return this.uiMessenger;
    }


}
