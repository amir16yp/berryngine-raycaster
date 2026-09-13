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
     *
     * Represents a floor or ceiling belonging to one tile.
     *
     * entryDistance:
     *     Distance where the ray enters the tile.
     *
     * exitDistance:
     *     Distance where the ray leaves the tile.
     *
     * height:
     *     World-space Z of the floor/ceiling.
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
         * +1 = right side
         */
        float cameraX =
                2.0f *
                        (screenX + 0.5f) /
                        (float) width
                        - 1.0f;

        /*
         * Calculate the ray angle.
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
         * Normalized ray direction.
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

        /*
         * Since rayDir is normalized:

             deltaDistX = distance needed
                          to cross one X cell

             deltaDistY = distance needed
                          to cross one Y cell
         */
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

        /*
         * Ray step direction.
         */
        final int stepX;
        final int stepY;

        /*
         * Distance to first vertical grid line.
         */
        float sideDistX;

        /*
         * Distance to first horizontal grid line.
         */
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
         *
         * DDA discovers everything near -> far.
         *
         * We later reverse both lists and draw far -> near.
         */
        List<Surface> surfaces =
                new ArrayList<>();

        List<PlaneSurface> planes =
                new ArrayList<>();

        /*
         * Tile currently occupied by the ray.
         */
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
         *
         * The first tile begins at the camera.
         */
        float previousEntryDistance =
                0.0f;

        /*
         * -----------------------------------------------------
         * DDA LOOP
         * -----------------------------------------------------
         */
        int maxSteps =
                Math.max(width, height) * 16
                        + 256;

        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            final float rayDistance;

            final int side;

            /*
             * Find the closest grid boundary.
             */
            if (sideDistX < sideDistY) {

                rayDistance =
                        sideDistX;

                sideDistX +=
                        deltaDistX;

                mapX +=
                        stepX;

                /*
                 * Hit an X-aligned grid plane.
                 */
                side = 0;

            } else {

                rayDistance =
                        sideDistY;

                sideDistY +=
                        deltaDistY;

                mapY +=
                        stepY;

                /*
                 * Hit a Y-aligned grid plane.
                 */
                side = 1;
            }

            /*
             * -------------------------------------------------
             * LEFT MAP
             * -------------------------------------------------
             *
             * The previous tile continues until this
             * boundary.
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
             *
             * The previous tile occupies:

                 previousEntryDistance
                         ->
                 rayDistance
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
             * Used only for vertical wall projection.
             *
             * This removes fish-eye distortion.
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
             *
             * Example:

                 previous floor = 0
                 current floor  = 1

             * This exposes the vertical face from
             * Z=0 to Z=1.
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

                /*
                 * The higher tile owns the exposed face.
                 */
                Tile wallTile;

                if (previous.floor > current.floor) {

                    wallTile =
                            previous;

                } else {

                    wallTile =
                            current;
                }

                /*
                 * Texture coordinate must use REAL
                 * ray distance.
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

                /*
                 * The wall blocks everything behind it.
                 *
                 * Do NOT add the solid tile's floor/ceiling
                 * here. They are hidden by the wall.
                 */
                break;
            }

            /*
             * Move to the new tile.
             */
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

        /*
         * Floor.
         */
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

        /*
         * Ceiling.
         */
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
     * DRAW FLOOR / CEILING
     * ---------------------------------------------------------
     *
     * A floor/ceiling is not drawn using a simple projected
     * rectangle.
     *
     * Instead, for every screen pixel:
     *
     *     1. Find the ray/plane intersection distance.
     *
     *     2. Check that the distance lies inside this tile.
     *
     *     3. Convert the intersection into world X/Y.
     *
     *     4. Convert world X/Y into texture coordinates.
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

        /*
         * ---------------------------------------------------------
         * CAMERA / PLANE RELATIONSHIP
         * ---------------------------------------------------------
         *
         * Floor:
         *
         *       floorZ < cameraZ
         *
         * Ceiling:
         *
         *       ceilingZ > cameraZ
         */
        float planeRelativeHeight =
                plane.height -
                        camera.position.z;

        /*
         * If the plane is exactly at camera height,
         * its projection is at the horizon and the
         * distance approaches infinity.
         */
        if (Math.abs(planeRelativeHeight) < 0.000001f) {
            return;
        }

        /*
         * ---------------------------------------------------------
         * DETERMINE SCREEN RANGE
         * ---------------------------------------------------------
         *
         * We don't use project(..., 0.0001f) here.
         *
         * Instead, the important fact is:
         *
         * floor  -> below horizon
         * ceiling -> above horizon
         */
        int startY;
        int endY;

        if (plane.ceiling) {

            /*
             * Ceiling is above the camera.
             *
             * Only render above the horizon.
             */
            startY = 0;
            endY = height / 2 - 1;

        } else {

            /*
             * Floor is below the camera.
             *
             * Only render below the horizon.
             */
            startY = height / 2;
            endY = height - 1;
        }

        /*
         * ---------------------------------------------------------
         * DRAW EACH SCREEN PIXEL
         * ---------------------------------------------------------
         */
        for (int y = startY;
             y <= endY;
             y++) {

            /*
             * Screen-space Y measured from the center.
             *
             * Pixel center is used for better precision.
             */
            float screenY =
                    (y + 0.5f) -
                            height * 0.5f;

        /*
         * Projection equation:

             screenY =
                 -(planeZ - cameraZ)
                 * focalLength
                 / distance

         *
         * Therefore:

             distance =
                 -(planeZ - cameraZ)
                 * focalLength
                 / screenY
         */
            if (Math.abs(screenY) < 0.000001f) {
                continue;
            }

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
             * -----------------------------------------------------
             * TILE INTERVAL
             * -----------------------------------------------------
             *
             * This plane only exists between the two DDA
             * boundaries of this tile.
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
             * -----------------------------------------------------
             * WORLD POSITION
             * -----------------------------------------------------
             */
            float worldX =
                    camera.position.x +
                            rayDirX * distance;

            float worldY =
                    camera.position.y +
                            rayDirY * distance;

            /*
             * -----------------------------------------------------
             * TEXTURE POSITION
             * -----------------------------------------------------
             *
             * Each map tile is one texture repetition.
             */
            float textureX =
                    worldX -
                            (float) Math.floor(worldX);

            float textureY =
                    worldY -
                            (float) Math.floor(worldY);

            /*
             * Keep coordinates inside [0,1).
             */
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
             * -----------------------------------------------------
             * TEXTURE PIXEL
             * -----------------------------------------------------
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
             * -----------------------------------------------------
             * DRAW
             * -----------------------------------------------------
             */
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

            /*
             * X grid plane.
             *
             * Y is the coordinate along the wall.
             */
            coordinate =
                    camera.position.y
                            +
                            rayDistance * rayDirY;

        } else {

            /*
             * Y grid plane.
             *
             * X is the coordinate along the wall.
             */
            coordinate =
                    camera.position.x
                            +
                            rayDistance * rayDirX;
        }

        /*
         * Fractional part.
         */
        coordinate -=
                (float) Math.floor(coordinate);

        /*
         * Consistent wall orientation.
         */
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

        /*
         * Keep inside [0, 1).
         */
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
         * Project top and bottom.
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

        /*
         * Make sure top <= bottom.
         */
        if (top > bottom) {

            int temp =
                    top;

            top =
                    bottom;

            bottom =
                    temp;
        }

        /*
         * Outside screen.
         */
        if (bottom < 0 ||
                top >= height) {

            return;
        }

        /*
         * Clip.
         */
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

        /*
         * Horizontal texture coordinate.
         */
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

        /*
         * Projected wall height.
         */
        float projectedHeight =
                bottom - top;

        if (projectedHeight <= 0.001f) {
            return;
        }

        /*
         * Draw vertical texture.
         */
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
     */
    private int project(
            Camera3D camera,
            float worldZ,
            float distance) {

        if (distance <= 0.0001f) {
            distance = 0.0001f;
        }

        return (int) (
                height * 0.5f
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
