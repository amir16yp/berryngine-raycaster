package raycaster;

import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.Arrays;
import java.util.List;

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

    private boolean isOpaqueEntityPixel(
            int color) {

        int alpha =
                (color >>> 24) &
                        0xFF;

        return alpha != 0;
    }

    private float[] intersectSlab(
            float origin,
            float direction,
            float minimum,
            float maximum,
            float currentEnter,
            float currentExit) {

        if (Math.abs(direction) <
                EPSILON) {

            /*
             * Ray is parallel to this slab.
             */
            if (origin < minimum ||
                    origin > maximum) {

                return null;
            }

            return new float[]{
                    currentEnter,
                    currentExit
            };
        }

        float t0 =
                (minimum - origin) /
                        direction;

        float t1 =
                (maximum - origin) /
                        direction;

        if (t0 > t1) {

            float tmp = t0;
            t0 = t1;
            t1 = tmp;
        }

        float enter =
                Math.max(
                        currentEnter,
                        t0
                );

        float exit =
                Math.min(
                        currentExit,
                        t1
                );

        if (enter > exit) {
            return null;
        }

        return new float[]{
                enter,
                exit
        };
    }

    private static final class EntityHit {

        float distance;
        int color;

        EntityHit(
                float distance,
                int color) {

            this.distance = distance;
            this.color = color;
        }
    }

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
                new float[width * height];

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

        render(pg, camera, null);
    }

    public void render(
            PixelGraphics pg,
            Camera3D camera,
            List<Entity3D> entities) {

        if (pg == null || camera == null) {
            return;
        }

        Arrays.fill(
                depth,
                Float.POSITIVE_INFINITY
        );

        /*
         * Map geometry.
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
         * Entity geometry.
         */
        if (entities != null) {

            for (Entity3D entity : entities) {

                if (entity == null ||
                        entity.texture == null ||
                        entity.width <= 0.0f ||
                        entity.height <= 0.0f ||
                        entity.depth <= 0.0f) {

                    continue;
                }

                renderEntity(
                        pg,
                        camera,
                        entity
                );
            }
        }
    }

    private int localXToTextureX(
            float localX,
            float halfWidth,
            float entityWidth,
            int textureWidth) {

        float u =
                (localX + halfWidth) /
                        entityWidth;

        u =
                Math.clamp(
                        u,
                        0.0f,
                        0.999999f
                );

        return (int) (
                u *
                        textureWidth
        );
    }

    private int localZToTextureY(
            float localZ,
            float entityHeight,
            int textureHeight) {

        /*
         * Local Z:
         *
         *  entity.height = top
         *  0             = bottom
         *
         * Texture:
         *
         *  y = 0              = top
         *  y = textureHeight-1 = bottom
         */
        float v =
                1.0f -
                        localZ /
                                entityHeight;

        v =
                Math.clamp(
                        v,
                        0.0f,
                        0.999999f
                );

        return (int) (
                v *
                        textureHeight
        );
    }

    private EntityHit traceEntityTexture(
            Entity3D entity,
            float originX,
            float originZ,
            float directionX,
            float directionZ,
            float tEnter,
            float tExit) {

        PixelGraphics texture =
                entity.texture;

        final int texWidth =
                texture.width;

        final int texHeight =
                texture.height;

        final float halfWidth =
                entity.width * 0.5f;

        /*
         * Each source texture pixel represents one rectangular
         * cell in entity-local X/Z space.
         */
        final float cellWidth =
                entity.width /
                        texWidth;

        final float cellHeight =
                entity.height /
                        texHeight;

        /*
         * Move an extremely tiny amount inside the entity.
         *
         * This prevents a ray exactly on a texel boundary from
         * repeatedly choosing the previous cell.
         */
        float t =
                tEnter +
                        0.00001f;

        if (t > tExit) {
            t = tEnter;
        }

        float x =
                originX +
                        directionX * t;

        float z =
                originZ +
                        directionZ * t;

        int texX =
                localXToTextureX(
                        x,
                        halfWidth,
                        entity.width,
                        texWidth
                );

        int texY =
                localZToTextureY(
                        z,
                        entity.height,
                        texHeight
                );

        if (texX < 0 ||
                texX >= texWidth ||
                texY < 0 ||
                texY >= texHeight) {

            return null;
        }

        /*
         * DDA direction through the texture grid.
         */
        int stepX;

        if (directionX > EPSILON) {
            stepX = 1;
        } else if (directionX < -EPSILON) {
            stepX = -1;
        } else {
            stepX = 0;
        }

        /*
         * Texture Y runs DOWN while local Z runs UP.
         */
        int stepTexY;

        if (directionZ > EPSILON) {

            /*
             * Going upward in world/local Z means decreasing texY.
             */
            stepTexY = -1;

        } else if (directionZ < -EPSILON) {

            stepTexY = 1;

        } else {

            stepTexY = 0;
        }

        /*
         * At most this many texture cells can be traversed.
         *
         * +4 is just boundary-condition headroom.
         */
        int maxSteps =
                texWidth +
                        texHeight +
                        4;

        for (int iteration = 0;
             iteration < maxSteps;
             iteration++) {

            if (texX < 0 ||
                    texX >= texWidth ||
                    texY < 0 ||
                    texY >= texHeight) {

                return null;
            }

            /*
             * Is the current source texel solid?
             */
            int color =
                    texture.getPixel(
                            texX,
                            texY
                    );

            if (isOpaqueEntityPixel(color)) {

                return new EntityHit(
                        t,
                        color
                );
            }

            /*
             * -----------------------------------------------------
             * NEXT X TEXEL BOUNDARY
             * -----------------------------------------------------
             */

            float nextTX =
                    Float.POSITIVE_INFINITY;

            if (stepX != 0) {

                float boundaryX;

                if (stepX > 0) {

                    boundaryX =
                            -halfWidth +
                                    (texX + 1) *
                                            cellWidth;

                } else {

                    boundaryX =
                            -halfWidth +
                                    texX *
                                            cellWidth;
                }

                nextTX =
                        (boundaryX -
                                originX) /
                                directionX;

                /*
                 * Numerical protection.
                 */
                if (nextTX <=
                        t + EPSILON) {

                    nextTX =
                            t +
                                    EPSILON;
                }
            }

            /*
             * -----------------------------------------------------
             * NEXT Z / TEXTURE-Y BOUNDARY
             * -----------------------------------------------------
             */

            float nextTZ =
                    Float.POSITIVE_INFINITY;

            if (stepTexY != 0) {

                float boundaryZ;

                if (stepTexY < 0) {

                    /*
                     * Moving UP.
                     *
                     * texY decreases.
                     *
                     * top of current texture row:
                     *
                     * z = height -
                     *     texY * cellHeight
                     */
                    boundaryZ =
                            entity.height -
                                    texY *
                                            cellHeight;

                } else {

                    /*
                     * Moving DOWN.
                     *
                     * bottom of current texture row.
                     */
                    boundaryZ =
                            entity.height -
                                    (texY + 1) *
                                            cellHeight;
                }

                nextTZ =
                        (boundaryZ -
                                originZ) /
                                directionZ;

                if (nextTZ <=
                        t + EPSILON) {

                    nextTZ =
                            t +
                                    EPSILON;
                }
            }

            /*
             * Whichever texel edge comes first.
             */
            float nextT =
                    Math.min(
                            nextTX,
                            nextTZ
                    );

            if (!Float.isFinite(nextT) ||
                    nextT > tExit) {

                return null;
            }

            /*
             * Corner crossing:
             *
             * advance both dimensions when both boundaries are
             * essentially at the same position.
             */
            boolean crossedX =
                    Math.abs(
                            nextTX -
                                    nextT
                    ) < 0.00001f;

            boolean crossedZ =
                    Math.abs(
                            nextTZ -
                                    nextT
                    ) < 0.00001f;

            if (crossedX) {
                texX += stepX;
            }

            if (crossedZ) {
                texY += stepTexY;
            }

            t =
                    nextT +
                            0.00001f;

            if (t > tExit) {
                return null;
            }
        }

        return null;
    }

    private void renderEntity(
            PixelGraphics pg,
            Camera3D camera,
            Entity3D entity) {

        if (entity.texture == null ||
                entity.texture.width <= 0 ||
                entity.texture.height <= 0) {

            return;
        }

        if (entity.width <= EPSILON ||
                entity.height <= EPSILON ||
                entity.depth <= EPSILON) {

            return;
        }

        /*
         * Entity-local bounds:
         *
         * X = width
         * Y = extrusion depth
         * Z = height
         *
         *          Z
         *          ^
         *          |
         *      +-------+
         *     /       /|
         *    +-------+ |
         *    | image | | --> local Y / depth
         *    |       |/
         *    +-------+
         *       X
         *
         * entity.pos is the center in X/Y,
         * but the BOTTOM in Z.
         */

        final float halfWidth =
                entity.width * 0.5f;

        final float halfDepth =
                entity.depth * 0.5f;

        final float cosYaw =
                Mathf.cos(entity.yaw);

        final float sinYaw =
                Mathf.sin(entity.yaw);

        /*
         * Camera relative to entity.
         */
        float relativeCameraX =
                camera.position.x -
                        entity.pos.x;

        float relativeCameraY =
                camera.position.y -
                        entity.pos.y;

        /*
         * Rotate camera into entity-local coordinates.
         */
        float localOriginX =
                relativeCameraX * cosYaw +
                        relativeCameraY * sinYaw;

        float localOriginY =
                -relativeCameraX * sinYaw +
                        relativeCameraY * cosYaw;

        float localOriginZ =
                camera.position.z -
                        entity.pos.z;

        final float horizon =
                getHorizon(camera);

        /*
         * Render every framebuffer pixel potentially covered
         * by the entity.
         *
         * This is deliberately ray-based instead of triangle-based.
         * A distant texture texel cannot disappear simply because
         * its triangles became sub-pixel.
         */
        for (int screenY = 0;
             screenY < height;
             screenY++) {

            /*
             * Vertical ray slope matching the projection used by
             * floors/walls in this renderer.
             *
             * worldZ(t) =
             *
             * camera.z + rayDirZ * t
             *
             * where t is horizontal ray distance.
             */
            float screenOffsetY =
                    (screenY + 0.5f) -
                            horizon;

            float rayDirZ =
                    -screenOffsetY /
                            focalLength;

            for (int screenX = 0;
                 screenX < width;
                 screenX++) {

                float cameraX =
                        2.0f *
                                (screenX + 0.5f) /
                                (float) width -
                                1.0f;

                /*
                 * Same horizontal ray construction as castColumn().
                 */
                float rayAngle =
                        camera.rotation.y +
                                (float) Math.atan(
                                        cameraX *
                                                Mathf.tan(
                                                        camera.fov *
                                                                0.5f
                                                )
                                );

                float worldRayX =
                        Mathf.cos(rayAngle);

                float worldRayY =
                        Mathf.sin(rayAngle);

                /*
                 * Rotate ray into entity-local coordinates.
                 */
                float localRayX =
                        worldRayX * cosYaw +
                                worldRayY * sinYaw;

                float localRayY =
                        -worldRayX * sinYaw +
                                worldRayY * cosYaw;

                /*
                 * Find interval through overall entity bounds.
                 */
                float tEnter =
                        0.0f;

                float tExit =
                        renderDistance;

                /*
                 * X slab.
                 */
                float[] interval =
                        intersectSlab(
                                localOriginX,
                                localRayX,
                                -halfWidth,
                                halfWidth,
                                tEnter,
                                tExit
                        );

                if (interval == null) {
                    continue;
                }

                tEnter = interval[0];
                tExit = interval[1];

                /*
                 * Y/depth slab.
                 */
                interval =
                        intersectSlab(
                                localOriginY,
                                localRayY,
                                -halfDepth,
                                halfDepth,
                                tEnter,
                                tExit
                        );

                if (interval == null) {
                    continue;
                }

                tEnter = interval[0];
                tExit = interval[1];

                /*
                 * Z/height slab.
                 */
                interval =
                        intersectSlab(
                                localOriginZ,
                                rayDirZ,
                                0.0f,
                                entity.height,
                                tEnter,
                                tExit
                        );

                if (interval == null) {
                    continue;
                }

                tEnter = interval[0];
                tExit = interval[1];

                if (tExit < MIN_DISTANCE) {
                    continue;
                }

                tEnter =
                        Math.max(
                                tEnter,
                                MIN_DISTANCE
                        );

                if (tEnter > tExit ||
                        tEnter > renderDistance) {

                    continue;
                }

                tExit =
                        Math.min(
                                tExit,
                                renderDistance
                        );

                /*
                 * Find the first opaque extruded texture cell
                 * touched by this ray.
                 */
                EntityHit hit =
                        traceEntityTexture(
                                entity,
                                localOriginX,
                                localOriginZ,
                                localRayX,
                                rayDirZ,
                                tEnter,
                                tExit
                        );

                if (hit == null) {
                    continue;
                }

                /*
                 * Map/entity depth convention:
                 *
                 * depth is distance along camera forward,
                 * not raw ray distance.
                 */
                float cameraDepth =
                        hit.distance *
                                Mathf.cos(
                                        rayAngle -
                                                camera.rotation.y
                                );

                if (cameraDepth <= MIN_DISTANCE) {
                    continue;
                }

                int depthIndex =
                        screenX +
                                screenY * width;

                if (cameraDepth >=
                        depth[depthIndex]) {

                    continue;
                }

                pg.setPixel(
                        screenX,
                        screenY,
                        hit.color
                );

                depth[depthIndex] =
                        cameraDepth;
            }
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
//        Arrays.fill(
//                depth,
//                Float.POSITIVE_INFINITY
//        );

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
                    depth[screenX + y * width]) {

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

            depth[screenX + y * width] =
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
                    depth[screenX + y * width]) {

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

            depth[screenX + y * width] =
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
