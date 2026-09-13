package raycaster;

import berryngine.Color;
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
        SKY,

        // School / indoor materials
        PAINTED_WALL,
        PLASTER,
        DRYWALL,
        CINDER_BLOCK,
        TERRAZZO,
        VINYL_FLOOR,
        LINOLEUM,
        RUBBER_FLOOR,
        HARDWOOD_FLOOR,
        LAMINATED_DESK,
        FORMICA,
        CORK,
        CHALKBOARD,
        WHITEBOARD,
        LOCKER_METAL,
        STAINLESS_STEEL,
        PAINTED_METAL,
        CHAIN_LINK,
        CARPET,
        ACOUSTIC_TILE,
        PAPER,
        CARDBOARD,
        PLASTIC,
        PLYWOOD,
        MDF
    }

    /**
     * Generate a material using the supplied random state.
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

            case PAINTED_WALL:
                paintedWall(gfx, rng);
                break;

            case PLASTER:
                plaster(gfx, rng);
                break;

            case DRYWALL:
                drywall(gfx, rng);
                break;

            case CINDER_BLOCK:
                cinderBlock(gfx, rng);
                break;

            case TERRAZZO:
                terrazzo(gfx, rng);
                break;

            case VINYL_FLOOR:
                vinylFloor(gfx, rng);
                break;

            case LINOLEUM:
                linoleum(gfx, rng);
                break;

            case RUBBER_FLOOR:
                rubberFloor(gfx, rng);
                break;

            case HARDWOOD_FLOOR:
                hardwoodFloor(gfx, rng);
                break;

            case LAMINATED_DESK:
                laminatedDesk(gfx, rng);
                break;

            case FORMICA:
                formica(gfx, rng);
                break;

            case CORK:
                cork(gfx, rng);
                break;

            case CHALKBOARD:
                chalkboard(gfx, rng);
                break;

            case WHITEBOARD:
                whiteboard(gfx, rng);
                break;

            case LOCKER_METAL:
                lockerMetal(gfx, rng);
                break;

            case STAINLESS_STEEL:
                stainlessSteel(gfx, rng);
                break;

            case PAINTED_METAL:
                paintedMetal(gfx, rng);
                break;

            case CHAIN_LINK:
                chainLink(gfx, rng);
                break;

            case CARPET:
                carpet(gfx, rng);
                break;

            case ACOUSTIC_TILE:
                acousticTile(gfx, rng);
                break;

            case PAPER:
                paper(gfx, rng);
                break;

            case CARDBOARD:
                cardboard(gfx, rng);
                break;

            case PLASTIC:
                plastic(gfx, rng);
                break;

            case PLYWOOD:
                plywood(gfx, rng);
                break;

            case MDF:
                mdf(gfx, rng);
                break;
        }

        return gfx;
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
                        Color.fromRGB(35, 85, 30),
                        Color.fromRGB(65, 125, 42));

                int variation = rng.nextInt(-12, 13);

                int r = clamp(Color.getRed(base) + variation);
                int g = clamp(Color.getGreen(base) + variation);
                int b = clamp(Color.getBlue(base) + variation);

                gfx.pixels[y * w + x] = Color.fromRGB(r, g, b);

                if (rng.chance(0.035f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? Color.fromRGB(20, 65, 25)
                                    : Color.fromRGB(80, 135, 45);
                }
            }
        }

        addBlobs(gfx, rng, 15, 1, 4,
                Color.fromRGB(25, 70, 25),
                Color.fromRGB(90, 135, 45));
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

                gfx.pixels[y * w + x] = Color.fromRGB(r, g, b);

                if (rng.chance(0.04f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? Color.fromRGB(75, 48, 30)
                                    : Color.fromRGB(135, 90, 50);
                }
            }
        }

        addBlobs(gfx, rng, 12, 1, 5,
                Color.fromRGB(65, 42, 27),
                Color.fromRGB(145, 95, 50));
    }

    public static void sand(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-10, 11);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(218 + variation),
                                clamp(190 + variation),
                                clamp(125 + variation)
                        );

                if (rng.chance(0.025f)) {
                    gfx.pixels[y * w + x] =
                            rng.chance(0.5f)
                                    ? Color.fromRGB(190, 160, 100)
                                    : Color.fromRGB(235, 210, 150);
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
                        Color.fromRGB(
                                clamp(235 + variation),
                                clamp(240 + variation),
                                clamp(245 + variation)
                        );

                if (rng.chance(0.025f)) {
                    gfx.pixels[y * w + x] = Color.fromRGB(205, 215, 225);
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
                        Color.fromRGB(
                                clamp(125 + variation),
                                clamp(125 + variation),
                                clamp(120 + variation)
                        );
            }
        }

        addBlobs(gfx, rng, 20, 1, 2,
                Color.fromRGB(75, 75, 72),
                Color.fromRGB(175, 175, 165));
    }

    public static void rock(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-25, 26);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(90 + variation),
                                clamp(88 + variation),
                                clamp(82 + variation)
                        );
            }
        }

        for (int i = 0; i < 8; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);
            int length = rng.nextInt(2, Math.max(3, w / 2));

            for (int j = 0; j < length; j++) {
                if (x >= 0 && x < w && y >= 0 && y < h) {
                    gfx.setPixel(x, y, Color.fromRGB(55, 53, 50));
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

                gfx.pixels[y * w + x] = Color.fromRGB(r, g, b);
            }
        }

        for (int i = 0; i < h / 3; i++) {
            int y = rng.nextInt(h);
            int x = rng.nextInt(w);
            int length = rng.nextInt(2, Math.max(3, w / 3));

            for (int j = 0; j < length; j++) {
                if (x + j < w) {
                    gfx.setPixel(
                            x + j,
                            y,
                            Color.fromRGBA(150, 220, 230, rng.nextInt(50, 130))
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
                        Color.fromRGB(
                                clamp(125 + variation),
                                clamp(72 + variation / 2),
                                clamp(38 + variation / 3)
                        );
            }
        }

        for (int i = 0; i < 3; i++) {
            int cx = rng.nextInt(w);
            int cy = rng.nextInt(h);

            for (int y = -3; y <= 3; y++) {
                for (int x = -5; x <= 5; x++) {
                    if (x * x + y * y * 2 < 20) {
                        int px = cx + x;
                        int py = cy + y;

                        if (px >= 0 && px < w && py >= 0 && py < h) {
                            gfx.setPixel(px, py, Color.fromRGB(70, 38, 22));
                        }
                    }
                }
            }
        }
    }

    public static void bark(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(Color.fromRGB(65, 38, 22));

        for (int x = 0; x < w; x++) {
            int shade = rng.nextInt(-15, 16);

            for (int y = 0; y < h; y++) {
                if (rng.chance(0.8f)) {
                    gfx.setPixel(
                            x,
                            y,
                            Color.fromRGB(
                                    clamp(75 + shade),
                                    clamp(43 + shade),
                                    clamp(24 + shade)
                            )
                    );
                }
            }
        }

        for (int i = 0; i < w / 3; i++) {
            int x = rng.nextInt(w);

            for (int y = 0; y < h; y++) {
                if (rng.chance(0.65f)) {
                    gfx.setPixel(x, y, Color.fromRGB(35, 22, 15));
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
                        Color.fromRGB(
                                clamp(155 + variation),
                                clamp(155 + variation),
                                clamp(150 + variation)
                        );
            }
        }

        addBlobs(gfx, rng, 25, 1, 3,
                Color.fromRGB(110, 110, 106),
                Color.fromRGB(185, 185, 180));
    }

    public static void asphalt(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-18, 19);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(55 + variation),
                                clamp(57 + variation),
                                clamp(55 + variation)
                        );

                if (rng.chance(0.03f)) {
                    gfx.pixels[y * w + x] =
                            Color.fromRGB(30, 30, 29);
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

        gfx.clear(Color.fromRGB(180, 175, 165));

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

                int color = Color.fromRGB(
                        clamp(145 + variation),
                        clamp(55 + variation / 2),
                        clamp(40 + variation / 3)
                );

                gfx.fillRect(bx, by, bw, bh, color);

                if (bh > 2) {
                    gfx.drawHorizontalLine(
                            bx,
                            by,
                            bw,
                            Color.fromRGB(
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

        gfx.clear(Color.fromRGB(90, 90, 88));

        for (int y = 0; y < h; y += tileSize) {
            for (int x = 0; x < w; x += tileSize) {
                int variation = rng.nextInt(-10, 11);

                int color = Color.fromRGB(
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

        gfx.clear(Color.fromRGB(80, 65, 55));

        for (int y = 0; y < h; y += tileH) {
            int row = y / tileH;
            int offset = (row & 1) == 0 ? 0 : tileW / 2;

            for (int x = -offset; x < w; x += tileW) {
                int variation = rng.nextInt(-15, 16);

                int color = Color.fromRGB(
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

                gfx.drawHorizontalLine(
                        x + 1,
                        Math.min(h - 1, y + tileH - 1),
                        tileW - 2,
                        Color.fromRGB(65, 40, 30)
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
                        Color.fromRGB(
                                clamp(base + variation),
                                clamp(base + variation),
                                clamp(base + variation)
                        );
            }
        }

        for (int i = 0; i < 10; i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    rng.nextInt(w),
                    y,
                    rng.nextInt(2, Math.max(3, w / 2)),
                    Color.fromRGBA(240, 240, 240, rng.nextInt(30, 100))
            );
        }
    }

    public static void rustedMetal(PixelGraphics gfx, Random.State rng) {
        metal(gfx, rng);

        int w = gfx.width;
        int h = gfx.height;

        addBlobs(
                gfx,
                rng,
                18,
                1,
                Math.max(2, Math.min(w, h) / 3),
                Color.fromRGB(100, 45, 25),
                Color.fromRGB(180, 75, 35)
        );
    }

    public static void glass(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(Color.fromRGBA(100, 180, 210, 80));

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = rng.nextInt(50, 110);

                gfx.setPixel(
                        x,
                        y,
                        Color.fromRGBA(
                                110,
                                195,
                                220,
                                a
                        )
                );
            }
        }

        for (int i = 0; i < Math.max(1, w / 8); i++) {
            int x = rng.nextInt(w);

            gfx.drawLine(
                    x,
                    h,
                    Math.min(
                            w - 1,
                            x + rng.nextInt(2, Math.max(3, w / 3))
                    ),
                    0,
                    Color.fromRGBA(230, 250, 255, 100)
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
                        Color.fromRGB(
                                clamp(215 + variation),
                                clamp(205 + variation),
                                clamp(145 + variation)
                        );
            }
        }

        for (int i = 0; i < w * h / 15; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(x, y, Color.fromRGBA(80, 80, 70, 80));
        }
    }

    // -------------------------------------------------------------------------
    // School materials
    // -------------------------------------------------------------------------

    /**
     * Painted school wall.
     *
     * Mostly flat paint with subtle color variation, small scuffs,
     * dirt near random areas and occasional tiny imperfections.
     */
    public static void paintedWall(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(218 + variation),
                                clamp(218 + variation),
                                clamp(210 + variation)
                        );
            }
        }

        addBlobs(
                gfx,
                rng,
                12,
                1,
                Math.max(1, Math.min(w, h) / 12),
                Color.fromRGB(190, 188, 180),
                Color.fromRGB(230, 228, 220)
        );

        // Subtle scuff marks.
        for (int i = 0; i < Math.max(2, w / 10); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    x,
                    y,
                    rng.nextInt(2, Math.max(3, w / 5)),
                    Color.fromRGBA(120, 120, 115, rng.nextInt(15, 45))
            );
        }
    }

    /**
     * Rough plaster wall.
     */
    public static void plaster(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-12, 13);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(205 + variation),
                                clamp(202 + variation),
                                clamp(192 + variation)
                        );
            }
        }

        addBlobs(
                gfx,
                rng,
                35,
                1,
                2,
                Color.fromRGB(175, 172, 164),
                Color.fromRGB(225, 222, 212)
        );
    }

    /**
     * Drywall with subtle seams and paper texture.
     */
    public static void drywall(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-4, 5);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(215 + variation),
                                clamp(214 + variation),
                                clamp(208 + variation)
                        );
            }
        }

        // Drywall seam.
        if (w > 8) {
            int seamX = rng.nextInt(w);

            gfx.drawLine(
                    seamX,
                    0,
                    seamX,
                    h - 1,
                    Color.fromRGBA(160, 158, 152, 55)
            );
        }

        addBlobs(
                gfx,
                rng,
                10,
                1,
                2,
                Color.fromRGB(195, 193, 187),
                Color.fromRGB(225, 224, 218)
        );
    }

    /**
     * Concrete cinder blocks.
     */
    public static void cinderBlock(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        int blockW = Math.max(8, w / 3);
        int blockH = Math.max(6, h / 4);
        int mortar = 1;

        gfx.clear(Color.fromRGB(95, 94, 90));

        for (int y = 0; y < h; y += blockH) {
            int row = y / blockH;
            int offset = (row & 1) == 0 ? 0 : blockW / 2;

            for (int x = -offset; x < w; x += blockW) {
                int bx = x + mortar;
                int by = y + mortar;
                int bw = blockW - mortar * 2;
                int bh = blockH - mortar * 2;

                if (bw <= 0 || bh <= 0) {
                    continue;
                }

                int variation = rng.nextInt(-12, 13);

                gfx.fillRect(
                        bx,
                        by,
                        bw,
                        bh,
                        Color.fromRGB(
                                clamp(155 + variation),
                                clamp(155 + variation),
                                clamp(150 + variation)
                        )
                );

                // Cinder block pores.
                for (int i = 0; i < 3; i++) {
                    int px = bx + rng.nextInt(Math.max(1, bw));
                    int py = by + rng.nextInt(Math.max(1, bh));

                    gfx.setPixel(
                            px,
                            py,
                            Color.fromRGBA(80, 80, 77, rng.nextInt(30, 90))
                    );
                }
            }
        }
    }

    /**
     * Terrazzo floor.
     */
    public static void terrazzo(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(Color.fromRGB(175, 172, 160));

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (rng.chance(0.05f)) {
                    int shade = rng.nextInt(100, 190);

                    gfx.setPixel(
                            x,
                            y,
                            Color.fromRGB(
                                    shade,
                                    clamp(shade - 3),
                                    clamp(shade - 8)
                            )
                    );
                } else if (rng.chance(0.015f)) {
                    gfx.setPixel(
                            x,
                            y,
                            Color.fromRGB(
                                    rng.nextInt(110, 170),
                                    rng.nextInt(100, 160),
                                    rng.nextInt(90, 150)
                            )
                    );
                }
            }
        }

        addBlobs(
                gfx,
                rng,
                30,
                1,
                3,
                Color.fromRGB(115, 110, 100),
                Color.fromRGB(205, 200, 188)
        );
    }

    /**
     * Generic vinyl flooring.
     */
    public static void vinylFloor(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(145 + variation),
                                clamp(148 + variation),
                                clamp(138 + variation)
                        );
            }
        }

        // Slight directional wear.
        for (int i = 0; i < Math.max(2, h / 5); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGBA(80, 80, 75, rng.nextInt(8, 30))
            );
        }
    }

    /**
     * Old linoleum.
     */
    public static void linoleum(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-8, 9);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(170 + variation),
                                clamp(165 + variation),
                                clamp(145 + variation)
                        );
            }
        }

        // Old floor discoloration.
        addBlobs(
                gfx,
                rng,
                18,
                2,
                5,
                Color.fromRGB(145, 138, 120),
                Color.fromRGB(185, 180, 160)
        );
    }

    /**
     * Dark rubber gym / stair flooring.
     */
    public static void rubberFloor(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-10, 11);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(45 + variation),
                                clamp(45 + variation),
                                clamp(43 + variation)
                        );

                if (rng.chance(0.025f)) {
                    gfx.pixels[y * w + x] =
                            Color.fromRGB(75, 75, 72);
                }
            }
        }
    }

    /**
     * Hardwood school floor.
     */
    public static void hardwoodFloor(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        int plankH = Math.max(5, h / 5);

        for (int y = 0; y < h; y += plankH) {
            int variation = rng.nextInt(-10, 11);

            int color = Color.fromRGB(
                    clamp(145 + variation),
                    clamp(88 + variation),
                    clamp(48 + variation)
            );

            gfx.fillRect(
                    0,
                    y,
                    w,
                    Math.min(plankH - 1, h - y),
                    color
            );

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGB(75, 45, 25)
            );

            // Occasional plank seam.
            if (w > 8) {
                int seamX = rng.nextInt(w);

                gfx.drawVLine(
                        seamX,
                        y,
                        Math.min(plankH, h - y),
                        Color.fromRGBA(75, 45, 25, 120)
                );
            }
        }

        // Fine grain.
        for (int i = 0; i < h * 2; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(220, 155, 90, rng.nextInt(15, 50))
            );
        }
    }

    /**
     * Laminated school desk surface.
     */
    public static void laminatedDesk(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-4, 5);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(185 + variation),
                                clamp(155 + variation),
                                clamp(105 + variation)
                        );
            }
        }

        // Plastic laminate grain.
        for (int i = 0; i < Math.max(2, w / 8); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGBA(100, 75, 45, rng.nextInt(10, 35))
            );
        }

        // Scratches.
        for (int i = 0; i < Math.max(1, w / 20); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    x,
                    y,
                    rng.nextInt(2, Math.max(3, w / 6)),
                    Color.fromRGBA(70, 60, 50, rng.nextInt(20, 60))
            );
        }
    }

    /**
     * Formica countertop / desk material.
     */
    public static void formica(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-3, 4);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(190 + variation),
                                clamp(190 + variation),
                                clamp(185 + variation)
                        );
            }
        }

        // Fine speckled laminate.
        for (int i = 0; i < w * h / 20; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            int shade = rng.nextInt(145, 210);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGB(shade, shade, shade)
            );
        }
    }

    /**
     * Cork bulletin board.
     */
    public static void cork(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-18, 19);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(170 + variation),
                                clamp(115 + variation),
                                clamp(65 + variation)
                        );
            }
        }

        addBlobs(
                gfx,
                rng,
                35,
                1,
                2,
                Color.fromRGB(125, 80, 45),
                Color.fromRGB(205, 145, 85)
        );
    }

    /**
     * Chalkboard.
     */
    public static void chalkboard(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(38 + variation),
                                clamp(65 + variation),
                                clamp(52 + variation)
                        );
            }
        }

        // Chalk dust / erased marks.
        for (int i = 0; i < Math.max(5, w * h / 30); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(
                            210,
                            215,
                            205,
                            rng.nextInt(15, 60)
                    )
            );
        }

        // Long erased streaks.
        for (int i = 0; i < Math.max(1, h / 10); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    rng.nextInt(Math.max(1, w / 4)),
                    y,
                    rng.nextInt(
                            Math.max(2, w / 5),
                            Math.max(3, w)
                    ),
                    Color.fromRGBA(220, 225, 215, 20)
            );
        }
    }

    /**
     * Whiteboard.
     */
    public static void whiteboard(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-3, 4);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(235 + variation),
                                clamp(237 + variation),
                                clamp(232 + variation)
                        );
            }
        }

        // Ghosted marker stains.
        addBlobs(
                gfx,
                rng,
                10,
                1,
                Math.max(1, Math.min(w, h) / 10),
                Color.fromRGB(190, 195, 190),
                Color.fromRGB(220, 220, 215)
        );

        // Fine scratches.
        for (int i = 0; i < Math.max(1, w / 20); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    rng.nextInt(w),
                    y,
                    rng.nextInt(2, Math.max(3, w / 4)),
                    Color.fromRGBA(100, 100, 95, 30)
            );
        }
    }

    /**
     * Painted school locker metal.
     */
    public static void lockerMetal(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-6, 7);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(85 + variation),
                                clamp(105 + variation),
                                clamp(115 + variation)
                        );
            }
        }

        // Vertical locker ridges.
        int ridgeSpacing = Math.max(5, w / 5);

        for (int x = 0; x < w; x += ridgeSpacing) {
            gfx.drawVLine(
                    x,
                    0,
                    h,
                    Color.fromRGBA(40, 50, 55, 90)
            );

            if (x + 1 < w) {
                gfx.drawVLine(
                        x + 1,
                        0,
                        h,
                        Color.fromRGBA(220, 225, 225, 45)
                );
            }
        }

        // Scratches / dents.
        for (int i = 0; i < Math.max(2, w / 12); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    x,
                    y,
                    rng.nextInt(2, Math.max(3, w / 5)),
                    Color.fromRGBA(230, 230, 230, rng.nextInt(25, 80))
            );
        }
    }

    /**
     * Stainless steel.
     */
    public static void stainlessSteel(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            float gradient =
                    h <= 1
                            ? 0.0f
                            : (float) y / (h - 1);

            int base = 165 + (int) (gradient * 35);

            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-6, 7);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(base + variation),
                                clamp(base + variation),
                                clamp(base + variation)
                        );
            }
        }

        // Brushed-metal streaks.
        for (int i = 0; i < Math.max(3, h / 3); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGBA(255, 255, 255, rng.nextInt(15, 45))
            );
        }
    }

    /**
     * Painted steel.
     */
    public static void paintedMetal(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(70 + variation),
                                clamp(80 + variation),
                                clamp(90 + variation)
                        );
            }
        }

        addBlobs(
                gfx,
                rng,
                12,
                1,
                3,
                Color.fromRGB(55, 60, 65),
                Color.fromRGB(100, 105, 110)
        );

        // Tiny chips revealing metal underneath.
        for (int i = 0; i < Math.max(2, w / 12); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(190, 195, 195, rng.nextInt(40, 100))
            );
        }
    }

    /**
     * Chain-link fence.
     */
    public static void chainLink(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(Color.fromRGBA(55, 60, 58, 255));

        int spacing = Math.max(5, Math.min(w, h) / 3);

        // Diagonal wire in one direction.
        for (int offset = -h; offset < w; offset += spacing) {
            gfx.drawLine(
                    offset,
                    0,
                    offset + h,
                    h - 1,
                    Color.fromRGBA(155, 160, 155, 210)
            );
        }

        // Diagonal wire in the other direction.
        for (int offset = 0; offset < w + h; offset += spacing) {
            gfx.drawLine(
                    offset,
                    0,
                    offset - h,
                    h - 1,
                    Color.fromRGBA(100, 105, 100, 210)
            );
        }
    }

    /**
     * School carpet.
     */
    public static void carpet(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-12, 13);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(75 + variation),
                                clamp(80 + variation),
                                clamp(75 + variation)
                        );
            }
        }

        // Fibers / tiny color variation.
        for (int i = 0; i < w * h / 3; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            int base = rng.chance(0.5f) ? 55 : 100;

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(
                            base + rng.nextInt(-5, 6),
                            base + rng.nextInt(-5, 6),
                            base + rng.nextInt(-5, 6),
                            rng.nextInt(80, 180)
                    )
            );
        }
    }

    /**
     * Suspended acoustic ceiling tile.
     */
    public static void acousticTile(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        gfx.clear(Color.fromRGB(75, 75, 72));

        int border = Math.max(1, Math.min(w, h) / 16);

        gfx.fillRect(
                border,
                border,
                Math.max(1, w - border * 2),
                Math.max(1, h - border * 2),
                Color.fromRGB(190, 190, 185)
        );

        // Tiny pores.
        for (int i = 0; i < w * h / 10; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(
                            rng.nextInt(150, 215),
                            rng.nextInt(150, 215),
                            rng.nextInt(145, 210),
                            rng.nextInt(40, 120)
                    )
            );
        }

        // Tile edge.
        gfx.drawHorizontalLine(
                0,
                0,
                w,
                Color.fromRGB(120, 120, 115)
        );

        gfx.drawVLine(
                0,
                0,
                h,
                Color.fromRGB(120, 120, 115)
        );
    }

    /**
     * Paper.
     */
    public static void paper(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-2, 3);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(242 + variation),
                                clamp(242 + variation),
                                clamp(235 + variation)
                        );
            }
        }

        // Very subtle paper fibers.
        for (int i = 0; i < w * h / 12; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(180, 180, 175, rng.nextInt(10, 35))
            );
        }
    }

    /**
     * Cardboard.
     */
    public static void cardboard(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            int variation = rng.nextInt(-7, 8);

            for (int x = 0; x < w; x++) {
                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(165 + variation),
                                clamp(120 + variation),
                                clamp(70 + variation)
                        );
            }
        }

        // Corrugated-looking horizontal fibers.
        for (int y = 0; y < h; y++) {
            if (rng.chance(0.45f)) {
                gfx.drawHorizontalLine(
                        0,
                        y,
                        w,
                        Color.fromRGBA(110, 75, 40, rng.nextInt(15, 45))
                );
            }
        }
    }

    /**
     * Generic hard plastic.
     */
    public static void plastic(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            float gradient =
                    h <= 1
                            ? 0.0f
                            : (float) y / (h - 1);

            int base = 175 + (int) (gradient * 20);

            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-3, 4);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(base + variation),
                                clamp(base + variation),
                                clamp(base + variation)
                        );
            }
        }

        // Plastic highlight.
        if (h > 2) {
            int y = rng.nextInt(Math.max(1, h / 3));

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGBA(255, 255, 255, 60)
            );
        }

        // Small scratches.
        for (int i = 0; i < Math.max(1, w / 15); i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    x,
                    y,
                    rng.nextInt(1, Math.max(2, w / 6)),
                    Color.fromRGBA(100, 100, 100, rng.nextInt(15, 50))
            );
        }
    }

    /**
     * Plywood.
     */
    public static void plywood(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            float grain =
                    (float) Math.sin(
                            y * 0.35 +
                                    Math.sin(y * 0.08) * 2.5
                    );

            for (int x = 0; x < w; x++) {
                int variation =
                        (int) (grain * 12.0f) +
                                rng.nextInt(-4, 5);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(175 + variation),
                                clamp(120 + variation / 2),
                                clamp(65 + variation / 3)
                        );
            }
        }

        // Layered plywood edge lines.
        for (int i = 0; i < Math.max(2, h / 8); i++) {
            int y = rng.nextInt(h);

            gfx.drawHorizontalLine(
                    0,
                    y,
                    w,
                    Color.fromRGBA(90, 55, 30, rng.nextInt(20, 60))
            );
        }
    }

    /**
     * MDF.
     */
    public static void mdf(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-5, 6);

                gfx.pixels[y * w + x] =
                        Color.fromRGB(
                                clamp(155 + variation),
                                clamp(125 + variation),
                                clamp(90 + variation)
                        );
            }
        }

        // Compressed wood fibers.
        for (int i = 0; i < w * h / 25; i++) {
            int x = rng.nextInt(w);
            int y = rng.nextInt(h);

            int shade = rng.nextInt(115, 175);

            gfx.setPixel(
                    x,
                    y,
                    Color.fromRGBA(shade, shade - 20, shade - 45, 80)
            );
        }
    }

    // -------------------------------------------------------------------------
    // Sky
    // -------------------------------------------------------------------------

    public static void sky(PixelGraphics gfx, Random.State rng) {
        int w = gfx.width;
        int h = gfx.height;

        if (w <= 0 || h <= 0) {
            return;
        }

        for (int y = 0; y < h; y++) {
            float t = h <= 1
                    ? 0.0f
                    : (float) y / (h - 1);

            float smooth = t * t * (3.0f - 2.0f * t);

            int r = (int) (35 + smooth * 85);
            int g = (int) (105 + smooth * 90);
            int b = (int) (190 + smooth * 55);

            for (int x = 0; x < w; x++) {
                int variation = rng.nextInt(-3, 4);

                gfx.pixels[y * w + x] = Color.fromRGB(
                        clamp(r + variation),
                        clamp(g + variation),
                        clamp(b + variation)
                );
            }
        }

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

                    float distance = nx * nx + ny * ny;
                    int alpha = (int) ((1.0f - distance) * 55.0f);

                    if (alpha > 0) {
                        gfx.blendPixel(
                                px,
                                py,
                                Color.fromRGBA(245, 248, 255, alpha)
                        );
                    }
                }
            }
        }

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
                        Color.fromRGBA(240, 247, 255, alpha)
                );
            }
        }
    }

    // -------------------------------------------------------------------------
    // Utility functions
    // -------------------------------------------------------------------------

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

        int r1 = Color.getRed(color1);
        int g1 = Color.getGreen(color1);
        int b1 = Color.getBlue(color1);

        int r2 = Color.getRed(color2);
        int g2 = Color.getGreen(color2);
        int b2 = Color.getBlue(color2);

        return Color.fromRGB(
                rng.nextInt(
                        Math.min(r1, r2),
                        Math.max(r1, r2) + 1
                ),
                rng.nextInt(
                        Math.min(g1, g2),
                        Math.max(g1, g2) + 1
                ),
                rng.nextInt(
                        Math.min(b1, b2),
                        Math.max(b1, b2) + 1
                )
        );
    }
    
    
    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}