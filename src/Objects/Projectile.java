// Projectile.java

package Objects;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class Projectile {
    private int x, y;
    private int speed;
    private BufferedImage sprite;
    private boolean isFacingRight;
    private int damage;

    public Projectile(int x, int y, int speed, boolean isFacingRight, BufferedImage sprite, int damage) throws IOException {
        this.x = x;
        this.y = y;
        this.speed = speed;
        this.isFacingRight = isFacingRight;
        this.sprite = ImageIO.read(new File("assets/images/objects/bullet.png"));
        this.damage = damage;
    }

    public void updatePosition() {
        x += isFacingRight ? speed : -speed;
    }

    public void draw(Graphics g) {
        g.drawImage(sprite, x, y, null);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getDamage() {
        return damage;
    }

    public boolean isOffScreen(int screenWidth) {
        return x < -sprite.getWidth() || x > screenWidth;
    }
}
