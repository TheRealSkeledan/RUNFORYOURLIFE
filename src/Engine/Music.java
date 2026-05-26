package Engine;

import java.io.File;
import java.io.IOException;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Music {
    private Clip clip;
    private String SP;
    private AudioInputStream audioInputStream;

	public Music(String SP) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		this.SP = SP;
		audioInputStream = AudioSystem.getAudioInputStream(new File(SP + ".wav").getAbsoluteFile());
		clip = AudioSystem.getClip();
	}

    public void play() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        clip.close();
        audioInputStream = AudioSystem.getAudioInputStream(new File(SP + ".wav").getAbsoluteFile());

        clip.open(audioInputStream);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        clip.start();
    }

	public void playSFX() throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		clip.open(audioInputStream);

		clip.start();
	}

    public void stop() {
        clip.stop();
        clip.close();
    }
}