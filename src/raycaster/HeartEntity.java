package raycaster;

import berryngine.*;

public class HeartEntity extends Entity3D {


    private static PixelGraphics dickNballs(int color)
    {
        PixelGraphics pg = new PixelGraphics(64, 64);
        // Draw left ball
        for (int x = 16; x <= 28; x++) {
            for (int y = 40; y <= 52; y++) {
                if (Math.hypot(x - 22, y - 46) <= 6) {
                    pg.setPixel(x, y, color);
                }
            }
        }

        // Draw right ball
        for (int x = 36; x <= 48; x++) {
            for (int y = 40; y <= 52; y++) {
                if (Math.hypot(x - 42, y - 46) <= 6) {
                    pg.setPixel(x, y, color);
                }
            }
        }

        // Draw shaft
        for (int x = 26; x <= 38; x++) {
            for (int y = 18; y <= 46; y++) {
                pg.setPixel(x, y, color);
            }
        }

        // Draw head
        for (int x = 24; x <= 40; x++) {
            for (int y = 10; y <= 18; y++) {
                if (Math.hypot(x - 32, y - 18) <= 8) {
                    pg.setPixel(x, y, color);
                }
            }
        }

        return pg;
    }
//
//    private static final PixelGraphics sprite =
//            ShapeGenerator.filledHeart(
//                    32,
//                    32,
//                    Color.RED
//            );

    private static final PixelGraphics sprite = dickNballs(Color.BLACK);
    private static final float ROTATION_SPEED =
            Mathf.toRadians(90.0f); // 90 degrees per second

    public HeartEntity(Vec3 pos) {
        super(
                pos,
                0.6f,
                0.6f,
                0.05f,
                sprite
        );
    }

    @Override
    public void update(float dt) {
        yaw += ROTATION_SPEED * dt;

        if (yaw >= Mathf.toRadians(360.0f)) {
            yaw -= Mathf.toRadians(360.0f);
        }
    }
}