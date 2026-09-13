package raycaster;

import berryngine.Color;
import berryngine.PixelGraphics;
import berryngine.ShapeGenerator;
import berryngine.SpriteSheetFont;

import java.util.HashMap;

public final class Tile {

    private static PixelGraphics generateGenericTile(int number)
    {
        PixelGraphics tile = new PixelGraphics(18, 18);
        tile.clear(Color.WHITE);
        tile.drawImage(ShapeGenerator.outlineRectangle(18, 18, 1, Color.RED),0,0);
        tile.renderString(SpriteSheetFont.START2P, String.valueOf(number), 2, 2, Color.BLACK);
        return tile;
    }

    private static PixelGraphics[] textureMap = new PixelGraphics[]{generateGenericTile(0),generateGenericTile(1)};

    public float floor;
    public float ceiling;

    public int floorTexture;
    public int ceilingTexture;

    public int wallTexture;

    public PixelGraphics getWallTexture()
    {
        return textureMap[this.wallTexture];
    }

    public PixelGraphics getFloorTexture()
    {
        return textureMap[floorTexture];
    }

    public PixelGraphics getCeilingTexture()
    {
        return textureMap[ceilingTexture];
    }

    public boolean solid;

    public Tile(float floor, float ceiling) {
        this.floor = floor;
        this.ceiling = ceiling;
    }
}