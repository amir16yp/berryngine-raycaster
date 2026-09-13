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
     *
     * Represents a wall / height transition.
     */
    private static final class Surface {

        final float rayDistance;
        final float perpendicularDistance;

        final float low;
        final float high;

        final float textureX;

        final Tile tile;

        Surface(
                float rayDistance,
                float perpendicularDistance,
                float low,
                float high,
                float textureX,
                Tile tile) {

            this.rayDistance = rayDistance;
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

        PlaneSurface(
                float entryDistance,
                float exitDistance,
                float height,
                Tile tile,
                boolean ceiling) {

            this.entryDistance = entryDistance;
            this.exitDistance = exitDistance;

            this.height = height;

            this.tile = tile;

            this.ceiling = ceiling;
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

        /*
         * Pixel-center coordinate.
         *
         * -1 = left side of camera
         *  0 = center
         * +1 = right side of camera
         */
        float cameraX =
                2.0f *
                        (screenX + 0.5f) /
                        (float) width
                        - 1.0f;

        /*
         * Calculate the horizontal ray angle.
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

        /*
         * Normalized horizontal ray direction.
         *
         * Pitch is handled during vertical projection.
         */
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

        final float deltaDistX;

        final float deltaDistY;

        if (Math.abs(rayDirX) < 0.000001f) {

            deltaDistX =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistX =
                    Math.abs(1.0f / rayDirX);
        }

        if (Math.abs(rayDirY) < 0.000001f) {

            deltaDistY =
                    Float.POSITIVE_INFINITY;

        } else {

            deltaDistY =
                    Math.abs(1.0f / rayDirY);
        }

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
         * DDA discovers everything near -> far.
         *
         * We later reverse both lists and draw far -> near.
         */
        List<Surface> surfaces =
                new ArrayList<>();

        List<PlaneSurface> planes =
                new ArrayList<>();

        Tile previous = null;

        if (map.inBounds(mapX, mapY)) {

            previous =
                    map.get(mapX, mapY);
        }

        /*
         * If the camera starts outside the map,
         * stop immediately.
         */
        if (previous == null &&
                !map.inBounds(mapX, mapY)) {

            return;
        }

        /*
         * Distance where the current tile begins.
         */
        float previousEntryDistance =
                0.0f;

        int maxSteps =
                Math.max(width, height) * 16
                        + 256;

        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            final float rayDistance;

            final int side;

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
             * LEFT MAP
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
                    map.get(mapX, mapY);

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
             *
             * Horizontal distance is used here because
             * the DDA operates in the X/Y plane.
             */
            float perpendicularDistance =
                    rayDistance *
                            Mathf.cos(
                                    rayAngle -
                                            camera.rotation.y
                            );

            if (perpendicularDistance < 0.0001f) {

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
                    previous.floor != current.floor) {

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

                if (previous.floor > current.floor) {

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
             * SOLID WALL
             * -------------------------------------------------
             */
            if (current != null &&
                    current.solid) {

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

                break;
            }

            previous =
                    current;

            previousEntryDistance =
                    rayDistance;
        }

        /*
         * -----------------------------------------------------
         * DRAW PLANES FAR -> NEAR
         * -----------------------------------------------------
         */
        Collections.reverse(planes);

        for (PlaneSurface plane : planes) {

            drawPlane(
                    pg,
                    camera,
                    screenX,
                    rayDirX,
                    rayDirY,
                    plane
            );
        }

        /*
         * -----------------------------------------------------
         * DRAW WALLS FAR -> NEAR
         * -----------------------------------------------------
         */
        Collections.reverse(surfaces);

        for (Surface surface : surfaces) {

            drawSurface(
                    pg,
                    camera,
                    screenX,
                    surface
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * ADD FLOOR + CEILING
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

        PixelGraphics floorTexture =
                tile.getFloorTexture();

        if (floorTexture != null &&
                floorTexture.width > 0 &&
                floorTexture.height > 0) {

            planes.add(
                    new PlaneSurface(
                            entryDistance,
                            exitDistance,
                            tile.floor,
                            tile,
                            false
                    )
            );
        }

        PixelGraphics ceilingTexture =
                tile.getCeilingTexture();

        if (ceilingTexture != null &&
                ceilingTexture.width > 0 &&
                ceilingTexture.height > 0) {

            planes.add(
                    new PlaneSurface(
                            entryDistance,
                            exitDistance,
                            tile.ceiling,
                            tile,
                            true
                    )
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * HORIZON
     * ---------------------------------------------------------
     *
     * Returns the screen-space Y coordinate of the horizon
     * after applying camera pitch.
     *
     * rotation.x > 0:
     *     camera looks up
     *     horizon moves down
     *
     * rotation.x < 0:
     *     camera looks down
     *     horizon moves up
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
     * DRAW FLOOR / CEILING
     * ---------------------------------------------------------
     */
    private void drawPlane(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            PlaneSurface plane) {

        if (plane.tile == null) {
            return;
        }

        PixelGraphics texture;

        if (plane.ceiling) {

            texture =
                    plane.tile.getCeilingTexture();

        } else {

            texture =
                    plane.tile.getFloorTexture();
        }

        if (texture == null) {
            return;
        }

        if (texture.width <= 0 ||
                texture.height <= 0) {

            return;
        }

        float planeRelativeHeight =
                plane.height -
                        camera.position.z;

        if (Math.abs(planeRelativeHeight) < 0.000001f) {
            return;
        }

        /*
         * -----------------------------------------------------
         * PITCHED HORIZON
         * -----------------------------------------------------
         */
        float horizon =
                getHorizon(camera);

        /*
         * A floor is below the camera.
         *
         * A ceiling is above the camera.
         *
         * Instead of assuming the horizon is at half the
         * screen, use the pitched horizon.
         */
        int startY;
        int endY;

        if (plane.ceiling) {

            startY = 0;
            endY =
                    (int) Math.ceil(horizon) - 1;

        } else {

            startY =
                    (int) Math.floor(horizon);

            endY =
                    height - 1;
        }

        /*
         * Entire plane is outside the screen.
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
         * DRAW EACH SCREEN PIXEL
         * -----------------------------------------------------
         */
        for (int y = startY;
             y <= endY;
             y++) {

            /*
             * Screen-space Y relative to the pitched horizon.
             *
             * Positive = below horizon.
             * Negative = above horizon.
             */
            float screenY =
                    (y + 0.5f) -
                            horizon;

            if (Math.abs(screenY) < 0.000001f) {
                continue;
            }

            /*
             * Projection equation:
             *
             *     screenY =
             *         -(planeZ - cameraZ)
             *         * focalLength
             *         / distance
             *
             * Therefore:
             *
             *     distance =
             *         -(planeZ - cameraZ)
             *         * focalLength
             *         / screenY
             */
            float distance =
                    -planeRelativeHeight
                            *
                            focalLength
                            /
                            screenY;

            /*
             * Plane must be in front of camera.
             */
            if (distance <= 0.0001f) {
                continue;
            }

            /*
             * -------------------------------------------------
             * TILE INTERVAL
             * -------------------------------------------------
             */
            if (distance <
                    plane.entryDistance - 0.0001f) {

                continue;
            }

            if (distance >
                    plane.exitDistance + 0.0001f) {

                continue;
            }

            /*
             * -------------------------------------------------
             * WORLD POSITION
             * -------------------------------------------------
             */
            float worldX =
                    camera.position.x +
                            rayDirX * distance;

            float worldY =
                    camera.position.y +
                            rayDirY * distance;

            /*
             * -------------------------------------------------
             * TEXTURE POSITION
             * -------------------------------------------------
             */
            float textureX =
                    worldX -
                            (float) Math.floor(worldX);

            float textureY =
                    worldY -
                            (float) Math.floor(worldY);

            if (textureX < 0.0f) {
                textureX += 1.0f;
            }

            if (textureY < 0.0f) {
                textureY += 1.0f;
            }

            if (textureX >= 1.0f) {
                textureX = 0.999999f;
            }

            if (textureY >= 1.0f) {
                textureY = 0.999999f;
            }

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

            pg.setPixel(
                    screenX,
                    y,
                    texture.getPixel(
                            texX,
                            texY
                    )
            );
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
                            rayDistance * rayDirY;

        } else {

            coordinate =
                    camera.position.x
                            +
                            rayDistance * rayDirX;
        }

        coordinate -=
                (float) Math.floor(coordinate);

        if (side == 0) {

            if (rayDirX > 0.0f) {

                coordinate =
                        1.0f - coordinate;
            }

        } else {

            if (rayDirY < 0.0f) {

                coordinate =
                        1.0f - coordinate;
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
     * DRAW WALL
     * ---------------------------------------------------------
     */
    private void drawSurface(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            Surface surface) {

        drawTexturedVertical(
                pg,
                camera,
                screenX,
                surface.perpendicularDistance,
                surface.low,
                surface.high,
                surface.tile,
                surface.textureX
        );
    }

    /*
     * ---------------------------------------------------------
     * DRAW VERTICAL TEXTURE
     * ---------------------------------------------------------
     */
    private void drawTexturedVertical(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float distance,
            float low,
            float high,
            Tile tile,
            float textureX) {

        if (tile == null) {
            return;
        }

        if (high <= low) {
            return;
        }

        PixelGraphics texture =
                tile.getWallTexture();

        if (texture == null) {
            return;
        }

        if (texture.width <= 0 ||
                texture.height <= 0) {

            return;
        }

        if (distance <= 0.0001f) {
            distance = 0.0001f;
        }

        /*
         * Project top and bottom using the pitched horizon.
         */
        int top =
                project(
                        camera,
                        high,
                        distance
                );

        int bottom =
                project(
                        camera,
                        low,
                        distance
                );

        if (top > bottom) {

            int temp =
                    top;

            top =
                    bottom;

            bottom =
                    temp;
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

        int texX =
                (int) (
                        textureX *
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

        float projectedHeight =
                bottom - top;

        if (projectedHeight <= 0.001f) {
            return;
        }

        for (int y = clippedTop;
             y <= clippedBottom;
             y++) {

            float t =
                    (y - top)
                            /
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
        }
    }

    /*
     * ---------------------------------------------------------
     * PROJECT WORLD Z -> SCREEN Y
     * ---------------------------------------------------------
     *
     * Pitch changes the position of the horizon.
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
