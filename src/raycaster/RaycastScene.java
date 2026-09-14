package raycaster;

import berryngine.*;
import java.util.ArrayList;

public final class RaycastScene implements Scene {

    public TileMap map;
    private PlayerEntity playerEntity;
    private HeightRaycaster raycaster;
    private final ArrayList<Entity3D> entityList = new ArrayList<Entity3D>();
    private final ArrayList<Entity3D> pendingEntityList = new ArrayList<Entity3D>();
    private final ArrayList<Entity3D> pendingRemovalList =
            new ArrayList<>();

    public static RaycastScene INSTANCE;
    public RaycastScene() {
        INSTANCE = this;
    }

    public void addEnity(Entity3D entity3D)
    {
        pendingEntityList.add(entity3D);
    }


    public void removeEntity(Entity3D entity3D) {
        pendingRemovalList.add(entity3D);
    }


    @Override
    public void update(GameWindow gameWindow, float dt) {

        for (Entity3D entity3D : entityList) {
            if (!entity3D.isRemoved()) {
                entity3D.update(dt);
            }
        }

        playerEntity.update(dt);

        if (!pendingRemovalList.isEmpty()) {
            entityList.removeAll(pendingRemovalList);
            pendingEntityList.removeAll(pendingRemovalList);
            pendingRemovalList.clear();
        }

        if (!pendingEntityList.isEmpty()) {
            entityList.addAll(pendingEntityList);
            pendingEntityList.clear();
        }
    }

    @Override
    public void render(
            GameWindow gameWindow,
            FramebufferPixelGraphics pg
    ) {
        pg.clear(Color.SKY_BLUE);
        raycaster.render(
                pg,
                playerEntity.camera,
                entityList
        );
        pg.renderString(BitmapFont.DEFAULT_8X9,playerEntity.pos.toString() + " e:" + entityList.size(),0,0,Color.BLACK);
        //PostFX.saturation(pg, 0.4f);
    }

    @Override
    public void onSceneEnter(GameWindow gameWindow) {
        LevelGenerator.TileStyle grass = new LevelGenerator.TileStyle(
                Tile.TEX_CONCRETE,
                Tile.TEX_GRASS,
                Tile.TEX_GRASS,
                Tile.TEX_SKY,
                0.0f,
                0.0f
        );

        LevelGenerator.TileStyle concretePath =
                grass.floorTexture(Tile.TEX_CONCRETE);

        LevelGenerator.TileStyle borderWall = new LevelGenerator.TileStyle(
                Tile.TEX_BRICK,
                Tile.TEX_GRASS,
                Tile.TEX_BRICK,
                Tile.TEX_SKY,
                0.0f,
                2.0f
        );

        LevelGenerator gen = LevelGenerator.create(64, 64)
                .fill(0, 0, 63, 63, grass);

// paths
        gen.fill(0, 30, 63, 33, concretePath);
        gen.fill(29, 0, 32, 63, concretePath);

// outer border
        gen.wallLine(0, 0, 63, 0, borderWall);
        gen.wallLine(0, 63, 63, 63, borderWall);
        gen.wallLine(0, 0, 0, 63, borderWall);
        gen.wallLine(63, 0, 63, 63, borderWall);

        map = gen.build();

        playerEntity = new PlayerEntity(
                new Vec3(
                        20.0f,
                        25.0f,
                        0.5f
                )
        );

        playerEntity.camera.rotation.y =
                Mathf.toRadians(45.0f);

        entityList.add(
                new HeartEntity(
                        new Vec3(
                                30f,
                                30.0f,
                                0.5f
                        )
                )
        );

        raycaster = new HeightRaycaster(
                map,
                gameWindow.getInternalWidth(),
                gameWindow.getInternalHeight(),
                Mathf.toRadians(70.0f)
        );
    }

    @Override
    public void onSceneExit(GameWindow gameWindow) {

    }
}