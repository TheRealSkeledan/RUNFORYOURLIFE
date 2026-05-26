package Panel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BackgroundRenderer — handles scrolling backgrounds, floor grid,
 * alarm lights, scanlines, and the Hotel atmosphere effect.
 *
 * Call {@link #init()} once after construction, then call the draw*
 * methods each frame from GamePanel.render().
 */
public class BackgroundRenderer {

    // ── Constants shared with GamePanel ──────────────────────────────────────
    private static final int   W        = 1280;
    private static final int   H        = 720;
    private static final float GROUND_Y = 580f;
    private static final float BPM      = 160f;
    private static final float BEAT_HZ  = BPM / 60f;

    // ── Scrolling background ──────────────────────────────────────────────────
    private record BgSegment(BufferedImage img, float x, int drawW, int drawH) {}
    private final List<BgSegment> bgSegments = new ArrayList<>();
    private BufferedImage[] bgImages = new BufferedImage[0];
    private final Random rng;

    // ── Animated effect state ─────────────────────────────────────────────────
    private float alarmPhase     = 0f;
    private float scanlineOffset = 0f;

    // ── Hotel atmosphere ──────────────────────────────────────────────────────
    private float hotelPhase   = 0f;
    private float hotelFlicker = 0f;
    private final Random hotelRng = new Random();

    public BackgroundRenderer(Random rng) {
        this.rng = rng;
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    public void init() {
        loadBgImages();
        resetSegments(0f);
    }

    public void reset() {
        alarmPhase     = 0f;
        scanlineOffset = 0f;
        hotelPhase     = 0f;
        hotelFlicker   = 0f;
        resetSegments(0f);
    }

    private void loadBgImages() {
        File dir = new File("assets/images/backgrounds");
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(png|jpg|jpeg)"));
        if (files == null || files.length == 0) return;
        List<BufferedImage> list = new ArrayList<>();
        for (File f : files) {
            try { list.add(ImageIO.read(f)); } catch (Exception ignored) {}
        }
        bgImages = list.toArray(new BufferedImage[0]);
    }

    private void resetSegments(float startX) {
        bgSegments.clear();
        float fillX = startX;
        while (fillX < W + 400) {
            BgSegment seg = makeRandomSegment(fillX);
            bgSegments.add(seg);
            fillX += seg.drawW();
        }
    }

    private BgSegment makeRandomSegment(float x) {
        if (bgImages.length == 0) return new BgSegment(null, x, W, H);
        BufferedImage img = bgImages[rng.nextInt(bgImages.length)];
        int drawH = H;
        int drawW = img.getWidth() * drawH / img.getHeight();
        return new BgSegment(img, x, drawW, drawH);
    }

    // ── Per-frame draws ───────────────────────────────────────────────────────

    /**
     * Main background: scrolling images (or gradient fallback) + floor grid
     * + alarm lights + scanlines.
     */
    public void drawBackground(Graphics2D g, float obsSpeed, float dt,
                               GamePanel.Difficulty diff,
                               float timeLeft, float timeLimitTotal) {

        int skyH = (int) GROUND_Y;

        // Scrolling image segments
        if (bgImages.length > 0) {
            float scrollDelta = obsSpeed * dt * 3f;
            bgSegments.replaceAll(s -> new BgSegment(s.img(), s.x() - scrollDelta, s.drawW(), s.drawH()));
            bgSegments.removeIf(s -> s.x() + s.drawW() < 0);
            float rightEdge = bgSegments.isEmpty() ? 0f : bgSegments.getLast().x() + bgSegments.getLast().drawW();
            while (rightEdge < W + 200) {
                BgSegment seg = makeRandomSegment(rightEdge);
                bgSegments.add(seg);
                rightEdge += seg.drawW();
            }
            for (BgSegment seg : bgSegments)
                if (seg.img() != null)
                    g.drawImage(seg.img(), (int) seg.x(), 0, seg.drawW(), seg.drawH(), null);
        } else {
            // Gradient fallback
            Color top = switch (diff) {
                case HARDCORE -> new Color(18, 10, 10);
                case HOTEL    -> new Color(22,  8,  8);
                default       -> new Color(28, 24, 50);
            };
            Color bot = switch (diff) {
                case HARDCORE -> new Color(35, 12, 12);
                case HOTEL    -> new Color(45, 15, 10);
                default       -> new Color(55, 40, 80);
            };
            g.setPaint(new GradientPaint(0, 0, top, 0, skyH, bot));
            g.fillRect(0, 0, W, skyH);
        }

        drawFloorGrid(g, obsSpeed, dt);
        drawAlarmLights(g, dt, diff, timeLeft, timeLimitTotal);
        drawScanlines(g, dt);

        // Difficulty tints
        if (diff == GamePanel.Difficulty.HARDCORE) {
            g.setColor(new Color(60, 0, 0, 55));
            g.fillRect(0, 0, W, (int) GROUND_Y);
        }
        if (diff == GamePanel.Difficulty.HOTEL) {
            g.setColor(new Color(80, 0, 0, 70));
            g.fillRect(0, 0, W, (int) GROUND_Y);
        }
    }

    /** Hotel-specific atmospheric overlay — call after drawBackground. */
    public void drawHotelAtmosphere(Graphics2D g, float dt) {
        hotelPhase   += dt * 0.4f;
        if (hotelRng.nextFloat() < 0.008f)
            hotelFlicker = 0.08f + hotelRng.nextFloat() * 0.16f;
        hotelFlicker = Math.max(0, hotelFlicker - 0.012f);

        float pulse = 0.5f + 0.5f * (float) Math.sin(hotelPhase);
        g.setPaint(new RadialGradientPaint(W / 2f, H / 2f, W * 0.65f,
                new float[]{ 0.3f, 1.0f },
                new Color[]{ new Color(0, 0, 0, 0),
                        new Color(80, 0, 0, (int)(80 + pulse * 60)) }));
        g.fillRect(0, 0, W, H);

        if (hotelFlicker > 0) {
            g.setColor(new Color(0, 0, 0, (int)(hotelFlicker * 255)));
            g.fillRect(0, 0, W, H);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private float bgScrollX = 0f;  // tracks scrolling for floor grid perspective

    private void drawFloorGrid(Graphics2D g, float obsSpeed, float dt) {
        bgScrollX += obsSpeed * 0.35f * dt;

        g.setStroke(new BasicStroke(1f));
        int floorTop = (int) GROUND_Y;
        int floorBot = H;

        g.setColor(new Color(255, 60, 60, 35));
        g.fillRect(0, floorTop, W, floorBot - floorTop);

        // Horizontal lines
        g.setColor(new Color(255, 80, 80, 60));
        int lineCount = 8;
        for (int i = 0; i <= lineCount; i++) {
            float t = (float) i / lineCount;
            int y = floorTop + (int)(t * (floorBot - floorTop));
            g.drawLine(0, y, W, y);
        }

        // Perspective vertical lines converging to vanishing point
        float scroll = (bgScrollX * 1.5f) % 120;
        g.setColor(new Color(255, 80, 80, 45));
        for (float vx = -scroll; vx < W + 120; vx += 120)
            g.drawLine((int) vx, floorBot, W / 2, floorTop);

        g.setStroke(new BasicStroke(1f));
    }

    private void drawAlarmLights(Graphics2D g, float dt,
                                 GamePanel.Difficulty diff,
                                 float timeLeft, float timeLimitTotal) {
        alarmPhase += dt * BEAT_HZ * (float)(Math.PI * 2);

        float urgency = timeLimitTotal > 0
                ? 1f - Math.min(1f, timeLeft / timeLimitTotal)
                : 1f;
        if (diff == GamePanel.Difficulty.HOTEL) urgency = 0.6f;

        float pulse     = 0.5f + 0.5f * (float) Math.sin(alarmPhase);
        int   baseAlpha = (int)(30 + urgency * 60 + pulse * (40 + urgency * 60));
        baseAlpha       = Math.min(baseAlpha, 180);

        g.setColor(new Color(200, 0, 0, baseAlpha));
        g.fillRect(0, 0, W, H);

        drawStrobeLight(g, 60,      -20, pulse, urgency);
        drawStrobeLight(g, W - 60,  -20, pulse, urgency);

        if (timeLimitTotal > 0 && timeLeft < 20f) {
            float critPulse = 0.5f + 0.5f * (float) Math.sin(alarmPhase * 2f);
            drawStrobeLight(g, W / 2, -20, critPulse, 1f);
        }
    }

    private void drawStrobeLight(Graphics2D g, int cx, int cy, float pulse, float urgency) {
        int    radius  = (int)(350 + urgency * 150);
        float[] fracs  = { 0f, 0.4f, 1f };
        int    alpha1  = (int)(80 + pulse * (60 + urgency * 80));
        int    alpha2  = (int)(20 + pulse * 30);
        Color[] colors = {
                new Color(255, 30, 30, Math.min(alpha1, 200)),
                new Color(200, 0,  0,  Math.min(alpha2, 100)),
                new Color(120, 0,  0,  0)
        };
        g.setPaint(new RadialGradientPaint(cx, cy, radius, fracs, colors));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawScanlines(Graphics2D g, float dt) {
        scanlineOffset = (scanlineOffset + BEAT_HZ * 40f * dt) % 4f;

        Composite original = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f));
        g.setColor(Color.BLACK);
        for (int y = (int) scanlineOffset; y < H; y += 4)
            g.fillRect(0, y, W, 2);
        g.setComposite(original);

        // Vignette
        g.setPaint(new RadialGradientPaint(
                W / 2f, H / 2f, W * 0.72f,
                new float[]{ 0.55f, 1.0f },
                new Color[]{ new Color(0, 0, 0, 0), new Color(0, 0, 0, 130) }
        ));
        g.fillRect(0, 0, W, H);
    }
}