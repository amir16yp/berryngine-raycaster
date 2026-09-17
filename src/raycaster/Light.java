package raycaster;

import berryngine.Color;
import berryngine.Vec3;

/**
 * A dynamic point light source.
 *
 * <p>Lights have a world position, an influence radius, a color and an
 * intensity. Color is stored as an ARGB integer (the alpha component is
 * ignored).</p>
 */
public final class Light {

    public Vec3 pos;
    public float radius;
    public int color;
    public float intensity;
    public boolean enabled;

    public Light(Vec3 pos, float radius, int color, float intensity) {
        this.pos = pos;
        this.radius = Math.max(0.0f, radius);
        this.color = color;
        this.intensity = Math.max(0.0f, intensity);
        this.enabled = true;
    }

    public Light(Vec3 pos, float radius, int color) {
        this(pos, radius, color, 1.0f);
    }

    public Light(Vec3 pos, float radius) {
        this(pos, radius, Color.WHITE, 1.0f);
    }
}