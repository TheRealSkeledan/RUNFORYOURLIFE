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
    private static final int FRAME_DURATION = 5; // frames per animation frame

    String level;
    private int money = 0;

    // Animation state
    private Map<String, BufferedImage[]> animations;
    private boolean isAnimating = false;
    private int currentFrame = 0;
    private int frameTick = 0;

    public MoneyClicker(String color) {
        level = color;
        // Name must match your XML character name and filename in assets/images/characters/
        animations = AnimationLoader.loadAnimations("MoneyButton");
    }

    public static void createClicker() {
        try {
            moneyClicker = ImageIO.read(new File("assets/images/ui/MoneyButton.png"));
        } catch (IOException e) {
            System.out.println("Failed to load Money image");
        }
    }

    public static void drawMoneyClicker(Graphics g) {
        g.drawImage(moneyClicker, 490, 210, 300, 300, null);
    }

    // Call this every frame from paintComponent instead of drawMoneyClicker
    public void drawAnimatedClicker(Graphics g) {
        BufferedImage[] clickFrames = animations.get("click");

        // If animation is playing and frames exist, draw the animation
        if (isAnimating && clickFrames != null && clickFrames.length > 0) {
            frameTick++;
            if (frameTick >= FRAME_DURATION) {
                frameTick = 0;
                currentFrame++;
                if (currentFrame >= clickFrames.length) {
                    // Animation finished — reset back to idle
                    currentFrame = 0;
                    isAnimating = false;
                }
            }

            if (isAnimating) {
                g.drawImage(clickFrames[currentFrame], 490, 210, 300, 300, null);
                return;
            }
        }

        // Default: draw the static idle image
        g.drawImage(moneyClicker, 490, 210, 300, 300, null);
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

    private void playClickAnimation() {
        BufferedImage[] clickFrames = animations.get("click");
        if (clickFrames != null && clickFrames.length > 0) {
            isAnimating = true;
            currentFrame = 0;
            frameTick = 0;
        }
    }

    public void GenerateMoney() {
        money += 1;
    }

    public int getMoney() {
        return money;
    }
}