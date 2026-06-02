package Panel;

import Engine.SoundEffect;
import Main.Main;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import Main.Main;

/**
 * MenuPanel — 1280×720 horror main menu, synced to 80 BPM (0.75 s / beat).
 *
 * BPM-synced effects
 * -------------------
 *  • Heartbeat flash  — fires on every beat (bright) + half-beat (dim)
 *  • Vignette pulse   — swells and contracts in time with the beat
 *  • Background bloom — brightens on the downbeat
 *  • Play button shake — gentle idle jitter, stronger on hover, both synced
 *
 * Other effects
 * -------------
 *  • Falling blood-drop particles
 *  • Drifting red fog layers
 *  • Scanlines + random screen flicker
 *  • Click → screen-wide red pulse + SoundEffect (assets/sfx/MenuClick.wav)
 *  • Buttons drawn at their native image size (no stretching)
 */
public class MenuPanel extends JPanel implements MouseListener, MouseMotionListener {

    // ── Logical resolution ─────────────────────────────────────────────────────
    private static final int W = 1280;
    private static final int H = 720;

    // ── BPM / timing ──────────────────────────────────────────────────────────
    private static final float BPM          = 80f;
    private static final float BEAT_SEC     = 60f / BPM;          // 0.75 s
    private static final float HALF_BEAT    = BEAT_SEC / 2f;      // 0.375 s
    private static final float DT           = 0.016f;

    // ── Asset paths ────────────────────────────────────────────────────────────
    private static final String IMG_ROOT    = "assets/images/ui/";
    private static final String MUSIC_PATH  = "assets/music/MainMenu.wav";
    private static final String CLICK_SFX   = "assets/sfx/MenuClick.wav";

    // ── Images ────────────────────────────────────────────────────────────────
    private BufferedImage imgLogo;
    private BufferedImage imgPlay;
    private BufferedImage imgAchievements;
    private BufferedImage imgCredits;

    // ── Button layout — positions only; size comes from native image size ──────
    //   (cx, cy) = centre of each button in logical pixels
    private static final int BTN_CENTER_X = W / 2;
    private static final int PLAY_CY      = 430;
    private static final int ACH_CY       = 528;
    private static final int CREDITS_CY   = 622;

    // Hit-rectangles built after images load (native size)
    private Rectangle playRect;
    private Rectangle achRect;
    private Rectangle creditsRect;

    // ── Hover state ───────────────────────────────────────────────────────────
    private enum Hover { NONE, PLAY, ACHIEVEMENTS, CREDITS }
    private Hover hover = Hover.NONE;

    // ── Click pulse ───────────────────────────────────────────────────────────
    private float clickPulse  = 0f;   // 0..1, decays after Play is clicked
    private boolean launching = false; // waiting for pulse to fade before swap

    // ── Offscreen buffer ──────────────────────────────────────────────────────
    private final BufferedImage offscreen;
    private final Graphics2D    og;

    // ── Music + SFX ───────────────────────────────────────────────────────────
    private Clip        musicClip;
    private SoundEffect clickSfx;

    // ── Frame ref ─────────────────────────────────────────────────────────────
    private final JFrame frame;
    private final Timer  repaintTimer;

    // ── Global time ───────────────────────────────────────────────────────────
    private float t         = 0f;
    private float beatPhase = 0f;   // 0..1 within the current beat

    // ── Heartbeat flash ───────────────────────────────────────────────────────
    private float heartbeatFlash = 0f;   // 0..1

    // ── Particles ─────────────────────────────────────────────────────────────
    private static final int PARTICLE_COUNT = 55;
    private final float[] px, py, pvx, pvy, psize, palpha;
    private final Random rng = new Random();

    // ── Fog ───────────────────────────────────────────────────────────────────
    private static final int FOG_LAYERS = 4;
    private final float[] fogX   = new float[FOG_LAYERS];
    private final float[] fogSpd = new float[FOG_LAYERS];
    private final float[] fogA   = new float[FOG_LAYERS];

    // ── Scanline flicker ──────────────────────────────────────────────────────
    private float flickerAlpha = 0f;

    // ── Play button shake ─────────────────────────────────────────────────────
    private float playShakeX = 0f;
    private float playShakeY = 0f;

    // ─────────────────────────────────────────────────────────────────────────
    public MenuPanel(JFrame frame) throws Exception {
        /*JButton achievementsButton = new JButton("ACHIEVEMENTS");

// If you are using structural custom buttons or standard listeners, attach it like this:
achievementsButton.addActionListener(e -> {
    // Optional: stop menu clip music if required, or simply change panel state
    Main.goToAchievements(frame);
});

// Remember to add the component to your layout array!
// this.add(achievementsButton);*/

        this.frame = frame;
        setBackground(Color.BLACK);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);

        offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        og = offscreen.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        og.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // Particles
        px     = new float[PARTICLE_COUNT];
        py     = new float[PARTICLE_COUNT];
        pvx    = new float[PARTICLE_COUNT];
        pvy    = new float[PARTICLE_COUNT];
        psize  = new float[PARTICLE_COUNT];
        palpha = new float[PARTICLE_COUNT];
        for (int i = 0; i < PARTICLE_COUNT; i++) resetParticle(i, true);

        // Fog
        for (int i = 0; i < FOG_LAYERS; i++) {
            fogX[i]   = rng.nextFloat() * W;
            fogSpd[i] = 14f + i * 8f;
            fogA[i]   = 0.06f + i * 0.025f;
        }

        loadImages();
        buildButtonRects();
        loadSfx();
        startMusic();

        repaintTimer = new Timer(16, e -> { tick(); render(); repaint(); });
        repaintTimer.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    // ─────────────────────────────────────────────────────────────────────────
    //  TICK
    // ─────────────────────────────────────────────────────────────────────────
    private void tick() {
        t += DT;

        // ── Beat phase (0..1 per beat) ─────────────────────────────────────
        float prevPhase = beatPhase;
        beatPhase += DT / BEAT_SEC;
        boolean newBeat     = (beatPhase >= 1f);
        boolean newHalfBeat = (!newBeat) && (prevPhase < 0.5f) && (beatPhase >= 0.5f);
        if (newBeat) beatPhase -= 1f;

        // ── Heartbeat flash ───────────────────────────────────────────────
        if (newBeat)     heartbeatFlash = Math.max(heartbeatFlash, 0.50f);  // strong
        if (newHalfBeat) heartbeatFlash = Math.max(heartbeatFlash, 0.18f);  // soft echo
        heartbeatFlash = Math.max(0f, heartbeatFlash - 0.035f);

        // ── Play button shake (BPM-synced) ────────────────────────────────
        //    Idle: tiny vibration locked to beat. Hover: bigger + faster.
        float shakeAmp = (hover == Hover.PLAY) ? 14f : 4f;
        // Three overlapping sines — beat, 2× beat, and a fast 7× jitter for rawness
        float beatRad  = beatPhase * 2f * (float)Math.PI;
        playShakeX = shakeAmp * (float)(Math.sin(beatRad * 2f) * 0.55
                + Math.sin(beatRad)       * 0.25
                + Math.sin(beatRad * 7f)  * 0.20);
        playShakeY = shakeAmp * 0.6f * (float)(Math.cos(beatRad)       * 0.50
                + Math.sin(beatRad * 3f)  * 0.30
                + Math.cos(beatRad * 5f)  * 0.20);

        // ── Particles ─────────────────────────────────────────────────────
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            px[i] += pvx[i];
            py[i] += pvy[i];
            palpha[i] -= 0.004f;
            if (py[i] > H + 20 || palpha[i] <= 0) resetParticle(i, false);
        }

        // ── Fog ───────────────────────────────────────────────────────────
        for (int i = 0; i < FOG_LAYERS; i++) {
            fogX[i] -= fogSpd[i] * DT;
            if (fogX[i] + 1000 < 0) fogX[i] = W + rng.nextFloat() * 200f;
        }

        // ── Click pulse decay ─────────────────────────────────────────────
        if (clickPulse > 0f) {
            clickPulse = Math.max(0f, clickPulse - 0.04f);
            // Once pulse fades, do the actual transition
            if (clickPulse <= 0f && launching) {
                launching = false;
                repaintTimer.stop();
                Main.goToSelection(frame, musicClip);
            }
        }

        // ── Scanline flicker ──────────────────────────────────────────────
        if (rng.nextFloat() < 0.015f) flickerAlpha = 0.08f + rng.nextFloat() * 0.12f;
        flickerAlpha = Math.max(0f, flickerAlpha - 0.008f);
    }

    private void resetParticle(int i, boolean randomY) {
        px[i]    = rng.nextFloat() * W;
        py[i]    = randomY ? rng.nextFloat() * H : -rng.nextFloat() * 30f;
        pvx[i]   = (rng.nextFloat() - 0.5f) * 0.6f;
        pvy[i]   = 0.7f + rng.nextFloat() * 1.8f;
        psize[i] = 2f + rng.nextFloat() * 5f;
        palpha[i]= 0.4f + rng.nextFloat() * 0.55f;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ASSETS
    // ─────────────────────────────────────────────────────────────────────────
    private void loadImages() {
        imgLogo         = tryLoad(IMG_ROOT + "Logo.png");
        imgPlay         = tryLoad(IMG_ROOT + "PlayButton.png");
        imgAchievements = tryLoad(IMG_ROOT + "AchievementsButton.png");
        imgCredits      = tryLoad(IMG_ROOT + "CreditsButton.png");
    }

    private BufferedImage tryLoad(String path) {
        try { return ImageIO.read(new File(path)); }
        catch (Exception e) { System.err.println("MenuPanel: could not load " + path); return null; }
    }

    /** Hit-rects sized to the native image dimensions (no forced scaling). */
    private void buildButtonRects() {
        playRect    = nativeRect(imgPlay,         BTN_CENTER_X, PLAY_CY,     320, 80);
        achRect     = nativeRect(imgAchievements, BTN_CENTER_X, ACH_CY,      320, 80);
        creditsRect = nativeRect(imgCredits,      BTN_CENTER_X, CREDITS_CY,  320, 80);
    }

    /** Returns a Rectangle centred at (cx, cy) using native image size, or fallback w×h. */
    private Rectangle nativeRect(BufferedImage img, int cx, int cy, int fallbackW, int fallbackH) {
        int w = (img != null) ? img.getWidth()  : fallbackW;
        int h = (img != null) ? img.getHeight() : fallbackH;
        return new Rectangle(cx - w / 2, cy - h / 2, w, h);
    }

    private void loadSfx() {
        try { clickSfx = new SoundEffect(CLICK_SFX); }
        catch (Exception e) { System.err.println("MenuPanel: SFX not found — " + CLICK_SFX); }
    }

    private void startMusic() {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(new File(MUSIC_PATH).getAbsoluteFile());
            musicClip = AudioSystem.getClip();
            musicClip.open(ais);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            musicClip.start();
        } catch (Exception e) { System.err.println("MenuPanel: music not found — " + MUSIC_PATH); }
    }

    private void stopMusic() {
        if (musicClip != null && musicClip.isRunning()) { musicClip.stop(); musicClip.close(); }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RENDER
    // ─────────────────────────────────────────────────────────────────────────
    private void render() {
        drawBackground();
        drawFog();
        drawParticles();
        drawLogo();
        drawButton(imgPlay,         playRect, hover == Hover.PLAY,         "PLAY",         true);
        drawButton(imgAchievements, achRect,  hover == Hover.ACHIEVEMENTS, "ACHIEVEMENTS", false);
        drawButton(imgCredits,   creditsRect, hover == Hover.CREDITS,      "CREDITS",      false);
        drawVignette();
        drawScanlines();

        // Heartbeat flash
        if (heartbeatFlash > 0f) {
            og.setColor(new Color(180, 0, 0, (int)(heartbeatFlash * 130)));
            og.fillRect(0, 0, W, H);
        }

        // Click pulse — bright white-red burst fading to nothing
        if (clickPulse > 0f) {
            int pa = (int)(clickPulse * 220);
            og.setColor(new Color(220, 10, 10, Math.min(255, pa)));
            og.fillRect(0, 0, W, H);
        }

        // Flicker
        if (flickerAlpha > 0f) {
            og.setColor(new Color(0, 0, 0, (int)(flickerAlpha * 255)));
            og.fillRect(0, 0, W, H);
        }

        // Version tag
        og.setFont(new Font("Monospaced", Font.PLAIN, 13));
        og.setColor(new Color(90, 30, 30, 160));
        og.drawString("v0.1", W - 44, H - 12);
    }

    // ── Background ────────────────────────────────────────────────────────────
    private void drawBackground() {
        GradientPaint bg = new GradientPaint(0, 0, new Color(8, 2, 2), 0, H, new Color(22, 4, 4));
        og.setPaint(bg);
        og.fillRect(0, 0, W, H);

        // Centre bloom — swells on the beat (beatPhase ~0 = peak)
        float beatBrightness = (float)Math.pow(Math.max(0, 1f - beatPhase * 3f), 2f);
        int bloomAlpha = (int)(30 + beatBrightness * 55);
        drawRadialGlow(W / 2, H / 2, 420, new Color(160, 10, 10, bloomAlpha));
        drawRadialGlow(W / 2, 230,   280, new Color(120, 5,  5,  30));
    }

    // ── Fog ───────────────────────────────────────────────────────────────────
    private void drawFog() {
        for (int i = 0; i < FOG_LAYERS; i++) {
            float breathe = 0.5f + 0.5f * (float)Math.sin(t * 0.4f + i * 1.3f);
            int alpha = Math.min(255, Math.max(0, (int)((fogA[i] + breathe * 0.03f) * 255)));
            int fw = 950 + i * 130;
            int fh = 190 + i * 45;
            int fy = H - fh + i * 32;
            RadialGradientPaint fog = new RadialGradientPaint(
                    fogX[i], fy + fh / 2f, fw / 2f,
                    new float[]{0f, 1f},
                    new Color[]{new Color(120, 0, 0, alpha), new Color(40, 0, 0, 0)});
            og.setPaint(fog);
            og.fillOval((int)fogX[i] - fw / 2, fy, fw, fh);
        }
    }

    // ── Particles ─────────────────────────────────────────────────────────────
    private void drawParticles() {
        for (int i = 0; i < PARTICLE_COUNT; i++) {
            int a = (int)(palpha[i] * 255);
            if (a <= 0) continue;
            og.setColor(new Color(180, 10, 10, a));
            int s = (int)psize[i];
            og.fillOval((int)px[i], (int)py[i], s, (int)(s * 1.6f));
            og.setColor(new Color(255, 80, 80, a / 3));
            og.fillOval((int)px[i] + 1, (int)py[i] + 1, Math.max(1, s / 3), Math.max(1, s / 3));
        }
    }

    // ── Logo ──────────────────────────────────────────────────────────────────
    private void drawLogo() {
        if (imgLogo != null) {
            int nW = imgLogo.getWidth();
            int nH = imgLogo.getHeight();
            // Only scale down if it doesn't fit; never scale up
            int drawW, drawH;
            if (nW > W - 80) {
                drawW = W - 80;
                drawH = Math.round(nH * ((float) drawW / nW));
            } else {
                drawW = nW;
                drawH = nH;
            }
            int drawX = (W - drawW) / 2;
            int drawY = 52;

            drawRadialGlow(W / 2, drawY + drawH / 2, drawW / 2 + 80, new Color(200, 0, 0, 55));
            og.drawImage(imgLogo, drawX, drawY, drawW, drawH, null);
            // Faint red bleed over logo to unify with horror palette
            og.setColor(new Color(160, 0, 0, 22));
            og.fillRect(drawX, drawY, drawW, drawH);
        } else {
            og.setFont(new Font("Monospaced", Font.BOLD, 68));
            og.setColor(new Color(200, 20, 20));
            FontMetrics fm = og.getFontMetrics();
            String title = "! RUN FOR YOUR LIFE !";
            og.drawString(title, (W - fm.stringWidth(title)) / 2, 180);
        }
    }

    // ── Buttons ───────────────────────────────────────────────────────────────
    /**
     * @param isPlay  true only for the Play button — applies BPM shake offset
     */
    private void drawButton(BufferedImage img, Rectangle rect, boolean hovered,
                            String label, boolean isPlay) {
        // Hover scale (only non-Play buttons, Play uses shake instead)
        float scale = (!isPlay && hovered) ? 1.07f : 1.0f;

        int drawW = Math.round(rect.width  * scale);
        int drawH = Math.round(rect.height * scale);
        int drawX = rect.x + (rect.width  - drawW) / 2;
        int drawY = rect.y + (rect.height - drawH) / 2;

        // Apply BPM shake to Play button
        if (isPlay) {
            drawX += Math.round(playShakeX);
            drawY += Math.round(playShakeY);
        }

        // Glow
        if (hovered) {
            drawRadialGlow(rect.x + rect.width / 2, rect.y + rect.height / 2,
                    drawW / 2 + 32, new Color(200, 0, 0, 80));
        } else if (isPlay) {
            // Idle Play glow pulses with the beat
            float beatBright = (float)Math.max(0, 1f - beatPhase * 2.5f);
            drawRadialGlow(rect.x + rect.width / 2, rect.y + rect.height / 2,
                    drawW / 2 + 20, new Color(160, 0, 0, (int)(30 + beatBright * 40)));
        }

        if (img != null) {
            og.drawImage(img, drawX, drawY, drawW, drawH, null);
            if (hovered) {
                og.setColor(new Color(180, 0, 0, 40));
                og.fillRect(drawX, drawY, drawW, drawH);
            }
        } else {
            // Fallback pill
            Color fill   = hovered ? new Color(140, 10, 10) : new Color(70, 5, 5);
            Color border = hovered ? new Color(220, 60, 60) : new Color(130, 30, 30);
            og.setColor(fill);
            og.fillRoundRect(drawX, drawY, drawW, drawH, 14, 14);
            og.setColor(border);
            og.setStroke(new BasicStroke(2f));
            og.drawRoundRect(drawX, drawY, drawW, drawH, 14, 14);
            og.setStroke(new BasicStroke(1f));
            og.setFont(new Font("Monospaced", Font.BOLD, 22));
            og.setColor(hovered ? Color.WHITE : new Color(200, 160, 160));
            FontMetrics fm = og.getFontMetrics();
            og.drawString(label,
                    drawX + (drawW - fm.stringWidth(label)) / 2,
                    drawY + (drawH + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    // ── Vignette ──────────────────────────────────────────────────────────────
    private void drawVignette() {
        // Swell on the beat: beatPhase 0→peak, decays by ~0.3
        float beatSwell = (float)Math.max(0, 1f - beatPhase / 0.3f);
        int outerAlpha = (int)(165 + beatSwell * 55);

        RadialGradientPaint vignette = new RadialGradientPaint(
                W / 2f, H / 2f, W * 0.72f,
                new float[]{0.35f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0), new Color(10, 0, 0, outerAlpha)});
        og.setPaint(vignette);
        og.fillRect(0, 0, W, H);

        float pulse = 0.5f + 0.5f * (float)Math.sin(beatPhase * 2f * Math.PI);
        RadialGradientPaint redEdge = new RadialGradientPaint(
                W / 2f, H / 2f, W * 0.85f,
                new float[]{0.55f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0),
                        new Color(80, 0, 0, (int)(50 + pulse * 40 + beatSwell * 30))});
        og.setPaint(redEdge);
        og.fillRect(0, 0, W, H);
    }

    // ── Scanlines ─────────────────────────────────────────────────────────────
    private void drawScanlines() {
        og.setColor(new Color(0, 0, 0, 28));
        for (int y = 0; y < H; y += 3) og.drawLine(0, y, W, y);
    }

    // ── Radial glow ───────────────────────────────────────────────────────────
    private void drawRadialGlow(int cx, int cy, int radius, Color colour) {
        if (radius <= 0) return;
        RadialGradientPaint g = new RadialGradientPaint(
                cx, cy, radius,
                new float[]{0f, 1f},
                new Color[]{colour,
                        new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 0)});
        og.setPaint(g);
        og.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    // ── Viewport scaling ──────────────────────────────────────────────────────
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelW = getWidth(), panelH = getHeight();
        float scale = Math.min((float) panelW / W, (float) panelH / H);
        int drawW = Math.round(W * scale), drawH = Math.round(H * scale);
        int drawX = (panelW - drawW) / 2,  drawY = (panelH - drawH) / 2;
        g.drawImage(offscreen, drawX, drawY, drawW, drawH, null);
    }

    // ── Screen → logical ──────────────────────────────────────────────────────
    private Point toLogical(int sx, int sy) {
        int panelW = getWidth(), panelH = getHeight();
        float scale = Math.min((float) panelW / W, (float) panelH / H);
        int drawW = Math.round(W * scale), drawH = Math.round(H * scale);
        int drawX = (panelW - drawW) / 2,  drawY = (panelH - drawH) / 2;
        return new Point(Math.round((sx - drawX) / scale), Math.round((sy - drawY) / scale));
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private void handleClick(int sx, int sy) {
        Point p = toLogical(sx, sy);
        if      (playRect.contains(p))    onPlay();
        else if (achRect.contains(p))     onAchievements();
        else if (creditsRect.contains(p)) onCredits();
    }

    private void handleMove(int sx, int sy) {
        Point p = toLogical(sx, sy);
        if      (playRect.contains(p))    hover = Hover.PLAY;
        else if (achRect.contains(p))     hover = Hover.ACHIEVEMENTS;
        else if (creditsRect.contains(p)) hover = Hover.CREDITS;
        else                              hover = Hover.NONE;
    }

    private void onPlay() {
        if (launching) return;
        if (clickSfx != null) clickSfx.play();
        clickPulse = 1.0f;
        launching  = true;
        // Music intentionally kept alive — passed to SelectionScreen via goToSelection
    }

    /** Exposes the live music clip so it can be handed off to SelectionScreen. */
    public Clip getMusicClip() { return musicClip; }

private void onAchievements() {
    // Pass the live clip so goToAchievements can stop it before switching panels
    Main.goToAchievements(frame, musicClip);
}    // This switches the panel over to your new screen instead of printing a stub!
    
    private void onCredits()      { System.out.println("Credits — not yet implemented"); }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override public void mouseClicked(MouseEvent e)  { handleClick(e.getX(), e.getY()); }
    @Override public void mouseMoved(MouseEvent e)    { handleMove(e.getX(),  e.getY()); }
    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   { hover = Hover.NONE; }
    @Override public void mouseDragged(MouseEvent e)  {}
}