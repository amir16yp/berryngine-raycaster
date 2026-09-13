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

    public boolean isOutOfBounds() {
        TileMap map = RaycastScene.INSTANCE.map;

        return pos.x < 0.0f ||
                pos.z < 0.0f ||
                pos.x >= map.getWidth() ||
                pos.z >= map.getHeight();
    }

}
