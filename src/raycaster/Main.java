package raycaster;

import berryngine.GameWindow;

public final class Main {

    public static void main(String[] args) {
        GameWindow.builder(
                        "Height Raycaster",
                        320,
                        200
                )
                .scale(3)
                .targetFps(60)
                .fixedHz(60)
                .run(new RaycastScene());
    }
}