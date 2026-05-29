package Panel;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * AchievementSystem — tracks in-game milestones and renders animated
 * toast notifications on-screen.
 *
 * ── How to use ────────────────────────────────────────────────────────────────
 *  1. Add a field in GamePanel:
 *       private final AchievementSystem achievements = new AchievementSystem();
 *
 *  2. Call update every tick (inside GamePanel.update):
 *       achievements.update(DT);
 *
 *  3. Feed stats each tick (inside GamePanel.update, after updating timers):
 *       achievements.tick(elapsed, runnerHits, runnerDuck, runnerGround,
 *                         chaserThrowCooldown, THROW_COOLDOWN,
 *                         projectiles.size(), obstacles.size(), diff);
 *
 *  4. Notify specific events where they happen in GamePanel:
 *       achievements.onRunnerHit();          // inside hitRunner()
 *       achievements.onProjectileFired();    // inside updateChaser() when throw fires
 *       achievements.onObstacleAvoided();    // inside updateObstacles() when obs leaves screen
 *       achievements.onRunnerWin(elapsed);   // inside endGame(true)
 *       achievements.onChaserWin();          // inside endGame(false)
 *       achievements.onJump();               // inside updateRunner() on jump
 *       achievements.onDuck();               // inside updateRunner() on duck
 *
 *  5. Draw toasts last in GamePanel.render() (after HUD, before greyscale):
 *       achievements.drawToasts(og);
 */
public class AchievementSystem {

    // ── Screen constants (must match GamePanel) ───────────────────────────────
    private static final int W = 1280;
    private static final int H = 720;

    // ── Toast display ─────────────────────────────────────────────────────────
    private static final float TOAST_DURATION  = 3.5f;   // seconds on screen
    private static final float TOAST_SLIDE_IN  = 0.25f;  // slide-in time
    private static final float TOAST_SLIDE_OUT = 0.4f;   // fade-out time
    private static final int   TOAST_W         = 320;
    private static final int   TOAST_H         = 68;
    private static final int   TOAST_MARGIN    = 14;

    // ── Counters & flags ──────────────────────────────────────────────────────
    private int   totalRunnerHits       = 0;   // hits landed on runner this game
    private int   totalProjectilesFired = 0;
    private int   totalObstaclesAvoided = 0;
    private int   totalJumps            = 0;
    private int   totalDucks            = 0;
    private float maxSurvivalTime       = 0f;
    private float consecutiveDodgeTimer = 0f;   // seconds since last hit
    private int   consecutiveDodges     = 0;    // obstacles avoided without getting hit
    private boolean runnerHitThisTick   = false;

    // ── Unlocked achievement IDs ──────────────────────────────────────────────
    private final List<String> unlocked = new ArrayList<>();

    // ── Toast queue ───────────────────────────────────────────────────────────
    private final Queue<Achievement> toastQueue = new LinkedList<>();
    private final List<ActiveToast>  activeToasts = new ArrayList<>();

    // ── All defined achievements ──────────────────────────────────────────────
    private final Achievement[] ALL = {
        // ── Runner achievements ────────────────────────────────────────────
        ach("first_blood",    "First Blood",       "Runner takes the first hit",
                Tier.BRONZE,  "\u2764"),
        ach("three_strikes",  "Three Strikes",     "Runner caught — 3 hits landed",
                Tier.SILVER,  "\uD83C\uDFAF"),
        ach("untouchable",    "Untouchable",        "Runner survives without being hit",
                Tier.GOLD,    "\u2B50"),
        ach("speedrunner",    "Speedrunner",        "Survive 30 seconds",
                Tier.BRONZE,  "\u23F1"),
        ach("marathoner",     "Marathoner",         "Survive 90 seconds",
                Tier.SILVER,  "\uD83C\uDFC3"),
        ach("eternal_runner", "Eternal Runner",     "Survive the full timer",
                Tier.GOLD,    "\uD83D\uDC8E"),
        ach("high_jumper",    "High Jumper",        "Jump 20 times in one game",
                Tier.BRONZE,  "\uD83D\uDD1D"),
        ach("limbo_master",   "Limbo Master",       "Duck 15 times in one game",
                Tier.BRONZE,  "\uD83E\uDD47"),
        ach("close_call",     "Close Call",         "Dodge 5 obstacles in a row",
                Tier.SILVER,  "\uD83D\uDCA8"),
        ach("matrix",         "The Matrix",         "Dodge 15 obstacles in a row",
                Tier.GOLD,    "\uD83D\uDD73"),

        // ── Chaser achievements ────────────────────────────────────────────
        ach("deadeye",        "Dead Eye",           "Land 3 projectile hits",
                Tier.SILVER,  "\uD83D\uDCA5"),
        ach("rapid_fire",     "Rapid Fire",         "Fire 10 projectiles in one game",
                Tier.BRONZE,  "\uD83D\uDD25"),
        ach("marksman",       "Marksman",           "Fire 25 projectiles in one game",
                Tier.SILVER,  "\uD83C\uDFF9"),

        // ── Difficulty-based ───────────────────────────────────────────────
        ach("hardcore_clear", "Hardcore Cleared",   "Win on HARDCORE difficulty",
                Tier.GOLD,    "\uD83D\uDC80"),
        ach("hotel_escape",   "Checked Out",        "Runner escapes the Hotel",
                Tier.GOLD,    "\uD83C\uDFE8"),
        ach("hotel_caught",   "Room Service",       "Chaser catches the runner in Hotel",
                Tier.GOLD,    "\uD83D\uDD14"),
    };

    /** Clamps a 0-255 alpha int so Color never throws. */
    private static int a(float alpha, int max) {
        return Math.max(0, Math.min(max, (int)(max * alpha)));
    }

    // ── Achievement definition ────────────────────────────────────────────────

    public enum Tier { BRONZE, SILVER, GOLD }

    public static class Achievement {
        public final String id, title, description, icon;
        public final Tier   tier;
        Achievement(String id, String title, String desc, Tier tier, String icon) {
            this.id = id; this.title = title; this.description = desc;
            this.tier = tier; this.icon = icon;
        }
    }

    private static Achievement ach(String id, String title, String desc,
                                   Tier tier, String icon) {
        return new Achievement(id, title, desc, tier, icon);
    }

    // ── Active toast ─────────────────────────────────────────────────────────

    private static class ActiveToast {
        final Achievement ach;
        float life;       // counts up to TOAST_DURATION
        int   slot;       // vertical slot index (0 = top)
        ActiveToast(Achievement a, int slot) { ach = a; life = 0f; this.slot = slot; }

        /** 0→1→1→0 alpha over the toast lifetime. */
        float alpha() {
            if (life < TOAST_SLIDE_IN)
                return life / TOAST_SLIDE_IN;
            if (life > TOAST_DURATION - TOAST_SLIDE_OUT)
                return (TOAST_DURATION - life) / TOAST_SLIDE_OUT;
            return 1f;
        }

        /** X offset for slide-in from the right edge. */
        int slideX() {
            if (life < TOAST_SLIDE_IN) {
                float t = life / TOAST_SLIDE_IN;
                float ease = 1f - (1f - t) * (1f - t);   // ease-out quad
                return (int)((1f - ease) * (TOAST_W + 20));
            }
            return 0;
        }
    }

    // ── Public event hooks ────────────────────────────────────────────────────

    /** Call when the runner takes a hit. */
    public void onRunnerHit() {
        totalRunnerHits++;
        consecutiveDodges     = 0;
        consecutiveDodgeTimer = 0f;
        runnerHitThisTick     = true;
        tryUnlock("first_blood");
        if (totalRunnerHits >= 3) tryUnlock("three_strikes");
    }

    /** Call when the chaser fires a projectile. */
    public void onProjectileFired() {
        totalProjectilesFired++;
        if (totalProjectilesFired >= 10) tryUnlock("rapid_fire");
        if (totalProjectilesFired >= 25) tryUnlock("marksman");
    }

    /** Call when an obstacle scrolls off screen (i.e., runner successfully passed it). */
    public void onObstacleAvoided() {
        totalObstaclesAvoided++;
        consecutiveDodges++;
        if (consecutiveDodges >= 5)  tryUnlock("close_call");
        if (consecutiveDodges >= 15) tryUnlock("matrix");
    }

    /** Call when the runner jumps. */
    public void onJump() {
        totalJumps++;
        if (totalJumps >= 20) tryUnlock("high_jumper");
    }

    /** Call when the runner ducks. */
    public void onDuck() {
        totalDucks++;
        if (totalDucks >= 15) tryUnlock("limbo_master");
    }

    /** Call inside endGame(true) — runner won. */
    public void onRunnerWin(float elapsed, GamePanel.Difficulty diff) {
        // Check untouchable (survived with 0 hits)
        if (totalRunnerHits == 0) tryUnlock("untouchable");
        if (diff == GamePanel.Difficulty.HARDCORE) tryUnlock("hardcore_clear");
        if (diff == GamePanel.Difficulty.HOTEL)    tryUnlock("hotel_escape");
    }

    /** Call inside endGame(false) — chaser won. */
    public void onChaserWin(GamePanel.Difficulty diff) {
        if (totalRunnerHits >= 3) tryUnlock("three_strikes");
        if (diff == GamePanel.Difficulty.HOTEL) tryUnlock("hotel_caught");
    }

    /** Call every tick from GamePanel.update() to track time-based achievements. */
    public void tick(float elapsed, GamePanel.Difficulty diff) {
        if (elapsed >= 30f)  tryUnlock("speedrunner");
        if (elapsed >= 90f)  tryUnlock("marathoner");
        // eternal_runner is awarded in onRunnerWin when time runs out naturally
    }

    /** Unlock eternal_runner explicitly when the timer expires. */
    public void onTimerExpired() {
        tryUnlock("eternal_runner");
    }

    // ── Internal unlock logic ─────────────────────────────────────────────────

    private void tryUnlock(String id) {
        if (unlocked.contains(id)) return;
        Achievement a = findById(id);
        if (a == null) return;
        unlocked.add(id);
        queueToast(a);
    }

    private Achievement findById(String id) {
        for (Achievement a : ALL) if (a.id.equals(id)) return a;
        return null;
    }

    private void queueToast(Achievement a) {
        toastQueue.add(a);
    }

    // ── Update (call every tick) ──────────────────────────────────────────────

    public void update(float dt) {
        runnerHitThisTick = false;

        // Advance active toasts
        activeToasts.removeIf(t -> t.life >= TOAST_DURATION);
        for (ActiveToast t : activeToasts) t.life += dt;

        // Re-assign slots compactly
        for (int i = 0; i < activeToasts.size(); i++) activeToasts.get(i).slot = i;

        // Promote from queue if slot available
        if (!toastQueue.isEmpty() && activeToasts.size() < 4) {
            Achievement next = toastQueue.poll();
            activeToasts.add(new ActiveToast(next, activeToasts.size()));
        }
    }

    // ── Draw toasts ───────────────────────────────────────────────────────────

    /**
     * Draw all active achievement toast notifications.
     * Call this inside GamePanel.render(), after the HUD but before greyscale.
     */
    public void drawToasts(Graphics2D g) {
        if (activeToasts.isEmpty()) return;

        Object aaHint = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (ActiveToast toast : activeToasts) {
            drawToast(g, toast);
        }

        if (aaHint != null) g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aaHint);
    }

    private void drawToast(Graphics2D g, ActiveToast toast) {
        float alpha  = toast.alpha();
        int   slideX = toast.slideX();

        int x = W - TOAST_W - 20 + slideX;
        int y = H - (toast.slot + 1) * (TOAST_H + TOAST_MARGIN) - 20;

        // ── Panel shadow ──────────────────────────────────────────────────────
        g.setColor(new Color(0, 0, 0, a(alpha, 100)));
        g.fill(new RoundRectangle2D.Float(x + 4, y + 4, TOAST_W, TOAST_H, 14, 14));

        // ── Tier accent colour ────────────────────────────────────────────────
        Color accent = switch (toast.ach.tier) {
            case BRONZE -> new Color(205, 127,  50);
            case SILVER -> new Color(180, 180, 200);
            case GOLD   -> new Color(255, 200,  40);
        };

        // ── Background panel ──────────────────────────────────────────────────
        g.setColor(new Color(20, 15, 30, a(alpha, 230)));
        g.fill(new RoundRectangle2D.Float(x, y, TOAST_W, TOAST_H, 14, 14));

        // ── Left accent stripe ────────────────────────────────────────────────
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a(alpha, 220)));
        g.fill(new RoundRectangle2D.Float(x, y, 6, TOAST_H, 6, 6));

        // ── Border ────────────────────────────────────────────────────────────
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a(alpha, 90)));
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new RoundRectangle2D.Float(x, y, TOAST_W, TOAST_H, 14, 14));
        g.setStroke(new BasicStroke(1f));

        // ── "ACHIEVEMENT UNLOCKED" label ──────────────────────────────────────
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a(alpha, 200)));
        g.drawString("ACHIEVEMENT UNLOCKED", x + 14, y + 16);

        // ── Icon ──────────────────────────────────────────────────────────────
        g.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 26));
        g.setColor(new Color(255, 255, 255, a(alpha, 220)));
        g.drawString(toast.ach.icon, x + 12, y + TOAST_H - 12);

        // ── Title ─────────────────────────────────────────────────────────────
        g.setFont(new Font("Arial", Font.BOLD, 15));
        g.setColor(new Color(255, 255, 255, a(alpha, 240)));
        g.drawString(toast.ach.title, x + 48, y + TOAST_H - 26);

        // ── Description ───────────────────────────────────────────────────────
        g.setFont(new Font("Arial", Font.PLAIN, 11));
        g.setColor(new Color(180, 170, 200, a(alpha, 190)));
        g.drawString(toast.ach.description, x + 48, y + TOAST_H - 12);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Returns IDs of all achievements unlocked this session. */
    public List<String> getUnlocked() { return new ArrayList<>(unlocked); }

    /** Returns the full catalogue of all achievements. */
    public Achievement[] getAllAchievements() { return ALL; }

    /** Resets per-game counters but retains unlocked list for the session. */
    public void resetForNewGame() {
        totalRunnerHits       = 0;
        totalProjectilesFired = 0;
        totalObstaclesAvoided = 0;
        totalJumps            = 0;
        totalDucks            = 0;
        maxSurvivalTime       = 0f;
        consecutiveDodges     = 0;
        consecutiveDodgeTimer = 0f;
        runnerHitThisTick     = false;
        toastQueue.clear();
        activeToasts.clear();
        // Note: unlocked list is kept intentionally — achievements persist per session.
        // If you want achievements to reset on every new game, add: unlocked.clear();
    }
}
