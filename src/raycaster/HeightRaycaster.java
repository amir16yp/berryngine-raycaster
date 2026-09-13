package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.Arrays;

public final class HeightRaycaster {

    private final TileMap map;

    private final int width;
    private final int height;

    private final float focalLength;

    /*
     * ---------------------------------------------------------
     * RENDER DISTANCE
     * ---------------------------------------------------------
     *
     * Measured in world/tile units.
     *
     * Rays stop once they reach this distance.
     */
    private final float renderDistance;

    /*
     * ---------------------------------------------------------
     * REUSABLE RENDER BUFFERS
     * ---------------------------------------------------------
     *
     * These are allocated once in the constructor.
     *
     * Nothing is allocated inside render().
     */
    private final Surface[] surfacePool;
    private final PlaneSurface[] planePool;

    private final float[] depth;

    /*
     * ---------------------------------------------------------
     * VERTICAL SURFACE
     * ---------------------------------------------------------
     */
    private static final class Surface {

        float distance;
        float perpendicularDistance;

        float low;
        float high;

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

        float entryDistance;
        float exitDistance;

        float height;

        Tile tile;

        boolean ceiling;
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
         * POOL SIZE
         * -----------------------------------------------------
         *
         * A ray can cross approximately:
         *
         *     renderDistance * sqrt(2)
         *
         * tiles in the worst case.
         *
         * Add some extra room for boundary cases.
         */
        int maxTiles =
                (int) Math.ceil(
                        renderDistance *
                                1.5f
                ) + 8;

        /*
         * One vertical transition surface per crossed tile
         * is enough for normal map geometry.
         */
        int maxSurfaces =
                Math.max(
                        16,
                        maxTiles
                );

        /*
         * Each walkable tile can produce:
         *
         *     floor
         *     ceiling
         *
         * so allow approximately two planes per tile.
         */
        int maxPlanes =
                Math.max(
                        32,
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
     *
     * Optional convenience constructor.
     *
     * If you were previously constructing the renderer with:
     *
     *     new HeightRaycaster(map, width, height, fov)
     *
     * this keeps that code working.
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

        float cameraX =
                2.0f *
                        (screenX + 0.5f) /
                        (float) width
                        - 1.0f;

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
         * RESET COUNTERS
         * -----------------------------------------------------
         *
         * No allocations.
         *
         * The objects in the pools are simply overwritten.
         */
        int surfaceCount = 0;
        int planeCount = 0;

        /*
         * -----------------------------------------------------
         * RESET DEPTH
         * -----------------------------------------------------
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
                0.000001f) {

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
                0.000001f) {

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
         * STEP
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
        Tile previous = null;

        if (map.inBounds(
                mapX,
                mapY)) {

            previous =
                    map.get(
                            mapX,
                            mapY
                    );
        }

        /*
         * Camera cannot start outside the map.
         */
        if (previous == null &&
                !map.inBounds(
                        mapX,
                        mapY)) {

            return;
        }

        /*
         * Distance where current tile began.
         */
        float previousEntryDistance =
                0.0f;

        /*
         * -----------------------------------------------------
         * SAFETY LIMIT
         * -----------------------------------------------------
         */
        int maxSteps =
                (int) Math.ceil(
                        renderDistance * 2.0f
                ) + 16;

        /*
         * -----------------------------------------------------
         * WALK RAY
         * -----------------------------------------------------
         */
        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            final float rayDistance;
            final int side;

            /*
             * -------------------------------------------------
             * RENDER DISTANCE CHECK
             * -------------------------------------------------
             */
            float nextBoundary =
                    Math.min(
                            sideDistX,
                            sideDistY
                    );

            if (nextBoundary >
                    renderDistance) {

                /*
                 * The current tile still extends up to the
                 * render-distance boundary.
                 *
                 * Render the visible portion of it.
                 */
                if (previous != null) {

                    planeCount =
                            addTilePlanes(
                                    previous,
                                    previousEntryDistance,
                                    renderDistance,
                                    planeCount
                            );
                }

                break;
            }

            /*
             * -------------------------------------------------
             * FIND NEXT GRID BOUNDARY
             * -------------------------------------------------
             */
            if (sideDistX < sideDistY) {

                rayDistance =
                        sideDistX;

                sideDistX +=
                        deltaDistX;

                mapX +=
                        stepX;

                side = 0;

            } else {

                rayDistance =
                        sideDistY;

                sideDistY +=
                        deltaDistY;

                mapY +=
                        stepY;

                side = 1;
            }

            /*
             * Safety check.
             */
            if (rayDistance >
                    renderDistance) {

                if (previous != null) {

                    planeCount =
                            addTilePlanes(
                                    previous,
                                    previousEntryDistance,
                                    renderDistance,
                                    planeCount
                            );
                }

                break;
            }

            /*
             * -------------------------------------------------
             * OUTSIDE MAP
             * -------------------------------------------------
             */
            if (!map.inBounds(
                    mapX,
                    mapY)) {

                if (previous != null) {

                    planeCount =
                            addTilePlanes(
                                    previous,
                                    previousEntryDistance,
                                    rayDistance,
                                    planeCount
                            );
                }

                break;
            }

            Tile current =
                    map.get(
                            mapX,
                            mapY
                    );

            /*
             * -------------------------------------------------
             * FINISH PREVIOUS TILE
             * -------------------------------------------------
             */
            if (previous != null) {

                planeCount =
                        addTilePlanes(
                                previous,
                                previousEntryDistance,
                                rayDistance,
                                planeCount
                        );
            }

            /*
             * -------------------------------------------------
             * PERPENDICULAR DISTANCE
             * -------------------------------------------------
             */
            float perpendicularDistance =
                    rayDistance *
                            Mathf.cos(
                                    rayAngle -
                                            camera.rotation.y
                            );

            if (perpendicularDistance <
                    0.0001f) {

                perpendicularDistance =
                        0.0001f;
            }

            /*
             * -------------------------------------------------
             * HEIGHT TRANSITION
             * -------------------------------------------------
             */
            if (previous != null &&
                    current != null &&
                    Math.abs(
                            previous.floor -
                                    current.floor
                    ) > 0.000001f) {

                float low =
                        Math.min(
                                previous.floor,
                                current.floor
                        );

                float high =
                        Math.max(
                                previous.floor,
                                current.floor
                        );

                Tile wallTile;

                if (previous.floor >
                        current.floor) {

                    wallTile =
                            previous;

                } else {

                    wallTile =
                            current;
                }

                float textureX =
                        calculateTextureX(
                                camera,
                                rayDirX,
                                rayDirY,
                                rayDistance,
                                side
                        );

                if (surfaceCount <
                        surfacePool.length) {

                    Surface surface =
                            surfacePool[
                                    surfaceCount++
                                    ];

                    surface.set(
                            rayDistance,
                            perpendicularDistance,
                            low,
                            high,
                            textureX,
                            wallTile
                    );
                }
            }

            /*
             * -------------------------------------------------
             * SOLID TILE
             * -------------------------------------------------
             */
            if (current != null &&
                    current.solid) {

                /*
                 * Current tile extends from this boundary until
                 * the next boundary.
                 */
                float exitDistance =
                        Math.min(
                                sideDistX,
                                sideDistY
                        );

                /*
                 * Clamp to render distance.
                 */
                exitDistance =
                        Math.min(
                                exitDistance,
                                renderDistance
                        );

                /*
                 * -------------------------------------------------
                 * TOP OF SOLID BLOCK
                 * -------------------------------------------------
                 */
                if (current.renderTop) {

                    planeCount =
                            addSolidTopPlane(
                                    current,
                                    rayDistance,
                                    exitDistance,
                                    planeCount
                            );
                }

                /*
                 * -------------------------------------------------
                 * FRONT WALL
                 * -------------------------------------------------
                 */
                float textureX =
                        calculateTextureX(
                                camera,
                                rayDirX,
                                rayDirY,
                                rayDistance,
                                side
                        );

                if (surfaceCount <
                        surfacePool.length) {

                    Surface surface =
                            surfacePool[
                                    surfaceCount++
                                    ];

                    surface.set(
                            rayDistance,
                            perpendicularDistance,
                            current.floor,
                            current.ceiling,
                            textureX,
                            current
                    );
                }

                /*
                 * Solid geometry stops the ray.
                 */
                break;
            }

            /*
             * Continue through walkable tile.
             */
            previous =
                    current;

            previousEntryDistance =
                    rayDistance;
        }

        /*
         * -----------------------------------------------------
         * RENDER WALLS
         * -----------------------------------------------------
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
         * RENDER PLANES
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
         */
        PixelGraphics floorTexture;

        if (tile.floor >
                0.000001f) {

            /*
             * Raised walkable tile.
             *
             * Its visible top uses topTexture.
             */
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

            /*
             * Normal floor.
             */
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
                                0.000001f
                );
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
         * Fallback.
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
         * Top of solid block is at ceiling.
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
     * DRAW PLANE
     * ---------------------------------------------------------
     */
    private void drawPlaneDepthTested(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
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
         * HEIGHT
         * -----------------------------------------------------
         */
        float relativeHeight =
                plane.height -
                        camera.position.z;

        if (Math.abs(
                relativeHeight
        ) < 0.000001f) {

            return;
        }

        float horizon =
                getHorizon(camera);

        /*
         * -----------------------------------------------------
         * SCREEN HALF
         * -----------------------------------------------------
         */
        int startY;
        int endY;

        if (relativeHeight > 0.0f) {

            /*
             * Above camera.
             */
            startY = 0;

            endY =
                    (int) Math.ceil(
                            horizon
                    ) - 1;

        } else {

            /*
             * Below camera.
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
            ) < 0.000001f) {

                continue;
            }

            /*
             * Perspective.
             */
            float distance =
                    -relativeHeight *
                            focalLength /
                            screenY;

            if (distance <=
                    0.0001f) {

                continue;
            }

            /*
             * Explicit render distance.
             */
            if (distance >
                    renderDistance) {

                continue;
            }

            /*
             * Only inside this tile.
             */
            if (distance <
                    plane.entryDistance -
                            0.0001f) {

                continue;
            }

            if (distance >
                    plane.exitDistance +
                            0.0001f) {

                continue;
            }

            /*
             * Depth test.
             */
            if (distance >=
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
                                    distance;

            float worldY =
                    camera.position.y +
                            rayDirY *
                                    distance;

            /*
             * -------------------------------------------------
             * LOCAL TILE COORDINATES
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
             * Update depth.
             */
            depth[y] =
                    distance;
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

        float distance =
                surface.perpendicularDistance;

        if (distance <=
                0.0001f) {

            distance =
                    0.0001f;
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
                        distance
                );

        int bottom =
                project(
                        camera,
                        surface.low,
                        distance
                );

        if (top > bottom) {

            int temp = top;

            top = bottom;
            bottom = temp;
        }

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
         * DRAW WALL
         * -----------------------------------------------------
         */
        for (int y = clippedTop;
             y <= clippedBottom;
             y++) {

            /*
             * Depth.
             */
            if (distance >=
                    depth[y]) {

                continue;
            }

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

            pg.setPixel(
                    screenX,
                    y,
                    texture.getPixel(
                            texX,
                            texY
                    )
            );

            depth[y] =
                    distance;
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

        if (side == 0) {

            coordinate =
                    camera.position.y
                            +
                            rayDistance *
                                    rayDirY;

        } else {

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
     */
    private int project(
            Camera3D camera,
            float worldZ,
            float distance) {

        if (distance <=
                0.0001f) {

            distance =
                    0.0001f;
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
}
