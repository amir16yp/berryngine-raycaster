package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;
import berryngine.Vec3;

public class Entity3D extends Entity {

    public float width;
    public float height;
    public float depth;
    public boolean visible = true;

    public float yaw = Mathf.toRadians(45f);
    private boolean removed = false;
    public PixelGraphics texture;

    public Entity3D(
            Vec3 pos,
            float width,
            float height,
            float depth,
            PixelGraphics texture) {

        this.pos = pos;

        this.width = width;
        this.height = height;
        this.depth = depth;

        this.texture = texture;
    }

    public void remove() {
        if (removed) {
            return;
        }

        removed = true;

        RaycastScene.INSTANCE.removeEntity(this);
    }

    public boolean isRemoved() {
        return removed;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (this.isOutOfBounds())
        {
            this.remove();
        }
    }
}