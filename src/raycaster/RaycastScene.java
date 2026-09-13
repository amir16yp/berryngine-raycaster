package raycaster;

import berryngine.*;

import java.awt.event.KeyEvent;

public final class RaycastScene implements Scene {

    private TileMap map;
    private Camera3D camera;
    private HeightRaycaster raycaster;

    public RaycastScene() {


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

        //updateMouseInput();
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

        LevelGenerator.TileStyle outside = new LevelGenerator.TileStyle(
                6, // concrete
                2, // grass
                4, // water
                7, // sky
                0.0f,
                2.0f
        );

        LevelGenerator.TileStyle road = outside.floorTexture(5);

        map = LevelGenerator.create(64, 64)
                .floorRect(0,0,64,64, outside)
                .floorRect(16, 0, 18, 64, road) // road
                .wallRect(0,0,63,63, outside)
                .ramp(8,3,8,4, 0.2f, 0.4f, outside)
                .build();

        camera = new Camera3D(
                new Vec3(8.0f, 3.0f, 0.5f)
        );

        camera.rotation.y = Mathf.toRadians(90.0f);

        raycaster = new HeightRaycaster(
                map,
                gameWindow.getInternalWidth(),
                gameWindow.getInternalHeight(),
                Mathf.toRadians(70.0f)
        );
    }

    @Override
    public void onSceneExit(GameWindow gameWindow) {

    }
}