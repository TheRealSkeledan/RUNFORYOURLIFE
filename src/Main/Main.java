package Main;
import Panel.GamePanel;
import Panel.MenuPanel;
import Panel.SelectionScreen;
import Panel.AchievementsPanel;

import javax.swing.*;
import java.awt.*;

public class Main {
    /** MenuPanel → AchievementsPanel. Stops the menu music clip before switching. */
    public static void goToAchievements(JFrame frame, javax.sound.sampled.Clip menuClip) {
        // Stop menu music before leaving — avoids double-play when returning
        if (menuClip != null) {
            if (menuClip.isRunning()) menuClip.stop();
            menuClip.close();
        }
        AchievementsPanel achievementsPanel;
        try {
            achievementsPanel = new AchievementsPanel(frame);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        swapPanel(frame, achievementsPanel);
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            JFrame frame = new JFrame("RUN FOR YOUR LIFE");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(true);
            frame.getContentPane().setBackground(Color.BLACK);
            frame.setLayout(new BorderLayout());

            MenuPanel menuPanel;
            try {
                menuPanel = new MenuPanel(frame);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            frame.add(menuPanel, BorderLayout.CENTER);
            frame.pack();
            frame.setMinimumSize(new Dimension(640, 360));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            menuPanel.requestFocusInWindow();
        });
    }

    /** Menu → SelectionScreen (called after the click pulse fades). */
    public static void goToSelection(JFrame frame, javax.sound.sampled.Clip musicClip) {
        SelectionScreen sel;
        try {
            sel = new SelectionScreen(frame, musicClip);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        swapPanel(frame, sel);
    }

    /** SelectionScreen → GamePanel (called when Confirm is clicked). */
    public static void startGame(JFrame frame, String mode, javax.sound.sampled.Clip menuClip) {
        GamePanel gamePanel;
        try {
            // Pass the clip so GamePanel.tryLoadMusic() can stop it before starting game music
            gamePanel = new GamePanel(frame, mode, menuClip);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        swapPanel(frame, gamePanel);
        gamePanel.requestFocusInWindow();
    }

    /** GamePanel (ESC on game-over) → MenuPanel. */
    public static void goToMenu(JFrame frame) {
        MenuPanel menuPanel;
        try {
            menuPanel = new MenuPanel(frame);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        swapPanel(frame, menuPanel);
    }

    private static void swapPanel(JFrame frame, JPanel next) {
        frame.getContentPane().removeAll();
        frame.getContentPane().add(next, BorderLayout.CENTER);
        frame.revalidate();
        frame.repaint();
        next.requestFocusInWindow();
    }
}