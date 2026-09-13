package raycaster;

import berryngine.*;

public class HeartEntity extends Entity3D {

    private static final PixelGraphics sprite =
            ShapeGenerator.filledHeart(
                    32,
                    32,
                    Color.RED
            );

    private static final float ROTATION_SPEED =
            Mathf.toRadians(90.0f); // 90 degrees per second

    public HeartEntity(Vec3 pos) {
        super(
                pos,
                0.5f,
                0.5f,
                0.1f,
                sprite
        );
    }

    @Override
    public void update(float dt) {
        yaw += ROTATION_SPEED * dt;

        // Optional: keep yaw from growing forever
        if (yaw >= Mathf.toRadians(360.0f)) {
            yaw -= Mathf.toRadians(360.0f);
        }
    }
}