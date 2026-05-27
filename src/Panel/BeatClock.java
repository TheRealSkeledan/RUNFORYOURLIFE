package Panel;

public class BeatClock {
    private final float bpm;
    private final float beatDuration;
    private final float barDuration;

    private float totalTime   = 0f;
    public float beatPhase    = 0f;
    public float barPhase     = 0f;
    public float phrasePhase  = 0f;
    public int   beatCount    = 0;
    public int   barCount     = 0;
    public boolean onBeat     = false;
    public boolean onBar      = false;
    public boolean onPhrase   = false;
    public boolean onSection  = false;

    public BeatClock(float bpm) {
        this.bpm          = bpm;
        this.beatDuration = 60f / bpm;
        this.barDuration  = beatDuration * 4f;
    }

    public void update(float dt) {
        float prevTotal = totalTime;
        totalTime += dt;

        beatPhase   = (totalTime % beatDuration)  / beatDuration;
        barPhase    = (totalTime % barDuration)   / barDuration;
        phrasePhase = (totalTime % (barDuration * 2f)) / (barDuration * 2f);

        int newBeatCount = (int)(totalTime / beatDuration);
        int newBarCount  = (int)(totalTime / barDuration);
        int prevBarCount = (int)(prevTotal  / barDuration);

        onBeat    = (newBeatCount > beatCount);
        onBar     = (newBarCount  > prevBarCount);
        onPhrase  = onBar && (newBarCount % 2  == 0);
        onSection = onBar && (newBarCount % 8  == 0);

        beatCount = newBeatCount;
        barCount  = newBarCount;
    }

    public void reset() {
        reset(0f);
    }

    public void reset(float audioOffsetSeconds) {
        totalTime    = audioOffsetSeconds;
        beatPhase    = 0f;
        barPhase     = 0f;
        phrasePhase  = 0f;
        beatCount    = (int)(totalTime / beatDuration);
        barCount     = (int)(totalTime / barDuration);
        onBeat = onBar = onPhrase = onSection = false;
    }

    public float beatPunch(float decayBeats) {
        float window = decayBeats;
        float t      = beatPhase / window;
        if (t > 1f) return 0f;

        return t < 0.05f ? t / 0.05f : 1f - t;
    }

    public float barSine() {
        return 0.5f + 0.5f * (float) Math.sin(barPhase * Math.PI * 2.0);
    }

    public boolean isBarBeat(int n) {
        return onBeat && (beatCount % 4 == n);
    }

    public float getBpm() { return bpm; }

    public float getTotalTime() { return totalTime; }
}