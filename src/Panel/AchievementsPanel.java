package Panel;

import Panel.AchievementSystem.Achievement;
import Panel.AchievementSystem.Tier;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class AchievementsPanel extends JPanel {
    private final JFrame frame;
    private final AchievementSystem achievementSystem;

    public AchievementsPanel(JFrame frame) {
        this.frame = frame;
        this.achievementSystem = new AchievementSystem();
        achievementSystem.load(); // Load persisted unlocked achievements from JSON

        this.setPreferredSize(new Dimension(1280, 720));
        this.setBackground(new Color(15, 12, 22));
        this.setLayout(new BorderLayout());

        initUI();
    }

    private void initUI() {
        // ── 1. HEADER TITLE ──────────────────────────────────────────────────
        JPanel headerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setFont(new Font("Arial", Font.BOLD, 36));
                g2.setColor(Color.WHITE);
                g2.drawString("ACHIEVEMENTS", 40, 60);
                // Show count of unlocked vs total
                long unlockedCount = java.util.Arrays.stream(achievementSystem.getAllAchievements())
                        .filter(a -> achievementSystem.isUnlocked(a.id)).count();
                int total = achievementSystem.getAllAchievements().length;
                g2.setFont(new Font("Arial", Font.PLAIN, 18));
                g2.setColor(new Color(180, 160, 220));
                g2.drawString(unlockedCount + " / " + total + " Unlocked", 40, 85);
            }
        };
        headerPanel.setPreferredSize(new Dimension(1280, 100));
        headerPanel.setOpaque(false);
        this.add(headerPanel, BorderLayout.NORTH);

        // ── 2. SCROLLABLE CONTAINER FOR LIST ROWS ────────────────────────────
        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setBackground(new Color(22, 18, 30));
        listContainer.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        Achievement[] allAchievements = achievementSystem.getAllAchievements();
        for (Achievement ach : allAchievements) {
            boolean unlocked = achievementSystem.isUnlocked(ach.id);
            listContainer.add(createAchievementRow(ach, unlocked));
            listContainer.add(Box.createRigidArea(new Dimension(0, 12)));
        }

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(new Color(22, 18, 30));
        this.add(scrollPane, BorderLayout.CENTER);

        // ── 3. BOTTOM BACK BUTTON NAVIGATION ──────────────────────────────────
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 40, 20));
        footerPanel.setOpaque(false);

        JButton backButton = new JButton("BACK TO MENU") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(new Color(80, 50, 120));
                } else {
                    g2.setColor(new Color(50, 35, 75));
                }
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setFont(new Font("Arial", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), textX, textY);
                g2.dispose();
            }
        };
        backButton.setPreferredSize(new Dimension(180, 45));
        backButton.setContentAreaFilled(false);
        backButton.setBorderPainted(false);
        backButton.setFocusPainted(false);
        backButton.addActionListener(e -> Main.Main.goToMenu(frame));

        footerPanel.add(backButton);
        this.add(footerPanel, BorderLayout.SOUTH);
    }

    private JPanel createAchievementRow(Achievement ach, boolean unlocked) {
        JPanel row = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Row background — dimmed if locked
                g2.setColor(unlocked ? new Color(34, 28, 46) : new Color(22, 20, 30));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

                // Tier accent color (greyed if locked)
                Color tierColor;
                if (!unlocked) {
                    tierColor = new Color(80, 80, 80);
                } else {
                    switch (ach.tier) {
                        case BRONZE: tierColor = new Color(205, 127, 50); break;
                        case SILVER: tierColor = new Color(180, 180, 200); break;
                        case GOLD:   tierColor = new Color(255, 200, 40); break;
                        default:     tierColor = Color.WHITE; break;
                    }
                }

                // Left visual indicator bar
                g2.setColor(tierColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, 6, getHeight(), 6, 6));

                // Icon or lock symbol
                g2.setFont(new Font("Dialog", Font.PLAIN, 26));
                g2.setColor(unlocked ? Color.WHITE : new Color(100, 100, 100));
                g2.drawString(unlocked ? ach.icon : "\uD83D\uDD12", 25, 48);

                // Title
                g2.setFont(new Font("Arial", Font.BOLD, 18));
                g2.setColor(unlocked ? Color.WHITE : new Color(100, 100, 100));
                g2.drawString(unlocked ? ach.title : "???", 80, 35);

                // Description (hidden if locked)
                g2.setFont(new Font("Arial", Font.PLAIN, 13));
                g2.setColor(unlocked ? new Color(175, 168, 185) : new Color(80, 80, 80));
                g2.drawString(unlocked ? ach.description : "Keep playing to unlock", 80, 58);

                // Tier badge on right
                g2.setFont(new Font("Arial", Font.BOLD, 12));
                g2.setColor(tierColor);
                g2.drawString(unlocked ? ach.tier.toString() : "LOCKED", getWidth() - 110, 46);

                // Unlocked checkmark
                if (unlocked) {
                    g2.setColor(new Color(80, 220, 120));
                    g2.setFont(new Font("Dialog", Font.BOLD, 18));
                    g2.drawString("\u2713", getWidth() - 140, 30);
                }
            }
        };
        row.setMaximumSize(new Dimension(1200, 80));
        row.setPreferredSize(new Dimension(1100, 80));
        row.setOpaque(false);
        return row;
    }
}
