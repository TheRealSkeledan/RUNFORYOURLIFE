package Panel;

import java.awt.*;

/**
 * HudRenderer — draws all 2-D overlay elements:
 *   hearts, throw bar, timer, mode label, controls hint,
 *   pause overlay, game-over overlay.
 *
 * Pass in the offscreen Graphics2D each frame; this class is stateless.
 */
public class HudRenderer {

    private static final int W = 1280;
    private static final int H = 720;

    // ── Public entry points ───────────────────────────────────────────────────

    public void drawHUD(Graphics2D g, int runnerHits, float chaserThrowCooldown,
                        float throwCooldownMax, float timeLeft, float timeLimitTotal,
                        float elapsed, GamePanel.Difficulty diff,
                        float runnerDodgeCooldown, float dodgeCooldownMax) {

        // Hearts (top-right)
        int hs = 38, hpad = 8;
        int startX = W - 3 * (hs + hpad) - 20;
        for (int i = 0; i < 3; i++)
            drawHeart(g, startX + i * (hs + hpad), 16, hs, i >= runnerHits);

        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(200, 200, 200, 180));
        g.drawString("RUNNER", startX, 70);

        // Throw cooldown bar (top-left)
        int barX = 20, barY = 60, barW = 130, barH = 12;
        g.setColor(new Color(60, 60, 60, 200));
        g.fillRoundRect(barX, barY, barW, barH, 6, 6);
        float tr = 1f - Math.min(1f, chaserThrowCooldown / throwCooldownMax);
        g.setColor(tr >= 1f ? new Color(80, 220, 255) : new Color(40, 130, 160));
        g.fillRoundRect(barX, barY, (int)(barW * tr), barH, 6, 6);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(200, 200, 200, 180));
        g.drawString(tr >= 1f ? "THROW READY [SPC]" : "THROW COOLDOWN", barX, barY - 4);
        g.drawString("CHASER", barX, barY + barH + 14);

        // Timer / elapsed (top-centre)
        if (timeLimitTotal > 0) {
            String ts = String.format("%d:%02d", (int)(timeLeft / 60), (int)(timeLeft % 60));
            Color tc = timeLeft <= 20
                    ? new Color(220, 50 + (int)(timeLeft / 20f * 150), 50)
                    : Color.WHITE;
            g.setFont(new Font("Arial", Font.BOLD, 30));
            g.setColor(tc);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ts, (W - fm.stringWidth(ts)) / 2, 42);
        } else {
            g.setFont(new Font("Arial", Font.PLAIN, 20));
            g.setColor(new Color(220, 180, 180));
            String ts = String.format("%ds", (int)elapsed);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(ts, (W - fm.stringWidth(ts)) / 2, 40);
        }

        // Mode label
        g.setFont(new Font("Arial", Font.ITALIC, 13));
        g.setColor(new Color(200, 200, 200, 120));
        String ml = diff.name();
        FontMetrics fm2 = g.getFontMetrics();
        g.drawString(ml, (W - fm2.stringWidth(ml)) / 2, 60);

        // Dodge cooldown bar (bottom-right)
        int dbarX = W - 220, dbarY = H - 60, dbarW = 180, dbarH = 12;
        g.setColor(new Color(60, 60, 60, 200));
        g.fillRoundRect(dbarX, dbarY, dbarW, dbarH, 6, 6);
        float dr = 1f - Math.min(1f, runnerDodgeCooldown / dodgeCooldownMax);
        g.setColor(dr >= 1f ? new Color(80, 255, 180) : new Color(40, 140, 100));
        g.fillRoundRect(dbarX, dbarY, (int)(dbarW * dr), dbarH, 6, 6);
        g.setFont(new Font("Arial", Font.BOLD, 13));
        g.setColor(new Color(200, 200, 200, 180));
        g.drawString(dr >= 1f ? "DODGE READY [→]" : "DODGE COOLDOWN", dbarX, dbarY - 4);
        g.drawString("RUNNER", dbarX, dbarY + dbarH + 14);


        // Controls hint (first 5 s)
        if (elapsed < 5f) {
            float alpha = elapsed < 4f ? 1f : (5f - elapsed);
            int a = (int)(alpha * 200);
            g.setFont(new Font("Arial", Font.PLAIN, 15));
            g.setColor(new Color(200, 200, 200, a));
            g.drawString("CHASER: W=Jump  S=Duck  Space=Throw", 20, H - 40);
            g.drawString("RUNNER: ↑=Jump  ↓=Duck", W - 260, H - 40);
        }
    }

    public void drawPauseOverlay(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(0, 0, W, H);
        g.setFont(new Font("Arial", Font.BOLD, 64));
        centred(g, "PAUSED", H / 2 - 30, Color.WHITE);
        g.setFont(new Font("Arial", Font.PLAIN, 24));
        centred(g, "Press P to resume", H / 2 + 40, Color.LIGHT_GRAY);
    }

    public void drawGameOverOverlay(Graphics2D g, boolean runnerWon,
                                    GamePanel.Difficulty diff,
                                    float elapsed, float timeLeft) {
        g.setColor(new Color(10, 5, 5, 215));
        g.fillRect(0, 0, W, H);
        g.setFont(new Font("Arial", Font.BOLD, 72));

        if (runnerWon) {
            centred(g, "RUNNER ESCAPED!", H / 2 - 80, new Color(80, 220, 140));
            g.setFont(new Font("Arial", Font.PLAIN, 26));
            String reason = timeLeft >= 0 ? "Timer ran out" : "Survived long enough";
            centred(g, reason + "  —  " + String.format("%ds", (int)elapsed),
                    H / 2, Color.LIGHT_GRAY);
        } else {
            String title = diff == GamePanel.Difficulty.HOTEL ? "I FOUND YOU." : "RUNNER CAUGHT!";
            Color  tc    = diff == GamePanel.Difficulty.HOTEL ? new Color(200, 40, 40) : new Color(220, 60, 60);
            centred(g, title, H / 2 - 80, tc);
            g.setFont(new Font("Arial", Font.PLAIN, 26));
            centred(g, String.format("Chaser landed 3 hits  —  %ds", (int)elapsed),
                    H / 2, Color.LIGHT_GRAY);
        }

        g.setFont(new Font("Arial", Font.PLAIN, 20));
        centred(g, "ENTER — play again     ESC — menu", H / 2 + 60, new Color(160, 130, 130));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawHeart(Graphics2D g, int x, int y, int size, boolean full) {
        int half = size / 2;
        g.setColor(full ? new Color(220, 40, 60) : new Color(55, 55, 65));
        g.fillOval(x, y, half + 2, half + 2);
        g.fillOval(x + half - 2, y, half + 2, half + 2);
        g.fillPolygon(
                new int[]{ x, x + size / 2, x + size },
                new int[]{ y + half / 2, y + size, y + half / 2 },
                3);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(1.5f));
        g.drawOval(x, y, half + 2, half + 2);
        g.drawOval(x + half - 2, y, half + 2, half + 2);
        g.drawPolygon(
                new int[]{ x, x + size / 2, x + size },
                new int[]{ y + half / 2, y + size, y + half / 2 },
                3);
        g.setStroke(new BasicStroke(1f));
    }

    private void centred(Graphics2D g, String s, int y, Color c) {
        g.setColor(c);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, (W - fm.stringWidth(s)) / 2, y);
    }
}