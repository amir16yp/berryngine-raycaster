package raycaster;

public final class TileMap {

    /**
     * Ambient light level for the entire map.
     *
     * <p>1.0 is full brightness, 0.0 is completely dark. Dynamic lights
     * are added on top of this value.</p>
     */
    public float ambientLight = 1.0f;

    private final int width;
    private final int height;
    private final Tile[] tiles;

    public TileMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new Tile[width * height];
    }

    public boolean inBounds(int x, int y) {
        return x >= 0
                && y >= 0
                && x < width
                && y < height;
    }

    public Tile get(int x, int y) {
        if (!inBounds(x, y))
            return null;

        return tiles[y * width + x];
    }

    public void set(int x, int y, Tile tile) {
        tiles[y * width + x] = tile;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void update(float dt) {
        for (Tile tile : tiles) {
            if (tile != null) {
                tile.update(dt);
            }
        }
    }
}