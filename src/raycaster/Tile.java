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

    private static final Random.State RNG = Random.newState(42L);
    private static final IVec2 DEFAULT_TEXTURE_SIZE = new IVec2(24, 24);

    public static final int TEX_BRICK          = 0;
    public static final int TEX_DIRT           = 1;
    public static final int TEX_GRASS          = 2;
    public static final int TEX_SAND           = 3;
    public static final int TEX_WATER          = 4;
    public static final int TEX_ASPHALT        = 5;
    public static final int TEX_CONCRETE       = 6;
    public static final int TEX_SKY            = 7;

    public static final int TEX_PAINTED_WALL   = 8;
    public static final int TEX_CINDER_BLOCK   = 9;
    public static final int TEX_TERRAZZO       = 10;
    public static final int TEX_VINYL          = 11;
    public static final int TEX_RUBBER         = 12;
    public static final int TEX_HARDWOOD       = 13;
    public static final int TEX_LOCKER_METAL   = 14;
    public static final int TEX_ACOUSTIC_TILE  = 15;
    public static final int TEX_CHALKBOARD     = 16;
    public static final int TEX_WHITEBOARD     = 17;
    public static final int TEX_GLASS          = 18;
    public static final int TEX_CHAIN_LINK     = 19;

    private static PixelGraphics mat(MaterialGenerator.Material material) {
        return MaterialGenerator.generate(
                material,
                DEFAULT_TEXTURE_SIZE.x,
                DEFAULT_TEXTURE_SIZE.y,
                RNG
        );
    }

    private static final PixelGraphics[] textureMap = new PixelGraphics[] {
            mat(MaterialGenerator.Material.BRICK),          // 0
            mat(MaterialGenerator.Material.DIRT),           // 1
            mat(MaterialGenerator.Material.GRASS),          // 2
            mat(MaterialGenerator.Material.SAND),           // 3
            mat(MaterialGenerator.Material.WATER),          // 4
            mat(MaterialGenerator.Material.ASPHALT),        // 5
            mat(MaterialGenerator.Material.CONCRETE),       // 6
            mat(MaterialGenerator.Material.SKY),            // 7

            mat(MaterialGenerator.Material.PAINTED_WALL),   // 8
            mat(MaterialGenerator.Material.CINDER_BLOCK),   // 9
            mat(MaterialGenerator.Material.TERRAZZO),       // 10
            mat(MaterialGenerator.Material.VINYL_FLOOR),    // 11
            mat(MaterialGenerator.Material.RUBBER_FLOOR),   // 12
            mat(MaterialGenerator.Material.HARDWOOD_FLOOR), // 13
            mat(MaterialGenerator.Material.LOCKER_METAL),   // 14
            mat(MaterialGenerator.Material.ACOUSTIC_TILE),  // 15
            mat(MaterialGenerator.Material.CHALKBOARD),     // 16
            mat(MaterialGenerator.Material.WHITEBOARD),     // 17
            mat(MaterialGenerator.Material.GLASS),          // 18
            mat(MaterialGenerator.Material.CHAIN_LINK)      // 19
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
