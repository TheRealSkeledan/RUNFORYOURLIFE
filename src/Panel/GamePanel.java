package Panel;

import Engine.Music;
import Engine.Obstacle;
import Engine.Player;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GamePanel — 1280×720 game viewport.
 *
 * Difficulty modes (set by SelectionScreen via constructor):
 *
 *  NORMAL    — 3 lives │ 2:00 timer │ normal obstacle stun │ normal chaser
 *  HARDCORE  — 1 life  │ 1:30 timer │ heavy obstacle stun  │ faster chaser
 *                         no invincibility frames after being hit
 *  HOTEL     — 3 lives │ no timer   │ normal obstacle stun │ slower/creepier chaser
 *                         darker red atmosphere
 */
public class GamePanel extends JPanel implements ActionListener, KeyListener {

    // ── Logical resolution ─────────────────────────────────────────────────────
    private static final int   W        = 1280;
    private static final int   H        = 720;
    private static final float GROUND_Y = 570f;

    // ── Timing ────────────────────────────────────────────────────────────────
    private static final int   TICK_MS = 16;
    private static final float DT      = TICK_MS / 1000f;

    // ── Obstacle ──────────────────────────────────────────────────────────────
    private static final float SPAWN_INTERVAL = 420f;
    private static final float DESPAWN_MARGIN = 120f;
    private static final float CATCH_DIST     = 28f;

    // ── Difficulty enum ────────────────────────────────────────────────────────
    public enum Difficulty { NORMAL, HARDCORE, HOTEL }

    // ── Per-difficulty constants ───────────────────────────────────────────────
    //                                  NORMAL    HARDCORE    HOTEL
    private static final float[] RUNNER_SPEED  = { 280f,   310f,   240f };
    private static final float[] CHASER_SPEED  = { 255f,   295f,   210f };
    private static final float[] SPEED_RAMP    = {   4f,     6f,     2f };
    private static final int[]   START_LIVES   = {   3,      1,      3  };
    private static final float[] TIME_LIMIT    = { 120f,    90f,    -1f }; // -1 = no limit
    private static final float[] STUN_NORMAL   = {  0.8f,   1.6f,   0.8f }; // ground obstacle
    private static final float[] FREEZE_TIME   = {  0.6f,   1.2f,   0.6f }; // aerial obstacle
    private static final float[] CHASER_GAP    = {  90f,    60f,   120f  }; // how close before chaser stops closing
    private static final boolean[] NO_INVINCIBILITY = { false, true, false };

    // ── Background tint per mode ──────────────────────────────────────────────
    // Hotel gets a creepier, more red-drenched palette
    private static final Color[] SKY_TOP = {
            new Color(28, 24, 50),   // normal — purple-blue night
            new Color(18, 10, 10),   // hardcore — near black
            new Color(22, 8,  8)     // hotel — dark red
    };
    private static final Color[] SKY_BOT = {
            new Color(55, 40, 80),   // normal
            new Color(35, 12, 12),   // hardcore
            new Color(45, 15, 10)    // hotel
    };
    private static final Color[] BUILD_FAR = {
            new Color(40, 32, 65),   // normal
            new Color(30, 10, 10),   // hardcore
            new Color(35, 10, 8)     // hotel
    };
    private static final Color[] BUILD_NEAR = {
            new Color(30, 24, 48),   // normal
            new Color(20, 6,  6),    // hardcore
            new Color(28, 8,  6)     // hotel
    };

    // ── Game state ────────────────────────────────────────────────────────────
    public enum State { PLAYING, PAUSED, GAME_OVER }
    private State state = State.PLAYING;

    // ── Active difficulty ─────────────────────────────────────────────────────
    private final Difficulty diff;
    private final int        diffIdx;   // ordinal, for array lookups

    // ── World ─────────────────────────────────────────────────────────────────
    private float cameraX   = 0f;
    private float worldDist = 0f;
    private float nextSpawn = 500f;
    private float elapsed   = 0f;       // seconds played
    private float timeLeft;             // counts down; <=0 means time-up (non-Hotel)

    // ── Entities ──────────────────────────────────────────────────────────────
    private Player runner;
    private Player chaser;
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final Random rng = new Random();

    // ── Input ─────────────────────────────────────────────────────────────────
    private boolean keyJump = false;
    private boolean keyDuck = false;

    // ── Game loop ─────────────────────────────────────────────────────────────
    private final Timer gameTimer;

    // ── Offscreen buffer ──────────────────────────────────────────────────────
    private final BufferedImage offscreen;
    private final Graphics2D    og;

    // ── Parallax ──────────────────────────────────────────────────────────────
    private float bgLayer1X = 0f;
    private float bgLayer2X = 0f;

    // ── Music ─────────────────────────────────────────────────────────────────
    @SuppressWarnings("unused")
    private Music bgm;

    // ── Hotel atmosphere ──────────────────────────────────────────────────────
    private float hotelVignettePhase = 0f; // slow pulse for hotel red vignette
    private float hotelFlickerAlpha  = 0f;
    private final Random hotelRng    = new Random();

    // ── Frame ref ─────────────────────────────────────────────────────────────
    private final JFrame frame;

    // ─────────────────────────────────────────────────────────────────────────
    public GamePanel(JFrame frame, String modeName) throws Exception {
        this.frame = frame;

        // Parse difficulty — default to NORMAL if unrecognised
        Difficulty parsed;
        try { parsed = Difficulty.valueOf(modeName.toUpperCase()); }
        catch (IllegalArgumentException e) { parsed = Difficulty.NORMAL; }
        this.diff    = parsed;
        this.diffIdx = diff.ordinal();

        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);

        offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        og = offscreen.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);
        og.setRenderingHint(RenderingHints.KEY_RENDERING,     RenderingHints.VALUE_RENDER_QUALITY);

        tryLoadMusic();
        spawnPlayers();

        gameTimer = new Timer(TICK_MS, this);
        gameTimer.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    // ── Music ─────────────────────────────────────────────────────────────────
    private void tryLoadMusic() {
        try {
            bgm = new Music("assets/music/bgm", false);
            Music.play();
        } catch (Exception ignored) { }
    }

    // ── Player construction ───────────────────────────────────────────────────
    private void spawnPlayers() {
        runner = new Player("runner", true,  200f,  GROUND_Y, RUNNER_SPEED[diffIdx]);
        chaser = new Player("chaser", false, -120f, GROUND_Y, CHASER_SPEED[diffIdx]);
        runner.lives = START_LIVES[diffIdx];
        timeLeft     = TIME_LIMIT[diffIdx];
    }

    // ── Restart ───────────────────────────────────────────────────────────────
    private void restartGame() {
        obstacles.clear();
        cameraX   = 0f;
        worldDist = 0f;
        nextSpawn = 500f;
        elapsed   = 0f;
        bgLayer1X = 0f;
        bgLayer2X = 0f;
        keyJump   = false;
        keyDuck   = false;
        spawnPlayers();
        state = State.PLAYING;
        gameTimer.start();
    }

    // ── Game loop ─────────────────────────────────────────────────────────────
    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == State.PLAYING) update(DT);
        renderToOffscreen();
        repaint();
    }

    // ── Update ────────────────────────────────────────────────────────────────
    private void update(float dt) {
        elapsed += dt;

        // ── Timer countdown (not Hotel) ───────────────────────────────────
        if (TIME_LIMIT[diffIdx] > 0) {
            timeLeft -= dt;
            if (timeLeft <= 0) {
                timeLeft = 0;
                // Time's up = runner escapes = they win; treat as GAME_OVER
                // with a different message (handled in drawGameOver)
                state = State.GAME_OVER;
                gameTimer.stop();
                return;
            }
        }

        // ── Speed ramp ────────────────────────────────────────────────────
        float speedBonus = SPEED_RAMP[diffIdx] * elapsed;
        runner.baseSpeed = RUNNER_SPEED[diffIdx] + speedBonus;
        chaser.baseSpeed = CHASER_SPEED[diffIdx] + speedBonus;

        runner.cacheDt(dt);
        chaser.cacheDt(dt);

        if (keyJump) runner.jump();
        runner.isDucking = keyDuck && runner.onGround;

        boolean runnerCanMove = !runner.isStunned() && !runner.isFrozen();
        if (runnerCanMove) {
            runner.x += runner.baseSpeed * dt;
            worldDist += runner.baseSpeed * dt;
        }

        boolean chaserCanMove = !chaser.isStunned() && !chaser.isFrozen();
        if (chaserCanMove) {
            if (chaser.x < runner.x - CHASER_GAP[diffIdx]) chaser.x += chaser.baseSpeed * dt;
            chaserAutoJump();
        }

        runner.update(dt, GROUND_Y, runnerCanMove);
        chaser.update(dt, GROUND_Y, chaserCanMove);

        cameraX   = runner.x - W * 0.35f;
        bgLayer1X -= runner.baseSpeed * 0.2f * dt;
        bgLayer2X -= runner.baseSpeed * 0.5f * dt;

        if (runner.x + W > nextSpawn) {
            spawnObstacle();
            nextSpawn += SPAWN_INTERVAL + rng.nextFloat() * 160f;
        }

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obs = it.next();
            if (obs.x + obs.width + DESPAWN_MARGIN < cameraX) { it.remove(); continue; }
            checkObstacleCollision(obs);
        }

        // ── Chaser catch check ────────────────────────────────────────────
        // Hardcore: no invincibility — every frame the chaser is on top drains a life
        boolean catchable = NO_INVINCIBILITY[diffIdx] ? true : !runner.isInvincible();
        if (catchable) {
            float dx = Math.abs(runner.x - chaser.x);
            float dy = Math.abs(runner.y - chaser.y);
            if (dx < CATCH_DIST && dy < 48f) {
                runner.loseLife();
                chaser.applyStun(0.6f);
            }
        }

        // ── Hotel atmosphere tick ─────────────────────────────────────────
        if (diff == Difficulty.HOTEL) {
            hotelVignettePhase += dt * 0.4f;
            if (hotelRng.nextFloat() < 0.008f)
                hotelFlickerAlpha = 0.08f + hotelRng.nextFloat() * 0.16f;
            hotelFlickerAlpha = Math.max(0f, hotelFlickerAlpha - 0.012f);
        }

        if (runner.lives <= 0) {
            state = State.GAME_OVER;
            gameTimer.stop();
        }
    }

    // ── Obstacle helpers ──────────────────────────────────────────────────────
    private void spawnObstacle() {
        Obstacle.Type type = rng.nextBoolean() ? Obstacle.Type.GROUND : Obstacle.Type.AERIAL;
        obstacles.add(new Obstacle(runner.x + W * 0.9f, GROUND_Y, type));
    }

    private void checkObstacleCollision(Obstacle obs) {
        if (!runner.isInvincible() && runner.getBounds().intersects(obs.getBounds())) {
            boolean safe = (obs.type == Obstacle.Type.GROUND && runner.isDucking)
                    || (obs.type == Obstacle.Type.AERIAL && !runner.onGround);
            if (!safe) runner.loseLife();
        }
        if (!chaser.isInvincible() && chaser.getBounds().intersects(obs.getBounds())) {
            if (obs.type == Obstacle.Type.GROUND) chaser.applyStun(STUN_NORMAL[diffIdx]);
            else                                  chaser.applyFreeze(FREEZE_TIME[diffIdx]);
        }
    }

    private void chaserAutoJump() {
        if (!chaser.onGround) return;
        for (Obstacle obs : obstacles) {
            if (obs.type == Obstacle.Type.GROUND && obs.x > chaser.x && obs.x < chaser.x + 110f) {
                chaser.jump();
                break;
            }
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────
    private void renderToOffscreen() {
        og.setColor(new Color(20, 18, 30));
        og.fillRect(0, 0, W, H);

        switch (state) {
            case PLAYING   -> drawGame();
            case PAUSED    -> { drawGame(); drawPause(); }
            case GAME_OVER -> { drawGame(); drawGameOver(); }
        }
    }

    private void drawGame() {
        drawBackground();
        drawGround();
        for (Obstacle obs : obstacles) obs.draw(og, cameraX);
        if (chaser != null) chaser.draw(og, cameraX);
        if (runner != null) runner.draw(og, cameraX);
        if (diff == Difficulty.HOTEL) drawHotelAtmosphere();
        drawHUD();
    }

    // ── Background (mode-tinted) ───────────────────────────────────────────────
    private void drawBackground() {
        GradientPaint sky = new GradientPaint(
                0, 0,             SKY_TOP[diffIdx],
                0, (int)GROUND_Y, SKY_BOT[diffIdx]);
        og.setPaint(sky);
        og.fillRect(0, 0, W, (int)GROUND_Y);

        og.setColor(BUILD_FAR[diffIdx]);
        int[] bldW = {80, 55, 110, 70, 95, 60, 120, 75};
        int[] bldH = {180, 130, 240, 150, 210, 120, 270, 165};
        float offFar = bgLayer1X % 600;
        for (int i = 0; i < 8; i++) {
            int bx = (int)((offFar + i * 180) % (W + 180)) - 90;
            og.fillRect(bx, (int)GROUND_Y - bldH[i], bldW[i], bldH[i]);
        }

        og.setColor(BUILD_NEAR[diffIdx]);
        float offNear = bgLayer2X % 400;
        for (int i = 0; i < 8; i++) {
            int bx = (int)((offNear + i * 200) % (W + 200)) - 100;
            og.fillRect(bx, (int)GROUND_Y - 90, 42, 90);
        }
    }

    // ── Ground ────────────────────────────────────────────────────────────────
    private void drawGround() {
        Color groundFill = diff == Difficulty.HOTEL ? new Color(40, 22, 22)
                : diff == Difficulty.HARDCORE ? new Color(38, 20, 20)
                : new Color(55, 50, 70);
        Color groundLine = diff == Difficulty.HOTEL ? new Color(80, 35, 35)
                : diff == Difficulty.HARDCORE ? new Color(70, 30, 30)
                : new Color(90, 80, 110);

        og.setColor(groundFill);
        og.fillRect(0, (int)GROUND_Y, W, H - (int)GROUND_Y);

        og.setColor(groundLine);
        og.setStroke(new BasicStroke(2.5f));
        og.drawLine(0, (int)GROUND_Y, W, (int)GROUND_Y);

        og.setColor(new Color(groundLine.getRed(), groundLine.getGreen(), groundLine.getBlue(), 160));
        og.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                1f, new float[]{22f, 16f}, cameraX % 38));
        og.drawLine(0, (int)GROUND_Y + 14, W, (int)GROUND_Y + 14);
        og.setStroke(new BasicStroke(1f));
    }

    // ── Hotel atmosphere overlay ───────────────────────────────────────────────
    private void drawHotelAtmosphere() {
        // Slow pulsing red vignette
        float pulse = 0.5f + 0.5f * (float)Math.sin(hotelVignettePhase);
        int vigAlpha = (int)(80 + pulse * 60);
        RadialGradientPaint vig = new RadialGradientPaint(
                W / 2f, H / 2f, W * 0.65f,
                new float[]{0.3f, 1.0f},
                new Color[]{new Color(0, 0, 0, 0), new Color(80, 0, 0, vigAlpha)});
        og.setPaint(vig);
        og.fillRect(0, 0, W, H);

        // Random light flicker
        if (hotelFlickerAlpha > 0f) {
            og.setColor(new Color(0, 0, 0, (int)(hotelFlickerAlpha * 255)));
            og.fillRect(0, 0, W, H);
        }
    }

    // ── HUD ───────────────────────────────────────────────────────────────────
    private void drawHUD() {
        if (runner == null) return;

        // ── Lives (hidden in Hardcore — you have 1, you know) ─────────────
        if (diff != Difficulty.HARDCORE) {
            og.setFont(new Font("Monospaced", Font.BOLD, 18));
            og.setColor(Color.WHITE);
            og.drawString("LIVES:", 18, 32);
            for (int i = 0; i < START_LIVES[diffIdx]; i++) {
                og.setColor(i < runner.lives ? new Color(220, 60, 80) : new Color(60, 60, 70));
                og.fillOval(95 + i * 26, 14, 18, 18);
            }
        } else {
            // Hardcore: show a single skull-like indicator
            og.setFont(new Font("Monospaced", Font.BOLD, 16));
            og.setColor(runner.lives > 0 ? new Color(220, 60, 60) : new Color(80, 20, 20));
            og.drawString("ONE LIFE", 18, 32);
        }

        // ── Distance (centre top) ─────────────────────────────────────────
        og.setFont(new Font("Monospaced", Font.PLAIN, 17));
        drawCenteredString(og, String.format("DIST: %dm", (int)(worldDist / 10f)),
                30, new Color(200, 200, 255));

        // ── Timer (top right) ─────────────────────────────────────────────
        if (TIME_LIMIT[diffIdx] > 0) {
            int mins = (int)(timeLeft / 60f);
            int secs = (int)(timeLeft % 60f);
            String timeStr = String.format("%d:%02d", mins, secs);
            // Turn red when under 20 seconds
            Color timeCol = timeLeft <= 20f
                    ? new Color(220, 50 + (int)(timeLeft / 20f * 150), 50)
                    : new Color(200, 200, 255);
            og.setFont(new Font("Monospaced", Font.BOLD, 19));
            FontMetrics fm = og.getFontMetrics();
            og.setColor(timeCol);
            og.drawString(timeStr, W - fm.stringWidth(timeStr) - 18, 30);
        } else {
            // Hotel: show elapsed time instead
            og.setFont(new Font("Monospaced", Font.PLAIN, 17));
            String time = String.format("TIME: %ds", (int)elapsed);
            FontMetrics fm = og.getFontMetrics();
            og.setColor(new Color(200, 180, 180));
            og.drawString(time, W - fm.stringWidth(time) - 18, 30);
        }

        // ── Mode label (subtle, top centre below dist) ────────────────────
        og.setFont(new Font("Monospaced", Font.ITALIC, 12));
        og.setColor(new Color(120, 100, 120, 160));
        String modeLabel = diff.name();
        FontMetrics fm2 = og.getFontMetrics();
        og.drawString(modeLabel, (W - fm2.stringWidth(modeLabel)) / 2, 48);

        // ── Status effects ────────────────────────────────────────────────
        if (runner.isStunned()) drawStatusTag("STUNNED", new Color(255, 220, 0),   18, 58);
        if (runner.isFrozen())  drawStatusTag("FROZEN",  new Color(100, 180, 255), 18, 76);
    }

    private void drawStatusTag(String text, Color c, int x, int y) {
        og.setColor(c);
        og.setFont(new Font("Monospaced", Font.BOLD, 14));
        og.drawString(text, x, y);
    }

    // ── Pause ─────────────────────────────────────────────────────────────────
    private void drawPause() {
        og.setColor(new Color(0, 0, 0, 150));
        og.fillRect(0, 0, W, H);
        og.setFont(new Font("Monospaced", Font.BOLD, 52));
        drawCenteredString(og, "PAUSED", H / 2 - 26, Color.WHITE);
        og.setFont(new Font("Monospaced", Font.PLAIN, 20));
        drawCenteredString(og, "Press P to resume", H / 2 + 38, Color.LIGHT_GRAY);
    }

    // ── Game over / time-up screen ────────────────────────────────────────────
    private void drawGameOver() {
        og.setColor(new Color(10, 5, 5, 200));
        og.fillRect(0, 0, W, H);

        boolean escaped = TIME_LIMIT[diffIdx] > 0 && timeLeft <= 0;

        if (escaped) {
            // Runner survived the full timer
            og.setFont(new Font("Monospaced", Font.BOLD, 60));
            drawCenteredString(og, "YOU ESCAPED!", H / 2 - 80, new Color(80, 220, 140));
            og.setFont(new Font("Monospaced", Font.PLAIN, 22));
            drawCenteredString(og,
                    String.format("Distance: %dm   Survived: %s",
                            (int)(worldDist / 10f),
                            diff == Difficulty.NORMAL ? "2:00" : "1:30"),
                    H / 2, Color.LIGHT_GRAY);
        } else {
            og.setFont(new Font("Monospaced", Font.BOLD, 60));
            String headline = diff == Difficulty.HOTEL ? "I FOUND YOU." : "CAUGHT!";
            drawCenteredString(og, headline, H / 2 - 80,
                    diff == Difficulty.HOTEL ? new Color(200, 40, 40) : new Color(220, 60, 60));

            og.setFont(new Font("Monospaced", Font.PLAIN, 22));
            drawCenteredString(og,
                    String.format("Distance: %dm   Time: %ds",
                            (int)(worldDist / 10f), (int)elapsed),
                    H / 2, Color.LIGHT_GRAY);
        }

        og.setFont(new Font("Monospaced", Font.PLAIN, 18));
        drawCenteredString(og, "ENTER — play again     ESC — menu",
                H / 2 + 56, new Color(160, 130, 130));
    }

    private void drawCenteredString(Graphics2D g, String s, int y, Color c) {
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
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

    // ── KeyListener ───────────────────────────────────────────────────────────
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();

        if (state == State.GAME_OVER) {
            if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_SPACE) { restartGame(); return; }
            if (k == KeyEvent.VK_ESCAPE) { Main.Main.goToMenu(frame); return; }
            return;
        }

        if (k == KeyEvent.VK_P) {
            if      (state == State.PLAYING) { state = State.PAUSED;  gameTimer.stop();  }
            else if (state == State.PAUSED)  { state = State.PLAYING; gameTimer.start(); }
            return;
        }

        if (state == State.PAUSED) return;

        if (k == KeyEvent.VK_W || k == KeyEvent.VK_UP)   keyJump = true;
        if (k == KeyEvent.VK_S || k == KeyEvent.VK_DOWN) keyDuck = true;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_W || k == KeyEvent.VK_UP)   keyJump = false;
        if (k == KeyEvent.VK_S || k == KeyEvent.VK_DOWN) {
            keyDuck = false;
            if (runner != null) runner.isDucking = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}