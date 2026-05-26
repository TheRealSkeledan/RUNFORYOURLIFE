package Panel;

import Engine.Music;
import Engine.Obstacle;
import Engine.Player;

import javax.imageio.ImageIO;
import javax.sound.sampled.Clip;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements ActionListener, KeyListener {

    private static final int   W        = 1280;
    private static final int   H        = 720;
    private static final float GROUND_Y = 580f;
    private static final int   TICK_MS  = 16;
    private static final float DT       = TICK_MS / 1000f;

    private static final int PW     = 180;
    private static final int PH     = 310;
    private static final int DUCK_H = 160;

    private static final float RUNNER_X = W * 0.65f;
    private static final float CHASER_X = W * 0.28f;

    public enum Difficulty { NORMAL, HARDCORE, HOTEL }

    private static final float[] OBS_SPEED  = { 340f, 430f, 270f };
    private static final float[] SPEED_RAMP = {   3f,   5f,  1.5f };
    private static final float[] TIME_LIMIT = { 120f,  90f,  -1f  };
    private static final float[] STUN_DUR   = {  1.1f, 1.8f,  1.1f };
    private static final float[] CHASER_KB  = {  60f,  80f,   50f  };

    private static final Color[] GROUND_FILL = {
            new Color(55,50,70), new Color(38,20,20), new Color(40,22,22)
    };
    private static final Color[] GROUND_LINE = {
            new Color(90,80,110), new Color(70,30,30), new Color(80,35,35)
    };

    public enum State { PLAYING, PAUSED, GAME_OVER }
    private State   state = State.PLAYING;
    private boolean runnerWon;

    private final Difficulty diff;
    private final int        di;

    private float elapsed   = 0f;
    private float timeLeft;
    private float obsSpeed;
    private float bgScrollX = 0f;

    private Player  runner;
    private float   runnerY      = GROUND_Y;
    private float   runnerVY     = 0f;
    private boolean runnerGround = true;
    private boolean runnerDuck   = false;
    private float   runnerStun   = 0f;
    private float   runnerInvince= 0f;
    private int     runnerHits   = 0;
    private float   runnerKnockX = RUNNER_X;

    private Player  chaser;
    private float   chaserY      = GROUND_Y;
    private float   chaserVY     = 0f;
    private boolean chaserGround = true;
    private boolean chaserDuck   = false;
    private float   chaserStun   = 0f;
    private float   chaserX      = CHASER_X;
    private float   chaserThrowCooldown = 0f;

    private static final float GRAVITY    = 1800f;
    private static final float JUMP_FORCE = -720f;
    private static final float WARN_TIME  = 2.0f;

    private record Warning(float y, Obstacle.Type type, float countdown) {}

    private final List<Obstacle>   obstacles   = new ArrayList<>();
    private final List<Warning>    warnings    = new ArrayList<>();
    private float nextSpawnTimer = 1.8f;

    private static class Projectile {
        float x, y;
        static final float SPEED = 680f;
        static final int   W2    = 24;
        static final int   H2    = 24;
        Projectile(float sx, float sy) { x = sx; y = sy; }
        Rectangle bounds() { return new Rectangle((int)x-W2/2,(int)y-H2/2,W2,H2); }
    }
    private final List<Projectile> projectiles  = new ArrayList<>();
    private static final float     THROW_COOLDOWN = 1.5f;

    private boolean keyRunnerJump  = false;
    private boolean keyRunnerDuck  = false;
    private boolean keyChaserJump  = false;
    private boolean keyChaserDuck  = false;
    private boolean keyChaserThrow = false;

    private final Timer gameTimer;

    private final BufferedImage offscreen;
    private final Graphics2D    og;

    private BufferedImage[] bgImages = new BufferedImage[0];
    private float bgOffset = 0f;

    private float hotelPhase   = 0f;
    private float hotelFlicker = 0f;
    private final Random hotelRng = new Random();

    @SuppressWarnings("unused") private Music bgm;
    private final Clip menuMusicClip;  // stopped before game music starts

    private final JFrame frame;
    private final Random rng = new Random();

    // ── Constructor — accepts the live menu Clip so we can stop it cleanly ────
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
        addKeyListener(this);

        offscreen = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        og = offscreen.createGraphics();
        og.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        og.setRenderingHint(RenderingHints.KEY_RENDERING,    RenderingHints.VALUE_RENDER_QUALITY);

        loadBgImages();
        tryLoadMusic();   // stops menu clip, then starts game music
        resetWorld();

        gameTimer = new Timer(TICK_MS, this);
        gameTimer.start();
    }

    /** Backwards-compatible constructor (no menu clip). */
    public GamePanel(JFrame frame, String modeName) throws Exception {
        this(frame, modeName, null);
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W, H); }

    // ── Asset loading ─────────────────────────────────────────────────────────
    private void loadBgImages() {
        File dir = new File("assets/images/backgrounds");
        if (!dir.isDirectory()) return;
        File[] files = dir.listFiles((d,n) -> n.toLowerCase().matches(".*\\.(png|jpg|jpeg)"));
        if (files == null || files.length == 0) return;
        List<BufferedImage> list = new ArrayList<>();
        for (File f : files) { try { list.add(ImageIO.read(f)); } catch (Exception ignored) {} }
        bgImages = list.toArray(new BufferedImage[0]);
    }

    private void tryLoadMusic() {
        // 1. Stop menu music first so there's no overlap
        if (menuMusicClip != null) {
            if (menuMusicClip.isRunning()) menuMusicClip.stop();
            menuMusicClip.close();
        }
        // 2. Start game music using instance method (not static)
        try {
            bgm = new Music("assets/music/NormalChaseTrack");
            bgm.play();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────
    private void resetWorld() {
        elapsed   = 0f;
        timeLeft  = TIME_LIMIT[di];
        obsSpeed  = OBS_SPEED[di];
        bgOffset  = 0f;
        bgScrollX = 0f;

        runnerY      = GROUND_Y; runnerVY = 0f; runnerGround = true;
        runnerDuck   = false;    runnerStun = 0f; runnerInvince = 0f;
        runnerHits   = 0;        runnerKnockX = RUNNER_X;

        chaserX      = CHASER_X; chaserY = GROUND_Y; chaserVY = 0f;
        chaserGround = true;     chaserDuck = false; chaserStun = 0f;
        chaserThrowCooldown = 0f;

        obstacles.clear(); warnings.clear(); projectiles.clear();
        nextSpawnTimer = 1.8f;
        keyRunnerJump = keyRunnerDuck = keyChaserJump = keyChaserDuck = keyChaserThrow = false;
        hotelPhase = 0f; hotelFlicker = 0f;

        runner = new Player("runner", true,  runnerKnockX, runnerY, OBS_SPEED[di]);
        chaser = new Player("chaser", false, chaserX,      chaserY, OBS_SPEED[di]);
        runner.lives = 3;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (state == State.PLAYING) update(DT);
        render();
        repaint();
    }

    private void update(float dt) {
        elapsed += dt;

        if (TIME_LIMIT[di] > 0) {
            timeLeft -= dt;
            if (timeLeft <= 0) { timeLeft = 0; endGame(true); return; }
        }

        obsSpeed = OBS_SPEED[di] + SPEED_RAMP[di] * elapsed;

        runnerStun          = Math.max(0, runnerStun          - dt);
        runnerInvince       = Math.max(0, runnerInvince       - dt);
        chaserStun          = Math.max(0, chaserStun          - dt);
        chaserThrowCooldown = Math.max(0, chaserThrowCooldown - dt);

        if (runnerStun <= 0) {
            if (keyRunnerJump && runnerGround) {
                runnerVY = JUMP_FORCE; runnerGround = false; runnerDuck = false;
            }
            runnerDuck = keyRunnerDuck && runnerGround;
        }
        if (!runnerGround) {
            runnerVY += GRAVITY * dt; runnerY += runnerVY;
            if (runnerY >= GROUND_Y) { runnerY = GROUND_Y; runnerVY = 0; runnerGround = true; }
        }
        if (runnerKnockX < RUNNER_X) runnerKnockX = Math.min(RUNNER_X, runnerKnockX + 120f * dt);

        if (chaserStun <= 0) {
            if (keyChaserJump && chaserGround) {
                chaserVY = JUMP_FORCE; chaserGround = false; chaserDuck = false;
            }
            chaserDuck = keyChaserDuck && chaserGround;
            if (keyChaserThrow && chaserThrowCooldown <= 0) {
                float projY = chaserY - PH * 0.6f;
                projectiles.add(new Projectile(chaserX + PW / 2f, projY));
                chaserThrowCooldown = THROW_COOLDOWN;
                keyChaserThrow = false;
            }
        }
        if (!chaserGround) {
            chaserVY += GRAVITY * dt; chaserY += chaserVY;
            if (chaserY >= GROUND_Y) { chaserY = GROUND_Y; chaserVY = 0; chaserGround = true; }
        }
        if (chaserX < CHASER_X) chaserX = Math.min(CHASER_X, chaserX + 80f * dt);
        if (chaserX + PW / 2f < 0) { endGame(true); return; }

        bgScrollX += obsSpeed * 0.35f * dt;
        bgOffset  -= obsSpeed * 0.35f * dt;

        nextSpawnTimer -= dt;
        if (nextSpawnTimer <= 0) {
            Obstacle.Type type = rng.nextBoolean() ? Obstacle.Type.GROUND : Obstacle.Type.AERIAL;
            float y = (type == Obstacle.Type.GROUND)
                    ? GROUND_Y - 90
                    : 120;

            warnings.add(new Warning(y, type, WARN_TIME));
            nextSpawnTimer = 1.4f + rng.nextFloat() * 1.2f;
        }

        List<Warning> keep = new ArrayList<>();

        for (Warning w : warnings) {

            float nc = w.countdown - dt;

            if (nc <= 0) {

                Obstacle obs = makeObstacle(W + 180, w.type);

                if (w.type == Obstacle.Type.AERIAL) {
                    obs.y = 260;
                }

                obstacles.add(obs);

            } else {

                keep.add(new Warning(w.y, w.type, nc));
            }
        }

        warnings.clear();
        warnings.addAll(keep);

        Iterator<Obstacle> it = obstacles.iterator();
        while (it.hasNext()) {
            Obstacle obs = it.next();
            obs.x -= obsSpeed * dt;
            if (obs.x + obs.width < -80) { it.remove(); continue; }
            handleObstacleCollisions(obs);
        }

        Iterator<Projectile> pit = projectiles.iterator();
        while (pit.hasNext()) {
            Projectile p = pit.next();
            p.x += Projectile.SPEED * dt;
            if (p.x > W + 60) { pit.remove(); continue; }
            if (runnerInvince <= 0 && p.bounds().intersects(runnerHitBox())) {
                pit.remove();
                if (state == State.GAME_OVER) return;
            }
        }

        if (diff == Difficulty.HOTEL) {
            hotelPhase += dt * 0.4f;
            if (hotelRng.nextFloat() < 0.008f)
                hotelFlicker = 0.08f + hotelRng.nextFloat() * 0.16f;
            hotelFlicker = Math.max(0, hotelFlicker - 0.012f);
        }
    }

    private Obstacle makeObstacle(float x, Obstacle.Type type) {
        Obstacle obs = new Obstacle(x, GROUND_Y, type); obs.x = x; return obs;
    }

    private Rectangle runnerHitBox() {
        int rh = runnerDuck ? DUCK_H : PH, rw = PW - 30;
        return new Rectangle((int)runnerKnockX - rw/2, (int)runnerY - rh, rw, rh);
    }
    private Rectangle chaserHitBox() {
        int ch = chaserDuck ? DUCK_H : PH, cw = PW - 30;
        return new Rectangle((int)chaserX - cw/2, (int)chaserY - ch, cw, ch);
    }

    private void handleObstacleCollisions(Obstacle obs) {
        Rectangle obsR = new Rectangle((int)obs.x, (int)(obs.y-obs.height), obs.width, obs.height);
        if (runnerInvince <= 0 && runnerHitBox().intersects(obsR)) {
            boolean safe = (obs.type==Obstacle.Type.GROUND && runnerDuck)
                    || (obs.type==Obstacle.Type.AERIAL && !runnerGround);
            if (!safe) { if (state==State.GAME_OVER) return; }
        }
        if (chaserStun <= 0 && chaserHitBox().intersects(obsR)) {
            boolean safe = (obs.type==Obstacle.Type.GROUND && chaserDuck)
                    || (obs.type==Obstacle.Type.AERIAL && !chaserGround);
            if (!safe) { chaserStun = STUN_DUR[di]; chaserX -= CHASER_KB[di]; chaser.applyStun(STUN_DUR[di]); }
        }
    }

    private void hitRunner() {
        runnerHits++;
        runner.lives  = 3 - runnerHits;
        runnerStun    = STUN_DUR[di];
        runnerInvince = STUN_DUR[di] + 1.0f;
        runnerKnockX  = Math.max(chaserX + PW/2f + 20f, runnerKnockX - 90f);
        runner.applyStun(STUN_DUR[di]);
        if (runnerHits >= 3) endGame(false);
    }

    private void endGame(boolean runnerWins) {
        runnerWon = runnerWins;
        state = State.GAME_OVER;
        gameTimer.stop();
    }

    // ── Render ────────────────────────────────────────────────────────────────
    private void render() {
        og.setColor(Color.BLACK); og.fillRect(0,0,W,H);
        drawBackground(); drawWarnings();
        for (Obstacle obs : obstacles) obs.draw(og, 0f);
        drawProjectiles(); drawPlayers();
        if (diff==Difficulty.HOTEL) drawHotelAtmosphere();
        drawHUD();
        if (state==State.PAUSED)    drawPauseOverlay();
        if (state==State.GAME_OVER) drawGameOverOverlay();
    }

    private void drawBackground() {
        int skyH = (int)GROUND_Y;
        if (bgImages.length > 0) {
            BufferedImage img = bgImages[0];
            int drawH = skyH;
            int drawW = img.getWidth() * drawH / img.getHeight();
            float off = bgOffset % drawW;
            if (off > 0) off -= drawW;
            for (float tx = off; tx < W; tx += drawW)
                og.drawImage(img, (int)tx, 0, drawW, drawH, null);
        } else {
            Color top = diff==Difficulty.HARDCORE ? new Color(18,10,10)
                    : diff==Difficulty.HOTEL    ? new Color(22,8,8)
                    :                             new Color(28,24,50);
            Color bot = diff==Difficulty.HARDCORE ? new Color(35,12,12)
                    : diff==Difficulty.HOTEL    ? new Color(45,15,10)
                    :                             new Color(55,40,80);
            og.setPaint(new GradientPaint(0,0,top,0,skyH,bot));
            og.fillRect(0,0,W,skyH);
        }
        if (diff==Difficulty.HARDCORE){og.setColor(new Color(60,0,0,55)); og.fillRect(0,0,W,(int)GROUND_Y);}
        if (diff==Difficulty.HOTEL)   {og.setColor(new Color(80,0,0,70)); og.fillRect(0,0,W,(int)GROUND_Y);}
    }

    private void drawWarnings() {

        for (Warning w : warnings) {

            float pulse = 0.5f + 0.5f *
                    (float)Math.sin(w.countdown * 8f);

            int alpha = 140 + (int)(pulse * 115);

            int size = 70;

            int x = W - 120;
            int y = (int)w.y;

            if (w.type == Obstacle.Type.GROUND) {

                og.setColor(new Color(255, 220, 40, alpha));

                int[] xs = {x, x - size/2, x + size/2};
                int[] ys = {y + size/2, y - size/2, y - size/2};

                og.fillPolygon(xs, ys, 3);

                og.setColor(Color.BLACK);
                og.setFont(new Font("Arial", Font.BOLD, 32));
                og.drawString("!", x - 5, y + 12);

            } else {

                og.setColor(new Color(80, 200, 255, alpha));

                int[] xs = {x, x - size/2, x + size/2};
                int[] ys = {y - size/2, y + size/2, y + size/2};

                og.fillPolygon(xs, ys, 3);

                og.setColor(Color.BLACK);
                og.setFont(new Font("Arial", Font.BOLD, 32));
                og.drawString("!", x - 5, y + 12);
            }
        }
    }

    private void drawArrow(int x1,int y1,int x2,int y2,int head) {
        og.setStroke(new BasicStroke(3.5f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        og.drawLine(x1,y1,x2,y2);
        double a=Math.atan2(y2-y1,x2-x1);
        og.drawLine(x2,y2,(int)(x2-head*Math.cos(a-Math.PI/6)),(int)(y2-head*Math.sin(a-Math.PI/6)));
        og.drawLine(x2,y2,(int)(x2-head*Math.cos(a+Math.PI/6)),(int)(y2-head*Math.sin(a+Math.PI/6)));
        og.setStroke(new BasicStroke(1f));
    }

    private void drawProjectiles() {
        for (Projectile p : projectiles) {
            int r=Projectile.W2;
            og.setColor(new Color(255,100,30,80));  og.fillOval((int)p.x-r-6,(int)p.y-r-6,(r+6)*2,(r+6)*2);
            og.setColor(new Color(255,160,60));      og.fillOval((int)p.x-r,(int)p.y-r,r*2,r*2);
            og.setColor(Color.WHITE);                og.fillOval((int)p.x-r/2,(int)p.y-r/2,r,r);
        }
    }

    private void drawPlayers() {
        chaser.x=chaserX; chaser.y=chaserY; chaser.onGround=chaserGround; chaser.isDucking=chaserDuck;
        if(chaserStun>0) chaser.applyStun(DT);
        chaser.cacheDt(DT); chaser.update(DT,GROUND_Y,true);
        drawPlayerScaled(chaser,(int)chaserX,(int)chaserY,chaserDuck,false,chaserStun>0);

        runner.x=runnerKnockX; runner.y=runnerY; runner.onGround=runnerGround; runner.isDucking=runnerDuck;
        runner.lives=3-runnerHits;
        if(runnerStun>0) runner.applyStun(DT);
        runner.hitCooldown=runnerInvince;
        runner.cacheDt(DT); runner.update(DT,GROUND_Y,true);
        boolean flash=runnerInvince>0&&runnerStun<=0&&(int)(runnerInvince*10)%2==0;
        if(!flash) drawPlayerScaled(runner,(int)runnerKnockX,(int)runnerY,runnerDuck,true,runnerStun>0);
    }

    private void drawPlayerScaled(Player p, int cx, int groundY, boolean ducking,
                                  boolean isRunner, boolean stunned) {

        int drawW;
        int drawH;

        if (isRunner) {
            drawW = ducking ? 260 : 300;
            drawH = ducking ? 170 : 340;
        } else {
            drawW = ducking ? 300 : 360;
            drawH = ducking ? 180 : 360;
        }

        int drawX = cx - drawW / 2;
        int drawY = groundY - drawH;

        p.draw(og, drawX, drawY, drawW, drawH);

        og.setColor(Color.WHITE);
    }

    private void drawHotelAtmosphere() {
        float pulse=0.5f+0.5f*(float)Math.sin(hotelPhase);
        og.setPaint(new RadialGradientPaint(W/2f,H/2f,W*0.65f,new float[]{0.3f,1.0f},
                new Color[]{new Color(0,0,0,0),new Color(80,0,0,(int)(80+pulse*60))}));
        og.fillRect(0,0,W,H);
        if(hotelFlicker>0){ og.setColor(new Color(0,0,0,(int)(hotelFlicker*255))); og.fillRect(0,0,W,H); }
    }

    private void drawHUD() {
        int hs=38,hpad=8,startX=W-3*(hs+hpad)-20;
        for(int i=0;i<3;i++) drawHeart(startX+i*(hs+hpad),16,hs,i>=runnerHits);
        og.setFont(new Font("Arial",Font.BOLD,13)); og.setColor(new Color(200,200,200,180));
        og.drawString("RUNNER",startX,70);

        int barX=20,barY=60,barW=130,barH=12;
        og.setColor(new Color(60,60,60,200)); og.fillRoundRect(barX,barY,barW,barH,6,6);
        float tr=1f-Math.min(1f,chaserThrowCooldown/THROW_COOLDOWN);
        og.setColor(tr>=1f?new Color(80,220,255):new Color(40,130,160));
        og.fillRoundRect(barX,barY,(int)(barW*tr),barH,6,6);
        og.setFont(new Font("Arial",Font.BOLD,13)); og.setColor(new Color(200,200,200,180));
        og.drawString(tr>=1f?"THROW READY [SPC]":"THROW COOLDOWN",barX,barY-4);
        og.drawString("CHASER",barX,barY+barH+14);

        if(TIME_LIMIT[di]>0){
            String ts=String.format("%d:%02d",(int)(timeLeft/60),(int)(timeLeft%60));
            Color tc=timeLeft<=20?new Color(220,50+(int)(timeLeft/20f*150),50):Color.WHITE;
            og.setFont(new Font("Arial",Font.BOLD,30)); og.setColor(tc);
            FontMetrics fm=og.getFontMetrics(); og.drawString(ts,(W-fm.stringWidth(ts))/2,42);
        } else {
            og.setFont(new Font("Arial",Font.PLAIN,20)); og.setColor(new Color(220,180,180));
            String ts=String.format("%ds",(int)elapsed);
            FontMetrics fm=og.getFontMetrics(); og.drawString(ts,(W-fm.stringWidth(ts))/2,40);
        }
        og.setFont(new Font("Arial",Font.ITALIC,13)); og.setColor(new Color(200,200,200,120));
        String ml=diff.name(); FontMetrics fm2=og.getFontMetrics();
        og.drawString(ml,(W-fm2.stringWidth(ml))/2,60);
        if(runnerStun>0){og.setFont(new Font("Arial",Font.BOLD,18));og.setColor(new Color(255,220,40));og.drawString("RUNNER STUNNED",W-230,60);}
        if(chaserStun>0){og.setFont(new Font("Arial",Font.BOLD,18));og.setColor(new Color(255,160,40));og.drawString("CHASER STUNNED",20,98);}
        if(elapsed<5f){
            float alpha=elapsed<4f?1f:(5f-elapsed); int a=(int)(alpha*200);
            og.setFont(new Font("Arial",Font.PLAIN,15)); og.setColor(new Color(200,200,200,a));
            og.drawString("CHASER: W=Jump  S=Duck  Space=Throw",20,H-40);
            og.drawString("RUNNER: ↑=Jump  ↓=Duck",W-260,H-40);
        }
    }

    private void drawHeart(int x,int y,int size,boolean full){
        int half=size/2;
        og.setColor(full?new Color(220,40,60):new Color(55,55,65));
        og.fillOval(x,y,half+2,half+2); og.fillOval(x+half-2,y,half+2,half+2);
        og.fillPolygon(new int[]{x,x+size/2,x+size},new int[]{y+half/2,y+size,y+half/2},3);
        og.setColor(new Color(0,0,0,120)); og.setStroke(new BasicStroke(1.5f));
        og.drawOval(x,y,half+2,half+2); og.drawOval(x+half-2,y,half+2,half+2);
        og.drawPolygon(new int[]{x,x+size/2,x+size},new int[]{y+half/2,y+size,y+half/2},3);
        og.setStroke(new BasicStroke(1f));
    }

    private void drawPauseOverlay(){
        og.setColor(new Color(0,0,0,160)); og.fillRect(0,0,W,H);
        og.setFont(new Font("Arial",Font.BOLD,64)); centred("PAUSED",H/2-30,Color.WHITE);
        og.setFont(new Font("Arial",Font.PLAIN,24)); centred("Press P to resume",H/2+40,Color.LIGHT_GRAY);
    }

    private void drawGameOverOverlay(){
        og.setColor(new Color(10,5,5,215)); og.fillRect(0,0,W,H);
        og.setFont(new Font("Arial",Font.BOLD,72));
        if(runnerWon){
            centred("RUNNER ESCAPED!",H/2-80,new Color(80,220,140));
            og.setFont(new Font("Arial",Font.PLAIN,26));
            String reason=TIME_LIMIT[di]>0?"Timer ran out":"Survived long enough";
            centred(reason+"  —  "+String.format("%ds",(int)elapsed),H/2,Color.LIGHT_GRAY);
        } else {
            centred(diff==Difficulty.HOTEL?"I FOUND YOU.":"RUNNER CAUGHT!",H/2-80,
                    diff==Difficulty.HOTEL?new Color(200,40,40):new Color(220,60,60));
            og.setFont(new Font("Arial",Font.PLAIN,26));
            centred(String.format("Chaser landed 3 hits  —  %ds",(int)elapsed),H/2,Color.LIGHT_GRAY);
        }
        og.setFont(new Font("Arial",Font.PLAIN,20));
        centred("ENTER — play again     ESC — menu",H/2+60,new Color(160,130,130));
    }

    private void centred(String s,int y,Color c){
        og.setColor(c); FontMetrics fm=og.getFontMetrics();
        og.drawString(s,(W-fm.stringWidth(s))/2,y);
    }

    @Override
    protected void paintComponent(Graphics g){
        super.paintComponent(g);
        int pw=getWidth(),ph=getHeight();
        float scale=Math.min((float)pw/W,(float)ph/H);
        int dw=Math.round(W*scale),dh=Math.round(H*scale);
        g.drawImage(offscreen,(pw-dw)/2,(ph-dh)/2,dw,dh,null);
    }

    @Override
    public void keyPressed(KeyEvent e){
        int k=e.getKeyCode();
        if(state==State.GAME_OVER){
            if(k==KeyEvent.VK_ENTER||k==KeyEvent.VK_SPACE){resetWorld();state=State.PLAYING;gameTimer.start();}
            else if(k==KeyEvent.VK_ESCAPE) Main.Main.goToMenu(frame);
            return;
        }
        if(k==KeyEvent.VK_P){
            if(state==State.PLAYING){state=State.PAUSED;gameTimer.stop();}
            else if(state==State.PAUSED){state=State.PLAYING;gameTimer.start();}
            return;
        }
        if(state==State.PAUSED) return;
        if(k==KeyEvent.VK_UP)    keyRunnerJump=true;
        if(k==KeyEvent.VK_DOWN)  keyRunnerDuck=true;
        if(k==KeyEvent.VK_W)     keyChaserJump=true;
        if(k==KeyEvent.VK_S)     keyChaserDuck=true;
        if(k==KeyEvent.VK_SPACE) keyChaserThrow=true;
    }

    @Override
    public void keyReleased(KeyEvent e){
        int k=e.getKeyCode();
        if(k==KeyEvent.VK_UP)   keyRunnerJump=false;
        if(k==KeyEvent.VK_DOWN){keyRunnerDuck=false; runnerDuck=false;}
        if(k==KeyEvent.VK_W)   keyChaserJump=false;
        if(k==KeyEvent.VK_S)  {keyChaserDuck=false; chaserDuck=false;}
    }

    @Override public void keyTyped(KeyEvent e){}
}