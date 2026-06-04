package Panel;



import Engine.Music;
import Engine.Obstacle;
import Engine.Player;

import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * GamePanel — orchestrates game state, delegates to:
 *   {@link PhysicsEngine}      — gravity / jump / knockback maths
 *   {@link InputHandler}       — raw key state
 *   {@link BackgroundRenderer} — scrolling BG, floor grid, alarm lights, scanlines
 *   {@link HudRenderer}        — HUD, pause overlay, game-over overlay
 */
public class GamePanel extends JPanel implements ActionListener {

    // ── Delegates ─────────────────────────────────────────────────────────────
private final AchievementSystem  achievements = new AchievementSystem(); // ← ADD THIS
    
    // ── Screen / timing constants ─────────────────────────────────────────────
    private static final int   W        = 1280;
    private static final int   H        = 720;
    private static final float GROUND_Y = 580f;
    // Target 120 FPS; the game loop thread uses precise nanosecond timing
    private static final int   TARGET_FPS = 60;
    private static final long  TICK_NS    = 1_000_000_000L / TARGET_FPS;
    private static final float DT         = 1f / TARGET_FPS;

    /**
     * How many seconds the audio engine typically takes to actually start
     * playing after bgm.play() is called.  Pre-seeds the BeatClock so visual
     * effects land on the beat rather than trailing it.
     * Tune this value if effects still feel early or late (try 0.1 – 0.3 s).
     */
    private static final float AUDIO_LATENCY = 0.20f;

    /**
     * Extra bar offset if the clock still feels ahead or behind after tuning
     * AUDIO_LATENCY.  2 bars = 3.0 s at 160 BPM.  Set to 0 if not needed.
     */
    private static final float BEAT_OFFSET_BARS = 0f;

    // ── Player geometry ───────────────────────────────────────────────────────
    private static final int PW     = 180;
    private static final int PH     = 310;
    private static final int DUCK_H = 160;

    // Players are spread far apart: chaser on left ~15%, runner on right ~80%
    private static final float RUNNER_X = W * 0.80f;
    private static final float CHASER_X = W * 0.15f;

    // ── Difficulty tables ─────────────────────────────────────────────────────
    public enum Difficulty { NORMAL, HARDCORE, HOTEL }

    private static final float[] OBS_SPEED  = { 340f, 430f, 270f };
    private static final float[] SPEED_RAMP = {   3f,   5f,  1.5f };
    private static final float[] TIME_LIMIT = { 130f,  120f,  -1f  };
    private static final float[] STUN_DUR   = {  1.1f, 1.8f,  1.1f };
    // Obstacle pushback in pixels: [NORMAL, HARDCORE, HOTEL] for chaser and runner
    private static final float[] OBS_CHASER_KB = {  5f,  10f,   5f  };
    private static final float[] OBS_RUNNER_KB = { 10f,  15f,  10f  };
    // Projectile knockback (existing chaser-win mechanic)
    private static final float[] CHASER_KB  = {  60f,  80f,   50f  };

    private static final float WARN_TIME      = 2.0f;
    private static final float THROW_COOLDOWN = 5f;

    // ── Game state ────────────────────────────────────────────────────────────
    public enum State { PLAYING, PAUSED, GAME_OVER }
    private State   state = State.PLAYING;
    private boolean runnerWon;

    private final Difficulty diff;
    private final int        di;

    private float elapsed   = 0f;
    private float timeLeft;
    private float obsSpeed;

    // ── Runner state ──────────────────────────────────────────────────────────
    private Player  runner;
    private float   runnerY       = GROUND_Y;
    private float   runnerVY      = 0f;
    private boolean runnerGround  = true;
    private boolean runnerDuck    = false;
    private float   runnerStun    = 0f;
    private float   runnerInvince = 0f;
    private int     runnerHits    = 0;
    private float   runnerKnockX  = RUNNER_X;   // runner's current X — only resets on new game
    private float   runnerDuckTimer  = 0f;
    private float   chaserDuckTimer  = 0f;
    private boolean runnerDuckConsumed  = false;  // true after timer expires; requires key re-press
    private boolean chaserDuckConsumed  = false;
    private static final float DUCK_MAX = 2.0f;

    // ── Chaser state ──────────────────────────────────────────────────────────
    private Player  chaser;
    private float   chaserY             = GROUND_Y;
    private float   chaserVY            = 0f;
    private boolean chaserGround        = true;
    private boolean chaserDuck          = false;
    private float   chaserStun          = 0f;
    private float   chaserX             = CHASER_X;
    private float   chaserThrowCooldown = 0f;
    private static final float ATTACK_ANIM_DUR = 0.5f;  // seconds the attack anim plays

    // ── Obstacles & warnings ──────────────────────────────────────────────────
    private record Warning(float y, Obstacle.Type type, float countdown) {}
    private final List<Obstacle> obstacles   = new ArrayList<>();
    private final List<Warning>  warnings    = new ArrayList<>();
    private float nextSpawnTimer = 1.8f;

    // ── Projectiles ───────────────────────────────────────────────────────────
    private static class Projectile {
        float x, y;
        static final float SPEED = 680f;
        static final int   W2    = 24;
        static final int   H2    = 24;
        Projectile(float sx, float sy) { x = sx; y = sy; }
        Rectangle bounds() { return new Rectangle((int)x - W2/2, (int)y - H2/2, W2, H2); }
    }
    private final List<Projectile> projectiles = new ArrayList<>();

    // ── FPS Counter ──────────────────────────────────────────────────────────────
    private int fps = 0;
    private int frames = 0;
    private long lastFpsTime = System.currentTimeMillis();

    // ── Delegates ─────────────────────────────────────────────────────────────
    private final InputHandler       input    = new InputHandler();
    private final BackgroundRenderer bgr;
    private final HudRenderer        hud      = new HudRenderer();
    private final BeatClock          clock    = new BeatClock(160f);
    private final SongTimeline       timeline = new SongTimeline();

    // ── Rendering — ping-pong double buffer ───────────────────────────────────
    // The game-loop thread always writes to backBuffer/og.
    // When a frame is complete, frontBuffer is swapped atomically via a volatile
    // reference so paintComponent (EDT) never reads a half-written frame.
    private final BufferedImage bufferA;
    private final BufferedImage bufferB;
    private final Graphics2D    gA;
    private final Graphics2D    gB;
    private BufferedImage backBuffer;   // game-loop thread writes here
    private Graphics2D    og;           // always points at backBuffer's G2D
    private volatile BufferedImage frontBuffer;  // EDT reads this — volatile for visibility

    // ── Misc ──────────────────────────────────────────────────────────────────
    @SuppressWarnings("unused") private Music bgm;
    private final Clip   menuMusicClip;
    private final JFrame frame;
    private final Random rng = new Random();

    // ── Constructors ──────────────────────────────────────────────────────────

    public GamePanel(JFrame frame, String modeName, Clip menuClip) throws Exception {
        this.frame         = frame;
        this.menuMusicClip = menuClip;

        Difficulty parsed;
        try   { parsed = Difficulty.valueOf(modeName.toUpperCase()); }
        catch (IllegalArgumentException e) { parsed = Difficulty.NORMAL; }
        diff = parsed;
        di   = diff.ordinal();

        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(input);

        // Guarantee achievements are saved even if the window is closed mid-game
        // (i.e. before endGame() is ever called).
        Runtime.getRuntime().addShutdownHook(new Thread(achievements::save, "AchievementSaveHook"));

        // Allocate both ping-pong buffers with identical settings
        bufferA = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        bufferB = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        gA = bufferA.createGraphics();
        gB = bufferB.createGraphics();
        for (Graphics2D g2 : new Graphics2D[]{ gA, gB }) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,       RenderingHints.VALUE_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,   RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        }
        // Start: game loop writes to bufferA, EDT reads bufferB (initially black)
        backBuffer  = bufferA;
        og          = gA;
        frontBuffer = bufferB;

        bgr = new BackgroundRenderer(rng);
        bgr.init();

        achievements.load();   // Load persisted achievements from JSON
        tryLoadMusic();
        resetWorld();

        // High-resolution game loop thread — more stable than Swing Timer
        Thread gameLoop = new Thread(() -> {
            long lastTime = System.nanoTime();
            while (true) {
                long now   = System.nanoTime();
                long delta = now - lastTime;
                if (delta >= TICK_NS) {
                    lastTime = now - (delta % TICK_NS); // absorb overrun
                    handleInput();
                    if (state == State.PLAYING) update(DT);
                    render();
                    // Atomic buffer swap: promote the just-finished back buffer to front.
                    // paintComponent only ever reads frontBuffer (volatile), so it will
                    // never see a partially-written frame regardless of EDT scheduling.
                    frontBuffer = backBuffer;
                    backBuffer  = (backBuffer == bufferA) ? bufferB : bufferA;
                    og          = (backBuffer == bufferA) ? gA      : gB;
                    SwingUtilities.invokeLater(this::repaint);
                    frames++;
                    long cur = System.currentTimeMillis();
                    if (cur - lastFpsTime >= 1000) {
                        fps = frames;
                        frames = 0;
                        lastFpsTime = cur;
                    }
                    input.consumeSingleFrameActions();
                } else {
                    // Yield to avoid 100% CPU spin
                    Thread.yield();
                }
            }
        }, "GameLoop");
        gameLoop.setDaemon(true);
        gameLoop.start();
    }

    /** Backwards-compatible constructor (no menu clip). */
    public GamePanel(JFrame frame, String modeName) throws Exception {
        this(frame, modeName, null);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    // ── Music ─────────────────────────────────────────────────────────────────

    private void tryLoadMusic() {
        if (menuMusicClip != null) {
            if (menuMusicClip.isRunning()) menuMusicClip.stop();
            menuMusicClip.close();
        }
        try {
            String track = (diff == Difficulty.HARDCORE)
                    ? "assets/music/HardcoreChaseTrack"
                    : "assets/music/NormalChaseTrack";
            bgm = new Music(track);
            bgm.play();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ── World reset ───────────────────────────────────────────────────────────

    private void resetWorld() {
        elapsed  = 0f;
        timeLeft = TIME_LIMIT[di];
        obsSpeed = OBS_SPEED[di];

        runnerY = GROUND_Y; runnerVY = 0f; runnerGround = true;
        runnerDuck = false; runnerStun = 0f; runnerInvince = 0f; runnerDuckTimer = 0f; runnerDuckConsumed = false;
        runnerHits = 0; runnerKnockX = RUNNER_X;

        chaserX = CHASER_X; chaserY = GROUND_Y; chaserVY = 0f;
        chaserGround = true; chaserDuck = false; chaserStun = 0f; chaserDuckTimer = 0f; chaserDuckConsumed = false;
        chaserThrowCooldown = 0f;

        obstacles.clear(); warnings.clear(); projectiles.clear();
        nextSpawnTimer = 1.8f;

        bgr.reset();
        float barDur = 60f / 160f * 4f;   // 1.5 s per bar at 160 BPM
        clock.reset(AUDIO_LATENCY + BEAT_OFFSET_BARS * barDur);
        timeline.reset();

        runner = new Player("runner", true,  runnerKnockX, runnerY, OBS_SPEED[di]);
        chaser = new Player("chaser", false, chaserX,      chaserY, OBS_SPEED[di]);
        // Hardcore: runner has only 1 life; Normal/Hotel: 3 lives
        runner.lives = (diff == Difficulty.HARDCORE) ? 1 : 3;

        state = State.PLAYING;
        achievements.resetForNewGame();

    }

    // ── Main loop ─────────────────────────────────────────────────────────────

    @Override
    public void actionPerformed(ActionEvent e) {
        // Intentionally empty — the game loop thread drives all updates.
    }

    // ── Input processing ──────────────────────────────────────────────────────

    private void handleInput() {
        // Pause toggle
        if (input.pause) {
            if      (state == State.PLAYING) { state = State.PAUSED; }
            else if (state == State.PAUSED)  { state = State.PLAYING; }
            return;
        }
        if (state == State.PAUSED) return;

        // Game-over screen actions
        if (state == State.GAME_OVER) {
            if (input.confirm) resetWorld();
            if (input.menu)    Main.Main.goToMenu(frame);
            return;
        }
    }

    // ── Update ────────────────────────────────────────────────────────────────

    private void update(float dt) {
        elapsed += dt;
        achievements.update(dt);
achievements.tick(elapsed, diff);
        clock.update(dt);
        timeline.update(clock, dt);

        // Timer
        if (TIME_LIMIT[di] > 0) {
            timeLeft -= dt;
if (timeLeft <= 0) {
    timeLeft = 0;
    achievements.onTimerExpired(); // ← ADD THIS
    endGame(true);
    return;
}        }

        // Speed ramp: accelerate during intense sections, recover during calm ones
        float targetSpeed = timeline.isIntenseSection()
                ? OBS_SPEED[di] + SPEED_RAMP[di] * elapsed
                : OBS_SPEED[di];
        // Smooth transition so speed doesn't snap
        obsSpeed += (targetSpeed - obsSpeed) * 2.0f * dt;

        // Cool-down timers
        runnerStun          = Math.max(0, runnerStun          - dt);
        runnerInvince       = Math.max(0, runnerInvince       - dt);
        chaserStun          = Math.max(0, chaserStun          - dt);
        chaserThrowCooldown = Math.max(0, chaserThrowCooldown - dt);

        updateRunner(dt);
        updateChaser(dt);
        updateObstacles(dt);
        updateProjectiles(dt);
        checkChaserRunnerContact();
    }

    private void updateRunner(float dt) {
        if (runnerStun <= 0) {
            if (input.runnerJump && runnerGround) {
                runnerVY     = PhysicsEngine.JUMP_FORCE;
                runnerGround = false;
                runnerDuck   = false;
                runnerDuckTimer = 0f;
                achievements.onJump();
            }
            boolean wasDucking = runnerDuck;
            // Duck: pressing starts 2s timer; releasing key ends early; timer expiry requires new press
            if (!input.runnerDuck) {
                // Key released — reset consumed flag so next press works
                runnerDuckConsumed = false;
            }
            if (input.runnerDuck && runnerGround && !runnerDuckConsumed) {
                if (!wasDucking) {
                    runnerDuck = true;
                    runnerDuckTimer = DUCK_MAX;
                    achievements.onDuck();
                } else {
                    runnerDuckTimer -= dt;
                    if (runnerDuckTimer <= 0) {
                        runnerDuck = false;
                        runnerDuckTimer = 0f;
                        runnerDuckConsumed = true;  // must release key before ducking again
                    }
                }
            } else if (!input.runnerDuck || !runnerGround) {
                runnerDuck = false;
                runnerDuckTimer = 0f;
            }
        }

        if (!runnerGround) {
            float[] res = PhysicsEngine.applyGravity(runnerY, runnerVY, GROUND_Y, dt);
            runnerY      = res[0];
            runnerVY     = res[1];
            runnerGround = (runnerY >= GROUND_Y);
        }

        // runnerKnockX is NOT recovered — it stays wherever knockback left it
    }

    private void updateChaser(float dt) {
        if (chaserStun <= 0) {
            if (input.chaserJump && chaserGround) {
                chaserVY     = PhysicsEngine.JUMP_FORCE;
                chaserGround = false;
                chaserDuck   = false;
            }
            boolean wasChaserDucking = chaserDuck;
            if (!input.chaserDuck) {
                chaserDuckConsumed = false;
            }
            if (input.chaserDuck && chaserGround && !chaserDuckConsumed) {
                if (!wasChaserDucking) {
                    chaserDuck = true;
                    chaserDuckTimer = DUCK_MAX;
                } else {
                    chaserDuckTimer -= dt;
                    if (chaserDuckTimer <= 0) {
                        chaserDuck = false;
                        chaserDuckTimer = 0f;
                        chaserDuckConsumed = true;
                    }
                }
            } else if (!input.chaserDuck || !chaserGround) {
                chaserDuck = false;
                chaserDuckTimer = 0f;
            }

            if (input.chaserThrow && chaserThrowCooldown <= 0) {
                // Spawn projectile at chaser's chest height based on current position
                int chaserDrawH = chaserDuck ? 180 : 360;
                float projX = chaserX + (chaserDuck ? 150f : 180f); // right side of chaser
                float projY = chaserY - chaserDrawH * 0.55f;         // chest/arm height
                projectiles.add(new Projectile(projX, projY));
                chaserThrowCooldown = THROW_COOLDOWN;
                achievements.onProjectileFired();
            }
        }

        if (!chaserGround) {
            float[] res = PhysicsEngine.applyGravity(chaserY, chaserVY, GROUND_Y, dt);
            chaserY      = res[0];
            chaserVY     = res[1];
            chaserGround = (chaserY >= GROUND_Y);
        }

        // chaserX is NOT recovered — it stays wherever knockback left it
        if (chaserX + PW / 2f < 0) { endGame(true); }
    }

    private void updateObstacles(float dt) {
        // Spawn warnings
        nextSpawnTimer -= dt;
        if (nextSpawnTimer <= 0) {
            Obstacle.Type type = rng.nextBoolean() ? Obstacle.Type.GROUND : Obstacle.Type.AERIAL;
            // Warning indicators sit near where the obstacle will appear
            float y = (type == Obstacle.Type.GROUND) ? GROUND_Y - Obstacle.GROUND_H / 2f : Obstacle.AERIAL_BOTTOM_Y - Obstacle.AERIAL_H / 2f;
            warnings.add(new Warning(y, type, WARN_TIME));
            nextSpawnTimer = 1.4f + rng.nextFloat() * 1.2f;
        }

        // Promote warnings → obstacles
        List<Warning> keep = new ArrayList<>();
        for (Warning w : warnings) {
            float nc = w.countdown() - dt;
            if (nc <= 0) {
                // Obstacle constructor sets its own Y correctly (ground sits on floor,
                // aerial hangs at Obstacle.AERIAL_BOTTOM_Y = 400 from screen top)
                Obstacle obs = new Obstacle(W + 180, GROUND_Y, w.type());
                obstacles.add(obs);
            } else {
                keep.add(new Warning(w.y(), w.type(), nc));
            }
        }
        warnings.clear();
        warnings.addAll(keep);

        // Move obstacles and check collisions
        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obs = it.next();
            obs.x -= obsSpeed * dt * 3;
            if (obs.x + obs.width < -80) { it.remove(); achievements.onObstacleAvoided(); continue; }
            handleObstacleCollisions(obs);
        }
    }

    private void updateProjectiles(float dt) {
        Iterator<Projectile> pit = projectiles.iterator();
        while (pit.hasNext()) {
            Projectile p = pit.next();
            p.x += Projectile.SPEED * dt;
            if (p.x > W + 60) { pit.remove(); continue; }
            if (runnerInvince <= 0 && p.bounds().intersects(runnerHitBox())) {
                pit.remove();
                achievements.onProjectileHit();
                hitRunner();
                if (state == State.GAME_OVER) return;
            }
        }
    }

    // ── Collision helpers ─────────────────────────────────────────────────────

    private Rectangle runnerHitBox() {
        int rh = runnerDuck ? DUCK_H : PH, rw = PW - 30;
        return new Rectangle((int)runnerKnockX - rw/2, (int)runnerY - rh, rw, rh);
    }

    private Rectangle chaserHitBox() {
        int ch = chaserDuck ? DUCK_H : PH, cw = PW - 30;
        return new Rectangle((int)chaserX - cw/2, (int)chaserY - ch, cw, ch);
    }

    private void handleObstacleCollisions(Obstacle obs) {
        Rectangle obsR = obs.getCollisionBounds();

        // GROUND obstacles (bottom) → jump to dodge | AERIAL obstacles (top) → duck to dodge
        if (runnerHitBox().intersects(obsR)) {
            boolean safe = (obs.type == Obstacle.Type.GROUND && !runnerGround)  // jumped over
                    || (obs.type == Obstacle.Type.AERIAL && runnerDuck);          // ducked under
            if (!safe && state != State.GAME_OVER) {
                runnerKnockX -= OBS_RUNNER_KB[di];
                runner.applyStun(0.3f);
                runnerStun = Math.max(runnerStun, 0.3f);
            }
        }

        if (chaserStun <= 0 && chaserHitBox().intersects(obsR)) {
            boolean safe = (obs.type == Obstacle.Type.GROUND && !chaserGround)  // jumped over
                    || (obs.type == Obstacle.Type.AERIAL && chaserDuck);          // ducked under
            if (!safe) {
                chaserStun = STUN_DUR[di];
                chaserX   -= OBS_CHASER_KB[di];
                chaser.applyStun(STUN_DUR[di]);
            }
        }
    }

    /**
     * Called every tick. If the chaser body-touches the runner (and runner is
     * not invincible), the runner loses a life, resets to their original X,
     * and the chaser plays an "attack" animation.
     */
    private void checkChaserRunnerContact() {
        if (state == State.GAME_OVER) return;
        if (runnerInvince > 0) return;   // runner is invincible — no hit
        if (chaserStun > 0) return;      // chaser is stunned — can't attack
        if (chaserHitBox().intersects(runnerHitBox())) {
            hitRunnerByContact();
        }
    }

    /** Runner touched by chaser directly — loses a life and resets to original X. */
    private void hitRunnerByContact() {
        achievements.onRunnerHit();
        runnerHits++;
        int maxLives = (diff == Difficulty.HARDCORE) ? 1 : 3;
        runner.lives  = maxLives - runnerHits;
        runnerStun    = STUN_DUR[di];
        runnerInvince = STUN_DUR[di] + 1.5f;
        runnerKnockX  = RUNNER_X;
        runner.applyStun(STUN_DUR[di]);

        chaser.playAttack(ATTACK_ANIM_DUR);
        if (runnerHits >= maxLives) endGame(false);
    }

    private void hitRunner() {
        achievements.onRunnerHit();

        int maxLives = (diff == Difficulty.HARDCORE) ? 1 : 3;

        runnerStun    = STUN_DUR[di];
        runnerInvince = STUN_DUR[di] + 1.0f;
        runnerKnockX  = Math.max(chaserX + PW / 2f + 20f, runnerKnockX - 90f);
        runner.applyStun(STUN_DUR[di]);
    }

    private void endGame(boolean runnerWins) {
        if (runnerWins) {
        achievements.onRunnerWin(elapsed, diff); // ← ADD THIS
    } else {
        achievements.onChaserWin(diff);          // ← ADD THIS
    }
        runnerWon = runnerWins;
        state     = State.GAME_OVER;
        achievements.save();   // Persist unlocked achievements to JSON
    }

    // ── Render ────────────────────────────────────────────────────────────────

    private void render() {
        og.setColor(Color.BLACK);
        og.fillRect(0, 0, W, H);

        // Save transform, apply shake, draw world, then restore — prevents drift
        java.awt.geom.AffineTransform saved = og.getTransform();
        og.translate((int) timeline.shakeX, (int) timeline.shakeY);
        bgr.drawBackground(og, obsSpeed, DT, diff, clock, timeline);
        drawWarnings();
        for (Obstacle obs : obstacles) obs.draw(og, 0f);
        drawProjectiles();
        drawPlayers();
        if (diff == Difficulty.HOTEL) bgr.drawHotelAtmosphere(og, DT, timeline);
        og.setTransform(saved);   // hard restore — no floating point drift

        hud.drawHUD(og, runnerHits, chaserThrowCooldown, THROW_COOLDOWN,
                timeLeft, TIME_LIMIT[di], elapsed, diff);
                achievements.drawToasts(og); // ← ADD THIS



        og.setColor(Color.WHITE);
        og.setFont(new Font("Consolas", Font.BOLD, 24));
        og.drawString("FPS: " + fps, 20, 40);

        if (state == State.PAUSED)    hud.drawPauseOverlay(og);
        if (state == State.GAME_OVER) hud.drawGameOverOverlay(og, runnerWon, diff, elapsed, timeLeft);

        // Greyscale post-process — fast int-array path, only active during grey sections
        bgr.applyGreyscale(backBuffer, og, timeline.grey);
    }

    private void drawWarnings() {
        for (Warning w : warnings) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(w.countdown() * 8f);
            int   alpha = 140 + (int)(pulse * 115);
            int   size  = 70;
            int   x     = W - 120;
            int   y     = (int) w.y();

            if (w.type() == Obstacle.Type.GROUND) {
                og.setColor(new Color(255, 220, 40, alpha));
                og.fillPolygon(new int[]{ x, x - size/2, x + size/2 },
                        new int[]{ y + size/2, y - size/2, y - size/2 }, 3);
                og.setColor(Color.BLACK);
                og.setFont(new Font("Arial", Font.BOLD, 32));
                og.drawString("!", x - 5, y + 12);
            } else {
                og.setColor(new Color(80, 200, 255, alpha));
                og.fillPolygon(new int[]{ x, x - size/2, x + size/2 },
                        new int[]{ y - size/2, y + size/2, y + size/2 }, 3);
                og.setColor(Color.BLACK);
                og.setFont(new Font("Arial", Font.BOLD, 32));
                og.drawString("!", x - 5, y + 12);
            }
        }
    }

    private void drawProjectiles() {
        for (Projectile p : projectiles) {
            int r = Projectile.W2;
            og.setColor(new Color(255, 100, 30, 80));  og.fillOval((int)p.x-r-6, (int)p.y-r-6, (r+6)*2, (r+6)*2);
            og.setColor(new Color(255, 160, 60));       og.fillOval((int)p.x-r,   (int)p.y-r,   r*2, r*2);
            og.setColor(Color.WHITE);                   og.fillOval((int)p.x-r/2, (int)p.y-r/2, r, r);
        }
    }

    private void drawPlayers() {
        // Chaser
        chaser.x = chaserX; chaser.y = chaserY;
        chaser.onGround = chaserGround; chaser.isDucking = chaserDuck;
        if (chaserStun > 0) chaser.applyStun(DT);
        chaser.cacheDt(DT); chaser.update(DT, GROUND_Y, true);
        drawPlayerScaled(chaser, (int)chaserX,      (int)chaserY, chaserDuck, false, chaserStun > 0);

        // Runner
        runner.x = runnerKnockX; runner.y = runnerY;
        runner.onGround = runnerGround; runner.isDucking = runnerDuck;
        runner.lives = ((diff == Difficulty.HARDCORE) ? 1 : 3) - runnerHits;
        if (runnerStun > 0) runner.applyStun(DT);
        runner.hitCooldown = runnerInvince;
        runner.cacheDt(DT); runner.update(DT, GROUND_Y, true);

        boolean flash = runnerInvince > 0 && runnerStun <= 0 && (int)(runnerInvince * 10) % 2 == 0;
        if (!flash) drawPlayerScaled(runner, (int)runnerKnockX, (int)runnerY, runnerDuck, true, runnerStun > 0);
    }

    private void drawPlayerScaled(Player p, int cx, int groundY,
                                  boolean ducking, boolean isRunner, boolean stunned) {
        // Width is ALWAYS the standing width — the duck frame uses the same horizontal
        // space as the standing frame (the character crouches down, not sideways).
        // Changing drawW for ducking squishes the sprite horizontally; don't do it.
        int drawW = isRunner ? (Player.STAND_W + 100) : (Player.STAND_W + 140);

        // Height changes per pose: duck = DUCK_H (shorter), stand = full height.
        // Because drawY = groundY - drawH, a shorter drawH means the top of the
        // sprite is lower → player visually sinks into the ground when ducking.
        int drawH = ducking ? Player.DUCK_H : (isRunner ? 340 : 360);

        int drawX = cx - drawW / 2;   // X centre unchanged regardless of pose
        int drawY = groundY - drawH;  // feet always anchored at groundY

        p.draw(og, drawX, drawY, drawW, drawH);
        og.setColor(Color.WHITE);
    }

    // ── Swing paint ───────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        // Do NOT call super.paintComponent — that would clear the panel to black
        // before we draw the offscreen image, causing a visible black flash.
        // Read frontBuffer: the last fully-rendered frame, swapped atomically
        // by the game loop after every render() call. The volatile write on the
        // game thread and this volatile read on the EDT form a happens-before
        // edge, so we are guaranteed to see a complete, non-torn frame.
        BufferedImage frame = frontBuffer;
        if (frame == null) return;
        int   pw    = getWidth(), ph = getHeight();
        float scale = Math.min((float)pw / W, (float)ph / H);
        int   dw    = Math.round(W * scale), dh = Math.round(H * scale);
        int   ox    = (pw - dw) / 2,        oy = (ph - dh) / 2;
        // Fill letterbox bars (if any) with black — only the bars, not the game area
        g.setColor(Color.BLACK);
        if (ox > 0) { g.fillRect(0, 0, ox, ph); g.fillRect(ox + dw, 0, ox + 1, ph); }
        if (oy > 0) { g.fillRect(0, 0, pw, oy); g.fillRect(0, oy + dh, pw, oy + 1); }
        g.drawImage(frame, ox, oy, dw, dh, null);
    }
}