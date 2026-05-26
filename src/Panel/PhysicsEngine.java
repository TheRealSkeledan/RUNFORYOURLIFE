package Panel;

/**
 * PhysicsEngine — pure physics helpers (gravity, jump, ground clamp).
 * No Swing / rendering dependencies.
 */
public class PhysicsEngine {

    public static final float GRAVITY    = 1800f;
    public static final float JUMP_FORCE = -720f;

    /**
     * Applies gravity to a vertical velocity and returns the new [vy, y] pair.
     * Clamps to groundY and sets vy=0 on landing.
     *
     * @param y        current y position
     * @param vy       current vertical velocity
     * @param groundY  y-coordinate of the ground
     * @param dt       delta time in seconds
     * @return         float[2] { newY, newVY }
     */
    public static float[] applyGravity(float y, float vy, float groundY, float dt) {
        vy += GRAVITY * dt;
        y  += vy * dt;
        if (y >= groundY) {
            y  = groundY;
            vy = 0f;
        }
        return new float[]{ y, vy };
    }

    /**
     * Returns the initial upward velocity for a jump, or the current vy
     * unchanged if the entity is not on the ground.
     */
    public static float startJump(boolean onGround) {
        return onGround ? JUMP_FORCE : 0f; // caller should only call when onGround
    }

    /**
     * Slides an x position back toward a target at the given recovery speed.
     *
     * @param current       current x
     * @param target        x to recover toward
     * @param recoverySpeed pixels per second
     * @param dt            delta time
     * @return              new x, clamped at target
     */
    public static float recoverX(float current, float target, float recoverySpeed, float dt) {
        if (current < target) return Math.min(target, current + recoverySpeed * dt);
        if (current > target) return Math.max(target, current - recoverySpeed * dt);
        return target;
    }
}