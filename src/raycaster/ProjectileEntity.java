package raycaster;

import berryngine.*;

public class ProjectileEntity extends Entity3D {
    private static final float ROTATION_SPEED = Mathf.toRadians(120f);
    public static PixelGraphics sprite = ShapeGenerator.circle(32, Color.RED);

    public ProjectileEntity(Vec3 pos, float speed, float forwardX, float forwardY) {
        super(pos, 0.5f, 0.5f, 0.05f, sprite);
        this.vel = new Vec3(
                forwardX * speed,
                forwardY * speed,
                0f
        );
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        yaw += ROTATION_SPEED * dt;

        if (yaw >= Mathf.toRadians(360.0f)) {
            yaw -= Mathf.toRadians(360.0f);
        }
    }
}