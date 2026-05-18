// MainMenuPanel.java

package Panel;

import App.Main;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.Objects;
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

        JButton achievementsButton = new JButton();
        achievementsButton.addActionListener(e -> mainFrame.showCharacterSelect());

        JButton creditsButton = new JButton();
        creditsButton.addActionListener(e -> mainFrame.showCharacterSelect());

        Image img1 = ImageIO.read(getClass().getResource("/images/ui/PlayButton.png"));
        Image img2 = ImageIO.read(getClass().getResource("/images/ui/AchievementsButton.png"));
        Image img3 = ImageIO.read(getClass().getResource("/images/ui/CreditsButton.png"));

        startButton.setIcon(new ImageIcon(img1));
        achievementsButton.setIcon(new ImageIcon(img2));
        creditsButton.setIcon(new ImageIcon(img3));

        gbc.gridx = 0;
        add(startButton, gbc);
        add(achievementsButton, gbc);
        add(creditsButton, gbc);

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