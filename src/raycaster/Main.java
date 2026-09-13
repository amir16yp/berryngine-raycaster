package raycaster;

import berryngine.GameWindow;

public final class Main {

    public static GameWindow GAME_WINDOW;

    static void main(String[] args) {
        GAME_WINDOW = GameWindow.builder(
                        "Height Raycaster",
                        480,
                        242
                )
                .scale(3)
                .targetFps(0)
                .fixedHz(60)
                //.setCaptureMouseByDefault(true)
                .run(new RaycastScene());
    }
}