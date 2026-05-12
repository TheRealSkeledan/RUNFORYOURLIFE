package Engine;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.*;

public class SoundEffect {
    private Clip clip;
    private AudioInputStream audioInputStream;
    private String soundFilePath;
    private static final float VOLUME_LEVEL = -10.0f;

    public SoundEffect(String soundFilePath) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        this.soundFilePath = soundFilePath;
        loadSound();
    }

    private void loadSound() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        audioInputStream = AudioSystem.getAudioInputStream(new File(soundFilePath).getAbsoluteFile());
        clip = AudioSystem.getClip();
        clip.open(audioInputStream);

        setVolume(VOLUME_LEVEL);
    }

    public void play() {
        if (clip != null) {
            clip.start();
        }
    }

    public void stop() {
        if (clip != null && clip.isRunning()) {
            clip.stop();
            clip.close();
        }
    }

    private void setVolume(float volume) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl volumeControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            volumeControl.setValue(volume);
        }
    }

    public boolean isPlaying() {
        return clip.isRunning();
    }

    public void release() {
        if (clip != null) {
            clip.close();
        }
    }
}