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

    private static Random.State RNG = Random.newState(42L);
    private static IVec2 DEFAULT_TEXTURE_SIZE = new IVec2(16,16);

    private static PixelGraphics[] textureMap = new PixelGraphics[] {
            MaterialGenerator.generate(MaterialGenerator.Material.BRICK, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 0
            MaterialGenerator.generate(MaterialGenerator.Material.DIRT, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 1
            MaterialGenerator.generate(MaterialGenerator.Material.GRASS, DEFAULT_TEXTURE_SIZE.x ,DEFAULT_TEXTURE_SIZE.y, RNG), // 2
            MaterialGenerator.generate(MaterialGenerator.Material.SAND, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 3
            MaterialGenerator.generate(MaterialGenerator.Material.WATER, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 4
            MaterialGenerator.generate(MaterialGenerator.Material.ASPHALT, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 5
            MaterialGenerator.generate(MaterialGenerator.Material.CONCRETE, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG), // 6
            MaterialGenerator.generate(MaterialGenerator.Material.SKY, DEFAULT_TEXTURE_SIZE.x, DEFAULT_TEXTURE_SIZE.y, RNG) // 7


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
