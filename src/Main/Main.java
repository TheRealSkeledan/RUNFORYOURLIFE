package Main;

import Panel.GamePanel;
import javax.swing.*;
import java.awt.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("RUN FOR YOUR LIFE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);

            GamePanel gamePanel;
            try {
                gamePanel = new GamePanel();
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            frame.add(gamePanel);
            frame.pack();
            frame.setMinimumSize(new Dimension(640, 420));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            gamePanel.requestFocusInWindow();
        });
    }
}