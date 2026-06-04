package Panel;

public class PhysicsEngine {

    public static final float GRAVITY    = 1800f;
    public static final float JUMP_FORCE = -720f;

    public static float[] applyGravity(float y, float vy, float groundY, float dt) {
        vy += GRAVITY * dt;
        y  += vy * dt;
        if (y >= groundY) {
            y  = groundY;
            vy = 0f;
        }
        return new float[]{ y, vy };
    }

    public static float startJump(boolean onGround) {
        return onGround ? JUMP_FORCE : 0f;
    }

    public static float recoverX(float current, float target, float recoverySpeed, float dt) {
        if (current < target) return Math.min(target, current + recoverySpeed * dt);
        if (current > target) return Math.max(target, current - recoverySpeed * dt);
        return target;
    }
}