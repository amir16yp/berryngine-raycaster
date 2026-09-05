package raycaster;

import berryngine.PixelGraphics;

public final class Tile {

    public float floor;
    public float ceiling;

    public PixelGraphics floorTexture;
    public PixelGraphics ceilingTexture;

    public PixelGraphics wallTexture;

    public boolean solid;

    public Tile(float floor, float ceiling) {
        this.floor = floor;
        this.ceiling = ceiling;
    }
}