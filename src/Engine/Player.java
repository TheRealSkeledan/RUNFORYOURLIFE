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
    public float stunTimer = 0;
    public float frozenTimer = 0;
    public boolean active = true;

    // --- Speed ---
    public float baseSpeed;
    public final float GRAVITY = 18f;
    public final float JUMP_FORCE = -420f;

    // --- Hit Cooldown ---
    public float hitCooldown = 0;

    // --- Animation ---
    private Map<String, BufferedImage[]> animations;
    private String currentAnim = "run";
    private int animFrame = 0;
    private float animTimer = 0;
    private static final float FRAME_DURATION = 0.1f;

    // --- Sprite Fallback Drawing ---
    public final Color primaryColor;
    public final Color secondaryColor;

    // --- Dimensions (~1/4 of 720px screen height) ---
    // Standing: 180px tall, proportional width
    // Ducking:  half height
    public static final int STAND_W = 130;
    public static final int STAND_H = 310;
    public static final int DUCK_W  = 160;   // wider when crouching
    public static final int DUCK_H  = 160;

    public Player(String name, boolean isRunner, float startX, float startY, float speed) {
        this.name = name;
        this.isRunner = isRunner;
        this.x = startX;
        this.y = startY;
        this.baseSpeed = speed;
        this.primaryColor   = isRunner ? new Color(80, 220, 160) : new Color(220, 90, 80);
        this.secondaryColor = isRunner ? new Color(40, 130, 100) : new Color(140, 40,  30);

        loadAnimations();
    }

    private void loadAnimations() {
        animations = AnimationLoader.loadAnimations(name.toLowerCase());
    }

    public int getWidth()  { return isDucking ? DUCK_W  : STAND_W; }
    public int getHeight() { return isDucking ? DUCK_H  : STAND_H; }

    public Rectangle getBounds() {
        int w = getWidth();
        int h = getHeight();
        return new Rectangle((int)(x - w / 2f), (int)(y - h), w, h);
    }

    public boolean isStunned()    { return stunTimer   > 0; }
    public boolean isFrozen()     { return frozenTimer > 0; }
    public boolean isInvincible() { return hitCooldown > 0; }

    public void applyStun(float seconds)   { stunTimer   = Math.max(stunTimer,   seconds); }
    public void applyFreeze(float seconds) { frozenTimer = Math.max(frozenTimer, seconds); }

    public void loseLife() {
        if (isInvincible()) return;
        lives--;
        hitCooldown = 2.0f;
        stunTimer   = 0;
    }

    public void update(float dt, float groundY, boolean canMoveHorizontally) {
        stunTimer   = Math.max(0, stunTimer   - dt);
        frozenTimer = Math.max(0, frozenTimer - dt);
        hitCooldown = Math.max(0, hitCooldown - dt);

        if (isStunned() || isFrozen()) {
            applyGravity(dt, groundY);
            updateAnim(dt, "stun");
            return;
        }

        applyGravity(dt, groundY);

        if (!canMoveHorizontally) { updateAnim(dt, "run");  return; }

        if      (!onGround)   updateAnim(dt, "jump");
        else if (isDucking)   updateAnim(dt, "duck");
        else                  updateAnim(dt, "run");
    }

    private void applyGravity(float dt, float groundY) {
        vy += GRAVITY * dt * 60 * dt;
        y  += vy * dt;
        if (y >= groundY) {
            y        = groundY;
            vy       = 0;
            onGround = true;
        }
    }

    public void jump() {
        if (onGround && !isStunned() && !isFrozen()) {
            vy        = JUMP_FORCE * dt_cached;
            onGround  = false;
            isDucking = false;
        }
    }

    private float dt_cached = 1f / 60f;
    public void cacheDt(float dt) { this.dt_cached = dt; }

    private void updateAnim(float dt, String anim) {
        if (animations == null || animations.isEmpty()) return;

        String target = anim;
        if (!animations.containsKey(target)) {
            target = animations.containsKey("run")
                    ? "run"
                    : animations.keySet().iterator().next();
        }

        if (!target.equals(currentAnim)) {
            currentAnim = target;
            animFrame   = 0;
            animTimer   = 0;
        }

        animTimer += dt;
        if (animTimer >= FRAME_DURATION) {
            animTimer -= FRAME_DURATION;
            BufferedImage[] frames = animations.get(currentAnim);
            if (frames != null && frames.length > 0)
                animFrame = (animFrame + 1) % frames.length;
        }
    }

    public void draw(Graphics2D g, float cameraX) {
        int drawX = (int)(x - cameraX);
        int drawY = (int)y;

        // Invincibility flash — skip every other frame
        if (isInvincible() && (int)(hitCooldown * 10) % 2 == 0) return;

        int w = getWidth();
        int h = getHeight();

        if (animations != null && animations.containsKey(currentAnim)) {
            BufferedImage[] frames = animations.get(currentAnim);
            if (frames != null && frames.length > 0) {
                BufferedImage frame = frames[Math.min(animFrame, frames.length - 1)];
                // Draw spritesheet frame at FULL player size
                g.drawImage(frame, drawX - w / 2, drawY - h, w, h, null);
                drawStatusEffects(g, drawX, drawY);
                return;
            }
        }

        // No spritesheet — draw large placeholder
        drawPlaceholder(g, drawX, drawY);
        drawStatusEffects(g, drawX, drawY);
    }

    public void draw(Graphics2D g, int drawX, int drawY, int drawW, int drawH) {

        // Invincibility flash
        if (isInvincible() && (int)(hitCooldown * 10) % 2 == 0) return;

        if (animations != null && animations.containsKey(currentAnim)) {
            BufferedImage[] frames = animations.get(currentAnim);

            if (frames != null && frames.length > 0) {
                BufferedImage frame = frames[Math.min(animFrame, frames.length - 1)];

                g.drawImage(frame, drawX, drawY, drawW, drawH, null);

                drawStatusEffects(
                        g,
                        drawX + drawW / 2,
                        drawY + drawH
                );

                return;
            }
        }

        // Fallback placeholder
        drawPlaceholder(
                g,
                drawX + drawW / 2,
                drawY + drawH
        );

        drawStatusEffects(
                g,
                drawX + drawW / 2,
                drawY + drawH
        );
    }

    private void drawPlaceholder(Graphics2D g, int cx, int baseY) {
        int w = getWidth();
        int h = getHeight();
        int top = baseY - h;

        // Shadow
        g.setColor(new Color(0, 0, 0, 50));
        g.fillOval(cx - w / 2, baseY - 6, w, 12);

        Color body = isStunned() || isFrozen() ? Color.GRAY : primaryColor;

        if (isDucking) {
            // Crouched rectangle
            g.setColor(body);
            g.fillRoundRect(cx - DUCK_W / 2, baseY - DUCK_H, DUCK_W, DUCK_H, 28, 28);
            // Eyes
            g.setColor(Color.WHITE);
            int eyeY = baseY - DUCK_H + DUCK_H / 4;
            if (isRunner) {
                g.fillOval(cx + 18, eyeY, 20, 20);
                g.setColor(Color.BLACK);
                g.fillOval(cx + 22, eyeY + 4, 10, 10);
            } else {
                g.fillOval(cx - 38, eyeY, 20, 20);
                g.setColor(Color.BLACK);
                g.fillOval(cx - 34, eyeY + 4, 10, 10);
            }
        } else {
            // --- Legs ---
            int legW   = STAND_W / 4;
            int legH   = STAND_H / 3;
            int legTop = baseY - legH;
            int swing  = (int)(Math.sin(animFrame * 1.1f) * (STAND_W / 5f));
            g.setColor(secondaryColor);
            g.fillRoundRect(cx - STAND_W / 4 - legW / 2, legTop + swing,      legW, legH, 8, 8);
            g.fillRoundRect(cx + STAND_W / 4 - legW / 2, legTop - swing,      legW, legH, 8, 8);

            // --- Torso ---
            int torsoW = (int)(STAND_W * 0.85f);
            int torsoH = (int)(STAND_H * 0.42f);
            int torsoY = baseY - legH - torsoH;
            g.setColor(body);
            g.fillRoundRect(cx - torsoW / 2, torsoY, torsoW, torsoH, 18, 18);

            // --- Arms ---
            int armW = STAND_W / 5;
            int armH = (int)(STAND_H * 0.28f);
            int armY = torsoY + torsoH / 6;
            int armSwing = (int)(Math.sin(animFrame * 1.1f) * (STAND_W / 6f));
            g.setColor(secondaryColor);
            if (isRunner) {
                // Arms behind (runner faces right)
                g.fillRoundRect(cx - torsoW / 2 - armW + 4,  armY - armSwing, armW, armH, 8, 8);
                g.fillRoundRect(cx + torsoW / 2 - 4,          armY + armSwing, armW, armH, 8, 8);
            } else {
                // Arms in front (chaser faces right toward runner)
                g.fillRoundRect(cx - torsoW / 2 - armW + 4,  armY + armSwing, armW, armH, 8, 8);
                g.fillRoundRect(cx + torsoW / 2 - 4,          armY - armSwing, armW, armH, 8, 8);
            }

            // --- Head ---
            int headR = (int)(STAND_W * 0.62f);
            int headX = cx - headR / 2;
            int headY = torsoY - (int)(headR * 0.75f);
            g.setColor(body);
            g.fillOval(headX, headY, headR, headR);

            // Eyes (face right for runner, face right for chaser too — both face inward)
            g.setColor(Color.WHITE);
            int eyeSize = headR / 4;
            int eyeY    = headY + headR / 3;
            if (isRunner) {
                int eyeX = headX + headR / 2;
                g.fillOval(eyeX, eyeY, eyeSize, eyeSize);
                g.setColor(Color.BLACK);
                g.fillOval(eyeX + eyeSize / 4, eyeY + eyeSize / 4, eyeSize / 2, eyeSize / 2);
            } else {
                int eyeX = headX + headR / 6;
                g.fillOval(eyeX, eyeY, eyeSize, eyeSize);
                g.setColor(Color.BLACK);
                g.fillOval(eyeX + eyeSize / 4, eyeY + eyeSize / 4, eyeSize / 2, eyeSize / 2);
            }

            // Chaser crown / runner hair tuft
            if (!isRunner) {
                // Jagged crown
                g.setColor(new Color(255, 200, 0));
                int[] xp = {
                        headX,           headX + headR/5,     headX + headR*2/5,
                        headX + headR/2, headX + headR*3/5,   headX + headR*4/5, headX + headR
                };
                int[] yp = {
                        headY,           headY - headR/3,     headY,
                        headY - headR/3, headY,               headY - headR/3,   headY
                };
                g.fillPolygon(xp, yp, 7);
            } else {
                // Hair tuft
                g.setColor(secondaryColor);
                g.fillOval(headX + headR / 3, headY - headR / 5, headR / 2, headR / 3);
            }
        }
    }

    private void drawStatusEffects(Graphics2D g, int cx, int baseY) {
        int h = getHeight();
        int w = getWidth();

        if (isFrozen()) {
            g.setColor(new Color(100, 180, 255, 55));
            g.fillOval(cx - w / 2 - 4, baseY - h - 4, w + 8, h + 8);
            g.setColor(new Color(100, 180, 255, 140));
            g.setStroke(new BasicStroke(2.5f));
            g.drawOval(cx - w / 2 - 4, baseY - h - 4, w + 8, h + 8);
            g.setStroke(new BasicStroke(1f));
        }

        if (isStunned()) {
            double angle = System.currentTimeMillis() * 0.005;
            g.setColor(new Color(255, 220, 0));
            int orbitR  = w / 2 + 14;
            int starSize = 10;
            for (int i = 0; i < 3; i++) {
                double a  = angle + i * Math.PI * 2 / 3;
                int sx = (int)(cx          + Math.cos(a) * orbitR);
                int sy = (int)(baseY - h   + Math.sin(a) * 20);
                g.fillOval(sx - starSize / 2, sy - starSize / 2, starSize, starSize);
            }
        }
    }
}