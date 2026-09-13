package raycaster;

import berryngine.*;

import java.util.Random;

/**
 * Generic, reusable building blocks for constructing {@link TileMap}s.
 *
 * <p>The generator works against a lightweight internal grid and only
 * materializes real {@link Tile} objects when {@link #build()} is called.</p>
 *
 * <p>All texture IDs are caller-defined. The generator does not attach any
 * meaning to particular texture IDs.</p>
 *
 * <h2>Example</h2>
 *
 * <pre>
 * TileStyle stone = new TileStyle(
 *         1,  // wall
 *         2,  // floor
 *         3,  // top
 *         4,  // ceiling
 *         0f, // floor height
 *         2f  // ceiling height
 * );
 *
 * TileStyle brick = new TileStyle(
 *         10,
 *         11,
 *         12,
 *         13,
 *         1f,
 *         3f
 * );
 *
 * TileMap map = LevelGenerator.create(32, 32)
 *         .defaults(0, 1, 2, 3)
 *         .room(2, 2, 10, 10, stone)
 *         .room(14, 2, 24, 10, brick)
 *         .outline(12, 12, 20, 20, brick)
 *         .fill(13, 13, 19, 19, stone)
 *         .corridor(10, 6, 14, 6, stone)
 *         .doorway(10, 6, brick)
 *         .build();
 * </pre>
 */
public final class LevelGenerator {

    // ===================================================================
    // Grid state
    // ===================================================================

    private final int width;
    private final int height;
    private final Cell[][] cells;

    // ===================================================================
    // Defaults
    // ===================================================================

    private int wallTexture = 0;
    private int floorTexture = 0;
    private int topTexture = 0;
    private int ceilingTexture = 0;

    private float baseFloor = 0.0f;
    private float clearance = 2.0f;

    private int corridorWidth = 1;

    private Random random = new Random();

    // ===================================================================
    // Construction
    // ===================================================================

    private LevelGenerator(int width, int height) {

        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Width and height must be positive."
            );
        }

        this.width = width;
        this.height = height;

        this.cells = new Cell[width][height];

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                cells[x][y] = new Cell(
                        baseFloor,
                        baseFloor + clearance
                );
            }
        }
    }

    /**
     * Starts a new generator over a {@code width} x {@code height} grid.
     *
     * <p>The grid starts completely open.</p>
     */
    public static LevelGenerator create(int width, int height) {
        return new LevelGenerator(width, height);
    }

    // ===================================================================
    // TileStyle
    // ===================================================================

    /**
     * Describes the complete visual and vertical properties of a tile.
     *
     * <p>A style is just data. It does not modify the generator until it is
     * passed to one of the builder methods.</p>
     */
    public static final class TileStyle {

        public final int wallTexture;
        public final int floorTexture;
        public final int topTexture;
        public final int ceilingTexture;

        public final float floorHeight;
        public final float ceilingHeight;

        /**
         * Creates a complete tile style.
         *
         * @param wallTexture    wall texture ID
         * @param floorTexture   floor texture ID
         * @param topTexture     top texture ID
         * @param ceilingTexture ceiling texture ID
         * @param floorHeight    floor/world height
         * @param ceilingHeight  ceiling/world height
         */
        public TileStyle(
                int wallTexture,
                int floorTexture,
                int topTexture,
                int ceilingTexture,
                float floorHeight,
                float ceilingHeight) {

            this.wallTexture = wallTexture;
            this.floorTexture = floorTexture;
            this.topTexture = topTexture;
            this.ceilingTexture = ceilingTexture;

            this.floorHeight = floorHeight;
            this.ceilingHeight = ceilingHeight;
        }

        /**
         * Creates a style using a floor height and vertical clearance.
         */
        public static TileStyle of(
                int wallTexture,
                int floorTexture,
                int topTexture,
                int ceilingTexture,
                float floorHeight,
                float clearance) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    floorHeight + clearance
            );
        }

        /**
         * Creates a copy with a different floor height.
         */
        public TileStyle floorHeight(float floorHeight) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    ceilingHeight
            );
        }

        /**
         * Creates a copy with a different ceiling height.
         */
        public TileStyle ceilingHeight(float ceilingHeight) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    ceilingHeight
            );
        }

        /**
         * Creates a copy with a different vertical clearance.
         */
        public TileStyle clearance(float clearance) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    floorHeight + clearance
            );
        }

        public TileStyle wallTexture(int texture) {

            return new TileStyle(
                    texture,
                    floorTexture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    ceilingHeight
            );
        }

        public TileStyle floorTexture(int texture) {

            return new TileStyle(
                    wallTexture,
                    texture,
                    topTexture,
                    ceilingTexture,
                    floorHeight,
                    ceilingHeight
            );
        }

        public TileStyle topTexture(int texture) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    texture,
                    ceilingTexture,
                    floorHeight,
                    ceilingHeight
            );
        }

        public TileStyle ceilingTexture(int texture) {

            return new TileStyle(
                    wallTexture,
                    floorTexture,
                    topTexture,
                    texture,
                    floorHeight,
                    ceilingHeight
            );
        }
    }

    // ===================================================================
    // Palette / configuration
    // ===================================================================

    /**
     * Sets the default texture IDs used by methods without an explicit style.
     */
    public LevelGenerator defaults(
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture) {

        this.wallTexture = wallTexture;
        this.floorTexture = floorTexture;
        this.topTexture = topTexture;
        this.ceilingTexture = ceilingTexture;

        return this;
    }

    /**
     * Sets the default floor height and vertical clearance.
     */
    public LevelGenerator defaultFloor(
            float floorHeight,
            float clearance) {

        this.baseFloor = floorHeight;
        this.clearance = clearance;

        return this;
    }

    /**
     * Sets the width of subsequently generated corridors.
     */
    public LevelGenerator corridorWidth(int tiles) {

        this.corridorWidth = Math.max(1, tiles);

        return this;
    }

    /**
     * Seeds the internal random number generator.
     */
    public LevelGenerator seed(long seed) {

        this.random = new Random(seed);

        return this;
    }

    /**
     * Supplies an existing random number generator.
     */
    public LevelGenerator random(Random random) {

        if (random == null) {
            throw new IllegalArgumentException("random cannot be null.");
        }

        this.random = random;

        return this;
    }

    /**
     * Returns the RNG currently used by this generator.
     */
    public Random random() {
        return random;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /**
     * Returns a style representing the current generator defaults.
     */
    public TileStyle defaultStyle() {

        return new TileStyle(
                wallTexture,
                floorTexture,
                topTexture,
                ceilingTexture,
                baseFloor,
                baseFloor + clearance
        );
    }

    // ===================================================================
    // Walls
    // ===================================================================

    /**
     * Turns one tile into a wall using the current defaults.
     */
    public LevelGenerator wall(int x, int y) {

        return wall(x, y, defaultStyle());
    }

    /**
     * Turns one tile into a wall using the supplied style.
     */
    public LevelGenerator wall(
            int x,
            int y,
            TileStyle style) {

        Cell cell = cellAt(x, y);

        if (cell == null) {
            return this;
        }

        cell.solid = true;

        cell.floor = style.floorHeight;
        cell.ceiling = style.ceilingHeight;

        cell.wallTexture = style.wallTexture;
        cell.floorTexture = style.floorTexture;
        cell.topTexture = style.topTexture;
        cell.ceilingTexture = style.ceilingTexture;

        return this;
    }

    /**
     * Turns one tile into a wall with explicit values.
     */
    public LevelGenerator wall(
            int x,
            int y,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return wall(
                x,
                y,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    /**
     * Draws a straight wall using the current defaults.
     */
    public LevelGenerator wallLine(
            int x1,
            int y1,
            int x2,
            int y2) {

        return wallLine(x1, y1, x2, y2, defaultStyle());
    }

    /**
     * Draws a straight wall using the supplied style.
     */
    public LevelGenerator wallLine(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        requireAxisAligned(
                "wallLine()",
                x1,
                y1,
                x2,
                y2
        );

        for (int x = Math.min(x1, x2);
             x <= Math.max(x1, x2);
             x++) {

            for (int y = Math.min(y1, y2);
                 y <= Math.max(y1, y2);
                 y++) {

                wall(x, y, style);
            }
        }

        return this;
    }

    /**
     * Draws a straight wall with explicit texture/height values.
     */
    public LevelGenerator wallLine(
            int x1,
            int y1,
            int x2,
            int y2,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return wallLine(
                x1,
                y1,
                x2,
                y2,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    /**
     * Draws only the perimeter of a rectangle as walls.
     */
    public LevelGenerator wallRect(
            int x1,
            int y1,
            int x2,
            int y2) {

        return wallRect(
                x1,
                y1,
                x2,
                y2,
                defaultStyle()
        );
    }

    /**
     * Draws only the perimeter of a rectangle using the supplied style.
     */
    public LevelGenerator wallRect(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX; x <= maxX; x++) {
            wall(x, minY, style);
            wall(x, maxY, style);
        }

        for (int y = minY; y <= maxY; y++) {
            wall(minX, y, style);
            wall(maxX, y, style);
        }

        return this;
    }

    /**
     * Draws a rectangle outline with explicit values.
     */
    public LevelGenerator wallRect(
            int x1,
            int y1,
            int x2,
            int y2,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return wallRect(
                x1,
                y1,
                x2,
                y2,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    /**
     * Fills an entire rectangle with solid wall tiles using defaults.
     */
    public LevelGenerator solidBlock(
            int x1,
            int y1,
            int x2,
            int y2) {

        return solidBlock(
                x1,
                y1,
                x2,
                y2,
                defaultStyle()
        );
    }

    /**
     * Fills an entire rectangle with solid wall tiles.
     */
    public LevelGenerator solidBlock(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        fillSolid(x1, y1, x2, y2, style);

        return this;
    }

    // ===================================================================
    // Fill / Outline
    // ===================================================================

    /**
     * Fills a rectangle with open floor using the current defaults.
     */
    public LevelGenerator fill(
            int x1,
            int y1,
            int x2,
            int y2) {

        return fill(
                x1,
                y1,
                x2,
                y2,
                defaultStyle()
        );
    }

    /**
     * Fills a rectangle with open floor using the supplied style.
     */
    public LevelGenerator fill(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        forEachIn(
                x1,
                y1,
                x2,
                y2,
                (x, y) -> open(x, y, style)
        );

        return this;
    }

    /**
     * Fills a rectangle with open floor using explicit values.
     */
    public LevelGenerator fill(
            int x1,
            int y1,
            int x2,
            int y2,
            int floorTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return fill(
                x1,
                y1,
                x2,
                y2,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    /**
     * Draws only the outline of a rectangle as open tiles.
     *
     * <p>This is useful when "outline" means a perimeter of floor/tiles
     * rather than a wall. Use {@link #wallRect(int, int, int, int)} when
     * you specifically want solid wall tiles.</p>
     */
    public LevelGenerator outline(
            int x1,
            int y1,
            int x2,
            int y2) {

        return outline(
                x1,
                y1,
                x2,
                y2,
                defaultStyle()
        );
    }

    /**
     * Draws only the outline of a rectangle using the supplied style.
     */
    public LevelGenerator outline(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX; x <= maxX; x++) {
            open(x, minY, style);
            open(x, maxY, style);
        }

        for (int y = minY; y <= maxY; y++) {
            open(minX, y, style);
            open(maxX, y, style);
        }

        return this;
    }

    /**
     * Draws an open rectangle outline with explicit values.
     */
    public LevelGenerator outline(
            int x1,
            int y1,
            int x2,
            int y2,
            int floorTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return outline(
                x1,
                y1,
                x2,
                y2,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    // ===================================================================
    // Border
    // ===================================================================

    /**
     * Walls off the outermost ring using current defaults.
     */
    public LevelGenerator border() {

        return border(defaultStyle());
    }

    /**
     * Walls off the outermost ring using the supplied style.
     */
    public LevelGenerator border(TileStyle style) {

        return wallRect(
                0,
                0,
                width - 1,
                height - 1,
                style
        );
    }

    // ===================================================================
    // Floors / open space
    // ===================================================================

    /**
     * Opens one tile using the current defaults.
     */
    public LevelGenerator open(int x, int y) {

        return open(x, y, defaultStyle());
    }

    /**
     * Opens one tile using the supplied style.
     */
    public LevelGenerator open(
            int x,
            int y,
            TileStyle style) {

        Cell cell = cellAt(x, y);

        if (cell == null) {
            return this;
        }

        cell.solid = false;

        cell.floor = style.floorHeight;
        cell.ceiling = style.ceilingHeight;

        cell.wallTexture = style.wallTexture;
        cell.floorTexture = style.floorTexture;
        cell.topTexture = style.topTexture;
        cell.ceilingTexture = style.ceilingTexture;

        return this;
    }

    /**
     * Opens one tile with explicit values.
     */
    public LevelGenerator open(
            int x,
            int y,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return open(
                x,
                y,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    /**
     * Opens a tile as a doorway using defaults.
     */
    public LevelGenerator doorway(int x, int y) {

        return open(x, y);
    }

    /**
     * Opens a tile as a doorway using a specific style.
     */
    public LevelGenerator doorway(
            int x,
            int y,
            TileStyle style) {

        return open(x, y, style);
    }

    /**
     * Opens a tile as a doorway with explicit values.
     */
    public LevelGenerator doorway(
            int x,
            int y,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return open(
                x,
                y,
                wallTexture,
                floorTexture,
                topTexture,
                ceilingTexture,
                floorHeight,
                ceilingHeight
        );
    }

    /**
     * Opens every tile in a rectangle using defaults.
     */
    public LevelGenerator floorRect(
            int x1,
            int y1,
            int x2,
            int y2) {

        return fill(x1, y1, x2, y2);
    }

    /**
     * Opens every tile in a rectangle using a style.
     */
    public LevelGenerator floorRect(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        return fill(x1, y1, x2, y2, style);
    }

    // ===================================================================
    // Rooms
    // ===================================================================

    /**
     * Creates a room using the current default style.
     */
    public LevelGenerator room(
            int x1,
            int y1,
            int x2,
            int y2) {

        return room(
                x1,
                y1,
                x2,
                y2,
                defaultStyle(),
                defaultStyle()
        );
    }

    /**
     * Creates a room with one style for walls and another for its interior.
     *
     * <p>The perimeter uses {@code wallStyle}; the interior uses
     * {@code floorStyle}.</p>
     */
    public LevelGenerator room(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle wallStyle,
            TileStyle floorStyle) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        wallRect(
                minX,
                minY,
                maxX,
                maxY,
                wallStyle
        );

        if (maxX - minX >= 2 && maxY - minY >= 2) {

            fill(
                    minX + 1,
                    minY + 1,
                    maxX - 1,
                    maxY - 1,
                    floorStyle
            );

        } else {

            fill(
                    minX,
                    minY,
                    maxX,
                    maxY,
                    floorStyle
            );
        }

        return this;
    }

    /**
     * Creates a room using one style for both walls and floor.
     */
    public LevelGenerator room(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        return room(
                x1,
                y1,
                x2,
                y2,
                style,
                style
        );
    }

    /**
     * Creates a room with explicit wall/floor texture and height values.
     */
    public LevelGenerator room(
            int x1,
            int y1,
            int x2,
            int y2,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        TileStyle style = new TileStyle(
                wallTexture,
                floorTexture,
                topTexture,
                ceilingTexture,
                floorHeight,
                ceilingHeight
        );

        return room(
                x1,
                y1,
                x2,
                y2,
                style,
                style
        );
    }

    // ===================================================================
    // Heights / platforms
    // ===================================================================

    /**
     * Changes one tile's floor height using current defaults.
     */
    public LevelGenerator height(
            int x,
            int y,
            float floorHeight) {

        return height(
                x,
                y,
                floorHeight,
                defaultStyle()
        );
    }

    /**
     * Changes one tile's floor height while using the supplied style's
     * textures and preserving its vertical clearance.
     */
    public LevelGenerator height(
            int x,
            int y,
            float floorHeight,
            TileStyle style) {

        Cell cell = cellAt(x, y);

        if (cell == null) {
            return this;
        }

        float delta = style.ceilingHeight - style.floorHeight;

        return open(
                x,
                y,
                new TileStyle(
                        style.wallTexture,
                        style.floorTexture,
                        style.topTexture,
                        style.ceilingTexture,
                        floorHeight,
                        floorHeight + delta
                )
        );
    }

    /**
     * Sets an exact floor and ceiling height with explicit textures.
     */
    public LevelGenerator height(
            int x,
            int y,
            float floorHeight,
            float ceilingHeight,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture) {

        return open(
                x,
                y,
                wallTexture,
                floorTexture,
                topTexture,
                ceilingTexture,
                floorHeight,
                ceilingHeight
        );
    }

    /**
     * Raises/lowers a rectangle to one floor height using defaults.
     */
    public LevelGenerator heightRect(
            int x1,
            int y1,
            int x2,
            int y2,
            float floorHeight) {

        return heightRect(
                x1,
                y1,
                x2,
                y2,
                floorHeight,
                defaultStyle()
        );
    }

    /**
     * Raises/lowers a rectangle using a supplied style.
     */
    public LevelGenerator heightRect(
            int x1,
            int y1,
            int x2,
            int y2,
            float floorHeight,
            TileStyle style) {

        forEachIn(
                x1,
                y1,
                x2,
                y2,
                (x, y) -> height(x, y, floorHeight, style)
        );

        return this;
    }

    /**
     * Sets a rectangle to an exact floor/ceiling height and style.
     */
    public LevelGenerator heightRect(
            int x1,
            int y1,
            int x2,
            int y2,
            float floorHeight,
            float ceilingHeight,
            TileStyle style) {

        TileStyle exact = new TileStyle(
                style.wallTexture,
                style.floorTexture,
                style.topTexture,
                style.ceilingTexture,
                floorHeight,
                ceilingHeight
        );

        return fill(
                x1,
                y1,
                x2,
                y2,
                exact
        );
    }

    /**
     * Creates a stepped ramp using the current defaults.
     */
    public LevelGenerator ramp(
            int x1,
            int y1,
            int x2,
            int y2,
            float fromHeight,
            float toHeight) {

        return ramp(
                x1,
                y1,
                x2,
                y2,
                fromHeight,
                toHeight,
                defaultStyle()
        );
    }

    /**
     * Creates a stepped ramp using a specific style.
     */
    public LevelGenerator ramp(
            int x1,
            int y1,
            int x2,
            int y2,
            float fromHeight,
            float toHeight,
            TileStyle style) {

        requireAxisAligned(
                "ramp()",
                x1,
                y1,
                x2,
                y2
        );

        boolean vertical = x1 == x2;

        int steps = vertical
                ? Math.abs(y2 - y1)
                : Math.abs(x2 - x1);

        float clearance =
                style.ceilingHeight - style.floorHeight;

        for (int i = 0; i <= steps; i++) {

            float t = steps == 0
                    ? 0.0f
                    : (float) i / (float) steps;

            float h =
                    fromHeight
                            + (toHeight - fromHeight) * t;

            int x = vertical
                    ? x1
                    : x1
                    + Integer.signum(x2 - x1) * i;

            int y = vertical
                    ? y1
                    + Integer.signum(y2 - y1) * i
                    : y1;

            height(
                    x,
                    y,
                    h,
                    style.floorHeight + clearance,
                    style.wallTexture,
                    style.floorTexture,
                    style.topTexture,
                    style.ceilingTexture
            );
        }

        return this;
    }

    // ===================================================================
    // Corridors
    // ===================================================================

    /**
     * Creates an L-shaped corridor using defaults.
     */
    public LevelGenerator corridor(
            int x1,
            int y1,
            int x2,
            int y2) {

        return corridor(
                x1,
                y1,
                x2,
                y2,
                true,
                defaultStyle()
        );
    }

    /**
     * Creates an L-shaped corridor using a specific style.
     */
    public LevelGenerator corridor(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        return corridor(
                x1,
                y1,
                x2,
                y2,
                true,
                style
        );
    }

    /**
     * Creates an L-shaped corridor, selecting which leg comes first.
     */
    public LevelGenerator corridor(
            int x1,
            int y1,
            int x2,
            int y2,
            boolean horizontalFirst) {

        return corridor(
                x1,
                y1,
                x2,
                y2,
                horizontalFirst,
                defaultStyle()
        );
    }

    /**
     * Creates an L-shaped corridor with a specific style.
     */
    public LevelGenerator corridor(
            int x1,
            int y1,
            int x2,
            int y2,
            boolean horizontalFirst,
            TileStyle style) {

        if (horizontalFirst) {

            corridorSegment(
                    x1,
                    y1,
                    x2,
                    y1,
                    style
            );

            corridorSegment(
                    x2,
                    y1,
                    x2,
                    y2,
                    style
            );

        } else {

            corridorSegment(
                    x1,
                    y1,
                    x1,
                    y2,
                    style
            );

            corridorSegment(
                    x1,
                    y2,
                    x2,
                    y2,
                    style
            );
        }

        return this;
    }

    /**
     * Creates an L-shaped corridor with explicit style values.
     */
    public LevelGenerator corridor(
            int x1,
            int y1,
            int x2,
            int y2,
            boolean horizontalFirst,
            int wallTexture,
            int floorTexture,
            int topTexture,
            int ceilingTexture,
            float floorHeight,
            float ceilingHeight) {

        return corridor(
                x1,
                y1,
                x2,
                y2,
                horizontalFirst,
                new TileStyle(
                        wallTexture,
                        floorTexture,
                        topTexture,
                        ceilingTexture,
                        floorHeight,
                        ceilingHeight
                )
        );
    }

    private void corridorSegment(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX; x <= maxX; x++) {

            for (int y = minY; y <= maxY; y++) {

                for (int w = 0;
                     w < corridorWidth;
                     w++) {

                    if (minX == maxX) {
                        open(x + w, y, style);
                    } else {
                        open(x, y + w, style);
                    }
                }
            }
        }
    }

    // ===================================================================
    // Per-tile texture overrides
    // ===================================================================

    public LevelGenerator wallTextureAt(
            int x,
            int y,
            int texture) {

        Cell cell = cellAt(x, y);

        if (cell != null) {
            cell.wallTexture = texture;
        }

        return this;
    }

    public LevelGenerator floorTextureAt(
            int x,
            int y,
            int texture) {

        Cell cell = cellAt(x, y);

        if (cell != null) {
            cell.floorTexture = texture;
        }

        return this;
    }

    public LevelGenerator topTextureAt(
            int x,
            int y,
            int texture) {

        Cell cell = cellAt(x, y);

        if (cell != null) {
            cell.topTexture = texture;
        }

        return this;
    }

    public LevelGenerator ceilingTextureAt(
            int x,
            int y,
            int texture) {

        Cell cell = cellAt(x, y);

        if (cell != null) {
            cell.ceilingTexture = texture;
        }

        return this;
    }

    // ===================================================================
    // Custom editor
    // ===================================================================

    /**
     * A callback for arbitrary single-tile modifications.
     */
    @FunctionalInterface
    public interface TileEditor {
        void edit(TileHandle tile);
    }

    /**
     * Runs a custom editor against one tile.
     */
    public LevelGenerator edit(
            int x,
            int y,
            TileEditor editor) {

        Cell cell = cellAt(x, y);

        if (cell != null) {
            editor.edit(new TileHandle(cell));
        }

        return this;
    }

    /**
     * Runs a custom editor against every tile in a rectangle.
     */
    public LevelGenerator region(
            int x1,
            int y1,
            int x2,
            int y2,
            TileEditor editor) {

        forEachIn(
                x1,
                y1,
                x2,
                y2,
                (x, y) -> edit(x, y, editor)
        );

        return this;
    }

    /**
     * Mutable view of a generator tile.
     */
    public static final class TileHandle {

        private final Cell cell;

        private TileHandle(Cell cell) {
            this.cell = cell;
        }

        public TileHandle solid(boolean solid) {
            cell.solid = solid;
            return this;
        }

        public TileHandle floorHeight(float floorHeight) {
            cell.floor = floorHeight;
            return this;
        }

        public TileHandle ceilingHeight(float ceilingHeight) {
            cell.ceiling = ceilingHeight;
            return this;
        }

        public TileHandle wallTexture(int texture) {
            cell.wallTexture = texture;
            return this;
        }

        public TileHandle floorTexture(int texture) {
            cell.floorTexture = texture;
            return this;
        }

        public TileHandle topTexture(int texture) {
            cell.topTexture = texture;
            return this;
        }

        public TileHandle ceilingTexture(int texture) {
            cell.ceilingTexture = texture;
            return this;
        }

        public boolean isSolid() {
            return cell.solid;
        }

        public float floorHeight() {
            return cell.floor;
        }

        public float ceilingHeight() {
            return cell.ceiling;
        }

        public int wallTexture() {
            return cell.wallTexture;
        }

        public int floorTexture() {
            return cell.floorTexture;
        }

        public int topTexture() {
            return cell.topTexture;
        }

        public int ceilingTexture() {
            return cell.ceilingTexture;
        }
    }

    // ===================================================================
    // Queries
    // ===================================================================

    public boolean isSolid(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null || cell.solid;
    }

    public float floorHeightAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0.0f
                : cell.floor;
    }

    public float ceilingHeightAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0.0f
                : cell.ceiling;
    }

    public int wallTextureAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0
                : cell.wallTexture;
    }

    public int floorTextureAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0
                : cell.floorTexture;
    }

    public int topTextureAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0
                : cell.topTexture;
    }

    public int ceilingTextureAt(int x, int y) {

        Cell cell = cellAt(x, y);

        return cell == null
                ? 0
                : cell.ceilingTexture;
    }

    public boolean inBounds(int x, int y) {

        return x >= 0
                && y >= 0
                && x < width
                && y < height;
    }

    // ===================================================================
    // Build
    // ===================================================================

    /**
     * Materializes the internal grid into a real {@link TileMap}.
     */
    public TileMap build() {

        TileMap map = new TileMap(width, height);

        for (int x = 0; x < width; x++) {

            for (int y = 0; y < height; y++) {

                Cell cell = cells[x][y];

                Tile tile = new Tile(
                        cell.floor,
                        cell.ceiling
                );

                tile.solid = cell.solid;

                tile.wallTexture = cell.wallTexture;
                tile.floorTexture = cell.floorTexture;
                tile.topTexture = cell.topTexture;
                tile.ceilingTexture = cell.ceilingTexture;

                map.set(x, y, tile);
            }
        }

        return map;
    }

    // ===================================================================
    // Internals
    // ===================================================================

    @FunctionalInterface
    private interface TileAction {
        void run(int x, int y);
    }

    private void forEachIn(
            int x1,
            int y1,
            int x2,
            int y2,
            TileAction action) {

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);

        int minY = Math.min(y1, y2);
        int maxY = Math.max(y1, y2);

        for (int x = minX; x <= maxX; x++) {

            for (int y = minY; y <= maxY; y++) {

                action.run(x, y);
            }
        }
    }

    private void fillSolid(
            int x1,
            int y1,
            int x2,
            int y2,
            TileStyle style) {

        forEachIn(
                x1,
                y1,
                x2,
                y2,
                (x, y) -> wall(x, y, style)
        );
    }

    private void requireAxisAligned(
            String operation,
            int x1,
            int y1,
            int x2,
            int y2) {

        if (x1 != x2 && y1 != y2) {

            throw new IllegalArgumentException(
                    operation
                            + " requires a horizontal or vertical line."
            );
        }
    }

    private Cell cellAt(int x, int y) {

        if (!inBounds(x, y)) {
            return null;
        }

        return cells[x][y];
    }

    // ===================================================================
    // Internal cell representation
    // ===================================================================

    /**
     * Lightweight pre-Tile representation.
     */
    private static final class Cell {

        boolean solid;

        float floor;
        float ceiling;

        int wallTexture;
        int floorTexture;
        int topTexture;
        int ceilingTexture;

        Cell(float floor, float ceiling) {

            this.floor = floor;
            this.ceiling = ceiling;
            this.solid = false;
        }
    }
}
