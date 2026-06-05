package Panel;

import Engine.SoundEffect;
import Main.Main;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class SplashPanel extends JPanel {

    // ── Logical resolution ────────────────────────────────────────────────────
    private static final int W = 1280;
    private static final int H = 720;

    // ── Timing constants (seconds) ────────────────────────────────────────────
    private static final float POP_IN_END    = 0.35f;   // scale-up complete
    private static final float SETTLE_END    = 0.55f;   // overshoot settled
    private static final float HOLD_END      = 2.00f;   // hold complete
    private static final float SLIDE_END     = 2.80f;   // slide-out complete → transition

    // ── Asset paths ───────────────────────────────────────────────────────────
    private static final String LOGO_PATH = "assets/images/ui/GameEngineLogo.png";
    private static final String SFX_PATH  = "assets/sfx/LogoSting.wav";

    // ── State ─────────────────────────────────────────────────────────────────
    private float          t          = 0f;
    private boolean        done       = false;
    private final JFrame   frame;
    private final Timer    timer;

    // ── Assets ────────────────────────────────────────────────────────────────
    private BufferedImage  logo;
    private SoundEffect    sting;

    // ── Offscreen ─────────────────────────────────────────────────────────────
    private final BufferedImage offscreen;
    private final Graphics2D    og;

    // ─────────────────────────────────────────────────────────────────────────
    public SplashPanel(JFrame frame) {
        this.frame = frame;
        setBackground(Color.BLACK);
        setFocusable(true);

        offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        og = offscreen.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        og.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);

        // Load logo
        try { logo = ImageIO.read(new File(LOGO_PATH)); }
        catch (Exception e) { System.err.println("SplashPanel: could not load " + LOGO_PATH); }

        // Load & play sting SFX immediately — no menu music yet
        try {
            sting = new SoundEffect(SFX_PATH);
            sting.play();
        } catch (Exception e) {
            System.err.println("SplashPanel: SFX not found — " + SFX_PATH + " (continuing silently)");
        }

        timer = new Timer(16, e -> tick());
        timer.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    // ── Game loop tick ────────────────────────────────────────────────────────
    private void tick() {
        if (done) return;
        t += 0.016f;

        render();
        repaint();

        if (t >= SLIDE_END) {
            done = true;
            timer.stop();
            // Hand off to MenuPanel — music starts inside MenuPanel's constructor
            SwingUtilities.invokeLater(() -> {
                try {
                    MenuPanel menu = new MenuPanel(frame);
                    // Swap the panel directly (same helper Main uses)
                    frame.getContentPane().removeAll();
                    frame.getContentPane().add(menu, BorderLayout.CENTER);
                    frame.revalidate();
                    frame.repaint();
                    menu.requestFocusInWindow();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    private void render() {
        // Clear to black
        og.setColor(Color.BLACK);
        og.fillRect(0, 0, W, H);

        if (logo == null) return;

        // ── Compute logo draw size (fit within W-80 × H-80, maintain aspect) ─
        int natW = logo.getWidth();
        int natH = logo.getHeight();
        int maxW = W - 80;
        int maxH = H - 120;
        float aspect = (float) natW / natH;
        int baseW, baseH;
        if ((float) maxW / maxH > aspect) {
            baseH = maxH;
            baseW = Math.round(baseH * aspect);
        } else {
            baseW = maxW;
            baseH = Math.round(baseW / aspect);
        }

        // ── Compute animated scale & Y offset ─────────────────────────────────
        float scale;
        float slideOffsetY = 0f;

        if (t < POP_IN_END) {
            // Ease-out cubic pop-in: 0 → 1.08
            float p = t / POP_IN_END;
            float eased = 1f - (1f - p) * (1f - p) * (1f - p);
            scale = eased * 1.08f;
        } else if (t < SETTLE_END) {
            // Spring settle: 1.08 → 1.00
            float p = (t - POP_IN_END) / (SETTLE_END - POP_IN_END);
            scale = 1.08f - 0.08f * p;
        } else if (t < HOLD_END) {
            // Hold
            scale = 1.0f;
        } else {
            // Slide up — ease-in cubic
            float p = (t - HOLD_END) / (SLIDE_END - HOLD_END);
            float eased = p * p * p;
            scale = 1.0f;
            slideOffsetY = -eased * (H + baseH);   // slide fully off top
        }

        int drawW = Math.round(baseW * scale);
        int drawH = Math.round(baseH * scale);
        int drawX = (W - drawW) / 2;
        int drawY = (H - drawH) / 2 + (int) slideOffsetY;

        // Subtle red glow behind logo during hold
        if (t >= SETTLE_END && t < HOLD_END) {
            float holdProgress = (t - SETTLE_END) / (HOLD_END - SETTLE_END);
            // Glow fades in quickly then holds
            float glowAlpha = Math.min(1f, holdProgress * 3f);
            drawRadialGlow(W / 2, drawY + drawH / 2, drawW / 2 + 90,
                    new Color(180, 0, 0, (int)(50 * glowAlpha)));
        }

        og.drawImage(logo, drawX, drawY, drawW, drawH, null);
    }

    private void drawRadialGlow(int cx, int cy, int radius, Color colour) {
        if (radius <= 0) return;
        RadialGradientPaint p = new RadialGradientPaint(
                cx, cy, radius,
                new float[]{0f, 1f},
                new Color[]{colour, new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)});
        og.setPaint(p);
        og.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    // ── Swing paint ───────────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int pw = getWidth(), ph = getHeight();
        float scale = Math.min((float) pw / W, (float) ph / H);
        int dw = Math.round(W * scale), dh = Math.round(H * scale);
        int ox = (pw - dw) / 2,         oy = (ph - dh) / 2;
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, pw, ph);
        g.drawImage(offscreen, ox, oy, dw, dh, null);
    }
}
