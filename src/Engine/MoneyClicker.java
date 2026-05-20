package Engine;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.File;
import javax.imageio.ImageIO;

public class MoneyClicker {
    private static BufferedImage moneyClicker;
    private String color;
    String level;

    public MoneyClicker(String color){
        level = color;
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

    public void GenerateMoney() {

    }
}

