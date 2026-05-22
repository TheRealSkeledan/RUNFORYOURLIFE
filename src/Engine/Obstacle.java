package Engine;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * An obstacle on the infinite track.
 * Type: GROUND (duck to pass) or AERIAL (jump to pass).
 *
 * Replace the placeholder rendering by providing a spritesheet via setSprite().
 */
public class Obstacle {

    public enum Type { GROUND, AERIAL }

    public float x;
    public final float y; // foot/centre-y depending on type
    public final int width;
    public final int height;
    public final Type type;

    private BufferedImage sprite; // optional – set via setSprite()

    // Colours for placeholder drawing
    private static final Color GROUND_COL = new Color(180, 80, 40);
    private static final Color AERIAL_COL = new Color( 60, 120, 220);
    private static final Color GROUND_DARK = new Color(120, 50, 20);
    private static final Color AERIAL_DARK = new Color( 30, 70, 160);

    /**
     * @param x world X of obstacle left edge
     * @param groundY Y coordinate of ground level (feet)
     * @param type GROUND (block on floor) or AERIAL (hanging hazard)
     */
    public Obstacle(float x, float groundY, Type type) {
        this.x = x;
        this.type = type;

        if (type == Type.GROUND) {
            width = 28;
            height = 34;
            y = groundY; // sits on the ground
        } else {
            width = 26;
            height = 26;
            y = groundY - Player.STAND_H * 0.55f; // hangs at head height
        }
    }

    public void setSprite(BufferedImage img) { this.sprite = img; }

    /** Collision rectangle in world space. */
    public Rectangle getBounds() {
        return new Rectangle((int)x, (int)(y - height), width, height);
    }

    public void draw(Graphics2D g, float cameraX) {
        int dx = (int)(x - cameraX);
        int top = (int)(y - height);

        if (sprite != null) {
            g.drawImage(sprite, dx, top, width, height, null);
            return;
        }

        // --- Placeholder ---
        if (type == Type.GROUND) {
            // Wooden crate / barrel look
            g.setColor(GROUND_COL);
            g.fillRoundRect(dx, top, width, height, 5, 5);
            g.setColor(GROUND_DARK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(dx, top, width, height, 5, 5);
            // Cross planks
            g.setStroke(new BasicStroke(1f));
            g.drawLine(dx, top, dx + width, top + height);
            g.drawLine(dx + width, top, dx, top + height);
            // Flame on top
            g.setColor(new Color(255, 80, 0, 200));
            g.fillOval(dx + width / 2 - 4, top - 9, 8, 12);
            g.setColor(new Color(255, 200, 0, 200));
            g.fillOval(dx + width / 2 - 2, top - 7, 4, 8);

        } else {
            // Aerial stalactite / hanging spike
            g.setColor(AERIAL_COL);
            int midX = dx + width / 2;
            int[] xs = { dx, midX, dx + width };
            int[] ys = { top, top + height, top };
            g.fillPolygon(xs, ys, 3);
            g.setColor(AERIAL_DARK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawPolygon(xs, ys, 3);
            g.setStroke(new BasicStroke(1f));
            // Glow
            g.setColor(new Color(100, 160, 255, 80));
            g.fillOval(dx + 2, top + 2, width - 4, height / 2);
        }
        g.setStroke(new BasicStroke(1f));
    }
}