package raycaster;

import berryngine.*;

public final class Level {

    private static final PixelGraphics WALL1 =
            ShapeGenerator.outlineRectangle(8, 8, 2, Color.RED);

    private Level() {
    }

    public static TileMap createMap() {
        final int W = 16;
        final int H = 16;

        TileMap map = new TileMap(W, H);

        // -----------------------------------------------------------------
        // Base map
        // -----------------------------------------------------------------

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                Tile tile = new Tile(0.0f, 2.0f);
                tile.solid = false;
                tile.wallTexture = WALL1;

                map.set(x, y, tile);
            }
        }

        // -----------------------------------------------------------------
        // Outer walls
        // -----------------------------------------------------------------

        for (int x = 0; x < W; x++) {
            wall(map, x, 0);
            wall(map, x, H - 1);
        }

        for (int y = 0; y < H; y++) {
            wall(map, 0, y);
            wall(map, W - 1, y);
        }

        // -----------------------------------------------------------------
        // Left room
        //
        // ############
        // #          #
        // #          #
        // #          #
        // ####    ####
        //    #    #
        //    #    #
        // -----------------------------------------------------------------

        wall(map, 4, 1);
        wall(map, 4, 2);
        wall(map, 4, 3);

        wall(map, 4, 5);
        wall(map, 4, 6);
        wall(map, 4, 7);
        wall(map, 4, 8);
        wall(map, 4, 9);

        // Doorway through the wall.
        open(map, 4, 4);

        // -----------------------------------------------------------------
        // Right room
        // -----------------------------------------------------------------

        wall(map, 11, 1);
        wall(map, 11, 2);
        wall(map, 11, 3);

        wall(map, 11, 5);
        wall(map, 11, 6);
        wall(map, 11, 7);
        wall(map, 11, 8);
        wall(map, 11, 9);

        open(map, 11, 4);

        // -----------------------------------------------------------------
        // Central raised platform
        // -----------------------------------------------------------------

        height(map, 6, 5, 1.0f);
        height(map, 7, 5, 1.0f);
        height(map, 8, 5, 1.0f);
        height(map, 9, 5, 1.0f);

        height(map, 6, 6, 1.0f);
        height(map, 7, 6, 1.0f);
        height(map, 8, 6, 1.0f);
        height(map, 9, 6, 1.0f);

        height(map, 6, 7, 1.0f);
        height(map, 7, 7, 1.0f);
        height(map, 8, 7, 1.0f);
        height(map, 9, 7, 1.0f);

        // -----------------------------------------------------------------
        // Higher section
        // -----------------------------------------------------------------

        height(map, 7, 8, 2.0f);
        height(map, 8, 8, 2.0f);

        // -----------------------------------------------------------------
        // Small raised islands
        // -----------------------------------------------------------------

        height(map, 2, 11, 0.5f);
        height(map, 3, 11, 0.5f);
        height(map, 2, 12, 0.5f);
        height(map, 3, 12, 0.5f);

        height(map, 12, 11, 0.75f);
        height(map, 13, 11, 0.75f);
        height(map, 12, 12, 0.75f);
        height(map, 13, 12, 0.75f);

        // -----------------------------------------------------------------
        // Interior walls / obstacles
        // -----------------------------------------------------------------

        wall(map, 2, 6);
        wall(map, 2, 7);
        wall(map, 2, 8);

        wall(map, 13, 6);
        wall(map, 13, 7);
        wall(map, 13, 8);

        // -----------------------------------------------------------------
        // Back room
        // -----------------------------------------------------------------

        wall(map, 5, 11);
        wall(map, 6, 11);
        wall(map, 7, 11);

        wall(map, 9, 11);
        wall(map, 10, 11);
        wall(map, 11, 11);

        // Door into back room.
        open(map, 8, 11);

        // -----------------------------------------------------------------
        // A few varying-height tiles for testing transitions
        // -----------------------------------------------------------------

        height(map, 5, 13, 0.25f);
        height(map, 6, 13, 0.50f);
        height(map, 7, 13, 0.75f);
        height(map, 8, 13, 1.00f);

        return map;
    }

    private static void wall(TileMap map, int x, int y) {
        Tile tile = map.get(x, y);

        tile.solid = true;
        tile.floor = 0.0f;
        tile.ceiling = 2.0f;
        tile.wallTexture = WALL1;
    }

    private static void open(TileMap map, int x, int y) {
        Tile tile = map.get(x, y);

        tile.solid = false;
    }

    private static void height(
            TileMap map,
            int x,
            int y,
            float floor) {

        Tile tile = map.get(x, y);

        tile.floor = floor;
        tile.ceiling = floor + 2.0f;
        tile.solid = false;
        tile.wallTexture = WALL1;
    }
}
