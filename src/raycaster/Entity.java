package raycaster;

import berryngine.Vec3;

public class Entity {
    public Vec3 pos;
    public Vec3 vel;

    public void update(float dt)
    {
        if (vel != null)
        {
            pos.addScaled(vel, dt);
        }
    }
}
