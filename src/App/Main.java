package App;

import Engine.Music;
import Panel.*;
import java.io.IOException;
import javax.swing.*;

public final class Main extends JFrame {
    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 720;

    public Main() throws IOException {
        setTitle("Project: Economics");
        setSize(SCREEN_WIDTH, SCREEN_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        try {
            Music menuMusic = new Music("assets/music/mainMenu");
            menuMusic.play();
        } catch (Exception e) {
            e.printStackTrace();
        }

        showMainMenu();
        setVisible(true);
    }

    public void showMainMenu() throws IOException {
        setContentPane(new MainMenuPanel(this));
        revalidate();
    }

    public void showGame() {
        try {
            GamePanel gamePanel = new GamePanel();
            setContentPane(gamePanel);
            revalidate();
            gamePanel.requestFocusInWindow();
            gamePanel.repaint();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startGame() throws IOException {
        showGame();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Main();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });
    }
}