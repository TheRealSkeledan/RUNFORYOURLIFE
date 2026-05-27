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

        // Set dimensions to match your window resolution
        this.setPreferredSize(new Dimension(1280, 720));
        this.setBackground(new Color(15, 12, 22)); // Ambient dark background
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

        // Dynamically pull and generate rows for all items inside the enum catalogue
        Achievement[] allAchievements = achievementSystem.getAllAchievements();
        for (Achievement ach : allAchievements) {
            listContainer.add(createAchievementRow(ach));
            listContainer.add(Box.createRigidArea(new Dimension(0, 12))); // Gap between items
        }

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16); // Smooth scrolling speed
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
        
        // Return to main menu via structural panel route
        backButton.addActionListener(e -> Main.Main.goToMenu(frame));

        footerPanel.add(backButton);
        this.add(footerPanel, BorderLayout.SOUTH);
    }

    private JPanel createAchievementRow(Achievement ach) {
        JPanel row = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Row background box
                g2.setColor(new Color(34, 28, 46));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));

                // Map standard enum states to visual accent color bars
                Color tierColor;
                switch (ach.tier) {
                    case BRONZE: tierColor = new Color(205, 127, 50); break;
                    case SILVER: tierColor = new Color(180, 180, 200); break;
                    case GOLD:   tierColor = new Color(255, 200, 40); break;
                    default:     tierColor = Color.WHITE; break;
                }

                // Left visual indicator bar
                g2.setColor(tierColor);
                g2.fill(new RoundRectangle2D.Float(0, 0, 6, getHeight(), 6, 6));

                // Icon rendering using Dialog font family for cross-OS unicode fallback support
                g2.setFont(new Font("Dialog", Font.PLAIN, 26));
                g2.setColor(Color.WHITE);
                g2.drawString(ach.icon, 25, 48);

                // Meta Info: Title and Description String positions
                g2.setFont(new Font("Arial", Font.BOLD, 18));
                g2.drawString(ach.title, 80, 35);

                g2.setFont(new Font("Arial", Font.PLAIN, 13));
                g2.setColor(new Color(175, 168, 185));
                g2.drawString(ach.description, 80, 58);

                // Right aligned Tier metadata string
                g2.setFont(new Font("Arial", Font.BOLD, 12));
                g2.setColor(tierColor);
                g2.drawString(ach.tier.toString(), getWidth() - 110, 46);
            }
        };
        row.setMaximumSize(new Dimension(1200, 80));
        row.setPreferredSize(new Dimension(1100, 80));
        row.setOpaque(false);
        return row;
    }
}