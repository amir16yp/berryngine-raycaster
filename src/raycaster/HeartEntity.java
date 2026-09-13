package raycaster;

import berryngine.*;

public class HeartEntity extends Entity3D {

    private static final PixelGraphics sprite = ShapeGenerator.filledHeart(32,32,Color.DARK_RED);
    private static final float ROTATION_SPEED =
            Mathf.toRadians(90.0f); // 90 degrees per second

    public HeartEntity(Vec3 pos) {
        super(
                pos,
                0.6f,
                0.6f,
                0.09f,
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