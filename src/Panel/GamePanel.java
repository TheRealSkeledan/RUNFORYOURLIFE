// GamePanel.java

package Panel;

import Abstract.Character;
import Engine.Map;
import Engine.UI;

import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.concurrent.*;
import javax.swing.*;

public class GamePanel extends JPanel {
    private final Character p1;
    private final boolean[] keys = new boolean[16];
    private boolean p1isFacingRight = true;

    private long lastTime = System.nanoTime();
    private int fps = 0;
    private int frameCount = 0;
    private final ScheduledExecutorService executorService;

    private static final int SCREEN_WIDTH = 1280;
    private static final int SCREEN_HEIGHT = 720;
    private static final int TARGET_FPS = 300;
    private static final long OPTIMAL_TIME = 1000000000 / TARGET_FPS;

    private int renderWidth = SCREEN_WIDTH;
    private int renderHeight = SCREEN_HEIGHT;
    private int renderXOffset = 0;
    private int renderYOffset = 0;

    public GamePanel(Character playerCharacter) throws IOException {
        this.p1 = playerCharacter;

        Map.setName("polus");
        UI.create();

        setFocusable(true);
        requestFocusInWindow();

        addKeyListener(new Keyboard());
        setDoubleBuffered(true);

        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::gameLoop, 0, OPTIMAL_TIME, TimeUnit.NANOSECONDS);

        this.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                adjustViewport();
            }
        });
    }

    private void adjustViewport() {
        Dimension size = getSize();
        double aspectRatio = (double) SCREEN_WIDTH / SCREEN_HEIGHT;
        double windowRatio = (double) size.width / size.height;

        if (windowRatio > aspectRatio) {
            renderHeight = size.height;
            renderWidth = (int) (size.height * aspectRatio);
            renderXOffset = (size.width - renderWidth) / 2;
            renderYOffset = 0;
        } else {
            renderWidth = size.width;
            renderHeight = (int) (size.width / aspectRatio);
            renderXOffset = 0;
            renderYOffset = (size.height - renderHeight) / 2;
        }
    }

    private void gameLoop() {
        try {
            moveP1();
            p1.updatePosition();
            p1.updateAnimationFrame();
            p1.updateProjectiles();
            repaint();

            long now = System.nanoTime();
            frameCount++;
            if (now - lastTime >= 1000000000) {
                fps = frameCount;
                frameCount = 0;
                lastTime = now;
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.BLACK);
        g.fillRect(0, 0, getWidth(), getHeight());

        Graphics2D g2d = (Graphics2D) g.create();

        g2d.translate(renderXOffset, renderYOffset);
        g2d.scale((double) renderWidth / SCREEN_WIDTH, (double) renderHeight / SCREEN_HEIGHT);

        Map.drawBackStage(g2d, 0, 0);

        if (p1isFacingRight) {
            g2d.drawImage(p1.getCurrentFrame(), p1.getX(), p1.getY(), this);
        } else {
            g2d.drawImage(p1.getCurrentFrame(), p1.getX() + p1.getWidth(), p1.getY(),
                          -p1.getWidth(), p1.getHeight(), this);
        }

        Map.drawFrontStage(g2d, 0, 0);
        UI.drawUI(p1.getHP(), p1.getKP(), true, g2d, p1.name);

        g2d.setColor(Color.RED);
        g2d.drawString("FPS: " + fps, 10, 10);

        g2d.dispose();
        p1.drawProjectiles(g);
        g.dispose();
    }

    private class Keyboard implements KeyListener {
        @Override
        public void keyTyped(KeyEvent e) {}
        @Override
        public void keyPressed(KeyEvent e) {
            switch (e.getKeyChar()) {
                case 'w' -> keys[0] = true;
                case 'a' -> { keys[1] = true; p1isFacingRight = false; }
                case 'e' -> keys[2] = true;
                case 'd' -> { keys[3] = true; p1isFacingRight = true; }
                case 'f' -> keys[4] = true;
                case 'c' -> keys[5] = true;
                case 'q' -> keys[6] = true;
                case 'r' -> keys[7] = true;
            }
        }
        @Override
        public void keyReleased(KeyEvent e) {
            switch (e.getKeyChar()) {
                case 'w' -> keys[0] = false;
                case 'a' -> keys[1] = false;
                case 'e' -> keys[2] = false;
                case 'd' -> keys[3] = false;
                case 'f' -> keys[4] = false;
                case 'c' -> keys[5] = false;
                case 'q' -> keys[6] = false;
                case 'r' -> keys[7] = false;
            }
        }
    }

    private void moveP1() throws IOException {
        if (keys[0]) p1.jump();
        if (!keys[2]) {
            if (keys[1]) p1.move(-p1.speed);
            if (keys[3]) p1.move(p1.speed);
        }
        if (keys[2]) p1.defend();
        if (keys[4]) p1.light();
        if (keys[5]) p1.heavy();
        if (keys[6]) p1.taunt();
        if (keys[7]) p1.finish();
        resetAnimP1();
    }

    private void resetAnimP1() {
        if (!keys[1] && !keys[2] && !keys[3] && !keys[4] && !keys[5] && !keys[6] && !keys[7] && !p1.jumping && !p1.isLocked) {
            p1.setAction("idle");
        }
    }
}