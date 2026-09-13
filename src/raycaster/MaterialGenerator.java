package raycaster;
import berryngine.Random;
import berryngine.PixelGraphics;
/**
 * Procedural material/texture generator for Berryngine.
 *
 * Uses:
 *   - berryngine.Random
 *   - berryngine.PixelGraphics
 *
 * Colors are stored as ARGB ints:
 *   0xAARRGGBB
 *
 * All generators return a PixelGraphics texture.
 */
public final class MaterialGenerator {

    private MaterialGenerator() {
    }

    // -------------------------------------------------------------------------
    // Public material types
    // -------------------------------------------------------------------------

    public enum Material {
        GRASS,
        DIRT,
        SAND,
        SNOW,
        STONE,
        ROCK,
        WATER,
        WOOD,
        BARK,

        CONCRETE,
        ASPHALT,
        BRICK,
        TILE,
        ROOF_TILE,
        METAL,
        RUSTED_METAL,
        GLASS,
        ROAD_MARKING,
        SKY
    }

    /**
     * Generate a material using the supplied seed.
     */
    public static PixelGraphics generate(
            Material material,
            int width,
            int height,
            Random.State rng) {

        PixelGraphics gfx = new PixelGraphics(width, height);

        switch (material) {
            case GRASS:
                grass(gfx, rng);
                break;

            case DIRT:
                dirt(gfx, rng);
                break;

            case SAND:
                sand(gfx, rng);
                break;

            case SNOW:
                snow(gfx, rng);
                break;

            case STONE:
                stone(gfx, rng);
                break;

            case ROCK:
                rock(gfx, rng);
                break;

            case WATER:
                water(gfx, rng);
                break;

            case WOOD:
                wood(gfx, rng);
                break;

            case BARK:
                bark(gfx, rng);
                break;

            case CONCRETE:
                concrete(gfx, rng);
                break;

            case ASPHALT:
                asphalt(gfx, rng);
                break;

            case BRICK:
                brick(gfx, rng);
                break;

            case TILE:
                tile(gfx, rng);
                break;

            case ROOF_TILE:
                roofTile(gfx, rng);
                break;

            case METAL:
                metal(gfx, rng);
                break;

            case RUSTED_METAL:
                rustedMetal(gfx, rng);
                break;

            case GLASS:
                glass(gfx, rng);
                break;

            case ROAD_MARKING:
                roadMarking(gfx, rng);
                break;

            case SKY:
                sky(gfx, rng);
                break;
        }

        return gfx;
    }

    public static void sky(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        if (w <= 0 || h <= 0) {
            return;
        }

        for (int y = 0; y < h; y++) {

            // Darker/deeper blue at the top, lighter near the horizon.
            float t = h <= 1
                    ? 0.0f
                    : (float) y / (h - 1);

            // Smooth atmospheric gradient.
            float smooth = t * t * (3.0f - 2.0f * t);

            int r = (int) (35 + smooth * 85);
            int g = (int) (105 + smooth * 90);
            int b = (int) (190 + smooth * 55);

            for (int x = 0; x < w; x++) {

                // Small per-pixel variation keeps the sky from looking flat.
                int variation = rng.nextInt(-3, 4);

                gfx.pixels[y * w + x] = rgb(
                        clamp(r + variation),
                        clamp(g + variation),
                        clamp(b + variation)
                );
            }
        }

        // Soft cloud patches.
        int cloudCount = Math.max(2, (w * h) / 700);

        for (int i = 0; i < cloudCount; i++) {
            int cx = rng.nextInt(w);
            int cy = rng.nextInt(Math.max(1, h / 2));

            int radiusX = rng.nextInt(
                    Math.max(2, w / 12),
                    Math.max(3, w / 5)
            );

            int radiusY = rng.nextInt(
                    Math.max(1, h / 20),
                    Math.max(2, h / 9)
            );

            for (int y = -radiusY; y <= radiusY; y++) {
                for (int x = -radiusX; x <= radiusX; x++) {

                    float nx = (float) x / radiusX;
                    float ny = (float) y / radiusY;

                    if (nx * nx + ny * ny > 1.0f) {
                        continue;
                    }

                    int px = cx + x;
                    int py = cy + y;

                    if (px < 0 || px >= w || py < 0 || py >= h) {
                        continue;
                    }

                    // Clouds are softer around the edges.
                    float distance = nx * nx + ny * ny;
                    int alpha = (int) ((1.0f - distance) * 55.0f);

                    if (alpha > 0) {
                        gfx.blendPixel(
                                px,
                                py,
                                rgba(245, 248, 255, alpha)
                        );
                    }
                }
            }
        }

        // A few thin high-altitude cloud streaks.
        int streaks = Math.max(1, w / 40);

        for (int i = 0; i < streaks; i++) {
            int y = rng.nextInt(Math.max(1, h / 2));
            int x = rng.nextInt(w);
            int length = rng.nextInt(
                    Math.max(2, w / 10),
                    Math.max(3, w / 2)
            );

            for (int j = 0; j < length && x + j < w; j++) {
                int alpha = rng.nextInt(8, 30);

                gfx.blendPixel(
                        x + j,
                        y,
                        rgba(240, 247, 255, alpha)
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Natural materials
    // -------------------------------------------------------------------------

    public static void grass(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                int base = randomBetween(rng,
                        rgb(35, 85, 30),
                        rgb(65, 125, 42));

                // Fine grass variation.
                int variation = rng.nextInt(-12, 13);

                int r = clamp(red(base) + variation);
                int g = clamp(green(base) + variation);
                int b = clamp(blue(base) + variation);

                gfx.pixels[y * w + x] = rgb(r, g, b);

                // Occasional darker blades / spots.
                if (rng.chance(0.035f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? rgb(20, 65, 25)
                                    : rgb(80, 135, 45);
                }
            }
        }

        // Small organic patches.
        addBlobs(gfx, rng, 15, 1, 4,
                rgb(25, 70, 25),
                rgb(90, 135, 45));
    }

    public static void dirt(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-15, 16);

                int r = clamp(105 + variation);
                int g = clamp(70 + variation);
                int b = clamp(42 + variation);

                gfx.pixels[y * w + x] = rgb(r, g, b);

                if (rng.chance(0.04f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? rgb(75, 48, 30)
                                    : rgb(135, 90, 50);
                }
            }
        }

        addBlobs(gfx, rng, 12, 1, 5,
                rgb(65, 42, 27),
                rgb(145, 95, 50));
    }

    public static void sand(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-10, 11);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(218 + variation),
                                clamp(190 + variation),
                                clamp(125 + variation)
                        );

                if (rng.chance(0.025f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? rgb(190, 160, 100)
                                    : rgb(235, 210, 150);
                }
            }
        }
    }

    public static void snow(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-7, 8);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(235 + variation),
                                clamp(240 + variation),
                                clamp(245 + variation)
                        );

                if (rng.chance(0.025f)) {
                    gfx.pixels[y * w + x] = rgb(205, 215, 225);
                }
            }
        }
    }

    public static void stone(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-18, 19);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(125 + variation),
                                clamp(125 + variation),
                                clamp(120 + variation)
                        );
            }
        }

        // Random mineral flecks.
        addBlobs(gfx, rng, 20, 1, 2,
                rgb(75, 75, 72),
                rgb(175, 175, 165));
    }

    public static void rock(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-25, 26);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(90 + variation),
                                clamp(88 + variation),
                                clamp(82 + variation)
                        );
            }
        }

        // Cracks.
        for (int i = 0; i < 8; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            int length = rng.nextInt(2, Math.max(3, w / 2));

            for (int j = 0; j < length; j++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    gfx.setPixel(x, y, rgb(55, 53, 50));
                }

                x += rng.nextInt(-1, 2);
                y += rng.nextInt(0, 2);
            }
        }
    }

    public static void water(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            float wave = (float) Math.sin(y * 0.65);

            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-6, 7);

                int r = clamp(25 + variation);
                int g = clamp(105 + (int) (wave * 8) + variation);
                int b = clamp(170 + (int) (wave * 10) + variation);

                gfx.pixels[y * w + x] = rgb(r, g, b);
            }
        }

        // Horizontal highlights.
        for (int i = 0; i < h / 3; i++) {
            int y = rng.nextInt(h);
            int x = rng.nextInt(w);
            int length = rng.nextInt(2, Math.max(3, w / 3));

            for (int j = 0; j < length; j++) {
                if (x + j < w) {
                    gfx.setPixel(
                            x + j,
                            y,
                            rgba(150, 220, 230, rng.nextInt(50, 130))
                    );
                }
            }
        }
    }

    public static void wood(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {

            float grain =
                    (float) Math.sin(
                            y * 0.45 +
                                    Math.sin(y * 0.12) * 2.0
                    );

            for (int x = 0; x < w; x++) {
                int variation =
                        (int) (grain * 15.0f) +
                                rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(125 + variation),
                                clamp(72 + variation / 2),
                                clamp(38 + variation / 3)
                        );
            }
        }

        // Knots.
        for (int i = 0; i < 3; i++) {
            int cx = rng.nextInt(w);
            int cy = rng.nextInt(h);

            for (int y = -3; y <= 3; y++) {
                for (int x = -5; x <= 5; x++) {
                    if (x * x + y * y * 2 < 20) {
                        int px = cx + x;
                        int py = cy + y;

                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            gfx.setPixel(px, py, rgb(70, 38, 22));
                        }
                    }
                }
            }
        }
    }

    public static void bark(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(rgb(65, 38, 22));

        for (int x = 0; x < w; x++) {
            int shade = rng.nextInt(-15, 16);

            for (int y = 0; y < h; y++) {
                if (rng.chance(0.8f)) {
                    gfx.setPixel(
                            x,
                            y,
                            rgb(
                                    clamp(75 + shade),
                                    clamp(43 + shade),
                                    clamp(24 + shade)
                            )
                    );
                }
            }
        }

        // Vertical bark cracks.
        for (int i = 0; i < w / 3; i++) {
            int x = rng.nextInt(w);

            for (int y = 0; y < h; y++) {
                if (rng.chance(0.65f)) {
                    gfx.setPixel(x, y, rgb(35, 22, 15));
                }

                x += rng.nextInt(-1, 2);
                x = Math.max(0, Math.min(w - 1, x));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Urban materials
    // -------------------------------------------------------------------------

    public static void concrete(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {

                int variation = rng.nextInt(-14, 15);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(155 + variation),
                                clamp(155 + variation),
                                clamp(150 + variation)
                        );
            }
        }

        // Pores / stains.
        addBlobs(gfx, rng, 25, 1, 3,
                rgb(110, 110, 106),
                rgb(185, 185, 180));
    }

    public static void asphalt(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-18, 19);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(55 + variation),
                                clamp(57 + variation),
                                clamp(55 + variation)
                        );

                if (rng.chance(0.03f)) {
                    gfx.pixels[y * w + x] =
                            rgb(30, 30, 29);
                }
            }
        }
    }

    public static void brick(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        int brickW = Math.max(4, w / 3);
        int brickH = Math.max(3, h / 4);
        int mortar = Math.max(1, Math.min(2, brickW / 6));

        gfx.clear(rgb(180, 175, 165));

        for (int y = 0; y < h; y += brickH) {
            int row = y / brickH;
            int offset = (row & 1) == 0 ? 0 : brickW / 2;

            for (int x = -offset; x < w; x += brickW) {

                int bx = x + mortar;
                int by = y + mortar;

                int bw = brickW - mortar * 2;
                int bh = brickH - mortar * 2;

                if (bw <= 0 || bh <= 0) {
                    continue;
                }

                int variation = rng.nextInt(-18, 19);

                int color = rgb(
                        clamp(145 + variation),
                        clamp(55 + variation / 2),
                        clamp(40 + variation / 3)
                );

                gfx.fillRect(bx, by, bw, bh, color);

                // Small brick highlight.
                if (bh > 2) {
                    gfx.drawHorizontalLine(
                            bx,
                            by,
                            bw,
                            rgb(
                                    clamp(170 + variation),
                                    clamp(70 + variation),
                                    clamp(50 + variation)
                            )
                    );
                }
            }
        }
    }

    public static void tile(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        int tileSize = Math.max(4, Math.min(w, h) / 3);
        int grout = 1;

        gfx.clear(rgb(90, 90, 88));

        for (int y = 0; y < h; y += tileSize) {
            for (int x = 0; x < w; x += tileSize) {

                int variation = rng.nextInt(-10, 11);

                int color = rgb(
                        clamp(180 + variation),
                        clamp(180 + variation),
                        clamp(175 + variation)
                );

                gfx.fillRect(
                        x + grout,
                        y + grout,
                        tileSize - grout * 2,
                        tileSize - grout * 2,
                        color
                );
            }
        }
    }

    public static void roofTile(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        int tileW = Math.max(4, w / 4);
        int tileH = Math.max(3, h / 3);

        gfx.clear(rgb(80, 65, 55));

        for (int y = 0; y < h; y += tileH) {
            int row = y / tileH;
            int offset = (row & 1) == 0 ? 0 : tileW / 2;

            for (int x = -offset; x < w; x += tileW) {
                int variation = rng.nextInt(-15, 16);

                int color = rgb(
                        clamp(130 + variation),
                        clamp(70 + variation),
                        clamp(45 + variation)
                );

                gfx.fillRect(
                        x + 1,
                        y + 1,
                        tileW - 2,
                        tileH - 1,
                        color
                );

                // Lower shadow.
                gfx.drawHorizontalLine(
                        x + 1,
                        Math.min(h - 1, y + tileH - 1),
                        tileW - 2,
                        rgb(65, 40, 30)
                );
            }
        }
    }

    public static void metal(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            float gradient = h <= 1 ? 0 : (float) y / (h - 1);

            int base = 120 + (int) (gradient * 45);

            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-8, 9);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(base + variation),
                                clamp(base + variation),
                                clamp(base + variation)
                        );
            }
        }

        // Fine scratches.
        for (int i = 0; i < 10; i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    rng.nextInt(w),
                    y,
                    rng.nextInt(2, Math.max(3, w / 2)),
                    rgba(240, 240, 240, rng.nextInt(30, 100))
            );
        }
    }

    public static void rustedMetal(PixelGraphics gfx, Random.State rng) {
        metal(gfx, rng);

        int w = gfx.width;
        int h = gfx.height;

        // Rust patches.
        addBlobs(
                gfx,
                rng,
                18,
                1,
                Math.max(2, Math.min(w, h) / 3),
                rgb(100, 45, 25),
                rgb(180, 75, 35)
        );
    }

    public static void glass(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(rgba(100, 180, 210, 80));

        // Vertical blue tint.
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = rng.nextInt(50, 110);

                gfx.setPixel(
                        x,
                        y,
                        rgba(
                                110,
                                195,
                                220,
                                a
                        )
                );
            }
        }

        // Reflections.
        for (int i = 0; i < Math.max(1, w / 8); i++) {
            int x = rng.nextInt(w);

            gfx.drawLine(
                    x,
                    h,
                    Math.min(w - 1, x + rng.nextInt(2, Math.max(3, w / 3))),
                    0,
                    rgba(230, 250, 255, 100)
            );
        }
    }

    public static void roadMarking(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-8, 9);

                gfx.pixels[y * w + x] =
                        rgb(
                                clamp(215 + variation),
                                clamp(205 + variation),
                                clamp(145 + variation)
                        );
            }
        }

        // Dirty/worn areas.
        for (int i = 0; i < w * h / 15; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(x, y, rgba(80, 80, 70, 80));
        }
    }

    // -------------------------------------------------------------------------
    // Utility functions
    // -------------------------------------------------------------------------

    /**
     * Adds random circular-ish patches to a texture.
     */
    private static void addBlobs(
            PixelGraphics gfx,
            Random.State rng,
            int count,
            int minRadius,
            int maxRadius,
            int color1,
            int color2) {

        if (gfx.width <= 0 || gfx.height <= 0) {
            return;
        }

        for (int i = 0; i < count; i++) {

            int cx = rng.nextInt(gfx.width);
            int cy = rng.nextInt(gfx.height);

            int radius = rng.nextInt(
                    minRadius,
                    Math.max(minRadius + 1, maxRadius + 1)
            );

            int color = rng.chance(0.5f) ? color1 : color2;

            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {

                    if (x * x + y * y > radius * radius) {
                        continue;
                    }

                    int px = cx + x;
                    int py = cy + y;

                    if (px < 0 || px >= gfx.width ||
                            py < 0 || py >= gfx.height) {
                        continue;
                    }

                    // Low alpha makes patches blend naturally.
                    int alpha = rng.nextInt(30, 100);

                    int c =
                            (color & 0x00FFFFFF) |
                                    (alpha << 24);

                    gfx.blendPixel(px, py, c);
                }
            }
        }
    }

    private static int randomBetween(
            Random.State rng,
            int color1,
            int color2) {

        int r1 = red(color1);
        int g1 = green(color1);
        int b1 = blue(color1);

        int r2 = red(color2);
        int g2 = green(color2);
        int b2 = blue(color2);

        return rgb(
                rng.nextInt(Math.min(r1, r2), Math.max(r1, r2) + 1),
                rng.nextInt(Math.min(g1, g2), Math.max(g1, g2) + 1),
                rng.nextInt(Math.min(b1, b2), Math.max(b1, b2) + 1)
        );
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 |
                (clamp(r) << 16) |
                (clamp(g) << 8) |
                clamp(b);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (clamp(a) << 24) |
                (clamp(r) << 16) |
                (clamp(g) << 8) |
                clamp(b);
    }

    private static int red(int color) {
        return color >> 16 & 255;
    }

    private static int green(int color) {
        return color >> 8 & 255;
    }

    private static int blue(int color) {
        return color & 255;
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
