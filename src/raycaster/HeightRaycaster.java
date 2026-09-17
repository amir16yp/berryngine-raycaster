package raycaster;

import berryngine.Color;
import berryngine.Mathf;
import berryngine.PixelGraphics;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

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
 * <p>Threading: a frame lock serializes render calls and cooperative scene
 * updates. Workers own disjoint vertical screen bands, private surface/plane
 * pools, and reusable pixel-write buffers. Only the calling thread writes to
 * the target PixelGraphics, after every worker has finished.</p>
 *
 * <p>The camera, map, tiles, entities, lights, and texture pixels must remain
 * unchanged until render() returns. Use getRenderLock() or withRenderLock()
 * for updates performed by another thread. Texture/map getters must support
 * concurrent read-only access; this class cannot synchronize their internals.</p>
 *
 * <p>Culling (enabled by default): exact per-column entity box intersections,
 * conservative projected entity row spans, and pre-trace entity occlusion.
 * Map DDA already visits only view rays up to the render distance or a solid
 * blocker. New cell-surface culling rejects offscreen walls/floors/ceilings/tops
 * and limits each surviving horizontal face to its projected row interval.
 * Cell traversal itself is preserved for height transitions and door holes.
 * No TileMap/Entity3D changes or cached static visibility data are required.</p>
 *
 * <p>Construct once and reuse. Call close() when the renderer is discarded.
 * Entity slab intersections and texture hits reuse worker-local scratch
 * objects; those helpers do not allocate per ray or per hit. The entire frame
 * is not allocation-free: frame coordination, list iterators, executor internals,
 * and occasional pixel-write buffer growth can still allocate.</p>
 */
public final class HeightRaycaster implements AutoCloseable {

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

    /** Full-screen camera-forward depth; each column has exactly one owner. */
    private final float[] depth;

    /** These capacities are per worker, not totals across the worker pool. */
    private final int surfacePoolSize;
    private final int planePoolSize;

    private final ReentrantLock renderLock = new ReentrantLock();
    private final Worker[] workers;
    private final ExecutorService executor;

    // Accessed only while renderLock is held.
    private boolean closed;
    private boolean rendering;
    private boolean entityCullingEnabled = true;
    private boolean tileCullingEnabled = true;
    private int surfacePoolOverflows;
    private int planePoolOverflows;

    /** A worker owns its pools and every pixel write in [startX, endX). */
    private static final class WorkerContext {
        final int startX;
        final int endX;
        final Surface[] surfacePool;
        final PlaneSurface[] planePool;

        // One scratch pair per worker, reused across every entity and pixel.
        // Never shared with another worker and never exposed outside rendering.
        final SlabInterval slabInterval = new SlabInterval();
        final EntityHit entityHit = new EntityHit();
        final RowSpan rowSpan = new RowSpan();

        // Cached once per frame, using the exact original ray equations.
        final float[] rayAngles;
        final float[] rayX;
        final float[] rayY;
        final float[] rayDepthFactors;
        boolean entityCullingEnabled;
        boolean tileCullingEnabled;

        int surfacePoolOverflows;
        int planePoolOverflows;

        // Packed triples: x, y, color. Preserve ALL writes, including overdraw,
        // so PixelGraphics.setPixel() retains its per-pixel alpha semantics.
        private int[] pixelWrites;
        private int pixelWriteSize;

        WorkerContext(int startX, int endX, int height,
                      int maxSurfaces, int maxPlanes) {
            this.startX = startX;
            this.endX = endX;
            int columns = endX - startX;
            rayAngles = new float[columns];
            rayX = new float[columns];
            rayY = new float[columns];
            rayDepthFactors = new float[columns];
            surfacePool = new Surface[maxSurfaces];
            planePool = new PlaneSurface[maxPlanes];
            for (int i = 0; i < maxSurfaces; i++) {
                surfacePool[i] = new Surface();
            }
            for (int i = 0; i < maxPlanes; i++) {
                planePool[i] = new PlaneSurface();
            }
            int initialCapacity = (int) Math.max(3L,
                    Math.min(196608L, 3L * (endX - startX) * height));
            pixelWrites = new int[initialCapacity];
        }

        void beginFrame(boolean entityCullingEnabled, boolean tileCullingEnabled) {
            this.entityCullingEnabled = entityCullingEnabled;
            this.tileCullingEnabled = tileCullingEnabled;
            pixelWriteSize = 0;
            surfacePoolOverflows = 0;
            planePoolOverflows = 0;
        }

        void writePixel(int x, int y, int color) {
            assert x >= startX && x < endX : "Pixel outside worker band";
            final int required = Math.addExact(pixelWriteSize, 3);
            if (required > pixelWrites.length) {
                final int maxArrayLength = Integer.MAX_VALUE - 8;
                if (required > maxArrayLength) {
                    throw new OutOfMemoryError("Raycaster pixel-write buffer is too large");
                }
                int capacity = (int) Math.max(required,
                        Math.min((long) maxArrayLength, 2L * pixelWrites.length));
                pixelWrites = Arrays.copyOf(pixelWrites, capacity);
            }
            pixelWrites[pixelWriteSize++] = x;
            pixelWrites[pixelWriteSize++] = y;
            pixelWrites[pixelWriteSize++] = color;
        }

        // Called ONLY by the render() caller, after the completion barrier.
        void copyTo(PixelGraphics target) {
            for (int i = 0; i < pixelWriteSize; i += 3) {
                target.setPixel(pixelWrites[i], pixelWrites[i + 1], pixelWrites[i + 2]);
            }
        }
    }

    private static final class FrameInput {
        final Camera3D camera;
        final List<Entity3D> entities;
        final List<Light> lights;
        final float ambient;
        final CountDownLatch done;

        FrameInput(Camera3D camera, List<Entity3D> entities, List<Light> lights,
                   float ambient, int workerCount) {
            this.camera = camera;
            this.entities = entities;
            this.lights = lights;
            this.ambient = ambient;
            done = new CountDownLatch(workerCount);
        }
    }

    /** Reused across frames, never submitted twice before its frame completes. */
    private final class Worker implements Runnable {
        final WorkerContext context;
        FrameInput frame;
        Throwable failure;

        Worker(WorkerContext context) {
            this.context = context;
        }

        @Override
        public void run() {
            final FrameInput current = frame;
            try {
                renderBand(context, current);
            } catch (Throwable error) {
                // Propagate on the caller only after ALL workers have stopped
                // touching this frame, including when a worker throws an Error.
                failure = error;
            } finally {
                frame = null;
                current.done.countDown();
                // Do not touch reusable worker/frame state after countDown().
            }
        }
    }

    private boolean isOpaqueEntityPixel(
            int color) {

        int alpha =
                (color >>> 24) &
                        0xFF;

        return alpha != 0;
    }

    /**
     * Intersect one axis-aligned slab with the current ray interval.
     * Writes both output fields on success; callers must ignore them on failure.
     * The output belongs to the current worker, so no allocation or lock is needed.
     */
    private boolean intersectSlab(
            float origin,
            float direction,
            float minimum,
            float maximum,
            float currentEnter,
            float currentExit,
            SlabInterval out) {

        if (Math.abs(direction) < EPSILON) {
            // Ray is parallel to this slab.
            if (origin < minimum || origin > maximum) {
                return false;
            }
            out.enter = currentEnter;
            out.exit = currentExit;
            return true;
        }

        float t0 = (minimum - origin) / direction;
        float t1 = (maximum - origin) / direction;
        if (t0 > t1) {
            float tmp = t0;
            t0 = t1;
            t1 = tmp;
        }

        float enter = Math.max(currentEnter, t0);
        float exit = Math.min(currentExit, t1);
        if (enter > exit) {
            return false;
        }

        out.enter = enter;
        out.exit = exit;
        return true;
    }

    /** Reusable result of a slab intersection; owned by one WorkerContext. */
    private static final class SlabInterval {
        float enter;
        float exit;
    }

    /** Reusable texture hit; its fields are valid only after a successful trace. */
    private static final class EntityHit {
        float distance;
        int color;
    }

    /** Inclusive pixel-row interval, reused by its owning worker. */
    private static final class RowSpan {
        int minY;
        int maxY;
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
        int minY;
        int maxY;

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
                boolean top,
                int minY,
                int maxY) {

            this.minY = minY;
            this.maxY = maxY;

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
        this(map, width, height, fov, renderDistance,
                Math.max(1, Runtime.getRuntime().availableProcessors() - 1));
    }

    /**
     * @param workerCount requested parallelism, clamped to the screen width;
     *                    1 runs on the calling thread without creating a pool
     */
    public HeightRaycaster(
            TileMap map,
            int width,
            int height,
            float fov,
            float renderDistance,
            int workerCount) {
        if (map == null) {
            throw new IllegalArgumentException("map cannot be null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be > 0");
        }

        this.map = map;
        this.width = width;
        this.height = height;
        this.renderDistance = Math.max(0.001f, renderDistance);
        this.focalLength = (width * 0.5f) / Mathf.tan(fov * 0.5f);
        depth = new float[Math.multiplyExact(width, height)];

        // Preserve the original per-ray pool sizing.
        int maxTiles = (int) Math.ceil(renderDistance * 1.5f) + 16;
        surfacePoolSize = Math.max(32, maxTiles);
        planePoolSize = Math.max(64, maxTiles * 2);

        int count = Math.min(width, workerCount);
        workers = new Worker[count];
        for (int i = 0; i < count; i++) {
            int startX = (int) ((long) i * width / count);
            int endX = (int) ((long) (i + 1) * width / count);
            workers[i] = new Worker(new WorkerContext(
                    startX, endX, height, surfacePoolSize, planePoolSize));
        }

        if (count == 1) {
            executor = null;
        } else {
            AtomicInteger nextThreadId = new AtomicInteger(1);
            executor = Executors.newFixedThreadPool(count, task -> {
                Thread thread = new Thread(task,
                        "HeightRaycaster-worker-" + nextThreadId.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            });
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

        render(pg, camera, null, null);
    }

    public void render(
            PixelGraphics pg,
            Camera3D camera,
            List<Entity3D> entities) {

        render(pg, camera, entities, null);
    }

    /**
     * Synchronous frame boundary: returns only after every worker has finished
     * and all generated pixel writes have been applied to pg on this thread.
     * It does not clear the target; unrendered pixels retain their background.
     * Interrupts are preserved but do not let workers outlive this call.
     */
    public void render(
            PixelGraphics pg,
            Camera3D camera,
            List<Entity3D> entities,
            List<Light> lights) {
        if (pg == null || camera == null) {
            return;
        }

        renderLock.lock();
        try {
            ensureOpen();
            if (rendering) {
                throw new IllegalStateException("Recursive render() is not supported");
            }
            rendering = true;
            try {
                FrameInput frame = new FrameInput(
                        camera, entities, lights, map.ambientLight, workers.length);

                for (Worker worker : workers) {
                    worker.context.beginFrame(entityCullingEnabled, tileCullingEnabled);
                    worker.failure = null;
                    worker.frame = frame;
                }

                Throwable failure = null;
                int submitted = 0;
                try {
                    if (executor == null) {
                        workers[0].run();
                        submitted = 1;
                    } else {
                        for (; submitted < workers.length; submitted++) {
                            executor.execute(workers[submitted]);
                        }
                    }
                } catch (RuntimeException | Error error) {
                    failure = error;
                } finally {
                    // A submission failure must not leave the barrier waiting
                    // for tasks that were never started.
                    for (int i = submitted; i < workers.length; i++) {
                        workers[i].frame = null;
                        frame.done.countDown();
                    }
                    awaitUninterruptibly(frame.done);
                }

                for (Worker worker : workers) {
                    surfacePoolOverflows += worker.context.surfacePoolOverflows;
                    planePoolOverflows += worker.context.planePoolOverflows;
                    if (worker.failure != null) {
                        if (failure == null) {
                            failure = worker.failure;
                        } else if (failure != worker.failure) {
                            failure.addSuppressed(worker.failure);
                        }
                    }
                    worker.failure = null;
                    worker.frame = null;
                }

                if (failure instanceof Error) {
                    throw (Error) failure;
                }
                if (failure instanceof RuntimeException) {
                    throw (RuntimeException) failure;
                }
                if (failure != null) {
                    throw new IllegalStateException("Raycaster worker failed", failure);
                }

                // No worker ever calls pg.setPixel(). Keep writes on the caller,
                // preserving each pixel's original overdraw/alpha write order.
                for (Worker worker : workers) {
                    worker.context.copyTo(pg);
                }
            } finally {
                rendering = false;
            }
        } finally {
            renderLock.unlock();
        }
    }

    private void renderBand(WorkerContext context, FrameInput frame) {
        cacheColumnRays(context, frame.camera);
        // Workers clear and modify only their own depth-buffer columns.
        for (int y = 0; y < height; y++) {
            int row = y * width;
            Arrays.fill(depth, row + context.startX, row + context.endX,
                    Float.POSITIVE_INFINITY);
        }

        for (int x = context.startX; x < context.endX; x++) {
            castColumn(context, frame.camera, x, frame.lights, frame.ambient);
        }

        if (frame.entities != null) {
            // Preserve entity order in each band, including equal-depth ties.
            for (Entity3D entity : frame.entities) {
                if (entity == null || entity.texture == null
                        || entity.width <= 0.0f || entity.height <= 0.0f
                        || entity.depth <= 0.0f || !entity.visible) {
                    continue;
                }
                renderEntity(context, frame.camera, entity, frame.lights, frame.ambient);
            }
        }
    }

    private static void awaitUninterruptibly(CountDownLatch done) {
        boolean interrupted = false;
        try {
            for (;;) {
                try {
                    done.await();
                    return;
                } catch (InterruptedException ignored) {
                    // Never release the frame lock while workers are still
                    // using this frame's scene references or render buffers.
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("HeightRaycaster is closed");
        }
    }

    /**
     * Same lock used by render(). All other threads that mutate the camera,
     * scene, lists, doors, lights, or textures must acquire this lock too.
     * Acquire it around target clearing/presentation too if those operations
     * could otherwise overlap another render() call on this instance.
     * Do not acquire this lock from a worker's map/texture getter: render()
     * already holds it while waiting for the workers.
     */
    public ReentrantLock getRenderLock() {
        return renderLock;
    }

    /** Execute a cooperative scene update under the frame lock. */
    public void withRenderLock(Runnable action) {
        Objects.requireNonNull(action, "action");
        renderLock.lock();
        try {
            ensureOpen();
            action.run();
        } finally {
            renderLock.unlock();
        }
    }

    /** Enabled by default; changing it does not affect an in-progress frame. */
    public void setEntityCullingEnabled(boolean enabled) {
        renderLock.lock();
        try {
            ensureOpen();
            entityCullingEnabled = enabled;
        } finally {
            renderLock.unlock();
        }
    }

    public boolean isEntityCullingEnabled() {
        renderLock.lock();
        try {
            return entityCullingEnabled;
        } finally {
            renderLock.unlock();
        }
    }

    /** Enabled by default; disables only the NEW cell-surface/span culling. */
    public void setTileCullingEnabled(boolean enabled) {
        renderLock.lock();
        try {
            ensureOpen();
            tileCullingEnabled = enabled;
        } finally {
            renderLock.unlock();
        }
    }

    public boolean isTileCullingEnabled() {
        renderLock.lock();
        try {
            return tileCullingEnabled;
        } finally {
            renderLock.unlock();
        }
    }

    public int getWorkerCount() {
        return workers.length;
    }

    /** Stop the persistent pool. Safe to call more than once. */
    @Override
    public void close() {
        renderLock.lock();
        try {
            if (closed) {
                return;
            }
            if (rendering) {
                throw new IllegalStateException("Cannot close from inside render()");
            }
            closed = true;
            if (executor == null) {
                return;
            }
            executor.shutdown();
            boolean interrupted = false;
            try {
                for (;;) {
                    try {
                        if (executor.awaitTermination(1, TimeUnit.DAYS)) {
                            break;
                        }
                    } catch (InterruptedException ignored) {
                        interrupted = true;
                    }
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        } finally {
            renderLock.unlock();
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
                Mathf.clamp(
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
                Mathf.clamp(
                        v,
                        0.0f,
                        0.999999f
                );

        return (int) (
                v *
                        textureHeight
        );
    }

    /** Writes the worker-local hit on success; allocates no per-hit object. */
    private boolean traceEntityTexture(
            Entity3D entity,
            float originX,
            float originZ,
            float directionX,
            float directionZ,
            float tEnter,
            float tExit,
            EntityHit out) {

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

            return false;
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

                return false;
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

                out.distance = t;
                out.color = color;
                return true;
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

                return false;
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
                return false;
            }
        }

        return false;
    }

    /** No arrays are created here; every worker owns its cached column rays. */
    private void cacheColumnRays(WorkerContext context, Camera3D camera) {
        for (int x = context.startX; x < context.endX; x++) {
            int i = x - context.startX;
            float cameraX = 2.0f * (x + 0.5f) / (float) width - 1.0f;
            float angle = camera.rotation.y + (float) Math.atan(
                    cameraX * Mathf.tan(camera.fov * 0.5f));
            context.rayAngles[i] = angle;
            context.rayX[i] = Mathf.cos(angle);
            context.rayY[i] = Mathf.sin(angle);
            context.rayDepthFactors[i] = Mathf.cos(angle - camera.rotation.y);
        }
    }

    /**
     * Conservative row bounds for a vertical interval over a positive interval
     * of horizontal ray distances. This renderer's floor/entity projection is
     * y + 0.5 = horizon - relativeZ * focalLength / horizontalRayDistance.
     * It is NOT the wall projection, which uses camera-forward depth.
     *
     * Extrema of this quotient over the interval occur at its four endpoints.
     * Near-camera/camera-inside cases are handled by a positive distance floor,
     * not by projecting only the eight corners of an ordinary perspective box.
     * A small pixel/roundoff margin keeps boundary samples conservative; the
     * existing exact slab, interval and depth tests still decide every write.
     * Non-finite/unsupported projection inputs fall back to the full viewport.
     */
    private boolean projectRowSpan(float relativeLow, float relativeHigh,
                                   float nearDistance, float farDistance,
                                   float horizon, RowSpan out) {
        out.minY = 0;
        out.maxY = height - 1;
        if (!Float.isFinite(relativeLow) || !Float.isFinite(relativeHigh)
                || !Float.isFinite(nearDistance) || !Float.isFinite(farDistance)
                || !Float.isFinite(horizon) || !Float.isFinite(focalLength)
                || focalLength <= 0.0f) {
            return true;
        }
        if (farDistance < nearDistance || farDistance < MIN_DISTANCE) {
            return false;
        }
        double near = Math.max(MIN_DISTANCE, nearDistance);
        double far = farDistance;
        double a = -(double) relativeLow * focalLength;
        double b = -(double) relativeHigh * focalLength;
        double y0 = horizon + a / near;
        double y1 = horizon + a / far;
        double y2 = horizon + b / near;
        double y3 = horizon + b / far;
        double low = Math.min(Math.min(y0, y1), Math.min(y2, y3));
        double high = Math.max(Math.max(y0, y1), Math.max(y2, y3));

        // EPSILON also covers rays treated as parallel by intersectSlab().
        double guard = 2.0 + Math.abs((double) focalLength) * EPSILON
                + 8.0 * Math.ulp(horizon)
                + 8.0 * Math.ulp((float) low)
                + 8.0 * Math.ulp((float) high);
        low -= 0.5 + guard;
        high += guard - 0.5;
        if (!Double.isFinite(low) || !Double.isFinite(high)) {
            return true;
        }
        if (high < 0.0 || low > height - 1.0) {
            return false;
        }
        out.minY = low <= 0.0 ? 0 : (int) Math.floor(low);
        out.maxY = high >= height - 1.0 ? height - 1 : (int) Math.ceil(high);
        return out.minY <= out.maxY;
    }

    /** Cull one cell's horizontal face before consuming a plane-pool entry. */
    private boolean findPlaneRows(WorkerContext context, Camera3D camera,
                                  float worldHeight, float entryDistance,
                                  float exitDistance, RowSpan out) {
        out.minY = 0;
        out.maxY = height - 1;
        if (!context.tileCullingEnabled) {
            return true;
        }
        float relativeHeight = worldHeight - camera.position.z;
        if (Math.abs(relativeHeight) < EPSILON) {
            return false;
        }
        // Match drawPlaneDepthTested's inclusive tile-edge tolerance.
        float near = Math.max(MIN_DISTANCE,
                Math.nextDown(entryDistance - 0.0001f));
        float far = Math.min(renderDistance,
                Math.nextUp(exitDistance + 0.0001f));
        return projectRowSpan(relativeHeight, relativeHeight, near, far,
                getHorizon(camera), out);
    }

    /**
     * Reject offscreen/degenerate cell walls without changing DDA topology.
     * Even a culled solid tile must still terminate traversal, and a transparent
     * door must still permit traversal. Never skip a cell based on its center.
     */
    private int addVisibleSurface(WorkerContext context, Camera3D camera,
                                  int count, float distance,
                                  float perpendicularDistance, float low,
                                  float high, float textureX, Tile tile) {
        if (context.tileCullingEnabled) {
            if (high <= low) {
                return count;
            }
            float d = Math.max(MIN_DISTANCE, perpendicularDistance);
            int top = project(camera, high, d);
            int bottom = project(camera, low, d);
            int min = Math.min(top, bottom);
            int max = Math.max(top, bottom);
            if (max < 0 || min >= height || max == min) {
                return count;
            }
        }
        if (count < context.surfacePool.length) {
            context.surfacePool[count++].set(distance, perpendicularDistance,
                    low, high, textureX, tile);
        } else {
            context.surfacePoolOverflows++;
        }
        return count;
    }

    private void renderEntity(
            WorkerContext context,
            Camera3D camera,
            Entity3D entity,
            List<Light> lights,
            float ambient) {
        if (entity.texture == null || entity.texture.width <= 0
                || entity.texture.height <= 0 || entity.width <= EPSILON
                || entity.height <= EPSILON || entity.depth <= EPSILON) {
            return;
        }

        // Position is the center in X/Y and the BOTTOM in Z; yaw rotates X/Y.
        final float halfWidth = entity.width * 0.5f;
        final float halfDepth = entity.depth * 0.5f;
        final float cosYaw = Mathf.cos(entity.yaw);
        final float sinYaw = Mathf.sin(entity.yaw);
        final float relativeCameraX = camera.position.x - entity.pos.x;
        final float relativeCameraY = camera.position.y - entity.pos.y;
        final float localOriginX = relativeCameraX * cosYaw
                + relativeCameraY * sinYaw;
        final float localOriginY = -relativeCameraX * sinYaw
                + relativeCameraY * cosYaw;
        final float localOriginZ = camera.position.z - entity.pos.z;
        final float horizon = getHorizon(camera);
        final SlabInterval interval = context.slabInterval;
        final EntityHit hit = context.entityHit;
        final RowSpan rows = context.rowSpan;

        /*
         * Screen-space culling using exact column/OBB intersections:
         * - only view-frustum rays are considered;
         * - [0, renderDistance] rejects behind-camera and distant intersections;
         * - each surviving column gets a conservative vertical screen span;
         * - a bound-entry depth test rejects hidden pixels BEFORE texel DDA.
         *
         * This is tighter than one projected box rectangle and remains valid
         * when the camera is inside the box or the box crosses the near plane.
         * Columns can be reordered without changing per-pixel entity/alpha order.
         */
        for (int screenX = context.startX; screenX < context.endX; screenX++) {
            final int column = screenX - context.startX;
            final float worldRayX = context.rayX[column];
            final float worldRayY = context.rayY[column];
            final float depthFactor = context.rayDepthFactors[column];
            final float localRayX = worldRayX * cosYaw + worldRayY * sinYaw;
            final float localRayY = -worldRayX * sinYaw + worldRayY * cosYaw;

            // These X/Y slabs are independent of screenY: test once per column.
            if (!intersectSlab(localOriginX, localRayX, -halfWidth, halfWidth,
                    0.0f, renderDistance, interval)) {
                continue;
            }
            if (!intersectSlab(localOriginY, localRayY, -halfDepth, halfDepth,
                    interval.enter, interval.exit, interval)) {
                continue;
            }
            final float columnEnter = interval.enter;
            final float columnExit = interval.exit;
            if (columnExit < MIN_DISTANCE) {
                continue;
            }

            int startY = 0;
            int endY = height - 1;
            if (context.entityCullingEnabled) {
                if (!projectRowSpan(-localOriginZ, entity.height - localOriginZ,
                        Math.max(columnEnter, MIN_DISTANCE), columnExit,
                        horizon, rows)) {
                    continue;
                }
                startY = rows.minY;
                endY = rows.maxY;
            }

            for (int screenY = startY; screenY <= endY; screenY++) {
                float screenOffsetY = (screenY + 0.5f) - horizon;
                float rayDirZ = -screenOffsetY / focalLength;
                if (!intersectSlab(localOriginZ, rayDirZ, 0.0f, entity.height,
                        columnEnter, columnExit, interval)) {
                    continue;
                }
                float tEnter = interval.enter;
                float tExit = interval.exit;
                if (tExit < MIN_DISTANCE) {
                    continue;
                }
                tEnter = Math.max(tEnter, MIN_DISTANCE);
                if (tEnter > tExit || tEnter > renderDistance) {
                    continue;
                }
                tExit = Math.min(tExit, renderDistance);
                final int depthIndex = screenX + screenY * width;

                // A texture hit cannot be nearer than its containing box entry.
                // Positive factor is required for this monotonic depth bound.
                if (context.entityCullingEnabled && depthFactor > 0.0f
                        && tEnter * depthFactor >= depth[depthIndex]) {
                    continue;
                }
                if (!traceEntityTexture(entity, localOriginX, localOriginZ,
                        localRayX, rayDirZ, tEnter, tExit, hit)) {
                    continue;
                }
                float cameraDepth = hit.distance * depthFactor;
                if (cameraDepth <= MIN_DISTANCE
                        || cameraDepth >= depth[depthIndex]) {
                    continue;
                }
                float hitWorldX = camera.position.x + worldRayX * hit.distance;
                float hitWorldY = camera.position.y + worldRayY * hit.distance;
                float hitWorldZ = camera.position.z + rayDirZ * hit.distance;
                int litColor = applyLighting(hit.color, hitWorldX, hitWorldY,
                        hitWorldZ, lights, ambient);
                context.writePixel(screenX, screenY, litColor);
                depth[depthIndex] = cameraDepth;
            }
        }
    }
    /*
     * ---------------------------------------------------------
     * LIGHTING
     * ---------------------------------------------------------
     */

    private int applyLighting(
            int color,
            float worldX,
            float worldY,
            float worldZ,
            List<Light> lights,
            float ambient) {

        if ((lights == null || lights.isEmpty())
                && ambient >= 1.0f - EPSILON) {

            return color;
        }

        float r = Color.getRed(color);
        float g = Color.getGreen(color);
        float b = Color.getBlue(color);
        int alpha = (color >>> 24) & 0xFF;

        float litR = r * ambient;
        float litG = g * ambient;
        float litB = b * ambient;

        if (lights != null) {

            for (int i = 0;
                 i < lights.size();
                 i++) {

                Light light = lights.get(i);

                if (light == null ||
                        !light.enabled) {

                    continue;
                }

                float dx = worldX - light.pos.x;
                float dy = worldY - light.pos.y;
                float dz = worldZ - light.pos.z;

                float radius = light.radius;

                if (radius <= 0.0f) {

                    continue;
                }

                float distSq =
                        dx * dx +
                                dy * dy +
                                dz * dz;

                float radiusSq = radius * radius;

                if (distSq >= radiusSq) {

                    continue;
                }

                float dist =
                        (float) Math.sqrt(
                                distSq
                        );

                float attenuation =
                        1.0f - dist / radius;

                if (attenuation <= 0.0f) {

                    continue;
                }

                float factor =
                        attenuation *
                                light.intensity;

                litR += r * factor *
                        (Color.getRed(light.color) / 255.0f);

                litG += g * factor *
                        (Color.getGreen(light.color) / 255.0f);

                litB += b * factor *
                        (Color.getBlue(light.color) / 255.0f);
            }
        }

        int outR = Mathf.clamp(
                (int) litR,
                0,
                255
        );

        int outG = Mathf.clamp(
                (int) litG,
                0,
                255
        );

        int outB = Mathf.clamp(
                (int) litB,
                0,
                255
        );

        return Color.fromRGBA(
                outR,
                outG,
                outB,
                alpha
        );
    }

    /*
     * ---------------------------------------------------------
     * CAST COLUMN
     * ---------------------------------------------------------
     */

    private void castColumn(
            WorkerContext context,
            Camera3D camera,
            int screenX,
            List<Light> lights,
            float ambient) {
        int i = screenX - context.startX;
        castDDA(context, camera, screenX, context.rayX[i], context.rayY[i],
                context.rayAngles[i], lights, ambient);
    }

    /*
     * ---------------------------------------------------------
     * DDA
     * ---------------------------------------------------------
     */

    private void castDDA(
            WorkerContext context,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float rayAngle,
            List<Light> lights,
            float ambient) {

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

        // Depth for this band is cleared once by renderBand().

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
                                    context,
                                    camera,
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
                                context,
                                camera,
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

                    surfaceCount = addVisibleSurface(context, camera, surfaceCount,
                            boundaryDistance, perpendicularDistance, low,
                            high, textureX, wallTile);
                }
            }

            /*
             * -------------------------------------------------
             * SOLID TILE
             * -------------------------------------------------
             */

            boolean nextIsDoor =
                    next != null
//                            && next.door != null
                    ;

            if (next != null &&
                    next.solid
//                    && !nextIsDoor
            ) {

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
                                    context,
                                    camera,
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

                surfaceCount = addVisibleSurface(context, camera, surfaceCount,
                        boundaryDistance, perpendicularDistance, next.floor,
                        next.ceiling, textureX, next);

                /*
                 * Solid geometry terminates this ray.
                 */
                break;
            }

            /*
             * -------------------------------------------------
             * CLOSED DOOR (see-through frame pixels)
             * -------------------------------------------------
             *
             * Draw the door surface but keep traversing so
             * transparent parts reveal what is behind it.
             */

            if (nextIsDoor) {

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

                surfaceCount = addVisibleSurface(context, camera, surfaceCount,
                        boundaryDistance, perpendicularDistance, next.floor,
                        next.ceiling, textureX, next);
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
                    context,
                    camera,
                    screenX,
                    context.surfacePool[i],
                    rayDirX,
                    rayDirY,
                    depth,
                    lights,
                    ambient
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
                    context,
                    camera,
                    screenX,
                    rayDirX,
                    rayDirY,
                    rayAngle,
                    context.planePool[i],
                    depth,
                    lights,
                    ambient
            );
        }
    }

    /*
     * ---------------------------------------------------------
     * ADD NORMAL TILE PLANES
     * ---------------------------------------------------------
     */

    private int addTilePlanes(
            WorkerContext context,
            Camera3D camera,
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {
        if (tile == null || exitDistance <= entryDistance) {
            return planeCount;
        }
        RowSpan rows = context.rowSpan;
        boolean top = tile.floor > EPSILON;
        if (findPlaneRows(context, camera, tile.floor,
                entryDistance, exitDistance, rows)) {
            PixelGraphics texture = top ? getTopTextureSafe(tile)
                    : getFloorTextureSafe(tile);
            if (top && !validTexture(texture)) {
                texture = getFloorTextureSafe(tile);
            }
            if (validTexture(texture)) {
                if (planeCount < context.planePool.length) {
                    context.planePool[planeCount++].set(entryDistance,
                            exitDistance, tile.floor, tile, false, top,
                            rows.minY, rows.maxY);
                } else {
                    context.planePoolOverflows++;
                }
            }
        }
        if (findPlaneRows(context, camera, tile.ceiling,
                entryDistance, exitDistance, rows)
                && validTexture(getCeilingTextureSafe(tile))) {
            if (planeCount < context.planePool.length) {
                context.planePool[planeCount++].set(entryDistance,
                        exitDistance, tile.ceiling, tile, true, false,
                        rows.minY, rows.maxY);
            } else {
                context.planePoolOverflows++;
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
            WorkerContext context,
            Camera3D camera,
            Tile tile,
            float entryDistance,
            float exitDistance,
            int planeCount) {
        if (tile == null || exitDistance <= entryDistance) {
            return planeCount;
        }
        RowSpan rows = context.rowSpan;
        if (!findPlaneRows(context, camera, tile.ceiling,
                entryDistance, exitDistance, rows)) {
            return planeCount;
        }
        PixelGraphics texture = getTopTextureSafe(tile);
        if (!validTexture(texture)) {
            texture = getFloorTextureSafe(tile);
        }
        if (!validTexture(texture)) {
            return planeCount;
        }
        if (planeCount < context.planePool.length) {
            context.planePool[planeCount++].set(entryDistance, exitDistance,
                    tile.ceiling, tile, false, true, rows.minY, rows.maxY);
        } else {
            context.planePoolOverflows++;
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
            WorkerContext context,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float rayAngle,
            PlaneSurface plane,
            float[] depth,
            List<Light> lights,
            float ambient) {

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

        // Rows outside this cell's projected ray-distance interval cannot hit it.
        startY = Math.max(startY, plane.minY);
        endY = Math.min(endY, plane.maxY);
        if (startY > endY) {
            return;
        }

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
                    Mathf.clamp(
                            textureX,
                            0.0f,
                            0.999999f
                    );

            textureY =
                    Mathf.clamp(
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
                    Mathf.clamp(
                            texX,
                            0,
                            texture.width - 1
                    );

            texY =
                    Mathf.clamp(
                            texY,
                            0,
                            texture.height - 1
                    );

            /*
             * -------------------------------------------------
             * LIGHTING
             * -------------------------------------------------
             */

            int litColor =
                    applyLighting(
                            texture.getPixel(
                                    texX,
                                    texY
                            ),
                            worldX,
                            worldY,
                            plane.height,
                            lights,
                            ambient
                    );

            /*
             * -------------------------------------------------
             * WRITE PIXEL
             * -------------------------------------------------
             */

            context.writePixel(
                    screenX,
                    y,
                    litColor
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
            WorkerContext context,
            Camera3D camera,
            int screenX,
            Surface surface,
            float rayDirX,
            float rayDirY,
            float[] depth,
            List<Light> lights,
            float ambient) {

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
         * World-space base position of the wall hit.
         */
        float worldX =
                camera.position.x +
                        rayDirX *
                                surface.distance;

        float worldY =
                camera.position.y +
                        rayDirY *
                                surface.distance;

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
                Mathf.clamp(
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
                    Mathf.clamp(
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
                    Mathf   .clamp(
                            texY,
                            0,
                            texture.height - 1
                    );

            /*
             * -------------------------------------------------
             * TRANSPARENCY TEST
             * -------------------------------------------------
             */

            int texColor =
                    texture.getPixel(
                            texX,
                            texY
                    );

            if (!isOpaqueEntityPixel(texColor)) {
                continue;
            }

            /*
             * -------------------------------------------------
             * LIGHTING
             * -------------------------------------------------
             */

            float worldZ =
                    surface.high -
                            t *
                                    (
                                            surface.high -
                                                    surface.low
                                    );

            int litColor =
                    applyLighting(
                            texColor,
                            worldX,
                            worldY,
                            worldZ,
                            lights,
                            ambient
                    );

            /*
             * -------------------------------------------------
             * WRITE PIXEL
             * -------------------------------------------------
             */

            context.writePixel(
                    screenX,
                    y,
                    litColor
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
        renderLock.lock();
        try {
            return surfacePoolOverflows;
        } finally {
            renderLock.unlock();
        }
    }

    /**
     * Returns the number of times the plane pool was too small.
     *
     * A non-zero value means geometry was silently omitted.
     */
    public int getPlanePoolOverflows() {
        renderLock.lock();
        try {
            return planePoolOverflows;
        } finally {
            renderLock.unlock();
        }
    }

    /**
     * Clears pool-overflow diagnostics.
     */
    public void resetDiagnostics() {
        renderLock.lock();
        try {
            surfacePoolOverflows = 0;
            planePoolOverflows = 0;
        } finally {
            renderLock.unlock();
        }
    }

    /**
     * Returns the configured render distance.
     */
    public float getRenderDistance() {

        return renderDistance;
    }

    /**
     * Returns the number of reusable vertical-surface objects per worker.
     */
    public int getSurfacePoolSize() {

        return surfacePoolSize;
    }

    /**
     * Returns the number of reusable plane objects per worker.
     */
    public int getPlanePoolSize() {

        return planePoolSize;
    }
}
