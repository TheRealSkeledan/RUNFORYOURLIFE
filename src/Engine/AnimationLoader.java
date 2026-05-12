// AnimationLoader.java

package Engine;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

// Unless you're sure you know what you are doing, I won't recommend changing this code
// This helps load spritesheets with a XML as an animation
public class AnimationLoader {

    public static Map<String, BufferedImage[]> loadAnimations(String characterName) {
        Map<String, BufferedImage[]> animations = new HashMap<>();

        try {
            String basePath = "assets/images/characters/";
            File xmlFile = new File(basePath + characterName + ".xml");
            File spritesheetFile = new File(basePath + characterName + ".png");

            if (!xmlFile.exists()) {
                throw new Exception("XML file not found: " + xmlFile.getAbsolutePath());
            }
            if (!spritesheetFile.exists()) {
                throw new Exception("Spritesheet file not found: " + spritesheetFile.getAbsolutePath());
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlFile);

            doc.getDocumentElement().normalize();

            NodeList characterNodes = doc.getElementsByTagName("character");
            Node characterNode = null;
            for (int i = 0; i < characterNodes.getLength(); i++) {
                Element element = (Element) characterNodes.item(i);
                if (element.getAttribute("name").equalsIgnoreCase(characterName)) {
                    characterNode = element;
                    break;
                }
            }

            if (characterNode == null) {
                throw new Exception("Character '" + characterName + "' not found in XML.");
            }

            Element characterElement = (Element) characterNode;
            int spriteWidth = Integer.parseInt(characterElement.getAttribute("spriteWidth"));
            int spriteHeight = Integer.parseInt(characterElement.getAttribute("spriteHeight"));

            BufferedImage spritesheet = ImageIO.read(spritesheetFile);

            NodeList animationNodes = characterElement.getElementsByTagName("animation");
            for (int i = 0; i < animationNodes.getLength(); i++) {
                Element animationElement = (Element) animationNodes.item(i);
                String animationName = animationElement.getAttribute("name");

                NodeList frameNodes = animationElement.getElementsByTagName("frame");
                BufferedImage[] frames = new BufferedImage[frameNodes.getLength()];

                for (int j = 0; j < frameNodes.getLength(); j++) {
                    Element frameElement = (Element) frameNodes.item(j);
                    int x = Integer.parseInt(frameElement.getAttribute("x"));
                    int y = Integer.parseInt(frameElement.getAttribute("y"));

                    if (x + spriteWidth > spritesheet.getWidth() || y + spriteHeight > spritesheet.getHeight()) {
                        throw new IllegalArgumentException("Frame coordinates out of bounds: x=" + x + ", y=" + y);
                    }

                    frames[j] = spritesheet.getSubimage(x, y, spriteWidth, spriteHeight);
                }

                animations.put(animationName, frames);
            }

        } catch (Exception e) {
            System.err.println("Error loading animations for " + characterName);
            e.printStackTrace();
        }

        return animations;
    }
}
