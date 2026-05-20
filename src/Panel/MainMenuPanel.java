package Panel;

import App.Main;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;

public class MainMenuPanel extends JPanel {
    private static final int SCREEN_WIDTH  = 1280;
    private static final int SCREEN_HEIGHT = 720;

    private static final Color BG_DARK          = new Color(10,  12,  20);
    private static final Color BG_MID           = new Color(18,  22,  38);
    private static final Color ACCENT_GOLD      = new Color(212, 175, 95);
    private static final Color TEXT_MUTED       = new Color(130, 120, 110);
    private static final Color TEXT_PRIMARY     = new Color(230, 220, 200);
    private static final Color DIVIDER          = new Color(212, 175, 95, 45);

    private int renderWidth   = SCREEN_WIDTH;
    private int renderHeight  = SCREEN_HEIGHT;
    private int renderXOffset = 0;
    private int renderYOffset = 0;

    public MainMenuPanel(Main mainFrame) throws IOException {
        setLayout(null);
        setBackground(BG_DARK);

        JLabel titleLabel = new JLabel("Project: Economics", SwingConstants.CENTER);
        titleLabel.setFont(loadFont("Cinzel", Font.BOLD, 72f));
        titleLabel.setForeground(ACCENT_GOLD);
        titleLabel.setBounds(0, 120, SCREEN_WIDTH, 90);
        add(titleLabel);

        JSeparator divider = new JSeparator(SwingConstants.HORIZONTAL) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int mid = getWidth() / 2;
                g2.setPaint(new GradientPaint(mid - 200, 0, new Color(0,0,0,0), mid, 0, ACCENT_GOLD));
                g2.fillRect(mid - 200, 0, 200, 1);
                g2.setPaint(new GradientPaint(mid, 0, ACCENT_GOLD, mid + 200, 0, new Color(0,0,0,0)));
                g2.fillRect(mid, 0, 200, 1);
                g2.dispose();
            }
        };
        divider.setBounds(SCREEN_WIDTH / 2 - 200, 252, 400, 2);
        divider.setOpaque(false);
        add(divider);

        // ── Load button images ─────────────────────────────────────────────
        Image imgPlay         = ImageIO.read(getClass().getResource("/images/ui/PlayButton.png"));
        Image imgAchievements = ImageIO.read(getClass().getResource("/images/ui/AchievementsButton.png"));
        Image imgCredits      = ImageIO.read(getClass().getResource("/images/ui/CreditsButton.png"));

        // ── Buttons ────────────────────────────────────────────────────────
        int btnW = 300, btnH = 64, btnX = (SCREEN_WIDTH - btnW) / 2;
        int gap   = 16;
        int startY = 285;

        ImageMenuButton playBtn = new ImageMenuButton(imgPlay, btnW, btnH);
        playBtn.setBounds(btnX, startY, btnW, btnH);
        playBtn.addActionListener(e -> mainFrame.showGame());
        add(playBtn);

        ImageMenuButton achievementsBtn = new ImageMenuButton(imgAchievements, btnW, btnH);
        achievementsBtn.setBounds(btnX, startY + (btnH + gap), btnW, btnH);
        achievementsBtn.addActionListener(e -> { /* TODO: show achievements */ });
        add(achievementsBtn);

        ImageMenuButton creditsBtn = new ImageMenuButton(imgCredits, btnW, btnH);
        creditsBtn.setBounds(btnX, startY + 2 * (btnH + gap), btnW, btnH);
        creditsBtn.addActionListener(e -> { /* TODO: show credits */ });
        add(creditsBtn);

        // ── Version label ──────────────────────────────────────────────────
        JLabel versionLabel = new JLabel("v1.0.0", SwingConstants.RIGHT);
        versionLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
        versionLabel.setForeground(TEXT_MUTED);
        versionLabel.setBounds(SCREEN_WIDTH - 80, SCREEN_HEIGHT - 28, 68, 18);
        add(versionLabel);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                adjustViewport();
                repositionChildren();
            }
        });
    }

    // ── Image-backed button with hover glow + scale ────────────────────────
    private static class ImageMenuButton extends JButton {
        private final Image baseImage;
        private final int   baseW, baseH;
        private boolean hovered = false;

        // Slightly larger image rendered on hover (scale factor)
        private static final double HOVER_SCALE = 1.04;

        private static final Color GLOW_COLOR  = new Color(212, 175, 95, 55);
        private static final Color GLOW_SHADOW = new Color(0, 0, 0, 80);

        ImageMenuButton(Image img, int w, int h) {
            this.baseImage = img;
            this.baseW = w;
            this.baseH = h;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(w, h));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,        RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,       RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,           RenderingHints.VALUE_RENDER_QUALITY);

            int w = getWidth(), h = getHeight();

            if (hovered) {
                // Drop shadow
                g2.setColor(GLOW_SHADOW);
                g2.fillRoundRect(3, 6, w - 2, h - 2, 12, 12);

                // Gold glow halo behind image
                g2.setColor(GLOW_COLOR);
                g2.fillRoundRect(-3, -3, w + 6, h + 6, 14, 14);

                // Draw image scaled up slightly, centred
                int drawW = (int)(w * HOVER_SCALE);
                int drawH = (int)(h * HOVER_SCALE);
                int dx    = (w - drawW) / 2;
                int dy    = (h - drawH) / 2;
                g2.drawImage(baseImage, dx, dy, drawW, drawH, null);
            } else {
                // Normal: subtle drop shadow then image at full size
                g2.setColor(GLOW_SHADOW);
                g2.fillRoundRect(2, 4, w - 2, h - 2, 12, 12);
                g2.drawImage(baseImage, 0, 0, w, h, null);
            }

            g2.dispose();
        }
    }

    // ── Background painting ────────────────────────────────────────────────
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int x = renderXOffset, y = renderYOffset, w = renderWidth, h = renderHeight;

        // Deep radial gradient background
        g2.setPaint(new RadialGradientPaint(
                new Point2D.Float(x + w / 2f, y + h / 2f), w * 0.75f,
                new float[]{0f, 1f}, new Color[]{BG_MID, BG_DARK}
        ));
        g2.fillRect(x, y, w, h);

        // Soft gold top-glow (like a distant light source above the menu)
        g2.setPaint(new RadialGradientPaint(
                new Point2D.Float(x + w / 2f, y + 60f), w * 0.4f,
                new float[]{0f, 1f}, new Color[]{new Color(212, 175, 95, 22), new Color(0,0,0,0)}
        ));
        g2.fillRect(x, y, w, h);

        // Corner ornaments
        drawCornerOrnament(g2, x + 32,     y + 32,     false, false);
        drawCornerOrnament(g2, x + w - 32, y + 32,     true,  false);
        drawCornerOrnament(g2, x + 32,     y + h - 32, false, true);
        drawCornerOrnament(g2, x + w - 32, y + h - 32, true,  true);

        g2.dispose();
    }

    private void drawCornerOrnament(Graphics2D g2, int cx, int cy, boolean flipX, boolean flipY) {
        g2.setColor(DIVIDER);
        g2.setStroke(new BasicStroke(1f));
        int sx = flipX ? -1 : 1, sy = flipY ? -1 : 1;
        g2.drawLine(cx,          cy,          cx + sx * 30, cy);
        g2.drawLine(cx,          cy,          cx,           cy + sy * 30);
        g2.drawLine(cx - sx * 2, cy,          cx + sx * 6,  cy);
        g2.drawLine(cx,          cy - sy * 2, cx,           cy + sy * 6);
    }

    // ── Viewport scaling ───────────────────────────────────────────────────
    private void adjustViewport() {
        Dimension size = getSize();
        double ar = (double) SCREEN_WIDTH / SCREEN_HEIGHT;
        double wr = (double) size.width   / size.height;
        if (wr > ar) {
            renderHeight  = size.height;
            renderWidth   = (int)(size.height * ar);
            renderXOffset = (size.width - renderWidth) / 2;
            renderYOffset = 0;
        } else {
            renderWidth   = size.width;
            renderHeight  = (int)(size.width / ar);
            renderXOffset = 0;
            renderYOffset = (size.height - renderHeight) / 2;
        }
    }

    private void repositionChildren() {
        double sx = (double) renderWidth  / SCREEN_WIDTH;
        double sy = (double) renderHeight / SCREEN_HEIGHT;
        for (Component c : getComponents()) {
            if (!(c instanceof JComponent jc)) continue;
            Rectangle lb = (Rectangle) jc.getClientProperty("logicalBounds");
            if (lb == null) {
                lb = c.getBounds();
                jc.putClientProperty("logicalBounds", new Rectangle(lb));
            }
            c.setBounds(
                    renderXOffset + (int)(lb.x      * sx),
                    renderYOffset + (int)(lb.y      * sy),
                    (int)(lb.width  * sx),
                    (int)(lb.height * sy)
            );
        }
        repaint();
    }

    private static Font loadFont(String name, int style, float size) {
        try {
            var stream = MainMenuPanel.class.getResourceAsStream("/fonts/" + name + ".ttf");
            if (stream != null) return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(style, size);
        } catch (Exception ignored) {}
        return new Font("Georgia", style, (int) size);
    }
}