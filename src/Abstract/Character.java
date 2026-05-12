// Character.java

package Abstract;

import Engine.AnimationLoader;
import Objects.Projectile;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public abstract class Character {
    public String name;
    public int x, y, speed;
    protected Map<String, BufferedImage[]> animations = new HashMap<>();
    protected BufferedImage currentFrame;
    protected int frameIndex = 0;
    protected int damage;
    public double weight;
    protected String action = "idle";
    protected double jumpSpeed = 0;
    protected int initialY;
    protected int KP = 0, HP = 100;
    protected BufferedImage bulletSprite;

    private long lastFrameTime = 0;
    private long frameDelay = 200;

    public boolean jumping = false;
    public boolean isLocked = false;

    protected List<Projectile> projectiles = new ArrayList<>();

    public Character(String name, int x, int y, int damage, int strength, int resistance, int speed, double weight) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.damage = damage;
        this.speed = speed;
        this.weight = weight;

        loadAnimations();
        setAction("idle");
    }

    public Character(String name, int x, int y, int damage, int strength, int resistance, int speed, double weight, String bSprite) throws IOException {
        this.name = name;
        this.x = x;
        this.y = y;
        this.damage = damage;
        this.speed = speed;
        this.weight = weight;
        this.bulletSprite = ImageIO.read(new File("assets/images/objects/bullet.png"));

        loadAnimations();
        setAction("idle");
    }

    private void loadAnimations() {
        animations = AnimationLoader.loadAnimations(name.toLowerCase());
    }

    public void setAction(String action) {
        if (isLocked && !this.action.equals("idle")) return;
    
        if (!this.action.equals(action)) {
            this.action = action;
            this.frameIndex = 0;
            isLocked = action.equals("light") || action.equals("heavy") || action.equals("special") || action.equals("final");
        }
        updateCurrentFrame();
    }
    

    private void updateCurrentFrame() {
        if (animations.containsKey(action)) {
            currentFrame = animations.get(action)[frameIndex];
        } else {
            System.out.println("No animation found for action: " + action);
        }
    }

    public void updateAnimationFrame() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastFrameTime >= frameDelay) {
            if (animations.containsKey(action)) {
                BufferedImage[] frames = animations.get(action);
                frameIndex++;
                if (frameIndex >= frames.length) {
                    frameIndex = 0;
                    if (isLocked) {
                        isLocked = false;
                        setAction("idle");
                    }
                }
                updateCurrentFrame();
            }
            lastFrameTime = currentTime;
        }
    }
    

    public BufferedImage getCurrentFrame() {
        return currentFrame;
    }

    public void move(int dx) {
        if (x + dx >= -110 && x + dx <= 1390 - getWidth()) {
            x += dx;
            if(y == 300)
                setAction("walk");
            else
                setAction("jump");
        }
    }

    public void defend() {
        setAction("block");
    }

    public void light() {
        setAction("light");
    }

    public void heavy() {
        setAction("heavy");
    }

    public void special() {
        setAction("special");
    }

    public void finish() {
        setAction("final");
    }

    public void taunt() {
        setAction("taunt");
    }

    public void jump() {
        if (!jumping) {
            jumping = true;
            jumpSpeed = -10;
            initialY = y;
            setAction("jump");
        }
    }

    public void updatePosition() {
        if (jumping) {
            jumpSpeed += weight;
            y += jumpSpeed;

            if (y >= initialY) {
                y = initialY;
                jumping = false;
                setAction("idle");
            }
        }
    }

    public BufferedImage getIdle() {
        return animations.containsKey("idle") ? animations.get("idle")[0] : null;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return currentFrame != null ? currentFrame.getWidth() : 0;
    }
    
    public int getHeight() {
        return currentFrame != null ? currentFrame.getHeight() : 0;
    }

    public void changeHP(int amt) {
        if (HP + amt >= 0 && HP + amt <= 100) {
            HP += amt;
        }
    }
    
    public int getHP() {
        return HP;
    }

    public void changeKP(int amt) {
        if (KP + amt >= 0 && KP + amt <= 100) {
            KP += amt;
        }
    }

    public int getKP() {
        return KP;
    }

    public void shootProjectile(String type, boolean isFacingRight) throws IOException {
        Projectile projectile = createProjectile(type, isFacingRight);
        if (projectile != null) {
            projectiles.add(projectile);
        }
    }

    protected Projectile createProjectile(String type, boolean isFacingRight) throws IOException {
        int projectileSpeed = 15;
        int damage = 20;
        if (type.equals("heavy")) {
            return new Projectile(x, y, projectileSpeed, isFacingRight, bulletSprite, damage);
        }
        return null;
    }

    public void updateProjectiles() {
        projectiles.forEach(Projectile::updatePosition);
        projectiles.removeIf(p -> p.isOffScreen(1280));
    }

    public void drawProjectiles(Graphics g) {
        projectiles.forEach(p -> p.draw(g));
    }
}