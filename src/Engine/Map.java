package Engine;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

// This is where the map is created for the game.
public class Map {
    private static String name;
    private static BufferedImage background;

    public static void setName(String n) {
        name = n;

        try {
            System.out.println(name);

            background = ImageIO.read(new File("assets/images/backgrounds/" + name + "-back.png"));
        } catch (IOException e) {
            System.out.println("Path couldn't find the file, " + name);
        }
    }

    public static String getName() {
        return name;
    }


    public static BufferedImage getBackground() {
        return background;
    }

    public static void drawBackStage(Graphics g, int x, int y) {
        g.drawImage(getBackground(), x, y, null);
    }

    public static int getWidth() {
        return background != null ? background.getWidth() : 0;
    }

    public static int getHeight() {
        return background != null ? background.getHeight() : 0;
    }
}