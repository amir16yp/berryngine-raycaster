package raycaster;

import berryngine.*;

public class Tile {

    private static PixelGraphics generateGenericTile(int number) {
        PixelGraphics tile = new PixelGraphics(18, 18);
        tile.clear(Color.WHITE);
        tile.drawImage(
                ShapeGenerator.outlineRectangle(18, 18, 1, Color.RED),
                0,
                0
        );
        tile.renderString(
                SpriteSheetFont.START2P,
                String.valueOf(number),
                2,
                2,
                Color.BLACK
        );
        return tile;
    }

    private static Random.State rng = Random.newState(42L);

    private static PixelGraphics[] textureMap = new PixelGraphics[] {
            MaterialGenerator.generate(MaterialGenerator.Material.BRICK, 32, 32, rng),
            MaterialGenerator.generate(MaterialGenerator.Material.DIRT, 32, 32, rng),
    };

    public float floor;
    public float ceiling;

    public int floorTexture;
    public int ceilingTexture;
    public int wallTexture;
    public int topTexture;
    public boolean solid;
    public boolean renderTop;

    public Tile(float floor, float ceiling) {
        this.floor = floor;
        this.ceiling = ceiling;

        // Default texture
        this.floorTexture = 0;
        this.ceilingTexture = 0;
        this.wallTexture = 0;
        this.renderTop = false;
        this.solid = false;
    }

    public PixelGraphics getWallTexture() {
        return textureMap[this.wallTexture];
    }

    public PixelGraphics getFloorTexture() {
        return textureMap[this.floorTexture];
    }

    public PixelGraphics getCeilingTexture() {
        return textureMap[this.ceilingTexture];
    }
    public PixelGraphics getTopTexture() {return textureMap[this.topTexture];}
}
