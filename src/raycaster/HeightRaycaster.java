package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.Arrays;

public final class HeightRaycaster {

    private static final float EPSILON = 0.000001f;
    private static final float MIN_DISTANCE = 0.0001f;

    private final TileMap map;

    private final int width;
    private final int height;

    private final float focalLength;
    private final float renderDistance;

    /*
     * Full framebuffer depth buffer.
     *
     * All depth values are CAMERA-FORWARD depth.
     */
    private final float[] depth;

    private final Surface[] surfacePool;
    private final PlaneSurface[] planePool;

    private int surfacePoolOverflows;
    private int planePoolOverflows;

    /*
     * =========================================================
     * RAYCAST SURFACES
     * =========================================================
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
     * =========================================================
     * ENTITY RASTERIZATION
     * =========================================================
     */

    private static final class ScreenVertex {

        float x;
        float y;

        /*
         * Camera-forward depth.
         */
        float z;

        void set(
                float x,
                float y,
                float z) {

            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /*
     * Reused vertices so entity rendering does not allocate
     * per triangle.
     */
    private final ScreenVertex entityV0 =
            new ScreenVertex();

    private final ScreenVertex entityV1 =
            new ScreenVertex();

    private final ScreenVertex entityV2 =
            new ScreenVertex();

    private final ScreenVertex entityV3 =
            new ScreenVertex();

    /*
     * =========================================================
     * CONSTRUCTOR
     * =========================================================
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

        this.map =
                map;

        this.width =
                width;

        this.height =
                height;

        this.renderDistance =
                Math.max(
                        MIN_DISTANCE,
                        renderDistance
                );

        this.focalLength =
                (width * 0.5f) /
                        Mathf.tan(
                                fov * 0.5f
                        );

        depth =
                new float[
                        width * height
                        ];

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

        int maxPlanes =
                Math.max(
                        64,
                        maxTiles * 2
                );

        surfacePool =
                new Surface[
                        maxSurfaces
                        ];

        for (int i = 0;
             i < surfacePool.length;
             i++) {

            surfacePool[i] =
                    new Surface();
        }

        planePool =
                new PlaneSurface[
                        maxPlanes
                        ];

        for (int i = 0;
             i < planePool.length;
             i++) {

            planePool[i] =
                    new PlaneSurface();
        }
    }

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
     * =========================================================
     * RENDER
     * =========================================================
     */

    public void render(
            PixelGraphics pg,
            Camera3D camera) {

        render(
                pg,
                camera,
                null
        );
    }

    public void render(
            PixelGraphics pg,
            Camera3D camera,
            Iterable<Entity3D> entities) {

        if (pg == null ||
                camera == null) {

            return;
        }

        Arrays.fill(
                depth,
                Float.POSITIVE_INFINITY
        );

        /*
         * World first.
         */
        for (int screenX = 0;
             screenX < width;
             screenX++) {

            castColumn(
                    pg,
                    camera,
                    screenX
            );
        }

        /*
         * Extruded entities afterward.
         *
         * They use the same Z-buffer as the world.
         */
        if (entities != null) {

            for (Entity3D entity : entities) {

                drawEntity(
                        pg,
                        camera,
                        entity
                );
            }
        }
    }

    /*
     * =========================================================
     * COLUMN RAY
     * =========================================================
     */

    private void castColumn(
            PixelGraphics pg,
            Camera3D camera,
            int screenX) {

        float cameraX =
                2.0f *
                        (screenX + 0.5f) /
                        (float) width
                        -
                        1.0f;

        float rayAngle =
                camera.rotation.y
                        +
                        (float) Math.atan(
                                cameraX *
                                        Mathf.tan(
                                                camera.fov *
                                                        0.5f
                                        )
                        );

        float rayDirX =
                Mathf.cos(
                        rayAngle
                );

        float rayDirY =
                Mathf.sin(
                        rayAngle
                );

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
     * =========================================================
     * DDA
     * =========================================================
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
                (int) Math.floor(
                        rayX
                );

        int mapY =
                (int) Math.floor(
                        rayY
                );

        int surfaceCount =
                0;

        int planeCount =
                0;

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

        final int stepX;
        final int stepY;

        float sideDistX;
        float sideDistY;

        if (rayDirX <
                0.0f) {

            stepX =
                    -1;

            sideDistX =
                    (rayX - mapX) *
                            deltaDistX;

        } else {

            stepX =
                    1;

            sideDistX =
                    (mapX + 1.0f - rayX) *
                            deltaDistX;
        }

        if (rayDirY <
                0.0f) {

            stepY =
                    -1;

            sideDistY =
                    (rayY - mapY) *
                            deltaDistY;

        } else {

            stepY =
                    1;

            sideDistY =
                    (mapY + 1.0f - rayY) *
                            deltaDistY;
        }

        if (!map.inBounds(
                mapX,
                mapY
        )) {

            return;
        }

        Tile current =
                map.get(
                        mapX,
                        mapY
                );

        float currentEntryDistance =
                0.0f;

        int maxSteps =
                (int) Math.ceil(
                        renderDistance *
                                1.5f
                ) + 32;

        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            float nextBoundary =
                    Math.min(
                            sideDistX,
                            sideDistY
                    );

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

            final float exitDistance =
                    nextBoundary;

            if (current != null) {

                planeCount =
                        addTilePlanes(
                                current,
                                currentEntryDistance,
                                exitDistance,
                                planeCount
                        );
            }

            final int side;
            final float boundaryDistance;

            if (sideDistX <
                    sideDistY) {

                boundaryDistance =
                        sideDistX;

                sideDistX +=
                        deltaDistX;

                mapX +=
                        stepX;

                side =
                        0;

            } else {

                boundaryDistance =
                        sideDistY;

                sideDistY +=
                        deltaDistY;

                mapY +=
                        stepY;

                side =
                        1;
            }

            if (boundaryDistance >
                    renderDistance) {

                break;
            }

            if (!map.inBounds(
                    mapX,
                    mapY
            )) {

                break;
            }

            Tile next =
                    map.get(
                            mapX,
                            mapY
                    );

            /*
             * Floor transition.
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

                    Tile wallTile =
                            oldFloor >
                                    newFloor
                                    ?
                                    current
                                    :
                                    next;

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
             * Solid tile.
             */
            if (next != null &&
                    next.solid) {

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

                if (next.renderTop) {

                    planeCount =
                            addSolidTopPlane(
                                    next,
                                    boundaryDistance,
                                    solidExitDistance,
                                    planeCount
                            );
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
                            next.floor,
                            next.ceiling,
                            textureX,
                            next
                    );

                } else {

                    surfacePoolOverflows++;
                }

                break;
            }

            current =
                    next;

            currentEntryDistance =
                    boundaryDistance;
        }

        for (int i = 0;
             i < surfaceCount;
             i++) {

            drawSurfaceDepthTested(
                    pg,
                    camera,
                    screenX,
                    surfacePool[i]
            );
        }

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
                    planePool[i]
            );
        }
    }

    /*
     * =========================================================
     * TILE PLANES
     * =========================================================
     */

    private int addTilePlanes(
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {

        if (tile == null ||
                exitDistance <=
                        entryDistance) {

            return planeCount;
        }

        PixelGraphics floorTexture;

        if (tile.floor >
                EPSILON) {

            floorTexture =
                    getTopTextureSafe(
                            tile
                    );

            if (!validTexture(
                    floorTexture
            )) {

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
                floorTexture
        )) {

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

        PixelGraphics ceilingTexture =
                getCeilingTextureSafe(
                        tile
                );

        if (validTexture(
                ceilingTexture
        )) {

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

    private int addSolidTopPlane(
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {

        if (tile == null ||
                exitDistance <=
                        entryDistance) {

            return planeCount;
        }

        PixelGraphics texture =
                getTopTextureSafe(
                        tile
                );

        if (!validTexture(
                texture
        )) {

            texture =
                    getFloorTextureSafe(
                            tile
                    );
        }

        if (!validTexture(
                texture
        )) {

            return planeCount;
        }

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
     * =========================================================
     * WORLD RENDERING
     * =========================================================
     */

    private void drawPlaneDepthTested(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float rayAngle,
            PlaneSurface plane) {

        if (plane.tile == null) {

            return;
        }

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

            if (!validTexture(
                    texture
            )) {

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

        if (!validTexture(
                texture
        )) {

            return;
        }

        float relativeHeight =
                plane.height -
                        camera.position.z;

        if (Math.abs(relativeHeight) <
                EPSILON) {

            return;
        }

        float horizon =
                getHorizon(
                        camera
                );

        int startY;
        int endY;

        if (relativeHeight >
                0.0f) {

            startY =
                    0;

            endY =
                    (int) Math.ceil(
                            horizon
                    ) - 1;

        } else {

            startY =
                    (int) Math.floor(
                            horizon
                    );

            endY =
                    height - 1;
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

        if (startY >
                endY) {

            return;
        }

        for (int y = startY;
             y <= endY;
             y++) {

            float screenY =
                    (y + 0.5f) -
                            horizon;

            if (Math.abs(screenY) <
                    EPSILON) {

                continue;
            }

            float rayDistance =
                    -relativeHeight *
                            focalLength /
                            screenY;

            if (rayDistance <=
                    MIN_DISTANCE ||
                    rayDistance >
                            renderDistance) {

                continue;
            }

            if (rayDistance <
                    plane.entryDistance -
                            0.0001f ||
                    rayDistance >
                            plane.exitDistance +
                                    0.0001f) {

                continue;
            }

            float cameraDepth =
                    calculatePerpendicularDistance(
                            rayDistance,
                            rayAngle,
                            camera.rotation.y
                    );

            int index =
                    depthIndex(
                            screenX,
                            y
                    );

            if (cameraDepth >=
                    depth[index]) {

                continue;
            }

            float worldX =
                    camera.position.x +
                            rayDirX *
                                    rayDistance;

            float worldY =
                    camera.position.y +
                            rayDirY *
                                    rayDistance;

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

            if (textureX <
                    0.0f) {

                textureX +=
                        1.0f;
            }

            if (textureY <
                    0.0f) {

                textureY +=
                        1.0f;
            }

            int texX =
                    (int) (
                            Math.clamp(
                                    textureX,
                                    0.0f,
                                    0.999999f
                            )
                                    *
                                    texture.width
                    );

            int texY =
                    (int) (
                            Math.clamp(
                                    textureY,
                                    0.0f,
                                    0.999999f
                            )
                                    *
                                    texture.height
                    );

            pg.setPixel(
                    screenX,
                    y,
                    texture.getPixel(
                            texX,
                            texY
                    )
            );

            depth[index] =
                    cameraDepth;
        }
    }

    private void drawSurfaceDepthTested(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            Surface surface) {

        Tile tile =
                surface.tile;

        if (tile == null ||
                surface.high <=
                        surface.low) {

            return;
        }

        PixelGraphics texture =
                getWallTextureSafe(
                        tile
                );

        if (!validTexture(
                texture
        )) {

            return;
        }

        float cameraDepth =
                Math.max(
                        MIN_DISTANCE,
                        surface.perpendicularDistance
                );

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

        if (top >
                bottom) {

            int temp =
                    top;

            top =
                    bottom;

            bottom =
                    temp;
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
                bottom -
                        top;

        if (projectedHeight <=
                EPSILON) {

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

        for (int y = clippedTop;
             y <= clippedBottom;
             y++) {

            int index =
                    depthIndex(
                            screenX,
                            y
                    );

            if (cameraDepth >=
                    depth[index]) {

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

            depth[index] =
                    cameraDepth;
        }
    }

    /*
     * =========================================================
     * EXTRUDED ENTITY
     * =========================================================
     *
     * Each opaque texture pixel becomes a rectangular prism.
     *
     * We don't actually draw every face of every prism:
     *
     *     front
     *     back
     *
     * are always drawn.
     *
     * Side faces are drawn only when an opaque pixel borders
     * transparency / texture boundary.
     *
     * This produces the extrusion of the alpha silhouette.
     */

    private void drawEntity(
            PixelGraphics pg,
            Camera3D camera,
            Entity3D entity) {

        if (entity == null ||
                entity.pos == null ||
                !validTexture(entity.texture)) {

            return;
        }

        if (entity.width <=
                0.0f ||
                entity.height <=
                        0.0f ||
                entity.depth <
                        0.0f) {

            return;
        }

        PixelGraphics texture =
                entity.texture;

        int texWidth =
                texture.width;

        int texHeight =
                texture.height;

        /*
         * Entity bounds.
         *
         * X = width
         * Y = depth
         * Z = height
         */
        float minX =
                entity.pos.x -
                        entity.width * 0.5f;

        float maxX =
                entity.pos.x +
                        entity.width * 0.5f;

        float minY =
                entity.pos.y -
                        entity.depth * 0.5f;

        float maxY =
                entity.pos.y +
                        entity.depth * 0.5f;

        float minZ =
                entity.pos.z;

        float maxZ =
                entity.pos.z +
                        entity.height;

        /*
         * Quickly reject the entity using its center.
         */
        float centerDX =
                entity.pos.x -
                        camera.position.x;

        float centerDY =
                entity.pos.y -
                        camera.position.y;

        float centerDepth =
                centerDX *
                        Mathf.cos(
                                camera.rotation.y
                        )
                        +
                        centerDY *
                                Mathf.sin(
                                        camera.rotation.y
                                );

        float radius =
                (float) Math.sqrt(
                        entity.width *
                                entity.width
                                +
                                entity.depth *
                                        entity.depth
                ) * 0.5f;

        if (centerDepth + radius <
                MIN_DISTANCE) {

            return;
        }

        if (centerDepth - radius >
                renderDistance) {

            return;
        }

        float texelWidth =
                entity.width /
                        texWidth;

        float texelHeight =
                entity.height /
                        texHeight;

        /*
         * Texture Y goes downward.
         *
         * World Z goes upward.
         */
        for (int ty = 0;
             ty < texHeight;
             ty++) {

            float zTop =
                    maxZ -
                            ty *
                                    texelHeight;

            float zBottom =
                    zTop -
                            texelHeight;

            for (int tx = 0;
                 tx < texWidth;
                 tx++) {

                int color =
                        texture.getPixel(
                                tx,
                                ty
                        );

                if (isTransparent(
                        color
                )) {

                    continue;
                }

                float x0 =
                        minX +
                                tx *
                                        texelWidth;

                float x1 =
                        x0 +
                                texelWidth;

                /*
                 * ---------------------------------------------
                 * FRONT
                 * ---------------------------------------------
                 *
                 * Y = minY
                 */

                drawWorldQuad(
                        pg,
                        camera,

                        x0, minY, zBottom,
                        x1, minY, zBottom,
                        x1, minY, zTop,
                        x0, minY, zTop,

                        color
                );

                /*
                 * ---------------------------------------------
                 * BACK
                 * ---------------------------------------------
                 *
                 * Y = maxY
                 */

                if (entity.depth >
                        EPSILON) {

                    drawWorldQuad(
                            pg,
                            camera,

                            x1, maxY, zBottom,
                            x0, maxY, zBottom,
                            x0, maxY, zTop,
                            x1, maxY, zTop,

                            color
                    );
                }

                if (entity.depth <=
                        EPSILON) {

                    continue;
                }

                /*
                 * ---------------------------------------------
                 * LEFT SILHOUETTE
                 * ---------------------------------------------
                 */

                if (!opaque(
                        texture,
                        tx - 1,
                        ty
                )) {

                    drawWorldQuad(
                            pg,
                            camera,

                            x0, maxY, zBottom,
                            x0, minY, zBottom,
                            x0, minY, zTop,
                            x0, maxY, zTop,

                            color
                    );
                }

                /*
                 * ---------------------------------------------
                 * RIGHT SILHOUETTE
                 * ---------------------------------------------
                 */

                if (!opaque(
                        texture,
                        tx + 1,
                        ty
                )) {

                    drawWorldQuad(
                            pg,
                            camera,

                            x1, minY, zBottom,
                            x1, maxY, zBottom,
                            x1, maxY, zTop,
                            x1, minY, zTop,

                            color
                    );
                }

                /*
                 * ---------------------------------------------
                 * TOP SILHOUETTE
                 * ---------------------------------------------
                 */

                if (!opaque(
                        texture,
                        tx,
                        ty - 1
                )) {

                    drawWorldQuad(
                            pg,
                            camera,

                            x0, minY, zTop,
                            x1, minY, zTop,
                            x1, maxY, zTop,
                            x0, maxY, zTop,

                            color
                    );
                }

                /*
                 * ---------------------------------------------
                 * BOTTOM SILHOUETTE
                 * ---------------------------------------------
                 */

                if (!opaque(
                        texture,
                        tx,
                        ty + 1
                )) {

                    drawWorldQuad(
                            pg,
                            camera,

                            x0, maxY, zBottom,
                            x1, maxY, zBottom,
                            x1, minY, zBottom,
                            x0, minY, zBottom,

                            color
                    );
                }
            }
        }
    }

    /*
     * =========================================================
     * ENTITY ALPHA
     * =========================================================
     */

    private boolean opaque(
            PixelGraphics texture,
            int x,
            int y) {

        if (x < 0 ||
                y < 0 ||
                x >= texture.width ||
                y >= texture.height) {

            return false;
        }

        return !isTransparent(
                texture.getPixel(
                        x,
                        y
                )
        );
    }

    private boolean isTransparent(
            int color) {

        /*
         * Assumes ARGB:
         *
         * 0xAARRGGBB
         */
        int alpha =
                (color >>> 24) &
                        0xFF;

        return alpha ==
                0;
    }

    /*
     * =========================================================
     * WORLD QUAD
     * =========================================================
     */

    private void drawWorldQuad(
            PixelGraphics pg,
            Camera3D camera,

            float x0,
            float y0,
            float z0,

            float x1,
            float y1,
            float z1,

            float x2,
            float y2,
            float z2,

            float x3,
            float y3,
            float z3,

            int color) {

        boolean p0 =
                projectWorldVertex(
                        camera,
                        x0,
                        y0,
                        z0,
                        entityV0
                );

        boolean p1 =
                projectWorldVertex(
                        camera,
                        x1,
                        y1,
                        z1,
                        entityV1
                );

        boolean p2 =
                projectWorldVertex(
                        camera,
                        x2,
                        y2,
                        z2,
                        entityV2
                );

        boolean p3 =
                projectWorldVertex(
                        camera,
                        x3,
                        y3,
                        z3,
                        entityV3
                );

        /*
         * This intentionally performs simple near-plane rejection.
         *
         * If the camera physically enters an entity you may see
         * clipping, but normal external viewing is correct.
         */
        if (!p0 ||
                !p1 ||
                !p2 ||
                !p3) {

            return;
        }

        /*
         * Two triangles.
         */
        drawTriangle(
                pg,
                entityV0,
                entityV1,
                entityV2,
                color
        );

        drawTriangle(
                pg,
                entityV0,
                entityV2,
                entityV3,
                color
        );
    }

    /*
     * =========================================================
     * PROJECT WORLD VERTEX
     * =========================================================
     */

    private boolean projectWorldVertex(
            Camera3D camera,
            float worldX,
            float worldY,
            float worldZ,
            ScreenVertex out) {

        float dx =
                worldX -
                        camera.position.x;

        float dy =
                worldY -
                        camera.position.y;

        float cameraCos =
                Mathf.cos(
                        camera.rotation.y
                );

        float cameraSin =
                Mathf.sin(
                        camera.rotation.y
                );

        /*
         * Camera-forward component.
         */
        float cameraDepth =
                dx *
                        cameraCos
                        +
                        dy *
                                cameraSin;

        if (cameraDepth <=
                MIN_DISTANCE ||
                cameraDepth >
                        renderDistance) {

            return false;
        }

        /*
         * Camera-right component.
         */
        float cameraSide =
                -dx *
                        cameraSin
                        +
                        dy *
                                cameraCos;

        float screenX =
                width *
                        0.5f
                        +
                        cameraSide *
                                focalLength /
                                cameraDepth;

        float screenY =
                getHorizon(
                        camera
                )
                        -
                        (
                                worldZ -
                                        camera.position.z
                        )
                                *
                                focalLength
                                /
                                cameraDepth;

        out.set(
                screenX,
                screenY,
                cameraDepth
        );

        return true;
    }

    /*
     * =========================================================
     * SOFTWARE TRIANGLE RASTERIZER
     * =========================================================
     */

    private void drawTriangle(
            PixelGraphics pg,
            ScreenVertex a,
            ScreenVertex b,
            ScreenVertex c,
            int color) {

        float area =
                edge(
                        a.x,
                        a.y,
                        b.x,
                        b.y,
                        c.x,
                        c.y
                );

        if (Math.abs(area) <
                EPSILON) {

            return;
        }

        float minXF =
                Math.min(
                        a.x,
                        Math.min(
                                b.x,
                                c.x
                        )
                );

        float maxXF =
                Math.max(
                        a.x,
                        Math.max(
                                b.x,
                                c.x
                        )
                );

        float minYF =
                Math.min(
                        a.y,
                        Math.min(
                                b.y,
                                c.y
                        )
                );

        float maxYF =
                Math.max(
                        a.y,
                        Math.max(
                                b.y,
                                c.y
                        )
                );

        int minX =
                Math.max(
                        0,
                        (int) Math.floor(
                                minXF
                        )
                );

        int maxX =
                Math.min(
                        width - 1,
                        (int) Math.ceil(
                                maxXF
                        )
                );

        int minY =
                Math.max(
                        0,
                        (int) Math.floor(
                                minYF
                        )
                );

        int maxY =
                Math.min(
                        height - 1,
                        (int) Math.ceil(
                                maxYF
                        )
                );

        if (minX >
                maxX ||
                minY >
                        maxY) {

            return;
        }

        float inverseArea =
                1.0f /
                        area;

        /*
         * Perspective-correct depth interpolation.
         *
         * Camera-space forward depth follows 1/Z in screen
         * coordinates.
         */
        float invZA =
                1.0f /
                        a.z;

        float invZB =
                1.0f /
                        b.z;

        float invZC =
                1.0f /
                        c.z;

        for (int y = minY;
             y <= maxY;
             y++) {

            float py =
                    y +
                            0.5f;

            for (int x = minX;
                 x <= maxX;
                 x++) {

                float px =
                        x +
                                0.5f;

                float e0 =
                        edge(
                                b.x,
                                b.y,
                                c.x,
                                c.y,
                                px,
                                py
                        );

                float e1 =
                        edge(
                                c.x,
                                c.y,
                                a.x,
                                a.y,
                                px,
                                py
                        );

                float e2 =
                        edge(
                                a.x,
                                a.y,
                                b.x,
                                b.y,
                                px,
                                py
                        );

                /*
                 * Accept either winding.
                 */
                boolean inside;

                if (area >
                        0.0f) {

                    inside =
                            e0 >=
                                    -EPSILON
                                    &&
                                    e1 >=
                                            -EPSILON
                                    &&
                                    e2 >=
                                            -EPSILON;

                } else {

                    inside =
                            e0 <=
                                    EPSILON
                                    &&
                                    e1 <=
                                            EPSILON
                                    &&
                                    e2 <=
                                            EPSILON;
                }

                if (!inside) {

                    continue;
                }

                float w0 =
                        e0 *
                                inverseArea;

                float w1 =
                        e1 *
                                inverseArea;

                float w2 =
                        e2 *
                                inverseArea;

                float invZ =
                        w0 *
                                invZA
                                +
                                w1 *
                                        invZB
                                +
                                w2 *
                                        invZC;

                if (invZ <=
                        EPSILON) {

                    continue;
                }

                float pixelDepth =
                        1.0f /
                                invZ;

                if (pixelDepth <=
                        MIN_DISTANCE ||
                        pixelDepth >
                                renderDistance) {

                    continue;
                }

                int index =
                        depthIndex(
                                x,
                                y
                        );

                if (pixelDepth >=
                        depth[index]) {

                    continue;
                }

                pg.setPixel(
                        x,
                        y,
                        color
                );

                depth[index] =
                        pixelDepth;
            }
        }
    }

    private float edge(
            float ax,
            float ay,
            float bx,
            float by,
            float px,
            float py) {

        return (
                px -
                        ax
        )
                *
                (
                        by -
                                ay
                )
                -
                (
                        py -
                                ay
                )
                        *
                        (
                                bx -
                                        ax
                        );
    }

    /*
     * =========================================================
     * TEXTURES
     * =========================================================
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
     * =========================================================
     * CAMERA / PROJECTION
     * =========================================================
     */

    private float getHorizon(
            Camera3D camera) {

        return height *
                0.5f
                +
                camera.rotation.x *
                        focalLength;
    }

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

        return Math.max(
                MIN_DISTANCE,
                distance
        );
    }

    private int project(
            Camera3D camera,
            float worldZ,
            float distance) {

        distance =
                Math.max(
                        MIN_DISTANCE,
                        distance
                );

        return (int) (
                getHorizon(
                        camera
                )
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
     * =========================================================
     * WALL TEXTURE X
     * =========================================================
     */

    private float calculateTextureX(
            Camera3D camera,
            float rayDirX,
            float rayDirY,
            float rayDistance,
            int side) {

        float coordinate;

        if (side ==
                0) {

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

        if (side ==
                0) {

            if (rayDirX >
                    0.0f) {

                coordinate =
                        1.0f -
                                coordinate;
            }

        } else {

            if (rayDirY <
                    0.0f) {

                coordinate =
                        1.0f -
                                coordinate;
            }
        }

        return Math.clamp(
                coordinate,
                0.0f,
                0.999999f
        );
    }

    /*
     * =========================================================
     * DEPTH
     * =========================================================
     */

    private int depthIndex(
            int x,
            int y) {

        return y *
                width
                +
                x;
    }

    public float getDepth(
            int x,
            int y) {

        if (x < 0 ||
                x >= width ||
                y < 0 ||
                y >= height) {

            return Float.POSITIVE_INFINITY;
        }

        return depth[
                depthIndex(
                        x,
                        y
                )
                ];
    }

    /*
     * =========================================================
     * DIAGNOSTICS
     * =========================================================
     */

    public int getSurfacePoolOverflows() {

        return surfacePoolOverflows;
    }

    public int getPlanePoolOverflows() {

        return planePoolOverflows;
    }

    public void resetDiagnostics() {

        surfacePoolOverflows =
                0;

        planePoolOverflows =
                0;
    }

    public float getRenderDistance() {

        return renderDistance;
    }

    public int getSurfacePoolSize() {

        return surfacePool.length;
    }

    public int getPlanePoolSize() {

        return planePool.length;
    }
}