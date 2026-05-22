
package Engine;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Map;

/**
 * Represents a player (runner or chaser) in the game.
 * Auto-runs horizontally; player controls jump/duck only.
 * Supports spritesheet animations via AnimationLoader.
 */
public class Player {

    // --- Identity ---
    public final String name;
    public final boolean isRunner;

    // --- Position & Physics ---
    public float x, y;
    public float vy = 0;
    public boolean onGround = true;
    public boolean isDucking = false;

    // --- Game State ---
    public int lives = 3; // Runner only
    public float stunTimer = 0; // Stun in seconds
    public float frozenTimer = 0; // Freeze from ability
    public boolean active = true;

    // --- Speed ---
    public float baseSpeed; // Current base speed (chaser decreases over time)
    public final float GRAVITY = 18f;
    public final float JUMP_FORCE = -420f;

    // --- Hit Cooldown (invincibility frames after being hit) ---
    public float hitCooldown = 0;

    // --- Animation ---
    private Map<String, BufferedImage[]> animations;
    private String currentAnim = "run";
    private int animFrame = 0;
    private float animTimer = 0;
    private static final float FRAME_DURATION = 0.1f; // seconds per frame

    // --- Sprite Fallback Drawing ---
    public final Color primaryColor;
    public final Color secondaryColor;

    // --- Dimensions (used for collision) ---
    public static final int STAND_W = 28;
    public static final int STAND_H = 48;
    public static final int DUCK_W = 28;
    public static final int DUCK_H = 28;

    public Player(String name, boolean isRunner, float startX, float startY, float speed) {
        this.name = name;
        this.isRunner = isRunner;
        this.x = startX;
        this.y = startY;
        this.baseSpeed = speed;
        this.primaryColor = isRunner ? new Color(80, 220, 160) : new Color(220, 90, 80);
        this.secondaryColor = isRunner ? new Color(40, 130, 100) : new Color(140, 40, 30);
    }

    public void loadAnimations(String characterName) {
        animations = AnimationLoader.loadAnimations(characterName);
    }

    public int getWidth() { return isDucking ? DUCK_W : STAND_W; }
    public int getHeight() { return isDucking ? DUCK_H : STAND_H; }

    public Rectangle getBounds() {
        int w = getWidth();
        int h = getHeight();
        return new Rectangle((int)(x - w / 2f), (int)(y - h), w, h);
    }

    public boolean isStunned() { return stunTimer > 0; }
    public boolean isFrozen() { return frozenTimer > 0; }
    public boolean isInvincible() { return hitCooldown > 0; }

    public void applyStun(float seconds) { stunTimer = Math.max(stunTimer, seconds); }
    public void applyFreeze(float seconds) { frozenTimer = Math.max(frozenTimer, seconds); }

    public void loseLife() {
        if (isInvincible()) return;
        lives--;
        hitCooldown = 2.0f;
        stunTimer = 0;
    }

    /**
     * Update physics, timers, and animation each frame.
     * @param dt delta time in seconds
     * @param groundY the Y coordinate of the ground (feet level)
     * @param canMoveHorizontally false during countdown or while stunned/frozen
     */
    public void update(float dt, float groundY, boolean canMoveHorizontally) {
        // Tick timers
        stunTimer = Math.max(0, stunTimer - dt);
        frozenTimer = Math.max(0, frozenTimer - dt);
        hitCooldown = Math.max(0, hitCooldown - dt);

        if (isStunned() || isFrozen()) {
            // Still apply gravity while stunned / frozen
            applyGravity(dt, groundY);
            updateAnim(dt, "stun");
            return;
        }

        if (!canMoveHorizontally) {
            applyGravity(dt, groundY);
            updateAnim(dt, "run");
            return;
        }

        // Gravity & vertical
        applyGravity(dt, groundY);

        // Animation state
        if (!onGround) {
            updateAnim(dt, "jump");
        } else if (isDucking) {
            updateAnim(dt, "duck");
        } else {
            updateAnim(dt, "run");
        }
    }

    private void applyGravity(float dt, float groundY) {
        vy += GRAVITY * dt * 60 * dt; // frame-rate-independent
        y += vy * dt;
        if (y >= groundY) {
            y = groundY;
            vy = 0;
            onGround = true;
        }
    }

    public void jump() {
        if (onGround && !isStunned() && !isFrozen()) {
            vy = JUMP_FORCE * dt_cached;
            onGround = false;
            isDucking = false;
        }
    }

    // We cache dt for jump so jump() can be called from key events
    private float dt_cached = 1f / 60f;
    public void cacheDt(float dt) { this.dt_cached = dt; }

    private void updateAnim(float dt, String anim) {
        // If no animation loaded, do nothing
        if (animations == null || animations.isEmpty()) return;

        // Pick best available animation
        String target = anim;
        if (!animations.containsKey(target)) {
            if (animations.containsKey("run")) target = "run";
            else target = animations.keySet().iterator().next();
        }

        if (!target.equals(currentAnim)) {
            currentAnim = target;
            animFrame = 0;
            animTimer = 0;
        }

        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            BufferedImage[] frames = animations.get(currentAnim);
            if (frames != null && frames.length > 0)
                animFrame = (animFrame + 1) % frames.length;
        }
    }

    /**
     * Draw the player. Uses sprite if loaded, otherwise draws a colourful placeholder.
     */
    public void draw(Graphics2D g, float cameraX) {
        int drawX = (int)(x - cameraX);
        int drawY = (int)y;

        // Flicker during invincibility
        if (isInvincible() && (int)(hitCooldown * 10) % 2 == 0) return;

        // --- Sprite rendering ---
        if (animations != null && animations.containsKey(currentAnim)) {
            BufferedImage[] frames = animations.get(currentAnim);
            if (frames != null && frames.length > 0) {
                BufferedImage frame = frames[Math.min(animFrame, frames.length - 1)];
                int w = getWidth();
                int h = getHeight();
                g.drawImage(frame, drawX - w / 2, drawY - h, w, h, null);
                drawStatusEffects(g, drawX, drawY);
                return;
            }
        }

        // --- Placeholder rendering ---
        drawPlaceholder(g, drawX, drawY);
        drawStatusEffects(g, drawX, drawY);
    }

    private void drawPlaceholder(Graphics2D g, int cx, int baseY) {
        int w = getWidth();
        int h = getHeight();
        int top = baseY - h;

        // Shadow
        g.setColor(new Color(0, 0, 0, 60));
        g.fillOval(cx - w / 2 - 2, baseY - 4, w + 4, 8);

        // Body
        g.setColor(isStunned() || isFrozen() ? Color.GRAY : primaryColor);
        if (isDucking) {
            // Ducking: wide low rectangle
            g.fillRoundRect(cx - w / 2, baseY - DUCK_H, DUCK_W, DUCK_H, 8, 8);
        } else {
            // Legs (animated)
            int legOff = (int)(Math.sin(animFrame * 1.2) * 5);
            g.setColor(secondaryColor);
            g.fillRect(cx - 8, baseY - 22, 7, 20 + legOff);
            g.fillRect(cx + 1, baseY - 22, 7, 20 - legOff);
            // Torso
            g.setColor(isStunned() || isFrozen() ? Color.GRAY : primaryColor);
            g.fillRoundRect(cx - 10, top + 14, 20, 22, 6, 6);
            // Head
            g.fillOval(cx - 9, top, 18, 18);
            // Eyes
            g.setColor(Color.WHITE);
            g.fillOval(cx - 5, top + 4, 4, 4);
            g.fillOval(cx + 1, top + 4, 4, 4);
            g.setColor(Color.BLACK);
            g.fillOval(cx - 4, top + 5, 2, 2);
            g.fillOval(cx + 2, top + 5, 2, 2);
        }

        // Chaser crown / runner ribbon
        if (!isRunner) {
            g.setColor(new Color(255, 200, 0));
            int[] xp = {cx - 8, cx - 4, cx, cx + 4, cx + 8, cx + 6, cx - 6};
            int[] yp = {top - 2, top + 2, top - 2, top + 2, top - 2, top - 8, top - 8};
            g.fillPolygon(xp, yp, 7);
        }

        // Lives indicator (runner only, tiny hearts above head)
        if (isRunner) {
            for (int i = 0; i < 3; i++) {
                g.setColor(i < lives ? new Color(220, 60, 80) : new Color(80, 80, 80));
                int hx = cx - 18 + i * 14;
                int hy = top - 14;
                g.fillOval(hx, hy, 5, 5);
                g.fillOval(hx + 5, hy, 5, 5);
                int[] hxp = {hx, hx + 5, hx + 10};
                int[] hyp = {hy + 4, hy + 4 + 6, hy + 4};
                g.fillPolygon(hxp, hyp, 3);
            }
        }
    }

    private void drawStatusEffects(Graphics2D g, int cx, int baseY) {
        // Freeze aura
        if (isFrozen()) {
            g.setColor(new Color(100, 180, 255, 60));
            g.fillOval(cx - 18, baseY - getHeight() - 4, 36, getHeight() + 8);
            g.setColor(new Color(100, 180, 255, 120));
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(cx - 18, baseY - getHeight() - 4, 36, getHeight() + 8);
            g.setStroke(new BasicStroke(1f));
        }
        // Stun stars
        if (isStunned()) {
            double angle = System.currentTimeMillis() * 0.005;
            g.setColor(new Color(255, 220, 0));
            for (int i = 0; i < 3; i++) {
                double a = angle + i * Math.PI * 2 / 3;
                int sx = (int)(cx + Math.cos(a) * 14);
                int sy = (int)(baseY - getHeight() - 8 + Math.sin(a) * 6);
                g.fillOval(sx - 3, sy - 3, 6, 6);
            }
        }
    }
}