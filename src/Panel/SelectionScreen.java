package Panel;

import Main.Main;
import Engine.SoundEffect;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;

/**
 * SelectionScreen — 1280×720 mode-selection screen.
 *
 * Layout (logical 1280×720)
 * --------------------------
 *   LEFT  half (0–640)     : preview image, fills left half, no padding
 *   RIGHT half (640–1280)  : mode title image + info button at top,
 *                            three mode buttons stacked, confirm at bottom
 *
 * New features
 * ------------
 *   • assets/images/ui/SelectionBG.png drawn as the background
 *   • Mode title images: NormalTitle.png / HardcoreTitle.png / HotelTitle.png
 *     drawn at the top of the right panel, native size, centred
 *   • InfoButton.png sits to the right of the title; click opens a popup overlay
 *     describing the selected mode
 *   • Hardcore selection adds a persistent screen shake
 *   • The whole UI drifts slightly towards the mouse cursor (parallax tilt)
 */
public class SelectionScreen extends JPanel implements MouseListener, MouseMotionListener {

    // ── Logical resolution ─────────────────────────────────────────────────────
    private static final int W = 1280;
    private static final int H = 720;

    // ── BPM ───────────────────────────────────────────────────────────────────
    private static final float BPM      = 80f;
    private static final float BEAT_SEC = 60f / BPM;
    private static final float DT       = 0.016f;

    // ── Asset paths ────────────────────────────────────────────────────────────
    private static final String UI          = "assets/images/ui/";
    private static final String CLICK_SFX   = "assets/sfx/MenuClick.wav";
    private static final String CONFIRM_SFX = "assets/sfx/MenuConfirm.wav";
    private static final String INFO_SFX    = "assets/sfx/MenuInfo.wav";

    // ── Modes ─────────────────────────────────────────────────────────────────
    private enum Mode { NORMAL, HARDCORE, HOTEL }
    private Mode selected = Mode.NORMAL;

    // ── Mode descriptions (shown in info popup) ────────────────────────────────
    private static final String[] MODE_NAMES = { "NORMAL", "HARDCORE", "HOTEL" };
    private static final String[] MODE_DESC  = {
            """
The classic experience.\s
Runner: run through the corridors and don't get caught
Chaser: Make sure to slow the runner and catch them
Runner has three chances, obstacles are easy to dodge, 2 minutes and 30 seconds on the clock
A balanced experience... if you're fast enough""",

            """
An equivalent to speedrun mode.
Runner only has 1 chance, only 2 minutes on the clock
Obstacles do heavy push back to the chaser and are very fast
Good luck.""",

            """
Oh... I finally found...
I can't believe I found you...
I found you.
Ready or not
Here I Come
"""
    };

    private BufferedImage imgBG;

    private BufferedImage previewNormal, previewHardcore, previewHotel;
    private BufferedImage titleNormal,   titleHardcore,   titleHotel;
    private BufferedImage btnNormal,     btnHardcore,     btnHotel;
    private BufferedImage btnConfirm,    btnInfo;

    private static final int LEFT_W      = 640;
    private static final int RIGHT_CX    = 960;

    private static final int TITLE_CY    = 90;
    private static final int INFO_MARGIN = 16;

    private static final int BTN_Y_START = 240;
    private static final int BTN_Y_GAP   = 145;

    private static final int CONFIRM_CY  = 648;

    private Rectangle rectNormal, rectHardcore, rectHotel, rectConfirm, rectInfo;

    private enum Hover { NONE, NORMAL, HARDCORE, HOTEL, CONFIRM, INFO }
    private Hover hover = Hover.NONE;

    private boolean popupOpen = false;

    private float   clickPulse = 0f;
    private boolean launching  = false;

    private final BufferedImage offscreen;
    private final Graphics2D    og;

    private SoundEffect clickSfx;
    private SoundEffect confirmSfx;
    private SoundEffect infoSfx;
    private final javax.sound.sampled.Clip musicClip;

    private float hardcoreIntensity = 0f;

    private final JFrame frame;
    private final Timer  repaintTimer;

    private float t         = 0f;
    private float beatPhase = 0f;
    private float heartbeatFlash = 0f;

    private float shakeX = 0f, shakeY = 0f;

    private float mouseLogX = W / 2f, mouseLogY = H / 2f;
    private float tiltX = 0f, tiltY = 0f;
    private static final float TILT_MAX    = 10f;
    private static final float TILT_SMOOTH = 0.08f;

    private static final int PC = 40;
    private final float[] px, py, pvx, pvy, psize, palpha;
    private final Random rng = new Random();

    private float flickerAlpha = 0f;

    private float previewOffsetX = 0f;
    private float previewAlpha   = 1f;

    public SelectionScreen(JFrame frame, javax.sound.sampled.Clip musicClip) throws Exception {
        this.frame     = frame;
        this.musicClip = musicClip;
        setBackground(Color.BLACK);
        setFocusable(true);
        addMouseListener(this);
        addMouseMotionListener(this);

        offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        og = offscreen.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        og.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);
        og.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        px = new float[PC]; py  = new float[PC];
        pvx= new float[PC]; pvy = new float[PC];
        psize = new float[PC]; palpha = new float[PC];
        for (int i = 0; i < PC; i++) resetParticle(i, true);

        loadImages();
        buildRects();
        loadSfx();

        repaintTimer = new Timer(16, e -> { tick(); render(); repaint(); });
        repaintTimer.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    private void tick() {
        t += DT;

        float prev = beatPhase;
        beatPhase += DT / BEAT_SEC;
        boolean newBeat     = beatPhase >= 1f;
        boolean newHalfBeat = !newBeat && prev < 0.5f && beatPhase >= 0.5f;
        if (newBeat) beatPhase -= 1f;
        if (newBeat)     heartbeatFlash = Math.max(heartbeatFlash, 0.38f);
        if (newHalfBeat) heartbeatFlash = Math.max(heartbeatFlash, 0.12f);
        heartbeatFlash = Math.max(0f, heartbeatFlash - 0.030f);

        float targetIntensity = (selected == Mode.HARDCORE) ? 1f : 0f;
        hardcoreIntensity += (targetIntensity - hardcoreIntensity) * 0.06f;

        if (selected == Mode.HARDCORE) {
            float amp = 3.5f;
            float rad  = beatPhase * 2f * (float)Math.PI;
            shakeX = amp * (float)(Math.sin(rad * 2.1f) * 0.55 + Math.sin(rad * 7.3f) * 0.30 + (rng.nextFloat()-0.5f) * 0.15);
            shakeY = amp * 0.6f * (float)(Math.cos(rad * 1.7f) * 0.50 + Math.sin(rad * 5.1f) * 0.35 + (rng.nextFloat()-0.5f) * 0.15);
        } else {
            shakeX *= 0.85f;
            shakeY *= 0.85f;
        }

        float targetTiltX = ((mouseLogX / W) - 0.5f) * 2f * TILT_MAX;
        float targetTiltY = ((mouseLogY / H) - 0.5f) * 2f * TILT_MAX;
        tiltX += (targetTiltX - tiltX) * TILT_SMOOTH;
        tiltY += (targetTiltY - tiltY) * TILT_SMOOTH;

        for (int i = 0; i < PC; i++) {
            px[i] += pvx[i]; py[i] += pvy[i]; palpha[i] -= 0.004f;
            if (py[i] > H + 20 || palpha[i] <= 0) resetParticle(i, false);
        }

        previewOffsetX *= 0.78f;
        previewAlpha    = Math.min(1f, previewAlpha + 0.08f);

        if (clickPulse > 0f) {
            clickPulse = Math.max(0f, clickPulse - 0.045f);
            if (clickPulse <= 0f && launching) {
                launching = false;
                repaintTimer.stop();
                Main.startGame(frame, selected.name(), musicClip);
            }
        }

        if (rng.nextFloat() < 0.012f) flickerAlpha = 0.05f + rng.nextFloat() * 0.09f;
        flickerAlpha = Math.max(0f, flickerAlpha - 0.006f);
    }

    private void resetParticle(int i, boolean randomY) {
        px[i]    = rng.nextFloat() * W;
        py[i]    = randomY ? rng.nextFloat() * H : -rng.nextFloat() * 30f;
        pvx[i]   = (rng.nextFloat() - 0.5f) * 0.5f;
        pvy[i]   = 0.6f + rng.nextFloat() * 1.6f;
        psize[i] = 2f + rng.nextFloat() * 4f;
        palpha[i]= 0.35f + rng.nextFloat() * 0.5f;
    }

    private void loadImages() {
        imgBG          = tryLoad(UI + "SelectionBG.png");
        previewNormal   = tryLoad(UI + "NormalMode.png");
        previewHardcore = tryLoad(UI + "HardcoreMode.png");
        previewHotel    = null; // Hotel preview image removed
        titleNormal     = tryLoad(UI + "NormalTitle.png");
        titleHardcore   = tryLoad(UI + "HardcoreTitle.png");
        titleHotel      = tryLoad(UI + "HotelTitle.png");
        btnNormal       = tryLoad(UI + "NormalButton.png");
        btnHardcore     = tryLoad(UI + "HardcoreButton.png");
        btnHotel        = tryLoad(UI + "HotelButton.png");
        btnConfirm      = tryLoad(UI + "ConfirmButton.png");
        btnInfo         = tryLoad(UI + "InfoButton.png");
    }

    private BufferedImage tryLoad(String path) {
        try { return ImageIO.read(new File(path)); }
        catch (Exception e) { System.err.println("SelectionScreen: missing " + path); return null; }
    }

    private void buildRects() {
        rectNormal   = nativeRect(btnNormal,   RIGHT_CX, BTN_Y_START,                320, 80);
        rectHardcore = nativeRect(btnHardcore, RIGHT_CX, BTN_Y_START + BTN_Y_GAP,    320, 80);
        rectHotel    = nativeRect(btnHotel,    RIGHT_CX, BTN_Y_START + BTN_Y_GAP * 2,320, 80);
        rectConfirm  = nativeRect(btnConfirm,  RIGHT_CX, CONFIRM_CY,                 320, 80);

        rectInfo = new Rectangle(W - 80, TITLE_CY - 20, 40, 40);
    }

    private Rectangle nativeRect(BufferedImage img, int cx, int cy, int fw, int fh) {
        int w = img != null ? img.getWidth()  : fw;
        int h = img != null ? img.getHeight() : fh;
        return new Rectangle(cx - w / 2, cy - h / 2, w, h);
    }

    private void loadSfx() {
        try { clickSfx   = new SoundEffect(CLICK_SFX);   } catch (Exception e) { System.err.println("SelectionScreen: missing " + CLICK_SFX);   }
        try { confirmSfx = new SoundEffect(CONFIRM_SFX); } catch (Exception e) { System.err.println("SelectionScreen: missing " + CONFIRM_SFX); }
        try { infoSfx    = new SoundEffect(INFO_SFX);    } catch (Exception e) { System.err.println("SelectionScreen: missing " + INFO_SFX);    }
    }

    private void render() {
        drawBackground();

        int ox = Math.round(tiltX + shakeX);
        int oy = Math.round(tiltY + shakeY);
        og.translate(ox, oy);

        drawParticles();
        drawDivider();
        drawPreview();
        drawTitleRow();
        drawModeButtons();
        drawConfirmButton();
        drawVignette();
        drawScanlines();

        og.translate(-ox, -oy);

        if (hardcoreIntensity > 0.01f) {
            drawHardcoreEffects();
        }

        if (heartbeatFlash > 0f) {
            og.setColor(new Color(180, 0, 0, (int)(heartbeatFlash * 110)));
            og.fillRect(0, 0, W, H);
        }
        if (clickPulse > 0f) {
            og.setColor(new Color(220, 10, 10, Math.min(255, (int)(clickPulse * 210))));
            og.fillRect(0, 0, W, H);
        }
        if (flickerAlpha > 0f) {
            og.setColor(new Color(0, 0, 0, (int)(flickerAlpha * 255)));
            og.fillRect(0, 0, W, H);
        }

        if (popupOpen) drawPopup();
    }

    private BufferedImage redFringe;
    private BufferedImage blueFringe;

    private void buildFringeImages(int shift) {
        if (redFringe == null || redFringe.getWidth() != W) {
            redFringe  = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
            blueFringe = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        }

        int[] srcPixels  = offscreen.getRGB(0, 0, W, H, null, 0, W);
        int[] redPixels  = new int[W * H];
        int[] bluePixels = new int[W * H];

        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int srcX = x - shift;
                if (srcX >= 0 && srcX < W) {
                    int argb  = srcPixels[y * W + srcX];
                    int a     = (argb >> 24) & 0xFF;
                    int r     = (argb >> 16) & 0xFF;

                    redPixels[y * W + x] = ((r * a / 255) << 24) | (r << 16);
                }

                int srcXb = x + shift;
                if (srcXb >= 0 && srcXb < W) {
                    int argb  = srcPixels[y * W + srcXb];
                    int a     = (argb >> 24) & 0xFF;
                    int b     = (argb)       & 0xFF;
                    bluePixels[y * W + x] = ((b * a / 255) << 24) | b;
                }
            }
        }

        redFringe.setRGB(0, 0, W, H, redPixels,  0, W);
        blueFringe.setRGB(0, 0, W, H, bluePixels, 0, W);
    }

    private void drawHardcoreEffects() {
        float hi = hardcoreIntensity;

        og.setColor(new Color(160, 0, 0, (int)(hi * 55)));
        og.fillRect(0, 0, W, H);

        float beatSwell = (float)Math.max(0, 1f - beatPhase / 0.35f);
        int vigAlpha = (int)(hi * (90 + beatSwell * 60));
        og.setPaint(new RadialGradientPaint(
                W / 2f, H / 2f, W * 0.60f,
                new float[]{0.30f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0),
                        new Color(120, 0, 0, Math.min(255, vigAlpha))}));
        og.fillRect(0, 0, W, H);

        int shift = Math.round(hi * 14f);
        if (shift < 1) return;

        buildFringeImages(shift);

        float fringeOpacity = Math.min(1f, hi * 0.75f);
        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fringeOpacity));
        og.drawImage(redFringe,  0, 0, null);
        og.drawImage(blueFringe, 0, 0, null);
        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
    }
    private void drawBackground() {
        if (imgBG != null) {
            og.drawImage(imgBG, 0, 0, W, H, null);
        } else {
            GradientPaint bg = new GradientPaint(0, 0, new Color(8, 2, 2), 0, H, new Color(22, 4, 4));
            og.setPaint(bg);
            og.fillRect(0, 0, W, H);
        }

        float bb = (float)Math.pow(Math.max(0, 1f - beatPhase * 3f), 2f);
        drawRadialGlow(W / 2, H / 2, 480, new Color(120, 4, 4, (int)(20 + bb * 35)));
    }

    private void drawDivider() {
        og.setColor(new Color(120, 10, 10, 80));
        og.setStroke(new BasicStroke(2f));
        og.drawLine(LEFT_W, 0, LEFT_W, H);
        og.setStroke(new BasicStroke(1f));
        drawRadialGlow(LEFT_W, H / 2, 55, new Color(160, 0, 0, 28));
    }

    private void drawPreview() {
        BufferedImage preview = switch (selected) {
            case NORMAL   -> previewNormal;
            case HARDCORE -> previewHardcore;
            case HOTEL    -> previewHotel;
        };

        Composite old = og.getComposite();
        og.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, previewAlpha));

        if (preview != null) {
            float aspect = (float) preview.getWidth() / preview.getHeight();
            int drawW, drawH;
            if ((float) LEFT_W / H > aspect) {
                drawH = H;
                drawW = Math.round(drawH * aspect);
            } else {
                drawW = LEFT_W;
                drawH = Math.round(drawW / aspect);
            }
            int drawX = (LEFT_W - drawW) / 2 + Math.round(previewOffsetX);
            int drawY = (H - drawH) / 2;

            drawRadialGlow(LEFT_W / 2, H / 2, drawW / 2 + 40, new Color(140, 0, 0, 28));
            og.drawImage(preview, drawX, drawY, drawW, drawH, null);
            og.setColor(new Color(60, 0, 0, 14));
            og.fillRect(drawX, drawY, drawW, drawH);
        } else {
            og.setColor(new Color(35, 8, 8));
            og.fillRect(0, 0, LEFT_W, H);
            og.setFont(new Font("Monospaced", Font.BOLD, 26));
            og.setColor(new Color(160, 50, 50));
            String lbl = selected.name();
            FontMetrics fm = og.getFontMetrics();
            og.drawString(lbl, (LEFT_W - fm.stringWidth(lbl)) / 2, H / 2 + fm.getAscent() / 2);
        }

        og.setComposite(old);
    }

    private void drawTitleRow() {
        BufferedImage title = switch (selected) {
            case NORMAL   -> titleNormal;
            case HARDCORE -> titleHardcore;
            case HOTEL    -> titleHotel;
        };

        int titleX = LEFT_W + 20;
        int titleY = TITLE_CY;

        if (title != null) {
            int tw = title.getWidth();
            int th = title.getHeight();
            og.drawImage(title, titleX, titleY - th / 2, tw, th, null);

            int infoSize = btnInfo != null ? Math.max(btnInfo.getWidth(), 36) : 36;
            int infoBtnX = titleX + tw + INFO_MARGIN;
            int infoBtnY = titleY - infoSize / 2;
            rectInfo = new Rectangle(infoBtnX, infoBtnY, infoSize, infoSize);
        } else {
            og.setFont(new Font("Monospaced", Font.BOLD, 34));
            og.setColor(new Color(220, 50, 50));
            og.drawString(MODE_NAMES[selected.ordinal()], titleX, titleY + 12);
            FontMetrics fm = og.getFontMetrics();
            int tw = fm.stringWidth(MODE_NAMES[selected.ordinal()]);
            int infoBtnX = titleX + tw + INFO_MARGIN;
            rectInfo = new Rectangle(infoBtnX, titleY - 18, 36, 36);
        }

        boolean hovInfo = hover == Hover.INFO;
        if (hovInfo) drawRadialGlow(rectInfo.x + rectInfo.width / 2,
                rectInfo.y + rectInfo.height / 2,
                rectInfo.width, new Color(200, 0, 0, 70));

        if (btnInfo != null) {
            og.drawImage(btnInfo, rectInfo.x, rectInfo.y, rectInfo.width, rectInfo.height, null);
            if (hovInfo || popupOpen) {
                og.setColor(new Color(200, 0, 0, 45));
                og.fillRect(rectInfo.x, rectInfo.y, rectInfo.width, rectInfo.height);
            }
        } else {
            Color c = hovInfo || popupOpen ? new Color(220, 60, 60) : new Color(140, 30, 30);
            og.setColor(c);
            og.fillOval(rectInfo.x, rectInfo.y, rectInfo.width, rectInfo.height);
            og.setColor(Color.WHITE);
            og.setFont(new Font("Monospaced", Font.BOLD, 20));
            FontMetrics fm = og.getFontMetrics();
            og.drawString("?",
                    rectInfo.x + (rectInfo.width  - fm.stringWidth("?")) / 2,
                    rectInfo.y + (rectInfo.height + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private void drawModeButtons() {
        drawModeButton(btnNormal,   rectNormal,   hover == Hover.NORMAL,   selected == Mode.NORMAL,   "NORMAL");
        drawModeButton(btnHardcore, rectHardcore, hover == Hover.HARDCORE, selected == Mode.HARDCORE, "HARDCORE");
        drawModeButton(btnHotel,    rectHotel,    hover == Hover.HOTEL,    selected == Mode.HOTEL,    "HOTEL");
    }

    private void drawModeButton(BufferedImage img, Rectangle rect,
                                boolean hovered, boolean isSelected, String label) {
        float scale = (hovered || isSelected) ? 1.06f : 1.0f;
        int dW = Math.round(rect.width  * scale);
        int dH = Math.round(rect.height * scale);
        int dX = rect.x + (rect.width  - dW) / 2;
        int dY = rect.y + (rect.height - dH) / 2;

        if (isSelected) {
            float bb = (float)Math.max(0, 1f - beatPhase * 2.5f);
            drawRadialGlow(rect.x + rect.width / 2, rect.y + rect.height / 2,
                    dW / 2 + 36, new Color(200, 0, 0, (int)(58 + bb * 50)));
        } else if (hovered) {
            drawRadialGlow(rect.x + rect.width / 2, rect.y + rect.height / 2,
                    dW / 2 + 22, new Color(180, 0, 0, 52));
        }

        if (img != null) {
            og.drawImage(img, dX, dY, dW, dH, null);
            if (isSelected) {
                og.setColor(new Color(180, 0, 0, 50)); og.fillRect(dX, dY, dW, dH);
            } else if (hovered) {
                og.setColor(new Color(180, 0, 0, 26)); og.fillRect(dX, dY, dW, dH);
            }
        } else {
            Color fill   = isSelected ? new Color(130, 8, 8) : (hovered ? new Color(100, 6, 6) : new Color(55, 4, 4));
            Color border = isSelected ? new Color(220, 50, 50) : (hovered ? new Color(180, 30, 30) : new Color(110, 20, 20));
            og.setColor(fill);   og.fillRoundRect(dX, dY, dW, dH, 14, 14);
            og.setColor(border); og.setStroke(new BasicStroke(isSelected ? 2.5f : 1.8f));
            og.drawRoundRect(dX, dY, dW, dH, 14, 14); og.setStroke(new BasicStroke(1f));
            og.setFont(new Font("Monospaced", Font.BOLD, 22));
            og.setColor(isSelected || hovered ? Color.WHITE : new Color(190, 140, 140));
            FontMetrics fm = og.getFontMetrics();
            og.drawString(label, dX + (dW - fm.stringWidth(label)) / 2,
                    dY + (dH + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private void drawConfirmButton() {
        boolean hov = hover == Hover.CONFIRM;
        float scale = hov ? 1.07f : 1.0f;
        int dW = Math.round(rectConfirm.width  * scale);
        int dH = Math.round(rectConfirm.height * scale);
        int dX = rectConfirm.x + (rectConfirm.width  - dW) / 2;
        int dY = rectConfirm.y + (rectConfirm.height - dH) / 2;

        if (hov) drawRadialGlow(rectConfirm.x + rectConfirm.width / 2,
                rectConfirm.y + rectConfirm.height / 2,
                dW / 2 + 32, new Color(200, 0, 0, 85));

        if (btnConfirm != null) {
            og.drawImage(btnConfirm, dX, dY, dW, dH, null);
            if (hov) { og.setColor(new Color(180, 0, 0, 40)); og.fillRect(dX, dY, dW, dH); }
        } else {
            og.setColor(hov ? new Color(140, 8, 8) : new Color(70, 4, 4));
            og.fillRoundRect(dX, dY, dW, dH, 14, 14);
            og.setColor(hov ? new Color(230, 60, 60) : new Color(130, 25, 25));
            og.setStroke(new BasicStroke(2f)); og.drawRoundRect(dX, dY, dW, dH, 14, 14); og.setStroke(new BasicStroke(1f));
            og.setFont(new Font("Monospaced", Font.BOLD, 22)); og.setColor(Color.WHITE);
            FontMetrics fm = og.getFontMetrics(); String lbl = "CONFIRM";
            og.drawString(lbl, dX + (dW - fm.stringWidth(lbl)) / 2, dY + (dH + fm.getAscent() - fm.getDescent()) / 2);
        }
    }

    private void drawPopup() {
        og.setColor(new Color(0, 0, 0, 185));
        og.fillRect(0, 0, W, H);

        // Popup box — centred
        int boxW = 660, boxH = 300;
        int boxX = (W - boxW) / 2;
        int boxY = (H - boxH) / 2;

        og.setColor(new Color(18, 4, 4, 240));
        og.fillRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        og.setColor(new Color(160, 20, 20, 200));
        og.setStroke(new BasicStroke(2.5f));
        og.drawRoundRect(boxX, boxY, boxW, boxH, 18, 18);
        og.setStroke(new BasicStroke(1f));

        og.setFont(new Font("Monospaced", Font.BOLD, 26));
        og.setColor(new Color(220, 50, 50));
        String header = "— " + MODE_NAMES[selected.ordinal()] + " —";
        FontMetrics fmH = og.getFontMetrics();
        og.drawString(header, boxX + (boxW - fmH.stringWidth(header)) / 2, boxY + 46);

        og.setFont(new Font("Monospaced", Font.PLAIN, 17));
        og.setColor(new Color(210, 170, 170));
        FontMetrics fmD = og.getFontMetrics();
        String[] lines = MODE_DESC[selected.ordinal()].split("\n");
        int lineH = fmD.getHeight();
        int textY = boxY + 90;
        for (String line : lines) {
            og.drawString(line, boxX + (boxW - fmD.stringWidth(line)) / 2, textY);
            textY += lineH + 4;
        }

        og.setFont(new Font("Monospaced", Font.ITALIC, 13));
        og.setColor(new Color(130, 60, 60));
        String hint = "click anywhere to close";
        FontMetrics fmHint = og.getFontMetrics();
        og.drawString(hint, boxX + (boxW - fmHint.stringWidth(hint)) / 2, boxY + boxH - 18);
    }

    private void drawParticles() {
        for (int i = 0; i < PC; i++) {
            int a = (int)(palpha[i] * 255); if (a <= 0) continue;
            og.setColor(new Color(180, 10, 10, a));
            int s = (int)psize[i];
            og.fillOval((int)px[i], (int)py[i], s, (int)(s * 1.6f));
            og.setColor(new Color(255, 80, 80, a / 3));
            og.fillOval((int)px[i] + 1, (int)py[i] + 1, Math.max(1, s / 3), Math.max(1, s / 3));
        }
    }

    private void drawVignette() {
        float swell = (float)Math.max(0, 1f - beatPhase / 0.3f);
        int alpha   = (int)(155 + swell * 50);
        og.setPaint(new RadialGradientPaint(W / 2f, H / 2f, W * 0.72f,
                new float[]{0.35f, 1.0f},
                new Color[]{new Color(0,0,0,0), new Color(10,0,0,alpha)}));
        og.fillRect(-20, -20, W + 40, H + 40);
    }

    private void drawScanlines() {
        og.setColor(new Color(0, 0, 0, 25));
        for (int y = -20; y < H + 20; y += 3) og.drawLine(-20, y, W + 20, y);
    }

    private void drawRadialGlow(int cx, int cy, int r, Color c) {
        if (r <= 0) return;
        og.setPaint(new RadialGradientPaint(cx, cy, r, new float[]{0f, 1f},
                new Color[]{c, new Color(c.getRed(), c.getGreen(), c.getBlue(), 0)}));
        og.fillOval(cx - r, cy - r, r * 2, r * 2);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelW = getWidth(), panelH = getHeight();
        float scale = Math.min((float) panelW / W, (float) panelH / H);
        int drawW = Math.round(W * scale), drawH = Math.round(H * scale);
        int drawX = (panelW - drawW) / 2,  drawY = (panelH - drawH) / 2;
        g.drawImage(offscreen, drawX, drawY, drawW, drawH, null);
    }

    private Point toLogical(int sx, int sy) {
        int panelW = getWidth(), panelH = getHeight();
        float scale = Math.min((float) panelW / W, (float) panelH / H);
        int drawW = Math.round(W * scale), drawH = Math.round(H * scale);
        int drawX = (panelW - drawW) / 2,  drawY = (panelH - drawH) / 2;
        return new Point(Math.round((sx - drawX) / scale), Math.round((sy - drawY) / scale));
    }

    private void handleClick(int sx, int sy) {
        Point p = toLogical(sx, sy);

        if (popupOpen) { popupOpen = false; return; }

        if      (rectInfo.contains(p))     { popupOpen = true; if (infoSfx != null) infoSfx.play(); else if (clickSfx != null) clickSfx.play(); }
        else if (rectNormal.contains(p))   selectMode(Mode.NORMAL);
        else if (rectHardcore.contains(p)) selectMode(Mode.HARDCORE);
        else if (rectHotel.contains(p))    selectMode(Mode.HOTEL);
        else if (rectConfirm.contains(p))  onConfirm();
    }

    private void handleMove(int sx, int sy) {
        Point p = toLogical(sx, sy);
        // Update smoothed logical mouse position for tilt
        mouseLogX = p.x;
        mouseLogY = p.y;

        if (popupOpen) { hover = Hover.NONE; return; }

        if      (rectInfo.contains(p))     hover = Hover.INFO;
        else if (rectNormal.contains(p))   hover = Hover.NORMAL;
        else if (rectHardcore.contains(p)) hover = Hover.HARDCORE;
        else if (rectHotel.contains(p))    hover = Hover.HOTEL;
        else if (rectConfirm.contains(p))  hover = Hover.CONFIRM;
        else                               hover = Hover.NONE;
    }

    private void selectMode(Mode mode) {
        if (mode == selected) return;
        selected = mode;
        previewOffsetX = -60f;
        previewAlpha   = 0.2f;
        if (clickSfx != null) clickSfx.play();
    }

    private void onConfirm() {
        if (launching) return;
        if (musicClip != null && musicClip.isRunning()) { musicClip.stop(); musicClip.close(); }
        if (confirmSfx != null) confirmSfx.play();
        else if (clickSfx != null) clickSfx.play();   // fallback if file missing
        clickPulse = 1.0f;
        launching  = true;
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override public void mouseClicked(MouseEvent e)  { handleClick(e.getX(), e.getY()); }
    @Override public void mouseMoved(MouseEvent e)    { handleMove(e.getX(),  e.getY()); }
    @Override public void mousePressed(MouseEvent e)  {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseEntered(MouseEvent e)  {}
    @Override public void mouseExited(MouseEvent e)   { hover = Hover.NONE; }
    @Override public void mouseDragged(MouseEvent e)  { handleMove(e.getX(), e.getY()); }
}