package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HeightRaycaster {

    private final TileMap map;

    private final int width;
    private final int height;

    private final float focalLength;

    /*
     * ---------------------------------------------------------
     * VERTICAL SURFACE
     * ---------------------------------------------------------
     */
    private static final class Surface {

        final float distance;
        final float perpendicularDistance;

        final float low;
        final float high;

        final float textureX;

        final Tile tile;

        Surface(
                float distance,
                float perpendicularDistance,
                float low,
                float high,
                float textureX,
                Tile tile) {

            this.distance = distance;
            this.perpendicularDistance =
                    perpendicularDistance;

            this.low = low;
            this.high = high;

            this.textureX = textureX;

            this.tile = tile;
        }
    }

    /*
     * ---------------------------------------------------------
     * HORIZONTAL SURFACE
     * ---------------------------------------------------------
     */
    private static final class PlaneSurface {

        final float entryDistance;
        final float exitDistance;

        final float height;

        final Tile tile;

        final boolean ceiling;
        final boolean top;

        PlaneSurface(
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

    public HeightRaycaster(
            TileMap map,
            int width,
            int height,
            float fov) {

        this.map = map;

        this.width = width;
        this.height = height;

        this.focalLength =
                (width * 0.5f) /
                        Mathf.tan(fov * 0.5f);
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
         * DELTA DISTANCE
         * -----------------------------------------------------
         */
        final float deltaDistX;
        final float deltaDistY;

        if (Math.abs(rayDirX) < 0.000001f) {

            deltaDistX =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistX =
                    Math.abs(
                            1.0f / rayDirX
                    );
        }

        if (Math.abs(rayDirY) < 0.000001f) {

            deltaDistY =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistY =
                    Math.abs(
                            1.0f / rayDirY
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
         * SURFACE LISTS
         * -----------------------------------------------------
         */
        List<Surface> surfaces =
                new ArrayList<>();

        List<PlaneSurface> planes =
                new ArrayList<>();

        /*
         * -----------------------------------------------------
         * CURRENT TILE
         * -----------------------------------------------------
         */
        Tile previous = null;

        if (map.inBounds(mapX, mapY)) {

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
                !map.inBounds(mapX, mapY)) {

            return;
        }

        /*
         * Distance where current tile began.
         */
        float previousEntryDistance =
                0.0f;

        /*
         * Safety limit.
         */
        int maxSteps =
                Math.max(
                        width,
                        height
                ) * 16 + 256;

        /*
         * -----------------------------------------------------
         * WALK THE RAY
         * -----------------------------------------------------
         */
        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            final float rayDistance;
            final int side;

            /*
             * Find next grid boundary.
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
             * -------------------------------------------------
             * OUTSIDE MAP
             * -------------------------------------------------
             */
            if (!map.inBounds(mapX, mapY)) {

                if (previous != null) {

                    addTilePlanes(
                            planes,
                            previous,
                            previousEntryDistance,
                            rayDistance
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

                addTilePlanes(
                        planes,
                        previous,
                        previousEntryDistance,
                        rayDistance
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
             *
             * When two walkable tiles have different floor
             * heights, create a vertical step wall.
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

                surfaces.add(
                        new Surface(
                                rayDistance,
                                perpendicularDistance,
                                low,
                                high,
                                textureX,
                                wallTile
                        )
                );
            }

            /*
             * -------------------------------------------------
             * SOLID TILE
             * -------------------------------------------------
             */
            if (current != null &&
                    current.solid) {

                /*
                 * Current tile extends from this boundary
                 * until the next grid boundary.
                 */
                float exitDistance =
                        Math.min(
                                sideDistX,
                                sideDistY
                        );

                /*
                 * -------------------------------------------------
                 * TOP OF SOLID BLOCK
                 * -------------------------------------------------
                 *
                 * IMPORTANT:
                 *
                 * The top is at tile.ceiling.
                 *
                 * It uses topTexture.
                 */
                if (current.renderTop) {

                    addSolidTopPlane(
                            planes,
                            current,
                            rayDistance,
                            exitDistance
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

                surfaces.add(
                        new Surface(
                                rayDistance,
                                perpendicularDistance,
                                current.floor,
                                current.ceiling,
                                textureX,
                                current
                        )
                );

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
         * RENDER EVERYTHING WITH PER-PIXEL DEPTH
         * -----------------------------------------------------
         *
         * We don't simply draw all planes and then all walls.
         *
         * Instead, create a small depth buffer for this column.
         */
        float[] depth =
                new float[height];

        for (int y = 0;
             y < height;
             y++) {

            depth[y] =
                    Float.POSITIVE_INFINITY;
        }

        /*
         * -----------------------------------------------------
         * WALLS
         * -----------------------------------------------------
         */
        for (Surface surface : surfaces) {

            drawSurfaceDepthTested(
                    pg,
                    camera,
                    screenX,
                    surface,
                    depth
            );
        }

        /*
         * -----------------------------------------------------
         * PLANES
         * -----------------------------------------------------
         */
        for (PlaneSurface plane : planes) {

            drawPlaneDepthTested(
                    pg,
                    camera,
                    screenX,
                    rayDirX,
                    rayDirY,
                    plane,
                    depth
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * ADD NORMAL TILE PLANES
     * ---------------------------------------------------------
     */
    private void addTilePlanes(
            List<PlaneSurface> planes,
            Tile tile,
            float entryDistance,
            float exitDistance) {

        if (tile == null) {
            return;
        }

        if (exitDistance <= entryDistance) {
            return;
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
                    getTopTextureSafe(tile);

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

            planes.add(
                    new PlaneSurface(
                            entryDistance,
                            exitDistance,
                            tile.floor,
                            tile,
                            false,
                            tile.floor >
                                    0.000001f
                    )
            );
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

            planes.add(
                    new PlaneSurface(
                            entryDistance,
                            exitDistance,
                            tile.ceiling,
                            tile,
                            true,
                            false
                    )
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * ADD SOLID TOP
     * ---------------------------------------------------------
     */
    private void addSolidTopPlane(
            List<PlaneSurface> planes,
            Tile tile,
            float entryDistance,
            float exitDistance) {

        if (tile == null) {
            return;
        }

        if (exitDistance <= entryDistance) {
            return;
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
            return;
        }

        /*
         * Top of a solid block is at ceiling.
         */
        planes.add(
                new PlaneSurface(
                        entryDistance,
                        exitDistance,
                        tile.ceiling,
                        tile,
                        false,
                        true
                )
        );
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

            /*
             * THIS IS THE BLOCK TOP.
             */
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

        if (Math.abs(relativeHeight) <
                0.000001f) {

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

            if (Math.abs(screenY) <
                    0.000001f) {

                continue;
            }

            /*
             * Perspective.
             */
            float distance =
                    -relativeHeight *
                            focalLength /
                            screenY;

            if (distance <= 0.0001f) {
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
                    Math.max(
                            0.0f,
                            Math.min(
                                    0.999999f,
                                    textureX
                            )
                    );

            textureY =
                    Math.max(
                            0.0f,
                            Math.min(
                                    0.999999f,
                                    textureY
                            )
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
                    Math.max(
                            0,
                            Math.min(
                                    texture.width - 1,
                                    texX
                            )
                    );

            texY =
                    Math.max(
                            0,
                            Math.min(
                                    texture.height - 1,
                                    texY
                            )
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

        if (distance <= 0.0001f) {
            distance = 0.0001f;
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
                Math.max(
                        0,
                        Math.min(
                                texture.width - 1,
                                texX
                        )
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
             *
             * Use perpendicular distance so walls don't
             * suffer fisheye distortion.
             */
            float wallDistance =
                    distance;

            if (wallDistance >=
                    depth[y]) {

                continue;
            }

            float t =
                    (y - top) /
                            projectedHeight;

            t =
                    Math.max(
                            0.0f,
                            Math.min(
                                    0.999999f,
                                    t
                            )
                    );

            int texY =
                    (int) (
                            t *
                                    texture.height
                    );

            texY =
                    Math.max(
                            0,
                            Math.min(
                                    texture.height - 1,
                                    texY
                            )
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
                    wallDistance;
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

        if (distance <= 0.0001f) {
            distance = 0.0001f;
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