package raycaster;

import berryngine.PixelGraphics;
import berryngine.Vec3;

public final class Entity3D {

    public Vec3 pos;

    public float width;
    public float height;
    public float depth;

    public float rotation;

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
}