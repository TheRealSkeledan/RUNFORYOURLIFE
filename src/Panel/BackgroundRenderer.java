package Panel;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * BackgroundRenderer — scrolling backgrounds, floor grid, alarm lights,
 * scanlines, hotel atmosphere, and music-driven effects via {@link SongTimeline}.
 *
 * Greyscale is applied as a full-screen post-process using {@code grey} (0..1).
 * Alarms and scanlines are suppressed entirely during calm sections.
 */
public class BackgroundRenderer {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int   W        = 1280;
    private static final int   H        = 720;
    private static final float GROUND_Y = 580f;

    // ── Scrolling background ──────────────────────────────────────────────────
    private record BgSegment(BufferedImage img, float x, int drawW, int drawH) {}
    private final List<BgSegment> bgSegments = new ArrayList<>();
    private BufferedImage[] bgImages = new BufferedImage[0];
    private final Random rng;

    // ── Scanline state (purely cosmetic, driven by beat) ──────────────────────
    private float scanlineOffset = 0f;

    // ── Hotel atmosphere ──────────────────────────────────────────────────────
    private float hotelPhase   = 0f;
    private float hotelFlicker = 0f;
    private final Random hotelRng = new Random();

    // ── Break-flash ───────────────────────────────────────────────────────────
    /** Brightness 0..1 that fades out after the break-bar hit. */
    private float breakFlash = 0f;

    // ── Greyscale scratch buffer ──────────────────────────────────────────────
    // greyBuffer must be TYPE_INT_RGB (no alpha) so ColorConvertOp→CS_GRAY
    // never leaves stray zero-alpha pixels that black-out the frame.
    // We never cache a second Graphics2D on the offscreen — the caller passes
    // its own og so there is only ever one Graphics2D writing to that surface.
    private BufferedImage greyBuffer;
    private final ColorConvertOp greyOp =
            new ColorConvertOp(java.awt.color.ColorSpace.getInstance(
                    java.awt.color.ColorSpace.CS_GRAY), null);

    // ── Constructor ───────────────────────────────────────────────────────────

    public BackgroundRenderer(Random rng) {
        this.rng = rng;
        // TYPE_INT_RGB (no alpha channel) is critical: ColorConvertOp to CS_GRAY
        // on a TYPE_INT_ARGB source zeroes out the alpha, causing black rectangles
        // when the result is composited back. RGB avoids the problem entirely.
        greyBuffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    }

    // ── Initialisation ────────────────────────────────────────────────────────

    public void init() {
        loadBgImages();
        resetSegments();
    }

    public void reset() {
        scanlineOffset = 0f;
        hotelPhase     = 0f;
        hotelFlicker   = 0f;
        breakFlash     = 0f;
        resetSegments();
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

    private void resetSegments() {
        bgSegments.clear();
        float fillX = 0f;
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

    // ── Main draw entry point ─────────────────────────────────────────────────

    /**
     * Draw everything that lives behind the players.
     * The caller should have already translated the Graphics2D by
     * {@code (timeline.shakeX, timeline.shakeY)} before calling this.
     *
     * @param g        offscreen Graphics2D (already shake-translated by caller)
     * @param obsSpeed current obstacle scroll speed
     * @param dt       delta time in seconds
     * @param diff     current difficulty
     * @param clock    live BeatClock
     * @param timeline live SongTimeline (updated this tick)
     */
    public void drawBackground(Graphics2D g,
                               float obsSpeed, float dt,
                               GamePanel.Difficulty diff,
                               BeatClock clock,
                               SongTimeline timeline) {

        drawSkyAndSegments(g, obsSpeed, dt, diff);
        drawFloorGrid(g, obsSpeed, dt);

        // Alarms and scanlines only during intense sections
        if (timeline.isIntenseSection()) {
            drawAlarmLights(g, clock, timeline, diff);
            drawScanlines(g, clock, dt);
        }

        // Difficulty tints (always on)
        if (diff == GamePanel.Difficulty.HARDCORE) {
            g.setColor(new Color(60, 0, 0, 55));
            g.fillRect(0, 0, W, (int) GROUND_Y);
        }
        if (diff == GamePanel.Difficulty.HOTEL) {
            g.setColor(new Color(80, 0, 0, 70));
            g.fillRect(0, 0, W, (int) GROUND_Y);
        }

        // Break flash — white slam on the break bar
        if (timeline.onBreakHit) breakFlash = 1f;
        if (breakFlash > 0f) {
            g.setColor(new Color(255, 255, 255, (int)(breakFlash * 200)));
            g.fillRect(0, 0, W, H);
            breakFlash = Math.max(0f, breakFlash - dt * 4f);   // ~0.25 s fade
        }
    }

    /** Hotel-specific atmospheric vignette — call after drawBackground. */
    public void drawHotelAtmosphere(Graphics2D g, float dt, SongTimeline timeline) {
        hotelPhase += dt * 0.4f;
        // Flicker only during intense sections
        if (timeline.isIntenseSection() && hotelRng.nextFloat() < 0.008f)
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

    /**
     * Post-process greyscale composite over the whole frame.
     * Call this AFTER all other drawing (players, HUD) is done.
     *
     * @param offscreen the full offscreen frame buffer (read source)
     * @param og        the Graphics2D that owns the offscreen (used for the blend)
     * @param grey      0 = full colour, 1 = full greyscale
     */
    public void applyGreyscale(BufferedImage offscreen, Graphics2D og, float grey) {
        if (grey <= 0.005f) return;
        // Clamp: AlphaComposite throws IllegalArgumentException if alpha > 1.0
        float alpha = Math.min(grey, 1.0f);

        // Step 1: convert the current offscreen into the RGB grey scratch buffer
        greyOp.filter(offscreen, greyBuffer);

        // Step 2: composite the grey image back using the caller's Graphics2D.
        // Using the same og that drew the frame is safe — we're in the game-loop
        // thread and paintComponent only reads the finished offscreen afterwards.
        Composite prev = og.getComposite();
        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
        og.drawImage(greyBuffer, 0, 0, null);
        og.setComposite(prev);   // always restore — don't leave a dirty composite
    }

    // ── Private draw helpers ──────────────────────────────────────────────────

    private float bgScrollX = 0f;

    private void drawSkyAndSegments(Graphics2D g, float obsSpeed, float dt,
                                    GamePanel.Difficulty diff) {
        int skyH = (int) GROUND_Y;

        if (bgImages.length > 0) {
            float scrollDelta = obsSpeed * dt * 6f;
            bgSegments.replaceAll(s -> new BgSegment(s.img(), s.x() - scrollDelta, s.drawW(), s.drawH()));
            bgSegments.removeIf(s -> s.x() + s.drawW() < 0);
            float rightEdge = bgSegments.isEmpty() ? 0f
                    : bgSegments.getLast().x() + bgSegments.getLast().drawW();
            while (rightEdge < W + 200) {
                BgSegment seg = makeRandomSegment(rightEdge);
                bgSegments.add(seg);
                rightEdge += seg.drawW();
            }
            for (BgSegment seg : bgSegments)
                if (seg.img() != null)
                    g.drawImage(seg.img(), (int) seg.x(), 0, seg.drawW(), seg.drawH(), null);
        } else {
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
    }

    private void drawFloorGrid(Graphics2D g, float obsSpeed, float dt) {
        bgScrollX += obsSpeed * 0.35f * dt;

        g.setStroke(new BasicStroke(1f));
        int floorTop = (int) GROUND_Y;
        int floorBot = H;

        g.setColor(new Color(255, 60, 60, 35));
        g.fillRect(0, floorTop, W, floorBot - floorTop);

        g.setColor(new Color(255, 80, 80, 60));
        int lineCount = 8;
        for (int i = 0; i <= lineCount; i++) {
            float t = (float) i / lineCount;
            int y = floorTop + (int)(t * (floorBot - floorTop));
            g.drawLine(0, y, W, y);
        }

        float scroll = (bgScrollX * 1.5f) % 120;
        g.setColor(new Color(255, 80, 80, 45));
        for (float vx = -scroll; vx < W + 120; vx += 120)
            g.drawLine((int) vx, floorBot, W / 2, floorTop);

        g.setStroke(new BasicStroke(1f));
    }

    private void drawAlarmLights(Graphics2D g, BeatClock clock,
                                 SongTimeline timeline, GamePanel.Difficulty diff) {
        float intensity = timeline.intensity;
        float pulse     = 0.5f + 0.5f * (float) Math.sin(clock.barPhase * Math.PI * 2f);

        // Beat punch makes the alarm flash hard on every downbeat
        float punch   = clock.beatPunch(0.4f);
        int baseAlpha = (int)(30 + intensity * 80 + punch * 80);
        baseAlpha     = Math.min(baseAlpha, 180);

        // Save composite before any tinted fills — dirty composite is the #1
        // cause of black rectangles on players and HUD after this method returns.
        Composite savedComposite = g.getComposite();

        g.setColor(new Color(200, 0, 0, baseAlpha));
        g.fillRect(0, 0, W, H);

        drawStrobeLight(g, 60,      -20, pulse, intensity);
        drawStrobeLight(g, W - 60,  -20, pulse, intensity);

        // Extra centre strobe on FULL_FORCE / INTENSE2 on beat 3 (backbeat)
        if ((timeline.section == SongTimeline.Section.FULL_FORCE
                || timeline.section == SongTimeline.Section.INTENSE2)
                && clock.isBarBeat(2)) {
            drawStrobeLight(g, W / 2, -20, clock.beatPunch(0.3f), 1f);
        }

        // Always restore — prevents composite leak into player/HUD drawing
        g.setComposite(savedComposite);
    }

    private void drawStrobeLight(Graphics2D g, int cx, int cy, float pulse, float urgency) {
        int    radius = (int)(350 + urgency * 150);
        float[] fracs = { 0f, 0.4f, 1f };
        int alpha1    = (int)(80 + pulse * (60 + urgency * 80));
        int alpha2    = (int)(20 + pulse * 30);
        Color[] cols  = {
                new Color(255, 30, 30, Math.min(alpha1, 200)),
                new Color(200, 0,  0,  Math.min(alpha2, 100)),
                new Color(120, 0,  0,  0)
        };
        g.setPaint(new RadialGradientPaint(cx, cy, radius, fracs, cols));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void drawScanlines(Graphics2D g, BeatClock clock, float dt) {
        // Speed up scanlines slightly on every beat for a subtle pulse
        float speedMult = 1f + clock.beatPunch(0.5f) * 2f;
        scanlineOffset = (scanlineOffset + (160f / 60f) * 40f * dt * speedMult) % 4f;

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