package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.Arrays;

/**
 * Software height-field raycaster.
 *
 * <p>Geometry model:</p>
 *
 * <ul>
 *     <li>Walkable tiles occupy the vertical interval
 *         [floor, ceiling].</li>
 *     <li>Adjacent tiles with different floor heights create
 *         vertical step surfaces.</li>
 *     <li>Solid tiles occupy their complete [floor, ceiling]
 *         interval and stop the ray.</li>
 *     <li>Solid tiles may optionally render a top plane.</li>
 * </ul>
 *
 * <p>Important depth rule:</p>
 *
 * <p>Every value written to the depth buffer is camera-forward
 * (perpendicular) depth. Ray distance is never written directly
 * to the depth buffer.</p>
 *
 * <p>The renderer performs no object allocation inside render().</p>
 */
public final class HeightRaycaster {

    private static final float EPSILON = 0.000001f;
    private static final float MIN_DISTANCE = 0.0001f;

    private final TileMap map;

    private final int width;
    private final int height;

    private final float focalLength;

    /**
     * Maximum ray distance in world/tile units.
     */
    private final float renderDistance;

    /*
     * ---------------------------------------------------------
     * REUSABLE RENDER BUFFERS
     * ---------------------------------------------------------
     */

    private final Surface[] surfacePool;
    private final PlaneSurface[] planePool;

    /**
     * One depth value for every screen row.
     *
     * Because the renderer processes one screen column at a time,
     * this is a column depth buffer rather than a complete
     * width * height buffer.
     *
     * Every value is camera-forward depth.
     */
    private final float[] depth;

    /*
     * Debug information.
     *
     * These are intentionally simple counters rather than
     * allocations or exception-based diagnostics.
     */
    private int surfacePoolOverflows;
    private int planePoolOverflows;

    /*
     * ---------------------------------------------------------
     * VERTICAL SURFACE
     * ---------------------------------------------------------
     */

    private static final class Surface {

        /**
         * Ray distance at which the surface was hit.
         */
        float distance;

        /**
         * Camera-forward/perpendicular distance.
         */
        float perpendicularDistance;

        /**
         * Lower world-space Z.
         */
        float low;

        /**
         * Upper world-space Z.
         */
        float high;

        /**
         * Horizontal texture coordinate.
         */
        float textureX;

        Tile tile;

        void set(
                float distance,
                float perpendicularDistance,
                float low,
                float high,
                float textureX,
                Tile tile) {

            this.distance =
                    distance;

            this.perpendicularDistance =
                    perpendicularDistance;

            this.low =
                    low;

            this.high =
                    high;

            this.textureX =
                    textureX;

            this.tile =
                    tile;
        }
    }

    /*
     * ---------------------------------------------------------
     * HORIZONTAL SURFACE
     * ---------------------------------------------------------
     */

    private static final class PlaneSurface {

        /**
         * Ray distance at which this tile begins.
         */
        float entryDistance;

        /**
         * Ray distance at which this tile ends.
         */
        float exitDistance;

        /**
         * World-space Z of the plane.
         */
        float height;

        Tile tile;

        /**
         * True for ceilings.
         */
        boolean ceiling;

        /**
         * True for a solid/raised top surface.
         *
         * Ordinary walkable floors have top == false.
         */
        boolean top;

        void set(
                float entryDistance,
                float exitDistance,
                float height,
                Tile tile,
                boolean ceiling,
                boolean top) {

            this.entryDistance =
                    entryDistance;

            this.exitDistance =
                    exitDistance;

            this.height =
                    height;

            this.tile =
                    tile;

            this.ceiling =
                    ceiling;

            this.top =
                    top;
        }
    }

    /*
     * ---------------------------------------------------------
     * CONSTRUCTOR
     * ---------------------------------------------------------
     */

    public HeightRaycaster(
            TileMap map,
            int width,
            int height,
            float fov,
            float renderDistance) {

        if (map == null) {
            throw new IllegalArgumentException(
                    "map cannot be null"
            );
        }

        if (width <= 0) {
            throw new IllegalArgumentException(
                    "width must be > 0"
            );
        }

        if (height <= 0) {
            throw new IllegalArgumentException(
                    "height must be > 0"
            );
        }

        this.map = map;

        this.width = width;
        this.height = height;

        this.renderDistance =
                Math.max(
                        0.001f,
                        renderDistance
                );

        this.focalLength =
                (width * 0.5f) /
                        Mathf.tan(
                                fov * 0.5f
                        );

        /*
         * -----------------------------------------------------
         * DEPTH BUFFER
         * -----------------------------------------------------
         */

        depth =
                new float[height];

        /*
         * -----------------------------------------------------
         * POOL SIZING
         * -----------------------------------------------------
         *
         * A ray can cross approximately:
         *
         *     renderDistance * sqrt(2)
         *
         * grid cells in the worst case.
         *
         * Add generous headroom for boundary conditions.
         */

        int maxTiles =
                (int) Math.ceil(
                        renderDistance *
                                1.5f
                ) + 16;

        int maxSurfaces =
                Math.max(
                        32,
                        maxTiles
                );

        /*
         * Each walkable tile can potentially contribute:
         *
         *     floor
         *     ceiling
         *
         * so allow two planes per tile.
         */
        int maxPlanes =
                Math.max(
                        64,
                        maxTiles * 2
                );

        surfacePool =
                new Surface[maxSurfaces];

        for (int i = 0;
             i < surfacePool.length;
             i++) {

            surfacePool[i] =
                    new Surface();
        }

        planePool =
                new PlaneSurface[maxPlanes];

        for (int i = 0;
             i < planePool.length;
             i++) {

            planePool[i] =
                    new PlaneSurface();
        }
    }

    /*
     * ---------------------------------------------------------
     * BACKWARD-COMPATIBLE CONSTRUCTOR
     * ---------------------------------------------------------
     */

    public HeightRaycaster(
            TileMap map,
            int width,
            int height,
            float fov) {

        this(
                map,
                width,
                height,
                fov,
                64.0f
        );
    }

    /*
     * ---------------------------------------------------------
     * RENDER
     * ---------------------------------------------------------
     */

    public void render(
            PixelGraphics pg,
            Camera3D camera) {

        if (pg == null ||
                camera == null) {

            return;
        }

        for (int screenX = 0;
             screenX < width;
             screenX++) {

            castColumn(
                    pg,
                    camera,
                    screenX
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * CAST COLUMN
     * ---------------------------------------------------------
     */

    private void castColumn(
            PixelGraphics pg,
            Camera3D camera,
            int screenX) {

        /*
         * Screen-space horizontal coordinate:
         *
         *     -1 ... +1
         */
        float cameraX =
                2.0f *
                        (screenX + 0.5f) /
                        (float) width
                        - 1.0f;

        /*
         * Horizontal ray angle relative to camera forward.
         */
        float rayAngle =
                camera.rotation.y
                        +
                        (float) Math.atan(
                                cameraX *
                                        Mathf.tan(
                                                camera.fov * 0.5f
                                        )
                        );

        float rayDirX =
                Mathf.cos(rayAngle);

        float rayDirY =
                Mathf.sin(rayAngle);

        castDDA(
                pg,
                camera,
                screenX,
                rayDirX,
                rayDirY,
                rayAngle
        );
    }

    /*
     * ---------------------------------------------------------
     * DDA
     * ---------------------------------------------------------
     */

    private void castDDA(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float rayAngle) {

        final float rayX =
                camera.position.x;

        final float rayY =
                camera.position.y;

        int mapX =
                (int) Math.floor(rayX);

        int mapY =
                (int) Math.floor(rayY);

        /*
         * -----------------------------------------------------
         * RESET PER-RAY COUNTERS
         * -----------------------------------------------------
         */

        int surfaceCount = 0;
        int planeCount = 0;

        /*
         * -----------------------------------------------------
         * RESET DEPTH
         * -----------------------------------------------------
         *
         * This is not an allocation.
         *
         * It clears only the current column's depth buffer.
         */
        Arrays.fill(
                depth,
                Float.POSITIVE_INFINITY
        );

        /*
         * -----------------------------------------------------
         * DELTA DISTANCE
         * -----------------------------------------------------
         */

        final float deltaDistX;
        final float deltaDistY;

        if (Math.abs(rayDirX) <
                EPSILON) {

            deltaDistX =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistX =
                    Math.abs(
                            1.0f /
                                    rayDirX
                    );
        }

        if (Math.abs(rayDirY) <
                EPSILON) {

            deltaDistY =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistY =
                    Math.abs(
                            1.0f /
                                    rayDirY
                    );
        }

        /*
         * -----------------------------------------------------
         * STEP DIRECTION
         * -----------------------------------------------------
         */

        final int stepX;
        final int stepY;

        float sideDistX;
        float sideDistY;

        if (rayDirX < 0.0f) {

            stepX = -1;

            sideDistX =
                    (rayX - mapX) *
                            deltaDistX;

        } else {

            stepX = 1;

            sideDistX =
                    (mapX + 1.0f - rayX) *
                            deltaDistX;
        }

        if (rayDirY < 0.0f) {

            stepY = -1;

            sideDistY =
                    (rayY - mapY) *
                            deltaDistY;

        } else {

            stepY = 1;

            sideDistY =
                    (mapY + 1.0f - rayY) *
                            deltaDistY;
        }

        /*
         * -----------------------------------------------------
         * CURRENT TILE
         * -----------------------------------------------------
         */

        Tile current = null;

        if (map.inBounds(
                mapX,
                mapY)) {

            current =
                    map.get(
                            mapX,
                            mapY
                    );

        } else {

            /*
             * Camera is outside the map.
             *
             * We cannot reliably traverse from here.
             */
            return;
        }

        /*
         * Distance at which current tile begins.
         */
        float currentEntryDistance =
                0.0f;

        /*
         * -----------------------------------------------------
         * SAFETY LIMIT
         * -----------------------------------------------------
         *
         * Worst-case grid crossing rate is approximately:
         *
         *     |dx| + |dy|
         *
         * whose maximum is sqrt(2).
         *
         * Use a generous bound.
         */
        int maxSteps =
                (int) Math.ceil(
                        renderDistance *
                                1.5f
                ) + 32;

        /*
         * -----------------------------------------------------
         * WALK RAY
         * -----------------------------------------------------
         */

        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            /*
             * -------------------------------------------------
             * FIND NEXT GRID BOUNDARY
             * -------------------------------------------------
             */

            float nextBoundary =
                    Math.min(
                            sideDistX,
                            sideDistY
                    );

            /*
             * -------------------------------------------------
             * RENDER-DISTANCE LIMIT
             * -------------------------------------------------
             *
             * The current tile continues until the render
             * distance if no map boundary is reached first.
             */
            if (nextBoundary >
                    renderDistance) {

                if (current != null) {

                    planeCount =
                            addTilePlanes(
                                    current,
                                    currentEntryDistance,
                                    renderDistance,
                                    planeCount
                            );
                }

                break;
            }

            /*
             * -------------------------------------------------
             * CURRENT TILE EXIT
             * -------------------------------------------------
             */

            final float exitDistance =
                    nextBoundary;

            /*
             * -------------------------------------------------
             * RENDER CURRENT TILE
             * -------------------------------------------------
             */

            if (current != null) {

                planeCount =
                        addTilePlanes(
                                current,
                                currentEntryDistance,
                                exitDistance,
                                planeCount
                        );
            }

            /*
             * -------------------------------------------------
             * CROSS GRID BOUNDARY
             * -------------------------------------------------
             */

            final int side;

            final float boundaryDistance;

            if (sideDistX < sideDistY) {

                boundaryDistance =
                        sideDistX;

                sideDistX +=
                        deltaDistX;

                mapX +=
                        stepX;

                side = 0;

            } else {

                boundaryDistance =
                        sideDistY;

                sideDistY +=
                        deltaDistY;

                mapY +=
                        stepY;

                side = 1;
            }

            if (boundaryDistance >
                    renderDistance) {

                break;
            }

            /*
             * -------------------------------------------------
             * FIND NEXT TILE
             * -------------------------------------------------
             */

            if (!map.inBounds(
                    mapX,
                    mapY)) {

                /*
                 * We reached the edge of the map.
                 *
                 * Current tile was already rendered up to this
                 * boundary above.
                 */
                break;
            }

            Tile next =
                    map.get(
                            mapX,
                            mapY
                    );

            /*
             * -------------------------------------------------
             * HEIGHT TRANSITION
             * -------------------------------------------------
             *
             * A transition exists when adjacent tile floor
             * heights differ.
             */
            if (current != null &&
                    next != null) {

                float oldFloor =
                        current.floor;

                float newFloor =
                        next.floor;

                if (Math.abs(
                        oldFloor -
                                newFloor
                ) > EPSILON) {

                    float low =
                            Math.min(
                                    oldFloor,
                                    newFloor
                            );

                    float high =
                            Math.max(
                                    oldFloor,
                                    newFloor
                            );

                    /*
                     * The upper side owns the visible wall.
                     *
                     * This preserves the behavior of the
                     * original renderer.
                     */
                    Tile wallTile;

                    if (oldFloor >
                            newFloor) {

                        wallTile =
                                current;

                    } else {

                        wallTile =
                                next;
                    }

                    float textureX =
                            calculateTextureX(
                                    camera,
                                    rayDirX,
                                    rayDirY,
                                    boundaryDistance,
                                    side
                            );

                    float perpendicularDistance =
                            calculatePerpendicularDistance(
                                    boundaryDistance,
                                    rayAngle,
                                    camera.rotation.y
                            );

                    if (surfaceCount <
                            surfacePool.length) {

                        Surface surface =
                                surfacePool[
                                        surfaceCount++
                                        ];

                        surface.set(
                                boundaryDistance,
                                perpendicularDistance,
                                low,
                                high,
                                textureX,
                                wallTile
                        );

                    } else {

                        surfacePoolOverflows++;
                    }
                }
            }

            /*
             * -------------------------------------------------
             * SOLID TILE
             * -------------------------------------------------
             */

            if (next != null &&
                    next.solid) {

                /*
                 * Determine how far the solid tile extends
                 * before its next grid boundary.
                 */
                float solidExitDistance =
                        Math.min(
                                sideDistX,
                                sideDistY
                        );

                solidExitDistance =
                        Math.min(
                                solidExitDistance,
                                renderDistance
                        );

                /*
                 * -------------------------------------------------
                 * SOLID TOP
                 * -------------------------------------------------
                 */

                if (next.renderTop) {

                    planeCount =
                            addSolidTopPlane(
                                    next,
                                    boundaryDistance,
                                    solidExitDistance,
                                    planeCount
                            );
                }

                /*
                 * -------------------------------------------------
                 * SOLID FRONT WALL
                 * -------------------------------------------------
                 */

                float textureX =
                        calculateTextureX(
                                camera,
                                rayDirX,
                                rayDirY,
                                boundaryDistance,
                                side
                        );

                float perpendicularDistance =
                        calculatePerpendicularDistance(
                                boundaryDistance,
                                rayAngle,
                                camera.rotation.y
                        );

                if (surfaceCount <
                        surfacePool.length) {

                    Surface surface =
                            surfacePool[
                                    surfaceCount++
                                    ];

                    surface.set(
                            boundaryDistance,
                            perpendicularDistance,
                            next.floor,
                            next.ceiling,
                            textureX,
                            next
                    );

                } else {

                    surfacePoolOverflows++;
                }

                /*
                 * Solid geometry terminates this ray.
                 */
                break;
            }

            /*
             * -------------------------------------------------
             * ENTER NEXT WALKABLE TILE
             * -------------------------------------------------
             */

            current =
                    next;

            currentEntryDistance =
                    boundaryDistance;
        }

        /*
         * -----------------------------------------------------
         * RENDER VERTICAL SURFACES
         * -----------------------------------------------------
         *
         * Surfaces are already ordered approximately front to
         * back by DDA traversal.
         */
        for (int i = 0;
             i < surfaceCount;
             i++) {

            drawSurfaceDepthTested(
                    pg,
                    camera,
                    screenX,
                    surfacePool[i],
                    depth
            );
        }

        /*
         * -----------------------------------------------------
         * RENDER HORIZONTAL PLANES
         * -----------------------------------------------------
         */

        for (int i = 0;
             i < planeCount;
             i++) {

            drawPlaneDepthTested(
                    pg,
                    camera,
                    screenX,
                    rayDirX,
                    rayDirY,
                    rayAngle,
                    planePool[i],
                    depth
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * ADD NORMAL TILE PLANES
     * ---------------------------------------------------------
     */

    private int addTilePlanes(
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {

        if (tile == null) {
            return planeCount;
        }

        if (exitDistance <=
                entryDistance) {

            return planeCount;
        }

        /*
         * -----------------------------------------------------
         * FLOOR
         * -----------------------------------------------------
         *
         * A raised tile uses its top texture for its visible
         * upper surface when available.
         */
        PixelGraphics floorTexture;

        if (tile.floor >
                EPSILON) {

            floorTexture =
                    getTopTextureSafe(
                            tile
                    );

            if (!validTexture(
                    floorTexture)) {

                floorTexture =
                        getFloorTextureSafe(
                                tile
                        );
            }

        } else {

            floorTexture =
                    getFloorTextureSafe(
                            tile
                    );
        }

        if (validTexture(
                floorTexture)) {

            if (planeCount <
                    planePool.length) {

                PlaneSurface plane =
                        planePool[
                                planeCount++
                                ];

                plane.set(
                        entryDistance,
                        exitDistance,
                        tile.floor,
                        tile,
                        false,
                        tile.floor >
                                EPSILON
                );

            } else {

                planePoolOverflows++;
            }
        }

        /*
         * -----------------------------------------------------
         * CEILING
         * -----------------------------------------------------
         */

        PixelGraphics ceilingTexture =
                getCeilingTextureSafe(
                        tile
                );

        if (validTexture(
                ceilingTexture)) {

            if (planeCount <
                    planePool.length) {

                PlaneSurface plane =
                        planePool[
                                planeCount++
                                ];

                plane.set(
                        entryDistance,
                        exitDistance,
                        tile.ceiling,
                        tile,
                        true,
                        false
                );

            } else {

                planePoolOverflows++;
            }
        }

        return planeCount;
    }

    /*
     * ---------------------------------------------------------
     * ADD SOLID TOP
     * ---------------------------------------------------------
     */

    private int addSolidTopPlane(
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {

        if (tile == null) {
            return planeCount;
        }

        if (exitDistance <=
                entryDistance) {

            return planeCount;
        }

        PixelGraphics texture =
                getTopTextureSafe(
                        tile
                );

        /*
         * Fallback to floor texture.
         */
        if (!validTexture(texture)) {

            texture =
                    getFloorTextureSafe(
                            tile
                    );
        }

        if (!validTexture(texture)) {
            return planeCount;
        }

        /*
         * The top of a solid block is at its ceiling.
         */
        if (planeCount <
                planePool.length) {

            PlaneSurface plane =
                    planePool[
                            planeCount++
                            ];

            plane.set(
                    entryDistance,
                    exitDistance,
                    tile.ceiling,
                    tile,
                    false,
                    true
            );

        } else {

            planePoolOverflows++;
        }

        return planeCount;
    }

    /*
     * ---------------------------------------------------------
     * TEXTURE ACCESS
     * ---------------------------------------------------------
     */

    private PixelGraphics getTopTextureSafe(
            Tile tile) {

        if (tile == null) {
            return null;
        }

        return tile.getTopTexture();
    }

    private PixelGraphics getFloorTextureSafe(
            Tile tile) {

        if (tile == null) {
            return null;
        }

        return tile.getFloorTexture();
    }

    private PixelGraphics getCeilingTextureSafe(
            Tile tile) {

        if (tile == null) {
            return null;
        }

        return tile.getCeilingTexture();
    }

    private PixelGraphics getWallTextureSafe(
            Tile tile) {

        if (tile == null) {
            return null;
        }

        return tile.getWallTexture();
    }

    private boolean validTexture(
            PixelGraphics texture) {

        return texture != null &&
                texture.width > 0 &&
                texture.height > 0;
    }

    /*
     * ---------------------------------------------------------
     * HORIZON
     * ---------------------------------------------------------
     */

    private float getHorizon(
            Camera3D camera) {

        return height * 0.5f
                +
                camera.rotation.x *
                        focalLength;
    }

    /*
     * ---------------------------------------------------------
     * PERPENDICULAR DEPTH
     * ---------------------------------------------------------
     *
     * This is the single depth convention used by the renderer.
     *
     * rayDistance:
     *
     *     distance traveled along the ray
     *
     * perpendicularDistance:
     *
     *     distance along camera forward direction
     *
     * The latter is what goes into the depth buffer.
     */

    private float calculatePerpendicularDistance(
            float rayDistance,
            float rayAngle,
            float cameraAngle) {

        float distance =
                rayDistance *
                        Mathf.cos(
                                rayAngle -
                                        cameraAngle
                        );

        if (distance <
                MIN_DISTANCE) {

            distance =
                    MIN_DISTANCE;
        }

        return distance;
    }

    /*
     * ---------------------------------------------------------
     * DRAW PLANE
     * ---------------------------------------------------------
     */

    private void drawPlaneDepthTested(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float rayAngle,
            PlaneSurface plane,
            float[] depth) {

        if (plane.tile == null) {
            return;
        }

        /*
         * -----------------------------------------------------
         * SELECT TEXTURE
         * -----------------------------------------------------
         */

        PixelGraphics texture;

        if (plane.ceiling) {

            texture =
                    getCeilingTextureSafe(
                            plane.tile
                    );

        } else if (plane.top) {

            texture =
                    getTopTextureSafe(
                            plane.tile
                    );

            if (!validTexture(texture)) {

                texture =
                        getFloorTextureSafe(
                                plane.tile
                        );
            }

        } else {

            texture =
                    getFloorTextureSafe(
                            plane.tile
                    );
        }

        if (!validTexture(texture)) {
            return;
        }

        /*
         * -----------------------------------------------------
         * HEIGHT RELATIVE TO CAMERA
         * -----------------------------------------------------
         */

        float relativeHeight =
                plane.height -
                        camera.position.z;

        if (Math.abs(
                relativeHeight
        ) < EPSILON) {

            return;
        }

        float horizon =
                getHorizon(camera);

        /*
         * -----------------------------------------------------
         * SELECT SCREEN HALF
         * -----------------------------------------------------
         */

        int startY;
        int endY;

        if (relativeHeight > 0.0f) {

            /*
             * Plane is above camera.
             *
             * Render it above the horizon.
             */
            startY = 0;

            endY =
                    (int) Math.ceil(
                            horizon
                    ) - 1;

        } else {

            /*
             * Plane is below camera.
             *
             * Render it below the horizon.
             */
            startY =
                    (int) Math.floor(
                            horizon
                    );

            endY =
                    height - 1;
        }

        /*
         * -----------------------------------------------------
         * CLIP
         * -----------------------------------------------------
         */

        if (endY < 0 ||
                startY >= height) {

            return;
        }

        startY =
                Math.max(
                        0,
                        startY
                );

        endY =
                Math.min(
                        height - 1,
                        endY
                );

        /*
         * -----------------------------------------------------
         * DRAW EACH PIXEL
         * -----------------------------------------------------
         */

        for (int y = startY;
             y <= endY;
             y++) {

            float screenY =
                    (y + 0.5f) -
                            horizon;

            if (Math.abs(
                    screenY
            ) < EPSILON) {

                continue;
            }

            /*
             * -------------------------------------------------
             * RAY DISTANCE
             * -------------------------------------------------
             *
             * This is distance along the actual ray.
             */
            float rayDistance =
                    -relativeHeight *
                            focalLength /
                            screenY;

            if (rayDistance <=
                    MIN_DISTANCE) {

                continue;
            }

            /*
             * Explicit render distance.
             */
            if (rayDistance >
                    renderDistance) {

                continue;
            }

            /*
             * -------------------------------------------------
             * TILE INTERVAL
             * -------------------------------------------------
             *
             * The point must actually lie within the tile
             * represented by this plane.
             */
            if (rayDistance <
                    plane.entryDistance -
                            0.0001f) {

                continue;
            }

            if (rayDistance >
                    plane.exitDistance +
                            0.0001f) {

                continue;
            }

            /*
             * -------------------------------------------------
             * CAMERA-FORWARD DEPTH
             * -------------------------------------------------
             *
             * This is critical:
             *
             * the value written into depth[] must have the
             * same meaning as wall depth.
             */
            float cameraDepth =
                    calculatePerpendicularDistance(
                            rayDistance,
                            rayAngle,
                            camera.rotation.y
                    );

            /*
             * -------------------------------------------------
             * DEPTH TEST
             * -------------------------------------------------
             */

            if (cameraDepth >=
                    depth[y]) {

                continue;
            }

            /*
             * -------------------------------------------------
             * WORLD POSITION
             * -------------------------------------------------
             */

            float worldX =
                    camera.position.x +
                            rayDirX *
                                    rayDistance;

            float worldY =
                    camera.position.y +
                            rayDirY *
                                    rayDistance;

            /*
             * -------------------------------------------------
             * WORLD-SPACE TEXTURE COORDINATES
             * -------------------------------------------------
             */

            float textureX =
                    worldX -
                            (float) Math.floor(
                                    worldX
                            );

            float textureY =
                    worldY -
                            (float) Math.floor(
                                    worldY
                            );

            /*
             * floor() should already make these positive for
             * ordinary finite coordinates, but retain the
             * normalization for negative world coordinates.
             */
            if (textureX < 0.0f) {
                textureX += 1.0f;
            }

            if (textureY < 0.0f) {
                textureY += 1.0f;
            }

            textureX =
                    Math.clamp(
                            textureX,
                            0.0f,
                            0.999999f
                    );

            textureY =
                    Math.clamp(
                            textureY,
                            0.0f,
                            0.999999f
                    );

            /*
             * -------------------------------------------------
             * TEXTURE PIXEL
             * -------------------------------------------------
             */

            int texX =
                    (int) (
                            textureX *
                                    texture.width
                    );

            int texY =
                    (int) (
                            textureY *
                                    texture.height
                    );

            texX =
                    Math.clamp(
                            texX,
                            0,
                            texture.width - 1
                    );

            texY =
                    Math.clamp(
                            texY,
                            0,
                            texture.height - 1
                    );

            /*
             * -------------------------------------------------
             * WRITE PIXEL
             * -------------------------------------------------
             */

            pg.setPixel(
                    screenX,
                    y,
                    texture.getPixel(
                            texX,
                            texY
                    )
            );

            /*
             * -------------------------------------------------
             * WRITE DEPTH
             * -------------------------------------------------
             */

            depth[y] =
                    cameraDepth;
        }
    }

    /*
     * ---------------------------------------------------------
     * DRAW WALL DEPTH TESTED
     * ---------------------------------------------------------
     */

    private void drawSurfaceDepthTested(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            Surface surface,
            float[] depth) {

        Tile tile =
                surface.tile;

        if (tile == null) {
            return;
        }

        if (surface.high <=
                surface.low) {

            return;
        }

        PixelGraphics texture =
                getWallTextureSafe(
                        tile
                );

        if (!validTexture(texture)) {
            return;
        }

        /*
         * -----------------------------------------------------
         * DEPTH
         * -----------------------------------------------------
         *
         * surface.perpendicularDistance is already camera
         * forward depth.
         */
        float cameraDepth =
                surface.perpendicularDistance;

        if (cameraDepth <=
                MIN_DISTANCE) {

            cameraDepth =
                    MIN_DISTANCE;
        }

        /*
         * -----------------------------------------------------
         * PROJECT WALL
         * -----------------------------------------------------
         */

        int top =
                project(
                        camera,
                        surface.high,
                        cameraDepth
                );

        int bottom =
                project(
                        camera,
                        surface.low,
                        cameraDepth
                );

        if (top > bottom) {

            int temp =
                    top;

            top =
                    bottom;

            bottom =
                    temp;
        }

        /*
         * -----------------------------------------------------
         * SCREEN CLIP
         * -----------------------------------------------------
         */

        if (bottom < 0 ||
                top >= height) {

            return;
        }

        int clippedTop =
                Math.max(
                        0,
                        top
                );

        int clippedBottom =
                Math.min(
                        height - 1,
                        bottom
                );

        float projectedHeight =
                bottom - top;

        if (projectedHeight <=
                0.001f) {

            return;
        }

        /*
         * -----------------------------------------------------
         * TEXTURE X
         * -----------------------------------------------------
         */

        int texX =
                (int) (
                        surface.textureX *
                                texture.width
                );

        texX =
                Math.clamp(
                        texX,
                        0,
                        texture.width - 1
                );

        /*
         * -----------------------------------------------------
         * DRAW
         * -----------------------------------------------------
         */

        for (int y = clippedTop;
             y <= clippedBottom;
             y++) {

            /*
             * -------------------------------------------------
             * DEPTH TEST
             * -------------------------------------------------
             */

            if (cameraDepth >=
                    depth[y]) {

                continue;
            }

            /*
             * -------------------------------------------------
             * VERTICAL TEXTURE COORDINATE
             * -------------------------------------------------
             */

            float t =
                    (y - top) /
                            projectedHeight;

            t =
                    Math.clamp(
                            t,
                            0.0f,
                            0.999999f
                    );

            int texY =
                    (int) (
                            t *
                                    texture.height
                    );

            texY =
                    Math.clamp(
                            texY,
                            0,
                            texture.height - 1
                    );

            /*
             * -------------------------------------------------
             * WRITE PIXEL
             * -------------------------------------------------
             */

            pg.setPixel(
                    screenX,
                    y,
                    texture.getPixel(
                            texX,
                            texY
                    )
            );

            /*
             * -------------------------------------------------
             * WRITE DEPTH
             * -------------------------------------------------
             */

            depth[y] =
                    cameraDepth;
        }
    }

    /*
     * ---------------------------------------------------------
     * WALL TEXTURE X
     * ---------------------------------------------------------
     */

    private float calculateTextureX(
            Camera3D camera,
            float rayDirX,
            float rayDirY,
            float rayDistance,
            int side) {

        float coordinate;

        /*
         * X-side wall:
         *
         * X coordinate is fixed by the grid boundary,
         * therefore use world Y as texture position.
         */
        if (side == 0) {

            coordinate =
                    camera.position.y
                            +
                            rayDistance *
                                    rayDirY;

        } else {

            /*
             * Y-side wall:
             *
             * use world X.
             */
            coordinate =
                    camera.position.x
                            +
                            rayDistance *
                                    rayDirX;
        }

        coordinate -=
                (float) Math.floor(
                        coordinate
                );

        /*
         * Maintain the original orientation behavior.
         */
        if (side == 0) {

            if (rayDirX > 0.0f) {

                coordinate =
                        1.0f -
                                coordinate;
            }

        } else {

            if (rayDirY < 0.0f) {

                coordinate =
                        1.0f -
                                coordinate;
            }
        }

        if (coordinate < 0.0f) {
            coordinate = 0.0f;
        }

        if (coordinate >= 1.0f) {
            coordinate = 0.999999f;
        }

        return coordinate;
    }

    /*
     * ---------------------------------------------------------
     * PROJECT WORLD Z
     * ---------------------------------------------------------
     *
     * distance is ALWAYS camera-forward distance here.
     */

    private int project(
            Camera3D camera,
            float worldZ,
            float distance) {

        if (distance <=
                MIN_DISTANCE) {

            distance =
                    MIN_DISTANCE;
        }

        float horizon =
                getHorizon(camera);

        return (int) (
                horizon
                        -
                        (
                                worldZ -
                                        camera.position.z
                        )
                                *
                                focalLength
                                /
                                distance
        );
    }

    /*
     * ---------------------------------------------------------
     * DEBUG / DIAGNOSTICS
     * ---------------------------------------------------------
     *
     * These methods do not affect rendering.
     */

    /**
     * Returns the number of times the vertical-surface pool
     * was too small.
     *
     * A non-zero value means geometry was silently omitted.
     */
    public int getSurfacePoolOverflows() {

        return surfacePoolOverflows;
    }

    /**
     * Returns the number of times the plane pool was too small.
     *
     * A non-zero value means geometry was silently omitted.
     */
    public int getPlanePoolOverflows() {

        return planePoolOverflows;
    }

    /**
     * Clears pool-overflow diagnostics.
     */
    public void resetDiagnostics() {

        surfacePoolOverflows = 0;
        planePoolOverflows = 0;
    }

    /**
     * Returns the configured render distance.
     */
    public float getRenderDistance() {

        return renderDistance;
    }

    /**
     * Returns the number of reusable vertical-surface objects.
     */
    public int getSurfacePoolSize() {

        return surfacePool.length;
    }

    /**
     * Returns the number of reusable plane objects.
     */
    public int getPlanePoolSize() {

        return planePool.length;
    }
}
