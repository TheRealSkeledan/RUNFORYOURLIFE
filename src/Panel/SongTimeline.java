package Panel;

/**
 * SongTimeline — maps the current bar count from {@link BeatClock} to a named
 * {@link Section}, and exposes smooth transition values that
 * {@link BackgroundRenderer} and {@link GamePanel} can consume each frame.
 *
 * ── Structure of the track (160 BPM, 1 bar = 1.5 s) ──────────────────────────
 *
 *  Bars  1–19   CALM          intro, no alarms
 *  Bars 19–35   INTENSE       alarms, screen shake
 *  Bar  35      BREAK         silent beat, everything drops
 *  Bars 36–51   FULL_FORCE    alarms + shake, maximum intensity
 *  Bars 51–64   GREY          greyscale, eerie quiet
 *  Bars 64–72   COLOUR_RETURN colour bleeds back in
 *  Bars 72–96   INTENSE2      alarms + shake again
 *  Bar  96+     OUTRO         fade to normal, ~4 s tail before loop
 *
 * All bar numbers are 1-indexed (matching the musical convention).
 * Internally we compare against 0-indexed {@code BeatClock.barCount}.
 */
public class SongTimeline {

    // ── Section definitions ───────────────────────────────────────────────────

    public enum Section {
        CALM,           // intro — quiet, no alarm effects
        INTENSE,        // first drop — alarms, shake
        BREAK,          // one-bar breath
        FULL_FORCE,     // heavier drop — alarms + shake at max
        GREY,           // greyscale calm
        COLOUR_RETURN,  // colour bleeds back (grey→normal)
        INTENSE2,       // second big drop
        OUTRO           // tail before loop restart
    }

    // Bar boundaries (0-indexed, so bar 1 in music = index 0 here)
    private static final int BAR_INTENSE       = 17;   // bar 19 in music
    private static final int BAR_BREAK         = 34;   // bar 35
    private static final int BAR_FULL_FORCE    = 35;   // bar 36
    private static final int BAR_GREY          = 50;   // bar 51
    private static final int BAR_COLOUR_RETURN = 55;   // bar 64
    private static final int BAR_INTENSE2      = 63;   // bar 72
    private static final int BAR_OUTRO         = 95;   // bar 96

    // ── Per-frame output values ───────────────────────────────────────────────

    /** The section currently playing. */
    public Section section = Section.CALM;

    /**
     * 0..1 — how "intense" the moment is right now.
     * Drives alarm brightness, shake magnitude, tint strength.
     * 0 = fully calm, 1 = maximum intensity.
     */
    public float intensity = 0f;

    /**
     * 0..1 — greyscale amount.
     * 0 = full colour, 1 = fully desaturated.
     */
    public float grey = 0f;

    /**
     * Screen-shake offset in pixels this frame (already computed).
     * Apply as a translation before drawing the world.
     */
    public float shakeX = 0f;
    public float shakeY = 0f;

    /**
     * True for exactly one tick at the BREAK bar downbeat —
     * use to trigger a flash / impact frame.
     */
    public boolean onBreakHit = false;

    // ── Internal state ────────────────────────────────────────────────────────

    private final java.util.Random rng = new java.util.Random();
    private float smoothIntensity = 0f;   // lerped toward target each frame
    private float smoothGrey      = 0f;
    private int   lastBarCount    = -1;

    // ── Update ────────────────────────────────────────────────────────────────

    /**
     * Call once per tick, after {@link BeatClock#update(float)}.
     *
     * @param clock  the live BeatClock
     * @param dt     delta time in seconds
     */
    public void update(BeatClock clock, float dt) {
        int bar = clock.barCount;   // 0-indexed

        onBreakHit = clock.onBar && (bar == BAR_BREAK);

        // ── Determine current section ─────────────────────────────────────────
        if      (bar >= BAR_OUTRO)         section = Section.OUTRO;
        else if (bar >= BAR_INTENSE2)      section = Section.INTENSE2;
        else if (bar >= BAR_COLOUR_RETURN) section = Section.COLOUR_RETURN;
        else if (bar >= BAR_GREY)          section = Section.GREY;
        else if (bar >= BAR_FULL_FORCE)    section = Section.FULL_FORCE;
        else if (bar == BAR_BREAK)         section = Section.BREAK;
        else if (bar >= BAR_INTENSE)       section = Section.INTENSE;
        else                               section = Section.CALM;

        // ── Target intensity for this section ─────────────────────────────────
        float targetIntensity = switch (section) {
            case CALM          -> 0f;
            case INTENSE       -> 0.65f + 0.35f * clock.barSine();   // breathes
            case BREAK         -> 0f;
            case FULL_FORCE    -> 0.85f + 0.15f * clock.beatPunch(0.5f);
            case GREY          -> 0f;
            case COLOUR_RETURN -> 0f;
            case INTENSE2      -> 0.80f + 0.20f * clock.beatPunch(0.5f);
            case OUTRO         -> 0f;
        };

        // ── Target grey for this section ──────────────────────────────────────
        // GREY: ramp to 1 over the first 2 bars, hold
        // COLOUR_RETURN: ramp from 1 → 0 over 8 bars
        float targetGrey = switch (section) {
            case GREY -> {
                int barsIn = bar - BAR_GREY;
                yield barsIn < 2 ? barsIn / 2f : 1f;
            }
            case COLOUR_RETURN -> {
                int barsIn   = bar - BAR_COLOUR_RETURN;
                int totalBars = BAR_INTENSE2 - BAR_COLOUR_RETURN; // 8 bars
                yield 1f - (float) barsIn / totalBars;
            }
            default -> 0f;
        };

        // ── Smooth transitions (lerp speed tuned to feel snappy but not jarring)
        float intensityLerp = (targetIntensity > smoothIntensity) ? 6f : 2.5f;
        float greyLerp      = 1.2f;

        smoothIntensity += (targetIntensity - smoothIntensity) * intensityLerp * dt;
        smoothGrey      += (targetGrey      - smoothGrey)      * greyLerp      * dt;

        intensity = Math.max(0f, Math.min(1f, smoothIntensity));
        grey      = Math.max(0f, Math.min(1f, smoothGrey));

        // ── Screen shake ──────────────────────────────────────────────────────
        shakeX = 0f; shakeY = 0f;
        if (intensity > 0.01f) {
            // Base shake scaled by intensity; extra punch on each beat downbeat
            float mag = intensity * 5f;
            if (clock.isBarBeat(0)) mag += 6f * clock.beatPunch(0.3f);   // downbeat hit
            if (clock.isBarBeat(2)) mag += 3f * clock.beatPunch(0.3f);   // backbeat hit
            if (section == Section.FULL_FORCE || section == Section.INTENSE2) {
                mag *= 1.4f;   // extra violent in the heavy sections
            }
            shakeX = (rng.nextFloat() * 2f - 1f) * mag;
            shakeY = (rng.nextFloat() * 2f - 1f) * mag * 0.6f;
        }

        lastBarCount = bar;
    }

    public void reset() {
        section         = Section.CALM;
        intensity       = 0f;
        grey            = 0f;
        shakeX          = 0f;
        shakeY          = 0f;
        onBreakHit      = false;
        smoothIntensity = 0f;
        smoothGrey      = 0f;
        lastBarCount    = -1;
    }

    // ── Convenience queries ───────────────────────────────────────────────────

    /** True while alarms and shake should be active. */
    public boolean isIntenseSection() {
        return section == Section.INTENSE
                || section == Section.FULL_FORCE
                || section == Section.INTENSE2;
    }

    /** True while greyscale or colour-return is happening. */
    public boolean isGreySection() {
        return section == Section.GREY || section == Section.COLOUR_RETURN;
    }
}