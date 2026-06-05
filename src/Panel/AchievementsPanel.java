package Panel;

import Panel.AchievementSystem.Achievement;
import Panel.AchievementSystem.Tier;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class AchievementsPanel extends JPanel {

    private static final int W          = 1280;
    private static final int H          = 720;
    private static final int ICON_SIZE  = 88;       // base icon size
    private static final int HOVER_LIFT = 8;        // pixels the icon rises on hover
    private static final int COLS       = 9;
    private static final int H_GAP      = 16;       // horizontal gap between icons
    private static final int V_GAP      = 30;       // vertical gap (space for label)
    private static final int PADDING    = 40;
    private static final String IMG_ROOT = "assets/images/achievements/";

    // ── State ─────────────────────────────────────────────────────────────────
    private final JFrame            frame;
    private final AchievementSystem achievementSystem;
    private String  hoveredId  = null;
    private String  selectedId = null;   // drives the detail overlay

    // ── Image cache ───────────────────────────────────────────────────────────
    private static final Map<String, BufferedImage> IMG_CACHE  = new HashMap<>();
    private static final Map<String, BufferedImage> GREY_CACHE = new HashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────
    public AchievementsPanel(JFrame frame) {
        this.frame = frame;
        this.achievementSystem = new AchievementSystem();
        achievementSystem.load();

        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(15, 12, 22));
        setLayout(new BorderLayout());
        initUI();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI Construction
    // ─────────────────────────────────────────────────────────────────────────

    private void initUI() {
        // Header
        JPanel header = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintHeader((Graphics2D) g, getWidth(), getHeight());
            }
        };
        header.setPreferredSize(new Dimension(W, 100));
        header.setOpaque(false);
        add(header, BorderLayout.NORTH);

        // Scrollable grid
        JPanel grid = buildGrid();
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        scroll.getViewport().setBackground(new Color(15, 12, 22));
        scroll.setBackground(new Color(15, 12, 22));
        add(scroll, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, PADDING, 14));
        footer.setOpaque(false);
        footer.add(buildBackButton());
        add(footer, BorderLayout.SOUTH);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Header
    // ─────────────────────────────────────────────────────────────────────────

    private void paintHeader(Graphics2D g2, int w, int h) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        GradientPaint bg = new GradientPaint(0, 0, new Color(22, 16, 34), 0, h, new Color(15, 12, 22));
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);
        g2.setPaint(null);

        g2.setColor(new Color(80, 60, 120, 100));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(PADDING, h - 1, w - PADDING, h - 1);
        g2.setStroke(new BasicStroke(1f));

        Achievement[] all      = achievementSystem.getAllAchievements();
        long          unlocked = java.util.Arrays.stream(all).filter(a -> achievementSystem.isUnlocked(a.id)).count();
        int           total    = all.length;

        // Title
        g2.setFont(new Font("Arial", Font.BOLD, 36));
        g2.setColor(Color.WHITE);
        g2.drawString("Achievements", PADDING, 52);

        // Progress label + bar
        String prog = unlocked + " / " + total + "  (" + (total == 0 ? 0 : (100 * unlocked / total)) + "%)";
        g2.setFont(new Font("Arial", Font.BOLD, 16));
        g2.setColor(new Color(210, 200, 240));
        g2.drawString("PROGRESS", PADDING, 78);

        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(prog, w - fm.stringWidth(prog) - PADDING, 78);

        int barX = PADDING + 100;
        int barW = w - barX - PADDING - fm.stringWidth(prog) - 16;
        int barY = 68; int barH = 10;
        g2.setColor(new Color(40, 30, 60));
        g2.fillRoundRect(barX, barY, barW, barH, barH, barH);
        if (total > 0 && unlocked > 0) {
            int fill = (int)(barW * (float) unlocked / total);
            GradientPaint fp = new GradientPaint(barX, 0, new Color(160, 100, 255), barX + fill, 0, new Color(200, 140, 255));
            g2.setPaint(fp);
            g2.fillRoundRect(barX, barY, Math.max(barH, fill), barH, barH, barH);
            g2.setPaint(null);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grid
    // ─────────────────────────────────────────────────────────────────────────

    private JPanel buildGrid() {
        Achievement[] all  = achievementSystem.getAllAchievements();
        int rows  = (int) Math.ceil((double) all.length / COLS);
        // Each cell: ICON_SIZE tall + HOVER_LIFT headroom + V_GAP for label
        int cellH = ICON_SIZE + HOVER_LIFT + V_GAP;
        int gridH = PADDING + rows * cellH + PADDING;

        JPanel panel = new JPanel(null) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                paintGrid((Graphics2D) g, all);
            }
            @Override public Dimension getPreferredSize() {
                return new Dimension(W - 20, Math.max(H - 170, gridH));
            }
        };
        panel.setOpaque(false);

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) {
                String hit = hitTest(all, e.getX(), e.getY(), panel.getWidth());
                if (!Objects.equals(hit, hoveredId)) {
                    hoveredId = hit;
                    panel.repaint();
                }
            }
        });
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mouseExited(MouseEvent e) {
                hoveredId = null;
                panel.repaint();
            }
            @Override public void mouseClicked(MouseEvent e) {
                String hit = hitTest(all, e.getX(), e.getY(), panel.getWidth());
                if (hit != null) {
                    selectedId = hit;
                    // Repaint the whole AchievementsPanel so the overlay draws on top
                    AchievementsPanel.this.repaint();
                }
            }
        });

        return panel;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Grid painting
    // ─────────────────────────────────────────────────────────────────────────

    private void paintGrid(Graphics2D g2, Achievement[] all) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int panelW = getWidth() > 0 ? getWidth() : W;
        int cellW  = ICON_SIZE + H_GAP;
        int cellH  = ICON_SIZE + HOVER_LIFT + V_GAP;
        int totalW = COLS * cellW - H_GAP;
        int startX = (panelW - totalW) / 2;
        int startY = PADDING + HOVER_LIFT;  // extra top space so lifted icons don't clip

        for (int i = 0; i < all.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int cx  = startX + col * cellW;           // top-left x of icon
            int cy  = startY + row * cellH;            // baseline top-left y (un-hovered)
            drawIcon(g2, all[i], cx, cy);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Single icon
    // ─────────────────────────────────────────────────────────────────────────

    private void drawIcon(Graphics2D g2, Achievement ach, int x, int y) {
        boolean unlocked = achievementSystem.isUnlocked(ach.id);
        boolean hovered  = ach.id.equals(hoveredId);

        // Lift on hover
        int drawY = hovered ? y - HOVER_LIFT : y;

        BufferedImage img = unlocked
                ? loadImage(ach.id)
                : loadGrey(ach.id);

        // Drop shadow when hovered
        if (hovered) {
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillRect(x + 5, drawY + 6, ICON_SIZE, ICON_SIZE);
        }

        // Draw the image (or dark placeholder)
        if (img != null) {
            if (!unlocked) {
                // Dimmed greyscale base
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
                g2.drawImage(img, x, drawY, ICON_SIZE, ICON_SIZE, null);
                g2.setComposite(old);
                // Dark overlay
                g2.setColor(new Color(10, 8, 20, 140));
                g2.fillRect(x, drawY, ICON_SIZE, ICON_SIZE);
            } else {
                g2.drawImage(img, x, drawY, ICON_SIZE, ICON_SIZE, null);
            }
        } else {
            // No image at all — solid dark square
            g2.setColor(new Color(28, 22, 42));
            g2.fillRect(x, drawY, ICON_SIZE, ICON_SIZE);
        }

        // Locked: "?" overlay
        if (!unlocked) {
            g2.setFont(new Font("Arial", Font.BOLD, 34));
            g2.setColor(new Color(160, 150, 190));
            FontMetrics fm = g2.getFontMetrics();
            String q = "?";
            g2.drawString(q,
                    x + (ICON_SIZE - fm.stringWidth(q)) / 2,
                    drawY + (ICON_SIZE + fm.getAscent()) / 2 - 4);
        }

        // Thin border — brighter on hover
        if (hovered && unlocked) {
            g2.setColor(new Color(255, 255, 255, 180));
            g2.setStroke(new BasicStroke(2f));
        } else {
            Color tier = unlocked ? tierColor(ach.tier) : new Color(60, 50, 80);
            g2.setColor(new Color(tier.getRed(), tier.getGreen(), tier.getBlue(), unlocked ? 160 : 80));
            g2.setStroke(new BasicStroke(1.5f));
        }
        g2.drawRect(x, drawY, ICON_SIZE - 1, ICON_SIZE - 1);
        g2.setStroke(new BasicStroke(1f));

        // Name label below icon (always shown, brighter on hover)
        String label = unlocked ? shorten(ach.title, 11) : "???";
        g2.setFont(new Font("Arial", Font.BOLD, hovered ? 11 : 10));
        g2.setColor(hovered
                ? (unlocked ? Color.WHITE : new Color(130, 120, 160))
                : (unlocked ? new Color(200, 195, 225) : new Color(90, 80, 115)));
        FontMetrics fm2 = g2.getFontMetrics();
        int lx = x + (ICON_SIZE - fm2.stringWidth(label)) / 2;
        int ly = drawY + ICON_SIZE + 14;
        g2.drawString(label, lx, ly);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Detail overlay — drawn on top of everything via paintChildren override
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g);
        if (selectedId != null) {
            Graphics2D g2 = (Graphics2D) g.create();
            drawDetailOverlay(g2, selectedId);
            g2.dispose();
        }
    }

    private void drawDetailOverlay(Graphics2D g2, String id) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Find achievement
        Achievement ach = null;
        for (Achievement a : achievementSystem.getAllAchievements())
            if (a.id.equals(id)) { ach = a; break; }
        if (ach == null) return;

        boolean unlocked = achievementSystem.isUnlocked(id);

        // ── Dim background ────────────────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRect(0, 0, getWidth(), getHeight());

        // ── Card dimensions ───────────────────────────────────────────────────
        int cardW = 480;
        int imgSz = 220;          // large image size
        int cardH = imgSz + 180;  // image + text area
        int cardX = (getWidth()  - cardW) / 2;
        int cardY = (getHeight() - cardH) / 2;

        Color accent = unlocked ? tierColor(ach.tier) : new Color(80, 70, 110);

        // ── Card shadow ───────────────────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 140));
        g2.fillRoundRect(cardX + 8, cardY + 8, cardW, cardH, 18, 18);

        // ── Card background ───────────────────────────────────────────────────
        GradientPaint cardBg = new GradientPaint(cardX, cardY, new Color(28, 20, 44),
                cardX, cardY + cardH, new Color(18, 14, 30));
        g2.setPaint(cardBg);
        g2.fillRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2.setPaint(null);

        // ── Card border ───────────────────────────────────────────────────────
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
        g2.setStroke(new BasicStroke(2f));
        g2.drawRoundRect(cardX, cardY, cardW, cardH, 18, 18);
        g2.setStroke(new BasicStroke(1f));

        // ── Top accent bar ────────────────────────────────────────────────────
        g2.setColor(accent);
        g2.fillRoundRect(cardX, cardY, cardW, 5, 18, 18);
        g2.fillRect(cardX, cardY + 2, cardW, 3);

        // ── Large image (centred in top portion) ──────────────────────────────
        int imgX = cardX + (cardW - imgSz) / 2;
        int imgY = cardY + 24;

        BufferedImage img = unlocked ? loadImage(id) : loadGrey(id);
        if (img != null) {
            if (!unlocked) {
                Composite old = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.4f));
                g2.drawImage(img, imgX, imgY, imgSz, imgSz, null);
                g2.setComposite(old);
                g2.setColor(new Color(10, 8, 20, 120));
                g2.fillRect(imgX, imgY, imgSz, imgSz);
            } else {
                g2.drawImage(img, imgX, imgY, imgSz, imgSz, null);
            }
        } else {
            g2.setColor(new Color(30, 24, 48));
            g2.fillRect(imgX, imgY, imgSz, imgSz);
        }

        // Thin border around image
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawRect(imgX, imgY, imgSz - 1, imgSz - 1);
        g2.setStroke(new BasicStroke(1f));

        // Locked "?" over image
        if (!unlocked) {
            g2.setFont(new Font("Arial", Font.BOLD, 72));
            g2.setColor(new Color(180, 170, 210, 200));
            FontMetrics fm = g2.getFontMetrics();
            String q = "?";
            g2.drawString(q,
                    imgX + (imgSz - fm.stringWidth(q)) / 2,
                    imgY + (imgSz + fm.getAscent()) / 2 - 10);
        }

        // ── Text block ────────────────────────────────────────────────────────
        int textY = imgY + imgSz + 18;
        int textX = cardX + 28;
        int textW = cardW - 56;

        // "ACHIEVEMENT UNLOCKED" / "LOCKED" label
        String statusLabel = unlocked ? "ACHIEVEMENT UNLOCKED" : "LOCKED";
        g2.setFont(new Font("Arial", Font.BOLD, 11));
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 200));
        g2.drawString(statusLabel, textX, textY);
        textY += 22;

        // Title
        g2.setFont(new Font("Arial", Font.BOLD, 22));
        g2.setColor(unlocked ? Color.WHITE : new Color(130, 120, 160));
        g2.drawString(unlocked ? ach.title : "???", textX, textY);
        textY += 26;

        // Description (word-wrap)
        String desc = unlocked ? ach.description : "Keep playing to unlock this achievement.";
        g2.setFont(new Font("Arial", Font.PLAIN, 13));
        g2.setColor(new Color(180, 170, 210));
        for (String line : wordWrap(desc, g2.getFontMetrics(), textW)) {
            g2.drawString(line, textX, textY);
            textY += 18;
        }

        // Tier badge (bottom-right of card)
        if (unlocked) {
            String tierStr = ach.tier.toString();
            g2.setFont(new Font("Arial", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int bw = fm.stringWidth(tierStr) + 20;
            int bh = 24;
            int bx = cardX + cardW - bw - 16;
            int by = cardY + cardH - bh - 14;
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
            g2.fillRoundRect(bx, by, bw, bh, 8, 8);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 180));
            g2.setStroke(new BasicStroke(1.2f));
            g2.drawRoundRect(bx, by, bw, bh, 8, 8);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(accent);
            g2.drawString(tierStr, bx + 10, by + bh - 6);
        }

        // ── Close hint ────────────────────────────────────────────────────────
        g2.setFont(new Font("Arial", Font.PLAIN, 11));
        g2.setColor(new Color(120, 110, 150));
        String hint = "Click anywhere to close";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(hint, (getWidth() - fm.stringWidth(hint)) / 2, cardY + cardH + 22);

        // ── Close listener (one-shot, added once per open) ────────────────────
        // We handle this via the panel-level mouse listener below
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mouse click to close overlay — attached to this panel
    // ─────────────────────────────────────────────────────────────────────────

    {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (selectedId != null) {
                    selectedId = null;
                    repaint();
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hit testing (grid coordinates)
    // ─────────────────────────────────────────────────────────────────────────

    private String hitTest(Achievement[] all, int mx, int my, int panelW) {
        int cellW  = ICON_SIZE + H_GAP;
        int cellH  = ICON_SIZE + HOVER_LIFT + V_GAP;
        int totalW = COLS * cellW - H_GAP;
        int startX = (panelW - totalW) / 2;
        int startY = PADDING + HOVER_LIFT;

        for (int i = 0; i < all.length; i++) {
            int col = i % COLS;
            int row = i / COLS;
            int ix  = startX + col * cellW;
            int iy  = startY + row * cellH - HOVER_LIFT; // extend hit upward for lifted state
            if (mx >= ix && mx <= ix + ICON_SIZE && my >= iy && my <= iy + ICON_SIZE + HOVER_LIFT)
                return all[i].id;
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Back button
    // ─────────────────────────────────────────────────────────────────────────

    private JButton buildBackButton() {
        JButton btn = new JButton("BACK TO MENU") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(80, 50, 120) : new Color(50, 35, 75));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setFont(new Font("Arial", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                        (getWidth()  - fm.stringWidth(getText())) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(180, 44));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.addActionListener(e -> Main.Main.goToMenu(frame));
        return btn;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Image helpers
    // ─────────────────────────────────────────────────────────────────────────

    private BufferedImage loadImage(String id) {
        if (IMG_CACHE.containsKey(id)) return IMG_CACHE.get(id);
        try {
            File f = new File(IMG_ROOT + id + ".png");
            BufferedImage img = f.exists() ? ImageIO.read(f) : null;
            IMG_CACHE.put(id, img);
            return img;
        } catch (Exception e) { IMG_CACHE.put(id, null); return null; }
    }

    private BufferedImage loadGrey(String id) {
        String key = id + "_grey";
        if (GREY_CACHE.containsKey(key)) return GREY_CACHE.get(key);
        BufferedImage src = loadImage(id);
        if (src == null) { GREY_CACHE.put(key, null); return null; }
        BufferedImage grey = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics gfx = grey.getGraphics();
        gfx.drawImage(src, 0, 0, null);
        gfx.dispose();
        GREY_CACHE.put(key, grey);
        return grey;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Utilities
    // ─────────────────────────────────────────────────────────────────────────

    private Color tierColor(Tier tier) {
        return switch (tier) {
            case BRONZE -> new Color(205, 127, 50);
            case SILVER -> new Color(180, 180, 200);
            case GOLD   -> new Color(255, 200, 40);
        };
    }

    private String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private java.util.List<String> wordWrap(String text, FontMetrics fm, int maxW) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String test = cur.isEmpty() ? w : cur + " " + w;
            if (fm.stringWidth(test) > maxW) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }
}
