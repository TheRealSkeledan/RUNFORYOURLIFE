package Panel;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * InputHandler — owns all raw key state for both players.
 * GamePanel registers this as the KeyListener, then reads the public fields.
 */
public class InputHandler implements KeyListener {

    // ── Runner (arrow keys) ───────────────────────────────────────────────────
    public boolean runnerJump  = false;
    public boolean runnerDuck  = false;
    public boolean runnerDodge = false;


    // ── Chaser (WASD + Space) ─────────────────────────────────────────────────
    public boolean chaserJump  = false;
    public boolean chaserDuck  = false;
    /** Consumed by GamePanel after processing so a single press fires one throw. */
    public boolean chaserThrow = false;

    // ── Meta ──────────────────────────────────────────────────────────────────
    public boolean pause       = false;
    public boolean confirm     = false;   // ENTER or SPACE on game-over screen
    public boolean menu        = false;   // ESC on game-over screen

    /** Call once per tick after reading all flags to clear single-frame actions. */
    public void consumeSingleFrameActions() {
        chaserThrow = false;
        pause       = false;
        confirm     = false;
        menu        = false;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP     -> runnerJump  = true;
            case KeyEvent.VK_DOWN   -> runnerDuck  = true;
            case KeyEvent.VK_W      -> chaserJump  = true;
            case KeyEvent.VK_S      -> chaserDuck  = true;
            case KeyEvent.VK_SPACE  -> chaserThrow = true;
            case KeyEvent.VK_P      -> pause       = true;
            case KeyEvent.VK_ENTER  -> confirm     = true;
            case KeyEvent.VK_ESCAPE -> menu        = true;
            case KeyEvent.VK_RIGHT  -> runnerDodge  = true;

        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_UP    -> runnerJump = false;
            case KeyEvent.VK_DOWN  -> runnerDuck = false;
            case KeyEvent.VK_RIGHT  -> runnerDodge  = false;
            case KeyEvent.VK_W     -> chaserJump = false;
            case KeyEvent.VK_S     -> chaserDuck = false;
        }
    }

    @Override public void keyTyped(KeyEvent e) {}
}