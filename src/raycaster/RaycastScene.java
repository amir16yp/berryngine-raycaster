package raycaster;

import berryngine.*;

import java.awt.event.KeyEvent;

public final class RaycastScene implements Scene {

    private final TileMap map;
    private final Camera3D camera;
    private final HeightRaycaster raycaster;

    public RaycastScene() {
        map = Level.createMap();

        camera = new Camera3D(
                new Vec3(8.0f, 3.0f, 0.5f)
        );

        camera.rotation.y = Mathf.toRadians(90.0f);

        raycaster = new HeightRaycaster(
                map,
                320,
                200,
                Mathf.toRadians(70.0f)
        );
    }

    private void updateKeyboardInput(
            float forwardX,
            float forwardY,
            float moveSpeed,
            float turnSpeed
    ) {
        float rightX = -forwardY;
        float rightY = forwardX;

        // Forward / backward
        if (Input.isKey(KeyEvent.VK_W)) {
            camera.position.x += forwardX * moveSpeed;
            camera.position.y += forwardY * moveSpeed;
        }

        if (Input.isKey(KeyEvent.VK_S)) {
            camera.position.x -= forwardX * moveSpeed;
            camera.position.y -= forwardY * moveSpeed;
        }

        // Strafing
        if (Input.isKey(KeyEvent.VK_A)) {
            camera.position.x -= rightX * moveSpeed;
            camera.position.y -= rightY * moveSpeed;
        }

        if (Input.isKey(KeyEvent.VK_D)) {
            camera.position.x += rightX * moveSpeed;
            camera.position.y += rightY * moveSpeed;
        }

        // Turning
        if (Input.isKey(KeyEvent.VK_LEFT) || Input.isKey(KeyEvent.VK_Q)) {
            camera.rotation.y -= turnSpeed;
        }

        if (Input.isKey(KeyEvent.VK_RIGHT) || Input.isKey(KeyEvent.VK_E)) {
            camera.rotation.y += turnSpeed;
        }

        // Looking up / down
        if (Input.isKey(KeyEvent.VK_UP)) {
            camera.rotation.x += turnSpeed;
        }

        if (Input.isKey(KeyEvent.VK_DOWN)) {
            camera.rotation.x -= turnSpeed;
        }
    }

    private void updateMouseInput() {
        int dx = Input.getMouseDeltaX();
        int dy = Input.getMouseDeltaY();

        // Mouse sensitivity is in radians per pixel.
        // Do NOT multiply mouse delta by frame delta.
        float sensitivity = 0.003f;

        // Mouse X -> yaw
        camera.rotation.y += dx * sensitivity;

        // Mouse Y -> pitch
        camera.rotation.x -= dy * sensitivity;

        // Prevent the camera from flipping upside down.
        float maxPitch = Mathf.toRadians(89.0f);

        if (camera.rotation.x > maxPitch) {
            camera.rotation.x = maxPitch;
        }

        if (camera.rotation.x < -maxPitch) {
            camera.rotation.x = -maxPitch;
        }
    }

    @Override
    public void update(GameWindow gameWindow, float delta) {
        float moveSpeed = 3.0f * delta;
        float turnSpeed = 2.5f * delta;

        float yaw = camera.rotation.y;

        float forwardX = Mathf.cos(yaw);
        float forwardY = Mathf.sin(yaw);

        updateKeyboardInput(
                forwardX,
                forwardY,
                moveSpeed,
                turnSpeed
        );

        updateMouseInput();
    }

    @Override
    public void render(
            GameWindow gameWindow,
            FramebufferPixelGraphics framebufferPixelGraphics
    ) {
        framebufferPixelGraphics.clear(Color.WHITE);

        raycaster.render(
                framebufferPixelGraphics,
                camera
        );
    }

    @Override
    public void onSceneEnter(GameWindow gameWindow) {

    }

    @Override
    public void onSceneExit(GameWindow gameWindow) {

    }
}