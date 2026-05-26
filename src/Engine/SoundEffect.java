package Engine;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;

public class SoundEffect {
    private Clip clip;
    private static final float VOLUME_LEVEL = -10.0f;

    public SoundEffect(String soundFilePath)
            throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        AudioInputStream ais = AudioSystem.getAudioInputStream(
                new File(soundFilePath).getAbsoluteFile());
        clip = AudioSystem.getClip();
        clip.open(ais);
        setVolume(VOLUME_LEVEL);
    }

    /**
     * Rewinds to the start then plays.
     * Safe to call repeatedly — each call restarts the clip from frame 0.
     */
    public void play() {
        if (clip == null) return;
        if (clip.isRunning()) clip.stop();
        clip.setFramePosition(0);
        clip.start();
    }

    public void stop() {
        if (clip != null && clip.isRunning()) clip.stop();
    }

    private void setVolume(float volume) {
        if (clip != null && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            ((FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN)).setValue(volume);
        }
    }

    public boolean isPlaying() { return clip != null && clip.isRunning(); }

    public void release() { if (clip != null) clip.close(); }
}