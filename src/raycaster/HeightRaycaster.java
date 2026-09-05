package raycaster;

import berryngine.PixelGraphics;
import berryngine.Mathf;

public final class HeightRaycaster {

    private final TileMap map;

    private final int width;
    private final int height;

    private final float focalLength;

    public HeightRaycaster(
            TileMap map,
            int width,
            int height,
            float fov) {

        this.map = map;
        this.width = width;
        this.height = height;

        focalLength =
                (width * 0.5f) /
                        Mathf.tan(fov * 0.5f);
    }

    public void render(
            PixelGraphics pg,
            Camera3D camera) {

        for (int screenX = 0; screenX < width; screenX++) {
            castColumn(pg, camera, screenX);
        }
    }

    private void castColumn(
            PixelGraphics pg,
            Camera3D camera,
            int screenX) {

        float cameraX =
                2.0f * screenX / width - 1.0f;

        float rayAngle =
                camera.rotation.y +
                        (float) Math.atan(cameraX *
                                Mathf.tan(camera.fov * 0.5f));

        float rayDirX = Mathf.cos(rayAngle);
        float rayDirY = Mathf.sin(rayAngle);

        castDDA(
  pg,
                camera,
                screenX,
                rayDirX,
                rayDirY
        );
    }

    private void castDDA(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY) {

        float rayX = camera.position.x;
        float rayY = camera.position.y;

        int mapX = (int) Math.floor(rayX);
        int mapY = (int) Math.floor(rayY);

        float deltaDistX =
                Math.abs(1.0f / rayDirX);

        float deltaDistY =
                Math.abs(1.0f / rayDirY);

        int stepX;
        int stepY;

        float sideDistX;
        float sideDistY;

        if (rayDirX < 0.0f) {
            stepX = -1;
            sideDistX =
                    (rayX - mapX) * deltaDistX;
        } else {
            stepX = 1;
            sideDistX =
                    (mapX + 1.0f - rayX) * deltaDistX;
        }

        if (rayDirY < 0.0f) {
            stepY = -1;
            sideDistY =
                    (rayY - mapY) * deltaDistY;
        } else {
            stepY = 1;
            sideDistY =
                    (mapY + 1.0f - rayY) * deltaDistY;
        }

        Tile previous = null;
        Tile current = map.get(mapX, mapY);

        float distance = 0.0f;

        int side = 0;

        while (true) {

            /*
             * Find the next grid boundary.
             */
            if (sideDistX < sideDistY) {
                distance = sideDistX;
                sideDistX += deltaDistX;
                mapX += stepX;
                side = 0;
            } else {
                distance = sideDistY;
                sideDistY += deltaDistY;
                mapY += stepY;
                side = 1;
            }

            /*
             * Ray has left the map.
             */
            if (!map.inBounds(mapX, mapY)) {
                break;
            }

            previous = current;
            current = map.get(mapX, mapY);

            /*
             * A height transition between two tiles
             * creates a vertical wall.
             */
            if (previous != null && current != null) {

                if (previous.floor != current.floor) {

                    float low =
                            Math.min(previous.floor, current.floor);

                    float high =
                            Math.max(previous.floor, current.floor);

                    renderVerticalSurface(
                            pg,
                            camera,
                            screenX,
                            rayDirX,
                            rayDirY,
                            distance,
                            low,
                            high,
                            previous,
                            side
                    );
                }
            }

            /*
             * A solid tile terminates the ray.
             */
            if (current.solid) {

                renderWall(
                        pg,
                        camera,
                        screenX,
                        rayDirX,
                        rayDirY,
                        distance,
                        current,
                        side
                );

                break;
            }
        }
    }

    private void renderWall(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float distance,
            Tile tile,
            int side) {

        float wallX;

        if (side == 0) {
            // Hit an X grid boundary.
            wallX =
                    camera.position.y +
                            distance * rayDirY;
        } else {
            // Hit a Y grid boundary.
            wallX =
                    camera.position.x +
                            distance * rayDirX;
        }

        wallX -= (float) Math.floor(wallX);

        drawTexturedVertical(
                pg,
                camera,
                screenX,
                distance,
                tile.floor,
                tile.ceiling,
                tile,
                wallX
        );
    }

    private void drawTexturedVertical(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float distance,
            float low,
            float high,
            Tile tile,
            float textureX) {

        if (tile.wallTexture == null)
            return;

        int top = project(camera, high, distance);
        int bottom = project(camera, low, distance);

        if (bottom < 0 || top >= height)
            return;

        int clippedTop = Math.max(top, 0);
        int clippedBottom = Math.min(bottom, height - 1);

        int textureWidth = tile.wallTexture.width;
        int textureHeight = tile.wallTexture.height;

        int texX = (int) (textureX * textureWidth);

        // Keep it inside the texture.
        texX = Math.max(0, Math.min(textureWidth - 1, texX));

        for (int y = clippedTop; y <= clippedBottom; y++) {

            float t =
                    (float) (y - top) /
                            (float) (bottom - top);

            int texY =
                    (int) (t * textureHeight);

            texY = Math.max(
                    0,
                    Math.min(textureHeight - 1, texY)
            );

            pg.setPixel(
                    screenX,
                    y,
                    tile.wallTexture.getPixel(texX, texY)
            );
        }
    }

    private int project(
            Camera3D camera,
            float worldZ,
            float distance) {

        return (int) (
                height * 0.5f
                        - (worldZ - camera.position.z)
                        * focalLength
                        / distance
        );
    }

    private void renderVerticalSurface(
            PixelGraphics pg,
            Camera3D camera,
            int screenX,
            float rayDirX,
            float rayDirY,
            float distance,
            float low,
            float high,
            Tile tile,
            int side) {

        float wallX;

        if (side == 0) {
            wallX =
                    camera.position.y +
                            distance * rayDirY;
        } else {
            wallX =
                    camera.position.x +
                            distance * rayDirX;
        }

        wallX -= (float) Math.floor(wallX);

        drawTexturedVertical(
                pg,
                camera,
                screenX,
                distance,
                low,
                high,
                tile,
                wallX
        );
    }
}