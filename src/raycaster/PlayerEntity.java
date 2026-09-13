package raycaster;

import berryngine.Input;
import berryngine.Mathf;
import berryngine.Vec3;

import java.awt.event.KeyEvent;

public class PlayerEntity extends Entity
{
    public Camera3D camera;
    public PlayerEntity(
            Vec3 pos
    )
    {
        this.pos = pos;
        this.camera = new Camera3D(pos);
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        float moveSpeed = 5.0f * dt;
        float turnSpeed = 3.5f * dt;

        float yaw = camera.rotation.y;

        float forwardX = Mathf.cos(yaw);
        float forwardY = Mathf.sin(yaw);

        updateKeyboardInput(
                forwardX,
                forwardY,
                moveSpeed,
                turnSpeed
        );

    }

    private void updateMouseInput() {
        int dx = Input.getMouseDeltaX();
        int dy = Input.getMouseDeltaY();

        // Mouse sensitivity is in radians per pixel.
        // Do NOT multiply mouse delta by frame delta.
        float sensitivity = 0.003f;

        // Mouse X -> yaw
        camera.rotation.y += dx * sensitivity;

        // Mouse Y -> pitch
        camera.rotation.x -= dy * sensitivity;

        // Prevent the camera from flipping upside down.
        float maxPitch = Mathf.toRadians(89.0f);

        if (camera.rotation.x > maxPitch) {
            camera.rotation.x = maxPitch;
        }

        if (camera.rotation.x < -maxPitch) {
            camera.rotation.x = -maxPitch;
        }
    }

    private void updateKeyboardInput(
            float forwardX,
            float forwardY,
            float moveSpeed,
            float turnSpeed
    ) {
        float rightX = -forwardY;
        float rightY = forwardX;

        // Forward / backward
        if (Input.isKey(KeyEvent.VK_W)) {
            camera.position.x += forwardX * moveSpeed;
            camera.position.y += forwardY * moveSpeed;
        }

        if (Input.isKey(KeyEvent.VK_S)) {
            camera.position.x -= forwardX * moveSpeed;
            camera.position.y -= forwardY * moveSpeed;
        }

        // Strafing
        if (Input.isKey(KeyEvent.VK_A)) {
            camera.position.x -= rightX * moveSpeed;
            camera.position.y -= rightY * moveSpeed;
        }

        if (Input.isKey(KeyEvent.VK_D)) {
            camera.position.x += rightX * moveSpeed;
            camera.position.y += rightY * moveSpeed;
        }

        // Turning
        if (Input.isKey(KeyEvent.VK_LEFT) || Input.isKey(KeyEvent.VK_Q)) {
            camera.rotation.y -= turnSpeed;
        }

        if (Input.isKey(KeyEvent.VK_RIGHT) || Input.isKey(KeyEvent.VK_E)) {
            camera.rotation.y += turnSpeed;
        }

        // Looking up / down
        if (Input.isKey(KeyEvent.VK_UP)) {
            camera.rotation.x += turnSpeed;
        }

        if (Input.isKey(KeyEvent.VK_DOWN)) {
            camera.rotation.x -= turnSpeed;
        }
    }
}
