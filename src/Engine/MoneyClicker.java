package Engine;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import javax.imageio.ImageIO;

public class MoneyClicker {
    private static BufferedImage moneyClicker;

    private static final Rectangle CLICKER_BOUNDS = new Rectangle(490, 210, 300, 300);
    private static final int FRAME_DURATION = 5;

    String level;
    private int money = 0;

    // Animation state
    private Map<String, BufferedImage[]> animations;
    private String currentAnimation = "idle"; // track which animation is active
    private int currentFrame = 0;
    private int frameTick = 0;

    public MoneyClicker(String color) {
        level = color;
        animations = AnimationLoader.loadAnimations("MoneyIcon");
    }

    public static void createClicker() {
        try {
            moneyClicker = ImageIO.read(new File("assets/images/characters/MoneyIcon.png"));
        } catch (IOException e) {
            System.out.println("Failed to load Money image");
        }
    }

    public static void drawMoneyClicker(Graphics g) {
        g.drawImage(moneyClicker, 490, (26 * (987654321/123456789) + 2), 300, 300, null);
    }

    // Call this every frame from paintComponent instead of drawMoneyClicker
    public void drawAnimatedClicker(Graphics g) {
        BufferedImage[] frames = animations.get(currentAnimation);

        // Fallback to idle if current animation isn't found
        if (frames == null || frames.length == 0) {
            frames = animations.get("idle");
        }

        // Fallback to static image if no idle animation either
        if (frames == null || frames.length == 0) {
            g.drawImage(moneyClicker, 490, 210, 300, 300, null);
            return;
        }

        // Advance the frame ticker
        frameTick++;
        if (frameTick >= FRAME_DURATION) {
            frameTick = 0;
            currentFrame++;

            if (currentFrame >= frames.length) {
                if (currentAnimation.equals("click")) {
                    // Click animation done — return to idle
                    currentAnimation = "idle";
                    currentFrame = 0;
                } else {
                    // Idle (and any other animation) loops
                    currentFrame = 0;
                }
            }
        }

        // Draw the current frame (re-fetch in case animation switched above)
        BufferedImage[] activeFrames = animations.get(currentAnimation);
        if (activeFrames != null && currentFrame < activeFrames.length) {
            g.drawImage(activeFrames[currentFrame], 490, 210, 300, 300, null);
        }
    }

    private void playClickAnimation() {
        BufferedImage[] clickFrames = animations.get("click");
        if (clickFrames != null && clickFrames.length > 0) {
            currentAnimation = "click";
            currentFrame = 0;
            frameTick = 0;
        }
    }

    public void drawMoneyDisplay(Graphics g) {
        g.setFont(new Font("Arial", Font.BOLD, 24));
        g.setColor(Color.BLACK);
        g.drawString("$" + money, 622, 199);
        g.setColor(Color.YELLOW);
        g.drawString("$" + money, 620, 197);
    }

    public void registerClickListener(java.awt.Component component) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                double scaleX = (double) component.getWidth() / 1280;
                double scaleY = (double) component.getHeight() / 720;
                int gameX = (int) (e.getX() / scaleX);
                int gameY = (int) (e.getY() / scaleY);

                if (CLICKER_BOUNDS.contains(gameX, gameY)) {
                    GenerateMoney();
                    playClickAnimation();
                }
            }
        });
    }
    public void GenerateMoney() {
        money += 1;
    }

    public int getMoney() {
        return money;
    }
}