// MainMenuPanel.java

package Panel;

import App.Main;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;

public class MainMenuPanel extends JPanel {
    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 720;
    private int renderWidth = SCREEN_WIDTH;
    private int renderHeight = SCREEN_HEIGHT;
    private int renderXOffset = 0;
    private int renderYOffset = 0;

    public MainMenuPanel(Main mainFrame) throws IOException {
        setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        JButton startButton = new JButton();
        startButton.addActionListener(e -> mainFrame.showCharacterSelect());

        Image img = ImageIO.read(getClass().getResource("/assets/images/ui/testButton.png"));
        startButton.setIcon(new ImageIcon(img));

        gbc.gridy = 0;
        add(startButton, gbc);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustViewport();
            }
        });
    }

    private void adjustViewport() {
        Dimension size = getSize();
        double aspectRatio = (double) SCREEN_WIDTH / SCREEN_HEIGHT;
        double windowRatio = (double) size.width / size.height;

        if (windowRatio > aspectRatio) {
            renderHeight = size.height;
            renderWidth = (int) (size.height * aspectRatio);
            renderXOffset = (size.width - renderWidth) / 2;
            renderYOffset = 0;
        } else {
            renderWidth = size.width;
            renderHeight = (int) (size.width / aspectRatio);
            renderXOffset = 0;
            renderYOffset = (size.height - renderHeight) / 2;
        }
    }
}