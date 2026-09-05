package raycaster;

import berryngine.Mathf;
import berryngine.Vec3;

public final class Camera3D {

    public Vec3 position;
    public Vec3 rotation;

    public float fov = Mathf.toRadians(70.0f);

    public Camera3D(Vec3 position) {
        this.position = position;
        this.rotation = new Vec3(0.0f, 0.0f, 0.0f);
    }
}