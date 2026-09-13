package raycaster;

import berryngine.*;

import java.awt.event.KeyEvent;
import java.util.ArrayList;

public final class RaycastScene implements Scene {

    private TileMap map;
    private Camera3D camera;
    private HeightRaycaster raycaster;
    private ArrayList<Entity3D> entityList = new ArrayList<Entity3D>();

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
        float moveSpeed = 5.0f * delta;
        float turnSpeed = 3.5f * delta;

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
                camera,
                entityList
        );
    }

    @Override
    public void onSceneEnter(GameWindow gameWindow) {

        LevelGenerator.TileStyle grass = new LevelGenerator.TileStyle(
                Tile.TEX_CONCRETE,
                Tile.TEX_GRASS,
                Tile.TEX_GRASS,
                Tile.TEX_SKY,
                0.0f,
                3.0f
        );

        LevelGenerator.TileStyle exteriorWall = new LevelGenerator.TileStyle(
                Tile.TEX_BRICK,
                Tile.TEX_TERRAZZO,
                Tile.TEX_CONCRETE,
                Tile.TEX_ACOUSTIC_TILE,
                0.0f,
                3.0f
        );

        LevelGenerator.TileStyle hallway = new LevelGenerator.TileStyle(
                Tile.TEX_PAINTED_WALL,
                Tile.TEX_TERRAZZO,
                Tile.TEX_CONCRETE,
                Tile.TEX_ACOUSTIC_TILE,
                0.0f,
                3.0f
        );

        LevelGenerator.TileStyle classroom = new LevelGenerator.TileStyle(
                Tile.TEX_PAINTED_WALL,
                Tile.TEX_VINYL,
                Tile.TEX_CONCRETE,
                Tile.TEX_ACOUSTIC_TILE,
                0.0f,
                3.0f
        );

        LevelGenerator.TileStyle gym = new LevelGenerator.TileStyle(
                Tile.TEX_CINDER_BLOCK,
                Tile.TEX_HARDWOOD,
                Tile.TEX_CONCRETE,
                Tile.TEX_ACOUSTIC_TILE,
                0.0f,
                4.0f
        );

        LevelGenerator.TileStyle bathroom = new LevelGenerator.TileStyle(
                Tile.TEX_PAINTED_WALL,
                Tile.TEX_TERRAZZO,
                Tile.TEX_CONCRETE,
                Tile.TEX_ACOUSTIC_TILE,
                0.0f,
                3.0f
        );

        LevelGenerator.TileStyle road = grass.floorTexture(Tile.TEX_ASPHALT);

        LevelGenerator.TileStyle sidewalk =
                grass.floorTexture(Tile.TEX_CONCRETE);

        LevelGenerator.TileStyle fence = new LevelGenerator.TileStyle(
                Tile.TEX_CHAIN_LINK,
                Tile.TEX_GRASS,
                Tile.TEX_CHAIN_LINK,
                Tile.TEX_SKY,
                0.0f,
                2.0f
        );


// ============================================================
// LEVEL
// ============================================================

        LevelGenerator gen = LevelGenerator.create(64, 64)
                .fill(0, 0, 63, 63, grass)
                .border(grass);

// ------------------------------------------------------------
// Street + front pavement
// ------------------------------------------------------------

        gen.fill(0, 56, 63, 63, road);

        gen.fill(26, 48, 37, 55, sidewalk);

        gen.fill(8, 46, 55, 48, sidewalk);

// ------------------------------------------------------------
// Main school shell
// ------------------------------------------------------------

        gen.room(
                10, 8,
                53, 45,
                exteriorWall,
                hallway
        );

// ------------------------------------------------------------
// Main central corridors
// ------------------------------------------------------------

// horizontal corridor
        gen.fill(
                11, 25,
                52, 29,
                hallway
        );

// vertical entrance corridor
        gen.fill(
                29, 26,
                34, 44,
                hallway
        );

// ------------------------------------------------------------
// NORTH CLASSROOMS
// ------------------------------------------------------------

        gen.room(12, 10, 21, 23, exteriorWall, classroom);
        gen.room(22, 10, 31, 23, exteriorWall, classroom);
        gen.room(32, 10, 41, 23, exteriorWall, classroom);
        gen.room(42, 10, 51, 23, exteriorWall, classroom);

// classroom doors
        gen.doorway(16, 23, hallway);
        gen.doorway(26, 23, hallway);
        gen.doorway(36, 23, hallway);
        gen.doorway(46, 23, hallway);

// ------------------------------------------------------------
// SOUTH-WEST CLASSROOMS
// ------------------------------------------------------------

        gen.room(12, 31, 21, 43, exteriorWall, classroom);
        gen.room(22, 31, 28, 43, exteriorWall, classroom);

        gen.doorway(16, 31, hallway);
        gen.doorway(25, 31, hallway);

// ------------------------------------------------------------
// GYM
// ------------------------------------------------------------

        gen.room(
                36, 31,
                51, 43,
                exteriorWall,
                gym
        );

// wide gym entrance
        gen.doorway(40, 31, hallway);
        gen.doorway(41, 31, hallway);

// ------------------------------------------------------------
// BATHROOMS
// ------------------------------------------------------------

        gen.room(
                30, 31,
                34, 36,
                exteriorWall,
                bathroom
        );

        gen.doorway(30, 33, hallway);

// ------------------------------------------------------------
// SMALL OFFICE / ADMIN ROOM
// ------------------------------------------------------------

        gen.room(
                30, 38,
                34, 43,
                exteriorWall,
                classroom
        );

        gen.doorway(30, 40, hallway);

// ------------------------------------------------------------
// MAIN ENTRANCE
// ------------------------------------------------------------

// open the exterior wall
        gen.doorway(29, 45, hallway);
        gen.doorway(30, 45, hallway);
        gen.doorway(31, 45, hallway);
        gen.doorway(32, 45, hallway);
        gen.doorway(33, 45, hallway);
        gen.doorway(34, 45, hallway);

// entrance path
        gen.fill(
                29, 46,
                34, 55,
                sidewalk
        );

// ------------------------------------------------------------
// FRONT STEPS
// ------------------------------------------------------------

        gen.heightRect(
                28, 47,
                35, 47,
                0.10f,
                sidewalk
        );

        gen.heightRect(
                29, 46,
                34, 46,
                0.20f,
                sidewalk
        );

// ------------------------------------------------------------
// PLAYGROUND
// ------------------------------------------------------------

        LevelGenerator.TileStyle playground =
                grass.floorTexture(Tile.TEX_ASPHALT);

        gen.fill(
                3, 12,
                8, 42,
                playground
        );

// fence
        gen.wallLine(2, 11, 9, 11, fence);
        gen.wallLine(2, 43, 9, 43, fence);
        gen.wallLine(2, 11, 2, 43, fence);
        gen.wallLine(9, 11, 9, 43, fence);

// playground gate
        gen.doorway(9, 27, grass);

// ------------------------------------------------------------
// SIDEWALKS AROUND SCHOOL
// ------------------------------------------------------------

        gen.fill(8, 6, 55, 7, sidewalk);
        gen.fill(8, 46, 55, 47, sidewalk);

        gen.fill(8, 6, 9, 47, sidewalk);
        gen.fill(54, 6, 55, 47, sidewalk);

// ------------------------------------------------------------
// FINAL MAP
// ------------------------------------------------------------

        map = gen.build();
        camera = new Camera3D(
                new Vec3(8.0f, 3.0f, 0.5f)
        );

        entityList.add(
                new Entity3D(
                        new Vec3(
                                8.0f,
                                8.0f,
                                0.0f
                        ),
                        1.0f,
                        1.0f,
                        0.2f,
                        ShapeGenerator.outlineHeart(
                                32,
                                32,
                                Color.RED
                        )
                )
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