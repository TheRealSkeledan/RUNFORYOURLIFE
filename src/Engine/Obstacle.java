package Engine;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.awt.geom.*;

/**
 * An obstacle on the infinite track.
 *
 * GROUND obstacles sit on the floor — players must JUMP to dodge them.
 *   → drawn from assets/obstacles/ground.png
 * AERIAL obstacles hang from above — players must DUCK to dodge them.
 *   → drawn from assets/obstacles/aerial.png
 *
 * Drop replacement PNGs at those paths and they load automatically.
 * If the file is missing the obstacle falls back to a programmatic placeholder.
 */
public class Obstacle {

    public enum Type { GROUND, AERIAL }

    // ── Dimensions ────────────────────────────────────────────────────────────
    // GROUND: ~50 px tall so players must jump over it
    public static final int GROUND_W = 80;
    public static final int GROUND_H = 50;

    // AERIAL: hangs so its BOTTOM sits at screen-Y 400 (400 px below the top edge).
    // Any height is fine visually; collision uses the actual pixel area.
    public static final int AERIAL_W = 100;
    public static final int AERIAL_H = 120;

    // The Y coordinate (screen pixels from top) where the bottom of every aerial
    // obstacle hangs.  Must be above the duck hitbox ceiling (GROUND_Y - DUCK_H).
    public static final float AERIAL_BOTTOM_Y = 400f;

    // ── Sprite cache — loaded once, shared by all instances ───────────────────
    private static BufferedImage groundSprite;
    private static BufferedImage aerialSprite;
    private static boolean spritesLoaded = false;

    private static void ensureSprites() {
        if (spritesLoaded) return;
        spritesLoaded = true;
        groundSprite = tryLoad("assets/images/obstacles/ground.png");
        aerialSprite = tryLoad("assets/images/obstacles/aerial.png");
    }

    private static BufferedImage tryLoad(String path) {
        try {
            File f = new File(path);
            if (f.exists()) return ImageIO.read(f);
        } catch (IOException ignored) {}
        return null; // triggers placeholder drawing
    }

    // ── Instance fields ───────────────────────────────────────────────────────
    public float x;
    public float y;       // bottom of the obstacle in screen-Y space
    public final int  width;
    public final int  height;
    public final Type type;

    /**
     * @param x       world X of obstacle left edge
     * @param groundY Y coordinate of the ground (foot level, e.g. 580)
     * @param type    GROUND (jump to pass) or AERIAL (duck to pass)
     */
    public Obstacle(float x, float groundY, Type type) {
        this.x    = x;
        this.type = type;
        ensureSprites();

        if (type == Type.GROUND) {
            width  = GROUND_W;
            height = GROUND_H;
            y      = groundY;           // bottom sits on the ground
        } else {
            width  = AERIAL_W;
            height = AERIAL_H;
            y      = AERIAL_BOTTOM_Y;  // bottom always at 400 px from top
        }
    }

    // ── Collision bounds ──────────────────────────────────────────────────────

    /** Tight collision rect (inset 8 px for fairness). */
    public Rectangle getCollisionBounds() {
        int inset = 8;
        return new Rectangle(
                (int)x      + inset,
                (int)(y - height) + inset,
                width  - inset * 2,
                height - inset * 2);
    }

    /** Alias kept for compatibility. */
    public Rectangle getBounds() { return getCollisionBounds(); }

    // ── Draw ─────────────────────────────────────────────────────────────────

    public void draw(Graphics2D g, float cameraX) {
        int dx  = (int)(x - cameraX);
        int top = (int)(y - height);

        if (type == Type.GROUND && groundSprite != null) {
            g.drawImage(groundSprite, dx, top, width, height, null);
        } else if (type == Type.AERIAL && aerialSprite != null) {
            g.drawImage(aerialSprite, dx, top, width, height, null);
        } else {
            drawPlaceholder(g, dx, top);
        }
    }

    // ── Placeholder drawing (used when PNG is absent) ─────────────────────────

    private void drawPlaceholder(Graphics2D g, int dx, int top) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (type == Type.GROUND) {
            // Low orange barrier — 50 px tall
            GradientPaint gp = new GradientPaint(
                    dx, top, new Color(220, 110, 30),
                    dx, top + height, new Color(140, 60, 15));
            g.setPaint(gp);
            g.fillRoundRect(dx, top, width, height, 10, 10);

            // Warning stripes
            g.setColor(new Color(255, 220, 0, 180));
            g.setStroke(new BasicStroke(4f));
            int stripeCount = 4;
            float stripeW = (float) width / stripeCount;
            for (int i = 0; i < stripeCount; i += 2) {
                int sx = dx + (int)(i * stripeW);
                g.fillRect(sx, top, (int)stripeW, height);
            }
            g.setColor(new Color(180, 80, 0));
            g.setStroke(new BasicStroke(2f));
            g.drawRoundRect(dx, top, width, height, 10, 10);
            g.setStroke(new BasicStroke(1f));

        } else {
            // Hanging spike ceiling hazard
            // Mount bar
            g.setColor(new Color(50, 50, 70));
            g.fillRoundRect(dx, top, width, 12, 6, 6);
            g.setColor(new Color(90, 90, 120));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(dx, top, width, 12, 6, 6);

            // Spike triangle
            int mx = dx + width / 2;
            int spikeTop = top + 12;
            int spikeBot = top + height;
            GradientPaint sg = new GradientPaint(
                    dx, spikeTop, new Color(60, 130, 240),
                    dx + width, spikeTop, new Color(20, 70, 160));
            g.setPaint(sg);
            g.fillPolygon(
                    new int[]{dx, mx, dx + width},
                    new int[]{spikeTop, spikeBot, spikeTop}, 3);
            g.setColor(new Color(130, 190, 255, 150));
            g.setStroke(new BasicStroke(1.5f));
            g.drawPolygon(
                    new int[]{dx, mx, dx + width},
                    new int[]{spikeTop, spikeBot, spikeTop}, 3);

            // Glow
            g.setColor(new Color(80, 150, 255, 30));
            g.fillOval(dx - 6, spikeTop, width + 12, height - 8);
            g.setStroke(new BasicStroke(1f));
        }
    }
}
